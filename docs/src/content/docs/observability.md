---
title: Observability
description: Observability Integration for monitoring and metrics
---

Getting insights from an application component about its operations, timing and relation to application code is crucial to understand latency.
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
