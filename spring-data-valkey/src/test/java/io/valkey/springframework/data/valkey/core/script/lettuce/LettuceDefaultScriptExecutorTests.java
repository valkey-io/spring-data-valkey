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
package io.valkey.springframework.data.valkey.core.script.lettuce;

import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.script.AbstractDefaultScriptExecutorTests;
import io.valkey.springframework.data.valkey.core.script.DefaultScriptExecutor;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * Integration test of {@link DefaultScriptExecutor} with Lettuce.
 *
 * @author Thomas Darimont
 * @author Mark Paluch
 */
public class LettuceDefaultScriptExecutorTests extends AbstractDefaultScriptExecutorTests {

	@Override
	protected ValkeyConnectionFactory getConnectionFactory() {
		return LettuceConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class);
	}
}
