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

## Redis Source Alignment

This repository is forked from Spring Data Redis and related projects. This section documents the source repositories to help with future upgrades, patches, and alignment.

| Local Module | Redis Source Repository | Branch/Tag | Notes |
|--------------|------------------------|------------|-------|
| `./` | [spring-data-build](https://github.com/spring-projects/spring-data-build) | `3.5.1` | Parent for Spring Data modules |
| `spring-data-valkey/` | [spring-data-redis](https://github.com/spring-projects/spring-data-redis) | `3.5.1` | Core Spring Data Redis functionality |
| `spring-boot-starter-data-valkey/` | [spring-boot](https://github.com/spring-projects/spring-boot) | `3.5.1` | Spring Boot auto-configuration for Redis |
| `docs/` | [spring-data-redis](https://github.com/spring-projects/spring-data-redis) | `3.5.1` | Documentation |
| `examples/` | [spring-data-examples](https://github.com/spring-projects/spring-data-examples) | `main` | Redis examples |

Key changes from Redis source:
- **Package names**:
  - Spring Data: `org.springframework.data.redis.*` → `io.valkey.springframework.data.valkey.*`
  - Spring Boot: `org.springframework.boot.*.redis.*` → `io.valkey.springframework.boot.*.valkey.*`
- **Class names and properties**: `*Redis*` → `*Valkey*` or `*redis*` → `*valkey*`
- **Dependencies**:
  - Spring Data: `org.springframework.data:spring-data-redis` → `io.valkey.springframework.data:spring-data-valkey`
  - Spring Boot: `org.springframework.boot:spring-boot-starter-data-redis` → `io.valkey.springframework.boot:spring-boot-starter-data-valkey`
- **Driver support**: Added Valkey GLIDE as the primary driver
- **Documentation**: Migrated from Antora/AsciiDoc to Starlight/Markdown

## Deploying Documentation

The documentation is deployed to GitHub Pages using GitHub Actions.  This includes the Astro Starlight documentation under `docs/`, as well as the Spring Data Valkey JavaDocs and schema.

Manually trigger the *Docs* workflow in GitHub Actions for a given branch to deploy the documentation on that branch.  The documentation will then be available at `https://valkey-io.github.io/spring-data-valkey/` or `https://spring.valkey.io` (CNAME redirect).

### Local Development

```bash
$ cd docs
$ npm install
$ npm run dev
```

The documentation will be available at `http://localhost:4321/`.

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
* [Valkey GLIDE Java Client Reference](https://glide.valkey.io/languages/java/)
* [Spring Data Reference](https://spring.io/projects/spring-data)
* [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/)
