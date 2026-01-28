---
title: Valkey Cache
description: Valkey Cache documentation
---

Spring Data Valkey provides an implementation of Spring Framework's [Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration.html#cache) in the `io.valkey.springframework.data.cache` package.
To use Valkey as a backing implementation, add `io.valkey.springframework.data.cache.ValkeyCacheManager` to your configuration, as follows:

```java
@Bean
public ValkeyCacheManager cacheManager(ValkeyConnectionFactory connectionFactory) {
    return ValkeyCacheManager.create(connectionFactory);
}
```

`ValkeyCacheManager` behavior can be configured with `io.valkey.springframework.data.cache.ValkeyCacheManager$ValkeyCacheManagerBuilder`, letting you set the default `io.valkey.springframework.data.cache.ValkeyCacheManager`, transaction behavior, and predefined caches.

```java
ValkeyCacheManager cacheManager = ValkeyCacheManager.builder(connectionFactory)
    .cacheDefaults(ValkeyCacheConfiguration.defaultCacheConfig())
    .transactionAware()
    .withInitialCacheConfigurations(Collections.singletonMap("predefined",
        ValkeyCacheConfiguration.defaultCacheConfig().disableCachingNullValues()))
    .build();
```

As shown in the preceding example, `ValkeyCacheManager` allows custom configuration on a per-cache basis.

The behavior of `io.valkey.springframework.data.cache.ValkeyCache` created by `io.valkey.springframework.data.cache.ValkeyCacheManager` is defined with `ValkeyCacheConfiguration`.
The configuration lets you set key expiration times, prefixes, and `ValkeySerializer` implementations for converting to and from the binary storage format, as shown in the following example:

```java
ValkeyCacheConfiguration cacheConfiguration = ValkeyCacheConfiguration.defaultCacheConfig()
    .entryTtl(Duration.ofSeconds(1))
    .disableCachingNullValues();
```

`io.valkey.springframework.data.cache.ValkeyCacheManager` defaults to a lock-free `io.valkey.springframework.data.cache.ValkeyCacheWriter` for reading and writing binary values.
Lock-free caching improves throughput.
The lack of entry locking can lead to overlapping, non-atomic commands for the `Cache` `putIfAbsent` and `clean` operations, as those require multiple commands to be sent to Valkey.
The locking counterpart prevents command overlap by setting an explicit lock key and checking against presence of this key, which leads to additional requests and potential command wait times.

Locking applies on the *cache level*, not per *cache entry*.

It is possible to opt in to the locking behavior as follows:

```java
ValkeyCacheManager cacheManager = ValkeyCacheManager
    .builder(ValkeyCacheWriter.lockingValkeyCacheWriter(connectionFactory))
    .cacheDefaults(ValkeyCacheConfiguration.defaultCacheConfig())
    ...
```

By default, any `key` for a cache entry gets prefixed with the actual cache name followed by two colons.
This behavior can be changed to a static as well as a computed prefix.

The following example shows how to set a static prefix:

```java
// static key prefix
ValkeyCacheConfiguration.defaultCacheConfig().prefixCacheNameWith("(͡° ᴥ ͡°)");
```

The following example shows how to set a computed prefix:

```java
// computed key prefix
ValkeyCacheConfiguration.defaultCacheConfig()
    .computePrefixWith(cacheName -> "¯\_(ツ)_/¯" + cacheName);
```

The cache implementation defaults to use `KEYS` and `DEL` to clear the cache. `KEYS` can cause performance issues with large keyspaces.
Therefore, the default `ValkeyCacheWriter` can be created with a `BatchStrategy` to switch to a `SCAN`-based batch strategy.
The `SCAN` strategy requires a batch size to avoid excessive Valkey command round trips:

```java
ValkeyCacheManager cacheManager = ValkeyCacheManager
    .builder(ValkeyCacheWriter.nonLockingValkeyCacheWriter(connectionFactory, BatchStrategies.scan(1000)))
    .cacheDefaults(ValkeyCacheConfiguration.defaultCacheConfig())
    ...
```

:::note
The `KEYS` batch strategy is fully supported using any driver and Valkey operation mode (Standalone, Clustered).
:::

