# Spring Data Valkey – IAM Authentication Example

This example demonstrates how to use **AWS IAM authentication** with Valkey-GLIDE for connecting
to **Amazon ElastiCache** or **Amazon MemoryDB** clusters.

## Overview

Instead of using static passwords, IAM authentication allows you to authenticate using AWS IAM
credentials. The GLIDE client automatically generates and refreshes short-lived IAM authentication
tokens, providing a more secure authentication mechanism.

## Prerequisites

1. **AWS ElastiCache or MemoryDB cluster** with IAM authentication enabled
2. **AWS credentials** configured in your environment (via environment variables, IAM role, or `~/.aws/credentials`)
3. **IAM policy** granting `elasticache:Connect` (for ElastiCache) or `memorydb:Connect` (for MemoryDB)
4. **TLS enabled** on the cluster (required for IAM authentication)

## Configuration

Update `src/main/resources/application.properties` with your cluster details:

```properties
spring.data.valkey.host=your-cluster-endpoint.cache.amazonaws.com
spring.data.valkey.port=6379
spring.data.valkey.username=your-iam-user-id
spring.data.valkey.ssl.enabled=true
spring.data.valkey.client-type=valkeyglide

spring.data.valkey.valkey-glide.iam-authentication.cluster-name=your-cluster-name
spring.data.valkey.valkey-glide.iam-authentication.service=ELASTICACHE
spring.data.valkey.valkey-glide.iam-authentication.region=us-east-1
```

### Configuration Properties

| Property | Description | Required |
|---|---|---|
| `iam-authentication.cluster-name` | Name of the ElastiCache/MemoryDB cluster | Yes |
| `iam-authentication.service` | AWS service type: `ELASTICACHE` or `MEMORYDB` | Yes |
| `iam-authentication.region` | AWS region (e.g., `us-east-1`) | Yes |
| `iam-authentication.refresh-interval-seconds` | Token refresh interval (default: 300s) | No |

## Programmatic Configuration

You can also configure IAM authentication programmatically:

```java
@Bean
public ValkeyGlideConnectionFactory valkeyConnectionFactory() {
    ValkeyClusterConfiguration clusterConfig = new ValkeyClusterConfiguration();
    clusterConfig.addClusterNode(new ValkeyClusterNode("your-endpoint", 6379));
    clusterConfig.setUsername("your-iam-user-id");

    ValkeyGlideClientConfiguration clientConfig = ValkeyGlideClientConfiguration.builder()
        .useSsl()
        .useIamAuthentication(new IamAuthenticationForGlide(
            "your-cluster-name",
            AwsServiceType.ELASTICACHE,
            "us-east-1",
            null  // use default refresh interval
        ))
        .build();

    return new ValkeyGlideConnectionFactory(clusterConfig, clientConfig);
}
```

## Running

```bash
mvn exec:java -pl examples/boot-iam-auth
```

## References

- [Valkey-GLIDE IAM Authentication](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#aws-iam-authentication-with-glide)
- [Using IAM with GLIDE for ElastiCache and MemoryDB](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#using-iam-authentication-with-glide-for-elasticache-and-memorydb)
- [Java Example](https://github.com/valkey-io/valkey-glide/wiki/Java-Wrapper#example---using-iam-authentication-with-glide-for-elasticache-and-memorydb)
