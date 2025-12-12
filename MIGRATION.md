# Migration Guide: Spring Data Redis to Spring Data Valkey

This guide helps you migrate from Spring Data Redis to Spring Data Valkey.

## Overview

Spring Data Valkey is a fork of Spring Data Redis that has been rebranded to work with [Valkey](https://valkey.io/), an open source high-performance key/value datastore that is fully compatible with Redis. The migration primarily involves updating package names, class names, and configuration properties from Redis to Valkey. Spring Data Valkey also adds support for [Valkey GLIDE](https://github.com/valkey-io/valkey-glide), a high-performance client library that is now the recommended default driver alongside existing Lettuce and Jedis support.

Migration paths for both Spring Boot and vanilla Spring Data Valkey are shown below.

## Dependency Changes

### Spring Boot

#### Maven

Update your `pom.xml`:

```xml
<!-- Before -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- After -->
<dependency>
    <groupId>io.valkey.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-valkey</artifactId>
    <version>${version}</version>
</dependency>
<dependency>
    <groupId>io.valkey</groupId>
    <artifactId>valkey-glide</artifactId>
    <classifier>${os.detected.classifier}</classifier>
    <version>${version}</version>
</dependency>
```

Valkey GLIDE requires platform-specific native libraries. Add the os-maven-plugin to resolve `${os.detected.classifier}`:

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
</build>
```

Note: You can continue using Lettuce or Jedis if preferred by setting `spring.data.valkey.client-type=lettuce` or `spring.data.valkey.client-type=jedis`.

#### Gradle

Update your `build.gradle`:

```groovy
// Before
implementation 'org.springframework.boot:spring-boot-starter-data-redis'

// After
implementation 'io.valkey.springframework.boot:spring-boot-starter-data-valkey:${version}'
implementation "io.valkey:valkey-glide:${version}:${osdetector.classifier}"
```

Add the osdetector plugin to resolve `${osdetector.classifier}`:

```groovy
plugins {
    id 'com.google.osdetector' version '1.7.3'
}
```

### Vanilla Spring

#### Maven

Update your `pom.xml`:

```xml
<!-- Before -->
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-redis</artifactId>
    <version>${version}</version>
</dependency>

<!-- After -->
<dependency>
    <groupId>io.valkey.springframework.data</groupId>
    <artifactId>spring-data-valkey</artifactId>
    <version>${version}</version>
</dependency>
<dependency>
    <groupId>io.valkey</groupId>
    <artifactId>valkey-glide</artifactId>
    <classifier>${os.detected.classifier}</classifier>
    <version>${version}</version>
</dependency>
```

#### Gradle

Update your `build.gradle`:

```groovy
// Before
implementation 'org.springframework.data:spring-data-redis:${version}'

// After
implementation 'io.valkey.springframework.data:spring-data-valkey:${version}'
implementation "io.valkey:valkey-glide:${version}:${osdetector.classifier}"
```

## Package Name Changes

All packages have been renamed from `org.springframework.data.redis` to `io.valkey.springframework.data.valkey` and Spring Boot Redis packages to their Valkey equivalents.

### Pattern

```
org.springframework.data.redis.*  →  io.valkey.springframework.data.valkey.*
org.springframework.boot.*.redis.*  →  io.valkey.springframework.boot.*.valkey.*
```

### Examples

```java
// Before
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

// After
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
```

## Class Name Changes

Classes containing "Redis" in their name have been renamed to use "Valkey" instead.

### Pattern

```
*Redis* → *Valkey*
```

### Examples

The following is a list of the more common classes, it is not exhaustive.

#### Core Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `RedisTemplate` | `ValkeyTemplate` |
| `StringRedisTemplate` | `StringValkeyTemplate` |
| `ReactiveRedisTemplate` | `ReactiveValkeyTemplate` |
| `RedisConnectionFactory` | `ValkeyConnectionFactory` |
| `RedisConnection` | `ValkeyConnection` |
| `RedisOperations` | `ValkeyOperations` |
| `RedisCallback` | `ValkeyCallback` |
| `RedisSerializer` | `ValkeySerializer` |

#### Connection Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `LettuceConnectionFactory` | `LettuceConnectionFactory` (unchanged) |
| `JedisConnectionFactory` | `JedisConnectionFactory` (unchanged) |
| `RedisStandaloneConfiguration` | `ValkeyStandaloneConfiguration` |
| `RedisClusterConfiguration` | `ValkeyClusterConfiguration` |
| `RedisSentinelConfiguration` | `ValkeySentinelConfiguration` |
| `RedisPassword` | `ValkeyPassword` |
| `RedisNode` | `ValkeyNode` |
| `RedisClusterNode` | `ValkeyClusterNode` |

Note: Jedis and Lettuce are external driver libraries. Their package names (e.g., `redis.clients.jedis`) and class names (e.g., `RedisURI`, `RedisClient`) should not be changed during migration.

#### Spring Boot Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `RedisAutoConfiguration` | `ValkeyAutoConfiguration` |
| `RedisReactiveAutoConfiguration` | `ValkeyReactiveAutoConfiguration` |
| `RedisRepositoriesAutoConfiguration` | `ValkeyRepositoriesAutoConfiguration` |
| `RedisHealthIndicator` | `ValkeyHealthIndicator` |
| `RedisReactiveHealthIndicator` | `ValkeyReactiveHealthIndicator` |
| `RedisCacheConfiguration` | `ValkeyCacheConfiguration` |
| `RedisProperties` | `ValkeyProperties` |

#### Repository Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `@EnableRedisRepositories` | `@EnableValkeyRepositories` |
| `RedisRepository` | `ValkeyRepository` |
| `@RedisHash` | `@ValkeyHash` |

#### Cache Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `RedisCacheManager` | `ValkeyCacheManager` |
| `RedisCacheConfiguration` | `ValkeyCacheConfiguration` |
| `RedisCacheWriter` | `ValkeyCacheWriter` |

#### Reactive Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `ReactiveRedisConnection` | `ReactiveValkeyConnection` |
| `ReactiveRedisConnectionFactory` | `ReactiveValkeyConnectionFactory` |
| `ReactiveRedisOperations` | `ReactiveValkeyOperations` |
| `ReactiveRedisTemplate` | `ReactiveValkeyTemplate` |

#### Pub/Sub Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `RedisMessageListenerContainer` | `ValkeyMessageListenerContainer` |
| `MessageListenerAdapter` | `MessageListenerAdapter` (unchanged) |

#### Scripting Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `RedisScript` | `ValkeyScript` |
| `DefaultRedisScript` | `DefaultValkeyScript` |

#### Spring Boot Test Classes

| Spring Data Redis | Spring Data Valkey |
|-------------------|-------------------|
| `RedisTestConfiguration` | `ValkeyTestConfiguration` |
| `@AutoConfigureDataRedis` | `@AutoConfigureDataValkey` |
| `@DataRedisTest` | `@DataValkeyTest` |

#### Migrating to Valkey GLIDE

Spring Data Valkey adds support for [Valkey GLIDE](https://github.com/valkey-io/valkey-glide) as a new high-performance driver. To migrate from Lettuce or Jedis:

| Class | Description |
|-------|-------------|
| `ValkeyGlideConnectionFactory` | Replaces `LettuceConnectionFactory` or `JedisConnectionFactory` |
| `ValkeyGlideConnection` | Connection implementation using GLIDE |
| `ValkeyGlideClientConfiguration` | Configuration for GLIDE client |

## Annotation Changes

```java
// Before
@EnableRedisRepositories
@RedisHash("users")
@Resource(name="redisTemplate")

// After
@EnableValkeyRepositories
@ValkeyHash("users")
@Resource(name="valkeyTemplate")
```

## Configuration Changes

### Simple Configuration

```java
// Before
@Configuration
@EnableRedisRepositories
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}

