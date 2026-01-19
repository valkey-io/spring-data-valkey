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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.core.BoundHashFieldExpirationOperations;
import io.valkey.springframework.data.valkey.core.BoundHashOperations;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import org.springframework.lang.Nullable;

/**
 * {@link Properties} extension for a Valkey back-store. Useful for reading (and storing) properties inside a Valkey hash.
 * Particularly useful inside a Spring container for hooking into Spring's property placeholder or
 * {@link org.springframework.beans.factory.config.PropertiesFactoryBean}.
 * <p>
 * Note that this implementation only accepts Strings - objects of other type are not supported.
 *
 * @see Properties
 * @see org.springframework.core.io.support.PropertiesLoaderSupport
 * @author Costin Leau
 */
public class ValkeyProperties extends Properties implements ValkeyMap<Object, Object> {

	private final BoundHashOperations<String, String, String> hashOps;
	private final ValkeyMap<String, String> delegate;

	/**
	 * Constructs a new {@link ValkeyProperties} instance.
	 */
	public ValkeyProperties(BoundHashOperations<String, String, String> boundOps) {
		this(null, boundOps);
	}

	/**
	 * Constructs a new {@link ValkeyProperties} instance.
	 *
	 * @param key Valkey key of this property map.
	 * @param operations {@link ValkeyOperations} for this properties.
	 * @see ValkeyOperations#getHashKeySerializer()
	 * @see ValkeyOperations#getHashValueSerializer()
	 */
	public ValkeyProperties(String key, ValkeyOperations<String, ?> operations) {
		this(null, operations.<String, String> boundHashOps(key));
	}

	/**
	 * Constructs a new {@link ValkeyProperties} instance.
	 *
	 * @param defaults default properties to apply, can be {@literal null}.
	 * @param boundOps {@link BoundHashOperations} for this properties.
	 */
	public ValkeyProperties(@Nullable Properties defaults, BoundHashOperations<String, String, String> boundOps) {

		super(defaults);

		this.hashOps = boundOps;
		this.delegate = new DefaultValkeyMap<>(boundOps);
	}

	/**
	 * Constructs a new {@link ValkeyProperties} instance.
	 *
	 * @param defaults default properties to apply, can be {@literal null}.
	 * @param key Valkey key of this property map.
	 * @param operations {@link ValkeyOperations} for this properties.
	 * @see ValkeyOperations#getHashKeySerializer()
	 * @see ValkeyOperations#getHashValueSerializer()
	 */
	public ValkeyProperties(Properties defaults, String key, ValkeyOperations<String, ?> operations) {
		this(defaults, operations.boundHashOps(key));
	}

	@Override
	public synchronized Object get(Object key) {
		return delegate.get(key);
	}

	@Override
	public synchronized Object put(Object key, Object value) {
		return delegate.put((String) key, (String) value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public synchronized void putAll(Map<? extends Object, ? extends Object> t) {
		delegate.putAll((Map<? extends String, ? extends String>) t);
	}

	@Override
	public Enumeration<?> propertyNames() {
		Set<String> keys = new LinkedHashSet<>(delegate.keySet());
		if (defaults != null) {
			keys.addAll(defaults.stringPropertyNames());
		}
		return Collections.enumeration(keys);
	}

	@Override
	public synchronized void clear() {
		delegate.clear();
	}

	@Override
	public synchronized Object clone() {
		return new ValkeyProperties(defaults, hashOps);
	}

	@Override
	public synchronized boolean contains(Object value) {
		return containsValue(value);
	}

	@Override
	public synchronized boolean containsKey(Object key) {
		return delegate.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return delegate.containsValue(value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public synchronized Enumeration<Object> elements() {
		return Collections.enumeration((Collection) delegate.values());
	}

	@Override
	@SuppressWarnings("unchecked")
	public Set<Entry<Object, Object>> entrySet() {
		return (Set) delegate.entrySet();
	}

	@Override
	public synchronized boolean equals(Object o) {

		if (o == this)
			return true;

		if (o instanceof ValkeyProperties) {
			return o.hashCode() == hashCode();
		}
		return false;
	}

	@Override
	public synchronized int hashCode() {

		int hash = ValkeyProperties.class.hashCode();
		return hash * 17 + delegate.hashCode();
	}

	@Override
	public synchronized boolean isEmpty() {
		return delegate.isEmpty();
	}

	@Override
	public synchronized Enumeration<Object> keys() {
		Set<Object> keys = keySet();
		return Collections.enumeration(keys);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Set<Object> keySet() {
		return (Set) delegate.keySet();
	}

	@Override
	public synchronized Object remove(Object key) {
		return delegate.remove(key);
	}

	@Override
	public synchronized int size() {
		return delegate.size();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Collection<Object> values() {
		return (Collection) delegate.values();
	}

	@Override
	public Long increment(Object key, long delta) {
		return hashOps.increment((String) key, delta);
	}

	@Override
	public Double increment(Object key, double delta) {
		return hashOps.increment((String) key, delta);
	}

	@Override
	public Object randomKey() {
		return delegate.randomKey();
	}

	@Override
	public Entry<Object, Object> randomEntry() {
		return (Entry) delegate.randomEntry();
	}

	@Override
	public ValkeyOperations<String, ?> getOperations() {
		return hashOps.getOperations();
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
	public String getKey() {
		return hashOps.getKey();
	}

	@Override
	public DataType getType() {
		return hashOps.getType();
	}

	@Override
	public Boolean persist() {
		return hashOps.persist();
	}

	@Override
	public void rename(String newKey) {
		hashOps.rename(newKey);
	}

	@Override
	public Object putIfAbsent(Object key, Object value) {
		return (hashOps.putIfAbsent((String) key, (String) value) ? null : get(key));
	}

	@Override
	public boolean remove(Object key, Object value) {
		return delegate.remove(key, value);
	}

	@Override
	public boolean replace(Object key, Object oldValue, Object newValue) {
		return delegate.replace((String) key, (String) oldValue, (String) newValue);
	}

	@Override
	public Object replace(Object key, Object value) {
		return delegate.replace((String) key, (String) value);
	}

	@Override
	public synchronized void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized void storeToXML(OutputStream os, String comment) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<java.util.Map.Entry<Object, Object>> scan() {
		return (Iterator) delegate.scan();
	}

	@Override
	public BoundHashFieldExpirationOperations<Object> hashFieldExpiration() {
		return (BoundHashFieldExpirationOperations) delegate.hashFieldExpiration();
	}

	@Override
	public BoundHashFieldExpirationOperations<Object> hashFieldExpiration(Collection<Object> hashFields) {
		return (BoundHashFieldExpirationOperations) delegate.hashFieldExpiration((Collection) hashFields);
	}

}
