---
title: Redis Repositories Overview
description: Redis Repositories with Spring Data
---

This chapter explains the basic foundations of Spring Data repositories and Redis specifics.
Before continuing to the Redis specifics, make sure you have a sound understanding of the basic concepts.

The goal of the Spring Data repository abstraction is to significantly reduce the amount of boilerplate code required to implement data access layers for various persistence stores.

Working with Redis Repositories lets you seamlessly convert and store domain objects in Redis Hashes, apply custom mapping strategies, and use secondary indexes.

:::note[Important]
Redis Repositories require at least Redis Server version 2.8.0 and do not work with transactions. Make sure to use a `RedisTemplate` with [disabled transaction support](/redis/transactions#tx.spring).
:::
