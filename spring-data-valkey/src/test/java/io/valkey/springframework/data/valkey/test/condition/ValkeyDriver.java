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
package io.valkey.springframework.data.valkey.test.condition;

import io.valkey.springframework.data.valkey.connection.ConnectionUtils;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;

/**
 * Enumerates the supported Valkey driver types.
 *
 * @author Thomas Darimont
 */
public enum ValkeyDriver {

	JEDIS {
		@Override
		public boolean matches(ValkeyConnectionFactory connectionFactory) {
			return ConnectionUtils.isJedis(connectionFactory);
		}
	},

	LETTUCE {
		@Override
		public boolean matches(ValkeyConnectionFactory connectionFactory) {
			return ConnectionUtils.isLettuce(connectionFactory);
		}

	};

	/**
	 * @param connectionFactory
	 * @return true of the given {@link ValkeyConnectionFactory} is supported by the current valkey driver.
	 */
	public abstract boolean matches(ValkeyConnectionFactory connectionFactory);
}
