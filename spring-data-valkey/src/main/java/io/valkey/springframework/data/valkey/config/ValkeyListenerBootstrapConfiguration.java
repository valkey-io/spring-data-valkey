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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import io.valkey.springframework.data.valkey.annotation.EnableValkeyListeners;
import io.valkey.springframework.data.valkey.annotation.ValkeyListener;
import io.valkey.springframework.data.valkey.annotation.ValkeyListenerAnnotationBeanPostProcessor;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * {@code @Configuration} class that registers a {@link ValkeyListenerAnnotationBeanPostProcessor} bean capable of
 * processing Spring's {@link ValkeyListener @ValkeyListener} annotation. Also registers a default
 * {@link ValkeyListenerEndpointRegistry}.
 * <p>
 * This configuration class is automatically imported when using the {@code @EnableValkeyListeners} annotation. See the
 * {@link EnableValkeyListeners @EnableValkeyListeners} for complete usage details.
 *
 * @author Ilyass Bougati
 * @author Mark Paluch
 * @since 4.1
 */
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ValkeyListenerBootstrapConfiguration {

	public ValkeyListenerBootstrapConfiguration() {
		Assert.state(ClassUtils.isPresent("org.springframework.messaging.handler.invocation.InvocableHandlerMethod",
				MethodValkeyListenerEndpoint.class.getClassLoader()), "spring-messaging must be on the class path");
	}

	@Bean(name = ValkeyListenerConfigUtils.VALKEY_LISTENER_ANNOTATION_PROCESSOR_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public ValkeyListenerAnnotationBeanPostProcessor valkeyListenerAnnotationBeanPostProcessor() {
		return new ValkeyListenerAnnotationBeanPostProcessor();
	}

	@Bean(name = ValkeyListenerConfigUtils.VALKEY_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public ValkeyListenerEndpointRegistry valkeyListenerEndpointRegistry() {
		return new ValkeyListenerEndpointRegistry();
	}
}
