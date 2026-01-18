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

import java.util.ArrayList;
import java.util.Collection;

import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.PersonObjectFactory;
import io.valkey.springframework.data.valkey.StringObjectFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.test.condition.ValkeyDetector;
import io.valkey.springframework.data.valkey.test.extension.ValkeyCluster;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Mark Paluch
 */
public class PubSubTestParams {

	public static Collection<Object[]> testParams() {
		// create Jedis Factory
		ObjectFactory<String> stringFactory = new StringObjectFactory();
		ObjectFactory<Person> personFactory = new PersonObjectFactory();

		JedisConnectionFactory jedisConnFactory = JedisConnectionFactoryExtension
				.getNewConnectionFactory(ValkeyStanalone.class);

		jedisConnFactory.afterPropertiesSet();

		ValkeyTemplate<String, String> stringTemplate = new StringValkeyTemplate(jedisConnFactory);
		ValkeyTemplate<String, Person> personTemplate = new ValkeyTemplate<>();
		personTemplate.setConnectionFactory(jedisConnFactory);
		personTemplate.afterPropertiesSet();
		ValkeyTemplate<byte[], byte[]> rawTemplate = new ValkeyTemplate<>();
		rawTemplate.setEnableDefaultSerializer(false);
		rawTemplate.setConnectionFactory(jedisConnFactory);
		rawTemplate.afterPropertiesSet();

		// add Lettuce
		LettuceConnectionFactory lettuceConnFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		ValkeyTemplate<String, String> stringTemplateLtc = new StringValkeyTemplate(lettuceConnFactory);
		ValkeyTemplate<String, Person> personTemplateLtc = new ValkeyTemplate<>();
		personTemplateLtc.setConnectionFactory(lettuceConnFactory);
		personTemplateLtc.afterPropertiesSet();
		ValkeyTemplate<byte[], byte[]> rawTemplateLtc = new ValkeyTemplate<>();
		rawTemplateLtc.setEnableDefaultSerializer(false);
		rawTemplateLtc.setConnectionFactory(lettuceConnFactory);
		rawTemplateLtc.afterPropertiesSet();

		// add Valkey Glide
		ValkeyGlideConnectionFactory glideConnFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		ValkeyTemplate<String, String> stringTemplateGlide = new StringValkeyTemplate(glideConnFactory);
		ValkeyTemplate<String, Person> personTemplateGlide = new ValkeyTemplate<>();
		personTemplateGlide.setConnectionFactory(glideConnFactory);
		personTemplateGlide.afterPropertiesSet();
		ValkeyTemplate<byte[], byte[]> rawTemplateGlide = new ValkeyTemplate<>();
		rawTemplateGlide.setEnableDefaultSerializer(false);
		rawTemplateGlide.setConnectionFactory(glideConnFactory);
		rawTemplateGlide.afterPropertiesSet();

		Collection<Object[]> parameters = new ArrayList<>();
		parameters.add(new Object[] { stringFactory, stringTemplate });
		parameters.add(new Object[] { personFactory, personTemplate });
		parameters.add(new Object[] { stringFactory, stringTemplateLtc });
		parameters.add(new Object[] { personFactory, personTemplateLtc });
		parameters.add(new Object[] { stringFactory, stringTemplateGlide });
		parameters.add(new Object[] { personFactory, personTemplateGlide });

		if (clusterAvailable()) {

			// add Jedis
			JedisConnectionFactory jedisClusterFactory = JedisConnectionFactoryExtension
					.getNewConnectionFactory(ValkeyCluster.class);

			ValkeyTemplate<String, String> jedisClusterStringTemplate =
					new StringValkeyTemplate(jedisClusterFactory);

			// add Lettuce
			LettuceConnectionFactory lettuceClusterFactory = LettuceConnectionFactoryExtension
					.getConnectionFactory(ValkeyCluster.class);

			ValkeyTemplate<String, String> lettuceClusterStringTemplate =
					new StringValkeyTemplate(lettuceClusterFactory);

			// Add Valkey-GLIDE
			ValkeyGlideConnectionFactory glideClusterFactory =
					ValkeyGlideConnectionFactoryExtension.getConnectionFactory(ValkeyCluster.class);

			ValkeyTemplate<String, String> glideClusterStringTemplate =
					new StringValkeyTemplate(glideClusterFactory);

			parameters.add(new Object[] { stringFactory, jedisClusterStringTemplate });
			parameters.add(new Object[] { stringFactory, lettuceClusterStringTemplate });
			parameters.add(new Object[] { stringFactory, glideClusterStringTemplate });
		}

		return parameters;
	}

	private static boolean clusterAvailable() {
		return ValkeyDetector.isClusterAvailable();
	}
}
