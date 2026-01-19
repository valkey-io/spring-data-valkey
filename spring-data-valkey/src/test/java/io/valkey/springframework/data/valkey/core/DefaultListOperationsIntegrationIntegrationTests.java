/*
 * Copyright 2013-2025 the original author or authors.
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
import static org.assertj.core.api.Assumptions.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;

import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.StringObjectFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.test.condition.EnabledIfLongRunningTest;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Integration test of {@link DefaultListOperations}
 *
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Lee Jaeheon
 * @param <K> Key test
 * @param <V> Value test
 */
@MethodSource("testParams")
public class DefaultListOperationsIntegrationIntegrationTests<K, V> {

	private final ValkeyTemplate<K, V> valkeyTemplate;

	private final ObjectFactory<K> keyFactory;

	private final ObjectFactory<V> valueFactory;

	private final ListOperations<K, V> listOps;

	public DefaultListOperationsIntegrationIntegrationTests(ValkeyTemplate<K, V> valkeyTemplate,
			ObjectFactory<K> keyFactory, ObjectFactory<V> valueFactory) {

		this.valkeyTemplate = valkeyTemplate;
		this.keyFactory = keyFactory;
		this.valueFactory = valueFactory;
		this.listOps = valkeyTemplate.opsForList();
	}

	public static Collection<Object[]> testParams() {
		return AbstractOperationsTestParams.testParams();
	}

	@BeforeEach
	void setUp() {

		valkeyTemplate.execute((ValkeyCallback<Object>) connection -> {
			connection.flushDb();
			return null;
		});
	}

