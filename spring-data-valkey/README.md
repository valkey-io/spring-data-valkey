# Spring Data Valkey

Spring Data Valkey provides Spring Data integration for [Valkey](https://valkey.io/), a high-performance, Redis-compatible in-memory database. This module is a fork of Spring Data Redis 3.5.1, created to offer first-class support for Valkey and optimized access to the Valkey ecosystem through Spring's familiar data access patterns.

## Features

* Connection package as low-level abstraction across multiple drivers ([Valkey GLIDE](https://github.com/valkey-io/valkey-glide), [Lettuce](https://github.com/lettuce-io/lettuce-core), and [Jedis](https://github.com/redis/jedis))
* Exception translation to Spring's portable Data Access exception hierarchy
* `ValkeyTemplate` for high-level abstraction with serialization support
* Pubsub support with MessageListenerContainer (Jedis and Lettuce, GLIDE support planned)
* Valkey Sentinel support (Jedis and Lettuce, GLIDE support planned)
* Reactive API using Lettuce
* JDK, String, JSON and Spring Object/XML mapping serializers
* JDK Collection implementations on top of Valkey
* Atomic counter support classes
* Sorting and Pipelining functionality
* Valkey implementation for Spring cache abstraction
* Automatic implementation of `Repository` interfaces with `@EnableValkeyRepositories`
* CDI support for repositories

## Installation

Add the Spring Data Valkey and Valkey GLIDE dependencies:

```xml
<dependency>
    <groupId>io.valkey.springframework.data</groupId>
    <artifactId>spring-data-valkey</artifactId>
    <version>${version}</version>
</dependency>
<dependency>
    <groupId>io.valkey</groupId>
    <artifactId>valkey-glide</artifactId>
    <classifier>${os.detected.classifier}</classifier>
    <version>${version}</version>
</dependency>
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

## Getting Started

### Basic Configuration

```java
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

@Service
public class Example {

    @Autowired
    private StringValkeyTemplate valkeyTemplate;

    public void addLink(String userId, URL url) {
        valkeyTemplate.boundListOps(userId).leftPush(url.toExternalForm());
    }
}
```

## Spring Boot Integration

For Spring Boot applications, use the [Spring Boot Starter](../spring-boot-starter-data-valkey/) which provides auto-configuration and simplified setup.

## Building from Source

See instructions on starting a Valkey server in the [Developer Guide](../DEVELOPER.md). The standalone and cluster instances started by the Makefile are used in the unit tests.

Then build Spring Data Valkey:

```bash
$ ../mvnw clean install
```
