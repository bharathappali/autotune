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
package com.autotune.utils;

import com.autotune.analyzer.AutotuneExperiment;
import com.autotune.analyzer.application.ApplicationSearchSpace;
import com.autotune.analyzer.application.ApplicationServiceStack;
import com.autotune.analyzer.application.Tunable;
import com.autotune.analyzer.deployment.AutotuneDeploymentInfo;
import com.autotune.analyzer.experiments.*;
import com.autotune.analyzer.k8sObjects.AutotuneConfig;
import com.autotune.analyzer.k8sObjects.AutotuneObject;
import com.autotune.analyzer.k8sObjects.Metric;
import com.autotune.analyzer.k8sObjects.SloInfo;
import com.autotune.analyzer.layer.Layer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static com.autotune.analyzer.deployment.AutotuneDeployment.autotuneObjectMap;
import static java.lang.String.valueOf;

public class TrialHelpers {
    /**
     * Convert the given ExperimentTrial object to JSON. This will be sent to the EM module
     *
     * @param experimentTrial object that holds the trial config
     * @return Equivalent JSONObject that is accepted by EM
     */
    public static JSONObject experimentTrialToJSON(ExperimentTrial experimentTrial) {

        /*
         * Top level experimentTrialJSON Object
         */
        JSONObject experimentTrialJSON = new JSONObject();
        experimentTrialJSON.put("experiment_id", experimentTrial.getExperimentId());
        experimentTrialJSON.put("namespace", experimentTrial.getNamespace());
        experimentTrialJSON.put("experiment_name", experimentTrial.getExperimentName());
        experimentTrialJSON.put("app-version", experimentTrial.getAppVersion());

        /*
         * Info object
         * experimentTrialJSON -> info
         */
        JSONObject infoValues = new JSONObject();
        infoValues.put("trial_id", experimentTrial.getTrialInfo().getTrialId());
        infoValues.put("trial_num", experimentTrial.getTrialInfo().getTrialNum());

        experimentTrialJSON.put("info", infoValues);

        /*
         * trialSettings object
         * experimentTrialJSON -> settings -> trail_settings
         */
        JSONObject trialSettingsValues = new JSONObject();
        trialSettingsValues.put("trial_run", experimentTrial.getExperimentSettings().getTrialSettings().getTrialRun());
        trialSettingsValues.put("trial_measurement_time", experimentTrial.getExperimentSettings().getTrialSettings().getTrialMeasurementTime());

        /*
         * deploymentPolicy object
         * experimentTrialJSON -> settings -> deployment_settings -> deployment_policy
         */
        JSONObject deploymentPolicyValues = new JSONObject();
        deploymentPolicyValues.put("type", experimentTrial.getExperimentSettings().getDeploymentSettings().getDeploymentPolicy().getDeploymentType());
        deploymentPolicyValues.put("target_env", experimentTrial.getExperimentSettings().getDeploymentSettings().getDeploymentPolicy().getTargetEnv());
        deploymentPolicyValues.put("agent", experimentTrial.getExperimentSettings().getDeploymentSettings().getDeploymentPolicy().getAgent());

        /*
         * deployment_tracking object
         * experimentTrialJSON -> settings -> deployment_settings -> deployment_tracking
         */
        JSONArray trackersArray = new JSONArray();
        for (String trackerObjs : experimentTrial.getExperimentSettings().getDeploymentSettings().getDeploymentTracking().getTrackers()) {
            trackersArray.put(trackerObjs);
        }
        JSONObject trackers = new JSONObject();
        trackers.put("trackers", trackersArray);

        JSONObject deploymentSettingsValues = new JSONObject();
        deploymentSettingsValues.put("deployment_policy", deploymentPolicyValues);
        deploymentSettingsValues.put("deployment_tracking", trackers);

        JSONObject experimentSettingsValues = new JSONObject();
        experimentSettingsValues.put("trial_settings", trialSettingsValues);
        experimentSettingsValues.put("deployment_settings", deploymentSettingsValues);
        // Update experimentTrialJSON
        experimentTrialJSON.put("settings", experimentSettingsValues);

        /*
         * deployments object section
         */
        JSONArray deploymentsArrayObjs = new JSONArray();
        for (TrialDetails deployment : experimentTrial.getTrialDetails()) {
            /*
             * resources object
             * experimentTrialJSON -> deployments -> config -> resources
             */
            JSONObject requestsValues = new JSONObject();
            /* CPU Value should only be */
            requestsValues.put("cpu", deployment.getRequests().getCpu());
            requestsValues.put("memory", deployment.getRequests().getMemory());
            JSONObject resourcesValues = new JSONObject();
            resourcesValues.put("requests", requestsValues);
            resourcesValues.put("limits", requestsValues);
            JSONObject resources = new JSONObject();
            resources.put("resources", resourcesValues);

            JSONObject container = new JSONObject();
            container.put("container", resources);

            JSONObject tspec = new JSONObject();
            tspec.put("spec", container);

            JSONObject template = new JSONObject();
            template.put("template", tspec);

            JSONObject resObject = new JSONObject();
            resObject.put("name", "update requests and limits");
            resObject.put("spec", template);

            /*
             * env object
             * experimentTrialJSON -> deployments -> config -> env
             */
            JSONObject envValues = new JSONObject();
            envValues.put("JDK_JAVA_OPTIONS", deployment.getRuntimeOptions());

            JSONObject env = new JSONObject();
            env.put("env", envValues);

            JSONObject containere = new JSONObject();
            containere.put("container", env);

            JSONObject tspece = new JSONObject();
            tspece.put("spec", containere);

            JSONObject templatee = new JSONObject();
            templatee.put("template", tspece);

            JSONObject envObject = new JSONObject();
            envObject.put("name", "update env");
            envObject.put("spec", templatee);

            /*
             * config object
             * experimentTrialJSON -> deployments -> config
             */
            JSONArray configArrayObjects = new JSONArray();
            configArrayObjects.put(resObject);
            configArrayObjects.put(envObject);

            /*
             * metrics object
             * experimentTrialJSON -> deployments -> metrics
             */
            JSONArray metricArrayObjects = new JSONArray();
            for (Metric metricObjects : deployment.getMetrics()) {
                JSONObject obj = new JSONObject();
                obj.put("name", metricObjects.getName());
                obj.put("query", metricObjects.getQuery());
                obj.put("datasource", metricObjects.getDatasource());
                metricArrayObjects.put(obj);
            }

            JSONObject deploymentObject = new JSONObject();
            deploymentObject.put("type", deployment.getDeploymentType());
            deploymentObject.put("deployment_name", deployment.getDeploymentName());
            deploymentObject.put("namespace", deployment.getDeploymentNameSpace());
            deploymentObject.put("state", deployment.getState());
            deploymentObject.put("result", deployment.getResult());
            deploymentObject.put("result_info", deployment.getResultInfo());
            deploymentObject.put("result_error", deployment.getResultError());
            deploymentObject.put("metrics", metricArrayObjects);
            deploymentObject.put("config", configArrayObjects);

            // Add this deployment tracker object to the deployment object array
            deploymentsArrayObjs.put(deploymentObject);
        }
        experimentTrialJSON.put("deployments", deploymentsArrayObjs);

        return experimentTrialJSON;
    }

