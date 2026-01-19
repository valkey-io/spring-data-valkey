/*
 * Copyright 2017-2025 the original author or authors.
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;

import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.ReactiveSubscription.Message;
import io.valkey.springframework.data.valkey.core.script.ValkeyScript;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import io.valkey.springframework.data.valkey.hash.HashMapper;
import io.valkey.springframework.data.valkey.listener.ChannelTopic;
import io.valkey.springframework.data.valkey.listener.PatternTopic;
import io.valkey.springframework.data.valkey.listener.Topic;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementWriter;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import org.springframework.util.Assert;

/**
 * Interface that specified a basic set of Valkey operations, implemented by {@link ReactiveValkeyTemplate}. Not often
 * used but a useful option for extensibility and testability (as it can be easily mocked or stubbed).
 * <p>
 * Streams of methods returning {@code Mono<K>} or {@code Flux<M>} are terminated with
 * {@link org.springframework.dao.InvalidDataAccessApiUsageException} when
 * {@link io.valkey.springframework.data.valkey.serializer.ValkeyElementReader#read(ByteBuffer)} returns {@literal null} for a
 * particular element as Reactive Streams prohibit the usage of {@literal null} values.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Dahye Anne Lee
 * @since 2.0
 */
public interface ReactiveValkeyOperations<K, V> {

	/**
	 * Executes the given action within a Valkey connection. Application exceptions thrown by the action object get
	 * propagated to the caller (can only be unchecked) whenever possible. Valkey exceptions are transformed into
	 * appropriate DAO ones. Allows for returning a result object, that is a domain object or a collection of domain
	 * objects. Performs automatic serialization/deserialization for the given objects to and from binary data suitable
	 * for the Valkey storage. Note: Callback code is not supposed to handle transactions itself! Use an appropriate
	 * transaction manager. Generally, callback code must not touch any Connection lifecycle methods, like close, to let
	 * the template do its work.
	 *
	 * @param <T> return type
	 * @param action callback object that specifies the Valkey action
	 * @return a result object returned by the action or {@link Flux#empty()}.
	 */
	<T> Flux<T> execute(ReactiveValkeyCallback<T> action);

	/**
	 * Executes the given action within a Valkey session using the same
	 * {@link io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection}. Application exceptions thrown by the
	 * action object get propagated to the caller (can only be unchecked) whenever possible. Valkey exceptions are
	 * transformed into appropriate DAO ones. Allows for returning a result object, that is a domain object or a
	 * collection of domain objects. Performs automatic serialization/deserialization for the given objects to and from
	 * binary data suitable for the Valkey storage. Note: Callback code is not supposed to handle transactions itself! Use
	 * an appropriate transaction manager. Generally, callback code must not touch any Connection lifecycle methods, like
	 * close, to let the template do its work.
	 *
	 * @param <T> return type
	 * @param action callback object that specifies the Valkey action
	 * @return a result object returned by the action or {@link Flux#empty()}.
	 * @since 2.6
	 */
	<T> Flux<T> executeInSession(ReactiveValkeySessionCallback<K, V, T> action);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Pub/Sub
	// -------------------------------------------------------------------------

	/**
	 * Publishes the given message to the given channel.
	 *
	 * @param destination the channel to publish to, must not be {@literal null} nor empty.
	 * @param message message to publish. Must not be {@literal null}.
	 * @return the number of clients that received the message
	 * @since 2.1
	 * @see <a href="https://valkey.io/commands/publish">Valkey Documentation: PUBLISH</a>
	 */
	Mono<Long> convertAndSend(String destination, V message);

