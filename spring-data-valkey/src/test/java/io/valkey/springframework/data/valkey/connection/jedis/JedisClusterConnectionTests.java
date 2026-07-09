/*
 * Copyright 2015-present the original author or authors.
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

import redis.clients.jedis.ConnectionPool;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

/**
 * Jedis Cluster connection tests via {@link JedisCluster}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Pavel Khokhlov
 * @author Dennis Neufeld
 * @author Tihomir Mateev
 * @author Viktoriya Kutsarova
 * @author Yordan Tsintsov
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JedisClusterConnectionTests extends JedisClusterConnectionTestSupport {

	private final JedisCluster nativeConnection;

	public JedisClusterConnectionTests(JedisCluster nativeConnection) {
		super(nativeConnection, new JedisClusterConnection(nativeConnection));
		this.nativeConnection = nativeConnection;
	}

	@BeforeEach
	void beforeEach() {

		for (ConnectionPool pool : nativeConnection.getClusterNodes().values()) {
			try (Jedis jedis = new Jedis(pool.getResource())) {
				jedis.flushAll();
			} catch (Exception ignore) {
				// ignore since we cannot remove data from replicas
			}
		}
	}

}
