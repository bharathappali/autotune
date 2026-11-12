# **Kruize Autotune tests**

Kruize Autotune tests repository contains tests to validate Kruize in remote monitoring and local monitoring modes. 

## Functional tests description

### Remote monitoring tests

  Here we test Kruize [Remote monitoring APIs](/design/MonitoringModeAPI.md). 

#### API tests

  The tests does the following:
  - Deploys kruize in non-CRD mode using the deploy script from the autotune repo
  - Validates the behaviour of createExperiment, updateResults and listRecommendations APIs in various scenarios covering both positive and negative usecases.

  For details refer this [doc](/tests/scripts/remote_monitoring_tests/Remote_monitoring_tests.md)

#### Stress test

To run the stress test refer the Stress test [README](/tests/scripts/remote_monitoring_tests/README.md)

#### Fault tolerant test

To run the fault tolerant test refer the [README](/tests/scripts/remote_monitoring_tests/fault_tolerant_tests.md)

### Local monitoring tests

Here we test Kruize [Local monitoring APIs](/design/KruizeLocalAPI.md).

#### API tests

  The tests does the following:
  - Deploys kruize in non-CRD mode using the deploy script from the autotune repo
  - Validates the behaviour of list datasources, import metadata and list metadata APIs in various scenarios covering both positive and negative usecases.

  For details refer this [doc](/tests/scripts/local_monitoring_tests/Local_monitoring_tests.md)

## Supported Clusters
- Minikube, Openshift

## Prerequisites for running the tests:

- Minikube setup 
- Tools like docker, kubectl, and jq
- python

Clone the kruize/benchmarks repo using the below command:

```
git clone https://github.com/kruize/benchmarks.git

```

## How to run the tests?

First, cleanup any previous instances of autotune using the below command:

```
<AUTOTUNE_REPO>/tests/test_autotune.sh -c minikube -t
```

Use the below command to test :

```
<AUTOTUNE_REPO>/tests/test_autotune.sh -c minikube [-i autotune image] [-o [operator image]] [--testsuite=Group of tests that you want to perform] [--testcase=Particular test case that you want to test] [-n namespace] [--resultsdir=results directory] [--skipsetup] [--cleanup_prometheus] [--api-version=<v1|legacy>] [-t cleanup kruize setup]
```

Where values for test_autotune.sh are:

```
usage: test_autotune.sh [ -c ] : cluster type. Supported type - minikube
                        [ -i ] : optional. Autotune docker image to be used for testing, default - kruize/autotune_operator:test
			[ -o ] : optional. Deploy Kruize in operator mode (only for local_monitoring_tests). Optionally specify operator image, default - quay.io/kruize/kruize-operator:<version>
			[ --testsuite ] : Testsuite to run. Use testsuite=help, to list the supported testsuites
			[ --testcase ] : Testcase to run. Use testcase=help along with the testsuite name to list the supported testcases in that testsuite
			[ -n ] : optional. Namespace to deploy autotune
			[ --resultsdir ] : optional. Results directory location, by default it creates the results directory in current working directory
			[ --skipsetup ] : optional. Specifying this option skips the autotune setup & application deployment
			[ --cleanup_prometheus ] : optional. Specifying this option along with -t option cleans up prometheus setup
			[ --api-version ] : optional. API version to use for recommendations - 'v1' for new API (/kruize/api/v1/recommendations), 'legacy' for old APIs (updateRecommendations/listRecommendations). Default is 'legacy'

Note: If you want to run a particular testcase then it is mandatory to specify the testsuite

```

For example,

To run remote monitoring tests,

```
<AUTOTUNE_REPO>/tests/test_autotune.sh -c minikube -i kruize/autotune_operator:0.8 --testsuite=remote_monitoring_tests --resultsdir=/home/results
```

To run local monitoring tests,

```
<AUTOTUNE_REPO>/tests/test_autotune.sh -c minikube -i kruize/autotune_operator:0.8 --testsuite=local_monitoring_tests --resultsdir=/home/results
```

To run local monitoring tests in operator mode,

```
<AUTOTUNE_REPO>/tests/test_autotune.sh -c minikube -o --testsuite=local_monitoring_tests --resultsdir=/home/results
```

Or with a specific operator image,

```
<AUTOTUNE_REPO>/tests/test_autotune.sh -c minikube -o quay.io/kruize/kruize-operator:latest --testsuite=local_monitoring_tests --resultsdir=/home/results
```

## Multi-instance remote monitoring (OpenShift)

Run several Kruize CRC stacks in isolated namespaces without touching the shared `openshift-tuning` install.

- Config is **YAML-only** ([`scripts/remote_monitoring_tests/instances.example.yaml`](scripts/remote_monitoring_tests/instances.example.yaml)); if `-f` is omitted the example file is used (`0.11.2` and `0.12`).
- `namespace` is optional and defaults to `id`.
- Preflight fails if any target namespace already exists — change the YAML.
- Bare image tags resolve to `quay.io/kruize/autotune_operator:<tag>`; full refs (any registry) are used as-is.

```
# Results default to ./rm-multi-<N> (atomic mkdir claim; safe if two runs start together).
# run_status starts as in_progress and becomes completed|failed when the run finishes.
<AUTOTUNE_REPO>/tests/scripts/remote_monitoring_tests/run_multi_instance_rm.sh \
  -c openshift --suite functional --testcase test_e2e

# Scale (same rosSimulationScalabilityTest client as standalone; timings parsed from its logs):
<AUTOTUNE_REPO>/tests/scripts/remote_monitoring_tests/run_multi_instance_rm.sh \
  -c openshift --suite scale -- -u 50 -d 1 -n 2 -g 2

# Or set an explicit results dir:
<AUTOTUNE_REPO>/tests/scripts/remote_monitoring_tests/run_multi_instance_rm.sh \
  -c openshift -r /tmp/rm-multi --suite functional --testcase test_e2e
```

Single-instance helper:

```
<AUTOTUNE_REPO>/tests/test_autotune.sh -c openshift --instance=rm-0-12 \
  -i quay.io/kruize/autotune_operator:0.12 \
  --testsuite=remote_monitoring_tests --testcase=test_e2e --resultsdir=/tmp/rm-0-12
```
