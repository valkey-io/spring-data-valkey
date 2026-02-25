---
title: Valkey Repositories Overview
description: Valkey Repositories with Spring Data
---

This chapter explains the basic foundations of Spring Data repositories and Valkey specifics.
Before continuing to the Valkey specifics, make sure you have a sound understanding of the basic concepts.

The goal of the Spring Data repository abstraction is to significantly reduce the amount of boilerplate code required to implement data access layers for various persistence stores.

Working with Valkey Repositories lets you seamlessly convert and store domain objects in Valkey Hashes, apply custom mapping strategies, and use secondary indexes.

:::note[Important]
Valkey Repositories require at least Valkey Server version 2.8.0 and do not work with transactions. Make sure to use a `ValkeyTemplate` with [disabled transaction support](/valkey/transactions#tx.spring).
:::
