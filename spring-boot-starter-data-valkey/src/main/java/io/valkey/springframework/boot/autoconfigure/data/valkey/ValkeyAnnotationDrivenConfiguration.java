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

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.valkey.springframework.data.valkey.annotation.EnableValkeyListeners;
import io.valkey.springframework.data.valkey.config.ValkeyListenerConfigUtils;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;

import org.springframework.messaging.Message;

/**
 * Configuration for Valkey annotation-driven listeners.
 *
 * @author Stephane Nicoll
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ EnableValkeyListeners.class, Message.class })
class ValkeyAnnotationDrivenConfiguration {

	private static final String DEFAULT_MESSAGE_LISTENER_BEAN_NAME = ValkeyListenerConfigUtils.VALKEY_MESSAGE_LISTENER_BEAN_NAME;

	private final ValkeyProperties properties;

	ValkeyAnnotationDrivenConfiguration(ValkeyProperties properties) {
		this.properties = properties;
	}

	@Bean
	@ConditionalOnMissingBean
	ValkeyMessageListenerContainerConfigurer valkeyMessageListenerContainerConfigurer() {
		return new ValkeyMessageListenerContainerConfigurer(this.properties);
	}

	@Bean(name = DEFAULT_MESSAGE_LISTENER_BEAN_NAME)
	@ConditionalOnSingleCandidate(ValkeyConnectionFactory.class)
	@ConditionalOnMissingBean(name = DEFAULT_MESSAGE_LISTENER_BEAN_NAME)
	ValkeyMessageListenerContainer valkeyMessageListenerContainer(ValkeyMessageListenerContainerConfigurer configurer,
			ValkeyConnectionFactory valkeyConnectionFactory) {
		ValkeyMessageListenerContainer container = new ValkeyMessageListenerContainer();
		configurer.configure(container, valkeyConnectionFactory);
		return container;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableValkeyListeners
	@ConditionalOnMissingBean(name = ValkeyListenerConfigUtils.VALKEY_LISTENER_ANNOTATION_PROCESSOR_BEAN_NAME)
	static class EnableValkeyListenersConfiguration {

	}

}
