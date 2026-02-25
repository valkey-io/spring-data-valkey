---
title: Core concepts
description: Core concepts of Spring Data repositories including CrudRepository, PagingAndSortingRepository, and derived queries
---

The central interface in the Spring Data repository abstraction is `Repository`.
It takes the domain class to manage as well as the identifier type of the domain class as type arguments.
This interface acts primarily as a marker interface to capture the types to work with and to help you to discover interfaces that extend this one.

:::tip
Spring Data considers domain types to be entities, more specifically aggregates.
So you will see the term "entity" used throughout the documentation that can be interchanged with the term "domain type" or "aggregate".

As you might have noticed in the introduction it already hinted towards domain-driven concepts.
We consider domain objects in the sense of DDD.
Domain objects have identifiers (otherwise these would be identity-less value objects), and we somehow need to refer to identifiers when working with certain patterns to access data.
Referring to identifiers will become more meaningful as we talk about repositories and query methods.
:::

The `CrudRepository` and `ListCrudRepository` interfaces provide sophisticated CRUD functionality for the entity class that is being managed.

*`CrudRepository` Interface*

```java
public interface CrudRepository<T, ID> extends Repository<T, ID> {

  <S extends T> S save(S entity);      // (1)

  Optional<T> findById(ID primaryKey); // (2)

  Iterable<T> findAll();               // (3)

  long count();                        // (4)

  void delete(T entity);               // (5)

  boolean existsById(ID primaryKey);   // (6)

  // … more functionality omitted.
}
```
```text
1. Saves the given entity.
2. Returns the entity identified by the given ID.
3. Returns all entities.
4. Returns the number of entities.
5. Deletes the given entity.
6. Indicates whether an entity with the given ID exists.
```

The methods declared in this interface are commonly referred to as CRUD methods.
`ListCrudRepository` offers equivalent methods, but they return `List` where the `CrudRepository` methods return an `Iterable`.

:::note[Important]
The repository interface implies a few reserved methods like `findById(ID identifier)` that target the domain type identifier property regardless of its property name.
Read more about this in "[Defining Query Methods](/repositories/query-methods-details#reserved-method-names)".

You can annotate your query method with `@Query` to provide a custom query if a property named `Id` doesn't refer to the identifier.
Following that path can easily lead to confusion and is discouraged as you will quickly hit type limits if the `ID` type and the type of your `Id` property deviate.
:::

:::note
We also provide persistence technology-specific abstractions, such as `JpaRepository` or `MongoRepository`.
Those interfaces extend `CrudRepository` and expose the capabilities of the underlying persistence technology in addition to the rather generic persistence technology-agnostic interfaces such as `CrudRepository`.
:::

Additional to the `CrudRepository`, there are `PagingAndSortingRepository` and `ListPagingAndSortingRepository` which add additional methods to ease paginated access to entities:

*`PagingAndSortingRepository` interface*

```java
public interface PagingAndSortingRepository<T, ID>  {

  Iterable<T> findAll(Sort sort);

  Page<T> findAll(Pageable pageable);
}
```

:::note
Extension interfaces are subject to be supported by the actual store module.
While this documentation explains the general scheme, make sure that your store module supports the interfaces that you want to use.
:::

To access the second page of `User` by a page size of 20, you could do something like the following:

```java
PagingAndSortingRepository<User, Long> repository = // … get access to a bean
Page<User> users = repository.findAll(PageRequest.of(1, 20));
```

`ListPagingAndSortingRepository` offers equivalent methods, but returns a `List` where the `PagingAndSortingRepository` methods return an `Iterable`.

In addition to query methods, query derivation for both count and delete queries is available.
The following list shows the interface definition for a derived count query:

*Derived Count Query*

```java
interface UserRepository extends CrudRepository<User, Long> {

  long countByLastname(String lastname);
}
```

The following listing shows the interface definition for a derived delete query:

*Derived Delete Query*

```java
interface UserRepository extends CrudRepository<User, Long> {

  long deleteByLastname(String lastname);

  List<User> removeByLastname(String lastname);
}
```

## Entity State Detection Strategies

The following table describes the strategies that Spring Data offers for detecting whether an entity is new:

*Table 1. Options for detection whether an entity is new in Spring Data*

| | |
|-----------|-------------|
| `@Id`-Property inspection (the default) | By default, Spring Data inspects the identifier property of the given entity. If the identifier property is `null` or `0` in case of primitive types, then the entity is assumed to be new. Otherwise, it is assumed to not be new. |
| `@Version`-Property inspection | If a property annotated with `@Version` is present and `null`, or in case of a version property of primitive type `0` the entity is considered new. If the version property is present but has a different value, the entity is considered to not be new. If no version property is present Spring Data falls back to inspection of the identifier property. |
| Implementing `Persistable` | If an entity implements `Persistable`, Spring Data delegates the new detection to the `isNew(…)` method of the entity. See the [Javadoc](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Persistable.html) for details.<br/><br/>_Note: Properties of `Persistable` will get detected and persisted if you use `AccessType.PROPERTY`. To avoid that, use `@Transient`._<br/> |
| Providing a custom `EntityInformation` implementation | You can customize the `EntityInformation` abstraction used in the repository base implementation by creating a subclass of the module specific repository factory and overriding the `getEntityInformation(…)` method. You then have to register the custom implementation of module specific repository factory as a Spring bean. Note that this should rarely be necessary. |
