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
package io.valkey.springframework.data.valkey.messaging;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.valkey.springframework.data.valkey.core.ValkeyCallback;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.listener.ChannelTopic;
import io.valkey.springframework.data.valkey.listener.support.TopicResolver;
import io.valkey.springframework.data.valkey.serializer.ValkeyMessageConverters;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.core.AbstractMessageSendingTemplate;
import org.springframework.messaging.core.DestinationResolver;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.util.Assert;

/**
 * Message-oriented template for publishing to Valkey Pub/Sub channels through {@link ValkeyOperations}.
 * <p>
 * This template bridges Spring Messaging's {@link Message} abstraction to Valkey Pub/Sub. {@link ChannelTopic
 * Destinations} are resolved through a {@link DestinationResolver} backed by a {@link TopicResolver}, and channel names
 * are written by using a {@link ValkeySerializer String serializer}.
 * <p>
 * In contrast to {@link ValkeyOperations#convertAndSend(String, Object)}, which uses the configured value serializer of
 * the underlying {@link ValkeyOperations}, this template delegates payload conversion to a {@link MessageConverter}. The
 * default converter is created through {@link ValkeyMessageConverters#createMessageConverter()} and selects a suitable
 * conversion strategy according to the payload type and, if present, the
 * {@link org.springframework.messaging.MessageHeaders#CONTENT_TYPE contentType} header.
 * <p>
 * Valkey Pub/Sub transmits only the serialized message body. Message headers participate in conversion before
 * publication and are not written to Valkey.
 *
 * @author Mark Paluch
 * @since 4.1
 * @see ValkeyMessageConverters
 * @see TopicResolver
 * @see TopicDestinationResolver
 * @see org.springframework.messaging.MessageHeaders#CONTENT_TYPE
 */
public class ValkeyMessageSendingTemplate extends AbstractMessageSendingTemplate<ChannelTopic>
		implements ValkeyMessageSendingOperations {

	private final ValkeyOperations<?, ?> valkeyOperations;
	private final ValkeySerializer<String> stringSerializer;
	private @Nullable DestinationResolver<ChannelTopic> destinationResolver;

	/**
	 * Create a new {@code ValkeyMessageSendingTemplate} for the given {@link ValkeyOperations}.
	 * <p>
	 * Uses the configured string {@link ValkeySerializer} of {@link ValkeyTemplate}, if available, and otherwise falls back
	 * to {@link ValkeySerializer#string()} for channel serialization.
	 *
	 * @param valkeyOperations the Valkey operations to use. Must not be {@literal null}.
	 */
	public ValkeyMessageSendingTemplate(ValkeyOperations<?, ?> valkeyOperations) {
		this(valkeyOperations,
				valkeyOperations instanceof ValkeyTemplate<?, ?> valkeyTemplate ? valkeyTemplate.getStringSerializer()
						: ValkeySerializer.string());
	}

	protected ValkeyMessageSendingTemplate(ValkeyOperations<?, ?> valkeyOperations,
			ValkeySerializer<String> stringSerializer) {
		this.valkeyOperations = valkeyOperations;
		this.stringSerializer = stringSerializer;
		setMessageConverter(ValkeyMessageConverters.createMessageConverter());
		setDestinationResolver(new TopicDestinationResolver<>(TopicResolver.channel()));
	}

	/**
	 * Configure the {@link DestinationResolver} to use to resolve String destination names into actual destinations of
	 * type {@link ChannelTopic}.
	 * <p>
	 * Defaults to a {@link TopicDestinationResolver} backed by {@link TopicResolver#channel()}. Set this property to
	 * {@literal null} only if destination-name based operations are not used.
	 *
	 * @param destinationResolver the destination resolver to use.
	 */
	public void setDestinationResolver(@Nullable DestinationResolver<ChannelTopic> destinationResolver) {
		this.destinationResolver = destinationResolver;
	}

	/**
	 * Return the configured destination resolver.
	 *
	 * @return the configured destination resolver.
	 */
	public @Nullable DestinationResolver<ChannelTopic> getDestinationResolver() {
		return this.destinationResolver;
	}

	@Override
	public void send(String destinationName, Message<?> message) throws MessagingException {
		ChannelTopic destination = resolveDestination(destinationName);
		doSend(destination, message);
	}

	@Override
	public <T> void convertAndSend(String destinationName, T payload) throws MessagingException {
		convertAndSend(destinationName, payload, null, null);
	}

	@Override
	public <T> void convertAndSend(String destinationName, T payload, @Nullable Map<String, Object> headers)
			throws MessagingException {
		convertAndSend(destinationName, payload, headers, null);
	}

	@Override
	public <T> void convertAndSend(String destinationName, T payload, @Nullable MessagePostProcessor postProcessor)
			throws MessagingException {
		convertAndSend(destinationName, payload, null, postProcessor);
	}

	@Override
	public <T> void convertAndSend(String destinationName, T payload, @Nullable Map<String, Object> headers,
			@Nullable MessagePostProcessor postProcessor) throws MessagingException {
		super.convertAndSend(resolveDestination(destinationName), payload, headers, postProcessor);
	}

	@Override
	protected void doSend(ChannelTopic destination, Message<?> message) {

		Assert.isInstanceOf(byte[].class, message.getPayload(), "Message payload must be of type byte[]");

		byte[] body = (byte[]) message.getPayload();
		valkeyOperations.execute((ValkeyCallback<@Nullable Long>) connection -> {

			byte[] channel = stringSerializer.serialize(destination.getTopic());
			return connection.publish(channel, body);
		});
	}

	protected final ChannelTopic resolveDestination(String destinationName) {
		Assert.state(this.destinationResolver != null, "DestinationResolver is required to resolve destination names");
		return this.destinationResolver.resolveDestination(destinationName);
	}

}
