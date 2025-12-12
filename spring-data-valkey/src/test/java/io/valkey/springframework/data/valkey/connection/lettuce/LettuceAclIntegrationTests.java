/*
 * Copyright 2020-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;

import java.io.IOException;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyStaticMasterReplicaConfiguration;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyAvailable;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeySentinelAvailable;
import io.valkey.springframework.data.valkey.test.extension.LettuceTestClientResources;
import io.valkey.springframework.data.valkey.util.ConnectionVerifier;

/**
 * Integration tests for Valkey 6 ACL.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
@EnabledOnValkeyAvailable(6382)
@EnabledOnCommand("HELLO")
class LettuceAclIntegrationTests {

	@Test // DATAREDIS-1046
	void shouldConnectWithDefaultAuthentication() {

		ValkeyStandaloneConfiguration standaloneConfiguration = new ValkeyStandaloneConfiguration("localhost", 6382);
		standaloneConfiguration.setPassword("foobared");

		LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
				.clientResources(LettuceTestClientResources.getSharedClientResources()).build();

		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(standaloneConfiguration,
				clientConfiguration);

		ConnectionVerifier.create(connectionFactory) //
				.execute(connection -> {
					assertThat(connection.ping()).isEqualTo("PONG");
				}) //
				.verifyAndClose();
	}

	@Test // DATAREDIS-1046
	void shouldConnectStandaloneWithAclAuthentication() {

		ValkeyStandaloneConfiguration standaloneConfiguration = new ValkeyStandaloneConfiguration("localhost", 6382);
		standaloneConfiguration.setUsername("spring");
		standaloneConfiguration.setPassword("data");

		LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
				.clientResources(LettuceTestClientResources.getSharedClientResources()).build();

		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(standaloneConfiguration,
				clientConfiguration);

		ConnectionVerifier.create(connectionFactory) //
				.execute(connection -> {
					assertThat(connection.ping()).isEqualTo("PONG");
				}) //
				.verifyAndClose();
	}

	@Test // DATAREDIS-1145
	@EnabledOnValkeySentinelAvailable(26382)
	void shouldConnectSentinelWithAuthentication() throws IOException {

		// Note: As per https://github.com/valkey/valkey/issues/7708, Sentinel does not support ACL authentication yet.

		LettuceClientConfiguration configuration = LettuceTestClientConfiguration.builder()
				.clientOptions(ClientOptions.builder().protocolVersion(ProtocolVersion.RESP2).build()).build();

		ValkeySentinelConfiguration sentinelConfiguration = new ValkeySentinelConfiguration("mymaster",
				Collections.singleton("localhost:26382"));
		sentinelConfiguration.setSentinelPassword("foobared");

		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(sentinelConfiguration, configuration);
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		try (ValkeySentinelConnection connection = connectionFactory.getSentinelConnection()) {
			assertThat(connection.masters()).isNotEmpty();
		} finally {
			connectionFactory.destroy();
		}
	}

	@Test // DATAREDIS-1046
	void shouldConnectMasterReplicaWithAclAuthentication() {

		ValkeyStaticMasterReplicaConfiguration masterReplicaConfiguration = new ValkeyStaticMasterReplicaConfiguration(
				"localhost", 6382);
		masterReplicaConfiguration.setUsername("spring");
		masterReplicaConfiguration.setPassword("data");

		LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
				.clientResources(LettuceTestClientResources.getSharedClientResources()).build();

		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(masterReplicaConfiguration,
				clientConfiguration);

		ConnectionVerifier.create(connectionFactory) //
				.execute(connection -> {
					assertThat(connection.ping()).isEqualTo("PONG");
				}) //
				.verifyAndClose();
	}
}
