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
package io.valkey.springframework.data.valkey.support.atomic;

import static org.assertj.core.api.Assertions.*;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.dao.DataRetrievalFailureException;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.GenericToStringSerializer;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Integration test of {@link ValkeyAtomicInteger}
 *
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Graham MacMaster
 */
@MethodSource("testParams")
public class ValkeyAtomicIntegerIntegrationTests {

	private final ValkeyConnectionFactory factory;
	private final ValkeyTemplate<String, Integer> template;

	private ValkeyAtomicInteger intCounter;

	public ValkeyAtomicIntegerIntegrationTests(ValkeyConnectionFactory factory) {

		this.factory = factory;

		this.template = new ValkeyTemplate<>();
		this.template.setConnectionFactory(factory);
		this.template.setKeySerializer(StringValkeySerializer.UTF_8);
		this.template.setValueSerializer(new GenericToStringSerializer<>(Integer.class));
		this.template.afterPropertiesSet();
	}

	public static Collection<Object[]> testParams() {
		return AtomicCountersParam.testParams();
	}

	@BeforeEach
	void before() {

		ValkeyConnection connection = factory.getConnection();
		connection.flushDb();
		connection.close();

		this.intCounter = new ValkeyAtomicInteger(getClass().getSimpleName() + ":int", factory);
	}

	@ParameterizedValkeyTest
	void testCheckAndSet() {

		intCounter.set(0);
		assertThat(intCounter.compareAndSet(1, 10)).isFalse();
		assertThat(intCounter.compareAndSet(0, 10)).isTrue();
		assertThat(intCounter.compareAndSet(10, 0)).isTrue();
	}

	@ParameterizedValkeyTest
	void testIncrementAndGet() {

		intCounter.set(0);
		assertThat(intCounter.incrementAndGet()).isOne();
	}

	@ParameterizedValkeyTest
	void testAddAndGet() {

		intCounter.set(0);
		int delta = 5;
		assertThat(intCounter.addAndGet(delta)).isEqualTo(delta);
	}

	@ParameterizedValkeyTest
	void testDecrementAndGet() {

		intCounter.set(1);
		assertThat(intCounter.decrementAndGet()).isZero();
	}

	@ParameterizedValkeyTest // DATAREDIS-469
	void testGetAndIncrement() {

		intCounter.set(1);
		assertThat(intCounter.getAndIncrement()).isOne();
		assertThat(intCounter.get()).isEqualTo(2);
	}

	@ParameterizedValkeyTest // DATAREDIS-469
	void testGetAndAdd() {

		intCounter.set(1);
		assertThat(intCounter.getAndAdd(5)).isOne();
		assertThat(intCounter.get()).isEqualTo(6);
	}

	@ParameterizedValkeyTest // DATAREDIS-469
	void testGetAndDecrement() {

		intCounter.set(1);
		assertThat(intCounter.getAndDecrement()).isOne();
		assertThat(intCounter.get()).isZero();
	}

	@ParameterizedValkeyTest // DATAREDIS-469
	void testGetAndSet() {

		intCounter.set(1);
		assertThat(intCounter.getAndSet(5)).isOne();
		assertThat(intCounter.get()).isEqualTo(5);
	}

	@ParameterizedValkeyTest // DATAREDIS-108, DATAREDIS-843
	void testCompareSet() throws Exception {

		AtomicBoolean alreadySet = new AtomicBoolean(false);
		int NUM = 50;
		String KEY = getClass().getSimpleName() + ":atomic:counter";
		CountDownLatch latch = new CountDownLatch(NUM);
		AtomicBoolean failed = new AtomicBoolean(false);
		ValkeyAtomicInteger atomicInteger = new ValkeyAtomicInteger(KEY, factory);

		for (int i = 0; i < NUM; i++) {

			new Thread(() -> {

				try {
					if (atomicInteger.compareAndSet(0, 1)) {
						if (alreadySet.get()) {
							failed.set(true);
						}
						alreadySet.set(true);
					}
				} finally {
					latch.countDown();
				}
			}).start();
		}

		latch.await();

		assertThat(failed.get()).withFailMessage("counter already modified").isFalse();
	}

