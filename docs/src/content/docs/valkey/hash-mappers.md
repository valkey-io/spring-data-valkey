---
title: Hash Mappers
description: Hash Mappers documentation
---

Data can be stored by using various data structures within Valkey. `io.valkey.springframework.data.serializer.Jackson2JsonValkeySerializer` can convert objects in [JSON](https://en.wikipedia.org/wiki/JSON) format. Ideally, JSON can be stored as a value by using plain keys. You can achieve a more sophisticated mapping of structured objects by using Valkey hashes. Spring Data Valkey offers various strategies for mapping data to hashes (depending on the use case):

* Direct mapping, by using `io.valkey.springframework.data.core.HashOperations` and a [serializer](/valkey/template#serializers)
* Using [Valkey Repositories](/repositories)
* Using `io.valkey.springframework.data.hash.HashMapper` and `io.valkey.springframework.data.core.HashOperations`

## Hash Mappers

Hash mappers are converters of map objects to a `Map<K, V>` and back. `io.valkey.springframework.data.hash.HashMapper` is intended for using with Valkey Hashes.

Multiple implementations are available:

* `io.valkey.springframework.data.hash.BeanUtilsHashMapper` using Spring's [BeanUtils](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/BeanUtils.html).
* `io.valkey.springframework.data.hash.ObjectHashMapper` using [Object-to-Hash Mapping](/valkey/valkey-repositories/mapping).
* [`Jackson2HashMapper`](#jackson2hashmapper) using [FasterXML Jackson](https://github.com/FasterXML/jackson).

The following example shows one way to implement hash mapping:

```java
public class Person {
  String firstname;
  String lastname;

  // …
}

public class HashMapping {

  @Resource(name = "valkeyTemplate")
  HashOperations<String, byte[], byte[]> hashOperations;

  HashMapper<Object, byte[], byte[]> mapper = new ObjectHashMapper();

  public void writeHash(String key, Person person) {

    Map<byte[], byte[]> mappedHash = mapper.toHash(person);
    hashOperations.putAll(key, mappedHash);
  }

  public Person loadHash(String key) {

    Map<byte[], byte[]> loadedHash = hashOperations.entries(key);
    return (Person) mapper.fromHash(loadedHash);
  }
}
```

## Jackson2HashMapper

`io.valkey.springframework.data.hash.Jackson2HashMapper` provides Valkey Hash mapping for domain objects by using [FasterXML Jackson](https://github.com/FasterXML/jackson).
`Jackson2HashMapper` can map top-level properties as Hash field names and, optionally, flatten the structure.
Simple types map to simple values. Complex types (nested objects, collections, maps, and so on) are represented as nested JSON.

Flattening creates individual hash entries for all nested properties and resolves complex types into simple types, as far as possible.

Consider the following class and the data structure it contains:

```java
public class Person {
  String firstname;
  String lastname;
  Address address;
  Date date;
  LocalDateTime localDateTime;
}

public class Address {
  String city;
  String country;
}
```

The following table shows how the data in the preceding class would appear in normal mapping:

*Table 1. Normal Mapping*

| Hash Field | Value |
|------------|-------|
| firstname | `Jon` |
| lastname | `Snow` |
| address | `{ "city" : "Castle Black", "country" : "The North" }` |
| date | `1561543964015` |
| localDateTime | `2018-01-02T12:13:14` |

The following table shows how the data in the preceding class would appear in flat mapping:

*Table 2. Flat Mapping*

| Hash Field | Value |
|------------|-------|
| firstname | `Jon` |
| lastname | `Snow` |
| address.city | `Castle Black` |
| address.country | `The North` |
| date | `1561543964015` |
| localDateTime | `2018-01-02T12:13:14` |

:::note
Flattening requires all property names to not interfere with the JSON path. Using dots or brackets in map keys or as property names is not supported when you use flattening. The resulting hash cannot be mapped back into an Object.
:::

:::note
`java.util.Date` and `java.util.Calendar` are represented with milliseconds. JSR-310 Date/Time types are serialized to their `toString` form if  `jackson-datatype-jsr310` is on the class path.
:::
