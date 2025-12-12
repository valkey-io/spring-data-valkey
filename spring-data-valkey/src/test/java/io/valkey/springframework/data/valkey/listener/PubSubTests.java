/*
 * Copyright 2011-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.listener.adapter.MessageListenerAdapter;
import io.valkey.springframework.data.valkey.test.condition.EnabledIfLongRunningTest;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * Base test class for PubSub integration tests
 *
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Mark Paluch
 * @author Vedran Pavic
 */
@MethodSource("testParams")
public class PubSubTests<T> {

	private static final String CHANNEL = "pubsub::test";

	protected ValkeyMessageListenerContainer container;
	protected ObjectFactory<T> factory;
	@SuppressWarnings("rawtypes") protected ValkeyTemplate template;

	private final BlockingDeque<Object> bag = new LinkedBlockingDeque<>(99);

	private final Object handler = new Object() {
		@SuppressWarnings("unused")
		public void handleMessage(Object message) {
			bag.add(message);
		}
	};

	private final MessageListenerAdapter adapter = new MessageListenerAdapter(handler);

	@SuppressWarnings("rawtypes")
	public PubSubTests(ObjectFactory<T> factory, ValkeyTemplate template) {
		this.factory = factory;
		this.template = template;
	}

	public static Collection<Object[]> testParams() {
		return PubSubTestParams.testParams();
	}

	@BeforeEach
	void setUp() {
		bag.clear();

		adapter.setSerializer(template.getValueSerializer());
		adapter.afterPropertiesSet();

		container = new ValkeyMessageListenerContainer();
		container.setConnectionFactory(template.getConnectionFactory());
		container.setBeanName("container");
		container.addMessageListener(adapter, Arrays.asList(new ChannelTopic(CHANNEL)));
		container.afterPropertiesSet();
		container.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		container.destroy();
	}

	/**
	 * Return a new instance of T
	 *
	 * @return
	 */
	T getT() {
		return factory.instance();
	}

	@ParameterizedValkeyTest
	void testContainerSubscribe() {
		T payload1 = getT();
		T payload2 = getT();

		template.convertAndSend(CHANNEL, payload1);
		template.convertAndSend(CHANNEL, payload2);

		await().atMost(Duration.ofSeconds(2)).until(() -> bag.contains(payload1) && bag.contains(payload2));
	}

	@ParameterizedValkeyTest
	void testMessageBatch() throws Exception {

		int COUNT = 10;
		for (int i = 0; i < COUNT; i++) {
			template.convertAndSend(CHANNEL, getT());
		}

		for (int i = 0; i < COUNT; i++) {
			assertThat(bag.poll(1, TimeUnit.SECONDS)).as("message #" + i).isNotNull();
		}
	}

	@ParameterizedValkeyTest
	@EnabledIfLongRunningTest
	void testContainerUnsubscribe() throws Exception {
		T payload1 = getT();
		T payload2 = getT();

		container.removeMessageListener(adapter, new ChannelTopic(CHANNEL));
		template.convertAndSend(CHANNEL, payload1);
		template.convertAndSend(CHANNEL, payload2);

		assertThat(bag.poll(200, TimeUnit.MILLISECONDS)).isNull();
	}

	@ParameterizedValkeyTest
	void testStartNoListeners() {
		container.removeMessageListener(adapter, new ChannelTopic(CHANNEL));
		container.stop();
		// DATVALKEY-207 This test previously took 5 seconds on start due to monitor wait
		container.start();
	}

	@ParameterizedValkeyTest // DATAREDIS-251, GH-964
	void testStartListenersToNoSpecificChannelTest() {

		assumeThat(isClusterAware(template.getConnectionFactory())).isFalse();

		container.removeMessageListener(adapter, new ChannelTopic(CHANNEL));
		container.addMessageListener(adapter, Collections.singletonList(new PatternTopic(CHANNEL + "*")));
		container.start();

		T payload = getT();

		assertThat(template.convertAndSend(CHANNEL, payload)).isEqualTo(1L);

		await().atMost(Duration.ofSeconds(2)).until(() -> bag.contains(payload));
	}

	private static boolean isClusterAware(ValkeyConnectionFactory connectionFactory) {

		if (connectionFactory instanceof LettuceConnectionFactory lettuce) {
			return lettuce.isClusterAware();
		} else if (connectionFactory instanceof JedisConnectionFactory jedis) {
			return jedis.isValkeyClusterAware();
		}
		return false;
	}
}
