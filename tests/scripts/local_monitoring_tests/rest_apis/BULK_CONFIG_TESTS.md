# Bulk Config API Tests

## Overview

This document describes the test suite for the Bulk Config API (`/bulkConfigs` endpoint). The tests validate all CRUD operations and error handling scenarios.

## Test File

**Location**: `tests/scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py`

## Prerequisites and Setup

### Important: Kruize Deployment Required

**YES, you need to have Kruize already deployed and running on your kind cluster before running these tests.**

The test framework does **NOT** deploy Kruize automatically when you run tests directly with pytest. The test scripts assume Kruize is already running and accessible.

### Two Ways to Run Tests:

#### Option 1: Using test_autotune.sh (Recommended - Handles Setup)

The `test_autotune.sh` script **WILL** deploy Kruize for you:

```bash
cd tests
./test_autotune.sh -c kind --testsuite=local_monitoring_tests
```

This script:
1. Deploys Kruize on your kind cluster (lines 66-92 in local_monitoring_tests.sh)
2. Waits for Kruize to be ready (60 second sleep)
3. Installs Python dependencies
4. Runs all the tests

You can skip the setup if Kruize is already running:
```bash
./test_autotune.sh -c kind --testsuite=local_monitoring_tests --skipsetup
```

#### Option 2: Direct pytest (Requires Manual Setup)

If you run pytest directly, you **MUST** have Kruize already deployed:

```bash
# 1. First, deploy Kruize manually on kind cluster
cd /path/to/kruize
./deploy.sh -c kind

# 2. Wait for Kruize to be ready
kubectl wait --for=condition=ready pod -l app=kruize -n monitoring --timeout=300s

# 3. Then run the tests
cd tests
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py --cluster_type=kind -v
```

### Setup Requirements

Before running tests, ensure:

1. **Kind cluster is running**:
   ```bash
   kind get clusters
   ```

2. **Kruize is deployed** (if using direct pytest):
   ```bash
   kubectl get pods -n monitoring | grep kruize
   ```

3. **Kruize is accessible**:
   ```bash
   # Port forward if needed
   kubectl port-forward -n monitoring svc/kruize 8080:8080
   ```

4. **Python dependencies installed**:
   ```bash
   pip install -r tests/scripts/local_monitoring_tests/requirements.txt
   ```

## Test Categories

### 1. Sanity Tests (`@pytest.mark.sanity`)

These are the core functional tests that validate basic CRUD operations:

- **`test_create_bulk_config`**: Create a bulk config with valid input
- **`test_create_duplicate_bulk_config`**: Verify error when creating duplicate config (409 error)
- **`test_list_all_bulk_configs`**: List all bulk configs
- **`test_list_bulk_config_by_name`**: List a specific bulk config by name
- **`test_update_bulk_config`**: Update an existing bulk config
- **`test_delete_bulk_config`**: Delete a bulk config

### 2. Negative Tests (`@pytest.mark.negative`)

These tests validate error handling and validation:

- **`test_create_bulk_config_missing_config_name`**: Missing required field `config_name`
- **`test_create_bulk_config_missing_cluster_name`**: Missing required field `cluster_name`
- **`test_create_bulk_config_missing_datasources`**: Missing required field `datasources`
- **`test_create_bulk_config_invalid_datasource`**: Non-existent datasource reference
- **`test_create_bulk_config_missing_metadata_profile`**: Missing required field `metadata_profile`
- **`test_create_bulk_config_invalid_metadata_profile`**: Non-existent metadata profile reference
- **`test_create_bulk_config_invalid_performance_profile`**: Non-existent performance profile reference
- **`test_create_bulk_config_invalid_config_name_format`**: Invalid characters in config_name
- **`test_update_non_existent_bulk_config`**: Update non-existent config (404 error)
- **`test_delete_non_existent_bulk_config`**: Delete non-existent config (404 error)
- **`test_list_non_existent_bulk_config`**: List non-existent config (404 error)

### 3. Extended Tests (`@pytest.mark.extended`)

These tests cover additional scenarios:

- **`test_create_bulk_config_with_minimal_fields`**: Create config with only required fields
- **`test_update_bulk_config_partial_update`**: Partial update (only some fields)

## Test Data Structure

### Valid Bulk Config Payload

