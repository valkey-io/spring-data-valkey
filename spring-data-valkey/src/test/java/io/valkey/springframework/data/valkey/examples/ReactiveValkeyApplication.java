/*
 * Copyright 2023-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.examples;

// tag::file[]
import reactor.core.publisher.Mono;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.core.ReactiveValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;

public class ReactiveValkeyApplication {

	private static final Log LOG = LogFactory.getLog(ReactiveValkeyApplication.class);

	public static void main(String[] args) {

		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory();
		connectionFactory.afterPropertiesSet();

		ReactiveValkeyTemplate<String, String> template = new ReactiveValkeyTemplate<>(connectionFactory,
				ValkeySerializationContext.string());

		Mono<Boolean> set = template.opsForValue().set("foo", "bar");
		set.block(Duration.ofSeconds(10));

		LOG.info("Value at foo:" + template.opsForValue().get("foo").block(Duration.ofSeconds(10)));

		connectionFactory.destroy();
	}
}
// end::file[]
