# Autotune REST API
The Autotune REST API design is proposed as follows:

##  listStacks
Get the list of application stacks monitored by autotune.

**Request**
`GET /listStacks`

`GET /listStacks?experiment_name=<EXPERIMENT_NAME>`

`curl -H 'Accept: application/json' http://<URL>:<PORT>/listStacks?experiment_name=<EXPERIMENT_NAME>`

**Response**
```
[
    {
        "experiment_name": "galaxies-autotune-min-http-response-time",
        "experiment_id": "7c07cf4db16adcf76bad79394c9e7df2f3b8d8e6942cfa3f7b254b5aec1299b0",
        "objective_function": "request_sum/request_count",
        "hpo_algo_impl": "optuna_tpe",
        "stacks": ["dinogun/galaxies:1.2-jdk-11.0.10_9"],
        "slo_class": "response_time",
        "direction": "minimize"
    },
    {
        "experiment_name": "petclinic-autotune-max-http-throughput",
        "experiment_id": "629ad1c575dd81576c98142aa6de9ddc241de0a9f008586923f200b3a6bc83c6",
        "objective_function": "request_count",
        "hpo_algo_impl": "optuna_tpe",
        "stacks": ["kruize/spring_petclinic:2.2.0-jdk-11.0.8-openj9-0.21.0"],
        "slo_class": "throughput",
        "direction": "maximize"
    }
]
```

## listStackLayers
Returns the list of application stacks monitored by autotune along with layers detected in the stacks.

**Request**
`GET /listStackLayers`

`GET /listStackLayers?experiment_name=<EXPERIMENT_NAME>`

`curl -H 'Accept: application/json' http://<URL>:<PORT>/listStackLayers?experiment_name=<EXPERIMENT_NAME>`

**Response**
```
[
    {
        "experiment_name": "galaxies-autotune-min-http-response-time",
        "experiment_id": "7c07cf4db16adcf76bad79394c9e7df2f3b8d8e6942cfa3f7b254b5aec1299b0",
        "objective_function": "request_sum/request_count",
        "hpo_algo_impl": "optuna_tpe",
        "stacks": [{
            "layers": [
                {
                    "layer_id": "af07fd998199bf2d57f95dc18f2cc2311b72f6de11e7e949b566fcdc5ecb443b",
                    "layer_level": 0,
                    "layer_name": "container",
                    "layer_details": "generic container tunables"
                },
                {
                    "layer_id": "63f4bd430913abffaa6c41a5e05015d5fea23134c99826470c904a7cfe56b40c",
                    "layer_level": 1,
                    "layer_name": "hotspot",
                    "layer_details": "hotspot tunables"
                },
                {
                    "layer_id": "3ec648860dd10049b2488f19ca6d80fc5b50acccdf4aafaedc2316c6eea66741",
                    "layer_level": 2,
                    "layer_name": "quarkus",
                    "layer_details": "quarkus tunables"
                }
            ],
            "stack_name": "dinogun/galaxies:1.2-jdk-11.0.10_9"
        }],
        "slo_class": "response_time",
        "direction": "minimize"
    },
    {
        "experiment_name": "petclinic-autotune-max-http-throughput",
        "experiment_id": "629ad1c575dd81576c98142aa6de9ddc241de0a9f008586923f200b3a6bc83c6",
        "objective_function": "request_count",
        "hpo_algo_impl": "optuna_tpe",
        "stacks": [{
            "layers": [
                {
                    "layer_id": "af07fd998199bf2d57f95dc18f2cc2311b72f6de11e7e949b566fcdc5ecb443b",
                    "layer_level": 0,
                    "layer_name": "container",
                    "layer_details": "generic container tunables"
                }
            ],
            "stack_name": "kruize/spring_petclinic:2.2.0-jdk-11.0.8-openj9-0.21.0"
        }],
        "slo_class": "throughput",
        "direction": "maximize"
    }
]
```

##  listStackTunables
Returns the list of application stacks monitored by autotune along with their tunables.
**Request**
`GET /listStackTunables` gives the tunables and layer information for all the application stacks monitored by autotune.

`GET /listStackTunables?experiment_name=<EXPERIMENT_NAME>` for getting the tunables information of a specific application.

`GET /listStackTunables?experiment_name=<EXPERIMENT_NAME>&layer_name=<LAYER>` for getting tunables of a specific layer for the application.

`curl -H 'Accept: application/json' http://<URL>:<PORT>/listStackTunables?experiment_name=<EXPERIMENT_NAME>`

