package com.autotune.http.results;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-pod read-through cache. The database row is the source of truth.
 * A miss, or a newer update_date, reloads from kruize_experiments.
 */
public final class ExperimentCache {
    public record ExperimentMeta(String name, String version, String clusterName, String performanceProfile,
                                 long experimentType, OffsetDateTime updateDate, JsonNode extendedData,
                                 Set<String> profileMetrics, Set<String> profileFunctions, boolean profileHasQuery) {
    }

    private static final ConcurrentHashMap<String, ExperimentMeta> EXPERIMENTS = new ConcurrentHashMap<>();

    private ExperimentCache() {
    }

    public static ExperimentMeta get(String name) {
        return EXPERIMENTS.get(name);
    }

    public static void put(ExperimentMeta meta) {
        if (meta != null && meta.name() != null) {
            EXPERIMENTS.put(meta.name(), meta);
        }
    }

    public static void invalidateExperiment(String name) {
        if (name == null) {
            EXPERIMENTS.clear();
        } else {
            EXPERIMENTS.remove(name);
        }
    }

    public static void invalidateAll() {
        EXPERIMENTS.clear();
    }
}
