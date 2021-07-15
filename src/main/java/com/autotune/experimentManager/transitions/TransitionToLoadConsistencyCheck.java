package com.autotune.experimentManager.transitions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransitionToLoadConsistencyCheck extends AbstractBaseTransition{
    private static final Logger LOGGER = LoggerFactory.getLogger(TransitionToLoadConsistencyCheck.class);
    @Override
    public void transit(String runId) {
        LOGGER.info("Executing transition - TransitionToLoadConsistencyCheck on thread - {}", Thread.currentThread().getId());
        System.out.println("Executing transition - TransitionToLoadConsistencyCheck on thread - {}" + Thread.currentThread().getId() + "For RunId - " + runId);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        processNextTransition(runId);
    }
}
