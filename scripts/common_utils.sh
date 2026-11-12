#!/bin/bash
#
# Copyright (c) 2020, 2020 Red Hat, IBM Corporation and others.
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

###############################  utilities  #################################

function check_running() {

	check_pod=$1
	check_pod_ns=$2
	ignore_ui_pod=$3
	ignore_db_pod=$4
	kubectl_cmd="kubectl -n ${check_pod_ns}"

	echo "Info: Waiting for ${check_pod} to come up....."
	err_wait=0
	counter=0
	while true; do
		sleep 2
		if [[ ${ignore_ui_pod} == "" || ${ignore_db_pod} == "" ]]; then
			pod_list=$(${kubectl_cmd} get pods | grep ${check_pod})
		else
			pod_list=$(${kubectl_cmd} get pods | grep ${check_pod} | grep -v "${ignore_ui_pod}" | grep -v "${ignore_db_pod}")
		fi
		if [[ -z "${pod_list}" ]]; then
		  echo "Error: No pods found matching ${check_pod}"
		  err=-1
		  break
		fi
		pod_stat=$(echo "${pod_list}" | awk '{ print $3 }')
		not_running_count=$(echo "${pod_list}" | awk '$3 != "Running" {count++} END {print count+0}')
		error_count=$(echo "${pod_list}" | awk '$3 == "Error" {count++} END {print count+0}')

		if [[ "${not_running_count}" == "0" ]]; then
			echo "Info: ${check_pod} deploy succeeded:"
			echo "${pod_list}"
			err=0
			break
		elif [[ "${error_count}" != "0" ]]; then
			# On Error, wait for 10 seconds before exiting.
			err_wait=$((err_wait + 1))
			if [ ${err_wait} -gt 5 ]; then
				echo "Error: ${check_pod} deploy failed:"
				echo "${pod_list}"
				err=-1
				break
			fi
		else
			sleep 2
			if [ $counter == 200 ]; then
				${kubectl_cmd} get pods | grep "${check_pod}"
				echo "ERROR: ${check_pod} Pods failed to come up!"
				exit -1
			fi
			((counter++))
		fi
	done

	${kubectl_cmd} get pods | grep ${check_pod}
	echo
}

function check_kustomize() {
	kubectl_tool=$(which kubectl)
	check_err "Error: Please install the kubectl tool"
	# Check to see if kubectl supports kustomize
	kubectl --help | grep "kustomize" >/dev/null
	check_err "Error: Please install a newer version of kubectl tool that supports the kustomize option (>=v1.12)"
}

# Check error code from last command, exit on error
check_err() {
	err=$?
	if [ ${err} -ne 0 ]; then
		echo "$*"
		exit -1
	fi
}

# Apply image overrides onto a manifest copy (never mutate the caller's base in-place permanently)
_kruize_crc_apply_images() {
	local src_manifest="$1"
	local dst_manifest="$2"
	awk -v image_name="${AUTOTUNE_DOCKER_IMAGE}" -v ui_image_name="${KRUIZE_UI_DOCKER_IMAGE}" '{
			if ($2=="name:") {
				prev=$3;
				print
			} else if ($1=="image:" && prev=="kruize") {
				$2=image_name;
				printf"          %s %s\n", $1, $2;
			} else if ($1=="image:" && prev=="kruize-ui-nginx-container") {
				$2=ui_image_name;
				printf"          %s %s\n", $1, $2;
			} else { print }
		 }' "${src_manifest}" >"${dst_manifest}"
}

# Deploy kruize in remote monitoring mode with the specified docker image
kruize_crc_start() {
	kubectl_cmd="kubectl -n ${autotune_ns}"
	# Preserve original cluster_type
	manifest_cluster_type="${cluster_type}"

	# Normalize for manifest reuse
	if [ "${cluster_type}" = "kind" ]; then
		manifest_cluster_type="minikube"
	fi

	local apply_manifest="${CRC_MANIFEST_FILE}"
	local tmp_manifest=""

	echo "use yaml build - $use_yaml_build"
	if [ ${use_yaml_build} -eq 0 ]; then
		tmp_manifest="$(mktemp)"
		_kruize_crc_apply_images "${CRC_MANIFEST_FILE}" "${tmp_manifest}"
		apply_manifest="${tmp_manifest}"
	fi

	# Instance mode: apply without -n so Namespace and cluster-scoped objects in the file are used as written
	if [ -n "${KRUIZE_INSTANCE_ID:-}" ]; then
		kubectl apply -f "${apply_manifest}"
	else
		${kubectl_cmd} apply -f "${apply_manifest}"
	fi
	check_running kruize ${autotune_ns} kruize-ui kruize-db
	if [ "${err}" != "0" ]; then
		[ -n "${tmp_manifest}" ] && rm -f "${tmp_manifest}"
		# Indicate deploy failed on error
		exit 1
	fi

	[ -n "${tmp_manifest}" ] && rm -f "${tmp_manifest}"
}

