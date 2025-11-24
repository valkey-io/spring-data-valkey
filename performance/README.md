# Spring Data Valkey Performance Tests

Performance benchmarks for Spring Data Valkey operations across different clients.

## Running Tests

### Template Performance Test

Test ValkeyTemplate operations (SET, GET, DELETE) with different clients:

```bash
mvn -q compile exec:java -Dclient=valkeyglide
mvn -q compile exec:java -Dclient=lettuce
mvn -q compile exec:java -Dclient=jedis
```

### Multi-Threaded Performance Test

Test template use across mulitple threads:

```bash
mvn -q compile exec:java@threaded-test -Dclient=valkeyglide
mvn -q compile exec:java@threaded-test -Dclient=lettuce
mvn -q compile exec:java@threaded-test -Dclient=jedis
```

### Direct Client Performance Test

Test direct client operations without Spring Data Valkey:

```bash
mvn -q compile exec:java@direct-test -Dclient=valkeyglide
mvn -q compile exec:java@direct-test -Dclient=lettuce
mvn -q compile exec:java@direct-test -Dclient=jedis
```

### Multi-Threaded Direct Client Performance Test

Test direct client operations across multiple threads:

```bash
mvn -q compile exec:java@threaded-direct-test -Dclient=valkeyglide
mvn -q compile exec:java@threaded-direct-test -Dclient=lettuce
mvn -q compile exec:java@threaded-direct-test -Dclient=jedis
```

### Template Load Test

Test ValkeyTemplate operations (SET, GET, DELETE) with different clients and concurrency levels.

Parameters:
- `client`: Client type - `valkeyglide`, `lettuce`, `jedis` (default: `valkeyglide`)
- `threads`: Number of threads (default: `10`)
- `operations`: Operations per thread (default: `50`)

```bash
mvn -q compile exec:java@load-test

mvn -q compile exec:java@load-test -Dthreads=5 -Doperations=20
mvn -q compile exec:java@load-test -Dthreads=100 -Doperations=200

mvn -q compile exec:java@load-test -Dclient=lettuce -Dthreads=30 -Doperations=100
mvn -q compile exec:java@load-test -Dclient=jedis -Dthreads=30 -Doperations=100
mvn -q compile exec:java@load-test -Dclient=valkeyglide -Dthreads=30 -Doperations=100
```
