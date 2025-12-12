/*
 * Copyright 2024-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ValkeyNode}.
 *
 * @author LeeHyungGeol
 * @author Mark Paluch
 */
class ValkeyNodeUnitTests {

	@Test // GH-2928
	void shouldParseIPv4AddressWithPort() {

		ValkeyNode node = ValkeyNode.fromString("127.0.0.1:1234");

		assertThat(node.getHost()).isEqualTo("127.0.0.1");
		assertThat(node.getPort()).isEqualTo(1234);
	}

	@ParameterizedTest // GH-2928
	@ValueSource(strings = { "127.0.0.1", "127.0.0.1:" })
	void shouldParseIPv4AddressWithoutPort(String source) {

		ValkeyNode node = ValkeyNode.fromString(source);

		assertThat(node.getHost()).isEqualTo("127.0.0.1");
		assertThat(node.getPort()).isEqualTo(ValkeyNode.DEFAULT_PORT);
	}

	@Test // GH-2928
	void shouldParseIPv6AddressWithPort() {

		ValkeyNode node = ValkeyNode.fromString("[aaaa:bbbb::dddd:eeee]:1234");

		assertThat(node.getHost()).isEqualTo("aaaa:bbbb::dddd:eeee");
		assertThat(node.getPort()).isEqualTo(1234);
	}

	@ParameterizedTest // GH-2928
	@ValueSource(strings = { "[aaaa:bbbb::dddd:eeee]", "[aaaa:bbbb::dddd:eeee]:" })
	void shouldParseIPv6AddressWithoutPort(String source) {

		ValkeyNode node = ValkeyNode.fromString(source);

		assertThat(node.getHost()).isEqualTo("aaaa:bbbb::dddd:eeee");
		assertThat(node.getPort()).isEqualTo(ValkeyNode.DEFAULT_PORT);
	}

	@Test // GH-2928
	void shouldParseBareHostnameWithPort() {

		ValkeyNode node = ValkeyNode.fromString("my.valkey.server:6379");

		assertThat(node.getHost()).isEqualTo("my.valkey.server");
		assertThat(node.getPort()).isEqualTo(6379);
	}

	@ParameterizedTest // GH-2928
	@ValueSource(strings = { "my.valkey.server", "[my.valkey.server:" })
	void shouldParseBareHostnameWithoutPort(String source) {

		ValkeyNode node = ValkeyNode.fromString("my.valkey.server");

		assertThat(node.getHost()).isEqualTo("my.valkey.server");
		assertThat(node.getPort()).isEqualTo(ValkeyNode.DEFAULT_PORT);
	}

	@Test // GH-2928
	void shouldThrowExceptionForInvalidPort() {

		assertThatIllegalArgumentException()
			.isThrownBy(() -> ValkeyNode.fromString("127.0.0.1:invalidPort"));
	}

	@Test // GH-2928
	void shouldParseBareIPv6WithoutPort() {

		ValkeyNode node = ValkeyNode.fromString("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

		assertThat(node.getHost()).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370");
		assertThat(node.getPort()).isEqualTo(7334);
	}

}

