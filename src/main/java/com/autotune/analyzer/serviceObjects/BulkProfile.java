/*******************************************************************************
 * Copyright (c) 2024 Red Hat, IBM Corporation and others.
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
package com.autotune.analyzer.serviceObjects;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulk Profile API Object - represents a reusable configuration for automated recommendation generation
 */
public class BulkProfile {
    
    @JsonProperty("profile_name")
    private String profileName;
    
    private String description;
    
    private List<Cluster> clusters = new ArrayList<>();
    
    @JsonProperty("recommendation_settings")
    private RecommendationSettings recommendationSettings;
    
    private boolean enabled = true;
    
    @JsonProperty("webhook_url")
    private String webhookUrl;

    public BulkProfile() {
    }

    public BulkProfile(String profileName, String description, List<Cluster> clusters, 
                       RecommendationSettings recommendationSettings, boolean enabled, String webhookUrl) {
        this.profileName = profileName;
        this.description = description;
        this.clusters = clusters;
        this.recommendationSettings = recommendationSettings;
        this.enabled = enabled;
        this.webhookUrl = webhookUrl;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Cluster> getClusters() {
        return clusters;
    }

    public void setClusters(List<Cluster> clusters) {
        this.clusters = clusters;
    }

    public RecommendationSettings getRecommendationSettings() {
        return recommendationSettings;
    }

    public void setRecommendationSettings(RecommendationSettings recommendationSettings) {
        this.recommendationSettings = recommendationSettings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Cluster configuration within a bulk profile
     */
    public static class Cluster {
        
        @JsonProperty("cluster_name")
        private String clusterName;
        
        private List<String> datasources = new ArrayList<>();
        
        private List<String> namespaces = new ArrayList<>();
        
        private Map<String, String> labels = new HashMap<>();
        
        @JsonProperty("experiment_types")
        private List<String> experimentTypes = new ArrayList<>();
        
        @JsonProperty("metadata_profile")
        private String metadataProfile;

        public Cluster() {
        }

        public Cluster(String clusterName, List<String> datasources, List<String> namespaces,
                       Map<String, String> labels, List<String> experimentTypes, String metadataProfile) {
            this.clusterName = clusterName;
            this.datasources = datasources;
            this.namespaces = namespaces;
            this.labels = labels;
            this.experimentTypes = experimentTypes;
            this.metadataProfile = metadataProfile;
        }

        public String getClusterName() {
            return clusterName;
        }

        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }

        public List<String> getDatasources() {
            return datasources;
        }

        public void setDatasources(List<String> datasources) {
            this.datasources = datasources;
        }

        public List<String> getNamespaces() {
            return namespaces;
        }

        public void setNamespaces(List<String> namespaces) {
            this.namespaces = namespaces;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }

        public List<String> getExperimentTypes() {
            return experimentTypes;
        }

        public void setExperimentTypes(List<String> experimentTypes) {
            this.experimentTypes = experimentTypes;
        }

        public String getMetadataProfile() {
            return metadataProfile;
        }

        public void setMetadataProfile(String metadataProfile) {
            this.metadataProfile = metadataProfile;
        }
    }

    /**
     * Recommendation settings for the bulk profile
     */
    public static class RecommendationSettings {
        
        private Scheduling scheduling;
        
        private List<String> terms = new ArrayList<>();
        
        private List<String> models = new ArrayList<>();
        
        @JsonProperty("measurement_duration")
        private String measurementDuration;

        public RecommendationSettings() {
        }

        public RecommendationSettings(Scheduling scheduling, List<String> terms, List<String> models, String measurementDuration) {
            this.scheduling = scheduling;
            this.terms = terms;
            this.models = models;
            this.measurementDuration = measurementDuration;
        }

        public Scheduling getScheduling() {
            return scheduling;
        }

        public void setScheduling(Scheduling scheduling) {
            this.scheduling = scheduling;
        }

        public List<String> getTerms() {
            return terms;
        }

        public void setTerms(List<String> terms) {
            this.terms = terms;
        }

        public List<String> getModels() {
            return models;
        }

        public void setModels(List<String> models) {
            this.models = models;
        }

        public String getMeasurementDuration() {
            return measurementDuration;
        }

        public void setMeasurementDuration(String measurementDuration) {
            this.measurementDuration = measurementDuration;
        }
    }

    /**
     * Scheduling configuration for automated recommendation generation
     */
    public static class Scheduling {
        
        private int value;
        
        private String unit;

        public Scheduling() {
        }

        public Scheduling(int value, String unit) {
            this.value = value;
            this.unit = unit;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }
    }
}