**Response**
```
[
    {
        "experiment_name": "petclinic-autotune-max-http-throughput",
        "experiment_id": "629ad1c575dd81576c98142aa6de9ddc241de0a9f008586923f200b3a6bc83c6",
        "objective_function": "request_count",
        "hpo_algo_impl": "optuna_tpe",
        "stacks": [{
            "layers": [
                {
                    "layer_id": "af07fd998199bf2d57f95dc18f2cc2311b72f6de11e7e949b566fcdc5ecb443b",
                    "layer_level": 0,
                    "tunables": [
                        {
                            "value_type": "double",
                            "lower_bound": "150.0Mi",
                            "name": "memoryRequest",
                            "step": 1,
                            "query_url": "http://10.111.106.208:9090/api/v1/query?query=container_memory_working_set_bytes{container=\"\", pod=\"kruize/spring_petclinic:2.2.0-jdk-11.0.8-openj9-0.21.0\"}",
                            "upper_bound": "300.0Mi"
                        },
                        {
                            "value_type": "double",
                            "lower_bound": "1.0",
                            "name": "cpuRequest",
                            "step": 0.01,
                            "query_url": "http://10.111.106.208:9090/api/v1/query?query=(container_cpu_usage_seconds_total{container!=\"POD\", pod=\"kruize/spring_petclinic:2.2.0-jdk-11.0.8-openj9-0.21.0\"}[1m])",
                            "upper_bound": "3.0"
                        }
                    ],
                    "layer_name": "container",
                    "layer_details": "generic container tunables"
                }
            ],
            "stack_name": "kruize/spring_petclinic:2.2.0-jdk-11.0.8-openj9-0.21.0"
        }],
        "function_variables": [{
            "value_type": "double",
            "name": "request_count",
            "query_url": "http://10.111.106.208:9090/api/v1/query?query=rate(http_server_requests_seconds_count{exception=\"None\",method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/webjars/**\",}[1m])"
        }],
        "slo_class": "throughput",
        "direction": "maximize"
    }
]
```
##  listAutotuneTunables
Get the tunables supported by autotune for the SLO.

**Request**
`GET /listAutotuneTunables` gives all tunables for all layers in the cluster

`GET /listAutotuneTunables?slo_class=<SLO_CLASS>` gives all tunables for the SLO class

`GET /listAutotuneTunables?slo_class=<SLO_CLASS>&layer_name=<LAYER>` gives tunables for the SLO class and the layer

`curl -H 'Accept: application/json' http://<URL>:<PORT>/listAutotuneTunables?slo_class=<SLO_CLASS>`

**Response**
```
[
    {
        "layer_id": "af07fd998199bf2d57f95dc18f2cc2311b72f6de11e7e949b566fcdc5ecb443b",
        "layer_level": 0,
        "tunables": [
            {
                "value_type": "double",
                "lower_bound": "150.0Mi",
                "name": "memoryRequest",
                "step": 1,
                "query_url": "http://10.111.106.208:9090/api/v1/query?query=container_memory_working_set_bytes{container=\"\", pod=\"$POD$\"}",
                "upper_bound": "300.0Mi"
            },
            {
                "value_type": "double",
                "lower_bound": "1.0",
                "name": "cpuRequest",
                "step": 0.01,
                "query_url": "http://10.111.106.208:9090/api/v1/query?query=(container_cpu_usage_seconds_total{container!=\"POD\", pod=\"$POD$\"}[1m])",
                "upper_bound": "3.0"
            }
        ],
        "layer_name": "container",
        "layer_details": "generic container tunables"
    },
    {
        "layer_id": "63f4bd430913abffaa6c41a5e05015d5fea23134c99826470c904a7cfe56b40c",
        "layer_level": 1,
        "tunables": [{
            "value_type": "integer",
            "lower_bound": "9",
            "name": "MaxInlineLevel",
            "step": 1,
            "query_url": "http://10.111.106.208:9090/api/v1/query?query=jvm_memory_used_bytes{area=\"heap\", container=\"\", pod=\"$POD$\"}",
            "upper_bound": "50"
        }],
        "layer_name": "hotspot",
        "layer_details": "hotspot tunables"
    },
    {
        "layer_id": "3ec648860dd10049b2488f19ca6d80fc5b50acccdf4aafaedc2316c6eea66741",
        "layer_level": 2,
        "tunables": [
            {
                "value_type": "integer",
                "lower_bound": "1",
                "name": "quarkus.thread-pool.core-threads",
                "step": 1,
                "query_url": "none",
                "upper_bound": "10"
            },
            {
                "value_type": "integer",
                "lower_bound": "1",
                "name": "quarkus.thread-pool.queue-size",
                "step": 1,
                "query_url": "none",
                "upper_bound": "100"
            },
            {
                "value_type": "integer",
                "lower_bound": "1",
                "name": "quarkus.hibernate-orm.jdbc.statement-fetch-size",
                "step": 1,
                "query_url": "none",
                "upper_bound": "50"
            }
        ],
        "layer_name": "quarkus",
        "layer_details": "quarkus tunables"
    }
]
```
##  SearchSpace
Generates the search space used for the analysis.

