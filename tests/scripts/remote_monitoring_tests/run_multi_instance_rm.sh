#!/bin/bash
#
# YAML-only multi-instance remote monitoring runner.
# Deploys each instance into an isolated namespace and runs functional or scale tests.
# Never targets openshift-tuning.
#
# Usage:
#   ./run_multi_instance_rm.sh [-f instances.yaml] [--suite functional|scale] \
#       [--testcase test_e2e] [-c openshift] [-r resultsdir] [--build-push] \
#       [--cleanup] [-- <scale-args...>]
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
DEFAULT_INSTANCES_FILE="${SCRIPT_DIR}/instances.example.yaml"
RESOLVE_IMAGE="${REPO_ROOT}/scripts/resolve_kruize_image.sh"
PARSE_STATS="${SCRIPT_DIR}/parse_scale_stats.py"

INSTANCES_FILE=""
SUITE="functional"
TESTCASE="test_e2e"
CLUSTER_TYPE="openshift"
RESULTS_DIR=""
RESULTS_DIR_SET=0
BUILD_PUSH=0
CLEANUP=0
DEFAULT_IMAGE_REPO="quay.io/kruize/autotune_operator"
EXTRA_SCALE_ARGS=()

RUN_STATUS_FILE="run_status"

# Atomically claim ./rm-multi-<N> via mkdir (fails if dir exists).
# Concurrent runs cannot get the same N. Marks the dir in_progress immediately.
allocate_rm_multi_results_dir() {
	local max=0
	local d base num candidate
	local attempts=0
	local max_attempts=10000

	shopt -s nullglob
	for d in ./rm-multi-*; do
		[[ -d "${d}" ]] || continue
		base="${d#./}"
		num="${base#rm-multi-}"
		if [[ "${num}" =~ ^[0-9]+$ ]] && (( num > max )); then
			max=${num}
		fi
	done
	shopt -u nullglob

	candidate=$((max + 1))
	while (( attempts < max_attempts )); do
		# mkdir is atomic: only one process succeeds for a given path
		if mkdir "./rm-multi-${candidate}" 2>/dev/null; then
			write_run_status "./rm-multi-${candidate}" "in_progress" "allocated"
			echo "./rm-multi-${candidate}"
			return 0
		fi
		candidate=$((candidate + 1))
		attempts=$((attempts + 1))
	done
	echo "ERROR: could not allocate an rm-multi-* results directory" >&2
	return 1
}

write_run_status() {
	local dir="$1"
	local state="$2"
	local detail="${3:-}"
	mkdir -p "${dir}"
	{
		echo "state=${state}"
		echo "updated=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
		echo "pid=${BASHPID:-$$}"
		[[ -n "${detail}" ]] && echo "detail=${detail}"
	} >"${dir}/${RUN_STATUS_FILE}"
}

usage() {
	echo "Usage: $0 [-f instances.yaml] [--suite functional|scale] [--testcase <marker>] [-c openshift] [-r resultsdir] [--build-push] [--cleanup] [-- <scale-args>]"
	echo "  -f omitted → ${DEFAULT_INSTANCES_FILE}"
	echo "  -r omitted → atomically create ./rm-multi-<N> (mkdir claim; safe for concurrent runs)"
	echo "  Preflight: aborts if any target namespace already exists (change the YAML)."
	echo "  Never deploys to or deletes openshift-tuning."
	exit 1
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		-f)
			INSTANCES_FILE="${2:-}"
			shift 2
			;;
		--suite)
			SUITE="${2:-}"
			shift 2
			;;
		--testcase)
			TESTCASE="${2:-}"
			shift 2
			;;
		-c)
			CLUSTER_TYPE="${2:-}"
			shift 2
			;;
		-r)
			RESULTS_DIR="${2:-}"
			RESULTS_DIR_SET=1
			shift 2
			;;
		--build-push)
			BUILD_PUSH=1
			shift
			;;
		--cleanup)
			CLEANUP=1
			shift
			;;
		-h|--help)
			usage
			;;
		--)
			shift
			EXTRA_SCALE_ARGS+=("$@")
			break
			;;
		*)
			EXTRA_SCALE_ARGS+=("$1")
			shift
			;;
	esac
done

if [[ -z "${INSTANCES_FILE}" ]]; then
	INSTANCES_FILE="${DEFAULT_INSTANCES_FILE}"
