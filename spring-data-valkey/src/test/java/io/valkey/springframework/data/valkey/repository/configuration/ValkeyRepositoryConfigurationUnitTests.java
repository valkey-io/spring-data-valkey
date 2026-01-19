/*
 * Copyright 2016-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.repository.configuration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyHash;
import io.valkey.springframework.data.valkey.core.ValkeyKeyValueAdapter;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.convert.ReferenceResolver;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;
import org.springframework.data.repository.Repository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for Valkey Repository configuration.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public class ValkeyRepositoryConfigurationUnitTests {

	static ValkeyTemplate<?, ?> createTemplateMock() {

		ValkeyTemplate<?, ?> template = mock(ValkeyTemplate.class);
		ValkeyConnectionFactory connectionFactory = mock(ValkeyConnectionFactory.class);
		ValkeyConnection connection = mock(ValkeyConnection.class);

		when(template.getConnectionFactory()).thenReturn(connectionFactory);
		when(connectionFactory.getConnection()).thenReturn(connection);

		return template;
	}

	@ExtendWith(SpringExtension.class)
	@DirtiesContext
	@ContextConfiguration(classes = { ContextWithCustomReferenceResolverUnitTests.Config.class })
	public static class ContextWithCustomReferenceResolverUnitTests {

		@EnableValkeyRepositories(considerNestedRepositories = true,
				includeFilters = { @ComponentScan.Filter(type = FilterType.REGEX, pattern = { ".*ContextSampleRepository" }) })
		static class Config {

			@Bean
			ValkeyTemplate<?, ?> valkeyTemplate() {
				return createTemplateMock();
			}

			@Bean
			ReferenceResolver valkeyReferenceResolver() {
				return mock(ReferenceResolver.class);
			}

		}

		@Autowired ApplicationContext ctx;

		@Test // DATAREDIS-425
		public void shouldPickUpReferenceResolver() {

			ValkeyKeyValueAdapter adapter = (ValkeyKeyValueAdapter) ctx.getBean("valkeyKeyValueAdapter");

			Object referenceResolver = ReflectionTestUtils.getField(adapter.getConverter(), "referenceResolver");

			assertThat(referenceResolver).isEqualTo((ctx.getBean("valkeyReferenceResolver")));
			assertThat(mockingDetails(referenceResolver).isMock()).isTrue();
		}
	}

	@ExtendWith(SpringExtension.class)
	@DirtiesContext
	@ContextConfiguration(classes = { ContextWithoutCustomizationUnitTests.Config.class })
	public static class ContextWithoutCustomizationUnitTests {

		@EnableValkeyRepositories(considerNestedRepositories = true,
				includeFilters = { @ComponentScan.Filter(type = FilterType.REGEX, pattern = { ".*ContextSampleRepository" }) })
		static class Config {

			@Bean
			ValkeyTemplate<?, ?> valkeyTemplate() {
				return createTemplateMock();
			}
		}

		@Autowired ApplicationContext ctx;

		@Test // DATAREDIS-425
		public void shouldInitWithDefaults() {
			assertThat(ctx.getBean(ContextSampleRepository.class)).isNotNull();

		}

		@Test // DATAREDIS-425
		public void shouldRegisterDefaultBeans() {

			assertThat(ctx.getBean(ContextSampleRepository.class)).isNotNull();
			assertThat(ctx.getBean("valkeyKeyValueAdapter")).isNotNull();
			assertThat(ctx.getBean("valkeyCustomConversions")).isNotNull();
			assertThat(ctx.getBean("valkeyReferenceResolver")).isNotNull();
		}
	}

	@ExtendWith(SpringExtension.class)
	@DirtiesContext
	@ContextConfiguration(classes = { WithMessageListenerConfigurationUnitTests.Config.class })
	public static class WithMessageListenerConfigurationUnitTests {

		@EnableValkeyRepositories(considerNestedRepositories = true,
				includeFilters = { @ComponentScan.Filter(type = FilterType.REGEX, pattern = { ".*ContextSampleRepository" }) },
				keyspaceNotificationsConfigParameter = "", messageListenerContainerRef = "myContainer")
		static class Config {

			@Bean
			ValkeyMessageListenerContainer myContainer() {
				return mock(ValkeyMessageListenerContainer.class);
			}

			@Bean
			ValkeyTemplate<?, ?> valkeyTemplate() {
				return createTemplateMock();
			}
		}

		@Autowired ApplicationContext ctx;

		@Test // DATAREDIS-425
		public void shouldConfigureMessageListenerContainer() {

			ValkeyKeyValueAdapter adapter = ctx.getBean("valkeyKeyValueAdapter", ValkeyKeyValueAdapter.class);
			Object messageListenerContainer = ReflectionTestUtils.getField(adapter, "messageListenerContainer");

			assertThat(Mockito.mockingDetails(messageListenerContainer).isMock()).isTrue();
		}
	}

	@ValkeyHash
	static class Sample {
		String id;
	}

	interface ContextSampleRepository extends Repository<Sample, Long> {}
}
