/*
 * Copyright 2017-2025 the original author or authors.
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.PersonObjectFactory;
import io.valkey.springframework.data.valkey.StringObjectFactory;
import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveSubscription.ChannelMessage;
import io.valkey.springframework.data.valkey.connection.ReactiveSubscription.Message;
import io.valkey.springframework.data.valkey.connection.ReactiveSubscription.PatternMessage;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.ReactiveOperationsTestParams.Fixture;
import io.valkey.springframework.data.valkey.core.script.DefaultValkeyScript;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import io.valkey.springframework.data.valkey.serializer.Jackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.JdkSerializationValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext.SerializationPair;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.condition.EnabledIfLongRunningTest;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Integration tests for {@link ReactiveValkeyTemplate}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Dahye Anne Lee
 */
@MethodSource("testParams")
public class ReactiveValkeyTemplateIntegrationTests<K, V> {

	private final ReactiveValkeyTemplate<K, V> valkeyTemplate;

	private final ObjectFactory<K> keyFactory;

	private final ObjectFactory<V> valueFactory;

	public static Collection<Fixture<?, ?>> testParams() {
		return ReactiveOperationsTestParams.testParams();
	}

	public ReactiveValkeyTemplateIntegrationTests(Fixture<K, V> fixture) {

		this.valkeyTemplate = fixture.getTemplate();
		this.keyFactory = fixture.getKeyFactory();
		this.valueFactory = fixture.getValueFactory();
	}

	@BeforeEach
	void before() {

		ValkeyConnectionFactory connectionFactory = (ValkeyConnectionFactory) valkeyTemplate.getConnectionFactory();
		ValkeyConnection connection = connectionFactory.getConnection();
		connection.flushAll();
		connection.close();
	}

