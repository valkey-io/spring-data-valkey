---
title: Getting Started
description: Getting Started documentation
---

An easy way to bootstrap setting up a working environment is to create a Spring-based project via [start.spring.io](https://start.spring.io/#!type=maven-project&dependencies=data-valkey) or create a Spring project in [Spring Tools](https://spring.io/tools).
## Examples Repository

The GitHub [spring-data-valkey](https://github.com/valkey-io/spring-data-valkey/tree/main/examples) repository hosts several examples that you can download and play around with to get a feel for how the library works.
## Hello World

First, you need to set up a running Valkey server.
Spring Data Valkey requires Valkey 2.6 or above and Spring Data Valkey integrates with [Valkey GLIDE](https://github.com/valkey-io/valkey-glide), [Lettuce](https://github.com/lettuce-io/lettuce-core) and [Jedis](https://github.com/redis/jedis), popular open-source Java libraries for Valkey.

Now you can create a simple Java application that stores and reads a value to and from Valkey.

Create the main application to run, as the following example shows:

```java
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.valkey.springframework.data.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.connection.glide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.core.ValkeyTemplate;

public class ValkeyApplication {

	private static final Log LOG = LogFactory.getLog(ValkeyApplication.class);

	public static void main(String[] args) {

		ValkeyConnectionFactory factory = new ValkeyGlideConnectionFactory();

		ValkeyTemplate<String, String> template = new ValkeyTemplate<>();
		template.setConnectionFactory(factory);
		template.afterPropertiesSet();

		template.opsForValue().set("foo", "bar");

		LOG.info("Value at foo:" + template.opsForValue().get("foo"));

		template.getConnectionFactory().getConnection().close();
	}
}
```
Even in this simple example, there are a few notable things to point out:

* You can create an instance of `io.valkey.springframework.data.core.ValkeyTemplate` with a `io.valkey.springframework.data.connection.ValkeyConnectionFactory`. Connection factories are an abstraction on top of the supported drivers.
* For reactive programming with `ReactiveValkeyTemplate`, only Lettuce is supported.
* There's no single way to use Valkey as it comes with support for a wide range of data structures such as plain keys ("strings"), lists, sets, sorted sets, streams, hashes and so on.
