# Spring Data Valkey

The primary goal of the [Spring Data](https://spring.io/projects/spring-data/) project is to make it easier to build Spring-powered applications that use new data access technologies such as non-relational databases, map-reduce frameworks, and cloud based data services.

This module provides integration with the [Valkey](https://valkey.io/) store, a high-performance data structure server that is fully compatible with Redis.

## Features

* Connection package as low-level abstraction across multiple drivers ([Valkey GLIDE](https://github.com/valkey-io/valkey-glide), [Lettuce](https://github.com/lettuce-io/lettuce-core), and [Jedis](https://github.com/redis/jedis)).
* Exception translation to Spring's portable Data Access exception hierarchy for driver exceptions.
* `ValkeyTemplate` that provides a high level abstraction for performing various Valkey operations, exception translation and serialization support.
* Pubsub support (such as a MessageListenerContainer for message-driven POJOs).
* Valkey Sentinel and Valkey Cluster support.
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

Here is a quick teaser of an application using Spring Data Valkey in Java:

```java
public class Example {

    // inject the actual template
    @Autowired
    private StringValkeyTemplate valkeyTemplate;

    // inject the template as ListOperations
    // can also inject as Value, Set, ZSet, and HashOperations
    @Resource(name="valkeyTemplate")
    private ListOperations<String, String> listOps;

    public void addLink(String userId, URL url) {
        listOps.leftPush(userId, url.toExternalForm());
        // or use template directly
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

### Maven configuration

Add the Maven dependency:

```xml
<dependency>
  <groupId>io.valkey.springframework.data</groupId>
  <artifactId>spring-data-valkey</artifactId>
  <version>${version}</version>
</dependency>
```

Note that a dependency for the underlying driver is also needed. It is recommended to use Valkey GLIDE:

```xml
<dependency>
  <groupId>io.valkey</groupId>
  <artifactId>valkey-glide</artifactId>
  <classifier>${os.detected.classifier}</classifier>
  <version>${version}</version>
</dependency>
```

Valkey GLIDE requires platform-specific native libraries. Add the os-maven-plugin to resolve `${os.detected.classifier}`:

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

## Generating a Release

In order to generate a new release, create and push a tag to the main branch.  This will build and test the project and add the artifacts to a draft release.  Verify the release and then publish it.

For example:

```bash
$ git tag v1.0
$ git push origin v1.0
```

## Migration from Spring Data Redis

If you're migrating from Spring Data Redis, see the [Migration Guide](MIGRATION.md) for detailed instructions on updating package names, class names, and configuration.

## Documentation

For general usage patterns and API documentation, refer to:
* [Spring Data Redis Reference Documentation](https://docs.spring.io/spring-data/redis/reference/) - Most concepts apply to Valkey
* [Valkey Documentation](https://valkey.io/docs/) - For Valkey-specific features and commands

## License

Spring Data Valkey is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).
