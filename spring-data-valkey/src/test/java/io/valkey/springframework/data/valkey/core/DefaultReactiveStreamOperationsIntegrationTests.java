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
import static org.junit.Assume.*;

import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.data.domain.Range;
import org.springframework.data.domain.Range.Bound;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.PersonObjectFactory;
import io.valkey.springframework.data.valkey.connection.Limit;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.XAddOptions;
import io.valkey.springframework.data.valkey.connection.stream.Consumer;
import io.valkey.springframework.data.valkey.connection.stream.MapRecord;
import io.valkey.springframework.data.valkey.connection.stream.ReadOffset;
import io.valkey.springframework.data.valkey.connection.stream.RecordId;
import io.valkey.springframework.data.valkey.connection.stream.StreamOffset;
import io.valkey.springframework.data.valkey.connection.stream.StreamReadOptions;
import io.valkey.springframework.data.valkey.connection.stream.StreamRecords;
import io.valkey.springframework.data.valkey.core.ReactiveOperationsTestParams.Fixture;
import io.valkey.springframework.data.valkey.serializer.GenericJackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.Jackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.JdkSerializationValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.OxmSerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext.SerializationPair;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Integration tests for {@link DefaultReactiveStreamOperations}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Marcin Zielinski
 * @author jinkshower
 */
@MethodSource("testParams")
@SuppressWarnings("unchecked")
@EnabledOnCommand("XADD")
public class DefaultReactiveStreamOperationsIntegrationTests<K, HK, HV> {

	private final ReactiveValkeyTemplate<K, ?> valkeyTemplate;
	private final ReactiveStreamOperations<K, HK, HV> streamOperations;

	private final ObjectFactory<K> keyFactory;
	private final ObjectFactory<HK> hashKeyFactory;
	private final ObjectFactory<HV> valueFactory;

	private final ValkeySerializer<?> serializer;

	public static Collection<Fixture<?, ?>> testParams() {
		return ReactiveOperationsTestParams.testParams();
	}

	public DefaultReactiveStreamOperationsIntegrationTests(Fixture<K, HV> fixture) {

		this.serializer = fixture.getSerializer();
		this.keyFactory = fixture.getKeyFactory();
		this.hashKeyFactory = (ObjectFactory<HK>) keyFactory;
		this.valueFactory = fixture.getValueFactory();

		ValkeySerializationContext<K, ?> context = null;
		if (fixture.getSerializer() != null) {
			context = ValkeySerializationContext.newSerializationContext()
					.value(SerializationPair.fromSerializer(fixture.getSerializer()))
					.hashKey(keyFactory instanceof PersonObjectFactory ? ValkeySerializer.java() : ValkeySerializer.string())
					.hashValue(serializer)
					.key(keyFactory instanceof PersonObjectFactory ? ValkeySerializer.java() : ValkeySerializer.string()).build();
		}

		this.valkeyTemplate = fixture.getTemplate();
		this.streamOperations = fixture.getSerializer() != null ? valkeyTemplate.opsForStream(context)
				: valkeyTemplate.opsForStream();
	}

