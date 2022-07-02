package com.autotune.experimentManager.transitions;

import com.autotune.experimentManager.data.EMMapper;
import com.autotune.experimentManager.data.ExperimentTrialData;
import com.autotune.experimentManager.utils.EMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransitionToInitiateMetricsCollectionPhase extends AbstractBaseTransition{
    private static final Logger LOGGER = LoggerFactory.getLogger(TransitionToInitiateMetricsCollectionPhase.class);
    @Override
    public void transit(String runId) {
        LOGGER.info("Executing transition - TransitionToInitiateMetricsCollectionPhase on thread - {} For RunId - ", Thread.currentThread().getId(), runId);
        ExperimentTrialData currentETD = ((ExperimentTrialData) EMMapper.getInstance().getMap().get(runId));
        currentETD.setStatus(EMUtil.EMExpStatus.COLLECTING_METRICS);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        processNextTransition(runId);
    }
}
