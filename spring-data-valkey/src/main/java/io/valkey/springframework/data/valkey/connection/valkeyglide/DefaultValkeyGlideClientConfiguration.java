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
 * Default implementation of {@link ValkeyGlideClientConfiguration}.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class DefaultValkeyGlideClientConfiguration implements ValkeyGlideClientConfiguration {
    private final @Nullable Duration commandTimeout;
    private final boolean useSsl;
    private final @Nullable Duration connectionTimeout;
    private final @Nullable ReadFrom readFrom;
    private final @Nullable Integer inflightRequestsLimit;
    private final @Nullable String clientAZ;
    private final @Nullable BackoffStrategy reconnectStrategy;
    private final int maxPoolSize;

    DefaultValkeyGlideClientConfiguration() {
        this(null, false, null, null, null, null, null, 8);
    }

    public DefaultValkeyGlideClientConfiguration(
            @Nullable Duration commandTimeout,
            boolean useSsl,
            @Nullable Duration connectionTimeout,
            @Nullable ReadFrom readFrom,
            @Nullable Integer inflightRequestsLimit,
            @Nullable String clientAZ,
            @Nullable BackoffStrategy reconnectStrategy,
            int maxPoolSize) {
        this.commandTimeout = commandTimeout;
        this.useSsl = useSsl;
        this.connectionTimeout = connectionTimeout;
        this.readFrom = readFrom;
        this.inflightRequestsLimit = inflightRequestsLimit;
        this.clientAZ = clientAZ;
        this.reconnectStrategy = reconnectStrategy;
        this.maxPoolSize = maxPoolSize;
    }

    @Nullable
    @Override
    public Duration getCommandTimeout() {
        return commandTimeout;
    }

    @Override
    public boolean isUseSsl() {
        return useSsl;
    }

    @Nullable
    @Override
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    @Nullable
    @Override
    public ReadFrom getReadFrom() {
        return readFrom;
    }

    @Nullable
    @Override
    public Integer getInflightRequestsLimit() {
        return inflightRequestsLimit;
    }

    @Nullable
    @Override
    public String getClientAZ() {
        return clientAZ;
    }

    @Nullable
    @Override
    public BackoffStrategy getReconnectStrategy() {
        return reconnectStrategy;
    }

    @Override
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    @Override
    public Optional<GlideClientOptions> getClientOptions() {
        return Optional.empty();
    }
}
