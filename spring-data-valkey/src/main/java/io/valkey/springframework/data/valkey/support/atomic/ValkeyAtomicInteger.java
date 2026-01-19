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
package io.valkey.springframework.data.valkey.support.atomic;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import org.springframework.dao.DataRetrievalFailureException;
import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.BoundKeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.ValueOperations;
import io.valkey.springframework.data.valkey.serializer.GenericToStringSerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Atomic integer backed by Valkey. Uses Valkey atomic increment/decrement and watch/multi/exec operations for CAS
 * operations.
 *
 * @author Costin Leau
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Graham MacMaster
 * @author Ning Wei
 * @see java.util.concurrent.atomic.AtomicInteger
 */
public class ValkeyAtomicInteger extends Number implements Serializable, BoundKeyOperations<String> {

	@Serial
	private static final long serialVersionUID = 1L;

	private volatile String key;

	private final ValueOperations<String, Integer> operations;
	private final ValkeyOperations<String, Integer> generalOps;

	/**
	 * Constructs a new {@link ValkeyAtomicInteger} instance. Uses the value existing in Valkey or {@code 0} if none is
	 * found.
	 *
	 * @param valkeyCounter Valkey key of this counter.
	 * @param factory connection factory.
	 */
	public ValkeyAtomicInteger(String valkeyCounter, ValkeyConnectionFactory factory) {
		this(valkeyCounter, factory, null);
	}

	/**
	 * Constructs a new {@link ValkeyAtomicInteger} instance with a {@code initialValue} that overwrites the existing
	 * value.
	 *
	 * @param valkeyCounter Valkey key of this counter.
	 * @param factory connection factory.
	 * @param initialValue initial value to set.
	 */
	public ValkeyAtomicInteger(String valkeyCounter, ValkeyConnectionFactory factory, int initialValue) {
		this(valkeyCounter, factory, Integer.valueOf(initialValue));
	}

	/**
	 * Constructs a new {@link ValkeyAtomicInteger} instance. Uses the value existing in Valkey or {@code 0} if none is
	 * found.
	 *
	 * @param valkeyCounter Valkey key of this counter.
	 * @param template the template.
	 * @see #ValkeyAtomicInteger(String, ValkeyConnectionFactory, int)
	 */
	public ValkeyAtomicInteger(String valkeyCounter, ValkeyOperations<String, Integer> template) {
		this(valkeyCounter, template, null);
	}

	/**
	 * Constructs a new {@link ValkeyAtomicInteger} instance instance with a {@code initialValue} that overwrites the
	 * existing value.
	 * <p>
	 * Note: You need to configure the given {@code template} with appropriate {@link ValkeySerializer} for the key and
	 * value.
	 * <p>
	 * As an alternative one could use the {@link #ValkeyAtomicInteger(String, ValkeyConnectionFactory, Integer)}
	 * constructor which uses appropriate default serializers.
	 *
	 * @param valkeyCounter Valkey key of this counter.
	 * @param template the template.
	 * @param initialValue initial value to set if the Valkey key is absent.
	 */
	public ValkeyAtomicInteger(String valkeyCounter, ValkeyOperations<String, Integer> template, int initialValue) {
		this(valkeyCounter, template, Integer.valueOf(initialValue));
	}

	private ValkeyAtomicInteger(String valkeyCounter, ValkeyConnectionFactory factory, @Nullable Integer initialValue) {

		ValkeyTemplate<String, Integer> valkeyTemplate = new ValkeyTemplate<>();
		valkeyTemplate.setKeySerializer(ValkeySerializer.string());
		valkeyTemplate.setValueSerializer(new GenericToStringSerializer<>(Integer.class));
		valkeyTemplate.setExposeConnection(true);
		valkeyTemplate.setConnectionFactory(factory);
		valkeyTemplate.afterPropertiesSet();

		this.key = valkeyCounter;
		this.generalOps = valkeyTemplate;
		this.operations = generalOps.opsForValue();

		if (initialValue == null) {
			initializeIfAbsent();
		} else {
			set(initialValue);
		}
	}

	private ValkeyAtomicInteger(String valkeyCounter, ValkeyOperations<String, Integer> template,
			@Nullable Integer initialValue) {

		Assert.hasText(valkeyCounter, "a valid counter name is required");
		Assert.notNull(template, "a valid template is required");
		Assert.notNull(template.getKeySerializer(), "a valid key serializer in template is required");
		Assert.notNull(template.getValueSerializer(), "a valid value serializer in template is required");

		this.key = valkeyCounter;
		this.generalOps = template;
		this.operations = generalOps.opsForValue();

		if (initialValue == null) {
			initializeIfAbsent();
		} else {
			set(initialValue);
		}
	}

	private void initializeIfAbsent() {
		operations.setIfAbsent(key, 0);
	}

	/**
	 * Get the current value.
	 *
	 * @return the current value.
	 */
	public int get() {

		Integer value = operations.get(key);

		if (value != null) {
			return value;
		}

		throw new DataRetrievalFailureException("The key '%s' seems to no longer exist".formatted(key));
	}

	/**
	 * Set to the given value.
	 *
	 * @param newValue the new value.
	 */
	public void set(int newValue) {
		operations.set(key, newValue);
	}

	/**
	 * Set to the given value and return the old value.
	 *
	 * @param newValue the new value.
	 * @return the previous value.
	 */
	public int getAndSet(int newValue) {

		Integer value = operations.getAndSet(key, newValue);

		return value != null ? value : 0;
	}

	/**
	 * Atomically set the value to the given updated value if the current value {@code ==} the expected value.
	 *
	 * @param expect the expected value.
	 * @param update the new value.
	 * @return {@literal true} if successful. {@literal false} indicates that the actual value was not equal to the
	 *         expected value.
	 */
	public boolean compareAndSet(int expect, int update) {
		return generalOps.execute(new CompareAndSet<>(this::get, this::set, key, expect, update));
	}

