---
title: Pipelining
description: Pipelining documentation
---

Valkey provides support for [pipelining](https://valkey.io/topics/pipelining), which involves sending multiple commands to the server without waiting for the replies and then reading the replies in a single step. Pipelining can improve performance when you need to send several commands in a row, such as adding many elements to the same List.

Spring Data Valkey provides several `ValkeyTemplate` methods for running commands in a pipeline. If you do not care about the results of the pipelined operations, you can use the standard `execute` method, passing `true` for the `pipeline` argument. The `executePipelined` methods run the provided `ValkeyCallback` or `SessionCallback` in a pipeline and return the results, as shown in the following example:

```java
//pop a specified number of items from a queue
List<Object> results = stringValkeyTemplate.executePipelined(
  new ValkeyCallback<Object>() {
    public Object doInValkey(ValkeyConnection connection) throws DataAccessException {
      StringValkeyConnection stringValkeyConn = (StringValkeyConnection)connection;
      for(int i=0; i< batchSize; i++) {
        stringValkeyConn.rPop("myqueue");
      }
    return null;
  }
});
```

The preceding example runs a bulk right pop of items from a queue in a pipeline.
The `results` `List` contains all the popped items. `ValkeyTemplate` uses its value, hash key, and hash value serializers to deserialize all results before returning, so the returned items in the preceding example are Strings.
There are additional `executePipelined` methods that let you pass a custom serializer for pipelined results.

Note that the value returned from the `ValkeyCallback` is required to be `null`, as this value is discarded in favor of returning the results of the pipelined commands.

:::tip
The Lettuce driver supports fine-grained flush control that allows to either flush commands as they appear, buffer or send them at connection close.
:::

```java
LettuceConnectionFactory factory = // ...
factory.setPipeliningFlushPolicy(PipeliningFlushPolicy.buffered(3)); // (1)
```
```text
1. Buffer locally and flush after every 3rd command.
```

:::note
Pipelining is limited to Valkey Standalone.
:::

Valkey Cluster is currently only supported through the Lettuce driver except for the following commands when using cross-slot keys: `rename`, `renameNX`, `sort`, `bLPop`, `bRPop`, `rPopLPush`, `bRPopLPush`, `info`, `sMove`, `sInter`, `sInterStore`, `sUnion`, `sUnionStore`, `sDiff`, `sDiffStore`.
Same-slot keys are fully supported.
