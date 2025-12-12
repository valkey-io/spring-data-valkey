/*
 * Copyright 2017-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.lettuce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;
import io.valkey.springframework.data.valkey.connection.ReactiveStreamCommands.AddStreamRecord;
import io.valkey.springframework.data.valkey.connection.stream.ByteBufferRecord;
import io.valkey.springframework.data.valkey.connection.stream.MapRecord;

/**
 * Unit tests for {@link LettuceReactiveValkeyConnection}.
 *
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LettuceReactiveValkeyConnectionUnitTests {

	@Mock(answer = Answers.RETURNS_MOCKS) StatefulRedisConnection<ByteBuffer, ByteBuffer> sharedConnection;

	@Mock RedisReactiveCommands<ByteBuffer, ByteBuffer> reactiveCommands;
	@Mock LettuceConnectionProvider connectionProvider;

	@BeforeEach
	void before() {
		when(connectionProvider.getConnectionAsync(any())).thenReturn(CompletableFuture.completedFuture(sharedConnection));
		when(connectionProvider.releaseAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
		when(sharedConnection.reactive()).thenReturn(reactiveCommands);
	}

	@Test // DATAREDIS-720
	void shouldLazilyInitializeConnection() {

		new LettuceReactiveValkeyConnection(connectionProvider);

		verifyNoInteractions(connectionProvider);
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	void shouldExecuteUsingConnectionProvider() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		connection.execute(cmd -> Mono.just("foo")).as(StepVerifier::create).expectNext("foo").verifyComplete();

		verify(connectionProvider).getConnectionAsync(StatefulConnection.class);
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	void shouldExecuteDedicatedUsingConnectionProvider() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		connection.executeDedicated(cmd -> Mono.just("foo")).as(StepVerifier::create).expectNext("foo").verifyComplete();

		verify(connectionProvider).getConnectionAsync(StatefulConnection.class);
	}

	@Test // DATAREDIS-720
	void shouldExecuteOnSharedConnection() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(sharedConnection,
				connectionProvider);

		connection.execute(cmd -> Mono.just("foo")).as(StepVerifier::create).expectNext("foo").verifyComplete();

		verifyNoInteractions(connectionProvider);
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	void shouldExecuteDedicatedWithSharedConnection() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(sharedConnection,
				connectionProvider);

		connection.executeDedicated(cmd -> Mono.just("foo")).as(StepVerifier::create).expectNext("foo").verifyComplete();

		verify(connectionProvider).getConnectionAsync(StatefulConnection.class);
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	void shouldOperateOnDedicatedConnection() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		connection.getConnection().as(StepVerifier::create).expectNextCount(1).verifyComplete();

		verify(connectionProvider).getConnectionAsync(StatefulConnection.class);
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	void shouldCloseOnlyDedicatedConnection() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(sharedConnection,
				connectionProvider);

		connection.getConnection().as(StepVerifier::create).expectNextCount(1).verifyComplete();
		connection.getDedicatedConnection().as(StepVerifier::create).expectNextCount(1).verifyComplete();

		connection.close();

		verify(sharedConnection, never()).closeAsync();
		verify(connectionProvider, times(1)).releaseAsync(sharedConnection);
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	void shouldCloseConnectionOnlyOnce() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		connection.getConnection().as(StepVerifier::create).expectNextCount(1).verifyComplete();

		connection.close();
		connection.close();

		verify(connectionProvider, times(1)).releaseAsync(sharedConnection);
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	@SuppressWarnings("unchecked")
	void multipleCallsInProgressShouldConnectOnlyOnce() throws Exception {

		CompletableFuture<StatefulConnection<?, ?>> connectionFuture = new CompletableFuture<>();
		reset(connectionProvider);
		when(connectionProvider.getConnectionAsync(any())).thenReturn(connectionFuture);

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		CompletableFuture<StatefulConnection<ByteBuffer, ByteBuffer>> first = (CompletableFuture) connection.getConnection()
				.toFuture();
		CompletableFuture<StatefulConnection<ByteBuffer, ByteBuffer>> second = (CompletableFuture) connection
				.getConnection().toFuture();

		assertThat(first).isNotDone();
		assertThat(second).isNotDone();

		verify(connectionProvider, times(1)).getConnectionAsync(StatefulConnection.class);

		connectionFuture.complete(sharedConnection);

		first.get(10, TimeUnit.SECONDS);
		second.get(10, TimeUnit.SECONDS);

		assertThat(first).isCompletedWithValue(sharedConnection);
		assertThat(second).isCompletedWithValue(sharedConnection);
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	void shouldPropagateConnectionFailures() {

		reset(connectionProvider);
		when(connectionProvider.getConnectionAsync(any()))
				.thenReturn(LettuceFutureUtils.failed(new RedisConnectionException("something went wrong")));

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		connection.getConnection().as(StepVerifier::create).expectError(ValkeyConnectionFailureException.class).verify();
	}

	@Test // DATAREDIS-720, DATAREDIS-721
	void shouldRejectCommandsAfterClose() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);
		connection.close();

		connection.getConnection().as(StepVerifier::create).expectError(IllegalStateException.class).verify();
	}

	@Test // DATAREDIS-659, DATAREDIS-708
	void bgReWriteAofShouldRespondCorrectly() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		when(reactiveCommands.bgrewriteaof()).thenReturn(Mono.just("OK"));

		connection.serverCommands().bgReWriteAof().as(StepVerifier::create).expectNextCount(1).verifyComplete();
	}

	@Test // DATAREDIS-659, DATAREDIS-667, DATAREDIS-708
	void bgSaveShouldRespondCorrectly() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		when(reactiveCommands.bgsave()).thenReturn(Mono.just("OK"));

		connection.serverCommands().bgSave().as(StepVerifier::create).expectNextCount(1).verifyComplete();
	}

	@Test // DATAREDIS-1122
	void xaddShouldHonorMaxlen() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		ArgumentCaptor<XAddArgs> args = ArgumentCaptor.forClass(XAddArgs.class);
		when(reactiveCommands.xadd(any(ByteBuffer.class), args.capture(), anyMap())).thenReturn(Mono.just("1-1"));

		MapRecord<ByteBuffer, ByteBuffer, ByteBuffer> record = MapRecord.create(ByteBuffer.wrap("key".getBytes()),
				Collections.emptyMap());

		connection.streamCommands().xAdd(Mono.just(AddStreamRecord.of(ByteBufferRecord.of(record)).maxlen(100)))
				.subscribe();

		assertThat(args.getValue()).extracting("maxlen").isEqualTo(100L);
	}

	@Test // GH-2047
	void xaddShouldHonorNoMkStream() {

		LettuceReactiveValkeyConnection connection = new LettuceReactiveValkeyConnection(connectionProvider);

		ArgumentCaptor<XAddArgs> args = ArgumentCaptor.forClass(XAddArgs.class);
		when(reactiveCommands.xadd(any(ByteBuffer.class), args.capture(), anyMap())).thenReturn(Mono.just("1-1"));

		MapRecord<ByteBuffer, ByteBuffer, ByteBuffer> record = MapRecord.create(ByteBuffer.wrap("key".getBytes()),
				Collections.emptyMap());

		connection.streamCommands().xAdd(Mono.just(AddStreamRecord.of(ByteBufferRecord.of(record)).makeNoStream()))
				.subscribe();

		assertThat(args.getValue()).extracting("nomkstream").isEqualTo(true);
	}

}
