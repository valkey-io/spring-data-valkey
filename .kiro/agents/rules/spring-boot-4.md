# Upstream Sync Rules — Spring Boot 4 (3.5.1 → 4.1.0)

## Target Versions

| Dependency | Version |
|-----------|---------|
| Spring Boot | 4.0.7 |
| Spring Data Parent | 4.1.0 |
| Lettuce | 7.5.2.RELEASE |
| Jedis | 7.4.1 |
| Jackson 2 | 2.21.4 |
| Jackson 3 | 3.1.4 |
| Netty | 4.2.10.Final |
| JSpecify | 1.0.0 |
| JUnit | 6.0.3 |
| Kotlin | 2.3.21 |
| Surefire | 3.5.3 |

## Current Status

**All phases complete. All tests pass, all examples run, docs build clean.**
- spring-data-valkey: 24,123 tests, 0 failures, 0 errors
- spring-boot-starter: 127 tests, 0 failures, 0 errors

## Upgrade Phases (for future upgrades)

| Phase | Description | Key Steps |
|-------|-------------|-----------|
| 1 | Bulk apply upstream changes | Generate per-package diffs, apply with rename rules, do NOT compile between packages |
| 2 | Compile and fix main code | Iteratively fix compilation errors, update rules with new exclusions |
| 3 | Boot starter sync | Port Boot module changes, preserve GLIDE files, handle structural changes |
| 4 | Tests compile and pass | Bulk sync test files, fix test infrastructure, run full suite |
| 5 | Examples and project files | `mvn install` then `make examples`, update READMEs/version refs |
| 6 | Documentation | Merge upstream doc changes into Starlight markdown docs |

### Phase execution order (strict)

1. **POM versions first** — update root pom.xml with new dependency versions
2. **Main code by package** (dependency order): `connection/` → `connection/lettuce/` → `connection/jedis/` → `core/` → `serializer/` → `cache/` → `listener/` → `config/` → `hash/` → `stream/` → `support/` → `repository/`
3. **Boot starter** — after main code compiles
4. **Tests** — bulk sync, then compile, then run
5. **Rebranding pass** — after tests pass, fix all Redis→Valkey regressions (files, classes, constants, javadoc)
6. **GLIDE stubs** — implement new interface methods, add tests
7. **Re-apply pre-existing fixes** — check git log for test flakiness fixes that sync may have overwritten
8. **Examples** — `mvn install` modules first, then run
9. **Project files** — version refs in markdown
10. **Docs** — merge upstream doc additions

### Critical lessons

- **Always `mvn install` before running examples** — `exec:java` resolves from local repo, not reactor
- **GLIDE `ClientType` enum gets overwritten by sync** — must re-add `VALKEYGLIDE` value
- **Abstract test parents need `@ParameterizedClass`** — workers may accidentally remove it thinking it's a duplicate
- **Workers removing "duplicate" imports can break `@ParameterizedClass` annotations** — verify abstract test classes retain both `@ParameterizedClass` and `@MethodSource`
- **Rebranding is a separate pass AFTER tests pass** — don't try to rebrand during initial sync

## Key Upgrade Characteristics

- **JSpecify null-safety**: `@Nullable`, `@NullMarked`, `@NullUnmarked` replace `org.springframework.lang.*`
- **Jackson 3**: Dual mode (`tools.jackson` added alongside `com.fasterxml.jackson`)
- **JUnit 6**: `@ParameterizedClass` + `@MethodSource` + `@Test` replaces custom framework
- **New features**: `@ValkeyListener` annotations, `SetCondition`/`CompareCondition`, hash field expiration, `DELEX`/`DIGEST`, `XDELEX`/`XACKDEL`

## Boot 4 Structural Changes

- **Module path**: `module/spring-boot-data-redis/` (Boot 4)
- **Our package**: `io.valkey.springframework.boot.autoconfigure.data.valkey` (preserved)
- **Health**: Moved to `org.springframework.boot:spring-boot-health` artifact
- **Observation replaces Metrics**: `LettuceObservationAutoConfiguration` (new)
- **Threading**: `org.springframework.boot.thread.Threading` (moved)
- **URL schemes**: Must accept `redis://`, `rediss://`, `valkey://`, `valkeys://`

## Post-Sync Verification Checklist

