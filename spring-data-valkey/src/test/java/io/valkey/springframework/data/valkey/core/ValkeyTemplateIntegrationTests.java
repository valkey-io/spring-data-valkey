/*
 * Copyright 2013-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.ConnectionFactoryTracker;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.SettingsUtils;
import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.StringValkeyConnection;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.core.ZSetOperations.TypedTuple;
import io.valkey.springframework.data.valkey.core.query.SortQueryBuilder;
import io.valkey.springframework.data.valkey.core.script.DefaultValkeyScript;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import io.valkey.springframework.data.valkey.serializer.GenericToStringSerializer;
import io.valkey.springframework.data.valkey.serializer.Jackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.condition.EnabledIfLongRunningTest;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.extension.LettuceTestClientResources;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;
import io.valkey.springframework.data.valkey.test.util.CollectionAwareComparator;

/**
 * Integration test of {@link ValkeyTemplate}
 *
 * @author Jennifer Hickey
 * @author Christoph Strobl
 * @author Anqing Shao
 * @author Duobiao Ou
 * @author Mark Paluch
 * @author ihaohong
 * @author Hendrik Duerkop
 * @author Chen Li
 * @author Vedran Pavic
 */
@MethodSource("testParams")
public class ValkeyTemplateIntegrationTests<K, V> {

	protected final ValkeyTemplate<K, V> valkeyTemplate;
	protected final ObjectFactory<K> keyFactory;
	protected final ObjectFactory<V> valueFactory;

	ValkeyTemplateIntegrationTests(ValkeyTemplate<K, V> valkeyTemplate, ObjectFactory<K> keyFactory,
			ObjectFactory<V> valueFactory) {

		this.valkeyTemplate = valkeyTemplate;
		this.keyFactory = keyFactory;
		this.valueFactory = valueFactory;
	}

	public static Collection<Object[]> testParams() {
		return AbstractOperationsTestParams.testParams();
	}

	@BeforeEach
	void setUp() {
		valkeyTemplate.execute((ValkeyCallback<Object>) connection -> {
			connection.flushDb();
			return null;
		});
	}

