/*
 * Copyright 2018-2025 the original author or authors.
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.data.domain.Range;
import org.springframework.data.domain.Range.Bound;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.connection.Limit;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.XAddOptions;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.stream.Consumer;
import io.valkey.springframework.data.valkey.connection.stream.MapRecord;
import io.valkey.springframework.data.valkey.connection.stream.ObjectRecord;
import io.valkey.springframework.data.valkey.connection.stream.PendingMessages;
import io.valkey.springframework.data.valkey.connection.stream.PendingMessagesSummary;
import io.valkey.springframework.data.valkey.connection.stream.ReadOffset;
import io.valkey.springframework.data.valkey.connection.stream.RecordId;
import io.valkey.springframework.data.valkey.connection.stream.StreamOffset;
import io.valkey.springframework.data.valkey.connection.stream.StreamReadOptions;
import io.valkey.springframework.data.valkey.connection.stream.StreamRecords;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyDriver;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyVersion;
import io.valkey.springframework.data.valkey.test.condition.ValkeyDetector;
import io.valkey.springframework.data.valkey.test.extension.ValkeyCluster;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Integration test of {@link DefaultStreamOperations}
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Marcin Zielinski
 * @author jinkshower
 */
@MethodSource("testParams")
@EnabledOnCommand("XADD")
public class DefaultStreamOperationsIntegrationTests<K, HK, HV> {

	private final ValkeyTemplate<K, ?> valkeyTemplate;
	private final @EnabledOnValkeyDriver.DriverQualifier ValkeyConnectionFactory connectionFactory;

	private final ObjectFactory<K> keyFactory;
	private final ObjectFactory<HK> hashKeyFactory;
	private final ObjectFactory<HV> hashValueFactory;
	private final StreamOperations<K, HK, HV> streamOps;

	public DefaultStreamOperationsIntegrationTests(ValkeyTemplate<K, ?> valkeyTemplate, ObjectFactory<K> keyFactory,
			ObjectFactory<?> objectFactory) {

		this.valkeyTemplate = valkeyTemplate;
		this.connectionFactory = valkeyTemplate.getRequiredConnectionFactory();
		this.keyFactory = keyFactory;
		this.hashKeyFactory = (ObjectFactory<HK>) keyFactory;
		this.hashValueFactory = (ObjectFactory<HV>) objectFactory;
		streamOps = valkeyTemplate.opsForStream();
	}

	public static Collection<Object[]> testParams() {

		List<Object[]> params = new ArrayList<>();
		params.addAll(AbstractOperationsTestParams
				.testParams(JedisConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class)));

		if (ValkeyDetector.isClusterAvailable()) {
			params.addAll(AbstractOperationsTestParams
					.testParams(JedisConnectionFactoryExtension.getConnectionFactory(ValkeyCluster.class)));
		}

