/*
 * Copyright 2016-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.repository;

import static io.valkey.springframework.data.valkey.connection.ClusterTestVariables.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.repository.configuration.EnableValkeyRepositories;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnabledOnValkeyClusterAvailable
class ValkeyRepositoryClusterIntegrationTests extends ValkeyRepositoryIntegrationTestBase {

	static final List<String> CLUSTER_NODES = Arrays.asList(CLUSTER_NODE_1.asString(), CLUSTER_NODE_2.asString(),
			CLUSTER_NODE_3.asString());

	@Configuration
	@EnableValkeyRepositories(considerNestedRepositories = true, indexConfiguration = MyIndexConfiguration.class,
			keyspaceConfiguration = MyKeyspaceConfiguration.class,
			includeFilters = { @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
					classes = { PersonRepository.class, CityRepository.class, ImmutableObjectRepository.class, UserRepository.class }) })
	static class Config {

		@Bean
		ValkeyTemplate<?, ?> valkeyTemplate(ValkeyConnectionFactory connectionFactory) {

			ValkeyTemplate<byte[], byte[]> template = new ValkeyTemplate<>();

			template.setConnectionFactory(connectionFactory);

			return template;
		}

		@Bean
		ValkeyConnectionFactory connectionFactory() {
			ValkeyClusterConfiguration clusterConfig = new ValkeyClusterConfiguration(CLUSTER_NODES);
			JedisConnectionFactory connectionFactory = new JedisConnectionFactory(clusterConfig);
			return connectionFactory;
		}
	}
}
