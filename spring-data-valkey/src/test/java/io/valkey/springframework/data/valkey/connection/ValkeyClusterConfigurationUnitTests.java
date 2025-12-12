/*
 * Copyright 2015-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.util.StringUtils;

/**
 * Unit tests for {@link ValkeyClusterConfiguration}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author John Blum
 */
class ValkeyClusterConfigurationUnitTests {

	private static final String HOST_AND_PORT_1 = "127.0.0.1:123";
	private static final String HOST_AND_PORT_2 = "localhost:456";
	private static final String HOST_AND_PORT_3 = "localhost:789";
	private static final String HOST_AND_PORT_4 = "[fe80::a00:27ff:fe4b:ee48]:6379";
	private static final String HOST_AND_PORT_5 = "[fe80:1234:1a2b:0:27ff:fe4b:0:ee48]:6380";

	@Test // DATAREDIS-315
	void shouldCreateValkeyClusterConfigurationCorrectly() {

		ValkeyClusterConfiguration config = new ValkeyClusterConfiguration(Collections.singleton(HOST_AND_PORT_1));

		assertThat(config.getClusterNodes().size()).isEqualTo(1);
		assertThat(config.getClusterNodes()).contains(new ValkeyNode("127.0.0.1", 123));
		assertThat(config.getMaxRedirects()).isNull();
	}

	@Test // GH-2418
	void shouldCreateValkeyClusterConfigurationForIPV6Correctly() {

		ValkeyClusterConfiguration config = new ValkeyClusterConfiguration(Collections.singleton("[aaa:bbb:ccc::dd1]:123"));

		assertThat(config.getClusterNodes().size()).isEqualTo(1);
		assertThat(config.getClusterNodes()).contains(new ValkeyNode("aaa:bbb:ccc::dd1", 123));
		assertThat(config.getClusterNodes()).first().hasToString("[aaa:bbb:ccc::dd1]:123");
		assertThat(config.getMaxRedirects()).isNull();
	}

	@Test // DATAREDIS-315
	void shouldCreateValkeyClusterConfigurationCorrectlyGivenMultipleHostAndPortStrings() {

		ValkeyClusterConfiguration config = new ValkeyClusterConfiguration(
				new HashSet<>(Arrays.asList(HOST_AND_PORT_1, HOST_AND_PORT_2, HOST_AND_PORT_3)));

		assertThat(config.getClusterNodes().size()).isEqualTo(3);
		assertThat(config.getClusterNodes()).contains(new ValkeyNode("127.0.0.1", 123), new ValkeyNode("localhost", 456),
				new ValkeyNode("localhost", 789));
	}

	@Test // DATAREDIS-315
	void shouldThrowExceptionWhenListOfHostAndPortIsNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ValkeyClusterConfiguration(Collections.singleton(null)));
	}

	@Test // DATAREDIS-315
	void shouldNotFailWhenListOfHostAndPortIsEmpty() {

		ValkeyClusterConfiguration config = new ValkeyClusterConfiguration(Collections.emptySet());

		assertThat(config.getClusterNodes().size()).isEqualTo(0);
	}

	@Test // DATAREDIS-315
	void shouldThrowExceptionGivenNullPropertySource() {
		assertThatIllegalArgumentException().isThrownBy(() -> ValkeyClusterConfiguration.of((PropertySource<?>) null));
	}

	@Test // DATAREDIS-315
	void shouldNotFailWhenGivenPropertySourceNotContainingRelevantProperties() {

		ValkeyClusterConfiguration config = ValkeyClusterConfiguration.of(new MockPropertySource());

		assertThat(config.getMaxRedirects()).isNull();
		assertThat(config.getClusterNodes().size()).isEqualTo(0);
	}

	@Test // DATAREDIS-315
	void shouldBeCreatedCorrectlyGivenValidPropertySourceWithSingleHostPort() {

		MockPropertySource propertySource = new MockPropertySource();
		propertySource.setProperty("spring.valkey.cluster.nodes", HOST_AND_PORT_1);
		propertySource.setProperty("spring.valkey.cluster.max-redirects", "5");

		ValkeyClusterConfiguration config = ValkeyClusterConfiguration.of(propertySource);

		assertThat(config.getMaxRedirects()).isEqualTo(5);
		assertThat(config.getClusterNodes()).contains(new ValkeyNode("127.0.0.1", 123));
	}

	@Test // DATAREDIS-315
	void shouldBeCreatedCorrectlyGivenValidPropertySourceWithMultipleHostPort() {

		MockPropertySource propertySource = new MockPropertySource();
		propertySource.setProperty("spring.valkey.cluster.nodes",
				StringUtils.collectionToCommaDelimitedString(Arrays.asList(HOST_AND_PORT_1, HOST_AND_PORT_2, HOST_AND_PORT_3)));
		propertySource.setProperty("spring.valkey.cluster.max-redirects", "5");

		ValkeyClusterConfiguration config = ValkeyClusterConfiguration.of(propertySource);

		assertThat(config.getMaxRedirects()).isEqualTo(5);
		assertThat(config.getClusterNodes()).contains(new ValkeyNode("127.0.0.1", 123), new ValkeyNode("localhost", 456),
				new ValkeyNode("localhost", 789));
	}

	@Test // GH-2360
	void shouldBeCreatedCorrectlyGivenValidPropertySourceWithMultipleIPv6AddressesAndPorts() {

		MockPropertySource propertySource = new MockPropertySource();

		propertySource.setProperty("spring.valkey.cluster.nodes",
				StringUtils.collectionToCommaDelimitedString(Arrays.asList(HOST_AND_PORT_4, HOST_AND_PORT_5)));
		propertySource.setProperty("spring.valkey.cluster.max-redirects", 2);

		ValkeyClusterConfiguration configuration = ValkeyClusterConfiguration.of(propertySource);

		assertThat(configuration.getMaxRedirects()).isEqualTo(2);
		assertThat(configuration.getClusterNodes()).contains(new ValkeyNode("fe80::a00:27ff:fe4b:ee48", 6379),
				new ValkeyNode("fe80:1234:1a2b:0:27ff:fe4b:0:ee48", 6380));
	}
}
