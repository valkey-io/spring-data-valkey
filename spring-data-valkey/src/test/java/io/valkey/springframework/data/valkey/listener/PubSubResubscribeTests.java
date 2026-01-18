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
import static org.awaitility.Awaitility.*;
import static org.junit.Assume.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.listener.adapter.MessageListenerAdapter;
import io.valkey.springframework.data.valkey.test.condition.EnabledIfLongRunningTest;
import io.valkey.springframework.data.valkey.test.condition.ValkeyDetector;
import io.valkey.springframework.data.valkey.test.extension.ValkeyCluster;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;

/**
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Vedran Pavic
 */
@MethodSource("testParams")
@EnabledIfLongRunningTest
public class PubSubResubscribeTests {

	private static final String CHANNEL = "pubsub::test";

	private final BlockingDeque<String> bag = new LinkedBlockingDeque<>(99);
	private final Object handler = new MessageHandler("handler1", bag);
	private final MessageListenerAdapter adapter = new MessageListenerAdapter(handler);

	private ValkeyMessageListenerContainer container;
	private ValkeyConnectionFactory factory;

	@SuppressWarnings("rawtypes") //
	private ValkeyTemplate template;

	public PubSubResubscribeTests(ValkeyConnectionFactory connectionFactory) {
		this.factory = connectionFactory;
	}

	public static Collection<Object[]> testParams() {

		List<ValkeyConnectionFactory> factories = new ArrayList<>(3);

		// Jedis
		JedisConnectionFactory jedisConnFactory = JedisConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		factories.add(jedisConnFactory);

		// Lettuce
		LettuceConnectionFactory lettuceConnFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		factories.add(lettuceConnFactory);

		if (clusterAvailable()) {

			LettuceConnectionFactory lettuceClusterConnFactory = LettuceConnectionFactoryExtension
					.getConnectionFactory(ValkeyCluster.class);

			factories.add(lettuceClusterConnFactory);
		}


		// Valkey-GLIDE
		ValkeyGlideConnectionFactory glideConnFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		factories.add(glideConnFactory);
	
		if (clusterAvailable()) {
		    ValkeyGlideConnectionFactory glideClusterConnFactory = ValkeyGlideConnectionFactoryExtension
		            .getConnectionFactory(ValkeyCluster.class);

			factories.add(glideClusterConnFactory);
		}

		return factories.stream().map(factory -> new Object[] { factory }).collect(Collectors.toList());
	}

	@BeforeEach
	void setUp() throws Exception {

		template = new StringValkeyTemplate(factory);

		adapter.setSerializer(template.getValueSerializer());
		adapter.afterPropertiesSet();

		container = new ValkeyMessageListenerContainer();
		container.setConnectionFactory(template.getConnectionFactory());
		container.setBeanName("container");
		container.setTaskExecutor(new SyncTaskExecutor());
		container.setSubscriptionExecutor(new SimpleAsyncTaskExecutor());
		container.afterPropertiesSet();
		container.start();
	}

	@AfterEach
	void tearDown() {
		container.stop();
		bag.clear();
	}

	@ParameterizedValkeyTest
	@EnabledIfLongRunningTest
	void testContainerPatternResubscribe() {

		String payload1 = "do";
		String payload2 = "re mi";

		final String PATTERN = "p*";
		final String ANOTHER_CHANNEL = "pubsub::test::extra";

		BlockingDeque<String> bag2 = new LinkedBlockingDeque<>(99);
		MessageListenerAdapter anotherListener = new MessageListenerAdapter(new MessageHandler("handler2", bag2));
		anotherListener.setSerializer(template.getValueSerializer());
		anotherListener.afterPropertiesSet();

		// remove adapter from all channels
		container.addMessageListener(anotherListener, new PatternTopic(PATTERN));

		// Wait for async subscription tasks to setup
		// test no messages are sent just to patterns
		template.convertAndSend(CHANNEL, payload1);
		template.convertAndSend(ANOTHER_CHANNEL, payload2);

		await().atMost(Duration.ofSeconds(2)).until(() -> bag2.contains(payload1) && bag2.contains(payload2));

		// bind original listener on another channel
		container.addMessageListener(adapter, new ChannelTopic(ANOTHER_CHANNEL));

		// Wait for async subscription tasks to setup
		template.convertAndSend(CHANNEL, payload1);
		template.convertAndSend(ANOTHER_CHANNEL, payload2);

		await().atMost(Duration.ofSeconds(2)).until(() -> bag.contains(payload2));

		// another listener receives messages on both channels
		await().atMost(Duration.ofSeconds(2)).until(() -> bag2.contains(payload1) && bag2.contains(payload2));
	}

	@ParameterizedValkeyTest
	void testContainerChannelResubscribe() {

		String payload1 = "do";
		String payload2 = "re mi";

		String anotherPayload1 = "od";
		String anotherPayload2 = "mi er";

		String ANOTHER_CHANNEL = "pubsub::test::extra";

		// bind listener on another channel
		container.addMessageListener(adapter, new ChannelTopic(ANOTHER_CHANNEL));
		container.removeMessageListener(null, new ChannelTopic(CHANNEL));

		// Listener removed from channel
		template.convertAndSend(CHANNEL, payload1);
		template.convertAndSend(CHANNEL, payload2);

		// Listener receives messages on another channel
		template.convertAndSend(ANOTHER_CHANNEL, anotherPayload1);
		template.convertAndSend(ANOTHER_CHANNEL, anotherPayload2);

		await().atMost(Duration.ofSeconds(2)).until(() -> bag.contains(anotherPayload1) && bag.contains(anotherPayload2));
	}

	/**
	 * Validates the behavior of {@link ValkeyMessageListenerContainer} when it needs to spin up a thread executing its
	 * PatternSubscriptionTask
	 */
	@ParameterizedValkeyTest
	void testInitializeContainerWithMultipleTopicsIncludingPattern() {

		assumeFalse(isClusterAware(template.getConnectionFactory()));

		container.stop();

		String uniqueChannel = "random-" + UUID.randomUUID();

		container.addMessageListener(adapter,
				Arrays.asList(new Topic[] { new ChannelTopic(uniqueChannel), new PatternTopic("s*") }));
		container.start();

		assertThat(template.convertAndSend("somechannel", "HELLO")).isEqualTo(1L);
		assertThat(template.convertAndSend(uniqueChannel, "WORLD")).isEqualTo(1L);

		await().atMost(Duration.ofSeconds(2)).until(() -> bag.contains("HELLO") && bag.contains("WORLD"));
	}

	private class MessageHandler {

		private final BlockingDeque<String> bag;
		private final String name;

		MessageHandler(String name, BlockingDeque<String> bag) {

			this.bag = bag;
			this.name = name;
		}

		@SuppressWarnings("unused")
		public void handleMessage(String message) {
			bag.add(message);
		}
	}

	private static boolean clusterAvailable() {
		return ValkeyDetector.isClusterAvailable();
	}

    private static boolean isClusterAware(ValkeyConnectionFactory connectionFactory) {

        if (connectionFactory instanceof LettuceConnectionFactory lettuceConnectionFactory) {
            return lettuceConnectionFactory.isClusterAware();
        } else if (connectionFactory instanceof JedisConnectionFactory jedisConnectionFactory) {
            return jedisConnectionFactory.isValkeyClusterAware();
        } else if (connectionFactory instanceof ValkeyGlideConnectionFactory glideConnectionFactory) {
            return glideConnectionFactory.isClusterAware();
        }
        return false;
    }
}
