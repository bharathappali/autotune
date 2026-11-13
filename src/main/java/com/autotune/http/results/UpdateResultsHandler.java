package com.autotune.http.results;

import com.autotune.analyzer.experiment.ExperimentInitiator;
import com.autotune.analyzer.exceptions.KruizeResponse;
import com.autotune.analyzer.utils.AnalyzerErrorConstants;
import com.autotune.http.HotJson;
import com.autotune.operator.KruizeDeploymentInfo;
import com.autotune.utils.MetricsConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * POST /updateResults: one parse, one validation, one multi-row insert, one update_date.
 */
public final class UpdateResultsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateResultsHandler.class);
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final String LOAD_EXPERIMENT = """
            SELECT version, cluster_name, performance_profile, experiment_type, update_date, extended_data::text AS extended_data
            FROM kruize_experiments WHERE experiment_name = $1
            """;
    private static final String LOAD_PROFILE = "SELECT slo::text AS slo FROM kruize_performance_profiles WHERE name = $1";
    private static final String INSERT_MIGRATION_KEY = """
            INSERT INTO kruize_results (version, experiment_name, cluster_name, interval_start_time, interval_end_time, duration_minutes, extended_data, meta_data)
            SELECT version, experiment_name, cluster_name, interval_start_time, interval_end_time, duration_minutes, extended_data, NULL
            FROM UNNEST($1::varchar[], $2::varchar[], $3::varchar[], $4::timestamp[], $5::timestamp[], $6::float8[], $7::jsonb[])
            AS u(version, experiment_name, cluster_name, interval_start_time, interval_end_time, duration_minutes, extended_data)
            ON CONFLICT (experiment_name, interval_end_time, interval_start_time) DO NOTHING
            RETURNING experiment_name, interval_start_time, interval_end_time
            """;
    /**
     * hbm2ddl update builds the primary key from the two {@code @Id} fields. The migration key includes
     * interval_start_time. The insert uses whichever unique key startup actually created.
     */
    private static final String INSERT_ENTITY_KEY = """
            INSERT INTO kruize_results (version, experiment_name, cluster_name, interval_start_time, interval_end_time, duration_minutes, extended_data, meta_data)
            SELECT version, experiment_name, cluster_name, interval_start_time, interval_end_time, duration_minutes, extended_data, NULL
            FROM UNNEST($1::varchar[], $2::varchar[], $3::varchar[], $4::timestamp[], $5::timestamp[], $6::float8[], $7::jsonb[])
            AS u(version, experiment_name, cluster_name, interval_start_time, interval_end_time, duration_minutes, extended_data)
            ON CONFLICT (experiment_name, interval_end_time) DO NOTHING
            RETURNING experiment_name, interval_start_time, interval_end_time
            """;
    private static final String PRIMARY_KEY_COLUMNS = """
            SELECT a.attname
            FROM pg_index i
            JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
            WHERE i.indrelid = 'kruize_results'::regclass AND i.indisprimary
            """;
    private static final String UPDATE_DATE = "UPDATE kruize_experiments SET update_date = $1 WHERE experiment_name = ANY($2)";

    private final PgPool pool;
    private final java.util.concurrent.atomic.AtomicReference<String> insertSql = new java.util.concurrent.atomic.AtomicReference<>();

    public UpdateResultsHandler(PgPool pool) {
        this.pool = pool;
    }

    public void handle(RoutingContext ctx) {
        int count = REQUESTS.incrementAndGet();
        Timer.Sample sample = Timer.start(MetricsConfig.meterRegistry());
        Buffer body = ctx.body() == null ? null : ctx.body().buffer();
        JsonNode tree;
        try {
            if (body == null || body.length() == 0) {
                fail(ctx, sample, 400, AnalyzerErrorConstants.AutotuneObjectErrors.JSON_PARSING_ERROR, null);
                return;
            }
            tree = HotJson.TREE_READER.readTree(body.getBytes());
        } catch (Exception e) {
            fail(ctx, sample, 400, AnalyzerErrorConstants.AutotuneObjectErrors.JSON_PARSING_ERROR, null);
            return;
        }
        if (tree == null || !tree.isArray()) {
            fail(ctx, sample, 400, AnalyzerErrorConstants.AutotuneObjectErrors.JSON_PARSING_ERROR, null);
            return;
        }
        String blank = ResultsCheck.blankNumber(tree);
        if (blank != null) {
            fail(ctx, sample, 400, blank, null);
            return;
        }
        if (tree.size() > KruizeDeploymentInfo.bulk_update_results_limit) {
            fail(ctx, sample, 400, AnalyzerErrorConstants.AutotuneObjectErrors.UNSUPPORTED_EXPERIMENT_RESULTS, null);
            return;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (JsonNode record : tree) {
            JsonNode name = record.get("experiment_name");
            if (name != null && name.isTextual()) {
                names.add(name.asText());
            }
        }
        loadAll(names).onSuccess(metas -> {
            try {
                prepare(ctx, sample, count, tree, metas);
            } catch (Exception e) {
                LOGGER.error("updateResults request {} failed", count, e);
                fail(ctx, sample, 500, e.getMessage(), null);
            }
        }).onFailure(err -> {
            LOGGER.error("updateResults request {} failed to load metadata", count, err);
            fail(ctx, sample, 500, err.getMessage(), null);
        });
    }

    private void prepare(RoutingContext ctx, Timer.Sample sample, int count, JsonNode tree, java.util.Map<String, ExperimentCache.ExperimentMeta> metas) {
        List<Pending> pending = new ArrayList<>();
        List<Failed> failed = new ArrayList<>();
        for (JsonNode record : tree) {
            String name = ResultsReshape.text(record.get("experiment_name"));
            ExperimentCache.ExperimentMeta meta = name == null ? null : metas.get(name);
            List<String> errors = ResultsCheck.check(record, meta);
            String version = ResultsReshape.text(record.get("version"));
            String startText = ResultsReshape.text(record.get("interval_start_time"));
            String endText = ResultsReshape.text(record.get("interval_end_time"));
            if (!errors.isEmpty()) {
                failed.add(new Failed(version, name, startText, endText, errors, 400));
                continue;
            }
            Instant start = ResultsCheck.parse(startText);
            Instant end = ResultsCheck.parse(endText);
            long minutes = (end.toEpochMilli() - start.toEpochMilli()) / 60000L;
            byte[] extended = ResultsReshape.extendedData(record, start, end);
            pending.add(new Pending(version, name, meta.clusterName(),
                    LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                    LocalDateTime.ofInstant(end, ZoneOffset.UTC),
                    (double) minutes, extended, startText, endText));
        }
        if (pending.isEmpty()) {
            if (failed.isEmpty()) {
                ok(ctx, sample);
            } else {
                fail(ctx, sample, 400, partialMessage(tree.size(), failed.size()), failed);
            }
            return;
        }
        List<Pending> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Pending row : pending) {
            if (!seen.add(row.key())) {
                failed.add(new Failed(row.version(), row.name(), row.startText(), row.endText(),
                        List.of(AnalyzerErrorConstants.APIErrors.updateResultsAPI.RESULTS_ALREADY_EXISTS), 409));
            } else {
                unique.add(row);
            }
        }
        insert(unique, false).onSuccess(inserted -> {
            for (Pending row : unique) {
                if (!inserted.contains(row.key())) {
                    failed.add(new Failed(row.version(), row.name(), row.startText(), row.endText(),
                            List.of(AnalyzerErrorConstants.APIErrors.updateResultsAPI.RESULTS_ALREADY_EXISTS), 409));
                }
            }
            Set<String> failedNames = new HashSet<>();
            for (Failed row : failed) {
                if (row.name() != null) {
                    failedNames.add(row.name());
                }
            }
            List<String> updateNames = new ArrayList<>();
            for (JsonNode record : tree) {
                String name = ResultsReshape.text(record.get("experiment_name"));
                if (name != null && !failedNames.contains(name) && !updateNames.contains(name)) {
                    updateNames.add(name);
                }
            }
            Future<Void> dates = updateNames.isEmpty() ? Future.succeededFuture() : updateDates(updateNames);
            dates.onComplete(done -> {
                if (done.failed()) {
                    LOGGER.error("Failed to update the date in the experiments table");
                }
                if (failed.isEmpty()) {
                    ok(ctx, sample);
                } else {
                    LOGGER.error("updateResults API request payload for requestID {} failed", count);
                    fail(ctx, sample, 400, partialMessage(tree.size(), failed.size()), failed);
                }
            });
        }).onFailure(err -> {
            LOGGER.error("updateResults insert failed", err);
            fail(ctx, sample, 500, err.getMessage(), null);
        });
    }

    private Future<java.util.Map<String, ExperimentCache.ExperimentMeta>> loadAll(Set<String> names) {
        if (names.isEmpty()) {
            return Future.succeededFuture(java.util.Map.of());
        }
        List<Future<ExperimentCache.ExperimentMeta>> loads = new ArrayList<>();
        for (String name : names) {
            loads.add(loadOne(name));
        }
        return Future.all(loads).map(done -> {
            java.util.Map<String, ExperimentCache.ExperimentMeta> map = new java.util.HashMap<>();
            for (int i = 0; i < loads.size(); i++) {
                ExperimentCache.ExperimentMeta meta = loads.get(i).result();
                if (meta != null) {
                    map.put(meta.name(), meta);
                }
            }
            return map;
        });
    }

    private Future<ExperimentCache.ExperimentMeta> loadOne(String name) {
        ExperimentCache.ExperimentMeta cached = ExperimentCache.get(name);
        if (cached != null) {
            return Future.succeededFuture(cached);
        }
        return pool.preparedQuery(LOAD_EXPERIMENT).execute(Tuple.of(name)).compose(rows -> {
            if (rows.size() == 0) {
                return Future.succeededFuture(null);
            }
            Row row = rows.iterator().next();
            String profile = row.getString("performance_profile");
            return loadProfile(profile).map(parsed -> {
                JsonNode extended = readTree(row.getString("extended_data"));
                String version = textOr(extended, "version", row.getString("version"));
                ExperimentCache.ExperimentMeta meta = new ExperimentCache.ExperimentMeta(
                        name,
                        version,
                        row.getString("cluster_name"),
                        profile,
                        row.getLong("experiment_type") == null ? 1L : row.getLong("experiment_type"),
                        readTimestamp(row, "update_date"),
                        extended,
                        parsed.metrics(),
                        parsed.functions(),
                        parsed.hasQuery());
                ExperimentCache.put(meta);
                return meta;
            });
        });
    }

    private Future<ProfileBits> loadProfile(String name) {
        if (name == null || name.isBlank()) {
            return Future.succeededFuture(ProfileBits.EMPTY);
        }
        return pool.preparedQuery(LOAD_PROFILE).execute(Tuple.of(name)).map(rows -> {
            if (rows.size() == 0) {
                return ProfileBits.EMPTY;
            }
            JsonNode slo = readTree(rows.iterator().next().getString("slo"));
            Set<String> metrics = new HashSet<>();
            Set<String> functions = new HashSet<>();
            boolean[] hasQuery = new boolean[1];
            JsonNode variables = slo == null ? null : slo.get("function_variables");
            if (variables != null && variables.isArray()) {
                for (JsonNode variable : variables) {
                    if (variable.hasNonNull("name")) {
                        metrics.add(variable.get("name").asText());
                    }
                    if (variable.hasNonNull("query") && !variable.get("query").asText().isBlank()) {
                        hasQuery[0] = true;
                    }
                    // Create stores aggregation_functions as a Gson map (object). The API JSON is an array.
                    collectFunctions(variable.get("aggregation_functions"), functions, hasQuery);
                }
            }
            if (functions.isEmpty() && !hasQuery[0]) {
                LOGGER.warn("Performance profile {} stored slo has no aggregation functions or query", name);
            }
            return new ProfileBits(metrics, functions, hasQuery[0]);
        });
    }

    private Future<Set<String>> insert(List<Pending> pending, boolean retried) {
        String[] versions = new String[pending.size()];
        String[] names = new String[pending.size()];
        String[] clusters = new String[pending.size()];
        LocalDateTime[] starts = new LocalDateTime[pending.size()];
        LocalDateTime[] ends = new LocalDateTime[pending.size()];
        Double[] durations = new Double[pending.size()];
        JsonObject[] documents = new JsonObject[pending.size()];
        for (int i = 0; i < pending.size(); i++) {
            Pending row = pending.get(i);
            versions[i] = row.version();
            names[i] = row.name();
            clusters[i] = row.cluster();
            starts[i] = row.start();
            ends[i] = row.end();
            durations[i] = row.durationMinutes();
            documents[i] = new JsonObject(new String(row.extended(), StandardCharsets.UTF_8));
        }
        Tuple tuple = Tuple.tuple()
                .addArrayOfString(versions)
                .addArrayOfString(names)
                .addArrayOfString(clusters)
                .addArrayOfLocalDateTime(starts)
                .addArrayOfLocalDateTime(ends)
                .addArrayOfDouble(durations)
                .addArrayOfJsonObject(documents);
        return resolveInsert().compose(sql -> pool.withTransaction(client -> client.preparedQuery(sql).execute(tuple))
                .map(this::insertedKeys)
                .recover(err -> {
                    if (!retried && partitionMissing(err)) {
                        return createPartitions(pending.get(0).end()).compose(v -> insert(pending, true));
                    }
                    if (conflictTargetMissing(err) && INSERT_MIGRATION_KEY.equals(sql)) {
                        insertSql.set(INSERT_ENTITY_KEY);
                        return insert(pending, retried);
                    }
                    return Future.failedFuture(err);
                }));
    }

    private Future<String> resolveInsert() {
        String cached = insertSql.get();
        if (cached != null) {
            return Future.succeededFuture(cached);
        }
        return pool.query(PRIMARY_KEY_COLUMNS).execute().map(rows -> {
            boolean includesStart = false;
            for (Row row : rows) {
                if ("interval_start_time".equals(row.getString(0))) {
                    includesStart = true;
                }
            }
            String sql = includesStart ? INSERT_MIGRATION_KEY : INSERT_ENTITY_KEY;
            insertSql.compareAndSet(null, sql);
            return insertSql.get();
        }).recover(err -> {
            LOGGER.warn("Could not read kruize_results primary key, using the entity key", err);
            insertSql.compareAndSet(null, INSERT_ENTITY_KEY);
            return Future.succeededFuture(insertSql.get());
        });
    }

    private static boolean conflictTargetMissing(Throwable err) {
        Throwable current = err;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("no unique or exclusion constraint")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Set<String> insertedKeys(RowSet<Row> rows) {
        Set<String> keys = new HashSet<>();
        for (Row row : rows) {
            keys.add(resultKey(row.getString("experiment_name"), row.getLocalDateTime("interval_start_time"), row.getLocalDateTime("interval_end_time")));
        }
        return keys;
    }

    private Future<Void> createPartitions(LocalDateTime end) {
        List<String> statements = PartitionSql.statements(end);
        Future<Void> chain = Future.succeededFuture();
        for (String sql : statements) {
            chain = chain.compose(v -> pool.query(sql).execute().mapEmpty());
        }
        return chain;
    }

    private Future<Void> updateDates(List<String> names) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        Tuple tuple = Tuple.tuple().addLocalDateTime(now).addArrayOfString(names.toArray(String[]::new));
        return pool.preparedQuery(UPDATE_DATE).execute(tuple).mapEmpty();
    }

    /**
     * Hibernate maps update_date through java.sql.Timestamp, so Postgres stores timestamp
     * and the reactive client returns LocalDateTime. A timestamptz column returns OffsetDateTime.
     */
    private static OffsetDateTime readTimestamp(Row row, String column) {
        Object value = row.getValue(column);
        if (value instanceof OffsetDateTime offset) {
            return offset;
        }
        if (value instanceof LocalDateTime local) {
            return local.atOffset(ZoneOffset.UTC);
        }
        return null;
    }

    private static boolean partitionMissing(Throwable err) {
        Throwable current = err;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("no partition of relation")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return HotJson.TREE_READER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        if (node != null && node.hasNonNull(field)) {
            return node.get(field).asText();
        }
        return fallback;
    }

    private static String partialMessage(int total, int failed) {
        return String.format("Out of a total of %s records, %s failed to save", total, failed);
    }

    private static void ok(RoutingContext ctx, Timer.Sample sample) {
        ctx.response().setStatusCode(201).putHeader("Content-Type", "application/json;charset=UTF-8").end(Buffer.buffer(HotJson.UPDATE_RESULTS_SUCCESS));
        stop(sample, "success");
    }

    private static void fail(RoutingContext ctx, Timer.Sample sample, int status, String message, List<Failed> failed) {
        ObjectNode body = HotJson.mapper().createObjectNode();
        body.put("message", message == null ? "" : message);
        body.put("httpcode", status);
        body.put("documentationLink", "");
        body.put("status", "ERROR");
        if (failed != null) {
            ArrayNode data = body.putArray("data");
            for (Failed row : failed) {
                ObjectNode item = data.addObject();
                if (row.version() != null) {
                    item.put("version", row.version());
                }
                if (row.name() != null) {
                    item.put("experiment_name", row.name());
                }
                if (row.start() != null) {
                    item.put("interval_start_time", row.start());
                }
                if (row.end() != null) {
                    item.put("interval_end_time", row.end());
                }
                ArrayNode errors = item.putArray("errors");
                List<KruizeResponse> grouped = ExperimentInitiator.getErrorMap(row.messages());
                if (grouped != null) {
                    for (KruizeResponse error : grouped) {
                        ObjectNode errorNode = errors.addObject();
                        errorNode.put("message", error.getMessage());
                        errorNode.put("httpcode", row.code() == 409 ? 409 : error.getHttpcode());
                        errorNode.put("documentationLink", "");
                        errorNode.put("status", "ERROR");
                    }
                }
            }
        }
        try {
            ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json;charset=UTF-8")
                    .end(Buffer.buffer(HotJson.COMPACT_WRITER.writeValueAsBytes(body)));
        } catch (Exception e) {
            ctx.response().setStatusCode(500).end();
        }
        stop(sample, "failure");
    }

    private static void stop(Timer.Sample sample, String status) {
        if (sample != null && MetricsConfig.timerBUpdateResults != null) {
            sample.stop(MetricsConfig.timerBUpdateResults.tag("status", status).register(MetricsConfig.meterRegistry()));
        }
    }

    private static void collectFunctions(JsonNode functionsNode, Set<String> functions, boolean[] hasQuery) {
        if (functionsNode == null || functionsNode.isNull()) {
            return;
        }
        if (functionsNode.isArray()) {
            for (JsonNode function : functionsNode) {
                takeFunction(function, null, functions, hasQuery);
            }
            return;
        }
        if (functionsNode.isObject()) {
            functionsNode.fields().forEachRemaining(entry -> takeFunction(entry.getValue(), entry.getKey(), functions, hasQuery));
        }
    }

    private static void takeFunction(JsonNode function, String key, Set<String> functions, boolean[] hasQuery) {
        if (function != null && function.isObject()) {
            if (function.hasNonNull("function")) {
                functions.add(function.get("function").asText());
            } else if (key != null && !key.isBlank()) {
                functions.add(key);
            }
            JsonNode query = function.get("query");
            if (query != null && query.isTextual() && !query.asText().isBlank()) {
                hasQuery[0] = true;
            }
        } else if (key != null && !key.isBlank()) {
            functions.add(key);
        }
    }

    private static String resultKey(String name, LocalDateTime start, LocalDateTime end) {
        LocalDateTime startKey = start == null ? null : start.truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime endKey = end == null ? null : end.truncatedTo(ChronoUnit.MILLIS);
        return name + "|" + startKey + "|" + endKey;
    }

    private record Pending(String version, String name, String cluster, LocalDateTime start, LocalDateTime end,
                           double durationMinutes, byte[] extended, String startText, String endText) {
        String key() {
            return resultKey(name, start, end);
        }
    }

    private record Failed(String version, String name, String start, String end, List<String> messages, int code) {
    }

    private record ProfileBits(Set<String> metrics, Set<String> functions, boolean hasQuery) {
        static final ProfileBits EMPTY = new ProfileBits(Set.of(), Set.of(), false);
    }
}
