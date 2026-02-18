# Benchmark Configuration Guide

This directory contains configuration files for the benchmark CI workflow.

## CI Workflow Inputs

### Predefined Configs

| Input              | Description                          | Options                                                                                   |
| ------------------ | ------------------------------------ | ----------------------------------------------------------------------------------------- |
| `primary_driver`   | Client library to benchmark          | `spring-data-valkey`, `spring-data-redis`, `valkey-glide`, `jedis`, `lettuce`, `redisson` |
| `secondary_driver` | Underlying driver for spring-data-\* | `valkey-glide`, `jedis`, `lettuce`, `none`                                                |
| `topology`         | Server topology                      | `standalone`, `cluster`                                                                   |
| `workload`         | Test scenario                        | `example-workload`, `example-workload-single-client`                                      |

### Custom Configs

For custom use cases, you can provide custom JSON configs directly:

| Input                    | Description                                              |
| ------------------------ | -------------------------------------------------------- |
| `custom_driver_config`   | Custom driver JSON (overrides driver/topology selection) |
| `custom_workload_config` | Custom workload JSON (overrides workload selection)      |

### Version Inputs

| Input               | Description                                                                                                 |
| ------------------- | ----------------------------------------------------------------------------------------------------------- |
| `primary_version`   | Version of primary driver. Leave empty for `spring-data-valkey` (uses branch HEAD)                          |
| `secondary_version` | Version or commit ID of secondary driver. Commit IDs (7-40 hex chars) trigger source build for valkey-glide |
| `job_id_prefix`     | Optional prefix for job ID (e.g., `nightly`, `pr-123`)                                                      |

## How Config Selection Works

The CI workflow maps your input selections to JSON config files using a naming convention.

### Driver Config Resolution

The workflow constructs the driver config filename from your inputs:

| Primary Driver       | Secondary Driver | Topology     | Resolved File                                      |
| -------------------- | ---------------- | ------------ | -------------------------------------------------- |
| `valkey-glide`       | (ignored)        | `standalone` | `example-valkey-glide-standalone.json`             |
| `valkey-glide`       | (ignored)        | `cluster`    | `example-valkey-glide-cluster.json`                |
| `spring-data-valkey` | `valkey-glide`   | `standalone` | `example-spring-data-valkey-glide-standalone.json` |
| `spring-data-valkey` | `valkey-glide`   | `cluster`    | `example-spring-data-valkey-glide-cluster.json`    |
| `spring-data-valkey` | `jedis`          | `cluster`    | `example-spring-data-valkey-jedis-cluster.json`    |

**Pattern:**

- Standalone drivers: `example-{primary_driver}-{topology}.json`
- Spring-data drivers: `example-{primary_driver}-{secondary_driver}-{topology}.json`
  - Note: `valkey-glide` is shortened to `glide` in filenames

### Workload Config Resolution

The `workload` input maps directly to a file:

- `example-workload` → `workloads/example-workload.json`
- `example-workload-single-client` → `workloads/example-workload-single-client.json`

### Custom Configs

When you provide `custom_driver_config` or `custom_workload_config`, the workflow:

1. Writes your JSON to a temp file
2. Validates it with `jq`
3. Uses it instead of the predefined config

This allows testing configurations not in the predefined set.

For the full specification of config formats to use when constructing custom configs, see the JSON schema files:

- **Driver config schema:** [schemas/driver-config.schema.json](schemas/driver-config.schema.json)
- **Workload config schema:** [schemas/workload-config.schema.json](schemas/workload-config.schema.json)

### Key config rules:

- `spring-data-valkey` and `spring-data-redis` require `secondary_driver_id`
- `spring-data-redis` does NOT support `valkey-glide` as secondary driver
- Standalone drivers (`jedis`, `lettuce`, `valkey-glide`, `redisson`) ignore `secondary_driver_id`