	@ParameterizedValkeyTest // GH-2040
	@EnabledOnCommand("COPY")
	void copy() {

		try (ReactiveValkeyClusterConnection connection = valkeyTemplate.getConnectionFactory()
				.getReactiveClusterConnection()){
			assumeThat(connection).isNull();
		} catch (InvalidDataAccessApiUsageException ignore) {
		}

		K key = keyFactory.instance();
		K targetKey = keyFactory.instance();
		V value = valueFactory.instance();
		V nextValue = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();
		valkeyTemplate.copy(key, targetKey, false).as(StepVerifier::create).expectNext(true).verifyComplete();
		valkeyTemplate.opsForValue().get(targetKey).as(StepVerifier::create).expectNext(value).verifyComplete();

		valkeyTemplate.opsForValue().set(key, nextValue).as(StepVerifier::create).expectNext(true).verifyComplete();
		valkeyTemplate.copy(key, targetKey, true).as(StepVerifier::create).expectNext(true).verifyComplete();
		valkeyTemplate.opsForValue().get(targetKey).as(StepVerifier::create).expectNext(nextValue).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void exists() {

		K key = keyFactory.instance();

		valkeyTemplate.hasKey(key).as(StepVerifier::create).expectNext(false).verifyComplete();

		valkeyTemplate.opsForValue().set(key, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.hasKey(key).as(StepVerifier::create).expectNext(true).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2883
	void countExistingKeysIfValidKeyExists() {

		K key = keyFactory.instance();
		K key2 = keyFactory.instance();
		K key3 = keyFactory.instance();

		ReactiveValueOperations<K, V> ops = valkeyTemplate.opsForValue();

		ops.set(key, valueFactory.instance()).as(StepVerifier::create).expectNext(true).verifyComplete();
		ops.set(key2, valueFactory.instance()).as(StepVerifier::create).expectNext(true).verifyComplete();
		ops.set(key3, valueFactory.instance()).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.countExistingKeys(Arrays.asList(key, key2, key3)).as(StepVerifier::create).expectNext(3L)
				.verifyComplete();
	}

	@ParameterizedValkeyTest // GH-2883
	void countExistingKeysIfNotValidKeyExists() {

		K key = keyFactory.instance();
		valkeyTemplate.countExistingKeys(List.of(key)).as(StepVerifier::create).expectNext(0L).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-743
	void scan() {

		assumeThat(valueFactory.instance() instanceof Person).isFalse();

		Map<K, V> tuples = new HashMap<>();
		tuples.put(keyFactory.instance(), valueFactory.instance());
		tuples.put(keyFactory.instance(), valueFactory.instance());
		tuples.put(keyFactory.instance(), valueFactory.instance());

		valkeyTemplate.opsForValue().multiSet(tuples).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.scan().collectList().as(StepVerifier::create) //
				.consumeNextWith(actual -> assertThat(actual).containsAll(tuples.keySet())) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void type() {

		K key = keyFactory.instance();

		valkeyTemplate.type(key).as(StepVerifier::create).expectNext(DataType.NONE).verifyComplete();

		valkeyTemplate.opsForValue().set(key, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.type(key).as(StepVerifier::create).expectNext(DataType.STRING).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void rename() {

		K oldName = keyFactory.instance();
		K newName = keyFactory.instance();

		valkeyTemplate.opsForValue().set(oldName, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.rename(oldName, newName).as(StepVerifier::create) //
				.expectNext(true) //
				.expectComplete() //
				.verify();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void renameNx() {

		K oldName = keyFactory.instance();
		K existing = keyFactory.instance();
		K newName = keyFactory.instance();

		valkeyTemplate.opsForValue().set(oldName, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();
		valkeyTemplate.opsForValue().set(existing, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.renameIfAbsent(oldName, newName).as(StepVerifier::create) //
				.expectNext(true) //
				.expectComplete() //
				.verify();

		valkeyTemplate.opsForValue().set(existing, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.renameIfAbsent(newName, existing).as(StepVerifier::create).expectNext(false) //
				.expectComplete() //
				.verify();
	}

	@ParameterizedValkeyTest // DATAREDIS-693
	void unlink() {

		K single = keyFactory.instance();

		valkeyTemplate.opsForValue().set(single, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.unlink(single).as(StepVerifier::create).expectNext(1L).verifyComplete();

		valkeyTemplate.hasKey(single).as(StepVerifier::create).expectNext(false).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-693
	void unlinkMany() {

		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();

		valkeyTemplate.opsForValue().set(key1, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();
		valkeyTemplate.opsForValue().set(key2, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.unlink(key1, key2).as(StepVerifier::create).expectNext(2L).verifyComplete();

		valkeyTemplate.hasKey(key1).as(StepVerifier::create).expectNext(false).verifyComplete();
		valkeyTemplate.hasKey(key2).as(StepVerifier::create).expectNext(false).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-913
	void unlinkManyPublisher() {

		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();

		assumeThat(key1 instanceof String && valueFactory instanceof StringObjectFactory).isTrue();

		valkeyTemplate.opsForValue().set(key1, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();
		valkeyTemplate.opsForValue().set(key2, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.unlink(valkeyTemplate.keys((K) "*")).as(StepVerifier::create).expectNext(2L).verifyComplete();

		valkeyTemplate.hasKey(key1).as(StepVerifier::create).expectNext(false).verifyComplete();
		valkeyTemplate.hasKey(key2).as(StepVerifier::create).expectNext(false).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-913
	void deleteManyPublisher() {

		K key1 = keyFactory.instance();
		K key2 = keyFactory.instance();

		assumeThat(key1 instanceof String && valueFactory instanceof StringObjectFactory).isTrue();

		valkeyTemplate.opsForValue().set(key1, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();
		valkeyTemplate.opsForValue().set(key2, valueFactory.instance()).as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		valkeyTemplate.delete(valkeyTemplate.keys((K) "*")).as(StepVerifier::create).expectNext(2L).verifyComplete();

		valkeyTemplate.hasKey(key1).as(StepVerifier::create).expectNext(false).verifyComplete();
		valkeyTemplate.hasKey(key2).as(StepVerifier::create).expectNext(false).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-683
	@SuppressWarnings("unchecked")
	void executeScript() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		assumeThat(value instanceof Long).isFalse();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		Flux<V> execute = valkeyTemplate.execute(
				new DefaultValkeyScript<>("return redis.call('get', KEYS[1])", (Class<V>) value.getClass()),
				Collections.singletonList(key));

		execute.as(StepVerifier::create).expectNext(value).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-683
	void executeScriptWithElementReaderAndWriter() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		SerializationPair json = SerializationPair.fromSerializer(new Jackson2JsonValkeySerializer<>(Person.class));
		ValkeyElementReader<String> resultReader = ValkeyElementReader.from(StringValkeySerializer.UTF_8);

		assumeThat(value instanceof Long).isFalse();

		Person person = new Person("Walter", "White", 51);
		valkeyTemplate
				.execute(new DefaultValkeyScript<>("return redis.call('set', KEYS[1], ARGV[1])", String.class),
						Collections.singletonList(key), Collections.singletonList(person), json.getWriter(), resultReader)
				.as(StepVerifier::create)
				.expectNext("OK").verifyComplete();

		Flux<Person> execute = valkeyTemplate.execute(
				new DefaultValkeyScript<>("return redis.call('get', KEYS[1])", Person.class), Collections.singletonList(key),
				Collections.emptyList(), json.getWriter(), json.getReader());

		execute.as(StepVerifier::create).expectNext(person).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void expire() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.expire(key, Duration.ofSeconds(10)).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.getExpire(key).as(StepVerifier::create) //
				.consumeNextWith(actual -> assertThat(actual).isGreaterThan(Duration.ofSeconds(8))).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-3114
	@EnabledOnCommand("SPUBLISH") // Valkey 7.0
	void expireWithCondition() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.expire(key, Expiration.seconds(10), ExpirationOptions.none()).as(StepVerifier::create)
				.expectNext(ExpireChanges.ExpiryChangeState.OK).verifyComplete();
		valkeyTemplate.expire(key, Expiration.seconds(20), ExpirationOptions.builder().lt().build()).as(StepVerifier::create)
				.expectNext(ExpireChanges.ExpiryChangeState.CONDITION_NOT_MET).verifyComplete();

		valkeyTemplate.getExpire(key).as(StepVerifier::create) //
				.consumeNextWith(actual -> assertThat(actual).isGreaterThan(Duration.ofSeconds(5))).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void preciseExpire() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.expire(key, Duration.ofMillis(10_001)).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.getExpire(key).as(StepVerifier::create) //
				.consumeNextWith(actual -> assertThat(actual).isGreaterThan(Duration.ofSeconds(8))).verifyComplete();
	}

	@ParameterizedValkeyTest // GH-3114
	@EnabledOnCommand("SPUBLISH") // Valkey 7.0
	void preciseExpireWithCondition() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.expire(key, Expiration.milliseconds(10000), ExpirationOptions.none()).as(StepVerifier::create)
				.expectNext(ExpireChanges.ExpiryChangeState.OK).verifyComplete();
		valkeyTemplate.expire(key, Expiration.milliseconds(20000), ExpirationOptions.builder().lt().build())
				.as(StepVerifier::create).expectNext(ExpireChanges.ExpiryChangeState.CONDITION_NOT_MET).verifyComplete();

		valkeyTemplate.getExpire(key).as(StepVerifier::create) //
				.consumeNextWith(actual -> assertThat(actual).isGreaterThan(Duration.ofSeconds(5))).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void expireAt() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		Instant expireAt = Instant.ofEpochSecond(Instant.now().plus(Duration.ofSeconds(10)).getEpochSecond());

		valkeyTemplate.expireAt(key, expireAt).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.getExpire(key).as(StepVerifier::create) //
				.consumeNextWith(actual -> assertThat(actual).isGreaterThan(Duration.ofSeconds(8))) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void preciseExpireAt() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		Instant expireAt = Instant.ofEpochSecond(Instant.now().plus(Duration.ofSeconds(10)).getEpochSecond(), 5);

		valkeyTemplate.expireAt(key, expireAt).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.getExpire(key).as(StepVerifier::create) //
				.consumeNextWith(actual -> assertThat(actual).isGreaterThan(Duration.ofSeconds(8))) //
				.verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void getTtlForAbsentKeyShouldCompleteWithoutValue() {

		K key = keyFactory.instance();

		valkeyTemplate.getExpire(key).as(StepVerifier::create).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void getTtlForKeyWithoutExpiryShouldCompleteWithZeroDuration() {

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		valkeyTemplate.getExpire(key).as(StepVerifier::create).expectNext(Duration.ZERO).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void move() {

		try (ReactiveValkeyClusterConnection connection = valkeyTemplate.getConnectionFactory()
				.getReactiveClusterConnection()) {
			assumeThat(connection).isNull();
		} catch (InvalidDataAccessApiUsageException ignore) {
		}

		K key = keyFactory.instance();
		V value = valueFactory.instance();

		valkeyTemplate.opsForValue().set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();
		valkeyTemplate.move(key, 5).as(StepVerifier::create).expectNext(true).verifyComplete();
		valkeyTemplate.hasKey(key).as(StepVerifier::create).expectNext(false).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void shouldApplyCustomSerializationContextToValues() {

		Person key = new PersonObjectFactory().instance();
		Person value = new PersonObjectFactory().instance();

		JdkSerializationValkeySerializer jdkSerializer = new JdkSerializationValkeySerializer();
		ValkeySerializationContext<Object, Object> objectSerializers = ValkeySerializationContext.newSerializationContext()
				.key(jdkSerializer) //
				.value(jdkSerializer) //
				.hashKey(jdkSerializer) //
				.hashValue(jdkSerializer) //
				.build();

		ReactiveValueOperations<Object, Object> valueOperations = valkeyTemplate.opsForValue(objectSerializers);

		valueOperations.set(key, value).as(StepVerifier::create).expectNext(true).verifyComplete();

		valueOperations.get(key).as(StepVerifier::create).expectNext(value).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-602
	void shouldApplyCustomSerializationContextToHash() {

		ValkeySerializationContext<K, V> serializationContext = valkeyTemplate.getSerializationContext();

		K key = keyFactory.instance();
		String hashField = "foo";
		Person hashValue = new PersonObjectFactory().instance();

		ValkeySerializationContext<K, V> objectSerializers = ValkeySerializationContext.<K, V> newSerializationContext()
				.key(serializationContext.getKeySerializationPair()) //
				.value(serializationContext.getValueSerializationPair()) //
				.hashKey(StringValkeySerializer.UTF_8) //
				.hashValue(new JdkSerializationValkeySerializer()) //
				.build();

		ReactiveHashOperations<K, String, Object> hashOperations = valkeyTemplate.opsForHash(objectSerializers);

		hashOperations.put(key, hashField, hashValue).as(StepVerifier::create).expectNext(true).verifyComplete();

		hashOperations.get(key, hashField).as(StepVerifier::create).expectNext(hashValue).verifyComplete();
	}

	@ParameterizedValkeyTest // DATAREDIS-612
	@EnabledIfLongRunningTest
	void listenToChannelShouldReceiveChannelMessagesCorrectly() throws InterruptedException {

		String channel = "my-channel";

		V message = valueFactory.instance();

		valkeyTemplate.listenToChannel(channel).as(StepVerifier::create) //
				.thenAwait(Duration.ofMillis(500)) // just make sure we the subscription completed
				.then(() -> valkeyTemplate.convertAndSend(channel, message).subscribe()) //
				.assertNext(received -> {

					assertThat(received).isInstanceOf(ChannelMessage.class);
					assertThat(received.getMessage()).isEqualTo(message);
					assertThat(received.getChannel()).isEqualTo(channel);
				}) //
				.thenAwait(Duration.ofMillis(10)) //
				.thenCancel() //
				.verify(Duration.ofSeconds(3));
	}

	@ParameterizedValkeyTest // GH-1622
	@EnabledIfLongRunningTest
	void listenToLaterChannelShouldReceiveChannelMessagesCorrectly() {

		String channel = "my-channel";

		V message = valueFactory.instance();

		valkeyTemplate.listenToChannelLater(channel) //
				.doOnNext(it -> valkeyTemplate.convertAndSend(channel, message).subscribe()).flatMapMany(Function.identity()) //
				.cast(Message.class)  // why? java16 why?
				.as(StepVerifier::create) //
				.assertNext(received -> {

					assertThat(received).isInstanceOf(ChannelMessage.class);
					assertThat(received.getMessage()).isEqualTo(message);
					assertThat(received.getChannel()).isEqualTo(channel);
				}) //
				.thenAwait(Duration.ofMillis(10)) //
				.thenCancel() //
				.verify(Duration.ofSeconds(3));
	}

	@ParameterizedValkeyTest // DATAREDIS-612
	void listenToPatternShouldReceiveChannelMessagesCorrectly() {

		String channel = "my-channel";
		String pattern = "my-*";

		V message = valueFactory.instance();

		Flux<? extends Message<String, V>> stream = valkeyTemplate.listenToPattern(pattern);

		stream.as(StepVerifier::create) //
				.thenAwait(Duration.ofMillis(500)) // just make sure we the subscription completed
				.then(() -> valkeyTemplate.convertAndSend(channel, message).subscribe()) //
				.assertNext(received -> {

					assertThat(received).isInstanceOf(PatternMessage.class);
					assertThat(received.getMessage()).isEqualTo(message);
					assertThat(received.getChannel()).isEqualTo(channel);
					assertThat(((PatternMessage) received).getPattern()).isEqualTo(pattern);
				}) //
				.thenCancel() //
				.verify(Duration.ofSeconds(3));
	}

	@ParameterizedValkeyTest // GH-1622
	void listenToPatternLaterShouldReceiveChannelMessagesCorrectly() {

		String channel = "my-channel";
		String pattern = "my-*";

		V message = valueFactory.instance();

		Mono<Flux<? extends Message<String, V>>> stream = valkeyTemplate.listenToPatternLater(pattern);

		stream.doOnNext(it -> valkeyTemplate.convertAndSend(channel, message).subscribe()) //
				.flatMapMany(Function.identity()) //
				.cast(Message.class) // why? java16 why?
				.as(StepVerifier::create) //
				.assertNext(received -> {

					assertThat(received).isInstanceOf(PatternMessage.class);
					assertThat(received.getMessage()).isEqualTo(message);
					assertThat(received.getChannel()).isEqualTo(channel);
					assertThat(((PatternMessage) received).getPattern()).isEqualTo(pattern);
				}) //
				.thenCancel() //
				.verify(Duration.ofSeconds(3));
	}

}