**Request**
`GET /searchSpace` gives the search space for all application stacks monitored.

`GET /searchSpace?experiment_name=<EXPERIMENT_NAME>` gives the search space for a specific application stack.

`curl -H 'Accept: application/json' http://<URL>:<PORT>/searchSpace`

**Response**

```
[
    {
        "experiment_name": "galaxies-autotune-min-http-response-time",
        "experiment_id": "7c07cf4db16adcf76bad79394c9e7df2f3b8d8e6942cfa3f7b254b5aec1299b0",
        "objective_function": "request_sum/request_count",
        "hpo_algo_impl": "optuna_tpe",
        "tunables": [
            {
                "value_type": "double",
                "lower_bound": "150.0Mi",
                "name": "memoryRequest",
                "step": 1,
                "upper_bound": "300.0Mi"
            },
            {
                "value_type": "double",
                "lower_bound": "1.0",
                "name": "cpuRequest",
                "step": 0.01,
                "upper_bound": "3.0"
            },
            {
                "value_type": "integer",
                "lower_bound": "9",
                "name": "MaxInlineLevel",
                "step": 1,
                "upper_bound": "50"
            },
            {
                "value_type": "integer",
                "lower_bound": "1",
                "name": "quarkus.thread-pool.core-threads",
                "step": 1,
                "upper_bound": "10"
            },
            {
                "value_type": "integer",
                "lower_bound": "1",
                "name": "quarkus.thread-pool.queue-size",
                "step": 1,
                "upper_bound": "100"
            },
            {
                "value_type": "integer",
                "lower_bound": "1",
                "name": "quarkus.hibernate-orm.jdbc.statement-fetch-size",
                "step": 1,
                "upper_bound": "50"
            }
        ],
        "direction": "minimize"
    }
]
```

##  Health
Get the status of autotune.

**Request**
`GET /health`

`curl -H 'Accept: application/json' http://<URL>:<PORT>/health`

**Response**

```
Healthy
```

##  CreateExperimentTrial
Create experiment trials using input JSON provided by Analyser module.

**Request**
`POST /createExperimentTrial`

