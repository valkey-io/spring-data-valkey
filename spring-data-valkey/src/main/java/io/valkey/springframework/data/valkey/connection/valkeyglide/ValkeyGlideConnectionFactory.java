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
import java.util.concurrent.ExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyPassword;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration.OpenTelemetryForGlide;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConnection;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import glide.api.OpenTelemetry;
import glide.api.OpenTelemetry.MetricsConfig;
import glide.api.OpenTelemetry.OpenTelemetryConfig;
import glide.api.OpenTelemetry.TracesConfig;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionFactory
    implements ValkeyConnectionFactory, InitializingBean, DisposableBean, SmartLifecycle {

    private static final Log logger = LogFactory.getLog(ValkeyGlideConnectionFactory.class);

    private final @Nullable ValkeyGlideClientConfiguration valkeyGlideConfiguration;
    private final ValkeyConfiguration configuration;
    
    // Bounded pool of pre-created adapters (with listeners pre-associated).
    private final LinkedBlockingQueue<UnifiedGlideClient> adapterPool;
    
    private @Nullable AsyncTaskExecutor executor;
    
    private boolean initialized = false;
    private boolean running = false;
    private boolean autoStartup = true;
    private boolean earlyStartup = true;
    private int phase = 0;
    private static final AtomicBoolean OTEL_INITIALIZED = new AtomicBoolean(false);
    private static @Nullable OpenTelemetryForGlide OTEL_INITIALIZED_CONFIG;
    private static final Object OTEL_LOCK = new Object();


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
        this.adapterPool = new LinkedBlockingQueue<>(valkeyGlideConfiguration.getMaxPoolSize());
    }

    /**
     * Constructs a new {@link ValkeyGlideConnectionFactory} instance with the given {@link ValkeyClusterConfiguration}.
     *
     * @param clusterConfiguration must not be {@literal null}
     * @since 3.0
     */
    public ValkeyGlideConnectionFactory(ValkeyClusterConfiguration clusterConfiguration) {
        this(clusterConfiguration, new DefaultValkeyGlideClientConfiguration());
    }

    /**
     * Constructs a new {@link ValkeyGlideConnectionFactory} instance with the given {@link ValkeyClusterConfiguration}
     * and {@link ValkeyGlideClientConfiguration}.
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
        this.adapterPool = new LinkedBlockingQueue<>(valkeyGlideConfiguration.getMaxPoolSize());
    }

    /**
     * Constructs a new {@link ValkeyGlideConnectionFactory} instance using the given {@link ValkeySentinelConfiguration}.
     *
     * @param sentinelConfiguration must not be {@literal null}.
     * @throws UnsupportedOperationException as Sentinel connections are not supported with Valkey-Glide.
     * @since 3.0
     */
    public ValkeyGlideConnectionFactory(ValkeySentinelConfiguration sentinelConfiguration) {
        throw new UnsupportedOperationException("Sentinel connections not supported with Valkey-Glide!");
    }

    /**
     * Constructs a new {@link ValkeyGlideConnectionFactory} instance using the given {@link ValkeySentinelConfiguration} and
     * {@link ValkeyGlideClientConfiguration}.
     *
     * @param sentinelConfiguration must not be {@literal null}.
     * @param clientConfiguration must not be {@literal null}.
     * @throws UnsupportedOperationException as Sentinel connections are not supported with Valkey-Glide.
     * @since 3.0
     */
    public ValkeyGlideConnectionFactory(ValkeySentinelConfiguration sentinelConfiguration,
            ValkeyGlideClientConfiguration clientConfiguration) {
        throw new UnsupportedOperationException("Sentinel connections not supported with Valkey-Glide!");
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
        this.adapterPool = new LinkedBlockingQueue<>(valkeyGlideConfiguration.getMaxPoolSize());
    }

    /**
     * Initialize the factory by pre-creating a pool of client instances if early startup is enabled.
     */
    @Override
    public void afterPropertiesSet() {
        if (initialized) {
            return;
        }
        
        // Skip eager initialization if early startup is disabled (useful for Spring Boot testing)
        if (!earlyStartup) {
            initialized = true;
            return;
        }
        
        // Initialize OpenTelemetry before creating any clients
        if (valkeyGlideConfiguration != null) {
            OpenTelemetryForGlide openTelemetryForGlide = valkeyGlideConfiguration.getOpenTelemetryForGlide();
            if (openTelemetryForGlide != null) {
                useOpenTelemetry(openTelemetryForGlide);
            }
        }

        // Pre-create pool of adapters based on configuration mode
        if (isClusterAware()) {
            for (int i = 0; i < valkeyGlideConfiguration.getMaxPoolSize(); i++) {
                adapterPool.offer(new ClusterGlideClientAdapter((ValkeyClusterConfiguration) configuration, valkeyGlideConfiguration));
            }
        } else {
            for (int i = 0; i < valkeyGlideConfiguration.getMaxPoolSize(); i++) {
                adapterPool.offer(new StandaloneGlideClientAdapter((ValkeyStandaloneConfiguration) configuration, valkeyGlideConfiguration));
            }
        }
        
        initialized = true;
    }

    @Override
    public ValkeyConnection getConnection() {
        afterPropertiesSet();
        
        // Return cluster connection when in cluster mode
        if (isClusterAware()) {
            return getClusterConnection();
        }
        
        // Get adapter from lock-free pool (or create new if pool is empty)
        UnifiedGlideClient adapter = adapterPool.poll();
        if (adapter == null) {
            if (ValkeyConfiguration.isClusterConfiguration(configuration)) {
                throw new IllegalStateException("Cannot create StandaloneGlideClientAdapter in cluster mode");
            }
            adapter = new StandaloneGlideClientAdapter((ValkeyStandaloneConfiguration) configuration, valkeyGlideConfiguration);
        }
        return new ValkeyGlideConnection(adapter, this);
    }

    @Override
    public ValkeyClusterConnection getClusterConnection() {
        afterPropertiesSet();
        
        if (!isClusterAware()) {
            throw new InvalidDataAccessResourceUsageException("Cluster mode is not configured!");
        }

        // Get adapter from lock-free pool (or create new if pool is empty)
        UnifiedGlideClient adapter = adapterPool.poll();
        if (adapter == null) {
            if (!ValkeyConfiguration.isClusterConfiguration(configuration)) {
                throw new IllegalStateException("Cannot create ClusterGlideClientAdapter in non-cluster mode");
            }
            adapter = new ClusterGlideClientAdapter((ValkeyClusterConfiguration) configuration, valkeyGlideConfiguration);
        }
        return new ValkeyGlideClusterConnection((ClusterGlideClientAdapter) adapter, this);
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
     * Release an adapter back to the bounded pool.
     * If the pool is full, the adapter is discarded (will be GC'd).
     * 
     * @param adapter the adapter to release
     */
    void releaseAdapter(UnifiedGlideClient adapter) {
        adapterPool.offer(adapter);
    }

    /**
     * Shut down the factory when this factory is destroyed.
     * Closes all pooled client instances.
     */
    @Override
    public void destroy() {
        initialized = false;
        running = false;
    }

    private boolean sameOtelConfig(@Nullable OpenTelemetryForGlide a, @Nullable OpenTelemetryForGlide b) {
        return java.util.Objects.equals(a, b);
    }

    private void useOpenTelemetry(OpenTelemetryForGlide openTelemetryForGlide) {
        Assert.notNull(openTelemetryForGlide, "OpenTelemetryForGlide must not be null");

        String tracesEndpoint = openTelemetryForGlide.tracesEndpoint();
        String metricsEndpoint = openTelemetryForGlide.metricsEndpoint();
        Integer samplePercentage = openTelemetryForGlide.samplePercentage();
        Long flushIntervalMs = openTelemetryForGlide.flushIntervalMs();

        boolean hasTraces = tracesEndpoint != null && !tracesEndpoint.isBlank();
        boolean hasMetrics = metricsEndpoint != null && !metricsEndpoint.isBlank();

        if (!hasTraces && !hasMetrics){
                throw new IllegalArgumentException(
                "OpenTelemetryForGlide requires at least one of tracesEndpoint or metricsEndpoint"
            );
        }
        
        if (samplePercentage != null) {
            Assert.isTrue(
                samplePercentage >= 0 && samplePercentage <= 100,
                "samplePercentage must be in range [0..100]"
            );
        }

        if (flushIntervalMs != null) {
            Assert.isTrue(
                flushIntervalMs > 0,
                "flushIntervalMs must be > 0"
            );
        }

        synchronized (OTEL_LOCK) {
            if (OTEL_INITIALIZED.getAndSet(true)) {
                if (sameOtelConfig(OTEL_INITIALIZED_CONFIG, openTelemetryForGlide)) {
                    return; // Already initialized with the same config
                }

                throw new IllegalStateException(
                    "OpenTelemetry is already initialized with a different configuration. "
                        + "existing=" + OTEL_INITIALIZED_CONFIG
                        + ", requested=" + openTelemetryForGlide
                );
            }

            OpenTelemetryConfig.Builder otelBuilder = OpenTelemetryConfig.builder();

            if (hasTraces) {
                TracesConfig.Builder tracesBuilder =
                    TracesConfig.builder().endpoint(tracesEndpoint);

                if (samplePercentage != null) {
                    tracesBuilder.samplePercentage(samplePercentage);
                }

                otelBuilder.traces(tracesBuilder.build());
            }

            if (hasMetrics) {
                otelBuilder.metrics(
                    MetricsConfig.builder()
                        .endpoint(metricsEndpoint)
                        .build()
                );
            }

            if (flushIntervalMs != null) {
                otelBuilder.flushIntervalMs(flushIntervalMs);
            }

            OTEL_INITIALIZED_CONFIG = openTelemetryForGlide;
            OpenTelemetry.init(otelBuilder.build());
        }
    }

    /**
     * Initializes the factory, starting it.
     */
    @Override
    public void start() {
        if (!initialized) {
            afterPropertiesSet();
        }
        running = true;
    }
    
    /**
     * Stops the factory.
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
     * @return the {@link ValkeyStandaloneConfiguration}, may be {@literal null}.
     * @since 3.0
     */
    @Nullable
    public ValkeyStandaloneConfiguration getStandaloneConfiguration() {
        return configuration instanceof ValkeyStandaloneConfiguration 
            ? (ValkeyStandaloneConfiguration) configuration 
            : null;
    }

    /**
     * @return the {@link ValkeyClusterConfiguration}, may be {@literal null}.
     * @since 3.0
     */
    @Nullable
    public ValkeyClusterConfiguration getClusterConfiguration() {
        return configuration instanceof ValkeyClusterConfiguration 
            ? (ValkeyClusterConfiguration) configuration 
            : null;
    }

    /**
     * @return the {@link ValkeySentinelConfiguration}, may be {@literal null}.
     * @since 3.0
     * @throws UnsupportedOperationException as Sentinel connections are not supported with Valkey-Glide.
     */
    @Nullable
    public ValkeySentinelConfiguration getSentinelConfiguration() {
        throw new UnsupportedOperationException("Sentinel connections not supported with Valkey-Glide!");
    }

    /**
     * Set the {@link AsyncTaskExecutor} to use for asynchronous command execution.
     *
     * @param executor {@link AsyncTaskExecutor executor} used to execute commands asynchronously.
     * @since 3.2
     */
    public void setExecutor(AsyncTaskExecutor executor) {
        if (executor != null) {
            logger.warn("AsyncTaskExecutor configuration ignored for Valkey-Glide. " +
                "Valkey-Glide provides async operations via CompletableFuture internally.");
        }
        this.executor = executor;
    }

    /**
     * Returns the current host.
     *
     * @return the host.
     */
    public String getHostName() {
        return ValkeyConfiguration.getHostOrElse(configuration, () -> "localhost");
    }

    /**
     * Returns the current port.
     *
     * @return the port.
     */
    public int getPort() {
        return ValkeyConfiguration.getPortOrElse(configuration, () -> 6379);
    }

    /**
     * Returns the index of the database.
     *
     * @return the database index.
     */
    public int getDatabase() {
        return ValkeyConfiguration.getDatabaseOrElse(configuration, () -> 0);
    }

    /**
     * Returns the client name.
     *
     * @return the client name or {@literal null} if not set.
     */
    @Nullable
    public String getClientName() {
        // Valkey Glide supports client names via CLIENT SETNAME/GETNAME command but not in configuration
        return null;
    }

	/**
	 * Returns whether to use SSL.
	 *
	 * @return use of SSL.
	 */
	public boolean isUseSsl() {
		return valkeyGlideConfiguration.isUseSsl();
	}

	/**
	 * Returns the password used for authenticating with the Valkey server.
	 *
	 * @return password for authentication or {@literal null} if not set.
	 */
	@Nullable
	public String getPassword() {
		return getValkeyPassword().map(String::new).orElse(null);
	}

    @Nullable
    private String getValkeyUsername() {
        return ValkeyConfiguration.getUsernameOrElse(configuration, () -> null);
    }

    private ValkeyPassword getValkeyPassword() {
        return ValkeyConfiguration.getPasswordOrElse(configuration, () -> ValkeyPassword.none());
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