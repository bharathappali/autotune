package com.autotune.analyzer.services;

import com.autotune.analyzer.kruizeObject.KruizeObject;
import com.autotune.analyzer.recommendations.RecommendationConfigItem;
import com.autotune.analyzer.recommendations.objects.MappedRecommendationForTimestamp;
import com.autotune.analyzer.recommendations.objects.TermRecommendations;
import com.autotune.analyzer.recommendations.updater.AcceleratorRecommendationUpdater;
import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.common.data.result.ContainerData;
import com.autotune.common.utils.CommonUtils;
import com.autotune.database.service.ExperimentDBService;
import com.autotune.utils.KruizeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.CHARACTER_ENCODING;

@WebServlet(asyncSupported = true)
public class ApplyRecommendation extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateExperiment.class);

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    /**
     * End point to apply recommendations
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            // Set the character encoding of the request to UTF-8
            request.setCharacterEncoding(CHARACTER_ENCODING);
            // Get the values from the request parameters
            String experiment_name = request.getParameter(KruizeConstants.JSONKeys.EXPERIMENT_NAME);
            if (null == experiment_name) {
                response.sendError(500, "Invalid experiment name");
                return;
            }

            Map<String, KruizeObject> mainKruizeExperimentMAP = new ConcurrentHashMap<>();
            new ExperimentDBService().loadExperimentAndRecommendationsFromDBByName(mainKruizeExperimentMAP, experiment_name);
            if (null == mainKruizeExperimentMAP.get(experiment_name)) {
                response.sendError(500, "Invalid experiment name");
                return;
            }

            KruizeObject kruizeObject = mainKruizeExperimentMAP.get(experiment_name);
            if (null == kruizeObject) {
                response.sendError(500, "Invalid kruize object found");
                return;
            }

            String namespace = kruizeObject.getKubernetes_objects().get(0).getNamespace();
            String workloadName = kruizeObject.getKubernetes_objects().get(0).getName();
            if (null == namespace){
                response.sendError(500, "Invalid namespace found in the experiment details please check the experiment");
                return;
            }

            Map<String, ContainerData> containers = kruizeObject.getKubernetes_objects().get(0).getContainerDataMap();
            if (null == containers) {
                response.sendError(500, "No containers found in experiment");
                return;
            }

            for (Map.Entry<String, ContainerData> entry : containers.entrySet()) {
                String containerName = entry.getKey();
                ContainerData containerData = entry.getValue();

                Map<Timestamp, MappedRecommendationForTimestamp> timestampMap = containerData.getContainerRecommendations().getData();
                if (null == timestampMap || timestampMap.isEmpty()) {
                    System.out.println("Timestamp map is empty");
                    continue;
                }


                Map.Entry<Timestamp, MappedRecommendationForTimestamp> latestEntry = null;
                for (Map.Entry<Timestamp, MappedRecommendationForTimestamp> timestampEntry: timestampMap.entrySet()) {
                    if (latestEntry == null || timestampEntry.getKey().after(latestEntry.getKey()))
                        latestEntry = timestampEntry;
                }

                MappedRecommendationForTimestamp latestRecommendation = latestEntry.getValue();
                if (null == latestRecommendation) {
                    System.out.println("latest recommendation is null");
                    continue;
                }


                TermRecommendations shortTermRec = latestRecommendation.getShortTermRecommendations();
                HashMap<AnalyzerConstants.ResourceSetting,
                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem>> existingMap =
                        shortTermRec.getCostRecommendations().getConfig();

                HashMap<AnalyzerConstants.ResourceSetting,
                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem>> updatedRec = new HashMap<>();

                // Process requests
                HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> updatedRequests = new HashMap<>();
                Map<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> existingRequests = existingMap.get(AnalyzerConstants.ResourceSetting.requests);

                for (Map.Entry<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> requestMapEntry: existingRequests.entrySet()) {
                    AnalyzerConstants.RecommendationItem recommendationItem = requestMapEntry.getKey();
                    RecommendationConfigItem recommendationConfigItem = requestMapEntry.getValue();

                    if (recommendationItem == AnalyzerConstants.RecommendationItem.CPU) {
                        updatedRequests.put(recommendationItem, CommonUtils.formatCpuUnits(recommendationConfigItem));
                    } else if (recommendationItem == AnalyzerConstants.RecommendationItem.MEMORY) {
                        updatedRequests.put(recommendationItem, CommonUtils.formatMemoryUnits(recommendationConfigItem));
                    } else {
                        updatedRequests.put(recommendationItem, CommonUtils.formatAcceleratorUnits(recommendationConfigItem));
                    }
                }

                HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> updatedLimits = new HashMap<>();
                Map<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> existingLimits = existingMap.get(AnalyzerConstants.ResourceSetting.limits);

                for (Map.Entry<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> limitsMapEntry: existingLimits.entrySet()) {
                    AnalyzerConstants.RecommendationItem recommendationItem = limitsMapEntry.getKey();
                    RecommendationConfigItem recommendationConfigItem = limitsMapEntry.getValue();

                    if (recommendationItem == AnalyzerConstants.RecommendationItem.CPU) {
                        updatedLimits.put(recommendationItem, CommonUtils.formatCpuUnits(recommendationConfigItem));
                    } else if (recommendationItem == AnalyzerConstants.RecommendationItem.MEMORY) {
                        updatedLimits.put(recommendationItem, CommonUtils.formatMemoryUnits(recommendationConfigItem));
                    } else {
                        updatedLimits.put(recommendationItem, CommonUtils.formatAcceleratorUnits(recommendationConfigItem));
                    }
                }

                if (!updatedRequests.isEmpty())
                    updatedRec.put(AnalyzerConstants.ResourceSetting.requests, updatedRequests);

                if (!updatedLimits.isEmpty())
                    updatedRec.put(AnalyzerConstants.ResourceSetting.limits, updatedLimits);

                System.out.println("Calling updater");
                AcceleratorRecommendationUpdater.updateOrRevertResources(containerName, namespace, workloadName, "job",
                        updatedRec);


//                // Dummy recommendations to apply
//                HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> dummyReq = new HashMap<>();
//                HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> dummyLim = new HashMap<>();
//                HashMap<AnalyzerConstants.ResourceSetting,
//                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem>> dummyRec = new HashMap<>();
//
//                dummyReq.put(AnalyzerConstants.RecommendationItem.CPU, new RecommendationConfigItem(1500.0, "m"));
//                dummyReq.put(AnalyzerConstants.RecommendationItem.MEMORY, new RecommendationConfigItem(512.0, "Mi"));
//
//                dummyLim.put(AnalyzerConstants.RecommendationItem.CPU, new RecommendationConfigItem(1500.0, "m"));
//                dummyLim.put(AnalyzerConstants.RecommendationItem.MEMORY, new RecommendationConfigItem(512.0, "Mi"));
//                dummyLim.put(AnalyzerConstants.RecommendationItem.NVIDIA_GPU_PARTITION_3_CORES_20GB, new RecommendationConfigItem(1.0, ""));
//
//                dummyRec.put(AnalyzerConstants.ResourceSetting.requests, dummyReq);
//                dummyRec.put(AnalyzerConstants.ResourceSetting.limits, dummyLim);
//
//                AcceleratorRecommendationUpdater.updateOrRevertResources(containerName, namespace, workloadName,  "job", dummyRec);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