`curl -H 'Accept: application/json' -X POST --data 'copy paste below JSON' http://<URL>:<PORT>/createExperimentTrial`
```
{
    "settings": {
        "trial_settings": {
            "measurement_cycles": "3",
            "warmup_duration": "1min",
            "warmup_cycles": "3",
            "measurement_duration": "1min",
            "iterations": "3"
        },
        "deployment_settings": {
            "deployment_tracking": {
                "trackers": [
                    "training"
                ]
            },
            "deployment_policy": {
                "type": "rollingUpdate"
            }
        }
    },
    "experiment_name": "quarkus-resteasy-autotune-min-http-response-time-db",
    "deployments": {
        "training": {
            "pod_metrics": {
                "request_sum": {
                    "datasource": "prometheus",
                    "query": "rate(http_server_requests_seconds_sum{method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/db\",}[1m])",
                    "name": "request_sum"
                },
                "request_count": {
                    "datasource": "prometheus",
                    "query": "rate(http_server_requests_seconds_count{method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/db\",}[1m])",
                    "name": "request_count"
                }
            },
            "deployment_name": "tfb-qrh-sample",
            "namespace": "default",
            "containers": {
                "kruize/tfb-qrh:1.13.2.F_mm.v1": {
                    "image_name": "kruize/tfb-qrh:1.13.2.F_mm.v1",
                    "container_name": "tfb-server",
                    "container_metrics": {
                        "MaxInlineLevel": {
                            "datasource": "prometheus",
                            "query": "jvm_memory_used_bytes{area=\"heap\", $CONTAINER_LABEL$=\"\", $POD_LABEL$=\"$POD$\"}",
                            "name": "MaxInlineLevel"
                        },
                        "memoryRequest": {
                            "datasource": "prometheus",
                            "query": "container_memory_working_set_bytes{$CONTAINER_LABEL$=\"\", $POD_LABEL$=\"$POD$\"}",
                            "name": "memoryRequest"
                        },
                        "cpuRequest": {
                            "datasource": "prometheus",
                            "query": "(container_cpu_usage_seconds_total{$CONTAINER_LABEL$!=\"POD\", $POD_LABEL$=\"$POD$\"}[1m])",
                            "name": "cpuRequest"
                        }
                    },
                    "config": {
                        "0": {
                            "requests": {
                                "cpu": {
                                    "amount": "2.11",
                                    "format": "",
                                    "additionalProperties": {}
                                },
                                "memory": {
                                    "amount": "160.0",
                                    "format": "",
                                    "additionalProperties": {}
                                }
                            },
                            "env": [
                                {
                                    "name": "JAVA_OPTIONS",
                                    "additionalProperties": {},
                                    "value": " -server -XX:MaxRAMPercentage=70 -XX:+AllowParallelDefineClass -XX:MaxInlineLevel=21 -XX:+UseZGC -XX:+TieredCompilation -Dquarkus.thread-pool.queue-size=27 -Dquarkus.thread-pool.core-threads=9"
                                },
                                {
                                    "name": "JDK_JAVA_OPTIONS",
                                    "additionalProperties": {},
                                    "value": " -server -XX:MaxRAMPercentage=70 -XX:+AllowParallelDefineClass -XX:MaxInlineLevel=21 -XX:+UseZGC -XX:+TieredCompilation -Dquarkus.thread-pool.queue-size=27 -Dquarkus.thread-pool.core-threads=9"
                                }
                            ],
                            "limits": {
                                "cpu": {
                                    "amount": "2.11",
                                    "format": "",
                                    "additionalProperties": {}
                                },
                                "memory": {
                                    "amount": "160.0",
                                    "format": "",
                                    "additionalProperties": {}
                                }
                            }
                        }
                    }
                }
            },
            "type": "training"
        }
    },
    "experiment_id": "04c99daec35563782c29f17ebe568ea96065a7b20b93eb47c225a2f2ad769445",
    "datasource_info": {
        "name": "prometheus",
        "url": "http://10.101.144.137:9090"
    },
    "trial_info": {
        "trial_id": "",
        "trial_num": 0,
        "trial_result_url": "http://localhost:8080/listExperiments?experiment_name=quarkus-resteasy-autotune-min-http-response-time-db"
    },
    "namespace": "default"
}

```

**Response**
```
201
```


### List Trial Status

**Request**
`GET /listTrialStatus` Gives the status of the Trial(s)


`GET /listTrialStatus?exp_name=<experiment name>` Gives the Trial status of the trials in a particular experiment

Example for `exp_name` set to `quarkus-resteasy-autotune-min-http-response-time-db`
**Response**
```
[
    {
        "experiment_name": "quarkus-resteasy-autotune-min-http-response-time-db",
        "deployments": [{
            "pod_metrics": [
                {
                    "name": "request_sum",
                    "dataSource": "prometheus"
                },
                {
                    "name": "request_count",
                    "dataSource": "prometheus"
                }
            ],
            "deployment_name": "tfb-qrh-sample",
            "namespace": "default",
            "containers": [{
                "image_name": "kruize/tfb-qrh:1.13.2.F_et17",
                "container_name": "tfb-server",
                "container_metrics": [
                    {
                        "summary_results" : {
                            "general_info": {
                                "min": 1836408,
                                "max": 19167073,
                                "mean": 18765576
                            }
                        },
                        "name": "memoryRequest"
                    },
                    {
                        "summary_results": {
                            "general_info": {
                                "min": 0.01836408,
                                "max": 0.019167073,
                                "mean": 0.018765576
                            }
                        },
                        "name": "cpuRequest"
                    }
                ]
            }],
            "type": "training"
        }],
        "experiment_id": "04c99daec35563782c29f17ebe568ea96065a7b20b93eb47c225a2f2ad769445",
        "info": {"trial_info": {
            "trial_id": "",
            "trial_num": 1,
            "trial_result_url": "http://localhost:8080/listExperiments?experiment_name=quarkus-resteasy-autotune-min-http-response-time-db"
        }},
        "status": "COMPLETED"
    }
]
```

`GET /listTrialStatus?exp_name=<experiment name>&status=<status>` Gives the Trial status of the trials in a particular experiment which matches the given status

