# Spring Boot Valkey Starter

A Spring Boot starter that provides auto-configuration for Valkey, enabling seamless integration with the high-performance Redis-compatible data store.

This starter simplifies the setup and configuration of Valkey in Spring Boot applications by providing:

- Auto-configuration for Valkey connections
- Spring Data integration
- Connection pooling and management

## Features

- **Multiple Client Support**: Works with multiple drivers (Valkey GLIDE, Lettuce, and Jedis)
- **Connection Pooling**: Automatic connection pool configuration with Apache Commons Pool2
- **Reactive Support**: Full reactive programming support with Spring WebFlux
- **SSL/TLS Support**: Secure connections with SSL bundle configuration
- **Cluster & Sentinel**: Support for Valkey cluster and sentinel configurations
- **Spring Data Integration**: Repositories, templates, and reactive templates

## Installation

Add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>io.valkey.springframework.data</groupId>
    <artifactId>spring-boot-starter-data-valkey</artifactId>
    <version>${version}</version>
</dependency>
```

Or to your `build.gradle`:

```gradle
dependencies {
    implementation 'io.valkey.springframework.data:spring-boot-starter-data-valkey:${version}'
}
```

This starter includes `spring-data-valkey` and the Valkey GLIDE driver by default. To use the Lettuce or Jedis driver instead, add their dependencies and set `spring.data.valkey.client-type` accordingly.

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
    private ValkeyTemplate<String, Object> valkeyTemplate;
    
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
    private String name;
    private String email;
    // getters and setters
}

public interface UserRepository extends CrudRepository<User, String> {
    List<User> findByName(String name);
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
# Valkey GLIDE configuration
spring.data.valkey.valkeyglide.shutdown-timeout=100ms
spring.data.valkey.valkeyglide.pool-size=8

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
spring.data.valkey.cluster.nodes=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002
spring.data.valkey.cluster.max-redirects=3
```

### Sentinel Configuration

```properties
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

## Building from Source

### Prerequisites

- JDK 17 or higher
- Maven 3.9 or higher (or use included wrapper)

### Build Commands

```bash
# Build the project (compile project, runs tests, and creates JAR)
./mvnw clean package

# Run tests only
./mvnw test

# Build without tests
./mvnw clean package -DskipTests
```

## License

The Spring Boot Valkey Starter is Open Source software released under the Apache 2.0 license.
