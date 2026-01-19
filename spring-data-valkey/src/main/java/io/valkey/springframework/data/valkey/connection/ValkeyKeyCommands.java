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
package io.valkey.springframework.data.valkey.connection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.KeyScanOptions;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Key-specific commands supported by Valkey.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author ihaohong
 */
public interface ValkeyKeyCommands {

	/**
	 * Copy given {@code sourceKey} to {@code targetKey}.
	 *
	 * @param sourceKey must not be {@literal null}.
	 * @param targetKey must not be {@literal null}.
	 * @param replace whether to replace existing keys.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/copy">Valkey Documentation: COPY</a>
	 * @since 2.6
	 */
	@Nullable
	Boolean copy(byte[] sourceKey, byte[] targetKey, boolean replace);

	/**
	 * Determine if given {@code key} exists.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal true} if key exists. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/exists">Valkey Documentation: EXISTS</a>
	 */
	@Nullable
	default Boolean exists(byte[] key) {

		Assert.notNull(key, "Key must not be null");
		Long count = exists(new byte[][] { key });
		return count != null ? count > 0 : null;
	}

	/**
	 * Count how many of the given {@code keys} exist. Providing the very same {@code key} more than once also counts
	 * multiple times.
	 *
	 * @param keys must not be {@literal null}.
	 * @return the number of keys existing among the ones specified as arguments. {@literal null} when used in pipeline /
	 *         transaction.
	 * @since 2.1
	 */
	@Nullable
	Long exists(byte[]... keys);

	/**
	 * Delete given {@code keys}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return The number of keys that were removed. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/del">Valkey Documentation: DEL</a>
	 */
	@Nullable
	Long del(byte[]... keys);

	/**
	 * Unlink the {@code keys} from the keyspace. Unlike with {@link #del(byte[]...)} the actual memory reclaiming here
	 * happens asynchronously.
	 *
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/unlink">Valkey Documentation: UNLINK</a>
	 * @since 2.1
	 */
	@Nullable
	Long unlink(byte[]... keys);

	/**
	 * Determine the type stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/type">Valkey Documentation: TYPE</a>
	 */
	@Nullable
	DataType type(byte[] key);

	/**
	 * Alter the last access time of given {@code key(s)}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/touch">Valkey Documentation: TOUCH</a>
	 * @since 2.1
	 */
	@Nullable
	Long touch(byte[]... keys);

	/**
	 * Find all keys matching the given {@code pattern}.
	 *
	 * @param pattern must not be {@literal null}.
	 * @return empty {@link Set} if no match found. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/keys">Valkey Documentation: KEYS</a>
	 */
	@Nullable
	Set<byte[]> keys(byte[] pattern);

	/**
	 * Use a {@link Cursor} to iterate over keys.
	 *
	 * @param options must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 2.4
	 * @see <a href="https://valkey.io/commands/scan">Valkey Documentation: SCAN</a>
	 */
	default Cursor<byte[]> scan(KeyScanOptions options) {
		return scan((ScanOptions) options);
	}

	/**
	 * Use a {@link Cursor} to iterate over keys.
	 *
	 * @param options must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.4
	 * @see <a href="https://valkey.io/commands/scan">Valkey Documentation: SCAN</a>
	 */
	Cursor<byte[]> scan(ScanOptions options);

	/**
	 * Return a random key from the keyspace.
	 *
	 * @return {@literal null} if no keys available or when used in pipeline or transaction.
	 * @see <a href="https://valkey.io/commands/randomkey">Valkey Documentation: RANDOMKEY</a>
	 */
	@Nullable
	byte[] randomKey();

	/**
	 * Rename key {@code oldKey} to {@code newKey}.
	 *
	 * @param oldKey must not be {@literal null}.
	 * @param newKey must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/rename">Valkey Documentation: RENAME</a>
	 */
	void rename(byte[] oldKey, byte[] newKey);

	/**
	 * Rename key {@code oldKey} to {@code newKey} only if {@code newKey} does not exist.
	 *
	 * @param oldKey must not be {@literal null}.
	 * @param newKey must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/renamenx">Valkey Documentation: RENAMENX</a>
	 */
	@Nullable
	Boolean renameNX(byte[] oldKey, byte[] newKey);

