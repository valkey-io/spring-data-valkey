# Developer Guide

This guide provides comprehensive information for developers working on Spring Data Valkey, including building, testing, and contributing to the project.

## Project Structure

Spring Data Valkey is organized as a multi-module Maven project:

* **[`spring-data-valkey`](spring-data-valkey/)** - Core Spring Data Valkey library
* **[`spring-boot-starter-data-valkey`](spring-boot-starter-data-valkey/)** - Spring Boot starter for auto-configuration
* **[`examples`](examples/)** - Example applications demonstrating various Spring Data features
* **[`performance`](performance/)** - Performance testing and benchmarking tools

## Prerequisites

* **JDK 17 or above** - Required for compilation and runtime
* **make** - For managing Valkey test infrastructure
* **Maven v3.8.0 or above** - If using `mvn` command (or use included `./mvnw`)
* **Docker** (optional) - For testcontainers/Docker tests

## Getting Started

Clone and build all modules:

```bash
$ git clone https://github.com/valkey-io/spring-data-valkey.git
$ cd spring-data-valkey
$ ./mvnw clean install
```

## Test Infrastructure

The project uses a `Makefile` to manage Valkey test infrastructure, automatically downloading and starting Valkey instances in various configurations (Standalone, Sentinel, Cluster).

### Full Test Run

Run full build with test infrastructure:

```bash
$ make test
```

### Manual Control

For development, you can control the test environment manually:

```bash
$ make start    # Start all Valkey instances
$ make stop     # Stop all instances  
$ make clean    # Clean up containers and data
```

Once instances are running, execute tests from your IDE or run the Maven build:

```bash
$ ./mvnw clean install
```

## Building the Project

```bash
# Clean build with tests
$ ./mvnw clean install

# Skip tests for faster build
$ ./mvnw clean install -DskipTests

# Build specific module
$ ./mvnw clean install -pl spring-data-valkey
```

## Running Unit Tests

```bash
# All tests with infrastructure management
$ make test

# Extended tests including long-running tests
$ make all-tests

# Unit tests only (requires running Valkey)
$ ./mvnw test

# Specific module tests (requires running Valkey)
$ ./mvnw test -pl spring-data-valkey
$ ./mvnw test -pl spring-boot-starter-data-valkey
```

## Running Examples

```bash
# Run all examples with infrastructure management
$ make examples

# Run individual examples against existing Valkey instance
$ ./mvnw -q exec:java -pl examples/quickstart
$ ./mvnw -q exec:java -pl examples/spring-boot
```

For detailed information about all available examples and their specific features, see the [examples](examples/) directory.

## Performance Testing

```bash
# Default performance test with infrastructure management
$ make performance

# Test with different clients against existing Valkey instance
$ ./mvnw -q exec:java -pl performance -Dclient=valkeyglide
$ ./mvnw -q exec:java -pl performance -Dclient=lettuce
```

For detailed information about all available performance tests and benchmarking options, see the [performance](performance/) directory.

## Logging Configuration

### Spring Boot

Add to your `application.properties`:

```properties
# Enable debug logging for Spring Data Valkey
logging.level.io.valkey.springframework.data=DEBUG

# Client logging (depending on what client is being used)
logging.level.glide=DEBUG
logging.level.io.lettuce.core=DEBUG
logging.level.redis.clients.jedis=DEBUG
```

### Vanilla Spring

For non-Spring Boot applications, configure logging via `logback.xml` in your classpath:

```xml
<configuration>

    <appender name="console" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d %5p %40.40c:%4L - %m%n</pattern>
        </encoder>
    </appender>

    <!-- Spring Data Valkey logging -->
    <logger name="io.valkey.springframework.data" level="DEBUG"/>
    
    <!-- Client logging (depending on what client is being used) -->
    <logger name="glide" level="DEBUG"/>
    <logger name="io.lettuce.core" level="DEBUG"/>
    <logger name="redis.clients.jedis" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="console"/>
    </root>

</configuration>
```

## Release Process

### Version Management

Versions are managed in the parent POM and inherited by all modules automatically.

When preparing a new release, use the Maven Versions Plugin to update all module versions.

For example:

```bash
$ mvn versions:set -DnewVersion=1.0.0
$ mvn versions:commit

# Revert instead of commit if necessary
$ mvn versions:revert
```

This automatically updates:
* **`pom.xml`** - Root project version (`<version>` element)
* **`**/pom.xml`** - All child modules' parent version references (`<parent><version>` element)

Manual update of the version is still required in these files:
* **`spring-data-valkey/src/main/resources/notice.txt`** - Version in notice text

### Generating a Release

In order to generate a new release, create and push a tag to the main branch. GitHub will build and test the project and add the artifacts to a draft release. Verify the release and then publish it in GitHub.

For example:

```bash
$ git tag v1.0.0
$ git push origin v1.0.0
```

## Additional Resources

* [Valkey Documentation](https://valkey.io/docs/)
* [Spring Data Documentation](https://docs.spring.io/spring-data/redis/reference/)
* [Valkey GLIDE Client](https://github.com/valkey-io/valkey-glide)
* [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/)
