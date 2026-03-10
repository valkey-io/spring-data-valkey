/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.valkey.springframework.data.valkey.connection.valkeyglide;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import glide.api.models.GlideString;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.IamAuthConfig;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.ServiceType;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyPassword;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration.AwsServiceType;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration.IamAuthenticationForGlide;
import glide.api.GlideClient;
import glide.api.models.Batch;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * 
 * @author Ilia Kolominsky
 * @since 2.0
 */
class StandaloneGlideClientAdapter implements UnifiedGlideClient {

    private final GlideClient glideClient;
    private final @Nullable DelegatingPubSubListener listener;
    private Batch currentBatch;
    private BatchStatus batchStatus = BatchStatus.None;

    StandaloneGlideClientAdapter(ValkeyStandaloneConfiguration standaloneConfig, ValkeyGlideClientConfiguration valkeyGlideConfiguration) {
        // Build GlideClientConfiguration using Glide's API
        var configBuilder = GlideClientConfiguration.builder();
        
        // CONNECTION PROPERTIES from driver-agnostic configuration
        configBuilder.address(NodeAddress.builder()
            .host(standaloneConfig.getHostName())
            .port(standaloneConfig.getPort())
            .build());
        
        // Set credentials from driver-agnostic configuration
        IamAuthenticationForGlide iamAuth = valkeyGlideConfiguration.getIamAuthentication();
        ValkeyPassword password = standaloneConfig.getPassword();

        if (iamAuth != null) {
            // IAM authentication — mutually exclusive with password
            String username = standaloneConfig.getUsername();
            if (!StringUtils.hasText(username)) {
                throw new IllegalArgumentException(
                    "Username is required for IAM authentication. "
                    + "Set spring.data.valkey.username or configure username in ValkeyStandaloneConfiguration.");
            }

            IamAuthConfig.IamAuthConfigBuilder iamConfigBuilder = IamAuthConfig.builder()
                .clusterName(iamAuth.clusterName())
                .service(mapServiceType(iamAuth.serviceType()))
                .region(iamAuth.region());

            if (iamAuth.refreshIntervalSeconds() != null) {
                iamConfigBuilder.refreshIntervalSeconds(iamAuth.refreshIntervalSeconds());
            }

            configBuilder.credentials(
                glide.api.models.configuration.ServerCredentials.builder()
                    .username(username)
                    .iamConfig(iamConfigBuilder.build())
                    .build());
        } else if (!password.equals(ValkeyPassword.none())) {
            String username = standaloneConfig.getUsername();
            
            if (StringUtils.hasText(username)) {
                // Username + password authentication
                configBuilder.credentials(
                    glide.api.models.configuration.ServerCredentials.builder()
                        .username(username)
                        .password(String.valueOf(password.get()))
                        .build());
            } else {
                // Password-only authentication
                configBuilder.credentials(
                    glide.api.models.configuration.ServerCredentials.builder()
                        .password(String.valueOf(password.get()))
                        .build());
            }
        }
        
        // Set database from driver-agnostic configuration
        int database = standaloneConfig.getDatabase();
        if (database != 0) {
            configBuilder.databaseId(database);
        }
        
        // DRIVER-SPECIFIC PROPERTIES from ValkeyGlideClientConfiguration
        
        // Request timeout
        Duration commandTimeout = valkeyGlideConfiguration.getCommandTimeout();
        if (commandTimeout != null) {
            configBuilder.requestTimeout((int) commandTimeout.toMillis());
        }

        // Connection timeout
        Duration connectionTimeout = valkeyGlideConfiguration.getConnectionTimeout();
        if (connectionTimeout != null) {
            var advancedConfigBuilder = AdvancedGlideClientConfiguration.builder();
            advancedConfigBuilder.connectionTimeout((int) connectionTimeout.toMillis());
            configBuilder.advancedConfiguration(advancedConfigBuilder.build());
        }
        
        // SSL/TLS
        if (valkeyGlideConfiguration.isUseSsl()) {
            configBuilder.useTLS(true);
        }
        
        // Read from strategy
        ReadFrom readFrom = valkeyGlideConfiguration.getReadFrom();
        if (readFrom != null) {
            configBuilder.readFrom(readFrom);
        }
        
        // Inflight requests limit
        Integer inflightRequestsLimit = valkeyGlideConfiguration.getInflightRequestsLimit();
        if (inflightRequestsLimit != null) {
            configBuilder.inflightRequestsLimit(inflightRequestsLimit);
        }
        
        // Client AZ
        String clientAZ = valkeyGlideConfiguration.getClientAZ();
        if (clientAZ != null) {
            configBuilder.clientAZ(clientAZ);
        }
        
        // Reconnect strategy
        BackoffStrategy reconnectStrategy = valkeyGlideConfiguration.getReconnectStrategy();
        if (reconnectStrategy != null) {
            configBuilder.reconnectStrategy(reconnectStrategy);
        }

        // Pubsub listener 
        this.listener = new DelegatingPubSubListener();

        // Configure pub/sub with callback for event-driven message delivery
        var subConfigBuilder = StandaloneSubscriptionConfiguration.builder();
        
        // Set callback that delegates to our listener holder
        subConfigBuilder.callback((msg, context) -> this.listener.onMessage(msg, context));
        configBuilder.subscriptionConfiguration(subConfigBuilder.build());

        // Build and create client
        GlideClientConfiguration config = configBuilder.build();
        try {
            this.glideClient = GlideClient.createClient(config).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted creating GlideClient", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed creating GlideClient", e);
        }
    }

    @Override
    @Nullable
    public DelegatingPubSubListener getDelegatingListener() {
        return listener;
    }

    @Override
    public Object customCommand(GlideString[] args) throws InterruptedException, ExecutionException {
        if (currentBatch != null) {
            currentBatch.customCommand(args);
            return null;
        }
        return glideClient.customCommand(args).get();
    }

    @Override
    public Object[] execBatch() throws InterruptedException, ExecutionException {
        if (currentBatch == null) {
            throw new IllegalStateException("No batch in progress");
        }
        return glideClient.exec(currentBatch, false).get();
    }

    @Override
    public Object getNativeClient() {
        return glideClient;
    }

    @Override
    public void close() throws ExecutionException {
        // The native client might be pooled - dont close
    }

    @Override
    public BatchStatus getBatchStatus() {
        return batchStatus;
    }

    @Override
    public int getBatchCount() {
        if (currentBatch == null) {
            throw new IllegalStateException("No batch in progress");
        }
        return currentBatch.getProtobufBatch().getCommandsCount();
    }

    @Override
    public void startNewBatch(boolean atomic) {
        currentBatch = new Batch(atomic).withBinaryOutput();
        batchStatus = atomic ? BatchStatus.Transaction : BatchStatus.Pipeline;
    }

    @Override
    public void discardBatch() {
        currentBatch = null;
        batchStatus = BatchStatus.None;
    }

    @Override
    public void reset() {
        currentBatch = null;
        batchStatus = BatchStatus.None;
        if (getDelegatingListener() != null) {
            getDelegatingListener().clearListener();
        }
    }

    /**
     * Maps the Spring Data Valkey {@link AwsServiceType} enum to the Glide-native {@link ServiceType} enum.
     */
    private static ServiceType mapServiceType(AwsServiceType serviceType) {
        return switch (serviceType) {
            case ELASTICACHE -> ServiceType.ELASTICACHE;
            case MEMORYDB -> ServiceType.MEMORYDB;
        };
    }

}
