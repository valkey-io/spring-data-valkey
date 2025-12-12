/*
 * Copyright 2014-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.jedis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.RedisProtocol;
import redis.clients.jedis.util.Pool;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyPassword;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory.State;
import org.springframework.lang.Nullable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link JedisConnectionFactory}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author John Blum
 */
class JedisConnectionFactoryUnitTests {

	private JedisConnectionFactory connectionFactory;

	private static final ValkeySentinelConfiguration SINGLE_SENTINEL_CONFIG = new ValkeySentinelConfiguration()
			.master("mymaster").sentinel("127.0.0.1", 26379);

	private static final ValkeyClusterConfiguration CLUSTER_CONFIG = new ValkeyClusterConfiguration()
			.clusterNode("127.0.0.1", 6379).clusterNode("127.0.0.1", 6380);

	@Test // DATAREDIS-324
	void shouldInitSentinelPoolWhenSentinelConfigPresent() {

		connectionFactory = initSpyedConnectionFactory(SINGLE_SENTINEL_CONFIG, new JedisPoolConfig());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		verify(connectionFactory, times(1)).createValkeySentinelPool(eq(SINGLE_SENTINEL_CONFIG));
		verify(connectionFactory, never()).createValkeyPool();
	}

	@Test // DATAREDIS-324
	void shouldInitJedisPoolWhenNoSentinelConfigPresent() {

		connectionFactory = initSpyedConnectionFactory((ValkeySentinelConfiguration) null, new JedisPoolConfig());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		verify(connectionFactory, times(1)).createValkeyPool();
		verify(connectionFactory, never()).createValkeySentinelPool(any(ValkeySentinelConfiguration.class));
	}

	@Test // DATAREDIS-765
	void shouldRejectPoolDisablingWhenSentinelConfigPresent() {

		connectionFactory = new JedisConnectionFactory(new ValkeySentinelConfiguration());

		assertThatIllegalStateException().isThrownBy(() -> connectionFactory.setUsePool(false));
	}

	@Test // DATAREDIS-315
	void shouldInitConnectionCorrectlyWhenClusterConfigPresent() {

		connectionFactory = initSpyedConnectionFactory(CLUSTER_CONFIG, new JedisPoolConfig());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		verify(connectionFactory, times(1)).createCluster(eq(CLUSTER_CONFIG), any(GenericObjectPoolConfig.class));
		verify(connectionFactory, never()).createValkeyPool();
	}

	@Test // DATAREDIS-315
	void shouldCloseClusterCorrectlyOnFactoryDestruction() throws IOException {

		JedisCluster clusterMock = mock(JedisCluster.class);
		JedisConnectionFactory factory = new JedisConnectionFactory();
		ReflectionTestUtils.setField(factory, "cluster", clusterMock);
		ReflectionTestUtils.setField(factory, "state", new AtomicReference(State.STARTED));

		factory.destroy();

		verify(clusterMock, times(1)).close();
	}

	@Test // DATAREDIS-574
	void shouldReadStandalonePassword() {

		ValkeyStandaloneConfiguration envConfig = new ValkeyStandaloneConfiguration();
		envConfig.setPassword(ValkeyPassword.of("foo"));

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());

