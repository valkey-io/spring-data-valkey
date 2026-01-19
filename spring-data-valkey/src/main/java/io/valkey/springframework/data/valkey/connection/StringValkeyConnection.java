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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metric;
import org.springframework.data.geo.Point;
import io.valkey.springframework.data.valkey.connection.stream.Consumer;
import io.valkey.springframework.data.valkey.connection.stream.PendingMessage;
import io.valkey.springframework.data.valkey.connection.stream.PendingMessages;
import io.valkey.springframework.data.valkey.connection.stream.PendingMessagesSummary;
import io.valkey.springframework.data.valkey.connection.stream.ReadOffset;
import io.valkey.springframework.data.valkey.connection.stream.RecordId;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoConsumers;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoGroups;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoStream;
import io.valkey.springframework.data.valkey.connection.stream.StreamOffset;
import io.valkey.springframework.data.valkey.connection.stream.StreamReadOptions;
import io.valkey.springframework.data.valkey.connection.stream.StreamRecords;
import io.valkey.springframework.data.valkey.connection.stream.StringRecord;
import io.valkey.springframework.data.valkey.connection.zset.Aggregate;
import io.valkey.springframework.data.valkey.connection.zset.Tuple;
import io.valkey.springframework.data.valkey.connection.zset.Weights;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ValkeyCallback;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;
import io.valkey.springframework.data.valkey.domain.geo.GeoReference;
import io.valkey.springframework.data.valkey.domain.geo.GeoShape;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

/**
 * Convenience extension of {@link ValkeyConnection} that accepts and returns {@link String}s instead of byte arrays.
 * Uses a {@link ValkeySerializer} underneath to perform the conversion.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @author David Liu
 * @author Mark Paluch
 * @author Ninad Divadkar
 * @author Tugdual Grall
 * @author Dengliming
 * @author Andrey Shlykov
 * @author ihaohong
 * @author Shyngys Sapraliyev
 * @see ValkeyCallback
 * @see ValkeySerializer
 * @see StringValkeyTemplate
 */
public interface StringValkeyConnection extends ValkeyConnection {

	/**
	 * String-friendly ZSet tuple.
	 */
	interface StringTuple extends Tuple {
		String getValueAsString();
	}

	/**
	 * 'Native' or 'raw' execution of the given command along-side the given arguments. The command is executed as is,
	 * with as little 'interpretation' as possible - it is up to the caller to take care of any processing of arguments or
	 * the result.
	 *
	 * @param command Command to execute
	 * @param args Possible command arguments (may be null)
	 * @return execution result.
	 * @see ValkeyCommands#execute(String, byte[]...)
	 */
	Object execute(String command, String... args);

	/**
	 * 'Native' or 'raw' execution of the given command along-side the given arguments. The command is executed as is,
	 * with as little 'interpretation' as possible - it is up to the caller to take care of any processing of arguments or
	 * the result.
	 *
	 * @param command Command to execute
	 * @return execution result.
	 * @see ValkeyCommands#execute(String, byte[]...)
	 */
	Object execute(String command);

	/**
	 * Determine if given {@code key} exists.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/exists">Valkey Documentation: EXISTS</a>
	 * @see ValkeyKeyCommands#exists(byte[])
	 */
	Boolean exists(String key);

	/**
	 * Count how many of the given {@code keys} exist.
	 *
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/exists">Valkey Documentation: EXISTS</a>
	 * @see ValkeyKeyCommands#exists(byte[][])
	 * @since 2.1
	 */
	@Nullable
	Long exists(String... keys);

	/**
	 * Delete given {@code keys}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return The number of keys that were removed.
	 * @see <a href="https://valkey.io/commands/del">Valkey Documentation: DEL</a>
	 * @see ValkeyKeyCommands#del(byte[]...)
	 */
	Long del(String... keys);

	/**
	 * Copy given {@code sourceKey} to {@code targetKey}.
	 *
	 * @param sourceKey must not be {@literal null}.
	 * @param targetKey must not be {@literal null}.
	 * @param replace whether to replace existing keys.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/copy">Valkey Documentation: COPY</a>
	 * @see ValkeyKeyCommands#copy(byte[], byte[], boolean)
	 */
	Boolean copy(String sourceKey, String targetKey, boolean replace);

	/**
	 * Unlink the {@code keys} from the keyspace. Unlike with {@link #del(String...)} the actual memory reclaiming here
	 * happens asynchronously.
	 *
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/unlink">Valkey Documentation: UNLINK</a>
	 * @since 2.1
	 */
	@Nullable
	Long unlink(String... keys);

	/**
	 * Determine the type stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/type">Valkey Documentation: TYPE</a>
	 * @see ValkeyKeyCommands#type(byte[])
	 */
	DataType type(String key);

	/**
	 * Alter the last access time of given {@code key(s)}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/touch">Valkey Documentation: TOUCH</a>
	 * @since 2.1
	 */
	@Nullable
	Long touch(String... keys);

	/**
	 * Find all keys matching the given {@code pattern}.
	 *
	 * @param pattern must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/keys">Valkey Documentation: KEYS</a>
	 * @see ValkeyKeyCommands#keys(byte[])
	 */
	Collection<String> keys(String pattern);

	/**
	 * Rename key {@code oldKey} to {@code newKey}.
	 *
	 * @param oldKey must not be {@literal null}.
	 * @param newKey must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/rename">Valkey Documentation: RENAME</a>
	 * @see ValkeyKeyCommands#rename(byte[], byte[])
	 */
	void rename(String oldKey, String newKey);

	/**
	 * Rename key {@code oldKey} to {@code newKey} only if {@code newKey} does not exist.
	 *
	 * @param oldKey must not be {@literal null}.
	 * @param newKey must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/renamenx">Valkey Documentation: RENAMENX</a>
	 * @see ValkeyKeyCommands#renameNX(byte[], byte[])
	 */
	Boolean renameNX(String oldKey, String newKey);

	/**
	 * Set time to live for given {@code key} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param seconds
	 * @return
	 * @see <a href="https://valkey.io/commands/expire">Valkey Documentation: EXPIRE</a>
	 * @see ValkeyKeyCommands#expire(byte[], long)
	 */
	default Boolean expire(String key, long seconds) {
		return expire(key, seconds, ExpirationOptions.Condition.ALWAYS);
	}

	/**
	 * Set time to live for given {@code key} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param condition the condition for expiration, must not be {@literal null}.
	 * @param seconds
	 * @return
	 * @since 3.5
	 * @see <a href="https://valkey.io/commands/expire">Valkey Documentation: EXPIRE</a>
	 * @see ValkeyKeyCommands#expire(byte[], long)
	 */
	Boolean expire(String key, long seconds, ExpirationOptions.Condition condition);

	/**
	 * Set time to live for given {@code key} in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param millis
	 * @return
	 * @see <a href="https://valkey.io/commands/pexpire">Valkey Documentation: PEXPIRE</a>
	 * @see ValkeyKeyCommands#pExpire(byte[], long)
	 */
	default Boolean pExpire(String key, long millis) {
		return pExpire(key, millis, ExpirationOptions.Condition.ALWAYS);
	}

	/**
	 * Set time to live for given {@code key} in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param millis
	 * @param condition the condition for expiration, must not be {@literal null}.
	 * @return
	 * @since 3.5
	 * @see <a href="https://valkey.io/commands/pexpire">Valkey Documentation: PEXPIRE</a>
	 * @see ValkeyKeyCommands#pExpire(byte[], long)
	 */
	Boolean pExpire(String key, long millis, ExpirationOptions.Condition condition);

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTime
	 * @return
	 * @see <a href="https://valkey.io/commands/expireat">Valkey Documentation: EXPIREAT</a>
	 * @see ValkeyKeyCommands#expireAt(byte[], long)
	 */
	default Boolean expireAt(String key, long unixTime) {
		return expireAt(key, unixTime, ExpirationOptions.Condition.ALWAYS);
	}

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTime
	 * @param condition the condition for expiration, must not be {@literal null}.
	 * @return
	 * @since 3.5
	 * @see <a href="https://valkey.io/commands/expireat">Valkey Documentation: EXPIREAT</a>
	 * @see ValkeyKeyCommands#expireAt(byte[], long)
	 */
	Boolean expireAt(String key, long unixTime, ExpirationOptions.Condition condition);

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTimeInMillis
	 * @return
	 * @see <a href="https://valkey.io/commands/pexpireat">Valkey Documentation: PEXPIREAT</a>
	 * @see ValkeyKeyCommands#pExpireAt(byte[], long)
	 */
	default Boolean pExpireAt(String key, long unixTimeInMillis) {
		return pExpireAt(key, unixTimeInMillis, ExpirationOptions.Condition.ALWAYS);
	}

	/**
	 * Set the expiration for given {@code key} as a {@literal UNIX} timestamp in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTimeInMillis
	 * @param condition the condition for expiration, must not be {@literal null}.
	 * @return
	 * @since 3.5
	 * @see <a href="https://valkey.io/commands/pexpireat">Valkey Documentation: PEXPIREAT</a>
	 * @see ValkeyKeyCommands#pExpireAt(byte[], long)
	 */
	Boolean pExpireAt(String key, long unixTimeInMillis, ExpirationOptions.Condition condition);

	/**
	 * Remove the expiration from given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/persist">Valkey Documentation: PERSIST</a>
	 * @see ValkeyKeyCommands#persist(byte[])
	 */
	Boolean persist(String key);

	/**
	 * Move given {@code key} to database with {@code index}.
	 *
	 * @param key must not be {@literal null}.
	 * @param dbIndex
	 * @return
	 * @see <a href="https://valkey.io/commands/move">Valkey Documentation: MOVE</a>
	 * @see ValkeyKeyCommands#move(byte[], int)
	 */
	Boolean move(String key, int dbIndex);

	/**
	 * Get the time to live for {@code key} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/ttl">Valkey Documentation: TTL</a>
	 * @see ValkeyKeyCommands#ttl(byte[])
	 */
	Long ttl(String key);

	/**
	 * Get the time to live for {@code key} in and convert it to the given {@link TimeUnit}.
	 *
	 * @param key must not be {@literal null}.
	 * @param timeUnit must not be {@literal null}.
	 * @return
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/ttl">Valkey Documentation: TTL</a>
	 * @see ValkeyKeyCommands#ttl(byte[], TimeUnit)
	 */
	Long ttl(String key, TimeUnit timeUnit);

	/**
	 * Get the precise time to live for {@code key} in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/pttl">Valkey Documentation: PTTL</a>
	 * @see ValkeyKeyCommands#pTtl(byte[])
	 */
	Long pTtl(String key);

	/**
	 * Get the precise time to live for {@code key} in and convert it to the given {@link TimeUnit}.
	 *
	 * @param key must not be {@literal null}.
	 * @param timeUnit must not be {@literal null}.
	 * @return
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/pttl">Valkey Documentation: PTTL</a>
	 * @see ValkeyKeyCommands#pTtl(byte[], TimeUnit)
	 */
	Long pTtl(String key, TimeUnit timeUnit);

	/**
	 * Returns {@code message} via server roundtrip.
	 *
	 * @param message the message to echo.
	 * @return
	 * @see <a href="https://valkey.io/commands/echo">Valkey Documentation: ECHO</a>
	 * @see ValkeyConnectionCommands#echo(byte[])
	 */
	String echo(String message);

	/**
	 * Sort the elements for {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param params must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/sort">Valkey Documentation: SORT</a>
	 */
	List<String> sort(String key, SortParameters params);

	/**
	 * Sort the elements for {@code key} and store result in {@code storeKey}.
	 *
	 * @param key must not be {@literal null}.
	 * @param params must not be {@literal null}.
	 * @param storeKey must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/sort">Valkey Documentation: SORT</a>
	 */
	Long sort(String key, SortParameters params, String storeKey);

	/**
	 * Get the type of internal representation used for storing the value at the given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @throws IllegalArgumentException if {@code key} is {@literal null}.
	 * @since 2.1
	 */
	@Nullable
	ValueEncoding encodingOf(String key);

	/**
	 * Get the {@link Duration} since the object stored at the given {@code key} is idle.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @throws IllegalArgumentException if {@code key} is {@literal null}.
	 * @since 2.1
	 */
	@Nullable
	Duration idletime(String key);

	/**
	 * Get the number of references of the value associated with the specified {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @throws IllegalArgumentException if {@code key} is {@literal null}.
	 * @since 2.1
	 */
	@Nullable
	Long refcount(String key);

	// -------------------------------------------------------------------------
	// Methods dealing with values/Valkey strings
	// -------------------------------------------------------------------------

	/**
	 * Get the value of {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/get">Valkey Documentation: GET</a>
	 * @see ValkeyStringCommands#get(byte[])
	 */
	String get(String key);

	/**
	 * Return the value at {@code key} and delete the key.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when key does not exist or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/getdel">Valkey Documentation: GETDEL</a>
	 * @since 2.6
	 */
	@Nullable
	String getDel(String key);

	/**
	 * Return the value at {@code key} and expire the key by applying {@link Expiration}.
	 *
	 * @param key must not be {@literal null}.
	 * @param expiration must not be {@literal null}.
	 * @return {@literal null} when key does not exist or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/getex">Valkey Documentation: GETEX</a>
	 * @since 2.6
	 */
	@Nullable
	String getEx(String key, Expiration expiration);