		params.addAll(AbstractOperationsTestParams
				.testParams(LettuceConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class)));

		if (ValkeyDetector.isClusterAvailable()) {
			params.addAll(AbstractOperationsTestParams
					.testParams(LettuceConnectionFactoryExtension.getConnectionFactory(ValkeyCluster.class)));
		}

		params.addAll(AbstractOperationsTestParams
				.testParams(ValkeyGlideConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class)));

		if (ValkeyDetector.isClusterAvailable()) {
			params.addAll(AbstractOperationsTestParams
					.testParams(ValkeyGlideConnectionFactoryExtension.getConnectionFactory(ValkeyCluster.class)));
		}

		return params;
	}

	@BeforeEach
	void setUp() {

		valkeyTemplate.execute((ValkeyCallback<Object>) connection -> {
			connection.flushDb();
			return null;
		});
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void addShouldAddMessage() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId = streamOps.add(key, Collections.singletonMap(hashKey, value));

		List<MapRecord<K, HK, HV>> messages = streamOps.range(key, Range.unbounded());

		assertThat(messages).hasSize(1);

		MapRecord<K, HK, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId);
		assertThat(message.getStream()).isEqualTo(key);

		if (!(key instanceof byte[] || value instanceof byte[])) {
			assertThat(message.getValue()).containsEntry(hashKey, value);
		}
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void addShouldAddReadSimpleMessage() {

		K key = keyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId = streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));

		List<ObjectRecord<K, HV>> messages = streamOps.range((Class<HV>) value.getClass(), key, Range.unbounded());

		assertThat(messages).hasSize(1);

		ObjectRecord<K, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId);
		assertThat(message.getStream()).isEqualTo(key);

		assertThat(message.getValue()).isEqualTo(value);
	}

	@ParameterizedValkeyTest // GH-2915
	void addMaxLenShouldLimitMessagesSize() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		streamOps.add(key, Collections.singletonMap(hashKey, value));

		HV newValue = hashValueFactory.instance();

		XAddOptions options = XAddOptions.maxlen(1).approximateTrimming(false);

		RecordId messageId = streamOps.add(key, Collections.singletonMap(hashKey, newValue), options);

		List<MapRecord<K, HK, HV>> messages = streamOps.range(key, Range.unbounded());

		assertThat(messages).hasSize(1);

		MapRecord<K, HK, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId);
		assertThat(message.getStream()).isEqualTo(key);

		if (!(key instanceof byte[] || value instanceof byte[])) {
			assertThat(message.getValue()).containsEntry(hashKey, newValue);
		}
	}

	@ParameterizedValkeyTest // GH-2915
	void addMaxLenShouldLimitSimpleMessagesSize() {

		K key = keyFactory.instance();
		HV value = hashValueFactory.instance();

		streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));

		HV newValue = hashValueFactory.instance();

		XAddOptions options = XAddOptions.maxlen(1).approximateTrimming(false);

		RecordId messageId = streamOps.add(StreamRecords.objectBacked(newValue).withStreamKey(key), options);

		List<ObjectRecord<K, HV>> messages = streamOps.range((Class<HV>) value.getClass(), key, Range.unbounded());

		assertThat(messages).hasSize(1);

		ObjectRecord<K, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId);
		assertThat(message.getStream()).isEqualTo(key);

		assertThat(message.getValue()).isEqualTo(newValue);
	}

	@ParameterizedValkeyTest // GH-2915
	void addMinIdShouldEvictLowerIdMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		streamOps.add(key, Collections.singletonMap(hashKey, value));
		RecordId messageId1 = streamOps.add(key, Collections.singletonMap(hashKey, value));

		XAddOptions options = XAddOptions.none().minId(messageId1);

		RecordId messageId2 = streamOps.add(key, Collections.singletonMap(hashKey, value), options);

		List<MapRecord<K, HK, HV>> messages = streamOps.range(key, Range.unbounded());

		assertThat(messages).hasSize(2);

		MapRecord<K, HK, HV> message1 = messages.get(0);

		assertThat(message1.getId()).isEqualTo(messageId1);
		assertThat(message1.getStream()).isEqualTo(key);

		MapRecord<K, HK, HV> message2 = messages.get(1);

		assertThat(message2.getId()).isEqualTo(messageId2);
		assertThat(message2.getStream()).isEqualTo(key);

		if (!(key instanceof byte[] || value instanceof byte[])) {
			assertThat(message1.getValue()).containsEntry(hashKey, value);
			assertThat(message2.getValue()).containsEntry(hashKey, value);
		}
	}

	@ParameterizedValkeyTest // GH-2915
	void addMinIdShouldEvictLowerIdSimpleMessages() {

		K key = keyFactory.instance();
		HV value = hashValueFactory.instance();

		streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));
		RecordId messageId1 = streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));

		XAddOptions options = XAddOptions.none().minId(messageId1);

		RecordId messageId2 = streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key), options);

		List<ObjectRecord<K, HV>> messages = streamOps.range((Class<HV>) value.getClass(), key, Range.unbounded());

		assertThat(messages).hasSize(2);

		ObjectRecord<K, HV> message1 = messages.get(0);

		assertThat(message1.getId()).isEqualTo(messageId1);
		assertThat(message1.getStream()).isEqualTo(key);
		assertThat(message1.getValue()).isEqualTo(value);

		ObjectRecord<K, HV> message2 = messages.get(1);

		assertThat(message2.getId()).isEqualTo(messageId2);
		assertThat(message2.getStream()).isEqualTo(key);
		assertThat(message2.getValue()).isEqualTo(value);
	}

	@ParameterizedValkeyTest // GH-2915
	void addMakeNoStreamShouldNotCreateStreamWhenNoStreamExists() {

		K key = keyFactory.instance();
		HV value = hashValueFactory.instance();

		XAddOptions options = XAddOptions.makeNoStream();

		streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key), options);

		assertThat(streamOps.size(key)).isZero();
		assertThat(streamOps.range(key, Range.unbounded())).isEmpty();
	}

	@ParameterizedValkeyTest // GH-2915
	void addMakeNoStreamShouldCreateStreamWhenStreamExists() {

		K key = keyFactory.instance();
		HV value = hashValueFactory.instance();

		streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));

		XAddOptions options = XAddOptions.makeNoStream();

		streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key), options);

		assertThat(streamOps.size(key)).isEqualTo(2);
		assertThat(streamOps.range(key, Range.unbounded())).hasSize(2);
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void simpleMessageReadWriteSymmetry() {

		K key = keyFactory.instance();
		HV value = hashValueFactory.instance();

		assumeThat(value).isNotInstanceOf(Person.class);

		RecordId messageId = streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));

		List<MapRecord<K, HK, HV>> messages = streamOps.range(key, Range.unbounded());

		assertThat(messages).hasSize(1);

		MapRecord<K, HK, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId);
		assertThat(message.getStream()).isEqualTo(key);

		assertThat(message.getValue().values()).containsExactly(value);
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void rangeShouldReportMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId1 = streamOps.add(key, Collections.singletonMap(hashKey, value));
		RecordId messageId2 = streamOps.add(key, Collections.singletonMap(hashKey, value));

		List<MapRecord<K, HK, HV>> messages = streamOps.range(key,
				Range.from(Bound.inclusive(messageId1.getValue())).to(Bound.inclusive(messageId2.getValue())),
				Limit.limit().count(1));

		assertThat(messages).hasSize(1);

		MapRecord<K, HK, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId1);
	}

	@ParameterizedValkeyTest // GH-2044
	@EnabledOnValkeyVersion("6.2")
	void exclusiveRangeShouldReportMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId1 = streamOps.add(key, Collections.singletonMap(hashKey, value));
		RecordId messageId2 = streamOps.add(key, Collections.singletonMap(hashKey, value));

		List<MapRecord<K, HK, HV>> messages = streamOps.range(key,
				Range.from(Bound.exclusive(messageId1.getValue())).to(Bound.inclusive(messageId2.getValue())));

		assertThat(messages).hasSize(1).extracting(MapRecord::getId).contains(messageId2);

		messages = streamOps.range(key,
				Range.from(Bound.inclusive(messageId1.getValue())).to(Bound.exclusive(messageId2.getValue())));

		assertThat(messages).hasSize(1).extracting(MapRecord::getId).contains(messageId1);
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void reverseRangeShouldReportMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId1 = streamOps.add(key, Collections.singletonMap(hashKey, value));
		RecordId messageId2 = streamOps.add(key, Collections.singletonMap(hashKey, value));

		List<MapRecord<K, HK, HV>> messages = streamOps.reverseRange(key, Range.unbounded());

		assertThat(messages).hasSize(2).extracting("id").containsSequence(messageId2, messageId1);
	}

	@ParameterizedValkeyTest // GH-2044
	@EnabledOnValkeyVersion("6.2")
	void exclusiveReverseRangeShouldReportMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId1 = streamOps.add(key, Collections.singletonMap(hashKey, value));
		RecordId messageId2 = streamOps.add(key, Collections.singletonMap(hashKey, value));
		RecordId messageId3 = streamOps.add(key, Collections.singletonMap(hashKey, value));

		List<MapRecord<K, HK, HV>> messages = streamOps.reverseRange(key,
				Range.from(Bound.exclusive(messageId1.getValue())).to(Bound.inclusive(messageId3.getValue())));

		assertThat(messages).hasSize(2).extracting(MapRecord::getId).containsSequence(messageId3, messageId2);

		messages = streamOps.reverseRange(key,
				Range.from(Bound.inclusive(messageId1.getValue())).to(Bound.exclusive(messageId3.getValue())));

		assertThat(messages).hasSize(2).extracting(MapRecord::getId).containsSequence(messageId2, messageId1);
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void reverseRangeShouldConvertSimpleMessages() {

		K key = keyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId1 = streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));
		RecordId messageId2 = streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));

		List<ObjectRecord<K, HV>> messages = streamOps.reverseRange((Class<HV>) value.getClass(), key, Range.unbounded());

		assertThat(messages).hasSize(2).extracting("id").containsSequence(messageId2, messageId1);

		ObjectRecord<K, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId2);
		assertThat(message.getStream()).isEqualTo(key);

		assertThat(message.getValue()).isEqualTo(value);
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void readShouldReadMessage() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId = streamOps.add(key, Collections.singletonMap(hashKey, value));

		List<MapRecord<K, HK, HV>> messages = streamOps.read(StreamOffset.create(key, ReadOffset.from("0-0")));

		assertThat(messages).hasSize(1);

		MapRecord<K, HK, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId);
		assertThat(message.getStream()).isEqualTo(key);

		if (!(key instanceof byte[] || value instanceof byte[])) {
			assertThat(message.getValue()).containsEntry(hashKey, value);
		}
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void readShouldReadSimpleMessage() {

		K key = keyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId1 = streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));
		streamOps.add(StreamRecords.objectBacked(value).withStreamKey(key));

		List<ObjectRecord<K, HV>> messages = streamOps.read((Class<HV>) value.getClass(),
				StreamOffset.create(key, ReadOffset.from("0-0")));

		assertThat(messages).hasSize(2);

		ObjectRecord<K, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId1);
		assertThat(message.getStream()).isEqualTo(key);

		assertThat(message.getValue()).isEqualTo(value);
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void readShouldReadMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		streamOps.add(key, Collections.singletonMap(hashKey, value));
		streamOps.add(key, Collections.singletonMap(hashKey, value));

		List<MapRecord<K, HK, HV>> messages = streamOps.read(StreamReadOptions.empty().count(2),
				StreamOffset.create(key, ReadOffset.from("0-0")));

		assertThat(messages).hasSize(2);
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void readShouldReadMessageWithConsumerGroup() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId = streamOps.add(key, Collections.singletonMap(hashKey, value));
		streamOps.createGroup(key, ReadOffset.from("0-0"), "my-group");

		List<MapRecord<K, HK, HV>> messages = streamOps.read(Consumer.from("my-group", "my-consumer"),
				StreamOffset.create(key, ReadOffset.lastConsumed()));

		assertThat(messages).hasSize(1);

		MapRecord<K, HK, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId);
		assertThat(message.getStream()).isEqualTo(key);

		if (!(key instanceof byte[] || value instanceof byte[])) {
			assertThat(message.getValue()).containsEntry(hashKey, value);
		}
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void sizeShouldReportStreamSize() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		streamOps.add(key, Collections.singletonMap(hashKey, value));
		assertThat(streamOps.size(key)).isEqualTo(1);

		streamOps.add(key, Collections.singletonMap(hashKey, value));
		assertThat(streamOps.size(key)).isEqualTo(2);
	}

	@ParameterizedValkeyTest // DATAREDIS-1084
	void pendingShouldReadMessageSummary() {
		// XPENDING summary not supported by Jedis
		assumeThat(valkeyTemplate.getRequiredConnectionFactory()).isInstanceOf(LettuceConnectionFactory.class);

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId = streamOps.add(key, Collections.singletonMap(hashKey, value));
		streamOps.createGroup(key, ReadOffset.from("0-0"), "my-group");

		streamOps.read(Consumer.from("my-group", "my-consumer"), StreamOffset.create(key, ReadOffset.lastConsumed()));

		PendingMessagesSummary pending = streamOps.pending(key, "my-group");

		assertThat(pending.getTotalPendingMessages()).isOne();
		assertThat(pending.getGroupName()).isEqualTo("my-group");
	}

	@ParameterizedValkeyTest // DATAREDIS-1084
	void pendingShouldReadMessageDetails() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId = streamOps.add(key, Collections.singletonMap(hashKey, value));
		streamOps.createGroup(key, ReadOffset.from("0-0"), "my-group");

		streamOps.read(Consumer.from("my-group", "my-consumer"), StreamOffset.create(key, ReadOffset.lastConsumed()));

		PendingMessages pending = streamOps.pending(key, "my-group", Range.unbounded(), 10L);

		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).getGroupName()).isEqualTo("my-group");
		assertThat(pending.get(0).getConsumerName()).isEqualTo("my-consumer");
		assertThat(pending.get(0).getTotalDeliveryCount()).isOne();
	}

	@ParameterizedValkeyTest // GH-2465
	void claimShouldReadMessageDetails() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = hashValueFactory.instance();

		RecordId messageId = streamOps.add(key, Collections.singletonMap(hashKey, value));
		streamOps.createGroup(key, ReadOffset.from("0-0"), "my-group");
		streamOps.read(Consumer.from("my-group", "name"), StreamOffset.create(key, ReadOffset.lastConsumed()));

		List<MapRecord<K, HK, HV>> messages = streamOps.claim(key, "my-group", "new-owner", Duration.ZERO, messageId);

		assertThat(messages).hasSize(1);

		MapRecord<K, HK, HV> message = messages.get(0);

		assertThat(message.getId()).isEqualTo(messageId);
		assertThat(message.getStream()).isEqualTo(key);

		if (!(key instanceof byte[] || value instanceof byte[])) {
			assertThat(message.getValue()).containsEntry(hashKey, value);
		}
	}
}
