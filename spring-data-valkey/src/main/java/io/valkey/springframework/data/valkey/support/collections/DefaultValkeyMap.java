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
package io.valkey.springframework.data.valkey.support.collections;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.core.BoundHashFieldExpirationOperations;
import io.valkey.springframework.data.valkey.core.BoundHashOperations;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import io.valkey.springframework.data.valkey.core.SessionCallback;
import org.springframework.lang.Nullable;

/**
 * Default implementation for {@link ValkeyMap}. Note that the current implementation doesn't provide the same locking
 * semantics across all methods. In highly concurrent environments, race conditions might appear.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Christian Bühler
 * @author Tihomir Mateev
 */
public class DefaultValkeyMap<K, V> implements ValkeyMap<K, V> {

	private final BoundHashOperations<String, K, V> hashOps;

	/**
	 * Constructs a new {@link DefaultValkeyMap} instance.
	 *
	 * @param key Valkey key of this map.
	 * @param operations {@link ValkeyOperations} for this map.
	 * @see ValkeyOperations#getHashKeySerializer()
	 * @see ValkeyOperations#getValueSerializer()
	 */
	public DefaultValkeyMap(String key, ValkeyOperations<String, ?> operations) {
		this.hashOps = operations.boundHashOps(key);
	}

	/**
	 * Constructs a new {@link DefaultValkeyMap} instance.
	 *
	 * @param boundOps {@link BoundHashOperations} for this map.
	 */
	public DefaultValkeyMap(BoundHashOperations<String, K, V> boundOps) {
		this.hashOps = boundOps;
	}

	@Override
	public Long increment(K key, long delta) {
		return hashOps.increment(key, delta);
	}

	@Override
	public Double increment(K key, double delta) {
		return hashOps.increment(key, delta);
	}

	@Override
	public K randomKey() {
		return hashOps.randomKey();
	}

	@Override
	public Entry<K, V> randomEntry() {
		return hashOps.randomEntry();
	}

	@Override
	public ValkeyOperations<String, ?> getOperations() {
		return hashOps.getOperations();
	}

	@Override
	public void clear() {
		getOperations().delete(Collections.singleton(getKey()));
	}

	@Override
	public boolean containsKey(Object key) {

		Boolean result = hashOps.hasKey(key);
		checkResult(result);
		return result;
	}

	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {

		Map<K, V> entries = hashOps.entries();
		checkResult(entries);
		return entries.entrySet();
	}

	@Override
	@Nullable
	public V get(Object key) {
		return hashOps.get(key);
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public Set<K> keySet() {
		return hashOps.keys();
	}

	@Override
	public V put(K key, V value) {

		V oldV = get(key);
		hashOps.put(key, value);
		return oldV;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		hashOps.putAll(m);
	}

	@Override
	@Nullable
	public V remove(Object key) {

		V v = get(key);
		hashOps.delete(key);
		return v;
	}

	@Override
	public int size() {

		Long size = hashOps.size();
		checkResult(size);
		return size.intValue();
	}

	@Override
	public Collection<V> values() {
		return hashOps.values();
	}

	@Override
	public boolean equals(@Nullable Object o) {

		if (o == this)
			return true;

		if (o instanceof ValkeyMap) {
			return o.hashCode() == hashCode();
		}
		return false;
	}

	@Override
	public int hashCode() {

		int result = 17 + getClass().hashCode();
		result = result * 31 + getKey().hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "ValkeyStore for key:" + getKey();
	}

	@Override
	@Nullable
	public V putIfAbsent(K key, V value) {
		return (hashOps.putIfAbsent(key, value) ? null : get(key));
	}

	@Override
	public boolean remove(Object key, Object value) {

		if (value == null) {
			throw new NullPointerException();
		}

		return hashOps.getOperations().execute(new SessionCallback<Boolean>() {
			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public Boolean execute(ValkeyOperations ops) {
				for (;;) {
					ops.watch(Collections.singleton(getKey()));
					V v = get(key);
					if (value.equals(v)) {
						ops.multi();
						remove(key);
						if (ops.exec(ops.getHashValueSerializer()) != null) {
							return true;
						}
					} else {
						return false;
					}
				}
			}
		});
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {

		if (oldValue == null || newValue == null) {
			throw new NullPointerException();
		}

		return hashOps.getOperations().execute(new SessionCallback<Boolean>() {
			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public Boolean execute(ValkeyOperations ops) {
				for (;;) {
					ops.watch(Collections.singleton(getKey()));
					V v = get(key);
					if (oldValue.equals(v)) {
						ops.multi();
						put(key, newValue);
						if (ops.exec(ops.getHashValueSerializer()) != null) {
							return true;
						}
					} else {
						return false;
					}
				}
			}
		});
	}

	@Override
	@Nullable
	public V replace(K key, V value) {

		if (value == null) {
			throw new NullPointerException();
		}

		return hashOps.getOperations().execute(new SessionCallback<V>() {
			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public V execute(ValkeyOperations ops) {
				for (;;) {
					ops.watch(Collections.singleton(getKey()));
					V v = get(key);
					if (v != null) {
						ops.multi();
						put(key, value);
						if (ops.exec(ops.getHashValueSerializer()) != null) {
							return v;
						}
					} else {
						return null;
					}
				}
			}
		});
	}

	@Override
	public Boolean expire(long timeout, TimeUnit unit) {
		return hashOps.expire(timeout, unit);
	}

	@Override
	public Boolean expireAt(Date date) {
		return hashOps.expireAt(date);
	}

	@Override
	public Long getExpire() {
		return hashOps.getExpire();
	}

	@Override
	public Boolean persist() {
		return hashOps.persist();
	}

	@Override
	public String getKey() {
		return hashOps.getKey();
	}

	@Override
	public void rename(String newKey) {
		hashOps.rename(newKey);
	}

	@Override
	public DataType getType() {
		return hashOps.getType();
	}

	@Override
	public Cursor<java.util.Map.Entry<K, V>> scan() {
		return scan(ScanOptions.NONE);
	}

	@Override
	public BoundHashFieldExpirationOperations<K> hashFieldExpiration() {
		return hashOps.hashExpiration();
	}

	@Override
	public BoundHashFieldExpirationOperations<K> hashFieldExpiration(Collection<K> hashFields) {
		return hashOps.hashExpiration(hashFields);
	}

	private void checkResult(@Nullable Object obj) {
		if (obj == null) {
			throw new IllegalStateException("Cannot read collection with Valkey connection in pipeline/multi-exec mode");
		}
	}

	/**
	 * @since 1.4
	 * @param options
	 * @return
	 */
	private Cursor<java.util.Map.Entry<K, V>> scan(ScanOptions options) {
		return hashOps.scan(options);
	}
}
