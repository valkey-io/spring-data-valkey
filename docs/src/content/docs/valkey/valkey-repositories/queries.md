---
title: Valkey-specific Query Methods
description: Queries documentation
---

Query methods allow automatic derivation of simple finder queries from the method name, as shown in the following example:

*Example 1. Sample Repository finder Method*

```java
public interface PersonRepository extends CrudRepository<Person, String> {

  List<Person> findByFirstname(String firstname);
}
```

:::note
Please make sure properties used in finder methods are set up for indexing.
:::

:::note
Query methods for Valkey repositories support only queries for entities and collections of entities with paging.
:::

Using derived query methods might not always be sufficient to model the queries to run. `ValkeyCallback` offers more control over the actual matching of index structures or even custom indexes.
To do so, provide a `ValkeyCallback` that returns a single or `Iterable` set of `id` values, as shown in the following example:

*Example 2. Sample finder using ValkeyCallback*

```java
String user = //...

List<ValkeySession> sessionsByUser = template.find(new ValkeyCallback<Set<byte[]>>() {

  public Set<byte[]> doInValkey(ValkeyConnection connection) throws DataAccessException {
    return connection
      .sMembers("sessions:securityContext.authentication.principal.username:" + user);
  }}, ValkeySession.class);
```

The following table provides an overview of the keywords supported for Valkey and what a method containing that keyword essentially translates to:

*Table 1. Supported keywords inside method names*

| Keyword | Sample | Valkey snippet |
|---------|--------|---------------|
| `And` | `findByLastnameAndFirstname` | `SINTER …:firstname:rand …:lastname:al'thor` |
| `Or` | `findByLastnameOrFirstname` | `SUNION …:firstname:rand …:lastname:al'thor` |
| `Is, Equals` | `findByFirstname`, `findByFirstnameIs`, `findByFirstnameEquals` | `SINTER …:firstname:rand` |
| `IsTrue` | `FindByAliveIsTrue` | `SINTER …:alive:1` |
| `IsFalse` | `findByAliveIsFalse` | `SINTER …:alive:0` |
| `Top,First` | `findFirst10ByFirstname`,`findTop5ByFirstname` | |

## Sorting Query Method results

Valkey repositories allow various approaches to define sorting order.
Valkey itself does not support in-flight sorting when retrieving hashes or sets.
Therefore, Valkey repository query methods construct a `Comparator` that is applied to the result before returning results as `List`.
Let's take a look at the following example:

*Example 3. Sorting Query Results*

```java
interface PersonRepository extends ValkeyRepository<Person, String> {

  List<Person> findByFirstnameOrderByAgeDesc(String firstname); // (1)

  List<Person> findByFirstname(String firstname, Sort sort);   // (2)
}
```
```text
1. Static sorting derived from method name.
2. Dynamic sorting using a method argument.
```
