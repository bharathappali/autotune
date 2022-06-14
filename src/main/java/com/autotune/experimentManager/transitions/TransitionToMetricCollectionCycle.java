package com.autotune.experimentManager.transitions;

import com.autotune.experimentManager.data.EMMapper;
import com.autotune.experimentManager.data.ExperimentTrialData;
import com.autotune.experimentManager.data.input.EMMetricInput;
import com.autotune.experimentManager.data.input.deployments.EMConfigDeploymentContainerConfig;
import com.autotune.experimentManager.data.input.metrics.EMMetricResult;
import com.autotune.experimentManager.data.iteration.EMIterationMetricResult;
import com.autotune.experimentManager.utils.EMConstants;
import com.autotune.experimentManager.utils.EMUtil;
import com.autotune.utils.GenericRestApiClient;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class TransitionToMetricCollectionCycle extends AbstractBaseTransition{

    @Override
    public void transit(String runId) {
        ExperimentTrialData trialData = (ExperimentTrialData) EMMapper.getInstance().getMap().get(runId);
        EMMetricResult emMetricData = new EMMetricResult();
        String podLabel = "app=galaxies-deployment";
        String contName = null;
        try {
            System.out.println("Running metrics collection");
            GenericRestApiClient apiClient = new GenericRestApiClient(
                                                EMUtil.getBaseDataSourceUrl(
                                                    trialData.getConfig().getEmConfigObject().getInfo().getDataSourceInfo().getDatasources().get(0).getUrl(),
                                                    trialData.getConfig().getEmConfigObject().getInfo().getDataSourceInfo().getDatasources().get(0).getName()
                                                )
                                             );

            ArrayList<EMMetricInput> pod_metrics = trialData.getConfig().getEmConfigObject().getDeployments().getTrainingDeployment().getPodMetrics();
            ArrayList<EMMetricInput> container_metrics = new ArrayList<EMMetricInput>();
            for (EMConfigDeploymentContainerConfig config : trialData.getConfig().getEmConfigObject().getDeployments().getTrainingDeployment().getContainers()) {
                container_metrics.addAll(config.getContainerMetrics());
                contName = config.getContainerName();
            }
            for (EMMetricInput metricInput : pod_metrics) {
                JSONObject jsonObject = apiClient.fetchMetricsJson(
                        EMConstants.HttpConstants.MethodType.GET,
                        metricInput.getQuery());
                if (jsonObject.has("status")
                    && jsonObject.getString("status").equalsIgnoreCase("success")) {
                    if (jsonObject.has("data")
                        && jsonObject.getJSONObject("data").has("result")
                        && !jsonObject.getJSONObject("data").getJSONArray("result").isEmpty()) {
                        JSONArray result = jsonObject.getJSONObject("data").getJSONArray("result");
                        for (Object result_obj: result) {
                            JSONObject result_json = (JSONObject) result_obj;
                            if (result_json.has("value")
                                && !result_json.getJSONArray("value").isEmpty()) {
                                EMIterationMetricResult emIterationMetricResult = trialData.getEmIterationManager()
                                        .getIterationDataList()
                                        .get(trialData.getEmIterationManager().getCurrentIteration()-1)
                                        .getEmIterationResult()
                                        .getIterationMetricResult(metricInput.getName());
                                EMMetricResult emMetricResult = new EMMetricResult(false);
                                emMetricResult.getEmMetricGenericResults().setMean(Float.parseFloat(result_json.getJSONArray("value").getString(1)));
                                if (trialData.getEmIterationManager()
                                        .getIterationDataList()
                                        .get(trialData.getEmIterationManager().getCurrentIteration()-1).getCurrentCycle()
                                        >
                                        trialData.getEmIterationManager()
                                                .getIterationDataList()
                                                .get(trialData.getEmIterationManager().getCurrentIteration()-1).getWarmCycles()
                                ) {

                                    emIterationMetricResult.addToMeasurementList(emMetricResult);
                                }
                                else {
                                    emIterationMetricResult.addToWarmUpList(emMetricResult);
                                }
                            }
                        }
                    }
                }
            }
            KubernetesClient client = new DefaultKubernetesClient();
            System.out.println(podLabel);
            String podName = client.pods().inNamespace(trialData.getConfig().getDeploymentNamespace()).withLabel(podLabel).list().getItems().get(0).getMetadata().getName();
            System.out.println("PodName - " + podName);
            for (EMMetricInput metricInput: container_metrics) {
                String reframedQuery = metricInput.getQuery();
                if (reframedQuery.contains("$CONTAINER_LABEL$=\"\"")) {
                    reframedQuery.replace("$CONTAINER_LABEL$=\"\"", "container="+podLabel);
                }
                if (reframedQuery.contains("$POD_LABEL$")) {
                    reframedQuery.replace("$POD_LABEL$", "pod");
                }
                if (reframedQuery.contains("$$POD$$")) {
                    reframedQuery.replace("$POD$", podName);
                }
                System.out.println(reframedQuery);
                if (reframedQuery.contains("CONTAINER_LABEL")) {
                    reframedQuery = reframedQuery.split("\\{")[0] + "{container=\""+contName+"\",pod=\""+podName+"\"}";
                }
                System.out.println("Reframed Query - " + reframedQuery);
                JSONObject jsonObject = apiClient.fetchMetricsJson(
                        EMConstants.HttpConstants.MethodType.GET,
                        reframedQuery);
                if (jsonObject.has("status")
                        && jsonObject.getString("status").equalsIgnoreCase("success")) {
                    if (jsonObject.has("data")
                            && jsonObject.getJSONObject("data").has("result")
                            && !jsonObject.getJSONObject("data").getJSONArray("result").isEmpty()) {
                        JSONArray result = jsonObject.getJSONObject("data").getJSONArray("result");
                        for (Object result_obj: result) {
                            JSONObject result_json = (JSONObject) result_obj;
                            if (result_json.has("value")
                                    && !result_json.getJSONArray("value").isEmpty()) {
                                EMIterationMetricResult emIterationMetricResult = trialData.getEmIterationManager()
                                        .getIterationDataList()
                                        .get(trialData.getEmIterationManager().getCurrentIteration()-1)
                                        .getEmIterationResult()
                                        .getIterationMetricResult(metricInput.getName());
                                EMMetricResult emMetricResult = new EMMetricResult(false);
                                emMetricResult.getEmMetricGenericResults().setMean(Float.parseFloat(result_json.getJSONArray("value").getString(1)));
                                if (trialData.getEmIterationManager()
                                        .getIterationDataList()
                                        .get(trialData.getEmIterationManager().getCurrentIteration()-1).getCurrentCycle()
                                        >
                                        trialData.getEmIterationManager()
                                                .getIterationDataList()
                                                .get(trialData.getEmIterationManager().getCurrentIteration()-1).getWarmCycles()
                                ) {

                                    emIterationMetricResult.addToMeasurementList(emMetricResult);
                                }
                                else {
                                    emIterationMetricResult.addToWarmUpList(emMetricResult);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Live Metric Map :");
        EMUtil.printMetricMap(trialData);
        System.out.println("Current Cycle - " + trialData.getEmIterationManager().getIterationDataList().get(trialData.getEmIterationManager().getCurrentIteration()-1).getCurrentCycle());
        trialData.getEmIterationManager().getIterationDataList().get(trialData.getEmIterationManager().getCurrentIteration()-1).incrementCycle();
        System.out.println("Next Cycle - " + trialData.getEmIterationManager().getIterationDataList().get(trialData.getEmIterationManager().getCurrentIteration()-1).getCurrentCycle());
        processNextTransition(runId);
    }
}
