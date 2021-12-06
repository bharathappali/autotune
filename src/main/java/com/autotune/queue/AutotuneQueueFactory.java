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
package com.autotune.queue;

import com.autotune.utils.AutotuneUtils.QueueName;

/**
 * AutotuneQueueFactory return object of a Queue, In autotune we are using two queues,
 * RECMGRQUEUE work between Dependency Analyzer and Recommendation Manager.
 * EXPMGRQUEUE work between Recommendation Manager and Experiment Manager
 * @author bipkumar
 *
 */
public class AutotuneQueueFactory {
	private AutotuneQueueFactory() { }

	public static AutotuneQueue getQueue(String queueName) {
		
		if (queueName == null) {
			return null;
		}
		if (queueName.equalsIgnoreCase(QueueName.RECMGRQUEUE.name())) {
			return RecommendationManagerQueue.getInstance();
			
		} else if (queueName.equalsIgnoreCase(QueueName.EXPMGRQUEUE.name())) {
			return ExperimentManagerQueue.getInstance();
		}
		
		return null;
	}
}
