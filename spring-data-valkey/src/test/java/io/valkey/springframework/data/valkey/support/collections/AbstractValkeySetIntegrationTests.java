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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;

import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.core.BoundSetOperations;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;
import org.springframework.util.ObjectUtils;

/**
 * Integration test for Valkey set.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Thomas Darimont
 */
public abstract class AbstractValkeySetIntegrationTests<T> extends AbstractValkeyCollectionIntegrationTests<T> {

	protected ValkeySet<T> set;

	/**
	 * Constructs a new <code>AbstractValkeySetTests</code> instance.
	 *
	 * @param factory
	 * @param template
	 */
	@SuppressWarnings("rawtypes")
	AbstractValkeySetIntegrationTests(ObjectFactory<T> factory, ValkeyTemplate template) {
		super(factory, template);
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		set = (ValkeySet<T>) collection;
	}

	@SuppressWarnings("unchecked")
	private ValkeySet<T> createSetFor(String key) {
		return new DefaultValkeySet<>((BoundSetOperations<String, T>) set.getOperations().boundSetOps(key));
	}

	@ParameterizedValkeyTest // GH-2037
	@EnabledOnCommand("SMISMEMBER")
	void testContainsAll() {
		
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		set.add(t1);
		set.add(t2);

		assertThat(set.containsAll(Arrays.asList(t1, t2, t3))).isFalse();
		assertThat(set.containsAll(Arrays.asList(t1, t2))).isTrue();
		assertThat(set.containsAll(Collections.emptyList())).isTrue();
	}