		assertThat(connectionFactory.getPassword()).isEqualTo("foo");
	}

	@Test // DATAREDIS-574
	void shouldWriteStandalonePassword() {

		ValkeyStandaloneConfiguration envConfig = new ValkeyStandaloneConfiguration();
		envConfig.setPassword(ValkeyPassword.of("foo"));

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());
		connectionFactory.setPassword("bar");

		assertThat(connectionFactory.getPassword()).isEqualTo("bar");
		assertThat(envConfig.getPassword()).isEqualTo(ValkeyPassword.of("bar"));
	}

	@Test // DATAREDIS-574
	void shouldReadSentinelPassword() {

		ValkeySentinelConfiguration envConfig = new ValkeySentinelConfiguration();
		envConfig.setPassword(ValkeyPassword.of("foo"));

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());

		assertThat(connectionFactory.getPassword()).isEqualTo("foo");
	}

	@Test // DATAREDIS-574
	void shouldWriteSentinelPassword() {

		ValkeySentinelConfiguration envConfig = new ValkeySentinelConfiguration();
		envConfig.setPassword(ValkeyPassword.of("foo"));

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());
		connectionFactory.setPassword("bar");

		assertThat(connectionFactory.getPassword()).isEqualTo("bar");
		assertThat(envConfig.getPassword()).isEqualTo(ValkeyPassword.of("bar"));
	}

	@Test // DATAREDIS-574
	void shouldReadClusterPassword() {

		ValkeyClusterConfiguration envConfig = new ValkeyClusterConfiguration();
		envConfig.setPassword(ValkeyPassword.of("foo"));

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());

		assertThat(connectionFactory.getPassword()).isEqualTo("foo");
	}

	@Test // DATAREDIS-574
	void shouldWriteClusterPassword() {

		ValkeyClusterConfiguration envConfig = new ValkeyClusterConfiguration();
		envConfig.setPassword(ValkeyPassword.of("foo"));

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());
		connectionFactory.setPassword("bar");

		assertThat(connectionFactory.getPassword()).isEqualTo("bar");
		assertThat(envConfig.getPassword()).isEqualTo(ValkeyPassword.of("bar"));
	}

	@Test // DATAREDIS-574
	void shouldReadStandaloneDatabaseIndex() {

		ValkeyStandaloneConfiguration envConfig = new ValkeyStandaloneConfiguration();
		envConfig.setDatabase(2);

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());

		assertThat(connectionFactory.getDatabase()).isEqualTo(2);
	}

	@Test // DATAREDIS-574
	void shouldWriteStandaloneDatabaseIndex() {

		ValkeyStandaloneConfiguration envConfig = new ValkeyStandaloneConfiguration();
		envConfig.setDatabase(2);

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());
		connectionFactory.setDatabase(3);

		assertThat(connectionFactory.getDatabase()).isEqualTo(3);
		assertThat(envConfig.getDatabase()).isEqualTo(3);
	}

	@Test // DATAREDIS-574
	void shouldReadSentinelDatabaseIndex() {

		ValkeySentinelConfiguration envConfig = new ValkeySentinelConfiguration();
		envConfig.setDatabase(2);

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());

		assertThat(connectionFactory.getDatabase()).isEqualTo(2);
	}

	@Test // DATAREDIS-574
	void shouldWriteSentinelDatabaseIndex() {

		ValkeySentinelConfiguration envConfig = new ValkeySentinelConfiguration();
		envConfig.setDatabase(2);

		connectionFactory = new JedisConnectionFactory(envConfig, JedisClientConfiguration.defaultConfiguration());
		connectionFactory.setDatabase(3);

		assertThat(connectionFactory.getDatabase()).isEqualTo(3);
		assertThat(envConfig.getDatabase()).isEqualTo(3);
	}

	@Test // DATAREDIS-574
	void shouldApplyClientConfiguration() throws NoSuchAlgorithmException {

		SSLParameters sslParameters = new SSLParameters();
		SSLContext context = SSLContext.getDefault();
		SSLSocketFactory socketFactory = context.getSocketFactory();
		JedisPoolConfig poolConfig = new JedisPoolConfig();

		JedisClientConfiguration configuration = JedisClientConfiguration.builder().useSsl() //
				.hostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()) //
				.sslParameters(sslParameters) //
				.sslSocketFactory(socketFactory).and() //
				.clientName("my-client") //
				.connectTimeout(Duration.of(10, ChronoUnit.MINUTES)) //
				.readTimeout(Duration.of(5, ChronoUnit.DAYS)) //
				.usePooling().poolConfig(poolConfig) //
				.build();

		connectionFactory = new JedisConnectionFactory(new ValkeyStandaloneConfiguration(), configuration);

		assertThat(connectionFactory.getClientConfiguration()).isSameAs(configuration);
		assertThat(connectionFactory.isUseSsl()).isTrue();
		assertThat(connectionFactory.getClientName()).isEqualTo("my-client");
		assertThat(connectionFactory.getTimeout()).isEqualTo((int) Duration.of(5, ChronoUnit.DAYS).toMillis());
		assertThat(connectionFactory.getUsePool()).isTrue();
		assertThat(connectionFactory.getPoolConfig()).isSameAs(poolConfig);
	}

	@Test // GH-3072
	void shouldInitializePool() throws Exception {

		JedisPoolConfig poolConfig = new JedisPoolConfig();
		Pool<Jedis> poolMock = mock(Pool.class);

		JedisClientConfiguration configuration = JedisClientConfiguration.builder() //
				.usePooling().poolConfig(poolConfig) //
				.build();

		connectionFactory = new JedisConnectionFactory(new ValkeyStandaloneConfiguration(), configuration) {
			@Override
			protected Pool<Jedis> createValkeyPool() {
				return poolMock;
			}
		};

		connectionFactory.afterPropertiesSet();

		verify(poolMock).preparePool();
	}

	@Test // DATAREDIS-574
	void shouldReturnStandaloneConfiguration() {

		ValkeyStandaloneConfiguration configuration = new ValkeyStandaloneConfiguration();
		connectionFactory = new JedisConnectionFactory(configuration, JedisClientConfiguration.defaultConfiguration());

		assertThat(connectionFactory.getStandaloneConfiguration()).isSameAs(configuration);
		assertThat(connectionFactory.getSentinelConfiguration()).isNull();
		assertThat(connectionFactory.getClusterConfiguration()).isNull();
	}

	@Test // DATAREDIS-574
	void shouldReturnSentinelConfiguration() {

		ValkeySentinelConfiguration configuration = new ValkeySentinelConfiguration();
		connectionFactory = new JedisConnectionFactory(configuration, JedisClientConfiguration.defaultConfiguration());

		assertThat(connectionFactory.getStandaloneConfiguration()).isNotNull();
		assertThat(connectionFactory.getSentinelConfiguration()).isSameAs(configuration);
		assertThat(connectionFactory.getClusterConfiguration()).isNull();
	}

	@Test // GH-2218
	void shouldConsiderSentinelAuthentication() {

		ValkeySentinelConfiguration configuration = new ValkeySentinelConfiguration();
		configuration.setSentinelUsername("sentinel");
		configuration.setSentinelPassword("the-password");
		connectionFactory = new JedisConnectionFactory(configuration, JedisClientConfiguration.defaultConfiguration());

		JedisClientConfig clientConfig = connectionFactory.createSentinelClientConfig(configuration);

		assertThat(clientConfig.getUser()).isEqualTo("sentinel");
		assertThat(clientConfig.getPassword()).isEqualTo("the-password");
	}

	@Test // DATAREDIS-574
	void shouldReturnClusterConfiguration() {

		ValkeyClusterConfiguration configuration = new ValkeyClusterConfiguration();
		connectionFactory = new JedisConnectionFactory(configuration, JedisClientConfiguration.defaultConfiguration());

		assertThat(connectionFactory.getStandaloneConfiguration()).isNotNull();
		assertThat(connectionFactory.getSentinelConfiguration()).isNull();
		assertThat(connectionFactory.getClusterConfiguration()).isSameAs(configuration);
	}

	@Test // DATAREDIS-574
	void shouldDenyChangesToImmutableClientConfiguration() throws NoSuchAlgorithmException {

		connectionFactory = new JedisConnectionFactory(new ValkeyStandaloneConfiguration(),
				JedisClientConfiguration.defaultConfiguration());

		assertThatIllegalStateException().isThrownBy(() -> connectionFactory.setClientName("foo"));
	}

	@Test // GH-2057
	void getConnectionShouldFailIfNotInitialized() {

		JedisConnectionFactory connectionFactory = new JedisConnectionFactory();

		assertThatIllegalStateException().isThrownBy(connectionFactory::getConnection);
		assertThatIllegalStateException().isThrownBy(connectionFactory::getClusterConnection);
		assertThatIllegalStateException().isThrownBy(connectionFactory::getSentinelConnection);
	}

	@Test // GH-2503, GH-2635
	void afterPropertiesTriggersConnectionInitialization() {

		JedisConnectionFactory connectionFactory = new JedisConnectionFactory();
		connectionFactory.afterPropertiesSet();

		assertThat(connectionFactory.isRunning()).isTrue();
	}

	@Test // GH-3007
	void clientConfigurationAppliesCustomizer() {

		JedisClientConfig resp3Config = apply(
				JedisClientConfiguration.builder().customize(DefaultJedisClientConfig.Builder::resp3).build());

		assertThat(resp3Config.getRedisProtocol()).isEqualTo(RedisProtocol.RESP3);

		JedisClientConfig resp2Config = apply(
				JedisClientConfiguration.builder().customize(it -> it.protocol(RedisProtocol.RESP2)).build());

		assertThat(resp2Config.getRedisProtocol()).isEqualTo(RedisProtocol.RESP2);
	}

	private static JedisClientConfig apply(JedisClientConfiguration configuration) {

		JedisConnectionFactory connectionFactory = new JedisConnectionFactory(new ValkeyStandaloneConfiguration(),
				configuration);
		connectionFactory.setEarlyStartup(false);
		connectionFactory.afterPropertiesSet();

		return (JedisClientConfig) ReflectionTestUtils.getField(connectionFactory, "clientConfig");
	}

	@Test // GH-2866
	void earlyStartupDoesNotStartConnectionFactory() {

		JedisConnectionFactory connectionFactory = new JedisConnectionFactory(new JedisPoolConfig());

		connectionFactory.setEarlyStartup(false);
		connectionFactory.afterPropertiesSet();

		assertThat(connectionFactory.isEarlyStartup()).isFalse();
		assertThat(connectionFactory.isAutoStartup()).isTrue();
		assertThat(connectionFactory.isRunning()).isFalse();

		assertThat(ReflectionTestUtils.getField(connectionFactory, "pool")).isNull();
	}

	private JedisConnectionFactory initSpyedConnectionFactory(ValkeySentinelConfiguration sentinelConfiguration,
			@Nullable JedisPoolConfig poolConfig) {

		Pool<Jedis> poolMock = mock(Pool.class);
		// we have to use a spy here as jedis would start connecting to valkey sentinels when the pool is created.
		JedisConnectionFactory connectionFactorySpy = spy(new JedisConnectionFactory(sentinelConfiguration, poolConfig));

		doReturn(poolMock).when(connectionFactorySpy).createValkeySentinelPool(any(ValkeySentinelConfiguration.class));
		doReturn(poolMock).when(connectionFactorySpy).createValkeyPool();

		return connectionFactorySpy;
	}

	private JedisConnectionFactory initSpyedConnectionFactory(ValkeyClusterConfiguration clusterConfiguration,
			@Nullable JedisPoolConfig poolConfig) {

		JedisCluster clusterMock = mock(JedisCluster.class);

		JedisConnectionFactory connectionFactorySpy = spy(new JedisConnectionFactory(clusterConfiguration, poolConfig));

		doReturn(clusterMock).when(connectionFactorySpy).createCluster(any(ValkeyClusterConfiguration.class),
				any(GenericObjectPoolConfig.class));

		doReturn(null).when(connectionFactorySpy).createValkeyPool();

		return connectionFactorySpy;
	}
}
