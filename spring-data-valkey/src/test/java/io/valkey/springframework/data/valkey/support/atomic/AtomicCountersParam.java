/*
 * Copyright 2011-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.support.atomic;

import java.util.Arrays;
import java.util.Collection;

import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Mark Paluch
 * @author Christoph Strobl
 */
abstract class AtomicCountersParam {

	static Collection<Object[]> testParams() {

		// Jedis
		JedisConnectionFactory jedisConnFactory = JedisConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		// Lettuce
		LettuceConnectionFactory lettuceConnFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class, false);

		// Valkey-Glide
		ValkeyGlideConnectionFactory valkeyGlideConnFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		return Arrays.asList(new Object[][] { { jedisConnFactory }, { lettuceConnFactory }, { valkeyGlideConnFactory } });
	}
}