Example for `status` as `COMPLETED`


**Response**

```
[
    {
        "experiment_name": "quarkus-resteasy-autotune-min-http-response-time-db",
        "deployments": [{
            "pod_metrics": [
                {
                    "name": "request_sum",
                    "dataSource": "prometheus"
                },
                {
                    "name": "request_count",
                    "dataSource": "prometheus"
                }
            ],
            "deployment_name": "tfb-qrh-sample",
            "namespace": "default",
            "containers": [{
                "image_name": "kruize/tfb-qrh:1.13.2.F_et17",
                "container_name": "tfb-server",
                "container_metrics": [
                    {
                        "summary_results" : {
                            "general_info": {
                                "min": 1536408,
                                "max": 1767073,
                                "mean": 16765576
                            }
                        },
                        "name": "memoryRequest"
                    },
                    {
                        "summary_results": {
                            "general_info": {
                                "min": 0.01536408,
                                "max": 0.017167073,
                                "mean": 0.016765576
                            }
                        },
                        "name": "cpuRequest"
                    }
                ]
            }],
            "type": "training"
        }],
        "experiment_id": "04c99daec35563782c29f17ebe568ea96065a7b20b93eb47c225a2f2ad769445",
        "info": {"trial_info": {
            "trial_id": "",
            "trial_num": 1,
            "trial_result_url": "http://localhost:8080/listExperiments?experiment_name=quarkus-resteasy-autotune-min-http-response-time-db"
        }},
        "status": "COMPLETED"
    },
    {
        "experiment_name": "quarkus-resteasy-autotune-min-http-response-time-db",
        "deployments": [{
            "pod_metrics": [
                {
                    "name": "request_sum",
                    "dataSource": "prometheus"
                },
                {
                    "name": "request_count",
                    "dataSource": "prometheus"
                }
            ],
            "deployment_name": "tfb-qrh-sample",
            "namespace": "default",
            "containers": [{
                "image_name": "kruize/tfb-qrh:1.13.2.F_et17",
                "container_name": "tfb-server",
                "container_metrics": [
                    {
                        "summary_results" : {
                            "general_info": {
                                "min": 1836408,
                                "max": 19167073,
                                "mean": 18765576
                            }
                        },
                        "name": "memoryRequest"
                    },
                    {
                        "summary_results": {
                            "general_info": {
                                "min": 0.01836408,
                                "max": 0.019167073,
                                "mean": 0.018765576
                            }
                        },
                        "name": "cpuRequest"
                    }
                ]
            }],
            "type": "training"
        }],
        "experiment_id": "04c99daec35563782c29f17ebe568ea96065a7b20b93eb47c225a2f2ad769445",
        "info": {"trial_info": {
            "trial_id": "",
            "trial_num": 2,
            "trial_result_url": "http://localhost:8080/listExperiments?experiment_name=quarkus-resteasy-autotune-min-http-response-time-db"
        }},
        "status": "COMPLETED"
    },
]
```

`GET /listTrialStatus?exp_name=<experiment name>&verbose=<true/false>` Gives the Trial status of the trials in a particular experiment with detailed output of iterations and cycles and metrics collected at each phase. By default verbose is `false` and the api returns only summary if available

Example for `verbose` is set to `true`


**Response**