deploy_crc_common() {
	# Default namespace handling
	if [ -z "$autotune_ns" ]; then
		if [ -n "${KRUIZE_INSTANCE_ID:-}" ]; then
			autotune_ns="${KRUIZE_INSTANCE_ID}"
		else
			case "$cluster_type" in
				openshift)
					autotune_ns="openshift-tuning"
					;;
				*)
					autotune_ns="monitoring"
					;;
			esac
		fi
	fi

	# Hard guard: instance mode must never target the shared install
	if [ -n "${KRUIZE_INSTANCE_ID:-}" ]; then
		if [ "${autotune_ns}" = "openshift-tuning" ] || [ "${KRUIZE_INSTANCE_ID}" = "openshift-tuning" ]; then
			echo "ERROR: Refusing CRC instance deploy that targets openshift-tuning"
			exit 1
		fi
		if [ "${cluster_type}" != "openshift" ]; then
			echo "ERROR: --instance CRC deploy is only supported for openshift"
			exit 1
		fi
		local gen_script="${SCRIPTS_DIR}/generate_crc_instance_manifest.sh"
		local out_dir="${KRUIZE_INSTANCE_MANIFEST_DIR:-/tmp/kruize-instance-manifests}"
		mkdir -p "${out_dir}"
		if [ -n "${KRUIZE_CRC_INSTANCE_MANIFEST:-}" ] && [ -f "${KRUIZE_CRC_INSTANCE_MANIFEST}" ]; then
			CRC_MANIFEST_FILE="${KRUIZE_CRC_INSTANCE_MANIFEST}"
			echo "Using pre-generated instance manifest: ${CRC_MANIFEST_FILE}"
		else
			CRC_MANIFEST_FILE="${out_dir}/kruize-crc-${KRUIZE_INSTANCE_ID}.yaml"
			"${gen_script}" --instance "${KRUIZE_INSTANCE_ID}" --namespace "${autotune_ns}" \
				--base "${KRUIZE_CRC_DEPLOY_MANIFEST_OPENSHIFT}" --out "${CRC_MANIFEST_FILE}"
			export KRUIZE_CRC_INSTANCE_MANIFEST="${CRC_MANIFEST_FILE}"
		fi
	else
		# Manifest selection by cluster type (stock path)
		case "$cluster_type" in
			openshift)
				CRC_MANIFEST_FILE="${KRUIZE_CRC_DEPLOY_MANIFEST_OPENSHIFT}"
				;;
			*)
				CRC_MANIFEST_FILE="${KRUIZE_CRC_DEPLOY_MANIFEST_MINIKUBE}"
				;;
		esac
		kubectl create namespace "${autotune_ns}" 2>/dev/null || true
	fi

	# cluster-specific prometheus checks:
	case "$cluster_type" in
		minikube)
			check_prometheus_installation
			;;
		kind)
			check_prometheus_installation_on_kind
			;;
		openshift)
			check_openshift_prometheus_installation
			;;
		*)
			echo "ERROR: Unknown cluster type '$cluster_type'"
			exit 1
			;;
	esac

	kruize_crc_start
}

# terminate_crc_common cluster_type namespace manifest
terminate_crc_common() {
	CRC_MANIFEST_FILE=$1
	if [ -z "$autotune_ns" ]; then
		if [ -n "${KRUIZE_INSTANCE_ID:-}" ]; then
			autotune_ns="${KRUIZE_INSTANCE_ID}"
		else
			case "$cluster_type" in
				openshift)
					autotune_ns="${AUTOTUNE_OPENSHIFT_NAMESPACE}"
					;;
				*)
					autotune_ns="monitoring"
					;;
			esac
		fi
	fi

	if [ -n "${KRUIZE_INSTANCE_ID:-}" ]; then
		if [ "${autotune_ns}" = "openshift-tuning" ] || [ "${KRUIZE_INSTANCE_ID}" = "openshift-tuning" ]; then
			echo "ERROR: Refusing CRC instance terminate that targets openshift-tuning"
			exit 1
		fi
		local gen_script="${SCRIPTS_DIR}/generate_crc_instance_manifest.sh"
		local out_dir="${KRUIZE_INSTANCE_MANIFEST_DIR:-/tmp/kruize-instance-manifests}"
		mkdir -p "${out_dir}"
		local instance_manifest="${KRUIZE_CRC_INSTANCE_MANIFEST:-${out_dir}/kruize-crc-${KRUIZE_INSTANCE_ID}.yaml}"
		if [ ! -f "${instance_manifest}" ]; then
			"${gen_script}" --instance "${KRUIZE_INSTANCE_ID}" --namespace "${autotune_ns}" \
				--base "${KRUIZE_CRC_DEPLOY_MANIFEST_OPENSHIFT}" --out "${instance_manifest}"
		fi
		echo "Deleting instance manifest objects from ${instance_manifest}"
		kubectl delete -f "${instance_manifest}" 2>/dev/null || true
		return
	fi

	kubectl_cmd="kubectl -n ${autotune_ns}"
	${kubectl_cmd} delete -f "${CRC_MANIFEST_FILE}" 2>/dev/null
}
