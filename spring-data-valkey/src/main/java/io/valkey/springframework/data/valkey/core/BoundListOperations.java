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
package io.valkey.springframework.data.valkey.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.valkey.springframework.data.valkey.connection.ValkeyListCommands.Direction;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * List operations bound to a certain key.
 *
 * @author Costin Leau
 * @author Mark Paluch
 */
public interface BoundListOperations<K, V> extends BoundKeyOperations<K> {

	/**
	 * Get elements between {@code begin} and {@code end} from list at the bound key.
	 *
	 * @param start
	 * @param end
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lrange">Valkey Documentation: LRANGE</a>
	 */
	@Nullable
	List<V> range(long start, long end);

	/**
	 * Trim list at the bound key to elements between {@code start} and {@code end}.
	 *
	 * @param start
	 * @param end
	 * @see <a href="https://valkey.io/commands/ltrim">Valkey Documentation: LTRIM</a>
	 */
	void trim(long start, long end);

	/**
	 * Get the size of list stored at the bound key.
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/llen">Valkey Documentation: LLEN</a>
	 */
	@Nullable
	Long size();

	/**
	 * Prepend {@code value} to the bound key.
	 *
	 * @param value
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lpush">Valkey Documentation: LPUSH</a>
	 */
	@Nullable
	Long leftPush(V value);

	/**
	 * Prepend {@code values} to the bound key.
	 *
	 * @param values
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lpush">Valkey Documentation: LPUSH</a>
	 */
	@Nullable
	Long leftPushAll(V... values);

	/**
	 * Prepend {@code values} to the bound key only if the list exists.
	 *
	 * @param value
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lpushx">Valkey Documentation: LPUSHX</a>
	 */
	@Nullable
	Long leftPushIfPresent(V value);

	/**
	 * Prepend {@code values} to the bound key before {@code value}.
	 *
	 * @param value
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lpush">Valkey Documentation: LPUSH</a>
	 */
	@Nullable
	Long leftPush(V pivot, V value);

	/**
	 * Append {@code value} to the bound key.
	 *
	 * @param value
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/rpush">Valkey Documentation: RPUSH</a>
	 */
	@Nullable
	Long rightPush(V value);

	/**
	 * Append {@code values} to the bound key.
	 *
	 * @param values
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/rpush">Valkey Documentation: RPUSH</a>
	 */
	@Nullable
	Long rightPushAll(V... values);

	/**
	 * Append {@code values} to the bound key only if the list exists.
	 *
	 * @param value
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/rpushx">Valkey Documentation: RPUSHX</a>
	 */
	@Nullable
	Long rightPushIfPresent(V value);

	/**
	 * Append {@code values} to the bound key before {@code value}.
	 *
	 * @param value
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lpush">Valkey Documentation: RPUSH</a>
	 */
	@Nullable
	Long rightPush(V pivot, V value);

	/**
	 * Atomically returns and removes the first/last element (head/tail depending on the {@code from} argument) of the
	 * list stored at the bound key, and pushes the element at the first/last element (head/tail depending on the
	 * {@code to} argument) of the list stored at {@code destinationKey}.
	 *
	 * @param from must not be {@literal null}.
	 * @param destinationKey must not be {@literal null}.
	 * @param to must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/lmove">Valkey Documentation: LMOVE</a>
	 */
	@Nullable
	V move(Direction from, K destinationKey, Direction to);

	/**
	 * Atomically returns and removes the first/last element (head/tail depending on the {@code from} argument) of the
	 * list stored at the bound key, and pushes the element at the first/last element (head/tail depending on the
	 * {@code to} argument) of the list stored at {@code destinationKey}.
	 * <p>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param from must not be {@literal null}.
	 * @param destinationKey must not be {@literal null}.
	 * @param to must not be {@literal null}.
	 * @param timeout
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/blmove">Valkey Documentation: BLMOVE</a>
	 */
	@Nullable
	V move(Direction from, K destinationKey, Direction to, Duration timeout);

	/**
	 * Atomically returns and removes the first/last element (head/tail depending on the {@code from} argument) of the
	 * list stored at the bound key, and pushes the element at the first/last element (head/tail depending on the
	 * {@code to} argument) of the list stored at {@code destinationKey}.
	 * <p>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param from must not be {@literal null}.
	 * @param destinationKey must not be {@literal null}.
	 * @param to must not be {@literal null}.
	 * @param timeout
	 * @param unit
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/blmove">Valkey Documentation: BLMOVE</a>
	 */
	@Nullable
	V move(Direction from, K destinationKey, Direction to, long timeout, TimeUnit unit);

	/**
	 * Set the {@code value} list element at {@code index}.
	 *
	 * @param index
	 * @param value
	 * @see <a href="https://valkey.io/commands/lset">Valkey Documentation: LSET</a>
	 */
	void set(long index, V value);