	@ParameterizedValkeyTest // DATAREDIS-317
	void testShouldThrowExceptionIfValkeyAtomicIntegerIsUsedWithValkeyTemplateAndNoKeySerializer() {

		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new ValkeyAtomicInteger("foo", new ValkeyTemplate<>()))
				.withMessageContaining("a valid key serializer in template is required");
	}

	@ParameterizedValkeyTest // DATAREDIS-317
	void testShouldThrowExceptionIfValkeyAtomicIntegerIsUsedWithValkeyTemplateAndNoValueSerializer() {

		ValkeyTemplate<String, Integer> template = new ValkeyTemplate<>();
		template.setKeySerializer(StringValkeySerializer.UTF_8);

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new ValkeyAtomicInteger("foo", template))
				.withMessageContaining("a valid value serializer in template is required");
	}

	@ParameterizedValkeyTest // DATAREDIS-317
	void testShouldBeAbleToUseValkeyAtomicIntegerWithProperlyConfiguredValkeyTemplate() {

		ValkeyAtomicInteger ral = new ValkeyAtomicInteger("DATAREDIS-317.atomicInteger", template);
		ral.set(32);

		assertThat(ral.get()).isEqualTo(32);
	}

	@ParameterizedValkeyTest // DATAREDIS-469
	void getThrowsExceptionWhenKeyHasBeenRemoved() {

		// setup integer
		ValkeyAtomicInteger test = new ValkeyAtomicInteger("test", factory, 1);
		assertThat(test.get()).isOne(); // this passes

		template.delete("test");

		assertThatExceptionOfType(DataRetrievalFailureException.class).isThrownBy(test::get)
				.withMessageContaining("'test' seems to no longer exist");
	}

	@ParameterizedValkeyTest // DATAREDIS-469
	void getAndSetReturnsZeroWhenKeyHasBeenRemoved() {

		// setup integer
		ValkeyAtomicInteger test = new ValkeyAtomicInteger("test", factory, 1);
		assertThat(test.get()).isOne(); // this passes

		template.delete("test");

		assertThat(test.getAndSet(2)).isZero();
	}

	@ParameterizedValkeyTest // DATAREDIS-874
	void updateAndGetAppliesGivenUpdateFunctionAndReturnsUpdatedValue() {

		AtomicBoolean operatorHasBeenApplied = new AtomicBoolean();
		int initialValue = 5;
		int expectedNewValue = 10;
		intCounter.set(initialValue);

		IntUnaryOperator updateFunction = input -> {

			operatorHasBeenApplied.set(true);

			return expectedNewValue;
		};

		int result = intCounter.updateAndGet(updateFunction);

		assertThat(result).isEqualTo(expectedNewValue);
		assertThat(intCounter.get()).isEqualTo(expectedNewValue);
		assertThat(operatorHasBeenApplied).isTrue();
	}

	@ParameterizedValkeyTest // DATAREDIS-874
	void updateAndGetUsesCorrectArguments() {

		AtomicBoolean operatorHasBeenApplied = new AtomicBoolean();
		int initialValue = 5;
		intCounter.set(initialValue);

		IntUnaryOperator updateFunction = input -> {

			operatorHasBeenApplied.set(true);

			assertThat(input).isEqualTo(initialValue);

			return -1;
		};

		intCounter.updateAndGet(updateFunction);

		assertThat(operatorHasBeenApplied).isTrue();
	}

	@ParameterizedValkeyTest // DATAREDIS-874
	void getAndUpdateAppliesGivenUpdateFunctionAndReturnsOriginalValue() {

		AtomicBoolean operatorHasBeenApplied = new AtomicBoolean();
		int initialValue = 5;
		int expectedNewValue = 10;
		intCounter.set(initialValue);

		IntUnaryOperator updateFunction = input -> {

			operatorHasBeenApplied.set(true);

			return expectedNewValue;
		};

		int result = intCounter.getAndUpdate(updateFunction);

		assertThat(result).isEqualTo(initialValue);
		assertThat(intCounter.get()).isEqualTo(expectedNewValue);
		assertThat(operatorHasBeenApplied).isTrue();
	}

	@ParameterizedValkeyTest // DATAREDIS-874
	void getAndUpdateUsesCorrectArguments() {

		AtomicBoolean operatorHasBeenApplied = new AtomicBoolean();
		int initialValue = 5;
		intCounter.set(initialValue);

		IntUnaryOperator updateFunction = input -> {

			operatorHasBeenApplied.set(true);

			assertThat(input).isEqualTo(initialValue);

			return -1;
		};

		intCounter.getAndUpdate(updateFunction);

		assertThat(operatorHasBeenApplied).isTrue();
	}

	@ParameterizedValkeyTest // DATAREDIS-874
	void accumulateAndGetAppliesGivenAccumulatorFunctionAndReturnsUpdatedValue() {

		AtomicBoolean operatorHasBeenApplied = new AtomicBoolean();
		int initialValue = 5;
		int expectedNewValue = 10;
		intCounter.set(initialValue);

		IntBinaryOperator accumulatorFunction = (x, y) -> {

			operatorHasBeenApplied.set(true);

			return expectedNewValue;
		};

		int result = intCounter.accumulateAndGet(15, accumulatorFunction);

		assertThat(result).isEqualTo(expectedNewValue);
		assertThat(intCounter.get()).isEqualTo(expectedNewValue);
		assertThat(operatorHasBeenApplied).isTrue();
	}

	@ParameterizedValkeyTest // DATAREDIS-874
	void accumulateAndGetUsesCorrectArguments() {

		AtomicBoolean operatorHasBeenApplied = new AtomicBoolean();
		int initialValue = 5;
		intCounter.set(initialValue);

		IntBinaryOperator accumulatorFunction = (x, y) -> {

			operatorHasBeenApplied.set(true);

			assertThat(x).isEqualTo(initialValue);
			assertThat(y).isEqualTo(15);

			return -1;
		};

		intCounter.accumulateAndGet(15, accumulatorFunction);

		assertThat(operatorHasBeenApplied).isTrue();
	}

	@ParameterizedValkeyTest // DATAREDIS-874
	void getAndAccumulateAppliesGivenAccumulatorFunctionAndReturnsOriginalValue() {

		AtomicBoolean operatorHasBeenApplied = new AtomicBoolean(false);
		int initialValue = 5;
		int expectedNewValue = 10;
		intCounter.set(initialValue);

		IntBinaryOperator accumulatorFunction = (x, y) -> {

			operatorHasBeenApplied.set(true);

			return expectedNewValue;
		};

		int result = intCounter.getAndAccumulate(15, accumulatorFunction);

		assertThat(result).isEqualTo(initialValue);
		assertThat(intCounter.get()).isEqualTo(expectedNewValue);
		assertThat(operatorHasBeenApplied).isTrue();
	}

	@ParameterizedValkeyTest // DATAREDIS-874
	void getAndAccumulateUsesCorrectArguments() {

		AtomicBoolean operatorHasBeenApplied = new AtomicBoolean();
		int initialValue = 5;
		intCounter.set(initialValue);

		IntBinaryOperator accumulatorFunction = (x, y) -> {

			operatorHasBeenApplied.set(true);

			assertThat(x).isEqualTo(initialValue);
			assertThat(y).isEqualTo(15);

			return -1;
		};

		intCounter.getAndAccumulate(15, accumulatorFunction);

		assertThat(operatorHasBeenApplied).isTrue();
	}
}