	/**
	 * Set {@code value} of {@code key} and return its old value.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/getset">Valkey Documentation: GETSET</a>
	 * @see ValkeyStringCommands#getSet(byte[], byte[])
	 */
	String getSet(String key, String value);

	/**
	 * Get multiple {@code keys}. Values are in the order of the requested keys.
	 *
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/mget">Valkey Documentation: MGET</a>
	 * @see ValkeyStringCommands#mGet(byte[]...)
	 */
	List<String> mGet(String... keys);

	/**
	 * Set {@code value} for {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/set">Valkey Documentation: SET</a>
	 * @see ValkeyStringCommands#set(byte[], byte[])
	 */
	@Nullable
	Boolean set(String key, String value);

	/**
	 * Set {@code value} for {@code key} applying timeouts from {@code expiration} if set and inserting/updating values
	 * depending on {@code option}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value must not be {@literal null}.
	 * @param expiration can be {@literal null}. Defaulted to {@link Expiration#persistent()}. Use
	 *          {@link Expiration#keepTtl()} to keep the existing expiration.
	 * @param option can be {@literal null}. Defaulted to {@link SetOption#UPSERT}.
	 * @since 1.7
	 * @see <a href="https://valkey.io/commands/set">Valkey Documentation: SET</a>
	 * @see ValkeyStringCommands#set(byte[], byte[], Expiration, SetOption)
	 */
	@Nullable
	Boolean set(String key, String value, Expiration expiration, SetOption option);

	/**
	 * Set {@code value} for {@code key}, only if {@code key} does not exist.
	 *
	 * @param key must not be {@literal null}.
	 * @param value must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/setnx">Valkey Documentation: SETNX</a>
	 * @see ValkeyStringCommands#setNX(byte[], byte[])
	 */
	@Nullable
	Boolean setNX(String key, String value);

	/**
	 * Set the {@code value} and expiration in {@code seconds} for {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param seconds
	 * @param value must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/setex">Valkey Documentation: SETEX</a>
	 * @see ValkeyStringCommands#setEx(byte[], long, byte[])
	 */
	@Nullable
	Boolean setEx(String key, long seconds, String value);

	/**
	 * Set the {@code value} and expiration in {@code milliseconds} for {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param milliseconds
	 * @param value must not be {@literal null}.
	 * @since 1.3
	 * @see <a href="https://valkey.io/commands/psetex">Valkey Documentation: PSETEX</a>
	 * @see ValkeyStringCommands#pSetEx(byte[], long, byte[])
	 */
	@Nullable
	Boolean pSetEx(String key, long milliseconds, String value);

	/**
	 * Set multiple keys to multiple values using key-value pairs provided in {@code tuple}.
	 *
	 * @param tuple must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/mset">Valkey Documentation: MSET</a>
	 * @see ValkeyStringCommands#mSet(Map)
	 */
	@Nullable
	Boolean mSetString(Map<String, String> tuple);

	/**
	 * Set multiple keys to multiple values using key-value pairs provided in {@code tuple} only if the provided key does
	 * not exist.
	 *
	 * @param tuple must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/msetnx">Valkey Documentation: MSETNX</a>
	 * @see ValkeyStringCommands#mSetNX(Map)
	 */
	Boolean mSetNXString(Map<String, String> tuple);

	/**
	 * Increment an integer value stored as string value of {@code key} by 1.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/incr">Valkey Documentation: INCR</a>
	 * @see ValkeyStringCommands#incr(byte[])
	 */
	Long incr(String key);

	/**
	 * Increment an integer value stored of {@code key} by {@code delta}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/incrby">Valkey Documentation: INCRBY</a>
	 * @see ValkeyStringCommands#incrBy(byte[], long)
	 */
	Long incrBy(String key, long value);

	/**
	 * Increment a floating point number value of {@code key} by {@code delta}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/incrbyfloat">Valkey Documentation: INCRBYFLOAT</a>
	 * @see ValkeyStringCommands#incrBy(byte[], double)
	 */
	Double incrBy(String key, double value);

	/**
	 * Decrement an integer value stored as string value of {@code key} by 1.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/decr">Valkey Documentation: DECR</a>
	 * @see ValkeyStringCommands#decr(byte[])
	 */
	Long decr(String key);

	/**
	 * Decrement an integer value stored as string value of {@code key} by {@code value}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/decrby">Valkey Documentation: DECRBY</a>
	 * @see ValkeyStringCommands#decrBy(byte[], long)
	 */
	Long decrBy(String key, long value);

	/**
	 * Append a {@code value} to {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/append">Valkey Documentation: APPEND</a>
	 * @see ValkeyStringCommands#append(byte[], byte[])
	 */
	Long append(String key, String value);

	/**
	 * Get a substring of value of {@code key} between {@code start} and {@code end}.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @return
	 * @see <a href="https://valkey.io/commands/getrange">Valkey Documentation: GETRANGE</a>
	 * @see ValkeyStringCommands#getRange(byte[], long, long)
	 */
	String getRange(String key, long start, long end);

	/**
	 * Overwrite parts of {@code key} starting at the specified {@code offset} with given {@code value}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @param offset
	 * @see <a href="https://valkey.io/commands/setrange">Valkey Documentation: SETRANGE</a>
	 * @see ValkeyStringCommands#setRange(byte[], byte[], long)
	 */
	void setRange(String key, String value, long offset);

	/**
	 * Get the bit value at {@code offset} of value at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param offset
	 * @return
	 * @see <a href="https://valkey.io/commands/getbit">Valkey Documentation: GETBIT</a>
	 * @see ValkeyStringCommands#getBit(byte[], long)
	 */
	Boolean getBit(String key, long offset);

	/**
	 * Sets the bit at {@code offset} in value stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param offset
	 * @param value
	 * @return the original bit value stored at {@code offset}.
	 * @see <a href="https://valkey.io/commands/setbit">Valkey Documentation: SETBIT</a>
	 * @see ValkeyStringCommands#setBit(byte[], long, boolean)
	 */
	Boolean setBit(String key, long offset, boolean value);

	/**
	 * Count the number of set bits (population counting) in value stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/bitcount">Valkey Documentation: BITCOUNT</a>
	 * @see ValkeyStringCommands#bitCount(byte[])
	 */
	Long bitCount(String key);

	/**
	 * Count the number of set bits (population counting) of value stored at {@code key} between {@code start} and
	 * {@code end}.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @return
	 * @see <a href="https://valkey.io/commands/bitcount">Valkey Documentation: BITCOUNT</a>
	 * @see ValkeyStringCommands#bitCount(byte[], long, long)
	 */
	Long bitCount(String key, long start, long end);

	/**
	 * Perform bitwise operations between strings.
	 *
	 * @param op must not be {@literal null}.
	 * @param destination must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/bitop">Valkey Documentation: BITOP</a>
	 * @see ValkeyStringCommands#bitOp(BitOperation, byte[], byte[]...)
	 */
	Long bitOp(BitOperation op, String destination, String... keys);

	/**
	 * Return the position of the first bit set to given {@code bit} in a string.
	 *
	 * @param key the key holding the actual String.
	 * @param bit the bit value to look for.
	 * @return {@literal null} when used in pipeline / transaction. The position of the first bit set to 1 or 0 according
	 *         to the request.
	 * @see <a href="https://valkey.io/commands/bitpos">Valkey Documentation: BITPOS</a>
	 * @since 2.1
	 */
	default Long bitPos(String key, boolean bit) {
		return bitPos(key, bit, org.springframework.data.domain.Range.unbounded());
	}

	/**
	 * Return the position of the first bit set to given {@code bit} in a string.
	 * {@link org.springframework.data.domain.Range} start and end can contain negative values in order to index
	 * <strong>bytes</strong> starting from the end of the string, where {@literal -1} is the last byte, {@literal -2} is
	 * the penultimate.
	 *
	 * @param key the key holding the actual String.
	 * @param bit the bit value to look for.
	 * @param range must not be {@literal null}. Use {@link Range#unbounded()} to not limit search.
	 * @return {@literal null} when used in pipeline / transaction. The position of the first bit set to 1 or 0 according
	 *         to the request.
	 * @see <a href="https://valkey.io/commands/bitpos">Valkey Documentation: BITPOS</a>
	 * @since 2.1
	 */
	@Nullable
	Long bitPos(String key, boolean bit, org.springframework.data.domain.Range<Long> range);

	/**
	 * Get the length of the value stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/strlen">Valkey Documentation: STRLEN</a>
	 * @see ValkeyStringCommands#strLen(byte[])
	 */
	Long strLen(String key);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Lists
	// -------------------------------------------------------------------------

	/**
	 * Append {@code values} to {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param values
	 * @return
	 * @see <a href="https://valkey.io/commands/rpush">Valkey Documentation: RPUSH</a>
	 * @see ValkeyListCommands#rPush(byte[], byte[]...)
	 */
	Long rPush(String key, String... values);

	/**
	 * Returns the index of matching elements inside the list stored at given {@literal key}. <br />
	 * Requires Valkey 6.0.6 or newer.
	 *
	 * @param key must not be {@literal null}.
	 * @param element must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lpos">Valkey Documentation: LPOS</a>
	 * @since 2.4
	 */
	@Nullable
	default Long lPos(String key, String element) {
		return CollectionUtils.firstElement(lPos(key, element, null, null));
	}

	/**
	 * Returns the index of matching elements inside the list stored at given {@literal key}. <br />
	 * Requires Valkey 6.0.6 or newer.
	 *
	 * @param key must not be {@literal null}.
	 * @param element must not be {@literal null}.
	 * @param rank specifies the "rank" of the first element to return, in case there are multiple matches. A rank of 1
	 *          means to return the first match, 2 to return the second match, and so forth.
	 * @param count number of matches to return.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lpos">Valkey Documentation: LPOS</a>
	 * @since 2.4
	 */
	List<Long> lPos(String key, String element, @Nullable Integer rank, @Nullable Integer count);

	/**
	 * Prepend {@code values} to {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param values
	 * @return
	 * @see <a href="https://valkey.io/commands/lpush">Valkey Documentation: LPUSH</a>
	 * @see ValkeyListCommands#lPush(byte[], byte[]...)
	 */
	Long lPush(String key, String... values);

	/**
	 * Append {@code values} to {@code key} only if the list exists.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/rpushx">Valkey Documentation: RPUSHX</a>
	 * @see ValkeyListCommands#rPushX(byte[], byte[])
	 */
	Long rPushX(String key, String value);

	/**
	 * Prepend {@code values} to {@code key} only if the list exists.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/lpushx">Valkey Documentation: LPUSHX</a>
	 * @see ValkeyListCommands#lPushX(byte[], byte[])
	 */
	Long lPushX(String key, String value);

	/**
	 * Get the size of list stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/llen">Valkey Documentation: LLEN</a>
	 * @see ValkeyListCommands#lLen(byte[])
	 */
	Long lLen(String key);

	/**
	 * Get elements between {@code start} and {@code end} from list at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @return
	 * @see <a href="https://valkey.io/commands/lrange">Valkey Documentation: LRANGE</a>
	 * @see ValkeyListCommands#lRange(byte[], long, long)
	 */
	List<String> lRange(String key, long start, long end);

	/**
	 * Trim list at {@code key} to elements between {@code start} and {@code end}.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @see <a href="https://valkey.io/commands/ltrim">Valkey Documentation: LTRIM</a>
	 * @see ValkeyListCommands#lTrim(byte[], long, long)
	 */
	void lTrim(String key, long start, long end);

	/**
	 * Get element at {@code index} form list at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param index
	 * @return
	 * @see <a href="https://valkey.io/commands/lindex">Valkey Documentation: LINDEX</a>
	 * @see ValkeyListCommands#lIndex(byte[], long)
	 */
	String lIndex(String key, long index);

	/**
	 * Insert {@code value} {@link Position#BEFORE} or {@link Position#AFTER} existing {@code pivot} for {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param where must not be {@literal null}.
	 * @param pivot
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/linsert">Valkey Documentation: LINSERT</a>
	 * @see ValkeyListCommands#lIndex(byte[], long)
	 */
	Long lInsert(String key, Position where, String pivot, String value);

	/**
	 * Atomically returns and removes the first/last element (head/tail depending on the {@code from} argument) of the
	 * list stored at {@code sourceKey}, and pushes the element at the first/last element (head/tail depending on the
	 * {@code to} argument) of the list stored at {@code destinationKey}.
	 *
	 * @param sourceKey must not be {@literal null}.
	 * @param destinationKey must not be {@literal null}.
	 * @param from must not be {@literal null}.
	 * @param to must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/lmove">Valkey Documentation: LMOVE</a>
	 * @see #bLMove(byte[], byte[], Direction, Direction, double)
	 * @see #lMove(byte[], byte[], Direction, Direction)
	 */
	@Nullable
	String lMove(String sourceKey, String destinationKey, Direction from, Direction to);

