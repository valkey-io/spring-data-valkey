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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.support.DefaultConversionService;
import io.valkey.springframework.data.valkey.serializer.JdkSerializationValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeyMessageConverters;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.DestinationVariableMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.validation.Validator;

/**
 * Unit tests for {@link ValkeyListenerConfigurer}.
 *
 * @author Mark Paluch
 */
class ValkeyListenerConfigurerTests {

	ValkeyListenerEndpointRegistrar registrar = new ValkeyListenerEndpointRegistrar();

	@Test
	void shouldApplyConfiguration() {

		Validator validator = mock(Validator.class);
		DestinationVariableMethodArgumentResolver resolver = new DestinationVariableMethodArgumentResolver(
				DefaultConversionService.getSharedInstance());

		ValkeyListenerConfigurer configurer = new ValkeyListenerConfigurer() {

			@Override
			public void addConverters(ConverterRegistry registry) {
				ValkeyListenerConfigurer.super.addConverters(registry);
			}

			@Override
			public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
				resolvers.add(resolver);
			}

			@Override
			public @Nullable Validator getValidator() {
				return validator;
			}

			@Override
			public void configureMessageConverters(ValkeyMessageConverters.Builder builder) {
				builder.withStringConverter(StandardCharsets.UTF_8).addCustomConverter(new JdkSerializationValkeySerializer());
			}

		};

		registrar.apply(List.of(configurer));

		DefaultMessageHandlerMethodFactory factory = (DefaultMessageHandlerMethodFactory) registrar
				.getMessageHandlerMethodFactory();

		assertThat(factory).hasFieldOrPropertyWithValue("validator", validator);
		assertThat(factory).hasFieldOrPropertyWithValue("customArgumentResolvers", List.of(resolver));
	}
}