fi
if [[ ! -f "${INSTANCES_FILE}" ]]; then
	echo "ERROR: instances file not found: ${INSTANCES_FILE}"
	exit 1
fi
if [[ "${CLUSTER_TYPE}" != "openshift" ]]; then
	echo "ERROR: multi-instance runner currently supports openshift only"
	exit 1
fi

if [[ "${RESULTS_DIR_SET}" -eq 0 ]] || [[ -z "${RESULTS_DIR}" ]]; then
	RESULTS_DIR="$(allocate_rm_multi_results_dir)"
else
	# Explicit -r: create if needed, then mark in_progress (do not steal an existing auto dir)
	if [[ -d "${RESULTS_DIR}" ]] && [[ -f "${RESULTS_DIR}/${RUN_STATUS_FILE}" ]]; then
		if grep -q '^state=in_progress' "${RESULTS_DIR}/${RUN_STATUS_FILE}" 2>/dev/null; then
			echo "ERROR: results dir ${RESULTS_DIR} is already in_progress (another run?). Pick another -r."
			exit 1
		fi
	fi
	mkdir -p "${RESULTS_DIR}"
	write_run_status "${RESULTS_DIR}" "in_progress" "explicit_-r"
fi

echo "Results directory: ${RESULTS_DIR} (state=in_progress)"
ORCH_LOG="${RESULTS_DIR}/orchestrator.log"
TUNING_FINGERPRINT_BEFORE="${RESULTS_DIR}/openshift-tuning-before.txt"
TUNING_FINGERPRINT_AFTER="${RESULTS_DIR}/openshift-tuning-after.txt"

# If the script dies unexpectedly, leave a failed marker (not stuck forever as in_progress).
on_unexpected_exit() {
	local ec=$?
	if [[ -n "${RESULTS_DIR:-}" ]] && [[ -d "${RESULTS_DIR}" ]]; then
		if [[ -f "${RESULTS_DIR}/${RUN_STATUS_FILE}" ]] && grep -q '^state=in_progress' "${RESULTS_DIR}/${RUN_STATUS_FILE}" 2>/dev/null; then
			write_run_status "${RESULTS_DIR}" "failed" "unexpected_exit_${ec}"
		fi
	fi
}
trap on_unexpected_exit EXIT

# shellcheck source=/dev/null
. "${RESOLVE_IMAGE}"

fingerprint_tuning() {
	local out="$1"
	{
		echo "timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
		kubectl get deploy kruize -n openshift-tuning -o jsonpath='image={.spec.template.spec.containers[0].image} replicas={.spec.replicas} gen={.metadata.generation}{"\n"}' 2>/dev/null || echo "deploy=missing"
		kubectl get pods -n openshift-tuning -l app=kruize -o wide 2>/dev/null || true
	} >"${out}"
}

load_instances_python() {
	python3 - "${INSTANCES_FILE}" "${DEFAULT_IMAGE_REPO}" <<'PY'
import json, sys
try:
    import yaml
except ImportError:
    print("ERROR: PyYAML required (pip install PyYAML)", file=sys.stderr)
    sys.exit(2)

path, default_repo = sys.argv[1], sys.argv[2]
with open(path) as f:
    data = yaml.safe_load(f) or {}

repo = data.get("default_image_repo") or default_repo
instances = data.get("instances") or []
if not instances:
    print("ERROR: no instances in YAML", file=sys.stderr)
    sys.exit(1)

rows = []
seen_ns = set()
for item in instances:
    iid = str(item.get("id", "")).strip()
    image = str(item.get("image", "")).strip()
    ns = str(item.get("namespace") or iid).strip()
    if not iid or not image:
        print("ERROR: each instance needs id and image", file=sys.stderr)
        sys.exit(1)
    if iid == "openshift-tuning" or ns == "openshift-tuning":
        print("ERROR: refuses openshift-tuning id/namespace — change the YAML", file=sys.stderr)
        sys.exit(1)
    if ns in seen_ns:
        print(f"ERROR: duplicate namespace {ns} in YAML", file=sys.stderr)
        sys.exit(1)
    seen_ns.add(ns)
    if "/" in image:
        resolved = image
    else:
        resolved = f"{repo}:{image}"
    rows.append({"id": iid, "namespace": ns, "image": resolved, "image_raw": image})

print(json.dumps(rows))
PY
}

