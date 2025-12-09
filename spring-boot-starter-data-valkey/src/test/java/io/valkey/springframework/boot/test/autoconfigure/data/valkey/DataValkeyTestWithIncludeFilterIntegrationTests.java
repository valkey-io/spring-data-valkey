/*
 * Copyright 2012-2024 the original author or authors.
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

package io.valkey.springframework.boot.test.autoconfigure.data.valkey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.stereotype.Service;

import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DataValkeyTest @DataValkeyTest} with include filters.
 *
 * @author Jayaram Pradhan
 */
@DataValkeyTest(includeFilters = @Filter(Service.class))
class DataValkeyTestWithIncludeFilterIntegrationTests {

	@Autowired
	private ExampleRepository exampleRepository;

	@Autowired
	private ExampleService service;

	@Test
	void contextLoads() {
		PersonHash personHash = new PersonHash();
		personHash.setDescription("Look, new @DataValkeyTest with include filters!");
		PersonHash savedEntity = this.exampleRepository.save(personHash);
		assertThat(savedEntity.getId()).isNotNull();
		assertThat(savedEntity.getDescription()).isEqualTo("Look, new @DataValkeyTest with include filters!");
		assertThat(this.service.hasRecord(savedEntity)).isTrue();
		this.exampleRepository.deleteAll();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		StringValkeyTemplate stringValkeyTemplate(ValkeyConnectionFactory connectionFactory) {
			return new StringValkeyTemplate(connectionFactory);
		}

	}

}
