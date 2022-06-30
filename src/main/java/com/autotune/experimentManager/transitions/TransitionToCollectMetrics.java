package com.autotune.experimentManager.transitions;

import com.autotune.experimentManager.data.EMMapper;
import com.autotune.experimentManager.data.ExperimentTrialData;
import com.autotune.experimentManager.utils.EMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransitionToCollectMetrics extends AbstractBaseTransition {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransitionToCollectMetrics.class);
    @Override
    public void transit(String runId) {
        LOGGER.info("Executing transition - TransitionToCollectMetrics on thread - {} For RunId - {}", Thread.currentThread().getId(), runId);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        processNextTransition(runId);
    }
}
