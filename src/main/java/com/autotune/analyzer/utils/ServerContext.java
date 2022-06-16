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
package com.autotune.analyzer.utils;

/**
 * Holds the server context of the dependency analyzer.
 */
public class ServerContext
{
	private ServerContext() { }
	public static final int AUTOTUNE_PORT = 8080;

	public static final String ROOT_CONTEXT = "/";
	public static final String UI_CONTEXT = ROOT_CONTEXT + "ui/";
	public static final String HEALTH_SERVICE = ROOT_CONTEXT + "health";
	public static final String METRICS_SERVICE = ROOT_CONTEXT + "metrics";
	public static final String LIST_STACKS = ROOT_CONTEXT + "listStacks";
	public static final String LIST_STACK_LAYERS = ROOT_CONTEXT + "listStackLayers";
	public static final String LIST_STACK_TUNABLES = ROOT_CONTEXT + "listStackTunables";
	public static final String LIST_AUTOTUNE_TUNABLES = ROOT_CONTEXT + "listAutotuneTunables";
	public static final String SEARCH_SPACE = ROOT_CONTEXT + "searchSpace";
	public static final String CREATE_EXPERIMENT_TRIAL = ServerContext.ROOT_CONTEXT + "createExperimentTrial";
	public static final String LIST_TRIAL_STATUS = ServerContext.ROOT_CONTEXT + "listTrialStatus";
	public static final String GET_NAMESPACES = ServerContext.UI_CONTEXT + "getNamespaces";
	public static final String GET_DEPLOYMENTS = ServerContext.UI_CONTEXT + "getDeployments";
}
