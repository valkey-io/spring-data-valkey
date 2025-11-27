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

import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;

/**
 * Performance test for ValkeyTemplate operations across different clients.
 */
public class TemplatePerformanceTest {

	private static final int OPERATIONS = 10000;
	private static final String KEY_PREFIX = "perf:test:";

	public static void main(String[] args) throws Exception {
		String clientType = System.getProperty("client", "valkeyglide");
		
		System.out.println("Running ValkeyTemplate Performance Test");
		System.out.println("Client: " + clientType);
		System.out.println("Operations: " + OPERATIONS);
		System.out.println("----------------------------------------");

		ValkeyConnectionFactory factory = createConnectionFactory(clientType);
		if (factory instanceof InitializingBean) {
			((InitializingBean) factory).afterPropertiesSet();
		}

		try {
			StringValkeyTemplate template = new StringValkeyTemplate(factory);
			runPerformanceTest(template);
		} finally {
			if (factory instanceof DisposableBean) {
				((DisposableBean) factory).destroy();
			}
		}
	}

	private static ValkeyConnectionFactory createConnectionFactory(String clientType) {
		return switch (clientType.toLowerCase()) {
			case "lettuce" -> new LettuceConnectionFactory();
			case "jedis" -> new JedisConnectionFactory();
			case "valkeyglide" -> new ValkeyGlideConnectionFactory();
			default -> throw new IllegalArgumentException("Unknown client: " + clientType);
		};
	}

	private static void runPerformanceTest(StringValkeyTemplate template) {
		// SET operations
		long start = System.nanoTime();
		for (int i = 0; i < OPERATIONS; i++) {
			template.opsForValue().set(KEY_PREFIX + i, "value" + i);
		}
		long setTime = System.nanoTime() - start;
		printResult("SET", setTime);

		// GET operations
		start = System.nanoTime();
		for (int i = 0; i < OPERATIONS; i++) {
			template.opsForValue().get(KEY_PREFIX + i);
		}
		long getTime = System.nanoTime() - start;
		printResult("GET", getTime);

		// DELETE operations
		start = System.nanoTime();
		for (int i = 0; i < OPERATIONS; i++) {
			template.delete(KEY_PREFIX + i);
		}
		long deleteTime = System.nanoTime() - start;
		printResult("DELETE", deleteTime);
	}

	private static void printResult(String operation, long durationNanos) {
		long durationMs = durationNanos / 1_000_000;
		System.out.printf("%s:    %,d ops/sec (%.2f ms total)%n", 
			operation, (long) (OPERATIONS * 1000.0 / durationMs), durationMs / 1.0);
	}
}
