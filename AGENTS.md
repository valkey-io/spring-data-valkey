# AGENTS: Unified Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively in the Spring Data Valkey repository. It covers build commands, contribution requirements, and essential guardrails for maintaining code quality.

## Repository Overview

Spring Data Valkey is a Spring Data module providing first-class Valkey/Redis integration for Spring applications. It is forked from Spring Data Redis with Valkey-specific enhancements including native Valkey GLIDE driver support.

**Primary Language:** Java 17+
**Build System:** Maven (use `./mvnw` wrapper)
**Framework:** Spring Data 3.5.x, Spring Boot 3.5.x
**Origin:** Forked from Spring Data Redis 3.5.1 — class and package renames follow the pattern `*Redis*` → `*Valkey*`, `*redis*` → `*valkey*` (see [MIGRATION.md](./MIGRATION.md) for full mapping, [DEVELOPER.md](./DEVELOPER.md) for source alignment and detailed build/release info)

**Key Components:**

- `spring-data-valkey/` - Core Spring Data library (connections, operations, repositories, serialization, caching, pub/sub, streams)
- `spring-boot-starter-data-valkey/` - Spring Boot auto-configuration and starter
- `examples/` - Example applications demonstrating various features
- `performance/` - Performance benchmarks
- `docs/` - Astro Starlight documentation site (Node.js)

**Supported Drivers:**

| Driver | Artifact |
|--------|----------|
| Valkey GLIDE (primary) | `io.valkey:valkey-glide` |
| Lettuce | `io.lettuce:lettuce-core` |
| Jedis | `redis.clients:jedis` |

**Driver Notes:**

- Valkey GLIDE requires `${os.detected.classifier}` (platform-specific JAR) and the `os-maven-plugin` build extension
- Sentinel support is available in Lettuce and Jedis only — GLIDE does not support Sentinel at this time

## Architecture Quick Facts

**Design Pattern:** Spring Data's repository and template abstraction over Valkey connections
**Connection Abstraction:** `ValkeyConnectionFactory` interface with driver-specific implementations (`ValkeyGlideConnectionFactory`, `LettuceConnectionFactory`, `JedisConnectionFactory`)
**Key Abstractions:** `ValkeyTemplate` (operations), `ValkeyRepository` (Spring Data repositories), `ValkeyCache` (Spring Cache), `MessageListenerContainer` (pub/sub)
**Auto-Configuration:** Spring Boot starter auto-configures connections, templates, repositories, and caching via `spring.data.valkey.*` properties
**Serialization:** Pluggable serializers (String, JSON, JDK, XML) — default is JDK serialization for values, String for keys

## Build and Test Rules (Agents)

### Preferred (Make + Maven)

```bash
# Full build with tests (manages Valkey infrastructure automatically)
make test

# Full build including long-running tests
make all-tests

# Run all examples
make examples

# Run performance benchmarks
make performance

# Test infrastructure only
make start       # Start Valkey standalone, sentinel, and cluster instances
make stop        # Stop all instances
make clean       # Clean up configs, pids, and logs
make clobber     # Remove entire work/ directory
```

### Raw Maven Commands

```bash
# Build all modules (skip tests)
./mvnw clean install -DskipTests

# Build all modules with tests (requires running Valkey — use `make start` first)
./mvnw clean install

# Build specific module
./mvnw clean install -pl spring-data-valkey
./mvnw clean install -pl spring-boot-starter-data-valkey

# Run tests for specific module (requires running Valkey)
./mvnw test -pl spring-data-valkey
./mvnw test -pl spring-boot-starter-data-valkey

# Run a single test class
./mvnw test -pl spring-data-valkey -Dtest=ClassName

# Run a single test method
./mvnw test -pl spring-data-valkey -Dtest=ClassName#methodName
```

**Test Results:** Stored in `target/surefire-reports/` within each module directory.

### Running Examples

```bash
# Run all examples (manages infrastructure)
make examples

# Run a single example (requires running Valkey — use `make start` first)
./mvnw -q compile exec:java -pl examples/quickstart
./mvnw -q compile exec:java -pl examples/spring-boot
```

### Running Performance Tests

```bash
# Default performance test with GLIDE (manages infrastructure)
make performance

# Run specific test profiles (requires running Valkey — use `make start` first)
./mvnw -q compile exec:java -pl performance -Dclient=valkeyglide
./mvnw -q compile exec:java -pl performance -Dclient=lettuce
./mvnw -q compile exec:java -pl performance -Dclient=jedis

# Multi-threaded and direct client tests
./mvnw -q compile exec:java@threaded-test -pl performance -Dclient=valkeyglide
./mvnw -q compile exec:java@direct-test -pl performance -Dclient=valkeyglide

# Load test with custom parameters
./mvnw -q compile exec:java@load-test -pl performance -Dclient=valkeyglide -Dthreads=100 -Doperations=200
```