`SCAN` is fully supported when using the Lettuce driver.
Jedis supports `SCAN` only in non-clustered modes.
Valkey GLIDE supports `SCAN` in standalone mode, with cluster mode support planned for a future release.

The following table lists the default settings for `ValkeyCacheManager`:

*Table 1. `ValkeyCacheManager` defaults*

| Setting | Value |
|---------|-------|
| Cache Writer | Non-locking, `KEYS` batch strategy |
| Cache Configuration | `ValkeyCacheConfiguration#defaultConfiguration` |
| Initial Caches | None |
| Transaction Aware | No |

The following table lists the default settings for `ValkeyCacheConfiguration`:

*Table 2. ValkeyCacheConfiguration defaults*

| Setting | Value |
|---------|-------|
| Key Expiration | None |
| Cache `null` | Yes |
| Prefix Keys | Yes |
| Default Prefix | The actual cache name |
| Key Serializer | `StringValkeySerializer` |
| Value Serializer | `JdkSerializationValkeySerializer` |
| Conversion Service | `DefaultFormattingConversionService` with default cache key converters |

:::note
By default `ValkeyCache`, statistics are disabled.
:::

Use `ValkeyCacheManagerBuilder.enableStatistics()` to collect local _hits_ and _misses_ through  `ValkeyCache#getStatistics()`, returning a snapshot of the collected data.

## Valkey Cache Expiration

The implementation of time-to-idle (TTI) as well as time-to-live (TTL) varies in definition and behavior even across different data stores.

In general:

* _time-to-live_ (TTL) _expiration_ - TTL is only set and reset by a create or update data access operation.
As long as the entry is written before the TTL expiration timeout, including on creation, an entry's timeout will reset to the configured duration of the TTL expiration timeout.
For example, if the TTL expiration timeout is set to 5 minutes, then the timeout will be set to 5 minutes on entry creation and reset to 5 minutes anytime the entry is updated thereafter and before the 5-minute interval expires.
If no update occurs within 5 minutes, even if the entry was read several times, or even just read once during the 5-minute interval, the entry will still expire.
The entry must be written to prevent the entry from expiring when declaring a TTL expiration policy.

* _time-to-idle_ (TTI) _expiration_ - TTI is reset anytime the entry is also read as well as for entry updates, and is effectively and extension to the TTL expiration policy.

:::note
Some data stores expire an entry when TTL is configured no matter what type of data access operation occurs on the entry (reads, writes, or otherwise).
After the set, configured TTL expiration timeout, the entry is evicted from the data store regardless.
Eviction actions (for example: destroy, invalidate, overflow-to-disk (for persistent stores), etc.) are data store specific.
:::

### Time-To-Live (TTL) Expiration

Spring Data Valkey's `Cache` implementation supports _time-to-live_ (TTL) expiration on cache entries.
Users can either configure the TTL expiration timeout with a fixed `Duration` or a dynamically computed `Duration` per cache entry by supplying an implementation of the new `ValkeyCacheWriter.TtlFunction` interface.

:::tip
The `ValkeyCacheWriter.TtlFunction` interface was introduced in Spring Data Valkey `3.2.0`.
:::

If all cache entries should expire after a set duration of time, then simply configure a TTL expiration timeout with a fixed `Duration`, as follows:

```java
ValkeyCacheConfiguration fiveMinuteTtlExpirationDefaults =
    ValkeyCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5));
```

However, if the TTL expiration timeout should vary by cache entry, then you must provide a custom implementation of the `ValkeyCacheWriter.TtlFunction` interface:

```java
enum MyCustomTtlFunction implements TtlFunction {

    INSTANCE;

    @Override
    public Duration getTimeToLive(Object key, @Nullable Object value) {
        // compute a TTL expiration timeout (Duration) based on the cache entry key and/or value
    }
}
```

:::note
Under-the-hood, a fixed `Duration` TTL expiration is wrapped in a `TtlFunction` implementation returning the provided `Duration`.
:::

Then, you can either configure the fixed `Duration` or the dynamic, per-cache entry `Duration` TTL expiration on a global basis using:

*Global fixed Duration TTL expiration timeout*

```java
ValkeyCacheManager cacheManager = ValkeyCacheManager.builder(valkeyConnectionFactory)
    .cacheDefaults(fiveMinuteTtlExpirationDefaults)
    .build();
```

