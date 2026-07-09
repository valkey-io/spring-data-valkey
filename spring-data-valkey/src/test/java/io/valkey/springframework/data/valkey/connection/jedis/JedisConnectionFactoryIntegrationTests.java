/*
 * Copyright 2017-present the original author or authors.
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

import redis.clients.jedis.RedisProtocol;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.csc.Cache;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.task.AsyncTaskExecutor;
import io.valkey.springframework.data.valkey.SettingsUtils;
import io.valkey.springframework.data.valkey.connection.ClusterCommandExecutor;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyVersion;
import io.valkey.springframework.data.valkey.util.ValkeyClientLibraryInfo;

/**
 * Integration tests for {@link JedisConnectionFactory}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
class JedisConnectionFactoryIntegrationTests {

	private @Nullable JedisConnectionFactory factory;

	@AfterEach
	void tearDown() {

		if (factory != null) {
			factory.destroy();
		}
	}

	@Test // DATAREDIS-574
	void shouldInitializeWithStandaloneConfiguration() {

		factory = new JedisConnectionFactory(
				new ValkeyStandaloneConfiguration(SettingsUtils.getHost(), SettingsUtils.getPort()),
				JedisClientConfiguration.defaultConfiguration());
		factory.afterPropertiesSet();
		factory.start();

		try (ValkeyConnection connection = factory.getConnection()) {
			assertThat(connection.ping()).isEqualTo("PONG");
		}
	}

	@Test // DATAREDIS-575
	void connectionAppliesClientName() {

		factory = new JedisConnectionFactory(
				new ValkeyStandaloneConfiguration(SettingsUtils.getHost(), SettingsUtils.getPort()),
				JedisClientConfiguration.builder().clientName("clientName").build());
		factory.afterPropertiesSet();
		factory.start();

		ValkeyConnection connection = factory.getConnection();

		assertThat(connection.getClientName()).isEqualTo("clientName");
	}

	@Test // GH-3268
	@EnabledOnValkeyVersion("7.2")
	void clientListReportsJedisLibNameWithSpringDataSuffix() {

		factory = new JedisConnectionFactory(
				new ValkeyStandaloneConfiguration(SettingsUtils.getHost(), SettingsUtils.getPort()),
				JedisClientConfiguration.builder().clientName("clientNameLibName").build());
		factory.afterPropertiesSet();
		factory.start();

		try (ValkeyConnection connection = factory.getConnection()) {

			ValkeyClientInfo self = connection.serverCommands().getClientList()
					.stream()
					.filter(info -> "clientNameLibName".equals(info.getName()))
					.findFirst()
					.orElseThrow();

			String expectedUpstreamDriver = "%s_v%s".formatted(ValkeyClientLibraryInfo.FRAMEWORK_NAME, ValkeyClientLibraryInfo.getVersion());
			assertThat(self.get("lib-name")).startsWith("jedis(" + expectedUpstreamDriver);
		}
		finally {
			factory.destroy();
		}
	}

	@Test // GH-2503
	void startStopStartConnectionFactory() {

		factory = new JedisConnectionFactory(
				new ValkeyStandaloneConfiguration(SettingsUtils.getHost(), SettingsUtils.getPort()),
				JedisClientConfiguration.defaultConfiguration());
		factory.afterPropertiesSet();

		factory.start();
		assertThat(factory.isRunning()).isTrue();

		factory.stop();
		assertThat(factory.isRunning()).isFalse();
		assertThatIllegalStateException().isThrownBy(() -> factory.getConnection());

		factory.start();
		assertThat(factory.isRunning()).isTrue();
		try (ValkeyConnection connection = factory.getConnection()) {
			assertThat(connection.ping()).isEqualTo("PONG");
		}

		factory.destroy();
	}

	@Test // GH-2594
	@EnabledOnValkeyClusterAvailable
	void configuresExecutorCorrectly() {

		AsyncTaskExecutor mockTaskExecutor = mock(AsyncTaskExecutor.class);

		JedisConnectionFactory factory = new JedisConnectionFactory(SettingsUtils.clusterConfiguration());
		factory.setExecutor(mockTaskExecutor);
		factory.start();

		ClusterCommandExecutor clusterCommandExecutor = factory.getRequiredClusterCommandExecutor();
		assertThat(clusterCommandExecutor).extracting("executor").isEqualTo(mockTaskExecutor);

		factory.destroy();
	}

	@Test // GH-3315
	void shouldCustomizeStandaloneClient() {

		Cache c = mock(Cache.class);
		factory = new JedisConnectionFactory(
				new ValkeyStandaloneConfiguration(SettingsUtils.getHost(), SettingsUtils.getPort()),
				JedisClientConfiguration.builder().customizeClientConfig(it -> it.protocol(RedisProtocol.RESP3))
						.customizeClient(builder -> builder.cache(c)).build());
		factory.afterPropertiesSet();
		factory.start();

		UnifiedJedis client = factory.getRequiredRedisClient();
		assertThat(client.getCache()).isEqualTo(c);
	}

	@Test // GH-3315
	@EnabledOnValkeyClusterAvailable
	void shouldCustomizeClusterClient() {

		Cache c = mock(Cache.class);
		factory = new JedisConnectionFactory(SettingsUtils.clusterConfiguration(),
				JedisClientConfiguration.builder().customizeClientConfig(it -> it.protocol(RedisProtocol.RESP3))
						.customizeClient(builder -> builder.cache(c)).build());
		factory.afterPropertiesSet();
		factory.start();

		UnifiedJedis client = factory.getRequiredRedisClient();
		assertThat(client.getCache()).isEqualTo(c);
	}

}
