---
title: Migration Guides
description: Migration steps, deprecations, and removals
---

This section contains details about migration steps, deprecations, and removals.

## Upgrading from 2.x to 3.x

### Re-/moved Types

| Type | Replacement |
|------|-------------|
| o.s.d.valkey.Version | o.s.d.util.Version |
| o.s.d.valkey.VersionParser | - |
| o.s.d.valkey.connection.ValkeyZSetCommands.Aggregate | o.s.d.valkey.connection.zset.Aggregate |
| o.s.d.valkey.connection.ValkeyZSetCommands.Tuple | o.s.d.valkey.connection.zset.Tuple |
| o.s.d.valkey.connection.ValkeyZSetCommands.Weights | o.s.d.valkey.connection.zset.Weights |
| o.s.d.valkey.connection.ValkeyZSetCommands.Range | o.s.d.domain.Range |
| o.s.d.valkey.connection.ValkeyZSetCommands.Limit | o.s.d.valkey.connection.Limit.java |
| o.s.d.valkey.connection.jedis.JedisUtils | - |
| o.s.d.valkey.connection.jedis.JedisVersionUtil | - |
| o.s.d.valkey.core.convert.CustomConversions | o.s.d.convert.CustomConversions |

### Changed Methods and Types

*Table 1. Core*

| Type | Method | Replacement |
|------|--------|-------------|
| o.s.d.valkey.core.Cursor | open | - |
| o.s.d.valkey.core.ValkeyTemplate | execute | doWithKeys |
| o.s.d.valkey.stream.StreamMessageListenerContainer | isAutoAck | isAutoAcknowledge |
| o.s.d.valkey.stream.StreamMessageListenerContainer | autoAck | autoAcknowledge |

*Table 2. Valkey Connection*

| Type | Method | Replacement |
|------|--------|-------------|
| o.s.d.valkey.connection.ClusterCommandExecutionFailureException | getCauses | getSuppressed |
| o.s.d.valkey.connection.ValkeyConnection | bgWriteAof | bgReWriteAof |
| o.s.d.valkey.connection.ValkeyConnection | slaveOf | replicaOf |
| o.s.d.valkey.connection.ValkeyConnection | slaveOfNoOne | replicaOfNoOne |
| o.s.d.valkey.connection.ReactiveClusterCommands | clusterGetSlaves | clusterGetReplicas |
| o.s.d.valkey.connection.ReactiveClusterCommands | clusterGetMasterSlaveMap | clusterGetMasterReplicaMap |
| o.s.d.valkey.connection.ReactiveKeyCommands | getNewName | getNewKey |
| o.s.d.valkey.connection.ValkeyClusterNode.Flag | SLAVE | REPLICA |
| o.s.d.valkey.connection.ValkeyClusterNode.Builder | slaveOf | replicaOf |
| o.s.d.valkey.connection.ValkeyNode | isSlave | isReplica |
| o.s.d.valkey.connection.ValkeySentinelCommands | slaves | replicas |
| o.s.d.valkey.connection.ValkeyServer | getNumberSlaves | getNumberReplicas |
| o.s.d.valkey.connection.ValkeyServerCommands | slaveOf | replicaOf |
| o.s.d.valkey.core.ClusterOperations | getSlaves | getReplicas |
| o.s.d.valkey.core.ValkeyOperations | slaveOf | replicaOf |

*Table 3. Valkey Operations*

| Type | Method | Replacement |
|------|--------|-------------|
| o.s.d.valkey.core.GeoOperations & BoundGeoOperations | geoAdd | add |
| o.s.d.valkey.core.GeoOperations & BoundGeoOperations | geoDist | distance |
| o.s.d.valkey.core.GeoOperations & BoundGeoOperations | geoHash | hash |
| o.s.d.valkey.core.GeoOperations & BoundGeoOperations | geoPos | position |
| o.s.d.valkey.core.GeoOperations & BoundGeoOperations | geoRadius | radius |
| o.s.d.valkey.core.GeoOperations & BoundGeoOperations | geoRadiusByMember | radius |
| o.s.d.valkey.core.GeoOperations & BoundGeoOperations | geoRemove | remove |

*Table 4. Valkey Cache*

| Type | Method | Replacement |
|------|--------|-------------|
| o.s.d.valkey.cache.ValkeyCacheConfiguration | prefixKeysWith | prefixCacheNameWith |
| o.s.d.valkey.cache.ValkeyCacheConfiguration | getKeyPrefix | getKeyPrefixFor |

### Jedis

Please read the Jedis [upgrading guide](https://github.com/valkey/jedis/blob/v4.0.0/docs/3to4.md) which covers important driver changes.

*Table 5. Jedis Valkey Connection*

| Type | Method | Replacement |
|------|--------|-------------|
| o.s.d.valkey.connection.jedis.JedisConnectionFactory | getShardInfo | _can be obtained via JedisClientConfiguration_ |
| o.s.d.valkey.connection.jedis.JedisConnectionFactory | setShardInfo | _can be set via JedisClientConfiguration_ |
| o.s.d.valkey.connection.jedis.JedisConnectionFactory | createCluster | _now requires a `Connection` instead of `Jedis` instance_ |
| o.s.d.valkey.connection.jedis.JedisConverters | | has package visibility now |
| o.s.d.valkey.connection.jedis.JedisConverters | tuplesToTuples | - |
| o.s.d.valkey.connection.jedis.JedisConverters | stringListToByteList | - |
| o.s.d.valkey.connection.jedis.JedisConverters | stringSetToByteSet | - |
| o.s.d.valkey.connection.jedis.JedisConverters | stringMapToByteMap | - |
| o.s.d.valkey.connection.jedis.JedisConverters | tupleSetToTupleSet | - |
| o.s.d.valkey.connection.jedis.JedisConverters | toTupleSet | - |
| o.s.d.valkey.connection.jedis.JedisConverters | toDataAccessException | o.s.d.valkey.connection.jedis.JedisExceptionConverter#convert |

#### Transactions / Pipelining

Pipelining and Transactions are now mutually exclusive.
The usage of server or connection commands in pipeline/transactions mode is no longer possible.

### Lettuce

#### Lettuce Pool

`LettucePool` and its implementation `DefaultLettucePool` have been removed without replacement.
Please refer to the [driver documentation](https://lettuce.io/core/release/reference/index.html#_connection_pooling) for driver native pooling capabilities.
Methods accepting pooling parameters have been updated.
This effects methods on `LettuceConnectionFactory` and `LettuceConnection`.

#### Lettuce Authentication

`AuthenticatingRedisClient` has been removed without replacement.
Please refer to the [driver documentation](https://lettuce.io/core/release/reference/index.html#basic.redisuri) for `RedisURI` to set authentication data.