	/**
	 * @param key must not be {@literal null}.
	 * @param expiration the {@link io.valkey.springframework.data.valkey.core.types.Expiration} to apply.
	 * @param options additional options to be sent along with the command.
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @since 3.5
	 * @see <a href="https://valkey.io/commands/expire">Valkey Documentation: EXPIRE</a>
	 * @see <a href="https://valkey.io/commands/pexpire">Valkey Documentation: PEXPIRE</a>
	 * @see <a href="https://valkey.io/commands/expireat">Valkey Documentation: EXPIREAT</a>
	 * @see <a href="https://valkey.io/commands/pexpireat">Valkey Documentation: PEXPIREAT</a>
	 * @see <a href="https://valkey.io/commands/persist">Valkey Documentation: PERSIST</a>
	 */
	@Nullable
	default Boolean applyExpiration(byte[] key, io.valkey.springframework.data.valkey.core.types.Expiration expiration,
			ExpirationOptions options) {

		if (expiration.isPersistent()) {
			return persist(key);
		}

		if (ObjectUtils.nullSafeEquals(ExpirationOptions.none(), options)) {
			if (ObjectUtils.nullSafeEquals(TimeUnit.MILLISECONDS, expiration.getTimeUnit())) {
				if (expiration.isUnixTimestamp()) {
					return expireAt(key, expiration.getExpirationTimeInMilliseconds());
				}
				return expire(key, expiration.getExpirationTimeInMilliseconds());
			}
			if (expiration.isUnixTimestamp()) {
				return expireAt(key, expiration.getExpirationTimeInSeconds());
			}
			return expire(key, expiration.getExpirationTimeInSeconds());
		}

		if (ObjectUtils.nullSafeEquals(TimeUnit.MILLISECONDS, expiration.getTimeUnit())) {
			if (expiration.isUnixTimestamp()) {
				return expireAt(key, expiration.getExpirationTimeInMilliseconds(), options.getCondition());
			}

			return expire(key, expiration.getExpirationTimeInMilliseconds(), options.getCondition());
		}

		if (expiration.isUnixTimestamp()) {
			return expireAt(key, expiration.getExpirationTimeInSeconds(), options.getCondition());
		}

		return expire(key, expiration.getExpirationTimeInSeconds(), options.getCondition());
	}

	/**
	 * Set time to live for given {@code key} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param seconds
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/expire">Valkey Documentation: EXPIRE</a>
	 */
	@Nullable
	default Boolean expire(byte[] key, long seconds) {
		return expire(key, seconds, ExpirationOptions.Condition.ALWAYS);
	}

	/**
	 * Set time to live for given {@code key} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param seconds
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/expire">Valkey Documentation: EXPIRE</a>
	 * @since 3.5
	 */
	@Nullable
	Boolean expire(byte[] key, long seconds, ExpirationOptions.Condition condition);

	/**
	 * Set time to live for given {@code key} using {@link Duration#toSeconds() seconds} precision.
	 *
	 * @param key must not be {@literal null}.
	 * @param duration
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/expire">Valkey Documentation: EXPIRE</a>
	 * @since 3.5
	 */
	@Nullable
	default Boolean expire(byte[] key, Duration duration) {
		return expire(key, duration.toSeconds());
	}

	/**
	 * Set time to live for given {@code key} in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param millis
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/pexpire">Valkey Documentation: PEXPIRE</a>
	 */
	@Nullable
	default Boolean pExpire(byte[] key, long millis) {
		return pExpire(key, millis, ExpirationOptions.Condition.ALWAYS);
	}

	/**
	 * Set time to live for given {@code key} in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param millis
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/pexpire">Valkey Documentation: PEXPIRE</a>
	 * @since 3.5
	 */
	@Nullable
	Boolean pExpire(byte[] key, long millis, ExpirationOptions.Condition condition);