	/**
	 * Atomically returns and removes the first/last element (head/tail depending on the {@code from} argument) of the
	 * list stored at {@code sourceKey}, and pushes the element at the first/last element (head/tail depending on the
	 * {@code to} argument) of the list stored at {@code destinationKey}.
	 *
	 * @param sourceKey must not be {@literal null}.
	 * @param destinationKey must not be {@literal null}.
	 * @param from must not be {@literal null}.
	 * @param to must not be {@literal null}.
	 * @param timeout
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/blmove">Valkey Documentation: BLMOVE</a>
	 * @see #lMove(byte[], byte[], Direction, Direction)
	 * @see #bLMove(byte[], byte[], Direction, Direction, double)
	 */
	@Nullable
	String bLMove(String sourceKey, String destinationKey, Direction from, Direction to, double timeout);

	/**
	 * Set the {@code value} list element at {@code index}.
	 *
	 * @param key must not be {@literal null}.
	 * @param index
	 * @param value
	 * @see <a href="https://valkey.io/commands/lset">Valkey Documentation: LSET</a>
	 * @see ValkeyListCommands#lSet(byte[], long, byte[])
	 */
	void lSet(String key, long index, String value);

	/**
	 * Removes the first {@code count} occurrences of {@code value} from the list stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/lrem">Valkey Documentation: LREM</a>
	 * @see ValkeyListCommands#lRem(byte[], long, byte[])
	 */
	Long lRem(String key, long count, String value);

	/**
	 * Removes and returns first element in list stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/lpop">Valkey Documentation: LPOP</a>
	 * @see ValkeyListCommands#lPop(byte[])
	 */
	String lPop(String key);

	/**
	 * Removes and returns first {@code} elements in list stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count
	 * @return
	 * @see <a href="https://valkey.io/commands/lpop">Valkey Documentation: LPOP</a>
	 * @see ValkeyListCommands#lPop(byte[], long)
	 * @since 2.6
	 */
	List<String> lPop(String key, long count);

	/**
	 * Removes and returns last element in list stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/rpop">Valkey Documentation: RPOP</a>
	 * @see ValkeyListCommands#rPop(byte[])
	 */
	String rPop(String key);

	/**
	 * Removes and returns last {@code} elements in list stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count
	 * @return
	 * @see <a href="https://valkey.io/commands/rpop">Valkey Documentation: RPOP</a>
	 * @see ValkeyListCommands#rPop(byte[], long)
	 * @since 2.6
	 */
	List<String> rPop(String key, long count);

	/**
	 * Removes and returns first element from lists stored at {@code keys} (see: {@link #lPop(byte[])}). <br>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param timeout
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/blpop">Valkey Documentation: BLPOP</a>
	 * @see ValkeyListCommands#bLPop(int, byte[]...)
	 */
	List<String> bLPop(int timeout, String... keys);

	/**
	 * Removes and returns last element from lists stored at {@code keys} (see: {@link #rPop(byte[])}). <br>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param timeout
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/brpop">Valkey Documentation: BRPOP</a>
	 * @see ValkeyListCommands#bRPop(int, byte[]...)
	 */
	List<String> bRPop(int timeout, String... keys);

	/**
	 * Remove the last element from list at {@code srcKey}, append it to {@code dstKey} and return its value.
	 *
	 * @param srcKey must not be {@literal null}.
	 * @param dstKey must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/rpoplpush">Valkey Documentation: RPOPLPUSH</a>
	 * @see ValkeyListCommands#rPopLPush(byte[], byte[])
	 */
	String rPopLPush(String srcKey, String dstKey);

	/**
	 * Remove the last element from list at {@code srcKey}, append it to {@code dstKey} and return its value (see
	 * {@link #rPopLPush(byte[], byte[])}). <br>
	 * <b>Blocks connection</b> until element available or {@code timeout} reached.
	 *
	 * @param timeout
	 * @param srcKey must not be {@literal null}.
	 * @param dstKey must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/brpoplpush">Valkey Documentation: BRPOPLPUSH</a>
	 * @see ValkeyListCommands#bRPopLPush(int, byte[], byte[])
	 */
	String bRPopLPush(int timeout, String srcKey, String dstKey);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Sets
	// -------------------------------------------------------------------------

	/**
	 * Add given {@code values} to set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param values
	 * @return
	 * @see <a href="https://valkey.io/commands/sadd">Valkey Documentation: SADD</a>
	 * @see ValkeySetCommands#sAdd(byte[], byte[]...)
	 */
	Long sAdd(String key, String... values);

	/**
	 * Remove given {@code values} from set at {@code key} and return the number of removed elements.
	 *
	 * @param key must not be {@literal null}.
	 * @param values
	 * @return
	 * @see <a href="https://valkey.io/commands/srem">Valkey Documentation: SREM</a>
	 * @see ValkeySetCommands#sRem(byte[], byte[]...)
	 */
	Long sRem(String key, String... values);

	/**
	 * Remove and return a random member from set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/spop">Valkey Documentation: SPOP</a>
	 * @see ValkeySetCommands#sPop(byte[])
	 */
	String sPop(String key);

	/**
	 * Remove and return {@code count} random members from set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count the number of random members to return.
	 * @return empty {@link List} if {@literal key} does not exist.
	 * @see <a href="https://valkey.io/commands/spop">Valkey Documentation: SPOP</a>
	 * @see ValkeySetCommands#sPop(byte[], long)
	 * @since 2.0
	 */
	List<String> sPop(String key, long count);

	/**
	 * Move {@code value} from {@code srcKey} to {@code destKey}
	 *
	 * @param srcKey must not be {@literal null}.
	 * @param destKey must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/smove">Valkey Documentation: SMOVE</a>
	 * @see ValkeySetCommands#sMove(byte[], byte[], byte[])
	 */
	Boolean sMove(String srcKey, String destKey, String value);

	/**
	 * Get size of set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/scard">Valkey Documentation: SCARD</a>
	 * @see ValkeySetCommands#sCard(byte[])
	 */
	Long sCard(String key);

	/**
	 * Check if set at {@code key} contains {@code value}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/sismember">Valkey Documentation: SISMEMBER</a>
	 * @see ValkeySetCommands#sIsMember(byte[], byte[])
	 */
	Boolean sIsMember(String key, String value);

	/**
	 * Check if set at {@code key} contains one or more {@code values}.
	 *
	 * @param key must not be {@literal null}.
	 * @param values must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/smismember">Valkey Documentation: SMISMEMBER</a>
	 * @see ValkeySetCommands#sMIsMember(byte[], byte[]...)
	 */
	@Nullable
	List<Boolean> sMIsMember(String key, String... values);

	/**
	 * Returns the members intersecting all given sets at {@code keys}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/sinter">Valkey Documentation: SINTER</a>
	 * @see ValkeySetCommands#sInter(byte[]...)
	 */
	Set<String> sInter(String... keys);

	/**
	 * Intersect all given sets at {@code keys} and store result in {@code destKey}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/sinterstore">Valkey Documentation: SINTERSTORE</a>
	 * @see ValkeySetCommands#sInterStore(byte[], byte[]...)
	 */
	Long sInterStore(String destKey, String... keys);

	/**
	 * Union all sets at given {@code keys}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/sunion">Valkey Documentation: SUNION</a>
	 * @see ValkeySetCommands#sUnion(byte[]...)
	 */
	Set<String> sUnion(String... keys);

	/**
	 * Union all sets at given {@code keys} and store result in {@code destKey}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/sunionstore">Valkey Documentation: SUNIONSTORE</a>
	 * @see ValkeySetCommands#sUnionStore(byte[], byte[]...)
	 */
	Long sUnionStore(String destKey, String... keys);

	/**
	 * Diff all sets for given {@code keys}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/sdiff">Valkey Documentation: SDIFF</a>
	 * @see ValkeySetCommands#sDiff(byte[]...)
	 */
	Set<String> sDiff(String... keys);

	/**
	 * Diff all sets for given {@code keys} and store result in {@code destKey}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/sdiffstore">Valkey Documentation: SDIFFSTORE</a>
	 * @see ValkeySetCommands#sDiffStore(byte[], byte[]...)
	 */
	Long sDiffStore(String destKey, String... keys);

	/**
	 * Get all elements of set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/smembers">Valkey Documentation: SMEMBERS</a>
	 * @see ValkeySetCommands#sMembers(byte[])
	 */
	Set<String> sMembers(String key);

	/**
	 * Get random element from set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/srandmember">Valkey Documentation: SRANDMEMBER</a>
	 * @see ValkeySetCommands#sRandMember(byte[])
	 */
	String sRandMember(String key);

	/**
	 * Get {@code count} random elements from set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count
	 * @return
	 * @see <a href="https://valkey.io/commands/srandmember">Valkey Documentation: SRANDMEMBER</a>
	 * @see ValkeySetCommands#sRem(byte[], byte[]...)
	 */
	List<String> sRandMember(String key, long count);

	/**
	 * Use a {@link Cursor} to iterate over elements in set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @return
	 * @since 1.4
	 * @see <a href="https://valkey.io/commands/scan">Valkey Documentation: SCAN</a>
	 * @see ValkeySetCommands#sScan(byte[], ScanOptions)
	 */
	Cursor<String> sScan(String key, ScanOptions options);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Sorted Sets
	// -------------------------------------------------------------------------

	/**
	 * Add {@code value} to a sorted set at {@code key}, or update its {@code score} if it already exists.
	 *
	 * @param key must not be {@literal null}.
	 * @param score the score.
	 * @param value the value.
	 * @return
	 * @see <a href="https://valkey.io/commands/zadd">Valkey Documentation: ZADD</a>
	 * @see ValkeyZSetCommands#zAdd(byte[], double, byte[])
	 */
	Boolean zAdd(String key, double score, String value);

	/**
	 * Add the {@code value} to a sorted set at {@code key}, or update its {@code score} depending on the given
	 * {@link ZAddArgs args}.
	 *
	 * @param key must not be {@literal null}.
	 * @param score must not be {@literal null}.
	 * @param value must not be {@literal null}.
	 * @param args must not be {@literal null} use {@link ZAddArgs#empty()} instead.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.5
	 * @see <a href="https://valkey.io/commands/zadd">Valkey Documentation: ZADD</a>
	 * @see ValkeyZSetCommands#zAdd(byte[], double, byte[], ZAddArgs)
	 */
	Boolean zAdd(String key, double score, String value, ZAddArgs args);

	/**
	 * Add {@code tuples} to a sorted set at {@code key}, or update its {@code score} if it already exists.
	 *
	 * @param key must not be {@literal null}.
	 * @param tuples the tuples.
	 * @return
	 * @see <a href="https://valkey.io/commands/zadd">Valkey Documentation: ZADD</a>
	 * @see ValkeyZSetCommands#zAdd(byte[], Set)
	 */
	Long zAdd(String key, Set<StringTuple> tuples);

	/**
	 * Add {@code tuples} to a sorted set at {@code key}, or update its {@code score} depending on the given
	 * {@link ZAddArgs args}.
	 *
	 * @param key must not be {@literal null}.
	 * @param tuples must not be {@literal null}.
	 * @param args must not be {@literal null} use {@link ZAddArgs#empty()} instead.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.5
	 * @see <a href="https://valkey.io/commands/zadd">Valkey Documentation: ZADD</a>
	 * @see ValkeyZSetCommands#zAdd(byte[], Set, ZAddArgs)
	 */
	@Nullable
	Long zAdd(String key, Set<StringTuple> tuples, ZAddArgs args);

	/**
	 * Remove {@code values} from sorted set. Return number of removed elements.
	 *
	 * @param key must not be {@literal null}.
	 * @param values must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/zrem">Valkey Documentation: ZREM</a>
	 * @see ValkeyZSetCommands#zRem(byte[], byte[]...)
	 */
	Long zRem(String key, String... values);

	/**
	 * Increment the score of element with {@code value} in sorted set by {@code increment}.
	 *
	 * @param key must not be {@literal null}.
	 * @param increment
	 * @param value the value.
	 * @return
	 * @see <a href="https://valkey.io/commands/zincrby">Valkey Documentation: ZINCRBY</a>
	 * @see ValkeyZSetCommands#zIncrBy(byte[], double, byte[])
	 */
	Double zIncrBy(String key, double increment, String value);

	/**
	 * Get random element from sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zrandmember">Valkey Documentation: ZRANDMEMBER</a>
	 */
	@Nullable
	String zRandMember(String key);

	/**
	 * Get {@code count} random elements from sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count if the provided {@code count} argument is positive, return a list of distinct fields, capped either at
	 *          {@code count} or the set size. If {@code count} is negative, the behavior changes and the command is
	 *          allowed to return the same value multiple times. In this case, the number of returned values is the
	 *          absolute value of the specified count.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zrandmember">Valkey Documentation: ZRANDMEMBER</a>
	 */
	@Nullable
	List<String> zRandMember(String key, long count);

	/**
	 * Get random element from sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zrandmember">Valkey Documentation: ZRANDMEMBER</a>
	 */
	@Nullable
	StringTuple zRandMemberWithScore(String key);