### Documentation (Node.js/Astro)

```bash
cd docs
npm install
npm run dev       # Local dev server at http://localhost:4321/
npm run build     # Production build
```

## Test Infrastructure

Tests require running Valkey instances. The `Makefile` manages this automatically:

- **Standalone:** ports 6379 (master), 6380, 6381 (replicas), 6382 (auth-enabled, password: `foobared`)
- **Sentinel:** ports 26379, 26380, 26381, 26382 (auth-enabled)
- **Cluster:** ports 7379, 7380, 7381 (shards), 7382 (replica)

Always use `make start` / `make stop` rather than managing instances manually. The `make test` target handles the full lifecycle.

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
git commit -s -m "feat: add new feature"

# Add signoff to existing commit
git commit --amend --signoff --no-edit

# Add signoff to multiple commits
git rebase -i HEAD~n --signoff
```

**Required format:** `Signed-off-by: Your Name <your.email@example.com>`

### Code Style & Spring Conventions

- Follow existing Spring Data conventions and formatting in the codebase
- Use constructor injection over field injection for Spring beans
- Use `@Configuration` classes for bean definitions, not XML
- Amend the Apache license header date range if needed
- For new types, copy the license header from an existing file with the current year
- Submit test cases (unit or integration) that back your changes
- Use `@DataValkeyTest` slice test annotation for focused Valkey component tests
- New Spring Boot auto-configuration must be registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Package Naming Convention

- Spring Data: `io.valkey.springframework.data.valkey.*`
- Spring Boot: `io.valkey.springframework.boot.*.valkey.*`

## Guardrails & Policies

### Generated Outputs (Never Commit)

- `target/` - Maven build artifacts
- `work/` - Valkey test infrastructure (configs, pids, logs, binaries)
- `node_modules/` - Node.js dependencies (docs)
- `build/` - Build output
- `.flattened-pom.xml` - Generated by flatten plugin
- `*.rdb`, `*.aof`, `appendonlydir/` - Valkey data files
- `dump.rdb` - Valkey dump file
- `.gradle/`, `*.iml`, `*.ipr`, `*.iws` - IDE files

### Security & Code Quality

- Never commit secrets, credentials, or API keys
- Run full build before committing: `./mvnw clean install`
- Maintain compatibility with all three drivers (GLIDE, Lettuce, Jedis)
- Do not modify test infrastructure configs in `work/` — they are generated by `make`
- Spring Boot auto-configuration properties use the `spring.data.valkey.*` prefix — see [spring-boot-starter-data-valkey/README.md](./spring-boot-starter-data-valkey/README.md) for the full property reference
- Do not modify vendored or third-party code

## Project Structure (Essential)

```text
spring-data-valkey/
├── spring-data-valkey/               # Core library (connections, ops, repos, cache, pub/sub)
├── spring-boot-starter-data-valkey/  # Spring Boot auto-configuration and starter
├── examples/                         # Example applications (see examples/README.md)
├── performance/                      # Performance benchmarks
├── docs/                             # Astro Starlight documentation (Node.js)
├── .github/workflows/                # CI/CD pipelines
├── Makefile                          # Test infrastructure management
├── pom.xml                           # Parent POM
└── mvnw                              # Maven wrapper
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `./mvnw clean install -DskipTests` succeeds
- [ ] Tests pass: `make test` succeeds
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] License headers present on new/modified files
- [ ] Driver compatibility maintained (changes work with GLIDE, Lettuce, and Jedis)
- [ ] Package naming conventions followed
- [ ] Spring conventions followed (constructor injection, `@Configuration` classes)

## Quick Facts for Reasoners

**Drivers Supported:** Valkey GLIDE (primary), Lettuce, Jedis
**Key Abstractions:** `ValkeyConnectionFactory`, `ValkeyTemplate`, `ValkeyRepository`, `ValkeyCache`
**Auto-Config Properties:** `spring.data.valkey.*` (host, port, cluster, sentinel, SSL, pooling, client-type)
**Test Annotation:** `@DataValkeyTest` for slice tests
**Forked From:** Spring Data Redis 3.5.1 — rename pattern `*Redis*` → `*Valkey*`

## If You Need More

- **Developer Guide:** [DEVELOPER.md](./DEVELOPER.md) — detailed build instructions, logging config, release process, Redis source alignment
- **Migration Guide:** [MIGRATION.md](./MIGRATION.md) — migrating from Spring Data Redis
- **Examples:** [examples/README.md](./examples/README.md)
- **Spring Boot Starter:** [spring-boot-starter-data-valkey/README.md](./spring-boot-starter-data-valkey/README.md)
- **Core Library:** [spring-data-valkey/README.md](./spring-data-valkey/README.md)
- **Documentation Site:** [docs/](./docs/) — Astro Starlight docs, deploy via GitHub Actions
