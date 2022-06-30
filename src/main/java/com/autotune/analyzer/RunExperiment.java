package com.autotune.analyzer;

import com.autotune.common.data.experiments.ExperimentTrial;
import com.autotune.utils.HttpUtils;
import com.autotune.utils.ServerContext;
import com.autotune.utils.TrialHelpers;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URL;

import static com.autotune.utils.AnalyzerConstants.ServiceConstants.DEPLOYMENT_NAME;
import static com.autotune.utils.ServerContext.EXPERIMENT_MANAGER_CREATE_TRIAL_END_POINT;
import static com.autotune.utils.ServerContext.OPTUNA_TRIALS_END_POINT;

public class RunExperiment implements Runnable
{
	private static final Logger LOGGER = LoggerFactory.getLogger(RunExperiment.class);
	private static final int MAX_NUMBER_OF_TRIALS = 1;
	private final AutotuneExperiment autotuneExperiment;

	public RunExperiment(AutotuneExperiment autotuneExperiment) {
		this.autotuneExperiment = autotuneExperiment;
	}

	/**
	 *
	 */
	public synchronized void receive() {
		while (true) {
			try {
				wait();
				break;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.info("Thread Interrupted");
			}
		}
	}

	/**
	 *
	 */
	public synchronized void send() {
		notify();
	}

	@Override
	public void run() {

		String operation = "EXP_TRIAL_GENERATE_NEW";
		String experimentId = autotuneExperiment.getAutotuneObject().getExperimentId();
		StringBuilder searchSpaceUrl = new StringBuilder(ServerContext.SEARCH_SPACE_END_POINT)
				.append("?")
				.append(DEPLOYMENT_NAME)
				.append("=")
				.append(autotuneExperiment.getDeploymentName());
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", experimentId);
		jsonObject.put("url", searchSpaceUrl.toString());
		jsonObject.put("operation", operation);

		for (int i = 0; i<MAX_NUMBER_OF_TRIALS; i++) {
			try {
				URL experimentTrialsURL = new URL(OPTUNA_TRIALS_END_POINT);
				autotuneExperiment.setExperimentStatus("[ ]: Getting Experiment Trial Config");
				/* STEP 1: Send a request for a trail config from Optuna */
				int trialNumber = Integer.parseInt(HttpUtils.postRequest(experimentTrialsURL, jsonObject.toString()));
				autotuneExperiment.setExperimentStatus("[ " + trialNumber + " ]: Received Experiment Trial Config");
				System.out.println("Optuna Trial No :" + trialNumber);
				StringBuilder trialConfigUrl = new StringBuilder(OPTUNA_TRIALS_END_POINT)
						.append("?id=")
						.append(experimentId)
						.append("&trial_number=")
						.append(trialNumber);
				URL trialConfigURL = new URL(trialConfigUrl.toString());

				/* STEP 2: We got a trial id from Optuna, now use that to get the actual config */
				String trialConfigJson = HttpUtils.getDataFromURL(trialConfigURL, "");
				autotuneExperiment.setExperimentStatus("[ " + trialNumber + " ]: Received Experiment Trial Config Info");
				System.out.println(trialConfigJson);

				/* STEP 3: Now create a trial to be passed to experiment manager to run */
				ExperimentTrial experimentTrial = TrialHelpers.createDefaultExperimentTrial(trialNumber,
						autotuneExperiment,
						trialConfigJson);
				autotuneExperiment.experimentTrials.add(experimentTrial);
				JSONObject experimentTrialJSON = TrialHelpers.experimentTrialToJSON(experimentTrial);

				/* STEP 4: Send trial to EM */
				autotuneExperiment.setExperimentStatus("[ " + trialNumber + " ]: Sending Experiment Trial Config Info to EM");
				System.out.println(experimentTrialJSON.toString(4));
				URL createExperimentTrialURL = new URL(EXPERIMENT_MANAGER_CREATE_TRIAL_END_POINT);
				String runId = HttpUtils.postRequest(createExperimentTrialURL, experimentTrialJSON.toString());
				autotuneExperiment.setExperimentStatus("[ " + trialNumber + " ]: Received Run Id: " + runId);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
