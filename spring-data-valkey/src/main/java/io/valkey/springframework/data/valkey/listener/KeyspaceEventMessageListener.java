/*
 * Copyright 2015-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.listener;

import java.util.Properties;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import io.valkey.springframework.data.valkey.connection.Message;
import io.valkey.springframework.data.valkey.connection.MessageListener;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Base {@link MessageListener} implementation for listening to Valkey keyspace notifications.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 1.7
 */
public abstract class KeyspaceEventMessageListener implements MessageListener, InitializingBean, DisposableBean {

	private static final Topic TOPIC_ALL_KEYEVENTS = new PatternTopic("__keyevent@*");

	private final ValkeyMessageListenerContainer listenerContainer;

	private @Nullable String keyspaceNotificationsConfigParameter = "EA";

	/**
	 * Creates new {@link KeyspaceEventMessageListener}.
	 *
	 * @param listenerContainer must not be {@literal null}.
	 */
	public KeyspaceEventMessageListener(ValkeyMessageListenerContainer listenerContainer) {

		Assert.notNull(listenerContainer, "ValkeyMessageListenerContainer to run in must not be null");
		this.listenerContainer = listenerContainer;
	}

	/**
	 * Set the configuration string to use for {@literal notify-keyspace-events}.
	 *
	 * @param keyspaceNotificationsConfigParameter can be {@literal null}.
	 * @since 1.8
	 */
	public void setKeyspaceNotificationsConfigParameter(@Nullable String keyspaceNotificationsConfigParameter) {
		this.keyspaceNotificationsConfigParameter = keyspaceNotificationsConfigParameter;
	}

	@Override
	public void afterPropertiesSet() {
		init();
	}

	@Override
	public void destroy() throws Exception {
		listenerContainer.removeMessageListener(this);
	}

	@Override
	public void onMessage(Message message, @Nullable byte[] pattern) {

		if (ObjectUtils.isEmpty(message.getChannel()) || ObjectUtils.isEmpty(message.getBody())) {
			return;
		}

		doHandleMessage(message);
	}

	/**
	 * Handle the actual message
	 *
	 * @param message never {@literal null}.
	 */
	protected abstract void doHandleMessage(Message message);

	/**
	 * Initialize the message listener by writing requried valkey config for {@literal notify-keyspace-events} and
	 * registering the listener within the container.
	 */
	public void init() {

		ValkeyConnectionFactory connectionFactory = listenerContainer.getConnectionFactory();

		if (StringUtils.hasText(keyspaceNotificationsConfigParameter) && connectionFactory != null) {

			try (ValkeyConnection connection = connectionFactory.getConnection()) {

				ValkeyServerCommands commands = connection.serverCommands();
				Properties config = commands.getConfig("notify-keyspace-events");

				if (!StringUtils.hasText(config.getProperty("notify-keyspace-events"))) {
					commands.setConfig("notify-keyspace-events", keyspaceNotificationsConfigParameter);
				}
			}
		}

		doRegister(listenerContainer);
	}

	/**
	 * Register instance within the container.
	 *
	 * @param container never {@literal null}.
	 */
	protected void doRegister(ValkeyMessageListenerContainer container) {
		listenerContainer.addMessageListener(this, TOPIC_ALL_KEYEVENTS);
	}

}
