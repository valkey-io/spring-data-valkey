/*
 * Copyright 2013-2025 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.valkey.springframework.data.valkey.DoubleObjectFactory;
import io.valkey.springframework.data.valkey.LongObjectFactory;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.PersonObjectFactory;
import io.valkey.springframework.data.valkey.RawObjectFactory;
import io.valkey.springframework.data.valkey.StringObjectFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.serializer.GenericJackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.GenericToStringSerializer;
import io.valkey.springframework.data.valkey.serializer.Jackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.OxmSerializer;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.XstreamOxmSerializerSingleton;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * Parameters for testing implementations of {@link AbstractOperations}
 *
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 */
abstract public class AbstractOperationsTestParams {

	// DATAREDIS-241
	public static Collection<Object[]> testParams() {

		List<Object[]> params = new ArrayList<>();
		params.addAll(testParams(LettuceConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class)));
		params.addAll(testParams(JedisConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class)));
		params.addAll(testParams(ValkeyGlideConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class)));
		return params;
	}

	// DATAREDIS-241
	public static Collection<Object[]> testParams(ValkeyConnectionFactory connectionFactory) {

		ObjectFactory<String> stringFactory = new StringObjectFactory();
		ObjectFactory<Long> longFactory = new LongObjectFactory();
		ObjectFactory<Double> doubleFactory = new DoubleObjectFactory();
		ObjectFactory<byte[]> rawFactory = new RawObjectFactory();
		ObjectFactory<Person> personFactory = new PersonObjectFactory();

		ValkeyTemplate<String, String> stringTemplate = new StringValkeyTemplate();
		stringTemplate.setConnectionFactory(connectionFactory);
		stringTemplate.afterPropertiesSet();

		ValkeyTemplate<String, Long> longTemplate = new ValkeyTemplate<>();
		longTemplate.setKeySerializer(StringValkeySerializer.UTF_8);
		longTemplate.setValueSerializer(new GenericToStringSerializer<>(Long.class));
		longTemplate.setConnectionFactory(connectionFactory);
		longTemplate.afterPropertiesSet();

		ValkeyTemplate<String, Double> doubleTemplate = new ValkeyTemplate<>();
		doubleTemplate.setKeySerializer(StringValkeySerializer.UTF_8);
		doubleTemplate.setValueSerializer(new GenericToStringSerializer<>(Double.class));
		doubleTemplate.setConnectionFactory(connectionFactory);
		doubleTemplate.afterPropertiesSet();

		ValkeyTemplate<byte[], byte[]> rawTemplate = new ValkeyTemplate<>();
		rawTemplate.setEnableDefaultSerializer(false);
		rawTemplate.setConnectionFactory(connectionFactory);
		rawTemplate.afterPropertiesSet();

		ValkeyTemplate<String, Person> personTemplate = new ValkeyTemplate<>();
		personTemplate.setConnectionFactory(connectionFactory);
		personTemplate.afterPropertiesSet();

		OxmSerializer serializer = XstreamOxmSerializerSingleton.getInstance();
		ValkeyTemplate<String, String> xstreamStringTemplate = new ValkeyTemplate<>();
		xstreamStringTemplate.setConnectionFactory(connectionFactory);
		xstreamStringTemplate.setDefaultSerializer(serializer);
		xstreamStringTemplate.afterPropertiesSet();

		ValkeyTemplate<String, Person> xstreamPersonTemplate = new ValkeyTemplate<>();
		xstreamPersonTemplate.setConnectionFactory(connectionFactory);
		xstreamPersonTemplate.setValueSerializer(serializer);
		xstreamPersonTemplate.afterPropertiesSet();

		Jackson2JsonValkeySerializer<Person> jackson2JsonSerializer = new Jackson2JsonValkeySerializer<>(Person.class);
		ValkeyTemplate<String, Person> jackson2JsonPersonTemplate = new ValkeyTemplate<>();
		jackson2JsonPersonTemplate.setConnectionFactory(connectionFactory);
		jackson2JsonPersonTemplate.setValueSerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplate.afterPropertiesSet();

		GenericJackson2JsonValkeySerializer genericJackson2JsonSerializer = new GenericJackson2JsonValkeySerializer();
		ValkeyTemplate<String, Person> genericJackson2JsonPersonTemplate = new ValkeyTemplate<>();
		genericJackson2JsonPersonTemplate.setConnectionFactory(connectionFactory);
		genericJackson2JsonPersonTemplate.setValueSerializer(genericJackson2JsonSerializer);
		genericJackson2JsonPersonTemplate.afterPropertiesSet();

		return Arrays.asList(new Object[][] { //
				{ stringTemplate, stringFactory, stringFactory }, //
				{ longTemplate, stringFactory, longFactory }, //
				{ doubleTemplate, stringFactory, doubleFactory }, //
				{ rawTemplate, rawFactory, rawFactory }, //
				{ personTemplate, stringFactory, personFactory }, //
				{ xstreamStringTemplate, stringFactory, stringFactory }, //
				{ xstreamPersonTemplate, stringFactory, personFactory }, //
				{ jackson2JsonPersonTemplate, stringFactory, personFactory }, //
				{ genericJackson2JsonPersonTemplate, stringFactory, personFactory } });
	}
}
