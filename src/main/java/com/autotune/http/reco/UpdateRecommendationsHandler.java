package com.autotune.http.reco;

import com.autotune.analyzer.kruizeObject.KruizeObject;
import com.autotune.analyzer.recommendations.engine.RecommendationEngine;
import com.autotune.analyzer.recommendations.term.Terms;
import com.autotune.analyzer.serviceObjects.Converters;
import com.autotune.analyzer.serviceObjects.CreateExperimentAPIObject;
import com.autotune.analyzer.serviceObjects.KubernetesAPIObject;
import com.autotune.analyzer.serviceObjects.ListRecommendationsAPIObject;
import com.autotune.analyzer.services.RecommendationsResource;
import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.analyzer.utils.AnalyzerErrorConstants;
import com.autotune.analyzer.utils.ExperimentTypeUtil;
import com.autotune.common.data.metrics.MetricAggregationInfoResults;
import com.autotune.common.data.metrics.MetricResults;
import com.autotune.common.data.result.ContainerData;
import com.autotune.common.data.result.IntervalResults;
import com.autotune.common.data.result.NamespaceData;
import com.autotune.common.k8sObjects.K8sObject;
import com.autotune.database.helper.DBHelpers;
import com.autotune.database.table.KruizeRecommendationEntry;
import com.autotune.http.HotJson;
import com.autotune.http.ServletBridge;
import com.autotune.http.results.PartitionSql;
import com.autotune.operator.KruizeDeploymentInfo;
import com.autotune.utils.KruizeConstants;
import com.autotune.utils.MetricsConfig;
import com.autotune.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.autotune.analyzer.utils.AnalyzerConstants.VersionConstants.APIVersionConstants.CURRENT_RECOMMENDATIONS_API_VERSION;

/**
 * POST /updateRecommendations and POST /kruize/api/v1/recommendations.
 * One pipelined read, numeric extract, the existing processors, one upsert.
 */
