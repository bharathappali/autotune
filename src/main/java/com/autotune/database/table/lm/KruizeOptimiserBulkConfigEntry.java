/*******************************************************************************
 * Copyright (c) 2026 Red Hat, IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.autotune.database.table.lm;

import com.autotune.analyzer.serviceObjects.OptimiserBulkConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

/**
 * Database entity to store Kruize optimiser bulk configuration.
 * Aligned with CreateExperiment structure for consistency.
 */
@Entity
@Table(name = "kruize_optimiser_bulk_config")
public class KruizeOptimiserBulkConfigEntry {
    private static final Logger LOGGER = LoggerFactory.getLogger(KruizeOptimiserBulkConfigEntry.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @Column(name = "config_name", columnDefinition = "VARCHAR(255)")
    private String configName;

    @Column(name = "cluster_name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String clusterName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datasources", columnDefinition = "jsonb", nullable = false)
    private JsonNode datasources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "namespaces", columnDefinition = "jsonb", nullable = false)
    private JsonNode namespaces;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    private JsonNode labels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "experiment_types", columnDefinition = "jsonb", nullable = false)
    private JsonNode experimentTypes;

    @Column(name = "metadata_profile", columnDefinition = "VARCHAR(255)")
    private String metadataProfile;

    @Column(name = "performance_profile", columnDefinition = "VARCHAR(255)", nullable = false)
    private String performanceProfile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trial_settings", columnDefinition = "jsonb")
    private JsonNode trialSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendation_settings", columnDefinition = "jsonb", nullable = false)
    private JsonNode recommendationSettings;

    @Column(name = "webhook_url", columnDefinition = "VARCHAR(500)")
    private String webhookUrl;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    // Default constructor
    public KruizeOptimiserBulkConfigEntry() {
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public JsonNode getDatasources() {
        return datasources;
    }

    public void setDatasources(JsonNode datasources) {
        this.datasources = datasources;
    }

    public JsonNode getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(JsonNode namespaces) {
        this.namespaces = namespaces;
    }

    public JsonNode getLabels() {
        return labels;
    }

    public void setLabels(JsonNode labels) {
        this.labels = labels;
    }

    public JsonNode getExperimentTypes() {
        return experimentTypes;
    }

    public void setExperimentTypes(JsonNode experimentTypes) {
        this.experimentTypes = experimentTypes;
    }

    public String getMetadataProfile() {
        return metadataProfile;
    }

    public void setMetadataProfile(String metadataProfile) {
        this.metadataProfile = metadataProfile;
    }

    public String getPerformanceProfile() {
        return performanceProfile;
    }

    public void setPerformanceProfile(String performanceProfile) {
        this.performanceProfile = performanceProfile;
    }

    public JsonNode getTrialSettings() {
        return trialSettings;
    }

    public void setTrialSettings(JsonNode trialSettings) {
        this.trialSettings = trialSettings;
    }

    public JsonNode getRecommendationSettings() {
        return recommendationSettings;
    }

    public void setRecommendationSettings(JsonNode recommendationSettings) {
        this.recommendationSettings = recommendationSettings;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Convert database entity to service object
     */
    public OptimiserBulkConfig toOptimiserBulkConfig() {
        try {
            OptimiserBulkConfig config = new OptimiserBulkConfig();
            config.setConfigName(this.configName);
            config.setClusterName(this.clusterName);

            // Convert JsonNode arrays to List<String>
            if (this.datasources != null) {
                config.setDatasources(objectMapper.convertValue(
                        this.datasources,
                        objectMapper.getTypeFactory().constructCollectionType(
                                java.util.List.class, String.class
                        )
                ));
            }

            if (this.namespaces != null) {
                config.setNamespaces(objectMapper.convertValue(
                        this.namespaces,
                        objectMapper.getTypeFactory().constructCollectionType(
                                java.util.List.class, String.class
                        )
                ));
            }

            if (this.labels != null) {
                config.setLabels(objectMapper.convertValue(
                        this.labels,
                        objectMapper.getTypeFactory().constructMapType(
                                java.util.Map.class, String.class, String.class
                        )
                ));
            }

            if (this.experimentTypes != null) {
                config.setExperimentTypes(objectMapper.convertValue(
                        this.experimentTypes,
                        objectMapper.getTypeFactory().constructCollectionType(
                                java.util.List.class, String.class
                        )
                ));
            }

            config.setMetadataProfile(this.metadataProfile);
            config.setPerformanceProfile(this.performanceProfile);

            // Convert JsonNode to TrialSettings
            if (this.trialSettings != null) {
                config.setTrialSettings(objectMapper.convertValue(
                        this.trialSettings,
                        OptimiserBulkConfig.TrialSettings.class
                ));
            }

            // Convert JsonNode to RecommendationSettings
            if (this.recommendationSettings != null) {
                config.setRecommendationSettings(objectMapper.convertValue(
                        this.recommendationSettings,
                        OptimiserBulkConfig.RecommendationSettings.class
                ));
            }

            config.setWebhookUrl(this.webhookUrl);
            config.setEnabled(this.enabled);

            if (this.createdAt != null) {
                config.setCreatedAt(this.createdAt.toInstant());
            }
            if (this.updatedAt != null) {
                config.setUpdatedAt(this.updatedAt.toInstant());
            }

            return config;
        } catch (Exception e) {
            LOGGER.error("Error converting KruizeOptimiserBulkConfigEntry to OptimiserBulkConfig: {}", e.getMessage());
            throw new RuntimeException("Failed to convert entity to service object", e);
        }
    }

    /**
     * Create database entity from service object
     */
    public static KruizeOptimiserBulkConfigEntry fromOptimiserBulkConfig(OptimiserBulkConfig config) {
        try {
            KruizeOptimiserBulkConfigEntry entry = new KruizeOptimiserBulkConfigEntry();
            entry.setConfigName(config.getConfigName());
            entry.setClusterName(config.getClusterName());

            // Convert List<String> to JsonNode
            if (config.getDatasources() != null) {
                entry.setDatasources(objectMapper.valueToTree(config.getDatasources()));
            }

            if (config.getNamespaces() != null) {
                entry.setNamespaces(objectMapper.valueToTree(config.getNamespaces()));
            }

            if (config.getLabels() != null) {
                entry.setLabels(objectMapper.valueToTree(config.getLabels()));
            }

            if (config.getExperimentTypes() != null) {
                entry.setExperimentTypes(objectMapper.valueToTree(config.getExperimentTypes()));
            }

            entry.setMetadataProfile(config.getMetadataProfile());
            entry.setPerformanceProfile(config.getPerformanceProfile());

            // Convert TrialSettings to JsonNode
            if (config.getTrialSettings() != null) {
                entry.setTrialSettings(objectMapper.valueToTree(config.getTrialSettings()));
            }

            // Convert RecommendationSettings to JsonNode
            if (config.getRecommendationSettings() != null) {
                entry.setRecommendationSettings(objectMapper.valueToTree(config.getRecommendationSettings()));
            }

            entry.setWebhookUrl(config.getWebhookUrl());
            entry.setEnabled(config.getEnabled() != null ? config.getEnabled() : true);

            if (config.getCreatedAt() != null) {
                entry.setCreatedAt(Timestamp.from(config.getCreatedAt()));
            }
            if (config.getUpdatedAt() != null) {
                entry.setUpdatedAt(Timestamp.from(config.getUpdatedAt()));
            }

            return entry;
        } catch (Exception e) {
            LOGGER.error("Error converting OptimiserBulkConfig to KruizeOptimiserBulkConfigEntry: {}", e.getMessage());
            throw new RuntimeException("Failed to convert service object to entity", e);
        }
    }

    @Override
    public String toString() {
        return "KruizeOptimiserBulkConfigEntry{" +
                "configName='" + configName + '\'' +
                ", clusterName='" + clusterName + '\'' +
                ", datasources=" + datasources +
                ", namespaces=" + namespaces +
                ", labels=" + labels +
                ", experimentTypes=" + experimentTypes +
                ", metadataProfile='" + metadataProfile + '\'' +
                ", performanceProfile='" + performanceProfile + '\'' +
                ", trialSettings=" + trialSettings +
                ", recommendationSettings=" + recommendationSettings +
                ", webhookUrl='" + webhookUrl + '\'' +
                ", enabled=" + enabled +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

// Made with Bob