	/**
	 * Get {@code count} random elements from sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count if the provided {@code count} argument is positive, return a list of distinct fields, capped either at
	 *          {@code count} or the set size. If {@code count} is negative, the behavior changes and the command is
	 *          allowed to return the same value multiple times. In this case, the number of returned values is the
	 *          absolute value of the specified count.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zrandmember">Valkey Documentation: ZRANDMEMBER</a>
	 */
	@Nullable
	List<StringTuple> zRandMemberWithScores(String key, long count);

	/**
	 * Determine the index of element with {@code value} in a sorted set.
	 *
	 * @param key must not be {@literal null}.
	 * @param value the value.
	 * @return
	 * @see <a href="https://valkey.io/commands/zrank">Valkey Documentation: ZRANK</a>
	 * @see ValkeyZSetCommands#zRank(byte[], byte[])
	 */
	Long zRank(String key, String value);

	/**
	 * Determine the index of element with {@code value} in a sorted set when scored high to low.
	 *
	 * @param key must not be {@literal null}.
	 * @param value the value.
	 * @return
	 * @see <a href="https://valkey.io/commands/zrevrank">Valkey Documentation: ZREVRANK</a>
	 * @see ValkeyZSetCommands#zRevRank(byte[], byte[])
	 */
	Long zRevRank(String key, String value);

	/**
	 * Get elements between {@code start} and {@code end} from sorted set.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @return
	 * @see <a href="https://valkey.io/commands/zrange">Valkey Documentation: ZRANGE</a>
	 * @see ValkeyZSetCommands#zRange(byte[], long, long)
	 */
	Set<String> zRange(String key, long start, long end);

	/**
	 * Get set of {@link Tuple}s between {@code start} and {@code end} from sorted set.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @return
	 * @see <a href="https://valkey.io/commands/zrange">Valkey Documentation: ZRANGE</a>
	 * @see ValkeyZSetCommands#zRangeWithScores(byte[], long, long)
	 */
	Set<StringTuple> zRangeWithScores(String key, long start, long end);

	/**
	 * Get elements where score is between {@code min} and {@code max} from sorted set.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @return
	 * @see <a href="https://valkey.io/commands/zrangebyscore">Valkey Documentation: ZRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRangeByScore(byte[], double, double)
	 */
	Set<String> zRangeByScore(String key, double min, double max);

	/**
	 * Get set of {@link Tuple}s where score is between {@code min} and {@code max} from sorted set.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @return
	 * @see <a href="https://valkey.io/commands/zrangebyscore">Valkey Documentation: ZRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRangeByScoreWithScores(byte[], double, double)
	 */
	Set<StringTuple> zRangeByScoreWithScores(String key, double min, double max);

	/**
	 * Get elements in range from {@code start} to {@code end} where score is between {@code min} and {@code max} from
	 * sorted set.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @param offset
	 * @param count
	 * @return
	 * @see <a href="https://valkey.io/commands/zrangebyscore">Valkey Documentation: ZRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRangeByScore(byte[], double, double, long, long)
	 */
	Set<String> zRangeByScore(String key, double min, double max, long offset, long count);

	/**
	 * Get set of {@link Tuple}s in range from {@code start} to {@code end} where score is between {@code min} and
	 * {@code max} from sorted set.
	 *
	 * @param key
	 * @param min
	 * @param max
	 * @param offset
	 * @param count
	 * @return
	 * @see <a href="https://valkey.io/commands/zrangebyscore">Valkey Documentation: ZRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRangeByScoreWithScores(byte[], double, double, long, long)
	 */
	Set<StringTuple> zRangeByScoreWithScores(String key, double min, double max, long offset, long count);

	/**
	 * Get elements in range from {@code start} to {@code end} from sorted set ordered from high to low.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @return
	 * @see <a href="https://valkey.io/commands/zrevrange">Valkey Documentation: ZREVRANGE</a>
	 * @see ValkeyZSetCommands#zRevRange(byte[], long, long)
	 */
	Set<String> zRevRange(String key, long start, long end);

	/**
	 * Get set of {@link Tuple}s in range from {@code start} to {@code end} from sorted set ordered from high to low.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @return
	 * @see <a href="https://valkey.io/commands/zrevrange">Valkey Documentation: ZREVRANGE</a>
	 * @see ValkeyZSetCommands#zRevRangeWithScores(byte[], long, long)
	 */
	Set<StringTuple> zRevRangeWithScores(String key, long start, long end);

	/**
	 * Get elements where score is between {@code min} and {@code max} from sorted set ordered from high to low.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @return
	 * @see <a href="https://valkey.io/commands/zrevrange">Valkey Documentation: ZREVRANGE</a>
	 * @see ValkeyZSetCommands#zRevRangeByScore(byte[], double, double)
	 */
	Set<String> zRevRangeByScore(String key, double min, double max);

	/**
	 * Get set of {@link Tuple} where score is between {@code min} and {@code max} from sorted set ordered from high to
	 * low.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @return
	 * @see <a href="https://valkey.io/commands/zrevrangebyscore">Valkey Documentation: ZREVRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRevRangeByScoreWithScores(byte[], double, double)
	 */
	Set<StringTuple> zRevRangeByScoreWithScores(String key, double min, double max);

	/**
	 * Get elements in range from {@code start} to {@code end} where score is between {@code min} and {@code max} from
	 * sorted set ordered high -> low.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @param offset
	 * @param count
	 * @return
	 * @see <a href="https://valkey.io/commands/zrevrangebyscore">Valkey Documentation: ZREVRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRevRangeByScore(byte[], double, double, long, long)
	 */
	Set<String> zRevRangeByScore(String key, double min, double max, long offset, long count);

	/**
	 * Get set of {@link Tuple} in range from {@code start} to {@code end} where score is between {@code min} and
	 * {@code max} from sorted set ordered high -> low.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @param offset
	 * @param count
	 * @return
	 * @see <a href="https://valkey.io/commands/zrevrangebyscore">Valkey Documentation: ZREVRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRevRangeByScoreWithScores(byte[], double, double, long, long)
	 */
	Set<StringTuple> zRevRangeByScoreWithScores(String key, double min, double max, long offset, long count);

	/**
	 * Count number of elements within sorted set with scores between {@code min} and {@code max}.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @return
	 * @see <a href="https://valkey.io/commands/zcount">Valkey Documentation: ZCOUNT</a>
	 * @see ValkeyZSetCommands#zCount(byte[], double, double)
	 */
	Long zCount(String key, double min, double max);

	/**
	 * Count number of elements within sorted set with value between {@code Range#min} and {@code Range#max} applying
	 * lexicographical ordering.
	 *
	 * @param key must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.4
	 * @see <a href="https://valkey.io/commands/zlexcount">Valkey Documentation: ZLEXCOUNT</a>
	 * @see ValkeyZSetCommands#zLexCount(byte[], org.springframework.data.domain.Range)
	 */
	@Nullable
	Long zLexCount(String key, org.springframework.data.domain.Range<String> range);

	/**
	 * Remove and return the value with its score having the lowest score from sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when the sorted set is empty or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/zpopmin">Valkey Documentation: ZPOPMIN</a>
	 * @since 2.6
	 */
	@Nullable
	Tuple zPopMin(String key);

	/**
	 * Remove and return {@code count} values with their score having the lowest score from sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count number of elements to pop.
	 * @return {@literal null} when the sorted set is empty or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/zpopmin">Valkey Documentation: ZPOPMIN</a>
	 * @since 2.6
	 */
	@Nullable
	Set<StringTuple> zPopMin(String key, long count);

	/**
	 * Remove and return the value with its score having the lowest score from sorted set at {@code key}. <b>Blocks
	 * connection</b> until element available or {@code timeout} reached.
	 *
	 * @param key must not be {@literal null}.
	 * @param timeout
	 * @param unit must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @see <a href="https://valkey.io/commands/bzpopmin">Valkey Documentation: BZPOPMIN</a>
	 * @since 2.6
	 */
	@Nullable
	StringTuple bZPopMin(String key, long timeout, TimeUnit unit);

	/**
	 * Remove and return the value with its score having the highest score from sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when the sorted set is empty or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/zpopmax">Valkey Documentation: ZPOPMAX</a>
	 * @since 2.6
	 */
	@Nullable
	StringTuple zPopMax(String key);

	/**
	 * Remove and return {@code count} values with their score having the highest score from sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count number of elements to pop.
	 * @return {@literal null} when the sorted set is empty or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/zpopmax">Valkey Documentation: ZPOPMAX</a>
	 * @since 2.6
	 */
	@Nullable
	Set<StringTuple> zPopMax(String key, long count);

	/**
	 * Remove and return the value with its score having the highest score from sorted set at {@code key}. <b>Blocks
	 * connection</b> until element available or {@code timeout} reached.
	 *
	 * @param key must not be {@literal null}.
	 * @param timeout
	 * @param unit must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @see <a href="https://valkey.io/commands/bzpopmax">Valkey Documentation: BZPOPMAX</a>
	 * @since 2.6
	 */
	@Nullable
	StringTuple bZPopMax(String key, long timeout, TimeUnit unit);

	/**
	 * Get the size of sorted set with {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/zcard">Valkey Documentation: ZCARD</a>
	 * @see ValkeyZSetCommands#zCard(byte[])
	 */
	Long zCard(String key);

	/**
	 * Get the score of element with {@code value} from sorted set with key {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value the value.
	 * @return
	 * @see <a href="https://valkey.io/commands/zscore">Valkey Documentation: ZSCORE</a>
	 * @see ValkeyZSetCommands#zScore(byte[], byte[])
	 */
	Double zScore(String key, String value);

	/**
	 * Get the scores of elements with {@code values} from sorted set with key {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param values the values.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/zmscore">Valkey Documentation: ZMSCORE</a>
	 * @see ValkeyZSetCommands#zMScore(byte[], byte[][])
	 * @since 2.6
	 */
	List<Double> zMScore(String key, String... values);

	/**
	 * Remove elements in range between {@code start} and {@code end} from sorted set with {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param start
	 * @param end
	 * @return
	 * @see <a href="https://valkey.io/commands/zremrangebyrank">Valkey Documentation: ZREMRANGEBYRANK</a>
	 * @see ValkeyZSetCommands#zRemRange(byte[], long, long)
	 */
	Long zRemRange(String key, long start, long end);

	/**
	 * Remove all elements between the lexicographical {@link Range}.
	 *
	 * @param key must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @return the number of elements removed, or {@literal null} when used in pipeline / transaction.
	 * @since 2.5
	 * @see <a href="https://valkey.io/commands/zremrangebylex">Valkey Documentation: ZREMRANGEBYLEX</a>
	 */
	Long zRemRangeByLex(String key, org.springframework.data.domain.Range<String> range);

	/**
	 * Remove elements with scores between {@code min} and {@code max} from sorted set with {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param min
	 * @param max
	 * @return
	 * @see <a href="https://valkey.io/commands/zremrangebyscore">Valkey Documentation: ZREMRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRemRangeByScore(byte[], double, double)
	 */
	Long zRemRangeByScore(String key, double min, double max);

	/**
	 * Diff sorted {@code sets}.
	 *
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zdiff">Valkey Documentation: ZDIFF</a>
	 */
	@Nullable
	Set<String> zDiff(String... sets);

	/**
	 * Diff sorted {@code sets}.
	 *
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zdiff">Valkey Documentation: ZDIFF</a>
	 */
	@Nullable
	Set<StringTuple> zDiffWithScores(String... sets);

	/**
	 * Diff sorted {@code sets} and store result in destination {@code destKey}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zdiffstore">Valkey Documentation: ZDIFFSTORE</a>
	 */
	@Nullable
	Long zDiffStore(String destKey, String... sets);

	/**
	 * Intersect sorted {@code sets}.
	 *
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zinter">Valkey Documentation: ZINTER</a>
	 */
	@Nullable
	Set<String> zInter(String... sets);

	/**
	 * Intersect sorted {@code sets}.
	 *
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zinter">Valkey Documentation: ZINTER</a>
	 */
	@Nullable
	Set<StringTuple> zInterWithScores(String... sets);

	/**
	 * Intersect sorted {@code sets}.
	 *
	 * @param aggregate must not be {@literal null}.
	 * @param weights must not be {@literal null}.
	 * @param sets must not be {@literal null}.
	 * @return
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zinter">Valkey Documentation: ZINTER</a>
	 */
	@Nullable
	default Set<StringTuple> zInterWithScores(Aggregate aggregate, int[] weights, String... sets) {
		return zInterWithScores(aggregate, Weights.of(weights), sets);
	}

	/**
	 * Intersect sorted {@code sets}.
	 *
	 * @param aggregate must not be {@literal null}.
	 * @param weights must not be {@literal null}.
	 * @param sets must not be {@literal null}.
	 * @return
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zinter">Valkey Documentation: ZINTER</a>
	 */
	@Nullable
	Set<StringTuple> zInterWithScores(Aggregate aggregate, Weights weights, String... sets);

	/**
	 * Intersect sorted {@code sets} and store result in destination {@code key}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param sets must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/zinterstore">Valkey Documentation: ZINTERSTORE</a>
	 * @see ValkeyZSetCommands#zInterStore(byte[], byte[]...)
	 */
	Long zInterStore(String destKey, String... sets);