Run ALL of these after any bulk sync from upstream:

```bash
# 1. Files with Redis names (should be 0 outside jedis/lettuce)
find spring-data-valkey/src/ spring-boot-starter-data-valkey/src/ -name "*Redis*" -not -path "*/lettuce/*" -not -path "*/jedis/*"

# 2. Class declarations mismatched with files
grep -rn '^class.*Redis\|^public class.*Redis\|^abstract class.*Redis' spring-data-valkey/src/ spring-boot-starter-data-valkey/src/ --include='*.java' | grep -v 'jedis\|lettuce'

# 3. Inner class declarations reverted to Redis
grep -rn 'class LettuceReactiveRedis\|class RedisNodeBuilder\|class RedisUpdateObject\|class RedisConnectionHolder\|class RedisKeyValueCallback\|class RedisCriteriaAccessor\|class DefaultRedisSortedSetIterator\|record RedisTransactionSynchronizer' spring-data-valkey/src/main/java/ --include='*.java'

# 4. Resource paths in string literals
grep -rn 'org/springframework/data/redis' spring-data-valkey/src/test/java/ --include='*.java' | grep -v 'import\|package\|//\|/\*'

# 5. Bean name literals
grep -rn '"redis' spring-data-valkey/src/ --include='*.java' | grep -v 'redis://\|rediss://\|redis-sentinel\|redis\.call\|DATAREDIS\|redis-master\|redis-6379\|jedis-client\|import\|//\|/\*'

# 6. Missing @ParameterizedClass on abstract test parents
grep -l '@MethodSource' spring-data-valkey/src/test/java/ -r --include='*.java' | xargs grep -L '@ParameterizedClass' | grep -i abstract

# 7. Copyright format
grep -rl 'Copyright [0-9]*-2025\|Copyright 2025 \|Copyright 2026 ' spring-data-valkey/src/ spring-boot-starter-data-valkey/src/ --include='*.java'

# 8. Duplicate test files (Redis + Valkey versions coexisting)
find spring-data-valkey/src/test/java -name '*Redis*' -not -path '*/lettuce/*' -not -path '*/jedis/*'
```

All should return 0 results.

## GLIDE Implementation Pattern

When upstream adds new interface methods, implement in `ValkeyGlide*Commands.java`:

```java
@Override
public ReturnType methodName(args) {
    Assert.notNull(key, "Key must not be null");
    try {
        List<Object> args = new ArrayList<>();
        args.add(key);
        // build command args...
        return connection.execute("COMMAND_NAME",
            (ResponseType glideResult) -> /* convert */,
            args.toArray());
    } catch (Exception ex) {
        throw new ValkeyGlideExceptionConverter().convert(ex);
    }
}
```

Tests: `ValkeyGlideConnection*IntegrationTests.java` with `@EnabledOnCommand("COMMAND_NAME")`.

## Pitfalls & Fixes

