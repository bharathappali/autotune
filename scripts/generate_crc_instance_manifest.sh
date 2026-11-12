#!/bin/bash
#
# Generate a per-instance CRC OpenShift manifest: stock CRC as-is, isolated.
# Never edits the git-tracked base manifest / openshift-tuning.
#
# Isolation only (no storage behavior changes vs stock):
#   - Own namespace, suffixed ClusterRole/CRB names
#   - Drop StorageClass/manual (shared; terminate must not delete it)
#   - Unique PV name + unique hostPath /mnt/data-kruize-<id> so instance
#     delete never touches openshift-tuning's /mnt/data
#   - Keep stock DB mount + PGDATA exactly (Postgres uses container-local
#     PGDATA; same as stock CRC — no node chmod/SELinux/prep)
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
HOST_PATH="/mnt/data-kruize-${SUFFIX}"

# Stock CRC copy: isolate names/paths only. Do not change PGDATA/mountPath.
python3 - "${OUT_MANIFEST}" "${NAMESPACE}" "${SUFFIX}" "${HOST_PATH}" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
namespace = sys.argv[2]
suffix = sys.argv[3]
host_path = sys.argv[4]
kept = []
for doc in path.read_text().split("---\n"):
    body = doc.strip()
    if not body:
        continue
    kind = None
    for ln in body.splitlines():
        if ln.startswith("kind:"):
            kind = ln.split(":", 1)[1].strip()
            break
    # Shared cluster object — do not create/delete with instance stacks.
    if kind == "StorageClass":
        continue
    if kind == "PersistentVolume" and "name: kruize-db-pv-volume" in body:
        body = body.replace("name: kruize-db-pv-volume", f"name: kruize-db-pv-volume-{suffix}")
        body = body.replace('path: "/mnt/data"', f'path: "{host_path}"')
        if f"name: kruize-db-pv-volume-{suffix}" not in body:
            raise SystemExit("ERROR: failed to suffix PV name")
        if f'path: "{host_path}"' not in body:
            raise SystemExit("ERROR: failed to set per-instance hostPath")
        if 'path: "/mnt/data"' in body:
            raise SystemExit("ERROR: PV still points at shared /mnt/data")
    # Remote-monitoring defaults for isolated test stacks (config only)
    body = (
        body.replace('"isROSEnabled": "false"', '"isROSEnabled": "true"')
        .replace('"local": "true"', '"local": "false"')
        .replace('"hbm2ddlauto": "none"', '"hbm2ddlauto": "update"')
        .replace("openshift-tuning", namespace)
    )
    kept.append(body)
path.write_text("---\n" + "\n---\n".join(kept) + "\n")
PY

# Protect compound names, suffix base names, then restore compounds with suffix.
# PV name is already suffixed in Python above; do not re-suffix here.
sed -i.bak \
	-e "s/kruize-recommendation-updater-crb/__TMP_CRB_REC__/g" \
	-e "s/instaslices-access-binding/__TMP_CRB_INST__/g" \
	-e "s/kruize-edit-ko-binding/__TMP_CRB_EDIT__/g" \
	-e "s/kruize-recommendation-updater/kruize-recommendation-updater-${SUFFIX}/g" \
	-e "s/kruize-monitoring-view/kruize-monitoring-view-${SUFFIX}/g" \
	-e "s/instaslices-access/instaslices-access-${SUFFIX}/g" \
	-e "s/kruize-edit-ko/kruize-edit-ko-${SUFFIX}/g" \
	-e "s/autotune-scc-crb/autotune-scc-crb-${SUFFIX}/g" \
	-e "s/__TMP_CRB_REC__/kruize-recommendation-updater-crb-${SUFFIX}/g" \
	-e "s/__TMP_CRB_INST__/instaslices-access-binding-${SUFFIX}/g" \
	-e "s/__TMP_CRB_EDIT__/kruize-edit-ko-binding-${SUFFIX}/g" \
	"${OUT_MANIFEST}"

rm -f "${OUT_MANIFEST}.bak"

if grep -q 'openshift-tuning' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest still references openshift-tuning"
	exit 1
fi
if grep -qE 'kind:[[:space:]]*StorageClass' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest must not include StorageClass/manual"
	exit 1
fi
if ! grep -q "name: kruize-db-pv-volume-${SUFFIX}" "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest missing per-instance PV name"
	exit 1
fi
if ! grep -q "path: \"${HOST_PATH}\"" "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest missing per-instance hostPath ${HOST_PATH}"
	exit 1
fi
if grep -qE 'path: "/mnt/data"|emptyDir:|gp3-csi|storageClassName: gp' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest must not use shared /mnt/data, emptyDir, or EBS classes"
	exit 1
fi
# Stock CRC DB paths must be unchanged
if ! grep -q 'value: /var/lib/pg_data' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest must keep stock PGDATA=/var/lib/pg_data"
	exit 1
fi
if grep -q 'value: /var/lib/pgsql/data/pgdata' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest must not rewrite PGDATA away from stock"
	exit 1
fi
if ! grep -q 'mountPath: /var/lib/pgsql/data' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest must keep stock DB mountPath"
	exit 1
fi
if ! grep -q 'claimName: kruize-db-pv-claim' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest DB Deployment missing PVC claim"
	exit 1
fi
if ! grep -q "kruize-recommendation-updater-crb-${SUFFIX}" "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest missing suffixed recommendation CRB"
	exit 1
fi
if grep -q '"local": "true"' "${OUT_MANIFEST}"; then
	echo "ERROR: Generated manifest still has local=true (expected remote monitoring)"
	exit 1
fi

echo "Generated instance manifest: ${OUT_MANIFEST}"
echo "  instance=${INSTANCE_ID} namespace=${NAMESPACE} pv=kruize-db-pv-volume-${SUFFIX} hostPath=${HOST_PATH} (stock PGDATA/mount unchanged)"
