---
title: Observability
description: Observability Integration for monitoring and metrics
---

Getting insights from an application component about its operations, timing and relation to application code is crucial to understand latency.

Spring Data Valkey provides observability integration through two approaches:
- *OpenTelemetry integration* through Valkey GLIDE for direct OTLP export
- *Micrometer integration* through the Lettuce driver for Spring-based metrics and tracing


## OpenTelemetry Integration (GLIDE)

Valkey GLIDE provides built-in OpenTelemetry support for direct export of traces and metrics to OTLP endpoints.
This integration operates independently of Spring's observability infrastructure and exports telemetry data directly to OpenTelemetry collectors.

### Spring Boot Configuration

When using Spring Boot, OpenTelemetry can be enabled via application properties:

```properties
# Enable OpenTelemetry instrumentation in GLIDE
spring.data.valkey.valkey-glide.open-telemetry.enabled=true

# Configure trace and metric endpoints (defaults shown)
spring.data.valkey.valkey-glide.open-telemetry.traces-endpoint=http://localhost:4318/v1/traces
spring.data.valkey.valkey-glide.open-telemetry.metrics-endpoint=http://localhost:4318/v1/metrics

# Optionally configure sampling and flush behavior
spring.data.valkey.valkey-glide.open-telemetry.sample-percentage=1
spring.data.valkey.valkey-glide.open-telemetry.flush-interval-ms=5000
```

### Programmatic Configuration

For non-Spring Boot applications, configure OpenTelemetry programmatically:

```java
@Configuration
public class ValkeyGlideOpenTelemetryConfig {

  @Bean
  public ValkeyGlideConnectionFactory valkeyConnectionFactory() {

    ValkeyGlideClientConfiguration clientConfig = ValkeyGlideClientConfiguration.builder()
      .useOpenTelemetry(ValkeyGlideClientConfiguration.OpenTelemetryForGlide.defaults())
      .build();

    return new ValkeyGlideConnectionFactory(new ValkeyStandaloneConfiguration(), clientConfig);
  }
}
```

For custom OpenTelemetry configuration:

```java
ValkeyGlideClientConfiguration clientConfig = ValkeyGlideClientConfiguration.builder()
  .useOpenTelemetry(new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(
    "http://jaeger:4318/v1/traces",      // traces endpoint
    "http://prometheus:4318/v1/metrics", // metrics endpoint
    10,                                  // sample percentage
    1000L                                // flush interval (ms)
  ))
  .build();
```

:::note
GLIDE's OpenTelemetry integration exports telemetry data directly to OTLP endpoints and does not integrate with Spring's Micrometer-based observability infrastructure. Automatically collected telemetry includes command duration, success/failure rates, connection pool metrics, and distributed traces.
:::

## Micrometer Integration (Lettuce)

Spring Data Valkey ships with a Micrometer integration through the Lettuce driver to collect observations during Valkey interaction.
Once the integration is set up, Micrometer will create meters and spans (for distributed tracing) for each Valkey command.

To enable the integration, apply the following configuration to `LettuceClientConfiguration`:

```java
@Configuration
class ObservabilityConfiguration {

  @Bean
  public ClientResources clientResources(ObservationRegistry observationRegistry) {

    return ClientResources.builder()
              .tracing(new MicrometerTracingAdapter(observationRegistry, "my-valkey-cache"))
              .build();
  }

  @Bean
  public LettuceConnectionFactory lettuceConnectionFactory(ClientResources clientResources) {

    LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                                                .clientResources(clientResources).build();
    ValkeyConfiguration valkeyConfiguration = …;
    return new LettuceConnectionFactory(valkeyConfiguration, clientConfig);
  }
}
```

See also [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/reference/specification/trace/semantic_conventions/database/#valkey) for further reference.

## Observability - Metrics

Below you can find a list of all metrics declared by this project.

### Valkey Command Observation

> Timer created around a Valkey command execution.

**Metric name** `spring.data.valkey`. **Type** `timer` and **base unit** `seconds`.

Fully qualified name of the enclosing class `io.valkey.springframework.data.connection.lettuce.observability.ValkeyObservation`.

*Table 1. Low cardinality Keys*

| Name | Description |
|------|-------------|
| `db.operation` | Valkey command value. |
| `db.valkey.database_index` | Valkey database index. |
| `db.system` | Database system. |
| `db.user` | Valkey user. |
| `net.peer.name` | Name of the database host. |
| `net.peer.port` | Logical remote port number. |
| `net.sock.peer.addr` | Mongo peer address. |
| `net.sock.peer.port` | Mongo peer port. |
| `net.transport` | Network transport. |

*Table 2. High cardinality Keys*

| Name | Description |
|------|-------------|
| `db.statement` | Valkey statement. |
| `spring.data.valkey.command.error` | Valkey error response. |

## Observability - Spans

Below you can find a list of all spans declared by this project.

### Valkey Command Observation Span

> Timer created around a Valkey command execution.

**Span name** `spring.data.valkey`.

Fully qualified name of the enclosing class `io.valkey.springframework.data.connection.lettuce.observability.ValkeyObservation`.

*Table 3. Tag Keys*

| Name | Description |
|------|-------------|
| `db.operation` | Valkey command value. |
| `db.valkey.database_index` | Valkey database index. |
| `db.statement` | Valkey statement. |
| `db.system` | Database system. |
| `db.user` | Valkey user. |
| `net.peer.name` | Name of the database host. |
| `net.peer.port` | Logical remote port number. |
| `net.sock.peer.addr` | Mongo peer address. |
| `net.sock.peer.port` | Mongo peer port. |
| `net.transport` | Network transport. |
| `spring.data.valkey.command.error` | Valkey error response. |
