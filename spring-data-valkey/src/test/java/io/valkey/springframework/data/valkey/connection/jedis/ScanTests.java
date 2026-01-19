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
package io.valkey.springframework.data.valkey.connection.jedis;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;

import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.BoundHashOperations;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * @author Mark Paluch
 * @author Christoph Strobl
 */
@MethodSource("params")
public class ScanTests {

	private ValkeyConnectionFactory factory;
	private ValkeyTemplate<String, String> valkeyOperations;

	private ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 1, TimeUnit.MINUTES,
			new LinkedBlockingDeque<>());

	public ScanTests(ValkeyConnectionFactory factory) {
		this.factory = factory;
	}

	public static List<ValkeyConnectionFactory> params() {

		JedisConnectionFactory jedisConnectionFactory = JedisConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		LettuceConnectionFactory lettuceConnectionFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		// Pretty strange that Lettuce is used in Jedis-tree test - adding Valkey-Glide as well
		ValkeyGlideConnectionFactory valkeyGlideConnectionFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		return Arrays.asList(jedisConnectionFactory, lettuceConnectionFactory, valkeyGlideConnectionFactory);
	}

	@BeforeEach
	void setUp() {

		valkeyOperations = new StringValkeyTemplate(factory);
		valkeyOperations.afterPropertiesSet();
	}

	@ParameterizedValkeyTest
	void contextLoads() throws InterruptedException {

		BoundHashOperations<String, String, String> hash = valkeyOperations.boundHashOps("hash");
		final AtomicReference<Exception> exception = new AtomicReference<>();

		// Create some keys so that SCAN requires a while to return all data.
		for (int i = 0; i < 10000; i++) {
			hash.put("key-" + i, "value");
		}

		// Concurrent access
		for (int i = 0; i < 10; i++) {

			executor.submit(() -> {
				try {

					Cursor<Entry<Object, Object>> cursorMap = valkeyOperations.boundHashOps("hash")
							.scan(ScanOptions.scanOptions().match("*").count(100).build());

					// This line invokes the lazy SCAN invocation
					while (cursorMap.hasNext()) {
						cursorMap.next();
					}
					cursorMap.close();
				} catch (Exception ex) {
					exception.set(ex);
				}
			});
		}

		// Wait until work is finished
		while (executor.getActiveCount() > 0) {
			Thread.sleep(100);
		}

		executor.shutdown();

		assertThat(exception.get()).isNull();
	}
}
