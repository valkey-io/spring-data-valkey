/*
 * Copyright 2026 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.valkey.springframework.data.valkey.config.ValkeyListenerConfigUtils;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStandalone;

/**
 * Integration test for {@link EnableValkeyListeners} and {@link ValkeyListener}.
 *
 * @author Mark Paluch
 * @author Ilyass Bougati
 */
@ParameterizedClass
@MethodSource("testParams")
public class ValkeyListenerIntegrationTests {

	private ValkeyConnectionFactory connectionFactory;
	private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	public ValkeyListenerIntegrationTests(ValkeyConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	static Collection<Arguments> testParams() {
		// Jedis
		JedisConnectionFactory jedisConnFactory = JedisConnectionFactoryExtension
				.getConnectionFactory(ValkeyStandalone.class);

		// Lettuce
		LettuceConnectionFactory lettuceConnFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStandalone.class);

		return List.of(Arguments.argumentSet("Jedis", jedisConnFactory),
				Arguments.argumentSet("Lettuce", lettuceConnFactory));
	}

	@Test // GH-1004
	void shouldListenForMessage() throws InterruptedException {

		context.registerBean(ValkeyListenerConfigUtils.VALKEY_MESSAGE_LISTENER_BEAN_NAME, ValkeyMessageListenerContainer.class,
				() -> {

			ValkeyMessageListenerContainer container = new ValkeyMessageListenerContainer();
			container.setRecoveryInterval(100);
			container.setConnectionFactory(connectionFactory);
			return container;
		});

		context.register(Config.class);
		context.refresh();

		StringValkeyTemplate template = new StringValkeyTemplate();
		template.setConnectionFactory(connectionFactory);
		template.afterPropertiesSet();

		MyListener bean = context.getBean(MyListener.class);
		bean.message.clear();

		template.convertAndSend("my-channel-listener", "Hello Redis!");

		String message = bean.message.poll(10, TimeUnit.SECONDS);
		assertThat(message).isEqualTo("Hello Redis!");
	}

	@AfterEach
	void tearDown() {
		context.stop();
	}

	@Configuration
	@EnableValkeyListeners
	static class Config {

		@Bean
		MyListener myListener() {
			return new MyListener();
		}
	}

	static class MyListener {

		LinkedBlockingQueue<String> message = new LinkedBlockingQueue<>();

		@ValkeyListener("my-channel-listener")
		void onMessage(String msg) {
			message.offer(msg);
		}

	}

}
