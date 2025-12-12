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
package io.valkey.springframework.data.valkey.support.collections;

import java.util.Arrays;
import java.util.Collection;

import io.valkey.springframework.data.valkey.DoubleAsStringObjectFactory;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.PersonObjectFactory;
import io.valkey.springframework.data.valkey.RawObjectFactory;
import io.valkey.springframework.data.valkey.StringObjectFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.Jackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.OxmSerializer;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.XstreamOxmSerializerSingleton;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * @author Costin Leau
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author Christoph Strobl
 */
public abstract class CollectionTestParams {

	public static Collection<Object[]> testParams() {

		OxmSerializer serializer = XstreamOxmSerializerSingleton.getInstance();
		Jackson2JsonValkeySerializer<Person> jackson2JsonSerializer = new Jackson2JsonValkeySerializer<>(Person.class);
		StringValkeySerializer stringSerializer = StringValkeySerializer.UTF_8;

		// create Jedis Factory
		ObjectFactory<String> stringFactory = new StringObjectFactory();
		ObjectFactory<String> doubleAsStringObjectFactory = new DoubleAsStringObjectFactory();
		ObjectFactory<Person> personFactory = new PersonObjectFactory();
		ObjectFactory<byte[]> rawFactory = new RawObjectFactory();

		JedisConnectionFactory jedisConnFactory = JedisConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		ValkeyTemplate<String, String> stringTemplate = new StringValkeyTemplate(jedisConnFactory);
		ValkeyTemplate<String, Person> personTemplate = new ValkeyTemplate<>();
		personTemplate.setConnectionFactory(jedisConnFactory);
		personTemplate.afterPropertiesSet();

		ValkeyTemplate<String, String> xstreamStringTemplate = new ValkeyTemplate<>();
		xstreamStringTemplate.setConnectionFactory(jedisConnFactory);
		xstreamStringTemplate.setDefaultSerializer(serializer);
		xstreamStringTemplate.afterPropertiesSet();

		ValkeyTemplate<String, Person> xstreamPersonTemplate = new ValkeyTemplate<>();
		xstreamPersonTemplate.setConnectionFactory(jedisConnFactory);
		xstreamPersonTemplate.setValueSerializer(serializer);
		xstreamPersonTemplate.afterPropertiesSet();

		// jackson2
		ValkeyTemplate<String, Person> jackson2JsonPersonTemplate = new ValkeyTemplate<>();
		jackson2JsonPersonTemplate.setConnectionFactory(jedisConnFactory);
		jackson2JsonPersonTemplate.setValueSerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplate.afterPropertiesSet();

		ValkeyTemplate<byte[], byte[]> rawTemplate = new ValkeyTemplate<>();
		rawTemplate.setConnectionFactory(jedisConnFactory);
		rawTemplate.setEnableDefaultSerializer(false);
		rawTemplate.setKeySerializer(stringSerializer);
		rawTemplate.afterPropertiesSet();

		// Lettuce
		LettuceConnectionFactory lettuceConnFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		ValkeyTemplate<String, String> stringTemplateLtc = new StringValkeyTemplate(lettuceConnFactory);
		ValkeyTemplate<String, Person> personTemplateLtc = new ValkeyTemplate<>();
		personTemplateLtc.setConnectionFactory(lettuceConnFactory);
		personTemplateLtc.afterPropertiesSet();

		ValkeyTemplate<String, Person> xstreamStringTemplateLtc = new ValkeyTemplate<>();
		xstreamStringTemplateLtc.setConnectionFactory(lettuceConnFactory);
		xstreamStringTemplateLtc.setDefaultSerializer(serializer);
		xstreamStringTemplateLtc.afterPropertiesSet();

		ValkeyTemplate<String, Person> xstreamPersonTemplateLtc = new ValkeyTemplate<>();
		xstreamPersonTemplateLtc.setValueSerializer(serializer);
		xstreamPersonTemplateLtc.setConnectionFactory(lettuceConnFactory);
		xstreamPersonTemplateLtc.afterPropertiesSet();

		ValkeyTemplate<String, Person> jackson2JsonPersonTemplateLtc = new ValkeyTemplate<>();
		jackson2JsonPersonTemplateLtc.setValueSerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplateLtc.setConnectionFactory(lettuceConnFactory);
		jackson2JsonPersonTemplateLtc.afterPropertiesSet();

		ValkeyTemplate<byte[], byte[]> rawTemplateLtc = new ValkeyTemplate<>();
		rawTemplateLtc.setConnectionFactory(lettuceConnFactory);
		rawTemplateLtc.setEnableDefaultSerializer(false);
		rawTemplateLtc.setKeySerializer(stringSerializer);
		rawTemplateLtc.afterPropertiesSet();

		// ValkeyGlide
		ValkeyGlideConnectionFactory valkeyGlideConnFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		ValkeyTemplate<String, String> stringTemplateVkg = new StringValkeyTemplate(valkeyGlideConnFactory);
		ValkeyTemplate<String, Person> personTemplateVkg = new ValkeyTemplate<>();
		personTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		personTemplateVkg.afterPropertiesSet();

		ValkeyTemplate<String, String> xstreamStringTemplateVkg = new ValkeyTemplate<>();
		xstreamStringTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		xstreamStringTemplateVkg.setDefaultSerializer(serializer);
		xstreamStringTemplateVkg.afterPropertiesSet();

		ValkeyTemplate<String, Person> xstreamPersonTemplateVkg = new ValkeyTemplate<>();
		xstreamPersonTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		xstreamPersonTemplateVkg.setValueSerializer(serializer);
		xstreamPersonTemplateVkg.afterPropertiesSet();

		ValkeyTemplate<String, Person> jackson2JsonPersonTemplateVkg = new ValkeyTemplate<>();
		jackson2JsonPersonTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		jackson2JsonPersonTemplateVkg.setValueSerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplateVkg.afterPropertiesSet();

		ValkeyTemplate<byte[], byte[]> rawTemplateVkg = new ValkeyTemplate<>();
		rawTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		rawTemplateVkg.setEnableDefaultSerializer(false);
		rawTemplateVkg.setKeySerializer(stringSerializer);
		rawTemplateVkg.afterPropertiesSet();
		
		return Arrays.asList(new Object[][] { { stringFactory, stringTemplate }, //
				{ doubleAsStringObjectFactory, stringTemplate }, //
				{ personFactory, personTemplate }, //
				{ stringFactory, xstreamStringTemplate }, //
				{ personFactory, xstreamPersonTemplate }, //
				{ personFactory, jackson2JsonPersonTemplate }, //
				{ rawFactory, rawTemplate },

				// lettuce
				{ stringFactory, stringTemplateLtc }, //
				{ personFactory, personTemplateLtc }, //
				{ doubleAsStringObjectFactory, stringTemplateLtc }, //
				{ personFactory, personTemplateLtc }, //
				{ stringFactory, xstreamStringTemplateLtc }, //
				{ personFactory, xstreamPersonTemplateLtc }, //
				{ personFactory, jackson2JsonPersonTemplateLtc }, //
				{ rawFactory, rawTemplateLtc },

				// ValkeyGlide
				{ stringFactory, stringTemplateVkg }, //
				{ personFactory, personTemplateVkg }, //
				{ doubleAsStringObjectFactory, stringTemplateVkg }, //
				{ personFactory, personTemplateVkg }, //
				{ stringFactory, xstreamStringTemplateVkg }, //
				{ personFactory, xstreamPersonTemplateVkg }, //
				{ personFactory, jackson2JsonPersonTemplateVkg }, //
				{ rawFactory, rawTemplateVkg } });
	}
}
