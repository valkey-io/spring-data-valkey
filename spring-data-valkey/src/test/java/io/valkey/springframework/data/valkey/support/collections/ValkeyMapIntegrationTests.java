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
import io.valkey.springframework.data.valkey.LongAsStringObjectFactory;
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
 * Integration test for ValkeyMap.
 *
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public class ValkeyMapIntegrationTests extends AbstractValkeyMapIntegrationTests<Object, Object> {

	@SuppressWarnings("rawtypes")
	public ValkeyMapIntegrationTests(ObjectFactory<Object> keyFactory, ObjectFactory<Object> valueFactory,
			ValkeyTemplate template) {
		super(keyFactory, valueFactory, template);
	}

	@SuppressWarnings("unchecked")
	ValkeyMap<Object, Object> createMap() {
		String valkeyName = getClass().getSimpleName();
		return new DefaultValkeyMap<Object, Object>(valkeyName, template);
	}

	// DATAREDIS-241
	@SuppressWarnings("rawtypes")
	public static Collection<Object[]> testParams() {

		OxmSerializer serializer = XstreamOxmSerializerSingleton.getInstance();
		Jackson2JsonValkeySerializer<Person> jackson2JsonSerializer = new Jackson2JsonValkeySerializer<>(Person.class);
		Jackson2JsonValkeySerializer<String> jackson2JsonStringSerializer = new Jackson2JsonValkeySerializer<>(
				String.class);
		StringValkeySerializer stringSerializer = StringValkeySerializer.UTF_8;

		// create Jedis Factory
		ObjectFactory<String> stringFactory = new StringObjectFactory();
		ObjectFactory<Person> personFactory = new PersonObjectFactory();
		ObjectFactory<String> doubleFactory = new DoubleAsStringObjectFactory();
		ObjectFactory<String> longFactory = new LongAsStringObjectFactory();
		ObjectFactory<byte[]> rawFactory = new RawObjectFactory();

		JedisConnectionFactory jedisConnFactory = JedisConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		ValkeyTemplate genericTemplate = new ValkeyTemplate();
		genericTemplate.setConnectionFactory(jedisConnFactory);
		genericTemplate.afterPropertiesSet();

		ValkeyTemplate<String, String> xstreamGenericTemplate = new ValkeyTemplate<>();
		xstreamGenericTemplate.setConnectionFactory(jedisConnFactory);
		xstreamGenericTemplate.setDefaultSerializer(serializer);
		xstreamGenericTemplate.afterPropertiesSet();

		ValkeyTemplate<String, Person> jackson2JsonPersonTemplate = new ValkeyTemplate<>();
		jackson2JsonPersonTemplate.setConnectionFactory(jedisConnFactory);
		jackson2JsonPersonTemplate.setDefaultSerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplate.setHashKeySerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplate.setHashValueSerializer(jackson2JsonStringSerializer);
		jackson2JsonPersonTemplate.afterPropertiesSet();

		ValkeyTemplate<String, byte[]> rawTemplate = new ValkeyTemplate<>();
		rawTemplate.setEnableDefaultSerializer(false);
		rawTemplate.setConnectionFactory(jedisConnFactory);
		rawTemplate.setKeySerializer(stringSerializer);
		rawTemplate.afterPropertiesSet();

		// Lettuce
		LettuceConnectionFactory lettuceConnFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class, false);

		ValkeyTemplate genericTemplateLettuce = new ValkeyTemplate();
		genericTemplateLettuce.setConnectionFactory(lettuceConnFactory);
		genericTemplateLettuce.afterPropertiesSet();

		ValkeyTemplate<String, Person> xGenericTemplateLettuce = new ValkeyTemplate<>();
		xGenericTemplateLettuce.setConnectionFactory(lettuceConnFactory);
		xGenericTemplateLettuce.setDefaultSerializer(serializer);
		xGenericTemplateLettuce.afterPropertiesSet();

		ValkeyTemplate<String, Person> jackson2JsonPersonTemplateLettuce = new ValkeyTemplate<>();
		jackson2JsonPersonTemplateLettuce.setConnectionFactory(lettuceConnFactory);
		jackson2JsonPersonTemplateLettuce.setDefaultSerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplateLettuce.setHashKeySerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplateLettuce.setHashValueSerializer(jackson2JsonStringSerializer);
		jackson2JsonPersonTemplateLettuce.afterPropertiesSet();

		ValkeyTemplate<String, String> stringTemplateLtc = new StringValkeyTemplate();
		stringTemplateLtc.setConnectionFactory(lettuceConnFactory);
		stringTemplateLtc.afterPropertiesSet();

		ValkeyTemplate<String, byte[]> rawTemplateLtc = new ValkeyTemplate<>();
		rawTemplateLtc.setEnableDefaultSerializer(false);
		rawTemplateLtc.setConnectionFactory(lettuceConnFactory);
		rawTemplateLtc.setKeySerializer(stringSerializer);
		rawTemplateLtc.afterPropertiesSet();

		// ValkeyGlide
		ValkeyGlideConnectionFactory valkeyGlideConnFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);
		ValkeyTemplate genericTemplateVkg = new ValkeyTemplate();
		genericTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		genericTemplateVkg.afterPropertiesSet();
		
		ValkeyTemplate<String, Person> xGenericTemplateVkg = new ValkeyTemplate<>();
		xGenericTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		xGenericTemplateVkg.setDefaultSerializer(serializer);
		xGenericTemplateVkg.afterPropertiesSet();

		ValkeyTemplate<String, Person> jackson2JsonPersonTemplateVkg = new ValkeyTemplate<>();
		jackson2JsonPersonTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		jackson2JsonPersonTemplateVkg.setDefaultSerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplateVkg.setHashKeySerializer(jackson2JsonSerializer);
		jackson2JsonPersonTemplateVkg.setHashValueSerializer(jackson2JsonStringSerializer);
		jackson2JsonPersonTemplateVkg.afterPropertiesSet();

		ValkeyTemplate<String, String> stringTemplateVkg = new StringValkeyTemplate(valkeyGlideConnFactory);
		stringTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		stringTemplateVkg.afterPropertiesSet();

		ValkeyTemplate<String, byte[]> rawTemplateVkg = new ValkeyTemplate<>();
		rawTemplateVkg.setEnableDefaultSerializer(false);
		rawTemplateVkg.setConnectionFactory(valkeyGlideConnFactory);
		rawTemplateVkg.setKeySerializer(stringSerializer);
		rawTemplateVkg.afterPropertiesSet();

		return Arrays.asList(new Object[][] { { stringFactory, stringFactory, genericTemplate },
				{ personFactory, personFactory, genericTemplate }, //
				{ stringFactory, personFactory, genericTemplate }, //
				{ personFactory, stringFactory, genericTemplate }, //
				{ personFactory, stringFactory, xstreamGenericTemplate }, //
				{ personFactory, stringFactory, jackson2JsonPersonTemplate }, //
				{ rawFactory, rawFactory, rawTemplate }, //

				// lettuce
				{ stringFactory, stringFactory, genericTemplateLettuce }, //
				{ personFactory, personFactory, genericTemplateLettuce }, //
				{ stringFactory, personFactory, genericTemplateLettuce }, //
				{ personFactory, stringFactory, genericTemplateLettuce }, //
				{ personFactory, stringFactory, xGenericTemplateLettuce }, //
				{ personFactory, stringFactory, jackson2JsonPersonTemplateLettuce }, //
				{ stringFactory, doubleFactory, stringTemplateLtc }, //
				{ stringFactory, longFactory, stringTemplateLtc }, //
				{ rawFactory, rawFactory, rawTemplateLtc },
			
				// valkeyglide
				{ stringFactory, stringFactory, genericTemplateVkg }, //
				{ personFactory, personFactory, genericTemplateVkg }, //
				{ stringFactory, personFactory, genericTemplateVkg }, //
				{ personFactory, stringFactory, genericTemplateVkg }, //
				{ personFactory, stringFactory, xGenericTemplateVkg }, //
				{ personFactory, stringFactory, jackson2JsonPersonTemplateVkg }, //
				{ stringFactory, doubleFactory, stringTemplateVkg }, //
				{ stringFactory, longFactory, stringTemplateVkg }, //
				{ rawFactory, rawFactory, rawTemplateVkg } });
	}
}