Or, alternatively:

*Global, dynamically computed per-cache entry Duration TTL expiration timeout*

```java
ValkeyCacheConfiguration defaults = ValkeyCacheConfiguration.defaultCacheConfig()
        .entryTtl(MyCustomTtlFunction.INSTANCE);

ValkeyCacheManager cacheManager = ValkeyCacheManager.builder(valkeyConnectionFactory)
    .cacheDefaults(defaults)
    .build();
```

Of course, you can combine both global and per-cache configuration using:

*Global fixed Duration TTL expiration timeout*

```java
ValkeyCacheConfiguration predefined = ValkeyCacheConfiguration.defaultCacheConfig()
                                         .entryTtl(MyCustomTtlFunction.INSTANCE);

Map<String, ValkeyCacheConfiguration> initialCaches = Collections.singletonMap("predefined", predefined);

ValkeyCacheManager cacheManager = ValkeyCacheManager.builder(valkeyConnectionFactory)
    .cacheDefaults(fiveMinuteTtlExpirationDefaults)
    .withInitialCacheConfigurations(initialCaches)
    .build();
```

### Time-To-Idle (TTI) Expiration

Valkey itself does not support the concept of true, time-to-idle (TTI) expiration.
Still, using Spring Data Valkey's Cache implementation, it is possible to achieve time-to-idle (TTI) expiration-like behavior.

The configuration of TTI in Spring Data Valkey's Cache implementation must be explicitly enabled, that is, is opt-in.
Additionally, you must also provide TTL configuration using either a fixed `Duration` or a custom implementation of the `TtlFunction` interface as described above in [Valkey Cache Expiration](#valkey-cache-expiration).

For example:

```java
@Configuration
@EnableCaching
class ValkeyConfiguration {

    @Bean
    ValkeyConnectionFactory valkeyConnectionFactory() {
        // ...
    }

    @Bean
    ValkeyCacheManager cacheManager(ValkeyConnectionFactory connectionFactory) {

        ValkeyCacheConfiguration defaults = ValkeyCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .enableTimeToIdle();

        return ValkeyCacheManager.builder(connectionFactory)
            .cacheDefaults(defaults)
            .build();
    }
}
```

Because Valkey servers do not implement a proper notion of TTI, then TTI can only be achieved with Valkey commands accepting expiration options.
In Valkey, the "expiration" is technically a time-to-live (TTL) policy.
However, TTL expiration can be passed when reading the value of a key thereby effectively resetting the TTL expiration timeout, as is now the case in Spring Data Valkey's `Cache.get(key)` operation.

`ValkeyCache.get(key)` is implemented by calling the Valkey `GETEX` command.

:::danger
The Valkey [`GETEX`](https://valkey.io/commands/getex) command is only available in Valkey version `6.2.0` and later.
Therefore, if you are not using Valkey `6.2.0` or later, then it is not possible to use Spring Data Valkey's TTI expiration.
A command execution exception will be thrown if you enable TTI against an incompatible Valkey (server) version.
No attempt is made to determine if the Valkey server version is correct and supports the `GETEX` command.
:::

:::danger
In order to achieve true time-to-idle (TTI) expiration-like behavior in your Spring Data Valkey application, then an entry must be consistently accessed with (TTL) expiration on every read or write operation.
There are no exceptions to this rule.
If you are mixing and matching different data access patterns across your Spring Data Valkey application (for example: caching, invoking operations using `ValkeyTemplate` and possibly, or especially when using Spring Data Repository CRUD operations), then accessing an entry may not necessarily prevent the entry from expiring if TTL expiration was set.
For example, an entry maybe "put" in (written to) the cache during a `@Cacheable` service method invocation with a TTL expiration (i.e. `SET <expiration options>`) and later read using a Spring Data Valkey Repository before the expiration timeout (using `GET` without expiration options).
A simple `GET` without specifying expiration options will not reset the TTL expiration timeout on an entry.
Therefore, the entry may expire before the next data access operation, even though it was just read.
Since this cannot be enforced in the Valkey server, then it is the responsibility of your application to consistently access an entry when time-to-idle expiration is configured, in and outside of caching, where appropriate.
:::
