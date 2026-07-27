/*
 * Copyright 2026-present the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import glide.api.models.GlideString;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.valkey.springframework.data.valkey.connection.CompareCondition;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.XDelOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.StreamDeletionPolicy;
import io.valkey.springframework.data.valkey.connection.stream.RecordId;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import org.springframework.dao.InvalidDataAccessApiUsageException;

/**
 * Wire-level unit tests for GLIDE commands that require Valkey 8.2+ or Redis 8.4+. These
 * validate the correct command serialization without needing a running server.
 *
 * @author Jeremy Parr-Pearson
 */
class ValkeyGlideNewCommandsUnitTests {

	private UnifiedGlideClient client;

	private ValkeyGlideConnection connection;

	private ValkeyGlideStreamCommands streamCommands;

	private ValkeyGlideKeyCommands keyCommands;

	private ValkeyGlideHashCommands hashCommands;

	@BeforeEach
	void setUp() throws Exception {
		client = mock(UnifiedGlideClient.class);
		connection = new ValkeyGlideConnection(client, null);
		streamCommands = new ValkeyGlideStreamCommands(connection);
		keyCommands = new ValkeyGlideKeyCommands(connection);
		hashCommands = new ValkeyGlideHashCommands(connection);
	}

	// --- XACKDEL ---

	@Test
	void xAckDelShouldIncludeIdsTokenAndCount() throws Exception {

		Object[] mockResult = new Object[] { 1L, -1L };
		when(client.customCommand(any(GlideString[].class))).thenReturn(mockResult);

		XDelOptions options = XDelOptions.deletionPolicy(StreamDeletionPolicy.DELETE_REFERENCES);
		streamCommands.xAckDel("stream".getBytes(), "group", options, RecordId.of("1-1"), RecordId.of("2-2"));

		ArgumentCaptor<GlideString[]> captor = ArgumentCaptor.forClass(GlideString[].class);
		verify(client).customCommand(captor.capture());

		GlideString[] args = captor.getValue();
		// XACKDEL key group DELREF IDS 2 1-1 2-2
		assertThat(args[0].getString()).isEqualTo("XACKDEL");
		assertThat(args[1].getString()).isEqualTo("stream");
		assertThat(args[2].getString()).isEqualTo("group");
		assertThat(args[3].getString()).isEqualTo("DELREF");
		assertThat(args[4].getString()).isEqualTo("IDS");
		assertThat(args[5].getString()).isEqualTo("2");
		assertThat(args[6].getString()).isEqualTo("1-1");
		assertThat(args[7].getString()).isEqualTo("2-2");
	}

	@Test
	void xAckDelWithKeepRefShouldSerializeCorrectly() throws Exception {

		Object[] mockResult = new Object[] { 1L };
		when(client.customCommand(any(GlideString[].class))).thenReturn(mockResult);

		XDelOptions options = XDelOptions.deletionPolicy(StreamDeletionPolicy.KEEP_REFERENCES);
		streamCommands.xAckDel("stream".getBytes(), "group", options, RecordId.of("0-1"));

		ArgumentCaptor<GlideString[]> captor = ArgumentCaptor.forClass(GlideString[].class);
		verify(client).customCommand(captor.capture());

		GlideString[] args = captor.getValue();
		// XACKDEL key group KEEPREF IDS 1 0-1
		assertThat(args[3].getString()).isEqualTo("KEEPREF");
		assertThat(args[4].getString()).isEqualTo("IDS");
		assertThat(args[5].getString()).isEqualTo("1");
		assertThat(args[6].getString()).isEqualTo("0-1");
	}

	// --- XDELEX ---

	@Test
	void xDelExShouldIncludeIdsTokenAndCount() throws Exception {

		Object[] mockResult = new Object[] { 1L, 1L, -1L };
		when(client.customCommand(any(GlideString[].class))).thenReturn(mockResult);

		XDelOptions options = XDelOptions.deletionPolicy(StreamDeletionPolicy.DELETE_REFERENCES);
		streamCommands.xDelEx("stream".getBytes(), options, RecordId.of("1-1"), RecordId.of("2-2"), RecordId.of("3-3"));

		ArgumentCaptor<GlideString[]> captor = ArgumentCaptor.forClass(GlideString[].class);
		verify(client).customCommand(captor.capture());

		GlideString[] args = captor.getValue();
		// XDELEX key DELREF IDS 3 1-1 2-2 3-3
		assertThat(args[0].getString()).isEqualTo("XDELEX");
		assertThat(args[1].getString()).isEqualTo("stream");
		assertThat(args[2].getString()).isEqualTo("DELREF");
		assertThat(args[3].getString()).isEqualTo("IDS");
		assertThat(args[4].getString()).isEqualTo("3");
		assertThat(args[5].getString()).isEqualTo("1-1");
		assertThat(args[6].getString()).isEqualTo("2-2");
		assertThat(args[7].getString()).isEqualTo("3-3");
	}

	// --- HGETEX ---