```json
{
  "config_name": "test-bulk-config",
  "cluster_name": "test-cluster",
  "datasources": ["prometheus-1"],
  "namespaces": ["default", "monitoring"],
  "labels": {
    "app": "test",
    "env": "dev"
  },
  "experiment_types": ["container"],
  "metadata_profile": "cluster-metadata-local-monitoring",
  "performance_profile": "resource-optimization-local-monitoring",
  "trial_settings": {
    "measurement_duration": "15min"
  },
  "recommendation_settings": {
    "scheduling": "24h",
    "terms": ["short", "medium"],
    "models": ["performance"]
  },
  "webhook_url": "http://example.com/webhook",
  "enabled": true
}
```

### Minimal Required Fields

```json
{
  "config_name": "test-minimal-config",
  "cluster_name": "test-cluster",
  "datasources": ["prometheus-1"],
  "experiment_types": ["container"],
  "metadata_profile": "cluster-metadata-local-monitoring",
  "recommendation_settings": {
    "scheduling": "24h",
    "terms": ["short"],
    "models": ["performance"]
  }
}
```

## Running the Tests

### Prerequisites

1. Kruize service must be running
2. Required profiles must exist:
   - Metric profile: `resource-optimization-local-monitoring`
   - Metadata profile: `cluster-metadata-local-monitoring`

### Run All Bulk Config Tests

```bash
cd tests
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py -v
```

### Run Specific Test Categories

**Sanity tests only:**
```bash
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py -m sanity -v
```

**Negative tests only:**
```bash
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py -m negative -v
```

**Extended tests only:**
```bash
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py -m extended -v
```

### Run Specific Test

```bash
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py::test_create_bulk_config -v
```

### Run with Different Cluster Types

```bash
# For minikube
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py --cluster_type=minikube -v

# For kind
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py --cluster_type=kind -v

# For openshift
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py --cluster_type=openshift -v
```

### Run via Test Script

```bash
cd tests
./test_autotune.sh -c minikube -t local_monitoring_tests
```

## Expected Results

### Success Scenarios

| Test | Expected Status Code | Expected Response |
|------|---------------------|-------------------|
| Create bulk config | 201 | Config object with all fields |
| List all configs | 200 | Array of config objects |
| List by name | 200 | Single config object |
| Update config | 200 | Updated config object |
| Delete config | 200 | Success message |

### Error Scenarios

| Test | Expected Status Code | Expected Error Message |
|------|---------------------|----------------------|
| Duplicate config | 409 | "already exists" |
| Missing required field | 400 | Field name in error |
| Invalid datasource | 400 | "does not exist" |
| Invalid metadata profile | 400 | "does not exist" |
| Invalid performance profile | 400 | "does not exist" |
| Invalid config_name format | 400 | "alphanumeric" |
| Non-existent config (update/delete/list) | 404 | "not found" |

## Test Cleanup

All tests include cleanup logic to:
1. Delete test configs before creating them (to handle test reruns)
2. Delete test configs after test completion
3. Use unique config names to avoid conflicts

## Helper Functions Used

From `tests/scripts/helpers/kruize.py`:
- `create_bulk_config(bulk_config, logging=True)`
- `list_bulk_configs(config_name=None, logging=True)`
- `update_bulk_config(config_name, update_data, logging=True)`
- `delete_bulk_config(config_name, logging=True)`

From `tests/scripts/helpers/utils.py`:
- Error message constants for validation

## Integration with CI/CD

These tests can be integrated into CI/CD pipelines:

```bash
# Run sanity tests in CI
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py -m sanity --junitxml=results.xml

# Run all tests in nightly builds
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py --junitxml=results.xml
```

## Troubleshooting

### Test Failures

1. **Connection errors**: Ensure Kruize service is running and accessible
2. **Profile not found errors**: Run `delete_and_create_metric_profile()` and `delete_and_create_metadata_profile()`
3. **Cleanup issues**: Manually delete test configs via API if needed

### Debug Mode

Run tests with verbose logging:
```bash
pytest scripts/local_monitoring_tests/rest_apis/test_bulk_config_api.py -v -s
```

## Future Enhancements

Potential additional tests:
1. Concurrent creation/update tests
2. Large payload tests (many namespaces, labels)
3. Performance tests (bulk operations)
4. Integration tests with actual experiment creation
5. Webhook notification tests
6. Scheduling trigger tests