	@ParameterizedValkeyTest
	void testDiff() {
		ValkeySet<T> diffSet1 = createSetFor("test:set:diff1");
		ValkeySet<T> diffSet2 = createSetFor("test:set:diff2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		set.add(t1);
		set.add(t2);
		set.add(t3);

		diffSet1.add(t2);
		diffSet2.add(t3);

		Set<T> diff = set.diff(Arrays.asList(diffSet1, diffSet2));
		assertThat(diff.size()).isEqualTo(1);
		assertThat(diff).contains(t1);
	}

	@ParameterizedValkeyTest
	void testDiffAndStore() {
		ValkeySet<T> diffSet1 = createSetFor("test:set:diff1");
		ValkeySet<T> diffSet2 = createSetFor("test:set:diff2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		set.add(t1);
		set.add(t2);
		set.add(t3);

		diffSet1.add(t2);
		diffSet2.add(t3);
		diffSet2.add(t4);

		String resultName = "test:set:diff:result:1";
		ValkeySet<T> diff = set.diffAndStore(Arrays.asList(diffSet1, diffSet2), resultName);

		assertThat(diff.size()).isEqualTo(1);
		assertThat(diff).contains(t1);
		assertThat(diff.getKey()).isEqualTo(resultName);
	}

	@ParameterizedValkeyTest
	void testIntersect() {
		ValkeySet<T> intSet1 = createSetFor("test:set:int1");
		ValkeySet<T> intSet2 = createSetFor("test:set:int2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		set.add(t1);
		set.add(t2);
		set.add(t3);

		intSet1.add(t2);
		intSet1.add(t4);
		intSet2.add(t2);
		intSet2.add(t3);

		Set<T> inter = set.intersect(Arrays.asList(intSet1, intSet2));
		assertThat(inter.size()).isEqualTo(1);
		assertThat(inter).contains(t2);
	}

	public void testIntersectAndStore() {
		ValkeySet<T> intSet1 = createSetFor("test:set:int1");
		ValkeySet<T> intSet2 = createSetFor("test:set:int2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		set.add(t1);
		set.add(t2);
		set.add(t3);

		intSet1.add(t2);
		intSet1.add(t4);
		intSet2.add(t2);
		intSet2.add(t3);

		String resultName = "test:set:intersect:result:1";
		ValkeySet<T> inter = set.intersectAndStore(Arrays.asList(intSet1, intSet2), resultName);
		assertThat(inter.size()).isEqualTo(1);
		assertThat(inter).contains(t2);
		assertThat(inter.getKey()).isEqualTo(resultName);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testUnion() {
		ValkeySet<T> unionSet1 = createSetFor("test:set:union1");
		ValkeySet<T> unionSet2 = createSetFor("test:set:union2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		set.add(t1);
		set.add(t2);

		unionSet1.add(t2);
		unionSet1.add(t4);
		unionSet2.add(t3);

		Set<T> union = set.union(Arrays.asList(unionSet1, unionSet2));
		assertThat(union.size()).isEqualTo(4);
		assertThat(union).contains(t1, t2, t3, t4);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testUnionAndStore() {
		ValkeySet<T> unionSet1 = createSetFor("test:set:union1");
		ValkeySet<T> unionSet2 = createSetFor("test:set:union2");

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		set.add(t1);
		set.add(t2);

		unionSet1.add(t2);
		unionSet1.add(t4);
		unionSet2.add(t3);

		String resultName = "test:set:union:result:1";
		ValkeySet<T> union = set.unionAndStore(Arrays.asList(unionSet1, unionSet2), resultName);
		assertThat(union.size()).isEqualTo(4);
		assertThat(union).contains(t1, t2, t3, t4);
		assertThat(union.getKey()).isEqualTo(resultName);
	}

	@ParameterizedValkeyTest
	public void testIterator() {
		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		List<T> list = Arrays.asList(t1, t2, t3, t4);

		assertThat(collection.addAll(list)).isTrue();
		Iterator<T> iterator = collection.iterator();

		List<T> result = new ArrayList<>(list);

		while (iterator.hasNext()) {
			T expected = iterator.next();
			Iterator<T> resultItr = result.iterator();
			while (resultItr.hasNext()) {
				T obj = resultItr.next();
				if (ObjectUtils.nullSafeEquals(expected, obj)) {
					resultItr.remove();
				}
			}
		}

		assertThat(result.size()).isEqualTo(0);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	public void testToArray() {
		Object[] expectedArray = new Object[] { getT(), getT(), getT() };
		List<T> list = (List<T>) Arrays.asList(expectedArray);

		assertThat(collection.addAll(list)).isTrue();

		Object[] array = collection.toArray();

		List<T> result = new ArrayList<>(list);

		for (int i = 0; i < array.length; i++) {
			Iterator<T> resultItr = result.iterator();
			while (resultItr.hasNext()) {
				T obj = resultItr.next();
				if (ObjectUtils.nullSafeEquals(array[i], obj)) {
					resultItr.remove();
				}
			}
		}

		assertThat(result).isEmpty();
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	public void testToArrayWithGenerics() {
		Object[] expectedArray = new Object[] { getT(), getT(), getT() };
		List<T> list = (List<T>) Arrays.asList(expectedArray);

		assertThat(collection.addAll(list)).isTrue();

		Object[] array = collection.toArray(new Object[expectedArray.length]);
		List<T> result = new ArrayList<>(list);

		for (int i = 0; i < array.length; i++) {
			Iterator<T> resultItr = result.iterator();
			while (resultItr.hasNext()) {
				T obj = resultItr.next();
				if (ObjectUtils.nullSafeEquals(array[i], obj)) {
					resultItr.remove();
				}
			}
		}

		assertThat(result.size()).isEqualTo(0);
	}

	// DATAREDIS-314
	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testScanWorksCorrectly() throws IOException {

		Object[] expectedArray = new Object[] { getT(), getT(), getT() };
		collection.addAll((List<T>) Arrays.asList(expectedArray));

		Cursor<T> cursor = (Cursor<T>) set.scan();
		while (cursor.hasNext()) {
			assertThat(cursor.next()).isIn(expectedArray[0], expectedArray[1], expectedArray[2]);
		}
		cursor.close();
	}

	@ParameterizedValkeyTest // GH-2049
	void randMemberReturnsSomething() {

		Object[] valuesArray = new Object[]{getT(), getT(), getT()};

		collection.addAll((List<T>) Arrays.asList(valuesArray));

		assertThat(set.randomValue()).isIn(valuesArray);
	}
}
