/*******************************************************************************
 * Copyright (c) 2021, 2022 Red Hat, IBM Corporation and others.
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
package com.autotune.experimentManager.services;

import com.autotune.experimentManager.data.ExperimentTrialData;
import com.autotune.experimentManager.services.util.EMAPIHandler;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.stream.Collectors;


/**
 * RestAPI Servlet used to load experiment trial in JSON format using POST method.
 * JSON format sample can be found here autotune/examples/createExperimentTrial.json
 */
public class CreateExperimentTrial extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateExperimentTrial.class);
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String inputData = req.getReader().lines().collect(Collectors.joining());
        JSONObject json = new JSONObject(inputData);

        LOGGER.info("Input JSON obtained:");
        LOGGER.info(json.toString(4));
        LOGGER.info("Creating ETD");
        ExperimentTrialData trialData = EMAPIHandler.createETD(json);
        String runId = EMAPIHandler.registerTrial(trialData);
        LOGGER.info("Linking runID - " + runId + " to ETD");
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(runId);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doGet(req, resp);
    }
}
