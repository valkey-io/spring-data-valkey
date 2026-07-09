/*
 * Copyright 2026-present the original author or authors.
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
package io.valkey.springframework.data.valkey.messaging;

import static org.mockito.Mockito.*;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.core.ValkeyCallback;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.listener.ChannelTopic;
import io.valkey.springframework.data.valkey.serializer.JdkSerializerMessageConverter;
import io.valkey.springframework.data.valkey.serializer.ValkeyMessageConverters;
import org.springframework.messaging.MessageHeaders;

/**
 * Unit tests for {@link ValkeyMessageSendingTemplate}.
 *
 * @author Mark Paluch
 */
class ValkeyMessageSendingTemplateUnitTests {

	ValkeyOperations<String, String> operationsMock = mock(ValkeyOperations.class);
	ValkeyConnection connectionMock = mock(ValkeyConnection.class);
	ValkeyMessageSendingTemplate template;

	@BeforeEach
	void setUp() {
		when(operationsMock.execute(any(ValkeyCallback.class))).then(invocation -> {
			ValkeyCallback<?> callback = invocation.getArgument(0);
			return callback.doInValkey(connectionMock);
		});

		template = new ValkeyMessageSendingTemplate(operationsMock);
		template.setMessageConverter(ValkeyMessageConverters.createMessageConverter(
				it -> it.addCustomConverter(new JdkSerializerMessageConverter(getClass().getClassLoader()))));
		template.setDefaultDestination(ChannelTopic.of("default-channel"));
	}

	@Test // GH-3339
	void shouldSendStringWithTopicResolution() {

		template.convertAndSend("channel", "message");
		verify(connectionMock).publish(argThat(isBytes("channel")), argThat(isBytes("message")));
	}

	@Test // GH-3339
	void shouldSendStringToDefaultChannel() {

		template.convertAndSend("message");
		verify(connectionMock).publish(argThat(isBytes("default-channel")), argThat(isBytes("message")));
	}

	@Test // GH-3339
	void shouldSendStringMessage() {

		template.convertAndSend(ChannelTopic.of("channel"), "message");
		verify(connectionMock).publish(argThat(isBytes("channel")), argThat(isBytes("message")));
	}

	@Test // GH-3339
	void shouldSendJsonMessage() {

		template.convertAndSend(ChannelTopic.of("channel"), new Person("White", "Walter"));
		verify(connectionMock).publish(argThat(isBytes("channel")),
				argThat(isBytes("{\"lastName\":\"White\",\"firstName\":\"Walter\"}")));
	}

	@Test // GH-3339
	void shouldSendStringAsJson() {

		template.convertAndSend(ChannelTopic.of("channel"), "message",
				Map.of(MessageHeaders.CONTENT_TYPE, "application/json"));
		verify(connectionMock).publish(argThat(isBytes("channel")), argThat(isBytes("\"message\"")));
	}

	@Test // GH-3339
	void shouldSendStringAsJdkSerialized() {

		template.convertAndSend(ChannelTopic.of("channel"), new SerializablePerson("foo", "bar"),
				Map.of(MessageHeaders.CONTENT_TYPE, JdkSerializerMessageConverter.APPLICATION_JAVA_SERIALIZED_OBJECT_VALUE));
		verify(connectionMock).publish(argThat(isBytes("channel")), any(byte[].class));
	}

	record Person(String lastName, String firstName) {
	}

	record SerializablePerson(String lastName, String firstName) implements Serializable {
	}

	static ArgumentMatcher<byte[]> isBytes(String value) {
		return new ArgumentMatcher<>() {
			@Override
			public boolean matches(byte[] argument) {
				return Arrays.equals(argument, value.getBytes());
			}

			@Override
			public String toString() {
				return "\"%s\".getBytes()".formatted(value);
			}
		};
	}

}
