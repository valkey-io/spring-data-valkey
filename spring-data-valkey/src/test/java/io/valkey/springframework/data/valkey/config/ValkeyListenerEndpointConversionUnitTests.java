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
package io.valkey.springframework.data.valkey.config;

import static org.mockito.Mockito.*;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import io.valkey.springframework.data.valkey.annotation.ValkeyListener;
import io.valkey.springframework.data.valkey.connection.DefaultMessage;
import io.valkey.springframework.data.valkey.listener.StringMessage;
import io.valkey.springframework.data.valkey.listener.adapter.HandlerMethodMessageListenerAdapter;
import io.valkey.springframework.data.valkey.serializer.JdkSerializationValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.JdkSerializerMessageConverter;
import io.valkey.springframework.data.valkey.serializer.ValkeyMessageConverters;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * Unit tests for {@link ValkeyListenerEndpointRegistrar} applying various conversions.
 *
 * @author Ilyass Bougati
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
class ValkeyListenerEndpointConversionUnitTests {

	@Mock ValkeyListenerEndpointRegistry endpointRegistry;

	@Test // GH-1004
	void shouldInjectConvertedPayload() throws NoSuchMethodException {

		ValkeyListenerEndpointRegistrar registrar = configureRegistrar(new ValkeyListenerConfigurer() {});

		AnnotatedService bean = mock(AnnotatedService.class);
		Method method = AnnotatedService.class.getMethod("handle", Person.class, String.class, byte[].class);

		HandlerMethodMessageListenerAdapter listener = createListener(bean, method, registrar);

		String json = "{\"firstname\":\"Walter\",\"lastname\":\"White\"}";
		listener.onMessage(new StringMessage("test-channel", json), null);

		verify(bean).handle(new Person("Walter", "White"), json, json.getBytes(StandardCharsets.UTF_8));
	}

	@Test // GH-1004
	void shouldInjectSerializedPayload() throws NoSuchMethodException {

		JdkSerializationValkeySerializer serializer = new JdkSerializationValkeySerializer();
		ValkeyListenerEndpointRegistrar registrar = configureRegistrar(new ValkeyListenerConfigurer() {
			@Override
			public void configureMessageConverters(ValkeyMessageConverters.Builder builder) {
				builder.addCustomConverter(serializer);
			}
		});

		Person person = new Person("Walter", "White");
		byte[] bytes = serializer.serialize(person);

		AnnotatedService bean = mock(AnnotatedService.class);
		Method method = AnnotatedService.class.getMethod("handle", Person.class);

		HandlerMethodMessageListenerAdapter listener = createListener(bean, method, registrar);

		listener.onMessage(new DefaultMessage("test-channel".getBytes(), bytes), null);

		verify(bean).handle(person);
	}

	@Test // GH-1004
	void shouldInjectNegotiatedSerializedPayload() throws NoSuchMethodException {

		JdkSerializationValkeySerializer serializer = new JdkSerializationValkeySerializer();
		ValkeyListenerEndpointRegistrar registrar = configureRegistrar(new ValkeyListenerConfigurer() {
			@Override
			public void configureMessageConverters(ValkeyMessageConverters.Builder builder) {
				builder.addCustomConverter(serializer);
			}
		});

		Person person = new Person("Walter", "White");
		byte[] bytes = serializer.serialize(person);

		AnnotatedService bean = mock(AnnotatedService.class);
		Method method = AnnotatedService.class.getMethod("handleJdkSerialization", Person.class);

		HandlerMethodMessageListenerAdapter listener = createListener(bean, method, registrar);

		listener.onMessage(new DefaultMessage("test-channel".getBytes(), bytes), null);

		verify(bean).handleJdkSerialization(person);
	}

	@Test // GH-1004
	void shouldSelectConverterCorrectly() throws NoSuchMethodException {

		JdkSerializationValkeySerializer serializer = new JdkSerializationValkeySerializer();
		ValkeyListenerEndpointRegistrar registrar = configureRegistrar(new ValkeyListenerConfigurer() {
			@Override
			public void configureMessageConverters(ValkeyMessageConverters.Builder builder) {
				builder.addCustomConverter(serializer);
			}
		});

		Person person = new Person("Walter", "White");

		AnnotatedService bean = mock(AnnotatedService.class);
		Method method = AnnotatedService.class.getMethod("handleJson", Person.class);

		HandlerMethodMessageListenerAdapter listener = createListener(bean, method, registrar);

		String json = "{\"firstname\":\"Walter\",\"lastname\":\"White\"}";
		listener.onMessage(new StringMessage("test-channel", json), null);

		verify(bean).handleJson(person);
	}

	private static HandlerMethodMessageListenerAdapter createListener(Object bean, Method method,
			ValkeyListenerEndpointRegistrar registrar) {

		MethodValkeyListenerEndpoint endpoint = new MethodValkeyListenerEndpoint(bean, method);
		endpoint.setId("id");
		endpoint.setMessageHandlerMethodFactory(registrar.getMessageHandlerMethodFactory());
		MergedAnnotation<ValkeyListener> annotation = MergedAnnotations.from(method).get(ValkeyListener.class);
		endpoint.setConsumes(annotation.getString("consumes"));

		return endpoint.createListener();
	}

	private ValkeyListenerEndpointRegistrar configureRegistrar(ValkeyListenerConfigurer customizer) {

		ValkeyListenerEndpointRegistrar registrar = new ValkeyListenerEndpointRegistrar();
		registrar.setBeanFactory(mock(BeanFactory.class));
		registrar.setEndpointRegistry(endpointRegistry);

		registrar.apply(List.of(customizer));
		return registrar;
	}

	static class AnnotatedService {

		@ValkeyListener(topic = "test-channel")
		public void handle(@Payload Person person, @Payload String body, @Payload byte[] content) {}

		@ValkeyListener(topic = "test-channel")
		public void handle(Person person) {}

		@ValkeyListener(topic = "test-channel", consumes = "application/json")
		public void handleJson(Person person) {}

		@ValkeyListener(topic = "test-channel",
				consumes = JdkSerializerMessageConverter.APPLICATION_JAVA_SERIALIZED_OBJECT_VALUE)
		public void handleJdkSerialization(Person person) {}

	}

	record Person(String firstname, String lastname) implements Serializable {

	}

}
