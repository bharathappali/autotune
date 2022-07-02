package com.autotune.experimentManager.transitions;

import com.autotune.common.experiments.ExperimentTrial;
import com.autotune.experimentManager.core.EMIterationManager;
import com.autotune.experimentManager.data.EMMapper;
import com.autotune.experimentManager.data.ExperimentTrialData;
import com.autotune.experimentManager.data.iteration.EMIterationData;
import com.autotune.experimentManager.utils.EMConstants;
import com.autotune.experimentManager.utils.EMUtil;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransitionToDeployConfig extends AbstractBaseTransition{
    private static final Logger LOGGER = LoggerFactory.getLogger(TransitionToDeployConfig.class);

    @Override
    public void transit(String runId) {
        ExperimentTrialData trialData = (ExperimentTrialData) EMMapper.getInstance().getMap().get(runId);
        EMIterationManager emIterationManager = trialData.getEmIterationManager();
        KubernetesClient client = new DefaultKubernetesClient();
        if(emIterationManager.getCurrentIteration() == 1) {
            ExperimentTrial experimentTrial = trialData.getExperimentTrial();
            experimentTrial.getTrialDetails().forEach((tracker, trialDetails) -> {
                trialDetails.getPodContainers().forEach((imageName, podContainer) -> {
                    podContainer.getTrialConfigs().forEach((trialNumber, containerConfigData) -> {
                        Deployment modifiedDeployment = EMUtil.getModifiedDeployment(
                                trialDetails.getDeploymentNameSpace(),
                                trialDetails.getDeploymentName(),
                                containerConfigData);
                        client.apps()
                                .deployments()
                                .inNamespace(trialDetails.getDeploymentNameSpace())
                                .withName(trialDetails.getDeploymentName())
                                .createOrReplace(modifiedDeployment);
                    });
                });
            });
        } else {
            LOGGER.info("Restarting the pod ... ");
            client.apps().deployments().inNamespace(trialData.getConfig().getDeploymentNamespace())
                    .withName(trialData.getConfig().getDeploymentName())
                    .rolling()
                    .restart();
            LOGGER.info("Done.");
        }
        processNextTransition(runId);
    }
}
