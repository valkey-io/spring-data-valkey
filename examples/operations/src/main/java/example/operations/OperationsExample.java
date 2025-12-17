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
package example.operations;

import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.geo.Point;

import java.util.Arrays;

/**
 * Example demonstrating various Valkey data structure operations.
 */
@Configuration
public class OperationsExample {

	@Bean
	public ValkeyConnectionFactory valkeyConnectionFactory() {
		return new ValkeyGlideConnectionFactory();
	}

	@Bean
	public ValkeyTemplate<String, String> valkeyTemplate(ValkeyConnectionFactory factory) {
		ValkeyTemplate<String, String> template = new ValkeyTemplate<>();
		template.setConnectionFactory(factory);
		template.setDefaultSerializer(StringValkeySerializer.UTF_8);
		return template;
	}

	public static void main(String[] args) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OperationsExample.class);
		@SuppressWarnings("unchecked")
		ValkeyTemplate<String, String> template = context.getBean(ValkeyTemplate.class);

		// List operations
		template.opsForList().rightPush("mylist", "one");
		template.opsForList().rightPush("mylist", "two");
		template.opsForList().rightPush("mylist", "three");
		System.out.println("List: " + template.opsForList().range("mylist", 0, -1));

		// Set operations
		template.opsForSet().add("myset", "banana", "apple", "cherry");
		System.out.println("\nSet: " + template.opsForSet().members("myset"));

		// Hash operations
		template.opsForHash().put("myhash", "field1", "value1");
		template.opsForHash().put("myhash", "field2", "value2");
		System.out.println("\nHash: " + template.opsForHash().entries("myhash"));

		// Sorted Set operations (added in random order, automatically sorted by score)
		template.opsForZSet().add("leaderboard", "player2", 175.0);
		template.opsForZSet().add("leaderboard", "player1", 100.0);
		template.opsForZSet().add("leaderboard", "player3", 250.0);
		System.out.println("\nLeaderboard (sorted by score): " + template.opsForZSet().range("leaderboard", 0, -1));

		// Geo operations
		template.opsForGeo().add("locations", new Point(-122.27652, 37.805186), "San Francisco");
		template.opsForGeo().add("locations", new Point(-118.24368, 34.05223), "Los Angeles");
		System.out.println("\nLocations: " + template.opsForGeo().position("locations", "San Francisco"));

		// HyperLogLog operations
		template.opsForHyperLogLog().add("visitors", "user1", "user2", "user3");
		template.opsForHyperLogLog().add("visitors", "user2", "user4"); // user2 counted once
		Long uniqueCount = template.opsForHyperLogLog().size("visitors");
		System.out.println("\nUnique visitors (approximate): " + uniqueCount);

		// Stream operations
		template.opsForStream().add("mystream", java.util.Map.of("sensor", "temperature", "value", "23.5"));
		template.opsForStream().add("mystream", java.util.Map.of("sensor", "humidity", "value", "65"));
		System.out.println("\nStream length: " + template.opsForStream().size("mystream"));

		// Cleanup
		template.delete(Arrays.asList("mylist", "myset", "myhash", "leaderboard", "locations", "visitors", "mystream"));

		context.close();
	}
}
