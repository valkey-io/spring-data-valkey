# Spring Boot + Valkey-GLIDE + OpenTelemetry

This example demonstrates using **Spring Boot** with **Spring Data Valkey**, backed by the **Valkey-GLIDE** client, with **OpenTelemetry tracing and metrics enabled via configuration**.

On startup, the application executes a small number of Valkey `SET` / `GET` commands using `StringValkeyTemplate`. Each command is automatically instrumented by GLIDE and exported via OpenTelemetry.

## How it works

- Spring Boot auto-configures all Valkey beans
- Valkey-GLIDE is selected using `client-type=valkeyglide`
- OpenTelemetry is enabled inside GLIDE using Spring Boot properties
- An OpenTelemetry Collector is started using Docker Compose
- Traces and metrics are exported through the collector

No explicit OpenTelemetry SDK or client setup code is required.

## Running the example

From the project root:

```bash
$ ./mvnw -q compile exec:java -pl examples/boot-telemetry
```

Or from this boot-telemetry folder:

```bash
$ ../../mvnw -q compile exec:java -Dspring.docker.compose.file=compose.yaml
```

Docker Compose is started automatically and kept running after the application exits.

When finished, stop and remove the container:

```bash
$ docker stop boot-telemetry-otel-collector-1 && docker rm boot-telemetry-otel-collector-1
```


## Key configuration properties

```properties
spring.data.valkey.client-type=valkeyglide

spring.data.valkey.valkey-glide.open-telemetry.enabled=true
spring.data.valkey.valkey-glide.open-telemetry.traces-endpoint=http://localhost:4318/v1/traces
spring.data.valkey.valkey-glide.open-telemetry.metrics-endpoint=http://localhost:4318/v1/metrics

spring.docker.compose.lifecycle-management=start-only
```

## Inspecting OpenTelemetry

```bash
$ docker logs -f boot-telemetry-otel-collector-1
```

If you change the configuration of the docker shutdown the existing containers first and then run the example again:

```bash
$ docker compose down --remove-orphans
```
