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
package io.valkey.springframework.data.valkey.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import io.valkey.springframework.data.valkey.SettingsUtils;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.serializer.GenericJackson2JsonValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.JdkSerializationValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.OxmSerializer;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.SerializationException;
import io.valkey.springframework.data.valkey.test.XstreamOxmSerializerSingleton;
import io.valkey.springframework.data.valkey.test.condition.ValkeyDetector;
import io.valkey.springframework.data.valkey.test.extension.ValkeyCluster;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 */
class CacheTestParams {

	private static Collection<ValkeyConnectionFactory> connectionFactories() {

		ValkeyStandaloneConfiguration config = new ValkeyStandaloneConfiguration();
		config.setHostName(SettingsUtils.getHost());
		config.setPort(SettingsUtils.getPort());

		List<ValkeyConnectionFactory> factoryList = new ArrayList<>(3);

		// Jedis Standalone
		JedisConnectionFactory jedisConnectionFactory = JedisConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);
		factoryList.add(jedisConnectionFactory);

		// Lettuce Standalone
		LettuceConnectionFactory lettuceConnectionFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);
		factoryList.add(lettuceConnectionFactory);

		// ValkeyGlide Standalone
		ValkeyGlideConnectionFactory valkeyGlideConnectionFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);
		factoryList.add(valkeyGlideConnectionFactory);

		if (clusterAvailable()) {

			// Jedis Cluster
			JedisConnectionFactory jedisClusterConnectionFactory = JedisConnectionFactoryExtension
					.getConnectionFactory(ValkeyCluster.class);
			factoryList
					.add(jedisClusterConnectionFactory);

			// Lettuce Cluster
			LettuceConnectionFactory lettuceClusterConnectionFactory = LettuceConnectionFactoryExtension
					.getConnectionFactory(ValkeyCluster.class);
			factoryList
					.add(lettuceClusterConnectionFactory);

			// Valkey-Glide Cluster
			ValkeyGlideConnectionFactory valkeyGlideClusterConnectionFactory = ValkeyGlideConnectionFactoryExtension
					.getConnectionFactory(ValkeyCluster.class);
			factoryList
					.add(valkeyGlideClusterConnectionFactory);
		}

		return factoryList;
	}

	static Collection<Object[]> justConnectionFactories() {
		return connectionFactories().stream().map(factory -> new Object[] { factory }).collect(Collectors.toList());
	}

	static Collection<Object[]> connectionFactoriesAndSerializers() {

		OxmSerializer oxmSerializer = XstreamOxmSerializerSingleton.getInstance();
		GenericJackson2JsonValkeySerializer jackson2Serializer = new GenericJackson2JsonValkeySerializer();
		JdkSerializationValkeySerializer jdkSerializer = new JdkSerializationValkeySerializer();

		return connectionFactories().stream().flatMap(factory -> Arrays.asList( //
				new Object[] { factory, new FixDamnedJunitParameterizedNameForValkeySerializer(jdkSerializer) }, //
				new Object[] { factory, new FixDamnedJunitParameterizedNameForValkeySerializer(jackson2Serializer) }, //
				new Object[] { factory, new FixDamnedJunitParameterizedNameForValkeySerializer(oxmSerializer) }).stream())
				.collect(Collectors.toList());
	}

	static class FixDamnedJunitParameterizedNameForValkeySerializer/* ¯\_(ツ)_/¯ */ implements ValkeySerializer {

		final ValkeySerializer serializer;

		FixDamnedJunitParameterizedNameForValkeySerializer(ValkeySerializer serializer) {
			this.serializer = serializer;
		}

		@Override
		@Nullable
		public byte[] serialize(@Nullable Object value) throws SerializationException {
			return serializer.serialize(value);
		}

		@Override
		@Nullable
		public Object deserialize(@Nullable byte[] bytes) throws SerializationException {
			return serializer.deserialize(bytes);
		}

		public static ValkeySerializer<Object> java() {
			return ValkeySerializer.java();
		}

		public static ValkeySerializer<Object> java(@Nullable ClassLoader classLoader) {
			return ValkeySerializer.java(classLoader);
		}

		public static ValkeySerializer<Object> json() {
			return ValkeySerializer.json();
		}

		public static ValkeySerializer<String> string() {
			return ValkeySerializer.string();
		}

		public static ValkeySerializer<byte[]> byteArray() {
			return ValkeySerializer.byteArray();
		}

		@Override
		public boolean canSerialize(Class type) {
			return serializer.canSerialize(type);
		}

		@Override
		public Class<?> getTargetType() {
			return serializer.getTargetType();
		}

		@Override // Why Junit? Why?
		public String toString() {
			return serializer.getClass().getSimpleName();
		}
	}

	private static boolean clusterAvailable() {
		return ValkeyDetector.isClusterAvailable();
	}
}
