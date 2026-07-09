# Upstream Sync Rules — General

Evergreen rules for syncing spring-data-valkey with upstream Spring Data Redis and Spring Boot releases. These rules apply to ALL upgrades regardless of version.

## Source Repository Mapping

| Local Module | Upstream Repository | Upstream Path |
|--------------|-------------------|---------------|
| `spring-data-valkey/` | [spring-data-redis](https://github.com/spring-projects/spring-data-redis) | `src/` |
| `spring-boot-starter-data-valkey/` | [spring-boot](https://github.com/spring-projects/spring-boot) | *(path varies by version — see version-specific rules)* |
| `docs/` | [spring-data-redis](https://github.com/spring-projects/spring-data-redis) | `src/main/antora/` (we use Starlight) |
| `examples/` | [spring-data-examples](https://github.com/spring-projects/spring-data-examples) | `redis/` |
| `pom.xml` (parent) | [spring-data-build](https://github.com/spring-projects/spring-data-build) | `parent/pom.xml` |

Local repos:
- `/path/to/spring-data-redis`
- `/path/to/spring-boot`
- `/path/to/spring-data-build`

## Rename Rules

### Package Renames

| Upstream | Local |
|----------|-------|
| `org.springframework.data.redis` | `io.valkey.springframework.data.valkey` |
| `org.springframework.boot.autoconfigure.data.redis` | `io.valkey.springframework.boot.autoconfigure.data.valkey` |

### Class/Interface Renames (Spring Data classes)

| Upstream | Local |
|----------|-------|
| `Redis*` | `Valkey*` (general rule) |
| `RedisTemplate` | `ValkeyTemplate` |
| `StringRedisTemplate` | `StringValkeyTemplate` |
| `RedisConnection` | `ValkeyConnection` |
| `RedisConnectionFactory` | `ValkeyConnectionFactory` |
| `RedisCallback` | `ValkeyCallback` |
| `RedisOperations` | `ValkeyOperations` |
| `ReactiveRedisTemplate` | `ReactiveValkeyTemplate` |
| `RedisClusterConfiguration` | `ValkeyClusterConfiguration` |
| `RedisSentinelConfiguration` | `ValkeySentinelConfiguration` |
| `RedisStandaloneConfiguration` | `ValkeyStandaloneConfiguration` |
| `RedisHash` | `ValkeyHash` |
| `EnableRedisRepositories` | `EnableValkeyRepositories` |
| `RedisSystemException` | `ValkeySystemException` |
| `RedisClientInfo` | `ValkeyClientInfo` |
| `RedisNode` | `ValkeyNode` |
| `RedisStreamCommands` | `ValkeyStreamCommands` |
| `RedisServerCommands` | `ValkeyServerCommands` |
| `RedisCommands` (Spring interface) | `ValkeyCommands` |
| `RedisClusterCommands` (Spring interface) | `ValkeyClusterCommands` |
| `RedisPubSubCommands` (Spring interface) | `ValkeyPubSubCommands` |
| `RedisSentinelCommands` (Spring interface) | `ValkeySentinelCommands` |
| `RedisSetCommands` (Spring interface) | `ValkeySetCommands` |
| `RedisClusterNode` (Spring class) | `ValkeyClusterNode` |
| `RedisCommand` (Spring enum) | `ValkeyCommand` |
| `RedisMessageListenerContainer` | `ValkeyMessageListenerContainer` |
| `RedisSerializer` | `ValkeySerializer` |
| `RedisCollectionFactoryBean` | `ValkeyCollectionFactoryBean` |
| `RedisRepositoryExtension` | `ValkeyRepositoryExtension` |
| `RedisNamespaceHandler` | `ValkeyNamespaceHandler` |

### Property Renames

| Upstream | Local |
|----------|-------|
| `spring.data.redis.*` | `spring.data.valkey.*` |
| `spring.redis.*` | `spring.valkey.*` |

### Bean Name Renames

| Upstream | Local |
|----------|-------|
| `redisTemplate` | `valkeyTemplate` |
| `redisConnectionFactory` | `valkeyConnectionFactory` |

### Documentation Renames

| Upstream | Local |
|----------|-------|
| "Redis" (prose) | "Valkey" |
| "Redis server" | "Valkey server" |
| "Spring Data Redis" | "Spring Data Valkey" |
| "spring-data-redis" (artifact) | "spring-data-valkey" |

Keep "Redis" when referring to:
- Redis protocol compatibility
- Redis Serialization Protocol (RESP)
- Historical context or migration from Redis

## Exclusions — Do NOT Rename

### Jedis Library
- `redis.clients.jedis.*` — entire package

### Lettuce Library (classes from `io.lettuce.core.*`)

| Class | Notes |
|-------|-------|
| `RedisURI` | Lettuce URI class |
| `RedisCodec` | Lettuce codec interface |
| `RedisClient` | Lettuce standalone client |
| `RedisClusterClient` | Lettuce cluster client |
| `AbstractRedisClient` | Lettuce base client |
| `RedisFuture` | Lettuce future type |
| `RedisCredentials` / `RedisCredentialsProvider` | Lettuce credentials |
| `RedisPubSubListener` | Lettuce pub/sub listener |
| `RedisException` / `RedisConnectionException` | Lettuce exceptions |
| `RedisCommandExecutionException` / `RedisCommandInterruptedException` / `RedisCommandTimeoutException` | Lettuce exceptions |
| `RedisProtocol` | Lettuce protocol enum |
| `RedisCommand<?, ?, ?>` | Lettuce command type (observability) |
| `Stateful*Connection` (all Lettuce variants) | Lettuce connections |
| `Base*Commands` / `Redis*Commands` / `Redis*AsyncCommands` / `Redis*ReactiveCommands` | Lettuce command interfaces |
| `RedisAdvancedCluster*Commands` | Lettuce advanced cluster |
| `RedisClusterNode` (`io.lettuce.core.cluster.models.partitions`) | Lettuce's — different from Spring's `ValkeyClusterNode` |
| `DEFAULT_REDIS_PORT` | Lettuce constant |

### Protocol and URI Schemes

| Pattern | Notes |
|---------|-------|
| `redis://` / `rediss://` / `redis-sentinel://` | URI schemes |
| `redis.call` / `redis.pcall` / `redis.log` / `redis.status_reply` / `redis.error_reply` | Lua scripting API |

### XML Schemas and Namespaces

| Pattern | Notes |
|---------|-------|
| `xmlns:redis="http://www.springframework.org/schema/redis"` | Schema namespace |
| `spring-redis-1.0.xsd` | Schema filename |
| `redis:collection`, `redis:list`, `redis:set`, `redis:map` | XML namespace prefixes |
| `schema/redis/spring-redis` | Schema paths |

### Other

| Pattern | Notes |
|---------|-------|
| `DATAREDIS-*` | Issue tracker references |
| `redis-master` | Sentinel master name in tests |
| `redis.host`, `redis.port`, etc. | Test properties for sentinel |
| `valkey-6379.sock` | Unix socket path in tests (renamed from redis-6379.sock) |
| Variable names: `redisURI`, `redisUri` | When referring to Lettuce's RedisURI type |

## Ambiguous Cases — Decision Rules

| Scenario | Decision |
|----------|----------|
| `RedisCommands` in Lettuce adapter code referencing Lettuce's type | Keep as `RedisCommands` |
| `RedisCommands` in Spring Data interface definition | Rename to `ValkeyCommands` |
| `RedisCommand<?, ?, ?>` in observability code | Keep (Lettuce protocol type) |
| `getRedisProtocol()` | Keep — refers to Lettuce's `RedisProtocol` |
| `RedisClusterNode` in Lettuce partition models | Keep (Lettuce's class) |
| `RedisClusterNode` in Spring Data connection package | Rename to `ValkeyClusterNode` |
| `instanceof RedisCommands` in Lettuce code | Keep — checking for Lettuce's type |
| `mock(RedisCommands.class)` in Lettuce tests | Keep — mocking Lettuce's type |

**Rule of thumb:** Check the import. If it's from `io.lettuce.core.*` or `redis.clients.jedis.*`, do NOT rename.

## Files Unique to spring-data-valkey (No Upstream Equivalent)

These must NOT be overwritten or modified during sync:

```
spring-data-valkey/src/main/java/.../connection/valkeyglide/  (entire package)
spring-boot-starter-data-valkey/src/main/java/.../ValkeyGlideConnectionConfiguration.java
spring-boot-starter-data-valkey/src/main/java/.../ValkeyGlideClientConfigurationBuilderCustomizer.java
```

## Sync Process

### Step 1: Determine Versions

Check current baseline in `pom.xml` and identify latest upstream tags:

```bash
grep springdata.parent.version pom.xml
cd /path/to/spring-data-redis && git tag | grep "^[34]\." | sort -V | tail -5
cd /path/to/spring-boot && git tag | grep "^v[34]\." | sort -V | tail -5
cd /path/to/spring-data-build && git tag | grep "^[34]\." | sort -V | tail -5
```

### Step 2: Generate Per-Package Diffs

```bash
cd /path/to/spring-data-redis
git diff <source>..<target> -- src/main/java/org/springframework/data/redis/<package>/ > /tmp/diff-<package>.patch
```

Get stats for scoping:
```bash
git diff <source>..<target> --stat -- src/main/ | tail -1
git diff <source>..<target> --diff-filter=A --name-only -- src/main/ | wc -l  # new files
git diff <source>..<target> --diff-filter=D --name-only -- src/main/ | wc -l  # deleted files
```

### Step 3: Classify Changes

For each file in the diff, categorize as:
1. **Pass-through** — Change applies directly after rename rules
2. **Skip** — File we don't have or deliberately diverged from
3. **Conflict** — Change touches code near GLIDE additions (human review needed)
4. **New file** — Upstream added a new file — apply rename rules and add

### Step 4: Apply Changes (dependency order)

Process packages in this order:
1. Connection interfaces (top-level `connection/` minus adapters)
2. Lettuce adapter (`connection/lettuce/`) — most exclusions
3. Jedis adapter (`connection/jedis/`)
4. Core (`core/`)
5. Serializer (`serializer/`)
6. Other packages (cache, config, listener, hash, stream, support, repository)
7. Boot starter (structural — see version-specific rules)
8. Tests (mirrors main code order)

For each package:
1. Identify corresponding local file using package rename mapping
2. Apply upstream code change
3. Apply ALL rename rules to new/modified code
4. Verify exclusions are respected
5. Compile: `./mvnw compile -pl spring-data-valkey -q`

### Step 5: Handle Boot Starter

The Boot starter requires special handling because Spring Boot may restructure between versions. See version-specific rules file for mapping tables.

### Step 6: Validate

```bash
./mvnw clean compile
./mvnw test -pl spring-data-valkey
./mvnw test -pl spring-boot-starter-data-valkey
make examples
```

### Step 7: Check for Missed References

```bash
# Should find nothing (old package still present):
grep -r "org\.springframework\.data\.redis" spring-data-valkey/src/ --include="*.java" | grep -v "test/resources"

# Should find nothing (Lettuce wrongly renamed):
grep -r "io\.lettuce\.core\.valkey" spring-data-valkey/src/ --include="*.java"
grep -r "valkey\.clients\.jedis" spring-data-valkey/src/ --include="*.java"
```

## Known Pitfalls

1. **Lettuce's `RedisCommand` vs Spring's `ValkeyCommand`** — In observability/instrumentation code, the import must point to `io.lettuce.core.protocol.RedisCommand`, not Spring's enum.

2. **Same-named interfaces** — Both Lettuce and Spring Data have interfaces called `RedisCommands`, `RedisServerCommands`, etc. Context determines whether to rename.

3. **XML schema references** — The XML namespace (`xmlns:redis`) and schema files (`spring-redis-1.0.xsd`) must keep their original Redis naming.

4. **Lua scripting** — `redis.call()`, `redis.pcall()`, etc. are server-side Lua APIs. Do NOT rename.

5. **URI schemes** — `redis://`, `rediss://`, `redis-sentinel://` are protocol-level. Do NOT rename.

6. **Protobuf dependency** — GLIDE requires `com.google.protobuf:protobuf-java` as an optional dependency.

7. **Boot starter path changes** — Spring Boot frequently moves autoconfigure code between versions. Always verify the current upstream path before diffing.

## Updating These Rules

After each upgrade iteration, update this file with:
- New exclusions discovered
- New ambiguous cases and their resolutions
- Pitfalls encountered
- Any patterns that broke the automated process

## Exclusions Added (Spring Boot 4 Upgrade)

### Inner Types That Keep "Redis" Naming

| Pattern | Notes |
|---------|-------|
| `ValueEncoding.RedisValueEncoding` | Inner enum of ValueEncoding |
| `newRedisClusterNode()` | Builder method name on ValkeyClusterNode |
| `toRedisClusterNode()` | Converter method name |
| `toSetOfRedisClusterNodes()` | Converter method name |

### Jedis 7.x Library (NEW in 7.4.1)

| Class | Notes |
|-------|-------|
| `RedisClusterClient` | `redis.clients.jedis.RedisClusterClient` — Jedis's cluster client |
| `RedisClient` | `redis.clients.jedis.RedisClient` — Jedis's standalone client |
| `ClusterClientBuilder` | Jedis builder |
| `SentinelClientBuilder` | Jedis builder |
| `StandaloneClientBuilder` | Jedis builder |

### Lettuce 7.x Library (NEW generic interfaces)

| Pattern | Notes |
|---------|-------|
| `RedisClusterCommands<K,V>` | Lettuce generic command interface |
| `RedisPubSubCommands<K,V>` | Lettuce pub/sub interface |
| `RedisSetCommands<K,V>` | Lettuce set commands interface |
| `RedisServerCommands<K,V>` | Lettuce server commands interface |
| `RedisSentinelCommands<K,V>` | Lettuce sentinel interface |
| `RedisClusterAsyncCommands<K,V>` | Lettuce async interface |
| `RedisClusterReactiveCommands<K,V>` | Lettuce reactive interface |
| `RedisReactiveCommands<K,V>` | Lettuce reactive interface |
| `RedisAsyncCommands<K,V>` | Lettuce async interface |

**Rule**: Any `Redis*Commands<K,V>` with type parameters in Lettuce adapter code is a Lettuce type. Do NOT rename.

### Jackson 3 Namespaces

| Pattern | Notes |
|---------|-------|
| `tools.jackson.*` | Entire Jackson 3 package — no renames needed |
| `com.fasterxml.jackson.*` | Existing Jackson 2 — preserved (dual mode) |

## Updates from Phases 3-4

### Method Names in Lettuce/Jedis Packages

Methods that KEEP "Redis" (return/wrap third-party library types):

| Method | Class | Reason |
|--------|-------|--------|
| `createRedisClient()` | `JedisConnectionFactory` | Returns `redis.clients.jedis.RedisClient` |
| `createRedisClusterClient()` | `JedisConnectionFactory` | Returns `redis.clients.jedis.RedisClusterClient` |
| `createRedisSentinelClient()` | `JedisConnectionFactory` | Returns Jedis sentinel client |
| `sentinelConfigurationToRedisURI()` | `LettuceConverters` | Returns Lettuce `RedisURI` |
| `createRedisStandaloneConfiguration()` | `LettuceConverters` | Named after Lettuce's `RedisURI` input |
| `createRedisSocketConfiguration()` | `LettuceConverters` | Named after Lettuce's `RedisURI` input |
| `getRedisProtocol()` | Lettuce config | Returns Lettuce's `RedisProtocol` |

Methods that MUST be "Valkey" (our methods returning our types):

| Method | Class | Notes |
|--------|-------|-------|
| `isValkeyClusterAware()` | `JedisConnectionFactory` | Our config detection method |
| `isValkeySentinelAware()` | `JedisConnectionFactory`, `LettuceConnectionFactory` | Our config detection method |
| `toListOfValkeyClientInformation()` | `LettuceConverters`, `JedisConverters` | Returns `List<ValkeyClientInfo>` |
| `toListOfValkeyServer()` | `LettuceConverters`, `JedisConverters` | Returns `List<ValkeyServer>` |
| `createValkeySentinelConfiguration()` | `LettuceConverters` | Returns `ValkeySentinelConfiguration` |
| `createValkeyConfiguration()` | `LettuceConnectionFactory` | Returns `ValkeyConfiguration` |
| `createValkeyPool()` | `JedisConnectionFactory` | Returns `Pool<Jedis>` |
| `createValkeySentinelPool()` | `JedisConnectionFactory` | Returns `Pool<Jedis>` |
| `getValkeyClient()` | `ValkeyClientProvider` | Returns `AbstractRedisClient` (our interface) |
| `getDedicatedValkeyCommands()` | `LettuceConnection` | Our internal method |
| `stringToValkeyClientListConverter()` | `LettuceConverters` | Our converter |
| `CompletedValkeyFuture` | `LettuceServerCommands` | Our inner class |
| `valkeyCredentialsProviderFactory()` | `LettuceClientConfiguration` | Our builder method |

### Inner Classes/Types That Keep "Redis" Naming

| Type | Location | Notes |
|------|----------|-------|
| `RedisNodeBuilder` | `ValkeyNode.RedisNodeBuilder` | Builder inner class |
| `RedisValueEncoding` | `ValueEncoding.RedisValueEncoding` | Inner enum |
| `GeoLocation` | `ValkeyGeoCommands.GeoLocation` | Deprecated inner class (extends domain.geo.GeoLocation) |

### Property String Literals

| Old Pattern | New Pattern | Notes |
|-------------|-------------|-------|
| `"spring.redis.*"` | `"spring.valkey.*"` | ALL string literals for config properties |
| `"redisKeyValueAdapter"` | `"valkeyKeyValueAdapter"` | Bean name string |
| `"redisTemplate"` | `"valkeyTemplate"` | Bean name string |

**Important**: String literal renames are NOT caught by class/import renames. Always do a separate grep pass:
```bash
grep -rn '"spring\.redis\.' spring-data-valkey/src/main/java/ --include="*.java"
grep -rn '"redis' spring-data-valkey/src/main/java/ --include="*.java" | grep -v "io\.lettuce\|redis\.clients\|redis://\|rediss://\|redis-sentinel\|redis\.call\|DATAREDIS"
```

### URL Schemes

The `ValkeyUrl` class must accept ALL of these as valid:
- `redis://` (standard insecure)
- `rediss://` (standard TLS)
- `valkey://` (Valkey-branded insecure)
- `valkeys://` (Valkey-branded TLS)
- `redis-sentinel://` (sentinel)

### JUnit Migration Notes

| JUnit 4 | JUnit 5/6 | Notes |
|---------|-----------|-------|
| `Assume.assumeTrue(String, boolean)` | `Assumptions.assumeTrue(boolean, String)` | Args REVERSED |
| `@Ignore` | `@Disabled` | |
| `AssumptionViolatedException` | `TestAbortedException` | |
| `@RunWith(Parameterized.class)` | `@ParameterizedClass` (JUnit 6) | |
| `org.junit.runners.Parameterized.Parameters` | Removed | Use `@MethodSource` |
| `junit.framework.Assert` | Not needed | Comes from junit:junit:4.x for multithreadedtc compat |

### File Naming Convention for Re-synced Tests

When re-syncing test files from upstream:
- The test class `RedisTemplateUnitTests` → file `ValkeyTemplateUnitTests.java` but class name inside must ALSO be renamed
- Constructor names must match the class name
- Watch for Lettuce's `RedisClusterNode` in test code — do NOT rename (it's from `io.lettuce.core.cluster.models.partitions`)

## Resource File Sync Rules

### Files to NEVER overwrite from upstream (Valkey-branded)

These files have intentional Valkey branding that differs from upstream:

| File | Reason |
|------|--------|
| `config/namespace.xml` | Uses `xmlns:valkey`, `valkey:listener-container`, `spring.valkey.io` schema URL |
| `listener/container.xml` | Uses `ValkeyMessageListenerContainer`, `ValkeyMDP`, `valkeyContainer` bean names |
| `support/collections/container.xml` | Uses `xmlns:valkey`, `valkey:collection`, `valkeyTemplate` |
| `pe.xml` | Uses `ValkeyTemplate`, `ValkeyViewPE`, `valkeyTemplate` |
| `test.properties` | Comment says "valkey", socket path uses `valkey-6379.sock` |
| `logback.xml` | Logger scoped to `io.valkey.springframework.data` |

**Rule**: Do NOT blindly sync XML/properties test resources from upstream. Always diff first and preserve Valkey branding for:
- XML namespace prefixes (`valkey:` not `redis:`)
- XSD schema URLs (`spring.valkey.io/schema/valkey/`)
- Bean names (`valkeyTemplate`, `valkeyConnectionFactory`, `valkeyContainer`)
- Class references (use our `Valkey*` class names, not upstream's `Redis*`)
- Comments and documentation text
- Socket/file paths that were intentionally rebranded

### Files safe to sync from upstream

| File | Notes |
|------|-------|
| `JedisConnectionIntegrationTests-context.xml` | Only class refs (already Valkey-packaged), no XML namespace |
| `UnifiedJedisConnectionIntegrationTests-context.xml` | New file, apply package renames |
| `LettuceConnection*-context.xml` | Only class refs |
| Lua scripts (`*.lua`) | No branding, pure logic |
| `props.properties` | Test data only, no branding |

### Boot starter resource files

| File | Action |
|------|--------|
| `AutoConfiguration.imports` | Sync — use our class FQNs |
| `spring.factories` | Sync — use our class FQNs, upstream structure |
| `additional-spring-configuration-metadata.json` | Sync — rename `spring.data.redis` → `spring.data.valkey`, fix prose ("Redis" → "Valkey" in descriptions), keep `RedisURI` (Lettuce class) |
| `test.jks` | Binary, sync as-is |

## Test File Sync Lessons (Phase 4)

### Test files MUST be bulk synced (not cherry-picked)

Phase 1 synced `src/main/java/` comprehensively but only cherry-picked individual test files. This left test infrastructure out of sync (custom `@ParameterizedValkeyTest` framework still present while upstream deleted it). **Always sync `src/test/java/` as a bulk operation**, same as main code.

### Test file sync process

Same as main code sync:
```bash
# 1. Backup GLIDE test files
# 2. Bulk copy ALL upstream test files with package renames
# 3. Restore GLIDE files
# 4. Apply class renames (same comprehensive sed as main code)
# 5. Restore Lettuce types: s/io\.lettuce\.(.*)\.Valkey/io.lettuce.\1.Redis/g
# 6. Delete Redis-named duplicate files (keep Valkey-named)
# 7. Fix constructors to match class names
# 8. Compile iteratively
```

### Test utility method renames

| Upstream | Ours | Notes |
|----------|------|-------|
| `getRedisData()` | `getValkeyData()` | On `ValkeyTestData` |
| `configureRedisListeners()` | `configureValkeyListeners()` | Interface method |
| `getNativeRedisConnectionMock()` | `getNativeValkeyConnectionMock()` | Test base class |

### Lettuce test files need special handling

Lettuce test files reference BOTH Spring's types (renamed to Valkey) AND Lettuce's own types (keep Redis). The safest approach for these files:
1. Sync from upstream with ONLY package renames
2. Apply Spring class renames individually (not blanket `Redis*` → `Valkey*`)
3. Then restore `io.lettuce.*` class names
4. For `RedisClusterCommands<K,V>` with type params: always Lettuce's type (keep Redis)
5. For `ValkeyClusterCommands` without params: Spring's interface (keep Valkey)


## Link and Javadoc Convention

### Documentation Links
- ALL `redis.io` links must be changed to `valkey.io` — main already had this done
- "Redis Documentation:" in javadoc `@see` tags → "Valkey Documentation:"
- This applies to ~2,070 javadoc links across connection interfaces and commands

### Protocol/Server References in Javadoc
These WERE renamed to Valkey on main (not left as Redis):
- "Redis 3 format" → "Valkey 3"
- "Redis 4, with bus port" → "Valkey 4, with bus port"  
- "Redis 7, with announced hostname" → "Valkey 7, with announced hostname"
- "Redis' representation" → "Valkey' representation"
- "redis.conf" → "valkey.conf"
- "/tmp/redis.sock" → "/tmp/valkey.sock"
- "Redis' DIGEST command" → "Valkey's DIGEST command"
- GitHub link text "Redis" (when not part of actual repo URL) → "Valkey"

### What stays as "redis" in source
- `redis-rs` (Rust crate name in GLIDE file paths)
- `antirez/redis` or `antirez/valkey` (GitHub repo URLs — use whatever main has)
- `isRedisClusterAware()` — REMOVED, now `isValkeyClusterAware()` (was incorrectly kept before)
- Plain English comments about the server behavior (e.g., "we give redis some time")
- ALL Lettuce/Jedis library types (see exclusions above)

## Test Variable/Method Name Convention

### Test variable names MUST be rebranded
The upstream sync frequently overwrites test variable renames. After ANY upstream sync, run:

```bash
# Find regressions in test files (excluding allowed patterns)
grep -rn "redisTemplate\|redisConnectionFactory\|redisConnectionMock\|redisConverter\|redisCache\|redisScript\|redisData" \
  spring-data-valkey/src/test/java/ | grep -v "/lettuce/\|/jedis/\|/valkeyglide/\|import"
```

Common test renames required after sync:
| Upstream | Ours |
|----------|------|
| `redisTemplate` | `valkeyTemplate` |
| `redisConnectionFactory` | `valkeyConnectionFactory` |
| `redisConnectionMock` | `valkeyConnectionMock` |
| `redisConverter` | `valkeyConverter` |
| `redisCache` | `valkeyCache` |
| `redisScript` | `valkeyScript` |
| `redisData` | `valkeyData` |
| `redisTemplateSpy` | `valkeyTemplateSpy` |
| `TypeWithRedisHashAnnotation` | `TypeWithValkeyHashAnnotation` |
| `PojoRedisSerializer` | `PojoValkeySerializer` |
| `RedisContextConfiguration` | `ValkeyContextConfiguration` |
| Method names with `Redis` | Method names with `Valkey` |

### Post-sync verification command
After completing a sync, run this to find ALL remaining regressions:

```bash
# Main sources (should return 0 — only redis-rs paths allowed)
grep -rn "redis\|Redis" spring-data-valkey/src/main/java/ | \
  grep -v "valkey\.io\|redis-rs" | grep -v "/lettuce/\|/jedis/" | grep -v "import" | wc -l

# Test sources (filter out library types and intentional refs)
grep -rn "redis\|Redis" spring-data-valkey/src/test/java/ | \
  grep -v "valkey\.io\|redis\.io" | grep -v "/lettuce/\|/jedis/\|/valkeyglide/" | \
  grep -v "import\|StatefulRedis\|RedisClient\|RedisClusterClient\|RedisURI\|RedisCommands\|RedisClusterCommands\|AbstractRedisClient\|RedisCodec\|isValkeyClusterAware" | \
  grep -v "redis://\|rediss://\|redis-sentinel\|redis_version\|RedisContainer\|spring-data-redis\|github.*redis" | \
  grep -v "\".*redis\|\".*Redis\|// we give redis" | wc -l
```

Both should return 0 (or very close to 0 with only intentional refs).

## Post-Sync GLIDE Restoration Checklist

After EVERY upstream sync, check for lost GLIDE test configurations:

```bash
# Find files where GLIDE content was lost
git diff main --name-only -- spring-data-valkey/src/ | while read f; do
  if [ ! -f "$f" ]; then continue; fi
  main_glide=$(git show main:"$f" 2>/dev/null | grep -ic "glide")
  curr_glide=$(grep -ic "glide" "$f" 2>/dev/null)
  if [ "$main_glide" -gt "$curr_glide" ] 2>/dev/null; then
    echo "LOST: $f (main=$main_glide, current=$curr_glide)"
  fi
done
```

Common files that get GLIDE content overwritten during sync:
- `*TestParams.java` — parameterized test providers (add GLIDE as 3rd driver)
- `*IntegrationTests.java` — integration tests with factory lists
- `BoundKeyParams.java`, `CollectionTestParams.java` — collection test params
- `PubSubTestParams.java`, `PubSubTests.java` — pub/sub tests
- `ValkeyClusterTemplateIntegrationTests.java` — cluster test configuration

Pattern to add:
```java
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;

// Standalone
ValkeyGlideConnectionFactory valkeyGlideConnFactory = ValkeyGlideConnectionFactoryExtension
    .getConnectionFactory(ValkeyStandalone.class);

// Cluster
ValkeyGlideConnectionFactory valkeyGlideClusterConnFactory = ValkeyGlideConnectionFactoryExtension
    .getConnectionFactory(ValkeyCluster.class);
```

## Post-Sync Method Name Verification

After sync, check for method name regressions in lettuce/ and jedis/ packages:

```bash
# Our methods that return Valkey types but got named redis again
grep -rn "Valkey[A-Za-z]* redis[A-Za-z]*" spring-data-valkey/src/main/java/ | grep -v "import"

# Our methods with Redis in name (should be Valkey)
grep -rn "toListOfRedis\|createRedisPool\|createRedisS\|stringToRedis\|getDedicatedRedis\|CompletedRedis\|getRedisClient" \
  spring-data-valkey/src/main/java/ | grep -v "import\|createRedisClient\|createRedisClusterClient\|createRedisSentinelClient"
```

Methods that STAY as Redis (Jedis 7.x library types):
- `createRedisClient()` — returns `redis.clients.jedis.RedisClient`
- `createRedisClusterClient()` — returns `redis.clients.jedis.RedisClusterClient`
- `createRedisSentinelClient()` — returns Jedis sentinel client

Methods that must be Valkey (our methods):
- `createValkeyPool()` / `createValkeySentinelPool()` — return `Pool<Jedis>`
- `getValkeyClient()` — on `ValkeyClientProvider` interface
- `toListOfValkeyServer()` / `toListOfValkeyClientInformation()`
- `stringToValkeyClientListConverter()`
- `getDedicatedValkeyCommands()`
- `createValkeyConfiguration()`
- `CompletedValkeyFuture`
- `valkeyCredentialsProviderFactory` (builder method + field)

## Develocity Extension

The `.mvn/extensions.xml` file contains Spring's Develocity build scan extension which we don't use. Remove it to avoid `[ERROR] [Resource-Usage]` noise. Keep `.mvn/jvm.config` and `.mvn/wrapper/`.
