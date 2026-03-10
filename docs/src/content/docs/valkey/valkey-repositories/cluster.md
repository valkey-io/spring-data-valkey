---
title: Valkey Repositories Running on a Cluster
description: Cluster documentation
---

You can use the Valkey repository support in a clustered Valkey environment.
See the "[Valkey Cluster](/valkey/cluster)" section for `ConnectionFactory` configuration details.
Still, some additional configuration must be done, because the default key distribution spreads entities and secondary indexes through out the whole cluster and its slots.

The following table shows the details of data on a cluster (based on previous examples):

## Default Key Distribution

| Key | Type | Slot | Node |
|-----|------|------|------|
| people:e2c7dcee-b8cd-4424-883e-736ce564363e | id for hash | 15171 | 127.0.0.1:7381 |
| people:a9d4b3a0-50d3-4538-a2fc-f7fc2581ee56 | id for hash | 7373 | 127.0.0.1:7380 |
| people:firstname:rand | index | 1700 | 127.0.0.1:7379 |

Some commands (such as `SINTER` and `SUNION`) can only be processed on the server side when all involved keys map to the same slot.
Otherwise, computation has to be done on client side.
Therefore, it is useful to pin keyspaces to a single slot, which lets make use of Valkey server side computation right away.
The following table shows what happens when you do (note the change in the slot column and the port value in the node column):

## Pinned Keyspace Distribution

| Key | Type | Slot | Node |
|-----|------|------|------|
| {people}:e2c7dcee-b8cd-4424-883e-736ce564363e | id for hash | 2399 | 127.0.0.1:7379 |
| {people}:a9d4b3a0-50d3-4538-a2fc-f7fc2581ee56 | id for hash | 2399 | 127.0.0.1:7379 |
| {people}:firstname:rand | index | 2399 | 127.0.0.1:7379 |

:::tip
Define and pin keyspaces by using `@ValkeyHash("{yourkeyspace}")` to specific slots when you use Valkey cluster.
:::
