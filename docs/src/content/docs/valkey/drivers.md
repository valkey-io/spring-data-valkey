---
title: Drivers
description: Drivers documentation
---

One of the first tasks when using Valkey and Spring is to connect to the store through the IoC container.
To do that, a Java connector (or binding) is required.
No matter the library you choose, you need to use only one set of Spring Data Valkey APIs (which behaves consistently across all connectors).
The `io.valkey.springframework.data.connection` package and its `ValkeyConnection` and `ValkeyConnectionFactory` interfaces for working with and retrieving active connections to Valkey.

## ValkeyConnection and ValkeyConnectionFactory

`ValkeyConnection` provides the core building block for Valkey communication, as it handles the communication with the Valkey backend.
It also automatically translates underlying connecting library exceptions to Spring's consistent [DAO exception hierarchy](https://docs.spring.io/spring-framework/reference/data-access.html#dao-exceptions) so that you can switch connectors without any code changes, as the operation semantics remain the same.

:::note
For the corner cases where the native library API is required, `ValkeyConnection` provides a dedicated method (`getNativeConnection`) that returns the raw, underlying object used for communication.
:::

Active `ValkeyConnection` objects are created through `ValkeyConnectionFactory`.
In addition, the factory acts as `PersistenceExceptionTranslator` objects, meaning that, once declared, they let you do transparent exception translation.
For example, you can do exception translation through the use of the `@Repository` annotation and AOP.
For more information, see the dedicated [section](https://docs.spring.io/spring-framework/reference/data-access.html#orm-exception-translation) in the Spring Framework documentation.

:::note
`ValkeyConnection` classes are **not** Thread-safe. While the underlying native connection, such as Lettuce's `StatefulRedisConnection`, may be Thread-safe, Spring Data Valkey's `LettuceConnection` class itself is not. Therefore, you should **not** share instances of a `ValkeyConnection` across multiple Threads. This is especially true for transactional, or blocking Valkey operations and commands, such as `BLPOP`. In transactional and pipelining operations, for instance, `ValkeyConnection` holds onto unguarded mutable state to complete the operation correctly, thereby making it unsafe to use with multiple Threads. This is by design.
:::

:::tip
If you need to share (stateful) Valkey resources, like connections, across multiple Threads, for performance reasons or otherwise, then you should acquire the native connection and use the Valkey client library (driver) API directly. Alternatively, you can use the `ValkeyTemplate`, which acquires and manages connections for operations (and Valkey commands) in a Thread-safe manner. See [documentation](/valkey/template) on `ValkeyTemplate` for more details.
:::

:::note
Depending on the underlying configuration, the factory can return a new connection or an existing connection (when a pool or shared native connection is used).
:::

The easiest way to work with a `ValkeyConnectionFactory` is to configure the appropriate connector through the IoC container and inject it into the using class.

Unfortunately, currently, not all connectors support all Valkey features.
When invoking a method on the Connection API that is unsupported by the underlying library, an `UnsupportedOperationException` is thrown.
The following overview explains features that are supported by the individual Valkey connectors:

*Table 1. Feature Availability across Valkey Connectors*

| Supported Feature | Lettuce | Jedis |
|-------------------|---------|-------|
| Standalone Connections | X | X |
| [Master/Replica Connections](/valkey/connection-modes#write-to-master-read-from-replica) | X | X |
| [Valkey Sentinel](/valkey/connection-modes#valkey-sentinel) | Master Lookup, Sentinel Authentication, Replica Reads | Master Lookup |
| [Valkey Cluster](/valkey/cluster) | Cluster Connections, Cluster Node Connections, Replica Reads | Cluster Connections, Cluster Node Connections |
| Transport Channels | TCP, OS-native TCP (epoll, kqueue), Unix Domain Sockets | TCP |
| Connection Pooling | X (using `commons-pool2`) | X (using `commons-pool2`) |
| Other Connection Features | Singleton-connection sharing for non-blocking commands | Pipelining and Transactions mutually exclusive. Cannot use server/connection commands in pipeline/transactions. |
| SSL Support | X | X |
| [Pub/Sub](/valkey/pubsub) | X | X |
| [Pipelining](/valkey/pipelining) | X | X (Pipelining and Transactions mutually exclusive) |
| [Transactions](/valkey/transactions) | X | X (Pipelining and Transactions mutually exclusive) |
| Datatype support | Key, String, List, Set, Sorted Set, Hash, Server, Stream, Scripting, Geo, HyperLogLog | Key, String, List, Set, Sorted Set, Hash, Server, Stream, Scripting, Geo, HyperLogLog |
| Reactive (non-blocking) API | X | |

## Configuring the Lettuce Connector

[Lettuce](https://github.com/lettuce-io/lettuce-core) is a [Netty](https://netty.io/)-based open-source connector supported by Spring Data Valkey through the `io.valkey.springframework.data.connection.lettuce` package.

*Add the following to the pom.xml files `dependencies` element:*

```xml
<dependencies>

  <!-- other dependency elements omitted -->

  <dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.3.2.RELEASE</version>
  </dependency>

</dependencies>
```

The following example shows how to create a new Lettuce connection factory:

```java
@Configuration
class AppConfig {

  @Bean
  public LettuceConnectionFactory valkeyConnectionFactory() {

    return new LettuceConnectionFactory(new ValkeyStandaloneConfiguration("server", 6379));
  }
}
```

There are also a few Lettuce-specific connection parameters that can be tweaked.
By default, all `LettuceConnection` instances created by the `LettuceConnectionFactory` share the same thread-safe native connection for all non-blocking and non-transactional operations.
To use a dedicated connection each time, set `shareNativeConnection` to `false`. `LettuceConnectionFactory` can also be configured to use a `LettucePool` for pooling blocking and transactional connections or all connections if `shareNativeConnection` is set to `false`.

The following example shows a more sophisticated configuration, including SSL and timeouts, that uses `LettuceClientConfigurationBuilder`:

```java
@Bean
public LettuceConnectionFactory lettuceConnectionFactory() {

  LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
    .useSsl().and()
    .commandTimeout(Duration.ofSeconds(2))
    .shutdownTimeout(Duration.ZERO)
    .build();

  return new LettuceConnectionFactory(new ValkeyStandaloneConfiguration("localhost", 6379), clientConfig);
}
```

For more detailed client configuration tweaks, see `io.valkey.springframework.data.connection.lettuce.LettuceClientConfiguration`.

Lettuce integrates with Netty's [native transports](https://netty.io/wiki/native-transports.html), letting you use Unix domain sockets to communicate with Valkey.
Make sure to include the appropriate native transport dependencies that match your runtime environment.
The following example shows how to create a Lettuce Connection factory for a Unix domain socket at `/var/run/valkey.sock`:

```java
@Configuration
class AppConfig {

  @Bean
  public LettuceConnectionFactory valkeyConnectionFactory() {

    return new LettuceConnectionFactory(new ValkeySocketConfiguration("/var/run/valkey.sock"));
  }
}
```

:::note
Netty currently supports the epoll (Linux) and kqueue (BSD/macOS) interfaces for OS-native transport.
:::

## Configuring the Jedis Connector

[Jedis](https://github.com/valkey/jedis) is a community-driven connector supported by the Spring Data Valkey module through the `io.valkey.springframework.data.connection.jedis` package.

*Add the following to the pom.xml files `dependencies` element:*

```xml
<dependencies>

  <!-- other dependency elements omitted -->

  <dependency>
    <groupId>valkey.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>5.1.2</version>
  </dependency>

</dependencies>
```

In its simplest form, the Jedis configuration looks as follow:

```java
@Configuration
class AppConfig {

  @Bean
  public JedisConnectionFactory valkeyConnectionFactory() {
    return new JedisConnectionFactory();
  }
}
```

For production use, however, you might want to tweak settings such as the host or password, as shown in the following example:

```java
@Configuration
class ValkeyConfiguration {

  @Bean
  public JedisConnectionFactory valkeyConnectionFactory() {

    ValkeyStandaloneConfiguration config = new ValkeyStandaloneConfiguration("server", 6379);
    return new JedisConnectionFactory(config);
  }
}
```
