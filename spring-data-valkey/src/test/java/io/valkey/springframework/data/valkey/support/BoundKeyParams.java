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
package io.valkey.springframework.data.valkey.support;

import java.util.Arrays;
import java.util.Collection;

import io.valkey.springframework.data.valkey.StringObjectFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.extension.LettuceConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.support.atomic.ValkeyAtomicInteger;
import io.valkey.springframework.data.valkey.support.atomic.ValkeyAtomicLong;
import io.valkey.springframework.data.valkey.support.collections.DefaultValkeyMap;
import io.valkey.springframework.data.valkey.support.collections.DefaultValkeySet;
import io.valkey.springframework.data.valkey.support.collections.ValkeyList;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Mark Paluch
 */
public class BoundKeyParams {

	public static Collection<Object[]> testParams() {
		// Jedis
		JedisConnectionFactory jedisConnFactory = JedisConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		StringValkeyTemplate templateJS = new StringValkeyTemplate(jedisConnFactory);
		DefaultValkeyMap mapJS = new DefaultValkeyMap("bound:key:map", templateJS);
		DefaultValkeySet setJS = new DefaultValkeySet("bound:key:set", templateJS);
		ValkeyList list = ValkeyList.create("bound:key:list", templateJS);

		// Lettuce
		LettuceConnectionFactory lettuceConnFactory = LettuceConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		StringValkeyTemplate templateLT = new StringValkeyTemplate(lettuceConnFactory);
		DefaultValkeyMap mapLT = new DefaultValkeyMap("bound:key:mapLT", templateLT);
		DefaultValkeySet setLT = new DefaultValkeySet("bound:key:setLT", templateLT);
		ValkeyList listLT = ValkeyList.create("bound:key:listLT", templateLT);

		// ValkeyGlide
		ValkeyGlideConnectionFactory vgConnFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		StringValkeyTemplate templateVG = new StringValkeyTemplate(vgConnFactory);
		DefaultValkeyMap mapVG = new DefaultValkeyMap("bound:key:mapVG", templateLT);
		DefaultValkeySet setVG = new DefaultValkeySet("bound:key:setVG", templateLT);
		ValkeyList listVG = ValkeyList.create("bound:key:listVG", templateLT);		

		StringObjectFactory sof = new StringObjectFactory();

		return Arrays
				.asList(new Object[][] { { new ValkeyAtomicInteger("bound:key:int", jedisConnFactory), sof, templateJS },
						{ new ValkeyAtomicLong("bound:key:long", jedisConnFactory), sof, templateJS }, { list, sof, templateJS },
						{ setJS, sof, templateJS }, { mapJS, sof, templateJS },
						{ new ValkeyAtomicInteger("bound:key:intLT", lettuceConnFactory), sof, templateLT },
						{ new ValkeyAtomicLong("bound:key:longLT", lettuceConnFactory), sof, templateLT },
						{ listLT, sof, templateLT }, { setLT, sof, templateLT }, { mapLT, sof, templateLT },
						{ new ValkeyAtomicInteger("bound:key:intVG", vgConnFactory), sof, templateVG },
						{ new ValkeyAtomicLong("bound:key:longVG", vgConnFactory), sof, templateVG },
						{ listVG, sof, templateVG }, { setVG, sof, templateVG }, { mapVG, sof, templateVG } });
	}
}
