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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.valkey.springframework.data.valkey.annotation.EnableValkeyListeners;
import io.valkey.springframework.data.valkey.connection.MessageListener;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;

/**
 * Unit tests for {@link ValkeyListenerConfigurer}.
 *
 * @author Ilyass Bougati
 * @author Mark Paluch
 */
class ValkeyListenerConfigurerIntegrationTests {

	@Test
	void shouldApplyConfiguration() {

		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {

			context.register(TestConfig.class);
			context.refresh();

			MockCustomConfigurer configurer = context.getBean(MockCustomConfigurer.class);

			assertThat(configurer.isRegistrarConfigured).isTrue();

			ValkeyListenerEndpointRegistry registry = context.getBean(ValkeyListenerEndpointRegistry.class);
			assertThat(registry.getEndpoints()).hasSize(1);
		}
	}

	@Configuration
	@EnableValkeyListeners
	static class TestConfig {

		@Bean
		public MockCustomConfigurer customConfigurer() {
			return new MockCustomConfigurer();
		}

		@Bean
		public ValkeyListenerEndpointRegistrar valkeyListenerEndpointRegistrar(ValkeyListenerEndpointRegistry registry) {
			ValkeyListenerEndpointRegistrar registrar = new ValkeyListenerEndpointRegistrar();
			registrar.setEndpointRegistry(registry);
			return registrar;
		}
	}

	static class MockCustomConfigurer implements ValkeyListenerConfigurer {

		boolean isRegistrarConfigured = false;

		@Override
		public void configureValkeyListeners(ValkeyListenerEndpointRegistrar registrar) {
			SimpleValkeyListenerEndpoint endpoint = new SimpleValkeyListenerEndpoint(mock(MessageListener.class));
			endpoint.setId("test");
			endpoint.setTopic("my-channel");
			registrar.registerEndpoint(endpoint, mock(ValkeyMessageListenerContainer.class));
			this.isRegistrarConfigured = true;
		}
	}
}
