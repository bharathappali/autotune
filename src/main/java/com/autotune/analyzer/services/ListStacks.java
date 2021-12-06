/*******************************************************************************
 * Copyright (c) 2020, 2021 Red Hat, IBM Corporation and others.
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

import com.autotune.analyzer.deployment.AutotuneDeployment;
import com.autotune.analyzer.k8sObjects.AutotuneObject;
import com.autotune.analyzer.utils.AnalyzerConstants;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.CHARACTER_ENCODING;
import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.JSON_CONTENT_TYPE;
import static com.autotune.analyzer.utils.AnalyzerErrorConstants.AutotuneServiceMessages.*;
import static com.autotune.analyzer.utils.ServiceHelpers.addExperimentDetails;
import static com.autotune.analyzer.utils.ServiceHelpers.addStackDetails;

public class ListStacks extends HttpServlet
{
    /**
     * Get the list of applications monitored by autotune.
     *
     * Request:
     * `GET /listStacks` gives list of application stacks monitored by autotune.
     *
     * Example JSON:
     * [
     *     {
     *         "experiment_name": "autotune-max-http-throughput",
     *         "experiment_id": "94f76772f43339f860e0d5aad8bebc1abf50f461712d4c5d14ea7aada280e8f3",
     *         "objective_function": "request_count",
     *         "hpo_algo_impl": "optuna_tpe",
     *         "stacks": [
     *             "dinogun/autotune_optuna:0.0.5",
     *             "dinogun/autotune_operator:0.0.5"
     *         ],
     *         "slo_class": "throughput",
     *         "direction": "maximize"
     *     },
     *     {
     *         "experiment_name": "galaxies-autotune-min-http-response-time",
     *         "experiment_id": "3bc579e7b1c29eb547809348c2a452e96cfd9ed9d3489d644f5fa4d3aeaa3c9f",
     *         "objective_function": "request_sum/request_count",
     *         "hpo_algo_impl": "optuna_tpe",
     *         "stacks": ["dinogun/galaxies:1.2-jdk-11.0.10_9"],
     *         "slo_class": "response_time",
     *         "direction": "minimize"
     *     }
     * ]
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(JSON_CONTENT_TYPE);
            response.setCharacterEncoding(CHARACTER_ENCODING);

            JSONArray outputJsonArray = new JSONArray();
            // Check if there are any experiments running at all ?
            if (AutotuneDeployment.autotuneObjectMap.isEmpty()) {
                outputJsonArray.put(AUTOTUNE_OBJECTS_NOT_FOUND);
                response.getWriter().println(outputJsonArray.toString(4));
                return;
            }

            String experimentName = request.getParameter(AnalyzerConstants.ServiceConstants.EXPERIMENT_NAME);
            // If experiment name is not null, try to find it in the hashmap
            if (experimentName != null) {
                AutotuneObject autotuneObject = AutotuneDeployment.autotuneObjectMap.get(experimentName);
                if (autotuneObject != null) {
                    JSONObject experimentJson = new JSONObject();
                    addExperimentDetails(experimentJson, autotuneObject);
                    addStackDetails(experimentJson, autotuneObject);
                    outputJsonArray.put(experimentJson);
                }
            } else {
                // Print all the experiments
                for (String autotuneObjectKey : AutotuneDeployment.autotuneObjectMap.keySet()) {
                    AutotuneObject autotuneObject = AutotuneDeployment.autotuneObjectMap.get(autotuneObjectKey);
                    JSONObject experimentJson = new JSONObject();
                    addExperimentDetails(experimentJson, autotuneObject);
                    addStackDetails(experimentJson, autotuneObject);
                    outputJsonArray.put(experimentJson);
                }
            }

            if (outputJsonArray.isEmpty()) {
                outputJsonArray.put(ERROR_EXPERIMENT_NAME + experimentName + NOT_FOUND);
            }
            response.getWriter().println(outputJsonArray.toString(4));
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
