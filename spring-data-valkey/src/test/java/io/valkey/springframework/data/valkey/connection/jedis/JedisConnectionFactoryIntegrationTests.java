/*
 * Copyright 2017-2025 the original author or authors.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import io.valkey.springframework.data.valkey.SettingsUtils;
import io.valkey.springframework.data.valkey.connection.ClusterCommandExecutor;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;
import org.springframework.lang.Nullable;

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
}
