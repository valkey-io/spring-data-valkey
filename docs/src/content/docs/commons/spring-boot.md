---
title: Spring Boot
description: Spring Boot integration with Spring Data Valkey
---

Spring Boot provides auto-configuration support for Spring Data Valkey through a Spring Boot Starter for Valkey. This starter automatically configures Valkey connections, templates, repositories, and caching with sensible defaults while allowing full customization when needed.

For complete documentation on Spring Boot, see the [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/).

# Spring Boot Starter Data Valkey

The following auto-configuration classes are from the *spring-boot-data-valkey* module:

| Configuration Class | Links |
|---------------------|-------|
| ValkeyAutoConfiguration | [javadoc](https://spring.valkey.io/spring-boot-starter-data-valkey/api/java/io/valkey/springframework/boot/autoconfigure/data/valkey/ValkeyAutoConfiguration.html) |
| ValkeyHealthContributorAutoConfiguration | [javadoc](https://spring.valkey.io/spring-boot-starter-data-valkey/api/java/io/valkey/springframework/boot/actuate/autoconfigure/data/valkey/ValkeyHealthContributorAutoConfiguration.html) |
| ValkeyReactiveAutoConfiguration | [javadoc](https://spring.valkey.io/spring-boot-starter-data-valkey/api/java/io/valkey/springframework/boot/autoconfigure/data/valkey/ValkeyReactiveAutoConfiguration.html) |
| ValkeyReactiveHealthContributorAutoConfiguration | [javadoc](https://spring.valkey.io/spring-boot-starter-data-valkey/api/java/io/valkey/springframework/boot/actuate/autoconfigure/data/valkey/ValkeyReactiveHealthContributorAutoConfiguration.html) |
| ValkeyRepositoriesAutoConfiguration | [javadoc](https://spring.valkey.io/spring-boot-starter-data-valkey/api/java/io/valkey/springframework/boot/autoconfigure/data/valkey/ValkeyRepositoriesAutoConfiguration.html) |
| ValkeyLettuceMetricsAutoConfiguration | [javadoc](https://spring.valkey.io/spring-boot-starter-data-valkey/api/java/io/valkey/springframework/boot/actuate/autoconfigure/metrics/valkey/ValkeyLettuceMetricsAutoConfiguration.html) |

For more information on the Spring Data Valkey Spring Boot Starter, see the [GitHub repository](https://github.com/valkey-io/spring-data-valkey/tree/main/spring-boot-starter-data-valkey).
