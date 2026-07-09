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
package io.valkey.springframework.data.valkey.annotation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.beans.factory.BeanFactory;
import io.valkey.springframework.data.valkey.config.MethodValkeyListenerEndpoint;
import io.valkey.springframework.data.valkey.config.ValkeyListenerConfigUtils;
import io.valkey.springframework.data.valkey.config.ValkeyListenerEndpointRegistry;
import io.valkey.springframework.data.valkey.listener.ChannelTopic;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;
import io.valkey.springframework.data.valkey.listener.StringMessage;
import io.valkey.springframework.data.valkey.listener.Topic;
import io.valkey.springframework.data.valkey.listener.adapter.HandlerMethodMessageListenerAdapter;
import io.valkey.springframework.data.valkey.listener.support.PubSubHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;

/**
 * Unit tests for {@link ValkeyListenerAnnotationBeanPostProcessor}.
 *
 * @author Ilyass Bougati
 * @author Mark Paluch
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class ValkeyListenerAnnotationBeanPostProcessorUnitTests {

	@Mock ValkeyListenerEndpointRegistry endpointRegistry;
	@Mock BeanFactory beanFactory;
	@Mock ValkeyMessageListenerContainer container;

	private ValkeyListenerAnnotationBeanPostProcessor processor;

	@BeforeEach
	void setUp() {

		processor = new ValkeyListenerAnnotationBeanPostProcessor();
		processor.setEndpointRegistry(endpointRegistry);
		processor.afterSingletonsInstantiated();
		processor.setBeanFactory(beanFactory);

		when(beanFactory.getBean(ValkeyListenerConfigUtils.VALKEY_MESSAGE_LISTENER_BEAN_NAME,
				ValkeyMessageListenerContainer.class)).thenReturn(container);
	}

	@Test // GH-1004
	void shouldRegisterEndpoint() throws NoSuchMethodException {

		AnnotatedService bean = new AnnotatedService();

		Object result = processor.postProcessAfterInitialization(bean, "annotatedService");

		processor.afterSingletonsInstantiated();

		ArgumentCaptor<MethodValkeyListenerEndpoint> endpointCaptor = ArgumentCaptor
				.forClass(MethodValkeyListenerEndpoint.class);

		assertThat(result).isSameAs(bean);

		verify(endpointRegistry).registerListener(endpointCaptor.capture(), eq(container));

		MethodValkeyListenerEndpoint registeredEndpoint = endpointCaptor.getValue();
		assertThat(registeredEndpoint.getBean()).isEqualTo(bean);
		assertThat(registeredEndpoint.getMethod()).isEqualTo(AnnotatedService.class.getMethod("handle", String.class));
	}

	@Test // GH-1004
	void shouldNotRegisterWithoutAnnotation() {

		PlainService bean = new PlainService();

		Object result = processor.postProcessAfterInitialization(bean, "plainService");

		assertThat(result).isSameAs(bean);
		verifyNoInteractions(endpointRegistry);
		verifyNoInteractions(beanFactory);
	}

	@Test // GH-1004
	void shouldInjectPayload() throws NoSuchMethodException {

		WithArgumentResolution bean = mock(WithArgumentResolution.class);
		Method method = WithArgumentResolution.class.getMethod("handle", String.class, Topic.class);

		MethodValkeyListenerEndpoint endpoint = processor.createEndpoint(method.getAnnotation(ValkeyListener.class),
				method, bean);

		HandlerMethodMessageListenerAdapter listener = endpoint.createListener();

		listener.onMessage(new StringMessage("test-channel", "hello"), null);

		verify(bean).handle("hello", ChannelTopic.of("test-channel"));
	}


	@Test // GH-1004
	void shouldInjectConvertedPayload() throws NoSuchMethodException {

		WithArgumentResolution bean = mock(WithArgumentResolution.class);
		Method method = WithArgumentResolution.class.getMethod("handle", String.class, String.class);

		MethodValkeyListenerEndpoint endpoint = processor.createEndpoint(method.getAnnotation(ValkeyListener.class),
				method, bean);

		HandlerMethodMessageListenerAdapter listener = endpoint.createListener();

		listener.onMessage(new StringMessage("test-channel", "hello"), null);

		verify(bean).handle("hello", "test-channel");
	}

	@Test // GH-1004
	void shouldInjectHeaders() throws NoSuchMethodException {

		WithArgumentResolution bean = mock(WithArgumentResolution.class);
		Method method = WithArgumentResolution.class.getMethod("handleHeaders", String.class, Map.class);

		MethodValkeyListenerEndpoint endpoint = processor.createEndpoint(method.getAnnotation(ValkeyListener.class),
				method, bean);

		HandlerMethodMessageListenerAdapter listener = endpoint.createListener();

		listener.onMessage(new StringMessage("test-channel", "hello"), null);

		ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);

		verify(bean).handleHeaders(eq("hello"), headersCaptor.capture());
		Map<String, Object> headers = headersCaptor.getValue();

		assertThat(headers).containsEntry(PubSubHeaders.TOPIC, ChannelTopic.of("test-channel"))
				.containsEntry(PubSubHeaders.CHANNEL, ChannelTopic.of("test-channel")) //
				.doesNotContainKey(PubSubHeaders.PATTERN);
	}

	static class AnnotatedService {

		@ValkeyListener(topic = "test-channel")
		public void handle(String message) {}

	}

	static class WithArgumentResolution {

		@ValkeyListener(topic = "test-channel")
		public void handle(String message, @Header Topic topic) {}

		@ValkeyListener(topic = "test-channel")
		public void handle(String message, @Header String topic) {}

		@ValkeyListener(topic = "test-channel")
		public void handleHeaders(String message, @Headers Map<String, Object> headers) {}

	}

	static class PlainService {

		public void doSomething() {}

	}

}
