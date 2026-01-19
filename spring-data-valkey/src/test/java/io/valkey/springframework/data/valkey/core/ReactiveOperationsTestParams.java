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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.valkey.springframework.data.valkey.ByteBufferObjectFactory;
import io.valkey.springframework.data.valkey.DoubleObjectFactory;
import io.valkey.springframework.data.valkey.LongObjectFactory;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.PersonObjectFactory;
import io.valkey.springframework.data.valkey.PrefixStringObjectFactory;
import io.valkey.springframework.data.valkey.StringObjectFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.serializer.GenericJackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.GenericToStringSerializer;
import io.valkey.springframework.data.valkey.serializer.Jackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.JdkSerializationValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.OxmSerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.XstreamOxmSerializerSingleton;
import io.valkey.springframework.data.valkey.test.condition.ValkeyDetector;
import io.valkey.springframework.data.valkey.test.extension.ValkeyCluster;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;
import org.springframework.lang.Nullable;

/**
 * Parameters for testing implementations of {@link ReactiveValkeyTemplate}
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
abstract public class ReactiveOperationsTestParams {

	public static Collection<Fixture<?, ?>> testParams() {

		ObjectFactory<String> stringFactory = new StringObjectFactory();
		ObjectFactory<String> clusterKeyStringFactory = new PrefixStringObjectFactory("{u1}.", stringFactory);
		ObjectFactory<Long> longFactory = new LongObjectFactory();
		ObjectFactory<Double> doubleFactory = new DoubleObjectFactory();
		ObjectFactory<ByteBuffer> rawFactory = new ByteBufferObjectFactory();
		ObjectFactory<Person> personFactory = new PersonObjectFactory();

		LettuceConnectionFactory lettuceConnectionFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		ReactiveValkeyTemplate<Object, Object> objectTemplate = new ReactiveValkeyTemplate<>(lettuceConnectionFactory,
				ValkeySerializationContext.java(ReactiveOperationsTestParams.class.getClassLoader()));

		StringValkeySerializer stringValkeySerializer = StringValkeySerializer.UTF_8;
		ReactiveValkeyTemplate<String, String> stringTemplate = new ReactiveValkeyTemplate<>(lettuceConnectionFactory,
				ValkeySerializationContext.fromSerializer(stringValkeySerializer));

		JdkSerializationValkeySerializer jdkSerializationValkeySerializer = new JdkSerializationValkeySerializer();
		GenericToStringSerializer<Long> longToStringSerializer = new GenericToStringSerializer(Long.class);
		ReactiveValkeyTemplate<String, Long> longTemplate = new ReactiveValkeyTemplate<>(lettuceConnectionFactory,
				ValkeySerializationContext.<String, Long> newSerializationContext(jdkSerializationValkeySerializer)
						.key(stringValkeySerializer).value(longToStringSerializer).build());

		GenericToStringSerializer<Double> doubleToStringSerializer = new GenericToStringSerializer(Double.class);
		ReactiveValkeyTemplate<String, Double> doubleTemplate = new ReactiveValkeyTemplate<>(lettuceConnectionFactory,
				ValkeySerializationContext.<String, Double> newSerializationContext(jdkSerializationValkeySerializer)
						.key(stringValkeySerializer).value(doubleToStringSerializer).build());

		ReactiveValkeyTemplate<ByteBuffer, ByteBuffer> rawTemplate = new ReactiveValkeyTemplate<>(lettuceConnectionFactory,
				ValkeySerializationContext.byteBuffer());

		ReactiveValkeyTemplate<String, Person> personTemplate = new ReactiveValkeyTemplate(lettuceConnectionFactory,
				ValkeySerializationContext.fromSerializer(jdkSerializationValkeySerializer));

		OxmSerializer oxmSerializer = XstreamOxmSerializerSingleton.getInstance();
		ReactiveValkeyTemplate<String, String> xstreamStringTemplate = new ReactiveValkeyTemplate(lettuceConnectionFactory,
				ValkeySerializationContext.fromSerializer(oxmSerializer));

		ReactiveValkeyTemplate<String, Person> xstreamPersonTemplate = new ReactiveValkeyTemplate(lettuceConnectionFactory,
				ValkeySerializationContext.fromSerializer(oxmSerializer));

		Jackson2JsonValkeySerializer<Person> jackson2JsonSerializer = new Jackson2JsonValkeySerializer<>(Person.class);
		ReactiveValkeyTemplate<String, Person> jackson2JsonPersonTemplate = new ReactiveValkeyTemplate(
				lettuceConnectionFactory, ValkeySerializationContext.fromSerializer(jackson2JsonSerializer));

		GenericJackson2JsonValkeySerializer genericJackson2JsonSerializer = new GenericJackson2JsonValkeySerializer();
		ReactiveValkeyTemplate<String, Person> genericJackson2JsonPersonTemplate = new ReactiveValkeyTemplate(
				lettuceConnectionFactory, ValkeySerializationContext.fromSerializer(genericJackson2JsonSerializer));

		List<Fixture<?, ?>> list = Arrays.asList( //
				new Fixture<>(stringTemplate, stringFactory, stringFactory, stringValkeySerializer, "String"), //
				new Fixture<>(objectTemplate, personFactory, personFactory, jdkSerializationValkeySerializer, "Person/JDK"), //
				new Fixture<>(longTemplate, stringFactory, longFactory, longToStringSerializer, "Long"), //
				new Fixture<>(doubleTemplate, stringFactory, doubleFactory, doubleToStringSerializer, "Double"), //
				new Fixture<>(rawTemplate, rawFactory, rawFactory, null, "raw"), //
				new Fixture<>(personTemplate, stringFactory, personFactory, jdkSerializationValkeySerializer,
						"String/Person/JDK"), //
				new Fixture<>(xstreamStringTemplate, stringFactory, stringFactory, oxmSerializer, "String/OXM"), //
				new Fixture<>(xstreamPersonTemplate, stringFactory, personFactory, oxmSerializer, "String/Person/OXM"), //
				new Fixture<>(jackson2JsonPersonTemplate, stringFactory, personFactory, jackson2JsonSerializer, "Jackson2"), //
				new Fixture<>(genericJackson2JsonPersonTemplate, stringFactory, personFactory, genericJackson2JsonSerializer,
						"Generic Jackson 2"));

		if (clusterAvailable()) {

			ReactiveValkeyTemplate<String, String> clusterStringTemplate;

			LettuceConnectionFactory lettuceClusterConnectionFactory = LettuceConnectionFactoryExtension
					.getConnectionFactory(ValkeyCluster.class);

			clusterStringTemplate = new ReactiveValkeyTemplate<>(lettuceClusterConnectionFactory,
					ValkeySerializationContext.string());

			list = new ArrayList<>(list);
			list.add(new Fixture<>(clusterStringTemplate, clusterKeyStringFactory, stringFactory, stringValkeySerializer,
					"Cluster String"));
		}

		return list;
	}

	private static boolean clusterAvailable() {
		return ValkeyDetector.isClusterAvailable();
	}

	static class Fixture<K, V> {

		private final ReactiveValkeyTemplate<K, V> template;
		private final ObjectFactory<K> keyFactory;
		private final ObjectFactory<V> valueFactory;
		private final @Nullable ValkeySerializer<K> serializer;
		private final String label;

		public Fixture(ReactiveValkeyTemplate<?, ?> template, ObjectFactory<K> keyFactory, ObjectFactory<V> valueFactory,
				@Nullable ValkeySerializer<?> serializer, String label) {

			this.template = (ReactiveValkeyTemplate) template;
			this.keyFactory = keyFactory;
			this.valueFactory = valueFactory;
			this.serializer = (ValkeySerializer) serializer;
			this.label = label;
		}

		public ReactiveValkeyTemplate<K, V> getTemplate() {
			return template;
		}

		public ObjectFactory<K> getKeyFactory() {
			return keyFactory;
		}

		public ObjectFactory<V> getValueFactory() {
			return valueFactory;
		}

		@Nullable
		public ValkeySerializer getSerializer() {
			return serializer;
		}

		public String getLabel() {
			return label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

}
