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

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConnection;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeySentinelAvailable;
import org.springframework.lang.Nullable;

/**
 * Sentinel integration tests for {@link JedisConnectionFactory}.
 *
 * @author Christoph Strobl
 * @author Fu Jian
 * @author Mark Paluch
 * @author Ajith Kumar
 */
@EnabledOnValkeySentinelAvailable
class JedisConnectionFactorySentinelIntegrationTests {

	private static final ValkeySentinelConfiguration SENTINEL_CONFIG = new ValkeySentinelConfiguration().master("mymaster")
			.sentinel("127.0.0.1", 26379).sentinel("127.0.0.1", 26380);
	private @Nullable JedisConnectionFactory factory;

	@AfterEach
	void tearDown() {

		if (factory != null) {
			factory.destroy();
		}
	}

	@Test // GH-2103
	void shouldConnectDataNodeCorrectly() {

		ValkeySentinelConfiguration configuration = new ValkeySentinelConfiguration().master("mymaster")
				.sentinel("127.0.0.1", 26379).sentinel("127.0.0.1", 26380);
		configuration.setDatabase(5);

		factory = new JedisConnectionFactory(configuration);
		factory.afterPropertiesSet();
		factory.start();

		try (ValkeyConnection connection = factory.getConnection()) {

			connection.serverCommands().flushAll();
			connection.stringCommands().set("key5".getBytes(), "value5".getBytes());

			connection.select(0);
			assertThat(connection.keyCommands().exists("key5".getBytes())).isFalse();
		}
	}

	@Test // GH-2103
	void shouldConnectSentinelNodeCorrectly() throws IOException {

		ValkeySentinelConfiguration configuration = new ValkeySentinelConfiguration().master("mymaster")
				.sentinel("127.0.0.1", 26379).sentinel("127.0.0.1", 26380);
		configuration.setDatabase(5);

		factory = new JedisConnectionFactory(configuration);
		factory.afterPropertiesSet();
		factory.start();

		try (ValkeySentinelConnection sentinelConnection = factory.getSentinelConnection()) {
			assertThat(sentinelConnection.masters()).isNotNull();
		}
	}

	@Test // DATAREDIS-574, DATAREDIS-765
	void shouldInitializeWithSentinelConfiguration() {

		JedisClientConfiguration clientConfiguration = JedisClientConfiguration.builder() //
				.clientName("clientName") //
				.build();

		factory = new JedisConnectionFactory(SENTINEL_CONFIG, clientConfiguration);
		factory.afterPropertiesSet();
		factory.start();

		try (ValkeyConnection connection = factory.getConnection()) {

			assertThat(factory.getUsePool()).isTrue();
			assertThat(connection.getClientName()).isEqualTo("clientName");
		}
	}

	@Test // DATAREDIS-324
	void shouldSendCommandCorrectlyViaConnectionFactoryUsingSentinel() {

		factory = new JedisConnectionFactory(SENTINEL_CONFIG);
		factory.afterPropertiesSet();
		factory.start();

		try (ValkeyConnection connection = factory.getConnection()) {
			assertThat(connection.ping()).isEqualTo("PONG");
		}
	}

	@Test // DATAREDIS-552
	void getClientNameShouldEqualWithFactorySetting() {

		factory = new JedisConnectionFactory(SENTINEL_CONFIG);
		factory.setClientName("clientName");
		factory.afterPropertiesSet();
		factory.start();

		try (ValkeyConnection connection = factory.getConnection()) {
			assertThat(connection.serverCommands().getClientName()).isEqualTo("clientName");
		}
	}

	@Test // DATAREDIS-1127
	void shouldNotFailOnFirstSentinelDown() throws IOException {

		ValkeySentinelConfiguration oneDownSentinelConfig = new ValkeySentinelConfiguration().master("mymaster")
				.sentinel("127.0.0.1", 1).sentinel("127.0.0.1", 26379);

		factory = new JedisConnectionFactory(oneDownSentinelConfig);
		factory.afterPropertiesSet();
		factory.start();

		try (ValkeySentinelConnection sentinelConnection = factory.getSentinelConnection()) {
			assertThat(sentinelConnection.isOpen()).isTrue();
		}
	}
}
