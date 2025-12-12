/*
 * Copyright 2017-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.core;

import reactor.test.StepVerifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementWriter;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;

/**
 * Integration tests for {@link ReactiveStringValkeyTemplate}.
 *
 * @author Mark Paluch
 */
@ExtendWith(LettuceConnectionFactoryExtension.class)
public class ReactiveStringValkeyTemplateIntegrationTests {

	private final ReactiveValkeyConnectionFactory connectionFactory;

	private ReactiveStringValkeyTemplate template;

	public ReactiveStringValkeyTemplateIntegrationTests(ReactiveValkeyConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	@BeforeEach
	void before() {

		template = new ReactiveStringValkeyTemplate(connectionFactory);

		template.execute(connection -> connection.serverCommands().flushDb()).as(StepVerifier::create).expectNext("OK")
				.verifyComplete();
	}

	@Test // DATAREDIS-643
	void shouldSetAndGetKeys() {

		template.opsForValue().set("key", "value").as(StepVerifier::create).expectNext(true).verifyComplete();
		template.opsForValue().get("key").as(StepVerifier::create).expectNext("value").verifyComplete();
	}

	@Test // GH-2655
	void keysFailsOnNullElements() {

		template.opsForValue().set("a", "1").as(StepVerifier::create).expectNext(true).verifyComplete();
		template.opsForValue().set("b", "1").as(StepVerifier::create).expectNext(true).verifyComplete();

		ValkeyElementReader<String> reader = ValkeyElementReader.from(StringValkeySerializer.UTF_8);
		ValkeyElementWriter<String> writer = ValkeyElementWriter.from(StringValkeySerializer.UTF_8);

		ValkeySerializationContext<String, String> nullReadingContext = ValkeySerializationContext
				.<String, String>newSerializationContext(StringValkeySerializer.UTF_8).key(buffer -> {

					String read = reader.read(buffer);

					return "a".equals(read) ? null : read;

				}, writer).build();

		ReactiveValkeyTemplate<String, String> customTemplate = new ReactiveValkeyTemplate<>(template.getConnectionFactory(),
				nullReadingContext);

		customTemplate.keys("b").as(StepVerifier::create).expectNext("b").verifyComplete();
		customTemplate.keys("a").as(StepVerifier::create).verifyError(InvalidDataAccessApiUsageException.class);
	}
}
