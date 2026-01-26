---
title: Preface
description: Introduction to Spring Data Valkey
---

The Spring Data Valkey project applies core Spring concepts to the development of solutions by using a key-value style data store.
We provide a "template" as a high-level abstraction for sending and receiving messages.
You may notice similarities to the JDBC support in the Spring Framework.

This section provides an easy-to-follow guide for getting started with the Spring Data Valkey module.

## Learning Spring

Spring Data uses Spring framework's [core](https://docs.spring.io/spring-framework/reference/core.html) functionality, including:

* [IoC](https://docs.spring.io/spring-framework/reference/core.html#beans) container
* [type conversion system](https://docs.spring.io/spring-framework/reference/core.html#validation)
* [expression language](https://docs.spring.io/spring-framework/reference/core.html#expressions)
* [JMX integration](https://docs.spring.io/spring-framework/reference/integration.html#jmx)
* [DAO exception hierarchy](https://docs.spring.io/spring-framework/reference/data-access.html#dao-exceptions)

While you need not know the Spring APIs, understanding the concepts behind them is important.
At a minimum, the idea behind Inversion of Control (IoC) should be familiar, and you should be familiar with whatever IoC container you choose to use.

The core functionality of the Valkey support can be used directly, with no need to invoke the IoC services of the Spring Container.
This is much like `JdbcTemplate`, which can be used "standalone" without any other services of the Spring container.
To leverage all the features of Spring Data Valkey, such as the repository support, you need to configure some parts of the library to use Spring.

To learn more about Spring, you can refer to the comprehensive documentation that explains the Spring Framework in detail.
There are a lot of articles, blog entries, and books on the subject.
See the Spring framework [home page](https://spring.io/projects/spring-framework/) for more information.

In general, this should be the starting point for developers wanting to try Spring Data Valkey.

## Learning NoSQL and Key Value Stores

NoSQL stores have taken the storage world by storm.
It is a vast domain with a plethora of solutions, terms, and patterns (to make things worse, even the term itself has multiple [meanings](https://www.google.com/search?q=nosoql+acronym)).
While some of the principles are common, it is crucial that you be familiar to some degree with the stores supported by SDR. The best way to get acquainted with these solutions is to read their documentation and follow their examples.
It usually does not take more then five to ten minutes to go through them and, if you come from an RDMBS-only background, many times these exercises can be eye-openers.

### Trying out the Samples

One can find various samples for key-value stores in the dedicated Spring Data example repo, at [https://github.com/spring-projects/spring-data-examples/tree/main/valkey](https://github.com/spring-projects/spring-data-examples/tree/main/valkey).

## Requirements

Spring Data Valkey binaries require JDK level 17 and above and [Spring Framework](https://spring.io/projects/spring-framework/) 6.0 and above.

In terms of key-value stores, [Valkey](https://valkey.io) 2.6.x or higher is required.
Spring Data Valkey is currently tested against the latest 6.0 release.

## Additional Help Resources

Learning a new framework is not always straightforward.
In this section, we try to provide what we think is an easy-to-follow guide for starting with the Spring Data Valkey module.
However, if you encounter issues or you need advice, feel free to use one of the following links:

### Community Forum

Spring Data on [Stack Overflow](https://stackoverflow.com/questions/tagged/spring-data) is a tag for all Spring Data (not just Document) users to share information and help each other.
Note that registration is needed only for posting.

### Professional Support

Professional, from-the-source support, with guaranteed response time, is available from [Pivotal Software, Inc.](https://pivotal.io/), the company behind Spring Data and Spring.

## Following Development

For information on the Spring Data source code repository, nightly builds, and snapshot artifacts, see the Spring Data home [page](https://spring.io/projects/spring-data/).

You can help make Spring Data best serve the needs of the Spring community by interacting with developers on Stack Overflow at either
[spring-data](https://stackoverflow.com/questions/tagged/spring-data) or [spring-data-valkey](https://stackoverflow.com/questions/tagged/spring-data-valkey).

If you encounter a bug or want to suggest an improvement (including to this documentation), please create a ticket on [Github](https://github.com/valkey-io/spring-data-valkey/issues/new).

To stay up to date with the latest news and announcements in the Spring eco system, subscribe to the Spring Community [Portal](https://spring.io/).

Lastly, you can follow the Spring [blog](https://spring.io/blog/) or the project team ([@SpringData](https://twitter.com/SpringData)) on Twitter.