```
[
    {
        "experiment_name": "quarkus-resteasy-autotune-min-http-response-time-db",
        "deployments": [{
            "pod_metrics": [
                
            ],
            "deployment_name": "tfb-qrh-sample",
            "namespace": "default",
            "containers": [{
                "image_name": "kruize/tfb-qrh:1.13.2.F_et17",
                "container_name": "tfb-server",
                "container_metrics": [
                    {
                        "iteration_results": [
                            {
                                "warmup_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.028764741,
                                            "max": 0.028764741,
                                            "mean": 0.028764741
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.02646923,
                                            "max": 0.02646923,
                                            "mean": 0.02646923
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.015272777,
                                            "max": 0.03555568,
                                            "mean": 0.025765896
                                        }
                                    }
                                ],
                                "iteration_index": 1,
                                "measurement_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.015272777,
                                            "max": 0.03555568,
                                            "mean": 0.025803935
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.014145565,
                                            "max": 0.03555568,
                                            "mean": 0.024007667
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.014145565,
                                            "max": 0.039553355,
                                            "mean": 0.024823695
                                        }
                                    }
                                ]
                            },
                            {
                                "warmup_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.20012215,
                                            "max": 0.20012215,
                                            "mean": 0.20012215
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.023426516,
                                            "max": 0.023426516,
                                            "mean": 0.023426516
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017135207,
                                            "max": 0.023426516,
                                            "mean": 0.020118417
                                        }
                                    }
                                ],
                                "iteration_index": 2,
                                "measurement_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017135207,
                                            "max": 0.024063969,
                                            "mean": 0.02155136
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017135207,
                                            "max": 0.03490772,
                                            "mean": 0.023852905
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.015921338,
                                            "max": 0.03490772,
                                            "mean": 0.023242423
                                        }
                                    }
                                ]
                            },
                            {
                                "warmup_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.20822306,
                                            "max": 0.20822306,
                                            "mean": 0.20822306
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.013513867,
                                            "max": 0.013513867,
                                            "mean": 0.013513867
                                        }
                                    }
                                ],
                                "iteration_index": 3,
                                "measurement_results": []
                            }
                        ],
                        "name": "cpuRequest"
                    }
                ]
            }],
            "type": "training"
        }],
        "experiment_id": "04c99daec35563782c29f17ebe568ea96065a7b20b93eb47c225a2f2ad769445",
        "info": {"trial_info": {
            "trial_id": "",
            "trial_num": 2,
            "trial_result_url": "http://localhost:8080/listExperiments?experiment_name=quarkus-resteasy-autotune-min-http-response-time-db"
        }},
        "status": "COLLECTING_METRICS"
    },
    {
        "experiment_name": "quarkus-resteasy-autotune-min-http-response-time-db",
        "deployments": [{
            "pod_metrics": [
                
            ],
            "deployment_name": "tfb-qrh-sample",
            "namespace": "default",
            "containers": [{
                "image_name": "kruize/tfb-qrh:1.13.2.F_et17",
                "container_name": "tfb-server",
                "container_metrics": [
                    {
                        "iteration_results": [
                            {
                                "warmup_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {}
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.01836408,
                                            "max": 0.019167073,
                                            "mean": 0.018765576
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017719252,
                                            "max": 0.02040143,
                                            "mean": 0.018912958
                                        }
                                    }
                                ],
                                "iteration_index": 1,
                                "measurement_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.008669471,
                                            "max": 0.02040143,
                                            "mean": 0.017281653
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.008669471,
                                            "max": 0.02040143,
                                            "mean": 0.017522506
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.008669471,
                                            "max": 0.026968002,
                                            "mean": 0.018652223
                                        }
                                    }
                                ]
                            },
                            {
                                "warmup_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {}
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.018662803,
                                            "max": 0.01922652,
                                            "mean": 0.018944662
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017208576,
                                            "max": 0.01922652,
                                            "mean": 0.018489202
                                        }
                                    }
                                ],
                                "iteration_index": 2,
                                "measurement_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017208576,
                                            "max": 0.01922652,
                                            "mean": 0.01831504
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017208576,
                                            "max": 0.01922652,
                                            "mean": 0.018352233
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017208576,
                                            "max": 0.019931443,
                                            "mean": 0.018316248
                                        }
                                    }
                                ]
                            },
                            {
                                "warmup_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.018642442,
                                            "max": 0.018642442,
                                            "mean": 0.018642442
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.017879914,
                                            "max": 0.029544305,
                                            "mean": 0.022022223
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.013003985,
                                            "max": 0.029544305,
                                            "mean": 0.02083616
                                        }
                                    }
                                ],
                                "iteration_index": 3,
                                "measurement_results": [
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.009872468,
                                            "max": 0.029544305,
                                            "mean": 0.019016853
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.009872468,
                                            "max": 0.029544305,
                                            "mean": 0.019888066
                                        }
                                    },
                                    {
                                        "summary": {"result_outcome": "SUCCESS"},
                                        "general_info": {
                                            "min": 0.008647251,
                                            "max": 0.029544305,
                                            "mean": 0.018989839
                                        }
                                    }
                                ]
                            }
                        ],
                        "name": "cpuRequest"
                    }
                ]
            }],
            "type": "training"
        }],
        "experiment_id": "04c99daec35563782c29f17ebe568ea96065a7b20b93eb47c225a2f2ad769445",
        "info": {"trial_info": {
            "trial_id": "",
            "trial_num": 1,
            "trial_result_url": "http://localhost:8080/listExperiments?experiment_name=quarkus-resteasy-autotune-min-http-response-time-db"
        }},
        "status": "COMPLETED"
    }
]
```