	@ParameterizedValkeyTest
	void testLeftPushWithPivot() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		assertThat(listOps.leftPush(key, v1)).isEqualTo(Long.valueOf(1));
		assertThat(listOps.leftPush(key, v2)).isEqualTo(Long.valueOf(2));
		assertThat(listOps.leftPush(key, v1, v3)).isEqualTo(Long.valueOf(3));
		assertThat(listOps.range(key, 0, -1)).containsSequence(v2, v3, v1);
	}

	@ParameterizedValkeyTest
	void testLeftPushIfPresent() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();

		assertThat(listOps.leftPushIfPresent(key, v1)).isEqualTo(Long.valueOf(0));
		assertThat(listOps.leftPush(key, v1)).isEqualTo(Long.valueOf(1));
		assertThat(listOps.leftPushIfPresent(key, v2)).isEqualTo(Long.valueOf(2));
		assertThat(listOps.range(key, 0, -1)).contains(v2, v1);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testLeftPushAll() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		assertThat(listOps.leftPushAll(key, v1, v2)).isEqualTo(Long.valueOf(2));
		assertThat(listOps.leftPush(key, v3)).isEqualTo(Long.valueOf(3));
		assertThat(listOps.range(key, 0, -1)).contains(v3, v2, v1);
	}

	@ParameterizedValkeyTest // DATAREDIS-611
	@EnabledIfLongRunningTest
	void testLeftPopDuration() {

		// 1 ms timeout gets upgraded to 1 sec timeout at the moment
		assumeThat(valueFactory instanceof StringObjectFactory).isTrue();

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();

		assertThat(listOps.leftPop(key, Duration.ofSeconds(1))).isNull();
		listOps.rightPushAll(key, v1, v2);
		assertThat(listOps.leftPop(key, Duration.ofSeconds(1))).isEqualTo(v1);
	}

	@ParameterizedValkeyTest // DATAREDIS-611
	@EnabledIfLongRunningTest
	void testRightPopDuration() {

		// 1 ms timeout gets upgraded to 1 sec timeout at the moment
		assumeThat(valueFactory instanceof StringObjectFactory).isTrue();

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();

		assertThat(listOps.rightPop(key, Duration.ofSeconds(1))).isNull();
		listOps.rightPushAll(key, v1, v2);
		assertThat(listOps.rightPop(key, Duration.ofSeconds(1))).isEqualTo(v2);
	}

	@ParameterizedValkeyTest
	@EnabledIfLongRunningTest
	void testRightPopAndLeftPushTimeout() {
		// 1 ms timeout gets upgraded to 1 sec timeout at the moment
		assumeThat(valueFactory instanceof StringObjectFactory).isTrue();

		K key = keyFactory.instance();
		K key2 = keyFactory.instance();
		V v1 = valueFactory.instance();

		assertThat(listOps.rightPopAndLeftPush(key, key2, 1, TimeUnit.MILLISECONDS)).isNull();
		listOps.leftPush(key, v1);
		assertThat(listOps.rightPopAndLeftPush(key, key2, 1, TimeUnit.MILLISECONDS)).isEqualTo(v1);
	}

	@ParameterizedValkeyTest // DATAREDIS-611
	@EnabledIfLongRunningTest
	void testRightPopAndLeftPushDuration() {
		// 1 ms timeout gets upgraded to 1 sec timeout at the moment
		assumeThat(valueFactory instanceof StringObjectFactory).isTrue();

		K key = keyFactory.instance();
		K key2 = keyFactory.instance();
		V v1 = valueFactory.instance();

		assertThat(listOps.rightPopAndLeftPush(key, key2, Duration.ofMillis(1))).isNull();
		listOps.leftPush(key, v1);
		assertThat(listOps.rightPopAndLeftPush(key, key2, Duration.ofMillis(1))).isEqualTo(v1);
	}

	@ParameterizedValkeyTest
	void testRightPopAndLeftPush() {

		K key = keyFactory.instance();
		K key2 = keyFactory.instance();
		V v1 = valueFactory.instance();

		assertThat(listOps.rightPopAndLeftPush(key, key2)).isNull();
		listOps.leftPush(key, v1);
		assertThat(listOps.rightPopAndLeftPush(key, key2)).isEqualTo(v1);
	}

	@ParameterizedValkeyTest
	void testRightPushWithPivot() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		assertThat(listOps.rightPush(key, v1)).isEqualTo(Long.valueOf(1));
		assertThat(listOps.rightPush(key, v2)).isEqualTo(Long.valueOf(2));
		assertThat(listOps.rightPush(key, v1, v3)).isEqualTo(Long.valueOf(3));
		assertThat(listOps.range(key, 0, -1)).containsSequence(v1, v3, v2);
	}

	@ParameterizedValkeyTest
	void testRightPushIfPresent() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();

		assertThat(listOps.rightPushIfPresent(key, v1)).isEqualTo(Long.valueOf(0));
		assertThat(listOps.rightPush(key, v1)).isEqualTo(Long.valueOf(1));
		assertThat(listOps.rightPushIfPresent(key, v2)).isEqualTo(Long.valueOf(2));
		assertThat(listOps.range(key, 0, -1)).containsSequence(v1, v2);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testRightPushAll() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		assertThat(listOps.rightPushAll(key, v1, v2)).isEqualTo(Long.valueOf(2));
		assertThat(listOps.rightPush(key, v3)).isEqualTo(Long.valueOf(3));
		assertThat(listOps.range(key, 0, -1)).containsSequence(v1, v2, v3);
	}

	@ParameterizedValkeyTest // DATAREDIS-288

	void testRightPushAllCollection() {

		K key = keyFactory.instance();

		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		assertThat(listOps.rightPushAll(key, Arrays.<V> asList(v1, v2, v3))).isEqualTo(Long.valueOf(3));
		assertThat(listOps.range(key, 0, -1)).containsSequence(v1, v2, v3);
	}

	@ParameterizedValkeyTest // DATAREDIS-288
	void rightPushAllShouldThrowExceptionWhenCalledWithEmptyCollection() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> listOps.rightPushAll(keyFactory.instance(), Collections.<V> emptyList()));
	}

	@ParameterizedValkeyTest
	// DATAREDIS-288
	void rightPushAllShouldThrowExceptionWhenCollectionContainsNullValue() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> listOps.rightPushAll(keyFactory.instance(), Arrays.asList(valueFactory.instance(), null)));
	}

	@ParameterizedValkeyTest // DATAREDIS-288
	void rightPushAllShouldThrowExceptionWhenCalledWithNull() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> listOps.rightPushAll(keyFactory.instance(), (Collection<V>) null));
	}

	@ParameterizedValkeyTest // DATAREDIS-288
	void testLeftPushAllCollection() {

		assumeThat(valkeyTemplate.getConnectionFactory() instanceof LettuceConnectionFactory).isTrue();

		K key = keyFactory.instance();

		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		assertThat(listOps.leftPushAll(key, Arrays.<V> asList(v1, v2, v3))).isEqualTo(Long.valueOf(3));
		assertThat(listOps.range(key, 0, -1)).containsSequence(v3, v2, v1);
	}

	@ParameterizedValkeyTest // DATAREDIS-288
	void leftPushAllShouldThrowExceptionWhenCalledWithEmptyCollection() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> listOps.leftPushAll(keyFactory.instance(), Collections.<V> emptyList()));
	}

	@ParameterizedValkeyTest // DATAREDIS-288
	void leftPushAllShouldThrowExceptionWhenCollectionContainsNullValue() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> listOps.leftPushAll(keyFactory.instance(), Arrays.asList(valueFactory.instance(), null)));
	}

	@ParameterizedValkeyTest // DATAREDIS-288
	void leftPushAllShouldThrowExceptionWhenCalledWithNull() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> listOps.leftPushAll(keyFactory.instance(), (Collection<V>) null));
	}

	@ParameterizedValkeyTest // GH-2039
	@EnabledOnCommand("LMOVE")
	void move() {

		K source = keyFactory.instance();
		K target = keyFactory.instance();

		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();
		V v4 = valueFactory.instance();

		listOps.rightPushAll(source, v1, v2, v3, v4);

		assertThat(listOps.move(ListOperations.MoveFrom.fromHead(source), ListOperations.MoveTo.toTail(target)))
				.isEqualTo(v1);
		assertThat(listOps.move(ListOperations.MoveFrom.fromTail(source), ListOperations.MoveTo.toHead(target)))
				.isEqualTo(v4);

		assertThat(listOps.range(source, 0, -1)).containsExactly(v2, v3);
		assertThat(listOps.range(target, 0, -1)).containsExactly(v4, v1);
	}

	@ParameterizedValkeyTest // GH-2039
	@EnabledOnCommand("BLMOVE")
	void moveWithTimeout() {

		K source = keyFactory.instance();
		K target = keyFactory.instance();

		V v1 = valueFactory.instance();
		V v4 = valueFactory.instance();

		listOps.rightPushAll(source, v1, v4);

		assertThat(listOps.move(ListOperations.MoveFrom.fromHead(source), ListOperations.MoveTo.toTail(target)))
				.isEqualTo(v1);
		assertThat(listOps.move(ListOperations.MoveFrom.fromTail(source), ListOperations.MoveTo.toHead(target)))
				.isEqualTo(v4);

		assertThat(listOps.move(ListOperations.MoveFrom.fromTail(source), ListOperations.MoveTo.toHead(target),
				Duration.ofMillis(10))).isNull();

		assertThat(listOps.range(source, 0, -1)).isEmpty();
		assertThat(listOps.range(target, 0, -1)).containsExactly(v4, v1);
	}

	@ParameterizedValkeyTest // GH-2937
	void getFirst() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		listOps.rightPush(key, v1);
		listOps.rightPush(key, v2);
		listOps.rightPush(key, v3);
		assertThat(listOps.getFirst(key)).isEqualTo(v1);
	}

	@ParameterizedValkeyTest // GH-2937
	void getLast() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		listOps.rightPush(key, v1);
		listOps.rightPush(key, v2);
		listOps.rightPush(key, v3);
		assertThat(listOps.getLast(key)).isEqualTo(v3);
	}

	@ParameterizedValkeyTest // DATAREDIS-1196
	@EnabledOnCommand("LPOS")
	void indexOf() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		assertThat(listOps.rightPush(key, v1)).isEqualTo(Long.valueOf(1));
		assertThat(listOps.rightPush(key, v2)).isEqualTo(Long.valueOf(2));
		assertThat(listOps.rightPush(key, v1, v3)).isEqualTo(Long.valueOf(3));
		assertThat(listOps.indexOf(key, v1)).isEqualTo(0);
	}

	@ParameterizedValkeyTest // DATAREDIS-1196
	@EnabledOnCommand("LPOS")
	void lastIndexOf() {

		K key = keyFactory.instance();
		V v1 = valueFactory.instance();
		V v2 = valueFactory.instance();
		V v3 = valueFactory.instance();

		assertThat(listOps.rightPush(key, v1)).isEqualTo(Long.valueOf(1));
		assertThat(listOps.rightPush(key, v2)).isEqualTo(Long.valueOf(2));
		assertThat(listOps.rightPush(key, v1)).isEqualTo(Long.valueOf(3));
		assertThat(listOps.rightPush(key, v3)).isEqualTo(Long.valueOf(4));
		assertThat(listOps.lastIndexOf(key, v1)).isEqualTo(2);
	}

}
