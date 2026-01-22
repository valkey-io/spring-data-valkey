---
title: Redis Overview
description: Spring Data Redis support and connectivity
---

One of the key-value stores supported by Spring Data is [Redis](https://redis.io).
To quote the Redis project home page:

> Redis is an advanced key-value store.
> It is similar to memcached but the dataset is not volatile, and values can be strings, exactly like in memcached, but also lists, sets, and ordered sets.
> All this data types can be manipulated with atomic operations to push/pop elements, add/remove elements, perform server side union, intersection, difference between sets, and so forth.
> Redis supports different kind of sorting abilities.

Spring Data Redis provides easy configuration and access to Redis from Spring applications.
It offers both low-level and high-level abstractions for interacting with the store, freeing the user from infrastructural concerns.

Spring Data support for Redis contains a wide range of features:

* [`RedisTemplate` and `ReactiveRedisTemplate` helper class](/redis/template) that increases productivity when performing common Redis operations.
Includes integrated serialization between objects and values.
* Exception translation into Spring's portable Data Access Exception hierarchy.
* Automatic implementation of [Repository interfaces](/repositories), including support for custom query methods.
* Feature-rich [Object Mapping](/redis/redis-repositories/mapping) integrated with Spring's Conversion Service.
* Annotation-based mapping metadata that is extensible to support other metadata formats.
* [Transactions](/redis/transactions) and [Pipelining](/redis/pipelining).
* [Redis Cache](/redis/redis-cache) integration through Spring's Cache abstraction.
* [Redis Pub/Sub Messaging](/redis/pubsub) and [Redis Stream](/redis/redis-streams) Listeners.
* [Redis Collection Implementations](/redis/support-classes) for Java such as `RedisList` or `RedisSet`.

## Why Spring Data Redis?

The Spring Framework is the leading full-stack Java/JEE application framework.
It provides a lightweight container and a non-invasive programming model enabled by the use of dependency injection, AOP, and portable service abstractions.

[NoSQL](https://en.wikipedia.org/wiki/NoSQL) storage systems provide an alternative to classical RDBMS for horizontal scalability and speed.
In terms of implementation, key-value stores represent one of the largest (and oldest) members in the NoSQL space.

The Spring Data Redis (SDR) framework makes it easy to write Spring applications that use the Redis key-value store by eliminating the redundant tasks and boilerplate code required for interacting with the store through Spring's excellent infrastructure support.

## Redis Support High-level View

The Redis support provides several components.For most tasks, the high-level abstractions and support services are the best choice.Note that, at any point, you can move between layers.For example, you can get a low-level connection (or even the native library) to communicate directly with Redis.