| Issue | Symptom | Fix |
|-------|---------|-----|
| Resource paths in string literals | `NoClassDefFoundError: SettingsUtils` | sed `s\|/org/springframework/data/redis/\|/io/valkey/springframework/data/valkey/\|g` |
| Missing `@ParameterizedClass` on abstract parents | `ParameterResolutionException` or 0 tests run | Add `@ParameterizedClass` above `@MethodSource` on abstract class |
| Bean name constants renamed but bean methods not | `NoSuchBeanDefinitionException` | Rename `@Bean` method names + test lookups |
| `ValkeyValueEncoding` reverted to `RedisValueEncoding` | Compilation OK (it's an inner enum) but wrong API | Rename enum + all references |
| Duplicate imports after worker sed | Compilation error (duplicate class) | `awk '!seen[$0]++'` to deduplicate |
| Reactive listener flaky test | `PatternMessage` received after dispose | Add drain loop: `while (collector.poll() != null && count++ < 100) {}` |
| `useUnifiedJedis=false` in test XML | Tests hang (Jedis 7 pool contention) | Upstream pattern, keep it |
| Worker removes `@ParameterizedClass` thinking it's duplicate | Collection tests get 0 runs or ParameterResolutionException | Verify abstract parents retain both annotations |

## Pre-existing Test Fixes to Preserve

These were applied on main before the upgrade and must be re-applied if sync overwrites them:

| PR | File | Fix |
|----|------|-----|
| #91 | ValkeyGlideConnectionKeyCommandsIntegrationTests | `isBetween(3500000.0, 3800000.0)` (was 3700000.0) |
| #91 | ValkeyGlideConnectionListCommandsIntegrationTests | `isLessThan(300L)` (was 200L) |
| #72 | ValkeyGlideConnectionClusterServerCommandsIntegrationTests | Use `>=` assertion, no Thread.sleep |
| — | ReactiveValkeyMessageListenerContainerIntegrationTests | Drain loop after dispose |

## Documentation Sync Notes

### Source mapping (Antora → Starlight)

| Upstream path | Our path | Notes |
|---------------|----------|-------|
| `src/main/antora/modules/ROOT/pages/redis/*.adoc` | `docs/src/content/docs/valkey/*.md[x]` | Main content |
| `src/main/antora/modules/ROOT/pages/redis/redis-repositories/*.adoc` | `docs/src/content/docs/valkey/valkey-repositories/*.md` | Repos subpages |
| `src/main/antora/modules/ROOT/pages/observability.adoc` | `docs/src/content/docs/observability.md` | Has GLIDE OTel content |
| `src/main/antora/modules/ROOT/pages/appendix.adoc` | `docs/src/content/docs/appendix.md` | Command support table |
| `src/main/antora/modules/ROOT/pages/preface.adoc` | `docs/src/content/docs/preface.md` | Intro/getting started |

### Doc sync process

1. Generate diff: `cd /path/to/spring-data-redis && git diff OLD..NEW -- src/main/antora/`
2. Identify new/deleted/changed pages
3. For NEW pages: convert AsciiDoc → Starlight markdown, rebrand, add GLIDE notes if relevant
4. For CHANGED pages: compare upstream diff against our page, apply equivalent changes (don't replace)
5. For appendix: add new commands to the supported commands table
6. Internal links must use absolute paths (e.g., `/valkey/pubsub-sending`) not relative `./`
7. Add new pages to sidebar in `docs/astro.config.mjs`
8. Verify: `cd docs && npm run build`

### Pages with GLIDE-specific content (DO NOT overwrite)

- `drivers.md` — feature comparison table, GLIDE connector section
- `getting-started.md` — GLIDE quickstart
- `connection-modes.md` — GLIDE connection modes
- `cluster.md` — GLIDE cluster support
- `pipelining.md` — GLIDE pipelining
- `transactions.md` — GLIDE transaction support
- `pubsub.mdx` — GLIDE pub/sub note
- `pubsub-receiving.mdx` — GLIDE driver note
- `pubsub-annotated.mdx` — GLIDE driver note
- `template.mdx` — GLIDE template usage
- `observability.md` — GLIDE OpenTelemetry section (entire first half)
- `valkey-cache.md` — GLIDE cache notes

### AsciiDoc → Markdown conversion cheatsheet

| AsciiDoc | Markdown |
|----------|----------|
| `= Title` | `# Title` |
| `== Section` | `## Section` |
| `[source,java]` + `----` | ` ```java ` |
| `javadoc:org.foo.Bar[]` | `Bar` (just class name) |
| `xref:redis/foo.adoc[text]` | `[text](/valkey/foo)` |
| `link:url[text]` | `[text](url)` |
| `NOTE:` / `TIP:` / `IMPORTANT:` | `:::note` / `:::tip` / `:::caution` |
| `[tabs]\n======\nJava::\n+\n[source,java]` | `<Tabs><TabItem label="Java">` (requires .mdx) |

## Boot Starter Package Structure (tech debt)

Our Boot test package structure doesn't match upstream Boot 4's layout. This is intentional (we flattened into one module) but creates manual mapping work during syncs.

| Upstream Boot 4 path | Our path | Notes |
|---------------------|----------|-------|
| `data/redis/domain/city/` | `autoconfigure/data/valkey/city/` | Test domain classes |
| `data/redis/domain/empty/` | `autoconfigure/data/valkey/empty/` | Test empty package |
| `data/redis/autoconfigure/` | `autoconfigure/data/valkey/` | Main auto-config |
| `data/redis/autoconfigure/health/` | `actuate/autoconfigure/data/valkey/` | Health auto-config |
| `data/redis/autoconfigure/observation/` | `actuate/autoconfigure/metrics/valkey/` | Observation auto-config |
| `data/redis/health/` | `actuate/data/valkey/` | Health indicators |
| `data/redis-test/` (separate module) | `test/autoconfigure/data/valkey/` | @DataValkeyTest slice |

Consider restructuring in a future PR to reduce mapping friction during syncs.

## Review Findings (post-sync)

### Subagent file writes are unreliable
Subagent-reported "success" does not guarantee the file was actually modified. Always verify with `grep` after subagent writes. Direct `sed` or heredoc writes are more reliable.

### Common rebranding regressions from upstream sync
The upstream sync reintroduces `redis` naming in:
- Public method names: `isRedisSentinelAware()`, `hasRedisSentinelConfigured()`
- Constructor/method parameter names: `redisCounter`, `redisTemplate`, `redisOperations`, `redisConnection`
- Internal variable names: `redisUrl`, `redisKey`, `redisUpdateObject`
- Constants: `REDIS_PORT`, `REDIS_CONTAINER_NAMES`, `REDIS_IMAGE_NAMES`
- Test method names: `testDefaultRedisConfiguration`, `redisIsUp`
- Javadoc: `@param redisListener`, `"Redis health check failed"`, `"stored in Redis"`
- `@since` tags: upstream versions (4.0.0) overwrite our versions (1.0.0)
- String literals: `SimpleAsyncTaskExecutor("redis-")`, `"Invalid Redis URL"`
- URL examples in tests: `redis://user:password@...` should be `valkey://user:password@...`

### Efficient regression detection
Use `git diff main -- <path> | grep "^+" | grep -i "redis"` filtered for legitimate exclusions to find rebranding regressions. Group by package to manage scope.

### Boot module resolves from local repo
After renaming methods in `spring-data-valkey`, must `mvn install -pl spring-data-valkey -DskipTests` before boot module can compile against the renamed API.

### Valkey 9 compatibility issues (separate ticket)
- Jedis 7 BITOP serialization incompatible with Valkey 9
- Stream DeletionPolicy (XADD/XTRIM) not implemented in Valkey 9
- GLIDE CLUSTER SETSLOT "Target node is myself" behavioral change
- GLIDE HSETEX returns Long (not String "OK") on Valkey 9 — handle with `(Object glideResult)` pattern

## Boot Module Test Structure

The boot test structure diverges from upstream intentionally:

**Upstream (Spring Boot):**
- `DataRedisAutoConfigurationTests` — single file, Lettuce is default, no classpath exclusions
- `DataRedisAutoConfigurationJedisTests` — excludes Lettuce from classpath

**Our structure (3 clients, GLIDE default):**
- `ValkeyAutoConfigurationTests` — GLIDE tests (DEFAULT, no client-type property, no classpath exclusions). Comprehensive: generic config, URL, cluster, SSL, OpenTelemetry, connection details, virtual threads, timeouts. TODOs for sentinel/master-replica.
- `ValkeyAutoConfigurationLettuceTests` — Lettuce tests (`client-type=lettuce` property). Covers all Lettuce-specific: sentinel, read-from, cluster refresh, pool, client options, master-replica, timeouts.
- `ValkeyAutoConfigurationJedisTests` — Jedis tests (`client-type=jedis` property). Subset: basic config, URL, pool, sentinel, cluster, SSL.
- `ValkeyPropertiesTests` — Tests defaults for ALL clients (GLIDE, Lettuce, SSL).

**Key differences from upstream:**
- We use `client-type` property to select clients (not `@ClassPathExclusions`)
- `@ClassPathExclusions` has JUnit 6 compatibility issues (ServiceLoader can't find engine)
- GLIDE test file is the "primary" — new generic upstream tests should be added here first
- Lettuce/Jedis tests cover their client-specific features

## When syncing boot tests in future upgrades

1. New upstream tests go into `ValkeyAutoConfigurationLettuceTests` (direct port with rename)
2. Generic tests (not Lettuce-specific) should ALSO get GLIDE equivalents in `ValkeyAutoConfigurationTests`
3. Sentinel/master-replica tests stay Lettuce-only (GLIDE doesn't support)
4. `ValkeyPropertiesTests` should have GLIDE defaults test preserved
5. Don't overwrite `ValkeyAutoConfigurationTests` with upstream — it's our custom GLIDE file
