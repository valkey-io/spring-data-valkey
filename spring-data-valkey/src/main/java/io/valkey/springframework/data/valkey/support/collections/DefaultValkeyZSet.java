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
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.Limit;
import io.valkey.springframework.data.valkey.core.BoundZSetOperations;
import io.valkey.springframework.data.valkey.core.ConvertingCursor;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import io.valkey.springframework.data.valkey.core.ZSetOperations.TypedTuple;

/**
 * Default implementation for {@link ValkeyZSet}. Note that the collection support works only with normal,
 * non-pipeline/multi-exec connections as it requires a reply to be sent right away.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Andrey Shlykov
 */
public class DefaultValkeyZSet<E> extends AbstractValkeyCollection<E> implements ValkeyZSet<E> {

	private final BoundZSetOperations<String, E> boundZSetOps;
	private final double defaultScore;

	private class DefaultValkeySortedSetIterator extends ValkeyIterator<E> {

		public DefaultValkeySortedSetIterator(Iterator<E> delegate) {
			super(delegate);
		}

		@Override
		protected void removeFromValkeyStorage(E item) {
			DefaultValkeyZSet.this.remove(item);
		}
	}

	/**
	 * Constructs a new {@link DefaultValkeyZSet} instance with a default score of {@literal 1}.
	 *
	 * @param key Valkey key of this set.
	 * @param operations {@link ValkeyOperations} for the value type of this set.
	 */
	public DefaultValkeyZSet(String key, ValkeyOperations<String, E> operations) {
		this(key, operations, 1);
	}

	/**
	 * Constructs a new {@link DefaultValkeyZSet} instance.
	 *
	 * @param key Valkey key of this set.
	 * @param operations {@link ValkeyOperations} for the value type of this set.
	 * @param defaultScore
	 */
	public DefaultValkeyZSet(String key, ValkeyOperations<String, E> operations, double defaultScore) {

		super(key, operations);

		boundZSetOps = operations.boundZSetOps(key);
		this.defaultScore = defaultScore;
	}

	/**
	 * Constructs a new {@link DefaultValkeyZSet} instance with a default score of '1'.
	 *
	 * @param boundOps {@link BoundZSetOperations} for the value type of this set.
	 */
	public DefaultValkeyZSet(BoundZSetOperations<String, E> boundOps) {
		this(boundOps, 1);
	}

	/**
	 * Constructs a new {@link DefaultValkeyZSet} instance.
	 *
	 * @param boundOps {@link BoundZSetOperations} for the value type of this set.
	 * @param defaultScore
	 */
	public DefaultValkeyZSet(BoundZSetOperations<String, E> boundOps, double defaultScore) {

		super(boundOps.getKey(), boundOps.getOperations());

		this.boundZSetOps = boundOps;
		this.defaultScore = defaultScore;
	}

	@Override
	public Set<E> diff(ValkeyZSet<?> set) {
		return boundZSetOps.difference(set.getKey());
	}

	@Override
	public Set<E> diff(Collection<? extends ValkeyZSet<?>> sets) {
		return boundZSetOps.difference(CollectionUtils.extractKeys(sets));
	}

	@Override
	public Set<TypedTuple<E>> diffWithScores(ValkeyZSet<?> set) {
		return boundZSetOps.differenceWithScores(set.getKey());
	}

	@Override
	public Set<TypedTuple<E>> diffWithScores(Collection<? extends ValkeyZSet<?>> sets) {
		return boundZSetOps.differenceWithScores(CollectionUtils.extractKeys(sets));
	}

	@Override
	public ValkeyZSet<E> diffAndStore(ValkeyZSet<?> set, String destKey) {

		boundZSetOps.differenceAndStore(set.getKey(), destKey);
		return new DefaultValkeyZSet<>(boundZSetOps.getOperations().boundZSetOps(destKey), getDefaultScore());
	}

	@Override
	public ValkeyZSet<E> diffAndStore(Collection<? extends ValkeyZSet<?>> sets, String destKey) {

		boundZSetOps.differenceAndStore(CollectionUtils.extractKeys(sets), destKey);
		return new DefaultValkeyZSet<>(boundZSetOps.getOperations().boundZSetOps(destKey), getDefaultScore());
	}

	@Override
	public Set<E> intersect(ValkeyZSet<?> set) {
		return boundZSetOps.intersect(set.getKey());
	}

	@Override
	public Set<E> intersect(Collection<? extends ValkeyZSet<?>> sets) {
		return boundZSetOps.intersect(CollectionUtils.extractKeys(sets));
	}

