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
package io.valkey.springframework.data.valkey.stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.core.convert.ConversionFailedException;
import io.valkey.springframework.data.valkey.ValkeySystemException;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.stream.ByteBufferRecord;
import io.valkey.springframework.data.valkey.connection.stream.Consumer;
import io.valkey.springframework.data.valkey.connection.stream.MapRecord;
import io.valkey.springframework.data.valkey.connection.stream.ObjectRecord;
import io.valkey.springframework.data.valkey.connection.stream.ReadOffset;
import io.valkey.springframework.data.valkey.connection.stream.Record;
import io.valkey.springframework.data.valkey.connection.stream.StreamOffset;
import io.valkey.springframework.data.valkey.core.ReactiveValkeyTemplate;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext.SerializationPair;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.stream.StreamReceiver.StreamReceiverOptions;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;

/**
 * Integration tests for {@link StreamReceiver}.
 *
 * @author Mark Paluch
 * @author Eddie McDaniel
 * @author John Blum
 */
@EnabledOnCommand("XREAD")
@ExtendWith(LettuceConnectionFactoryExtension.class)
public class StreamReceiverIntegrationTests {

	final LettuceConnectionFactory connectionFactory;
	final StringValkeyTemplate valkeyTemplate;
	final ReactiveValkeyTemplate<String, String> reactiveValkeyTemplate;

	public StreamReceiverIntegrationTests(LettuceConnectionFactory connectionFactory) {

		this.connectionFactory = connectionFactory;
		this.valkeyTemplate = new StringValkeyTemplate(connectionFactory);

		ValkeySerializationContext<String, String> serializationContext = ValkeySerializationContext
				.<String, String> newSerializationContext(StringValkeySerializer.UTF_8).hashKey(SerializationPair.raw())
				.hashValue(SerializationPair.raw()).build();

		this.reactiveValkeyTemplate = new ReactiveValkeyTemplate<>(connectionFactory, serializationContext);
	}

	@BeforeEach
	void before() {

		ValkeyConnection connection = connectionFactory.getConnection();
		connection.flushDb();
		connection.close();
	}

