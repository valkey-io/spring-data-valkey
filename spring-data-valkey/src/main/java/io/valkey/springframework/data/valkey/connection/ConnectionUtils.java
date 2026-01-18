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
package io.valkey.springframework.data.valkey.connection;

import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;

/**
 * Utilities for examining a {@link ValkeyConnection}
 *
 * @author Jennifer Hickey
 * @author Thomas Darimont
 */
public abstract class ConnectionUtils {

	public static boolean isAsync(ValkeyConnectionFactory connectionFactory) {
		return connectionFactory instanceof LettuceConnectionFactory
			|| connectionFactory instanceof ValkeyGlideConnectionFactory;
	}

	public static boolean isLettuce(ValkeyConnectionFactory connectionFactory) {
		return connectionFactory instanceof LettuceConnectionFactory;
	}

	public static boolean isJedis(ValkeyConnectionFactory connectionFactory) {
		return connectionFactory instanceof JedisConnectionFactory;
	}
}