	@Test
	void hGetExWithKeepTtlShouldNotSendAnyExpirationArg() throws Exception {

		Object[] mockResult = new Object[] { GlideString.of("value1") };
		when(client.customCommand(any(GlideString[].class))).thenReturn(mockResult);

		hashCommands.hGetEx("hash".getBytes(), Expiration.keepTtl(), "field1".getBytes());

		ArgumentCaptor<GlideString[]> captor = ArgumentCaptor.forClass(GlideString[].class);
		verify(client).customCommand(captor.capture());

		GlideString[] args = captor.getValue();
		// HGETEX key FIELDS 1 field1 (no expiration token)
		assertThat(args[0].getString()).isEqualTo("HGETEX");
		assertThat(args[1].getString()).isEqualTo("hash");
		assertThat(args[2].getString()).isEqualTo("FIELDS");
		assertThat(args[3].getString()).isEqualTo("1");
		assertThat(args[4].getString()).isEqualTo("field1");
		// Verify no PERSIST, EX, PX, EXAT, PXAT tokens
		for (GlideString arg : args) {
			assertThat(arg.getString()).isNotIn("PERSIST", "EX", "PX", "EXAT", "PXAT", "KEEPTTL");
		}
	}

	@Test
	void hGetExWithPersistShouldSendPersist() throws Exception {

		Object[] mockResult = new Object[] { GlideString.of("value1") };
		when(client.customCommand(any(GlideString[].class))).thenReturn(mockResult);

		hashCommands.hGetEx("hash".getBytes(), Expiration.persistent(), "field1".getBytes());

		ArgumentCaptor<GlideString[]> captor = ArgumentCaptor.forClass(GlideString[].class);
		verify(client).customCommand(captor.capture());

		GlideString[] args = captor.getValue();
		// HGETEX key PERSIST FIELDS 1 field1
		assertThat(args[0].getString()).isEqualTo("HGETEX");
		assertThat(args[1].getString()).isEqualTo("hash");
		assertThat(args[2].getString()).isEqualTo("PERSIST");
		assertThat(args[3].getString()).isEqualTo("FIELDS");
	}

	@Test
	void hGetExWithExpirationShouldSendExToken() throws Exception {

		Object[] mockResult = new Object[] { GlideString.of("value1") };
		when(client.customCommand(any(GlideString[].class))).thenReturn(mockResult);

		hashCommands.hGetEx("hash".getBytes(), Expiration.seconds(60), "field1".getBytes());

		ArgumentCaptor<GlideString[]> captor = ArgumentCaptor.forClass(GlideString[].class);
		verify(client).customCommand(captor.capture());

		GlideString[] args = captor.getValue();
		// HGETEX key EX 60 FIELDS 1 field1
		assertThat(args[0].getString()).isEqualTo("HGETEX");
		assertThat(args[1].getString()).isEqualTo("hash");
		assertThat(args[2].getString()).isEqualTo("EX");
		assertThat(args[3].getString()).isEqualTo("60");
		assertThat(args[4].getString()).isEqualTo("FIELDS");
	}

	// --- DELEX ---

	@Test
	void delexWithDigestShouldUseAsBytes() throws Exception {

		when(client.customCommand(any(GlideString[].class))).thenReturn(1L);

		CompareCondition condition = CompareCondition.ifDigestEquals("abcdef0123456789");
		keyCommands.delex("key".getBytes(), condition);

		ArgumentCaptor<GlideString[]> captor = ArgumentCaptor.forClass(GlideString[].class);
		verify(client).customCommand(captor.capture());

		GlideString[] args = captor.getValue();
		// DELEX key IFDEQ abcdef0123456789
		assertThat(args[0].getString()).isEqualTo("DELEX");
		assertThat(args[1].getString()).isEqualTo("key");
		assertThat(args[2].getString()).isEqualTo("IFDEQ");
		assertThat(args[3].getString()).isEqualTo("abcdef0123456789");
	}

	@Test
	void delexWithValueShouldUseAsBytes() throws Exception {

		when(client.customCommand(any(GlideString[].class))).thenReturn(1L);

		CompareCondition condition = CompareCondition.ifEquals("hello".getBytes());
		keyCommands.delex("key".getBytes(), condition);

		ArgumentCaptor<GlideString[]> captor = ArgumentCaptor.forClass(GlideString[].class);
		verify(client).customCommand(captor.capture());

		GlideString[] args = captor.getValue();
		// DELEX key IFEQ hello
		assertThat(args[0].getString()).isEqualTo("DELEX");
		assertThat(args[1].getString()).isEqualTo("key");
		assertThat(args[2].getString()).isEqualTo("IFEQ");
		assertThat(args[3].getString()).isEqualTo("hello");
	}

	@Test
	void delexShouldHandleWrongtypeResponse() throws Exception {

		when(client.customCommand(any(GlideString[].class))).thenReturn(GlideString.of("WRONGTYPE"));

		CompareCondition condition = CompareCondition.ifEquals("value".getBytes());

		assertThatThrownBy(() -> keyCommands.delex("key".getBytes(), condition))
			.isInstanceOf(InvalidDataAccessApiUsageException.class)
			.hasMessageContaining("WRONGTYPE");
	}

	@Test
	void delexShouldReturnTrueOnSuccess() throws Exception {

		when(client.customCommand(any(GlideString[].class))).thenReturn(1L);

		CompareCondition condition = CompareCondition.ifEquals("value".getBytes());
		Boolean result = keyCommands.delex("key".getBytes(), condition);

		assertThat(result).isTrue();
	}

	@Test
	void delexShouldReturnFalseOnMismatch() throws Exception {

		when(client.customCommand(any(GlideString[].class))).thenReturn(0L);

		CompareCondition condition = CompareCondition.ifEquals("value".getBytes());
		Boolean result = keyCommands.delex("key".getBytes(), condition);

		assertThat(result).isFalse();
	}

}
