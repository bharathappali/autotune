#!/bin/bash
#
# Copyright (c) 2020, 2021 Red Hat, IBM Corporation and others.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
##### Script to test autotune #####
#

CURRENT_DIR="$(dirname "$(realpath "$0")")"
SCRIPTS_DIR="${CURRENT_DIR}/scripts"
# Source the common functions scripts
. ${SCRIPTS_DIR}/common/common_functions.sh

# Source the test suite scripts
. ${SCRIPTS_DIR}/remote_monitoring_tests/remote_monitoring_tests.sh
. ${SCRIPTS_DIR}/local_monitoring_tests/local_monitoring_tests.sh
. ${SCRIPTS_DIR}/local_monitoring_tests/authentication_tests.sh
. ${SCRIPTS_DIR}/local_monitoring_tests/datasource_tests.sh


resultsdir="${CURRENT_DIR}"

# usage of the test script
function usage() {
	echo ""
	echo "Usage: $0 -c [minikube|openshift|kind] [-t terminate or cleanup kruize] [-i autotune image] [-o [operator image]] [-n namespace] [--instance=<id>] [--testsuite=...] [--testcase=...] [--resultsdir=...] [--skipsetup] [--cleanup_prometheus] [--api-version=v1|legacy]"
	echo ""
	echo "Example: $0 -c minikube --testsuite=remote_monitoring_tests --resultsdir=/home/results"
	echo "Example: $0 -c openshift --instance=rm-0-12 -i quay.io/kruize/autotune_operator:0.12 --testsuite=remote_monitoring_tests --testcase=test_e2e"
	echo "Example: $0 -c minikube --testsuite=local_monitoring_tests -o"
	echo "Example: $0 -c minikube --testsuite=remote_monitoring_tests --api-version=legacy"
	echo ""
	echo "  --instance=<id>  Isolated CRC install (OpenShift). Namespace defaults to <id> unless -n is set."
	echo "                   Refuses openshift-tuning. Never touches the shared openshift-tuning install."
	echo ""
	test_suite_usage
	echo ""
	exit -1
}

# List of testsuites supported
# output: Display the names of the supported test suite
function test_suite_usage() {
	echo "Supported Test suites are:"
	for array in "${TEST_SUITE_ARRAY[@]}"
	do
		echo "		           ${array}"
	done
	exit -1
}

# Check if the cluster_type is one of kubernetes clusters
# input: cluster type
# output: If cluster type is not supported then print the usage
function check_cluster_type() {
	if [ -z "${cluster_type}" ]; then
		echo
		usage
	fi
	case "${cluster_type}" in
    minikube|openshift|kind)
		;;
	  *)
      echo "Error: Cluster type **${cluster_type}** is not supported  "
      usage
	esac
}

# check if the specified testsuite type exists
# input: testsuite
# output: if the given test suite is not supported print the spported testsuite
function check_testsuite_type() {
	for ts in ${TEST_SUITE_ARRAY[@]}
	do
		if [ "${testsuite}" == "${ts}" ]; then
			matched=1
		fi
	done
	
	if [ "${testsuite}" == "help" ]; then
		test_suite_usage
	fi
	
	if [ "${matched}" -eq "0" ]; then
		echo "Error: Invalid testsuite **${testsuite}** "
		test_suite_usage
	fi
}

