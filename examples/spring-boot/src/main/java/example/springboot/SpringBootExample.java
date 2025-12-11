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
package example.springboot;

import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.repository.configuration.EnableValkeyRepositories;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Spring Boot example demonstrating Valkey Starter with both template and repository usage.
 */
@SpringBootApplication
@EnableValkeyRepositories
public class SpringBootExample implements CommandLineRunner {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StringValkeyTemplate valkeyTemplate;

	public static void main(String[] args) {
		SpringApplication.run(SpringBootExample.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		templateExample();
		repositoryExample();

		// Cleanup
		valkeyTemplate.delete(Arrays.asList("message", "counter", "items"));
		userRepository.deleteAll();
	}

	private void templateExample() {
		// Store and retrieve a simple value
		valkeyTemplate.opsForValue().set("message", "Hello Valkey!");
		String message = valkeyTemplate.opsForValue().get("message");
		System.out.println("Stored message: " + message);

		// Increment a counter
		valkeyTemplate.opsForValue().increment("counter");
		valkeyTemplate.opsForValue().increment("counter");
		String counter = valkeyTemplate.opsForValue().get("counter");
		System.out.println("\nCounter value: " + counter);

		// Work with a list
		valkeyTemplate.opsForList().rightPush("items", "apple");
		valkeyTemplate.opsForList().rightPush("items", "banana");
		valkeyTemplate.opsForList().rightPush("items", "cherry");
		List<String> items = valkeyTemplate.opsForList().range("items", 0, -1);
		System.out.println("\nItems in list: " + items);
	}

	private void repositoryExample() {
		// Create and save users
		User alice = new User("1", "Alice Johnson", "alice@example.com", 28);
		User bob = new User("2", "Bob Smith", "bob@example.com", 35);
		User charlie = new User("3", "Charlie Brown", "charlie@example.com", 22);

		userRepository.save(alice);
		userRepository.save(bob);
		userRepository.save(charlie);

		// Find all users
		System.out.println("\nAll users:");
		userRepository.findAll().forEach(System.out::println);

		// Find by ID
		Optional<User> foundUser = userRepository.findById("1");
		foundUser.ifPresent(user -> System.out.println("\nFound user by ID: " + user));

		// Find by name
		List<User> aliceUsers = userRepository.findByName("Alice Johnson");
		System.out.println("\nUsers named 'Alice Johnson': " + aliceUsers);

		// Find by email
		List<User> exampleUsers = userRepository.findByEmail("alice@example.com");
		System.out.println("\nUsers with email 'alice@example.com': " + exampleUsers);

		// Find by age
		List<User> specificAgeUsers = userRepository.findByAge(28);
		System.out.println("\nUsers aged 28: " + specificAgeUsers);

		// Count users
		long userCount = userRepository.count();
		System.out.println("\nTotal users: " + userCount);
	}
}