	/**
	 * Intersect sorted {@code sets} and store result in destination {@code key}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param aggregate must not be {@literal null}.
	 * @param weights
	 * @param sets must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/zinterstore">Valkey Documentation: ZINTERSTORE</a>
	 * @see ValkeyZSetCommands#zInterStore(byte[], Aggregate, int[], byte[]...)
	 */
	Long zInterStore(String destKey, Aggregate aggregate, int[] weights, String... sets);

	/**
	 * Union sorted {@code sets}.
	 *
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zunion">Valkey Documentation: ZUNION</a>
	 */
	@Nullable
	Set<String> zUnion(String... sets);

	/**
	 * Union sorted {@code sets}.
	 *
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zunion">Valkey Documentation: ZUNION</a>
	 */
	@Nullable
	Set<StringTuple> zUnionWithScores(String... sets);

	/**
	 * Union sorted {@code sets}.
	 *
	 * @param aggregate must not be {@literal null}.
	 * @param weights must not be {@literal null}.
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zunion">Valkey Documentation: ZUNION</a>
	 */
	@Nullable
	default Set<StringTuple> zUnionWithScores(Aggregate aggregate, int[] weights, String... sets) {
		return zUnionWithScores(aggregate, Weights.of(weights), sets);
	}

	/**
	 * Union sorted {@code sets}.
	 *
	 * @param aggregate must not be {@literal null}.
	 * @param weights must not be {@literal null}.
	 * @param sets must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/zunion">Valkey Documentation: ZUNION</a>
	 */
	@Nullable
	Set<StringTuple> zUnionWithScores(Aggregate aggregate, Weights weights, String... sets);

	/**
	 * Union sorted {@code sets} and store result in destination {@code key}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param sets must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/zunionstore">Valkey Documentation: ZUNIONSTORE</a>
	 * @see ValkeyZSetCommands#zUnionStore(byte[], byte[]...)
	 */
	Long zUnionStore(String destKey, String... sets);

	/**
	 * Union sorted {@code sets} and store result in destination {@code key}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param aggregate must not be {@literal null}.
	 * @param weights
	 * @param sets must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/zunionstore">Valkey Documentation: ZUNIONSTORE</a>
	 * @see ValkeyZSetCommands#zUnionStore(byte[], Aggregate, int[], byte[]...)
	 */
	Long zUnionStore(String destKey, Aggregate aggregate, int[] weights, String... sets);

	/**
	 * Use a {@link Cursor} to iterate over elements in sorted set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @return
	 * @since 1.4
	 * @see <a href="https://valkey.io/commands/zscan">Valkey Documentation: ZSCAN</a>
	 * @see ValkeyZSetCommands#zScan(byte[], ScanOptions)
	 */
	Cursor<StringTuple> zScan(String key, ScanOptions options);

	/**
	 * Get elements where score is between {@code min} and {@code max} from sorted set.
	 *
	 * @param key must not be {@literal null}.
	 * @param min must not be {@literal null}.
	 * @param max must not be {@literal null}.
	 * @return
	 * @since 1.5
	 * @see <a href="https://valkey.io/commands/zrangebyscore">Valkey Documentation: ZRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRangeByScore(byte[], String, String)
	 */
	Set<String> zRangeByScore(String key, String min, String max);

	/**
	 * Get elements in range from {@code start} to {@code end} where score is between {@code min} and {@code max} from
	 * sorted set.
	 *
	 * @param key must not be {@literal null}.
	 * @param min must not be {@literal null}.
	 * @param max must not be {@literal null}.
	 * @param offset
	 * @param count
	 * @return
	 * @since 1.5
	 * @see <a href="https://valkey.io/commands/zrangebyscore">Valkey Documentation: ZRANGEBYSCORE</a>
	 * @see ValkeyZSetCommands#zRangeByScore(byte[], double, double, long, long)
	 */
	Set<String> zRangeByScore(String key, String min, String max, long offset, long count);

	/**
	 * Get all the elements in the sorted set at {@literal key} in lexicographical ordering.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @since 1.6
	 * @see <a href="https://valkey.io/commands/zrangebylex">Valkey Documentation: ZRANGEBYLEX</a>
	 * @see ValkeyZSetCommands#zRangeByLex(byte[])
	 */
	Set<String> zRangeByLex(String key);

	/**
	 * Get all the elements in {@link Range} from the sorted set at {@literal key} in lexicographical ordering.
	 *
	 * @param key must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @return
	 * @since 1.6
	 * @see <a href="https://valkey.io/commands/zrangebylex">Valkey Documentation: ZRANGEBYLEX</a>
	 * @see ValkeyZSetCommands#zRangeByLex(byte[], org.springframework.data.domain.Range)
	 */
	Set<String> zRangeByLex(String key, org.springframework.data.domain.Range<String> range);

	/**
	 * Get all the elements in {@link Range} from the sorted set at {@literal key} in lexicographical ordering. Result is
	 * limited via {@link io.valkey.springframework.data.valkey.connection.Limit}.
	 *
	 * @param key must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @param limit can be {@literal null}.
	 * @return
	 * @since 1.6
	 * @see <a href="https://valkey.io/commands/zrangebylex">Valkey Documentation: ZRANGEBYLEX</a>
	 * @see ValkeyZSetCommands#zRangeByLex(byte[], org.springframework.data.domain.Range,
	 *      io.valkey.springframework.data.valkey.connection.Limit)
	 */
	Set<String> zRangeByLex(String key, org.springframework.data.domain.Range<String> range,
			io.valkey.springframework.data.valkey.connection.Limit limit);

	/**
	 * Get all the elements in the sorted set at {@literal key} in reversed lexicographical ordering.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @since 2.4
	 * @see <a href="https://valkey.io/commands/zrevrangebylex">Valkey Documentation: ZREVRANGEBYLEX</a>
	 * @see ValkeyZSetCommands#zRevRangeByLex(byte[])
	 */
	default Set<String> zRevRangeByLex(String key) {
		return zRevRangeByLex(key, org.springframework.data.domain.Range.unbounded());
	}

	/**
	 * Get all the elements in {@link Range} from the sorted set at {@literal key} in reversed lexicographical ordering.
	 *
	 * @param key must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @return
	 * @since 2.4
	 * @see <a href="https://valkey.io/commands/zrevrangebylex">Valkey Documentation: ZREVRANGEBYLEX</a>
	 * @see ValkeyZSetCommands#zRevRangeByLex(byte[], org.springframework.data.domain.Range)
	 */
	default Set<String> zRevRangeByLex(String key, org.springframework.data.domain.Range<String> range) {
		return zRevRangeByLex(key, range, io.valkey.springframework.data.valkey.connection.Limit.unlimited());
	}

	/**
	 * Get all the elements in {@link Range} from the sorted set at {@literal key} in reversed lexicographical ordering.
	 * Result is limited via {@link io.valkey.springframework.data.valkey.connection.Limit}.
	 *
	 * @param key must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return
	 * @since 2.4
	 * @see <a href="https://valkey.io/commands/zrevrangebylex">Valkey Documentation: ZREVRANGEBYLEX</a>
	 * @see ValkeyZSetCommands#zRevRangeByLex(byte[], org.springframework.data.domain.Range,
	 *      io.valkey.springframework.data.valkey.connection.Limit)
	 */
	Set<String> zRevRangeByLex(String key, org.springframework.data.domain.Range<String> range,
			io.valkey.springframework.data.valkey.connection.Limit limit);

	/**
	 * This command is like ZRANGE , but stores the result in the {@literal dstKey} destination key.
	 *
	 * @param dstKey must not be {@literal null}.
	 * @param srcKey must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/zrangestore">Valkey Documentation: ZRANGESTORE</a>
	 */
	@Nullable
	default Long zRangeStoreByLex(String dstKey, String srcKey, org.springframework.data.domain.Range<String> range) {
		return zRangeStoreByLex(dstKey, srcKey, range, io.valkey.springframework.data.valkey.connection.Limit.unlimited());
	}

	/**
	 * This command is like ZRANGE , but stores the result in the {@literal dstKey} destination key.
	 *
	 * @param dstKey must not be {@literal null}.
	 * @param srcKey must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/zrangestore">Valkey Documentation: ZRANGESTORE</a>
	 */
	@Nullable
	Long zRangeStoreByLex(String dstKey, String srcKey, org.springframework.data.domain.Range<String> range,
			io.valkey.springframework.data.valkey.connection.Limit limit);

	/**
	 * This command is like ZRANGE … REV , but stores the result in the {@literal dstKey} destination key.
	 *
	 * @param dstKey must not be {@literal null}.
	 * @param srcKey must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/zrangestore">Valkey Documentation: ZRANGESTORE</a>
	 */
	@Nullable
	default Long zRangeStoreRevByLex(String dstKey, String srcKey, org.springframework.data.domain.Range<String> range) {
		return zRangeStoreRevByLex(dstKey, srcKey, range, io.valkey.springframework.data.valkey.connection.Limit.unlimited());
	}

	/**
	 * This command is like ZRANGE … REV , but stores the result in the {@literal dstKey} destination key.
	 *
	 * @param dstKey must not be {@literal null}.
	 * @param srcKey must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/zrangestore">Valkey Documentation: ZRANGESTORE</a>
	 */
	@Nullable
	Long zRangeStoreRevByLex(String dstKey, String srcKey, org.springframework.data.domain.Range<String> range,
			io.valkey.springframework.data.valkey.connection.Limit limit);

	/**
	 * This command is like ZRANGE, but stores the result in the {@literal dstKey} destination key.
	 *
	 * @param dstKey must not be {@literal null}.
	 * @param srcKey must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/zrangestore">Valkey Documentation: ZRANGESTORE</a>
	 */
	@Nullable
	default Long zRangeStoreByScore(String dstKey, String srcKey,
			org.springframework.data.domain.Range<? extends Number> range) {
		return zRangeStoreByScore(dstKey, srcKey, range, io.valkey.springframework.data.valkey.connection.Limit.unlimited());
	}

	/**
	 * This command is like ZRANGE, but stores the result in the {@literal dstKey} destination key.
	 *
	 * @param dstKey must not be {@literal null}.
	 * @param srcKey must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/zrangestore">Valkey Documentation: ZRANGESTORE</a>
	 */
	@Nullable
	Long zRangeStoreByScore(String dstKey, String srcKey, org.springframework.data.domain.Range<? extends Number> range,
			io.valkey.springframework.data.valkey.connection.Limit limit);

	/**
	 * This command is like ZRANGE … REV, but stores the result in the {@literal dstKey} destination key.
	 *
	 * @param dstKey must not be {@literal null}.
	 * @param srcKey must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/zrangestore">Valkey Documentation: ZRANGESTORE</a>
	 */
	@Nullable
	default Long zRangeStoreRevByScore(String dstKey, String srcKey,
			org.springframework.data.domain.Range<? extends Number> range) {
		return zRangeStoreRevByScore(dstKey, srcKey, range, io.valkey.springframework.data.valkey.connection.Limit.unlimited());
	}

	/**
	 * This command is like ZRANGE … REV, but stores the result in the {@literal dstKey} destination key.
	 *
	 * @param dstKey must not be {@literal null}.
	 * @param srcKey must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/zrangestore">Valkey Documentation: ZRANGESTORE</a>
	 */
	@Nullable
	Long zRangeStoreRevByScore(String dstKey, String srcKey,
			org.springframework.data.domain.Range<? extends Number> range,
			io.valkey.springframework.data.valkey.connection.Limit limit);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Hashes
	// -------------------------------------------------------------------------

	/**
	 * Set the {@code value} of a hash {@code field}.
	 *
	 * @param key must not be {@literal null}.
	 * @param field must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/hset">Valkey Documentation: HSET</a>
	 * @see ValkeyHashCommands#hSet(byte[], byte[], byte[])
	 */
	Boolean hSet(String key, String field, String value);

	/**
	 * Set the {@code value} of a hash {@code field} only if {@code field} does not exist.
	 *
	 * @param key must not be {@literal null}.
	 * @param field must not be {@literal null}.
	 * @param value
	 * @return
	 * @see <a href="https://valkey.io/commands/hsetnx">Valkey Documentation: HSETNX</a>
	 * @see ValkeyHashCommands#hSetNX(byte[], byte[], byte[])
	 */
	Boolean hSetNX(String key, String field, String value);

	/**
	 * Get value for given {@code field} from hash at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param field must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/hget">Valkey Documentation: HGET</a>
	 * @see ValkeyHashCommands#hGet(byte[], byte[])
	 */
	String hGet(String key, String field);

	/**
	 * Get values for given {@code fields} from hash at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/hmget">Valkey Documentation: HMGET</a>
	 * @see ValkeyHashCommands#hMGet(byte[], byte[]...)
	 */
	List<String> hMGet(String key, String... fields);

	/**
	 * Set multiple hash fields to multiple values using data provided in {@code hashes}
	 *
	 * @param key must not be {@literal null}.
	 * @param hashes must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/hmset">Valkey Documentation: HMSET</a>
	 * @see ValkeyHashCommands#hMGet(byte[], byte[]...)
	 */
	void hMSet(String key, Map<String, String> hashes);

