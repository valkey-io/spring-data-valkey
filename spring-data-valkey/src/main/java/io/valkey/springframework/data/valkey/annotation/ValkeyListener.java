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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import io.valkey.springframework.data.valkey.config.ValkeyListenerConfigUtils;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.MessageMapping;

/**
 * Annotation that marks a method to be the target of a Valkey Pub/Sub message listener on the specified {@link #topic}.
 * The {@link #container()} identifies the {@link io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer}
 * to subscribe with. If not set, a <em>default</em> container is assumed to be available with a bean name of
 * {@code valkeyMessageListenerContainer} unless an explicit default has been provided through configuration.
 * <p>
 * Processing of {@code @ValkeyListener} annotations is performed by registering a
 * {@link ValkeyListenerAnnotationBeanPostProcessor}. This can be done manually or, more conveniently, through the
 * {@link EnableValkeyListeners @EnableValkeyListeners} annotation.
 * <p>
 * Annotated Valkey listener methods are allowed to have flexible signatures similar to what {@link MessageMapping}
 * provides:
 * <ul>
 * <li>{@link io.valkey.springframework.data.valkey.listener.Topic} to get access to the Valkey topic</li>
 * <li>{@link org.springframework.messaging.Message} to use Spring's messaging abstraction</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Payload @Payload}-annotated method arguments, including
 * support for validation</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Header @Header}-annotated method arguments to extract
 * specific header values, including Valkey headers defined by
 * {@link io.valkey.springframework.data.valkey.listener.support.PubSubHeaders}</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Headers @Headers}-annotated method argument that must
 * also be assignable to {@link java.util.Map} for obtaining access to all headers</li>
 * <li>{@link org.springframework.messaging.MessageHeaders} arguments for obtaining access to all headers</li>
 * <li>{@link org.springframework.messaging.support.MessageHeaderAccessor} for convenient access to all method
 * arguments</li>
 * </ul>
 * <p>
 * This annotation can be used as a <em>{@linkplain Repeatable repeatable}</em> annotation.
 * <p>
 * This annotation may be used as a <em>meta-annotation</em> to create custom <em>composed annotations</em> with
 * attribute overrides.
 *
 * @author Ilyass Bougati
 * @author Mark Paluch
 * @since 4.1
 * @see EnableValkeyListeners
 * @see ValkeyListenerAnnotationBeanPostProcessor
 * @see ValkeyListeners
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(ValkeyListeners.class)
@MessageMapping
public @interface ValkeyListener {

	/**
	 * The unique identifier of the container managing this endpoint.
	 * <p>
	 * If none is specified, an auto-generated one is provided.
	 */
	String id() default "";

	/**
	 * The bean name of the {@link io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer} to subscribe
	 * with.
	 */
	String container() default ValkeyListenerConfigUtils.VALKEY_MESSAGE_LISTENER_BEAN_NAME;

	/**
	 * The destination name for this listener, resolved through a
	 * {@link io.valkey.springframework.data.valkey.listener.support.TopicResolver} strategy. Alias for {@link #topic()}.
	 */
	@AliasFor("topic")
	String value() default "";

	/**
	 * The destination name for this listener, resolved through a
	 * {@link io.valkey.springframework.data.valkey.listener.support.TopicResolver} strategy.
	 */
	@AliasFor("value")
	String topic() default "";

	/**
	 * Configure the primary mapping by MIME type that is consumed by the handler method. Setting {@code consumes} defines
	 * the message content type as Valkey Pub/Sub messages do not feature headers to indicate a content type towards
	 * message converter selection. Examples:
	 *
	 * <pre class="code">
	 * consumes = "text/plain"
	 * consumes = "application/*"
	 * </pre>
	 *
	 * @see org.springframework.util.MimeType
	 * @see MessageHeaders#CONTENT_TYPE
	 */
	String consumes() default "";

}