	/**
	 * Subscribe to the given Valkey {@code channels} and emit {@link Message messages} received for those.
	 * <p>
	 * Note that this method allocates a new
	 * {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} and uses a dedicated
	 * connection, similar to other methods on this interface. Invoking this method multiple times is an indication that
	 * you should use {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} directly.
	 *
	 * @param channels must not be {@literal null}.
	 * @return a hot sequence of {@link Message messages}.
	 * @since 2.1
	 * @see io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer
	 */
	default Flux<? extends Message<String, V>> listenToChannel(String... channels) {

		Assert.notNull(channels, "Channels must not be null");

		return listenTo(Arrays.stream(channels).map(ChannelTopic::of).toArray(ChannelTopic[]::new));
	}

	/**
	 * Subscribe to the Valkey channels matching the given {@code pattern} and emit {@link Message messages} received for
	 * those.
	 * <p>
	 * Note that this method allocates a new
	 * {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} and uses a dedicated
	 * connection, similar to other methods on this interface. Invoking this method multiple times is an indication that
	 * you should use {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} directly.
	 *
	 * @param patterns must not be {@literal null}.
	 * @return a hot sequence of {@link Message messages}.
	 * @since 2.1
	 * @see io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer
	 */
	default Flux<? extends Message<String, V>> listenToPattern(String... patterns) {

		Assert.notNull(patterns, "Patterns must not be null");
		return listenTo(Arrays.stream(patterns).map(PatternTopic::of).toArray(PatternTopic[]::new));
	}

	/**
	 * Subscribe to the Valkey channels for the given {@link Topic topics} and emit {@link Message messages} received for
	 * those.
	 * <p>
	 * Note that this method allocates a new
	 * {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} and uses a dedicated
	 * connection, similar to other methods on this interface. Invoking this method multiple times is an indication that
	 * you should use {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} directly.
	 *
	 * @param topics must not be {@literal null}.
	 * @return a hot sequence of {@link Message messages}.
	 * @since 2.1
	 * @see io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer
	 */
	Flux<? extends Message<String, V>> listenTo(Topic... topics);

	/**
	 * Subscribe to the given Valkey {@code channels} and emit {@link Message messages} received for those. The
	 * {@link Mono} completes once the {@link Topic topic} subscriptions are registered.
	 * <p>
	 * Note that this method allocates a new
	 * {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} and uses a dedicated
	 * connection, similar to other methods on this interface. Invoking this method multiple times is an indication that
	 * you should use {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} directly.
	 *
	 * @param channels must not be {@literal null}.
	 * @return a hot sequence of {@link Message messages}.
	 * @since 2.6
	 * @see io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer#receiveLater(ChannelTopic...)
	 */
	default Mono<Flux<? extends Message<String, V>>> listenToChannelLater(String... channels) {

		Assert.notNull(channels, "Channels must not be null");

		return listenToLater(Arrays.stream(channels).map(ChannelTopic::of).toArray(ChannelTopic[]::new));
	}

	/**
	 * Subscribe to the Valkey channels matching the given {@code pattern} and emit {@link Message messages} received for
	 * those. The {@link Mono} completes once the {@link Topic topic} subscriptions are registered.
	 * <p>
	 * Note that this method allocates a new
	 * {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} and uses a dedicated
	 * connection, similar to other methods on this interface. Invoking this method multiple times is an indication that
	 * you should use {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} directly.
	 *
	 * @param patterns must not be {@literal null}.
	 * @return a hot sequence of {@link Message messages}.
	 * @since 2.6
	 * @see io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer#receiveLater(PatternTopic...)
	 */
	default Mono<Flux<? extends Message<String, V>>> listenToPatternLater(String... patterns) {

		Assert.notNull(patterns, "Patterns must not be null");
		return listenToLater(Arrays.stream(patterns).map(PatternTopic::of).toArray(PatternTopic[]::new));
	}