	/**
	 * Increment {@code value} of a hash {@code field} by the given {@code delta}.
	 *
	 * @param key must not be {@literal null}.
	 * @param field must not be {@literal null}.
	 * @param delta
	 * @return
	 * @see <a href="https://valkey.io/commands/hincrby">Valkey Documentation: HINCRBY</a>
	 * @see ValkeyHashCommands#hIncrBy(byte[], byte[], long)
	 */
	Long hIncrBy(String key, String field, long delta);

	/**
	 * Increment {@code value} of a hash {@code field} by the given {@code delta}.
	 *
	 * @param key must not be {@literal null}.
	 * @param field
	 * @param delta
	 * @return
	 * @see <a href="https://valkey.io/commands/hincrbyfloat">Valkey Documentation: HINCRBYFLOAT</a>
	 * @see ValkeyHashCommands#hIncrBy(byte[], byte[], double)
	 */
	Double hIncrBy(String key, String field, double delta);

	/**
	 * Return a random field from the hash stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/hrandfield">Valkey Documentation: HRANDFIELD</a>
	 */
	@Nullable
	String hRandField(String key);

	/**
	 * Return a random field from the hash along with its value stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/hrandfield">Valkey Documentation: HRANDFIELD</a>
	 */
	@Nullable
	Map.Entry<String, String> hRandFieldWithValues(String key);

	/**
	 * Return a random field from the hash stored at {@code key}. If the provided {@code count} argument is positive,
	 * return a list of distinct fields, capped either at {@code count} or the hash size. If {@code count} is negative,
	 * the behavior changes and the command is allowed to return the same field multiple times. In this case, the number
	 * of returned fields is the absolute value of the specified count.
	 *
	 * @param key must not be {@literal null}.
	 * @param count number of fields to return.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/hrandfield">Valkey Documentation: HRANDFIELD</a>
	 */
	@Nullable
	List<String> hRandField(String key, long count);

	/**
	 * Return a random field from the hash along with its value stored at {@code key}. If the provided {@code count}
	 * argument is positive, return a list of distinct fields, capped either at {@code count} or the hash size. If
	 * {@code count} is negative, the behavior changes and the command is allowed to return the same field multiple times.
	 * In this case, the number of returned fields is the absolute value of the specified count.
	 *
	 * @param key must not be {@literal null}.
	 * @param count number of fields to return.
	 * @return {@literal null} if key does not exist or when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/hrandfield">Valkey Documentation: HRANDFIELD</a>
	 */
	@Nullable
	List<Map.Entry<String, String>> hRandFieldWithValues(String key, long count);

	/**
	 * Determine if given hash {@code field} exists.
	 *
	 * @param key must not be {@literal null}.
	 * @param field must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/hexits">Valkey Documentation: HEXISTS</a>
	 * @see ValkeyHashCommands#hExists(byte[], byte[])
	 */
	Boolean hExists(String key, String field);

	/**
	 * Delete given hash {@code fields}.
	 *
	 * @param key must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/hdel">Valkey Documentation: HDEL</a>
	 * @see ValkeyHashCommands#hDel(byte[], byte[]...)
	 */
	Long hDel(String key, String... fields);

	/**
	 * Get size of hash at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/hlen">Valkey Documentation: HLEN</a>
	 * @see ValkeyHashCommands#hLen(byte[])
	 */
	Long hLen(String key);

	/**
	 * Get key set (fields) of hash at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/hkeys">Valkey Documentation: HKEYS</a>?
	 * @see ValkeyHashCommands#hKeys(byte[])
	 */
	Set<String> hKeys(String key);

	/**
	 * Get entry set (values) of hash at {@code field}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/hvals">Valkey Documentation: HVALS</a>
	 * @see ValkeyHashCommands#hVals(byte[])
	 */
	List<String> hVals(String key);

	/**
	 * Get entire hash stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/hgetall">Valkey Documentation: HGETALL</a>
	 * @see ValkeyHashCommands#hGetAll(byte[])
	 */
	Map<String, String> hGetAll(String key);

	/**
	 * Use a {@link Cursor} to iterate over entries in hash at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @return
	 * @since 1.4
	 * @see <a href="https://valkey.io/commands/hscan">Valkey Documentation: HSCAN</a>
	 * @see ValkeyHashCommands#hScan(byte[], ScanOptions)
	 */
	Cursor<Map.Entry<String, String>> hScan(String key, ScanOptions options);

	/**
	 * Returns the length of the value associated with {@code field} in the hash stored at {@code key}. If the key or the
	 * field do not exist, {@code 0} is returned.
	 *
	 * @param key must not be {@literal null}.
	 * @param field must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.1
	 */
	@Nullable
	Long hStrLen(String key, String field);

	/**
	 * Set time to live for given {@code field} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param seconds the amount of time after which the key will be expired in seconds, must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 2} indicating the specific field is
	 *         deleted already due to expiration, or provided expiry interval is 0; {@code 1} indicating expiration time
	 *         is set/updated; {@code 0} indicating the expiration time is not set; {@code -2} indicating there is no such
	 *         field; {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hexpire/">Valkey Documentation: HEXPIRE</a>
	 * @since 3.5
	 */
	@Nullable
	default List<Long> hExpire(String key, long seconds, String... fields) {
		return hExpire(key, seconds, ExpirationOptions.Condition.ALWAYS, fields);
	}

	/**
	 * Set time to live for given {@code field} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param seconds the amount of time after which the key will be expired in seconds, must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 2} indicating the specific field is
	 *         deleted already due to expiration, or provided expiry interval is 0; {@code 1} indicating expiration time
	 *         is set/updated; {@code 0} indicating the expiration time is not set (a provided NX | XX | GT | LT condition
	 *         is not met); {@code -2} indicating there is no such field; {@literal null} when used in pipeline /
	 *         transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hexpire/">Valkey Documentation: HEXPIRE</a>
	 * @since 3.5
	 */
	@Nullable
	List<Long> hExpire(String key, long seconds, ExpirationOptions.Condition condition, String... fields);

	/**
	 * Set time to live for given {@code field} in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param millis the amount of time after which the key will be expired in milliseconds, must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 2} indicating the specific field is
	 *         deleted already due to expiration, or provided expiry interval is 0; {@code 1} indicating expiration time
	 *         is set/updated; {@code 0} indicating the expiration time is not set; {@code -2} indicating there is no such
	 *         field; {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hpexpire/">Valkey Documentation: HPEXPIRE</a>
	 * @since 3.5
	 */
	@Nullable
	default List<Long> hpExpire(String key, long millis, String... fields) {
		return hpExpire(key, millis, ExpirationOptions.Condition.ALWAYS, fields);
	}

	/**
	 * Set time to live for given {@code field} in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param millis the amount of time after which the key will be expired in milliseconds, must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 2} indicating the specific field is
	 *         deleted already due to expiration, or provided expiry interval is 0; {@code 1} indicating expiration time
	 *         is set/updated; {@code 0} indicating the expiration time is not set (a provided NX | XX | GT | LT condition
	 *         is not met); {@code -2} indicating there is no such field; {@literal null} when used in pipeline /
	 *         transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hpexpire/">Valkey Documentation: HPEXPIRE</a>
	 * @since 3.5
	 */
	@Nullable
	List<Long> hpExpire(String key, long millis, ExpirationOptions.Condition condition, String... fields);

	/**
	 * Set the expiration for given {@code field} as a {@literal UNIX} timestamp.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTime the moment in time in which the field expires, must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 2} indicating the specific field is
	 *         deleted already due to expiration, or provided expiry interval is in the past; {@code 1} indicating
	 *         expiration time is set/updated; {@code 0} indicating the expiration time is not set; {@code -2} indicating
	 *         there is no such field; {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hexpireat/">Valkey Documentation: HEXPIREAT</a>
	 * @since 3.5
	 */
	@Nullable
	default List<Long> hExpireAt(String key, long unixTime, String... fields) {
		return hExpireAt(key, unixTime, ExpirationOptions.Condition.ALWAYS, fields);
	}

	/**
	 * Set the expiration for given {@code field} as a {@literal UNIX} timestamp.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTime the moment in time in which the field expires, must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 2} indicating the specific field is
	 *         deleted already due to expiration, or provided expiry interval is in the past; {@code 1} indicating
	 *         expiration time is set/updated; {@code 0} indicating the expiration time is not set (a provided NX | XX |
	 *         GT | LT condition is not met); {@code -2} indicating there is no such field; {@literal null} when used in
	 *         pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hexpireat/">Valkey Documentation: HEXPIREAT</a>
	 * @since 3.5
	 */
	@Nullable
	List<Long> hExpireAt(String key, long unixTime, ExpirationOptions.Condition condition, String... fields);

	/**
	 * Set the expiration for given {@code field} as a {@literal UNIX} timestamp in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTimeInMillis the moment in time in which the field expires in milliseconds, must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 2} indicating the specific field is
	 *         deleted already due to expiration, or provided expiry interval is in the past; {@code 1} indicating
	 *         expiration time is set/updated; {@code 0} indicating the expiration time is not set; {@code -2} indicating
	 *         there is no such field; {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hpexpireat/">Valkey Documentation: HPEXPIREAT</a>
	 * @since 3.5
	 */
	@Nullable
	default List<Long> hpExpireAt(String key, long unixTimeInMillis, String... fields) {
		return hpExpireAt(key, unixTimeInMillis, ExpirationOptions.Condition.ALWAYS, fields);
	}

	/**
	 * Set the expiration for given {@code field} as a {@literal UNIX} timestamp in milliseconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param unixTimeInMillis the moment in time in which the field expires in milliseconds, must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 2} indicating the specific field is
	 *         deleted already due to expiration, or provided expiry interval is in the past; {@code 1} indicating
	 *         expiration time is set/updated; {@code 0} indicating the expiration time is not set (a provided NX | XX |
	 *         GT | LT condition is not met); {@code -2} indicating there is no such field; {@literal null} when used in
	 *         pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hpexpireat/">Valkey Documentation: HPEXPIREAT</a>
	 * @since 3.5
	 */
	@Nullable
	List<Long> hpExpireAt(String key, long unixTimeInMillis, ExpirationOptions.Condition condition,
			String... fields);

	/**
	 * Remove the expiration from given {@code field}.
	 *
	 * @param key must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: {@code 1} indicating expiration time is
	 *         removed; {@code -1} field has no expiration time to be removed; {@code -2} indicating there is no such
	 *         field; {@literal null} when used in pipeline / transaction.{@literal null} when used in pipeline /
	 *         transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hpersist/">Valkey Documentation: HPERSIST</a>
	 * @since 3.5
	 */
	@Nullable
	List<Long> hPersist(String key, String... fields);

	/**
	 * Get the time to live for {@code fields} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: the time to live in milliseconds; or a
	 *         negative value to signal an error. The command returns {@code -1} if the key exists but has no associated
	 *         expiration time. The command returns {@code -2} if the key does not exist; {@literal null} when used in
	 *         pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hexpire/">Valkey Documentation: HTTL</a>
	 * @since 3.5
	 */
	@Nullable
	List<Long> hTtl(String key, String... fields);

	/**
	 * Get the time to live for {@code fields} in and convert it to the given {@link TimeUnit}.
	 *
	 * @param key must not be {@literal null}.
	 * @param timeUnit must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: the time to live in the {@link TimeUnit}
	 *         provided; or a negative value to signal an error. The command returns {@code -1} if the key exists but has
	 *         no associated expiration time. The command returns {@code -2} if the key does not exist; {@literal null}
	 *         when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hexpire/">Valkey Documentation: HTTL</a>
	 * @since 3.5
	 */
	@Nullable
	List<Long> hTtl(String key, TimeUnit timeUnit, String... fields);

	/**
	 * Get the time to live for {@code fields} in seconds.
	 *
	 * @param key must not be {@literal null}.
	 * @param fields must not be {@literal null}.
	 * @return a list of {@link Long} values for each of the fields provided: the time to live in milliseconds; or a
	 *         negative value to signal an error. The command returns {@code -1} if the key exists but has no associated
	 *         expiration time. The command returns {@code -2} if the key does not exist; {@literal null} when used in
	 *         pipeline / transaction.
	 * @see <a href="https://valkey.io/docs/latest/commands/hexpire/">Valkey Documentation: HTTL</a>
	 * @since 3.5
	 */
	@Nullable
	List<Long> hpTtl(String key, String... fields);

	// -------------------------------------------------------------------------
	// Methods dealing with HyperLogLog
	// -------------------------------------------------------------------------

	/**
	 * Adds given {@literal values} to the HyperLogLog stored at given {@literal key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param values must not be {@literal null}.
	 * @return
	 * @since 1.5
	 * @see <a href="https://valkey.io/commands/pfadd">Valkey Documentation: PFADD</a>
	 * @see ValkeyHyperLogLogCommands#pfAdd(byte[], byte[]...)
	 */
	Long pfAdd(String key, String... values);

	/**
	 * Return the approximated cardinality of the structures observed by the HyperLogLog at {@literal key(s)}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/pfcount">Valkey Documentation: PFCOUNT</a>
	 * @see ValkeyHyperLogLogCommands#pfCount(byte[]...)
	 */
	Long pfCount(String... keys);

