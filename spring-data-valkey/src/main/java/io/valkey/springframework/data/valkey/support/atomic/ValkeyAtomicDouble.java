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

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

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
 * Atomic double backed by Valkey. Uses Valkey atomic increment/decrement and watch/multi/exec operations for CAS
 * operations.
 *
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Graham MacMaster
 * @author Ning Wei
 */
public class ValkeyAtomicDouble extends Number implements Serializable, BoundKeyOperations<String> {

	@Serial
	private static final long serialVersionUID = 1L;

	private volatile String key;

	private final ValueOperations<String, Double> operations;
	private final ValkeyOperations<String, Double> generalOps;

	/**
	 * Constructs a new {@link ValkeyAtomicDouble} instance. Uses the value existing in Valkey or {@code 0} if none is
	 * found.
	 *
	 * @param valkeyCounter Valkey key of this counter.
	 * @param factory connection factory.
	 */
	public ValkeyAtomicDouble(String valkeyCounter, ValkeyConnectionFactory factory) {
		this(valkeyCounter, factory, null);
	}

	/**
	 * Constructs a new {@link ValkeyAtomicDouble} instance with a {@code initialValue} that overwrites the existing value.
	 *
	 * @param valkeyCounter Valkey key of this counter.
	 * @param factory connection factory.
	 * @param initialValue initial value to set.
	 */
	public ValkeyAtomicDouble(String valkeyCounter, ValkeyConnectionFactory factory, double initialValue) {
		this(valkeyCounter, factory, Double.valueOf(initialValue));
	}

