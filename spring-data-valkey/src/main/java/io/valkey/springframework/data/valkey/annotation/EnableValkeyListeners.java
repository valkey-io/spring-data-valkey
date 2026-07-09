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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;
import io.valkey.springframework.data.valkey.config.ValkeyListenerBootstrapConfiguration;
import io.valkey.springframework.data.valkey.config.ValkeyListenerEndpointRegistry;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;
import io.valkey.springframework.data.valkey.serializer.ValkeyMessageConverters;

/**
 * Enable Valkey Pub/Sub listener annotated endpoints that are created under the cover by a
 * {@link ValkeyListenerAnnotationBeanPostProcessor}. To be used on
 * {@link org.springframework.context.annotation.Configuration @Configuration} classes as follows:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableValkeyListeners
 * public class AppConfig {
 *
 * 	&#064;Bean
 * 	public ValkeyMessageListenerContainer myValkeyListenerContainer() {
 * 		ValkeyMessageListenerContainer factory = new ValkeyMessageListenerContainer();
 * 		factory.setConnectionFactory(connectionFactory());
 * 		return factory;
 * 	}
 *
 * 	// other &#064;Bean definitions
 * }
 * </pre>
 * <p>
 * The {@code ValkeyListenerAnnotationBeanPostProcessor} is responsible for creating endpoints.
 * <p>
 * {@code @EnableValkeyListeners} enables detection of {@link ValkeyListener @ValkeyListener} annotations on any
 * Spring-managed bean in the container. For example, given a class {@code MyService}:
 *
 * <pre class="code">
 * package com.acme.foo;
 *
 * public class MyService {
 *
 * 	&#064;ValkeyListener(container = "myValkeyListenerContainer", topic = "myChannel")
 * 	public void process(String msg) {
 * 		// process incoming message
 * 	}
 * }
 * </pre>
 * <p>
 * The container to use is identified by the {@link ValkeyListener#container() container} attribute defining the name of
 * the {@link ValkeyMessageListenerContainer} bean to use. When none is set a {@code ValkeyMessageListenerContainer} bean
 * named {@code valkeyMessageListenerContainer} is assumed to be present.
 * <p>
 * The following configuration would ensure that every time a Pub/Sub is received on the topic channel named
 * "myChannel", {@code MyService.process()} is invoked with the content of the message:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableValkeyListeners
 * public class AppConfig {
 *
 * 	&#064;Bean
 * 	public MyService myService() {
 * 		return new MyService();
 * 	}
 *
 * 	// Valkey infrastructure setup
 * }
 * </pre>
 * <p>
 * Alternatively, if {@code MyService} were annotated with {@code @Component}, the following configuration would ensure
 * that its {@code @EnableValkeyListeners} annotated method is invoked with a matching incoming message:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableValkeyListeners
 * &#064;ComponentScan(basePackages = "com.acme.foo")
 * public class AppConfig {}
 * </pre>
 * <p>
 * Annotated methods can use flexible signature; in particular, it is possible to use the
 * {@link org.springframework.messaging.Message Message} abstraction and related annotations, see {@link ValkeyListener}
 * Javadoc for more details. For instance, the following would inject the content of the message and the "channel" name
 * header:
 *
 * <pre class="code">
 * &#064;ValkeyListener(container = "myValkeyListenerContainer", topic = "myChannel")
 * public void process(String msg, @Header("channel") String channel) {
 * 	// process incoming message
 * }
 * </pre>
 * <p>
 * These features are abstracted by the
 * {@link org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory} that is responsible for
 * building the necessary invoker to process the annotated method. By default,
 * {@link org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory} is used.
 * <p>
 * Implementing {@code ValkeyListenerConfigurer} allows for fine-grained control over endpoint registration via the
 * {@code ValkeyListenerEndpointRegistrar}. For example, the following configures an extra endpoint:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableValkeyListeners
 * public class AppConfig implements ValkeyListenerConfigurer {
 *
 * 	&#064;Override
 * 	public void configureValkeyListeners(ValkeyListenerEndpointRegistrar registrar) {
 * 		SimpleValkeyListenerEndpoint myEndpoint = new SimpleValkeyListenerEndpoint();
 * 		// ... configure the endpoint
 * 		registrar.registerEndpoint(endpoint, anotherValkeyMessageListenerContainer());
 * 	}
 *
 * 	&#064;Bean
 * 	public MyService myService() {
 * 		return new MyService();
 * 	}
 *
 * 	&#064;Bean
 * 	public ValkeyMessageListenerContainer anotherValkeyMessageListenerContainer() {
 * 		// ...
 * 	}
 *
 * 	// Valkey infrastructure setup
 * }
 * </pre>
 * <p>
 * Beans implementing {@code ValkeyListenerConfigurer} can configure various aspects of annotation-driven endpoints
 * including converter registration, configuration of a {@code Validator}, and configuration of
 * {@link org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver}s. For example, the following
 * configures the charset for a string message converter and disables built-in converter registration:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableValkeyListeners
 * public class AppConfig implements ValkeyListenerConfigurer {
 *
 * 	&#064;Override
 * 	public void configureMessageConverters(ValkeyMessageConverters.Builder builder) {
 * 		builder.withStringConverter(StandardCharsets.US_ASCII).registerDefaults(false);
 * 	}
 *
 * 	// Valkey infrastructure setup
 * }
 * </pre>
 * <p>
 * Note that all beans implementing {@code ValkeyListenerConfigurer} will be detected and invoked in a similar fashion.
 * The example above can be translated into a regular bean definition registered in the context in case you use the XML
 * configuration.
 *
 * @author Ilyass Bougati
 * @since 4.1
 * @see ValkeyListener
 * @see ValkeyListenerAnnotationBeanPostProcessor
 * @see ValkeyListenerEndpointRegistry
 * @see ValkeyMessageConverters
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ValkeyListenerBootstrapConfiguration.class)
public @interface EnableValkeyListeners {

}
