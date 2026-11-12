# **Kruize Remote monitoring scalability test**

Kruize Remote monitoring scalability test validates the behaviour of [Kruize remote monitoring APIs](/design/MonitoringModeAPI.md) by scaling kruize pods and running multiple experiments with 15 days of metrics uploaded and captures various metrics.

## Test description

   The test does the following:
   - Deploys kruize in non-CRD mode using the [deploy script](https://github.com/kruize/autotune/blob/master/deploy.sh) from the autotune repo
   - Scales kruize replicas to 10
   - Exposes kruize service
   - Creates a resource optimization performance profile using the [createPerformanceProfile API](/design/PerformanceProfileAPI.md) 
   - Creates the specified no. of client threads that run in parallel, creating multiple experiments and uploading the metrics results for 15 days
  
## Prerequisites for running the tests:
- Openshift cluster
- Tools like kubectl, oc

## How to run the test?

Use the below command to test :

```
cd <KRUIZE_REPO>/tests/scripts/remote_monitoring_tests/scale_test
./remote_monitoring_scale_test_bulk.sh [-i Kruize image] [-r results directory path] [-u No. of experiments (default - 5000)] [-d No. of days of results (default - 15)] [-n No. of clients (default - 20)] [-m results duration interval in mins, (default - 15)] [-t interval hours (default - 6)] [-s Initial start date (default - 2023-01-10T00:00:00.000Z)] [-q query db interval in mins, (default - 10)] [-c Experiment type [container|namespace|container_ns|gpucontainer] (default - container)] [-g No. of kruize replicas (default - 10)] [--api-version=<v1|legacy>]
```

Where values for remote_monitoring_scale_test_bulk.sh are:

```
usage: remote_monitoring_fault_tolerant_tests.sh 
    [ -i ] : optional. Kruize docker image to be used for testing
             default - quay.io/kruize/autotune:mvp_demo
    [ -r ] : Results directory path
	[-u No. of experiments (default - 5000)]
	[-d No. of days of results (default - 15)] 
	[-n No. of clients (default - 20)]
	[-m results duration interval in mins, (default - 15)]
	[-t interval hours (default - 6)]
	[-s Initial start date (default - 2023-01-10T00:00:00.000Z)]
	[-q query db interval in mins, (default - 10)]
	[-c Experiment type [container|namespace|container_ns|gpucontainer] (default - container)]
	[-a Test case (default - scale_5k) Valid values - [scale_5k/migration] migration - Used by db_migration test]
	[-g No. of kruize replicas (default - 10)] 
    [--api-version] : optional. API version to use - 'v1' for new API (/kruize/api/v1/recommendations), 'legacy' for old APIs (updateRecommendations/listRecommendations). Default is 'legacy'
```

For example,

```
cd <KRUIZE_REPO>/tests/scripts/remote_monitoring_tests/scale_test
./remote_monitoring_scale_test_bulk.sh -i quay.io/kruize/autotune_operator:0.6  -u 250  -d 15  -n 20 -t 6  -q 10  -s 2023-01-10T00:00:00.000Z  -r /tmp/scale_test_results

```

To test namespace experiments (Namespace exps is supported from kruize rel 0.7):

```
cd <KRUIZE_REPO>/tests/scripts/remote_monitoring_tests/scale_test
./remote_monitoring_scale_test_bulk.sh -i quay.io/kruize/autotune_operator:0.7  -u 250  -d 15  -n 20 -t 6  -q 10  -s 2023-01-10T00:00:00.000Z  -r /tmp/scale_test_results -c namespace

```

To test with a mix of container & namespace experiments (Namespace exps is supported from kruize rel 0.7):

```
cd <KRUIZE_REPO>/tests/scripts/remote_monitoring_tests/scale_test
./remote_monitoring_scale_test_bulk.sh -i quay.io/kruize/autotune_operator:0.7  -u 250  -d 15  -n 20 -t 6  -q 10  -s 2023-01-10T00:00:00.000Z  -r /tmp/scale_test_results -c container_ns

```

To test with the new Recommendations API v1.0 available from 0.11 release

```
cd <KRUIZE_REPO>/tests/scripts/remote_monitoring_tests/scale_test
./remote_monitoring_scale_test_bulk.sh -i quay.io/kruize/autotune_operator:0.11  -u 250  -d 15  -n 20 -t 6  -q 10  -s 2023-01-10T00:00:00.000Z  -r /tmp/scale_test_results --api-version=v1

```

### Isolated multi-instance scale (does not touch `openshift-tuning`)

Use `--instance=<id>` so CRC is deployed into namespace `<id>` (or `--namespace=<ns>`) with unique ClusterRole/CRB/PV names. The load client [`rosSimulationScalabilityTest.py`](scale_test/rosSimulationScalabilityTest.py) is **unchanged** (same closed-loop timings as standalone runs).

```
cd <KRUIZE_REPO>/tests/scripts/remote_monitoring_tests/scale_test
./remote_monitoring_scale_test_bulk.sh --instance=rm-0-12 \
  -i quay.io/kruize/autotune_operator:0.12 \
  -u 250 -d 2 -n 5 -g 2 -r /tmp/scale_rm-0-12
```

### Multi-instance YAML orchestrator

YAML-only runner (default file: [`instances.example.yaml`](instances.example.yaml) with `0.11.2` and `0.12`). Preflight **aborts** if any target namespace already exists.

```
cd <KRUIZE_REPO>/tests/scripts/remote_monitoring_tests
# -r omitted → atomically create ./rm-multi-<N> (mkdir claim; concurrent runs get different N)
# Each results dir has run_status: in_progress → completed|failed
./run_multi_instance_rm.sh -c openshift --suite scale -- -u 50 -d 1 -n 2 -g 2
# or functional:
./run_multi_instance_rm.sh -c openshift --suite functional --testcase test_e2e
```

Artifacts: `<results>/<id>/kruize_stats.json` (scale timings **parsed** from existing client logs), `<results>/comparison.csv`, `<results>/comparison.md`. Optional `--cleanup` removes only instance namespaces/objects — never `openshift-tuning`.

**Note:** Scale client timings are closed-loop batch metrics (same as historical scale_test). They are not open-loop / coordinated-omission-corrected latency percentiles.

Once the tests are complete, manually check the logs for any exceptions or errors or crashes. Verify if the execution times captured in exec_time.log are as expected.

Below commands are used in the script to capture the execution time and the count of experiments and results from the database:

Commands used to capture the execution time:

```
grep -m28 -H 'Time elapsed:' *.log | awk -F '[:.]' '{ sum[$1] += ($4 * 3600) + ($5 * 60) + $6 } END { for (key in sum) { printf "%s: Total time elapsed: %02d:%02d:%02d\n", key, sum[key] / 3600, (sum[key] / 60) % 60, sum[key] % 60 } }' | sort

```

The above command captures the execution time for 7 days of metrics data upload, modify -m28 (-m<4 * 7> ) to -m<4 * num_days_of_res>

Commands to fetch the count of experiments and results from the DB:

```
kubectl exec -it `kubectl get pods -o=name -n openshift-tuning | grep kruize-db` -n openshift-tuning -- psql -U admin -d kruizeDB -c "SELECT count(*) from public.kruize_experiments ;"; kubectl exec -it `kubectl get pods -o=name -n openshift-tuning | grep kruize-db` -n openshift-tuning -- psql -U admin -d kruizeDB -c "SELECT count(*) from public.kruize_results ;"; kubectl exec -it `kubectl get pods -o=name -n openshift-tuning | grep kruize-db` -n openshift-tuning -- psql -U admin -d kruizeDB -c "SELECT count(*) from public.kruize_recommendations ;"

```
