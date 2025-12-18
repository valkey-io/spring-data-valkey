# Spring Data Valkey

Spring Data Valkey is a dedicated integration module for the [Valkey](https://valkey.io/)
 data store, a high-performance, Redis-compatible in-memory database.
The project is a fork of Spring Data Redis 3.5.1, created to offer first-class support for Valkey and to ensure seamless, optimized access to the Valkey ecosystem.

This module is purpose-built to provide the best possible experience when using Valkey from Spring applications, leveraging the specialized [Valkey-GLIDE](https://github.com/valkey-io/valkey-glide)
 client library for high-performance, cross-language connectivity. By aligning API compatibility with Spring Data Redis, Spring Data Valkey enables developers to migrate with minimal friction while benefiting from improved performance, modern driver capabilities, and long-term support for the Valkey platform.

## Features

* Connection package as low-level abstraction across multiple drivers ([Valkey GLIDE](https://github.com/valkey-io/valkey-glide), [Lettuce](https://github.com/lettuce-io/lettuce-core), and [Jedis](https://github.com/redis/jedis)).
* Exception translation to Spring's portable Data Access exception hierarchy for driver exceptions.
* `ValkeyTemplate` that provides a high level abstraction for performing various Valkey operations, exception translation and serialization support.
* Pubsub support (such as a MessageListenerContainer for message-driven POJOs). Available with Jedis and Lettuce, with Valkey GLIDE support WIP for version 1.0.0.
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

For non-Spring Boot applications, manually configure Spring Data Valkey:

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

### Maven Configuration

#### Spring Boot

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
        <version>${version}</version>
        <classifier>${os.detected.classifier}</classifier>
    </dependency>
</dependencies>
```

#### Vanilla Spring

Add the Maven dependency:

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

#### Platform Dependencies

Because GLIDE has platform-specific native libraries, add the os-maven-plugin to resolve `${os.detected.classifier}`:

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

This applies to both Spring Boot and Vanilla Spring configurations above.

### Examples

For more comprehensive examples covering templates, repositories, caching, and other Spring Data functionality, see the [examples](examples/) directory.

## Building from Source

Spring Data Valkey can be easily built with the [maven wrapper](https://github.com/takari/maven-wrapper). You also need JDK 17 or above and `make`. The local build environment is managed within a `Makefile` to download, build and spin up Valkey in various configurations (Standalone, Sentinel, Cluster, etc.)

```bash
$ make test
```

The preceding command runs a full build. You can use `make start`, `make stop`, and `make clean` commands to control the environment yourself. This is useful if you want to avoid constant server restarts. Once all Valkey instances have been started, you can either run tests in your IDE or the full Maven build:

```bash
$ ./mvnw clean install
```

If you want to build with the regular `mvn` command, you will need [Maven v3.8.0 or above](https://maven.apache.org/run-maven/index.html).

<!-- ## Generating a Release - This should go to a developer readme

In order to generate a new release, create and push a tag to the main branch.  This will build and test the project and add the artifacts to a draft release.  Verify the release and then publish it.

For example:

```bash
$ git tag v1.0
$ git push origin v1.0
``` -->

## Migration from Spring Data Redis

If you're migrating from Spring Data Redis, see the [Migration Guide](MIGRATION.md) for detailed instructions on updating package names, class names, and configuration.

## Documentation

For general usage patterns and API documentation, refer to:
* [Spring Data Redis Reference Documentation](https://docs.spring.io/spring-data/redis/reference/) - Most concepts apply to Valkey
* [Valkey Documentation](https://valkey.io/docs/) - For Valkey-specific features and commands

## License

Spring Data Valkey is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).
