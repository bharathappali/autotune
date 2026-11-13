package com.autotune.http.results;

import com.autotune.analyzer.recommendations.utils.RecommendationUtils;
import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.http.HotJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

/**
 * Copies the request kubernetes_objects subtree into the K8sObject jsonb shape
 * readers already parse: containers as a map, metrics as a map, one results entry.
 */
public final class ResultsReshape {
    private ResultsReshape() {
    }

    public static byte[] extendedData(JsonNode record, Instant start, Instant end) {
        ObjectNode root = HotJson.mapper().createObjectNode();
        ArrayNode objects = root.putArray("kubernetes_objects");
        JsonNode source = record.get("kubernetes_objects").get(0);
        ObjectNode k8s = objects.addObject();
        putText(k8s, "type", source.get("type"));
        putText(k8s, "name", source.get("name"));
        putText(k8s, "namespace", source.get("namespace"));
        String endKey = HotJson.formatTimestamp(end.toEpochMilli());
        long durationSeconds = (end.toEpochMilli() - start.toEpochMilli()) / 1000L;
        double durationMinutes = (end.toEpochMilli() - start.toEpochMilli()) / 60000.0;
        JsonNode containers = source.get("containers");
        JsonNode namespace = source.get("namespaces");
        if (containers != null && containers.isArray() && !containers.isEmpty()) {
            ObjectNode containerMap = k8s.putObject("containers");
            for (JsonNode container : containers) {
                String containerName = text(container.get("container_name"));
                ObjectNode stored = containerMap.putObject(containerName == null ? "" : containerName);
                putText(stored, "container_name", container.get("container_name"));
                putText(stored, "container_image_name", container.get("container_image_name"));
                defaultRecommendations(stored);
                ObjectNode metrics = stored.putObject("metrics");
                ObjectNode intervalMetrics = HotJson.mapper().createObjectNode();
                ObjectNode accelerators = HotJson.mapper().createObjectNode();
                copyMetrics(container.get("metrics"), metrics, intervalMetrics, accelerators, true);
                ObjectNode results = stored.putObject("results");
                ObjectNode interval = results.putObject(endKey);
                interval.put("interval_start_time", HotJson.formatTimestamp(start.toEpochMilli()));
                interval.put("interval_end_time", endKey);
                interval.put("duration_in_minutes", durationMinutes);
                interval.put("duration_in_seconds", (double) durationSeconds);
                interval.set("metrics", intervalMetrics);
                if (!accelerators.isEmpty()) {
                    interval.set("acceleratorMetricResultHashMap", accelerators);
                }
            }
        } else if (namespace != null && !namespace.isNull()) {
            ObjectNode namespaceMap = k8s.putObject("namespaces");
            String namespaceName = text(namespace.get("namespace"));
            ObjectNode stored = namespaceMap.putObject(namespaceName == null ? "" : namespaceName);
            putText(stored, "namespace", namespace.get("namespace"));
            defaultRecommendations(stored);
            ObjectNode metrics = stored.putObject("metrics");
            ObjectNode intervalMetrics = HotJson.mapper().createObjectNode();
            copyMetrics(namespace.get("metrics"), metrics, intervalMetrics, null, false);
            ObjectNode results = stored.putObject("results");
            ObjectNode interval = results.putObject(endKey);
            interval.put("interval_start_time", HotJson.formatTimestamp(start.toEpochMilli()));
            interval.put("interval_end_time", endKey);
            interval.put("duration_in_minutes", durationMinutes);
            interval.put("duration_in_seconds", (double) durationSeconds);
            interval.set("metrics", intervalMetrics);
        }
        try {
            return HotJson.COMPACT_WRITER.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void copyMetrics(JsonNode metrics, ObjectNode metricMap, ObjectNode intervalMetrics, ObjectNode accelerators, boolean container) {
        if (metrics == null || !metrics.isArray()) {
            return;
        }
        for (JsonNode metric : metrics) {
            String name = text(metric.get("name"));
            if (name == null) {
                continue;
            }
            ObjectNode storedMetric = metricMap.putObject(name);
            storedMetric.put("name", name);
            JsonNode results = metric.get("results");
            JsonNode info = results == null ? null : results.get("aggregation_info");
            ObjectNode storedResults = null;
            if (info != null && !info.isNull()) {
                storedResults = storedMetric.putObject("results");
                storedResults.put("name", name);
                storedResults.set("aggregation_info", numbers(info));
                storedResults.put("percentile_results_available", false);
            }
            ObjectNode flat = intervalMetrics.putObject(name);
            flat.put("name", name);
            if (info != null && !info.isNull()) {
                flat.set("aggregation_info", numbers(info));
                flat.put("percentile_results_available", false);
            }
            JsonNode metadata = results == null ? null : results.get("metadata");
            if (metadata != null && !metadata.isNull()) {
                ObjectNode storedMetadata = metadata(metadata, container && isAccelerator(name));
                if (storedResults != null) {
                    storedResults.set("metadata", storedMetadata);
                }
                flat.set("metadata", storedMetadata.deepCopy());
            }
            if (container && accelerators != null && isAccelerator(name)) {
                if (metadata != null && metadata.hasNonNull("accelerator_model_name")) {
                    ObjectNode device = accelerators.putObject(name);
                    ObjectNode details = device.putObject("acceleratorDeviceData");
                    details.put("manufacturer", "NVIDIA");
                    details.put("modelName", metadata.get("accelerator_model_name").asText());
                    if (metadata.hasNonNull("node")) {
                        details.put("hostName", metadata.get("node").asText());
                    }
                    boolean partitioned = metadata.hasNonNull("accelerator_profile_name");
                    details.put("isPartition", partitioned);
                    details.put("isPartitionSupported", RecommendationUtils.checkIfModelIsKruizeSupportedMIG(metadata.get("accelerator_model_name").asText()));
                    if (results.get("aggregation_info") != null) {
                        device.set("metricResults", flat.deepCopy());
                    }
                }
            }
        }
    }

    private static ObjectNode metadata(JsonNode source, boolean accelerator) {
        ObjectNode copy = HotJson.mapper().createObjectNode();
        if (accelerator) {
            copy.put("type", "accelerator");
        } else if (source.hasNonNull("type")) {
            copy.set("type", source.get("type").deepCopy());
        }
        source.fields().forEachRemaining(field -> {
            if (!"type".equals(field.getKey()) && !field.getValue().isNull()) {
                copy.set(field.getKey(), field.getValue().deepCopy());
            }
        });
        return copy;
    }

    private static JsonNode numbers(JsonNode info) {
        ObjectNode copy = HotJson.mapper().createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = info.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isNumber() && !"count".equals(field.getKey())) {
                copy.put(field.getKey(), field.getValue().doubleValue());
            } else {
                copy.set(field.getKey(), field.getValue().deepCopy());
            }
        }
        return copy;
    }

    private static void defaultRecommendations(ObjectNode stored) {
        ObjectNode recommendations = stored.putObject("recommendations");
        recommendations.put("version", "1.0");
        ObjectNode note = recommendations.putObject("notifications").putObject("120001");
        note.put("type", "info");
        note.put("message", "There is not enough data available to generate a recommendation.");
        note.put("code", 120001);
    }

    private static boolean isAccelerator(String name) {
        return AnalyzerConstants.MetricName.acceleratorCoreUsage.name().equalsIgnoreCase(name)
                || AnalyzerConstants.MetricName.acceleratorMemoryUsage.name().equalsIgnoreCase(name)
                || AnalyzerConstants.MetricName.acceleratorFrameBufferUsage.name().equalsIgnoreCase(name);
    }

    private static void putText(ObjectNode node, String field, JsonNode value) {
        if (value != null && value.isTextual()) {
            node.put(field, value.asText());
        }
    }

    static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
