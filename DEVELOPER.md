# Developer Guide

This guide provides comprehensive information for developers working on Spring Data Valkey, including building, testing, and contributing to the project.

## Project Structure

Spring Data Valkey is organized as a multi-module Maven project:

* **`spring-data-valkey/`** - Core Spring Data Valkey library
* **`spring-boot-starter-data-valkey/`** - Spring Boot starter for auto-configuration
* **`examples/`** - Example applications demonstrating various Spring Data features
* **`performance/`** - Performance testing and benchmarking tools

## Prerequisites

* **JDK 17 or above** - Required for compilation and runtime
* **make** - For managing Valkey test infrastructure
* **Maven v3.8.0 or above** - If using `mvn` command (or use included `./mvnw`)
* **Docker** (optional) - For containerized Valkey instances

## Getting Started

Clone and build all modules:

```bash
$ git clone https://github.com/valkey-io/spring-data-valkey.git
$ cd spring-data-valkey
$ ./mvnw clean install
```

## Start Valkey Server

Spring Data Valkey can be easily built with the [maven wrapper](https://github.com/takari/maven-wrapper). You also need JDK 17 or above and `make`. The local build environment is managed within a `Makefile` to download, build and spin up Valkey in various configurations (Standalone, Sentinel, Cluster, etc.)

```bash
$ make test
```

The preceding command runs a full build and test. You can use `make start`, `make stop`, and `make clean` commands to control the environment yourself. This is useful if you want to avoid constant server restarts. Once all Valkey instances have been started, you can either run tests in your IDE or the full Maven build:

```bash
$ ./mvnw clean install
```

If you want to build with the regular `mvn` command, you will need [Maven v3.8.0 or above](https://maven.apache.org/run-maven/index.html).

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

# Test with different clients
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
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Spring Data Valkey logging -->
    <logger name="io.valkey.springframework.data" level="DEBUG"/>
    
    <!-- Client logging (depending on what client is being used) -->
    <logger name="glide" level="DEBUG"/>
    <logger name="io.lettuce.core" level="DEBUG"/>
    <logger name="redis.clients.jedis" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## Release Process

### Version Management

Versions are managed in the parent POM:

```xml
<version>${version}</version>
```

All modules inherit this version automatically.

When preparing a new release, update the following files (example shows `0.1.0` to `1.0.0`):

```bash
# 1. Update root project version
$ sed -i 's/<version>0\.1\.0<\/version>/<version>1.0.0<\/version>/' pom.xml

# 2. Update all parent version references in child modules
$ find . -name "pom.xml" -not -path "./pom.xml" -exec sed -i 's/<version>0\.1\.0<\/version>/<version>1.0.0<\/version>/' {} \;

# 3. Update notice file
$ sed -i 's/Spring Data Valkey 0\.1\.0/Spring Data Valkey 1.0.0/' spring-data-valkey/src/main/resources/notice.txt
```

Note: Child modules must explicitly specify parent versions - Maven requires this for proper dependency resolution.

### Generating a Release

In order to generate a new release, create and push a tag to the main branch. This will build and test the project and add the artifacts to a draft release. Verify the release and then publish it.

For example:

```bash
$ git tag v1.0
$ git push origin v1.0
```

## Additional Resources

* [Valkey Documentation](https://valkey.io/docs/)
* [Spring Data Documentation](https://docs.spring.io/spring-data/redis/reference/)
* [Valkey GLIDE Client](https://github.com/valkey-io/valkey-glide)
* [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/)