    /**
     * Create a ExperimentTrial object that holds the trial config to be deployed to the k8s cluster
     *
     * @param trialNumber passed in from Optuna
     * @param autotuneExperiment from the user
     * @param trialConfigJson from Optuna
     * @return ExperimentTrial object
     */
    public static ExperimentTrial createDefaultExperimentTrial(int trialNumber,
                                                               AutotuneExperiment autotuneExperiment,
                                                               String trialConfigJson) {

        ApplicationSearchSpace appSearchSpace = autotuneExperiment.getApplicationServiceStack().getApplicationSearchSpace();
        AutotuneObject autotuneObject = autotuneObjectMap.get(autotuneExperiment.getExperimentName());

        TrialSettings trialSettings = new TrialSettings("15mins",
                "3mins");
        DeploymentPolicy deploymentPolicy = new DeploymentPolicy("rollingUpdate",
                "qa",
                "EM"
        );

        TrialInfo trialInfo = new TrialInfo("",
                trialNumber);

        ArrayList<String> trackers = new ArrayList<>();
        trackers.add("training");
        // trackers.add("production");
        DeploymentTracking deploymentTracking = new DeploymentTracking(trackers);
        DeploymentSettings deploymentSettings = new DeploymentSettings(deploymentPolicy,
                deploymentTracking);
        ExperimentSettings experimentSettings = new ExperimentSettings(trialSettings,
                deploymentSettings);

        ArrayList<TrialDetails> deployments = new ArrayList<>();
        // TODO: "runtimeOptions" needs to be interpreted at a runtime level
        // TODO: That means that once we detect a certain layer, it will be associated with a runtime
        // TODO: The runtime layer will know how to pass the options to container through kubernetes
        // This will be the default for Java
        // TODO: The -XX:MaxRAMPercentage will be based on actual observation of the size of the heap

        for (String tracker : trackers ) {
            System.out.println(trialConfigJson);
            ApplicationServiceStack applicationServiceStack = autotuneExperiment.getApplicationServiceStack();
            ArrayList<Metric> metrics = new ArrayList<>();
            TrialDetails deployment = new TrialDetails(tracker,
                    applicationServiceStack.getDeploymentName(),
                    applicationServiceStack.getNamespace(),
                    "",
                    "",
                    "",
                    "",
                    metrics,
                    null,
                    null,
                    ""
            );
            JSONArray trialConfigArray = new JSONArray(trialConfigJson);
            for (Object trialConfigObject : trialConfigArray) {
                JSONObject trialConfig = (JSONObject) trialConfigObject;
                String tunableName = trialConfig.getString("tunable_name");
                Tunable tunable = autotuneExperiment.getApplicationServiceStack().getApplicationSearchSpace().getTunablesMap().get(tunableName);
                // String tunableValue = trialConfig.getString("tunable_value");
                Class<Layer> classRef = AutotuneDeploymentInfo.getLayer(tunable.getLayerName());
                try {
                    Object inst = classRef.getDeclaredConstructor().newInstance();
                    Method method = classRef.getMethod("prepTunable", Tunable.class, JSONObject.class, TrialDetails.class);
                    method.invoke(inst, tunable, trialConfig, deployment);
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            /* Create the metrics array */
            /* First iterate through the objective function variables */
            SloInfo sloInfo = autotuneObject.getSloInfo();
            for (Metric metric : sloInfo.getFunctionVariables()) {
                metrics.add(metric);
            }

            /* Now check for any metric object for tunables that have associated queries */
            for (String autotuneConfigName : applicationServiceStack.getApplicationServiceStackLayers().keySet()) {
                AutotuneConfig autotuneConfig = applicationServiceStack.getApplicationServiceStackLayers().get(autotuneConfigName);
                for (Tunable tunable : autotuneConfig.getTunables()) {
                    String tunableQuery = tunable.getQueries().get(AutotuneDeploymentInfo.getMonitoringAgent());
                    if (tunableQuery != null && !tunableQuery.isEmpty()) {
                        Metric queryMetric = new Metric(tunable.getName(),
                                tunableQuery,
                                AutotuneDeploymentInfo.getMonitoringAgent(),
                                tunable.getValueType());
                        metrics.add(queryMetric);
                    }
                }
            }

            deployments.add(deployment);
        }

        ExperimentTrial experimentTrial = new ExperimentTrial(appSearchSpace.getExperimentName(),
                appSearchSpace.getExperimentId(),
                autotuneObject.getNamespace(),
                "v1",
                trialInfo,
                experimentSettings,
                deployments
        );

        return experimentTrial;
    }
}
