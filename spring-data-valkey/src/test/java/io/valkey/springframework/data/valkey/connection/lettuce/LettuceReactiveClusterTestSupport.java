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
package io.valkey.springframework.data.valkey.connection.lettuce;

import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;
import io.valkey.springframework.data.valkey.test.extension.LettuceExtension;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@EnabledOnValkeyClusterAvailable
@ExtendWith(LettuceExtension.class)
public abstract class LettuceReactiveClusterTestSupport {

	RedisClusterCommands<String, String> nativeCommands;
	LettuceReactiveValkeyClusterConnection connection;

	@BeforeEach
	public void before(RedisClusterClient clusterClient) {

		nativeCommands = clusterClient.connect().sync();
		connection = new LettuceReactiveValkeyClusterConnection(
				new ClusterConnectionProvider(clusterClient, LettuceReactiveValkeyConnection.CODEC), clusterClient);
	}

	@AfterEach
	public void tearDown() {

		if (nativeCommands != null) {
			nativeCommands.flushall();

			if (nativeCommands instanceof RedisCommands valkeyCommands) {
				valkeyCommands.getStatefulConnection().close();
			}

			if (nativeCommands instanceof RedisAdvancedClusterCommands valkeyAdvancedClusterCommands) {
				valkeyAdvancedClusterCommands.getStatefulConnection().close();
			}
		}

		if (connection != null) {
			connection.close();
		}
	}
}
