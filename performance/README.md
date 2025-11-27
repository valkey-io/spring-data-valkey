# Spring Data Valkey Performance Tests

Performance benchmarks for Spring Data Valkey operations across different clients.

## Prerequisites

- JDK 17 or higher
- Maven 3.9.9 or higher (use `../mvnw` from root directory)
- Valkey server running on `localhost:6379` (or configure connection in tests)

If using a development build of Spring Data Valkey, first install to your local Maven repository before running the tests:
```bash
# From project root
$ ./mvnw clean install -DskipTests
```

See instructions on starting a Valkey server using the `Makefile` in the root [README](../README.md#building-from-source).  The standalone instance started by the Makefile is used in these tests.

## Running Tests

### Template Performance Test

Test ValkeyTemplate operations (`SET`, `GET`, `DELETE`) with different clients:

```bash
$ mvn -q compile exec:java -Dclient=valkeyglide
$ mvn -q compile exec:java -Dclient=lettuce
$ mvn -q compile exec:java -Dclient=jedis
```

### Multi-Threaded Performance Test

Test template use across mulitple threads with different clients:

```bash
$ mvn -q compile exec:java@threaded-test -Dclient=valkeyglide
$ mvn -q compile exec:java@threaded-test -Dclient=lettuce
$ mvn -q compile exec:java@threaded-test -Dclient=jedis
```

### Direct Client Performance Test

Test direct client operations without Spring Data Valkey (for comparison):

```bash
$ mvn -q compile exec:java@direct-test -Dclient=valkeyglide
$ mvn -q compile exec:java@direct-test -Dclient=lettuce
$ mvn -q compile exec:java@direct-test -Dclient=jedis
```

### Multi-Threaded Direct Client Performance Test

Test direct client operations across multiple threads:

```bash
$ mvn -q compile exec:java@threaded-direct-test -Dclient=valkeyglide
$ mvn -q compile exec:java@threaded-direct-test -Dclient=lettuce
$ mvn -q compile exec:java@threaded-direct-test -Dclient=jedis
```

### Template Load Test

Test ValkeyTemplate operations (`SET`, `GET`, `DELETE`) with different clients and concurrency levels.

Parameters:
- `client`: Client type - `valkeyglide`, `lettuce`, `jedis` (default: `valkeyglide`)
- `threads`: Number of threads (default: `10`)
- `operations`: Operations per thread (default: `50`)

```bash
# Compare a single client with different concurrency levels
$ mvn -q compile exec:java@load-test -Dclient=valkeyglide -Dthreads=5 -Doperations=20
$ mvn -q compile exec:java@load-test -Dclient=valkeyglide -Dthreads=20 -Doperations=100

# Comapre across different clients
$ mvn -q compile exec:java@load-test -Dclient=valkeyglide -Dthreads=100 -Doperations=200
$ mvn -q compile exec:java@load-test -Dclient=lettuce -Dthreads=100 -Doperations=200
$ mvn -q compile exec:java@load-test -Dclient=jedis -Dthreads=100 -Doperations=200
```