	private ValkeyAtomicDouble(String valkeyCounter, ValkeyConnectionFactory factory, @Nullable Double initialValue) {

		Assert.hasText(valkeyCounter, "a valid counter name is required");
		Assert.notNull(factory, "a valid factory is required");

		ValkeyTemplate<String, Double> valkeyTemplate = new ValkeyTemplate<>();
		valkeyTemplate.setKeySerializer(ValkeySerializer.string());
		valkeyTemplate.setValueSerializer(new GenericToStringSerializer<>(Double.class));
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

	/**
	 * Constructs a new {@link ValkeyAtomicDouble} instance. Uses the value existing in Valkey or 0 if none is found.
	 *
	 * @param valkeyCounter Valkey key of this counter.
	 * @param template the template.
	 * @see #ValkeyAtomicDouble(String, ValkeyConnectionFactory, double)
	 */
	public ValkeyAtomicDouble(String valkeyCounter, ValkeyOperations<String, Double> template) {
		this(valkeyCounter, template, null);
	}

	/**
	 * Constructs a new {@link ValkeyAtomicDouble} instance with a {@code initialValue} that overwrites the existing value
	 * at {@code valkeyCounter}.
	 * <p>
	 * Note: You need to configure the given {@code template} with appropriate {@link ValkeySerializer} for the key and
	 * value.
	 * <p>
	 * As an alternative one could use the {@link #ValkeyAtomicDouble(String, ValkeyConnectionFactory, Double)} constructor
	 * which uses appropriate default serializers.
	 *
	 * @param valkeyCounter Valkey key of this counter.
	 * @param template the template
	 * @param initialValue initial value to set if the Valkey key is absent.
	 */
	public ValkeyAtomicDouble(String valkeyCounter, ValkeyOperations<String, Double> template, double initialValue) {
		this(valkeyCounter, template, Double.valueOf(initialValue));
	}

	private ValkeyAtomicDouble(String valkeyCounter, ValkeyOperations<String, Double> template,
			@Nullable Double initialValue) {

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
		operations.setIfAbsent(key, (double) 0);
	}

	/**
	 * Get the current value.
	 *
	 * @return the current value.
	 */
	public double get() {

		Double value = operations.get(key);

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
	public void set(double newValue) {
		operations.set(key, newValue);
	}

	/**
	 * Set to the given value and return the old value.
	 *
	 * @param newValue the new value.
	 * @return the previous value.
	 */
	public double getAndSet(double newValue) {

		Double value = operations.getAndSet(key, newValue);

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
	public boolean compareAndSet(double expect, double update) {
		return generalOps.execute(new CompareAndSet<>(this::get, this::set, key, expect, update));
	}

	/**
	 * Atomically increment by one the current value.
	 *
	 * @return the previous value.
	 */
	public double getAndIncrement() {
		return incrementAndGet() - 1.0;
	}

	/**
	 * Atomically decrement by one the current value.
	 *
	 * @return the previous value.
	 */
	public double getAndDecrement() {
		return decrementAndGet() + 1.0;
	}

	/**
	 * Atomically add the given value to current value.
	 *
	 * @param delta the value to add.
	 * @return the previous value.
	 */
	public double getAndAdd(double delta) {
		return addAndGet(delta) - delta;
	}

	/**
	 * Atomically update the current value using the given {@link DoubleUnaryOperator update function}.
	 *
	 * @param updateFunction the function which calculates the value to set. Should be a pure function (no side effects),
	 *          because it will be applied several times if update attempts fail due to concurrent calls. Must not be
	 *          {@literal null}.
	 * @return the previous value.
	 * @since 2.2
	 */
	public double getAndUpdate(DoubleUnaryOperator updateFunction) {

		Assert.notNull(updateFunction, "Update function must not be null");

		double previousValue, newValue;

		do {
			previousValue = get();
			newValue = updateFunction.applyAsDouble(previousValue);
		} while (!compareAndSet(previousValue, newValue));

		return previousValue;
	}

	/**
	 * Atomically update the current value using the given {@link DoubleBinaryOperator accumulator function}. The new
	 * value is calculated by applying the accumulator function to the current value and the given {@code updateValue}.
	 *
	 * @param updateValue the value which will be passed into the accumulator function.
	 * @param accumulatorFunction the function which calculates the value to set. Should be a pure function (no side
	 *          effects), because it will be applied several times if update attempts fail due to concurrent calls. Must
	 *          not be {@literal null}.
	 * @return the previous value.
	 * @since 2.2
	 */
	public double getAndAccumulate(double updateValue, DoubleBinaryOperator accumulatorFunction) {

		Assert.notNull(accumulatorFunction, "Accumulator function must not be null");

		double previousValue, newValue;

		do {
			previousValue = get();
			newValue = accumulatorFunction.applyAsDouble(previousValue, updateValue);
		} while (!compareAndSet(previousValue, newValue));

		return previousValue;
	}

	/**
	 * Atomically increment by one the current value.
	 *
	 * @return the updated value.
	 */
	public double incrementAndGet() {
		return operations.increment(key, 1.0);
	}

	/**
	 * Atomically decrement by one the current value.
	 *
	 * @return the updated value.
	 */
	public double decrementAndGet() {
		return operations.increment(key, -1.0);
	}

	/**
	 * Atomically add the given value to current value.
	 *
	 * @param delta the value to add.
	 * @return the updated value.
	 */
	public double addAndGet(double delta) {
		return operations.increment(key, delta);
	}

	/**
	 * Atomically update the current value using the given {@link DoubleUnaryOperator update function}.
	 *
	 * @param updateFunction the function which calculates the value to set. Should be a pure function (no side effects),
	 *          because it will be applied several times if update attempts fail due to concurrent calls. Must not be
	 *          {@literal null}.
	 * @return the updated value.
	 * @since 2.2
	 */
	public double updateAndGet(DoubleUnaryOperator updateFunction) {

		Assert.notNull(updateFunction, "Update function must not be null");

		double previousValue, newValue;

		do {
			previousValue = get();
			newValue = updateFunction.applyAsDouble(previousValue);
		} while (!compareAndSet(previousValue, newValue));

		return newValue;
	}

	/**
	 * Atomically update the current value using the given {@link DoubleBinaryOperator accumulator function}. The new
	 * value is calculated by applying the accumulator function to the current value and the given {@code updateValue}.
	 *
	 * @param updateValue the value which will be passed into the accumulator function.
	 * @param accumulatorFunction the function which calculates the value to set. Should be a pure function (no side
	 *          effects), because it will be applied several times if update attempts fail due to concurrent calls. Must
	 *          not be {@literal null}.
	 * @return the updated value.
	 * @since 2.2
	 */
	public double accumulateAndGet(double updateValue, DoubleBinaryOperator accumulatorFunction) {

		Assert.notNull(accumulatorFunction, "Accumulator function must not be null");

		double previousValue, newValue;

		do {
			previousValue = get();
			newValue = accumulatorFunction.applyAsDouble(previousValue, updateValue);
		} while (!compareAndSet(previousValue, newValue));

		return newValue;
	}

	/**
	 * @return the String representation of the current value.
	 */
	@Override
	public String toString() {
		return Double.toString(get());
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
		return (int) get();
	}

	@Override
	public long longValue() {
		return (long) get();
	}

	@Override
	public float floatValue() {
		return (float) get();
	}

	@Override
	public double doubleValue() {
		return get();
	}

}
