# Spring Data Valkey Examples

This directory contains standalone examples demonstrating various features of Spring Data Valkey using Valkey GLIDE as the driver.

## Prerequisites

If using a development build of Spring Data Valkey, first install to your local Maven repository before running the examples:

```bash
# From project root
$ ./mvnw clean install -DskipTests
```

See instructions on starting a Valkey server in the [Developer Guide](../DEVELOPER.md). The standalone and cluster instances started by the Makefile are used in these examples.

## Running Examples

Run any example from this directory:

```bash
$ ../mvnw -q compile exec:java -pl quickstart
```

Replace `quickstart` with any example name below. To run from project root, use `./mvnw -q compile exec:java -pl examples/<example-name>`. To run from a specific example directory, use `../../mvnw -q compile exec:java`.

## Available Examples

| Example | Description |
|---------|-------------|
| **quickstart** | Basic ValkeyTemplate usage for simple key-value operations |
| **operations** | Comprehensive examples of all Valkey data structures (List, Set, Hash, ZSet, Geo, Stream, HyperLogLog) |
| **cluster** | Valkey cluster configuration and operations with hash tags for proper key routing |
| **spring-boot** | Example of using Valkey Spring Boot starter to bootstrap use of Spring Data Valkey |
| **cache** | Spring Cache abstraction with Valkey backend (@Cacheable, TTL configuration) |
| **repositories** | Spring Data repository abstraction with @ValkeyHash entities and custom finder methods |
| **serialization** | Different serialization strategies (String, JSON, JDK) for storing objects |
| **transactions** | MULTI/EXEC transactions with WATCH for optimistic locking |
| **pipeline** | Pipelining multiple commands for improved performance |
| **streams** | Valkey Streams for event sourcing and message queues (XADD, XREAD, consumer groups) |
| **collections** | Valkey-backed Java collections (ValkeyList, ValkeySet, ValkeyMap) and atomic counters |
| **scripting** | Lua script execution (EVAL, EVALSHA) for atomic operations |

## Notes

- All examples use Valkey GLIDE as the connection driver (Lettuce and Jedis are also supported)
- All examples reference a parent examples POM which specifies any common dependencies (spring-data-valkey, valkey-glide, etc)
- Many examples create resources directly in `main()` for simplicity; see `quickstart` and `operations` for inline `@Configuration` examples, or `cache` and `repositories` for separate `@Configuration` class examples
- Each example cleans up any data it creates in the datastore
