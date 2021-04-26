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

package com.autotune.experimentManager.utils;

import com.autotune.experimentManager.finiteStateMachine.api.EMState;

import java.util.Set;

/**
 * Utility class for keeping the experiment manager specific constants, common code, utility functions etc.
 */

public final class EMUtils {

    private EMUtils() {

    }

    public static final String DEFAULT_EVENT_NAME = "event";
    public static final String DEFAULT_TRANSITION_NAME = "transition";
    public static String NAMESPACE="default";

    public static String NEW_DEPLOYMENT_NAME_SUFIX="autotune-trial";

    public static String TRIALS ="trials";
    public static String DEPLOYMENT_NAME_KEY = "deployment_name";
    public static String UPDATE_CONFIG = "update_config";
    public static String ID="id";
    public static String APP_VERSION="app-version";
    public static String DEPLOYMENT_NAME="deployment_name";
    public static String TRIAL_NUM= "trial_num";
    public static String TRIAL_RUN="trial_run";
    public static String TRIAL_MEASUREMENT_TIME="trial_measurement_time";
    public static String METRICS="metrics";
    public static String NAME="name";
    public static String QUERY="query";
    public static String DATASOURCE="datasource";

    public static String dumpFSMStates(final Set<EMState> states) {
        StringBuilder result = new StringBuilder();
        for (EMState state : states) {
            result.append(state.getName()).append(";");
        }
        return result.toString();
    }
}
