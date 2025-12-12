# Spring Boot Example

Spring Boot auto-configuration example demonstrating both ValkeyTemplate and repository usage.

The main benefits of using Spring Boot are to help manage dependencies as most are added by simply adding the starter, and also auto-configuration of the various Spring Data beans, such as `StringValkeyTemplate`, without the need for a separate `@Configuration`.

## Running Example

The Spring Boot example cannot share the same parent POM as the other examples due to the need to have different dependencies, so it only works standalone.

```bash
$ ../../mvnw compile exec:java
```
