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
package com.autotune.common.bulk;

import com.autotune.analyzer.serviceObjects.BulkProfile;
import com.autotune.analyzer.serviceObjects.BulkProfileUpdateRequest;
import com.autotune.common.data.ValidationOutputData;
import com.autotune.common.datasource.DataSourceInfo;
import com.autotune.common.datasource.DataSourceOperatorImpl;
import com.autotune.common.utils.CommonUtils;
import com.autotune.database.service.ExperimentDBService;
import com.autotune.utils.KruizeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation utility for Bulk Profile API requests
 */
public class BulkProfileValidation {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BulkProfileValidation.class);
    
    // Valid values for recommendation settings
    private static final Set<String> VALID_TERMS = new HashSet<>(Arrays.asList(
        "short_term", "medium_term", "long_term"
    ));
    
    private static final Set<String> VALID_MODELS = new HashSet<>(Arrays.asList(
        "performance", "cost"
    ));
    
    private static final Set<String> VALID_EXPERIMENT_TYPES = new HashSet<>(Arrays.asList(
        "container", "namespace"
    ));
    
    // Regex pattern for scheduling format: number + unit (e.g., "24h", "30min", "7days")
    // Supports: h, hr, hrs, hour, hours, m, min, mins, minute, minutes, d, day, days
    private static final String SCHEDULING_PATTERN = "^\\d+\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|d|day|days)$";

    /**
     * Validate a bulk profile for creation
     * @param bulkProfile The bulk profile to validate
     * @return ValidationOutputData with success status and error details if any
     */
    public static ValidationOutputData validateCreate(BulkProfile bulkProfile) {
        // Check required fields
        if (bulkProfile.getProfileName() == null || bulkProfile.getProfileName().trim().isEmpty()) {
            return new ValidationOutputData(false, "profile_name is required", HttpServletResponse.SC_BAD_REQUEST);
        }
        
        if (bulkProfile.getClusters() == null || bulkProfile.getClusters().isEmpty()) {
            return new ValidationOutputData(false, "At least one cluster is required", HttpServletResponse.SC_BAD_REQUEST);
        }
        
        if (bulkProfile.getRecommendationSettings() == null) {
            return new ValidationOutputData(false, "recommendation_settings is required", HttpServletResponse.SC_BAD_REQUEST);
        }
        
        // Validate profile name format (alphanumeric, hyphens, underscores)
        if (!bulkProfile.getProfileName().matches("^[a-zA-Z0-9_-]+$")) {
            return new ValidationOutputData(false, 
                "profile_name must contain only alphanumeric characters, hyphens, and underscores", 
                HttpServletResponse.SC_BAD_REQUEST);
        }
        
        // Validate clusters
        ValidationOutputData clusterValidation = validateClusters(bulkProfile.getClusters());
        if (!clusterValidation.isSuccess()) {
            return clusterValidation;
        }
        
        // Validate recommendation settings
        ValidationOutputData settingsValidation = validateRecommendationSettings(bulkProfile.getRecommendationSettings());
        if (!settingsValidation.isSuccess()) {
            return settingsValidation;
        }
        
        // Validate webhook URL if provided
        if (bulkProfile.getWebhookUrl() != null && !bulkProfile.getWebhookUrl().trim().isEmpty()) {
            ValidationOutputData webhookValidation = validateWebhookUrl(bulkProfile.getWebhookUrl());
            if (!webhookValidation.isSuccess()) {
                return webhookValidation;
            }
        }
        
        return new ValidationOutputData(true, null, HttpServletResponse.SC_OK);
    }

    /**
     * Validate a bulk profile update request
     * @param updateRequest The update request to validate
     * @return ValidationOutputData with success status and error details if any
     */
    public static ValidationOutputData validateUpdate(BulkProfileUpdateRequest updateRequest) {
        // Check if at least one field is provided for update
        if (!updateRequest.hasUpdates()) {
            return new ValidationOutputData(false, 
                "At least one field must be provided for update", 
                HttpServletResponse.SC_BAD_REQUEST);
        }
        
        // Validate clusters if provided
        if (updateRequest.getClusters() != null) {
            if (updateRequest.getClusters().isEmpty()) {
                return new ValidationOutputData(false, 
                    "clusters cannot be empty if provided", 
                    HttpServletResponse.SC_BAD_REQUEST);
            }
            ValidationOutputData clusterValidation = validateClusters(updateRequest.getClusters());
            if (!clusterValidation.isSuccess()) {
                return clusterValidation;
            }
        }
        
        // Validate recommendation settings if provided
        if (updateRequest.getRecommendationSettings() != null) {
            ValidationOutputData settingsValidation = validateRecommendationSettings(updateRequest.getRecommendationSettings());
            if (!settingsValidation.isSuccess()) {
                return settingsValidation;
            }
        }
        
        // Validate webhook URL if provided
        if (updateRequest.getWebhookUrl() != null && !updateRequest.getWebhookUrl().trim().isEmpty()) {
            ValidationOutputData webhookValidation = validateWebhookUrl(updateRequest.getWebhookUrl());
            if (!webhookValidation.isSuccess()) {
                return webhookValidation;
            }
        }
        
        return new ValidationOutputData(true, null, HttpServletResponse.SC_OK);
    }

    /**
     * Validate cluster configurations
     */
    private static ValidationOutputData validateClusters(List<BulkProfile.Cluster> clusters) {
        for (BulkProfile.Cluster cluster : clusters) {
            // Validate cluster name
            if (cluster.getClusterName() == null || cluster.getClusterName().trim().isEmpty()) {
                return new ValidationOutputData(false, 
                    "cluster_name is required for each cluster", 
                    HttpServletResponse.SC_BAD_REQUEST);
            }
            
            // Validate datasources
            if (cluster.getDatasources() == null || cluster.getDatasources().isEmpty()) {
                return new ValidationOutputData(false,
                    "At least one datasource is required for cluster: " + cluster.getClusterName(),
                    HttpServletResponse.SC_BAD_REQUEST);
            }
            
            // Validate each datasource connection
            for (String datasourceName : cluster.getDatasources()) {
                String errorMessage = validateDatasourceConnection(datasourceName);
                if (!errorMessage.isEmpty()) {
                    return new ValidationOutputData(false, errorMessage, HttpServletResponse.SC_BAD_REQUEST);
                }
            }
            
            // Validate namespaces if provided
            if (cluster.getNamespaces() != null) {
                for (String namespace : cluster.getNamespaces()) {
                    if (namespace == null || namespace.trim().isEmpty()) {
                        return new ValidationOutputData(false,
                            "Empty namespace not allowed in cluster: " + cluster.getClusterName(),
                            HttpServletResponse.SC_BAD_REQUEST);
                    }
                }
            }
            
            // Validate labels if provided
            if (cluster.getLabels() != null) {
                for (String key : cluster.getLabels().keySet()) {
                    if (key == null || key.trim().isEmpty()) {
                        return new ValidationOutputData(false,
                            "Empty label key not allowed in cluster: " + cluster.getClusterName(),
                            HttpServletResponse.SC_BAD_REQUEST);
                    }
                }
            }
            
            // Validate experiment_types
            if (cluster.getExperimentTypes() == null || cluster.getExperimentTypes().isEmpty()) {
                return new ValidationOutputData(false,
                    "At least one experiment_type is required for cluster: " + cluster.getClusterName(),
                    HttpServletResponse.SC_BAD_REQUEST);
            }
            
            for (String expType : cluster.getExperimentTypes()) {
                if (!VALID_EXPERIMENT_TYPES.contains(expType)) {
                    return new ValidationOutputData(false,
                        "Invalid experiment_type: " + expType + " in cluster: " + cluster.getClusterName() +
                        ". Valid values are: " + VALID_EXPERIMENT_TYPES,
                        HttpServletResponse.SC_BAD_REQUEST);
                }
            }
            
            // Validate metadata_profile
            if (cluster.getMetadataProfile() == null || cluster.getMetadataProfile().trim().isEmpty()) {
                return new ValidationOutputData(false,
                    "metadata_profile is required for cluster: " + cluster.getClusterName(),
                    HttpServletResponse.SC_BAD_REQUEST);
            }
        }
        
        return new ValidationOutputData(true, null, HttpServletResponse.SC_OK);
    }

    /**
     * Validate recommendation settings
     */
    private static ValidationOutputData validateRecommendationSettings(BulkProfile.RecommendationSettings settings) {
        // Validate scheduling
        if (settings.getScheduling() == null) {
            return new ValidationOutputData(false, 
                "scheduling is required in recommendation_settings", 
                HttpServletResponse.SC_BAD_REQUEST);
        }
        
        ValidationOutputData schedulingValidation = validateScheduling(settings.getScheduling());
        if (!schedulingValidation.isSuccess()) {
            return schedulingValidation;
        }
        
        // Validate terms
        if (settings.getTerms() == null || settings.getTerms().isEmpty()) {
            return new ValidationOutputData(false, 
                "At least one term is required in recommendation_settings", 
                HttpServletResponse.SC_BAD_REQUEST);
        }
        
        for (String term : settings.getTerms()) {
            if (!VALID_TERMS.contains(term)) {
                return new ValidationOutputData(false, 
                    "Invalid term: " + term + ". Valid values are: " + VALID_TERMS, 
                    HttpServletResponse.SC_BAD_REQUEST);
            }
        }
        
        // Validate models
        if (settings.getModels() == null || settings.getModels().isEmpty()) {
            return new ValidationOutputData(false, 
                "At least one model is required in recommendation_settings", 
                HttpServletResponse.SC_BAD_REQUEST);
        }
        
        for (String model : settings.getModels()) {
            if (!VALID_MODELS.contains(model)) {
                return new ValidationOutputData(false, 
                    "Invalid model: " + model + ". Valid values are: " + VALID_MODELS, 
                    HttpServletResponse.SC_BAD_REQUEST);
            }
        }
        
        return new ValidationOutputData(true, null, HttpServletResponse.SC_OK);
    }

    /**
     * Validate scheduling configuration
     * Accepts formats like: "24h", "30min", "7days", "1hr", etc.
     */
    private static ValidationOutputData validateScheduling(String scheduling) {
        if (scheduling == null || scheduling.trim().isEmpty()) {
            return new ValidationOutputData(false,
                "scheduling is required",
                HttpServletResponse.SC_BAD_REQUEST);
        }
        
        if (!scheduling.matches(SCHEDULING_PATTERN)) {
            return new ValidationOutputData(false,
                "Invalid scheduling format: " + scheduling +
                ". Expected format: number + unit (e.g., '24h', '30min', '7days'). " +
                "Valid units: h/hr/hrs/hour/hours, m/min/mins/minute/minutes, d/day/days",
                HttpServletResponse.SC_BAD_REQUEST);
        }
        
        return new ValidationOutputData(true, null, HttpServletResponse.SC_OK);
    }

    /**
     * Validate webhook URL format
     */
    private static ValidationOutputData validateWebhookUrl(String webhookUrl) {
        try {
            URL url = new URL(webhookUrl);
            String protocol = url.getProtocol();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                return new ValidationOutputData(false, 
                    "webhook_url must use http or https protocol", 
                    HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (MalformedURLException e) {
            return new ValidationOutputData(false, 
                "Invalid webhook_url format: " + e.getMessage(), 
                HttpServletResponse.SC_BAD_REQUEST);
        }
        
        return new ValidationOutputData(true, null, HttpServletResponse.SC_OK);
    }

    /**
     * Validate datasource connection and reachability
     * This method follows the same pattern as BulkServiceValidation.validateDatasourceConnection()
     *
     * @param datasourceName The name of the datasource to validate
     * @return Empty string if valid, error message otherwise
     */
    public static String validateDatasourceConnection(String datasourceName) {
        String errorMessage = "";
        try {
            DataSourceInfo dataSourceInfo = null;
            try {
                dataSourceInfo = new ExperimentDBService().loadDataSourceFromDBByName(datasourceName);
            } catch (Exception e) {
                errorMessage = String.format(KruizeConstants.DataSourceConstants.DataSourceMetadataErrorMsgs.LOAD_DATASOURCE_FROM_DB_ERROR, datasourceName, e.getMessage());
                LOGGER.error(errorMessage);
                return errorMessage;
            }
            LOGGER.info(KruizeConstants.DataSourceConstants.DataSourceInfoMsgs.VERIFYING_DATASOURCE_REACHABILITY, datasourceName);
            DataSourceOperatorImpl op = DataSourceOperatorImpl.getInstance().getOperator(KruizeConstants.SupportedDatasources.PROMETHEUS);
            if (dataSourceInfo == null || op.isServiceable(dataSourceInfo) == CommonUtils.DatasourceReachabilityStatus.NOT_REACHABLE) {
                errorMessage = KruizeConstants.DataSourceConstants.DataSourceErrorMsgs.DATASOURCE_NOT_SERVICEABLE;
                LOGGER.error(errorMessage);
            }
        } catch (Exception ex) {
            errorMessage = ex.getMessage();
            LOGGER.error(errorMessage);
        }
        return errorMessage;
    }
}

