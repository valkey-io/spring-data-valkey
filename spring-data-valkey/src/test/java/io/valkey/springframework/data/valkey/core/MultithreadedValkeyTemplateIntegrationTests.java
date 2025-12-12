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
package io.valkey.springframework.data.valkey.core;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * @author Artem Bilian
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@MethodSource("testParams")
public class MultithreadedValkeyTemplateIntegrationTests {

	private final ValkeyConnectionFactory factory;

	public MultithreadedValkeyTemplateIntegrationTests(ValkeyConnectionFactory factory) {
		this.factory = factory;
	}

	public static Collection<Object> testParams() {

		JedisConnectionFactory jedis = JedisConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class);
		LettuceConnectionFactory lettuce = LettuceConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class);
		ValkeyGlideConnectionFactory valkeyGlide = ValkeyGlideConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class);

		return Arrays.asList(jedis, lettuce, valkeyGlide);
	}

	@ParameterizedValkeyTest // DATAREDIS-300
	void assertResouresAreReleasedProperlyWhenSharingValkeyTemplate() throws InterruptedException {

		final ValkeyTemplate<Object, Object> template = new ValkeyTemplate<>();
		template.setConnectionFactory(factory);
		template.afterPropertiesSet();

		ExecutorService executor = Executors.newCachedThreadPool();

		for (int i = 0; i < 9; i++) {
			executor.execute(template.boundValueOps("foo")::get);
		}

		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
	}

}
