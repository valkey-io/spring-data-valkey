package io.valkey.springframework.data.valkey.connection.valkeyglide;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration.AwsServiceType;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration.IamAuthenticationForGlide;

/**
 * Unit tests for IAM authentication configuration in Valkey-Glide integration.
 *
 * @author Ilia Kolominsky
 */
class ValkeyGlideIamAuthenticationConfigurationTests {

    @Test
    void shouldCreateIamAuthConfigWithAllFields() {

        IamAuthenticationForGlide config = new IamAuthenticationForGlide(
                "my-cluster", AwsServiceType.ELASTICACHE, "us-east-1", 600);

        assertThat(config.clusterName()).isEqualTo("my-cluster");
        assertThat(config.serviceType()).isEqualTo(AwsServiceType.ELASTICACHE);
        assertThat(config.region()).isEqualTo("us-east-1");
        assertThat(config.refreshIntervalSeconds()).isEqualTo(600);
    }

    @Test
    void shouldCreateIamAuthConfigWithMemoryDb() {

        IamAuthenticationForGlide config = new IamAuthenticationForGlide(
                "memdb-cluster", AwsServiceType.MEMORYDB, "eu-west-1", null);

        assertThat(config.clusterName()).isEqualTo("memdb-cluster");
        assertThat(config.serviceType()).isEqualTo(AwsServiceType.MEMORYDB);
        assertThat(config.region()).isEqualTo("eu-west-1");
        assertThat(config.refreshIntervalSeconds()).isNull();
    }

    @Test
    void shouldThrowWhenClusterNameIsNull() {

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IamAuthenticationForGlide(null, AwsServiceType.ELASTICACHE, "us-east-1", null))
                .withMessageContaining("clusterName must not be null");
    }

    @Test
    void shouldThrowWhenServiceTypeIsNull() {

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IamAuthenticationForGlide("my-cluster", null, "us-east-1", null))
                .withMessageContaining("serviceType must not be null");
    }

    @Test
    void shouldThrowWhenRegionIsNull() {

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IamAuthenticationForGlide("my-cluster", AwsServiceType.ELASTICACHE, null, null))
                .withMessageContaining("region must not be null");
    }

    @Test
    void shouldAllowNullRefreshIntervalSeconds() {

        IamAuthenticationForGlide config = new IamAuthenticationForGlide(
                "my-cluster", AwsServiceType.ELASTICACHE, "us-east-1", null);

        assertThat(config.refreshIntervalSeconds()).isNull();
    }

    @Test
    void builderShouldStoreIamAuthConfiguration() {

        IamAuthenticationForGlide iamConfig = new IamAuthenticationForGlide(
                "my-cluster", AwsServiceType.ELASTICACHE, "us-east-1", 300);

        ValkeyGlideClientConfiguration config = ValkeyGlideClientConfiguration.builder()
                .useIamAuthentication(iamConfig)
                .build();

        assertThat(config.getIamAuthentication()).isNotNull();
        assertThat(config.getIamAuthentication().clusterName()).isEqualTo("my-cluster");
        assertThat(config.getIamAuthentication().serviceType()).isEqualTo(AwsServiceType.ELASTICACHE);
        assertThat(config.getIamAuthentication().region()).isEqualTo("us-east-1");
        assertThat(config.getIamAuthentication().refreshIntervalSeconds()).isEqualTo(300);
    }

    @Test
    void builderShouldDefaultToNullIamAuth() {

        ValkeyGlideClientConfiguration config = ValkeyGlideClientConfiguration.builder().build();

        assertThat(config.getIamAuthentication()).isNull();
    }

    @Test
    void defaultConfigurationShouldHaveNullIamAuth() {

        ValkeyGlideClientConfiguration config = ValkeyGlideClientConfiguration.defaultConfiguration();

        assertThat(config.getIamAuthentication()).isNull();
    }

    @Test
    void builderShouldCombineIamAuthWithOtherSettings() {

        IamAuthenticationForGlide iamConfig = new IamAuthenticationForGlide(
                "my-cluster", AwsServiceType.MEMORYDB, "ap-southeast-1", 120);

        ValkeyGlideClientConfiguration config = ValkeyGlideClientConfiguration.builder()
                .useSsl()
                .useIamAuthentication(iamConfig)
                .maxPoolSize(16)
                .build();

        assertThat(config.isUseSsl()).isTrue();
        assertThat(config.getMaxPoolSize()).isEqualTo(16);
        assertThat(config.getIamAuthentication()).isNotNull();
        assertThat(config.getIamAuthentication().serviceType()).isEqualTo(AwsServiceType.MEMORYDB);
    }

    @Test
    void awsServiceTypeEnumShouldHaveExpectedValues() {

        assertThat(AwsServiceType.values()).containsExactly(AwsServiceType.ELASTICACHE, AwsServiceType.MEMORYDB);
    }

    @Test
    void awsServiceTypeShouldBeResolvableFromString() {

        assertThat(AwsServiceType.valueOf("ELASTICACHE")).isEqualTo(AwsServiceType.ELASTICACHE);
        assertThat(AwsServiceType.valueOf("MEMORYDB")).isEqualTo(AwsServiceType.MEMORYDB);
    }
}
