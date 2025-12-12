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

package io.valkey.springframework.boot.docker.compose.service.connection.valkey;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails.Standalone;
import io.valkey.springframework.boot.testsupport.docker.compose.DockerComposeTest;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ValkeyDockerComposeConnectionDetailsFactory}.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Scott Frederick
 * @author Eddú Meléndez
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "Requires Docker")
class ValkeyDockerComposeConnectionDetailsFactoryIntegrationTests {

	@DockerComposeTest(composeFile = "io/valkey/springframework/boot/docker/compose/service/connection/valkey/valkey-compose.yml", image = "valkey/valkey:8.1.1")
	void runCreatesConnectionDetails(ValkeyConnectionDetails connectionDetails) {
		assertConnectionDetails(connectionDetails);
	}

	private void assertConnectionDetails(ValkeyConnectionDetails connectionDetails) {
		assertThat(connectionDetails.getUsername()).isNull();
		assertThat(connectionDetails.getPassword()).isNull();
		assertThat(connectionDetails.getCluster()).isNull();
		assertThat(connectionDetails.getSentinel()).isNull();
		Standalone standalone = connectionDetails.getStandalone();
		assertThat(standalone).isNotNull();
		assertThat(standalone.getDatabase()).isZero();
		assertThat(standalone.getPort()).isGreaterThan(0);
		assertThat(standalone.getHost()).isNotNull();
	}

}
