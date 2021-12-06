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

import com.autotune.analyzer.application.ApplicationServiceStack;
import com.autotune.analyzer.deployment.AutotuneDeployment;
import com.autotune.analyzer.k8sObjects.AutotuneObject;
import com.autotune.analyzer.utils.AnalyzerConstants;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ListApplications extends HttpServlet
{
    /**
     * Get the list of applications monitored by autotune.
     *
     * Request:
     * `GET /listApplications` gives list of applications monitored by autotune.
     *
     * Example JSON:
     * [
     *     {
     *       "experiment_name": "app1_autotune",
     *       “objective_function”: “transaction_response_time”,
     *       "slo_class": "response_time",
     *       “direction”: “minimize”
     *     },
     *     {
     *       "experiment_name": "app2_autotune",
     *       “objective_function”: “performedChecks_total”,
     *       "slo_class": "throughput",
     *       “direction”: “maximize”
     *     }
     * ]
     * @param req
     * @param resp
     * @throws IOException
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JSONArray outputJsonArray = new JSONArray();
        resp.setContentType("application/json");

        String experimentName = req.getParameter(AnalyzerConstants.ServiceConstants.EXPERIMENT_NAME);

        for (String autotuneObjectKey : AutotuneDeployment.applicationServiceStackMap.keySet()) {
            AutotuneObject autotuneObject = AutotuneDeployment.autotuneObjectMap.get(autotuneObjectKey);

            if (experimentName == null) {
                for (String application : AutotuneDeployment.applicationServiceStackMap.get(autotuneObjectKey).keySet()) {
                    addApplicationToResponse(outputJsonArray, autotuneObject, application);
                }
            } else {
                addApplicationToResponse(outputJsonArray, autotuneObject, experimentName);
            }
        }

        if (outputJsonArray.isEmpty()) {
            if (AutotuneDeployment.autotuneObjectMap.isEmpty())
                outputJsonArray.put("Error: No objects of kind Autotune found!");
            else
                outputJsonArray.put("Error: Application " + experimentName + " not found!");
        }

        resp.getWriter().println(outputJsonArray.toString(4));
    }

    private void addApplicationToResponse(JSONArray outputJsonArray, AutotuneObject autotuneObject, String podName) {
        // Check if application is monitored by autotune
        if (!AutotuneDeployment.applicationServiceStackMap.get(autotuneObject.getExperimentName()).containsKey(podName))
            return;

        ApplicationServiceStack applicationServiceStack = AutotuneDeployment.applicationServiceStackMap.get(autotuneObject.getExperimentName()).get(podName);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put(AnalyzerConstants.ServiceConstants.EXPERIMENT_NAME, autotuneObject.getExperimentName());
        jsonObject.put(AnalyzerConstants.ServiceConstants.POD_NAME, podName);
        jsonObject.put(AnalyzerConstants.ServiceConstants.DEPLOYMENT_NAME, applicationServiceStack.getDeploymentName());
        jsonObject.put(AnalyzerConstants.AutotuneObjectConstants.NAMESPACE, autotuneObject.getNamespace());
        jsonObject.put(AnalyzerConstants.AutotuneObjectConstants.DIRECTION, autotuneObject.getSloInfo().getDirection());
        jsonObject.put(AnalyzerConstants.AutotuneObjectConstants.OBJECTIVE_FUNCTION, autotuneObject.getSloInfo().getObjectiveFunction());
        jsonObject.put(AnalyzerConstants.AutotuneObjectConstants.SLO_CLASS, autotuneObject.getSloInfo().getSloClass());
        jsonObject.put(AnalyzerConstants.AutotuneObjectConstants.ID, autotuneObject.getExperimentId());
        jsonObject.put(AnalyzerConstants.AutotuneObjectConstants.HPO_ALGO_IMPL, autotuneObject.getSloInfo().getHpoAlgoImpl());

        outputJsonArray.put(jsonObject);
    }

}
