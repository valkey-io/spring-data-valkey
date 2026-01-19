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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.DoubleAsStringObjectFactory;
import io.valkey.springframework.data.valkey.DoubleObjectFactory;
import io.valkey.springframework.data.valkey.LongAsStringObjectFactory;
import io.valkey.springframework.data.valkey.LongObjectFactory;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.connection.Limit;
import io.valkey.springframework.data.valkey.core.BoundZSetOperations;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.DefaultTypedTuple;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.ZSetOperations.TypedTuple;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Integration test for Valkey ZSet.
 *
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author Andrey Shlykov
 * @author Christoph Strobl
 */
public abstract class AbstractValkeyZSetTestIntegration<T> extends AbstractValkeyCollectionIntegrationTests<T> {

	private ValkeyZSet<T> zSet;

	/**
	 * Constructs a new <code>AbstractValkeyZSetTest</code> instance.
	 *
	 * @param factory
	 * @param template
	 */
	@SuppressWarnings("rawtypes")
	AbstractValkeyZSetTestIntegration(ObjectFactory<T> factory, ValkeyTemplate template) {
		super(factory, template);
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		zSet = (ValkeyZSet<T>) collection;
	}

	@ParameterizedValkeyTest
	void testAddWithScore() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		Iterator<T> iterator = zSet.iterator();
		assertThat(iterator.next()).isEqualTo(t1);
		assertThat(iterator.next()).isEqualTo(t2);
		assertThat(iterator.next()).isEqualTo(t3);
		assertThat(iterator.hasNext()).isFalse();
	}

	@ParameterizedValkeyTest
	public void testAdd() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1);
		zSet.add(t2);
		zSet.add(t3);

		Double d = new Double("1");

		assertThat(zSet.score(t1)).isEqualTo(d);
		assertThat(zSet.score(t2)).isEqualTo(d);
		assertThat(zSet.score(t3)).isEqualTo(d);
	}

	@ParameterizedValkeyTest
	void testFirst() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet).hasSize(3);
		assertThat(zSet.first()).isEqualTo(t1);
	}

	@ParameterizedValkeyTest // GH-2038
	@EnabledOnCommand("ZPOPMIN")
	void testPopFirst() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet.popFirst()).isEqualTo(t1);
		assertThat(zSet).hasSize(2);
	}

	@ParameterizedValkeyTest // GH-2038
	@EnabledOnCommand("ZPOPMIN")
	void testPopFirstWithTimeout() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet.popFirst(1, TimeUnit.SECONDS)).isEqualTo(t1);
		assertThat(zSet).hasSize(2);
	}

	@ParameterizedValkeyTest
	void testFirstException() {
		assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> zSet.first());
	}

	@ParameterizedValkeyTest
	void testLast() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet).hasSize(3);
		assertThat(zSet.last()).isEqualTo(t3);
	}

	@ParameterizedValkeyTest
	@EnabledOnCommand("ZPOPMAX")
	void testPopLast() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet.popLast()).isEqualTo(t3);
		assertThat(zSet).hasSize(2);
	}

	@ParameterizedValkeyTest
	@EnabledOnCommand("ZPOPMAX")
	void testPopLastWithTimeout() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet.popLast(1, TimeUnit.SECONDS)).isEqualTo(t3);
		assertThat(zSet).hasSize(2);
	}

	@ParameterizedValkeyTest
	void testLastException() {
		assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> zSet.last());
	}

	@ParameterizedValkeyTest
	void testRank() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet.rank(t1)).isEqualTo(Long.valueOf(0));
		assertThat(zSet.rank(t2)).isEqualTo(Long.valueOf(1));
		assertThat(zSet.rank(t3)).isEqualTo(Long.valueOf(2));
		assertThat(zSet.rank(getT())).isNull();
		// assertNull();
	}

	@ParameterizedValkeyTest
	void testReverseRank() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet.reverseRank(t3)).isEqualTo(Long.valueOf(0));
		assertThat(zSet.reverseRank(t2)).isEqualTo(Long.valueOf(1));
		assertThat(zSet.reverseRank(t1)).isEqualTo(Long.valueOf(2));
		assertThat(zSet.rank(getT())).isNull();
	}

	@ParameterizedValkeyTest // DATAREDIS-729
	void testLexCountUnbounded() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, DoubleAsStringObjectFactory.class,
				LongAsStringObjectFactory.class, LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 1);
		zSet.add(t3, 1);

		assertThat(zSet.lexCount(Range.unbounded())).isEqualTo(Long.valueOf(3));
	}

	@ParameterizedValkeyTest // DATAREDIS-729
	void testLexCountBounded() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, DoubleAsStringObjectFactory.class,
				LongAsStringObjectFactory.class, LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 1);
		zSet.add(t3, 1);

		assertThat(zSet.lexCount(Range.rightUnbounded(Range.Bound.exclusive(t1.toString())))).isEqualTo(Long.valueOf(2));
	}

	@ParameterizedValkeyTest
	void testScore() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 3);
		zSet.add(t2, 4);
		zSet.add(t3, 5);

		assertThat(zSet.score(getT())).isNull();
		assertThat(zSet.score(t1)).isEqualTo(Double.valueOf(3));
		assertThat(zSet.score(t2)).isEqualTo(Double.valueOf(4));
		assertThat(zSet.score(t3)).isEqualTo(Double.valueOf(5));
	}

	@ParameterizedValkeyTest
	void testDefaultScore() {
		assertThat(zSet.getDefaultScore()).isCloseTo(1, Offset.offset(0d));
	}

	@SuppressWarnings("unchecked")
	private ValkeyZSet<T> createZSetFor(String key) {
		return new DefaultValkeyZSet<>((BoundZSetOperations<String, T>) zSet.getOperations().boundZSetOps(key));
	}

	@ParameterizedValkeyTest
	void testRange() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		Set<T> range = zSet.range(1, 2);
		assertThat(range).hasSize(2);
		Iterator<T> iterator = range.iterator();
		assertThat(iterator.next()).isEqualTo(t2);
		assertThat(iterator.next()).isEqualTo(t3);
	}

	@ParameterizedValkeyTest
	void testRangeWithScores() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		Set<TypedTuple<T>> range = zSet.rangeWithScores(1, 2);
		assertThat(range).hasSize(2);

		Iterator<TypedTuple<T>> iterator = range.iterator();
		TypedTuple<T> tuple1 = iterator.next();
		assertThat(tuple1.getValue()).isEqualTo(t2);
		assertThat(tuple1.getScore()).isEqualTo(Double.valueOf(2));

		TypedTuple<T> tuple2 = iterator.next();
		assertThat(tuple2.getValue()).isEqualTo(t3);
		assertThat(tuple2.getScore()).isEqualTo(Double.valueOf(3));
	}

	@ParameterizedValkeyTest
	void testReverseRange() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		Set<T> range = zSet.reverseRange(1, 2);
		assertThat(range).hasSize(2);
		Iterator<T> iterator = range.iterator();
		assertThat(iterator.next()).isEqualTo(t2);
		assertThat(iterator.next()).isEqualTo(t1);
	}

	@ParameterizedValkeyTest
	void testReverseRangeWithScores() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		Set<TypedTuple<T>> range = zSet.reverseRangeWithScores(1, 2);
		assertThat(range).hasSize(2);

		Iterator<TypedTuple<T>> iterator = range.iterator();
		TypedTuple<T> tuple1 = iterator.next();
		assertThat(tuple1.getValue()).isEqualTo(t2);
		assertThat(tuple1.getScore()).isEqualTo(Double.valueOf(2));

		TypedTuple<T> tuple2 = iterator.next();
		assertThat(tuple2.getValue()).isEqualTo(t1);
		assertThat(tuple2.getScore()).isEqualTo(Double.valueOf(1));
	}

	@ParameterizedValkeyTest // DATAREDIS-407
	void testRangeByLexUnbounded() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, DoubleAsStringObjectFactory.class,
				LongAsStringObjectFactory.class, LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		Set<T> tuples = zSet.rangeByLex(Range.unbounded());

		assertThat(tuples).hasSize(3);
		T tuple = tuples.iterator().next();
		assertThat(tuple).isEqualTo(t1);
	}

	@ParameterizedValkeyTest // DATAREDIS-407
	void testRangeByLexBounded() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, DoubleAsStringObjectFactory.class,
				LongAsStringObjectFactory.class, LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		Set<T> tuples = zSet.rangeByLex(Range.open(t1.toString(), t3.toString()));

		assertThat(tuples).hasSize(1);
		T tuple = tuples.iterator().next();
		assertThat(tuple).isEqualTo(t2);
	}

	@ParameterizedValkeyTest // DATAREDIS-407
	void testRangeByLexUnboundedWithLimit() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, DoubleAsStringObjectFactory.class,
				LongAsStringObjectFactory.class, LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		Set<T> tuples = zSet.rangeByLex(Range.unbounded(), Limit.limit().count(1).offset(1));

		assertThat(tuples).hasSize(1);
		T tuple = tuples.iterator().next();
		assertThat(tuple).isEqualTo(t2);
	}

	@ParameterizedValkeyTest // DATAREDIS-407
	void testRangeByLexBoundedWithLimit() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, LongAsStringObjectFactory.class,
				LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		Set<T> tuples = zSet.rangeByLex(Range.rightUnbounded(Range.Bound.inclusive(t1.toString())),
				Limit.limit().count(2).offset(1));

		assertThat(tuples).hasSize(2).containsSequence(t2, t3);
	}

	@ParameterizedValkeyTest // DATAREDIS-729
	void testReverseRangeByLexBoundedWithLimit() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, DoubleAsStringObjectFactory.class,
				LongAsStringObjectFactory.class, LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		Set<T> tuples = zSet.reverseRangeByLex(Range.rightUnbounded(Range.Bound.inclusive(t1.toString())),
				Limit.limit().count(2).offset(1));

		assertThat(tuples).hasSize(2).containsSequence(t2, t1);
	}

	@ParameterizedValkeyTest // DATAREDIS-729
	void testReverseRangeByScore() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		Set<T> range = zSet.reverseRangeByScore(1.5, 3.5);
		assertThat(range).hasSize(2);
		Iterator<T> iterator = range.iterator();
		assertThat(iterator.next()).isEqualTo(t3);
		assertThat(iterator.next()).isEqualTo(t2);
	}

	@ParameterizedValkeyTest
	void testReverseRangeByScoreWithScores() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		Set<TypedTuple<T>> range = zSet.reverseRangeByScoreWithScores(1.5, 3.5);
		assertThat(range).hasSize(2);

		Iterator<TypedTuple<T>> iterator = range.iterator();
		TypedTuple<T> tuple1 = iterator.next();
		assertThat(tuple1.getValue()).isEqualTo(t3);
		assertThat(tuple1.getScore()).isEqualTo(Double.valueOf(3));

		TypedTuple<T> tuple2 = iterator.next();
		assertThat(tuple2.getValue()).isEqualTo(t2);
		assertThat(tuple2.getScore()).isEqualTo(Double.valueOf(2));
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testRangeByScore() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		Set<T> range = zSet.rangeByScore(1.5, 3.5);
		assertThat(range).hasSize(2);
		assertThat(range).contains(t2, t3);

		Iterator<T> iterator = range.iterator();
		assertThat(iterator.next()).isEqualTo(t2);
		assertThat(iterator.next()).isEqualTo(t3);
	}

	@ParameterizedValkeyTest
	void testRangeByScoreWithScores() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		Set<TypedTuple<T>> range = zSet.rangeByScoreWithScores(1.5, 3.5);
		assertThat(range).hasSize(2);

		Iterator<TypedTuple<T>> iterator = range.iterator();
		TypedTuple<T> tuple1 = iterator.next();
		assertThat(tuple1.getValue()).isEqualTo(t2);
		assertThat(tuple1.getScore()).isEqualTo(Double.valueOf(2));

		TypedTuple<T> tuple2 = iterator.next();
		assertThat(tuple2.getValue()).isEqualTo(t3);
		assertThat(tuple2.getScore()).isEqualTo(Double.valueOf(3));
	}

	@ParameterizedValkeyTest // GH-2345
	void testRangeAndStoreByLex() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, DoubleAsStringObjectFactory.class,
				LongAsStringObjectFactory.class, LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		ValkeyZSet<T> tuples = zSet.rangeAndStoreByLex("dest", Range.closed(t2.toString(), t3.toString()));

		assertThat(tuples).hasSize(2).containsSequence(t2, t3);
	}

	@ParameterizedValkeyTest // GH-2345
	void testRangeAndStoreRevByLex() {

		assumeThat(factory).isOfAnyClassIn(DoubleObjectFactory.class, DoubleAsStringObjectFactory.class,
				LongAsStringObjectFactory.class, LongObjectFactory.class);

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		ValkeyZSet<T> tuples = zSet.reverseRangeAndStoreByLex("dest", Range.closed(t1.toString(), t3.toString()),
				Limit.limit().count(2).offset(1));

		assertThat(tuples).hasSize(2).containsSequence(t1, t2);
	}

	@ParameterizedValkeyTest // GH-2345
	@Disabled("https://github.com/spring-projects/spring-data-valkey/issues/2441")
	void testRangeAndStoreByScore() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		ValkeyZSet<T> tuples = zSet.rangeAndStoreByScore("dest", Range.closed(2, 3));

		assertThat(tuples).hasSize(2).containsSequence(t2, t3);
	}

	@ParameterizedValkeyTest // GH-2345
	@Disabled("https://github.com/spring-projects/spring-data-valkey/issues/2441")
	void testRangeAndStoreRevByScore() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		ValkeyZSet<T> tuples = zSet.reverseRangeAndStoreByScore("dest", Range.closed(1, 3),
				Limit.limit().count(2).offset(0));

		assertThat(tuples).hasSize(2).containsSequence(t2, t3);
	}

	@ParameterizedValkeyTest
	void testRemove() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		zSet.add(t4, 4);

		zSet.remove(1, 2);

		assertThat(zSet).hasSize(2);
		Iterator<T> iterator = zSet.iterator();
		assertThat(iterator.next()).isEqualTo(t1);
		assertThat(iterator.next()).isEqualTo(t4);
	}

	@ParameterizedValkeyTest
	void testRemoveByScore() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		zSet.add(t4, 4);

		zSet.removeByScore(1.5, 2.5);

		assertThat(zSet).hasSize(3);
		Iterator<T> iterator = zSet.iterator();
		assertThat(iterator.next()).isEqualTo(t1);
		assertThat(iterator.next()).isEqualTo(t3);
		assertThat(iterator.next()).isEqualTo(t4);
	}

	@ParameterizedValkeyTest // GH-2041
	@EnabledOnCommand("ZDIFF")
	void testDifference() {

		ValkeyZSet<T> set1 = createZSetFor("test:zset:set1");
		ValkeyZSet<T> set2 = createZSetFor("test:zset:set2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		set1.add(t2, 2);
		set1.add(t4, 3);
		set2.add(t2, 2);
		set2.add(t3, 3);

		assertThat(zSet.diff(Arrays.asList(set1, set2))).containsOnly(t1);
		assertThat(zSet.diffWithScores(Arrays.asList(set1, set2))).containsOnly(new DefaultTypedTuple<>(t1, 1d));
	}

	@ParameterizedValkeyTest // GH-2041
	void testDifferenceAndStore() {

		ValkeyZSet<T> set1 = createZSetFor("test:zset:set1");
		ValkeyZSet<T> set2 = createZSetFor("test:zset:set2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		set1.add(t2, 2);
		set1.add(t4, 3);
		set2.add(t2, 2);
		set2.add(t3, 3);

		String resultName = "test:zset:inter:result:1";
		ValkeyZSet<T> diff = zSet.diffAndStore(Arrays.asList(set1, set2), resultName);

		assertThat(diff).containsOnly(t1);
	}

	@ParameterizedValkeyTest // GH-2042
	@EnabledOnCommand("ZINTER")
	void testIntersect() {

		ValkeyZSet<T> interSet1 = createZSetFor("test:zset:inter1");
		ValkeyZSet<T> interSet2 = createZSetFor("test:zset:inter");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		interSet1.add(t2, 2);
		interSet1.add(t4, 3);
		interSet2.add(t2, 2);
		interSet2.add(t3, 3);

		assertThat(zSet.intersect(Arrays.asList(interSet1, interSet2))).containsOnly(t2);
		assertThat(zSet.intersectWithScores(Arrays.asList(interSet1, interSet2)))
				.containsOnly(new DefaultTypedTuple<>(t2, 6d));
	}

	@ParameterizedValkeyTest
	void testIntersectAndStore() {

		ValkeyZSet<T> interSet1 = createZSetFor("test:zset:inter1");
		ValkeyZSet<T> interSet2 = createZSetFor("test:zset:inter");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		interSet1.add(t2, 2);
		interSet1.add(t4, 3);
		interSet2.add(t2, 2);
		interSet2.add(t3, 3);

		String resultName = "test:zset:inter:result:1";
		ValkeyZSet<T> inter = zSet.intersectAndStore(Arrays.asList(interSet1, interSet2), resultName);

		assertThat(inter).hasSize(1);
		assertThat(inter).contains(t2);
		assertThat(inter.score(t2)).isEqualTo(Double.valueOf(6));
		assertThat(inter.getKey()).isEqualTo(resultName);
	}

	@ParameterizedValkeyTest // GH-2042
	@EnabledOnCommand("ZUNION")
	void testUnion() {

		ValkeyZSet<T> set1 = createZSetFor("test:zset:union1");
		ValkeyZSet<T> set2 = createZSetFor("test:zset:union2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);

		set1.add(t2, 2);
		set1.add(t4, 3);
		set2.add(t2, 2);
		set2.add(t3, 3);

		assertThat(zSet.union(Arrays.asList(set1, set2))).contains(t1, t2, t3, t4);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testUnionAndStore() {

		ValkeyZSet<T> unionSet1 = createZSetFor("test:zset:union1");
		ValkeyZSet<T> unionSet2 = createZSetFor("test:zset:union2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);

		unionSet1.add(t2, 2);
		unionSet1.add(t4, 5);
		unionSet2.add(t3, 6);

		String resultName = "test:zset:union:result:1";
		ValkeyZSet<T> union = zSet.unionAndStore(Arrays.asList(unionSet1, unionSet2), resultName);
		assertThat(union).hasSize(4);
		assertThat(union).contains(t1, t2, t3, t4);
		assertThat(union.getKey()).isEqualTo(resultName);

		assertThat(union.score(t1)).isEqualTo(Double.valueOf(1));
		assertThat(union.score(t2)).isEqualTo(Double.valueOf(4));
		assertThat(union.score(t3)).isEqualTo(Double.valueOf(6));
		assertThat(union.score(t4)).isEqualTo(Double.valueOf(5));
	}

	@ParameterizedValkeyTest
	public void testIterator() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		zSet.add(t4, 4);

		Iterator<T> iterator = collection.iterator();

		assertThat(iterator.next()).isEqualTo(t1);
		assertThat(iterator.next()).isEqualTo(t2);
		assertThat(iterator.next()).isEqualTo(t3);
		assertThat(iterator.next()).isEqualTo(t4);
		assertThat(iterator.hasNext()).isFalse();
	}

	@ParameterizedValkeyTest
	public void testToArray() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		zSet.add(t4, 4);

		Object[] array = collection.toArray();
		assertThat(array).isEqualTo(new Object[] { t1, t2, t3, t4 });
	}

	@ParameterizedValkeyTest
	public void testToArrayWithGenerics() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		zSet.add(t4, 4);

		Object[] array = collection.toArray(new Object[zSet.size()]);
		assertThat(array).isEqualTo(new Object[] { t1, t2, t3, t4 });
	}

	@ParameterizedValkeyTest // DATAREDIS-314
	void testScanWorksCorrectly() throws IOException {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		zSet.add(t1, 1);
		zSet.add(t2, 2);
		zSet.add(t3, 3);
		zSet.add(t4, 4);

		Cursor<T> cursor = (Cursor<T>) zSet.scan();
		while (cursor.hasNext()) {
			assertThat(cursor.next()).isIn(t1, t2, t3, t4);
		}

		cursor.close();

	}

	@ParameterizedValkeyTest // GH-1794
	void testZAddIfAbsentWorks() {

		T t1 = getT();

		assertThat(zSet.addIfAbsent(t1, 1)).isTrue();
		assertThat(zSet.addIfAbsent(t1, 1)).isFalse();
	}

	@ParameterizedValkeyTest // GH-2049
	@EnabledOnCommand("ZRANDMEMBER")
	void randMemberReturnsSomething() {

		Object[] valuesArray = new Object[] { getT(), getT(), getT() };

		collection.addAll((List<T>) Arrays.asList(valuesArray));

		assertThat(zSet.randomValue()).isIn(valuesArray);
	}
}
