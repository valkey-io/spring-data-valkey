---
title: Features
description: What Spring Data Valkey offers beyond Spring Data Redis
---

Spring Data Valkey provides first-class support for Valkey with enhanced capabilities through the [Valkey GLIDE](https://github.com/valkey-io/valkey-glide) driver.

## What's New in Spring Data Valkey

### Valkey GLIDE Driver Support

Spring Data Valkey is the first Spring Data module to support Valkey GLIDE, a high-performance client library purpose-built for Valkey. GLIDE is the recommended driver and provides capabilities not available in traditional Redis clients.

See the [Drivers](/valkey/drivers) page for a complete feature comparison across all supported drivers.

### Availability Zone Awareness

AZ-aware reads for cluster deployments allow read operations to prefer replicas in the same availability zone, reducing cross-AZ data transfer costs and improving latency.

### IAM Authentication (AWS)

Native support for AWS IAM authentication with Amazon ElastiCache and MemoryDB. The client automatically generates and refreshes short-lived IAM tokens, eliminating static passwords. *(Available in upcoming release)*

### Native OpenTelemetry Support

Built-in OpenTelemetry instrumentation for GLIDE enables automatic trace and metric export through Spring Boot properties—no code changes required.

### Pub/Sub Support

GLIDE provides pub/sub support with callback-based message delivery configured at client creation time. The driver supports configurable reconnection strategies through `BackoffStrategy`.

### High-Performance I/O

GLIDE's internal connection pooling and async operations are optimized for high-throughput scenarios, delivering improved performance for demanding workloads.

## Spring Boot Enhancements

* Property-based IAM authentication configuration
* Property-based OpenTelemetry configuration for GLIDE
* Auto-configuration for all three drivers (GLIDE, Lettuce, Jedis)
* Enhanced Actuator metrics for GLIDE connections

## Migration from Spring Data Redis

Spring Data Valkey maintains API compatibility with Spring Data Redis. Migration primarily involves updating dependencies and package names. See the [Migration Guide](/commons/migration) for details.
