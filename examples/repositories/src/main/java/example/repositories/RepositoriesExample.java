/*
 * Copyright 2025 the original author or authors.
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
package example.repositories;

import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.repository.configuration.EnableValkeyRepositories;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Example demonstrating Spring Data Valkey repositories.
 */
public class RepositoriesExample {

	public static void main(String[] args) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
		PersonRepository repository = context.getBean(PersonRepository.class);

		repository.save(new Person("1", "John", "Doe", 30));
		repository.save(new Person("2", "Jane", "Doe", 28));
		repository.save(new Person("3", "Bob", "Smith", 35));

		System.out.println("All persons:");
		repository.findAll().forEach(System.out::println);

		System.out.println("\nPersons with lastname 'Doe':");
		repository.findByLastname("Doe").forEach(System.out::println);

		// Cleanup
		repository.deleteAll();

		context.close();
	}

	@Configuration
	@EnableValkeyRepositories
	static class Config {

		@Bean
		public ValkeyGlideConnectionFactory connectionFactory() {
			return new ValkeyGlideConnectionFactory();
		}

		@Bean
		public ValkeyTemplate<?, ?> valkeyTemplate(ValkeyGlideConnectionFactory connectionFactory) {
			ValkeyTemplate<byte[], byte[]> template = new ValkeyTemplate<>();
			template.setConnectionFactory(connectionFactory);
			return template;
		}
	}
}
