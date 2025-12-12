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
package io.valkey.springframework.data.valkey.core.script;

import static org.assertj.core.api.Assertions.*;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.ValkeyCallback;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.GenericToStringSerializer;
import io.valkey.springframework.data.valkey.serializer.Jackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementWriter;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext.ValkeySerializationContextBuilder;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;
import org.springframework.scripting.support.StaticScriptSource;

/**
 * @author Mark Paluch
 * @author Christoph Strobl
 */
public class DefaultReactiveScriptExecutorTests {

	private static LettuceConnectionFactory connectionFactory;
	private static StringValkeyTemplate stringTemplate;
	private static ReactiveScriptExecutor<String> stringScriptExecutor;

	@BeforeAll
	static void setUp() {

		connectionFactory = LettuceConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class);

		stringTemplate = new StringValkeyTemplate(connectionFactory);
		stringScriptExecutor = new DefaultReactiveScriptExecutor<>(connectionFactory, ValkeySerializationContext.string());
	}

	@BeforeEach
	void before() {

		ValkeyConnection connection = connectionFactory.getConnection();
		try {
			connection.scriptingCommands().scriptFlush();
			connection.flushDb();
		} finally {
			connection.close();
		}
	}

	protected ValkeyConnectionFactory getConnectionFactory() {
		return connectionFactory;
	}

	@Test // DATAREDIS-711
	void shouldReturnLong() {

		DefaultValkeyScript<Long> script = new DefaultValkeyScript<>();
		script.setLocation(new ClassPathResource("io/valkey/springframework/data/valkey/core/script/increment.lua"));
		script.setResultType(Long.class);

		stringScriptExecutor.execute(script, Collections.singletonList("mykey")).as(StepVerifier::create).verifyComplete();

		stringTemplate.opsForValue().set("mykey", "2");

		stringScriptExecutor.execute(script, Collections.singletonList("mykey")).as(StepVerifier::create).expectNext(3L)
				.verifyComplete();
	}

	@Test // DATAREDIS-711
	void shouldReturnBoolean() {

		ValkeySerializationContextBuilder<String, Long> builder = ValkeySerializationContext
				.newSerializationContext(StringValkeySerializer.UTF_8);
		builder.value(new GenericToStringSerializer<>(Long.class));

		DefaultValkeyScript<Boolean> script = new DefaultValkeyScript<>();
		script.setLocation(new ClassPathResource("io/valkey/springframework/data/valkey/core/script/cas.lua"));
		script.setResultType(Boolean.class);

		ReactiveScriptExecutor<String> scriptExecutor = new DefaultReactiveScriptExecutor<>(connectionFactory,
				builder.build());

		stringTemplate.opsForValue().set("counter", "0");

		scriptExecutor.execute(script, Collections.singletonList("counter"), Arrays.asList(0, 3)).as(StepVerifier::create)
				.expectNext(true).verifyComplete();

		scriptExecutor.execute(script, Collections.singletonList("counter"), Arrays.asList(0, 3)).as(StepVerifier::create)
				.expectNext(false).verifyComplete();
	}

	@Test // DATAREDIS-711
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void shouldApplyCustomArgsSerializer() {

		DefaultValkeyScript<List> script = new DefaultValkeyScript<>();
		script.setLocation(new ClassPathResource("io/valkey/springframework/data/valkey/core/script/bulkpop.lua"));
		script.setResultType(List.class);

		stringTemplate.boundListOps("mylist").leftPushAll("a", "b", "c", "d");

		Flux<List<String>> mylist = stringScriptExecutor.execute(script, Collections.singletonList("mylist"),
				Collections.singletonList(1L), ValkeyElementWriter.from(new GenericToStringSerializer<>(Long.class)),
				(ValkeyElementReader) ValkeyElementReader.from(StringValkeySerializer.UTF_8));

		mylist.as(StepVerifier::create).expectNext(Collections.singletonList("a")).verifyComplete();
	}

	@Test // DATAREDIS-711
	void testExecuteMixedListResult() {

		DefaultValkeyScript<List> script = new DefaultValkeyScript<>();
		script.setLocation(new ClassPathResource("io/valkey/springframework/data/valkey/core/script/popandlength.lua"));
		script.setResultType(List.class);

		stringScriptExecutor.execute(script, Collections.singletonList("mylist")).as(StepVerifier::create)
				.expectNext(Arrays.asList(null, 0L)).verifyComplete();

		stringTemplate.boundListOps("mylist").leftPushAll("a", "b");

		stringScriptExecutor.execute(script, Collections.singletonList("mylist")).as(StepVerifier::create)
				.expectNext(Arrays.asList("a", 1L)).verifyComplete();
	}

	@Test // DATAREDIS-711
	void shouldReturnValueResult() {

		DefaultValkeyScript<String> script = new DefaultValkeyScript<>();
		script.setScriptText("return redis.call('GET',KEYS[1])");
		script.setResultType(String.class);

		stringTemplate.opsForValue().set("foo", "bar");

		Flux<String> foo = stringScriptExecutor.execute(script, Collections.singletonList("foo"));

		foo.as(StepVerifier::create).expectNext("bar").expectNext();
	}

	@Test // DATAREDIS-711
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void shouldReturnStatusValue() {

		DefaultValkeyScript script = new DefaultValkeyScript();
		script.setScriptText("return redis.call('SET',KEYS[1], ARGV[1])");

		ValkeySerializationContextBuilder<String, Long> builder = ValkeySerializationContext
				.newSerializationContext(StringValkeySerializer.UTF_8);
		builder.value(new GenericToStringSerializer<>(Long.class));

		ReactiveScriptExecutor<String> scriptExecutor = new DefaultReactiveScriptExecutor<>(connectionFactory,
				builder.build());

		StepVerifier.create(scriptExecutor.execute(script, Collections.singletonList("foo"), Collections.singletonList(3L)))
				.expectNext("OK").verifyComplete();

		assertThat(stringTemplate.opsForValue().get("foo")).isEqualTo("3");
	}

	@Test // DATAREDIS-711
	void shouldApplyCustomResultSerializer() {

		Jackson2JsonValkeySerializer<Person> personSerializer = new Jackson2JsonValkeySerializer<>(Person.class);

		ValkeyTemplate<String, Person> template = new ValkeyTemplate<>();
		template.setKeySerializer(StringValkeySerializer.UTF_8);
		template.setValueSerializer(personSerializer);
		template.setConnectionFactory(getConnectionFactory());
		template.afterPropertiesSet();

		DefaultValkeyScript<String> script = new DefaultValkeyScript<>();
		script.setScriptSource(new StaticScriptSource("redis.call('SET',KEYS[1], ARGV[1])\nreturn 'FOO'"));
		script.setResultType(String.class);

		Person joe = new Person("Joe", "Schmoe", 23);
		Flux<String> result = stringScriptExecutor.execute(script, Collections.singletonList("bar"),
				Collections.singletonList(joe), ValkeyElementWriter.from(personSerializer),
				ValkeyElementReader.from(StringValkeySerializer.UTF_8));

		result.as(StepVerifier::create).expectNext("FOO").verifyComplete();

		assertThat(template.opsForValue().get("bar")).isEqualTo(joe);
	}

	@Test // DATAREDIS-711
	void executeAddsScriptToScriptCache() {

		DefaultValkeyScript<String> script = new DefaultValkeyScript<>();
		script.setScriptText("return 'HELLO'");
		script.setResultType(String.class);

		// Execute script twice, second time should be from cache

		assertThat(stringTemplate.execute(
				(ValkeyCallback<List<Boolean>>) connection -> connection.scriptingCommands().scriptExists(script.getSha1())))
						.containsExactly(false);

		stringScriptExecutor.execute(script, Collections.emptyList()).as(StepVerifier::create).expectNext("HELLO")
				.verifyComplete();

		assertThat(stringTemplate.execute(
				(ValkeyCallback<List<Boolean>>) connection -> connection.scriptingCommands().scriptExists(script.getSha1())))
						.containsExactly(true);

		stringScriptExecutor.execute(script, Collections.emptyList()).as(StepVerifier::create).expectNext("HELLO")
				.verifyComplete();
	}
}
