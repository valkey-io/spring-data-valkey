# Spring Data Valkey

Spring Data Valkey is a dedicated integration module for the [Valkey](https://valkey.io/) data store, a high-performance, Redis-compatible in-memory database. The project is a fork of Spring Data Redis 3.5.1, created to offer first-class support for Valkey and to ensure seamless, optimized access to the Valkey ecosystem.

This project provides both the [core Spring Data Valkey library](spring-data-valkey/) and a [Spring Boot starter](spring-boot-starter-data-valkey/) for auto-configuration. It is purpose-built to provide the best possible experience when using Valkey from Spring applications, leveraging the specialized [Valkey-GLIDE](https://github.com/valkey-io/valkey-glide) client library for high-performance, cross-language connectivity. By aligning API compatibility with Spring Data Redis, Spring Data Valkey enables developers to migrate with minimal friction while benefiting from improved performance, modern driver capabilities, and long-term support for the Valkey platform.

## Features

### Spring Data Valkey

* Connection package as low-level abstraction across multiple drivers ([Valkey GLIDE](https://github.com/valkey-io/valkey-glide), [Lettuce](https://github.com/lettuce-io/lettuce-core), and [Jedis](https://github.com/redis/jedis)).
* Exception translation to Spring's portable Data Access exception hierarchy for driver exceptions.
* `ValkeyTemplate` that provides a high level abstraction for performing various Valkey operations, exception translation and serialization support.
* Pubsub support (such as a MessageListenerContainer for message-driven POJOs).
* OpenTelemetry instrumentation support when using the Valkey GLIDE client for emitting traces and metrics for Valkey operations.
* Valkey Sentinel support is currently available in Jedis and Lettuce, while support in Valkey GLIDE is planned for a future release.
* Reactive API using Lettuce.
* JDK, String, JSON and Spring Object/XML mapping serializers.
* JDK Collection implementations on top of Valkey.
* Atomic counter support classes.
* Sorting and Pipelining functionality.
* Dedicated support for SORT, SORT/GET pattern and returned bulk values.
* Valkey implementation for Spring cache abstraction.
* Automatic implementation of `Repository` interfaces including support for custom finder methods using `@EnableValkeyRepositories`.
* CDI support for repositories.

### Spring Boot Starter

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

## Installation

### Spring Boot

For Spring Boot applications, use the [Spring Boot Starter](spring-boot-starter-data-valkey/):

```xml
<dependency>
    <groupId>io.valkey.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-valkey</artifactId>
    <version>${version}</version>
</dependency>
```

### Vanilla Spring

For non-Spring Boot applications, use the [core library](spring-data-valkey/):

```xml
<dependency>
    <groupId>io.valkey.springframework.data</groupId>
    <artifactId>spring-data-valkey</artifactId>
    <version>${version}</version>
</dependency>
```

See the respective module READMEs for complete installation instructions including driver dependencies.

## Getting Started

### Spring Boot

For Spring Boot applications, use the [Spring Boot Starter](spring-boot-starter-data-valkey/):

```java
@Service
public class Example {

    @Autowired
    private StringValkeyTemplate valkeyTemplate;

    public void addLink(String userId, URL url) {
        valkeyTemplate.opsForList().leftPush(userId, url.toExternalForm());
    }
}
```

### Vanilla Spring

For non-Spring Boot applications, manually configure Spring Data Valkey using the [core library](spring-data-valkey/):

```java
@Service
public class Example {

    @Autowired
    private StringValkeyTemplate valkeyTemplate;

    public void addLink(String userId, URL url) {
        valkeyTemplate.boundListOps(userId).leftPush(url.toExternalForm());
    }
}

@Configuration
class ApplicationConfig {

    @Bean
    public ValkeyConnectionFactory valkeyConnectionFactory() {
        return new ValkeyGlideConnectionFactory();
    }

    @Bean
    public StringValkeyTemplate valkeyTemplate(ValkeyConnectionFactory factory) {
        return new StringValkeyTemplate(factory);
    }
}
```

### Examples

For more comprehensive examples covering templates, repositories, caching, and other Spring Data functionality, see the [examples](examples/) directory.

## Building from Source

See the [Developer Guide](DEVELOPER.md) for comprehensive build instructions and development setup.

## Migration from Spring Data Redis

If you're migrating from Spring Data Redis, see the [Migration Guide](MIGRATION.md) for detailed instructions on updating package names, class names, and configuration.

## Documentation

For general usage patterns and API documentation, refer to:
* [Spring Data Redis Reference Documentation](https://docs.spring.io/spring-data/redis/reference/) - Most concepts apply to Valkey
* [Valkey Documentation](https://valkey.io/docs/) - For Valkey-specific features and commands

## License

Spring Data Valkey is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).