// After
@Configuration
@EnableValkeyRepositories
public class ValkeyConfig {

    @Bean
    public ValkeyConnectionFactory valkeyConnectionFactory() {
        return new ValkeyGlideConnectionFactory();
    }

    @Bean
    public StringValkeyTemplate valkeyTemplate(ValkeyConnectionFactory factory) {
        return new StringValkeyTemplate(factory);
    }
}
```

### Spring Boot Configuration

The Spring Boot starter provides auto-configuration. Update your `application.properties`:

```properties
# Before
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=your-password

# After
spring.data.valkey.host=localhost
spring.data.valkey.port=6379
spring.data.valkey.password=your-password

# Optional: Choose client (default is GLIDE)
spring.data.valkey.client-type=valkeyglide  # or lettuce, jedis
```

## Testing Changes

### Testcontainers

Update your test configuration to use Valkey containers:

```java
// Before
@SpringBootTest
@Testcontainers
class MyTest {

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
}

// After
@SpringBootTest
@Testcontainers
class MyTest {

    @Container
    @ServiceConnection(name = "redis") // Required for GenericContainer to be detected as a Redis service
    static GenericContainer<?> valkey = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8"))
            .withExposedPorts(6379);

    @Autowired
    private ValkeyTemplate<String, String> valkeyTemplate;
}
```

## Migration Checklist

**Spring Boot:**
- [ ] Update dependencies to Valkey Spring Boot starter
- [ ] Update imports: `org.springframework.data.redis.*` → `io.valkey.springframework.data.valkey.*`
- [ ] Update imports: `org.springframework.boot.autoconfigure.data.redis.*` → `io.valkey.springframework.boot.autoconfigure.data.valkey.*`
- [ ] Rename classes: `*Redis*` → `*Valkey*`
- [ ] Update annotations: `@EnableRedisRepositories` → `@EnableValkeyRepositories`
- [ ] Update bean names: `redisTemplate` → `valkeyTemplate`
- [ ] Update properties: `spring.data.redis.*` → `spring.data.valkey.*`

**Vanilla Spring:**
- [ ] Update dependencies to Spring Data Valkey
- [ ] Update imports: `org.springframework.data.redis.*` → `io.valkey.springframework.data.valkey.*`
- [ ] Rename classes: `*Redis*` → `*Valkey*`
- [ ] Update annotations: `@EnableRedisRepositories` → `@EnableValkeyRepositories`
- [ ] Update bean names: `redisTemplate` → `valkeyTemplate`
- [ ] Update configuration classes

## Automated Migration Script

While updating dependencies and adding new configurations must be done manually, the renaming of packages and classes can be automated with a script. Here's an example using `sed`:

```bash
$ find path/to/project -type f \( -name "*.java" -o -name "*.properties" -o -name "*.yml" -o -name "*.xml" -o -name "*.gradle" \) -exec sed -i \
  `# Packages` \
  -e 's/org\.springframework\.data\.redis\./io.valkey.springframework.data.valkey./g' \
  -e 's/org\.springframework\.boot\.\(.*\)redis\./io.valkey.springframework.boot.\1valkey./g' \
  `# Classes` \
  -e 's/AutoConfigureDataRedis/AutoConfigureDataValkey/g' \
  -e 's/DataRedisTest/DataValkeyTest/g' \
  -e 's/DefaultRedisScript/DefaultValkeyScript/g' \
  -e 's/EnableRedisRepositories/EnableValkeyRepositories/g' \
  -e 's/GenericJacksonJsonRedisSerializer/GenericJacksonJsonValkeySerializer/g' \
  -e 's/Jackson2JsonRedisSerializer/Jackson2JsonValkeySerializer/g' \
  -e 's/ReactiveRedisConnection/ReactiveValkeyConnection/g' \
  -e 's/ReactiveRedisConnectionFactory/ReactiveValkeyConnectionFactory/g' \
  -e 's/ReactiveRedisOperations/ReactiveValkeyOperations/g' \
  -e 's/ReactiveRedisTemplate/ReactiveValkeyTemplate/g' \
  -e 's/ReactiveStringRedisTemplate/ReactiveStringValkeyTemplate/g' \
  -e 's/RedisAutoConfiguration/ValkeyAutoConfiguration/g' \
  -e 's/RedisCache/ValkeyCache/g' \
  -e 's/RedisCacheConfiguration/ValkeyCacheConfiguration/g' \
  -e 's/RedisCacheManager/ValkeyCacheManager/g' \
  -e 's/RedisCacheWriter/ValkeyCacheWriter/g' \
  -e 's/RedisCallback/ValkeyCallback/g' \
  -e 's/RedisClusterConfiguration/ValkeyClusterConfiguration/g' \
  -e 's/RedisClusterNode/ValkeyClusterNode/g' \
  -e 's/RedisConnection/ValkeyConnection/g' \
  -e 's/RedisConnectionFactory/ValkeyConnectionFactory/g' \
  -e 's/RedisConnectionFailureException/ValkeyConnectionFailureException/g' \
  -e 's/RedisGeoCommands/ValkeyGeoCommands/g' \
  -e 's/RedisHash/ValkeyHash/g' \
  -e 's/RedisHealthIndicator/ValkeyHealthIndicator/g' \
  -e 's/RedisMessageListenerContainer/ValkeyMessageListenerContainer/g' \
  -e 's/RedisNode/ValkeyNode/g' \
  -e 's/RedisOperations/ValkeyOperations/g' \
  -e 's/RedisPassword/ValkeyPassword/g' \
  -e 's/RedisProperties/ValkeyProperties/g' \
  -e 's/RedisReactiveAutoConfiguration/ValkeyReactiveAutoConfiguration/g' \
  -e 's/RedisReactiveHealthIndicator/ValkeyReactiveHealthIndicator/g' \
  -e 's/RedisRepository/ValkeyRepository/g' \
  -e 's/RedisRepositoriesAutoConfiguration/ValkeyRepositoriesAutoConfiguration/g' \
  -e 's/RedisScript/ValkeyScript/g' \
  -e 's/RedisSentinelConfiguration/ValkeySentinelConfiguration/g' \
  -e 's/RedisSerializationContext/ValkeySerializationContext/g' \
  -e 's/RedisSerializationContextBuilder/ValkeySerializationContextBuilder/g' \
  -e 's/RedisSerializer/ValkeySerializer/g' \
  -e 's/RedisStandaloneConfiguration/ValkeyStandaloneConfiguration/g' \
  -e 's/RedisSystemException/ValkeySystemException/g' \
  -e 's/RedisTemplate\b/ValkeyTemplate/g' \
  -e 's/RedisTestConfiguration/ValkeyTestConfiguration/g' \
  -e 's/StringRedisSerializer/StringValkeySerializer/g' \
  -e 's/StringRedisTemplate\b/StringValkeyTemplate/g' \
  `# Variables` \
  -e 's/"reactiveRedisTemplate"/"reactiveValkeyTemplate"/g' \
  -e 's/"redisConnectionFactory"/"valkeyConnectionFactory"/g' \
  -e 's/"redisTemplate"/"valkeyTemplate"/g' \
  -e 's/"stringRedisTemplate"/"stringValkeyTemplate"/g' \
  `# Properties` \
  -e 's/spring\.data\.redis\./spring.data.valkey./g' \
  -e 's/spring\.redis\./spring.data.valkey./g' \
  {} \;
```

## Additional Resources

- [Valkey Documentation](https://valkey.io/docs/)
- [Valkey GLIDE](https://github.com/valkey-io/valkey-glide)
- [Spring Data Redis Documentation](https://docs.spring.io/spring-data/redis/reference/)
- [Spring Data Redis](https://github.com/spring-projects/spring-data-redis)
