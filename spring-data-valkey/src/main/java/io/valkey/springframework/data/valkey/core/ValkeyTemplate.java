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

import java.io.Closeable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.ValkeySystemException;
import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyKeyCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyTxCommands;
import io.valkey.springframework.data.valkey.connection.SortParameters;
import io.valkey.springframework.data.valkey.connection.zset.Tuple;
import io.valkey.springframework.data.valkey.core.ZSetOperations.TypedTuple;
import io.valkey.springframework.data.valkey.core.query.QueryUtils;
import io.valkey.springframework.data.valkey.core.query.SortQuery;
import io.valkey.springframework.data.valkey.core.script.DefaultScriptExecutor;
import io.valkey.springframework.data.valkey.core.script.ValkeyScript;
import io.valkey.springframework.data.valkey.core.script.ScriptExecutor;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;
import io.valkey.springframework.data.valkey.hash.HashMapper;
import io.valkey.springframework.data.valkey.hash.ObjectHashMapper;
import io.valkey.springframework.data.valkey.serializer.JdkSerializationValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.SerializationUtils;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Helper class that simplifies Valkey data access code.
 * <p>
 * Performs automatic serialization/deserialization between the given objects and the underlying binary data in the
 * Valkey store. By default, it uses Java serialization for its objects (through {@link JdkSerializationValkeySerializer}
 * ). For String intensive operations consider the dedicated {@link StringValkeyTemplate}.
 * <p>
 * The central method is {@link #execute(ValkeyCallback)}, supporting Valkey access code implementing the
 * {@link ValkeyCallback} interface. It provides {@link ValkeyConnection} handling such that neither the
 * {@link ValkeyCallback} implementation nor the calling code needs to explicitly care about retrieving/closing Valkey
 * connections, or handling Connection lifecycle exceptions. For typical single step actions, there are various
 * convenience methods.
 * <p>
 * Once configured, this class is thread-safe.
 * <p>
 * Note that while the template is generified, it is up to the serializers/deserializers to properly convert the given
 * Objects to and from binary data.
 * <p>
 * <b>This is the central class in Valkey support</b>.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Ninad Divadkar
 * @author Anqing Shao
 * @author Mark Paluch
 * @author Denis Zavedeev
 * @author ihaohong
 * @author Chen Li
 * @author Vedran Pavic
 * @param <K> the Valkey key type against which the template works (usually a String)
 * @param <V> the Valkey value type against which the template works
 * @see StringValkeyTemplate
 */
public class ValkeyTemplate<K, V> extends ValkeyAccessor implements ValkeyOperations<K, V>, BeanClassLoaderAware {

	private boolean enableTransactionSupport = false;
	private boolean exposeConnection = false;
	private boolean initialized = false;
	private boolean enableDefaultSerializer = true;
	private @Nullable ValkeySerializer<?> defaultSerializer;
	private @Nullable ClassLoader classLoader;

	@SuppressWarnings("rawtypes") private @Nullable ValkeySerializer keySerializer = null;
	@SuppressWarnings("rawtypes") private @Nullable ValkeySerializer valueSerializer = null;
	@SuppressWarnings("rawtypes") private @Nullable ValkeySerializer hashKeySerializer = null;
	@SuppressWarnings("rawtypes") private @Nullable ValkeySerializer hashValueSerializer = null;
	private ValkeySerializer<String> stringSerializer = ValkeySerializer.string();

	private @Nullable ScriptExecutor<K> scriptExecutor;

	private final BoundOperationsProxyFactory boundOperations = new BoundOperationsProxyFactory();
	private final ValueOperations<K, V> valueOps = new DefaultValueOperations<>(this);
	private final ListOperations<K, V> listOps = new DefaultListOperations<>(this);
	private final SetOperations<K, V> setOps = new DefaultSetOperations<>(this);
	private final StreamOperations<K, ?, ?> streamOps = new DefaultStreamOperations<>(this,
			ObjectHashMapper.getSharedInstance());
	private final ZSetOperations<K, V> zSetOps = new DefaultZSetOperations<>(this);
	private final GeoOperations<K, V> geoOps = new DefaultGeoOperations<>(this);
	private final HashOperations<K, ?, ?> hashOps = new DefaultHashOperations<>(this);
	private final HyperLogLogOperations<K, V> hllOps = new DefaultHyperLogLogOperations<>(this);
	private final ClusterOperations<K, V> clusterOps = new DefaultClusterOperations<>(this);

	/**
	 * Constructs a new {@code ValkeyTemplate} instance.
	 */
	public ValkeyTemplate() {}

	@Override
	public void afterPropertiesSet() {

		super.afterPropertiesSet();

		if (defaultSerializer == null) {

			defaultSerializer = new JdkSerializationValkeySerializer(
					classLoader != null ? classLoader : this.getClass().getClassLoader());
		}

		if (enableDefaultSerializer) {

			if (keySerializer == null) {
				keySerializer = defaultSerializer;
			}
			if (valueSerializer == null) {
				valueSerializer = defaultSerializer;
			}
			if (hashKeySerializer == null) {
				hashKeySerializer = defaultSerializer;
			}
			if (hashValueSerializer == null) {
				hashValueSerializer = defaultSerializer;
			}
		}

		if (scriptExecutor == null) {
			this.scriptExecutor = new DefaultScriptExecutor<>(this);
		}

		initialized = true;
	}

	/**
	 * Returns whether the underlying ValkeyConnection should be directly exposed to the ValkeyCallback code, or rather a
	 * connection proxy (default behavior).
	 *
	 * @return {@literal true} to expose the native Valkey connection or {@literal false} to provide a proxied connection
	 *         to ValkeyCallback code.
	 */
	public boolean isExposeConnection() {
		return exposeConnection;
	}

	/**
	 * Sets whether the underlying ValkeyConnection should be directly exposed to the ValkeyCallback code. By default, the
	 * connection is not exposed, and a proxy is used instead. This proxy suppresses potentially disruptive operations,
	 * such as {@code quit} and {@code disconnect} commands, ensuring that the connection remains stable during the
	 * callback execution. Defaults to proxy use.
	 *
	 * @param exposeConnection {@literal true} to expose the actual Valkey connection to ValkeyCallback code, allowing full
	 *          access to Valkey commands, including quit and disconnect. {@literal false} to proxy connections that
	 *          suppress the quit and disconnect commands, protecting the connection from being inadvertently closed
	 *          during callback execution.
	 */
	public void setExposeConnection(boolean exposeConnection) {
		this.exposeConnection = exposeConnection;
	}

	/**
	 * Returns whether the default serializer should be used or not.
	 *
	 * @return {@literal true} if the default serializer should be used; {@literal false} otherwise.
	 */
	public boolean isEnableDefaultSerializer() {
		return enableDefaultSerializer;
	}

	/**
	 * Configure whether the default serializer should be used or not. If the default serializer is enabled, the template
	 * will use it to serialize and deserialize values. However, if the default serializer is disabled , any serializers
	 * that have not been explicitly set will remain {@literal null}, and their corresponding values will neither be
	 * serialized nor deserialized. Defaults to {@literal true}.
	 *
	 * @param enableDefaultSerializer {@literal true} if the default serializer should be used; {@literal false}
	 *          otherwise.
	 */
	public void setEnableDefaultSerializer(boolean enableDefaultSerializer) {
		this.enableDefaultSerializer = enableDefaultSerializer;
	}

	/**
	 * Sets whether this template participates in ongoing transactions using {@literal MULTI...EXEC|DISCARD} to keep track
	 * of operations.
	 *
	 * @param enableTransactionSupport {@literal true}to participate in ongoing transactions; {@literal false} to not
	 *          track transactions.
	 * @since 1.3
	 * @see ValkeyConnectionUtils#getConnection(ValkeyConnectionFactory, boolean)
	 * @see TransactionSynchronizationManager#isActualTransactionActive()
	 */
	public void setEnableTransactionSupport(boolean enableTransactionSupport) {
		this.enableTransactionSupport = enableTransactionSupport;
	}

	/**
	 * Sets the {@link ClassLoader} to be used for the default {@link JdkSerializationValkeySerializer} in case no other
	 * {@link ValkeySerializer} is explicitly set as the default one.
	 *
	 * @param classLoader can be {@literal null}.
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader
	 * @since 1.8
	 */
	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	/**
	 * Returns the default serializer used by this template.
	 *
	 * @return template default serializer.
	 */
	@Nullable
	public ValkeySerializer<?> getDefaultSerializer() {
		return defaultSerializer;
	}

	/**
	 * Sets the default serializer to use for this template. All serializers (except the
	 * {@link #setStringSerializer(ValkeySerializer)}) are initialized to this value unless explicitly set. Defaults to
	 * {@link JdkSerializationValkeySerializer}.
	 *
	 * @param serializer default serializer to use.
	 */
	public void setDefaultSerializer(ValkeySerializer<?> serializer) {
		this.defaultSerializer = serializer;
	}

	/**
	 * Sets the key serializer to be used by this template. Defaults to {@link #getDefaultSerializer()}.
	 *
	 * @param serializer the key serializer to be used by this template.
	 */
	public void setKeySerializer(ValkeySerializer<?> serializer) {
		this.keySerializer = serializer;
	}

	/**
	 * Returns the key serializer used by this template.
	 *
	 * @return the key serializer used by this template.
	 */
	@Override
	public ValkeySerializer<?> getKeySerializer() {
		return keySerializer;
	}

	/**
	 * Sets the value serializer to be used by this template. Defaults to {@link #getDefaultSerializer()}.
	 *
	 * @param serializer the value serializer to be used by this template.
	 */
	public void setValueSerializer(ValkeySerializer<?> serializer) {
		this.valueSerializer = serializer;
	}

	/**
	 * Returns the value serializer used by this template.
	 *
	 * @return the value serializer used by this template.
	 */
	@Override
	public ValkeySerializer<?> getValueSerializer() {
		return valueSerializer;
	}

	/**
	 * Returns the hashKeySerializer.
	 *
	 * @return Returns the hashKeySerializer
	 */
	@Override
	public ValkeySerializer<?> getHashKeySerializer() {
		return hashKeySerializer;
	}

	/**
	 * Sets the hash key (or field) serializer to be used by this template. Defaults to {@link #getDefaultSerializer()}.
	 *
	 * @param hashKeySerializer The hashKeySerializer to set.
	 */
	public void setHashKeySerializer(ValkeySerializer<?> hashKeySerializer) {
		this.hashKeySerializer = hashKeySerializer;
	}

	/**
	 * Returns the hashValueSerializer.
	 *
	 * @return Returns the hashValueSerializer
	 */
	@Override
	public ValkeySerializer<?> getHashValueSerializer() {
		return hashValueSerializer;
	}

	/**
	 * Sets the hash value serializer to be used by this template. Defaults to {@link #getDefaultSerializer()}.
	 *
	 * @param hashValueSerializer The hashValueSerializer to set.
	 */
	public void setHashValueSerializer(ValkeySerializer<?> hashValueSerializer) {
		this.hashValueSerializer = hashValueSerializer;
	}

	/**
	 * Returns the stringSerializer.
	 *
	 * @return Returns the stringSerializer
	 */
	public ValkeySerializer<String> getStringSerializer() {
		return stringSerializer;
	}

	/**
	 * Sets the string value serializer to be used by this template (when the arguments or return types are always
	 * strings). Defaults to {@link StringValkeySerializer}.
	 *
	 * @see ValueOperations#get(Object, long, long)
	 * @param stringSerializer The stringValueSerializer to set.
	 */
	public void setStringSerializer(ValkeySerializer<String> stringSerializer) {
		this.stringSerializer = stringSerializer;
	}

	/**
	 * @param scriptExecutor The {@link ScriptExecutor} to use for executing Valkey scripts
	 */
	public void setScriptExecutor(ScriptExecutor<K> scriptExecutor) {
		this.scriptExecutor = scriptExecutor;
	}

	@Override
	@Nullable
	public <T> T execute(ValkeyCallback<T> action) {
		return execute(action, isExposeConnection());
	}

	/**
	 * Executes the given action object within a connection, which can be exposed or not.
	 *
	 * @param <T> return type
	 * @param action callback object that specifies the Valkey action
	 * @param exposeConnection whether to enforce exposure of the native Valkey Connection to callback code
	 * @return object returned by the action
	 */
	@Nullable
	public <T> T execute(ValkeyCallback<T> action, boolean exposeConnection) {
		return execute(action, exposeConnection, false);
	}

	/**
	 * Executes the given action object within a connection that can be exposed or not. Additionally, the connection can
	 * be pipelined. Note the results of the pipeline are discarded (making it suitable for write-only scenarios).
	 *
	 * @param <T> return type
	 * @param action callback object to execute
	 * @param exposeConnection whether to enforce exposure of the native Valkey Connection to callback code
	 * @param pipeline whether to pipeline or not the connection for the execution
	 * @return object returned by the action
	 */
	@Nullable
	public <T> T execute(ValkeyCallback<T> action, boolean exposeConnection, boolean pipeline) {

		Assert.isTrue(initialized, "template not initialized; call afterPropertiesSet() before using it");
		Assert.notNull(action, "Callback object must not be null");

		ValkeyConnectionFactory factory = getRequiredConnectionFactory();
		ValkeyConnection conn = ValkeyConnectionUtils.getConnection(factory, enableTransactionSupport);

		try {

			boolean existingConnection = TransactionSynchronizationManager.hasResource(factory);
			ValkeyConnection connToUse = preProcessConnection(conn, existingConnection);

			boolean pipelineStatus = connToUse.isPipelined();
			if (pipeline && !pipelineStatus) {
				connToUse.openPipeline();
			}

			ValkeyConnection connToExpose = (exposeConnection ? connToUse : createValkeyConnectionProxy(connToUse));
			T result = action.doInValkey(connToExpose);

			// close pipeline
			if (pipeline && !pipelineStatus) {
				connToUse.closePipeline();
			}

			return postProcessResult(result, connToUse, existingConnection);
		} finally {
			ValkeyConnectionUtils.releaseConnection(conn, factory);
		}
	}

	@Override
	public <T> T execute(SessionCallback<T> session) {

		Assert.isTrue(initialized, "template not initialized; call afterPropertiesSet() before using it");
		Assert.notNull(session, "Callback object must not be null");

		ValkeyConnectionFactory factory = getRequiredConnectionFactory();
		// bind connection
		ValkeyConnectionUtils.bindConnection(factory, enableTransactionSupport);
		try {
			return session.execute(this);
		} finally {
			ValkeyConnectionUtils.unbindConnection(factory);
		}
	}

	@Override
	public List<Object> executePipelined(SessionCallback<?> session) {
		return executePipelined(session, valueSerializer);
	}

	@Override
	public List<Object> executePipelined(SessionCallback<?> session, @Nullable ValkeySerializer<?> resultSerializer) {

		Assert.isTrue(initialized, "template not initialized; call afterPropertiesSet() before using it");
		Assert.notNull(session, "Callback object must not be null");

		ValkeyConnectionFactory factory = getRequiredConnectionFactory();
		// bind connection
		ValkeyConnectionUtils.bindConnection(factory, enableTransactionSupport);
		try {
			return execute((ValkeyCallback<List<Object>>) connection -> {
				connection.openPipeline();
				boolean pipelinedClosed = false;
				try {
					Object result = executeSession(session);
					if (result != null) {
						throw new InvalidDataAccessApiUsageException(
								"Callback cannot return a non-null value as it gets overwritten by the pipeline");
					}
					List<Object> closePipeline = connection.closePipeline();
					pipelinedClosed = true;
					return deserializeMixedResults(closePipeline, resultSerializer, hashKeySerializer, hashValueSerializer);
				} finally {
					if (!pipelinedClosed) {
						connection.closePipeline();
					}
				}
			});
		} finally {
			ValkeyConnectionUtils.unbindConnection(factory);
		}
	}

	@Override
	public List<Object> executePipelined(ValkeyCallback<?> action) {
		return executePipelined(action, valueSerializer);
	}

	@Override
	public List<Object> executePipelined(ValkeyCallback<?> action, @Nullable ValkeySerializer<?> resultSerializer) {

		return execute((ValkeyCallback<List<Object>>) connection -> {
			connection.openPipeline();
			boolean pipelinedClosed = false;
			try {
				Object result = action.doInValkey(connection);
				if (result != null) {
					throw new InvalidDataAccessApiUsageException(
							"Callback cannot return a non-null value as it gets overwritten by the pipeline");
				}
				List<Object> closePipeline = connection.closePipeline();
				pipelinedClosed = true;
				return deserializeMixedResults(closePipeline, resultSerializer, hashKeySerializer, hashValueSerializer);
			} finally {
				if (!pipelinedClosed) {
					connection.closePipeline();
				}
			}
		});
	}

	@Override
	public <T> T execute(ValkeyScript<T> script, List<K> keys, Object... args) {
		return scriptExecutor.execute(script, keys, args);
	}

	@Override
	public <T> T execute(ValkeyScript<T> script, ValkeySerializer<?> argsSerializer, ValkeySerializer<T> resultSerializer,
			List<K> keys, Object... args) {
		return scriptExecutor.execute(script, argsSerializer, resultSerializer, keys, args);
	}

	@Override
	public <T extends Closeable> T executeWithStickyConnection(ValkeyCallback<T> callback) {

		Assert.isTrue(initialized, "template not initialized; call afterPropertiesSet() before using it");
		Assert.notNull(callback, "Callback object must not be null");

		ValkeyConnectionFactory factory = getRequiredConnectionFactory();

		ValkeyConnection connection = preProcessConnection(ValkeyConnectionUtils.doGetConnection(factory, true, false, false),
				false);

		return callback.doInValkey(connection);
	}

	private Object executeSession(SessionCallback<?> session) {
		return session.execute(this);
	}

	protected ValkeyConnection createValkeyConnectionProxy(ValkeyConnection connection) {

		Class<?>[] ifcs = ClassUtils.getAllInterfacesForClass(connection.getClass(), getClass().getClassLoader());

		return (ValkeyConnection) Proxy.newProxyInstance(connection.getClass().getClassLoader(), ifcs,
				new CloseSuppressingInvocationHandler(connection));
	}

	/**
	 * Processes the connection (before any settings are executed on it). Default implementation returns the connection as
	 * is.
	 *
	 * @param connection valkey connection
	 */
	protected ValkeyConnection preProcessConnection(ValkeyConnection connection, boolean existingConnection) {
		return connection;
	}

	@Nullable
	protected <T> T postProcessResult(@Nullable T result, ValkeyConnection conn, boolean existingConnection) {
		return result;
	}

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Keys
	// -------------------------------------------------------------------------

	@Override
	public Boolean copy(K source, K target, boolean replace) {

		byte[] sourceKey = rawKey(source);
		byte[] targetKey = rawKey(target);

		return doWithKeys(connection -> connection.copy(sourceKey, targetKey, replace));
	}

	@Override
	public Boolean hasKey(K key) {

		byte[] rawKey = rawKey(key);

		return doWithKeys(connection -> connection.exists(rawKey));
	}

	@Override
	public Long countExistingKeys(Collection<K> keys) {

		Assert.notNull(keys, "Keys must not be null");

		byte[][] rawKeys = rawKeys(keys);
		return doWithKeys(connection -> connection.exists(rawKeys));
	}

	@Override
	public Boolean delete(K key) {

		byte[] rawKey = rawKey(key);

		Long result = doWithKeys(connection -> connection.del(rawKey));
		return result != null && result.intValue() == 1;
	}

	@Override
	public Long delete(Collection<K> keys) {

		if (CollectionUtils.isEmpty(keys)) {
			return 0L;
		}

		byte[][] rawKeys = rawKeys(keys);

		return doWithKeys(connection -> connection.del(rawKeys));
	}

	@Override
	public Boolean unlink(K key) {

		byte[] rawKey = rawKey(key);

		Long result = doWithKeys(connection -> connection.unlink(rawKey));

		return result != null && result.intValue() == 1;
	}

	@Override
	public Long unlink(Collection<K> keys) {

		if (CollectionUtils.isEmpty(keys)) {
			return 0L;
		}

		byte[][] rawKeys = rawKeys(keys);

		return doWithKeys(connection -> connection.unlink(rawKeys));
	}

	@Override
	public DataType type(K key) {

		byte[] rawKey = rawKey(key);
		return doWithKeys(connection -> connection.type(rawKey));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Set<K> keys(K pattern) {

		byte[] rawKey = rawKey(pattern);
		Set<byte[]> rawKeys = doWithKeys(connection -> connection.keys(rawKey));

		return keySerializer != null ? SerializationUtils.deserialize(rawKeys, keySerializer) : (Set<K>) rawKeys;
	}

	@Override
	public Cursor<K> scan(ScanOptions options) {
		Assert.notNull(options, "ScanOptions must not be null");

		return executeWithStickyConnection(
				(ValkeyCallback<Cursor<K>>) connection -> new ConvertingCursor<>(connection.scan(options),
						this::deserializeKey));
	}

	@Override
	public K randomKey() {

		byte[] rawKey = doWithKeys(ValkeyKeyCommands::randomKey);
		return deserializeKey(rawKey);
	}

	@Override
	public void rename(K oldKey, K newKey) {

		byte[] rawOldKey = rawKey(oldKey);
		byte[] rawNewKey = rawKey(newKey);

		doWithKeys(connection -> {
			connection.rename(rawOldKey, rawNewKey);
			return null;
		});
	}

	@Override
	public Boolean renameIfAbsent(K oldKey, K newKey) {

		byte[] rawOldKey = rawKey(oldKey);
		byte[] rawNewKey = rawKey(newKey);
		return doWithKeys(connection -> connection.renameNX(rawOldKey, rawNewKey));
	}

	@Override
	public Boolean expire(K key, final long timeout, final TimeUnit unit) {

		byte[] rawKey = rawKey(key);
		long rawTimeout = TimeoutUtils.toMillis(timeout, unit);

		return doWithKeys(connection -> {
			try {
				return connection.pExpire(rawKey, rawTimeout);
			} catch (Exception ignore) {
				// Driver may not support pExpire or we may be running on Valkey 2.4
				return connection.expire(rawKey, TimeoutUtils.toSeconds(timeout, unit));
			}
		});
	}

	@Override
	public Boolean expireAt(K key, final Date date) {

		byte[] rawKey = rawKey(key);

		return doWithKeys(connection -> {
			try {
				return connection.pExpireAt(rawKey, date.getTime());
			} catch (Exception ignore) {
				return connection.expireAt(rawKey, date.getTime() / 1000);
			}
		});
	}

	@Nullable
	@Override
	public ExpireChanges.ExpiryChangeState expire(K key, Expiration expiration, ExpirationOptions options) {

		byte[] rawKey = rawKey(key);
		Boolean raw = doWithKeys(connection -> connection.applyExpiration(rawKey, expiration, options));

		return raw != null ? ExpireChanges.ExpiryChangeState.of(raw) : null;
	}

	@Override
	public Boolean persist(K key) {

		byte[] rawKey = rawKey(key);
		return doWithKeys(connection -> connection.persist(rawKey));
	}

	@Override
	public Long getExpire(K key) {

		byte[] rawKey = rawKey(key);
		return doWithKeys(connection -> connection.ttl(rawKey));
	}

	@Override
	public Long getExpire(K key, TimeUnit timeUnit) {

		byte[] rawKey = rawKey(key);
		return doWithKeys(connection -> {
			try {
				return connection.pTtl(rawKey, timeUnit);
			} catch (Exception ignore) {
				// Driver may not support pTtl or we may be running on Valkey 2.4
				return connection.ttl(rawKey, timeUnit);
			}
		});
	}

	@Override
	public Boolean move(K key, final int dbIndex) {

		byte[] rawKey = rawKey(key);
		return doWithKeys(connection -> connection.move(rawKey, dbIndex));
	}

	/**
	 * Executes the Valkey dump command and returns the results. Valkey uses a non-standard serialization mechanism and
	 * includes checksum information, thus the raw bytes are returned as opposed to deserializing with valueSerializer.
	 * Use the return value of dump as the value argument to restore
	 *
	 * @param key The key to dump
	 * @return results The results of the dump operation
	 */
	@Override
	public byte[] dump(K key) {

		byte[] rawKey = rawKey(key);
		return doWithKeys(connection -> connection.dump(rawKey));
	}

	/**
	 * Executes the Valkey restore command. The value passed in should be the exact serialized data returned from
	 * {@link #dump(Object)}, since Valkey uses a non-standard serialization mechanism.
	 *
	 * @param key The key to restore
	 * @param value The value to restore, as returned by {@link #dump(Object)}
	 * @param timeToLive An expiration for the restored key, or 0 for no expiration
	 * @param unit The time unit for timeToLive
	 * @param replace use {@literal true} to replace a potentially existing value instead of erroring.
	 * @throws ValkeySystemException if the key you are attempting to restore already exists and {@code replace} is set to
	 *           {@literal false}.
	 */
	@Override
	public void restore(K key, byte[] value, long timeToLive, TimeUnit unit, boolean replace) {

		byte[] rawKey = rawKey(key);
		long rawTimeout = TimeoutUtils.toMillis(timeToLive, unit);

		doWithKeys(connection -> {
			connection.restore(rawKey, rawTimeout, value, replace);
			return null;
		});
	}

	@Nullable
	private <T> T doWithKeys(Function<ValkeyKeyCommands, T> action) {
		return execute((ValkeyCallback<? extends T>) connection -> action.apply(connection.keyCommands()), true);
	}

	// -------------------------------------------------------------------------
	// Methods dealing with sorting
	// -------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public List<V> sort(SortQuery<K> query) {
		return sort(query, valueSerializer);
	}

	@Override
	public <T> List<T> sort(SortQuery<K> query, @Nullable ValkeySerializer<T> resultSerializer) {

		byte[] rawKey = rawKey(query.getKey());
		SortParameters params = QueryUtils.convertQuery(query, stringSerializer);

		List<byte[]> vals = doWithKeys(connection -> connection.sort(rawKey, params));

		return SerializationUtils.deserialize(vals, resultSerializer);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> sort(SortQuery<K> query, BulkMapper<T, V> bulkMapper) {
		return sort(query, bulkMapper, valueSerializer);
	}

	@Override
	public <T, S> List<T> sort(SortQuery<K> query, BulkMapper<T, S> bulkMapper,
			@Nullable ValkeySerializer<S> resultSerializer) {

		List<S> values = sort(query, resultSerializer);

		if (values == null || values.isEmpty()) {
			return Collections.emptyList();
		}

		int bulkSize = query.getGetPattern().size();
		List<T> result = new ArrayList<>(values.size() / bulkSize + 1);

		List<S> bulk = new ArrayList<>(bulkSize);
		for (S s : values) {

			bulk.add(s);
			if (bulk.size() == bulkSize) {
				result.add(bulkMapper.mapBulk(Collections.unmodifiableList(bulk)));
				// create a new list (we could reuse the old one but the client might hang on to it for some reason)
				bulk = new ArrayList<>(bulkSize);
			}
		}

		return result;
	}

	@Override
	public Long sort(SortQuery<K> query, K storeKey) {

		byte[] rawStoreKey = rawKey(storeKey);
		byte[] rawKey = rawKey(query.getKey());
		SortParameters params = QueryUtils.convertQuery(query, stringSerializer);

		return doWithKeys(connection -> connection.sort(rawKey, params, rawStoreKey));
	}

	// -------------------------------------------------------------------------
	// Methods dealing with Transactions
	// -------------------------------------------------------------------------

	@Override
	public void watch(K key) {

		byte[] rawKey = rawKey(key);

		executeWithoutResult(connection -> connection.watch(rawKey));
	}

	@Override
	public void watch(Collection<K> keys) {

		byte[][] rawKeys = rawKeys(keys);

		executeWithoutResult(connection -> connection.watch(rawKeys));
	}

	@Override
	public void unwatch() {

		executeWithoutResult(ValkeyTxCommands::unwatch);
	}

	@Override
	public void multi() {
		executeWithoutResult(ValkeyTxCommands::multi);
	}

	@Override
	public void discard() {
		executeWithoutResult(ValkeyTxCommands::discard);
	}

	/**
	 * Execute a transaction, using the default {@link ValkeySerializer}s to deserialize any results that are byte[]s or
	 * Collections or Maps of byte[]s or Tuples. Other result types (Long, Boolean, etc) are left as-is in the converted
	 * results. If conversion of tx results has been disabled in the {@link ValkeyConnectionFactory}, the results of exec
	 * will be returned without deserialization. This check is mostly for backwards compatibility with 1.0.
	 *
	 * @return The (possibly deserialized) results of transaction exec
	 */
	@Override
	public List<Object> exec() {

		List<Object> results = execRaw();
		if (getRequiredConnectionFactory().getConvertPipelineAndTxResults()) {
			return deserializeMixedResults(results, valueSerializer, hashKeySerializer, hashValueSerializer);
		} else {
			return results;
		}
	}

	@Override
	public List<Object> exec(ValkeySerializer<?> valueSerializer) {
		return deserializeMixedResults(execRaw(), valueSerializer, valueSerializer, valueSerializer);
	}

	protected List<Object> execRaw() {

		List<Object> raw = execute(ValkeyTxCommands::exec);
		return raw == null ? Collections.emptyList() : raw;
	}

	// -------------------------------------------------------------------------
	// Methods dealing with Valkey Server Commands
	// -------------------------------------------------------------------------

	@Override
	public List<ValkeyClientInfo> getClientList() {
		return execute(ValkeyServerCommands::getClientList);
	}

	@Override
	public void killClient(String host, int port) {
		executeWithoutResult(connection -> connection.killClient(host, port));
	}

	/*
	 * @see io.valkey.springframework.data.valkey.core.ValkeyOperations#replicaOf(java.lang.String, int)
	 */
	@Override
	public void replicaOf(String host, int port) {
		executeWithoutResult(connection -> connection.replicaOf(host, port));
	}

	@Override
	public void replicaOfNoOne() {
		executeWithoutResult(ValkeyServerCommands::replicaOfNoOne);
	}

	@Override
	public Long convertAndSend(String channel, Object message) {

		Assert.hasText(channel, "a non-empty channel is required");

		byte[] rawChannel = rawString(channel);
		byte[] rawMessage = rawValue(message);

		return execute(connection -> connection.publish(rawChannel, rawMessage), true);
	}

	private void executeWithoutResult(Consumer<ValkeyConnection> action) {
		execute(it -> {

			action.accept(it);
			return null;
		}, true);
	}

	// -------------------------------------------------------------------------
	// Methods to obtain specific operations interface objects.
	// -------------------------------------------------------------------------

	@Override
	public ClusterOperations<K, V> opsForCluster() {
		return clusterOps;
	}

	@Override
	public GeoOperations<K, V> opsForGeo() {
		return geoOps;
	}

	@Override
	@SuppressWarnings("unchecked")
	public BoundGeoOperations<K, V> boundGeoOps(K key) {
		return boundOperations.createProxy(BoundGeoOperations.class, key, DataType.ZSET, this, ValkeyOperations::opsForGeo);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <HK, HV> BoundHashOperations<K, HK, HV> boundHashOps(K key) {
		return boundOperations.createProxy(BoundHashOperations.class, key, DataType.HASH, this, it -> it.opsForHash());
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <HK, HV> HashOperations<K, HK, HV> opsForHash() {
		return (HashOperations) hashOps;
	}

	@Override
	public HyperLogLogOperations<K, V> opsForHyperLogLog() {
		return hllOps;
	}

	@Override
	public ListOperations<K, V> opsForList() {
		return listOps;
	}

	@Override
	@SuppressWarnings("unchecked")
	public BoundListOperations<K, V> boundListOps(K key) {
		return boundOperations.createProxy(BoundListOperations.class, key, DataType.LIST, this,
				ValkeyOperations::opsForList);
	}

	@Override
	@SuppressWarnings("unchecked")
	public BoundSetOperations<K, V> boundSetOps(K key) {
		return boundOperations.createProxy(BoundSetOperations.class, key, DataType.SET, this, ValkeyOperations::opsForSet);
	}

	@Override
	public SetOperations<K, V> opsForSet() {
		return setOps;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <HK, HV> StreamOperations<K, HK, HV> opsForStream() {
		return (StreamOperations<K, HK, HV>) streamOps;
	}

	@Override
	public <HK, HV> StreamOperations<K, HK, HV> opsForStream(HashMapper<? super K, ? super HK, ? super HV> hashMapper) {
		return new DefaultStreamOperations<>(this, hashMapper);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <HK, HV> BoundStreamOperations<K, HK, HV> boundStreamOps(K key) {
		return boundOperations.createProxy(BoundStreamOperations.class, key, DataType.STREAM, this, it -> opsForStream());
	}

	@Override
	@SuppressWarnings("unchecked")
	public BoundValueOperations<K, V> boundValueOps(K key) {
		return boundOperations.createProxy(BoundValueOperations.class, key, DataType.STRING, this,
				ValkeyOperations::opsForValue);
	}

	@Override
	public ValueOperations<K, V> opsForValue() {
		return valueOps;
	}

	@Override
	@SuppressWarnings("unchecked")
	public BoundZSetOperations<K, V> boundZSetOps(K key) {
		return boundOperations.createProxy(BoundZSetOperations.class, key, DataType.ZSET, this,
				ValkeyOperations::opsForZSet);
	}

	@Override
	public ZSetOperations<K, V> opsForZSet() {
		return zSetOps;
	}

	@SuppressWarnings("unchecked")
	private byte[] rawKey(Object key) {
		Assert.notNull(key, "non null key required");
		if (keySerializer == null && key instanceof byte[] bytes) {
			return bytes;
		}
		return keySerializer.serialize(key);
	}

	private byte[] rawString(String key) {
		return stringSerializer.serialize(key);
	}

	@SuppressWarnings("unchecked")
	private byte[] rawValue(Object value) {
		if (valueSerializer == null && value instanceof byte[] bytes) {
			return bytes;
		}
		return valueSerializer.serialize(value);
	}

	private byte[][] rawKeys(Collection<K> keys) {
		final byte[][] rawKeys = new byte[keys.size()][];

		int i = 0;
		for (K key : keys) {
			rawKeys[i++] = rawKey(key);
		}

		return rawKeys;
	}

	@SuppressWarnings("unchecked")
	private K deserializeKey(byte[] value) {
		return keySerializer != null ? (K) keySerializer.deserialize(value) : (K) value;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Nullable
	private List<Object> deserializeMixedResults(@Nullable List<Object> rawValues,
			@Nullable ValkeySerializer valueSerializer, @Nullable ValkeySerializer hashKeySerializer,
			@Nullable ValkeySerializer hashValueSerializer) {

		if (rawValues == null) {
			return null;
		}

		List<Object> values = new ArrayList<>();
		for (Object rawValue : rawValues) {

			if (rawValue instanceof byte[] bytes && valueSerializer != null) {
				values.add(valueSerializer.deserialize(bytes));
			} else if (rawValue instanceof List list) {
				// Lists are the only potential Collections of mixed values....
				values.add(deserializeMixedResults(list, valueSerializer, hashKeySerializer, hashValueSerializer));
			} else if (rawValue instanceof Set set && !(set.isEmpty())) {
				values.add(deserializeSet(set, valueSerializer));
			} else if (rawValue instanceof Map map && !(map.isEmpty()) && map.values().iterator().next() instanceof byte[]) {
				values.add(SerializationUtils.deserialize(map, hashKeySerializer, hashValueSerializer));
			} else {
				values.add(rawValue);
			}
		}

		return values;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Set<?> deserializeSet(Set rawSet, @Nullable ValkeySerializer valueSerializer) {

		if (rawSet.isEmpty()) {
			return rawSet;
		}

		Object setValue = rawSet.iterator().next();

		if (setValue instanceof byte[] && valueSerializer != null) {
			return (SerializationUtils.deserialize(rawSet, valueSerializer));
		} else if (setValue instanceof Tuple) {
			return convertTupleValues(rawSet, valueSerializer);
		} else {
			return rawSet;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Set<TypedTuple<V>> convertTupleValues(Set<Tuple> rawValues, @Nullable ValkeySerializer valueSerializer) {

		Set<TypedTuple<V>> set = new LinkedHashSet<>(rawValues.size());
		for (Tuple rawValue : rawValues) {
			Object value = rawValue.getValue();
			if (valueSerializer != null) {
				value = valueSerializer.deserialize(rawValue.getValue());
			}
			set.add(new DefaultTypedTuple(value, rawValue.getScore()));
		}
		return set;
	}

}
