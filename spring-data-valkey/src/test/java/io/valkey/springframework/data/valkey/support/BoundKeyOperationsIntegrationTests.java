/*
 * Copyright 2011-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.support;

import static org.assertj.core.api.Assertions.*;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.core.BoundKeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyCallback;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.support.atomic.ValkeyAtomicInteger;
import io.valkey.springframework.data.valkey.support.atomic.ValkeyAtomicLong;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Christoph Strobl
 */
@MethodSource("testParams")
public class BoundKeyOperationsIntegrationTests {

	@SuppressWarnings("rawtypes") //
	private BoundKeyOperations keyOps;

	private ObjectFactory<Object> objFactory;

	@SuppressWarnings("rawtypes") //
	private ValkeyTemplate template;

	@SuppressWarnings("rawtypes")
	public BoundKeyOperationsIntegrationTests(BoundKeyOperations<Object> keyOps, ObjectFactory<Object> objFactory,
			ValkeyTemplate template) {
		this.objFactory = objFactory;
		this.keyOps = keyOps;
		this.template = template;
	}

	public static Collection<Object[]> testParams() {
		return BoundKeyParams.testParams();
	}

	@BeforeEach
	void setUp() {
		populateBoundKey();
	}

	@SuppressWarnings("unchecked")
	@AfterEach
	void tearDown() {
		template.execute((ValkeyCallback<Object>) connection -> {
			connection.flushDb();
			return null;
		});
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testRename() throws Exception {

		Object key = keyOps.getKey();
		Object newName = objFactory.instance();

		keyOps.rename(newName);
		assertThat(keyOps.getKey()).isEqualTo(newName);

		keyOps.rename(key);
		assertThat(keyOps.getKey()).isEqualTo(key);
	}

	@ParameterizedValkeyTest // DATAREDIS-251
	void testExpire() throws Exception {

		assertThat(keyOps.getExpire()).as(keyOps.getClass().getName() + " -> " + keyOps.getKey())
				.isEqualTo(Long.valueOf(-1));

		if (keyOps.expire(10, TimeUnit.SECONDS)) {
			long expire = keyOps.getExpire().longValue();
			assertThat(expire <= 10 && expire > 5).isTrue();
		}
	}

	@ParameterizedValkeyTest // DATAREDIS-251
	void testPersist() throws Exception {

		keyOps.persist();

		assertThat(keyOps.getExpire()).as(keyOps.getClass().getName() + " -> " + keyOps.getKey())
				.isEqualTo(Long.valueOf(-1));
		if (keyOps.expire(10, TimeUnit.SECONDS)) {
			assertThat(keyOps.getExpire().longValue() > 0).isTrue();
		}

		keyOps.persist();
		assertThat(keyOps.getExpire().longValue()).as(keyOps.getClass().getName() + " -> " + keyOps.getKey()).isEqualTo(-1);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void populateBoundKey() {
		if (keyOps instanceof Collection collection) {
			collection.add("dummy");
		} else if (keyOps instanceof Map map) {
			map.put("dummy", "dummy");
		} else if (keyOps instanceof ValkeyAtomicInteger atomic) {
			atomic.set(42);
		} else if (keyOps instanceof ValkeyAtomicLong atomic) {
			atomic.set(42L);
		}
	}
}
