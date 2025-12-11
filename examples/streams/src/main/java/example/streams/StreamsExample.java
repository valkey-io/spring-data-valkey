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
package example.streams;

import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.stream.*;

import java.util.List;
import java.util.Map;

/**
 * Example demonstrating Valkey Streams for event sourcing and message queues.
 */
public class StreamsExample {

	public static void main(String[] args) {

		ValkeyGlideConnectionFactory connectionFactory = new ValkeyGlideConnectionFactory();
		connectionFactory.afterPropertiesSet();

		try {
			StringValkeyTemplate template = new StringValkeyTemplate(connectionFactory);

			String streamKey = "events";

			// Add entries to stream (XADD)
			System.out.println("Adding events to stream:");
			RecordId id1 = template.opsForStream().add(streamKey, Map.of(
				"event", "user.login",
				"userId", "123",
				"timestamp", String.valueOf(System.currentTimeMillis())
			));
			System.out.println("Event 1 ID: " + id1);

			RecordId id2 = template.opsForStream().add(streamKey, Map.of(
				"event", "user.purchase",
				"userId", "123",
				"amount", "99.99"
			));
			System.out.println("Event 2 ID: " + id2);

			RecordId id3 = template.opsForStream().add(streamKey, Map.of(
				"event", "user.logout",
				"userId", "123"
			));
			System.out.println("Event 3 ID: " + id3);

			// Get stream length (XLEN)
			Long length = template.opsForStream().size(streamKey);
			System.out.println("\nStream length: " + length);

			// Read all entries (XRANGE)
			System.out.println("\nReading all events:");
			List<MapRecord<String, Object, Object>> records = template.opsForStream()
				.range(streamKey, Range.unbounded());
			records.forEach(record -> {
				System.out.println("  ID: " + record.getId() + " -> " + record.getValue());
			});

			// Read specific range (XRANGE with bounds)
			System.out.println("\nReading events from ID " + id1 + " to " + id2 + ":");
			records = template.opsForStream()
				.range(streamKey, Range.closed(id1.getValue(), id2.getValue()));
			records.forEach(record -> {
				System.out.println("  " + record.getValue().get("event"));
			});

			// Read new entries (XREAD) - non-blocking
			System.out.println("\nReading new events after " + id2 + ":");
			@SuppressWarnings("unchecked")
			List<MapRecord<String, Object, Object>> newRecords = template.opsForStream()
				.read(StreamOffset.create(streamKey, ReadOffset.from(id2.getValue())));
			newRecords.forEach(record -> {
				System.out.println("  " + record.getValue().get("event"));
			});

			// Consumer groups example
			System.out.println("\nCreating consumer group:");
			String groupName = "event-processors";
			try {
				template.opsForStream().createGroup(streamKey, groupName);
				System.out.println("Consumer group '" + groupName + "' created");
			} catch (Exception e) {
				System.out.println("Consumer group already exists or error: " + e.getMessage());
			}

			// Read from consumer group (XREADGROUP)
			System.out.println("\nReading as consumer 'worker-1' from group:");
			@SuppressWarnings("unchecked")
			List<MapRecord<String, Object, Object>> groupRecords = template.opsForStream()
				.read(Consumer.from(groupName, "worker-1"),
					StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
			
			if (groupRecords.isEmpty()) {
				System.out.println("  No new messages (all already consumed)");
			} else {
				groupRecords.forEach(record -> {
					System.out.println("  Processing: " + record.getValue().get("event"));
					// Acknowledge message (XACK)
					template.opsForStream().acknowledge(streamKey, groupName, record.getId());
				});
			}

			// Trim stream to keep only recent entries (XTRIM)
			System.out.println();
			Long trimmed = template.opsForStream().trim(streamKey, 100);
			System.out.println("Trimmed " + trimmed + " old entries (keeping max 100)");

			// Cleanup
			template.delete(streamKey);
		} finally {
			connectionFactory.destroy();
		}
	}
}