	/**
	 * Subscribe to the Valkey channels for the given {@link Topic topics} and emit {@link Message messages} received for
	 * those. The {@link Mono} completes once the {@link Topic topic} subscriptions are registered.
	 * <p>
	 * Note that this method allocates a new
	 * {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} and uses a dedicated
	 * connection, similar to other methods on this interface. Invoking this method multiple times is an indication that
	 * you should use {@link io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer} directly.
	 *
	 * @param topics must not be {@literal null}.
	 * @return a hot sequence of {@link Message messages}.
	 * @since 2.6
	 * @see io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer#receiveLater(Iterable,
	 *      ValkeySerializationContext.SerializationPair, ValkeySerializationContext.SerializationPair)
	 */
	Mono<Flux<? extends Message<String, V>>> listenToLater(Topic... topics);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Keys
	// -------------------------------------------------------------------------

	/**
	 * Copy given {@code sourceKey} to {@code targetKey}.
	 *
	 * @param sourceKey must not be {@literal null}.
	 * @param targetKey must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/copy">Valkey Documentation: COPY</a>
	 * @since 2.6
	 */
	Mono<Boolean> copy(K sourceKey, K targetKey, boolean replace);

	/**
	 * Determine if given {@code key} exists.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/exists">Valkey Documentation: EXISTS</a>
	 */
	Mono<Boolean> hasKey(K key);

	/**
	 * Get the number of given {@code keys} that exists.
	 *
	 * @param keys must not be {@literal null} or {@literal empty}.
	 * @return the number of existing keys in valkey. 0 if there are no existing keys.
	 * @see <a href="https://valkey.io/docs/commands/exists/">Valkey Documentation: EXISTS</a>
	 * @since 3.5
	 */
	Mono<Long> countExistingKeys(Collection<K> keys);

	/**
	 * Determine the type stored at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/type">Valkey Documentation: TYPE</a>
	 */
	Mono<DataType> type(K key);

	/**
	 * Find all keys matching the given {@code pattern}. <br />
	 * <strong>IMPORTANT:</strong> It is recommended to use {@link #scan()} to iterate over the keyspace as
	 * {@link #keys(Object)} is a non-interruptible and expensive Valkey operation.
	 *
	 * @param pattern must not be {@literal null}.
	 * @return the {@link Flux} emitting matching keys one by one.
	 * @throws IllegalArgumentException in case the pattern is {@literal null}.
	 * @see <a href="https://valkey.io/commands/keys">Valkey Documentation: KEYS</a>
	 */
	Flux<K> keys(K pattern);

	/**
	 * Use a {@link Flux} to iterate over keys. The resulting {@link Flux} acts as a cursor and issues {@code SCAN}
	 * commands itself as long as the subscriber signals demand.
	 *
	 * @return the {@link Flux} emitting the {@literal keys} one by one or an {@link Flux#empty() empty flux} if none
	 *         exist.
	 * @see <a href="https://valkey.io/commands/scan">Valkey Documentation: SCAN</a>
	 * @since 2.1
	 */
	default Flux<K> scan() {
		return scan(ScanOptions.NONE);
	}

	/**
	 * Use a {@link Flux} to iterate over keys. The resulting {@link Flux} acts as a cursor and issues {@code SCAN}
	 * commands itself as long as the subscriber signals demand.
	 *
	 * @param options must not be {@literal null}. Use {@link ScanOptions#NONE} instead.
	 * @return the {@link Flux} emitting the {@literal keys} one by one or an {@link Flux#empty() empty flux} if none
	 *         exist.
	 * @throws IllegalArgumentException when the given {@code options} is {@literal null}.
	 * @see <a href="https://valkey.io/commands/scan">Valkey Documentation: SCAN</a>
	 * @since 2.1
	 */
	Flux<K> scan(ScanOptions options);

	/**
	 * Return a random key from the keyspace.
	 *
	 * @return
	 * @see <a href="https://valkey.io/commands/randomkey">Valkey Documentation: RANDOMKEY</a>
	 */
	Mono<K> randomKey();

	/**
	 * Rename key {@code oldKey} to {@code newKey}.
	 *
	 * @param oldKey must not be {@literal null}.
	 * @param newKey must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/rename">Valkey Documentation: RENAME</a>
	 */
	Mono<Boolean> rename(K oldKey, K newKey);

