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
import java.util.Set;

import io.valkey.springframework.data.valkey.core.ValkeyOperations;

/**
 * Valkey extension for the {@link Set} contract. Supports {@link Set} specific operations backed by Valkey operations.
 *
 * @param <E> the type of elements in this collection.
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public interface ValkeySet<E> extends ValkeyCollection<E>, Set<E> {

	/**
	 * Constructs a new {@link ValkeySet} instance.
	 *
	 * @param key Valkey key of this set.
	 * @param operations {@link ValkeyOperations} for the value type of this set.
	 * @since 2.6
	 */
	static <E> ValkeySet<E> create(String key, ValkeyOperations<String, E> operations) {
		return new DefaultValkeySet<>(key, operations);
	}

	/**
	 * Diff this set and another {@link ValkeySet}.
	 *
	 * @param set must not be {@literal null}.
	 * @return a {@link Set} containing the values that differ.
	 * @since 1.0
	 */
	Set<E> diff(ValkeySet<?> set);

	/**
	 * Diff this set and other {@link ValkeySet}s.
	 *
	 * @param sets must not be {@literal null}.
	 * @return a {@link Set} containing the values that differ.
	 * @since 1.0
	 */
	Set<E> diff(Collection<? extends ValkeySet<?>> sets);

	/**
	 * Create a new {@link ValkeySet} by diffing this sorted set and {@link ValkeySet} and store result in destination
	 * {@code destKey}.
	 *
	 * @param set must not be {@literal null}.
	 * @param destKey must not be {@literal null}.
	 * @return a new {@link ValkeySet} pointing at {@code destKey}.
	 * @since 1.0
	 */
	ValkeySet<E> diffAndStore(ValkeySet<?> set, String destKey);

	/**
	 * Create a new {@link ValkeySet} by diffing this sorted set and the collection {@link ValkeySet} and store result in
	 * destination {@code destKey}.
	 *
	 * @param sets must not be {@literal null}.
	 * @param destKey must not be {@literal null}.
	 * @return a new {@link ValkeySet} pointing at {@code destKey}.
	 * @since 1.0
	 */
	ValkeySet<E> diffAndStore(Collection<? extends ValkeySet<?>> sets, String destKey);

	/**
	 * Intersect this set and another {@link ValkeySet}.
	 *
	 * @param set must not be {@literal null}.
	 * @return a {@link Set} containing the intersecting values.
	 * @since 1.0
	 */
	Set<E> intersect(ValkeySet<?> set);

	/**
	 * Intersect this set and other {@link ValkeySet}s.
	 *
	 * @param sets must not be {@literal null}.
	 * @return a {@link Set} containing the intersecting values.
	 * @since 1.0
	 */
	Set<E> intersect(Collection<? extends ValkeySet<?>> sets);

	/**
	 * Create a new {@link ValkeySet} by intersecting this sorted set and {@link ValkeySet} and store result in destination
	 * {@code destKey}.
	 *
	 * @param set must not be {@literal null}.
	 * @param destKey must not be {@literal null}.
	 * @return a new {@link ValkeySet} pointing at {@code destKey}
	 * @since 1.0
	 */
	ValkeySet<E> intersectAndStore(ValkeySet<?> set, String destKey);

	/**
	 * Create a new {@link ValkeySet} by intersecting this sorted set and the collection {@link ValkeySet} and store result
	 * in destination {@code destKey}.
	 *
	 * @param sets must not be {@literal null}.
	 * @param destKey must not be {@literal null}.
	 * @return a new {@link ValkeySet} pointing at {@code destKey}.
	 * @since 1.0
	 */
	ValkeySet<E> intersectAndStore(Collection<? extends ValkeySet<?>> sets, String destKey);

	/**
	 * Get random element from the set.
	 *
	 * @return
	 * @since 2.6
	 */
	E randomValue();

	/**
	 * @since 1.4
	 * @return
	 */
	Iterator<E> scan();

	/**
	 * Union this set and another {@link ValkeySet}.
	 *
	 * @param set must not be {@literal null}.
	 * @return a {@link Set} containing the combined values.
	 * @since 2.6
	 */
	Set<E> union(ValkeySet<?> set);

	/**
	 * Union this set and other {@link ValkeySet}s.
	 *
	 * @param sets must not be {@literal null}.
	 * @return a {@link Set} containing the combined values.
	 * @since 1.0
	 */
	Set<E> union(Collection<? extends ValkeySet<?>> sets);

	/**
	 * Create a new {@link ValkeySet} by union this sorted set and {@link ValkeySet} and store result in destination
	 * {@code destKey}.
	 *
	 * @param set must not be {@literal null}.
	 * @param destKey must not be {@literal null}.
	 * @return a new {@link ValkeySet} pointing at {@code destKey}.
	 * @since 1.0
	 */
	ValkeySet<E> unionAndStore(ValkeySet<?> set, String destKey);

	/**
	 * Create a new {@link ValkeySet} by union this sorted set and the collection {@link ValkeySet} and store result in
	 * destination {@code destKey}.
	 *
	 * @param sets must not be {@literal null}.
	 * @param destKey must not be {@literal null}.
	 * @return a new {@link ValkeySet} pointing at {@code destKey}.
	 * @since 1.0
	 */
	ValkeySet<E> unionAndStore(Collection<? extends ValkeySet<?>> sets, String destKey);
}
