/*
 * Copyright 2012-2023 the original author or authors.
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

package io.valkey.springframework.boot.autoconfigure.data.valkey;

import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.ReactiveValkeyTemplate;
import io.valkey.springframework.data.valkey.core.ReactiveStringValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Data's reactive Valkey
 * support.
 *
 * @author Mark Paluch
 * @author Stephane Nicoll
 * @since 2.0.0
 */
@AutoConfiguration(after = ValkeyAutoConfiguration.class)
@ConditionalOnClass({ ReactiveValkeyConnectionFactory.class, ReactiveValkeyTemplate.class, Flux.class })
public class ValkeyReactiveAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "reactiveValkeyTemplate")
	@ConditionalOnBean(ReactiveValkeyConnectionFactory.class)
	public ReactiveValkeyTemplate<Object, Object> reactiveValkeyTemplate(
			ReactiveValkeyConnectionFactory reactiveValkeyConnectionFactory, ResourceLoader resourceLoader) {
		ValkeySerializer<Object> javaSerializer = ValkeySerializer.java(resourceLoader.getClassLoader());
		ValkeySerializationContext<Object, Object> serializationContext = ValkeySerializationContext
			.newSerializationContext()
			.key(javaSerializer)
			.value(javaSerializer)
			.hashKey(javaSerializer)
			.hashValue(javaSerializer)
			.build();
		return new ReactiveValkeyTemplate<>(reactiveValkeyConnectionFactory, serializationContext);
	}

	@Bean
	@ConditionalOnMissingBean(name = "reactiveStringValkeyTemplate")
	@ConditionalOnBean(ReactiveValkeyConnectionFactory.class)
	public ReactiveStringValkeyTemplate reactiveStringValkeyTemplate(
			ReactiveValkeyConnectionFactory reactiveValkeyConnectionFactory) {
		return new ReactiveStringValkeyTemplate(reactiveValkeyConnectionFactory);
	}

}
