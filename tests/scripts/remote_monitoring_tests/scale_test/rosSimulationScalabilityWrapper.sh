#!/bin/bash
#
# Copyright (c) 2023, 2023 IBM Corporation, RedHat and others.
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

# Default values
ip=""
port=""
count=""
exp_type="container"
minutesjump=""
name_prefix=""
initial_startdate="2023-01-01T00:00:00.000Z"
limit_days="15"
interval_hours="6"
outputdir="results"

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --api-version)
            api_version="$2"
            shift 2
            ;;
        --ip)
            ip="$2"
            shift 2
            ;;
        --port)
            port="$2"
            shift 2
            ;;
        --count)
            count="$2"
            shift 2
            ;;
        --minutesjump)
            minutesjump="$2"
            shift 2
            ;;
        --exptype)
            exp_type="$2"
            shift 2
            ;;
        --name)
            name_prefix="$2"
            shift 2
            ;;
        --initialstartdate)
            initial_startdate="$2"
            shift 2
            ;;
        --limitdays)
            limit_days="$2"
            shift 2
            ;;
        --intervalhours)
            interval_hours="$2"
            shift 2
            ;;
        --clientthread)
            client_thread="$2"
            shift 2
            ;;
	--prometheusserver)
            prometheus_server="$2"
            shift 2
            ;;
	--outputdir)
            outputdir="$2"
            shift 2
	    ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

if [[ -z "$ip" || -z "$port" || -z "$count" || -z "$minutesjump" || -z "$name_prefix" ]]; then
    echo "Missing required arguments."
    echo "Usage: $0 --ip <IP> --port <port> --count <count> --exptype <exp_type> --minutesjump <minutesjump> --name <name_prefix> --initialstartdate <initial_startdate> --limitdays <limit_days> --intervalhours <interval_hours>"
    exit 1
fi

echo "rosSimulationScalabilityTest.sh :: api_version = ${api_version}"
# Set the API version to default if not passed on parameter
if [ -z "${api_version}" ]; then
  api_version="legacy"
fi

# Calculate the number of iterations based on interval and limit days
iterations=$(( $limit_days * 24 / $interval_hours ))

# Portable start-date arithmetic (GNU date -d breaks on macOS/BSD).
scale_startdate_plus_hours() {
    local base="$1"
    local hours="$2"
    python3 - "$base" "$hours" <<'PY'
from datetime import datetime, timedelta, timezone
import sys
raw = sys.argv[1].replace("Z", "+00:00")
hours = int(sys.argv[2])
dt = datetime.fromisoformat(raw)
if dt.tzinfo is None:
    dt = dt.replace(tzinfo=timezone.utc)
dt = dt.astimezone(timezone.utc) + timedelta(hours=hours)
ms = dt.microsecond // 1000
print(dt.strftime("%Y-%m-%dT%H:%M:%S.") + f"{ms:03d}Z")
PY
}

# Loop for each iteration
for (( i = 0; i < $iterations; i++ )); do
    # Calculate the current start date for this iteration
    current_startdate="$(scale_startdate_plus_hours "${initial_startdate}" "$(( i * interval_hours ))")"
    if [[ -z "${current_startdate}" ]]; then
        echo "ERROR: failed to compute startdate from ${initial_startdate} + $(( i * interval_hours ))h"
        exit 1
    fi

    # Build the full command
    full_command="python3 -u rosSimulationScalabilityTest.py --ip $ip --port $port --count $count --minutesjump $minutesjump --startdate $current_startdate --name ${name_prefix} --exptype ${exp_type} --api-version ${api_version}"

    # Execute the command
    echo "Executing: $full_command"
    eval "$full_command"

    # Wait for the command to complete before moving to the next iteration
    wait

    echo "Collecting kruize metrics"
    metrics_ns="${KRUIZE_NAMESPACE:-${NAMESPACE:-}}"
    metrics_ns_arg=""
    if [[ -n "${metrics_ns}" ]]; then
        metrics_ns_arg="-n ${metrics_ns}"
    fi
    metrics_command="python3 ../../../../scripts/kruize_metrics.py -c openshift -s ${prometheus_server} -t 360m -e ${outputdir}/results -r kruizeMetrics-${client_thread}.csv --api-version ${api_version} ${metrics_ns_arg}"
    eval "${metrics_command}" &

    # Sleep for a short duration to avoid flooding the system with too many requests
    sleep 5
done
