---
title: Keyspaces
description: Keyspaces documentation
---

Keyspaces define prefixes used to create the actual key for the Valkey Hash.
By default, the prefix is set to `getClass().getName()`.
You can alter this default by setting `@ValkeyHash` on the aggregate root level or by setting up a programmatic configuration.
However, the annotated keyspace supersedes any other configuration.

The following example shows how to set the keyspace configuration with the `@EnableValkeyRepositories` annotation:

*Example 1. Keyspace Setup via `@EnableValkeyRepositories`*

```java
@Configuration
@EnableValkeyRepositories(keyspaceConfiguration = MyKeyspaceConfiguration.class)
public class ApplicationConfig {

  //... ValkeyConnectionFactory and ValkeyTemplate Bean definitions omitted

  public static class MyKeyspaceConfiguration extends KeyspaceConfiguration {

    @Override
    protected Iterable<KeyspaceSettings> initialConfiguration() {
      return Collections.singleton(new KeyspaceSettings(Person.class, "people"));
    }
  }
}
```

The following example shows how to programmatically set the keyspace:

*Example 2. Programmatic Keyspace setup*

```java
@Configuration
@EnableValkeyRepositories
public class ApplicationConfig {

  //... ValkeyConnectionFactory and ValkeyTemplate Bean definitions omitted

  @Bean
  public ValkeyMappingContext keyValueMappingContext() {
    return new ValkeyMappingContext(
      new MappingConfiguration(new IndexConfiguration(), new MyKeyspaceConfiguration()));
  }

  public static class MyKeyspaceConfiguration extends KeyspaceConfiguration {

    @Override
    protected Iterable<KeyspaceSettings> initialConfiguration() {
      return Collections.singleton(new KeyspaceSettings(Person.class, "people"));
    }
  }
}
```
