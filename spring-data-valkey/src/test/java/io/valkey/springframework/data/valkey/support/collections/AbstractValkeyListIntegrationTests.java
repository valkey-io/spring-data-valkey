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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyListCommands;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Integration tests for ValkeyList
 *
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Mark Paluch
 * @author John Blum
 */
public abstract class AbstractValkeyListIntegrationTests<T> extends AbstractValkeyCollectionIntegrationTests<T> {

	protected ValkeyList<T> list;

	/**
	 * Constructs a new {@link AbstractValkeyListIntegrationTests}.
	 *
	 * @param factory {@link ObjectFactory} used to create different types of elements to store in the list.
	 * @param template {@link ValkeyTemplate} used to perform operations on Valkey.
	 */
	@SuppressWarnings("rawtypes")
	AbstractValkeyListIntegrationTests(ObjectFactory<T> factory, ValkeyTemplate template) {
		super(factory, template);
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setUp() throws Exception {

		super.setUp();
		this.list = (ValkeyList<T>) this.collection;
	}

	@ParameterizedValkeyTest
	void testAddIndexObjectHead() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);

		assertThat(list.get(0)).isEqualTo(t1);
		list.add(0, t3);
		assertThat(list.get(0)).isEqualTo(t3);
	}

	@ParameterizedValkeyTest
	void testAddIndexObjectTail() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);

		assertThat(list.get(1)).isEqualTo(t2);
		list.add(2, t3);
		assertThat(list.get(2)).isEqualTo(t3);
	}

	@ParameterizedValkeyTest
	void testAddIndexObjectMiddle() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);

		assertThat(list.get(0)).isEqualTo(t1);
		assertThatIllegalArgumentException().isThrownBy(() -> list.add(1, t3));
	}

	@ParameterizedValkeyTest
	void addAllIndexCollectionHead() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		list.add(t1);
		list.add(t2);

		List<T> asList = Arrays.asList(t3, t4);

		assertThat(list.get(0)).isEqualTo(t1);

		list.addAll(0, asList);

		// verify insertion order
		assertThat(list.get(0)).isEqualTo(t3);
		assertThat(list.get(1)).isEqualTo(t4);
	}

	@ParameterizedValkeyTest
	void addAllIndexCollectionTail() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		list.add(t1);
		list.add(t2);

		List<T> asList = Arrays.asList(t3, t4);

		assertThat(list.get(0)).isEqualTo(t1);
		assertThat(list.addAll(2, asList)).isTrue();

		// verify insertion order
		assertThat(list.get(2)).isEqualTo(t3);
		assertThat(list.get(3)).isEqualTo(t4);
	}

	@ParameterizedValkeyTest
	void addAllIndexCollectionMiddle() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();

		list.add(t1);
		list.add(t2);

		List<T> asList = Arrays.asList(t3, t4);

		assertThat(list.get(0)).isEqualTo(t1);
		assertThatIllegalArgumentException().isThrownBy(() -> list.addAll(1, asList));
	}

	@ParameterizedValkeyTest // DATAREDIS-1196
	@EnabledOnCommand("LPOS")
	void testIndexOfObject() {

		T t1 = getT();
		T t2 = getT();

		assertThat(list.indexOf(t1)).isEqualTo(-1);
		list.add(t1);
		assertThat(list.indexOf(t1)).isEqualTo(0);

		assertThat(list.indexOf(t2)).isEqualTo(-1);
		list.add(t2);
		assertThat(list.indexOf(t2)).isEqualTo(1);
	}

	@ParameterizedValkeyTest
	void testOffer() {

		T t1 = getT();

		assertThat(list.offer(t1)).isTrue();
		assertThat(list.get(0)).isEqualTo(t1);
	}

	@ParameterizedValkeyTest
	void testPeek() {

		assertThat(list.peek()).isNull();

		T t1 = getT();

		list.add(t1);

		assertThat(list.peek()).isEqualTo(t1);

		list.clear();

		assertThat(list.peek()).isNull();
	}

	@ParameterizedValkeyTest
	void testElement() {

		assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(list::element);

		T t1 = getT();

		list.add(t1);
		assertThat(list.element()).isEqualTo(t1);

		list.clear();
		assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(list::element);
	}

	@ParameterizedValkeyTest
	void testPop() {
		testPoll();
	}

	@ParameterizedValkeyTest
	void testPoll() {

		assertThat(list.poll()).isNull();

		T t1 = getT();

		list.add(t1);

		assertThat(list.poll()).isEqualTo(t1);
		assertThat(list.poll()).isNull();
	}

	@ParameterizedValkeyTest
	void testPollTimeout() throws InterruptedException {

		T t1 = getT();
		list.add(t1);
		assertThat(list.poll(1, TimeUnit.MILLISECONDS)).isEqualTo(t1);
	}

	@ParameterizedValkeyTest
	void testRemove() {

		assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(list::remove);

		T t1 = getT();

		list.add(t1);

		assertThat(list.remove()).isEqualTo(t1);
		assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(list::remove);
	}

	@ParameterizedValkeyTest // GH-2039
	@EnabledOnCommand("LMOVE")
	@SuppressWarnings("unchecked")
	void testMoveFirstTo() {

		ValkeyList<T> target = new DefaultValkeyList<T>(template.boundListOps(collection.getKey() + ":target"));

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);
		list.add(t3);

		assertThat(list.moveFirstTo(target, ValkeyListCommands.Direction.first())).isEqualTo(t1);
		assertThat(list.moveFirstTo(target, ValkeyListCommands.Direction.first())).isEqualTo(t2);
		assertThat(list.moveFirstTo(target, ValkeyListCommands.Direction.last())).isEqualTo(t3);
		assertThat(list).isEmpty();
		assertThat(target).hasSize(3).containsSequence(t2, t1, t3);
	}

	@ParameterizedValkeyTest // GH-2039
	@EnabledOnCommand("LMOVE")
	@SuppressWarnings("unchecked")
	void testMoveLastTo() {

		ValkeyList<T> target = new DefaultValkeyList<T>(template.boundListOps(collection.getKey() + ":target"));

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);
		list.add(t3);

		assertThat(list.moveLastTo(target, ValkeyListCommands.Direction.first())).isEqualTo(t3);
		assertThat(list.moveLastTo(target, ValkeyListCommands.Direction.first())).isEqualTo(t2);
		assertThat(list.moveLastTo(target, ValkeyListCommands.Direction.last())).isEqualTo(t1);
		assertThat(list).isEmpty();
		assertThat(target).hasSize(3).containsSequence(t2, t3, t1);
	}

	@ParameterizedValkeyTest
	void testRange() {

		T t1 = getT();
		T t2 = getT();

		assertThat(list.range(0, -1)).isEmpty();

		list.add(t1);
		list.add(t2);

		assertThat(list.range(0, -1)).hasSize(2);
		assertThat(list.range(0, 0).get(0)).isEqualTo(t1);
		assertThat(list.range(1, 1).get(0)).isEqualTo(t2);
	}

	@ParameterizedValkeyTest
	void testRemoveIndex() {
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> list.remove(0));
	}

	@ParameterizedValkeyTest
	void testSet() {

		T t1 = getT();
		T t2 = getT();

		list.add(t1);
		list.set(0, t1);

		assertThat(list.set(0, t2)).isEqualTo(t1);
		assertThat(list.get(0)).isEqualTo(t2);
	}

	@ParameterizedValkeyTest
	void testTrim() {

		T t1 = getT();
		T t2 = getT();

		assertThat(list.trim(0, 0)).isEmpty();

		list.add(t1);
		list.add(t2);

		assertThat(list).hasSize(2);
		assertThat(list.trim(0L, 0L)).hasSize(1);
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isEqualTo(t1);
		assertThat(list).hasSize(1);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testCappedCollection() {

		ValkeyList<T> cappedList = new DefaultValkeyList<T>(template.boundListOps(collection.getKey() + ":capped"), 1);

		T first = getT();

		cappedList.offer(first);

		assertThat(cappedList).hasSize(1);

		cappedList.add(getT());

		assertThat(cappedList).hasSize(1);

		T last = getT();

		cappedList.add(last);
		assertThat(cappedList).hasSize(1);
		assertThat(cappedList.get(0)).isEqualTo(first);
	}

	@ParameterizedValkeyTest
	void testAddFirst() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		Arrays.asList(t1, t2, t3).forEach(this.list::addFirst);

		Iterator<T> iterator = list.iterator();

		assertThat(iterator.next()).isEqualTo(t3);
		assertThat(iterator.next()).isEqualTo(t2);
		assertThat(iterator.next()).isEqualTo(t1);
	}

	@ParameterizedValkeyTest
	void testAddLast() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		Arrays.asList(t1, t2, t3).forEach(this.list::addLast);

		Iterator<T> iterator = list.iterator();

		assertThat(iterator.next()).isEqualTo(t1);
		assertThat(iterator.next()).isEqualTo(t2);
		assertThat(iterator.next()).isEqualTo(t3);
	}

	@ParameterizedValkeyTest
	void testDescendingIterator() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);
		list.add(t3);

		Iterator<T> iterator = list.descendingIterator();

		assertThat(iterator.next()).isEqualTo(t3);
		assertThat(iterator.next()).isEqualTo(t2);
		assertThat(iterator.next()).isEqualTo(t1);
	}

	@ParameterizedValkeyTest // GH-2602
	void testListIteratorAddNextPreviousIsCorrect() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		ListIterator<T> listIterator = this.list.listIterator();

		assertThat(listIterator).isNotNull();

		Arrays.asList(t1, t2, t3).forEach(listIterator::add);

		assertThat(this.list).containsExactly(t1, t2, t3);
		assertThat(listIterator.hasNext()).isFalse();
		assertThat(listIterator.hasPrevious()).isTrue();
		assertThat(listIterator.previous()).isEqualTo(t3);
		assertThat(listIterator.previous()).isEqualTo(t2);
		assertThat(listIterator.previous()).isEqualTo(t1);
		assertThat(listIterator.hasPrevious()).isFalse();
		assertThat(listIterator.hasNext()).isTrue();
		assertThat(listIterator.next()).isEqualTo(t1);
		assertThat(listIterator.next()).isEqualTo(t2);
		assertThat(listIterator.next()).isEqualTo(t3);
		assertThat(listIterator.hasNext()).isFalse();
		assertThat(listIterator.hasPrevious()).isTrue();
	}

	@ParameterizedValkeyTest // GH-2602
	public void testListIteratorSetIsCorrect() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();
		T t4 = getT();
		T t5 = getT();

		Collections.addAll(this.list, t1, t2, t3, t2, t5);

		assertThat(this.list).containsExactly(t1, t2, t3, t2, t5);

		ListIterator<T> listIterator = this.list.listIterator();

		int index = 0;

		while (listIterator.hasNext()) {
			listIterator.next();
			if (index++ == 3) {
				listIterator.set(t4);
			}
		}

		assertThat(this.list).containsExactly(t1, t2, t3, t4, t5);
	}

	@ParameterizedValkeyTest
	void testDrainToCollectionWithMaxElements() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);
		list.add(t3);

		List<T> c = new ArrayList<>();

		list.drainTo(c, 2);

		assertThat(list).hasSize(1).contains(t3);
		assertThat(c).hasSize(2).contains(t1, t2);
	}

	@ParameterizedValkeyTest
	void testDrainToCollection() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);
		list.add(t3);

		List<T> c = new ArrayList<>();

		list.drainTo(c);

		assertThat(list).isEmpty();
		assertThat(c).hasSize(3).contains(t1, t2, t3);
	}

	@ParameterizedValkeyTest
	void testGetFirst() {

		T t1 = getT();
		T t2 = getT();

		list.add(t1);
		list.add(t2);

		assertThat(list.getFirst()).isEqualTo(t1);
	}

	@ParameterizedValkeyTest
	void testLast() {
		testAdd();
	}

	@ParameterizedValkeyTest
	void testOfferFirst() {
		testAddFirst();
	}

	@ParameterizedValkeyTest
	void testOfferLast() {
		testAddLast();
	}

	@ParameterizedValkeyTest
	void testPeekFirst() {
		testPeek();
	}

	@ParameterizedValkeyTest
	void testPeekLast() {

		T t1 = getT();
		T t2 = getT();

		list.add(t1);
		list.add(t2);

		assertThat(list.peekLast()).isEqualTo(t2);
		assertThat(list).hasSize(2);
	}

	@ParameterizedValkeyTest
	void testPollFirst() {
		testPoll();
	}

	@ParameterizedValkeyTest
	void testPollLast() {

		T t1 = getT();
		T t2 = getT();

		list.add(t1);
		list.add(t2);

		T last = list.pollLast();

		assertThat(last).isEqualTo(t2);
		assertThat(list).hasSize(1).contains(t1);
	}

	@ParameterizedValkeyTest
	void testPollLastTimeout() throws InterruptedException {

		T t1 = getT();
		T t2 = getT();

		list.add(t1);
		list.add(t2);

		T last = list.pollLast(1, TimeUnit.MILLISECONDS);

		assertThat(last).isEqualTo(t2);
		assertThat(list).hasSize(1).contains(t1);
	}

	@ParameterizedValkeyTest
	void testPut() {
		testOffer();
	}

	@ParameterizedValkeyTest
	void testPutFirst() {
		testAdd();
	}

	@ParameterizedValkeyTest
	void testPutLast() {
		testPut();
	}

	@ParameterizedValkeyTest
	void testRemainingCapacity() {
		assertThat(list.remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
	}

	@ParameterizedValkeyTest
	void testRemoveFirst() {
		testPop();
	}

	@ParameterizedValkeyTest
	void testRemoveFirstOccurrence() {
		testRemove();
	}

	@ParameterizedValkeyTest
	void testRemoveLast() {
		testPollLast();
	}

	@ParameterizedValkeyTest
	void testRmoveLastOccurrence() {

		T t1 = getT();
		T t2 = getT();

		list.add(t1);
		list.add(t2);
		list.add(t1);
		list.add(t2);
		list.removeLastOccurrence(t2);

		assertThat(list).hasSize(3).containsExactly(t1, t2, t1);
	}

	@ParameterizedValkeyTest
	void testTake() {
		testPoll();
	}

	@ParameterizedValkeyTest
	void testTakeFirst() {
		testTake();
	}

	@ParameterizedValkeyTest
	void testTakeLast() {
		testPollLast();
	}

	@ParameterizedValkeyTest // DATAREDIS-1196
	@EnabledOnCommand("LPOS")
	void lastIndexOf() {

		T t1 = getT();
		T t2 = getT();
		T t3 = getT();

		list.add(t1);
		list.add(t2);
		list.add(t1);
		list.add(t3);

		assertThat(list.lastIndexOf(t1)).isEqualTo(2);
	}

	@ParameterizedValkeyTest // GH-2602
	void testReversed() {

		T elementOne = getT();
		T elementTwo = getT();
		T elementThree = getT();

		Collections.addAll(this.list, elementOne, elementTwo, elementThree);

		assertThat(this.list).containsExactly(elementOne, elementTwo, elementThree);

		ValkeyList<T> reversedList = this.list.reversed();

		assertThat(reversedList).isNotNull();
		assertThat(reversedList).isNotSameAs(this.list);
		assertThat(reversedList).hasSameSizeAs(this.list);
		assertThat(reversedList).containsExactly(elementThree, elementTwo, elementOne);
		assertThat(reversedList.reversed()).isEqualTo(this.list);
	}

	@ParameterizedValkeyTest // // GH-2602
	public void testReversedListIterator() {

		T elementOne = getT();
		T elementTwo = getT();
		T elementThree = getT();
		T elementFour = getT();

		Collections.addAll(this.list, elementOne, elementTwo, elementThree, elementFour);

		assertThat(this.list).containsExactly(elementOne, elementTwo, elementThree, elementFour);

		List<T> expectedList = Arrays.asList(elementFour, elementThree, elementTwo, elementOne);

		ValkeyList<T> reversedList = this.list.reversed();

		assertThat(reversedList).containsExactly(elementFour, elementThree, elementTwo, elementOne);

		Iterator<T> reversedListIterator = reversedList.iterator();

		assertThat(reversedListIterator).isNotNull();
		assertThat(reversedListIterator).hasNext();

		int index = -1;

		while (reversedListIterator.hasNext()) {
			assertThat(reversedListIterator.next()).isEqualTo(expectedList.get(++index));
			if (index == 1) {
				reversedListIterator.remove();
				++index;
			}
		}

		assertThat(reversedList).containsExactly(elementFour, elementTwo, elementOne);

		ValkeyList<T> reorderedList = reversedList.reversed();

		assertThat(reorderedList).isEqualTo(this.list);
		assertThat(reorderedList).hasSameSizeAs(reversedList);
		assertThat(reorderedList).containsExactly(elementOne, elementTwo, elementFour);
	}

	@ParameterizedValkeyTest // GH-2602
	void testReversedWithAddFirst() {

		T elementOne = getT();
		T elementTwo = getT();
		T elementThree = getT();

		Collections.addAll(this.list, elementOne, elementTwo);

		ValkeyList<T> reversedList = this.list.reversed();

		reversedList.addFirst(elementThree);

		assertThat(reversedList).containsExactly(elementThree, elementTwo, elementOne);

		ValkeyList<T> reorderedList = reversedList.reversed();

		assertThat(reorderedList).containsExactly(elementOne, elementTwo, elementThree);
	}

	@ParameterizedValkeyTest // GH-2602
	void testReversedWithAddLast() {

		T elementZero = getT();
		T elementOne = getT();
		T elementTwo = getT();

		Collections.addAll(this.list, elementOne, elementTwo);

		assertThat(this.list).containsExactly(elementOne, elementTwo);

		ValkeyList<T> reversedList = this.list.reversed();

		reversedList.addLast(elementZero);

		assertThat(reversedList).containsExactly(elementTwo, elementOne, elementZero);

		ValkeyList<T> reorderedList = reversedList.reversed();

		assertThat(reorderedList).containsExactly(elementZero, elementOne, elementTwo);
	}

	@ParameterizedValkeyTest // GH-2602
	void testReversedWithRemoveFirst() {

		T elementOne = getT();
		T elementTwo = getT();
		T elementThree = getT();

		Collections.addAll(this.list, elementOne, elementTwo, elementThree);

		ValkeyList<T> reversedList = this.list.reversed();

		assertThat(reversedList).containsExactly(elementThree, elementTwo, elementOne);
		assertThat(reversedList.removeFirst()).isEqualTo(elementThree);
		assertThat(reversedList).containsExactly(elementTwo, elementOne);

		ValkeyList<T> reorderedList = reversedList.reversed();

		assertThat(reorderedList).containsExactly(elementOne, elementTwo);
	}

	@ParameterizedValkeyTest // GH-2602
	void testReversedWithRemoveLast() {

		T elementOne = getT();
		T elementTwo = getT();
		T elementThree = getT();

		Collections.addAll(this.list, elementOne, elementTwo, elementThree);

		ValkeyList<T> reversedList = this.list.reversed();

		assertThat(reversedList).containsExactly(elementThree, elementTwo, elementOne);
		assertThat(reversedList.removeLast()).isEqualTo(elementOne);
		assertThat(reversedList).containsExactly(elementThree, elementTwo);

		ValkeyList<T> reorderedList = reversedList.reversed();

		assertThat(reorderedList).containsExactly(elementTwo, elementThree);
	}

	@SuppressWarnings("unused")
	private static String toString(List<?> list) {

		StringBuilder stringBuilder = new StringBuilder("[");

		boolean comma = false;

		for (Object element : list) {
			stringBuilder.append(comma ? ", " : "").append(element);
			comma = true;
		}

		return stringBuilder.append("]").toString();
	}
}
