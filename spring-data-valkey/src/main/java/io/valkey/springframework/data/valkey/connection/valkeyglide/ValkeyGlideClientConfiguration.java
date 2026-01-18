/*
 * Copyright 2011-2025 the original author or authors.
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
import java.util.Optional;

import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.ReadFrom;
import org.springframework.lang.Nullable;

/**
 * Configuration interface for Valkey-Glide client settings.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public interface ValkeyGlideClientConfiguration {
    
    /**
     * Creates a new {@link ValkeyGlideClientConfigurationBuilder} to build {@link ValkeyGlideClientConfiguration}.
     *
     * @return a new {@link ValkeyGlideClientConfigurationBuilder}.
     */
    static ValkeyGlideClientConfigurationBuilder builder() {
        return new ValkeyGlideClientConfigurationBuilder();
    }
    
    /**
     * Creates a default {@link ValkeyGlideClientConfiguration} with default settings.
     *
     * @return a {@link ValkeyGlideClientConfiguration} with defaults.
     */
    static ValkeyGlideClientConfiguration defaultConfiguration() {
        return builder().build();
    }
    
    /**
     * Get the command timeout for Valkey-Glide client operations.
     * 
     * @return The command timeout duration. May be {@literal null} if not set.
     */
    @Nullable
    Duration getCommandTimeout();

    /**
     * Get the connection timeout for Valkey-Glide client operations.
     * 
     * @return The connection timeout duration. May be {@literal null} if not set.
     */
    @Nullable
    Duration getConnectionTimeout();

    /**
     * Check if SSL is enabled.
     * 
     * @return {@literal true} if SSL is enabled.
     */
    boolean isUseSsl();
    
    /**
     * Get the read from strategy for the client.
     * 
     * @return The {@link ReadFrom} strategy. May be {@literal null} if not set.
     */
    @Nullable
    ReadFrom getReadFrom();
    
    /**
     * Get the maximum number of concurrent in-flight requests.
     * 
     * @return The inflight requests limit. May be {@literal null} if not set.
     */
    @Nullable
    Integer getInflightRequestsLimit();
    
    /**
     * Get the client availability zone.
     * 
     * @return The client AZ. May be {@literal null} if not set.
     */
    @Nullable
    String getClientAZ();
    
    /**
     * Get the reconnection strategy.
     * 
     * @return The {@link BackoffStrategy}. May be {@literal null} if not set.
     */
    @Nullable
    BackoffStrategy getReconnectStrategy();

    /**
     * Get the maximum pool size for client pooling.
     * 
     * @return The maximum pool size. Default is 8.
     */
    int getMaxPoolSize();

    /**
     * Get OpenTelemetry configuration for Valkey-Glide client.
     * 
     * @return The {@link OpenTelemetryForGlide} configuration. May be {@literal null} if not set.
     */
    @Nullable
    OpenTelemetryForGlide getOpenTelemetryForGlide();

    /**
     * Get client options for mode-specific configurations.
     * Placeholder for future mode-specific extensions.
     * 
     * @return Optional containing client options if configured.
     */
    Optional<GlideClientOptions> getClientOptions();
    /**
     * Record representing OpenTelemetry configuration for Valkey-Glide client.
     *
     * @param tracesEndpoint     the OTLP endpoint for traces, or {@code null} if not set.
     * @param metricsEndpoint    the OTLP endpoint for metrics, or {@code null} if not set.
     * @param samplePercentage   the sampling percentage for traces, or {@code null} if not set.
     * @param flushIntervalMs    the flush interval in milliseconds, or {@code null} if not set.
     */
    public record OpenTelemetryForGlide(
            @Nullable String tracesEndpoint,
            @Nullable String metricsEndpoint,
            @Nullable Integer samplePercentage,
            @Nullable Long flushIntervalMs
    ) {

        /**
         * Default OpenTelemetry configuration for Valkey-Glide.
         */
        public static OpenTelemetryForGlide defaults() {
            return new OpenTelemetryForGlide(
                    "http://localhost:4318/v1/traces",
                    "http://localhost:4318/v1/metrics",
                    1,
                    5000L
            );
        }
    }

    /**
     * Builder for {@link ValkeyGlideClientConfiguration}.
     */
    class ValkeyGlideClientConfigurationBuilder {
        
        private @Nullable Duration commandTimeout;
        private @Nullable Duration connectionTimeout;
        private boolean useSsl = false;
        private @Nullable ReadFrom readFrom;
        private @Nullable Integer inflightRequestsLimit;
        private @Nullable String clientAZ;
        private @Nullable BackoffStrategy reconnectStrategy;
        private int maxPoolSize = 8; // Default pool size
        private @Nullable OpenTelemetryForGlide openTelemetryForGlide;
        
        
        ValkeyGlideClientConfigurationBuilder() {}
        
        /**
         * Set the command timeout.
         * 
         * @param commandTimeout the command timeout duration.
         * @return {@literal this} builder.
         */
        public ValkeyGlideClientConfigurationBuilder commandTimeout(Duration commandTimeout) {
            this.commandTimeout = commandTimeout;
            return this;
        }
        
        /**
         * Set the connection timeout.
         * 
         * @param connectionTimeout the connection timeout duration.
         * @return {@literal this} builder.
         */
        public ValkeyGlideClientConfigurationBuilder connectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }
        
        /**
         * Enable SSL for the connection.
         * 
         * @return {@literal this} builder.
         */
        public ValkeyGlideClientConfigurationBuilder useSsl() {
            this.useSsl = true;
            return this;
        }
        
        /**
         * Set the read from strategy.
         * 
         * @param readFrom the {@link ReadFrom} strategy.
         * @return {@literal this} builder.
         */
        public ValkeyGlideClientConfigurationBuilder readFrom(ReadFrom readFrom) {
            this.readFrom = readFrom;
            return this;
        }
        
        /**
         * Set the maximum number of concurrent in-flight requests.
         * 
         * @param inflightRequestsLimit the inflight requests limit.
         * @return {@literal this} builder.
         */
        public ValkeyGlideClientConfigurationBuilder inflightRequestsLimit(Integer inflightRequestsLimit) {
            this.inflightRequestsLimit = inflightRequestsLimit;
            return this;
        }
        
        /**
         * Set the client availability zone.
         * 
         * @param clientAZ the client AZ.
         * @return {@literal this} builder.
         */
        public ValkeyGlideClientConfigurationBuilder clientAZ(String clientAZ) {
            this.clientAZ = clientAZ;
            return this;
        }
        
        /**
         * Set the reconnection strategy.
         * 
         * @param reconnectStrategy the {@link BackoffStrategy}.
         * @return {@literal this} builder.
         */
        public ValkeyGlideClientConfigurationBuilder reconnectStrategy(BackoffStrategy reconnectStrategy) {
            this.reconnectStrategy = reconnectStrategy;
            return this;
        }

        /**
         * Initialize GLIDE OpenTelemetry with OTLP endpoints.
         *
         * If at least one endpoint (traces or metrics) is provided, this will initialize
         * OpenTelemetry once per JVM.
         */
        public ValkeyGlideClientConfigurationBuilder useOpenTelemetry(
            OpenTelemetryForGlide openTelemetryForGlide
        ) {
            this.openTelemetryForGlide = openTelemetryForGlide;
            return this;
        }

        /**
         * Set the maximum pool size for client pooling.
         * 
         * @param maxPoolSize the maximum number of clients in the pool.
         * @return {@literal this} builder.
         */
        public ValkeyGlideClientConfigurationBuilder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }
        
        /**
         * Build the {@link ValkeyGlideClientConfiguration}.
         * 
         * @return a new {@link ValkeyGlideClientConfiguration} instance.
         */
        public ValkeyGlideClientConfiguration build() {
            return new DefaultValkeyGlideClientConfiguration(
                commandTimeout, 
                useSsl, 
                connectionTimeout,
                readFrom,
                inflightRequestsLimit,
                clientAZ,
                reconnectStrategy,
                maxPoolSize,
                openTelemetryForGlide
            );
        }
    }
}
