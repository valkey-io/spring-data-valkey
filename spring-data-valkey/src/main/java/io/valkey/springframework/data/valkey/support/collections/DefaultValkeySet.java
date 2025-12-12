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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.core.BoundSetOperations;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ScanOptions;

/**
 * Default implementation for {@link ValkeySet}. Note that the collection support works only with normal,
 * non-pipeline/multi-exec connections as it requires a reply to be sent right away.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public class DefaultValkeySet<E> extends AbstractValkeyCollection<E> implements ValkeySet<E> {

	private final BoundSetOperations<String, E> boundSetOps;

	private class DefaultValkeySetIterator extends ValkeyIterator<E> {

		public DefaultValkeySetIterator(Iterator<E> delegate) {
			super(delegate);
		}

		@Override
		protected void removeFromValkeyStorage(E item) {
			DefaultValkeySet.this.remove(item);
		}
	}

	/**
	 * Constructs a new {@link DefaultValkeySet} instance.
	 *
	 * @param key Valkey key of this set.
	 * @param operations {@link ValkeyOperations} for the value type of this set.
	 */
	public DefaultValkeySet(String key, ValkeyOperations<String, E> operations) {

		super(key, operations);
		boundSetOps = operations.boundSetOps(key);
	}

	/**
	 * Constructs a new {@link DefaultValkeySet} instance.
	 *
	 * @param boundOps {@link BoundSetOperations} for the value type of this set.
	 */
	public DefaultValkeySet(BoundSetOperations<String, E> boundOps) {

		super(boundOps.getKey(), boundOps.getOperations());
		this.boundSetOps = boundOps;
	}

	@Override
	public Set<E> diff(ValkeySet<?> set) {
		return boundSetOps.diff(set.getKey());
	}

	@Override
	public Set<E> diff(Collection<? extends ValkeySet<?>> sets) {
		return boundSetOps.diff(CollectionUtils.extractKeys(sets));
	}

	@Override
	public ValkeySet<E> diffAndStore(ValkeySet<?> set, String destKey) {
		boundSetOps.diffAndStore(set.getKey(), destKey);
		return new DefaultValkeySet<>(boundSetOps.getOperations().boundSetOps(destKey));
	}

	@Override
	public ValkeySet<E> diffAndStore(Collection<? extends ValkeySet<?>> sets, String destKey) {
		boundSetOps.diffAndStore(CollectionUtils.extractKeys(sets), destKey);
		return new DefaultValkeySet<>(boundSetOps.getOperations().boundSetOps(destKey));
	}

	@Override
	public Set<E> intersect(ValkeySet<?> set) {
		return boundSetOps.intersect(set.getKey());
	}

	@Override
	public Set<E> intersect(Collection<? extends ValkeySet<?>> sets) {
		return boundSetOps.intersect(CollectionUtils.extractKeys(sets));
	}

	@Override
	public ValkeySet<E> intersectAndStore(ValkeySet<?> set, String destKey) {
		boundSetOps.intersectAndStore(set.getKey(), destKey);
		return new DefaultValkeySet<>(boundSetOps.getOperations().boundSetOps(destKey));
	}

	@Override
	public ValkeySet<E> intersectAndStore(Collection<? extends ValkeySet<?>> sets, String destKey) {
		boundSetOps.intersectAndStore(CollectionUtils.extractKeys(sets), destKey);
		return new DefaultValkeySet<>(boundSetOps.getOperations().boundSetOps(destKey));
	}

	@Override
	public Set<E> union(ValkeySet<?> set) {
		return boundSetOps.union(set.getKey());
	}

	@Override
	public Set<E> union(Collection<? extends ValkeySet<?>> sets) {
		return boundSetOps.union(CollectionUtils.extractKeys(sets));
	}

	@Override
	public ValkeySet<E> unionAndStore(ValkeySet<?> set, String destKey) {
		boundSetOps.unionAndStore(set.getKey(), destKey);
		return new DefaultValkeySet<>(boundSetOps.getOperations().boundSetOps(destKey));
	}

	@Override
	public ValkeySet<E> unionAndStore(Collection<? extends ValkeySet<?>> sets, String destKey) {
		boundSetOps.unionAndStore(CollectionUtils.extractKeys(sets), destKey);
		return new DefaultValkeySet<>(boundSetOps.getOperations().boundSetOps(destKey));
	}

	@Override
	public E randomValue() {
		return boundSetOps.randomMember();
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean add(E e) {
		Long result = boundSetOps.add(e);
		checkResult(result);
		return result == 1;
	}

	@Override
	public void clear() {
		boundSetOps.getOperations().delete(getKey());
	}

	@Override
	public boolean contains(Object o) {
		Boolean result = boundSetOps.isMember(o);
		checkResult(result);
		return result;
	}

	@Override
	public boolean containsAll(Collection<?> c) {

		if (c.isEmpty()) {
			return true;
		}

		Map<Object, Boolean> member = boundSetOps.isMember(c.toArray());
		checkResult(member);

		return member.values().stream().reduce(true, (left, right) -> left && right);
	}

	@Override
	public Iterator<E> iterator() {
		Set<E> members = boundSetOps.members();
		checkResult(members);
		return new DefaultValkeySetIterator(members.iterator());
	}

	@Override
	public boolean remove(Object o) {
		Long result = boundSetOps.remove(o);
		checkResult(result);
		return result == 1;
	}

	@Override
	public int size() {
		Long result = boundSetOps.size();
		checkResult(result);
		return result.intValue();
	}

	@Override
	public DataType getType() {
		return DataType.SET;
	}

	@Override
	public Cursor<E> scan() {
		return scan(ScanOptions.NONE);
	}

	/**
	 * @since 1.4
	 * @param options
	 * @return
	 */
	public Cursor<E> scan(ScanOptions options) {
		return boundSetOps.scan(options);
	}
}