	@ParameterizedValkeyTest
	void testDumpAndRestoreNoTtl() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key1).set(value1);
		byte[] serializedValue = valkeyTemplate.dump(key1);
		assertThat(serializedValue).isNotNull();
		valkeyTemplate.delete(key1);
		valkeyTemplate.restore(key1, serializedValue, 0, TimeUnit.SECONDS);
		assertThat(valkeyTemplate.boundValueOps(key1).get()).isEqualTo(value1);
	}

	@ParameterizedValkeyTest
	void testRestoreTtl() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key1).set(value1);
		byte[] serializedValue = valkeyTemplate.dump(key1);
		assertThat(serializedValue).isNotNull();
		valkeyTemplate.delete(key1);
		valkeyTemplate.restore(key1, serializedValue, 10000, TimeUnit.MILLISECONDS);
		assertThat(valkeyTemplate.boundValueOps(key1).get()).isEqualTo(value1);
		assertThat(valkeyTemplate.getExpire(key1)).isGreaterThan(1L);
	}

	@SuppressWarnings("unchecked")
	@ParameterizedValkeyTest
	void testKeys() throws Exception {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		assumeThat(key1 instanceof String || key1 instanceof byte[]).isTrue();
		valkeyTemplate.opsForValue().set(key1, value1);
		K keyPattern = key1 instanceof String ? (K) "*" : (K) "*".getBytes();
		assertThat(valkeyTemplate.keys(keyPattern)).isNotNull();
	}

	@ParameterizedValkeyTest // GH-2260
	void testScan() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		assumeThat(key1 instanceof String || key1 instanceof byte[]).isTrue();
		valkeyTemplate.opsForValue().set(key1, value1);
		long count = 0;
		try (Cursor<K> cursor = valkeyTemplate.scan(ScanOptions.scanOptions().count(1).build())) {
			while (cursor.hasNext()) {
				assertThat(cursor.next()).isEqualTo(key1);
				count++;
			}
		}
		assertThat(count).isEqualTo(1);
	}

	@SuppressWarnings("rawtypes")
	@ParameterizedValkeyTest
	void testTemplateNotInitialized() throws Exception {
		ValkeyTemplate tpl = new ValkeyTemplate();
		tpl.setConnectionFactory(valkeyTemplate.getConnectionFactory());
		assertThatIllegalArgumentException().isThrownBy(() -> tpl.exec());
	}

	@ParameterizedValkeyTest
	void testStringTemplateExecutesWithStringConn() {
		assumeThat(valkeyTemplate instanceof StringValkeyTemplate).isTrue();
		String value = valkeyTemplate.execute((ValkeyCallback<String>) connection -> {
			StringValkeyConnection stringConn = (StringValkeyConnection) connection;
			stringConn.set("test", "it");
			return stringConn.get("test");
		});
		assertThat("it").isEqualTo(value);
	}

	@ParameterizedValkeyTest
	public void testExec() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		K listKey = keyFactory.instance();
		V listValue = valueFactory.instance();
		K setKey = keyFactory.instance();
		V setValue = valueFactory.instance();
		K zsetKey = keyFactory.instance();
		V zsetValue = valueFactory.instance();
		List<Object> results = valkeyTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			public List<Object> execute(ValkeyOperations operations) throws DataAccessException {
				operations.multi();
				operations.opsForValue().set(key1, value1);
				operations.opsForValue().get(key1);
				operations.opsForList().leftPush(listKey, listValue);
				operations.opsForList().range(listKey, 0L, 1L);
				operations.opsForSet().add(setKey, setValue);
				operations.opsForSet().members(setKey);
				operations.opsForZSet().add(zsetKey, zsetValue, 1d);
				operations.opsForZSet().rangeWithScores(zsetKey, 0L, -1L);
				return operations.exec();
			}
		});
		List<V> list = Collections.singletonList(listValue);
		Set<V> set = new HashSet<>(Collections.singletonList(setValue));
		Set<TypedTuple<V>> tupleSet = new LinkedHashSet<>(
				Collections.singletonList(new DefaultTypedTuple<>(zsetValue, 1d)));

		assertThat(results).usingElementComparator(CollectionAwareComparator.INSTANCE).containsExactly(true, value1, 1L,
				list, 1L, set, true, tupleSet);
	}

	@ParameterizedValkeyTest
	public void testDiscard() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		V value2 = valueFactory.instance();
		valkeyTemplate.opsForValue().set(key1, value1);
		valkeyTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			public List<Object> execute(ValkeyOperations operations) throws DataAccessException {
				operations.multi();
				operations.opsForValue().set(key1, value2);
				operations.discard();
				return null;
			}
		});
		assertThat(valkeyTemplate.boundValueOps(key1).get()).isEqualTo(value1);
	}

	@ParameterizedValkeyTest
	void testExecCustomSerializer() {
		assumeThat(valkeyTemplate instanceof StringValkeyTemplate).isTrue();
		List<Object> results = valkeyTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			public List<Object> execute(ValkeyOperations operations) throws DataAccessException {
				operations.multi();
				operations.opsForValue().set("foo", "5");
				operations.opsForValue().get("foo");
				operations.opsForValue().append("foo1", "5");
				operations.opsForList().leftPush("foolist", "6");
				operations.opsForList().range("foolist", 0L, 1L);
				operations.opsForSet().add("fooset", "7");
				operations.opsForSet().members("fooset");
				operations.opsForZSet().add("foozset", "9", 1d);
				operations.opsForZSet().rangeWithScores("foozset", 0L, -1L);
				operations.opsForZSet().range("foozset", 0, -1);
				operations.opsForHash().put("foomap", "10", "11");
				operations.opsForHash().entries("foomap");
				return operations.exec(new GenericToStringSerializer<>(Long.class));
			}
		});
		// Everything should be converted to Longs
		List<Long> list = Collections.singletonList(6L);
		Set<Long> longSet = new HashSet<>(Collections.singletonList(7L));
		Set<TypedTuple<Long>> tupleSet = new LinkedHashSet<>(Collections.singletonList(new DefaultTypedTuple<>(9L, 1d)));
		Set<Long> zSet = new LinkedHashSet<>(Collections.singletonList(9L));
		Map<Long, Long> map = new LinkedHashMap<>();
		map.put(10L, 11L);

		assertThat(results).containsExactly(true, 5L, 1L, 1L, list, 1L, longSet, true, tupleSet, zSet, true, map);
	}

	@ParameterizedValkeyTest
	public void testExecConversionDisabled() {

		LettuceConnectionFactory factory2 = new LettuceConnectionFactory(SettingsUtils.getHost(), SettingsUtils.getPort());
		factory2.setClientResources(LettuceTestClientResources.getSharedClientResources());
		factory2.setConvertPipelineAndTxResults(false);
		factory2.afterPropertiesSet();

		ConnectionFactoryTracker.add(factory2);

		StringValkeyTemplate template = new StringValkeyTemplate(factory2);
		template.afterPropertiesSet();
		List<Object> results = template.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			public List<Object> execute(ValkeyOperations operations) throws DataAccessException {
				operations.multi();
				operations.opsForValue().set("foo", "bar");
				operations.opsForValue().get("foo");
				return operations.exec();
			}
		});
		// first value is "OK" from set call, results should still be in byte[]
		assertThat(new String((byte[]) results.get(1))).isEqualTo("bar");
	}

	@SuppressWarnings("rawtypes")
	@ParameterizedValkeyTest
	public void testExecutePipelined() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		K listKey = keyFactory.instance();
		V listValue = valueFactory.instance();
		V listValue2 = valueFactory.instance();
		List<Object> results = valkeyTemplate.executePipelined((ValkeyCallback) connection -> {
			byte[] rawKey = serialize(key1, valkeyTemplate.getKeySerializer());
			byte[] rawListKey = serialize(listKey, valkeyTemplate.getKeySerializer());
			connection.set(rawKey, serialize(value1, valkeyTemplate.getValueSerializer()));
			connection.get(rawKey);
			connection.rPush(rawListKey, serialize(listValue, valkeyTemplate.getValueSerializer()));
			connection.rPush(rawListKey, serialize(listValue2, valkeyTemplate.getValueSerializer()));
			connection.lRange(rawListKey, 0, -1);
			return null;
		});
		assertThat(results).usingElementComparator(CollectionAwareComparator.INSTANCE).containsExactly(true, value1, 1L, 2L,
				Arrays.asList(listValue, listValue2));
	}

	@SuppressWarnings("rawtypes")
	@ParameterizedValkeyTest
	void testExecutePipelinedCustomSerializer() {

		assumeThat(valkeyTemplate instanceof StringValkeyTemplate).isTrue();

		List<Object> results = valkeyTemplate.executePipelined((ValkeyCallback) connection -> {
			StringValkeyConnection stringValkeyConn = (StringValkeyConnection) connection;
			stringValkeyConn.set("foo", "5");
			stringValkeyConn.get("foo");
			stringValkeyConn.rPush("foolist", "10");
			stringValkeyConn.rPush("foolist", "11");
			stringValkeyConn.lRange("foolist", 0, -1);
			return null;
		}, new GenericToStringSerializer<>(Long.class));

		assertThat(results).containsExactly(true, 5L, 1L, 2L, Arrays.asList(10L, 11L));
	}

	@ParameterizedValkeyTest // DATAREDIS-500
	void testExecutePipelinedWidthDifferentHashKeySerializerAndHashValueSerializer() {

		assumeThat(valkeyTemplate instanceof StringValkeyTemplate).isTrue();

		valkeyTemplate.setKeySerializer(StringValkeySerializer.UTF_8);
		valkeyTemplate.setHashKeySerializer(new GenericToStringSerializer<>(Long.class));
		valkeyTemplate.setHashValueSerializer(new Jackson2JsonValkeySerializer<>(Person.class));

		Person person = new Person("Homer", "Simpson", 38);

		valkeyTemplate.opsForHash().put((K) "foo", 1L, person);

		List<Object> results = valkeyTemplate.executePipelined((ValkeyCallback) connection -> {
			connection.hGetAll(((StringValkeySerializer) valkeyTemplate.getKeySerializer()).serialize("foo"));
			return null;
		});

		assertThat(person).isEqualTo(((Map) results.get(0)).get(1L));
	}

	@ParameterizedValkeyTest
	public void testExecutePipelinedNonNullValkeyCallback() {
		assertThatExceptionOfType(InvalidDataAccessApiUsageException.class)
				.isThrownBy(() -> valkeyTemplate.executePipelined((ValkeyCallback<String>) connection -> "Hey There"));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@ParameterizedValkeyTest
	public void testExecutePipelinedTx() {

		assumeThat(valkeyTemplate.getConnectionFactory()).isInstanceOf(LettuceConnectionFactory.class);

		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		List<Object> pipelinedResults = valkeyTemplate.executePipelined(new SessionCallback() {
			public Object execute(ValkeyOperations operations) throws DataAccessException {

				operations.multi();
				operations.opsForList().leftPush(key1, value1);
				operations.opsForList().rightPop(key1);
				operations.opsForList().size(key1);
				operations.exec();

				try {
					// Await EXEC completion as it's executed on a dedicated connection.
					Thread.sleep(100);
				} catch (InterruptedException ignore) {}

				operations.opsForValue().set(key1, value1);
				operations.opsForValue().get(key1);

				return null;
			}
		});
		// Should contain the List of deserialized exec results and the result of the last call to get()
		assertThat(pipelinedResults).usingElementComparator(CollectionAwareComparator.INSTANCE)
				.containsExactly(Arrays.asList(1L, value1, 0L), true, value1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@ParameterizedValkeyTest
	void testExecutePipelinedTxCustomSerializer() {
		assumeThat(valkeyTemplate.getConnectionFactory()).isInstanceOf(LettuceConnectionFactory.class);
		assumeThat(valkeyTemplate instanceof StringValkeyTemplate).isTrue();
		List<Object> pipelinedResults = valkeyTemplate.executePipelined(new SessionCallback() {
			public Object execute(ValkeyOperations operations) throws DataAccessException {
				operations.multi();
				operations.opsForList().leftPush("fooList", "5");
				operations.opsForList().rightPop("fooList");
				operations.opsForList().size("fooList");
				operations.exec();
				operations.opsForValue().set("foo", "2");
				operations.opsForValue().get("foo");
				return null;
			}
		}, new GenericToStringSerializer<>(Long.class));
		// Should contain the List of deserialized exec results and the result of the last call to get()
		assertThat(pipelinedResults).isEqualTo(Arrays.asList(Arrays.asList(1L, 5L, 0L), true, 2L));
	}

	@ParameterizedValkeyTest
	public void testExecutePipelinedNonNullSessionCallback() {
		assertThatExceptionOfType(InvalidDataAccessApiUsageException.class)
				.isThrownBy(() -> valkeyTemplate.executePipelined(new SessionCallback<String>() {
					@SuppressWarnings("rawtypes")
					public String execute(ValkeyOperations operations) throws DataAccessException {
						return "Whatup";
					}
				}));
	}

	@ParameterizedValkeyTest // DATAREDIS-688
	void testDelete() {

		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key1, value1);

		assertThat(valkeyTemplate.hasKey(key1)).isTrue();
		assertThat(valkeyTemplate.delete(key1)).isTrue();
		assertThat(valkeyTemplate.hasKey(key1)).isFalse();
	}

	@ParameterizedValkeyTest
	void testCopy() {

		assumeThat(valkeyTemplate.execute((ValkeyCallback<ValkeyConnection>) it -> it))
				.isNotInstanceOf(ValkeyClusterConnection.class);

		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();
		V value1 = valueFactory.instance();
		V value2 = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key1, value1);

		valkeyTemplate.copy(key1, key2, false);
		assertThat(valkeyTemplate.opsForValue().get(key2)).isEqualTo(value1);

		valkeyTemplate.opsForValue().set(key1, value2);

		valkeyTemplate.copy(key1, key2, true);
		assertThat(valkeyTemplate.opsForValue().get(key2)).isEqualTo(value2);
	}

	@ParameterizedValkeyTest // DATAREDIS-688
	void testDeleteMultiple() {

		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();
		V value1 = valueFactory.instance();
		V value2 = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key1, value1);
		valkeyTemplate.opsForValue().set(key2, value2);

		assertThat(valkeyTemplate.delete(Arrays.asList(key1, key2)).longValue()).isEqualTo(2L);
		assertThat(valkeyTemplate.hasKey(key1)).isFalse();
		assertThat(valkeyTemplate.hasKey(key2)).isFalse();
	}

	@ParameterizedValkeyTest
	void testSort() {

		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();

		assumeThat(value1 instanceof Number).isTrue();

		valkeyTemplate.opsForList().rightPush(key1, value1);

		List<V> results = valkeyTemplate.sort(SortQueryBuilder.sort(key1).build());
		assertThat(results).isEqualTo(Collections.singletonList(value1));
	}

	@ParameterizedValkeyTest
	void testSortStore() {
		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();
		V value1 = valueFactory.instance();
		assumeThat(value1 instanceof Number).isTrue();
		valkeyTemplate.opsForList().rightPush(key1, value1);
		assertThat(valkeyTemplate.sort(SortQueryBuilder.sort(key1).build(), key2)).isEqualTo(Long.valueOf(1));
		assertThat(valkeyTemplate.boundListOps(key2).range(0, -1)).isEqualTo(Collections.singletonList(value1));
	}

	@ParameterizedValkeyTest
	public void testSortBulkMapper() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		assumeThat(value1 instanceof Number).isTrue();
		valkeyTemplate.opsForList().rightPush(key1, value1);
		List<String> results = valkeyTemplate.sort(SortQueryBuilder.sort(key1).get("#").build(), tuple -> "FOO");
		assertThat(results).isEqualTo(Collections.singletonList("FOO"));
	}

	@ParameterizedValkeyTest
	void testExpireAndGetExpireMillis() {

		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key1).set(value1);
		valkeyTemplate.expire(key1, 500, TimeUnit.MILLISECONDS);

		assertThat(valkeyTemplate.getExpire(key1, TimeUnit.MILLISECONDS)).isGreaterThan(0L);
	}

	@ParameterizedValkeyTest // GH-3114
	@EnabledOnCommand("SPUBLISH") // Valkey 7.0
	void testBoundExpireAndGetExpireSeconds() {

		K key = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key).set(value1);

		BoundKeyExpirationOperations exp = valkeyTemplate.expiration(key);

		assertThat(exp.expire(Duration.ofSeconds(5))).isEqualTo(ExpireChanges.ExpiryChangeState.OK);

		assertThat(exp.getTimeToLive(TimeUnit.SECONDS)).satisfies(ttl -> {
			assertThat(ttl.isPersistent()).isFalse();
			assertThat(ttl.value()).isGreaterThan(1);
		});
	}

	@ParameterizedValkeyTest // GH-3114
	@EnabledOnCommand("SPUBLISH") // Valkey 7.0
	void testBoundExpireWithConditionsAndGetExpireSeconds() {

		K key = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key).set(value1);

		BoundKeyExpirationOperations exp = valkeyTemplate.expiration(key);

		assertThat(exp.expire(Duration.ofSeconds(5))).isEqualTo(ExpireChanges.ExpiryChangeState.OK);
		assertThat(exp.expire(Expiration.from(Duration.ofSeconds(1)), ExpirationOptions.builder().gt().build()))
				.isEqualTo(ExpireChanges.ExpiryChangeState.CONDITION_NOT_MET);
		assertThat(exp.expire(Expiration.from(Duration.ofSeconds(10)), ExpirationOptions.builder().gt().build()))
				.isEqualTo(ExpireChanges.ExpiryChangeState.OK);

		assertThat(exp.getTimeToLive(TimeUnit.SECONDS)).satisfies(ttl -> {
			assertThat(ttl.isPersistent()).isFalse();
			assertThat(ttl.value()).isGreaterThan(5);
		});
	}

	@ParameterizedValkeyTest
	void testGetExpireNoTimeUnit() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key1).set(value1);
		valkeyTemplate.expire(key1, 2, TimeUnit.SECONDS);
		Long expire = valkeyTemplate.getExpire(key1);
		// Default behavior is to return seconds
		assertThat(expire > 0L && expire <= 2L).isTrue();
	}

	@ParameterizedValkeyTest
	void testGetExpireSeconds() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key1).set(value1);
		valkeyTemplate.expire(key1, 1500, TimeUnit.MILLISECONDS);
		assertThat(valkeyTemplate.getExpire(key1, TimeUnit.SECONDS)).isEqualTo(Long.valueOf(1));
	}

	@ParameterizedValkeyTest // DATAREDIS-526
	void testGetExpireSecondsForKeyDoesNotExist() {

		Long expire = valkeyTemplate.getExpire(keyFactory.instance(), TimeUnit.SECONDS);
		assertThat(expire).isLessThan(0L);
	}

	@ParameterizedValkeyTest // DATAREDIS-526
	void testGetExpireSecondsForKeyExistButHasNoAssociatedExpire() {

		K key = keyFactory.instance();
		Long expire = valkeyTemplate.getExpire(key, TimeUnit.SECONDS);

		assertThat(expire).isLessThan(0L);
	}

	@ParameterizedValkeyTest // DATAREDIS-526
	void testGetExpireMillisForKeyDoesNotExist() {

		Long expire = valkeyTemplate.getExpire(keyFactory.instance(), TimeUnit.MILLISECONDS);

		assertThat(expire).isLessThan(0L);
	}

	@ParameterizedValkeyTest // DATAREDIS-526
	void testGetExpireMillisForKeyExistButHasNoAssociatedExpire() {

		K key = keyFactory.instance();
		valkeyTemplate.boundValueOps(key).set(valueFactory.instance());

		Long expire = valkeyTemplate.getExpire(key, TimeUnit.MILLISECONDS);

		assertThat(expire).isLessThan(0L);
	}

	@ParameterizedValkeyTest // DATAREDIS-526
	void testGetExpireMillis() {

		K key = keyFactory.instance();
		valkeyTemplate.boundValueOps(key).set(valueFactory.instance());
		valkeyTemplate.expire(key, 1, TimeUnit.DAYS);

		Long ttl = valkeyTemplate.getExpire(key, TimeUnit.HOURS);

		assertThat(ttl).isGreaterThanOrEqualTo(23L);
		assertThat(ttl).isLessThan(25L);
	}

	@ParameterizedValkeyTest // GH-3017
	void testSetGetExpireMillis() {

		K key = keyFactory.instance();
		V value1 = valueFactory.instance();
		V value2 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key).set(value1);

		V oldValue = valkeyTemplate.boundValueOps(key).setGet(value2, 1, TimeUnit.DAYS);
		valkeyTemplate.expire(key, 1, TimeUnit.DAYS);
		Long ttl = valkeyTemplate.getExpire(key, TimeUnit.HOURS);

		assertThat(oldValue).isEqualTo(value1);
		assertThat(ttl).isGreaterThanOrEqualTo(23L);
		assertThat(ttl).isLessThan(25L);
	}

	@ParameterizedValkeyTest // DATAREDIS-611
	void testGetExpireDuration() {

		K key = keyFactory.instance();
		valkeyTemplate.boundValueOps(key).set(valueFactory.instance());
		valkeyTemplate.expire(key, Duration.ofDays(1));

		Long ttl = valkeyTemplate.getExpire(key, TimeUnit.HOURS);

		assertThat(ttl).isGreaterThanOrEqualTo(23L);
		assertThat(ttl).isLessThan(25L);
	}

	@ParameterizedValkeyTest // DATAREDIS-526
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testGetExpireMillisUsingTransactions() {

		K key = keyFactory.instance();
		List<Object> result = valkeyTemplate.execute(new SessionCallback<List<Object>>() {

			@Override
			public List<Object> execute(ValkeyOperations operations) throws DataAccessException {

				operations.multi();
				operations.boundValueOps(key).set(valueFactory.instance());
				operations.expire(key, 1, TimeUnit.DAYS);
				operations.getExpire(key, TimeUnit.HOURS);

				return operations.exec();
			}
		});

		assertThat(result).hasSize(3);
		assertThat(((Long) result.get(2))).isGreaterThanOrEqualTo(23L);
		assertThat(((Long) result.get(2))).isLessThan(25L);
	}

	@ParameterizedValkeyTest // DATAREDIS-526
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testGetExpireMillisUsingPipelining() {

		K key = keyFactory.instance();
		List<Object> result = valkeyTemplate.executePipelined(new SessionCallback<Object>() {

			@Override
			public Object execute(ValkeyOperations operations) throws DataAccessException {

				operations.boundValueOps(key).set(valueFactory.instance());
				operations.expire(key, 1, TimeUnit.DAYS);
				operations.getExpire(key, TimeUnit.HOURS);

				return null;
			}
		});

		assertThat(result).hasSize(3);
		assertThat(((Long) result.get(2))).isGreaterThanOrEqualTo(23L);
		assertThat(((Long) result.get(2))).isLessThan(25L);
	}

	@ParameterizedValkeyTest
	void testExpireAt() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key1).set(value1);
		valkeyTemplate.expireAt(key1, new Date(System.currentTimeMillis() + 5L));
		await().until(() -> !valkeyTemplate.hasKey(key1));
	}

	@ParameterizedValkeyTest // DATAREDIS-611
	void testExpireAtInstant() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.boundValueOps(key1).set(value1);
		valkeyTemplate.expireAt(key1, Instant.now().plus(5, ChronoUnit.MILLIS));
		await().until(() -> !valkeyTemplate.hasKey(key1));
	}

	@ParameterizedValkeyTest
	@EnabledIfLongRunningTest
	void testExpireAtMillisNotSupported() {

		assumeThat(valkeyTemplate.getConnectionFactory() instanceof JedisConnectionFactory).isTrue();

		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();

		assumeThat(key1 instanceof String && value1 instanceof String).isTrue();

		StringValkeyTemplate template2 = new StringValkeyTemplate(valkeyTemplate.getConnectionFactory());
		template2.boundValueOps((String) key1).set((String) value1);
		template2.expireAt((String) key1, new Date(System.currentTimeMillis() + 5L));
		// Just ensure this works as expected, pExpireAt just adds some precision over expireAt
		await().until(() -> !template2.hasKey((String) key1));
	}

	@ParameterizedValkeyTest
	void testRandomKey() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.opsForValue().set(key1, value1);

		for (int i = 0; i < 20; i++) {

			K k = valkeyTemplate.randomKey();
			if (k == null) {
				continue;
			}

			assertThat(k).isEqualTo(key1);
		}
	}

	@ParameterizedValkeyTest
	void testRename() {
		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.opsForValue().set(key1, value1);
		valkeyTemplate.rename(key1, key2);
		assertThat(valkeyTemplate.opsForValue().get(key2)).isEqualTo(value1);
	}

	@ParameterizedValkeyTest
	void testRenameIfAbsent() {
		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.opsForValue().set(key1, value1);
		valkeyTemplate.renameIfAbsent(key1, key2);
		assertThat(valkeyTemplate.hasKey(key2)).isTrue();
	}

	@ParameterizedValkeyTest
	void testType() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		valkeyTemplate.opsForValue().set(key1, value1);
		assertThat(valkeyTemplate.type(key1)).isEqualTo(DataType.STRING);
	}

	@ParameterizedValkeyTest // DATAREDIS-506
	public void testWatch() {
		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		V value2 = valueFactory.instance();
		V value3 = valueFactory.instance();
		valkeyTemplate.opsForValue().set(key1, value1);

		Thread th = new Thread(() -> valkeyTemplate.opsForValue().set(key1, value2));

		List<Object> results = valkeyTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public List<Object> execute(ValkeyOperations operations) throws DataAccessException {

				operations.watch(key1);

				th.start();
				try {
					th.join();
				} catch (InterruptedException ignore) {}

				operations.multi();
				operations.opsForValue().set(key1, value3);
				return operations.exec();
			}
		});

		assertThat(results).isEmpty();

		assertThat(valkeyTemplate.opsForValue().get(key1)).isEqualTo(value2);
	}

	@ParameterizedValkeyTest
	public void testUnwatch() {

		K key1 = keyFactory.instance();
		V value1 = valueFactory.instance();
		V value2 = valueFactory.instance();
		V value3 = valueFactory.instance();
		valkeyTemplate.opsForValue().set(key1, value1);
		Thread th = new Thread(() -> valkeyTemplate.opsForValue().set(key1, value2));

		List<Object> results = valkeyTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public List<Object> execute(ValkeyOperations operations) throws DataAccessException {

				operations.watch(key1);

				th.start();
				try {
					th.join();
				} catch (InterruptedException ignore) {}

				operations.unwatch();
				operations.multi();
				operations.opsForValue().set(key1, value3);
				return operations.exec();
			}
		});

		assertThat(results.size()).isEqualTo(1);
		assertThat(valkeyTemplate.opsForValue().get(key1)).isEqualTo(value3);
	}

	@ParameterizedValkeyTest // DATAREDIS-506
	public void testWatchMultipleKeys() {

		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();
		V value1 = valueFactory.instance();
		V value2 = valueFactory.instance();
		V value3 = valueFactory.instance();
		valkeyTemplate.opsForValue().set(key1, value1);

		Thread th = new Thread(() -> valkeyTemplate.opsForValue().set(key1, value2));

		List<Object> results = valkeyTemplate.execute(new SessionCallback<List<Object>>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public List<Object> execute(ValkeyOperations operations) throws DataAccessException {

				List<K> keys = new ArrayList<>();
				keys.add(key1);
				keys.add(key2);
				operations.watch(keys);

				th.start();
				try {
					th.join();
				} catch (InterruptedException ignore) {}

				operations.multi();
				operations.opsForValue().set(key1, value3);
				return operations.exec();
			}
		});

		assertThat(results).isEmpty();

		assertThat(valkeyTemplate.opsForValue().get(key1)).isEqualTo(value2);
	}

	@ParameterizedValkeyTest
	void testConvertAndSend() {
		V value1 = valueFactory.instance();
		// Make sure basic message sent without Exception on serialization
		assertThat(valkeyTemplate.convertAndSend("Channel", value1)).isEqualTo(0L);
	}

	@ParameterizedValkeyTest
	void testExecuteScriptCustomSerializers() {
		K key1 = keyFactory.instance();
		DefaultValkeyScript<String> script = new DefaultValkeyScript<>();
		script.setScriptText("return 'Hey'");
		script.setResultType(String.class);
		assertThat(valkeyTemplate.execute(script, valkeyTemplate.getValueSerializer(), StringValkeySerializer.UTF_8,
				Collections.singletonList(key1))).isEqualTo("Hey");
	}

	@ParameterizedValkeyTest
	void clientListShouldReturnCorrectly() {
		assertThat(valkeyTemplate.getClientList().size()).isNotEqualTo(0);
	}

	@ParameterizedValkeyTest // DATAREDIS-529
	void countExistingKeysReturnsNumberOfKeysCorrectly() {

		Map<K, V> source = new LinkedHashMap<>(3, 1);
		source.put(keyFactory.instance(), valueFactory.instance());
		source.put(keyFactory.instance(), valueFactory.instance());
		source.put(keyFactory.instance(), valueFactory.instance());

		valkeyTemplate.opsForValue().multiSet(source);

		assertThat(valkeyTemplate.countExistingKeys(source.keySet())).isEqualTo(3L);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private byte[] serialize(Object value, ValkeySerializer serializer) {
		if (serializer == null && value instanceof byte[]) {
			return (byte[]) value;
		}
		return serializer.serialize(value);
	}
}