	@Test // DATAREDIS-864
	void shouldReceiveMapRecords() {

		StreamReceiver<String, MapRecord<String, String, String>> receiver = StreamReceiver.create(connectionFactory);

		Flux<MapRecord<String, String, String>> messages = receiver
				.receive(StreamOffset.create("my-stream", ReadOffset.from("0-0")));

		messages.as(StepVerifier::create) //
				.then(() -> reactiveValkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("key", "value"))
						.subscribe())
				.consumeNextWith(it -> {

					assertThat(it.getStream()).isEqualTo("my-stream");
					assertThat(it.getValue()).containsEntry("key", "value");
				}) //
				.thenCancel() //
				.verify(Duration.ofSeconds(5));
	}

	@Test // DATAREDIS-864
	void shouldReceiveSimpleObjectHashRecords() {

		StreamReceiverOptions<String, ObjectRecord<String, String>> receiverOptions = StreamReceiverOptions.builder()
				.targetType(String.class).build();

		StreamReceiver<String, ObjectRecord<String, String>> receiver = StreamReceiver.create(connectionFactory,
				receiverOptions);

		Flux<ObjectRecord<String, String>> messages = receiver.receive(StreamOffset.fromStart("my-stream"));

		messages.as(StepVerifier::create) //
				.then(() -> reactiveValkeyTemplate.opsForStream().add(ObjectRecord.create("my-stream", "foobar")).subscribe())
				.consumeNextWith(it -> {

					assertThat(it.getStream()).isEqualTo("my-stream");
					assertThat(it.getValue()).isEqualTo("foobar");
				}) //
				.thenCancel() //
				.verify(Duration.ofSeconds(5));
	}

	@Test // DATAREDIS-864
	void shouldReceiveObjectHashRecords() {

		StreamReceiverOptions<String, ObjectRecord<String, LoginEvent>> receiverOptions = StreamReceiverOptions.builder()
				.targetType(LoginEvent.class).build();

		StreamReceiver<String, ObjectRecord<String, LoginEvent>> receiver = StreamReceiver.create(connectionFactory,
				receiverOptions);

		Flux<ObjectRecord<String, LoginEvent>> messages = receiver.receive(StreamOffset.fromStart("my-logins"));

		messages.as(StepVerifier::create) //
				.then(() -> reactiveValkeyTemplate.opsForStream()
						.add(ObjectRecord.create("my-logins", new LoginEvent("Walter", "White"))).subscribe())
				.consumeNextWith(it -> {

					assertThat(it.getStream()).isEqualTo("my-logins");
					assertThat(it.getValue()).isEqualTo(new LoginEvent("Walter", "White"));
				}) //
				.thenCancel() //
				.verify(Duration.ofSeconds(5));
	}

	@Test // DATAREDIS-1172
	void shouldReceiveCustomHashValueRecords() {

		SerializationPair<Integer> serializationPair = mock(SerializationPair.class);
		when(serializationPair.read(any(ByteBuffer.class))).thenReturn(345920);

		StreamReceiverOptions<String, MapRecord<String, String, Integer>> receiverOptions = StreamReceiverOptions.builder()
				.<String, Integer> hashValueSerializer(serializationPair).build();

		StreamReceiver<String, MapRecord<String, String, Integer>> receiver = StreamReceiver.create(connectionFactory,
				receiverOptions);

		Flux<MapRecord<String, String, Integer>> messages = receiver.receive(StreamOffset.fromStart("my-stream"));

		messages.as(StepVerifier::create).then(() -> reactiveValkeyTemplate.opsForStream()
				.add("my-stream", Collections.singletonMap("Jesse", "Pinkman")).subscribe()).consumeNextWith(it -> {
					assertThat(it.getStream()).isEqualTo("my-stream");
					assertThat(it.getValue()).contains(entry("Jesse", 345920));
				}).thenCancel().verify(Duration.ofSeconds(5));
	}

	@Test // DATAREDIS-864
	void latestModeLosesMessages() {

		// XADD/XREAD highly timing-dependent as this tests require a poll subscription to receive messages using $ offset.

		StreamReceiverOptions<String, MapRecord<String, String, String>> options = StreamReceiverOptions.builder()
				.pollTimeout(Duration.ofSeconds(4)).build();
		StreamReceiver<String, MapRecord<String, String, String>> receiver = StreamReceiver.create(connectionFactory,
				options);

		Flux<MapRecord<String, String, String>> messages = receiver
				.receive(StreamOffset.create("my-stream", ReadOffset.latest()));

		messages.as(publisher -> StepVerifier.create(publisher, 0)) //
				.thenRequest(1) //
				.thenAwait(Duration.ofMillis(500)) //
				.then(() -> {
					reactiveValkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("key", "value1")).subscribe();
				}) //
				.expectNextCount(1) //
				.then(() -> {
					reactiveValkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("key", "value2")).subscribe();
				}) //
				.thenRequest(1) //
				.thenAwait(Duration.ofMillis(500)) //
				.then(() -> {
					reactiveValkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("key", "value3")).subscribe();
				}).consumeNextWith(it -> {

					assertThat(it.getStream()).isEqualTo("my-stream");
				}) //
				.thenCancel() //
				.verify(Duration.ofSeconds(5));
	}

	@Test // DATAREDIS-864
	void shouldReceiveAsConsumerGroupMessages() {

		StreamReceiver<String, MapRecord<String, String, String>> receiver = StreamReceiver.create(connectionFactory);

		Flux<MapRecord<String, String, String>> messages = receiver.receive(Consumer.from("my-group", "my-consumer-id"),
				StreamOffset.create("my-stream", ReadOffset.lastConsumed()));

		valkeyTemplate.opsForStream().createGroup("my-stream", ReadOffset.from("0-0"), "my-group");
		valkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("key", "value"));
		valkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("key2", "value2"));

		messages.as(StepVerifier::create) //
				.consumeNextWith(it -> {

					assertThat(it.getStream()).isEqualTo("my-stream");

					assertThat(it.getValue().values()).containsAnyOf("value", "value2");
				}).consumeNextWith(it -> {

					assertThat(it.getStream()).isEqualTo("my-stream");
					// assertThat(it.getValue()).containsEntry("key2", "value2");
					assertThat(it.getValue().values()).containsAnyOf("value", "value2");
				}) //
				.thenCancel() //
				.verify(Duration.ofSeconds(5));
	}

	@Test // DATAREDIS-864
	void shouldStopReceivingOnError() {

		StreamReceiverOptions<String, MapRecord<String, String, String>> options = StreamReceiverOptions.builder()
				.pollTimeout(Duration.ofMillis(100)).build();

		StreamReceiver<String, MapRecord<String, String, String>> receiver = StreamReceiver.create(connectionFactory,
				options);

		Flux<MapRecord<String, String, String>> messages = receiver.receive(Consumer.from("my-group", "my-consumer-id"),
				StreamOffset.create("my-stream", ReadOffset.lastConsumed()));

		valkeyTemplate.opsForStream().createGroup("my-stream", ReadOffset.from("0-0"), "my-group");
		valkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("key", "value"));

		messages.as(StepVerifier::create) //
				.expectNextCount(1) //
				.then(() -> reactiveValkeyTemplate.delete("my-stream").subscribe()) //
				.expectError(ValkeySystemException.class) //
				.verify(Duration.ofSeconds(5));
	}

	@Test // DATAREDIS-864
	void shouldResumeFromError() {

		AtomicReference<Throwable> ref = new AtomicReference<>();
		StreamReceiverOptions<String, ObjectRecord<String, Long>> options = StreamReceiverOptions.builder()
				.pollTimeout(Duration.ofMillis(100)).targetType(Long.class).onErrorResume(throwable -> {

					ref.set(throwable);
					return Mono.empty();
				}).build();

		StreamReceiver<String, ObjectRecord<String, Long>> receiver = StreamReceiver.create(connectionFactory, options);

		Flux<ObjectRecord<String, Long>> messages = receiver.receive(StreamOffset.fromStart("my-stream"));

		valkeyTemplate.opsForStream().createGroup("my-stream", ReadOffset.from("0-0"), "my-group");
		valkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("payload", "1"));
		valkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("payload", "foo"));
		valkeyTemplate.opsForStream().add("my-stream", Collections.singletonMap("payload", "3"));

		messages.map(Record::getValue).as(StepVerifier::create) //
				.expectNext(1L) //
				.expectNext(3L) //
				.thenCancel() //
				.verify();

		assertThat(ref.get()).isInstanceOf(ConversionFailedException.class)
				.hasCauseInstanceOf(ConversionFailedException.class).hasRootCauseInstanceOf(NumberFormatException.class);
		assertThat(((ConversionFailedException) ref.get()).getValue()).isInstanceOf(ByteBufferRecord.class);
	}

	static class LoginEvent {

		String firstName, lastName;

		LoginEvent(String firstName, String lastName) {
			this.firstName = firstName;
			this.lastName = lastName;
		}

		public String getFirstName() {
			return this.firstName;
		}

		public void setFirstName(String firstName) {
			this.firstName = firstName;
		}

		public String getLastName() {
			return this.lastName;
		}

		public void setLastName(String lastName) {
			this.lastName = lastName;
		}

		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof LoginEvent that)) {
				return false;
			}

			return Objects.equals(this.getFirstName(), that.getFirstName())
				&& Objects.equals(this.getLastName(), that.getLastName());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getFirstName(), getLastName());
		}

		@Override
		public String toString() {

			return "LoginEvent{" +
				"firstname='" + firstName + '\'' +
				", lastname='" + lastName + '\'' +
				'}';
		}
	}
}
