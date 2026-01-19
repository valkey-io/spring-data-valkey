# Spring Data Valkey Performance Tests

Performance benchmarks for Spring Data Valkey operations across different clients.

## Prerequisites

If using a development build of Spring Data Valkey, first install to your local Maven repository before running the tests:
```bash
# From project root
$ ./mvnw clean install -DskipTests
```

See instructions on starting a Valkey server in the [Developer Guide](../DEVELOPER.md). The standalone instance started by the Makefile is used in these tests.

## Running Tests

All commands below assume you're in the `performance/` directory. To run from the project root, use `./mvnw -q compile exec:java[@<test-name>] -pl performance [<parameters>]` instead of `../mvnw`.  Replace `<test-name>` and `<parameters>` from options listed below.

### Template Performance Test

Test ValkeyTemplate operations (`SET`, `GET`, `DELETE`) with different clients:

```bash
$ ../mvnw -q compile exec:java -Dclient=valkeyglide
$ ../mvnw -q compile exec:java -Dclient=lettuce
$ ../mvnw -q compile exec:java -Dclient=jedis
```

### Multi-Threaded Performance Test

Test template use across multiple threads with different clients:

```bash
$ ../mvnw -q compile exec:java@threaded-test -Dclient=valkeyglide
$ ../mvnw -q compile exec:java@threaded-test -Dclient=lettuce
$ ../mvnw -q compile exec:java@threaded-test -Dclient=jedis
```

### Direct Client Performance Test

Test direct client operations without Spring Data Valkey (for comparison):

```bash
$ ../mvnw -q compile exec:java@direct-test -Dclient=valkeyglide
$ ../mvnw -q compile exec:java@direct-test -Dclient=lettuce
$ ../mvnw -q compile exec:java@direct-test -Dclient=jedis
```

### Multi-Threaded Direct Client Performance Test

Test direct client operations across multiple threads:

```bash
$ ../mvnw -q compile exec:java@threaded-direct-test -Dclient=valkeyglide
$ ../mvnw -q compile exec:java@threaded-direct-test -Dclient=lettuce
$ ../mvnw -q compile exec:java@threaded-direct-test -Dclient=jedis
```

### Template Load Test

Test ValkeyTemplate operations (`SET`, `GET`, `DELETE`) with different clients and concurrency levels.

Parameters:
- `client`: Client type - `valkeyglide`, `lettuce`, `jedis` (default: `valkeyglide`)
- `threads`: Number of threads (default: `10`)
- `operations`: Operations per thread (default: `50`)

```bash
# Compare a single client with different concurrency levels
$ ../mvnw -q compile exec:java@load-test -Dclient=valkeyglide -Dthreads=5 -Doperations=20
$ ../mvnw -q compile exec:java@load-test -Dclient=valkeyglide -Dthreads=20 -Doperations=100

# Compare across different clients
$ ../mvnw -q compile exec:java@load-test -Dclient=valkeyglide -Dthreads=100 -Doperations=200
$ ../mvnw -q compile exec:java@load-test -Dclient=lettuce -Dthreads=100 -Doperations=200
$ ../mvnw -q compile exec:java@load-test -Dclient=jedis -Dthreads=100 -Doperations=200
```