	/**
	 * Rename key {@code oldKey} to {@code newKey} only if {@code newKey} does not exist.
	 *
	 * @param oldKey must not be {@literal null}.
	 * @param newKey must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/renamenx">Valkey Documentation: RENAMENX</a>
	 */
	Mono<Boolean> renameIfAbsent(K oldKey, K newKey);

	/**
	 * Delete given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return The number of keys that were removed.
	 * @see <a href="https://valkey.io/commands/del">Valkey Documentation: DEL</a>
	 */
	Mono<Long> delete(K... key);

	/**
	 * Delete given {@code keys}. This command buffers keys received from {@link Publisher} into chunks of 128 keys to
	 * delete to reduce the number of issued {@code DEL} commands.
	 *
	 * @param keys must not be {@literal null}.
	 * @return The number of keys that were removed.
	 * @see <a href="https://valkey.io/commands/del">Valkey Documentation: DEL</a>
	 */
	Mono<Long> delete(Publisher<K> keys);

	/**
	 * Unlink the {@code key} from the keyspace. Unlike with {@link #delete(Object[])} the actual memory reclaiming here
	 * happens asynchronously.
	 *
	 * @param key must not be {@literal null}.
	 * @return The number of keys that were removed. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/unlink">Valkey Documentation: UNLINK</a>
	 * @since 2.1
	 */
	Mono<Long> unlink(K... key);

	/**
	 * Unlink the {@code keys} from the keyspace. Unlike with {@link #delete(Publisher)} the actual memory reclaiming here
	 * happens asynchronously. This command buffers keys received from {@link Publisher} into chunks of 128 keys to delete
	 * to reduce the number of issued {@code UNLINK} commands.
	 *
	 * @param keys must not be {@literal null}.
	 * @return The number of keys that were removed. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/unlink">Valkey Documentation: UNLINK</a>
	 * @since 2.1
	 */
	Mono<Long> unlink(Publisher<K> keys);

	/**
	 * Set time to live for given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param timeout must not be {@literal null}.
	 * @return
	 */
	Mono<Boolean> expire(K key, Duration timeout);

	/**
	 * Set the expiration for given {@code key} as a {@literal expireAt} timestamp.
	 *
	 * @param key must not be {@literal null}.
	 * @param expireAt must not be {@literal null}.
	 * @return
	 */
	Mono<Boolean> expireAt(K key, Instant expireAt);

	/**
	 * Set the expiration for given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param expiration must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @throws IllegalArgumentException any of required arguments is {@literal null}.
	 * @see <a href="https://valkey.io/commands/expire">Valkey Documentation: EXPIRE</a>
	 * @see <a href="https://valkey.io/commands/pexpire">Valkey Documentation: PEXPIRE</a>
	 * @see <a href="https://valkey.io/commands/expireat">Valkey Documentation: EXPIREAT</a>
	 * @see <a href="https://valkey.io/commands/pexpireat">Valkey Documentation: PEXPIREAT</a>
	 * @see <a href="https://valkey.io/commands/persist">Valkey Documentation: PERSIST</a>
	 * @since 3.5
	 */
	Mono<ExpireChanges.ExpiryChangeState> expire(K key, Expiration expiration, ExpirationOptions options);

	/**
	 * Remove the expiration from given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/persist">Valkey Documentation: PERSIST</a>
	 */
	Mono<Boolean> persist(K key);

	/**
	 * Move given {@code key} to database with {@code index}.
	 *
	 * @param key must not be {@literal null}.
	 * @param dbIndex
	 * @return
	 * @see <a href="https://valkey.io/commands/move">Valkey Documentation: MOVE</a>
	 */
	Mono<Boolean> move(K key, int dbIndex);

	/**
	 * Get the time to live for {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return the {@link Duration} of the associated key. {@link Duration#ZERO} if no timeout associated or empty
	 *         {@link Mono} if the key does not exist.
	 * @see <a href="https://valkey.io/commands/pttl">Valkey Documentation: PTTL</a>
	 */
	Mono<Duration> getExpire(K key);

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Lua scripts
	// -------------------------------------------------------------------------