iter_rows() {
	python3 -c 'import json,sys; [print(json.dumps(x)) for x in json.loads(sys.stdin.read())]' <<<"${INSTANCES_JSON}"
}

echo "Loading instances from ${INSTANCES_FILE}" | tee "${ORCH_LOG}"
INSTANCES_JSON="$(load_instances_python)"
echo "${INSTANCES_JSON}" | tee -a "${ORCH_LOG}"

echo "Preflight: checking target namespaces do not exist..." | tee -a "${ORCH_LOG}"
while IFS= read -r row; do
	ns="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["namespace"])' "${row}")"
	if kubectl get namespace "${ns}" >/dev/null 2>&1; then
		echo "ERROR: Namespace \"${ns}\" already exists. Refusing to proceed so existing workloads (including openshift-tuning) are not disturbed. Change id/namespace in the instances YAML to unused names." | tee -a "${ORCH_LOG}"
		exit 1
	fi
done < <(iter_rows)

fingerprint_tuning "${TUNING_FINGERPRINT_BEFORE}"
echo "Recorded openshift-tuning fingerprint → ${TUNING_FINGERPRINT_BEFORE}" | tee -a "${ORCH_LOG}"

if [[ "${BUILD_PUSH}" -eq 1 ]]; then
	echo "Building and pushing images..." | tee -a "${ORCH_LOG}"
	while IFS= read -r row; do
		img="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["image"])' "${row}")"
		pushd "${REPO_ROOT}" >/dev/null
		./build.sh -d -i "${img}" | tee -a "${ORCH_LOG}"
		docker push "${img}" | tee -a "${ORCH_LOG}"
		popd >/dev/null
	done < <(iter_rows)
fi

