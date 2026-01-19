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
package io.valkey.springframework.data.valkey.connection.lettuce;

import static io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnection.*;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SslVerifyMode;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.resource.ClientResources;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.ExceptionTranslationStrategy;
import io.valkey.springframework.data.valkey.PassThroughExceptionTranslationStrategy;
import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;
import io.valkey.springframework.data.valkey.connection.*;
import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration.ClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration.WithDatabaseIndex;
import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration.WithPassword;
import org.springframework.data.util.Optionals;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ValkeyConnectionFactory Connection factory} creating <a href="https://lettuce.io/">Lettuce</a>-based
 * connections.
 * <p>
 * This factory creates a new {@link LettuceConnection} on each call to {@link #getConnection()}. While multiple
 * {@link LettuceConnection}s share a single thread-safe native connection by default, {@link LettuceConnection} and its
 * {@link LettuceClusterConnection clustered variant} are not Thread-safe and instances should not be shared across
 * threads.
 * <p>
 * The shared native connection is never closed by {@link LettuceConnection}, therefore it is not validated by default
 * on {@link #getConnection()}. Use {@link #setValidateConnection(boolean)} to change this behavior if necessary. If
 * {@code shareNativeConnection} is {@literal true}, a shared connection will be used for regular operations and a
 * {@link LettuceConnectionProvider} will be used to select a connection for blocking and tx operations only, which
 * should not share a connection. If native connection sharing is disabled, new (or pooled) connections will be used for
 * all operations.
 * <p>
 * {@link LettuceConnectionFactory} should be configured using an environmental configuration and the
 * {@link LettuceConnectionFactory client configuration}. Lettuce supports the following environmental configurations:
 * <ul>
 * <li>{@link ValkeyStandaloneConfiguration}</li>
 * <li>{@link ValkeyStaticMasterReplicaConfiguration}</li>
 * <li>{@link ValkeySocketConfiguration}</li>
 * <li>{@link ValkeySentinelConfiguration}</li>
 * <li>{@link ValkeyClusterConfiguration}</li>
 * </ul>
 * <p>
 * This connection factory implements {@link InitializingBean} and {@link SmartLifecycle} for flexible lifecycle
 * control. It must be {@link #afterPropertiesSet() initialized} and {@link #start() started} before you can obtain a
 * connection. {@link #afterPropertiesSet() Initialization} {@link SmartLifecycle#start() starts} this bean
 * {@link #isEarlyStartup() early} by default. You can {@link SmartLifecycle#stop()} and {@link SmartLifecycle#start()
 * restart} this connection factory if needed. Disabling {@link #isEarlyStartup() early startup} leaves lifecycle
 * management to the container refresh if {@link #isAutoStartup() auto-startup} is enabled.
 *
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Balázs Németh
 * @author Ruben Cervilla
 * @author Luis De Bello
 * @author Andrea Como
 * @author Chris Bono
 * @author John Blum
 * @author Zhian Chen
 */
public class LettuceConnectionFactory implements ValkeyConnectionFactory, ReactiveValkeyConnectionFactory,
		InitializingBean, DisposableBean, SmartLifecycle {

	private static final ExceptionTranslationStrategy EXCEPTION_TRANSLATION = new PassThroughExceptionTranslationStrategy(
			LettuceExceptionConverter.INSTANCE);

	private int phase = 0; // in between min and max values
	private boolean autoStartup = true;
	private boolean earlyStartup = true;
	private boolean convertPipelineAndTxResults = true;
	private boolean eagerInitialization = false;

	private boolean shareNativeConnection = true;
	private boolean validateConnection = false;
	private @Nullable AbstractRedisClient client;

	private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);

	private @Nullable ClusterCommandExecutor clusterCommandExecutor;

	private @Nullable AsyncTaskExecutor executor;

	private final LettuceClientConfiguration clientConfiguration;

	private @Nullable LettuceConnectionProvider connectionProvider;
	private @Nullable LettuceConnectionProvider reactiveConnectionProvider;

	private final Log log = LogFactory.getLog(getClass());

	private final Lock lock = new ReentrantLock();

	private PipeliningFlushPolicy pipeliningFlushPolicy = PipeliningFlushPolicy.flushEachCommand();

	private @Nullable ValkeyConfiguration configuration;

	private ValkeyStandaloneConfiguration standaloneConfig = new ValkeyStandaloneConfiguration("localhost", 6379);

	private @Nullable SharedConnection<byte[]> connection;
	private @Nullable SharedConnection<ByteBuffer> reactiveConnection;

	/**
	 * Lifecycle state of this factory.
	 */
	enum State {
		CREATED, STARTING, STARTED, STOPPING, STOPPED, DESTROYED;
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance with default settings.
	 */
	public LettuceConnectionFactory() {
		this(new MutableLettuceClientConfiguration());
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance with default settings.
	 */
	public LettuceConnectionFactory(String host, int port) {
		this(new ValkeyStandaloneConfiguration(host, port), new MutableLettuceClientConfiguration());
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance given {@link LettuceClientConfiguration}.
	 *
	 * @param clientConfiguration must not be {@literal null}
	 * @since 2.0
	 */
	private LettuceConnectionFactory(LettuceClientConfiguration clientConfiguration) {

		Assert.notNull(clientConfiguration, "LettuceClientConfiguration must not be null");

		this.clientConfiguration = clientConfiguration;
		this.configuration = this.standaloneConfig;
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance using the given {@link ValkeySocketConfiguration}.
	 *
	 * @param valkeyConfiguration must not be {@literal null}.
	 * @since 2.1
	 */
	public LettuceConnectionFactory(ValkeyConfiguration valkeyConfiguration) {
		this(valkeyConfiguration, new MutableLettuceClientConfiguration());
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance using the given
	 * {@link ValkeyStaticMasterReplicaConfiguration} and {@link LettuceClientConfiguration}.
	 *
	 * @param valkeyConfiguration must not be {@literal null}.
	 * @param clientConfiguration must not be {@literal null}.
	 * @since 2.1
	 */
	public LettuceConnectionFactory(ValkeyConfiguration valkeyConfiguration,
			LettuceClientConfiguration clientConfiguration) {

		this(clientConfiguration);

		Assert.notNull(valkeyConfiguration, "ValkeyConfiguration must not be null");

		this.configuration = valkeyConfiguration;
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance using the given {@link ValkeyClusterConfiguration}
	 * applied to create a {@link RedisClusterClient}.
	 *
	 * @param clusterConfiguration must not be {@literal null}.
	 * @since 1.7
	 */
	public LettuceConnectionFactory(ValkeyClusterConfiguration clusterConfiguration) {
		this(clusterConfiguration, new MutableLettuceClientConfiguration());
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance using the given {@link ValkeyClusterConfiguration} and
	 * {@link LettuceClientConfiguration}.
	 *
	 * @param clusterConfiguration must not be {@literal null}.
	 * @param clientConfiguration must not be {@literal null}.
	 * @since 2.0
	 */
	public LettuceConnectionFactory(ValkeyClusterConfiguration clusterConfiguration,
			LettuceClientConfiguration clientConfiguration) {

		this(clientConfiguration);

		Assert.notNull(clusterConfiguration, "ValkeyClusterConfiguration must not be null");

		this.configuration = clusterConfiguration;
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance using the given {@link ValkeySentinelConfiguration}.
	 *
	 * @param sentinelConfiguration must not be {@literal null}.
	 * @since 1.6
	 */
	public LettuceConnectionFactory(ValkeySentinelConfiguration sentinelConfiguration) {
		this(sentinelConfiguration, new MutableLettuceClientConfiguration());
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance using the given {@link ValkeySentinelConfiguration} and
	 * {@link LettuceClientConfiguration}.
	 *
	 * @param sentinelConfiguration must not be {@literal null}.
	 * @param clientConfiguration must not be {@literal null}.
	 * @since 2.0
	 */
	public LettuceConnectionFactory(ValkeySentinelConfiguration sentinelConfiguration,
			LettuceClientConfiguration clientConfiguration) {

		this(clientConfiguration);

		Assert.notNull(sentinelConfiguration, "ValkeySentinelConfiguration must not be null");

		this.configuration = sentinelConfiguration;
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance with default settings.
	 */
	public LettuceConnectionFactory(ValkeyStandaloneConfiguration configuration) {
		this(configuration, new MutableLettuceClientConfiguration());
	}

	/**
	 * Constructs a new {@link LettuceConnectionFactory} instance using the given {@link ValkeyStandaloneConfiguration} and
	 * {@link LettuceClientConfiguration}.
	 *
	 * @param standaloneConfiguration must not be {@literal null}.
	 * @param clientConfiguration must not be {@literal null}.
	 * @since 2.0
	 */
	public LettuceConnectionFactory(ValkeyStandaloneConfiguration standaloneConfiguration,
			LettuceClientConfiguration clientConfiguration) {

		this(clientConfiguration);

		Assert.notNull(standaloneConfiguration, "ValkeyStandaloneConfiguration must not be null");

		this.standaloneConfig = standaloneConfiguration;
		this.configuration = this.standaloneConfig;
	}

	/**
	 * Creates a {@link ValkeyConfiguration} based on a {@link String URI} according to the following:
	 * <ul>
	 * <li>If {@code redisUri} contains sentinels, a {@link ValkeySentinelConfiguration} is returned</li>
	 * <li>If {@code redisUri} has a configured socket a {@link ValkeySocketConfiguration} is returned</li>
	 * <li>Otherwise a {@link ValkeyStandaloneConfiguration} is returned</li>
	 * </ul>
	 *
	 * @param redisUri the connection URI in the format of a {@link RedisURI}.
	 * @return an appropriate {@link ValkeyConfiguration} instance representing the Valkey URI.
	 * @since 2.5.3
	 * @see #createValkeyConfiguration(RedisURI)
	 * @see RedisURI
	 */
	public static ValkeyConfiguration createValkeyConfiguration(String redisUri) {

		Assert.hasText(redisUri, "RedisURI must not be null or empty");

		return createValkeyConfiguration(RedisURI.create(redisUri));
	}

	/**
	 * Creates a {@link ValkeyConfiguration} based on a {@link RedisURI} according to the following:
	 * <ul>
	 * <li>If {@link RedisURI} contains sentinels, a {@link ValkeySentinelConfiguration} is returned</li>
	 * <li>If {@link RedisURI} has a configured socket a {@link ValkeySocketConfiguration} is returned</li>
	 * <li>Otherwise a {@link ValkeyStandaloneConfiguration} is returned</li>
	 * </ul>
	 *
	 * @param redisUri the connection URI.
	 * @return an appropriate {@link ValkeyConfiguration} instance representing the Valkey URI.
	 * @since 2.5.3
	 * @see RedisURI
	 */
	public static ValkeyConfiguration createValkeyConfiguration(RedisURI redisUri) {

		Assert.notNull(redisUri, "RedisURI must not be null");

		if (!ObjectUtils.isEmpty(redisUri.getSentinels())) {
			return LettuceConverters.createValkeySentinelConfiguration(redisUri);
		}

		if (!ObjectUtils.isEmpty(redisUri.getSocket())) {
			return LettuceConverters.createValkeySocketConfiguration(redisUri);
		}

		return LettuceConverters.createValkeyStandaloneConfiguration(redisUri);
	}

	ClusterCommandExecutor getRequiredClusterCommandExecutor() {

		Assert.state(this.clusterCommandExecutor != null, "ClusterCommandExecutor not initialized");

		return this.clusterCommandExecutor;
	}

	/**
	 * Configures the {@link AsyncTaskExecutor executor} used to execute commands asynchronously across the cluster.
	 *
	 * @param executor {@link AsyncTaskExecutor executor} used to execute commands asynchronously across the cluster.
	 * @since 3.2
	 */
	public void setExecutor(AsyncTaskExecutor executor) {

		Assert.notNull(executor, "AsyncTaskExecutor must not be null");

		this.executor = executor;
	}

	/**
	 * Returns the current host.
	 *
	 * @return the host.
	 */
	public String getHostName() {
		return ValkeyConfiguration.getHostOrElse(configuration, standaloneConfig::getHostName);
	}

	/**
	 * Sets the hostname.
	 *
	 * @param hostName the hostname to set.
	 * @deprecated since 2.0, configure the hostname using {@link ValkeyStandaloneConfiguration}.
	 */
	@Deprecated
	public void setHostName(String hostName) {
		standaloneConfig.setHostName(hostName);
	}

	/**
	 * Returns the current port.
	 *
	 * @return the port.
	 */
	public int getPort() {
		return ValkeyConfiguration.getPortOrElse(configuration, standaloneConfig::getPort);
	}

	/**
	 * Sets the port.
	 *
	 * @param port the port to set.
	 * @deprecated since 2.0, configure the port using {@link ValkeyStandaloneConfiguration}.
	 */
	@Deprecated
	public void setPort(int port) {
		standaloneConfig.setPort(port);
	}

	/**
	 * Configures the flushing policy when using pipelining. If not set, defaults to
	 * {@link PipeliningFlushPolicy#flushEachCommand() flush on each command}.
	 *
	 * @param pipeliningFlushPolicy the flushing policy to control when commands get written to the Valkey connection.
	 * @see LettuceConnection#openPipeline()
	 * @see StatefulRedisConnection#flushCommands()
	 * @since 2.3
	 */
	public void setPipeliningFlushPolicy(PipeliningFlushPolicy pipeliningFlushPolicy) {

		Assert.notNull(pipeliningFlushPolicy, "PipeliningFlushingPolicy must not be null");

		this.pipeliningFlushPolicy = pipeliningFlushPolicy;
	}

	/**
	 * Returns the connection timeout (in milliseconds).
	 *
	 * @return connection timeout.
	 */
	public long getTimeout() {
		return getClientTimeout();
	}

	/**
	 * Sets the connection timeout (in milliseconds).
	 *
	 * @param timeout the timeout.
	 * @deprecated since 2.0, configure the timeout using {@link LettuceClientConfiguration}.
	 * @throws IllegalStateException if {@link LettuceClientConfiguration} is immutable.
	 */
	@Deprecated
	public void setTimeout(long timeout) {
		getMutableConfiguration().setTimeout(Duration.ofMillis(timeout));
	}

	/**
	 * Returns whether to use SSL.
	 *
	 * @return use of SSL.
	 */
	public boolean isUseSsl() {
		return clientConfiguration.isUseSsl();
	}

	/**
	 * Sets to use SSL connection.
	 *
	 * @param useSsl {@literal true} to use SSL.
	 * @deprecated since 2.0, configure SSL usage using {@link LettuceClientConfiguration}.
	 * @throws IllegalStateException if {@link LettuceClientConfiguration} is immutable.
	 */
	@Deprecated
	public void setUseSsl(boolean useSsl) {
		getMutableConfiguration().setUseSsl(useSsl);
	}

	/**
	 * Returns whether to verify certificate validity/hostname check when SSL is used.
	 *
	 * @return whether to verify peers when using SSL.
	 * @deprecated since 3.4, use {@link LettuceClientConfiguration#getVerifyMode()} instead.
	 */
	@Deprecated(since = "3.4")
	public boolean isVerifyPeer() {
		return clientConfiguration.isVerifyPeer();
	}

	/**
	 * Sets to use verify certificate validity/hostname check when SSL is used.
	 *
	 * @param verifyPeer {@literal false} not to verify hostname.
	 * @deprecated since 2.0, configure peer verification using {@link LettuceClientConfiguration}.
	 * @throws IllegalStateException if {@link LettuceClientConfiguration} is immutable.
	 */
	@Deprecated
	public void setVerifyPeer(boolean verifyPeer) {
		getMutableConfiguration().setVerifyPeer(verifyPeer);
	}

	/**
	 * Returns whether to issue a StartTLS.
	 *
	 * @return use of StartTLS.
	 */
	public boolean isStartTls() {
		return clientConfiguration.isStartTls();
	}

	/**
	 * Sets to issue StartTLS.
	 *
	 * @param startTls {@literal true} to issue StartTLS.
	 * @deprecated since 2.0, configure StartTLS using {@link LettuceClientConfiguration}.
	 * @throws IllegalStateException if {@link LettuceClientConfiguration} is immutable.
	 */
	@Deprecated
	public void setStartTls(boolean startTls) {
		getMutableConfiguration().setStartTls(startTls);
	}

	/**
	 * Indicates if validation of the native Lettuce connection is enabled.
	 *
	 * @return connection validation enabled.
	 */
	public boolean getValidateConnection() {
		return validateConnection;
	}

	/**
	 * Enables validation of the shared native Lettuce connection on calls to {@link #getConnection()}. A new connection
	 * will be created and used if validation fails.
	 * <p>
	 * Lettuce will automatically reconnect until close is called, which should never happen through
	 * {@link LettuceConnection} if a shared native connection is used, therefore the default is {@literal false}.
	 * <p>
	 * Setting this to {@literal true} will result in a round-trip call to the server on each new connection, so this
	 * setting should only be used if connection sharing is enabled and there is code that is actively closing the native
	 * Lettuce connection.
	 *
	 * @param validateConnection enable connection validation.
	 */
	public void setValidateConnection(boolean validateConnection) {
		this.validateConnection = validateConnection;
	}

	/**
	 * Indicates if multiple {@link LettuceConnection}s should share a single native connection.
	 *
	 * @return native connection shared.
	 */
	public boolean getShareNativeConnection() {
		return this.shareNativeConnection;
	}

	/**
	 * Enables multiple {@link LettuceConnection}s to share a single native connection. If set to {@literal false}, every
	 * operation on {@link LettuceConnection} will open and close a socket.
	 *
	 * @param shareNativeConnection enable connection sharing.
	 */
	public void setShareNativeConnection(boolean shareNativeConnection) {
		this.shareNativeConnection = shareNativeConnection;
	}

	/**
	 * Indicates {@link #setShareNativeConnection(boolean) shared connections} should be eagerly initialized. Eager
	 * initialization requires a running Valkey instance during {@link #start() startup} to allow early validation of
	 * connection factory configuration. Eager initialization also prevents blocking connect while using reactive API and
	 * is recommended for reactive API usage.
	 *
	 * @return {@literal true} if the shared connection is initialized upon {@link #start()}.
	 * @since 2.2
	 * @see #start()
	 */
	public boolean getEagerInitialization() {
		return this.eagerInitialization;
	}

	/**
	 * Enables eager initialization of {@link #setShareNativeConnection(boolean) shared connections}.
	 *
	 * @param eagerInitialization enable eager connection shared connection initialization upon
	 *          {@link #afterPropertiesSet()}.
	 * @since 2.2
	 */
	public void setEagerInitialization(boolean eagerInitialization) {
		this.eagerInitialization = eagerInitialization;
	}

	/**
	 * Returns the index of the database.
	 *
	 * @return the database index.
	 */
	public int getDatabase() {
		return ValkeyConfiguration.getDatabaseOrElse(configuration, standaloneConfig::getDatabase);
	}

	/**
	 * Sets the index of the database used by this connection factory. Default is 0.
	 *
	 * @param index database index.
	 * @deprecated since 3.2, configure the database index using {@link ValkeyStandaloneConfiguration},
	 *             {@link ValkeySocketConfiguration}, {@link ValkeySentinelConfiguration}, or
	 *             {@link ValkeyStaticMasterReplicaConfiguration}.
	 */
	@Deprecated
	public void setDatabase(int index) {

		Assert.isTrue(index >= 0, "invalid DB index (a positive index required)");

		if (ValkeyConfiguration.isDatabaseIndexAware(configuration)) {
			((WithDatabaseIndex) configuration).setDatabase(index);
			return;
		}

		standaloneConfig.setDatabase(index);
	}

	/**
	 * Returns the client name.
	 *
	 * @return the client name or {@literal null} if not set.
	 * @since 2.1
	 */
	@Nullable
	public String getClientName() {
		return clientConfiguration.getClientName().orElse(null);
	}

	/**
	 * Sets the client name used by this connection factory.
	 *
	 * @param clientName the client name. Can be {@literal null}.
	 * @since 2.1
	 * @deprecated configure the client name using {@link LettuceClientConfiguration}.
	 * @throws IllegalStateException if {@link LettuceClientConfiguration} is immutable.
	 */
	@Deprecated
	public void setClientName(@Nullable String clientName) {
		this.getMutableConfiguration().setClientName(clientName);
	}

	/**
	 * Returns the native {@link AbstractRedisClient} used by this instance. The client is initialized as part of
	 * {@link #afterPropertiesSet() the bean initialization lifecycle} and only available when this connection factory is
	 * initialized.
	 * <p>
	 * Depending on the configuration, the client can be either {@link RedisClient} or {@link RedisClusterClient}.
	 *
	 * @return the native {@link AbstractRedisClient}. Can be {@literal null} if not initialized.
	 * @since 2.5
	 * @see #afterPropertiesSet()
	 */
	@Nullable
	public AbstractRedisClient getNativeClient() {
		assertStarted();
		return this.client;
	}

	/**
	 * Returns the native {@link AbstractRedisClient} used by this instance. The client is initialized as part of
	 * {@link #afterPropertiesSet() the bean initialization lifecycle} and only available when this connection factory is
	 * initialized. Throws {@link IllegalStateException} if not yet initialized.
	 * <p>
	 * Depending on the configuration, the client can be either {@link RedisClient} or {@link RedisClusterClient}.
	 *
	 * @return the native {@link AbstractRedisClient}.
	 * @since 2.5
	 * @throws IllegalStateException if not yet initialized.
	 * @see #getNativeClient()
	 */
	public AbstractRedisClient getRequiredNativeClient() {

		AbstractRedisClient client = getNativeClient();

		Assert.state(client != null, "Client not yet initialized; Did you forget to call initialize the bean");

		return client;
	}

	@Nullable
	private String getValkeyUsername() {
		return ValkeyConfiguration.getUsernameOrElse(configuration, standaloneConfig::getUsername);
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

	private ValkeyPassword getValkeyPassword() {
		return ValkeyConfiguration.getPasswordOrElse(configuration, standaloneConfig::getPassword);
	}

	/**
	 * Sets the password used for authenticating with the Valkey server.
	 *
	 * @param password the password to set
	 * @deprecated since 2.0, configure the password using {@link ValkeyStandaloneConfiguration},
	 *             {@link ValkeySentinelConfiguration} or {@link ValkeyClusterConfiguration}.
	 */
	@Deprecated
	public void setPassword(String password) {

		if (ValkeyConfiguration.isAuthenticationAware(configuration)) {
			((WithPassword) configuration).setPassword(password);
			return;
		}

		standaloneConfig.setPassword(ValkeyPassword.of(password));
	}

	/**
	 * Returns the shutdown timeout for shutting down the RedisClient (in milliseconds).
	 *
	 * @return shutdown timeout.
	 * @since 1.6
	 */
	public long getShutdownTimeout() {
		return clientConfiguration.getShutdownTimeout().toMillis();
	}

	/**
	 * Sets the shutdown timeout for shutting down the RedisClient (in milliseconds).
	 *
	 * @param shutdownTimeout the shutdown timeout.
	 * @since 1.6
	 * @deprecated since 2.0, configure the shutdown timeout using {@link LettuceClientConfiguration}.
	 * @throws IllegalStateException if {@link LettuceClientConfiguration} is immutable.
	 */
	@Deprecated
	public void setShutdownTimeout(long shutdownTimeout) {
		getMutableConfiguration().setShutdownTimeout(Duration.ofMillis(shutdownTimeout));
	}

	/**
	 * Get the {@link ClientResources} to reuse infrastructure.
	 *
	 * @return {@literal null} if not set.
	 * @since 1.7
	 */
	@Nullable
	public ClientResources getClientResources() {
		return clientConfiguration.getClientResources().orElse(null);
	}

	/**
	 * Sets the {@link ClientResources} to reuse the client infrastructure. <br />
	 * Set to {@literal null} to not share resources.
	 *
	 * @param clientResources can be {@literal null}.
	 * @since 1.7
	 * @deprecated since 2.0, configure {@link ClientResources} using {@link LettuceClientConfiguration}.
	 * @throws IllegalStateException if {@link LettuceClientConfiguration} is immutable.
	 */
	@Deprecated
	public void setClientResources(ClientResources clientResources) {
		getMutableConfiguration().setClientResources(clientResources);
	}

	/**
	 * @return the {@link LettuceClientConfiguration}.
	 * @since 2.0
	 */
	public LettuceClientConfiguration getClientConfiguration() {
		return this.clientConfiguration;
	}

	/**
	 * @return the {@link ValkeyStandaloneConfiguration}.
	 * @since 2.0
	 */
	public ValkeyStandaloneConfiguration getStandaloneConfiguration() {
		return this.standaloneConfig;
	}

	/**
	 * @return the {@link ValkeySocketConfiguration} or {@literal null} if not set.
	 * @since 2.1
	 */
	@Nullable
	public ValkeySocketConfiguration getSocketConfiguration() {
		return isDomainSocketAware() ? (ValkeySocketConfiguration) this.configuration : null;
	}

	/**
	 * @return the {@link ValkeySentinelConfiguration}, may be {@literal null}.
	 * @since 2.0
	 */
	@Nullable
	public ValkeySentinelConfiguration getSentinelConfiguration() {
		return isValkeySentinelAware() ? (ValkeySentinelConfiguration) this.configuration : null;
	}

	/**
	 * @return the {@link ValkeyClusterConfiguration}, may be {@literal null}.
	 * @since 2.0
	 */
	@Nullable
	public ValkeyClusterConfiguration getClusterConfiguration() {
		return isClusterAware() ? (ValkeyClusterConfiguration) this.configuration : null;
	}

	@Override
	public int getPhase() {
		return this.phase;
	}

	/**
	 * Specify the lifecycle phase for pausing and resuming this executor. The default is {@code 0}.
	 *
	 * @since 3.2
	 * @see SmartLifecycle#getPhase()
	 */
	public void setPhase(int phase) {
		this.phase = phase;
	}

	/**
	 * @since 3.3
	 */
	@Override
	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	/**
	 * Configure if this Lifecycle connection factory should get started automatically by the container at the time that
	 * the containing ApplicationContext gets refreshed.
	 * <p>
	 * This connection factory defaults to early auto-startup during {@link #afterPropertiesSet()} and can potentially
	 * create Valkey connections early on in the lifecycle. See {@link #setEarlyStartup(boolean)} for delaying connection
	 * creation to the ApplicationContext refresh if auto-startup is enabled.
	 *
	 * @param autoStartup {@literal true} to automatically {@link #start()} the connection factory; {@literal false}
	 *          otherwise.
	 * @since 3.3
	 * @see #setEarlyStartup(boolean)
	 * @see #start()
	 */
	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	/**
	 * @return whether to {@link #start()} the component during {@link #afterPropertiesSet()}.
	 * @since 3.3
	 */
	public boolean isEarlyStartup() {
		return this.earlyStartup;
	}

	/**
	 * Configure if this InitializingBean's component Lifecycle should get started early by {@link #afterPropertiesSet()}
	 * at the time that the bean is initialized. The component defaults to auto-startup.
	 * <p>
	 * This method is related to {@link #setAutoStartup(boolean) auto-startup} and can be used to delay Valkey client
	 * startup until the ApplicationContext refresh. Disabling early startup does not disable auto-startup.
	 *
	 * @param earlyStartup {@literal true} to early {@link #start()} the component; {@literal false} otherwise.
	 * @since 3.3
	 * @see #setAutoStartup(boolean)
	 */
	public void setEarlyStartup(boolean earlyStartup) {
		this.earlyStartup = earlyStartup;
	}

	/**
	 * Specifies if pipelined results should be converted to the expected data type. If {@code false}, results of
	 * {@link LettuceConnection#closePipeline()} and {LettuceConnection#exec()} will be of the type returned by the
	 * Lettuce driver.
	 *
	 * @return {@code true} to convert pipeline and transaction results; {@code false} otherwise.
	 */
	@Override
	public boolean getConvertPipelineAndTxResults() {
		return convertPipelineAndTxResults;
	}

	/**
	 * Specifies if pipelined and transaction results should be converted to the expected data type. If {@code false},
	 * results of {@link LettuceConnection#closePipeline()} and {LettuceConnection#exec()} will be of the type returned by
	 * the Lettuce driver.
	 *
	 * @param convertPipelineAndTxResults {@code true} to convert pipeline and transaction results; {@code false}
	 *          otherwise.
	 */
	public void setConvertPipelineAndTxResults(boolean convertPipelineAndTxResults) {
		this.convertPipelineAndTxResults = convertPipelineAndTxResults;
	}

	/**
	 * @return true when {@link ValkeyStaticMasterReplicaConfiguration} is present.
	 * @since 2.1
	 */
	private boolean isStaticMasterReplicaAware() {
		return ValkeyConfiguration.isStaticMasterReplicaConfiguration(this.configuration);
	}

	/**
	 * @return true when {@link ValkeySentinelConfiguration} is present.
	 * @since 1.5
	 */
	public boolean isValkeySentinelAware() {
		return ValkeyConfiguration.isSentinelConfiguration(this.configuration);
	}

	/**
	 * @return true when {@link ValkeySocketConfiguration} is present.
	 * @since 2.1
	 */
	private boolean isDomainSocketAware() {
		return ValkeyConfiguration.isDomainSocketConfiguration(this.configuration);
	}

	/**
	 * @return true when {@link ValkeyClusterConfiguration} is present.
	 * @since 1.7
	 */
	public boolean isClusterAware() {
		return ValkeyConfiguration.isClusterConfiguration(this.configuration);
	}

	@Override
	public void start() {

		State current = this.state.getAndUpdate(state -> isCreatedOrStopped(state) ? State.STARTING : state);

		if (isCreatedOrStopped(current)) {

			AbstractRedisClient client = createClient();

			this.client = client;

			LettuceConnectionProvider connectionProvider = new ExceptionTranslatingConnectionProvider(
					createConnectionProvider(client, CODEC));

			this.connectionProvider = connectionProvider;
			this.reactiveConnectionProvider = new ExceptionTranslatingConnectionProvider(
					createConnectionProvider(client, LettuceReactiveValkeyConnection.CODEC));

			if (isClusterAware()) {
				this.clusterCommandExecutor = createClusterCommandExecutor((RedisClusterClient) client, connectionProvider);
			}

			this.state.set(State.STARTED);

			if (getEagerInitialization() && getShareNativeConnection()) {
				initConnection();
			}
		}
	}

	private static boolean isCreatedOrStopped(@Nullable State state) {
		return State.CREATED.equals(state) || State.STOPPED.equals(state);
	}

	private ClusterCommandExecutor createClusterCommandExecutor(RedisClusterClient client,
			LettuceConnectionProvider connectionProvider) {

		return new ClusterCommandExecutor(new LettuceClusterTopologyProvider(client),
				new LettuceClusterConnection.LettuceClusterNodeResourceProvider(connectionProvider), EXCEPTION_TRANSLATION,
				this.executor);
	}

	@Override
	public void stop() {

		if (state.compareAndSet(State.STARTED, State.STOPPING)) {

			resetConnection();

			dispose(connectionProvider);
			connectionProvider = null;

			dispose(reactiveConnectionProvider);
			reactiveConnectionProvider = null;

			if (client != null) {
				try {
					Duration quietPeriod = clientConfiguration.getShutdownQuietPeriod();
					Duration timeout = clientConfiguration.getShutdownTimeout();

					client.shutdown(quietPeriod.toMillis(), timeout.toMillis(), TimeUnit.MILLISECONDS);
					client = null;
				} catch (Exception ex) {
					if (log.isWarnEnabled()) {
						log.warn(ClassUtils.getShortName(client.getClass()) + " did not shut down gracefully.", ex);
					}
				}
			}
		}

		state.set(State.STOPPED);
	}

	@Override
	public boolean isRunning() {
		return State.STARTED.equals(this.state.get());
	}

	@Override
	public void afterPropertiesSet() {

		if (isEarlyStartup()) {
			start();
		}
	}

	@Override
	public void destroy() {

		stop();
		this.client = null;

		ClusterCommandExecutor clusterCommandExecutor = this.clusterCommandExecutor;

		if (clusterCommandExecutor != null) {
			try {
				clusterCommandExecutor.destroy();
				this.clusterCommandExecutor = null;
			} catch (Exception ex) {
				log.warn("Cannot properly close cluster command executor", ex);
			}
		}

		this.state.set(State.DESTROYED);
	}

	private void dispose(@Nullable LettuceConnectionProvider connectionProvider) {

		if (connectionProvider instanceof DisposableBean disposableBean) {
			try {
				disposableBean.destroy();
			} catch (Exception ex) {
				if (log.isWarnEnabled()) {
					log.warn(connectionProvider + " did not shut down gracefully.", ex);
				}
			}
		}
	}

	@Override
	public ValkeyConnection getConnection() {

		assertStarted();

		if (isClusterAware()) {
			return getClusterConnection();
		}

		LettuceConnection connection = doCreateLettuceConnection(getSharedConnection(), this.connectionProvider,
				getTimeout(), getDatabase());

		connection.setConvertPipelineAndTxResults(this.convertPipelineAndTxResults);

		return connection;
	}

	@Override
	public ValkeyClusterConnection getClusterConnection() {

		assertStarted();

		if (!isClusterAware()) {
			throw new InvalidDataAccessApiUsageException("Cluster is not configured");
		}

		RedisClusterClient clusterClient = (RedisClusterClient) this.client;

		StatefulRedisClusterConnection<byte[], byte[]> sharedConnection = getSharedClusterConnection();

		LettuceClusterTopologyProvider topologyProvider = new LettuceClusterTopologyProvider(clusterClient);

		return doCreateLettuceClusterConnection(sharedConnection, this.connectionProvider, topologyProvider,
				getRequiredClusterCommandExecutor(), this.clientConfiguration.getCommandTimeout());
	}

	@Override
	public ValkeySentinelConnection getSentinelConnection() {

		assertStarted();

		return new LettuceSentinelConnection(this.connectionProvider);
	}

	/**
	 * Customization hook for {@link LettuceConnection} creation.
	 *
	 * @param sharedConnection the shared {@link StatefulRedisConnection} if {@link #getShareNativeConnection()} is
	 *          {@literal true}; {@literal null} otherwise.
	 * @param connectionProvider the {@link LettuceConnectionProvider} to release connections.
	 * @param timeout command timeout in {@link TimeUnit#MILLISECONDS}.
	 * @param database database index to operate on.
	 * @return the {@link LettuceConnection}.
	 * @throws IllegalArgumentException if a required parameter is {@literal null}.
	 * @since 2.2
	 */
	protected LettuceConnection doCreateLettuceConnection(
			@Nullable StatefulRedisConnection<byte[], byte[]> sharedConnection, LettuceConnectionProvider connectionProvider,
			long timeout, int database) {

		LettuceConnection connection = new LettuceConnection(sharedConnection, connectionProvider, timeout, database);

		connection.setPipeliningFlushPolicy(this.pipeliningFlushPolicy);

		return connection;
	}

	/**
	 * Customization hook for {@link LettuceClusterConnection} creation.
	 *
	 * @param sharedConnection the shared {@link StatefulRedisConnection} if {@link #getShareNativeConnection()} is
	 *          {@literal true}; {@literal null} otherwise.
	 * @param connectionProvider the {@link LettuceConnectionProvider} to release connections.
	 * @param topologyProvider the {@link ClusterTopologyProvider}.
	 * @param clusterCommandExecutor the {@link ClusterCommandExecutor} to release connections.
	 * @param commandTimeout command timeout {@link Duration}.
	 * @return the {@link LettuceConnection}.
	 * @throws IllegalArgumentException if a required parameter is {@literal null}.
	 * @since 2.2
	 */
	protected LettuceClusterConnection doCreateLettuceClusterConnection(
			@Nullable StatefulRedisClusterConnection<byte[], byte[]> sharedConnection,
			LettuceConnectionProvider connectionProvider, ClusterTopologyProvider topologyProvider,
			ClusterCommandExecutor clusterCommandExecutor, Duration commandTimeout) {

		LettuceClusterConnection connection = new LettuceClusterConnection(sharedConnection, connectionProvider,
				topologyProvider, clusterCommandExecutor, commandTimeout);

		connection.setPipeliningFlushPolicy(this.pipeliningFlushPolicy);

		return connection;
	}

	@Override
	public LettuceReactiveValkeyConnection getReactiveConnection() {

		assertStarted();

		if (isClusterAware()) {
			return getReactiveClusterConnection();
		}

		return getShareNativeConnection()
				? new LettuceReactiveValkeyConnection(getSharedReactiveConnection(), reactiveConnectionProvider)
				: new LettuceReactiveValkeyConnection(reactiveConnectionProvider);
	}

	@Override
	public LettuceReactiveValkeyClusterConnection getReactiveClusterConnection() {

		assertStarted();

		if (!isClusterAware()) {
			throw new InvalidDataAccessApiUsageException("Cluster is not configured");
		}

		RedisClusterClient client = (RedisClusterClient) this.client;

		return getShareNativeConnection()
				? new LettuceReactiveValkeyClusterConnection(getSharedReactiveConnection(), reactiveConnectionProvider, client)
				: new LettuceReactiveValkeyClusterConnection(reactiveConnectionProvider, client);
	}

	/**
	 * Initialize the shared connection if {@link #getShareNativeConnection() native connection sharing} is enabled and
	 * reset any previously existing connection.
	 */
	public void initConnection() {

		resetConnection();

		if (isClusterAware()) {
			getSharedClusterConnection();
		} else {
			getSharedConnection();
		}

		getSharedReactiveConnection();
	}

	/**
	 * Reset the underlying shared Connection, to be reinitialized on next access.
	 */
	public void resetConnection() {

		doInLock(() -> {

			Optionals.toStream(Optional.ofNullable(this.connection), Optional.ofNullable(this.reactiveConnection))
					.forEach(SharedConnection::resetConnection);

			this.connection = null;
			this.reactiveConnection = null;
		});
	}

	/**
	 * Validate the shared connections and reinitialize if invalid.
	 */
	public void validateConnection() {

		assertStarted();

		getOrCreateSharedConnection().validateConnection();
		getOrCreateSharedReactiveConnection().validateConnection();
	}

	private SharedConnection<byte[]> getOrCreateSharedConnection() {

		return doInLock(() -> {

			if (this.connection == null) {
				this.connection = new SharedConnection<>(this.connectionProvider);
			}

			return this.connection;
		});
	}

	private SharedConnection<ByteBuffer> getOrCreateSharedReactiveConnection() {

		return doInLock(() -> {

			if (this.reactiveConnection == null) {
				this.reactiveConnection = new SharedConnection<>(this.reactiveConnectionProvider);
			}

			return this.reactiveConnection;
		});
	}

	@Override
	public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
		return EXCEPTION_TRANSLATION.translate(ex);
	}

	/**
	 * @return the shared connection using {@code byte[]} encoding for imperative API use. {@literal null} if
	 *         {@link #getShareNativeConnection() connection sharing} is disabled or when connected to Valkey Cluster.
	 */
	@Nullable
	protected StatefulRedisConnection<byte[], byte[]> getSharedConnection() {

		return shareNativeConnection && !isClusterAware()
				? (StatefulRedisConnection<byte[], byte[]>) getOrCreateSharedConnection().getConnection()
				: null;
	}

	/**
	 * @return the shared cluster connection using {@code byte[]} encoding for imperative API use. {@literal null} if
	 *         {@link #getShareNativeConnection() connection sharing} is disabled or when connected to Valkey
	 *         Standalone/Sentinel/Master-Replica.
	 * @since 2.5.7
	 */
	@Nullable
	protected StatefulRedisClusterConnection<byte[], byte[]> getSharedClusterConnection() {

		return shareNativeConnection && isClusterAware()
				? (StatefulRedisClusterConnection<byte[], byte[]>) getOrCreateSharedConnection().getConnection()
				: null;
	}

	/**
	 * @return the shared connection using {@link ByteBuffer} encoding for reactive API use. {@literal null} if
	 *         {@link #getShareNativeConnection() connection sharing} is disabled.
	 * @since 2.0.1
	 */
	@Nullable
	protected StatefulConnection<ByteBuffer, ByteBuffer> getSharedReactiveConnection() {
		return shareNativeConnection ? getOrCreateSharedReactiveConnection().getConnection() : null;
	}

	LettuceConnectionProvider createConnectionProvider(AbstractRedisClient client, RedisCodec<?, ?> codec) {

		LettuceConnectionProvider connectionProvider = doCreateConnectionProvider(client, codec);

		if (this.clientConfiguration instanceof LettucePoolingClientConfiguration poolingClientConfiguration) {
			return new LettucePoolingConnectionProvider(connectionProvider, poolingClientConfiguration);
		}

		return connectionProvider;
	}

	/**
	 * Create a {@link LettuceConnectionProvider} given {@link AbstractRedisClient} and {@link RedisCodec}. Configuration
	 * of this connection factory specifies the type of the created connection provider. This method creates either a
	 * {@link LettuceConnectionProvider} for either {@link RedisClient} or {@link RedisClusterClient}. Subclasses may
	 * override this method to decorate the connection provider.
	 *
	 * @param client either {@link RedisClient} or {@link RedisClusterClient}, must not be {@literal null}.
	 * @param codec used for connection creation, must not be {@literal null}. By default, a {@code byte[]} codec.
	 *          Reactive connections require a {@link java.nio.ByteBuffer} codec.
	 * @return the connection provider.
	 * @since 2.1
	 */
	protected LettuceConnectionProvider doCreateConnectionProvider(AbstractRedisClient client, RedisCodec<?, ?> codec) {

		return isStaticMasterReplicaAware() ? createStaticMasterReplicaConnectionProvider((RedisClient) client, codec)
				: isClusterAware() ? createClusterConnectionProvider((RedisClusterClient) client, codec)
						: createStandaloneConnectionProvider((RedisClient) client, codec);
	}

	@SuppressWarnings("all")
	private StaticMasterReplicaConnectionProvider createStaticMasterReplicaConnectionProvider(RedisClient client,
			RedisCodec<?, ?> codec) {

		List<RedisURI> nodes = ((ValkeyStaticMasterReplicaConfiguration) this.configuration).getNodes().stream()
				.map(it -> createValkeyURIAndApplySettings(it.getHostName(), it.getPort()))
				.peek(it -> it.setDatabase(getDatabase())).collect(Collectors.toList());

		return new StaticMasterReplicaConnectionProvider(client, codec, nodes,
				getClientConfiguration().getReadFrom().orElse(null));
	}

	private ClusterConnectionProvider createClusterConnectionProvider(RedisClusterClient client, RedisCodec<?, ?> codec) {
		return new ClusterConnectionProvider(client, codec, getClientConfiguration().getReadFrom().orElse(null));
	}

	private StandaloneConnectionProvider createStandaloneConnectionProvider(RedisClient client, RedisCodec<?, ?> codec) {
		return new StandaloneConnectionProvider(client, codec, getClientConfiguration().getReadFrom().orElse(null));
	}

	protected AbstractRedisClient createClient() {

		return isStaticMasterReplicaAware() ? createStaticMasterReplicaClient()
				: isValkeySentinelAware() ? createSentinelClient()
						: isClusterAware() ? createClusterClient() : createBasicClient();
	}

	private RedisClient createStaticMasterReplicaClient() {

		RedisClient valkeyClient = this.clientConfiguration.getClientResources().map(RedisClient::create)
				.orElseGet(RedisClient::create);

		this.clientConfiguration.getClientOptions().ifPresent(valkeyClient::setOptions);

		return valkeyClient;
	}

	private RedisClient createSentinelClient() {

		RedisURI redisURI = getSentinelValkeyURI();

		RedisClient valkeyClient = this.clientConfiguration.getClientResources()
				.map(clientResources -> RedisClient.create(clientResources, redisURI))
				.orElseGet(() -> RedisClient.create(redisURI));

		this.clientConfiguration.getClientOptions().ifPresent(valkeyClient::setOptions);

		return valkeyClient;
	}

	@SuppressWarnings("all")
	private RedisURI getSentinelValkeyURI() {

		ValkeySentinelConfiguration sentinelConfiguration = (ValkeySentinelConfiguration) this.configuration;

		RedisURI redisUri = LettuceConverters.sentinelConfigurationToValkeyURI(sentinelConfiguration);

		applyToAll(redisUri, it -> {

			this.clientConfiguration.getClientName().ifPresent(it::setClientName);

			it.setSsl(this.clientConfiguration.isUseSsl());
			it.setVerifyPeer(this.clientConfiguration.getVerifyMode());
			it.setStartTls(this.clientConfiguration.isStartTls());
			it.setTimeout(this.clientConfiguration.getCommandTimeout());
		});

		redisUri.setDatabase(getDatabase());

		this.clientConfiguration.getValkeyCredentialsProviderFactory().ifPresent(factory -> {

			redisUri.setCredentialsProvider(factory.createCredentialsProvider(this.configuration));

			RedisCredentialsProvider sentinelCredentials = factory
					.createSentinelCredentialsProvider((ValkeySentinelConfiguration) this.configuration);

			redisUri.getSentinels().forEach(it -> it.setCredentialsProvider(sentinelCredentials));
		});

		return redisUri;
	}

	@SuppressWarnings("all")
	private RedisClusterClient createClusterClient() {

		List<RedisURI> initialUris = new ArrayList<>();

		ClusterConfiguration clusterConfiguration = (ClusterConfiguration) this.configuration;

		clusterConfiguration.getClusterNodes().stream()
				.map(node -> createValkeyURIAndApplySettings(node.getHost(), node.getPort())).forEach(initialUris::add);

		RedisClusterClient clusterClient = this.clientConfiguration.getClientResources()
				.map(clientResources -> RedisClusterClient.create(clientResources, initialUris))
				.orElseGet(() -> RedisClusterClient.create(initialUris));

		clusterClient.setOptions(getClusterClientOptions(clusterConfiguration));

		return clusterClient;
	}

	private ClusterClientOptions getClusterClientOptions(ClusterConfiguration clusterConfiguration) {

		Optional<ClientOptions> clientOptions = this.clientConfiguration.getClientOptions();

		Optional<ClusterClientOptions> clusterClientOptions = clientOptions.filter(ClusterClientOptions.class::isInstance)
				.map(ClusterClientOptions.class::cast);

		ClusterClientOptions resolvedClusterClientOptions = clusterClientOptions.orElseGet(() -> clientOptions
				.map(it -> ClusterClientOptions.builder(it).build()).orElseGet(ClusterClientOptions::create));

		if (clusterConfiguration.getMaxRedirects() != null) {
			return resolvedClusterClientOptions.mutate().maxRedirects(clusterConfiguration.getMaxRedirects()).build();
		}

		return resolvedClusterClientOptions;
	}

	@SuppressWarnings("all")
	private RedisClient createBasicClient() {

		RedisURI uri = isDomainSocketAware() ? createValkeySocketURIAndApplySettings(getSocketConfiguration().getSocket())
				: createValkeyURIAndApplySettings(getHostName(), getPort());

		RedisClient valkeyClient = this.clientConfiguration.getClientResources()
				.map(clientResources -> RedisClient.create(clientResources, uri)).orElseGet(() -> RedisClient.create(uri));

		this.clientConfiguration.getClientOptions().ifPresent(valkeyClient::setOptions);

		return valkeyClient;
	}

	private void assertStarted() {

		State current = this.state.get();

		if (State.STARTED.equals(current)) {
			return;
		}

		switch (current) {
			case CREATED, STOPPED -> throw new IllegalStateException(
					"LettuceConnectionFactory has been %s. Use start() to initialize it".formatted(current));
			case DESTROYED ->
				throw new IllegalStateException("LettuceConnectionFactory was destroyed and cannot be used anymore");
			default -> throw new IllegalStateException("LettuceConnectionFactory is %s".formatted(current));
		}
	}

	private static void applyToAll(RedisURI source, Consumer<RedisURI> action) {

		action.accept(source);
		source.getSentinels().forEach(action);
	}

	private RedisURI createValkeyURIAndApplySettings(String host, int port) {

		RedisURI.Builder builder = RedisURI.Builder.redis(host, port);

		applyAuthentication(builder);

		clientConfiguration.getClientName().ifPresent(builder::withClientName);

		builder.withDatabase(getDatabase());
		builder.withSsl(clientConfiguration.isUseSsl());
		builder.withVerifyPeer(clientConfiguration.getVerifyMode());
		builder.withStartTls(clientConfiguration.isStartTls());
		builder.withTimeout(clientConfiguration.getCommandTimeout());

		return builder.build();
	}

	private RedisURI createValkeySocketURIAndApplySettings(String socketPath) {

		return applyAuthentication(RedisURI.Builder.socket(socketPath))
				.withTimeout(this.clientConfiguration.getCommandTimeout()).withDatabase(getDatabase()).build();
	}

	private RedisURI.Builder applyAuthentication(RedisURI.Builder builder) {

		String username = getValkeyUsername();

		if (StringUtils.hasText(username)) {
			// See https://github.com/lettuce-io/lettuce-core/issues/1404
			builder.withAuthentication(username, new String(getValkeyPassword().toOptional().orElse(new char[0])));
		} else {
			getValkeyPassword().toOptional().ifPresent(builder::withPassword);
		}

		this.clientConfiguration.getValkeyCredentialsProviderFactory()
				.ifPresent(factory -> builder.withAuthentication(factory.createCredentialsProvider(this.configuration)));

		return builder;
	}

	private MutableLettuceClientConfiguration getMutableConfiguration() {

		Assert.state(clientConfiguration instanceof MutableLettuceClientConfiguration,
				() -> "Client configuration must be instance of MutableLettuceClientConfiguration but is %s"
						.formatted(ClassUtils.getShortName(clientConfiguration.getClass())));

		return (MutableLettuceClientConfiguration) clientConfiguration;
	}

	private long getClientTimeout() {
		return clientConfiguration.getCommandTimeout().toMillis();
	}

	private void doInLock(Runnable runnable) {
		doInLock(() -> {
			runnable.run();
			return null;
		});
	}

	private <T> T doInLock(Supplier<T> supplier) {

		this.lock.lock();

		try {
			return supplier.get();
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Wrapper for shared connections. Keeps track of the connection lifecycle. The wrapper is Thread-safe as it
	 * synchronizes concurrent calls by blocking.
	 *
	 * @param <E> connection encoding.
	 * @author Mark Paluch
	 * @author Christoph Strobl
	 * @author Jonn Blum
	 * @since 2.1
	 */
	class SharedConnection<E> {

		private final LettuceConnectionProvider connectionProvider;

		private @Nullable StatefulConnection<E, E> connection;

		SharedConnection(LettuceConnectionProvider connectionProvider) {
			this.connectionProvider = connectionProvider;
		}

		/**
		 * Returns a valid Lettuce {@link StatefulConnection connection}.
		 * <p>
		 * Initializes and validates the connection if {@link #setValidateConnection(boolean) enabled}.
		 *
		 * @return the new Lettuce {@link StatefulConnection connection}.
		 */
		@Nullable
		StatefulConnection<E, E> getConnection() {

			return doInLock(() -> {

				if (this.connection == null) {
					this.connection = getNativeConnection();
				}

				if (getValidateConnection()) {
					validateConnection();
				}

				return this.connection;
			});
		}

		/**
		 * Acquire a {@link StatefulConnection native connection} from the associated {@link LettuceConnectionProvider}.
		 *
		 * @return a new {@link StatefulConnection native connection}.
		 * @see io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionProvider#getConnection(Class)
		 * @see io.lettuce.core.api.StatefulConnection
		 */
		@SuppressWarnings("unchecked")
		private StatefulConnection<E, E> getNativeConnection() {
			return this.connectionProvider.getConnection(StatefulConnection.class);
		}

		/**
		 * Null-safe operation to evaluate whether the given {@link StatefulConnection connetion} is
		 * {@link StatefulConnection#isOpen() open}.
		 *
		 * @param connection {@link StatefulConnection} to evaluate.
		 * @return a boolean value indicating whether the given {@link StatefulConnection} is not {@literal null} and is
		 *         {@link StatefulConnection#isOpen() open}.
		 * @see io.lettuce.core.api.StatefulConnection#isOpen()
		 */
		private boolean isOpen(@Nullable StatefulConnection<?, ?> connection) {
			return connection != null && connection.isOpen();
		}

		/**
		 * Validate the {@link StatefulConnection connection}.
		 * <p>
		 * {@link StatefulConnection Connections} are considered valid if they can send/receive ping packets. Invalid
		 * {@link StatefulConnection connections} will be closed and the connection state will be reset.
		 */
		void validateConnection() {

			doInLock(() -> {

				StatefulConnection<?, ?> connection = this.connection;
				boolean valid = false;

				if (isOpen(connection)) {
					try {

						if (connection instanceof StatefulRedisConnection<?, ?> statefulConnection) {
							statefulConnection.sync().ping();
						}

						if (connection instanceof StatefulRedisClusterConnection<?, ?> statefulClusterConnection) {
							statefulClusterConnection.sync().ping();
						}

						valid = true;

					} catch (Exception ex) {
						log.debug("Validation failed", ex);
					}
				}

				if (!valid) {
					log.info("Validation of shared connection failed; Creating a new connection.");
					resetConnection();
					this.connection = getNativeConnection();
				}
			});
		}

		/**
		 * Reset the underlying shared {@link StatefulConnection connection}, to be reinitialized on next access.
		 */
		void resetConnection() {

			doInLock(() -> {

				StatefulConnection<?, ?> connection = this.connection;

				if (connection != null) {
					this.connectionProvider.release(connection);
				}

				this.connection = null;
			});
		}
	}

	/**
	 * Mutable implementation of {@link LettuceClientConfiguration}.
	 *
	 * @author Mark Paluch
	 * @author Christoph Strobl
	 */
	static class MutableLettuceClientConfiguration implements LettuceClientConfiguration {

		private boolean useSsl;
		private SslVerifyMode verifyMode = SslVerifyMode.FULL;
		private boolean startTls;

		private @Nullable ClientResources clientResources;

		private Duration timeout = Duration.ofSeconds(RedisURI.DEFAULT_TIMEOUT);
		private Duration shutdownTimeout = Duration.ofMillis(100);

		private @Nullable String clientName;

		@Override
		public boolean isUseSsl() {
			return useSsl;
		}

		void setUseSsl(boolean useSsl) {
			this.useSsl = useSsl;
		}

		@Override
		public boolean isVerifyPeer() {
			return verifyMode != SslVerifyMode.NONE;
		}

		@Override
		public SslVerifyMode getVerifyMode() {
			return verifyMode;
		}

		void setVerifyPeer(boolean verifyPeer) {
			this.verifyMode = verifyPeer ? SslVerifyMode.FULL : SslVerifyMode.NONE;
		}

		@Override
		public boolean isStartTls() {
			return startTls;
		}

		void setStartTls(boolean startTls) {
			this.startTls = startTls;
		}

		@Override
		public Optional<ClientResources> getClientResources() {
			return Optional.ofNullable(clientResources);
		}

		void setClientResources(ClientResources clientResources) {
			this.clientResources = clientResources;
		}

		@Override
		public Optional<ClientOptions> getClientOptions() {
			return Optional.empty();
		}

		@Override
		public Optional<ReadFrom> getReadFrom() {
			return Optional.empty();
		}

		@Override
		public Optional<ValkeyCredentialsProviderFactory> getValkeyCredentialsProviderFactory() {
			return Optional.empty();
		}

		@Override
		public Optional<String> getClientName() {
			return Optional.ofNullable(clientName);
		}

		/**
		 * @param clientName can be {@literal null}.
		 * @since 2.1
		 */
		void setClientName(@Nullable String clientName) {
			this.clientName = clientName;
		}

		@Override
		public Duration getCommandTimeout() {
			return timeout;
		}

		void setTimeout(Duration timeout) {
			this.timeout = timeout;
		}

		@Override
		public Duration getShutdownTimeout() {
			return shutdownTimeout;
		}

		void setShutdownTimeout(Duration shutdownTimeout) {
			this.shutdownTimeout = shutdownTimeout;
		}

		@Override
		public Duration getShutdownQuietPeriod() {
			return shutdownTimeout;
		}
	}

	/**
	 * {@link LettuceConnectionProvider} that translates connection exceptions into {@link RedisConnectionException}.
	 */
	private static class ExceptionTranslatingConnectionProvider
			implements LettuceConnectionProvider, LettuceConnectionProvider.TargetAware, DisposableBean {

		private final LettuceConnectionProvider delegate;

		public ExceptionTranslatingConnectionProvider(LettuceConnectionProvider delegate) {
			this.delegate = delegate;
		}

		@Override
		public <T extends StatefulConnection<?, ?>> T getConnection(Class<T> connectionType) {

			try {
				return delegate.getConnection(connectionType);
			} catch (RuntimeException ex) {
				throw translateException(ex);
			}
		}

		@Override
		public <T extends StatefulConnection<?, ?>> T getConnection(Class<T> connectionType, RedisURI redisURI) {

			try {
				return ((TargetAware) delegate).getConnection(connectionType, redisURI);
			} catch (RuntimeException ex) {
				throw translateException(ex);
			}
		}

		@Override
		public <T extends StatefulConnection<?, ?>> CompletionStage<T> getConnectionAsync(Class<T> connectionType) {

			CompletableFuture<T> future = new CompletableFuture<>();

			delegate.getConnectionAsync(connectionType).whenComplete((t, throwable) -> {

				if (throwable != null) {
					future.completeExceptionally(translateException(throwable));
				} else {
					future.complete(t);
				}
			});

			return future;
		}

		@Override
		public <T extends StatefulConnection<?, ?>> CompletionStage<T> getConnectionAsync(Class<T> connectionType,
				RedisURI redisURI) {

			CompletableFuture<T> future = new CompletableFuture<>();

			((TargetAware) delegate).getConnectionAsync(connectionType, redisURI).whenComplete((t, throwable) -> {

				if (throwable != null) {
					future.completeExceptionally(translateException(throwable));
				} else {
					future.complete(t);
				}
			});

			return future;
		}

		@Override
		public void release(StatefulConnection<?, ?> connection) {
			delegate.release(connection);
		}

		@Override
		public CompletableFuture<Void> releaseAsync(StatefulConnection<?, ?> connection) {
			return delegate.releaseAsync(connection);
		}

		@Override
		public void destroy() throws Exception {

			if (delegate instanceof DisposableBean disposableBean) {
				disposableBean.destroy();
			}
		}

		private RuntimeException translateException(Throwable cause) {
			return cause instanceof ValkeyConnectionFailureException connectionFailure ? connectionFailure
					: new ValkeyConnectionFailureException("Unable to connect to Valkey", cause);
		}
	}
}
