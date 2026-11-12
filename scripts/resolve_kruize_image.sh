#!/bin/bash
#
# Resolve a Kruize image reference.
# Bare tags become quay.io/kruize/autotune_operator:<tag>
# Full refs (containing '/') are returned unchanged.
#
# Usage:
#   resolve_kruize_image.sh <image-or-tag> [default_image_repo]
#   source resolve_kruize_image.sh && resolve_kruize_image <image-or-tag>
#

DEFAULT_KRUIZE_IMAGE_REPO="${DEFAULT_KRUIZE_IMAGE_REPO:-quay.io/kruize/autotune_operator}"

resolve_kruize_image() {
	local image_or_tag="$1"
	local repo="${2:-${DEFAULT_KRUIZE_IMAGE_REPO}}"

	if [[ -z "${image_or_tag}" ]]; then
		echo "ERROR: empty image" >&2
		return 1
	fi

	if [[ "${image_or_tag}" == *"/"* ]]; then
		echo "${image_or_tag}"
	else
		echo "${repo}:${image_or_tag}"
	fi
}

# Allow running as a script
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
	if [[ $# -lt 1 ]]; then
		echo "Usage: $0 <image-or-tag> [default_image_repo]" >&2
		exit 1
	fi
	resolve_kruize_image "$@"
fi
