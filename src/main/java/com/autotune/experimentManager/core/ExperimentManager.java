package com.autotune.experimentManager.core;

import com.autotune.experimentManager.services.GetTrialStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.autotune.experimentManager.services.CreateExperiment;
import com.autotune.experimentManager.services.GetExperiments;
import com.autotune.experimentManager.settings.EMS;
import com.autotune.experimentManager.utils.EMConstants;

import org.eclipse.jetty.servlet.ServletContextHandler;

public class ExperimentManager {

    public static EMExecutorService emExecutorService;
    public static EMStageProcessor emStageProcessor;
    public static EMScheduledStageProcessor emScheduledStageProcessor;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExperimentManager.class);


    public static void initializeEM() {
        LOGGER.info(EMConstants.Logs.ExperimentManager.INITIALIZE_EM);
        emExecutorService = EMExecutorService.getService();
        emStageProcessor = new EMStageProcessor();
        emScheduledStageProcessor = new EMScheduledStageProcessor();
    }

    public static void launch(ServletContextHandler contextHandler) {
        initializeEM();
        addEMServlets(contextHandler);

        if (true != performEnvCheck()) {
            // Raise an exception or error
        }

        if (true != performConfigCheck()) {
            // Raise an exception or error
        }

        // Set the initial executors based on settings
        emExecutorService.createExecutors(EMS.getController().getCurrentExecutors());
        emExecutorService.initiateExperimentStageProcessor(emStageProcessor);
        emExecutorService.initiateExperimentStageProcessor(emScheduledStageProcessor);

    }

    public static boolean performEnvCheck() {
        // TODO: Read the system limit's and detect the environment
        // Parking it for future implementation
        return true;
    }

    public static boolean performConfigCheck() {
        // TODO: Read the config and change settings accordingly
        return true;
    }

    public static void notifyQueueProcessor() {
        emStageProcessor.notifyProcessor();
    }

    public static void notifyScheduledQueueProcessor() {
        emScheduledStageProcessor.notifyProcessor();
    }

    private static void addEMServlets(ServletContextHandler context) {
        LOGGER.info(EMConstants.Logs.ExperimentManager.ADD_EM_SERVLETS);
        System.out.println(EMConstants.Logs.ExperimentManager.ADD_EM_SERVLETS);
        context.addServlet(CreateExperiment.class, EMConstants.APIPaths.CREATE_EXPERIMENT);
        context.addServlet(GetExperiments.class, EMConstants.APIPaths.GET_EXPERIMENTS);
        context.addServlet(GetTrialStatus.class, EMConstants.APIPaths.GET_TRIAL_STATUS);
    }
}
