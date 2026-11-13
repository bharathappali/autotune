package com.autotune.http.results;

import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.analyzer.utils.AnalyzerErrorConstants;
import com.autotune.analyzer.utils.ExperimentTypeUtil;
import com.autotune.http.HotJson;
import com.autotune.utils.KruizeConstants;
import com.autotune.utils.KruizeSupportedTypes;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.autotune.analyzer.utils.AnalyzerErrorConstants.AutotuneObjectErrors.MISSING_EXPERIMENT_NAME;

/**
 * Tree checks for one updateResults record. Messages match the validators the tests already assert.
 */
public final class ResultsCheck {
    private static final DateTimeFormatter PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final List<String> METRICS_WITHOUT_FORMAT = List.of("namespaceTotalPods", "namespaceRunningPods");
    private static final List<String> CONTAINER_MANDATORY = List.of("cpuUsage", "memoryUsage", "memoryRSS");
    private static final List<String> NAMESPACE_MANDATORY = List.of("namespaceCpuUsage", "namespaceMemoryUsage", "namespaceMemoryRSS");

    private ResultsCheck() {
    }

    public static List<String> check(JsonNode record, ExperimentCache.ExperimentMeta meta) {
        List<String> errors = new ArrayList<>();
        String name = text(record.get("experiment_name"));
        if (name == null) {
            errors.add(MISSING_EXPERIMENT_NAME + "null");
            return errors;
        }
        if (meta == null) {
            errors.add(MISSING_EXPERIMENT_NAME + name);
            return errors;
        }
        String version = text(record.get("version"));
        String expected = meta.version();
        if (expected == null && meta.extendedData() != null) {
            expected = text(meta.extendedData().get("version"));
        }
        if (version == null || version.isBlank()) {
            errors.add("version: must not be blank");
            return errors;
        }
        if (expected != null && !expected.equals(version)) {
            errors.add(String.format(AnalyzerErrorConstants.AutotuneObjectErrors.VERSION_MISMATCH, expected, version));
            return errors;
        }
        JsonNode startNode = record.get("interval_start_time");
        JsonNode endNode = record.get("interval_end_time");
        if (startNode == null || startNode.isNull()) {
            errors.add("interval_start_time: must not be null");
            return errors;
        }
        if (endNode == null || endNode.isNull()) {
            errors.add("interval_end_time: must not be null");
            return errors;
        }
        Instant start;
        Instant end;
        try {
            start = parse(startNode.asText());
            end = parse(endNode.asText());
        } catch (DateTimeParseException e) {
            errors.add(e.getMessage());
            return errors;
        }
        if (!start.isBefore(end)) {
            errors.add(AnalyzerErrorConstants.AutotuneObjectErrors.WRONG_TIMESTAMP);
            return errors;
        }
        JsonNode objects = record.get("kubernetes_objects");
        if (objects == null || objects.isNull()) {
            errors.add("kubernetes_objects: must not be null");
            return errors;
        }
        if (!objects.isArray() || objects.size() != 1) {
            errors.add("kubernetes_objects: " + AnalyzerErrorConstants.AutotuneObjectErrors.UNSUPPORTED_BULK_KUBERNETES);
            return errors;
        }
        String profileError = profile(record, meta, name);
        if (profileError != null) {
            errors.add(profileError);
            return errors;
        }
        String durationError = duration(meta, start, end);
        if (durationError != null) {
            errors.add(durationError);
            return errors;
        }
        String kubeError = kubernetes(record, meta, name);
        if (kubeError != null) {
            errors.add(kubeError);
        }
        return errors;
    }

    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "avg", "sum", "min", "max", "median", "mode", "range", "count", "value");

    public static String blankNumber(JsonNode node) {
        return blankNumber(null, node);
    }

    private static String blankNumber(String field, JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String found = blankNumber(entry.getKey(), entry.getValue());
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = blankNumber(field, child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (field != null && NUMERIC_FIELDS.contains(field) && node.isTextual()) {
            String value = node.asText().trim();
            if (value.isEmpty()) {
                return AnalyzerErrorConstants.AutotuneObjectErrors.AGGREGATION_INFO_INVALID_VALUE;
            }
            try {
                Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return e.getMessage();
            }
        }
        return null;
    }

    public static Instant parse(String text) {
        LocalDateTime local = LocalDateTime.parse(millisTimestamp(text), PARSER);
        return local.toInstant(ZoneOffset.UTC);
    }

    /**
     * Scale posts fractional seconds with microsecond precision. The stored and
     * returned contract is millisecond precision with a literal Z.
     */
    private static String millisTimestamp(String text) {
        if (text == null || !text.endsWith("Z")) {
            return text;
        }
        int dot = text.indexOf('.');
        if (dot < 0) {
            return text.substring(0, text.length() - 1) + ".000Z";
        }
        String fraction = text.substring(dot + 1, text.length() - 1);
        if (fraction.length() > 3) {
            fraction = fraction.substring(0, 3);
        } else if (fraction.length() < 3) {
            fraction = (fraction + "000").substring(0, 3);
        }
        return text.substring(0, dot) + "." + fraction + "Z";
    }

    private static String profile(JsonNode record, ExperimentCache.ExperimentMeta meta, String experiment) {
        JsonNode k8s = record.get("kubernetes_objects").get(0);
        JsonNode containers = k8s.get("containers");
        JsonNode namespace = k8s.get("namespaces");
        List<String> errors = new ArrayList<>();
        boolean namespaceExperiment = ExperimentTypeUtil.isNamespaceExperiment(meta.experimentType());
        if (!namespaceExperiment && containers != null && containers.isArray()) {
            checkContainers(containers, meta, experiment, errors);
        } else if (namespace != null && !namespace.isNull()) {
            checkNamespace(namespace, meta, experiment, errors);
        }
        if (errors.isEmpty()) {
            return null;
        }
        return "Performance profile: " + errors;
    }

    private static void checkContainers(JsonNode containers, ExperimentCache.ExperimentMeta meta, String experiment, List<String> errors) {
        for (JsonNode container : containers) {
            String label = text(container.get("container_name"));
            JsonNode metrics = container.get("metrics");
            if (metrics == null || metrics.isNull() || !metrics.isArray()) {
                errors.add(String.format(AnalyzerErrorConstants.AutotuneObjectErrors.MISSING_METRICS, label, experiment));
                continue;
            }
            String suffix = String.format(AnalyzerErrorConstants.AutotuneObjectErrors.CONTAINER_AND_EXPERIMENT, label, experiment);
            if (checkMetrics(metrics, true, suffix, meta, experiment, errors)) {
                return;
            }
        }
    }

    private static void checkNamespace(JsonNode namespace, ExperimentCache.ExperimentMeta meta, String experiment, List<String> errors) {
        String label = text(namespace.get("namespace"));
        JsonNode metrics = namespace.get("metrics");
        if (metrics == null || metrics.isNull() || !metrics.isArray()) {
            errors.add(String.format(AnalyzerErrorConstants.AutotuneObjectErrors.MISSING_METRICS_NAMESPACE, label, experiment));
            return;
        }
        String suffix = String.format(AnalyzerErrorConstants.AutotuneObjectErrors.NAMESPACE_AND_EXPERIMENT, label, experiment);
        checkMetrics(metrics, false, suffix, meta, experiment, errors);
    }

    private static boolean checkMetrics(JsonNode metrics, boolean container, String suffix, ExperimentCache.ExperimentMeta meta, String experiment, List<String> errors) {
        Set<String> names = new HashSet<>();
        for (JsonNode metric : metrics) {
            String metricName = text(metric.get("name"));
            String problem = metricProblem(metric, metricName);
            if (problem != null) {
                errors.add(problem.startsWith(AnalyzerErrorConstants.AutotuneObjectErrors.QUERY_FUNCTION_MISSING) ? problem : problem + suffix);
                return true;
            }
            if (metricName != null) {
                try {
                    AnalyzerConstants.MetricName.valueOf(metricName);
                    names.add(metricName);
                } catch (IllegalArgumentException ignored) {
                    // validateMetricsValues already reported an unsupported name.
                }
            }
            if (meta.profileFunctions().isEmpty() && !meta.profileHasQuery()) {
                errors.add(AnalyzerErrorConstants.AutotuneObjectErrors.QUERY_FUNCTION_MISSING);
                return true;
            }
        }
        if (!errors.isEmpty()) {
            return true;
        }
        List<String> mandatory = container ? CONTAINER_MANDATORY : NAMESPACE_MANDATORY;
        if (!names.containsAll(mandatory)) {
            errors.add(String.format(AnalyzerErrorConstants.AutotuneObjectErrors.MISSING_MANDATORY_PARAMETERS, experiment, mandatory));
            return true;
        }
        if (!meta.profileMetrics().isEmpty()) {
            List<String> invalid = new ArrayList<>();
            for (String present : names) {
                if (!meta.profileMetrics().contains(present)) {
                    invalid.add(present);
                }
            }
            if (!invalid.isEmpty()) {
                errors.add(String.format(AnalyzerErrorConstants.AutotuneObjectErrors.INVALID_METRICS_FOUND, experiment, invalid));
                return true;
            }
        }
        return false;
    }

    private static String metricProblem(JsonNode metric, String metricName) {
        try {
            AnalyzerConstants.MetricName.valueOf(metricName);
        } catch (Exception e) {
            return AnalyzerErrorConstants.AutotuneObjectErrors.UNSUPPORTED_METRIC;
        }
        JsonNode info = metric.path("results").path("aggregation_info");
        if (!METRICS_WITHOUT_FORMAT.contains(metricName)) {
            JsonNode format = info.get("format");
            String formatText = format != null && format.isTextual() ? format.asText() : null;
            if (!KruizeSupportedTypes.SUPPORTED_FORMATS.contains(formatText)) {
                return AnalyzerErrorConstants.AutotuneObjectErrors.UNSUPPORTED_FORMAT;
            }
        }
        if (info.isObject()) {
            // Getter order used by the existing validator reports avg before the other numeric fields.
            for (String field : List.of("avg", "sum", "min", "max", "median", "mode", "range", "count", "value")) {
                JsonNode valueNode = info.get(field);
                if (valueNode != null && valueNode.isNumber()) {
                    double value = valueNode.doubleValue();
                    if (value < 0 || Double.isNaN(value)) {
                        return field + AnalyzerErrorConstants.AutotuneObjectErrors.BLANK_AGGREGATION_INFO_VALUE + metricName;
                    }
                }
            }
        }
        return null;
    }

    private static String duration(ExperimentCache.ExperimentMeta meta, Instant start, Instant end) {
        JsonNode trial = meta.extendedData() == null ? null : meta.extendedData().get("trial_settings");
        if (trial == null) {
            return null;
        }
        JsonNode measurement = trial.get(KruizeConstants.JSONKeys.MEASUREMENT_DURATION);
        if (measurement == null || !measurement.isTextual()) {
            return null;
        }
        String raw = measurement.asText();
        if (raw.length() < 3) {
            return AnalyzerErrorConstants.AutotuneObjectErrors.MEASUREMENT_DURATION_ERROR;
        }
        double minutes;
        try {
            minutes = Double.parseDouble(raw.substring(0, raw.length() - 3));
        } catch (NumberFormatException e) {
            return AnalyzerErrorConstants.AutotuneObjectErrors.MEASUREMENT_DURATION_ERROR;
        }
        double durationSeconds = (end.toEpochMilli() - start.toEpochMilli()) / KruizeConstants.TimeConv.NO_OF_MSECS_IN_SEC;
        double lower = Math.abs((minutes * KruizeConstants.TimeConv.NO_OF_SECONDS_PER_MINUTE) - KruizeConstants.TimeConv.MEASUREMENT_DURATION_THRESHOLD_SECONDS);
        double upper = (minutes * KruizeConstants.TimeConv.NO_OF_SECONDS_PER_MINUTE) + KruizeConstants.TimeConv.MEASUREMENT_DURATION_THRESHOLD_SECONDS;
        if (durationSeconds < lower || durationSeconds > upper) {
            return AnalyzerErrorConstants.AutotuneObjectErrors.MEASUREMENT_DURATION_ERROR;
        }
        return null;
    }

    private static String kubernetes(JsonNode record, ExperimentCache.ExperimentMeta meta, String experiment) {
        JsonNode storedObjects = meta.extendedData() == null ? null : meta.extendedData().get("kubernetes_objects");
        if (storedObjects == null || !storedObjects.isArray() || storedObjects.isEmpty()) {
            return null;
        }
        JsonNode expected = storedObjects.get(0);
        JsonNode found = record.get("kubernetes_objects").get(0);
        String foundType = text(found.get("type"));
        String foundName = text(found.get("name"));
        String foundNamespace = text(found.get("namespace"));
        if (ExperimentTypeUtil.isNamespaceExperiment(meta.experimentType())
                && foundType != null && foundName != null && foundNamespace != null) {
            return String.format(AnalyzerErrorConstants.AutotuneObjectErrors.MISSING_NAMESPACE_SPECIFIC_UPDATE_RESULTS_FIELDS, experiment);
        }
        StringBuilder message = new StringBuilder();
        String expectedType = text(expected.get("type"));
        if (expectedType != null && !expectedType.equals(foundType)) {
            message.append(String.format("Kubernetes Object Types MisMatched. Expected Type: %s, Found: %s in Results for experiment: %s \n", expectedType, foundType, experiment));
        }
        String expectedName = text(expected.get("name"));
        if (expectedName != null && !expectedName.equals(foundName)) {
            message.append(String.format("Kubernetes Object Names MisMatched. Expected Name: %s, Found: %s in Results for experiment: %s \n", expectedName, foundName, experiment));
        }
        if (ExperimentTypeUtil.isContainerExperiment(meta.experimentType())) {
            String expectedNamespace = text(expected.get("namespace"));
            if (expectedNamespace != null && !expectedNamespace.equals(foundNamespace)) {
                message.append(String.format("Kubernetes Object Namespaces MisMatched. Expected Namespace: %s, Found: %s in Results for experiment: %s \n", expectedNamespace, foundNamespace, experiment));
            }
        }
        if (!message.isEmpty()) {
            return "kubernetes_objects : " + message;
        }
        if (!ExperimentTypeUtil.isNamespaceExperiment(meta.experimentType())) {
            JsonNode containers = found.get("containers");
            if (containers != null && containers.isArray()) {
                for (JsonNode container : containers) {
                    String containerName = text(container.get("container_name"));
                    if (containerName == null || containerName.isBlank()) {
                        return AnalyzerErrorConstants.AutotuneObjectErrors.NULL_OR_BLANK_CONTAINER_NAME;
                    }
                }
            }
        } else {
            JsonNode namespace = found.get("namespaces");
            if (namespace != null && !namespace.isNull()) {
                String namespaceName = text(namespace.get("namespace"));
                if (namespaceName == null || namespaceName.isBlank()) {
                    return AnalyzerErrorConstants.AutotuneObjectErrors.NULL_OR_BLANK_NAMESPACE_NAME;
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