	/**
	 * Merge N different HyperLogLogs at {@literal sourceKeys} into a single {@literal destinationKey}.
	 *
	 * @param destinationKey must not be {@literal null}.
	 * @param sourceKeys must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/pfmerge">Valkey Documentation: PFMERGE</a>
	 * @see ValkeyHyperLogLogCommands#pfMerge(byte[], byte[]...)
	 */
	void pfMerge(String destinationKey, String... sourceKeys);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Geo-Indexes
	// -------------------------------------------------------------------------

	/**
	 * Add {@link Point} with given member {@literal name} to {@literal key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param point must not be {@literal null}.
	 * @param member must not be {@literal null}.
	 * @return Number of elements added.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/geoadd">Valkey Documentation: GEOADD</a>
	 * @see ValkeyGeoCommands#geoAdd(byte[], Point, byte[])
	 */
	Long geoAdd(String key, Point point, String member);

	/**
	 * Add {@link GeoLocation} to {@literal key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param location must not be {@literal null}.
	 * @return Number of elements added.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/geoadd">Valkey Documentation: GEOADD</a>
	 * @see ValkeyGeoCommands#geoAdd(byte[], GeoLocation)
	 */
	Long geoAdd(String key, GeoLocation<String> location);

	/**
	 * Add {@link Map} of member / {@link Point} pairs to {@literal key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param memberCoordinateMap must not be {@literal null}.
	 * @return Number of elements added.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/geoadd">Valkey Documentation: GEOADD</a>
	 * @see ValkeyGeoCommands#geoAdd(byte[], Map)
	 */
	Long geoAdd(String key, Map<String, Point> memberCoordinateMap);

	/**
	 * Add {@link GeoLocation}s to {@literal key}
	 *
	 * @param key must not be {@literal null}.
	 * @param locations must not be {@literal null}.
	 * @return Number of elements added.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/geoadd">Valkey Documentation: GEOADD</a>
	 * @see ValkeyGeoCommands#geoAdd(byte[], Iterable)
	 */
	Long geoAdd(String key, Iterable<GeoLocation<String>> locations);

	/**
	 * Get the {@link Distance} between {@literal member1} and {@literal member2}.
	 *
	 * @param key must not be {@literal null}.
	 * @param member1 must not be {@literal null}.
	 * @param member2 must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/geodist">Valkey Documentation: GEODIST</a>
	 * @see ValkeyGeoCommands#geoDist(byte[], byte[], byte[])
	 */
	Distance geoDist(String key, String member1, String member2);

	/**
	 * Get the {@link Distance} between {@literal member1} and {@literal member2} in the given {@link Metric}.
	 *
	 * @param key must not be {@literal null}.
	 * @param member1 must not be {@literal null}.
	 * @param member2 must not be {@literal null}.
	 * @param metric must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/geodist">Valkey Documentation: GEODIST</a>
	 * @see ValkeyGeoCommands#geoDist(byte[], byte[], byte[], Metric)
	 */
	Distance geoDist(String key, String member1, String member2, Metric metric);

	/**
	 * Get geohash representation of the position for one or more {@literal member}s.
	 *
	 * @param key must not be {@literal null}.
	 * @param members must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/geohash">Valkey Documentation: GEOHASH</a>
	 * @see ValkeyGeoCommands#geoHash(byte[], byte[]...)
	 */
	List<String> geoHash(String key, String... members);

	/**
	 * Get the {@link Point} representation of positions for one or more {@literal member}s.
	 *
	 * @param key must not be {@literal null}.
	 * @param members must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/geopos">Valkey Documentation: GEOPOS</a>
	 * @see ValkeyGeoCommands#geoPos(byte[], byte[]...)
	 */
	List<Point> geoPos(String key, String... members);

	/**
	 * Get the {@literal member}s within the boundaries of a given {@link Circle}.
	 *
	 * @param key must not be {@literal null}.
	 * @param within must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/georadius">Valkey Documentation: GEORADIUS</a>
	 * @see ValkeyGeoCommands#geoRadius(byte[], Circle)
	 */
	GeoResults<GeoLocation<String>> geoRadius(String key, Circle within);

	/**
	 * Get the {@literal member}s within the boundaries of a given {@link Circle} applying {@link GeoRadiusCommandArgs}.
	 *
	 * @param key must not be {@literal null}.
	 * @param within must not be {@literal null}.
	 * @param args must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/georadius">Valkey Documentation: GEORADIUS</a>
	 * @see ValkeyGeoCommands#geoRadius(byte[], Circle, GeoRadiusCommandArgs)
	 */
	GeoResults<GeoLocation<String>> geoRadius(String key, Circle within, GeoRadiusCommandArgs args);

	/**
	 * Get the {@literal member}s within the circle defined by the {@literal members} coordinates and given
	 * {@literal radius}.
	 *
	 * @param key must not be {@literal null}.
	 * @param member must not be {@literal null}.
	 * @param radius
	 * @return never {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/georadiusbymember">Valkey Documentation: GEORADIUSBYMEMBER</a>
	 * @see ValkeyGeoCommands#geoRadiusByMember(byte[], byte[], double)
	 */
	GeoResults<GeoLocation<String>> geoRadiusByMember(String key, String member, double radius);

	/**
	 * Get the {@literal member}s within the circle defined by the {@literal members} coordinates and given
	 * {@link Distance}.
	 *
	 * @param key must not be {@literal null}.
	 * @param member must not be {@literal null}.
	 * @param radius must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/georadiusbymember">Valkey Documentation: GEORADIUSBYMEMBER</a>
	 * @see ValkeyGeoCommands#geoRadiusByMember(byte[], byte[], Distance)
	 */
	GeoResults<GeoLocation<String>> geoRadiusByMember(String key, String member, Distance radius);

	/**
	 * Get the {@literal member}s within the circle defined by the {@literal members} coordinates and given
	 * {@link Distance} and {@link GeoRadiusCommandArgs}.
	 *
	 * @param key must not be {@literal null}.
	 * @param member must not be {@literal null}.
	 * @param radius must not be {@literal null}.
	 * @param args must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.8
	 * @see <a href="https://valkey.io/commands/georadiusbymember">Valkey Documentation: GEORADIUSBYMEMBER</a>
	 * @see ValkeyGeoCommands#geoRadiusByMember(byte[], byte[], Distance, GeoRadiusCommandArgs)
	 */
	GeoResults<GeoLocation<String>> geoRadiusByMember(String key, String member, Distance radius,
			GeoRadiusCommandArgs args);

	/**
	 * Remove the {@literal member}s.
	 *
	 * @param key must not be {@literal null}.
	 * @param members must not be {@literal null}.
	 * @since 1.8
	 * @return Number of members elements removed.
	 * @see <a href="https://valkey.io/commands/zrem">Valkey Documentation: ZREM</a>
	 * @see ValkeyGeoCommands#geoRemove(byte[], byte[]...)
	 */
	Long geoRemove(String key, String... members);

	/**
	 * Return the members of a geo set which are within the borders of the area specified by a given {@link GeoShape
	 * shape}. The query's center point is provided by {@link GeoReference}.
	 *
	 * @param key must not be {@literal null}.
	 * @param reference must not be {@literal null}.
	 * @param predicate must not be {@literal null}.
	 * @param args must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/geosearch">Valkey Documentation: GEOSEARCH</a>
	 */
	@Nullable
	GeoResults<GeoLocation<String>> geoSearch(String key, GeoReference<String> reference, GeoShape predicate,
			GeoSearchCommandArgs args);

	/**
	 * Query the members of a geo set which are within the borders of the area specified by a given {@link GeoShape shape}
	 * and store the result at {@code destKey}. The query's center point is provided by {@link GeoReference}.
	 *
	 * @param key must not be {@literal null}.
	 * @param reference must not be {@literal null}.
	 * @param predicate must not be {@literal null}.
	 * @param args must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/geosearch">Valkey Documentation: GEOSEARCH</a>
	 */
	@Nullable
	Long geoSearchStore(String destKey, String key, GeoReference<String> reference, GeoShape predicate,
			GeoSearchStoreCommandArgs args);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Pub/Sub
	// -------------------------------------------------------------------------

	/**
	 * Publishes the given message to the given channel.
	 *
	 * @param channel the channel to publish to, must not be {@literal null}.
	 * @param message message to publish
	 * @return the number of clients that received the message
	 * @see <a href="https://valkey.io/commands/publish">Valkey Documentation: PUBLISH</a>
	 * @see ValkeyPubSubCommands#publish(byte[], byte[])
	 */
	Long publish(String channel, String message);

	/**
	 * Subscribes the connection to the given channels. Once subscribed, a connection enters listening mode and can only
	 * subscribe to other channels or unsubscribe. No other commands are accepted until the connection is unsubscribed.
	 * <p>
	 * Note that this operation is blocking and the current thread starts waiting for new messages immediately.
	 *
	 * @param listener message listener, must not be {@literal null}.
	 * @param channels channel names, must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/subscribe">Valkey Documentation: SUBSCRIBE</a>
	 * @see ValkeyPubSubCommands#subscribe(MessageListener, byte[]...)
	 */
	void subscribe(MessageListener listener, String... channels);

	/**
	 * Subscribes the connection to all channels matching the given patterns. Once subscribed, a connection enters
	 * listening mode and can only subscribe to other channels or unsubscribe. No other commands are accepted until the
	 * connection is unsubscribed.
	 * <p>
	 * Note that this operation is blocking and the current thread starts waiting for new messages immediately.
	 *
	 * @param listener message listener, must not be {@literal null}.
	 * @param patterns channel name patterns, must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/psubscribe">Valkey Documentation: PSUBSCRIBE</a>
	 * @see ValkeyPubSubCommands#pSubscribe(MessageListener, byte[]...)
	 */
	void pSubscribe(MessageListener listener, String... patterns);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Lua Scripting
	// -------------------------------------------------------------------------

	/**
	 * Load lua script into scripts cache, without executing it.<br>
	 * Execute the script by calling {@link #evalSha(byte[], ReturnType, int, byte[]...)}.
	 *
	 * @param script must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/script-load">Valkey Documentation: SCRIPT LOAD</a>
	 * @see ValkeyScriptingCommands#scriptLoad(byte[])
	 */
	String scriptLoad(String script);

	/**
	 * Evaluate given {@code script}.
	 *
	 * @param script must not be {@literal null}.
	 * @param returnType must not be {@literal null}.
	 * @param numKeys
	 * @param keysAndArgs must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/eval">Valkey Documentation: EVAL</a>
	 * @see ValkeyScriptingCommands#eval(byte[], ReturnType, int, byte[]...)
	 */
	<T> T eval(String script, ReturnType returnType, int numKeys, String... keysAndArgs);

	/**
	 * Evaluate given {@code scriptSha}.
	 *
	 * @param scriptSha must not be {@literal null}.
	 * @param returnType must not be {@literal null}.
	 * @param numKeys
	 * @param keysAndArgs must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/evalsha">Valkey Documentation: EVALSHA</a>
	 * @see ValkeyScriptingCommands#evalSha(String, ReturnType, int, byte[]...)
	 */
	<T> T evalSha(String scriptSha, ReturnType returnType, int numKeys, String... keysAndArgs);

	/**
	 * Assign given name to current connection.
	 *
	 * @param name
	 * @since 1.3
	 * @see <a href="https://valkey.io/commands/client-setname">Valkey Documentation: CLIENT SETNAME</a>
	 * @see ValkeyServerCommands#setClientName(byte[])
	 */
	void setClientName(String name);

	/**
	 * Request information and statistics about connected clients.
	 *
	 * @return {@link List} of {@link ValkeyClientInfo} objects.
	 * @since 1.3
	 * @see <a href="https://valkey.io/commands/client-list">Valkey Documentation: CLIENT LIST</a>
	 * @see ValkeyServerCommands#getClientList()
	 */
	List<ValkeyClientInfo> getClientList();

	/**
	 * Get / Manipulate specific integer fields of varying bit widths and arbitrary non (necessary) aligned offset stored
	 * at a given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param command must not be {@literal null}.
	 * @return
	 */
	List<Long> bitfield(String key, BitFieldSubCommands command);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Streams
	// -------------------------------------------------------------------------

	static RecordId[] entryIds(String... entryIds) {

		if (entryIds.length == 1) {
			return new RecordId[] { RecordId.of(entryIds[0]) };
		}

		return Arrays.stream(entryIds).map(RecordId::of).toArray(RecordId[]::new);
	}

	/**
	 * Acknowledge one or more record as processed.
	 *
	 * @param key the stream key.
	 * @param group name of the consumer group.
	 * @param entryIds record Id's to acknowledge.
	 * @return length of acknowledged records. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xack">Valkey Documentation: XACK</a>
	 */
	@Nullable
	default Long xAck(String key, String group, String... entryIds) {
		return xAck(key, group, entryIds(entryIds));
	}

	Long xAck(String key, String group, RecordId... recordIds);

	/**
	 * Append a record to the stream {@code key}.
	 *
	 * @param key the stream key.
	 * @param body record body.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xadd">Valkey Documentation: XADD</a>
	 */
	@Nullable
	default RecordId xAdd(String key, Map<String, String> body) {
		return xAdd(StreamRecords.newRecord().in(key).ofStrings(body));
	}

