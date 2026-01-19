/*
 * Copyright 2018-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.support.atomic;

import static org.assertj.core.api.Assertions.*;

import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;

import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.ValueOperations;
import io.valkey.springframework.data.valkey.serializer.GenericToStringSerializer;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Integration tests for {@link CompareAndSet}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
@MethodSource("testParams")
public class CompareAndSetIntegrationIntegrationTests {

	private static final String KEY = "key";

	private final ValkeyConnectionFactory factory;
	private final ValkeyTemplate<String, Long> template;
	private final ValueOperations<String, Long> valueOps;

	public CompareAndSetIntegrationIntegrationTests(ValkeyConnectionFactory factory) {

		this.factory = factory;

		this.template = new ValkeyTemplate<>();
		this.template.setConnectionFactory(factory);
		this.template.setKeySerializer(StringValkeySerializer.UTF_8);
		this.template.setValueSerializer(new GenericToStringSerializer<>(Long.class));
		this.template.afterPropertiesSet();

		this.valueOps = this.template.opsForValue();
	}

	public static Collection<Object[]> testParams() {
		return AtomicCountersParam.testParams();
	}

	@BeforeEach
	void setUp() {

		ValkeyConnection connection = factory.getConnection();
		connection.flushDb();
		connection.close();
	}

	@ParameterizedValkeyTest // DATAREDIS-843
	void shouldUpdateCounter() {

		long expected = 5;
		long actual = 5;
		long update = 6;

		CompareAndSet<Long> cas = new CompareAndSet<>(() -> actual, newValue -> valueOps.set(KEY, newValue), KEY, expected,
				update);

		assertThat(template.execute(cas)).isTrue();
		assertThat(valueOps.get(KEY)).isEqualTo(update);
	}

	@ParameterizedValkeyTest // DATAREDIS-843
	void expectationNotMet() {

		long expected = 5;
		long actual = 7;
		long update = 6;

		CompareAndSet<Long> cas = new CompareAndSet<>(() -> actual, newValue -> valueOps.set(KEY, newValue), KEY, expected,
				update);

		assertThat(template.execute(cas)).isFalse();
		assertThat(valueOps.get(KEY)).isNull();
	}

	@ParameterizedValkeyTest // DATAREDIS-843
	void concurrentUpdate() {

		long expected = 5;
		long actual = 5;
		long update = 6;
		long concurrentlyUpdated = 7;

		CompareAndSet<Long> cas = new CompareAndSet<>(() -> actual, newValue -> {

			ValkeyConnection connection = factory.getConnection();
			connection.set(KEY.getBytes(), Long.toString(concurrentlyUpdated).getBytes());
			connection.close();

			valueOps.set(KEY, newValue);
		}, KEY, expected, update);

		assertThat(template.execute(cas)).isFalse();
		assertThat(valueOps.get(KEY)).isEqualTo(concurrentlyUpdated);
	}
}