	/**
	 * Removes the first {@code count} occurrences of {@code value} from the list stored at the bound key.
	 *
	 * @param count
	 * @param value
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lrem">Valkey Documentation: LREM</a>
	 */
	@Nullable
	Long remove(long count, Object value);

	/**
	 * Returns the first element from the list at the bound {@code key}.
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.4
	 */
	@Nullable
	V getFirst();

	/**
	 * Returns the last element from the list at the bound {@code key}.
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.4
	 */
	@Nullable
	V getLast();

	/**
	 * Get element at {@code index} from list at the bound key.
	 *
	 * @param index
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lindex">Valkey Documentation: LINDEX</a>
	 */
	@Nullable
	V index(long index);

	/**
	 * Returns the index of the first occurrence of the specified value in the list at at {@code key}. <br />
	 * Requires Valkey 6.0.6 or newer.
	 *
	 * @param value must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction or when not contained in list.
	 * @since 2.4
	 * @see <a href="https://valkey.io/commands/lpos">Valkey Documentation: LPOS</a>
	 */
	@Nullable
	Long indexOf(V value);

	/**
	 * Returns the index of the last occurrence of the specified value in the list at at {@code key}. <br />
	 * Requires Valkey 6.0.6 or newer.
	 *
	 * @param value must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction or when not contained in list.
	 * @since 2.4
	 * @see <a href="https://valkey.io/commands/lpos">Valkey Documentation: LPOS</a>
	 */
	@Nullable
	Long lastIndexOf(V value);

	/**
	 * Removes and returns first element in list stored at the bound key.
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lpop">Valkey Documentation: LPOP</a>
	 */
	@Nullable
	V leftPop();

	/**
	 * Removes and returns first {@code} elements in list stored at {@code key}.
	 *
	 * @param count
	 * @return can be {@literal null}.
	 * @see <a href="https://valkey.io/commands/lpop">Valkey Documentation: LPOP</a>
	 * @since 2.6
	 */
	@Nullable
	List<V> leftPop(long count);

	/**
	 * Removes and returns first element from lists stored at the bound key . <br>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param timeout
	 * @param unit must not be {@literal null}.
	 * @return {@literal null} when timeout reached or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/blpop">Valkey Documentation: BLPOP</a>
	 */
	@Nullable
	V leftPop(long timeout, TimeUnit unit);

	/**
	 * Removes and returns first element from lists stored at the bound key . <br>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param timeout must not be {@literal null}.
	 * @return {@literal null} when timeout reached or used in pipeline / transaction.
	 * @throws IllegalArgumentException if the timeout is {@literal null} or negative.
	 * @since 2.3
	 * @see <a href="https://valkey.io/commands/blpop">Valkey Documentation: BLPOP</a>
	 */
	@Nullable
	default V leftPop(Duration timeout) {

		Assert.notNull(timeout, "Timeout must not be null");
		Assert.isTrue(!timeout.isNegative(), "Timeout must not be negative");

		return leftPop(TimeoutUtils.toSeconds(timeout), TimeUnit.SECONDS);
	}

	/**
	 * Removes and returns last element in list stored at the bound key.
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/rpop">Valkey Documentation: RPOP</a>
	 */
	@Nullable
	V rightPop();

	/**
	 * Removes and returns last {@code} elements in list stored at {@code key}.
	 *
	 * @param count
	 * @return can be {@literal null}.
	 * @see <a href="https://valkey.io/commands/rpop">Valkey Documentation: RPOP</a>
	 * @since 2.6
	 */
	@Nullable
	List<V> rightPop(long count);

	/**
	 * Removes and returns last element from lists stored at the bound key. <br>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param timeout
	 * @param unit must not be {@literal null}.
	 * @return {@literal null} when timeout reached or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/brpop">Valkey Documentation: BRPOP</a>
	 */
	@Nullable
	V rightPop(long timeout, TimeUnit unit);

	/**
	 * Removes and returns last element from lists stored at the bound key. <br>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param timeout must not be {@literal null}.
	 * @return {@literal null} when timeout reached or used in pipeline / transaction.
	 * @throws IllegalArgumentException if the timeout is {@literal null} or negative.
	 * @since 2.3
	 * @see <a href="https://valkey.io/commands/brpop">Valkey Documentation: BRPOP</a>
	 */
	@Nullable
	default V rightPop(Duration timeout) {

		Assert.notNull(timeout, "Timeout must not be null");
		Assert.isTrue(!timeout.isNegative(), "Timeout must not be negative");

		return rightPop(TimeoutUtils.toSeconds(timeout), TimeUnit.SECONDS);
	}

	ValkeyOperations<K, V> getOperations();
}
