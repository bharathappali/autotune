package com.autotune.analyzer.serviceObjects;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class BulkProfileUpdateRequest {
    private String description;

    private List<BulkProfile.Cluster> clusters;

    @JsonProperty("recommendation_settings")
    private BulkProfile.RecommendationSettings recommendationSettings;

    private Boolean enabled;

    @JsonProperty("webhook_url")
    private String webhookUrl;

    public BulkProfileUpdateRequest() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<BulkProfile.Cluster> getClusters() {
        return clusters;
    }

    public void setClusters(List<BulkProfile.Cluster> clusters) {
        this.clusters = clusters;
    }

    public BulkProfile.RecommendationSettings getRecommendationSettings() {
        return recommendationSettings;
    }

    public void setRecommendationSettings(BulkProfile.RecommendationSettings recommendationSettings) {
        this.recommendationSettings = recommendationSettings;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Check if any field is set for update
     * @return true if at least one field is set
     */
    public boolean hasUpdates() {
        return description != null ||
                clusters != null ||
                recommendationSettings != null ||
                enabled != null ||
                webhookUrl != null;
    }
}
