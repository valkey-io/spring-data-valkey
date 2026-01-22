---
title: Time To Live
description: Expirations documentation
---

Objects stored in Redis may be valid only for a certain amount of time.
This is especially useful for persisting short-lived objects in Redis without having to remove them manually when they reach their end of life.
The expiration time in seconds can be set with `@RedisHash(timeToLive=...)` as well as by using `org.springframework.data.redis.core.convert.KeyspaceConfiguration$KeyspaceSettings` (see [Keyspaces](/redis/redis-repositories/keyspaces)).

More flexible expiration times can be set by using the `@TimeToLive` annotation on either a numeric property or a method.
However, do not apply `@TimeToLive` on both a method and a property within the same class.
The following example shows the `@TimeToLive` annotation on a property and on a method:

*Example 1. Expirations*

```java
public class TimeToLiveOnProperty {

  @Id
  private String id;

  @TimeToLive
  private Long expiration;
}

public class TimeToLiveOnMethod {

  @Id
  private String id;

  @TimeToLive
  public long getTimeToLive() {
  	return new Random().nextLong();
  }
}
```

:::note
Annotating a property explicitly with `@TimeToLive` reads back the actual `TTL` or `PTTL` value from Redis. -1 indicates that the object has no associated expiration.
:::

The repository implementation ensures subscription to [Redis keyspace notifications](https://redis.io/topics/notifications) via `org.springframework.data.redis.listener.RedisMessageListenerContainer`.

When the expiration is set to a positive value, the corresponding `EXPIRE` command is run.
In addition to persisting the original, a phantom copy is persisted in Redis and set to expire five minutes after the original one.
This is done to enable the Repository support to publish `org.springframework.data.redis.core.RedisKeyExpiredEvent`, holding the expired value in Spring's `ApplicationEventPublisher` whenever a key expires, even though the original values have already been removed.
Expiry events are received on all connected applications that use Spring Data Redis repositories.

By default, the key expiry listener is disabled when initializing the application.
The startup mode can be adjusted in `@EnableRedisRepositories` or `RedisKeyValueAdapter` to start the listener with the application or upon the first insert of an entity with a TTL.
See `org.springframework.data.redis.core.RedisKeyValueAdapter$EnableKeyspaceEvents` for possible values.

The `RedisKeyExpiredEvent` holds a copy of the expired domain object as well as the key.

:::note
Delaying or disabling the expiry event listener startup impacts `RedisKeyExpiredEvent` publishing.
A disabled event listener does not publish expiry events.
A delayed startup can cause loss of events because of the delayed listener initialization.
:::

:::note
The keyspace notification message listener alters `notify-keyspace-events` settings in Redis, if those are not already set.
Existing settings are not overridden, so you must set up those settings correctly (or leave them empty).
Note that `CONFIG` is disabled on AWS ElastiCache, and enabling the listener leads to an error.
To work around this behavior, set the `keyspaceNotificationsConfigParameter` parameter to an empty string.
This prevents `CONFIG` command usage.
:::

:::note
Redis Pub/Sub messages are not persistent.
If a key expires while the application is down, the expiry event is not processed, which may lead to secondary indexes containing references to the expired object.
:::

:::note
`@EnableKeyspaceEvents(shadowCopy = OFF)` disable storage of phantom copies and reduces data size within Redis. `RedisKeyExpiredEvent` will only contain the `id` of the expired key.
:::
