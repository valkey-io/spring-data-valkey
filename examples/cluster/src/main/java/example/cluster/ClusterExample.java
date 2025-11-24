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
package example.cluster;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;

import java.util.Arrays;

/**
 * Example demonstrating Spring Data Valkey with a Valkey cluster.
 */
public class ClusterExample {

	public static void main(String[] args) {

		// Configure cluster nodes (matches Makefile cluster configuration)
		ValkeyClusterConfiguration clusterConfig = new ValkeyClusterConfiguration();
		clusterConfig.clusterNode("127.0.0.1", 7379);
		clusterConfig.clusterNode("127.0.0.1", 7380);
		clusterConfig.clusterNode("127.0.0.1", 7381);
		clusterConfig.clusterNode("127.0.0.1", 7382);

		ValkeyGlideConnectionFactory connectionFactory = new ValkeyGlideConnectionFactory(
				clusterConfig, ValkeyGlideClientConfiguration.defaultConfiguration());
		connectionFactory.afterPropertiesSet();
		System.out.println("Connected to cluster with " + clusterConfig.getClusterNodes().size() + " nodes");

		try {
			StringValkeyTemplate template = new StringValkeyTemplate(connectionFactory);

			// Use hash tags to keep keys on same cluster node
			String key1 = "{user:123}:profile";
			String key2 = "{user:123}:settings";
			String key3 = "{user:123}:session";

			// String operations
			template.opsForValue().set(key1, "John Doe");
			template.opsForValue().set(key2, "theme=dark,lang=en");
			template.opsForValue().set(key3, "active");

			System.out.println("\nStored user data:");
			System.out.println("Profile: " + template.opsForValue().get(key1));
			System.out.println("Settings: " + template.opsForValue().get(key2));
			System.out.println("Session: " + template.opsForValue().get(key3));

			// Hash operations
			String hashKey = "{user:123}:metadata";
			template.opsForHash().put(hashKey, "created", "2025-01-01");
			template.opsForHash().put(hashKey, "lastLogin", "2025-01-15");
			template.opsForHash().put(hashKey, "loginCount", "42");

			System.out.println("\nUser metadata:");
			template.opsForHash().entries(hashKey).forEach((field, value) ->
				System.out.println(field + ": " + value));

			// Cleanup
			template.delete(Arrays.asList(key1, key2, key3, hashKey));
		} finally {
			connectionFactory.destroy();
		}
	}
}
