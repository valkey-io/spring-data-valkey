# Spring Boot Valkey Starter

A Spring Boot starter that provides auto-configuration for Valkey, enabling seamless integration with the high-performance Redis-compatible data store.

This starter simplifies the setup and configuration of Valkey in Spring Boot applications by providing auto-configuration for Valkey connections and Spring Data integration.

The project is a fork of Spring Boot Starter Data Redis 3.5.1 (part of the [Spring Boot](https://github.com/spring-projects/spring-boot) repository).

## Features

* Complete auto-configuration for Valkey connections, templates, repositories, and caching with zero-configuration defaults.
* Support for multiple Valkey drivers ([Valkey GLIDE](https://github.com/valkey-io/valkey-glide), [Lettuce](https://github.com/lettuce-io/lettuce-core), and [Jedis](https://github.com/redis/jedis)).
* Connection pooling configuration for all supported clients.
* Valkey Cluster auto-configuration and support.
* Valkey Sentinel configuration support (Lettuce and Jedis only).
* SSL/TLS connection support with Spring Boot SSL bundles.
* Spring Boot Actuator health indicators and metrics for Valkey connections.
* Property-based OpenTelemetry configuration for Valkey GLIDE, enabling automatic trace and metric export without application code changes.
* `@DataValkeyTest` slice test annotation for focused Valkey testing.
* Testcontainers integration with `@ServiceConnection` annotation.
* Docker Compose support for automatic service detection and startup.
* Configuration properties with IDE auto-completion support.

For the full list of Spring Data features see [Spring Data Valkey](../spring-data-valkey/).

## Installation

Add the starter and Valkey GLIDE dependencies:

```xml
<dependencies>
    <dependency>
        <groupId>io.valkey.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-valkey</artifactId>
        <version>${version}</version>
    </dependency>
    <dependency>
        <groupId>io.valkey</groupId>
        <artifactId>valkey-glide</artifactId>
        <classifier>${os.detected.classifier}</classifier>
        <version>${version}</version>
    </dependency>
</dependencies>
```

Add the os-maven-plugin for platform-specific GLIDE libraries:

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
</build>
```

Or to your `build.gradle`:

```gradle
plugins {
    id 'com.google.osdetector' version '1.7.3'
}

dependencies {
    implementation 'io.valkey.springframework.boot:spring-boot-starter-data-valkey:${version}'
    implementation 'io.valkey:valkey-glide:${version}:${osdetector.classifier}'
}
```

Note: The Valkey GLIDE dependency must also be explicitly added due to the OS classifier (platform-specific JAR).  To use the Lettuce or Jedis driver instead, add their dependencies and set `spring.data.valkey.client-type` accordingly.

## Getting Started

The starter provides zero-configuration defaults. Just add the dependency (see above) and optionally configure connection properties.

### Basic Configuration

Add Valkey connection properties to your `application.properties`:

```properties
spring.data.valkey.host=localhost
spring.data.valkey.port=6379
spring.data.valkey.password=your-password
spring.data.valkey.database=0
```

### Using ValkeyTemplate

```java
@Service
public class ValkeyService {

    @Autowired
    private ValkeyTemplate<Object, Object> valkeyTemplate;

    public void setValue(String key, Object value) {
        valkeyTemplate.opsForValue().set(key, value);
    }

    public Object getValue(String key) {
        return valkeyTemplate.opsForValue().get(key);
    }
}
```

### Using Spring Data Repositories

```java
@ValkeyHash("users")
public class User {
    @Id
    private String id;

    @Indexed
    private String name;

    @Indexed
    private String email;

    // getters and setters
}

public interface UserRepository extends CrudRepository<User, String> {
    List<User> findByName(String name);
    List<User> findByEmail(String email);
}
```

## Configuration Options

### Connection Settings

```properties
# Basic connection
spring.data.valkey.host=localhost
spring.data.valkey.port=6379
spring.data.valkey.username=default
spring.data.valkey.password=your-password
spring.data.valkey.database=0
spring.data.valkey.timeout=2000ms
spring.data.valkey.connect-timeout=2000ms
spring.data.valkey.client-name=my-app-name  # Lettuce and Jedis only

# Client type (valkeyglide, lettuce, or jedis)
spring.data.valkey.client-type=valkeyglide
```

### Connection Pooling

```properties
# Valkey GLIDE pooling
spring.data.valkey.valkeyglide.max-pool-size=8

# Lettuce pooling
spring.data.valkey.lettuce.pool.enabled=true
spring.data.valkey.lettuce.pool.max-active=8
spring.data.valkey.lettuce.pool.max-idle=8
spring.data.valkey.lettuce.pool.min-idle=0
spring.data.valkey.lettuce.pool.max-wait=-1ms

# Jedis pooling
spring.data.valkey.jedis.pool.enabled=true
spring.data.valkey.jedis.pool.max-active=8
spring.data.valkey.jedis.pool.max-idle=8
spring.data.valkey.jedis.pool.min-idle=0
spring.data.valkey.jedis.pool.max-wait=-1ms
```

### Cluster Configuration

```properties
# Generic cluster settings
spring.data.valkey.cluster.nodes=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002
spring.data.valkey.cluster.max-redirects=3

# Valkey GLIDE cluster settings
spring.data.valkey.valkeyglide.cluster.refresh.adaptive=true
spring.data.valkey.valkeyglide.cluster.refresh.period=30s
spring.data.valkey.valkeyglide.cluster.refresh.dynamic-refresh-sources=true
```

### Sentinel Configuration

```properties
# Lettuce and Jedis only - GLIDE does not support Sentinel at this time
spring.data.valkey.sentinel.master=mymaster
spring.data.valkey.sentinel.nodes=127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381
spring.data.valkey.sentinel.username=sentinel-user
spring.data.valkey.sentinel.password=sentinel-password
```

### SSL Configuration

```properties
spring.data.valkey.ssl.enabled=true
spring.data.valkey.ssl.bundle=valkey-ssl
```

### Advanced Configuration

```properties
# Valkey GLIDE advanced settings
spring.data.valkey.valkeyglide.connection-timeout=2000ms
spring.data.valkey.valkeyglide.read-from=PRIMARY
spring.data.valkey.valkeyglide.inflight-requests-limit=250
spring.data.valkey.valkeyglide.client-az=us-west-2a
```

## Actuator Support

Spring Boot Actuator integration provides health indicators and metrics for Valkey connections.

### Health Indicators

Add Actuator dependency and enable health endpoints:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Configure health endpoints in `application.properties`:

```properties
management.endpoints.web.exposure.include=health,metrics
management.health.valkey.enabled=true
```

Access health information at `/actuator/health/valkey`:

```json
{
  "status": "UP",
  "details": {
    "version": "8.0.1",
    "cluster_size": 3,
    "slots_up": 16384,
    "slots_fail": 0
  }
}
```

### Metrics

Valkey metrics are automatically collected when Micrometer is present:

- **Connection metrics** - Pool size, active connections
- **Command metrics** - Latency, success/failure rates (Lettuce only)
- **Cache metrics** - Hits, misses, puts, removals

View metrics at `/actuator/metrics/valkey.*` or integrate with monitoring systems like Prometheus.

## OpenTelemetry Integration

The starter provides automatic OpenTelemetry instrumentation when using the Valkey GLIDE client. Valkey GLIDE has built-in OpenTelemetry support that can be enabled via Spring Boot properties - no additional dependencies are required.

Configure OpenTelemetry in `application.properties`:

```properties
# Enable OpenTelemetry instrumentation in GLIDE
spring.data.valkey.valkey-glide.open-telemetry.enabled=true

# Configure trace and metric endpoints
spring.data.valkey.valkey-glide.open-telemetry.traces-endpoint=http://localhost:4318/v1/traces
spring.data.valkey.valkey-glide.open-telemetry.metrics-endpoint=http://localhost:4318/v1/metrics

# Optionally configure sampling and flush behavior
spring.data.valkey.valkey-glide.open-telemetry.sample-percentage=10
spring.data.valkey.valkey-glide.open-telemetry.flush-interval-ms=50
```

Automatically collected telemetry includes command duration, success/failure rates, connection pool metrics, and distributed traces. For local development, use Docker Compose with an OpenTelemetry Collector to view traces and metrics.

## Testing

### @DataValkeyTest

The starter provides `@DataValkeyTest` annotation for focused testing of Valkey components:

```java
@DataValkeyTest
class MyValkeyTest {

    @Autowired
    private ValkeyConnectionFactory connectionFactory;

    @Test
    void contextLoads() {
        assertThat(connectionFactory).isNotNull();
    }
}
```

To use `@DataValkeyTest`, add the starter with `test` scope:

```xml
<dependency>
    <groupId>io.valkey.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-valkey</artifactId>
    <scope>test</scope>
</dependency>
```

You can add the starter both with and without `test` scope to get both production and test features.

### Testcontainers Support

Use `@ServiceConnection` for automatic test configuration:

```java
@SpringBootTest
@Testcontainers
class MyIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey:latest")
        .withExposedPorts(6379);

    @Autowired
    private ValkeyTemplate<String, String> valkeyTemplate;

    @Test
    void test() {
        valkeyTemplate.opsForValue().set("key", "value");
        assertThat(valkeyTemplate.opsForValue().get("key")).isEqualTo("value");
    }
}
```

### Docker Compose Support

Spring Boot automatically detects and starts Valkey from `compose.yml`:

```java
@SpringBootTest
class MyIntegrationTest {

    @Autowired
    private ValkeyTemplate<String, String> valkeyTemplate;

    @Test
    void test() {
        valkeyTemplate.opsForValue().set("key", "value");
        assertThat(valkeyTemplate.opsForValue().get("key")).isEqualTo("value");
    }
}
```

With a `compose.yml` in your project root (`composeFile` attribute can also specify a different filename/location):

```yml
services:
  valkey:
    image: 'valkey/valkey:latest'
    ports:
      - '6379:6379'
```

## Virtual Threads / AsyncTaskExecutor

Valkey GLIDE provides async operations via CompletableFuture and does not require an external thread pool configuration.  As a result, if `spring.task.execution.pool.virtual-threads.enabled` is enabled when using GLIDE, a warning is logged and the configuration option is ignored.

## Building from Source

See instructions on starting a Valkey server in the [Developer Guide](../DEVELOPER.md). The standalone and cluster instances started by the Makefile are used in the unit tests.

Then build the starter:

```bash
$ ../mvnw clean install
```
