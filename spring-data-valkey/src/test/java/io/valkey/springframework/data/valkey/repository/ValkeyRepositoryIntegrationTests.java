/*
 * Copyright 2015-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.convert.ConfigurableTypeInformationMapper;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.convert.DefaultValkeyTypeMapper;
import io.valkey.springframework.data.valkey.core.convert.MappingValkeyConverter;
import io.valkey.springframework.data.valkey.core.convert.ValkeyCustomConversions;
import io.valkey.springframework.data.valkey.core.convert.ValkeyTypeMapper;
import io.valkey.springframework.data.valkey.core.convert.ReferenceResolver;
import io.valkey.springframework.data.valkey.core.mapping.ValkeyMappingContext;
import io.valkey.springframework.data.valkey.repository.configuration.EnableValkeyRepositories;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
public class ValkeyRepositoryIntegrationTests extends ValkeyRepositoryIntegrationTestBase {

	@Configuration
	@EnableValkeyRepositories(considerNestedRepositories = true, indexConfiguration = MyIndexConfiguration.class,
			keyspaceConfiguration = MyKeyspaceConfiguration.class,
			includeFilters = { @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
					classes = { PersonRepository.class, CityRepository.class, ImmutableObjectRepository.class, UserRepository.class }) })
	static class Config {

		@Bean
		ValkeyConnectionFactory connectionFactory() {
			return new JedisConnectionFactory();
		}

		@Bean
		ValkeyTemplate<?, ?> valkeyTemplate(ValkeyConnectionFactory connectionFactory) {

			ValkeyTemplate<String, String> template = new ValkeyTemplate<>();

			template.setDefaultSerializer(StringValkeySerializer.UTF_8);
			template.setConnectionFactory(connectionFactory);

			return template;
		}

		@Bean
		public MappingValkeyConverter valkeyConverter(ValkeyMappingContext mappingContext,
				ValkeyCustomConversions customConversions, ReferenceResolver referenceResolver) {

			MappingValkeyConverter mappingValkeyConverter = new MappingValkeyConverter(mappingContext, null, referenceResolver,
					customTypeMapper());

			mappingValkeyConverter.setCustomConversions(customConversions);

			return mappingValkeyConverter;
		}

		private ValkeyTypeMapper customTypeMapper() {

			Map<Class<?>, String> mapping = new HashMap<>();

			mapping.put(Person.class, "person");
			mapping.put(City.class, "city");

			ConfigurableTypeInformationMapper mapper = new ConfigurableTypeInformationMapper(mapping);

			return new DefaultValkeyTypeMapper(DefaultValkeyTypeMapper.DEFAULT_TYPE_KEY, Collections.singletonList(mapper));
		}
	}

	@Autowired ValkeyOperations<String, String> operations;

	@Test // DATAREDIS-543
	public void shouldConsiderCustomTypeMapper() {

		Person rand = new Person();

		rand.id = "rand";
		rand.firstname = "rand";
		rand.lastname = "al'thor";

		repo.save(rand);

		Map<String, String> entries = operations.<String, String> opsForHash().entries("persons:rand");

		assertThat(entries.get("_class")).isEqualTo("person");
	}
}
