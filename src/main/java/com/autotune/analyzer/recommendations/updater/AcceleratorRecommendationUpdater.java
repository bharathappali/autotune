package com.autotune.analyzer.recommendations.updater;

import com.autotune.analyzer.recommendations.RecommendationConfigItem;
import com.autotune.analyzer.utils.AnalyzerConstants;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcceleratorRecommendationUpdater {
    public static void updateOrRevertResources(String containerName,
                                               String namespace,
                                               String workloadName,
                                               String koType,
                                               HashMap<AnalyzerConstants.ResourceSetting,
                                                       HashMap<AnalyzerConstants.RecommendationItem,
                                                               RecommendationConfigItem>> recommendations )  {

        System.out.println("Updating Resources");
        Map<String, Map<String, Quantity>> originalResourcesRequests = new HashMap<>();
        Map<String, Map<String, Quantity>> originalResourcesLimits = new HashMap<>();

        try (KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
            if (koType.equalsIgnoreCase("deployment")) {
                kubernetesClient.apps().deployments().inNamespace(namespace).list().getItems().forEach(deployment -> {
                    deployment.getSpec().getTemplate().getSpec().getContainers().stream().filter(
                            container -> container.getName().equals(containerName)
                    ).forEach(container -> {
                        originalResourcesRequests.put(containerName, container.getResources().getRequests());
                        originalResourcesLimits.put(containerName, container.getResources().getLimits());

                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> requestRecommendation =
                                recommendations.get(AnalyzerConstants.ResourceSetting.requests);

                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> limitsRecommendation =
                                recommendations.get(AnalyzerConstants.ResourceSetting.limits);

                        Map<String, Quantity> requestMap = new HashMap<>();
                        Map<String, Quantity> limitsMap = new HashMap<>();

                        for (Map.Entry<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> entry : requestRecommendation.entrySet()) {
                            System.out.println("Recommendation: " + entry.getKey().toString());
                            System.out.println("Quantity: " + entry.getValue().getAmount().intValue() + " | Format: entry.getValue().getFormat()");
                            requestMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue()), entry.getValue().getFormat()));
                        }

                        for (Map.Entry<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> entry : limitsRecommendation.entrySet()) {
                            System.out.println("Recommendation: " + entry.getKey().toString());
                            System.out.println("Quantity: " + entry.getValue().getAmount().intValue() + " | Format: entry.getValue().getFormat()");
                            if (entry.getKey().toString().contains("nvidia")) {
                                limitsMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue())));
                            } else {
                                limitsMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue()), entry.getValue().getFormat()));
                            }
                        }


                        container.getResources().setRequests(requestMap);
                        container.getResources().setLimits(limitsMap);

                        kubernetesClient.apps().deployments()
                                .inNamespace(namespace)
                                .withName(deployment.getMetadata().getName())
                                .patch(deployment);
                    });
                });
            } else if (koType.equalsIgnoreCase("statefulset")) {
                kubernetesClient.apps().statefulSets().inNamespace(namespace).list().getItems().forEach(statefulSet -> {
                    statefulSet.getSpec().getTemplate().getSpec().getContainers().stream().filter(
                            container -> container.getName().equals(containerName)
                    ).forEach(container -> {
                        originalResourcesRequests.put(containerName, container.getResources().getRequests());
                        originalResourcesLimits.put(containerName, container.getResources().getLimits());

                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> requestRecommendation =
                                recommendations.get(AnalyzerConstants.ResourceSetting.requests);

                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> limitsRecommendation =
                                recommendations.get(AnalyzerConstants.ResourceSetting.limits);

                        Map<String, Quantity> requestMap = new HashMap<>();
                        Map<String, Quantity> limitsMap = new HashMap<>();

                        for (Map.Entry<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> entry : requestRecommendation.entrySet()) {
                            requestMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue()), entry.getValue().getFormat()));
                        }

                        for (Map.Entry<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> entry : limitsRecommendation.entrySet()) {
                            if (entry.getKey().toString().contains("nvidia")) {
                                limitsMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue())));
                            } else {
                                limitsMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue()), entry.getValue().getFormat()));
                            }
                        }

                        container.getResources().setRequests(requestMap);
                        container.getResources().setLimits(limitsMap);

                        kubernetesClient.apps().statefulSets()
                                .inNamespace(namespace)
                                .withName(statefulSet.getMetadata().getName())
                                .patch(statefulSet);
                    });
                });
            } else if (koType.equalsIgnoreCase("job")) {
                System.out.println("inside job edit");
                Job existingJob = kubernetesClient.batch().jobs().inNamespace(namespace).withName(workloadName).get();
                if (existingJob == null) {
                    System.out.println("Job not found!");
                    return;
                }
                PodTemplateSpec podTemplate = existingJob.getSpec().getTemplate();
                List<Container> containers = podTemplate.getSpec().getContainers();

                for (Container container : containers) {
                    if (container.getName().equals(containerName)) {
                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> requestRecommendation =
                                recommendations.get(AnalyzerConstants.ResourceSetting.requests);

                        HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> limitsRecommendation =
                                recommendations.get(AnalyzerConstants.ResourceSetting.limits);

                        Map<String, Quantity> requestMap = new HashMap<>();
                        Map<String, Quantity> limitsMap = new HashMap<>();

                        for (Map.Entry<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> entry : requestRecommendation.entrySet()) {
                            System.out.println("Recommendation: " + entry.getKey().toString());
                            System.out.println("Quantity: " + entry.getValue().getAmount().intValue() + " | Format:" + entry.getValue().getFormat());
                            requestMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue()), entry.getValue().getFormat()));
                        }

                        for (Map.Entry<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> entry : limitsRecommendation.entrySet()) {
                            System.out.println("Recommendation: " + entry.getKey().toString());
                            System.out.println("Quantity: " + entry.getValue().getAmount().intValue() + " | Format:" + entry.getValue().getFormat());
                            if (entry.getKey().toString().contains("nvidia")) {
                                limitsMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue())));
                            } else {
                                limitsMap.put(entry.getKey().toString(), new Quantity(String.valueOf(entry.getValue().getAmount().intValue()), entry.getValue().getFormat()));
                            }
                        }

                        container.getResources().setRequests(requestMap);
                        container.getResources().setLimits(limitsMap);
                    }
                }

                Map<String, String> labels = podTemplate.getMetadata().getLabels();
                if (labels != null) {
                    labels.remove("controller-uid");
                    labels.remove("batch.kubernetes.io/controller-uid");
                }

                existingJob.getSpec().setSelector(null);

                kubernetesClient.batch().jobs().inNamespace(namespace).withName(workloadName).delete();
                kubernetesClient.batch().jobs().inNamespace(namespace).createOrReplace(existingJob);
            }
            kubernetesClient.close();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}
