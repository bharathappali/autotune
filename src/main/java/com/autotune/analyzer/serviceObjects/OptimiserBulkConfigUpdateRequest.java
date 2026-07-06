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
package com.autotune.analyzer.serviceObjects;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Request object for updating an optimiser bulk config.
 * All fields are optional - only provided fields will be updated.
 */
public class OptimiserBulkConfigUpdateRequest {
    @JsonProperty("cluster_name")
    private String clusterName;

    private List<String> datasources;

    private List<String> namespaces;

    private Map<String, String> labels;

    @JsonProperty("experiment_types")
    private List<String> experimentTypes;

    @JsonProperty("metadata_profile")
    private String metadataProfile;

    @JsonProperty("performanceProfile")
    private String performanceProfile;

    @JsonProperty("trial_settings")
    private OptimiserBulkConfig.TrialSettings trialSettings;

    @JsonProperty("recommendation_settings")
    private OptimiserBulkConfig.RecommendationSettings recommendationSettings;

    @JsonProperty("webhook_url")
    private String webhookUrl;

    private Boolean enabled;

    public OptimiserBulkConfigUpdateRequest() {
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

    public String getPerformanceProfile() {
        return performanceProfile;
    }

    public void setPerformanceProfile(String performanceProfile) {
        this.performanceProfile = performanceProfile;
    }

    public OptimiserBulkConfig.TrialSettings getTrialSettings() {
        return trialSettings;
    }

    public void setTrialSettings(OptimiserBulkConfig.TrialSettings trialSettings) {
        this.trialSettings = trialSettings;
    }

    public OptimiserBulkConfig.RecommendationSettings getRecommendationSettings() {
        return recommendationSettings;
    }

    public void setRecommendationSettings(OptimiserBulkConfig.RecommendationSettings recommendationSettings) {
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
}

// Made with Bob