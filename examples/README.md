# Spring Data Valkey Examples

This directory contains standalone examples demonstrating various features of Spring Data Valkey using Valkey GLIDE as the driver.

## Prerequisites

- JDK 17 or higher
- Maven 3.9.9 or higher (use `../mvnw` from root directory)
- Valkey server running on `localhost:6379` (or configure connection in examples)

If using a development build of Spring Data Valkey, first install to your local Maven repository before running the examples:
```bash
# From project root
$ ./mvnw clean install -DskipTests
```

See instructions on starting a Valkey server using the `Makefile` in the root [README](../README.md#building-from-source).  The standalone and cluster instances started by the Makefile are used in these examples.

## Running Examples

Each example can be run independently using Maven, from the examples root directory:

```bash
$ cd examples
$ ../mvnw -q compile exec:java -pl <example-name>
```

For example:
```bash
$ cd examples/<example-name>
$ ../../mvnw -q compile exec:java
```

To run all examples sequentially:

```bash
$ cd examples
$ for module in $(ls -d */ | grep -v target | grep -v spring-boot | sed 's|/||'); do
  echo ""
  echo "====================================="
  echo "Running: $module"
  echo "====================================="
  ../mvnw -q compile exec:java -pl $module
done
```

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

Note that the `spring-boot` example is standalone and has its own [README](spring-boot/) with separate run instructions.

## Notes

- All examples use Valkey GLIDE as the connection driver (Lettuce and Jedis are also supported)
- All examples reference a parent examples POM which specifies any common depedencies (spring-data-valkey, valkey-glide, etc)
- Many examples create resources directly in `main()` for simplicity; see `quickstart` and `operations` for inline `@Configuration` examples, or `cache` and `repositories` for separate `@Configuration` class examples
- Each example cleans up any data it creates in the datastore
