"""
Copyright (c) 2024, 2024 Red Hat, IBM Corporation and others.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""
import copy
import json

import pytest
import sys
import time
import shutil
sys.path.append("../../")

from helpers.fixtures import *
from helpers.generate_rm_jsons import *
from helpers.kruize import *
from helpers.short_term_list_reco_json_schema import *
from helpers.list_reco_json_validate import *
from helpers.list_datasources_json_validate import *
from helpers.utils import *
from helpers.utils import benchmarks_install
from helpers.utils import clone_repo
from helpers.utils import apply_tfb_load
from helpers.utils import wait_for_container_to_complete
from helpers.list_metadata_json_validate import *
from helpers.list_metadata_json_schema import *
from helpers.list_metadata_json_verbose_true_schema import *
from helpers.list_metadata_json_cluster_name_without_verbose_schema import *
from helpers.list_metric_profiles_validate import *
from helpers.list_metric_profiles_without_parameters_schema import *
from helpers.short_term_list_reco_json_schema import *
from helpers.list_reco_json_validate import *
from helpers.import_metadata_json_validate import *
from pathlib import Path

current_directory = Path(__file__).resolve().parent
# Navigate up 4 levels dynamically using 'parents'
base_dir = current_directory.parents[3]  # (index 3 because it's zero-based)
metric_profile_dir = base_dir / 'manifests' / 'autotune' / 'performance-profiles'


@pytest.mark.test_e2e
def test_list_recommendations_multiple_exps_for_datasource_workloads(cluster_type):
    """
    Test Description: This test validates list recommendations for multiple experiments posted using different json files
    """
    clone_repo("https://github.com/kruize/benchmarks")
    benchmarks_install()
    container_id = apply_tfb_load("default", cluster_type)
    print(container_id)

    # list all datasources
    form_kruize_url(cluster_type)

    # Get the datasources name
    datasource_name = None
    response = list_datasources(datasource_name)

    list_datasources_json = response.json()

    assert response.status_code == SUCCESS_200_STATUS_CODE

    # Validate the json against the json schema
    errorMsg = validate_list_datasources_json(list_datasources_json, list_datasources_json_schema)
    assert errorMsg == ""


    # Import datasource metadata
    input_json_file = "../json_files/import_metadata.json"

    response = delete_metadata(input_json_file)
    print("delete metadata = ", response.status_code)

    # Import metadata using the specified json
    response = import_metadata(input_json_file)
    metadata_json = response.json()

    # Validate the json against the json schema
    errorMsg = validate_import_metadata_json(metadata_json, import_metadata_json_schema)
    assert errorMsg == ""


    # Display metadata from prometheus-1 datasource
    json_data = json.load(open(input_json_file))
    datasource = json_data['datasource_name']

    response = list_metadata(datasource)

    list_metadata_json = response.json()
    assert response.status_code == SUCCESS_200_STATUS_CODE

    # Validate the json against the json schema
    errorMsg = validate_list_metadata_json(list_metadata_json, list_metadata_json_schema)
    assert errorMsg == ""


    # Display metadata for default namespace
    # Currently only default cluster is supported by Kruize
    cluster_name = "default"

    response = list_metadata(datasource=datasource, cluster_name=cluster_name, verbose="true")

    list_metadata_json = response.json()
    assert response.status_code == SUCCESS_200_STATUS_CODE

    # Validate the json against the json schema
    errorMsg = validate_list_metadata_json(list_metadata_json, list_metadata_json_verbose_true_schema)
    assert errorMsg == ""


    # delete tfb experiments
    tfb_exp_json_file = "../json_files/create_tfb_exp.json"
    tfb_db_exp_json_file = "../json_files/create_tfb_db_exp.json"

    response = delete_experiment(tfb_exp_json_file)
    print("delete tfb exp = ", response.status_code)

    response = delete_experiment(tfb_db_exp_json_file)
    print("delete tfb_db exp = ", response.status_code)

    #Install default metric profile
    metric_profile_json_file = metric_profile_dir / 'resource_optimization_local_monitoring.json'
    response = delete_metric_profile(metric_profile_json_file)
    print("delete metric profile = ", response.status_code)

    # Create metric profile using the specified json
    response = create_metric_profile(metric_profile_json_file)

    data = response.json()
    print(data['message'])

    assert response.status_code == SUCCESS_STATUS_CODE
    assert data['status'] == SUCCESS_STATUS

    json_file = open(metric_profile_json_file, "r")
    input_json = json.loads(json_file.read())
    metric_profile_name = input_json['metadata']['name']
    assert data['message'] == CREATE_METRIC_PROFILE_SUCCESS_MSG % metric_profile_name

    response = list_metric_profiles(name=metric_profile_name, logging=False)
    metric_profile_json = response.json()

    assert response.status_code == SUCCESS_200_STATUS_CODE

    # Validate the json against the json schema
    errorMsg = validate_list_metric_profiles_json(metric_profile_json, list_metric_profiles_schema)
    assert errorMsg == ""


    # Create tfb experiments using the specified json
    response = create_experiment(tfb_exp_json_file)

    data = response.json()
    print(data['message'])

    assert response.status_code == SUCCESS_STATUS_CODE
    assert data['status'] == SUCCESS_STATUS
    assert data['message'] == CREATE_EXP_SUCCESS_MSG

    response = create_experiment(tfb_db_exp_json_file)

    data = response.json()
    print(data['message'])

    assert response.status_code == SUCCESS_STATUS_CODE
    assert data['status'] == SUCCESS_STATUS
    assert data['message'] == CREATE_EXP_SUCCESS_MSG

    # Wait for the container to complete
    wait_for_container_to_complete(container_id)

    # generate recommendations
    json_file = open(tfb_exp_json_file, "r")
    input_json = json.loads(json_file.read())
    tfb_exp_name = input_json[0]['experiment_name']

    json_file = open(tfb_db_exp_json_file, "r")
    input_json = json.loads(json_file.read())
    tfb_db_exp_name = input_json[0]['experiment_name']


    response = generate_recommendations(tfb_exp_name)
    assert response.status_code == SUCCESS_STATUS_CODE

    # Invoke list recommendations for the specified experiment
    response = list_recommendations(tfb_exp_name)
    assert response.status_code == SUCCESS_200_STATUS_CODE
    list_reco_json = response.json()

    # Validate the json against the json schema
    errorMsg = validate_list_reco_json(list_reco_json, list_reco_json_schema)
    assert errorMsg == ""


    response = generate_recommendations(tfb_db_exp_name)
    assert response.status_code == SUCCESS_STATUS_CODE

    # Invoke list recommendations for the specified experiment
    response = list_recommendations(tfb_db_exp_name)
    assert response.status_code == SUCCESS_200_STATUS_CODE
    list_reco_json = response.json()

    # Validate the json against the json schema
    errorMsg = validate_list_reco_json(list_reco_json, list_reco_json_schema)
    assert errorMsg == ""

    # Delete tfb experiment
    response = delete_experiment(tfb_exp_json_file)
    print("delete exp = ", response.status_code)
    assert response.status_code == SUCCESS_STATUS_CODE

    # Delete tfb_db experiment
    response = delete_experiment(tfb_db_exp_json_file)
    print("delete exp = ", response.status_code)
    assert response.status_code == SUCCESS_STATUS_CODE

    # Delete Metric Profile
    response = delete_metric_profile(metric_profile_json_file)
    print("delete metric profile = ", response.status_code)
    assert response.status_code == SUCCESS_STATUS_CODE

    # Remove benchmarks directory
    shutil.rmtree("benchmarks")
