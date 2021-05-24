package com.autotune.experimentManager.core;

import com.autotune.experimentManager.services.CreateExperiment;
import com.autotune.experimentManager.services.GetExperiments;
import com.autotune.experimentManager.settings.EMS;
import com.autotune.experimentManager.utils.EMConstants;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class ExperimentManager {

    public static EMExecutorService emExecutorService;
    public static EMStageProcessor emStageProcessor;
    public static EMScheduledStageProcessor emScheduledStageProcessor;

    public static void initializeEM() {
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

        if (true != performLoadCheck()) {
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

    public static boolean performLoadCheck() {
        // TODO: Read the queue length and fix the max executors setting
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
        context.addServlet(CreateExperiment.class, EMConstants.APIPaths.CREATE_EXPERIMENT);
        context.addServlet(GetExperiments.class, EMConstants.APIPaths.GET_EXPERIMENTS);
    }
}
