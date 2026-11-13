package com.autotune.http;

import com.autotune.analyzer.adapters.DeviceDetailsAdapter;
import com.autotune.analyzer.adapters.MetricMetadataAdapter;
import com.autotune.analyzer.adapters.RecommendationItemAdapter;
import com.autotune.analyzer.kruizeObject.KruizeObject;
import com.autotune.analyzer.recommendations.RecommendationConfigItem;
import com.autotune.analyzer.serviceObjects.Converters;
import com.autotune.analyzer.serviceObjects.UpdateResultsAPIObject;
import com.autotune.analyzer.services.UpdateResults;
import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.common.data.metrics.MetricAggregationInfoResults;
import com.autotune.common.data.result.ExperimentResultData;
import com.autotune.database.helper.DBHelpers;
import com.autotune.database.table.KruizeResultsEntry;
import com.autotune.http.results.ResultsReshape;
import com.autotune.analyzer.utils.GsonUTCDateAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stored results jsonb and recommendation response keys against the converters the readers already use.
 */
class HotPathJsonTest {
    @Test
    void storedResultsMatchTheExistingConverter() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        compare("hotpath/results.json");
        compare("hotpath/update_results_accelerator.json");
        compare("hotpath/mixed_bulk_results_namespace.json");
    }

    @Test
    void recommendationResponseUsesIntegerAggregationsAndResourceNames() throws Exception {
        MetricAggregationInfoResults aggregation = new MetricAggregationInfoResults();
        aggregation.setAvg(1.9);
        aggregation.setSum(-2.2);
        aggregation.setMin(0.0);
        aggregation.setMax(3.8);
        aggregation.setCount(4);
        aggregation.setFormat("");
        String encoded = new String(HotJson.COMPACT_WRITER.writeValueAsBytes(aggregation));
        JsonNode node = HotJson.TREE_READER.readTree(encoded);
        assertEquals(1, node.get("avg").intValue());
        assertEquals(-2, node.get("sum").intValue());
        assertEquals(0, node.get("min").intValue());
        assertEquals(3, node.get("max").intValue());
        assertEquals(4, node.get("count").intValue());
        assertFalse(node.has("format"));

        Map<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> requests = new EnumMap<>(AnalyzerConstants.RecommendationItem.class);
        requests.put(AnalyzerConstants.RecommendationItem.CPU, new RecommendationConfigItem(250.0, "m"));
        requests.put(AnalyzerConstants.RecommendationItem.NVIDIA_GPU_PARTITION_1_CORE_5GB, new RecommendationConfigItem(1.0, "cores"));
        JsonNode keys = HotJson.TREE_READER.readTree(HotJson.COMPACT_WRITER.writeValueAsBytes(requests));
        assertTrue(keys.has("cpu"));
        assertTrue(keys.has("nvidia.com/mig-1g.5gb"));
        assertFalse(keys.has("CPU"));
        assertFalse(keys.has("NVIDIA_GPU_PARTITION_1_CORE_5GB"));

        Map<AnalyzerConstants.ResourceSetting, Map<AnalyzerConstants.RecommendationItem, RecommendationConfigItem>> resources =
                new EnumMap<>(AnalyzerConstants.ResourceSetting.class);
        resources.put(AnalyzerConstants.ResourceSetting.requests, requests);
        JsonNode nested = HotJson.TREE_READER.readTree(HotJson.COMPACT_WRITER.writeValueAsBytes(resources));
        assertTrue(nested.has("requests"));
    }

    private static void compare(String path) throws Exception {
        try (var input = HotPathJsonTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture " + path);
            }
            JsonNode tree = HotJson.TREE_READER.readTree(input);
        JsonNode record = tree.isArray() ? tree.get(0) : tree;
        Gson gson = new GsonBuilder()
                .disableHtmlEscaping()
                .registerTypeAdapter(Double.class, new UpdateResults.CustomNumberDeserializer())
                .registerTypeAdapter(Integer.class, new UpdateResults.CustomNumberDeserializer())
                .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                .registerTypeAdapter(AnalyzerConstants.RecommendationItem.class, new RecommendationItemAdapter())
                .registerTypeAdapter(com.autotune.common.data.system.info.device.DeviceDetails.class, new DeviceDetailsAdapter())
                .registerTypeAdapter(com.autotune.common.data.metrics.MetricMetadata.class, new MetricMetadataAdapter())
                .create();
        UpdateResultsAPIObject api = gson.fromJson(record.toString(), UpdateResultsAPIObject.class);
        KruizeObject experiment = new KruizeObject();
        experiment.setClusterName(null);
        api.setKruizeObject(experiment);
        ExperimentResultData data = Converters.KruizeObjectConverters.convertUpdateResultsAPIObjToExperimentResultData(api);
        KruizeResultsEntry entry = DBHelpers.Converters.KruizeObjectConverters.convertExperimentResultToExperimentResultsTable(data);
        JsonNode expected = prune(entry.getExtended_data().deepCopy());
        Instant start = ResultsCheckTime.parse(record.get("interval_start_time").asText());
        Instant end = ResultsCheckTime.parse(record.get("interval_end_time").asText());
        JsonNode actual = prune(HotJson.TREE_READER.readTree(ResultsReshape.extendedData(record, start, end)));
        assertEquals(expected, actual, path);
        }
    }

    private static JsonNode prune(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = HotJson.mapper().createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode child = prune(field.getValue());
                if (child == null || child.isNull()) {
                    continue;
                }
                if (child.isObject() && child.isEmpty()) {
                    continue;
                }
                if (child.isArray() && child.isEmpty()) {
                    continue;
                }
                copy.set(field.getKey(), child);
            }
            return copy;
        }
        if (node.isArray()) {
            var copy = HotJson.mapper().createArrayNode();
            node.forEach(child -> {
                JsonNode pruned = prune(child);
                if (pruned != null && !pruned.isNull()) {
                    copy.add(pruned);
                }
            });
            return copy;
        }
        return node;
    }

    private static final class ResultsCheckTime {
        static Instant parse(String text) {
            return com.autotune.http.results.ResultsCheck.parse(text);
        }
    }
}