	@Override
	public Set<TypedTuple<E>> intersectWithScores(ValkeyZSet<?> set) {
		return boundZSetOps.intersectWithScores(set.getKey());
	}

	@Override
	public Set<TypedTuple<E>> intersectWithScores(Collection<? extends ValkeyZSet<?>> sets) {
		return boundZSetOps.intersectWithScores(CollectionUtils.extractKeys(sets));
	}

	@Override
	public ValkeyZSet<E> intersectAndStore(ValkeyZSet<?> set, String destKey) {

		boundZSetOps.intersectAndStore(set.getKey(), destKey);
		return new DefaultValkeyZSet<>(boundZSetOps.getOperations().boundZSetOps(destKey), getDefaultScore());
	}

	@Override
	public ValkeyZSet<E> intersectAndStore(Collection<? extends ValkeyZSet<?>> sets, String destKey) {

		boundZSetOps.intersectAndStore(CollectionUtils.extractKeys(sets), destKey);
		return new DefaultValkeyZSet<>(boundZSetOps.getOperations().boundZSetOps(destKey), getDefaultScore());
	}

	@Override
	public Set<E> union(ValkeyZSet<?> set) {
		return boundZSetOps.union(set.getKey());
	}

	@Override
	public Set<E> union(Collection<? extends ValkeyZSet<?>> sets) {
		return boundZSetOps.union(CollectionUtils.extractKeys(sets));
	}

	@Override
	public Set<TypedTuple<E>> unionWithScores(ValkeyZSet<?> set) {
		return boundZSetOps.unionWithScores(set.getKey());
	}

	@Override
	public Set<TypedTuple<E>> unionWithScores(Collection<? extends ValkeyZSet<?>> sets) {
		return boundZSetOps.unionWithScores(CollectionUtils.extractKeys(sets));
	}

	@Override
	public ValkeyZSet<E> unionAndStore(ValkeyZSet<?> set, String destKey) {
		boundZSetOps.unionAndStore(set.getKey(), destKey);
		return new DefaultValkeyZSet<>(boundZSetOps.getOperations().boundZSetOps(destKey), getDefaultScore());
	}

	@Override
	public ValkeyZSet<E> unionAndStore(Collection<? extends ValkeyZSet<?>> sets, String destKey) {
		boundZSetOps.unionAndStore(CollectionUtils.extractKeys(sets), destKey);
		return new DefaultValkeyZSet<>(boundZSetOps.getOperations().boundZSetOps(destKey), getDefaultScore());
	}

	@Override
	public E randomValue() {
		return boundZSetOps.randomMember();
	}

	@Override
	public Set<E> range(long start, long end) {
		return boundZSetOps.range(start, end);
	}

	@Override
	public Set<E> reverseRange(long start, long end) {
		return boundZSetOps.reverseRange(start, end);
	}

	@Override
	public Set<E> rangeByLex(Range<String> range, Limit limit) {
		return boundZSetOps.rangeByLex(range, limit);
	}

	@Override
	public Set<E> reverseRangeByLex(Range<String> range, Limit limit) {
		return boundZSetOps.reverseRangeByLex(range, limit);
	}

	@Override
	public Set<E> rangeByScore(double min, double max) {
		return boundZSetOps.rangeByScore(min, max);
	}

	@Override
	public Set<E> reverseRangeByScore(double min, double max) {
		return boundZSetOps.reverseRangeByScore(min, max);
	}

	@Override
	public Set<TypedTuple<E>> rangeByScoreWithScores(double min, double max) {
		return boundZSetOps.rangeByScoreWithScores(min, max);
	}

	@Override
	public Set<TypedTuple<E>> rangeWithScores(long start, long end) {
		return boundZSetOps.rangeWithScores(start, end);
	}

	@Override
	public Set<TypedTuple<E>> reverseRangeByScoreWithScores(double min, double max) {
		return boundZSetOps.reverseRangeByScoreWithScores(min, max);
	}

	@Override
	public Set<TypedTuple<E>> reverseRangeWithScores(long start, long end) {
		return boundZSetOps.reverseRangeWithScores(start, end);
	}

	@Override
	public ValkeyZSet<E> rangeAndStoreByLex(String dstKey, Range<String> range, Limit limit) {
		boundZSetOps.rangeAndStoreByLex(dstKey, range, limit);
		return new DefaultValkeyZSet<>(getOperations().boundZSetOps(dstKey));
	}

	@Override
	public ValkeyZSet<E> reverseRangeAndStoreByLex(String dstKey, Range<String> range, Limit limit) {
		boundZSetOps.reverseRangeAndStoreByLex(dstKey, range, limit);
		return new DefaultValkeyZSet<>(getOperations().boundZSetOps(dstKey));
	}

