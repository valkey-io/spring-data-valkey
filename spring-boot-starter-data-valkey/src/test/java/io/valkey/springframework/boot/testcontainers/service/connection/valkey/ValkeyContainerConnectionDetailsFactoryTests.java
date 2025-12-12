/*
 * Copyright 2012-2025 the original author or authors.
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

package io.valkey.springframework.boot.testcontainers.service.connection.valkey;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyAutoConfiguration;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ValkeyContainerConnectionDetailsFactory}.
 *
 * @author Andy Wilkinson
 */
@SpringJUnitConfig
@Testcontainers(disabledWithoutDocker = true)
class ValkeyContainerConnectionDetailsFactoryTests {

	@Container
	@ServiceConnection
	@SuppressWarnings("resource")
	static final GenericContainer<?> valkey = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.1.1"))
			.withExposedPorts(6379);

	@Autowired(required = false)
	private ValkeyConnectionDetails connectionDetails;

	@Autowired
	private ValkeyConnectionFactory connectionFactory;

	@Test
	void connectionCanBeMadeToValkeyContainer() {
		assertThat(this.connectionDetails).isNotNull();
		try (ValkeyConnection connection = this.connectionFactory.getConnection()) {
			assertThat(connection.commands().echo("Hello, World".getBytes())).isEqualTo("Hello, World".getBytes());
		}
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration(ValkeyAutoConfiguration.class)
	static class TestConfiguration {

	}

}
