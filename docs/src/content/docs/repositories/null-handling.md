---
title: Null Handling of Repository Methods
description: Null handling in Spring Data repositories including nullability annotations and Kotlin support
---

As of Spring Data 2.0, repository CRUD methods that return an individual aggregate instance use Java 8's `Optional` to indicate the potential absence of a value.
Besides that, Spring Data supports returning the following wrapper types on query methods:

* `com.google.common.base.Optional`
* `scala.Option`
* `io.vavr.control.Option`

Alternatively, query methods can choose not to use a wrapper type at all.
The absence of a query result is then indicated by returning `null`.
Repository methods returning collections, collection alternatives, wrappers, and streams are guaranteed never to return `null` but rather the corresponding empty representation.
See "[Repository query return types](/repositories/query-return-types-reference)" for details.

## Nullability Annotations

You can express nullability constraints for repository methods by using [Spring Framework's nullability annotations](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#null-safety).
They provide a tooling-friendly approach and opt-in `null` checks during runtime, as follows:

* `@NonNullApi`: Used on the package level to declare that the default behavior for parameters and return values is, respectively, neither to accept nor to produce `null` values.
* `@NonNull`: Used on a parameter or return value that must not be `null` (not needed on a parameter and return value where `@NonNullApi` applies).
* `@Nullable`: Used on a parameter or return value that can be `null`.

Spring annotations are meta-annotated with [JSR 305](https://jcp.org/en/jsr/detail?id=305) annotations (a dormant but widely used JSR).
JSR 305 meta-annotations let tooling vendors (such as [IDEA](https://www.jetbrains.com/help/idea/nullable-and-notnull-annotations.html), [Eclipse](https://help.eclipse.org/latest/index.jsp?topic=/org.eclipse.jdt.doc.user/tasks/task-using_external_null_annotations.htm), and [Kotlin](https://kotlinlang.org/docs/reference/java-interop.html#null-safety-and-platform-types)) provide null-safety support in a generic way, without having to hard-code support for Spring annotations.
To enable runtime checking of nullability constraints for query methods, you need to activate non-nullability on the package level by using Spring's `@NonNullApi` in `package-info.java`, as shown in the following example:

*Declaring Non-nullability in `package-info.java`*

```java
@org.springframework.lang.NonNullApi
package com.acme;
```

Once non-null defaulting is in place, repository query method invocations get validated at runtime for nullability constraints.
If a query result violates the defined constraint, an exception is thrown.
This happens when the method would return `null` but is declared as non-nullable (the default with the annotation defined on the package in which the repository resides).
If you want to opt-in to nullable results again, selectively use `@Nullable` on individual methods.
Using the result wrapper types mentioned at the start of this section continues to work as expected: an empty result is translated into the value that represents absence.

The following example shows a number of the techniques just described:

*Using different nullability constraints*

```java
package com.acme;                                                       // (1)

import org.springframework.lang.Nullable;

interface UserRepository extends Repository<User, Long> {

  User getByEmailAddress(EmailAddress emailAddress);                    // (2)

  @Nullable
  User findByEmailAddress(@Nullable EmailAddress emailAdress);          // (3)

  Optional<User> findOptionalByEmailAddress(EmailAddress emailAddress); // (4)
}
```
```text
1. The repository resides in a package (or sub-package) for which we have defined non-null behavior.
2. Throws an `EmptyResultDataAccessException` when the query does not produce a result.
Throws an `IllegalArgumentException` when the `emailAddress` handed to the method is `null`.
3. Returns `null` when the query does not produce a result.
Also accepts `null` as the value for `emailAddress`.
4. Returns `Optional.empty()` when the query does not produce a result.
Throws an `IllegalArgumentException` when the `emailAddress` handed to the method is `null`.
```

## Nullability in Kotlin-based Repositories

Kotlin has the definition of [nullability constraints](https://kotlinlang.org/docs/reference/null-safety.html) baked into the language.
Kotlin code compiles to bytecode, which does not express nullability constraints through method signatures but rather through compiled-in metadata.
Make sure to include the `kotlin-reflect` JAR in your project to enable introspection of Kotlin's nullability constraints.
Spring Data repositories use the language mechanism to define those constraints to apply the same runtime checks, as follows:

*Using nullability constraints on Kotlin repositories*

```kotlin
interface UserRepository : Repository<User, String> {

  fun findByUsername(username: String): User     // (1)

  fun findByFirstname(firstname: String?): User? // (2)
}
```
```text
1. The method defines both the parameter and the result as non-nullable (the Kotlin default).
The Kotlin compiler rejects method invocations that pass `null` to the method.
If the query yields an empty result, an `EmptyResultDataAccessException` is thrown.
2. This method accepts `null` for the `firstname` parameter and returns `null` if the query does not produce a result.
```
