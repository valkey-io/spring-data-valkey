---
title: Object-to-Hash Mapping
description: Mapping documentation
---

The Valkey Repository support persists Objects to Hashes.
This requires an Object-to-Hash conversion which is done by a `ValkeyConverter`.
The default implementation uses `Converter` for mapping property values to and from Valkey native `byte[]`.

Given the `Person` type from the previous sections, the default mapping looks like the following:

```text
_class = org.example.Person                 // (1)
id = e2c7dcee-b8cd-4424-883e-736ce564363e
firstname = rand                            // (2)
lastname = al'thor
address.city = emond's field                // (3)
address.country = andor
```
```text
1. The `_class` attribute is included on the root level as well as on any nested interface or abstract types.
2. Simple property values are mapped by path.
3. Properties of complex types are mapped by their dot path.
```

## Data Mapping and Type Conversion

This section explains how types are mapped to and from a Hash representation:

*Table 1. Default Mapping Rules*

| Type | Sample | Mapped Value |
|------|--------|--------------|
| Simple Type (for example, String) | `String firstname = "rand";` | `firstname = "rand"` |
| Byte array (`byte[]`) | `byte[] image = "rand".getBytes();` | `image = "rand"` |
| Complex Type (for example, Address) | `Address address = new Address("emond's field");` | `address.city = "emond's field"` |
| List of Simple Type | `List<String> nicknames = asList("dragon reborn", "lews therin");` | `nicknames.[0] = "dragon reborn"`, `nicknames.[1] = "lews therin"` |
| Map of Simple Type | `Map<String, String> atts = asMap({"eye-color", "grey"}, {"hair-color", "brown"});` | `atts.[eye-color] = "grey"`, `atts.[hair-color] = "brown"` |
| List of Complex Type | `List<Address> addresses = asList(new Address("emond's field"));` | `addresses.[0].city = "emond's field"`, `addresses.[1].city = "..."` |
| Map of Complex Type | `Map<String, Address> addresses = asMap({"home", new Address("emond's field")});` | `addresses.[home].city = "emond's field"`, `addresses.[work].city = "..."` |

:::caution
Due to the flat representation structure, Map keys need to be simple types, such as `String` or `Number`.
:::

Mapping behavior can be customized by registering the corresponding `Converter` in `ValkeyCustomConversions`.
Those converters can take care of converting from and to a single `byte[]` as well as `Map<String, byte[]>`.
The first one is suitable for (for example) converting a complex type to (for example) a binary JSON representation that still uses the default mappings hash structure.
The second option offers full control over the resulting hash.

:::danger
Writing objects to a Valkey hash deletes the content from the hash and re-creates the whole hash, so data that has not been mapped is lost.
:::

The following example shows two sample byte array converters:

*Example 1. Sample byte[] Converters*

```java
@WritingConverter
public class AddressToBytesConverter implements Converter<Address, byte[]> {

  private final Jackson2JsonValkeySerializer<Address> serializer;

  public AddressToBytesConverter() {

    serializer = new Jackson2JsonValkeySerializer<Address>(Address.class);
    serializer.setObjectMapper(new ObjectMapper());
  }

  @Override
  public byte[] convert(Address value) {
    return serializer.serialize(value);
  }
}

@ReadingConverter
public class BytesToAddressConverter implements Converter<byte[], Address> {

  private final Jackson2JsonValkeySerializer<Address> serializer;

  public BytesToAddressConverter() {

    serializer = new Jackson2JsonValkeySerializer<Address>(Address.class);
    serializer.setObjectMapper(new ObjectMapper());
  }

  @Override
  public Address convert(byte[] value) {
    return serializer.deserialize(value);
  }
}
```

Using the preceding byte array `Converter` produces output similar to the following:

```text
_class = org.example.Person
id = e2c7dcee-b8cd-4424-883e-736ce564363e
firstname = rand
lastname = al'thor
address = { city : "emond's field", country : "andor" }
```

The following example shows two examples of `Map` converters:

*Example 2. Sample Map<String, byte[]> Converters*

```java
@WritingConverter
public class AddressToMapConverter implements Converter<Address, Map<String, byte[]>> {

  @Override
  public Map<String, byte[]> convert(Address source) {
    return singletonMap("ciudad", source.getCity().getBytes());
  }
}

@ReadingConverter
public class MapToAddressConverter implements Converter<Map<String, byte[]>, Address> {

  @Override
  public Address convert(Map<String, byte[]> source) {
    return new Address(new String(source.get("ciudad")));
  }
}
```

Using the preceding Map `Converter` produces output similar to the following:

```text
_class = org.example.Person
id = e2c7dcee-b8cd-4424-883e-736ce564363e
firstname = rand
lastname = al'thor
ciudad = "emond's field"
```

:::note
Custom conversions have no effect on index resolution. [Secondary Indexes](/valkey/valkey-repositories/indexes) are still created, even for custom converted types.
:::

## Customizing Type Mapping

If you want to avoid writing the entire Java class name as type information and would rather like to use a key, you can use the `@TypeAlias` annotation on the entity class being persisted.
If you need to customize the mapping even more, look at the [`TypeInformationMapper`](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/convert/TypeInformationMapper.html) interface.
An instance of that interface can be configured at the `DefaultValkeyTypeMapper`, which can be configured on `MappingValkeyConverter`.

The following example shows how to define a type alias for an entity:

*Example 3. Defining `@TypeAlias` for an entity*

```java
@TypeAlias("pers")
class Person {

}
```

The resulting document contains `pers` as the value in a `_class` field.

### Configuring Custom Type Mapping

The following example demonstrates how to configure a custom `ValkeyTypeMapper` in `MappingValkeyConverter`:

*Example 4. Configuring a custom `ValkeyTypeMapper` via Spring Java Config*

```java
class CustomValkeyTypeMapper extends DefaultValkeyTypeMapper {
  //implement custom type mapping here
}
```

```java
@Configuration
class SampleValkeyConfiguration {

  @Bean
  public MappingValkeyConverter valkeyConverter(ValkeyMappingContext mappingContext,
        ValkeyCustomConversions customConversions, ReferenceResolver referenceResolver) {

    MappingValkeyConverter mappingValkeyConverter = new MappingValkeyConverter(mappingContext, null, referenceResolver,
            customTypeMapper());

    mappingValkeyConverter.setCustomConversions(customConversions);

    return mappingValkeyConverter;
  }

  @Bean
  public ValkeyTypeMapper customTypeMapper() {
    return new CustomValkeyTypeMapper();
  }
}
```