run_one() {
	local row="$1"
	local iid ns img out_dir run_log infra_start infra_end infra_secs rc
	iid="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["id"])' "${row}")"
	ns="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["namespace"])' "${row}")"
	img="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["image"])' "${row}")"
	out_dir="${RESULTS_DIR}/${iid}"
	mkdir -p "${out_dir}"
	run_log="${out_dir}/run.log"
	rc=0

	echo "=== Starting instance id=${iid} ns=${ns} image=${img} suite=${SUITE} ===" | tee -a "${ORCH_LOG}" "${run_log}"

	infra_start=$(date +%s)
	export KRUIZE_INSTANCE_ID="${iid}"
	export KRUIZE_NAMESPACE="${ns}"
	export NAMESPACE="${ns}"
	export KRUIZE_INSTANCE_MANIFEST_DIR="${out_dir}"

	if [[ "${SUITE}" == "functional" ]]; then
		pushd "${REPO_ROOT}/tests" >/dev/null
		./test_autotune.sh -c "${CLUSTER_TYPE}" -i "${img}" -n "${ns}" --instance="${iid}" \
			--testsuite=remote_monitoring_tests --testcase="${TESTCASE}" \
			--resultsdir="${out_dir}" >>"${run_log}" 2>&1 || rc=$?
		popd >/dev/null
		infra_end=$(date +%s)
		infra_secs=$((infra_end - infra_start))
		python3 - <<PY
import json
from pathlib import Path
stats = {
  "id": "${iid}",
  "namespace": "${ns}",
  "image": "${img}",
  "suite": "functional",
  "testcase": "${TESTCASE}",
  "infra_setup_and_suite_seconds": ${infra_secs},
  "metric_kind": "fixed_work_wall_clock",
  "source": "test_autotune_functional",
  "exit_code": ${rc},
}
Path("${out_dir}/kruize_stats.json").write_text(json.dumps(stats, indent=2) + "\n")
PY
	elif [[ "${SUITE}" == "scale" ]]; then
		pushd "${SCRIPT_DIR}/scale_test" >/dev/null
		local scale_args=("${EXTRA_SCALE_ARGS[@]+"${EXTRA_SCALE_ARGS[@]}"}")
		if [[ ${#scale_args[@]} -eq 0 ]]; then
			scale_args=(-u 50 -d 1 -n 2 -g 2 -q 5)
		fi
		./remote_monitoring_scale_test_bulk.sh -i "${img}" -r "${out_dir}" \
			--instance="${iid}" --namespace="${ns}" "${scale_args[@]}" >>"${run_log}" 2>&1 || rc=$?
		popd >/dev/null
		infra_end=$(date +%s)
		infra_secs=$((infra_end - infra_start))
		local scale_log_dir
		scale_log_dir="$(find "${out_dir}" -type d -name 'remote-monitoring-scale-test-*' 2>/dev/null | sort | tail -1 || true)"
		if [[ -n "${scale_log_dir}" ]]; then
			python3 "${PARSE_STATS}" --log-dir "${scale_log_dir}" --id "${iid}" \
				--namespace "${ns}" --image "${img}" --out "${out_dir}/kruize_stats.json" \
				--infra-setup-seconds "${infra_secs}" >>"${run_log}" 2>&1 || true
		else
			echo "{\"id\":\"${iid}\",\"namespace\":\"${ns}\",\"image\":\"${img}\",\"exit_code\":${rc},\"error\":\"scale log dir not found\"}" >"${out_dir}/kruize_stats.json"
		fi
	else
		echo "ERROR: unknown suite ${SUITE}" | tee -a "${ORCH_LOG}"
		rc=1
	fi

	echo "${rc}" >"${out_dir}/exit_code"
	echo "=== Finished instance id=${iid} rc=${rc} ===" | tee -a "${ORCH_LOG}" "${run_log}"
	exit "${rc}"
}

PIDS=()
INSTANCE_IDS=()
while IFS= read -r row; do
	iid="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["id"])' "${row}")"
	INSTANCE_IDS+=("${iid}")
	run_one "${row}" &
	PIDS+=($!)
done < <(iter_rows)

FAIL=0
for pid in "${PIDS[@]}"; do
	if ! wait "${pid}"; then
		FAIL=1
	fi
done

python3 - "${PARSE_STATS}" "${RESULTS_DIR}" ${INSTANCE_IDS[@]+"${INSTANCE_IDS[@]}"} <<'PY'
import json, importlib.util, sys
from pathlib import Path
parse_path = Path(sys.argv[1])
results_dir = Path(sys.argv[2])
ids = sys.argv[3:]
spec = importlib.util.spec_from_file_location("parse_scale_stats", parse_path)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
stats_list = []
for iid in ids:
    f = results_dir / iid / "kruize_stats.json"
    if f.exists():
        stats_list.append(json.loads(f.read_text()))
mod.write_comparison(stats_list, results_dir)
print(f"Wrote {results_dir}/comparison.csv and comparison.md")
PY

fingerprint_tuning "${TUNING_FINGERPRINT_AFTER}"
before_line="$(grep '^image=' "${TUNING_FINGERPRINT_BEFORE}" || true)"
after_line="$(grep '^image=' "${TUNING_FINGERPRINT_AFTER}" || true)"
if [[ "${before_line}" != "${after_line}" ]]; then
	echo "ERROR: openshift-tuning Deployment image/replicas/generation changed" | tee -a "${ORCH_LOG}"
	echo "  before: ${before_line}" | tee -a "${ORCH_LOG}"
	echo "  after:  ${after_line}" | tee -a "${ORCH_LOG}"
	FAIL=1
else
	echo "openshift-tuning deploy fingerprint unchanged" | tee -a "${ORCH_LOG}"
fi

if [[ "${CLEANUP}" -eq 1 ]]; then
	echo "Cleaning up instance stacks only..." | tee -a "${ORCH_LOG}"
	while IFS= read -r row; do
		iid="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["id"])' "${row}")"
		ns="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["namespace"])' "${row}")"
		if [[ "${ns}" == "openshift-tuning" ]]; then
			echo "Refusing cleanup of openshift-tuning" | tee -a "${ORCH_LOG}"
			continue
		fi
		pushd "${REPO_ROOT}" >/dev/null
		./deploy.sh -c openshift -m crc -n "${ns}" --instance="${iid}" -t >>"${ORCH_LOG}" 2>&1 || true
		popd >/dev/null
	done < <(iter_rows)
fi

if [[ "${FAIL}" -ne 0 ]]; then
	write_run_status "${RESULTS_DIR}" "failed" "instance_or_fingerprint_failure"
	trap - EXIT
	echo "One or more instances failed. See ${RESULTS_DIR} (run_status=failed)" | tee -a "${ORCH_LOG}"
	exit 1
fi
write_run_status "${RESULTS_DIR}" "completed" "ok"
trap - EXIT
echo "All instances completed. Results under ${RESULTS_DIR} (run_status=completed)" | tee -a "${ORCH_LOG}"
