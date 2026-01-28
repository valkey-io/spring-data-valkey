---
title: Migrating Spring Data
description: Instructions for migrating from Spring Data Redis to Spring Data Valkey
---

Spring Data Valkey is a fork of Spring Data Redis 3.5.1, created to provide first-class support for Valkey. To migrate from Spring Data Redis to Spring Data Valkey, see the comprehensive [Migration Guide](https://github.com/valkey-io/spring-data-valkey/blob/main/MIGRATION.md) in the Spring Data Valkey repository.

The migration guide covers:

- **Dependency Changes** - Updated Maven/Gradle dependencies for Spring Boot and vanilla Spring
- **Package Name Changes** - Spring Data packages: `org.springframework.data.redis.*` → `io.valkey.springframework.data.*`, Spring Boot packages: `org.springframework.boot.*.redis.*` → `io.valkey.springframework.boot.*.valkey.*`
- **Class Name Changes** - Redis classes renamed to Valkey equivalents (`RedisTemplate` → `ValkeyTemplate`, `@EnableRedisRepositories` → `@EnableValkeyRepositories`, etc.)
- **Configuration Changes** - Updated property names and configuration classes
- **Testing Changes** - Testcontainers and test configuration updates
- **Valkey GLIDE Integration** - New high-performance driver support alongside Lettuce and Jedis