	@BeforeEach
	void before() {

		ValkeyConnectionFactory connectionFactory = (ValkeyConnectionFactory) valkeyTemplate.getConnectionFactory();
		ValkeyConnection connection = connectionFactory.getConnection();
		connection.flushAll();
		connection.close();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void addShouldAddMessage() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		RecordId messageId = streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();

		streamOperations.range(key, Range.unbounded()) //
				.as(StepVerifier::create) //
				.consumeNextWith(actual -> {

					assertThat(actual.getId()).isEqualTo(messageId);
					assertThat(actual.getStream()).isEqualTo(key);

					if (!(key instanceof byte[] || value instanceof byte[])) {
						assertThat(actual.getValue()).containsEntry(hashKey, value);
					}
				}) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void addShouldAddReadSimpleMessage() {

		assumeTrue(!(serializer instanceof Jackson2JsonValkeySerializer)
				&& !(serializer instanceof GenericJackson2JsonValkeySerializer)
				&& !(serializer instanceof JdkSerializationValkeySerializer) && !(serializer instanceof OxmSerializer));

		K key = keyFactory.instance();
		HV value = valueFactory.instance();

		RecordId messageId = streamOperations.add(StreamRecords.objectBacked(value).withStreamKey(key)).block();

		streamOperations.range((Class<HV>) value.getClass(), key, Range.unbounded()).as(StepVerifier::create) //
				.consumeNextWith(it -> {
					assertThat(it.getId()).isEqualTo(messageId);
					assertThat(it.getStream()).isEqualTo(key);

					assertThat(it.getValue()).isEqualTo(value);

				}) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void addShouldAddReadSimpleMessageWithRawSerializer() {

		assumeTrue(!(serializer instanceof Jackson2JsonValkeySerializer)
				&& !(serializer instanceof GenericJackson2JsonValkeySerializer));

		SerializationPair<K> keySerializer = valkeyTemplate.getSerializationContext().getKeySerializationPair();

		ValkeySerializationContext<K, String> serializationContext = ValkeySerializationContext
				.<K, String> newSerializationContext(StringValkeySerializer.UTF_8).key(keySerializer)
				.hashValue(SerializationPair.raw()).hashKey(SerializationPair.raw()).build();

		ReactiveValkeyTemplate<K, String> raw = new ReactiveValkeyTemplate<>(valkeyTemplate.getConnectionFactory(),
				serializationContext);

		K key = keyFactory.instance();
		Person value = new PersonObjectFactory().instance();

		RecordId messageId = raw.opsForStream().add(StreamRecords.objectBacked(value).withStreamKey(key)).block();

		raw.opsForStream().range((Class<HV>) value.getClass(), key, Range.unbounded()).as(StepVerifier::create) //
				.consumeNextWith(it -> {

					assertThat(it.getId()).isEqualTo(messageId);
					assertThat(it.getStream()).isEqualTo(key);
					assertThat(it.getValue()).isEqualTo(value);
				}) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2915
	void addMaxLenShouldLimitMessagesSize() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();

		HV newValue = valueFactory.instance();
		XAddOptions options = XAddOptions.maxlen(1).approximateTrimming(false);

		RecordId messageId = streamOperations.add(key, Collections.singletonMap(hashKey, newValue), options).block();

		streamOperations.range(key, Range.unbounded()).as(StepVerifier::create).consumeNextWith(actual -> {

			assertThat(actual.getId()).isEqualTo(messageId);
			assertThat(actual.getStream()).isEqualTo(key);
			assertThat(actual).hasSize(1);

			if (!(key instanceof byte[] || value instanceof byte[])) {
				assertThat(actual.getValue()).containsEntry(hashKey, newValue);
			}

		}).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2915
	void addMaxLenShouldLimitSimpleMessagesSize() {

		assumeTrue(!(serializer instanceof Jackson2JsonValkeySerializer)
				&& !(serializer instanceof GenericJackson2JsonValkeySerializer)
				&& !(serializer instanceof JdkSerializationValkeySerializer) && !(serializer instanceof OxmSerializer));

		K key = keyFactory.instance();
		HV value = valueFactory.instance();

		streamOperations.add(StreamRecords.objectBacked(value).withStreamKey(key)).block();

		HV newValue = valueFactory.instance();
		XAddOptions options = XAddOptions.maxlen(1).approximateTrimming(false);

		RecordId messageId = streamOperations.add(StreamRecords.objectBacked(newValue).withStreamKey(key), options).block();

		streamOperations.range((Class<HV>) value.getClass(), key, Range.unbounded()).as(StepVerifier::create)
				.consumeNextWith(actual -> {

					assertThat(actual.getId()).isEqualTo(messageId);
					assertThat(actual.getStream()).isEqualTo(key);
					assertThat(actual.getValue()).isEqualTo(newValue);

				}).expectNextCount(0).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2915
	void addMaxLenShouldLimitSimpleMessageWithRawSerializerSize() {

		assumeTrue(!(serializer instanceof Jackson2JsonValkeySerializer)
				&& !(serializer instanceof GenericJackson2JsonValkeySerializer));

		SerializationPair<K> keySerializer = valkeyTemplate.getSerializationContext().getKeySerializationPair();

		ValkeySerializationContext<K, String> serializationContext = ValkeySerializationContext
				.<K, String> newSerializationContext(StringValkeySerializer.UTF_8).key(keySerializer)
				.hashValue(SerializationPair.raw()).hashKey(SerializationPair.raw()).build();

		ReactiveValkeyTemplate<K, String> raw = new ReactiveValkeyTemplate<>(valkeyTemplate.getConnectionFactory(),
				serializationContext);

		K key = keyFactory.instance();
		Person value = new PersonObjectFactory().instance();

		raw.opsForStream().add(StreamRecords.objectBacked(value).withStreamKey(key)).block();

		Person newValue = new PersonObjectFactory().instance();
		XAddOptions options = XAddOptions.maxlen(1).approximateTrimming(false);

		RecordId messageId = raw.opsForStream().add(StreamRecords.objectBacked(newValue).withStreamKey(key), options)
				.block();

		raw.opsForStream().range((Class<HV>) value.getClass(), key, Range.unbounded()).as(StepVerifier::create)
				.consumeNextWith(it -> {

					assertThat(it.getId()).isEqualTo(messageId);
					assertThat(it.getStream()).isEqualTo(key);
					assertThat(it.getValue()).isEqualTo(newValue);

				}).expectNextCount(0).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2915
	void addMinIdShouldEvictLowerIdMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();
		RecordId messageId1 = streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();

		XAddOptions options = XAddOptions.none().minId(messageId1);

		RecordId messageId2 = streamOperations.add(key, Collections.singletonMap(hashKey, value), options).block();

		streamOperations.range(key, Range.unbounded()).as(StepVerifier::create).consumeNextWith(actual -> {
			assertThat(actual.getId()).isEqualTo(messageId1);
			assertThat(actual.getStream()).isEqualTo(key);
		}).consumeNextWith(actual -> {
			assertThat(actual.getId()).isEqualTo(messageId2);
			assertThat(actual.getStream()).isEqualTo(key);
		}).expectNextCount(0).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2915
	void addMakeNoStreamShouldNotCreateStreamWhenNoStreamExists() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		XAddOptions options = XAddOptions.makeNoStream();

		streamOperations.add(key, Collections.singletonMap(hashKey, value), options).block();

		streamOperations.size(key).as(StepVerifier::create).expectNext(0L).verifyComplete();

		streamOperations.range(key, Range.unbounded()).as(StepVerifier::create).expectNextCount(0L).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2915
	void addMakeNoStreamShouldCreateStreamWhenStreamExists() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();

		XAddOptions options = XAddOptions.makeNoStream();

		streamOperations.add(key, Collections.singletonMap(hashKey, value), options).block();

		streamOperations.size(key).as(StepVerifier::create).expectNext(2L).verifyComplete();

		streamOperations.range(key, Range.unbounded()).as(StepVerifier::create).expectNextCount(2L).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void rangeShouldReportMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		RecordId messageId1 = streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();
		RecordId messageId2 = streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();

		streamOperations
				.range(key, Range.from(Bound.inclusive(messageId1.getValue())).to(Bound.inclusive(messageId2.getValue())),
						Limit.limit().count(1)) //
				.as(StepVerifier::create) //
				.consumeNextWith(actual -> {

					assertThat(actual.getId()).isEqualTo(messageId1);
				}) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void reverseRangeShouldReportMessages() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		RecordId messageId1 = streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();
		RecordId messageId2 = streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();

		streamOperations.reverseRange(key, Range.unbounded()).map(MapRecord::getId) //
				.as(StepVerifier::create) //
				.expectNext(messageId2, messageId1) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void reverseRangeShouldConvertSimpleMessages() {

		assumeTrue(!(serializer instanceof Jackson2JsonValkeySerializer)
				&& !(serializer instanceof GenericJackson2JsonValkeySerializer));

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		RecordId messageId1 = streamOperations.add(StreamRecords.objectBacked(value).withStreamKey(key)).block();
		RecordId messageId2 = streamOperations.add(StreamRecords.objectBacked(value).withStreamKey(key)).block();

		streamOperations.reverseRange((Class<HV>) value.getClass(), key, Range.unbounded()).as(StepVerifier::create)
				.consumeNextWith(it -> assertThat(it.getId()).isEqualTo(messageId2))
				.consumeNextWith(it -> assertThat(it.getId()).isEqualTo(messageId1)).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void readShouldReadMessage() {

		// assumeFalse(valueFactory instanceof PersonObjectFactory);
		// assumeFalse(keyFactory instanceof LongObjectFactory);
		// assumeFalse(keyFactory instanceof DoubleObjectFactory);

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		RecordId messageId = streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();

		streamOperations.read(StreamOffset.create(key, ReadOffset.from("0-0"))) //
				.as(StepVerifier::create) //
				.consumeNextWith(actual -> {

					assertThat(actual.getId()).isEqualTo(messageId);
					assertThat(actual.getStream()).isEqualTo(key);

					if (!(key instanceof byte[] || value instanceof byte[])) {
						assertThat(actual.getValue()).containsEntry(hashKey, value);
					}
				}).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void readShouldReadMessages() {

		assumeFalse(valueFactory instanceof PersonObjectFactory);

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();
		streamOperations.add(key, Collections.singletonMap(hashKey, value)).block();

		streamOperations.read(StreamReadOptions.empty().count(2), StreamOffset.create(key, ReadOffset.from("0-0"))) //
				.as(StepVerifier::create) //
				.expectNextCount(2) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-864
	void sizeShouldReportStreamSize() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		streamOperations.add(key, Collections.singletonMap(hashKey, value)).as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();
		streamOperations.size(key) //
				.as(StepVerifier::create) //
				.expectNext(1L) //
				.verifyComplete();

		streamOperations.add(key, Collections.singletonMap(hashKey, value)).as(StepVerifier::create).expectNextCount(1)
				.verifyComplete();
		streamOperations.size(key) //
				.as(StepVerifier::create) //
				.expectNext(2L) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-1084
	void pendingShouldReadMessageSummary() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		streamOperations.add(key, Collections.singletonMap(hashKey, value)).then().as(StepVerifier::create)
				.verifyComplete();

		streamOperations.createGroup(key, ReadOffset.from("0-0"), "my-group").then().as(StepVerifier::create)
				.verifyComplete();

		streamOperations.read(Consumer.from("my-group", "my-consumer"), StreamOffset.create(key, ReadOffset.lastConsumed()))
				.then().as(StepVerifier::create).verifyComplete();

		streamOperations.pending(key, "my-group").as(StepVerifier::create).assertNext(pending -> {

			assertThat(pending.getTotalPendingMessages()).isOne();
			assertThat(pending.getGroupName()).isEqualTo("my-group");
		}).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-1084
	void pendingShouldReadMessageDetails() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		streamOperations.add(key, Collections.singletonMap(hashKey, value)).then().as(StepVerifier::create)
				.verifyComplete();

		streamOperations.createGroup(key, ReadOffset.from("0-0"), "my-group").then().as(StepVerifier::create)
				.verifyComplete();

		streamOperations.read(Consumer.from("my-group", "my-consumer"), StreamOffset.create(key, ReadOffset.lastConsumed()))
				.then().as(StepVerifier::create).verifyComplete();

		streamOperations.pending(key, "my-group", Range.unbounded(), 10L).as(StepVerifier::create).assertNext(pending -> {

			assertThat(pending).hasSize(1);
			assertThat(pending.get(0).getGroupName()).isEqualTo("my-group");
			assertThat(pending.get(0).getConsumerName()).isEqualTo("my-consumer");
			assertThat(pending.get(0).getTotalDeliveryCount()).isOne();
		}).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2465
	void claimShouldReadMessageDetails() {

		K key = keyFactory.instance();
		HK hashKey = hashKeyFactory.instance();
		HV value = valueFactory.instance();

		Map<HK, HV> content = Collections.singletonMap(hashKey, value);
		RecordId messageId = streamOperations.add(key, content).block();

		streamOperations.createGroup(key, ReadOffset.from("0-0"), "my-group").then().as(StepVerifier::create)
				.verifyComplete();

		streamOperations.read(Consumer.from("my-group", "my-consumer"), StreamOffset.create(key, ReadOffset.lastConsumed()))
				.then().as(StepVerifier::create).verifyComplete();

		streamOperations.claim(key, "my-group", "name", Duration.ZERO, messageId).as(StepVerifier::create)
				.assertNext(claimed -> {
					assertThat(claimed.getStream()).isEqualTo(key);
					assertThat(claimed.getValue()).isEqualTo(content);
					assertThat(claimed.getId()).isEqualTo(messageId);
				}).verifyComplete();
	}
}
