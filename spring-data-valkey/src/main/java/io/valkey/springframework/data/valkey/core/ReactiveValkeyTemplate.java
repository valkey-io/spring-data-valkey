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

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.ReactiveKeyCommands;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection.CommandResponse;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ReactiveSubscription.Message;
import io.valkey.springframework.data.valkey.core.script.DefaultReactiveScriptExecutor;
import io.valkey.springframework.data.valkey.core.script.ReactiveScriptExecutor;
import io.valkey.springframework.data.valkey.core.script.ValkeyScript;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import io.valkey.springframework.data.valkey.hash.HashMapper;
import io.valkey.springframework.data.valkey.hash.ObjectHashMapper;
import io.valkey.springframework.data.valkey.listener.ReactiveValkeyMessageListenerContainer;
import io.valkey.springframework.data.valkey.listener.Topic;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementWriter;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Central abstraction for reactive Valkey data access implementing {@link ReactiveValkeyOperations}.
 * <p>
 * Performs automatic serialization/deserialization between the given objects and the underlying binary data in the
 * Valkey store.
 * <p>
 * Note that while the template is generified, it is up to the serializers/deserializers to properly convert the given
 * Objects to and from binary data.
 * <p>
 * Streams of methods returning {@code Mono<K>} or {@code Flux<M>} are terminated with
 * {@link org.springframework.dao.InvalidDataAccessApiUsageException} when
 * {@link io.valkey.springframework.data.valkey.serializer.ValkeyElementReader#read(ByteBuffer)} returns {@literal null} for a
 * particular element as Reactive Streams prohibit the usage of {@literal null} values.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Petromir Dzhunev
 * @author John Blum
 * @author Dahye Anne Lee
 * @param <K> the Valkey key type against which the template works (usually a String)
 * @param <V> the Valkey value type against which the template works
 * @since 2.0
 */
public class ReactiveValkeyTemplate<K, V> implements ReactiveValkeyOperations<K, V> {

	private final ReactiveValkeyConnectionFactory connectionFactory;
	private final ValkeySerializationContext<K, V> serializationContext;
	private final boolean exposeConnection;
	private final ReactiveScriptExecutor<K> reactiveScriptExecutor;
	private final ReactiveGeoOperations<K, V> geoOps;
	private final ReactiveHashOperations<K, ?, ?> hashOps;
	private final ReactiveHyperLogLogOperations<K, V> hllOps;
	private final ReactiveListOperations<K, V> listOps;
	private final ReactiveSetOperations<K, V> setOps;
	private final ReactiveStreamOperations<K, ?, ?> streamOps;
	private final ReactiveValueOperations<K, V> valueOps;
	private final ReactiveZSetOperations<K, V> zsetOps;

	/**
	 * Creates new {@link ReactiveValkeyTemplate} using given {@link ReactiveValkeyConnectionFactory} and
	 * {@link ValkeySerializationContext}.
	 *
	 * @param connectionFactory must not be {@literal null}.
	 * @param serializationContext must not be {@literal null}.
	 */
	public ReactiveValkeyTemplate(ReactiveValkeyConnectionFactory connectionFactory,
			ValkeySerializationContext<K, V> serializationContext) {
		this(connectionFactory, serializationContext, false);
	}

	/**
	 * Creates new {@link ReactiveValkeyTemplate} using given {@link ReactiveValkeyConnectionFactory} and
	 * {@link ValkeySerializationContext}.
	 *
	 * @param connectionFactory must not be {@literal null}.
	 * @param serializationContext must not be {@literal null}.
	 * @param exposeConnection flag indicating to expose the connection used.
	 */
	public ReactiveValkeyTemplate(ReactiveValkeyConnectionFactory connectionFactory,
			ValkeySerializationContext<K, V> serializationContext, boolean exposeConnection) {

		Assert.notNull(connectionFactory, "ConnectionFactory must not be null");
		Assert.notNull(serializationContext, "SerializationContext must not be null");

		this.connectionFactory = connectionFactory;
		this.serializationContext = serializationContext;
		this.exposeConnection = exposeConnection;
		this.reactiveScriptExecutor = new DefaultReactiveScriptExecutor<>(connectionFactory, serializationContext);

		this.geoOps = opsForGeo(serializationContext);
		this.hashOps = opsForHash(serializationContext);
		this.hllOps = opsForHyperLogLog(serializationContext);
		this.listOps = opsForList(serializationContext);
		this.setOps = opsForSet(serializationContext);
		this.streamOps = opsForStream(serializationContext);
		this.valueOps = opsForValue(serializationContext);
		this.zsetOps = opsForZSet(serializationContext);
	}

	/**
	 * Returns the connectionFactory.
	 *
	 * @return Returns the connectionFactory
	 */
	public ReactiveValkeyConnectionFactory getConnectionFactory() {
		return connectionFactory;
	}

	// -------------------------------------------------------------------------
	// Execution methods
	// -------------------------------------------------------------------------

	@Override
	public <T> Flux<T> execute(ReactiveValkeyCallback<T> action) {
		return execute(action, exposeConnection);
	}

	/**
	 * Executes the given action object within a connection that can be exposed or not. Additionally, the connection can
	 * be pipelined. Note the results of the pipeline are discarded (making it suitable for write-only scenarios).
	 *
	 * @param <T> return type
	 * @param action callback object to execute
	 * @param exposeConnection whether to enforce exposure of the native Valkey Connection to callback code
	 * @return object returned by the action
	 */
	public <T> Flux<T> execute(ReactiveValkeyCallback<T> action, boolean exposeConnection) {

		Assert.notNull(action, "Callback object must not be null");
		return Flux.from(doInConnection(action, exposeConnection));
	}

	@Override
	public <T> Flux<T> executeInSession(ReactiveValkeySessionCallback<K, V, T> action) {

		Assert.notNull(action, "Callback object must not be null");
		return Flux
				.from(doInConnection(connection -> action.doWithOperations(withConnection(connection)), exposeConnection));
	}

	/**
	 * Create a reusable Flux for a {@link ReactiveValkeyCallback}. Callback is executed within a connection context. The
	 * connection is released outside the callback.
	 *
	 * @param callback must not be {@literal null}
	 * @return a {@link Flux} wrapping the {@link ReactiveValkeyCallback}.
	 */
	public <T> Flux<T> createFlux(ReactiveValkeyCallback<T> callback) {

		Assert.notNull(callback, "ReactiveValkeyCallback must not be null");

		return Flux.from(doInConnection(callback, exposeConnection));
	}

	/**
	 * Internal variant of {@link #createFlux(ReactiveValkeyCallback)} bypassing proxy creation. Create a reusable Flux for
	 * a {@link ReactiveValkeyCallback}. Callback is executed within a connection context. The connection is released
	 * outside the callback.
	 *
	 * @param callback must not be {@literal null}
	 * @return a {@link Flux} wrapping the {@link ReactiveValkeyCallback}.
	 * @since 2.6
	 */
	<T> Flux<T> doCreateFlux(ReactiveValkeyCallback<T> callback) {

		Assert.notNull(callback, "ReactiveValkeyCallback must not be null");

		return Flux.from(doInConnection(callback, true));
	}

	/**
	 * Create a reusable Mono for a {@link ReactiveValkeyCallback}. Callback is executed within a connection context. The
	 * connection is released outside the callback.
	 *
	 * @param callback must not be {@literal null}
	 * @return a {@link Mono} wrapping the {@link ReactiveValkeyCallback}.
	 */
	public <T> Mono<T> createMono(ReactiveValkeyCallback<T> callback) {

		Assert.notNull(callback, "ReactiveValkeyCallback must not be null");

		return Mono.from(doInConnection(callback, exposeConnection));
	}

	/**
	 * Internal variant of {@link #createMono(ReactiveValkeyCallback)} bypassing proxy creation. Create a reusable Mono for
	 * a {@link ReactiveValkeyCallback}. Callback is executed within a connection context. The connection is released
	 * outside the callback.
	 *
	 * @param callback must not be {@literal null}
	 * @return a {@link Mono} wrapping the {@link ReactiveValkeyCallback}.
	 * @since 2.6
	 */
	<T> Mono<T> doCreateMono(ReactiveValkeyCallback<T> callback) {

		Assert.notNull(callback, "ReactiveValkeyCallback must not be null");

		return Mono.from(doInConnection(callback, true));
	}

	/**
	 * Executes the given action object within a connection that can be exposed or not. Additionally, the connection can
	 * be pipelined. Note the results of the pipeline are discarded (making it suitable for write-only scenarios).
	 *
	 * @param <T> return type
	 * @param action callback object to execute
	 * @param exposeConnection whether to enforce exposure of the native Valkey Connection to callback code
	 * @return object returned by the action
	 */
	<T> Publisher<T> doInConnection(ReactiveValkeyCallback<T> action, boolean exposeConnection) {

		Assert.notNull(action, "Callback object must not be null");

		Mono<ReactiveValkeyConnection> connection = getConnection();

		if (!exposeConnection) {
			connection = connection.map(this::createValkeyConnectionProxy);
		}

		return Flux.usingWhen(connection, conn -> {

			Publisher<T> result = action.doInValkey(conn);

			return postProcessResult(result, conn, false);

		}, ReactiveValkeyConnection::closeLater);
	}

	/**
	 * Creates a {@link Mono} which emits a new {@link ReactiveValkeyConnection}. Can be overridden in subclasses to
	 * provide a different mechanism for connection allocation for the given method.
	 *
	 * @since 2.5.5
	 */
	protected Mono<ReactiveValkeyConnection> getConnection() {

		ReactiveValkeyConnectionFactory factory = getConnectionFactory();

		return Mono.fromSupplier(() -> preProcessConnection(factory.getReactiveConnection(), false));
	}

	@Override
	public Mono<Long> convertAndSend(String destination, V message) {

		Assert.hasText(destination, "Destination channel must not be empty");
		Assert.notNull(message, "Message must not be null");

		return doCreateMono(connection -> connection.pubSubCommands().publish(
				getSerializationContext().getStringSerializationPair().write(destination),
				getSerializationContext().getValueSerializationPair().write(message)));
	}

	@Override
	public Flux<? extends Message<String, V>> listenTo(Topic... topics) {

		ReactiveValkeyMessageListenerContainer container = new ReactiveValkeyMessageListenerContainer(getConnectionFactory());

		return container
				.receive(Arrays.asList(topics), getSerializationContext().getStringSerializationPair(),
						getSerializationContext().getValueSerializationPair()) //
				.doFinally((signalType) -> container.destroyLater().subscribe());
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Mono<Flux<? extends Message<String, V>>> listenToLater(Topic... topics) {

		ReactiveValkeyMessageListenerContainer container = new ReactiveValkeyMessageListenerContainer(getConnectionFactory());

		return (Mono) container.receiveLater(Arrays.asList(topics), getSerializationContext().getStringSerializationPair(),
						getSerializationContext().getValueSerializationPair()) //
				.map(it -> it.doFinally(signalType -> container.destroyLater().subscribe()))
				.doOnCancel(() -> container.destroyLater().subscribe());
	}

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey keys
	// -------------------------------------------------------------------------

	@Override
	public Mono<Boolean> copy(K sourceKey, K targetKey, boolean replace) {

		Assert.notNull(sourceKey, "Source key must not be null");
		Assert.notNull(targetKey, "Target key must not be null");

		return doCreateMono(connection -> connection.keyCommands().copy(rawKey(sourceKey), rawKey(targetKey), replace));
	}

	@Override
	public Mono<Boolean> hasKey(K key) {

		Assert.notNull(key, "Key must not be null");

		return doCreateMono(connection -> connection.keyCommands().exists(rawKey(key)));
	}

	@Override
	public Mono<Long> countExistingKeys(Collection<K> keys) {

		Assert.notNull(keys, "Keys must not be null");

		return doCreateMono(connection -> connection.keyCommands().exists(rawKeys(keys)));
	}

	@Override
	public Mono<DataType> type(K key) {

		Assert.notNull(key, "Key must not be null");

		return doCreateMono(connection -> connection.keyCommands().type(rawKey(key)));
	}

	@Override
	public Flux<K> keys(K pattern) {

		Assert.notNull(pattern, "Pattern must not be null");

		return doCreateFlux(connection -> connection.keyCommands().keys(rawKey(pattern))) //
				.flatMap(Flux::fromIterable) //
				.map(this::readRequiredKey);
	}

	@Override
	public Flux<K> scan(ScanOptions options) {

		Assert.notNull(options, "ScanOptions must not be null");

		return doCreateFlux(connection -> connection.keyCommands().scan(options)) //
				.map(this::readRequiredKey);
	}

	@Override
	public Mono<K> randomKey() {
		return doCreateMono(connection -> connection.keyCommands().randomKey()).map(this::readRequiredKey);
	}

	@Override
	public Mono<Boolean> rename(K oldKey, K newKey) {

		Assert.notNull(oldKey, "Old key must not be null");
		Assert.notNull(newKey, "New Key must not be null");

		return doCreateMono(connection -> connection.keyCommands().rename(rawKey(oldKey), rawKey(newKey)));
	}

	@Override
	public Mono<Boolean> renameIfAbsent(K oldKey, K newKey) {

		Assert.notNull(oldKey, "Old key must not be null");
		Assert.notNull(newKey, "New Key must not be null");

		return doCreateMono(connection -> connection.keyCommands().renameNX(rawKey(oldKey), rawKey(newKey)));
	}

	@Override
	@SafeVarargs
	public final Mono<Long> delete(K... keys) {

		Assert.notNull(keys, "Keys must not be null");
		Assert.notEmpty(keys, "Keys must not be empty");
		Assert.noNullElements(keys, "Keys must not contain null elements");

		if (keys.length == 1) {
			return doCreateMono(connection -> connection.keyCommands().del(rawKey(keys[0])));
		}

		Mono<List<ByteBuffer>> listOfKeys = Flux.fromArray(keys).map(this::rawKey).collectList();
		return doCreateMono(connection -> listOfKeys.flatMap(rawKeys -> connection.keyCommands().mDel(rawKeys)));
	}

	@Override
	public Mono<Long> delete(Publisher<K> keys) {

		Assert.notNull(keys, "Keys must not be null");

		return doCreateFlux(connection -> connection.keyCommands() //
				.mDel(Flux.from(keys).map(this::rawKey).buffer(128)) //
				.map(CommandResponse::getOutput)) //
						.collect(Collectors.summingLong(value -> value));
	}

	@Override
	@SafeVarargs
	public final Mono<Long> unlink(K... keys) {

		Assert.notNull(keys, "Keys must not be null");
		Assert.notEmpty(keys, "Keys must not be empty");
		Assert.noNullElements(keys, "Keys must not contain null elements");

		if (keys.length == 1) {
			return doCreateMono(connection -> connection.keyCommands().unlink(rawKey(keys[0])));
		}

		Mono<List<ByteBuffer>> listOfKeys = Flux.fromArray(keys).map(this::rawKey).collectList();
		return doCreateMono(connection -> listOfKeys.flatMap(rawKeys -> connection.keyCommands().mUnlink(rawKeys)));
	}

	@Override
	public Mono<Long> unlink(Publisher<K> keys) {

		Assert.notNull(keys, "Keys must not be null");

		return doCreateFlux(connection -> connection.keyCommands() //
				.mUnlink(Flux.from(keys).map(this::rawKey).buffer(128)) //
				.map(CommandResponse::getOutput)) //
						.collect(Collectors.summingLong(value -> value));
	}

	@Override
	public Mono<Boolean> expire(K key, Duration timeout) {

		Assert.notNull(key, "Key must not be null");
		Assert.notNull(timeout, "Timeout must not be null");

		if (timeout.getNano() == 0) {
			return doCreateMono(connection -> connection.keyCommands() //
					.expire(rawKey(key), timeout));
		}

		return doCreateMono(connection -> connection.keyCommands().pExpire(rawKey(key), timeout));
	}

	@Override
	public Mono<Boolean> expireAt(K key, Instant expireAt) {

		Assert.notNull(key, "Key must not be null");
		Assert.notNull(expireAt, "Expire at must not be null");

		if (expireAt.getNano() == 0) {
			return doCreateMono(connection -> connection.keyCommands() //
					.expireAt(rawKey(key), expireAt));
		}

		return doCreateMono(connection -> connection.keyCommands().pExpireAt(rawKey(key), expireAt));
	}

	@Override
	public Mono<ExpireChanges.ExpiryChangeState> expire(K key, Expiration expiration, ExpirationOptions options) {

		Assert.notNull(key, "Key must not be null");
		Assert.notNull(expiration, "Expiration at must not be null");
		Assert.notNull(options, "ExpirationOptions at must not be null");

		Mono<ReactiveKeyCommands.ExpireCommand> just = Mono
				.just(ReactiveKeyCommands.ExpireCommand.expire(rawKey(key), expiration).withOptions(options));
		return doCreateMono(connection -> connection.keyCommands().applyExpiration(just))
				.map(ReactiveValkeyConnection.BooleanResponse::getOutput).map(ExpireChanges.ExpiryChangeState::of);
	}

	@Override
	public Mono<Boolean> persist(K key) {

		Assert.notNull(key, "Key must not be null");

		return doCreateMono(connection -> connection.keyCommands().persist(rawKey(key)));
	}

	@Override
	public Mono<Duration> getExpire(K key) {

		Assert.notNull(key, "Key must not be null");

		return doCreateMono(connection -> connection.keyCommands().pTtl(rawKey(key)).flatMap(expiry -> {

			if (expiry == -1) {
				return Mono.just(Duration.ZERO);
			}

			if (expiry == -2) {
				return Mono.empty();
			}

			return Mono.just(Duration.ofMillis(expiry));
		}));
	}

	@Override
	public Mono<Boolean> move(K key, int dbIndex) {

		Assert.notNull(key, "Key must not be null");

		return doCreateMono(connection -> connection.keyCommands().move(rawKey(key), dbIndex));
	}

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Lua scripts
	// -------------------------------------------------------------------------

	@Override
	public <T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, List<?> args) {
		return reactiveScriptExecutor.execute(script, keys, args);
	}

	@Override
	public <T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, List<?> args, ValkeyElementWriter<?> argsWriter,
			ValkeyElementReader<T> resultReader) {
		return reactiveScriptExecutor.execute(script, keys, args, argsWriter, resultReader);
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	/**
	 * Processes the connection (before any settings are executed on it). Default implementation returns the connection as
	 * is.
	 *
	 * @param connection must not be {@literal null}.
	 * @param existingConnection
	 */
	protected ReactiveValkeyConnection preProcessConnection(ReactiveValkeyConnection connection,
			boolean existingConnection) {
		return connection;
	}

	/**
	 * Processes the result before returning the {@link Publisher}. Default implementation returns the result as is.
	 *
	 * @param result must not be {@literal null}.
	 * @param connection must not be {@literal null}.
	 * @param existingConnection
	 * @return
	 */
	protected <T> Publisher<T> postProcessResult(Publisher<T> result, ReactiveValkeyConnection connection,
			boolean existingConnection) {
		return result;
	}

	protected ReactiveValkeyConnection createValkeyConnectionProxy(ReactiveValkeyConnection reactiveValkeyConnection) {

		Class<?>[] ifcs = ClassUtils.getAllInterfacesForClass(reactiveValkeyConnection.getClass(),
				getClass().getClassLoader());
		return (ReactiveValkeyConnection) Proxy.newProxyInstance(reactiveValkeyConnection.getClass().getClassLoader(), ifcs,
				new CloseSuppressingInvocationHandler(reactiveValkeyConnection));
	}

	@Override
	public ReactiveGeoOperations<K, V> opsForGeo() {
		return geoOps;
	}

	@Override
	public <K1, V1> ReactiveGeoOperations<K1, V1> opsForGeo(ValkeySerializationContext<K1, V1> serializationContext) {
		return new DefaultReactiveGeoOperations<>(this, serializationContext);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <HK, HV> ReactiveHashOperations<K, HK, HV> opsForHash() {
		return (ReactiveHashOperations<K, HK, HV>) hashOps;
	}

	@Override
	public <K1, HK, HV> ReactiveHashOperations<K1, HK, HV> opsForHash(
			ValkeySerializationContext<K1, ?> serializationContext) {
		return new DefaultReactiveHashOperations<>(this, serializationContext);
	}

	@Override
	public ReactiveHyperLogLogOperations<K, V> opsForHyperLogLog() {
		return hllOps;
	}

	@Override
	public <K1, V1> ReactiveHyperLogLogOperations<K1, V1> opsForHyperLogLog(
			ValkeySerializationContext<K1, V1> serializationContext) {
		return new DefaultReactiveHyperLogLogOperations<>(this, serializationContext);
	}

	@Override
	public ReactiveListOperations<K, V> opsForList() {
		return listOps;
	}

	@Override
	public <K1, V1> ReactiveListOperations<K1, V1> opsForList(ValkeySerializationContext<K1, V1> serializationContext) {
		return new DefaultReactiveListOperations<>(this, serializationContext);
	}

	@Override
	public ReactiveSetOperations<K, V> opsForSet() {
		return setOps;
	}

	@Override
	public <K1, V1> ReactiveSetOperations<K1, V1> opsForSet(ValkeySerializationContext<K1, V1> serializationContext) {
		return new DefaultReactiveSetOperations<>(this, serializationContext);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <HK, HV> ReactiveStreamOperations<K, HK, HV> opsForStream() {
		return (ReactiveStreamOperations<K, HK, HV>) streamOps;
	}

	@Override
	public <HK, HV> ReactiveStreamOperations<K, HK, HV> opsForStream(
			HashMapper<? super K, ? super HK, ? super HV> hashMapper) {
		return opsForStream(serializationContext, hashMapper);
	}

	@SuppressWarnings("unchecked")
	public <HK, HV> ReactiveStreamOperations<K, HK, HV> opsForStream(
			ValkeySerializationContext<K, ?> serializationContext) {
		return opsForStream(serializationContext, (HashMapper) ObjectHashMapper.getSharedInstance());
	}

	protected <HK, HV> ReactiveStreamOperations<K, HK, HV> opsForStream(
			ValkeySerializationContext<K, ?> serializationContext,
			@Nullable HashMapper<? super K, ? super HK, ? super HV> hashMapper) {
		return new DefaultReactiveStreamOperations<>(this, serializationContext, hashMapper);
	}

	@Override
	public ReactiveValueOperations<K, V> opsForValue() {
		return valueOps;
	}

	@Override
	public <K1, V1> ReactiveValueOperations<K1, V1> opsForValue(ValkeySerializationContext<K1, V1> serializationContext) {
		return new DefaultReactiveValueOperations<>(this, serializationContext);
	}

	@Override
	public ReactiveZSetOperations<K, V> opsForZSet() {
		return zsetOps;
	}

	@Override
	public <K1, V1> ReactiveZSetOperations<K1, V1> opsForZSet(ValkeySerializationContext<K1, V1> serializationContext) {
		return new DefaultReactiveZSetOperations<>(this, serializationContext);
	}

	@Override
	public ValkeySerializationContext<K, V> getSerializationContext() {
		return serializationContext;
	}

	private ReactiveValkeyOperations<K, V> withConnection(ReactiveValkeyConnection connection) {
		return new BoundConnectionValkeyTemplate(connection, connectionFactory, serializationContext);
	}

	class BoundConnectionValkeyTemplate extends ReactiveValkeyTemplate<K, V> {

		private final ReactiveValkeyConnection connection;

		public BoundConnectionValkeyTemplate(ReactiveValkeyConnection connection,
				ReactiveValkeyConnectionFactory connectionFactory, ValkeySerializationContext<K, V> serializationContext) {
			super(connectionFactory, serializationContext, true);
			this.connection = connection;
		}

		@Override
		<T> Publisher<T> doInConnection(ReactiveValkeyCallback<T> action, boolean exposeConnection) {

			Assert.notNull(action, "Callback object must not be null");

			ReactiveValkeyConnection connToUse = ReactiveValkeyTemplate.this.preProcessConnection(connection, true);
			Publisher<T> result = action.doInValkey(connToUse);
			return ReactiveValkeyTemplate.this.postProcessResult(result, connToUse, true);
		}
	}

	private ByteBuffer rawKey(K key) {
		return getSerializationContext().getKeySerializationPair().getWriter().write(key);
	}

	private List<ByteBuffer> rawKeys(Collection<K> keys) {

		List<ByteBuffer> rawKeys = new ArrayList<>(keys.size());

		for (K key : keys) {
			rawKeys.add(rawKey(key));
		}

		return rawKeys;
	}

	@Nullable
	private K readKey(ByteBuffer buffer) {
		return getSerializationContext().getKeySerializationPair().getReader().read(buffer);
	}

	private K readRequiredKey(ByteBuffer buffer) {

		K key = readKey(buffer);

		if (key != null) {
			return key;
		}

		throw new InvalidDataAccessApiUsageException("Deserialized key is null");
	}
}