	/**
	 * Atomically increment by one the current value.
	 *
	 * @return the previous value.
	 */
	public int getAndIncrement() {
		return incrementAndGet() - 1;
	}

	/**
	 * Atomically decrement by one the current value.
	 *
	 * @return the previous value.
	 */
	public int getAndDecrement() {
		return decrementAndGet() + 1;
	}

	/**
	 * Atomically add the given value to current value.
	 *
	 * @param delta the value to add.
	 * @return the previous value.
	 */
	public int getAndAdd(int delta) {
		return addAndGet(delta) - delta;
	}

	/**
	 * Atomically update the current value using the given {@link IntUnaryOperator update function}.
	 *
	 * @param updateFunction the function which calculates the value to set. Should be a pure function (no side effects),
	 *          because it will be applied several times if update attempts fail due to concurrent calls. Must not be
	 *          {@literal null}.
	 * @return the previous value.
	 * @since 2.2
	 */
	public int getAndUpdate(IntUnaryOperator updateFunction) {

		Assert.notNull(updateFunction, "Update function must not be null");

		int previousValue, newValue;

		do {
			previousValue = get();
			newValue = updateFunction.applyAsInt(previousValue);
		} while (!compareAndSet(previousValue, newValue));

		return previousValue;
	}

	/**
	 * Atomically update the current value using the given {@link IntBinaryOperator accumulator function}. The new value
	 * is calculated by applying the accumulator function to the current value and the given {@code updateValue}.
	 *
	 * @param updateValue the value which will be passed into the accumulator function.
	 * @param accumulatorFunction the function which calculates the value to set. Should be a pure function (no side
	 *          effects), because it will be applied several times if update attempts fail due to concurrent calls. Must
	 *          not be {@literal null}.
	 * @return the previous value.
	 * @since 2.2
	 */
	public int getAndAccumulate(int updateValue, IntBinaryOperator accumulatorFunction) {

		Assert.notNull(accumulatorFunction, "Accumulator function must not be null");

		int previousValue, newValue;

		do {
			previousValue = get();
			newValue = accumulatorFunction.applyAsInt(previousValue, updateValue);
		} while (!compareAndSet(previousValue, newValue));

		return previousValue;
	}

	/**
	 * Atomically increment by one the current value.
	 *
	 * @return the updated value.
	 */
	public int incrementAndGet() {
		return operations.increment(key).intValue();
	}

	/**
	 * Atomically decrement by one the current value.
	 *
	 * @return the updated value.
	 */
	public int decrementAndGet() {
		return operations.decrement(key).intValue();
	}

	/**
	 * Atomically add the given value to current value.
	 *
	 * @param delta the value to add.
	 * @return the updated value.
	 */
	public int addAndGet(int delta) {
		return operations.increment(key, delta).intValue();
	}

	/**
	 * Atomically update the current value using the given {@link IntUnaryOperator update function}.
	 *
	 * @param updateFunction the function which calculates the value to set. Should be a pure function (no side effects),
	 *          because it will be applied several times if update attempts fail due to concurrent calls. Must not be
	 *          {@literal null}.
	 * @return the updated value.
	 * @since 2.2
	 */
	public int updateAndGet(IntUnaryOperator updateFunction) {

		Assert.notNull(updateFunction, "Update function must not be null");

		int previousValue, newValue;

		do {
			previousValue = get();
			newValue = updateFunction.applyAsInt(previousValue);
		} while (!compareAndSet(previousValue, newValue));

		return newValue;
	}

	/**
	 * Atomically update the current value using the given {@link IntBinaryOperator accumulator function}. The new value
	 * is calculated by applying the accumulator function to the current value and the given {@code updateValue}.
	 *
	 * @param updateValue the value which will be passed into the accumulator function.
	 * @param accumulatorFunction the function which calculates the value to set. Should be a pure function (no side
	 *          effects), because it will be applied several times if update attempts fail due to concurrent calls. Must
	 *          not be {@literal null}.
	 * @return the updated value.
	 * @since 2.2
	 */
	public int accumulateAndGet(int updateValue, IntBinaryOperator accumulatorFunction) {

		Assert.notNull(accumulatorFunction, "Accumulator function must not be null");

		int previousValue, newValue;

		do {
			previousValue = get();
			newValue = accumulatorFunction.applyAsInt(previousValue, updateValue);
		} while (!compareAndSet(previousValue, newValue));

		return newValue;
	}

	/**
	 * @return the String representation of the current value.
	 */
	@Override
	public String toString() {
		return Integer.toString(get());
	}

	@Override
	public String getKey() {
		return key;
	}

	@Override
	public DataType getType() {
		return DataType.STRING;
	}

	@Override
	public Long getExpire() {
		return generalOps.getExpire(key);
	}

	@Override
	public Boolean expire(long timeout, TimeUnit unit) {
		return generalOps.expire(key, timeout, unit);
	}

	@Override
	public Boolean expireAt(Date date) {
		return generalOps.expireAt(key, date);
	}

	@Override
	public Boolean persist() {
		return generalOps.persist(key);
	}

	@Override
	public void rename(String newKey) {

		generalOps.rename(key, newKey);
		key = newKey;
	}

	@Override
	public ValkeyOperations<String, ?> getOperations() {
		return generalOps;
	}

	@Override
	public int intValue() {
		return get();
	}

	@Override
	public long longValue() {
		return get();
	}

	@Override
	public float floatValue() {
		return get();
	}

	@Override
	public double doubleValue() {
		return get();
	}
}
