#!/bin/bash
#
# Generate a per-instance CRC OpenShift manifest copy with unique namespace and
# cluster-scoped object names. Never edits the git-tracked base manifest.
#
# Usage:
#   generate_crc_instance_manifest.sh --instance <id> [--namespace <ns>] \
#       [--base <path>] --out <path>
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_BASE="${REPO_ROOT}/manifests/crc/default-db-included-installation/openshift/kruize-crc-openshift.yaml"

INSTANCE_ID=""
NAMESPACE=""
BASE_MANIFEST="${DEFAULT_BASE}"
OUT_MANIFEST=""

usage() {
	echo "Usage: $0 --instance <id> [--namespace <ns>] [--base <manifest>] --out <path>"
	echo "  namespace defaults to <id>"
	echo "  refuses namespace or id equal to openshift-tuning"
	exit 1
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--instance)
			INSTANCE_ID="${2:-}"
			shift 2
			;;
		--namespace)
			NAMESPACE="${2:-}"
			shift 2
			;;
		--base)
			BASE_MANIFEST="${2:-}"
			shift 2
			;;
		--out)
			OUT_MANIFEST="${2:-}"
			shift 2
			;;
		-h|--help)
			usage
			;;
		*)
			echo "Unknown option: $1"
			usage
			;;
	esac
done

if [[ -z "${INSTANCE_ID}" || -z "${OUT_MANIFEST}" ]]; then
	usage
fi

if [[ -z "${NAMESPACE}" ]]; then
	NAMESPACE="${INSTANCE_ID}"
fi

if [[ "${INSTANCE_ID}" == "openshift-tuning" || "${NAMESPACE}" == "openshift-tuning" ]]; then
	echo "ERROR: Refusing to generate a manifest for openshift-tuning (protects the shared install)."
	exit 1
fi

if [[ ! -f "${BASE_MANIFEST}" ]]; then
	echo "ERROR: Base manifest not found: ${BASE_MANIFEST}"
	exit 1
fi

mkdir -p "$(dirname "${OUT_MANIFEST}")"
cp "${BASE_MANIFEST}" "${OUT_MANIFEST}"

SUFFIX="${INSTANCE_ID}"

# Namespace rewrite (stock CRC OpenShift NS)
sed -i.bak "s/openshift-tuning/${NAMESPACE}/g" "${OUT_MANIFEST}"

# Protect compound names, suffix base names, then restore compounds with suffix
# so we never get kruize-recommendation-updater-<id>-crb-<id>.
sed -i.bak \
	-e "s/kruize-recommendation-updater-crb/__TMP_CRB_REC__/g" \
	-e "s/instaslices-access-binding/__TMP_CRB_INST__/g" \
	-e "s/kruize-edit-ko-binding/__TMP_CRB_EDIT__/g" \
	-e "s/kruize-recommendation-updater/kruize-recommendation-updater-${SUFFIX}/g" \
	-e "s/kruize-monitoring-view/kruize-monitoring-view-${SUFFIX}/g" \
	-e "s/instaslices-access/instaslices-access-${SUFFIX}/g" \
	-e "s/kruize-edit-ko/kruize-edit-ko-${SUFFIX}/g" \
	-e "s/autotune-scc-crb/autotune-scc-crb-${SUFFIX}/g" \
	-e "s/kruize-db-pv-volume/kruize-db-pv-volume-${SUFFIX}/g" \
	-e "s/__TMP_CRB_REC__/kruize-recommendation-updater-crb-${SUFFIX}/g" \
	-e "s/__TMP_CRB_INST__/instaslices-access-binding-${SUFFIX}/g" \
	-e "s/__TMP_CRB_EDIT__/kruize-edit-ko-binding-${SUFFIX}/g" \
	"${OUT_MANIFEST}"

rm -f "${OUT_MANIFEST}.bak"

if grep -q 'openshift-tuning' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest still references openshift-tuning"
	exit 1
fi
if grep -qE 'name:[[:space:]]*kruize-db-pv-volume[[:space:]]*(#|$)' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest still has unsuffixed kruize-db-pv-volume"
	exit 1
fi
if ! grep -q "kruize-db-pv-volume-${SUFFIX}" "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest missing suffixed PV name"
	exit 1
fi
if ! grep -q "kruize-recommendation-updater-crb-${SUFFIX}" "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest missing suffixed recommendation CRB"
	exit 1
fi

echo "Generated instance manifest: ${OUT_MANIFEST}"
echo "  instance=${INSTANCE_ID} namespace=${NAMESPACE}"