# Iterate through the commandline options
while getopts c:ti:osn:-: gopts
do
	case ${gopts} in
	-)
		case "${OPTARG}" in
			testsuite=*)
				testsuite=${OPTARG#*=}
				check_testsuite_type
				;;
			testcase=*)
				testcase=${OPTARG#*=}
				;;
			servicename=*)
      			servicename=${OPTARG#*=}
        		;;
			datasource_namespace=*)
				datasource_namespace=${OPTARG#*=}
				;;
			resultsdir=*)
				resultsdir=${OPTARG#*=}
				;;
		  api-version=*)
				api_version=${OPTARG#*=}
				;;
			instance=*)
				KRUIZE_INSTANCE_ID=${OPTARG#*=}
				export KRUIZE_INSTANCE_ID
				;;
			skipsetup)
				skip_setup=1
				;;
			cleanup_prometheus)
				cleanup_prometheus=1
				;;
			operator-image=*)
				KRUIZE_OPERATOR_IMAGE=${OPTARG#*=}
				;;
		esac
		;;
	c)
		cluster_type="${OPTARG}"
		;;
	t)
		setup=0
		;;
	i)
		KRUIZE_DOCKER_IMAGE="${OPTARG}"
		;;
	n)
		KRUIZE_NAMESPACE="${OPTARG}"
		export KRUIZE_NAMESPACE
		;;
	o)
		USE_OPERATOR=1
		# Check if next argument exists and doesn't start with '-'
		# If so, treat it as the operator image
		if [ ! -z "${!OPTIND}" ] && [[ "${!OPTIND}" != -* ]]; then
			KRUIZE_OPERATOR_IMAGE="${!OPTIND}"
			OPTIND=$((OPTIND + 1))
		else
			KRUIZE_OPERATOR_IMAGE=""
		fi
		export USE_OPERATOR
		export KRUIZE_OPERATOR_IMAGE
		;;
	s)
		setup=1
		;;
	[?])
		usage
	esac
done

# Instance mode: namespace defaults to instance id; never allow openshift-tuning
if [ -n "${KRUIZE_INSTANCE_ID:-}" ]; then
	if [ -z "${KRUIZE_NAMESPACE:-}" ]; then
		KRUIZE_NAMESPACE="${KRUIZE_INSTANCE_ID}"
		export KRUIZE_NAMESPACE
	fi
	if [ "${KRUIZE_NAMESPACE}" = "openshift-tuning" ] || [ "${KRUIZE_INSTANCE_ID}" = "openshift-tuning" ]; then
		echo "Error: --instance must not target openshift-tuning"
		exit -1
	fi
fi

# Check if the cluster type is supported
check_cluster_type

# It is necessary to pass testsuite name when testcase is specified
if [ ! -z "${testcase}" ]; then
	if [ -z "${testsuite}" ]; then
		echo "Error: Do specify the testsuite name"
		exit -1
	fi
fi

# It is necessary to pass datasource namespace when servicename is specified
if [ ! -z "${servicename}" ]; then
	if [ -z "${datasource_namespace}" ]; then
		echo "Error: Do specify the datasource namespace"
		exit -1
	fi
fi

# Set the API version environment variable if specified
if [ -n "${api_version}" ]; then
	case "${api_version}" in
		v1|V1)
			export USE_NEW_RECOMMENDATION_API=true
			echo "Using NEW API (v1): /kruize/api/v1/recommendations"
			;;
		legacy|LEGACY|old|OLD)
			export USE_NEW_RECOMMENDATION_API=false
			echo "Using OLD/LEGACY APIs: /updateRecommendations, /generateRecommendations, /listRecommendations"
			;;
		*)
			echo "Error: Invalid API version '${api_version}'. Valid values are: v1, legacy"
			exit -1
			;;
	esac
else
	# Default to old/legacy API if no parameter specified
	export USE_NEW_RECOMMENDATION_API=false
	echo "Using default OLD/LEGACY APIs: /updateRecommendations, /generateRecommendations, /listRecommendations"
fi

# Set the root for result directory
RESULTS_ROOT_DIR="${resultsdir}/kruize_test_results"
mkdir -p ${RESULTS_ROOT_DIR}

# create the result directory with a time stamp
RESULTS="${RESULTS_ROOT_DIR}/kruize_${testsuite}_$(date +%Y%m%d-%H%M%S)"
mkdir -p "${RESULTS}"

if [ "${setup}" -ne "0" ]; then
	# Call the proper setup function based on the cluster_type
	echo -n "############# Performing tests for Kruize #############"

	# Perform the specific testsuite if specified
	if [ ! -z "${testsuite}" ]; then
        	${testsuite} > >(tee "${RESULTS}/${testsuite}.log") 2>&1
	fi

	echo ""
	echo "*********************************************************************************"
	echo "~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Overall summary of the tests ~~~~~~~~~~~~~~~~~~~~~~~"
	overallsummary  ${FAILED_TEST_SUITE}
	echo ""
	echo "*********************************************************************************"

	if [ "${TOTAL_TESTS_FAILED}" -ne "0" ]; then
        	exit 1
	else
        	exit 0
	fi

	echo "########################################################################"
	echo ""
else
	autotune_cleanup
fi
