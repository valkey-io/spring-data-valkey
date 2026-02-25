---
title: Connection Modes
description: Connection Modes documentation
---

Valkey can be operated in various setups.
Each mode of operation requires specific configuration that is explained in the following sections.

## Valkey Standalone

The easiest way to get started is by using Valkey Standalone with a single Valkey server,

Configure `io.valkey.springframework.data.connection.valkeyglide.ValkeyGlideConnectionFactory`, `io.valkey.springframework.data.connection.lettuce.LettuceConnectionFactory` or `io.valkey.springframework.data.connection.jedis.JedisConnectionFactory`, as shown in the following example:

```java
@Configuration
class ValkeyStandaloneConfiguration {

  /**
   * Valkey GLIDE
   */
  @Bean
  public ValkeyConnectionFactory valkeyGlideConnectionFactory() {
    return new ValkeyGlideConnectionFactory(new ValkeyStandaloneConfiguration("server", 6379));
  }

  /**
   * Lettuce
   */
  @Bean
  public ValkeyConnectionFactory lettuceConnectionFactory() {
    return new LettuceConnectionFactory(new ValkeyStandaloneConfiguration("server", 6379));
  }

  /**
   * Jedis
   */
  @Bean
  public ValkeyConnectionFactory jedisConnectionFactory() {
    return new JedisConnectionFactory(new ValkeyStandaloneConfiguration("server", 6379));
  }
}
```

## Write to Master, Read from Replica

The Valkey Master/Replica setup -- without automatic failover (for automatic failover see: [Sentinel](/valkey/connection-modes#valkey-sentinel)) -- not only allows data to be safely stored at more nodes.
It also allows, by using [Valkey GLIDE](/valkey/drivers#configuring-the-valkey-glide-connector), reading data from replicas while pushing writes to the master.
You can set the read/write strategy to be used by using `ValkeyGlideClientConfiguration`, as shown in the following example:

```java
@Configuration
class WriteToMasterReadFromReplicaConfiguration {

  @Bean
  public ValkeyGlideConnectionFactory valkeyConnectionFactory() {

    ValkeyGlideClientConfiguration clientConfig = ValkeyGlideClientConfiguration.builder()
      .readFrom(glide.api.models.configuration.ReadFrom.PREFER_REPLICA)
      .build();

    ValkeyStandaloneConfiguration serverConfig = new ValkeyStandaloneConfiguration("server", 6379);

    return new ValkeyGlideConnectionFactory(serverConfig, clientConfig);
  }
}
```

:::tip
For environments reporting non-public addresses through the `INFO` command (for example, when using AWS), use `io.valkey.springframework.data.connection.ValkeyStaticMasterReplicaConfiguration` instead of `io.valkey.springframework.data.connection.ValkeyStandaloneConfiguration`. Please note that `ValkeyStaticMasterReplicaConfiguration` does not support Pub/Sub because of missing Pub/Sub message propagation across individual servers.
:::

## Valkey Sentinel

For dealing with high-availability Valkey, Spring Data Valkey has support for [Valkey Sentinel](https://valkey.io/topics/sentinel), using `io.valkey.springframework.data.connection.ValkeySentinelConfiguration`, as shown in the following example:

:::note[Driver Support]
Valkey Sentinel is currently supported by Lettuce and Jedis drivers. Valkey GLIDE support for Sentinel is planned for a future release.
:::

```java
/**
 * Lettuce
 */
@Bean
public ValkeyConnectionFactory lettuceConnectionFactory() {
  ValkeySentinelConfiguration sentinelConfig = new ValkeySentinelConfiguration()
  .master("mymaster")
  .sentinel("127.0.0.1", 26379)
  .sentinel("127.0.0.1", 26380);
  return new LettuceConnectionFactory(sentinelConfig);
}

/**
 * Jedis
 */
@Bean
public ValkeyConnectionFactory jedisConnectionFactory() {
  ValkeySentinelConfiguration sentinelConfig = new ValkeySentinelConfiguration()
  .master("mymaster")
  .sentinel("127.0.0.1", 26379)
  .sentinel("127.0.0.1", 26380);
  return new JedisConnectionFactory(sentinelConfig);
}
```

:::tip
`ValkeySentinelConfiguration` can also be defined through `ValkeySentinelConfiguration.of(PropertySource)`, which lets you pick up the following properties:

*Configuration Properties*

* `spring.valkey.sentinel.master`: name of the master node.
* `spring.valkey.sentinel.nodes`: Comma delimited list of host:port pairs.
* `spring.valkey.sentinel.username`: The username to apply when authenticating with Valkey Sentinel (requires Valkey 6)
* `spring.valkey.sentinel.password`: The password to apply when authenticating with Valkey Sentinel
* `spring.valkey.sentinel.dataNode.username`: The username to apply when authenticating with Valkey Data Node
* `spring.valkey.sentinel.dataNode.password`: The password to apply when authenticating with Valkey Data Node
* `spring.valkey.sentinel.dataNode.database`: The database index to apply when authenticating with Valkey Data Node
:::

Sometimes, direct interaction with one of the Sentinels is required. Using `ValkeyConnectionFactory.getSentinelConnection()` or `ValkeyConnection.getSentinelCommands()` gives you access to the first active Sentinel configured.

## Valkey Cluster

[Cluster support](/valkey/cluster) is based on the same building blocks as non-clustered communication. `io.valkey.springframework.data.connection.ValkeyClusterConnection`, an extension to `ValkeyConnection`, handles the communication with the Valkey Cluster and translates errors into the Spring DAO exception hierarchy.
`ValkeyClusterConnection` instances are created with the `ValkeyConnectionFactory`, which has to be set up with the associated `io.valkey.springframework.data.connection.ValkeyClusterConfiguration`, as shown in the following example:

*Example 1. Sample ValkeyConnectionFactory Configuration for Valkey Cluster*

```java
@Component
@ConfigurationProperties(prefix = "spring.valkey.cluster")
public class ClusterConfigurationProperties {

    /*
     * spring.valkey.cluster.nodes[0] = 127.0.0.1:7379
     * spring.valkey.cluster.nodes[1] = 127.0.0.1:7380
     * ...
     */
    List<String> nodes;

    /**
     * Get initial collection of known cluster nodes in format {@code host:port}.
     *
     * @return
     */
    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }
}

@Configuration
public class AppConfig {

    /**
     * Type safe representation of application.properties
     */
    @Autowired ClusterConfigurationProperties clusterProperties;

    public @Bean ValkeyConnectionFactory valkeyGlideConnectionFactory() {

        return new ValkeyGlideConnectionFactory(
            new ValkeyClusterConfiguration(clusterProperties.getNodes()));
    }

    public @Bean ValkeyConnectionFactory lettuceConnectionFactory() {

        return new LettuceConnectionFactory(
            new ValkeyClusterConfiguration(clusterProperties.getNodes()));
    }
}
```

:::tip
`ValkeyClusterConfiguration` can also be defined through `ValkeyClusterConfiguration.of(PropertySource)`, which lets you pick up the following properties:

*Configuration Properties*

* `spring.valkey.cluster.nodes`: Comma-delimited list of host:port pairs.
* `spring.valkey.cluster.max-redirects`: Number of allowed cluster redirections.
:::

:::note
The initial configuration points driver libraries to an initial set of cluster nodes. Changes resulting from live cluster reconfiguration are kept only in the native driver and are not written back to the configuration.
:::