public final class UpdateRecommendationsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateRecommendationsHandler.class);
    private static final Gson EXPERIMENT_GSON = new Gson();
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final String LOAD_EXPERIMENT = """
            SELECT version, cluster_name, experiment_type, extended_data::text AS extended_data
            FROM kruize_experiments WHERE experiment_name = $1
            """;
    private static final String LOAD_RESULTS = """
            SELECT interval_start_time, interval_end_time, extended_data::text AS extended_data
            FROM kruize_results
            WHERE experiment_name = $1
              AND interval_end_time >= $2
              AND interval_end_time <= $3
              AND (($4::varchar IS NULL AND cluster_name IS NULL) OR cluster_name = $4)
            """;
    private static final String UPSERT = """
            INSERT INTO kruize_recommendations (version, experiment_name, cluster_name, interval_end_time, extended_data)
            VALUES ($1, $2, $3, $4, $5)
            ON CONFLICT (experiment_name, interval_end_time) DO UPDATE
            SET extended_data = EXCLUDED.extended_data
            """;

    private final PgPool pool;
    private final WorkerExecutor workers;
    private final ServletBridge cold;
    private final boolean v1;

    public UpdateRecommendationsHandler(PgPool pool, WorkerExecutor workers, ServletBridge cold, boolean v1) {
        this.pool = pool;
        this.workers = workers;
        this.cold = cold;
        this.v1 = v1;
    }

    public void handle(RoutingContext ctx) {
        if (!KruizeDeploymentInfo.is_ros_enabled || (v1 && KruizeDeploymentInfo.local)) {
            cold.handle(ctx);
            return;
        }
        int count = REQUESTS.incrementAndGet();
        Timer.Sample sample = Timer.start(MetricsConfig.meterRegistry());
        String experiment = ctx.request().getParam(KruizeConstants.JSONKeys.EXPERIMENT_NAME);
        String endText = ctx.request().getParam(KruizeConstants.JSONKeys.INTERVAL_END_TIME);
        String startText = ctx.request().getParam(KruizeConstants.JSONKeys.INTERVAL_START_TIME);
        RecommendationEngine probe = new RecommendationEngine(experiment, endText, startText);
        String invalid = v1 ? probe.validate_local() : probe.validate();
        if (invalid != null && !invalid.isEmpty()) {
            error(ctx, sample, 400, trimComma(invalid));
            return;
        }
        Timestamp end = Utils.DateUtils.getTimeStampFrom(KruizeConstants.DateFormats.STANDARD_JSON_DATE_FORMAT, endText);
        pool.withConnection(connection -> load(connection, experiment)).compose(loaded -> {
            if (loaded.object() == null) {
                error(ctx, sample, 400, AnalyzerErrorConstants.AutotuneObjectErrors.MISSING_EXPERIMENT_NAME + experiment);
                return Future.succeededFuture();
            }
            if (loaded.object().getExperiment_usecase_type() != null
                    && loaded.object().getExperiment_usecase_type().isLocal_monitoring()) {
                cold.handle(ctx);
                return Future.succeededFuture();
            }
            Timestamp windowStart = windowStart(loaded.object(), end);
            return connectionQuery(experiment, loaded.cluster(), windowStart, end).compose(rows ->
                    workers.<Computed>executeBlocking(promise -> {
                        try {
                            promise.complete(compute(loaded.object(), rows, experiment, endText, end, count));
                        } catch (Exception e) {
                            promise.fail(e);
                        }
                    }, false)
            ).compose(computed -> {
                if (computed.error() != null) {
                    error(ctx, sample, computed.status(), computed.error());
                    return Future.succeededFuture();
                }
                return upsert(computed, false).compose(v -> {
                    ctx.response().setStatusCode(201)
                            .putHeader("Content-Type", "application/json;charset=UTF-8")
                            .end(Buffer.buffer(computed.body()));
                    stop(sample, "success");
                    return Future.<Void>succeededFuture();
                });
            });
        }).onFailure(err -> {
            LOGGER.error("updateRecommendations failed", err);
            error(ctx, sample, 500, err.getMessage());
        });
    }

    private Future<Loaded> load(SqlConnection connection, String experiment) {
        return connection.preparedQuery(LOAD_EXPERIMENT).execute(Tuple.of(experiment)).map(rows -> {
            if (rows.size() == 0) {
                return new Loaded(null, null);
            }
            Row row = rows.iterator().next();
            CreateExperimentAPIObject api = EXPERIMENT_GSON.fromJson(row.getString("extended_data"), CreateExperimentAPIObject.class);
            Long type = row.getLong("experiment_type");
            api.setExperimentType(ExperimentTypeUtil.getExperimentTypeFromBitMask(type == null ? 1L : type));
            if (row.getString("cluster_name") != null) {
                api.setClusterName(row.getString("cluster_name"));
            }
            if (api.getExperimentName() == null) {
                api.setExperimentName(experiment);
            }
            KruizeObject object = Converters.KruizeObjectConverters.convertCreateExperimentAPIObjToKruizeObject(api);
            if (object != null) {
                object.setValidation_data(new com.autotune.common.data.ValidationOutputData(true, null, null));
            }
            return new Loaded(object, row.getString("cluster_name"));
        });
    }

    private Future<List<ResultRow>> connectionQuery(String experiment, String cluster, Timestamp start, Timestamp end) {
        Tuple tuple = Tuple.of(experiment,
                LocalDateTime.ofInstant(start.toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(end.toInstant(), ZoneOffset.UTC),
                cluster);
        return pool.preparedQuery(LOAD_RESULTS).execute(tuple).map(rows -> {
            List<ResultRow> list = new ArrayList<>();
            for (Row row : rows) {
                list.add(new ResultRow(row.getLocalDateTime("interval_start_time"), row.getLocalDateTime("interval_end_time"), row.getString("extended_data")));
            }
            return list;
        });
    }

    private Computed compute(KruizeObject object, List<ResultRow> rows, String experiment, String endText, Timestamp end, int count) throws Exception {
        if (rows.isEmpty()) {
            String message = String.format(AnalyzerErrorConstants.AutotuneObjectErrors.NO_METRICS_AVAILABLE,
                    HotJson.formatTimestamp(windowStart(object, end).getTime()), endText);
            return Computed.error(400, message);
        }
        applyRows(object, rows);
        RecommendationEngine engine = new RecommendationEngine(experiment, endText, null);
        engine.setKruizeObject(object);
        KruizeObject done;
        try {
            done = engine.preparePreloaded(count);
        } catch (Throwable failure) {
            throw new Exception(failure.getMessage(), failure);
        }
        if (done.getValidation_data() != null && !done.getValidation_data().isSuccess()) {
            int status = done.getValidation_data().getErrorCode() == null ? 500 : done.getValidation_data().getErrorCode();
            return Computed.error(status, done.getValidation_data().getMessage());
        }
        ListRecommendationsAPIObject api = Converters.KruizeObjectConverters.convertKruizeObjectToListRecommendationSO(done, false, false, end);
        if (v1 && api.getKubernetesObjects() != null) {
            for (KubernetesAPIObject kubernetesAPIObject : api.getKubernetesObjects()) {
                if (kubernetesAPIObject.getContainerAPIObjects() != null) {
                    kubernetesAPIObject.getContainerAPIObjects().forEach(container -> {
                        if (container.getContainerRecommendations() != null && container.getContainerRecommendations().getData() != null) {
                            RecommendationsResource.processRecommendations(container.getContainerRecommendations().getData().values());
                        }
                    });
                }
                if (kubernetesAPIObject.getNamespaceAPIObject() != null
                        && kubernetesAPIObject.getNamespaceAPIObject().getNamespaceRecommendations() != null
                        && kubernetesAPIObject.getNamespaceAPIObject().getNamespaceRecommendations().getData() != null) {
                    RecommendationsResource.processRecommendations(kubernetesAPIObject.getNamespaceAPIObject().getNamespaceRecommendations().getData().values());
                }
            }
            api.setApiVersion(CURRENT_RECOMMENDATIONS_API_VERSION);
        }
        byte[] body = HotJson.COMPACT_WRITER.writeValueAsBytes(List.of(api));
        KruizeRecommendationEntry stored = DBHelpers.Converters.KruizeObjectConverters.convertKruizeObjectTORecommendation(done, end);
        byte[] document = stored == null || stored.getExtended_data() == null
                ? HotJson.COMPACT_WRITER.writeValueAsBytes(java.util.Map.of("kubernetes_objects", api.getKubernetesObjects()))
                : HotJson.COMPACT_WRITER.writeValueAsBytes(stored.getExtended_data());
        LocalDateTime storedEnd = stored != null && stored.getInterval_end_time() != null
                ? stored.getInterval_end_time().toLocalDateTime()
                : LocalDateTime.ofInstant(end.toInstant(), ZoneOffset.UTC);
        String version = stored == null ? KruizeConstants.KRUIZE_RECOMMENDATION_API_VERSION.LATEST.getVersionNumber() : stored.getVersion();
        return new Computed(body, document, storedEnd, done.getClusterName(), done.getExperimentName(), version, null, 201);
    }

    private static void applyRows(KruizeObject object, List<ResultRow> rows) throws Exception {
        Map<String, K8sObject> byName = new HashMap<>();
        if (object.getKubernetes_objects() != null) {
            for (K8sObject k8sObject : object.getKubernetes_objects()) {
                if (k8sObject != null && k8sObject.getName() != null) {
                    byName.put(k8sObject.getName(), k8sObject);
                }
            }
        }
        for (ResultRow row : rows) {
            JsonNode root = HotJson.TREE_READER.readTree(row.extended());
            JsonNode objects = root.get("kubernetes_objects");
            if (objects == null || !objects.isArray()) {
                continue;
            }
            Timestamp start = Timestamp.valueOf(row.start());
            Timestamp end = Timestamp.valueOf(row.end());
            for (JsonNode k8s : objects) {
                String name = text(k8s.get("name"));
                K8sObject target = byName.get(name);
                if (target == null) {
                    target = new K8sObject(name, text(k8s.get("type")), text(k8s.get("namespace")));
                    byName.put(name, target);
                }
                JsonNode containers = k8s.get("containers");
                if (containers != null && containers.isObject()) {
                    if (target.getContainerDataMap() == null) {
                        target.setContainerDataMap(new HashMap<>());
                    }
                    var fields = containers.fields();
                    while (fields.hasNext()) {
                        var field = fields.next();
                        ContainerData container = target.getContainerDataMap().get(field.getKey());
                        if (container == null) {
                            container = new ContainerData(field.getKey(), text(field.getValue().get("container_image_name")), null, null);
                            target.getContainerDataMap().put(field.getKey(), container);
                        }
                        if (container.getResults() == null) {
                            container.setResults(new HashMap<>());
                        }
                        container.getResults().put(end, interval(field.getValue(), start, end));
                    }
                }
                JsonNode namespaces = k8s.get("namespaces");
                if (namespaces != null && namespaces.isObject()) {
                    if (target.getNamespaceDataMap() == null) {
                        target.setNamespaceDataMap(new HashMap<>());
                    }
                    var fields = namespaces.fields();
                    while (fields.hasNext()) {
                        var field = fields.next();
                        NamespaceData namespace = target.getNamespaceDataMap().get(field.getKey());
                        if (namespace == null) {
                            namespace = new NamespaceData(field.getKey(), null, null);
                            target.getNamespaceDataMap().put(field.getKey(), namespace);
                        }
                        if (namespace.getResults() == null) {
                            namespace.setResults(new HashMap<>());
                        }
                        namespace.getResults().put(end, interval(field.getValue(), start, end));
                    }
                }
            }
        }
        object.setKubernetes_objects(new ArrayList<>(byName.values()));
    }

    private static IntervalResults interval(JsonNode container, Timestamp start, Timestamp end) {
        IntervalResults interval = new IntervalResults(start, end);
        HashMap<AnalyzerConstants.MetricName, MetricResults> metrics = new HashMap<>();
        JsonNode metricNode = container.get("metrics");
        if (metricNode != null && metricNode.isObject()) {
            var fields = metricNode.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                try {
                    AnalyzerConstants.MetricName metricName = AnalyzerConstants.MetricName.valueOf(field.getKey());
                    JsonNode info = field.getValue().path("results").path("aggregation_info");
                    if (info.isMissingNode() || info.isNull()) {
                        info = field.getValue().path("aggregation_info");
                    }
                    MetricResults results = new MetricResults();
                    results.setName(metricName.name());
                    MetricAggregationInfoResults aggregation = new MetricAggregationInfoResults();
                    number(info, "avg", aggregation::setAvg);
                    number(info, "sum", aggregation::setSum);
                    number(info, "min", aggregation::setMin);
                    number(info, "max", aggregation::setMax);
                    number(info, "median", aggregation::setMedian);
                    number(info, "mode", aggregation::setMode);
                    number(info, "range", aggregation::setRange);
                    if (info.hasNonNull("count")) {
                        aggregation.setCount(info.get("count").asInt());
                    }
                    if (info.hasNonNull("format")) {
                        aggregation.setFormat(info.get("format").asText());
                        results.setFormat(info.get("format").asText());
                    }
                    results.setAggregationInfoResult(aggregation);
                    metrics.put(metricName, results);
                } catch (IllegalArgumentException ignored) {
                    // Metric names outside the enum are not read by the processors.
                }
            }
        }
        interval.setMetricResultsMap(metrics);
        return interval;
    }

    private static void number(JsonNode node, String field, java.util.function.Consumer<Double> setter) {
        if (node != null && node.hasNonNull(field) && node.get(field).isNumber()) {
            setter.accept(node.get(field).doubleValue());
        }
    }

    private static Timestamp windowStart(KruizeObject object, Timestamp end) {
        Map<String, Terms> terms = new HashMap<>();
        try {
            if (object.getMode() != null && object.getMode().equalsIgnoreCase(AnalyzerConstants.MONITOR)) {
                if (object.getRecommendation_settings() == null
                        || object.getRecommendation_settings().getTermSettings() == null
                        || object.getRecommendation_settings().getTermSettings().getTerms() == null) {
                    KruizeObject.setDefaultTerms(terms, object);
                } else {
                    KruizeObject.setCustomTerms(terms, object);
                }
            } else if (object.getRecommendation_settings() == null
                    || object.getRecommendation_settings().getTermSettings() == null
                    || object.getRecommendation_settings().getTermSettings().getTerms() == null) {
                KruizeObject.setDefaultTermsForAutoAndRecreate(terms, object);
            } else {
                KruizeObject.setCustomTerms(terms, object);
            }
        } catch (Throwable e) {
            KruizeObject.setDefaultTerms(terms, object);
        }
        int maxDay = Terms.getMaxDays(terms);
        return Timestamp.valueOf(end.toLocalDateTime().minusDays(maxDay));
    }

    private Future<Void> upsert(Computed computed, boolean retried) {
        JsonObject document = new JsonObject(new String(computed.stored(), StandardCharsets.UTF_8));
        Tuple tuple = Tuple.of(computed.version(), computed.name(), computed.cluster(), computed.end(), document);
        return pool.preparedQuery(UPSERT).execute(tuple).compose(rows -> Future.<Void>succeededFuture(), err -> {
            if (!retried && missingPartition(err)) {
                return partitions(computed.end()).compose(v -> upsert(computed, true));
            }
            return Future.failedFuture(err);
        });
    }

    private Future<Void> partitions(LocalDateTime end) {
        Future<Void> chain = Future.succeededFuture();
        for (String sql : PartitionSql.statements(end)) {
            chain = chain.compose(v -> pool.query(sql).execute().<Void>map(rows -> null));
        }
        return chain;
    }

    private static boolean missingPartition(Throwable err) {
        Throwable current = err;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("no partition of relation")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String trimComma(String message) {
        String trimmed = message.trim();
        if (trimmed.endsWith(",")) {
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static void error(RoutingContext ctx, Timer.Sample sample, int status, String message) {
        String body = "{\"message\":\"" + escape(message) + "\",\"httpcode\":" + status + ",\"documentationLink\":\"\",\"status\":\"ERROR\"}";
        ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json;charset=UTF-8").end(body);
        stop(sample, "failure");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void stop(Timer.Sample sample, String status) {
        if (sample != null && MetricsConfig.timerBUpdateRecommendations != null) {
            sample.stop(MetricsConfig.timerBUpdateRecommendations.tag("status", status).register(MetricsConfig.meterRegistry()));
        }
    }

    private record Loaded(KruizeObject object, String cluster) {
    }

    private record ResultRow(LocalDateTime start, LocalDateTime end, String extended) {
    }

    private record Computed(byte[] body, byte[] stored, LocalDateTime end, String cluster, String name, String version, String error, int status) {
        static Computed error(int status, String message) {
            return new Computed(null, null, null, null, null, null, message, status);
        }
    }
}
