/*******************************************************************************
 * Copyright (c) 2020, 2021 Red Hat, IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.autotune;

import com.autotune.analyzer.Analyzer;
import com.autotune.utils.ServerContext;
import com.autotune.experimentManager.core.ExperimentManager;
import com.autotune.experimentManager.utils.EMConstants;
import com.autotune.service.HealthService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.autotune.utils.ServerContext.AUTOTUNE_PORT;
import static com.autotune.utils.ServerContext.HEALTH_SERVICE;

public class Autotune
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Autotune.class);


	public static void main(String[] args) {
		System.out.println("Hello from integration");
		ServletContextHandler context = null;

		disableServerLogging();

		Server server = new Server(AUTOTUNE_PORT);
		context = new ServletContextHandler();
		context.setContextPath(ServerContext.ROOT_CONTEXT);
		server.setHandler(context);
		addAutotuneServlets(context);

		String autotuneMode = System.getenv(EMConstants.EMEnv.AUTOTUNE_MODE);

		if (null != autotuneMode) {
			if (autotuneMode.equalsIgnoreCase(EMConstants.EMEnv.EM_ONLY_MODE)) {
				startAutotuneEMOnly(context);
			} else {
				startAutotuneNormalMode(context);
			}
		} else {
			startAutotuneNormalMode(context);
		}

		try {
			server.start();
		} catch (Exception e) {
			LOGGER.error("Could not start the server!");
			e.printStackTrace();
		}
	}

	private static void addAutotuneServlets(ServletContextHandler context) {
		context.addServlet(HealthService.class, HEALTH_SERVICE);
	}

	private static void disableServerLogging() {
		/* The jetty server creates a lot of server log messages that are unnecessary.
		 * This disables jetty logging. */
		System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
		System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
	}

	private static void startAutotuneEMOnly(ServletContextHandler contextHandler) {
		ExperimentManager.launch(contextHandler);
	}

	private static void startAutotuneNormalMode(ServletContextHandler contextHandler) {
		Analyzer.start(contextHandler);
		ExperimentManager.launch(contextHandler);
	}
}
