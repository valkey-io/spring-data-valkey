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
package example.transactions;

import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.SessionCallback;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;

import java.util.Arrays;
import java.util.List;

/**
 * Example demonstrating Valkey transactions with MULTI/EXEC.
 */
public class TransactionsExample {

	public static void main(String[] args) {

		ValkeyGlideConnectionFactory connectionFactory = new ValkeyGlideConnectionFactory();
		connectionFactory.afterPropertiesSet();

		try {
			ValkeyTemplate<String, String> template = new ValkeyTemplate<>();
			template.setConnectionFactory(connectionFactory);
			template.setDefaultSerializer(StringValkeySerializer.UTF_8);
			template.afterPropertiesSet();

			// Basic transaction using SessionCallback
			List<Object> results = template.execute(new SessionCallback<List<Object>>() {
				@SuppressWarnings({"unchecked", "rawtypes"})
				@Override
				public List<Object> execute(ValkeyOperations operations) {
					operations.multi();
					operations.opsForValue().set("key1", "value1");
					operations.opsForValue().set("key2", "value2");
					return operations.exec();
				}
			});
			System.out.println("Basic transaction results: " + results);

			// Transaction with WATCH (optimistic locking)
			template.opsForValue().set("counter", "0");

			// Retry loop for optimistic locking
			boolean success = false;
			int attempts = 0;
			while (!success && attempts < 3) {
				attempts++;
				List<Object> watchResults = template.execute(new SessionCallback<List<Object>>() {
					@SuppressWarnings({"unchecked", "rawtypes"})
					@Override
					public List<Object> execute(ValkeyOperations operations) {
						// Watch key BEFORE reading
						operations.watch("counter");

						// Read current value
						String value = (String) operations.opsForValue().get("counter");
						int counter = Integer.parseInt(value);

						// Start transaction
						operations.multi();
						operations.opsForValue().set("counter", String.valueOf(counter + 1));
						return operations.exec();
					}
				});

				if (watchResults != null && !watchResults.isEmpty()) {
					success = true;
					System.out.println("\nTransaction succeeded on attempt " + attempts + ". Counter: " + template.opsForValue().get("counter"));
				} else {
					System.out.println("\nAttempt " + attempts + " failed (key was modified), retrying...");
				}
			}
			if (!success) {
				System.out.println("Transaction failed after " + attempts + " attempts");
			}

			// Cleanup
			template.delete(Arrays.asList("key1", "key2", "counter"));
		} finally {
			connectionFactory.destroy();
		}
	}
}
