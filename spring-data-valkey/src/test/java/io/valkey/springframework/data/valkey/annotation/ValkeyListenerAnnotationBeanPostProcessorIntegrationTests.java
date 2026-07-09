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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.valkey.springframework.data.valkey.config.ValkeyListenerConfigUtils;
import io.valkey.springframework.data.valkey.config.ValkeyListenerEndpointRegistry;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;
import io.valkey.springframework.data.valkey.listener.Topic;

/**
 * Integration test for {@link EnableValkeyListeners} and {@link ValkeyListener}
 *
 * @author Ilyass Bougati
 * @author Mark Paluch
 */
class ValkeyListenerAnnotationBeanPostProcessorIntegrationTests {

	@Test // GH-1004
	void registersListenerWithDefaultContainer() {

		AtomicReference<ValkeyListenerEndpointRegistry> registryRef = new AtomicReference<>();

		doWithContext(context -> {
			ValkeyMessageListenerContainer container = context.getBean("valkeyMessageListenerContainer",
					ValkeyMessageListenerContainer.class);

			verify(container).addMessageListener(any(), any(Topic.class));

			ValkeyListenerEndpointRegistry registry = context.getBean(ValkeyListenerEndpointRegistry.class);
			assertThat(registry.isRunning()).isTrue();
			registryRef.set(registry);
		}, DefaultConfig.class, SimpleService.class);

		assertThat(registryRef.get().isRunning()).isFalse();
	}

	@Test // GH-3340
	void registersListenerWithNamedContainer() {

		doWithContext(context -> {
			ValkeyMessageListenerContainer customContainer = context.getBean("customContainer1",
					ValkeyMessageListenerContainer.class);
			ValkeyMessageListenerContainer defaultContainer = context
					.getBean(ValkeyListenerConfigUtils.VALKEY_MESSAGE_LISTENER_BEAN_NAME, ValkeyMessageListenerContainer.class);

			verify(customContainer).addMessageListener(any(), any(Topic.class));
			verify(defaultContainer, never()).addMessageListener(any(), any(Topic.class));
		}, DefaultConfig.class, MultiContainerService.class, CustomContainerConfig.class);
	}

	@Test // GH-3340
	void registersListenersAcrossMultipleContainers() {

		doWithContext(context -> {
			ValkeyMessageListenerContainer containerOne = context.getBean("customContainer1",
					ValkeyMessageListenerContainer.class);
			ValkeyMessageListenerContainer containerTwo = context.getBean("customContainer2",
					ValkeyMessageListenerContainer.class);

			verify(containerOne).addMessageListener(any(), any(Topic.class));
			verify(containerTwo).addMessageListener(any(), any(Topic.class));
		}, CustomContainerConfig.class, MultiContainerService.class);
	}

	@Test // GH-3340
	void failsWithMissingNamedContainer() {

		assertThatThrownBy(() -> new AnnotationConfigApplicationContext(DefaultConfig.class, NamedContainerService.class))
				.hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class).hasMessageContaining("customContainer");
	}

	@Test // GH-3340
	void registersListenersMultipleContainers() {

		doWithContext(context -> {
			ValkeyMessageListenerContainer container = context
					.getBean(ValkeyListenerConfigUtils.VALKEY_MESSAGE_LISTENER_BEAN_NAME, ValkeyMessageListenerContainer.class);

			verify(container).addMessageListener(any(), any(Topic.class));
		}, DefaultConfig.class, CustomContainerConfig.class, UnnamedContainerService.class);
	}

	@Test // GH-3340
	void registrationFailsOnUnresolvableContainer() {

		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> doWithContext(context -> {}, CustomContainerConfig.class, UnnamedContainerService.class));
	}

	private static void doWithContext(Consumer<ApplicationContext> action, Class<?>... annotatedClasses) {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.register(annotatedClasses);
			context.refresh();
			action.accept(context);
		}
	}

	@Configuration
	@EnableValkeyListeners
	static class DefaultConfig {

		@Bean
		public ValkeyMessageListenerContainer valkeyMessageListenerContainer() {
			return mock(ValkeyMessageListenerContainer.class);
		}
	}

	static class SimpleService {

		@ValkeyListener(topic = "test-topic")
		public void handle(String msg) {}

	}

	static class UnnamedContainerService {

		@ValkeyListener(topic = "test-topic", container = "")
		public void handle(String msg) {}

	}

	static class NamedContainerService {

		@ValkeyListener(container = "customContainer", topic = "test-topic")
		public void handle(String msg) {}

	}

	@Configuration
	@EnableValkeyListeners
	static class CustomContainerConfig {

		@Bean
		public ValkeyMessageListenerContainer customContainer1() {
			return mock(ValkeyMessageListenerContainer.class);
		}

		@Bean
		public ValkeyMessageListenerContainer customContainer2() {
			return mock(ValkeyMessageListenerContainer.class);
		}

	}

	static class MultiContainerService {

		@ValkeyListener(container = "customContainer1", topic = "topic-one")
		@ValkeyListener(container = "customContainer2", topic = "topic-two")
		public void handle(String msg) {}

	}

}
