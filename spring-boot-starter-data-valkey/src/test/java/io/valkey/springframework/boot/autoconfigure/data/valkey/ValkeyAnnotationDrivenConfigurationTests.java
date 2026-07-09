/*
 * Copyright 2012-present the original author or authors.
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

package io.valkey.springframework.boot.autoconfigure.data.valkey;

import java.time.Duration;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyProperties.Listener;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.config.ValkeyListenerConfigUtils;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;

import org.springframework.util.backoff.ExponentialBackOff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ValkeyAnnotationDrivenConfiguration}.
 *
 * @author Stephane Nicoll
 */
class ValkeyAnnotationDrivenConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValkeyAnnotationDrivenConfiguration.class))
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void registersContainerAndAnnotationProcessor() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(ValkeyMessageListenerContainer.class);
			assertThat(context).hasBean(ValkeyListenerConfigUtils.VALKEY_LISTENER_ANNOTATION_PROCESSOR_BEAN_NAME);
		});
	}

	@Test
	void backsOffWhenContainerWithDefaultNameIsDefined() {
		ValkeyMessageListenerContainer container = mock(ValkeyMessageListenerContainer.class);
		this.contextRunner
			.withBean("valkeyMessageListenerContainer", ValkeyMessageListenerContainer.class, () -> container)
			.run((context) -> assertThat(context).hasSingleBean(ValkeyMessageListenerContainer.class)
				.getBean(ValkeyMessageListenerContainer.class)
				.isSameAs(container));
	}

	@Test
	void registerContainerWhenContainerWithCustomNameIsDefined() {
		ValkeyMessageListenerContainer container = mock(ValkeyMessageListenerContainer.class);
		this.contextRunner
			.withBean("customValkeyMessageListenerContainer", ValkeyMessageListenerContainer.class, () -> container)
			.run((context) -> assertThat(context).getBeans(ValkeyMessageListenerContainer.class)
				.hasSize(2)
				.containsKey("customValkeyMessageListenerContainer"));
	}

	@Test
	void containerConfigurationMatchesDefaults() {
		this.contextRunner.run((context) -> {
			ValkeyMessageListenerContainer container = context.getBean(ValkeyMessageListenerContainer.class);
			Listener listener = new ValkeyProperties().getListener();
			assertThat(container.isAutoStartup()).isEqualTo(listener.isAutoStartup());
			assertThat(container.getMaxSubscriptionRegistrationWaitingTime())
				.isEqualTo(listener.getSubscriptionRegistrationTimeout().toMillis());
		});
	}

	@Test
	void containerCanBeConfigured() {
		this.contextRunner.withPropertyValues("spring.data.valkey.listener.auto-startup=false",
				"spring.data.valkey.listener.subscription-registration-timeout=2s",
				"spring.data.valkey.listener.recovery.max-retries=6", "spring.data.valkey.listener.recovery.delay=4s",
				"spring.data.valkey.listener.recovery.multiplier=1.5",
				"spring.data.valkey.listener.recovery.max-delay=2m", "spring.data.valkey.listener.recovery.jitter=500ms")
			.run((context) -> {
				ValkeyMessageListenerContainer container = context.getBean(ValkeyMessageListenerContainer.class);
				assertThat(container.isAutoStartup()).isFalse();
				assertThat(container.getMaxSubscriptionRegistrationWaitingTime()).isEqualTo(2000);
				assertThat(container).extracting("backOff")
					.asInstanceOf(InstanceOfAssertFactories.type(ExponentialBackOff.class))
					.satisfies((backOff) -> {
						assertThat(backOff.getMaxAttempts()).isEqualTo(6);
						assertThat(backOff.getInitialInterval()).isEqualTo(4000);
						assertThat(backOff.getMultiplier()).isEqualTo(1.5);
						assertThat(backOff.getMaxInterval()).isEqualTo(Duration.ofMinutes(2).toMillis());
						assertThat(backOff.getJitter()).isEqualTo(500);
					});
			});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ValkeyProperties.class)
	static class TestConfiguration {

		@Bean
		ValkeyConnectionFactory valkeyConnectionFactory() {
			return mock(ValkeyConnectionFactory.class);
		}

	}

}
