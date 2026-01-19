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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.valkey.springframework.data.valkey.SettingsUtils;
import io.valkey.springframework.data.valkey.connection.AbstractTransactionalTestBase;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@ContextConfiguration
public class TransactionalJedisIntegrationTests extends AbstractTransactionalTestBase {

	@Configuration
	public static class JedisContextConfiguration extends ValkeyContextConfiguration {

		@Override
		@Bean
		public JedisConnectionFactory valkeyConnectionFactory() {
			return new JedisConnectionFactory(SettingsUtils.standaloneConfiguration());
		}
	}
}
