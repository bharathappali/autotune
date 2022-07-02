package com.autotune.experimentManager.transitions.util;

import com.autotune.experimentManager.core.EMIterationManager;
import com.autotune.experimentManager.data.EMMapper;
import com.autotune.experimentManager.data.ExperimentTrialData;
import com.autotune.experimentManager.data.input.EMMetricInput;
import com.autotune.experimentManager.data.input.metrics.EMMetricResult;
import com.autotune.experimentManager.data.iteration.EMIterationData;
import com.autotune.experimentManager.data.iteration.EMIterationMetricResult;
import com.autotune.experimentManager.exceptions.EMMetricCollectionException;
import com.autotune.experimentManager.utils.EMConstants;
import com.autotune.experimentManager.utils.EMUtil;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class TransistionHelper {
    public static class LoadAnalyser {
        public static boolean isLoadApplied() {
            return false;
        }

        public static boolean isReadyToLoad() {
            return true;
        }
    }

    public static class ConfigHelper {
        public static JSONArray getContainerConfig(String containerName, JSONArray containersConfig) {
            for (Object obj : containersConfig) {
                JSONObject containerObj = (JSONObject) obj;
                if (containerObj.getString(EMConstants.EMJSONKeys.CONTAINER_NAME).equalsIgnoreCase(containerName)) {
                    return containerObj.getJSONArray(EMConstants.EMJSONKeys.CONFIG);
                }
            }
            return null;
        }
    }

    public static class CollectMetrics {
        public static void startMetricCollection(ExperimentTrialData etd) {
            for (EMMetricInput emMetricInput : etd.getConfig()
                                                .getEmConfigObject()
                                                .getDeployments()
                                                .getTrainingDeployment()
                                                .getPodMetrics()) {
                String query = expandQuery(etd, emMetricInput.getQuery());
                ArrayList<EMMetricResult> metricResults = getMetricsFromDataSource(emMetricInput.getName(), query, emMetricInput.getDataSource());
                try {
                    int totalCycles = etd.getEmIterationManager().getIterationData(etd.getEmIterationManager().getCurrentIteration()).getTotalCycles();
                    if (metricResults.size() < totalCycles) {
                        throw new EMMetricCollectionException();
                    }
                    int warmupCycles = etd.getEmIterationManager().getIterationData(etd.getEmIterationManager().getCurrentIteration()).getWarmCycles();
                    EMIterationMetricResult emIterationMetricResult = etd.getEmIterationManager().getIterationData(etd.getEmIterationManager().getCurrentIteration()).getEmIterationResult().getIterationMetricResult(emMetricInput.getName());
                    for (int i = 0; i < metricResults.size(); i++) {
                        if (i >= warmupCycles) {
                            emIterationMetricResult.addToMeasurementList(metricResults.get(i));
                        } else {
                            emIterationMetricResult.addToWarmUpList(metricResults.get(i));
                        }
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }

            }
        }

        public static String expandQuery(ExperimentTrialData etd, String query) {
            // Needs to be expanded based on the pod details
            return null;
        }

        public static ArrayList<EMMetricResult> getMetricsFromDataSource(String metricName, String Query, String datasource) {
            // Needs to be replaced with EMRestAPI for datasource querying
            System.out.println("Collecting metrics from datasource");
            return null;
        }
    }

    public static class MetricsFormatter {
        public static JSONObject getMetricsJson(String runId) {
            // Need to get data from ETD and process it
            ExperimentTrialData trialData = (ExperimentTrialData) EMMapper.getInstance().getMap().get(runId);
            JSONObject retJson = trialData.getConfig().getEmConfigObject().toJSON();
            System.out.println("Input JSON without Metrics :");
            System.out.println(retJson.toString(2));
            retJson.remove("settings");
            retJson.getJSONObject("info").remove("datasource_info");
            JSONObject cpuRequestResult = new JSONObject("" +
                    "{\"general_info\":{\"mean\":3.98389," +
                    "\"min\":3.6809621160604," +
                    "\"max\":4.10951920556296}}"
            );
            JSONObject jvmMemUsedResult = new JSONObject("" +
                    "{\"general_info\":{\"max\":1123," +
                    "\"min\":769," +
                    "\"mean\":832.63}}");
            JSONObject memoryRequestResult = new JSONObject("" +
                    "{\"general_info\":{\"max\":1212," +
                    "\"min\":834," +
                    "\"mean\":976.794}}");
            JSONObject requestSumResult = new JSONObject("" +
                    "{\"general_info\":{\"min\":2.15," +
                    "\"mean\":31.91," +
                    "\"max\":2107.212121}," +
                    "\"percentile_info\":{\"50p\":0.63," +
                    "\"95p\":8.94," +
                    "\"97p\":64.75," +
                    "\"99p\":82.59," +
                    "\"99.9p\":93.48," +
                    "\"99.99p\":111.5," +
                    "\"99.999p\":198.52," +
                    "\"100p\":30000}}");
            JSONObject requestCountResult = new JSONObject("" +
                    "{\"general_info\":{\"max\":21466," +
                    "\"min\":2.11," +
                    "\"mean\":21045}}");
            JSONArray containersArr = ((JSONObject) retJson.getJSONArray("deployments").get(0)).getJSONArray("containers");
            for (Object container: containersArr){
                JSONObject containerJson = (JSONObject) container;
                containerJson.remove("config");
                JSONArray containerMetrics = containerJson.getJSONArray("container_metrics");
                for (Object contMetObj : containerMetrics) {
                    JSONObject contMetJson = (JSONObject) contMetObj;
                    contMetJson.remove("datasource");
                    contMetJson.remove("query");
                    if (contMetJson.getString("name").equalsIgnoreCase("cpuRequest")) {
                        contMetJson.put("summary_results", cpuRequestResult);
                    } else if (contMetJson.getString("name").equalsIgnoreCase("memoryRequest")) {
                        contMetJson.put("summary_results", memoryRequestResult);
                    } else if (contMetJson.getString("name").equalsIgnoreCase("request_sum")) {
                        contMetJson.put("summary_results", requestSumResult);
                    } else if (contMetJson.getString("name").equalsIgnoreCase("request_count")) {
                        contMetJson.put("summary_results", requestCountResult);
                    } else if (contMetJson.getString("name").equalsIgnoreCase("JvmMemoryUsed")) {
                        contMetJson.put("summary_results", jvmMemUsedResult);
                    }
                }
            }
            JSONArray podMetrics = ((JSONObject) retJson.getJSONArray("deployments").get(0)).getJSONArray("pod_metrics");
            for (Object podmetobj : podMetrics) {
                JSONObject podmetJson = (JSONObject) podmetobj;
                podmetJson.remove("datasource");
                podmetJson.remove("query");
                if (podmetJson.getString("name").equalsIgnoreCase("cpuRequest")) {
                    podmetJson.put("summary_results", cpuRequestResult);
                } else if (podmetJson.getString("name").equalsIgnoreCase("memoryRequest")) {
                    podmetJson.put("summary_results", memoryRequestResult);
                } else if (podmetJson.getString("name").equalsIgnoreCase("request_sum")) {
                    podmetJson.put("summary_results", requestSumResult);
                } else if (podmetJson.getString("name").equalsIgnoreCase("request_count")) {
                    podmetJson.put("summary_results", requestCountResult);
                } else if (podmetJson.getString("name").equalsIgnoreCase("JvmMemoryUsed")) {
                    podmetJson.put("summary_results", jvmMemUsedResult);
                }
            }
            System.out.println("Input JSON with Metrics :");
            System.out.println(retJson.toString(2));
            return retJson;
        }

        public static JSONObject getLiveMetricResult(String runId, boolean needIterationData, boolean needSummaryResults) {
            ExperimentTrialData trialData = (ExperimentTrialData) EMMapper.getInstance().getMap().get(runId);
            JSONArray cpuRequestIterationResult = new JSONArray();
            JSONArray memoryRequestIterationResult = new JSONArray();
            if (needIterationData) {
                EMIterationManager emIterationManager = trialData.getEmIterationManager();
                ArrayList<EMIterationData> emIterationDataList = emIterationManager.getIterationDataList();

                for (EMIterationData emIterationData : emIterationDataList) {
                    ArrayList<EMMetricInput> emMetricInputArrayList = trialData.getConfig().getEmConfigObject().getDeployments().getTrainingDeployment().getAllMetrics();
                    for (EMMetricInput emMetricInput : emMetricInputArrayList) {
                        JSONObject iteration_data = new JSONObject();
                        iteration_data.put("iteration_index", emIterationData.getIterationIndex());
                        JSONArray warmup = new JSONArray();
                        JSONArray measurement = new JSONArray();
                        EMIterationMetricResult emIterationMetricResult = emIterationData.getEmIterationResult().getIterationMetricResult(emMetricInput.getName());
                        int counter = 0;
                        for (EMMetricResult warmupResult : emIterationMetricResult.getWarmUpResults()) {
                            warmup.put(warmupResult.toJSON());
                            counter++;
                        }
                        counter = 0;
                        for (EMMetricResult measurementResults : emIterationMetricResult.getMeasurementResults()) {
                            measurement.put(measurementResults.toJSON());
                            counter++;
                        }
                        iteration_data.put("warmup_results", warmup);
                        iteration_data.put("measurement_results", measurement);
                        if (emMetricInput.getName().equalsIgnoreCase("cpuRequest")) {
                            cpuRequestIterationResult.put(iteration_data);
                        }
                        if (emMetricInput.getName().equalsIgnoreCase("cpuRequest")) {
                            memoryRequestIterationResult.put(iteration_data);
                        }
                    }

                }
            }

            JSONObject retJson = trialData.getConfig().getEmConfigObject().toJSON();
            retJson.remove("settings");
            retJson.getJSONObject("info").remove("datasource_info");
            JSONObject cpuRequestResult = new JSONObject("" +
                    "{\"general_info\":{\"mean\":3.98389," +
                    "\"min\":3.6809621160604," +
                    "\"max\":4.10951920556296}}"
            );
            JSONObject memoryRequestResult = new JSONObject("" +
                    "{\"general_info\":{\"max\":1212," +
                    "\"min\":834," +
                    "\"mean\":976.794}}");
            JSONObject jvmMemUsedResult = new JSONObject("" +
                    "{\"general_info\":{\"max\":1123," +
                    "\"min\":769," +
                    "\"mean\":832.63}}");
            JSONObject requestSumResult = new JSONObject("" +
                    "{\"general_info\":{\"min\":2.15," +
                    "\"mean\":31.91," +
                    "\"max\":2107.212121}," +
                    "\"percentile_info\":{\"50p\":0.63," +
                    "\"95p\":8.94," +
                    "\"97p\":64.75," +
                    "\"99p\":82.59," +
                    "\"99.9p\":93.48," +
                    "\"99.99p\":111.5," +
                    "\"99.999p\":198.52," +
                    "\"100p\":30000}}");
            JSONObject requestCountResult = new JSONObject("" +
                    "{\"general_info\":{\"max\":21466," +
                    "\"min\":2.11," +
                    "\"mean\":21045}}");
            JSONArray containersArr = ((JSONObject) retJson.getJSONArray("deployments").get(0)).getJSONArray("containers");
            for (Object container: containersArr){
                JSONObject containerJson = (JSONObject) container;
                containerJson.remove("config");
                JSONArray containerMetrics = containerJson.getJSONArray("container_metrics");
                for (Object contMetObj : containerMetrics) {
                    JSONObject contMetJson = (JSONObject) contMetObj;
                    contMetJson.remove("datasource");
                    contMetJson.remove("query");
                    if (contMetJson.getString("name").equalsIgnoreCase("cpuRequest")) {
                        if (needIterationData) {
                            contMetJson.put("iteration_results", cpuRequestIterationResult);
                        }
                        if (needSummaryResults)
                            contMetJson.put("summary_results", EMUtil.getSummaryResult(trialData, "cpuRequest"));
                    } else if (contMetJson.getString("name").equalsIgnoreCase("memoryRequest")) {
                        if (needIterationData) {
                            contMetJson.put("iteration_results", memoryRequestIterationResult);
                        }
                        if (needSummaryResults)
                            contMetJson.put("summary_results", EMUtil.getSummaryResult(trialData, "memoryRequest"));
                    } else if (contMetJson.getString("name").equalsIgnoreCase("request_sum")) {
                        if (needSummaryResults)
                            contMetJson.put("summary_results", requestSumResult);
                    } else if (contMetJson.getString("name").equalsIgnoreCase("request_count")) {
                        if (needSummaryResults)
                            contMetJson.put("summary_results", requestCountResult);
                    } else if (contMetJson.getString("name").equalsIgnoreCase("JvmMemoryUsed")) {
                        if (needSummaryResults)
                            contMetJson.put("summary_results", jvmMemUsedResult);
                    }
                }
            }
            JSONArray podMetrics = ((JSONObject) retJson.getJSONArray("deployments").get(0)).getJSONArray("pod_metrics");
            for (Object podmetobj : podMetrics) {
                JSONObject podmetJson = (JSONObject) podmetobj;
                podmetJson.remove("datasource");
                podmetJson.remove("query");
                if (podmetJson.getString("name").equalsIgnoreCase("cpuRequest")) {
                    if (needIterationData) {
                        podmetJson.put("iteration_results", cpuRequestIterationResult);
                    }
                    if (needSummaryResults)
                        podmetJson.put("summary_results", EMUtil.getSummaryResult(trialData, "cpuRequest"));
                } else if (podmetJson.getString("name").equalsIgnoreCase("memoryRequest")) {
                    if (needIterationData) {
                        podmetJson.put("iteration_results", memoryRequestIterationResult);
                    }
                    if (needSummaryResults)
                        podmetJson.put("summary_results", EMUtil.getSummaryResult(trialData, "memoryRequest"));
                } else if (podmetJson.getString("name").equalsIgnoreCase("request_sum")) {
                    if (needSummaryResults)
                        podmetJson.put("summary_results", requestSumResult);
                } else if (podmetJson.getString("name").equalsIgnoreCase("request_count")) {
                    if (needSummaryResults)
                        podmetJson.put("summary_results", requestCountResult);
                } else if (podmetJson.getString("name").equalsIgnoreCase("JvmMemoryUsed")) {
                    if (needSummaryResults)
                        podmetJson.put("summary_results", jvmMemUsedResult);
                }
            }
            System.out.println("Input JSON with Metrics :");
            System.out.println(retJson.toString(2));
            return retJson;
        }
    }

    public static class DataPoster {
        public static void sendData(String URL, JSONObject payload) {
            try {
                System.out.print(URL);
                HttpClient httpClient = HttpClientBuilder.create().build();
                try {
                    HttpPost request = new HttpPost(URL);
                    StringEntity params = new StringEntity(payload.toString());
                    request.addHeader("content-type", "application/json; utf-8");
                    request.setEntity(params);
                    HttpResponse response = httpClient.execute(request);
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    System.out.println("Sending Metrics JSON done.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