	/**
	 * Executes the given {@link ValkeyScript}.
	 *
	 * @param script must not be {@literal null}.
	 * @return result value of the script {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is {@literal null},
	 *         likely indicating a throw-away status reply (i.e. "OK").
	 */
	default <T> Flux<T> execute(ValkeyScript<T> script) {
		return execute(script, Collections.emptyList());
	}

	/**
	 * Executes the given {@link ValkeyScript}.
	 *
	 * @param script must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return result value of the script {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is {@literal null},
	 *         likely indicating a throw-away status reply (i.e. "OK").
	 */
	default <T> Flux<T> execute(ValkeyScript<T> script, List<K> keys) {
		return execute(script, keys, Collections.emptyList());
	}

	/**
	 * Executes the given {@link ValkeyScript}
	 *
	 * @param script The script to execute. Must not be {@literal null}.
	 * @param keys keys that need to be passed to the script. Must not be {@literal null}.
	 * @param args args that need to be passed to the script. Must not be {@literal null}.
	 * @return result value of the script {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is {@literal null},
	 *         likely indicating a throw-away status reply (i.e. "OK").
	 * @since 3.4
	 */
	default <T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, Object... args) {
		return execute(script, keys, Arrays.asList(args));
	}

	/**
	 * Executes the given {@link ValkeyScript}
	 *
	 * @param script The script to execute. Must not be {@literal null}.
	 * @param keys keys that need to be passed to the script. Must not be {@literal null}.
	 * @param args args that need to be passed to the script. Must not be {@literal null}.
	 * @return result value of the script {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is {@literal null},
	 *         likely indicating a throw-away status reply (i.e. "OK").
	 */
	<T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, List<?> args);

	/**
	 * Executes the given {@link ValkeyScript}, using the provided {@link ValkeySerializer}s to serialize the script
	 * arguments and result.
	 *
	 * @param script The script to execute
	 * @param argsWriter The {@link ValkeyElementWriter} to use for serializing args
	 * @param resultReader The {@link ValkeyElementReader} to use for serializing the script return value
	 * @param keys keys that need to be passed to the script.
	 * @param args args that need to be passed to the script.
	 * @return result value of the script {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is {@literal null},
	 *         likely indicating a throw-away status reply (i.e. "OK").
	 */
	<T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, List<?> args, ValkeyElementWriter<?> argsWriter,
			ValkeyElementReader<T> resultReader);

	// -------------------------------------------------------------------------
	// Methods to obtain specific operations interface objects.
	// -------------------------------------------------------------------------

	// operation types

	/**
	 * Returns geospatial specific operations interface.
	 *
	 * @return geospatial specific operations.
	 */
	ReactiveGeoOperations<K, V> opsForGeo();

	/**
	 * Returns geospatial specific operations interface.
	 *
	 * @param serializationContext serializers to be used with the returned operations, must not be {@literal null}.
	 * @return geospatial specific operations.
	 */
	<K, V> ReactiveGeoOperations<K, V> opsForGeo(ValkeySerializationContext<K, V> serializationContext);

	/**
	 * Returns the operations performed on hash values.
	 *
	 * @param <HK> hash key (or field) type.
	 * @param <HV> hash value type.
	 * @return hash operations.
	 */
	<HK, HV> ReactiveHashOperations<K, HK, HV> opsForHash();

	/**
	 * Returns the operations performed on hash values given a {@link ValkeySerializationContext}.
	 *
	 * @param serializationContext serializers to be used with the returned operations, must not be {@literal null}.
	 * @param <HK> hash key (or field) type.
	 * @param <HV> hash value type.
	 * @return hash operations.
	 */
	<K, HK, HV> ReactiveHashOperations<K, HK, HV> opsForHash(ValkeySerializationContext<K, ?> serializationContext);

	/**
	 * Returns the operations performed on multisets using HyperLogLog.
	 *
	 * @return never {@literal null}.
	 */
	ReactiveHyperLogLogOperations<K, V> opsForHyperLogLog();

	/**
	 * Returns the operations performed on multisets using HyperLogLog given a {@link ValkeySerializationContext}.
	 *
	 * @param serializationContext serializers to be used with the returned operations, must not be {@literal null}.
	 * @return never {@literal null}.
	 */
	<K, V> ReactiveHyperLogLogOperations<K, V> opsForHyperLogLog(ValkeySerializationContext<K, V> serializationContext);

	/**
	 * Returns the operations performed on list values.
	 *
	 * @return list operations.
	 */
	ReactiveListOperations<K, V> opsForList();

	/**
	 * Returns the operations performed on list values given a {@link ValkeySerializationContext}.
	 *
	 * @param serializationContext serializers to be used with the returned operations, must not be {@literal null}.
	 * @return list operations.
	 */
	<K, V> ReactiveListOperations<K, V> opsForList(ValkeySerializationContext<K, V> serializationContext);

	/**
	 * Returns the operations performed on set values.
	 *
	 * @return set operations.
	 */
	ReactiveSetOperations<K, V> opsForSet();

	/**
	 * Returns the operations performed on set values given a {@link ValkeySerializationContext}.
	 *
	 * @param serializationContext serializers to be used with the returned operations, must not be {@literal null}.
	 * @return set operations.
	 */
	<K, V> ReactiveSetOperations<K, V> opsForSet(ValkeySerializationContext<K, V> serializationContext);

	/**
	 * Returns the operations performed on streams.
	 *
	 * @return stream operations.
	 * @since 2.2
	 */
	<HK, HV> ReactiveStreamOperations<K, HK, HV> opsForStream();

	/**
	 * Returns the operations performed on streams.
	 *
	 * @param hashMapper the {@link HashMapper} to use when mapping complex objects.
	 * @return stream operations.
	 * @since 2.2
	 */
	<HK, HV> ReactiveStreamOperations<K, HK, HV> opsForStream(HashMapper<? super K, ? super HK, ? super HV> hashMapper);

	/**
	 * Returns the operations performed on streams given a {@link ValkeySerializationContext}.
	 *
	 * @param serializationContext serializers to be used with the returned operations, must not be {@literal null}.
	 * @return stream operations.
	 * @since 2.2
	 */
	<HK, HV> ReactiveStreamOperations<K, HK, HV> opsForStream(ValkeySerializationContext<K, ?> serializationContext);

	/**
	 * Returns the operations performed on simple values (or Strings in Valkey terminology).
	 *
	 * @return value operations
	 */
	ReactiveValueOperations<K, V> opsForValue();

	/**
	 * Returns the operations performed on simple values (or Strings in Valkey terminology) given a
	 * {@link ValkeySerializationContext}.
	 *
	 * @param serializationContext serializers to be used with the returned operations, must not be {@literal null}.
	 * @return value operations.
	 */
	<K, V> ReactiveValueOperations<K, V> opsForValue(ValkeySerializationContext<K, V> serializationContext);

	/**
	 * Returns the operations performed on zset values (also known as sorted sets).
	 *
	 * @return zset operations.
	 */
	ReactiveZSetOperations<K, V> opsForZSet();

	/**
	 * Returns the operations performed on zset values (also known as sorted sets) given a
	 * {@link ValkeySerializationContext}.
	 *
	 * @param serializationContext serializers to be used with the returned operations, must not be {@literal null}.
	 * @return zset operations.
	 */
	<K, V> ReactiveZSetOperations<K, V> opsForZSet(ValkeySerializationContext<K, V> serializationContext);

	/**
	 * @return the {@link ValkeySerializationContext}.
	 */
	ValkeySerializationContext<K, V> getSerializationContext();
}
