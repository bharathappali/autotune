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
package com.autotune.analyzer.services;

import com.autotune.analyzer.serviceObjects.BulkProfile;
import com.autotune.common.bulk.BulkProfileValidation;
import com.autotune.common.data.ValidationOutputData;
import com.autotune.database.dao.ExperimentDAO;
import com.autotune.database.dao.ExperimentDAOImpl;
import com.autotune.database.table.lm.KruizeBulkProfileEntry;
import com.autotune.analyzer.serviceObjects.BulkProfileUpdateRequest;
import com.autotune.utils.GenericRestApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API service for Bulk Profile management
 * Provides CRUD operations for bulk profiles and webhook notifications
 */
@WebServlet(asyncSupported = true)
public class BulkProfileService extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(BulkProfileService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private ExperimentDAO experimentDAO;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        experimentDAO = new ExperimentDAOImpl();
    }

    /**
     * GET /bulkProfile - List all bulk profiles or get a specific profile
     * Query parameters:
     * - profile_name: (optional) Get specific profile by name
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            String profileName = req.getParameter("profile_name");

            if (profileName != null && !profileName.trim().isEmpty()) {
                // Get specific profile
                handleGetProfile(profileName, resp, out);
            } else {
                // List all profiles
                handleListProfiles(resp, out);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing GET request: {}", e.getMessage(), e);
            sendErrorResponse(resp, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /bulkProfile - Create a new bulk profile
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            // Parse request body
            String requestBody = req.getReader().lines().collect(Collectors.joining());
            LOGGER.info("Received bulk profile creation request: {}", requestBody);

            BulkProfile bulkProfile = objectMapper.readValue(requestBody, BulkProfile.class);

            // Validate the profile
            ValidationOutputData validation = BulkProfileValidation.validateCreate(bulkProfile);
            if (!validation.isSuccess()) {
                sendErrorResponse(resp, out, validation.getErrorCode(), validation.getMessage());
                return;
            }

            // Check if profile already exists
            KruizeBulkProfileEntry existingProfile = experimentDAO.loadBulkProfileByName(bulkProfile.getProfileName());
            if (existingProfile != null) {
                sendErrorResponse(resp, out, HttpServletResponse.SC_CONFLICT,
                        "Bulk profile with name '" + bulkProfile.getProfileName() + "' already exists");
                return;
            }

            // Convert to database entity and save
            KruizeBulkProfileEntry profileEntry = KruizeBulkProfileEntry.fromBulkProfile(bulkProfile);
            LOGGER.info("Saving bulk profile '{}' to database", bulkProfile.getProfileName());

            ValidationOutputData saveResult = experimentDAO.addBulkProfileToDB(profileEntry);

            if (!saveResult.isSuccess()) {
                LOGGER.error("Failed to save bulk profile '{}' to database: {}",
                        bulkProfile.getProfileName(), saveResult.getMessage());
                sendErrorResponse(resp, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Failed to create bulk profile: " + saveResult.getMessage());
                return;
            }

            LOGGER.info("Successfully saved bulk profile '{}' to database", bulkProfile.getProfileName());

            // Return success response
            resp.setStatus(HttpServletResponse.SC_CREATED);
            out.write(objectMapper.writeValueAsString(bulkProfile));
            LOGGER.info("Bulk profile '{}' created successfully with scheduling: {}",
                    bulkProfile.getProfileName(),
                    bulkProfile.getRecommendationSettings().getScheduling());

        } catch (Exception e) {
            LOGGER.error("Error creating bulk profile: {}", e.getMessage(), e);
            sendErrorResponse(resp, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * PUT /bulkProfile?profile_name=<name> - Update an existing bulk profile
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            String profileName = req.getParameter("profile_name");
            if (profileName == null || profileName.trim().isEmpty()) {
                sendErrorResponse(resp, out, HttpServletResponse.SC_BAD_REQUEST,
                        "profile_name query parameter is required");
                return;
            }

            // Parse request body
            String requestBody = req.getReader().lines().collect(Collectors.joining());
            BulkProfileUpdateRequest updateRequest = objectMapper.readValue(requestBody, BulkProfileUpdateRequest.class);

            // Validate the update request
            ValidationOutputData validation = BulkProfileValidation.validateUpdate(updateRequest);
            if (!validation.isSuccess()) {
                sendErrorResponse(resp, out, validation.getErrorCode(), validation.getMessage());
                return;
            }

            // Load existing profile
            KruizeBulkProfileEntry existingEntry = experimentDAO.loadBulkProfileByName(profileName);
            if (existingEntry == null) {
                sendErrorResponse(resp, out, HttpServletResponse.SC_NOT_FOUND,
                        "Bulk profile not found: " + profileName);
                return;
            }

            // Convert existing entry to BulkProfile
            BulkProfile existingProfile = existingEntry.toBulkProfile();

            // Apply updates
            if (updateRequest.getClusterName() != null) {
                existingProfile.setClusterName(updateRequest.getClusterName());
            }
            if (updateRequest.getDatasources() != null) {
                existingProfile.setDatasources(updateRequest.getDatasources());
            }
            if (updateRequest.getNamespaces() != null) {
                existingProfile.setNamespaces(updateRequest.getNamespaces());
            }
            if (updateRequest.getLabels() != null) {
                existingProfile.setLabels(updateRequest.getLabels());
            }
            if (updateRequest.getExperimentTypes() != null) {
                existingProfile.setExperimentTypes(updateRequest.getExperimentTypes());
            }
            if (updateRequest.getMetadataProfile() != null) {
                existingProfile.setMetadataProfile(updateRequest.getMetadataProfile());
            }
            if (updateRequest.getPerformanceProfile() != null) {
                existingProfile.setPerformanceProfile(updateRequest.getPerformanceProfile());
            }
            if (updateRequest.getTrialSettings() != null) {
                existingProfile.setTrialSettings(updateRequest.getTrialSettings());
            }
            if (updateRequest.getRecommendationSettings() != null) {
                existingProfile.setRecommendationSettings(updateRequest.getRecommendationSettings());
            }
            if (updateRequest.getWebhookUrl() != null) {
                existingProfile.setWebhookUrl(updateRequest.getWebhookUrl());
            }
            if (updateRequest.getEnabled() != null) {
                existingProfile.setEnabled(updateRequest.getEnabled());
            }

            // Convert back to database entity and update
            KruizeBulkProfileEntry updatedEntry = KruizeBulkProfileEntry.fromBulkProfile(existingProfile);

            ValidationOutputData updateResult = experimentDAO.updateBulkProfileToDB(updatedEntry);
            if (!updateResult.isSuccess()) {
                sendErrorResponse(resp, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Failed to update bulk profile: " + updateResult.getMessage());
                return;
            }

            // Trigger webhook if URL is configured
            if (existingProfile.getWebhookUrl() != null && !existingProfile.getWebhookUrl().trim().isEmpty()) {
                triggerWebhook(existingProfile);
            }

            // Return updated profile
            resp.setStatus(HttpServletResponse.SC_OK);
            out.write(objectMapper.writeValueAsString(existingProfile));
            LOGGER.info("Updated bulk profile: {}", profileName);

        } catch (Exception e) {
            LOGGER.error("Error updating bulk profile: {}", e.getMessage(), e);
            sendErrorResponse(resp, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * DELETE /bulkProfile?profile_name=<name> - Delete a bulk profile
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            String profileName = req.getParameter("profile_name");
            if (profileName == null || profileName.trim().isEmpty()) {
                sendErrorResponse(resp, out, HttpServletResponse.SC_BAD_REQUEST,
                        "profile_name query parameter is required");
                return;
            }

            // Check if profile exists
            KruizeBulkProfileEntry existingProfile = experimentDAO.loadBulkProfileByName(profileName);
            if (existingProfile == null) {
                sendErrorResponse(resp, out, HttpServletResponse.SC_NOT_FOUND,
                        "Bulk profile not found: " + profileName);
                return;
            }

            // Delete the profile
            ValidationOutputData deleteResult = experimentDAO.deleteBulkProfileByName(profileName);
            if (!deleteResult.isSuccess()) {
                sendErrorResponse(resp, out, deleteResult.getErrorCode(),
                        "Failed to delete bulk profile: " + deleteResult.getMessage());
                return;
            }

            // Return success response
            resp.setStatus(HttpServletResponse.SC_OK);
            out.write("{\"message\":\"Bulk profile deleted successfully\",\"profile_name\":\"" + profileName + "\"}");
            LOGGER.info("Deleted bulk profile: {}", profileName);

        } catch (Exception e) {
            LOGGER.error("Error deleting bulk profile: {}", e.getMessage(), e);
            sendErrorResponse(resp, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handle GET request for a specific profile
     */
    private void handleGetProfile(String profileName, HttpServletResponse resp, PrintWriter out) throws Exception {
        KruizeBulkProfileEntry profileEntry = experimentDAO.loadBulkProfileByName(profileName);

        if (profileEntry == null) {
            sendErrorResponse(resp, out, HttpServletResponse.SC_NOT_FOUND,
                    "Bulk profile not found: " + profileName);
            return;
        }

        BulkProfile profile = profileEntry.toBulkProfile();
        resp.setStatus(HttpServletResponse.SC_OK);
        out.write(objectMapper.writeValueAsString(profile));
    }

    /**
     * Handle GET request to list all profiles
     */
    private void handleListProfiles(HttpServletResponse resp, PrintWriter out) throws Exception {
        List<KruizeBulkProfileEntry> profileEntries = experimentDAO.loadAllBulkProfiles();
        List<BulkProfile> profiles = new ArrayList<>();

        for (KruizeBulkProfileEntry entry : profileEntries) {
            profiles.add(entry.toBulkProfile());
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        out.write(objectMapper.writeValueAsString(profiles));
    }

    /**
     * Trigger webhook notification for profile update
     */
    private void triggerWebhook(BulkProfile profile) {
        try {
            String webhookUrl = profile.getWebhookUrl();
            String payload = objectMapper.writeValueAsString(profile);

            LOGGER.info("Triggering webhook for profile: {} to URL: {}", profile.getProfileName(), webhookUrl);

            GenericRestApiClient client = new GenericRestApiClient();
            client.setBaseURL(webhookUrl);
            GenericRestApiClient.HttpResponseWrapper response = client.callKruizeAPI(payload);

            if (response != null && response.getStatusCode() == HttpServletResponse.SC_OK) {
                LOGGER.info("Webhook triggered successfully for profile: {}, status: {}",
                        profile.getProfileName(), response.getStatusCode());
            } else {
                LOGGER.warn("Webhook returned non-OK status for profile: {}, status: {}",
                        profile.getProfileName(), response != null ? response.getStatusCode() : "null");
            }

        } catch (Exception e) {
            LOGGER.error("Failed to trigger webhook for profile: {}, error: {}",
                    profile.getProfileName(), e.getMessage(), e);
            // Don't fail the update if webhook fails - just log the error
        }
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse resp, PrintWriter out, int statusCode, String message) {
        try {
            resp.setStatus(statusCode);
            String errorJson = String.format("{\"error\":\"%s\",\"status\":%d}",
                    message.replace("\"", "\\\""), statusCode);
            out.write(errorJson);
        } catch (Exception e) {
            LOGGER.error("Error sending error response: {}", e.getMessage(), e);
        }
    }
}