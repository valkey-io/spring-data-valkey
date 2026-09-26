/*
 * Copyright 2025-present the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.valkeyglide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;

import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.zset.Tuple;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;

/**
 * Unit tests for GLIDE scan cursors.
 *
 * @author Mantas Aleknavičius
 */
class ValkeyGlideScanCursorUnitTests {

	private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);

	@Test // GH-108
	void sScanContinuesAfterEmptyIntermediateBatch() throws Exception {

		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class))).thenReturn(
				new Object[] { GlideString.of("17"), new Object[] { GlideString.of("member:1") } },
				new Object[] { GlideString.of("29"), new Object[] {} },
				new Object[] { GlideString.of("0"), new Object[] { GlideString.of("member:2") } });
		ValkeyGlideConnection connection = new ValkeyGlideConnection(client, null);
		ValkeyGlideSetCommands commands = new ValkeyGlideSetCommands(connection);

		try (Cursor<byte[]> cursor = commands.sScan("set".getBytes(), scanOptions())) {
			assertThat(cursor.next()).isEqualTo("member:1".getBytes());
			assertThat(cursor.hasNext()).isTrue();
			assertThat(cursor.next()).isEqualTo("member:2".getBytes());
			assertThat(cursor.hasNext()).isFalse();
		}

		verify(client, times(3)).customCommand(any(GlideString[].class));
	}

	@Test // GH-108
	void hScanContinuesAfterEmptyIntermediateBatch() throws Exception {

		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class))).thenReturn(
				new Object[] { GlideString.of("17"),
						new Object[] { GlideString.of("field:1"), GlideString.of("value:1") } },
				new Object[] { GlideString.of("29"), new Object[] {} },
				new Object[] { GlideString.of("0"),
						new Object[] { GlideString.of("field:2"), GlideString.of("value:2") } });
		ValkeyGlideConnection connection = new ValkeyGlideConnection(client, null);
		ValkeyGlideHashCommands commands = new ValkeyGlideHashCommands(connection);

		try (Cursor<Map.Entry<byte[], byte[]>> cursor = commands.hScan("hash".getBytes(), scanOptions())) {
			Map.Entry<byte[], byte[]> first = cursor.next();
			assertThat(first.getKey()).isEqualTo("field:1".getBytes());
			assertThat(first.getValue()).isEqualTo("value:1".getBytes());

			assertThat(cursor.hasNext()).isTrue();
			Map.Entry<byte[], byte[]> second = cursor.next();
			assertThat(second.getKey()).isEqualTo("field:2".getBytes());
			assertThat(second.getValue()).isEqualTo("value:2".getBytes());
			assertThat(cursor.hasNext()).isFalse();
		}

		verify(client, times(3)).customCommand(any(GlideString[].class));
	}

	@Test // GH-108
	void zScanContinuesAfterEmptyIntermediateBatch() throws Exception {

		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class))).thenReturn(
				new Object[] { GlideString.of("17"), new Object[] { GlideString.of("member:1"), 1.0 } },
				new Object[] { GlideString.of("29"), new Object[] {} },
				new Object[] { GlideString.of("0"), new Object[] { GlideString.of("member:2"), 2.0 } });
		ValkeyGlideConnection connection = new ValkeyGlideConnection(client, null);
		ValkeyGlideZSetCommands commands = new ValkeyGlideZSetCommands(connection);

		try (Cursor<Tuple> cursor = commands.zScan("zset".getBytes(), scanOptions())) {
			Tuple first = cursor.next();
			assertThat(first.getValue()).isEqualTo("member:1".getBytes());
			assertThat(first.getScore()).isEqualTo(1.0);

			assertThat(cursor.hasNext()).isTrue();
			Tuple second = cursor.next();
			assertThat(second.getValue()).isEqualTo("member:2".getBytes());
			assertThat(second.getScore()).isEqualTo(2.0);
			assertThat(cursor.hasNext()).isFalse();
		}

		verify(client, times(3)).customCommand(any(GlideString[].class));
	}

	@Test // GH-108
	void nodeScopedScanContinuesAfterEmptyIntermediateBatch() throws Exception {

		ClusterGlideClientAdapter client = mock(ClusterGlideClientAdapter.class);
		Route route = new ByAddressRoute("localhost", 6379);
		when(client.hasOneShotRouteForNextCommand()).thenReturn(true);
		when(client.consumeOneShotRoute()).thenReturn(route);
		when(client.customCommand(any(GlideString[].class))).thenReturn(
				new Object[] { GlideString.of("17"), new Object[] { GlideString.of("key:1") } },
				new Object[] { GlideString.of("29"), new Object[] {} },
				new Object[] { GlideString.of("0"), new Object[] { GlideString.of("key:2") } });
		ValkeyGlideClusterConnection connection = new ValkeyGlideClusterConnection(client);
		ValkeyGlideClusterKeyCommands commands = new ValkeyGlideClusterKeyCommands(connection);

		try (Cursor<byte[]> cursor = commands.scan(scanOptions())) {
			assertThat(cursor.next()).isEqualTo("key:1".getBytes());
			assertThat(cursor.hasNext()).isTrue();
			assertThat(cursor.next()).isEqualTo("key:2".getBytes());
			assertThat(cursor.hasNext()).isFalse();
		}

		verify(client, times(3)).customCommand(any(GlideString[].class));
		verify(client, times(3)).setOneShotRouteForNextCommand(route);
	}

	// A malformed reply (null, or fewer than 2 elements) carries no cursor to advance.
	// Since hasNext() loops while (!finished), the cursor must set finished on such a
	// reply rather than spin forever waiting for progress. assertTimeoutPreemptively
	// turns a regression (the pre-fix hang) into a fast failure instead of a stuck suite.

	@Test // GH-111
	void keyScanTerminatesOnMalformedReply() {

		assertTimeoutPreemptively(TERMINATION_TIMEOUT, () -> {
			assertKeyScanTerminates((Object[]) null);
			assertKeyScanTerminates(new Object[] { GlideString.of("17") });
		});
	}

	@Test // GH-111
	void sScanTerminatesOnMalformedReply() {

		assertTimeoutPreemptively(TERMINATION_TIMEOUT, () -> {
			assertSScanTerminates((Object[]) null);
			assertSScanTerminates(new Object[] { GlideString.of("17") });
		});
	}

	@Test // GH-111
	void hScanTerminatesOnMalformedReply() {

		assertTimeoutPreemptively(TERMINATION_TIMEOUT, () -> {
			assertHScanTerminates((Object[]) null);
			assertHScanTerminates(new Object[] { GlideString.of("17") });
		});
	}

	@Test // GH-111
	void zScanTerminatesOnMalformedReply() {

		assertTimeoutPreemptively(TERMINATION_TIMEOUT, () -> {
			assertZScanTerminates((Object[]) null);
			assertZScanTerminates(new Object[] { GlideString.of("17") });
		});
	}

	@Test // GH-111
	void closeStopsSetScanPaging() throws Exception {

		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class)))
			.thenReturn(new Object[] { GlideString.of("17"), new Object[] { GlideString.of("member:1") } });
		ValkeyGlideSetCommands commands = new ValkeyGlideSetCommands(new ValkeyGlideConnection(client, null));

		Cursor<byte[]> cursor = commands.sScan("set".getBytes(), scanOptions());
		assertThat(cursor.next()).isEqualTo("member:1".getBytes());

		cursor.close();

		// close() must stop hasNext() paging, even with a member still buffered
		assertThat(cursor.hasNext()).isFalse();
		verify(client, times(1)).customCommand(any(GlideString[].class));
		// close() must not rewind the reported position
		assertThat(cursor.getPosition()).isEqualTo(1);
	}

	@Test // GH-111
	void closeStopsHashScanPaging() throws Exception {

		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class))).thenReturn(new Object[] { GlideString.of("17"),
				new Object[] { GlideString.of("field:1"), GlideString.of("value:1") } });
		ValkeyGlideHashCommands commands = new ValkeyGlideHashCommands(new ValkeyGlideConnection(client, null));

		Cursor<Map.Entry<byte[], byte[]>> cursor = commands.hScan("hash".getBytes(), scanOptions());
		cursor.next();

		cursor.close();

		// close() must stop hasNext() paging, even with an entry still buffered
		assertThat(cursor.hasNext()).isFalse();
		verify(client, times(1)).customCommand(any(GlideString[].class));
		// close() must not rewind the reported position
		assertThat(cursor.getPosition()).isEqualTo(1);
	}

	private static void assertKeyScanTerminates(Object[] reply) throws Exception {
		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class))).thenReturn(reply);
		ValkeyGlideKeyCommands commands = new ValkeyGlideKeyCommands(new ValkeyGlideConnection(client, null));
		try (Cursor<byte[]> cursor = commands.scan(scanOptions())) {
			assertThat(cursor.hasNext()).isFalse();
		}
	}

	private static void assertSScanTerminates(Object[] reply) throws Exception {
		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class))).thenReturn(reply);
		ValkeyGlideSetCommands commands = new ValkeyGlideSetCommands(new ValkeyGlideConnection(client, null));
		try (Cursor<byte[]> cursor = commands.sScan("set".getBytes(), scanOptions())) {
			assertThat(cursor.hasNext()).isFalse();
		}
	}

	private static void assertHScanTerminates(Object[] reply) throws Exception {
		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class))).thenReturn(reply);
		ValkeyGlideHashCommands commands = new ValkeyGlideHashCommands(new ValkeyGlideConnection(client, null));
		try (Cursor<Map.Entry<byte[], byte[]>> cursor = commands.hScan("hash".getBytes(), scanOptions())) {
			assertThat(cursor.hasNext()).isFalse();
		}
	}

	private static void assertZScanTerminates(Object[] reply) throws Exception {
		UnifiedGlideClient client = mock(UnifiedGlideClient.class);
		when(client.customCommand(any(GlideString[].class))).thenReturn(reply);
		ValkeyGlideZSetCommands commands = new ValkeyGlideZSetCommands(new ValkeyGlideConnection(client, null));
		try (Cursor<Tuple> cursor = commands.zScan("zset".getBytes(), scanOptions())) {
			assertThat(cursor.hasNext()).isFalse();
		}
	}

	private static ScanOptions scanOptions() {
		return ScanOptions.scanOptions().match("*").count(1).build();
	}

}
