---
title: Valkey Transactions
description: Transactions documentation
---

Valkey provides support for [transactions](https://valkey.io/topics/transactions) through the `multi`, `exec`, and `discard` commands.
These operations are available on `io.valkey.springframework.data.core.ValkeyTemplate`.
However, `ValkeyTemplate` is not guaranteed to run all the operations in the transaction with the same connection.

Spring Data Valkey provides the `io.valkey.springframework.data.core.SessionCallback` interface for use when multiple operations need to be performed with the same `connection`, such as when using Valkey transactions.The following example uses the `multi` method:

```java
//execute a transaction
List<Object> txResults = valkeyOperations.execute(new SessionCallback<List<Object>>() {
  public List<Object> execute(ValkeyOperations operations) throws DataAccessException {
    operations.multi();
    operations.opsForSet().add("key", "value1");

    // This will contain the results of all operations in the transaction
    return operations.exec();
  }
});
System.out.println("Number of items added to set: " + txResults.get(0));
```

`ValkeyTemplate` uses its value, hash key, and hash value serializers to deserialize all results of `exec` before returning.
There is an additional `exec` method that lets you pass a custom serializer for transaction results.

It is worth mentioning that in case between `multi()` and `exec()` an exception happens (e.g. a timeout exception in case Valkey does not respond within the timeout) then the connection may get stuck in a transactional state.
To prevent such a situation need have to discard the transactional state to clear the connection:

```java
List<Object> txResults = valkeyOperations.execute(new SessionCallback<List<Object>>() {
  public List<Object> execute(ValkeyOperations operations) throws DataAccessException {
    boolean transactionStateIsActive = true;
	try {
      operations.multi();
      operations.opsForSet().add("key", "value1");

      // This will contain the results of all operations in the transaction
      return operations.exec();
    } catch (RuntimeException e) {
	    operations.discard();
		throw e;
    }
  }
});
```

## `@Transactional` Support

By default, `ValkeyTemplate` does not participate in managed Spring transactions.
If you want `ValkeyTemplate` to make use of Valkey transaction when using `@Transactional` or `TransactionTemplate`, you need to be explicitly enable transaction support for each `ValkeyTemplate` by setting `setEnableTransactionSupport(true)`.
Enabling transaction support binds `ValkeyConnection` to the current transaction backed by a `ThreadLocal`.
If the transaction finishes without errors, the Valkey transaction gets commited with `EXEC`, otherwise rolled back with `DISCARD`.
Valkey transactions are batch-oriented.
Commands issued during an ongoing transaction are queued and only applied when committing the transaction.

Spring Data Valkey distinguishes between read-only and write commands in an ongoing transaction.
Read-only commands, such as `KEYS`, are piped to a fresh (non-thread-bound) `ValkeyConnection` to allow reads.
Write commands are queued by `ValkeyTemplate` and applied upon commit.

The following example shows how to configure transaction management:

*Example 1. Configuration enabling Transaction Management*

```java
@Configuration
@EnableTransactionManagement                                 // (1)
public class ValkeyTxContextConfiguration {

  @Bean
  public StringValkeyTemplate valkeyTemplate() {
    StringValkeyTemplate template = new StringValkeyTemplate(valkeyConnectionFactory());
    // explicitly enable transaction support
    template.setEnableTransactionSupport(true);              // (2)
    return template;
  }

  @Bean
  public ValkeyConnectionFactory valkeyConnectionFactory() {
    // jedis || Lettuce
  }

  @Bean
  public PlatformTransactionManager transactionManager() throws SQLException {
    return new DataSourceTransactionManager(dataSource());   // (3)
  }

  @Bean
  public DataSource dataSource() throws SQLException {
    // ...
  }
}
```
```text
1. Configures a Spring Context to enable [declarative transaction management](https://docs.spring.io/spring-framework/reference/data-access.html#transaction-declarative).
2. Configures `ValkeyTemplate` to participate in transactions by binding connections to the current thread.
3. Transaction management requires a `PlatformTransactionManager`.
Spring Data Valkey does not ship with a `PlatformTransactionManager` implementation.
Assuming your application uses JDBC, Spring Data Valkey can participate in transactions by using existing transaction managers.
```

The following examples each demonstrate a usage constraint:

*Example 2. Usage Constraints*

```java
// must be performed on thread-bound connection
template.opsForValue().set("thing1", "thing2");

// read operation must be run on a free (not transaction-aware) connection
template.keys("*");

// returns null as values set within a transaction are not visible
template.opsForValue().get("thing1");
```
