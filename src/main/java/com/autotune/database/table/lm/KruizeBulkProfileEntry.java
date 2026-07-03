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

import com.autotune.analyzer.serviceObjects.BulkProfile;
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
 * Database entity to store Kruize bulk profile configurations.
 * Aligned with CreateExperiment structure for consistency.
 */
@Entity
@Table(name = "kruize_bulk_profile")
public class KruizeBulkProfileEntry {
    private static final Logger LOGGER = LoggerFactory.getLogger(KruizeBulkProfileEntry.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @Column(name = "profile_name", columnDefinition = "VARCHAR(255)")
    private String profileName;

    @Column(name = "cluster_name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String clusterName;

    @Column(name = "datasources", columnDefinition = "text[]", nullable = false)
    private String[] datasources;

    @Column(name = "namespaces", columnDefinition = "text[]", nullable = false)
    private String[] namespaces;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    private JsonNode labels;

    @Column(name = "experiment_types", columnDefinition = "text[]", nullable = false)
    private String[] experimentTypes;

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
    public KruizeBulkProfileEntry() {
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String[] getDatasources() {
        return datasources;
    }

    public void setDatasources(String[] datasources) {
        this.datasources = datasources;
    }

    public String[] getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(String[] namespaces) {
        this.namespaces = namespaces;
    }

    public JsonNode getLabels() {
        return labels;
    }

    public void setLabels(JsonNode labels) {
        this.labels = labels;
    }

    public String[] getExperimentTypes() {
        return experimentTypes;
    }

    public void setExperimentTypes(String[] experimentTypes) {
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
    public BulkProfile toBulkProfile() {
        try {
            BulkProfile profile = new BulkProfile();
            profile.setProfileName(this.profileName);
            profile.setClusterName(this.clusterName);

            // Convert String[] arrays to List<String>
            if (this.datasources != null) {
                profile.setDatasources(java.util.Arrays.asList(this.datasources));
            }

            if (this.namespaces != null) {
                profile.setNamespaces(java.util.Arrays.asList(this.namespaces));
            }

            if (this.labels != null) {
                profile.setLabels(objectMapper.convertValue(
                        this.labels,
                        objectMapper.getTypeFactory().constructMapType(
                                java.util.Map.class, String.class, String.class
                        )
                ));
            }

            if (this.experimentTypes != null) {
                profile.setExperimentTypes(java.util.Arrays.asList(this.experimentTypes));
            }

            profile.setMetadataProfile(this.metadataProfile);
            profile.setPerformanceProfile(this.performanceProfile);

            // Convert JsonNode to TrialSettings
            if (this.trialSettings != null) {
                profile.setTrialSettings(objectMapper.convertValue(
                        this.trialSettings,
                        BulkProfile.TrialSettings.class
                ));
            }

            // Convert JsonNode to RecommendationSettings
            if (this.recommendationSettings != null) {
                profile.setRecommendationSettings(objectMapper.convertValue(
                        this.recommendationSettings,
                        BulkProfile.RecommendationSettings.class
                ));
            }

            profile.setWebhookUrl(this.webhookUrl);
            profile.setEnabled(this.enabled);

            if (this.createdAt != null) {
                profile.setCreatedAt(this.createdAt.toInstant());
            }
            if (this.updatedAt != null) {
                profile.setUpdatedAt(this.updatedAt.toInstant());
            }

            return profile;
        } catch (Exception e) {
            LOGGER.error("Error converting KruizeBulkProfileEntry to BulkProfile: {}", e.getMessage());
            throw new RuntimeException("Failed to convert entity to service object", e);
        }
    }

    /**
     * Create database entity from service object
     */
    public static KruizeBulkProfileEntry fromBulkProfile(BulkProfile profile) {
        try {
            KruizeBulkProfileEntry entry = new KruizeBulkProfileEntry();
            entry.setProfileName(profile.getProfileName());
            entry.setClusterName(profile.getClusterName());

            // Convert List<String> to String[]
            if (profile.getDatasources() != null) {
                entry.setDatasources(profile.getDatasources().toArray(new String[0]));
            }

            if (profile.getNamespaces() != null) {
                entry.setNamespaces(profile.getNamespaces().toArray(new String[0]));
            }

            if (profile.getLabels() != null) {
                entry.setLabels(objectMapper.valueToTree(profile.getLabels()));
            }

            if (profile.getExperimentTypes() != null) {
                entry.setExperimentTypes(profile.getExperimentTypes().toArray(new String[0]));
            }

            entry.setMetadataProfile(profile.getMetadataProfile());
            entry.setPerformanceProfile(profile.getPerformanceProfile());

            // Convert TrialSettings to JsonNode
            if (profile.getTrialSettings() != null) {
                entry.setTrialSettings(objectMapper.valueToTree(profile.getTrialSettings()));
            }

            // Convert RecommendationSettings to JsonNode
            if (profile.getRecommendationSettings() != null) {
                entry.setRecommendationSettings(objectMapper.valueToTree(profile.getRecommendationSettings()));
            }

            entry.setWebhookUrl(profile.getWebhookUrl());
            entry.setEnabled(profile.getEnabled() != null ? profile.getEnabled() : true);

            if (profile.getCreatedAt() != null) {
                entry.setCreatedAt(Timestamp.from(profile.getCreatedAt()));
            }
            if (profile.getUpdatedAt() != null) {
                entry.setUpdatedAt(Timestamp.from(profile.getUpdatedAt()));
            }

            return entry;
        } catch (Exception e) {
            LOGGER.error("Error converting BulkProfile to KruizeBulkProfileEntry: {}", e.getMessage());
            throw new RuntimeException("Failed to convert service object to entity", e);
        }
    }

    @Override
    public String toString() {
        return "KruizeBulkProfileEntry{" +
                "profileName='" + profileName + '\'' +
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
