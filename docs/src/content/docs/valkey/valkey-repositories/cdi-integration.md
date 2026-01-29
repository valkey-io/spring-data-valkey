---
title: CDI Integration
description: CDI Integration documentation
---

Instances of the repository interfaces are usually created by a container, for which Spring is the most natural choice when working with Spring Data.
Spring offers sophisticated support for creating bean instances.
Spring Data Valkey ships with a custom CDI extension that lets you use the repository abstraction in CDI environments.
The extension is part of the JAR, so, to activate it, drop the Spring Data Valkey JAR into your classpath.

You can then set up the infrastructure by implementing a CDI Producer for the `io.valkey.springframework.data.connection.ValkeyConnectionFactory` and `io.valkey.springframework.data.core.ValkeyOperations`, as shown in the following example:

```java
class ValkeyOperationsProducer {
  @Produces
  ValkeyConnectionFactory valkeyConnectionFactory() {

    ValkeyGlideConnectionFactory connectionFactory = new ValkeyGlideConnectionFactory(new ValkeyStandaloneConfiguration());
    connectionFactory.afterPropertiesSet();
	connectionFactory.start();

    return connectionFactory;
  }

  void disposeValkeyConnectionFactory(@Disposes ValkeyConnectionFactory valkeyConnectionFactory) throws Exception {

    if (valkeyConnectionFactory instanceof DisposableBean) {
      ((DisposableBean) valkeyConnectionFactory).destroy();
    }
  }

  @Produces
  @ApplicationScoped
  ValkeyOperations<byte[], byte[]> valkeyOperationsProducer(ValkeyConnectionFactory valkeyConnectionFactory) {

    ValkeyTemplate<byte[], byte[]> template = new ValkeyTemplate<byte[], byte[]>();
    template.setConnectionFactory(valkeyConnectionFactory);
    template.afterPropertiesSet();

    return template;
  }

}
```

The necessary setup can vary, depending on your JavaEE environment.

The Spring Data Valkey CDI extension picks up all available repositories as CDI beans and creates a proxy for a Spring Data repository whenever a bean of a repository type is requested by the container.
Thus, obtaining an instance of a Spring Data repository is a matter of declaring an `@Injected` property, as shown in the following example:

```java
class RepositoryClient {

  @Inject
  PersonRepository repository;

  public void businessMethod() {
    List<Person> people = repository.findAll();
  }
}
```

A Valkey Repository requires `io.valkey.springframework.data.core.ValkeyKeyValueAdapter` and `io.valkey.springframework.data.core.ValkeyKeyValueTemplate` instances.
These beans are created and managed by the Spring Data CDI extension if no provided beans are found.
You can, however, supply your own beans to configure the specific properties of `io.valkey.springframework.data.core.ValkeyKeyValueAdapter` and `io.valkey.springframework.data.core.ValkeyKeyValueTemplate`.