	/**
	 * Set time to live for given {@code key} using {@link Duration#toMillis() milliseconds} precision.
	 *
	 * @param key must not be {@literal null}.
	 * @param duration
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/pexpire">Valkey Documentation: PEXPIRE</a>
	 * @since 3.5
	 */
	@Nullable
	default Boolean pExpire(byte[] key, Duration duration) {
		return pExpire(key, duration.toMillis());
	}

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTime
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/expireat">Valkey Documentation: EXPIREAT</a>
	 */
	@Nullable
	default Boolean expireAt(byte[] key, long unixTime) {
		return expireAt(key, unixTime, ExpirationOptions.Condition.ALWAYS);
	}

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTime
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/expireat">Valkey Documentation: EXPIREAT</a>
	 * @since 3.5
	 */
	@Nullable
	Boolean expireAt(byte[] key, long unixTime, ExpirationOptions.Condition condition);

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp in {@link Instant#getEpochSecond() seconds}
	 * precision.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTime
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/expireat">Valkey Documentation: EXPIREAT</a>
	 * @since 3.5
	 */
	@Nullable
	default Boolean expireAt(byte[] key, Instant unixTime) {
		return expireAt(key, unixTime.getEpochSecond());
	}

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTimeInMillis
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/pexpireat">Valkey Documentation: PEXPIREAT</a>
	 */
	@Nullable
	default Boolean pExpireAt(byte[] key, long unixTimeInMillis) {
		return pExpireAt(key, unixTimeInMillis, ExpirationOptions.Condition.ALWAYS);
	}

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTimeInMillis
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/pexpireat">Valkey Documentation: PEXPIREAT</a>
	 * @since 3.5
	 */
	@Nullable
	Boolean pExpireAt(byte[] key, long unixTimeInMillis, ExpirationOptions.Condition condition);

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp in {@link Instant#toEpochMilli()
	 * milliseconds} precision.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTime
	 * @return {@literal null} when used in pipeline / transaction. {@literal true} if the timeout was set or
	 *         {@literal false} if the timeout was not set; for example, the key doesn't exist, or the operation was
	 *         skipped because of the provided arguments.
	 * @see <a href="https://valkey.io/commands/pexpireat">Valkey Documentation: PEXPIREAT</a>
	 * @since 3.5
	 */
	@Nullable
	default Boolean pExpireAt(byte[] key, Instant unixTime) {
		return pExpireAt(key, unixTime.toEpochMilli());
	}

	/**
	 * Remove the expiration from given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/persist">Valkey Documentation: PERSIST</a>
	 */
	@Nullable
	Boolean persist(byte[] key);

	/**
	 * Move given {@code key} to database with {@code index}.
	 *
	 * @param key must not be {@literal null}.
	 * @param dbIndex
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/move">Valkey Documentation: MOVE</a>
	 */
	@Nullable
	Boolean move(byte[] key, int dbIndex);

	/**
	 * Get the time to live for {@code key} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/ttl">Valkey Documentation: TTL</a>
	 */
	@Nullable
	Long ttl(byte[] key);

	/**
	 * Get the time to live for {@code key} in and convert it to the given {@link TimeUnit}.
	 *
	 * @param key must not be {@literal null}.
	 * @param timeUnit must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/ttl">Valkey Documentation: TTL</a>
	 */
	@Nullable
	Long ttl(byte[] key, TimeUnit timeUnit);

	/**
	 * Get the precise time to live for {@code key} in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/pttl">Valkey Documentation: PTTL</a>
	 */
	@Nullable
	Long pTtl(byte[] key);

	/**
	 * Get the precise time to live for {@code key} in and convert it to the given {@link TimeUnit}.
	 *
	 * @param key must not be {@literal null}.
	 * @param timeUnit must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/pttl">Valkey Documentation: PTTL</a>
	 */
	@Nullable
	Long pTtl(byte[] key, TimeUnit timeUnit);

	/**
	 * Sort the elements for {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param params must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sort">Valkey Documentation: SORT</a>
	 */
	@Nullable
	List<byte[]> sort(byte[] key, SortParameters params);

	/**
	 * Sort the elements for {@code key} and store result in {@code storeKey}.
	 *
	 * @param key must not be {@literal null}.
	 * @param params must not be {@literal null}.
	 * @param storeKey must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sort">Valkey Documentation: SORT</a>
	 */
	@Nullable
	Long sort(byte[] key, SortParameters params, byte[] storeKey);

	/**
	 * Retrieve serialized version of the value stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/dump">Valkey Documentation: DUMP</a>
	 */
	@Nullable
	byte[] dump(byte[] key);

	/**
	 * Create {@code key} using the {@code serializedValue}, previously obtained using {@link #dump(byte[])}.
	 *
	 * @param key must not be {@literal null}.
	 * @param ttlInMillis
	 * @param serializedValue must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/restore">Valkey Documentation: RESTORE</a>
	 */
	default void restore(byte[] key, long ttlInMillis, byte[] serializedValue) {
		restore(key, ttlInMillis, serializedValue, false);
	}

	/**
	 * Create {@code key} using the {@code serializedValue}, previously obtained using {@link #dump(byte[])}.
	 *
	 * @param key must not be {@literal null}.
	 * @param ttlInMillis
	 * @param serializedValue must not be {@literal null}.
	 * @param replace use {@literal true} to replace a potentially existing value instead of erroring.
	 * @since 2.1
	 * @see <a href="https://valkey.io/commands/restore">Valkey Documentation: RESTORE</a>
	 */
	void restore(byte[] key, long ttlInMillis, byte[] serializedValue, boolean replace);

	/**
	 * Get the type of internal representation used for storing the value at the given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@link io.valkey.springframework.data.valkey.connection.ValueEncoding.ValkeyValueEncoding#VACANT} if key does not
	 *         exist or {@literal null} when used in pipeline / transaction.
	 * @throws IllegalArgumentException if {@code key} is {@literal null}.
	 * @see <a href="https://valkey.io/commands/object">Valkey Documentation: OBJECT ENCODING</a>
	 * @since 2.1
	 */
	@Nullable
	ValueEncoding encodingOf(byte[] key);

	/**
	 * Get the {@link Duration} since the object stored at the given {@code key} is idle.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @throws IllegalArgumentException if {@code key} is {@literal null}.
	 * @see <a href="https://valkey.io/commands/object">Valkey Documentation: OBJECT IDLETIME</a>
	 * @since 2.1
	 */
	@Nullable
	Duration idletime(byte[] key);

	/**
	 * Get the number of references of the value associated with the specified {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @throws IllegalArgumentException if {@code key} is {@literal null}.
	 * @see <a href="https://valkey.io/commands/object">Valkey Documentation: OBJECT REFCOUNT</a>
	 * @since 2.1
	 */
	@Nullable
	Long refcount(byte[] key);

}
