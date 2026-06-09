/*
 * Copyright 2026 the original author or authors.
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
package example.clusterpipeline;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyCallback;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Example demonstrating pipelining in Valkey Cluster mode.
 *
 * <p>Cluster pipelining supports both same-slot and cross-slot keys.
 * The Valkey GLIDE driver routes each command in the batch to the
 * correct cluster node transparently.
 */
public class ClusterPipelineExample {

	public static void main(String[] args) {

		ValkeyClusterConfiguration clusterConfig = new ValkeyClusterConfiguration();
		clusterConfig.setClusterNodes(Arrays.asList(
				new ValkeyNode("127.0.0.1", 7379),
				new ValkeyNode("127.0.0.1", 7380),
				new ValkeyNode("127.0.0.1", 7381)
		));

		ValkeyGlideConnectionFactory connectionFactory = new ValkeyGlideConnectionFactory(
				clusterConfig, ValkeyGlideClientConfiguration.defaultConfiguration());
		connectionFactory.afterPropertiesSet();

		try {
			ValkeyTemplate<String, String> template = new ValkeyTemplate<>();
			template.setConnectionFactory(connectionFactory);
			template.setDefaultSerializer(StringValkeySerializer.UTF_8);
			template.afterPropertiesSet();

			System.out.println("=== Cluster Pipeline Example ===\n");

			// 1. Same-slot pipeline using hash tags
			sameSlotPipeline(template);

			// 2. Cross-slot pipeline (keys on different nodes)
			crossSlotPipeline(template);

			// 3. Performance comparison
			performanceComparison(template);

		} finally {
			connectionFactory.destroy();
		}
	}

	private static void sameSlotPipeline(ValkeyTemplate<String, String> template) {
		System.out.println("--- Same-slot pipeline (hash tags) ---");

		List<Object> results = template.executePipelined((ValkeyCallback<Object>) connection -> {
			byte[] key1 = "{user:1}:name".getBytes();
			byte[] key2 = "{user:1}:email".getBytes();
			byte[] key3 = "{user:1}:role".getBytes();

			connection.stringCommands().set(key1, "Alice".getBytes());
			connection.stringCommands().set(key2, "alice@example.com".getBytes());
			connection.stringCommands().set(key3, "admin".getBytes());
			connection.stringCommands().get(key1);
			connection.stringCommands().get(key2);
			connection.stringCommands().get(key3);
			return null;
		});

		System.out.println("SET results: " + results.subList(0, 3));
		System.out.println("GET results: " + results.subList(3, 6));

		template.delete(Arrays.asList("{user:1}:name", "{user:1}:email", "{user:1}:role"));
		System.out.println();
	}

	private static void crossSlotPipeline(ValkeyTemplate<String, String> template) {
		System.out.println("--- Cross-slot pipeline (different nodes) ---");

		List<Object> results = template.executePipelined((ValkeyCallback<Object>) connection -> {
			// These keys will hash to different slots and be routed to different nodes
			connection.stringCommands().set("orders:1001".getBytes(), "pending".getBytes());
			connection.stringCommands().set("inventory:sku-42".getBytes(), "150".getBytes());
			connection.stringCommands().set("sessions:abc123".getBytes(), "active".getBytes());
			connection.hashCommands().hSet("metrics:daily".getBytes(), "requests".getBytes(), "5000".getBytes());

			connection.stringCommands().get("orders:1001".getBytes());
			connection.stringCommands().get("inventory:sku-42".getBytes());
			connection.stringCommands().get("sessions:abc123".getBytes());
			connection.hashCommands().hGet("metrics:daily".getBytes(), "requests".getBytes());
			return null;
		});

		System.out.println("Pipeline executed across multiple cluster nodes");
		System.out.println("Results: " + results);

		template.delete(Arrays.asList("orders:1001", "inventory:sku-42", "sessions:abc123", "metrics:daily"));
		System.out.println();
	}

	private static void performanceComparison(ValkeyTemplate<String, String> template) {
		System.out.println("--- Performance: pipeline vs sequential ---");
		int count = 500;

		// Sequential
		long start = System.currentTimeMillis();
		for (int i = 0; i < count; i++) {
			template.opsForValue().set("bench:" + i, "val:" + i);
		}
		long sequentialTime = System.currentTimeMillis() - start;
		System.out.println("Sequential " + count + " SETs: " + sequentialTime + "ms");

		// Pipelined
		start = System.currentTimeMillis();
		template.executePipelined((ValkeyCallback<Object>) connection -> {
			for (int i = 0; i < count; i++) {
				connection.stringCommands().set(("bench:" + i).getBytes(), ("val:" + i).getBytes());
			}
			return null;
		});
		long pipelineTime = System.currentTimeMillis() - start;
		System.out.println("Pipelined  " + count + " SETs: " + pipelineTime + "ms");

		if (sequentialTime > 0) {
			System.out.println("Speedup: " + String.format("%.1fx", (double) sequentialTime / pipelineTime));
		}

		// Cleanup
		List<String> keys = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			keys.add("bench:" + i);
		}
		template.delete(keys);
	}
}
