package com.autotune.experimentManager.services.util;

import com.autotune.common.experiments.ContainerConfigData;
import com.autotune.common.experiments.ExperimentTrial;
import com.autotune.common.experiments.PodContainer;
import com.autotune.common.experiments.TrialDetails;
import com.autotune.common.k8sObjects.Metric;
import com.autotune.experimentManager.core.ExperimentManager;
import com.autotune.experimentManager.data.*;
import com.autotune.experimentManager.data.input.EMConfigObject;
import com.autotune.experimentManager.exceptions.EMInvalidInstanceCreation;
import com.autotune.experimentManager.exceptions.IncompatibleInputJSONException;
import com.autotune.experimentManager.services.CreateExperimentTrial;
import com.autotune.experimentManager.utils.EMConstants;
import com.autotune.experimentManager.utils.EMUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class EMAPIHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EMAPIHandler.class);
    public static ExperimentTrialData createETD(JSONObject json, ExperimentTrial experimentTrial) {
        try {
            LOGGER.info("Creating EMTrailConfig");
            EMTrialConfig config = new EMTrialConfig(json);
            LOGGER.info("EMTrailConfig created");
            ExperimentTrialData trailData = new ExperimentTrialData(config, experimentTrial);
            LOGGER.info("ETD created");
            return trailData;
        } catch (IncompatibleInputJSONException | EMInvalidInstanceCreation e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String registerTrial(ExperimentTrialData trialData) {
        String runId = EMUtil.createUUID();
        String nsdKey = EMUtil.formatNSDKey(trialData.getConfig().getDeploymentNamespace(), trialData.getConfig().getDeploymentName());

        if (trialData.getConfig().getDeploymentStrategy().equalsIgnoreCase(EMConstants.DeploymentStrategies.ROLLING_UPDATE)) {
            if (EMMapper.getInstance().getDeploymentRunIdMap().containsKey(nsdKey)) {
                LinkedList<String> depList = (LinkedList<String>) EMMapper.getInstance().getDeploymentRunIdMap().get(nsdKey);
                if (depList.isEmpty()) {
                    // TODO: Need to be handled
                } else {
                    String existingRunId = depList.getLast();
                    ExperimentTrialData lastETD = ((ExperimentTrialData) EMMapper.getInstance().getMap().get(existingRunId));
                    if (lastETD.getStatus().toString().equalsIgnoreCase(EMUtil.EMExpStatus.COMPLETED.toString())) {
                        depList.add(runId);
                        EMMapper.getInstance().getMap().put(runId, trialData);
                        pushTransitionToQueue(runId);
                    } else {
                        depList.add(runId);
                        EMMapper.getInstance().getMap().put(runId, trialData);
                        lastETD.setNotifyTrialCompletion(true);
                        trialData.setStatus(EMUtil.EMExpStatus.WAIT);
                    }
                }
            } else {
                LinkedList<String> runIdList = new LinkedList<String>();
                runIdList.add(runId);
                EMMapper.getInstance().getDeploymentRunIdMap().put(nsdKey, runIdList);
                EMMapper.getInstance().getMap().put(runId, trialData);
                pushTransitionToQueue(runId);
            }
        } else {
            if (EMMapper.getInstance().getDeploymentRunIdMap().containsKey(nsdKey)) {
                Queue<String> depQueue = ((Queue<String>) EMMapper.getInstance().getDeploymentRunIdMap().get(nsdKey));
                depQueue.add(runId);
            } else {
                LinkedList<String> runIdList = new LinkedList<String>();
                runIdList.add(runId);
                EMMapper.getInstance().getDeploymentRunIdMap().put(nsdKey, runIdList);
            }
            EMMapper.getInstance().getMap().put(runId, trialData);
            trialData.setStatus(EMUtil.EMExpStatus.IN_PROGRESS);
            pushTransitionToQueue(runId);
        }
        return runId;
    }

    private static void pushTransitionToQueue(String runId) {
        EMStageTransition transition = new EMStageTransition(runId, EMUtil.EMExpStages.CREATE_CONFIG);
        EMStageProcessQueue.getStageProcessQueueInstance().getQueue().add(transition);
        ExperimentManager.notifyQueueProcessor();
    }

    // Will be deprecated in future releases
    public static JSONObject getOlderJSON(ExperimentTrial experimentTrial) {
        System.out.println("You are using an outdated JSON will be deprecated in future releases");
        JSONObject oldJson = new JSONObject();


        JSONObject infoObject = new JSONObject();
        JSONObject trailInfoObject = new JSONObject();
        trailInfoObject.put(EMConstants.EMJSONKeys.TRIAL_ID, experimentTrial.getTrialInfo().getTrialId());
        trailInfoObject.put(EMConstants.EMJSONKeys.TRIAL_NUM, experimentTrial.getTrialInfo().getTrialNum());
        trailInfoObject.put(EMConstants.EMJSONKeys.TRIAL_RESULT_URL, experimentTrial.getTrialInfo().getTrialResultURL());
        infoObject.put(EMConstants.EMJSONKeys.TRIAL_INFO, trailInfoObject);
        JSONObject dsInfoObject = new JSONObject();
        JSONArray dsArray = new JSONArray();
        dsInfoObject.put(EMConstants.EMJSONKeys.URL, experimentTrial.getDatasourceInfo().getUrl());
        dsInfoObject.put(EMConstants.EMJSONKeys.NAME, experimentTrial.getDatasourceInfo().getName());
        dsArray.put(dsInfoObject);
        infoObject.put(EMConstants.EMJSONKeys.DATASOURCE_INFO, dsArray);
        oldJson.put(EMConstants.EMJSONKeys.INFO, infoObject);
        oldJson.put(EMConstants.EMJSONKeys.EXPERIMENT_NAME, experimentTrial.getExperimentName());
        oldJson.put(EMConstants.EMJSONKeys.EXPERIMENT_ID, experimentTrial.getExperimentId());


        JSONObject settingsObject = new JSONObject();
        JSONObject trialSettingsObject = new JSONObject();
        JSONObject deploymentSettingsObject = new JSONObject();
        trialSettingsObject.put(EMConstants.EMJSONKeys.MEASUREMENT_CYCLES, experimentTrial.getExperimentSettings().getTrialSettings().getTrialMeasurementCycles());
        trialSettingsObject.put(EMConstants.EMJSONKeys.MEASUREMENT_DURATION, experimentTrial.getExperimentSettings().getTrialSettings().getTrialMeasurementDuration());
        trialSettingsObject.put(EMConstants.EMJSONKeys.ITERATIONS, experimentTrial.getExperimentSettings().getTrialSettings().getTrialIterations());
        trialSettingsObject.put(EMConstants.EMJSONKeys.WARMUP_CYCLES, experimentTrial.getExperimentSettings().getTrialSettings().getTrialWarmupCycles());
        trialSettingsObject.put(EMConstants.EMJSONKeys.WARMUP_DURATION, experimentTrial.getExperimentSettings().getTrialSettings().getTrialWarmupDuration());
        settingsObject.put(EMConstants.EMJSONKeys.TRIAL_SETTINGS, trialSettingsObject);
        JSONObject policyObject = new JSONObject();
        policyObject.put(EMConstants.EMJSONKeys.TYPE, experimentTrial.getExperimentSettings().getDeploymentSettings().getDeploymentPolicy().getDeploymentType());
        deploymentSettingsObject.put(EMConstants.EMJSONKeys.DEPLOYMENT_POLICY, policyObject);
        JSONArray trackers = new JSONArray();
        trackers.put("training");
        JSONObject trackersObject = new JSONObject();
        trackersObject.put(EMConstants.EMJSONKeys.TRACKERS, trackers);
        deploymentSettingsObject.put(EMConstants.EMJSONKeys.DEPLOYMENT_TRACKING, trackersObject);
        settingsObject.put(EMConstants.EMJSONKeys.DEPLOYMENT_SETTINGS, deploymentSettingsObject);
        oldJson.put(EMConstants.EMJSONKeys.SETTINGS, settingsObject);


        JSONArray deployments =  new JSONArray();
        Map<String, TrialDetails> map = experimentTrial.getTrialDetails();
        for (Map.Entry<String, TrialDetails> entry : map.entrySet()) {
            String type = entry.getKey();
            TrialDetails trialDetails = entry.getValue();
            JSONObject deploymentObject = new JSONObject();
            deploymentObject.put(EMConstants.EMJSONKeys.TYPE, type);
            deploymentObject.put(EMConstants.EMJSONKeys.DEPLOYMENT_NAME, trialDetails.getDeploymentName());
            deploymentObject.put(EMConstants.EMJSONKeys.NAMESPACE, trialDetails.getDeploymentNameSpace());

            JSONArray podmetrics = new JSONArray();
            Map<String, Metric> metricMap = trialDetails.getPodMetrics();
            for (Map.Entry<String, Metric> metricEntry : metricMap.entrySet()) {
                JSONObject metricObject = new JSONObject();
                metricObject.put(EMConstants.EMJSONKeys.NAME, metricEntry.getValue().getName());
                metricObject.put(EMConstants.EMJSONKeys.DATASOURCE, metricEntry.getValue().getDatasource());
                metricObject.put(EMConstants.EMJSONKeys.QUERY, metricEntry.getValue().getQuery());
                podmetrics.put(metricObject);
            }
            deploymentObject.put(EMConstants.EMJSONKeys.POD_METRICS, podmetrics);

            JSONArray containers = new JSONArray();
            Map<String, PodContainer> podContainers= trialDetails.getPodContainers();
            for (Map.Entry<String, PodContainer> podContainerMap : podContainers.entrySet()) {
                String containerKey = podContainerMap.getKey();
                PodContainer podContainer = podContainerMap.getValue();
                JSONObject container = new JSONObject();

                JSONArray containerMetrics = new JSONArray();
                Map<String, Metric> containerMM = trialDetails.getPodContainers().get(containerKey).getContainerMetrics();
                for (Map.Entry<String, Metric> metricEntry : containerMM.entrySet()) {
                    JSONObject metricObject = new JSONObject();
                    metricObject.put(EMConstants.EMJSONKeys.NAME, metricEntry.getValue().getName());
                    metricObject.put(EMConstants.EMJSONKeys.DATASOURCE, metricEntry.getValue().getDatasource());
                    metricObject.put(EMConstants.EMJSONKeys.QUERY, metricEntry.getValue().getQuery());
                    containerMetrics.put(metricObject);
                }
                container.put(EMConstants.EMJSONKeys.CONTAINER_METRICS, containerMetrics);

                JSONArray configArray = new JSONArray();
                Map<String, ContainerConfigData> configMM = podContainer.getTrialConfigs();
                for (Map.Entry<String, ContainerConfigData> configDataEntry : configMM.entrySet()) {
                    String configKey = configDataEntry.getKey();
                    ContainerConfigData configData = configDataEntry.getValue();
                    JSONObject configObject = new JSONObject();
                    configArray.put(configObject);
                }
                container.put(EMConstants.EMJSONKeys.CONFIG, configArray);
                container.put(EMConstants.EMJSONKeys.CONTAINER_NAME, podContainer.getContainerName());
                container.put(EMConstants.EMJSONKeys.IMAGE_NAME, podContainer.getStackName());

                containers.put(container);
            }
            deploymentObject.put(EMConstants.EMJSONKeys.CONTAINERS, containers);






            deployments.put(deploymentObject);
        }
        oldJson.put(EMConstants.EMJSONKeys.DEPLOYMENTS, deployments);
        return oldJson;
    }
}