	@Override
	public ValkeyZSet<E> rangeAndStoreByScore(String dstKey, Range<? extends Number> range, Limit limit) {
		boundZSetOps.rangeAndStoreByScore(dstKey, range, limit);
		return new DefaultValkeyZSet<>(getOperations().boundZSetOps(dstKey));
	}

	@Override
	public ValkeyZSet<E> reverseRangeAndStoreByScore(String dstKey, Range<? extends Number> range, Limit limit) {
		boundZSetOps.reverseRangeAndStoreByScore(dstKey, range, limit);
		return new DefaultValkeyZSet<>(getOperations().boundZSetOps(dstKey));
	}

	@Override
	public ValkeyZSet<E> remove(long start, long end) {
		boundZSetOps.removeRange(start, end);
		return this;
	}

	@Override
	public ValkeyZSet<E> removeByLex(Range<String> range) {
		boundZSetOps.removeRangeByLex(range);
		return this;
	}

	@Override
	public ValkeyZSet<E> removeByScore(double min, double max) {
		boundZSetOps.removeRangeByScore(min, max);
		return this;
	}

	@Override
	public boolean add(E e) {
		Boolean result = add(e, getDefaultScore());
		checkResult(result);
		return result;
	}

	@Override
	public boolean add(E e, double score) {
		Boolean result = boundZSetOps.add(e, score);
		checkResult(result);
		return result;
	}

	@Override
	public boolean addIfAbsent(E e, double score) {

		Boolean result = boundZSetOps.addIfAbsent(e, score);
		checkResult(result);
		return result;
	}

	@Override
	public void clear() {
		boundZSetOps.removeRange(0, -1);
	}

	@Override
	public boolean contains(Object o) {
		return (boundZSetOps.rank(o) != null);
	}

	@Override
	public Iterator<E> iterator() {
		Set<E> members = boundZSetOps.range(0, -1);
		checkResult(members);
		return new DefaultValkeySortedSetIterator(members.iterator());
	}

	@Override
	public boolean remove(Object o) {

		Long result = boundZSetOps.remove(o);
		checkResult(result);
		return result == 1;
	}

	@Override
	public int size() {

		Long result = boundZSetOps.size();
		checkResult(result);
		return result.intValue();
	}

	@Override
	public Double getDefaultScore() {
		return defaultScore;
	}

	@Override
	public E first() {

		Set<E> members = boundZSetOps.range(0, 0);
		checkResult(members);
		Iterator<E> iterator = members.iterator();

		if (iterator.hasNext()) {
			return iterator.next();
		}

		throw new NoSuchElementException();
	}

	@Override
	public E popFirst() {

		TypedTuple<E> tuple = boundZSetOps.popMin();

		if (tuple != null) {
			return tuple.getValue();
		}

		throw new NoSuchElementException();
	}

	@Override
	public E popFirst(long timeout, TimeUnit unit) {

		TypedTuple<E> tuple = boundZSetOps.popMin(timeout, unit);

		if (tuple != null) {
			return tuple.getValue();
		}

		throw new NoSuchElementException();
	}

	@Override
	public E last() {

		Set<E> members = boundZSetOps.reverseRange(0, 0);
		checkResult(members);
		Iterator<E> iterator = members.iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		}

		throw new NoSuchElementException();
	}

	@Override
	public E popLast() {

		TypedTuple<E> tuple = boundZSetOps.popMax();

		if (tuple != null) {
			return tuple.getValue();
		}

		throw new NoSuchElementException();
	}

	@Override
	public E popLast(long timeout, TimeUnit unit) {

		TypedTuple<E> tuple = boundZSetOps.popMax(timeout, unit);

		if (tuple != null) {
			return tuple.getValue();
		}

		throw new NoSuchElementException();
	}

	@Override
	public Long rank(Object o) {
		return boundZSetOps.rank(o);
	}

	@Override
	public Long reverseRank(Object o) {
		return boundZSetOps.reverseRank(o);
	}

	@Override
	public Long lexCount(Range<String> range) {
		return boundZSetOps.lexCount(range);
	}

	@Override
	public Double score(Object o) {
		return boundZSetOps.score(o);
	}

	@Override
	public DataType getType() {
		return DataType.ZSET;
	}

	@Override
	public Cursor<E> scan() {
		return new ConvertingCursor<>(scan(ScanOptions.NONE), TypedTuple::getValue);
	}

	/**
	 * @since 1.4
	 * @param options
	 * @return
	 */
	public Cursor<TypedTuple<E>> scan(ScanOptions options) {
		return boundZSetOps.scan(options);
	}
}
