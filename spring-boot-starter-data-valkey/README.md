# Spring Boot Valkey Starter

A Spring Boot starter that provides auto-configuration for Valkey, enabling seamless integration with the high-performance Redis-compatible data store.

This starter simplifies the setup and configuration of Valkey in Spring Boot applications by providing auto-configuration for Valkey connections and Spring Data integration.

## Current Limitations

- Valkey GLIDE Sentinel support
- Valkey GLIDE connection pooling

## Installation

Add the starter and Valkey GLIDE (along with OS detector) to your `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>io.valkey.springframework.data</groupId>
        <artifactId>spring-boot-starter-data-valkey</artifactId>
        <version>${version}</version>
    </dependency>
    <dependency>
        <groupId>io.valkey</groupId>
        <artifactId>valkey-glide</artifactId>
        <version>${version}</version>
        <classifier>${os.detected.classifier}</classifier>
    </dependency>
</dependencies>

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
    implementation 'io.valkey.springframework.data:spring-boot-starter-data-valkey:${version}'
    implementation 'io.valkey:valkey-glide:${version}:${osdetector.classifier}'
}
```

Note that the Valkey GLIDE dependency must also be explicitly added due to the OS classifier (platform-specific JAR).  To use the Lettuce or Jedis driver instead, add their dependencies and set `spring.data.valkey.client-type` accordingly.

## Quick Start

The starter provides zero-configuration defaults. Just add the dependency and optionally configure connection properties.

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

# Client type (valkeyglide, lettuce, or jedis)
spring.data.valkey.client-type=valkeyglide
```

### Connection Pooling

```properties
# Valkey GLIDE does not support pooling at this time

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
# For Lettuce and Jedis only, GLIDE does not support Sentinel at this time
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

## Testcontainers Support

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

## Docker Compose Support

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

With a `compose.yml` in your project root:

```yml
services:
  valkey:
    image: 'valkey/valkey:latest'
    ports:
      - '6379:6379'
```

## Building from Source

### Prerequisites

- JDK 17 or higher
- Maven 3.9 or higher (or use included wrapper)
- Docker (for integration tests)

### Build Commands

```bash
# Build the project (compile project, run tests, and create JAR)
./mvnw clean package

# Run tests only
./mvnw test

# Build without tests
./mvnw clean package -DskipTests
```

## License

The Spring Boot Valkey Starter is Open Source software released under the Apache 2.0 license.
