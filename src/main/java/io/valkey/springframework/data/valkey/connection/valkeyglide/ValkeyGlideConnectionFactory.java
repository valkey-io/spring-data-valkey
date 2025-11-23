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

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyPassword;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConnection;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

// Imports needed for working with valkey-glide
// Imports for valkey-glide library
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;

import java.util.concurrent.ExecutionException;

/**
 * Connection factory creating <a href="https://github.com/valkey-io/valkey-glide">Valkey Glide</a> based
 * connections. This is the central class for connecting to Valkey using Valkey-Glide.
 * 
 * <p>This factory creates a new {@link ValkeyGlideConnection} on each call to {@link #getConnection()}.
 * The underlying client instance is shared among all connections.
 * 
 * <p>This class implements the {@link org.springframework.beans.factory.InitializingBean} interface,
 * triggering the creation of the client instance on {@link #afterPropertiesSet()}.
 * It also implements {@link org.springframework.beans.factory.DisposableBean} for closing the client on
 * application shutdown.
 * 
 * <p>The Valkey Glide connection factory can be used both with a standalone Valkey server and with a
 * Valkey cluster. For standalone mode, use the {@link #ValkeyGlideConnectionFactory()} constructor
 * or {@link #ValkeyGlideConnectionFactory(ValkeyGlideClientConfiguration)} constructor. For cluster mode,
 * ensure the {@link ValkeyGlideClientConfiguration} is configured for cluster mode.
 * 
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideConnectionFactory
    implements ValkeyConnectionFactory, InitializingBean, DisposableBean, SmartLifecycle {
        
    private final @Nullable ValkeyGlideClientConfiguration valkeyGlideConfiguration;
    private final ValkeyConfiguration configuration;
    
    private boolean initialized = false;
    private boolean running = false;
    private boolean autoStartup = true;
    private boolean earlyStartup = true;
    private int phase = 0;

    /**
     * Constructs a new {@link ValkeyGlideConnectionFactory} instance with default settings.
     */
    public ValkeyGlideConnectionFactory() {
        this(new ValkeyStandaloneConfiguration(), new DefaultValkeyGlideClientConfiguration());
    }

    public ValkeyGlideConnectionFactory(ValkeyStandaloneConfiguration standaloneConfiguration) {
		this(standaloneConfiguration, new DefaultValkeyGlideClientConfiguration());
	}

    /**
     * Constructs a new {@link ValkeyGlideConnectionFactory} instance with the given {@link ValkeyStandaloneConfiguration}
     * and {@link ValkeyGlideClientConfiguration}.
     *
     * @param standaloneConfiguration must not be {@literal null}
     * @param clientConfiguration must not be {@literal null}
     * @since 3.0
     */
    public ValkeyGlideConnectionFactory(ValkeyStandaloneConfiguration standaloneConfiguration,
            ValkeyGlideClientConfiguration valkeyGlideConfiguration) {
        
        Assert.notNull(standaloneConfiguration, "ValkeyStandaloneConfiguration must not be null!");
        Assert.notNull(valkeyGlideConfiguration, "ValkeyGlideClientConfiguration must not be null!");
        
        this.configuration = standaloneConfiguration;
        this.valkeyGlideConfiguration = valkeyGlideConfiguration;
    }

    /**
     * Constructs a new {@link ValkeyGlideConnectionFactory} instance with the given {@link ValkeyClusterConfiguration}
     * and {@link ValkeyGlideClusterClientConfiguration}.
     *
     * @param clusterConfiguration must not be {@literal null}
     * @param clusterClientConfiguration must not be {@literal null}
     * @since 3.0
     */
    public ValkeyGlideConnectionFactory(ValkeyClusterConfiguration clusterConfiguration,
        ValkeyGlideClientConfiguration valkeyGlideConfiguration) {
        
        Assert.notNull(clusterConfiguration, "ValkeyClusterConfiguration must not be null!");
        Assert.notNull(valkeyGlideConfiguration, "ValkeyGlideClientConfiguration must not be null!");
        
        this.configuration = clusterConfiguration;
        this.valkeyGlideConfiguration = valkeyGlideConfiguration;
    }

    /**
     * Constructs a new {@link ValkeyGlideConnectionFactory} instance with the given {@link ValkeyGlideClientConfiguration}.
     *
     * @param clientConfiguration must not be {@literal null}
     * @deprecated since 3.0, use {@link #ValkeyGlideConnectionFactory(ValkeyStandaloneConfiguration, ValkeyGlideClientConfiguration)}
     *             or {@link #ValkeyGlideConnectionFactory(ValkeyClusterConfiguration, ValkeyGlideClusterClientConfiguration)} instead
     */
    @Deprecated
    public ValkeyGlideConnectionFactory(ValkeyGlideClientConfiguration valkeyGlideConfiguration) {
        Assert.notNull(valkeyGlideConfiguration, "ValkeyGlideClientConfiguration must not be null!");
        
        this.configuration = new ValkeyStandaloneConfiguration();
        this.valkeyGlideConfiguration = valkeyGlideConfiguration;
    }

    /**
     * Initialize the shared client if not already initialized.
     */
    @Override
    public void afterPropertiesSet() {
        if (initialized) {
            return;
        }
    }

    @Override
    public ValkeyConnection getConnection() {
        afterPropertiesSet();
        
        // Return cluster connection when in cluster mode
        if (isClusterAware()) {
            return getClusterConnection();
        }
        
        GlideClient glideClient = createGlideClient();
        return new ValkeyGlideConnection(new StandaloneGlideClientAdapter(glideClient));
    }

    @Override
    public ValkeyClusterConnection getClusterConnection() {
        afterPropertiesSet();
        
        if (!isClusterAware()) {
            throw new InvalidDataAccessResourceUsageException("Cluster mode is not configured!");
        }

        GlideClusterClient glideClusterClient = createGlideClusterClient();
        return new ValkeyGlideClusterConnection(new ClusterGlideClientAdapter(glideClusterClient));
    }

    @Override
    public boolean getConvertPipelineAndTxResults() {
        return true;
    }

    @Override
    public ValkeySentinelConnection getSentinelConnection() {
        throw new UnsupportedOperationException("Sentinel connections not supported with Valkey-Glide!");
    }

    /**
     * Shut down the client when this factory is destroyed.
     */
    @Override
    public void destroy() {
        initialized = false;
        running = false;
    }

    /**
     * Creates a GlideClient instance for each connection.
     */
    private GlideClient createGlideClient() {
        try {
            if (ValkeyConfiguration.isClusterConfiguration(configuration)) {
                throw new IllegalStateException("Cannot create GlideClient in cluster mode");
            }
            // Cast to standalone configuration
            ValkeyStandaloneConfiguration standaloneConfig = 
                (ValkeyStandaloneConfiguration) configuration;
            
            // Build GlideClientConfiguration using Glide's API
            var configBuilder = GlideClientConfiguration.builder();
            
            // CONNECTION PROPERTIES from driver-agnostic configuration
            configBuilder.address(NodeAddress.builder()
                .host(standaloneConfig.getHostName())
                .port(standaloneConfig.getPort())
                .build());
            
            // Set credentials from driver-agnostic configuration
            ValkeyPassword password = standaloneConfig.getPassword();
            if (!password.equals(ValkeyPassword.none())) {
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
            
            // Build and create client
            GlideClientConfiguration config = configBuilder.build();
            return GlideClient.createClient(config).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create GlideClient: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a GlideClusterClient instance for each connection.
     */
    private GlideClusterClient createGlideClusterClient() {
        try {
            if (!ValkeyConfiguration.isClusterConfiguration(configuration)) {
                throw new IllegalStateException("Cannot create GlideClusterClient in non-cluster mode");
            }
            
            // Cast to cluster configuration
            ValkeyClusterConfiguration clusterConfig = 
                (ValkeyClusterConfiguration) configuration;
            
            // Build GlideClusterClientConfiguration using Glide's API
            var configBuilder = 
                GlideClusterClientConfiguration.builder();
            
            // CONNECTION PROPERTIES from driver-agnostic configuration
            // Add all cluster nodes
            clusterConfig.getClusterNodes().forEach(node -> {
                configBuilder.address(NodeAddress.builder()
                    .host(node.getHost())
                    .port(node.getPort())
                    .build());
            });
            
            // Set credentials from driver-agnostic configuration
            ValkeyPassword password = clusterConfig.getPassword();
            if (!password.equals(ValkeyPassword.none())) {
                String username = clusterConfig.getUsername();
                
                if (StringUtils.hasText(username)) {
                    configBuilder.credentials(
                        glide.api.models.configuration.ServerCredentials.builder()
                            .username(username)
                            .password(String.valueOf(password.get()))
                            .build());
                } else {
                    configBuilder.credentials(
                        glide.api.models.configuration.ServerCredentials.builder()
                            .password(String.valueOf(password.get()))
                            .build());
                }
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
                var advancedConfigBuilder = AdvancedGlideClusterClientConfiguration.builder();
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
            
            // Build and create cluster client
            GlideClusterClientConfiguration config = configBuilder.build();
            return GlideClusterClient.createClient(config).get();
            
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Failed to create GlideClusterClient: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes the factory, starting the client.
     */
    @Override
    public void start() {
        if (!initialized) {
            afterPropertiesSet();
        }
        running = true;
    }
    
    /**
     * Stops the client.
     */
    @Override
    public void stop() {
        running = false;
    }
    
    /**
     * Returns if the client is running.
     * 
     * @return true if running
     */
    @Override
    public boolean isRunning() {
        return initialized && running;
    }

    /**
     * @return true if cluster mode is enabled.
     */
    public boolean isClusterAware() {
        return ValkeyConfiguration.isClusterConfiguration(this.configuration);
    }
    /**
     * @return The client configuration used.
     */
    public ValkeyGlideClientConfiguration getClientConfiguration() {
        return valkeyGlideConfiguration;
    }


    /**
     * @return whether this lifecycle component should get started automatically by the container
     */
    @Override
    public boolean isAutoStartup() {
        return this.autoStartup;
    }

    /**
     * Configure if this Lifecycle connection factory should get started automatically by the container.
     *
     * @param autoStartup {@literal true} to automatically {@link #start()} the connection factory; {@literal false} otherwise.
     */
    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    /**
     * @return whether to {@link #start()} the component during {@link #afterPropertiesSet()}.
     */
    public boolean isEarlyStartup() {
        return this.earlyStartup;
    }

    /**
     * Configure if this InitializingBean's component Lifecycle should get started early by {@link #afterPropertiesSet()}
     * at the time that the bean is initialized. The component defaults to auto-startup.
     *
     * @param earlyStartup {@literal true} to early {@link #start()} the component; {@literal false} otherwise.
     */
    public void setEarlyStartup(boolean earlyStartup) {
        this.earlyStartup = earlyStartup;
    }
    
    /**
     * @return the phase value for this lifecycle component
     */
    @Override
    public int getPhase() {
        return this.phase;
    }
    
    /**
     * Specify the lifecycle phase for pausing and resuming this executor.
     * 
     * @param phase the phase value to set
     */
    public void setPhase(int phase) {
        this.phase = phase;
    }
    
    /**
     * Translates a Valkey-Glide exception to a Spring DAO exception.
     * 
     * @param ex The exception to translate
     * @return The translated exception, or null if the exception cannot be translated
     */
    @Override
    @Nullable
    public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        // Use ValkeyGlideExceptionConverter to translate exceptions
        return new ValkeyGlideExceptionConverter().convert(ex);
    }
}
