/*
 * Copyright 2025 the original author or authors.
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
package performance;

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import redis.clients.jedis.Jedis;

/**
 * Direct client performance test without Spring Data Valkey overhead.
 */
public class DirectClientPerformanceTest {

	private static final int OPERATIONS = 10000;
	private static final String KEY_PREFIX = "direct:test:";

	public static void main(String[] args) throws Exception {
		String clientType = System.getProperty("client", "valkeyglide");
		
		System.out.println("Running Direct Client Performance Test");
		System.out.println("Client: " + clientType);
		System.out.println("Operations: " + OPERATIONS);
		System.out.println("----------------------------------------");

		switch (clientType.toLowerCase()) {
			case "valkeyglide" -> testValkeyGlide();
			case "lettuce" -> testLettuce();
			case "jedis" -> testJedis();
			default -> throw new IllegalArgumentException("Unknown client: " + clientType);
		}
	}

	private static void testValkeyGlide() throws Exception {
		GlideClientConfiguration config = GlideClientConfiguration.builder()
			.address(NodeAddress.builder().host("localhost").port(6379).build())
			.build();
		
		try (GlideClient client = GlideClient.createClient(config).get()) {
			// SET operations
			long start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				client.set(GlideString.of(KEY_PREFIX + i), GlideString.of("value" + i)).get();
			}
			long setTime = System.nanoTime() - start;
			printResult("SET", setTime);

			// GET operations
			start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				client.get(GlideString.of(KEY_PREFIX + i)).get();
			}
			long getTime = System.nanoTime() - start;
			printResult("GET", getTime);

			// DELETE operations
			start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				client.del(new GlideString[]{GlideString.of(KEY_PREFIX + i)}).get();
			}
			long deleteTime = System.nanoTime() - start;
			printResult("DELETE", deleteTime);
		}
	}

	private static void testLettuce() {
		RedisClient client = RedisClient.create(RedisURI.create("redis://localhost:6379"));
		
		try (StatefulRedisConnection<String, String> connection = client.connect()) {
			RedisCommands<String, String> commands = connection.sync();
			
			// SET operations
			long start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				commands.set(KEY_PREFIX + i, "value" + i);
			}
			long setTime = System.nanoTime() - start;
			printResult("SET", setTime);

			// GET operations
			start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				commands.get(KEY_PREFIX + i);
			}
			long getTime = System.nanoTime() - start;
			printResult("GET", getTime);

			// DELETE operations
			start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				commands.del(KEY_PREFIX + i);
			}
			long deleteTime = System.nanoTime() - start;
			printResult("DELETE", deleteTime);
		} finally {
			client.shutdown();
		}
	}

	private static void testJedis() {
		try (Jedis jedis = new Jedis("localhost", 6379)) {
			// SET operations
			long start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				jedis.set(KEY_PREFIX + i, "value" + i);
			}
			long setTime = System.nanoTime() - start;
			printResult("SET", setTime);

			// GET operations
			start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				jedis.get(KEY_PREFIX + i);
			}
			long getTime = System.nanoTime() - start;
			printResult("GET", getTime);

			// DELETE operations
			start = System.nanoTime();
			for (int i = 0; i < OPERATIONS; i++) {
				jedis.del(KEY_PREFIX + i);
			}
			long deleteTime = System.nanoTime() - start;
			printResult("DELETE", deleteTime);
		}
	}

	private static void printResult(String operation, long durationNanos) {
		long durationMs = durationNanos / 1_000_000;
		System.out.printf("%s:    %,d ops/sec (%.2f ms total)%n", 
			operation, (long) (OPERATIONS * 1000.0 / durationMs), durationMs / 1.0);
	}
}