	/**
	 * Append the given {@link StringRecord} to the stream stored at {@link StringRecord#getStream()}.
	 *
	 * @param record must not be {@literal null}.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 */
	@Nullable
	default RecordId xAdd(StringRecord record) {
		return xAdd(record, XAddOptions.none());
	}

	/**
	 * Append the given {@link StringRecord} to the stream stored at {@link StringRecord#getStream()}.
	 *
	 * @param record must not be {@literal null}.
	 * @param options must not be {@literal null}, use {@link XAddOptions#none()} instead.
	 * @return the record Id. {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	@Nullable
	RecordId xAdd(StringRecord record, XAddOptions options);

	/**
	 * Change the ownership of a pending message to the given new {@literal consumer} without increasing the delivered
	 * count.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param group the name of the {@literal consumer group}.
	 * @param newOwner the name of the new {@literal consumer}.
	 * @param options must not be {@literal null}.
	 * @return list of {@link RecordId ids} that changed user.
	 * @see <a href="https://valkey.io/commands/xclaim">Valkey Documentation: XCLAIM</a>
	 * @since 2.3
	 */
	List<RecordId> xClaimJustId(String key, String group, String newOwner, XClaimOptions options);

	/**
	 * Change the ownership of a pending message to the given new {@literal consumer}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param group the name of the {@literal consumer group}.
	 * @param newOwner the name of the new {@literal consumer}.
	 * @param minIdleTime must not be {@literal null}.
	 * @param recordIds must not be {@literal null}.
	 * @return list of {@link StringRecord} that changed user.
	 * @see <a href="https://valkey.io/commands/xclaim">Valkey Documentation: XCLAIM</a>
	 * @since 2.3
	 */
	default List<StringRecord> xClaim(String key, String group, String newOwner, Duration minIdleTime,
			RecordId... recordIds) {
		return xClaim(key, group, newOwner, XClaimOptions.minIdle(minIdleTime).ids(recordIds));
	}

	/**
	 * Change the ownership of a pending message to the given new {@literal consumer}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param group the name of the {@literal consumer group}.
	 * @param newOwner the name of the new {@literal consumer}.
	 * @param options must not be {@literal null}.
	 * @return list of {@link StringRecord} that changed user.
	 * @see <a href="https://valkey.io/commands/xclaim">Valkey Documentation: XCLAIM</a>
	 * @since 2.3
	 */
	List<StringRecord> xClaim(String key, String group, String newOwner, XClaimOptions options);

	/**
	 * Removes the specified entries from the stream. Returns the number of items deleted, that may be different from the
	 * number of IDs passed in case certain IDs do not exist.
	 *
	 * @param key the stream key.
	 * @param entryIds stream record Id's.
	 * @return number of removed entries. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xdel">Valkey Documentation: XDEL</a>
	 */
	@Nullable
	default Long xDel(String key, String... entryIds) {
		return xDel(key, entryIds(entryIds));
	}

	Long xDel(String key, RecordId... recordIds);

	/**
	 * Create a consumer group.
	 *
	 * @param key the stream key.
	 * @param readOffset
	 * @param group name of the consumer group.
	 * @since 2.2
	 * @return {@literal true} if successful. {@literal null} when used in pipeline / transaction.
	 */
	@Nullable
	String xGroupCreate(String key, ReadOffset readOffset, String group);

	/**
	 * Create a consumer group.
	 *
	 * @param key the stream key.
	 * @param readOffset
	 * @param group name of the consumer group.
	 * @param mkStream if true the group will create the stream if needed (MKSTREAM)
	 * @since
	 * @return {@literal true} if successful. {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	@Nullable
	String xGroupCreate(String key, ReadOffset readOffset, String group, boolean mkStream);

	/**
	 * Delete a consumer from a consumer group.
	 *
	 * @param key the stream key.
	 * @param consumer consumer identified by group name and consumer key.
	 * @since 2.2
	 * @return {@literal true} if successful. {@literal null} when used in pipeline / transaction.
	 */
	@Nullable
	Boolean xGroupDelConsumer(String key, Consumer consumer);

	/**
	 * Destroy a consumer group.
	 *
	 * @param key the stream key.
	 * @param group name of the consumer group.
	 * @return {@literal true} if successful. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 */
	@Nullable
	Boolean xGroupDestroy(String key, String group);

	/**
	 * Obtain general information about the stream stored at the specified {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	@Nullable
	XInfoStream xInfo(String key);

	/**
	 * Obtain information about {@literal consumer groups} associated with the stream stored at the specified
	 * {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	@Nullable
	XInfoGroups xInfoGroups(String key);

	/**
	 * Obtain information about every consumer in a specific {@literal consumer group} for the stream stored at the
	 * specified {@literal key}.
	 *
	 * @param key the {@literal key} the stream is stored at.
	 * @param groupName name of the {@literal consumer group}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.3
	 */
	@Nullable
	XInfoConsumers xInfoConsumers(String key, String groupName);

	/**
	 * Get the length of a stream.
	 *
	 * @param key the stream key.
	 * @return length of the stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xlen">Valkey Documentation: XLEN</a>
	 */
	@Nullable
	Long xLen(String key);

	/**
	 * Obtain the {@link PendingMessagesSummary} for a given {@literal consumer group}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param groupName the name of the {@literal consumer group}. Must not be {@literal null}.
	 * @return a summary of pending messages within the given {@literal consumer group} or {@literal null} when used in
	 *         pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/xpending">Valkey Documentation: xpending</a>
	 * @since 2.3
	 */
	@Nullable
	PendingMessagesSummary xPending(String key, String groupName);

	/**
	 * Obtain detailed information about pending {@link PendingMessage messages} for a given
	 * {@link org.springframework.data.domain.Range} within a {@literal consumer group}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param groupName the name of the {@literal consumer group}. Must not be {@literal null}.
	 * @param consumerName the name of the {@literal consumer}. Must not be {@literal null}.
	 * @param range the range of messages ids to search within. Must not be {@literal null}.
	 * @param count limit the number of results. Must not be {@literal null}.
	 * @return pending messages for the given {@literal consumer group} or {@literal null} when used in pipeline /
	 *         transaction.
	 * @see <a href="https://valkey.io/commands/xpending">Valkey Documentation: xpending</a>
	 * @since 2.3
	 */
	@Nullable
	PendingMessages xPending(String key, String groupName, String consumerName,
			org.springframework.data.domain.Range<String> range, Long count);

	/**
	 * Obtain detailed information about pending {@link PendingMessage messages} for a given
	 * {@link org.springframework.data.domain.Range} within a {@literal consumer group}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param groupName the name of the {@literal consumer group}. Must not be {@literal null}.
	 * @param range the range of messages ids to search within. Must not be {@literal null}.
	 * @param count limit the number of results. Must not be {@literal null}.
	 * @return pending messages for the given {@literal consumer group} or {@literal null} when used in pipeline /
	 *         transaction.
	 * @see <a href="https://valkey.io/commands/xpending">Valkey Documentation: xpending</a>
	 * @since 2.3
	 */
	@Nullable
	PendingMessages xPending(String key, String groupName, org.springframework.data.domain.Range<String> range,
			Long count);

	/**
	 * Obtain detailed information about pending {@link PendingMessage messages} applying given {@link XPendingOptions
	 * options}.
	 *
	 * @param key the {@literal key} the stream is stored at. Must not be {@literal null}.
	 * @param groupName the name of the {@literal consumer group}. Must not be {@literal null}.
	 * @param options the options containing {@literal range}, {@literal consumer} and {@literal count}. Must not be
	 *          {@literal null}.
	 * @return pending messages matching given criteria or {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/xpending">Valkey Documentation: xpending</a>
	 * @since 2.3
	 */
	@Nullable
	PendingMessages xPending(String key, String groupName, XPendingOptions options);

	/**
	 * Read records from a stream within a specific {@link Range}.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xrange">Valkey Documentation: XRANGE</a>
	 */
	@Nullable
	default List<StringRecord> xRange(String key, org.springframework.data.domain.Range<String> range) {
		return xRange(key, range, io.valkey.springframework.data.valkey.connection.Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a
	 * {@link io.valkey.springframework.data.valkey.connection.Limit}.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xrange">Valkey Documentation: XRANGE</a>
	 */
	@Nullable
	List<StringRecord> xRange(String key, org.springframework.data.domain.Range<String> range,
			io.valkey.springframework.data.valkey.connection.Limit limit);

	/**
	 * Read records from one or more {@link StreamOffset}s.
	 *
	 * @param stream the streams to read from.
	 * @return list ith members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xread">Valkey Documentation: XREAD</a>
	 */
	@Nullable
	default List<StringRecord> xReadAsString(StreamOffset<String> stream) {
		return xReadAsString(StreamReadOptions.empty(), new StreamOffset[] { stream });
	}

	/**
	 * Read records from one or more {@link StreamOffset}s.
	 *
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xread">Valkey Documentation: XREAD</a>
	 */
	@Nullable
	default List<StringRecord> xReadAsString(StreamOffset<String>... streams) {
		return xReadAsString(StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s.
	 *
	 * @param readOptions read arguments.
	 * @param stream the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xread">Valkey Documentation: XREAD</a>
	 */
	@Nullable
	default List<StringRecord> xReadAsString(StreamReadOptions readOptions, StreamOffset<String> stream) {
		return xReadAsString(readOptions, new StreamOffset[] { stream });
	}

	/**
	 * Read records from one or more {@link StreamOffset}s.
	 *
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xread">Valkey Documentation: XREAD</a>
	 */
	@Nullable
	List<StringRecord> xReadAsString(StreamReadOptions readOptions, StreamOffset<String>... streams);

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group.
	 *
	 * @param consumer consumer/group.
	 * @param stream the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xreadgroup">Valkey Documentation: XREADGROUP</a>
	 */
	@Nullable
	default List<StringRecord> xReadGroupAsString(Consumer consumer, StreamOffset<String> stream) {
		return xReadGroupAsString(consumer, StreamReadOptions.empty(), new StreamOffset[] { stream });
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group.
	 *
	 * @param consumer consumer/group.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xreadgroup">Valkey Documentation: XREADGROUP</a>
	 */
	@Nullable
	default List<StringRecord> xReadGroupAsString(Consumer consumer, StreamOffset<String>... streams) {
		return xReadGroupAsString(consumer, StreamReadOptions.empty(), streams);
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group.
	 *
	 * @param consumer consumer/group.
	 * @param readOptions read arguments.
	 * @param stream the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xreadgroup">Valkey Documentation: XREADGROUP</a>
	 */
	@Nullable
	default List<StringRecord> xReadGroupAsString(Consumer consumer, StreamReadOptions readOptions,
			StreamOffset<String> stream) {
		return xReadGroupAsString(consumer, readOptions, new StreamOffset[] { stream });
	}

	/**
	 * Read records from one or more {@link StreamOffset}s using a consumer group.
	 *
	 * @param consumer consumer/group.
	 * @param readOptions read arguments.
	 * @param streams the streams to read from.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xreadgroup">Valkey Documentation: XREADGROUP</a>
	 */
	@Nullable
	List<StringRecord> xReadGroupAsString(Consumer consumer, StreamReadOptions readOptions,
			StreamOffset<String>... streams);

	/**
	 * Read records from a stream within a specific {@link Range} in reverse order.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xrevrange">Valkey Documentation: XREVRANGE</a>
	 */
	@Nullable
	default List<StringRecord> xRevRange(String key, org.springframework.data.domain.Range<String> range) {
		return xRevRange(key, range, Limit.unlimited());
	}

	/**
	 * Read records from a stream within a specific {@link Range} applying a
	 * {@link io.valkey.springframework.data.valkey.connection.Limit} in reverse order.
	 *
	 * @param key the stream key.
	 * @param range must not be {@literal null}.
	 * @param limit must not be {@literal null}.
	 * @return list with members of the resulting stream. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xrevrange">Valkey Documentation: XREVRANGE</a>
	 */
	@Nullable
	List<StringRecord> xRevRange(String key, org.springframework.data.domain.Range<String> range,
			io.valkey.springframework.data.valkey.connection.Limit limit);

	/**
	 * Trims the stream to {@code count} elements.
	 *
	 * @param key the stream key.
	 * @param count length of the stream.
	 * @return number of removed entries. {@literal null} when used in pipeline / transaction.
	 * @since 2.2
	 * @see <a href="https://valkey.io/commands/xtrim">Valkey Documentation: XTRIM</a>
	 */
	@Nullable
	Long xTrim(String key, long count);

	/**
	 * Trims the stream to {@code count} elements.
	 *
	 * @param key the stream key.
	 * @param count length of the stream.
	 * @param approximateTrimming the trimming must be performed in a approximated way in order to maximize performances.
	 * @return number of removed entries. {@literal null} when used in pipeline / transaction.
	 * @since 2.4
	 * @see <a href="https://valkey.io/commands/xtrim">Valkey Documentation: XTRIM</a>
	 */
	@Nullable
	Long xTrim(String key, long count, boolean approximateTrimming);
}
