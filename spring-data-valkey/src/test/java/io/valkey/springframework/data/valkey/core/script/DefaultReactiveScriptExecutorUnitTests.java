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
package io.valkey.springframework.data.valkey.core.script;

import static org.mockito.Mockito.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.ByteBuffer;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.valkey.springframework.data.valkey.Person;
import io.valkey.springframework.data.valkey.ValkeySystemException;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ReactiveScriptingCommands;
import io.valkey.springframework.data.valkey.connection.ReturnType;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;

/**
 * Unit tests for {@link DefaultReactiveScriptExecutor}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
@ExtendWith(MockitoExtension.class)
class DefaultReactiveScriptExecutorUnitTests {

	private final DefaultValkeyScript<String> SCRIPT = new DefaultValkeyScript<>("return KEYS[0]", String.class);

	@Mock ReactiveValkeyConnectionFactory connectionFactoryMock;
	@Mock ReactiveValkeyConnection connectionMock;
	@Mock ReactiveScriptingCommands scriptingCommandsMock;

	private DefaultReactiveScriptExecutor<String> executor;

	@BeforeEach
	void setUp() {

		when(connectionFactoryMock.getReactiveConnection()).thenReturn(connectionMock);
		when(connectionMock.scriptingCommands()).thenReturn(scriptingCommandsMock);
		when(connectionMock.closeLater()).thenReturn(Mono.empty());

		executor = new DefaultReactiveScriptExecutor<>(connectionFactoryMock, ValkeySerializationContext.string());
	}

	@Test // DATAREDIS-683
	void executeCheckForPresenceOfScriptViaEvalSha1() {

		when(scriptingCommandsMock.evalSha(anyString(), any(ReturnType.class), anyInt()))
				.thenReturn(Flux.just(ByteBuffer.wrap("FOO".getBytes())));

		executor.execute(SCRIPT).as(StepVerifier::create).expectNext("FOO").verifyComplete();

		verify(scriptingCommandsMock).evalSha(anyString(), any(ReturnType.class), anyInt());
		verify(scriptingCommandsMock, never()).eval(any(), any(ReturnType.class), anyInt());
	}

	@Test // DATAREDIS-683
	void executeShouldUseEvalInCaseNoSha1PresentForGivenScript() {

		when(scriptingCommandsMock.evalSha(anyString(), any(ReturnType.class), anyInt())).thenReturn(
				Flux.error(new ValkeySystemException("NOSCRIPT No matching script; Please use EVAL.", new Exception())));

		when(scriptingCommandsMock.eval(any(), any(ReturnType.class), anyInt()))
				.thenReturn(Flux.just(ByteBuffer.wrap("FOO".getBytes())));

		executor.execute(SCRIPT).as(StepVerifier::create).expectNext("FOO").verifyComplete();

		verify(scriptingCommandsMock).evalSha(anyString(), any(ReturnType.class), anyInt());
		verify(scriptingCommandsMock).eval(any(), any(ReturnType.class), anyInt());
	}

	@Test // DATAREDIS-683
	void executeShouldThrowExceptionInCaseEvalShaFailsWithOtherThanValkeySystemException() {

		when(scriptingCommandsMock.evalSha(anyString(), any(ReturnType.class), anyInt())).thenReturn(Flux
				.error(new UnsupportedOperationException("NOSCRIPT No matching script; Please use EVAL.", new Exception())));

		executor.execute(SCRIPT).as(StepVerifier::create).verifyError(UnsupportedOperationException.class);
	}

	@Test // DATAREDIS-683
	void releasesConnectionAfterExecution() {

		when(scriptingCommandsMock.evalSha(anyString(), any(ReturnType.class), anyInt()))
				.thenReturn(Flux.just(ByteBuffer.wrap("FOO".getBytes())));

		Flux<String> execute = executor.execute(SCRIPT, Collections.emptyList());

		verify(connectionMock, never()).close();

		execute.as(StepVerifier::create).expectNext("FOO").verifyComplete();

		verify(connectionMock).closeLater();
		verify(connectionMock, never()).close();
	}

	@Test // DATAREDIS-683
	void releasesConnectionOnError() {

		when(scriptingCommandsMock.evalSha(anyString(), any(ReturnType.class), anyInt()))
				.thenReturn(Flux.error(new RuntimeException()));

		executor.execute(SCRIPT).as(StepVerifier::create).verifyError();

		verify(connectionMock).closeLater();
		verify(connectionMock, never()).close();
	}

	@Test // DATAREDIS-683
	void doesNotConvertRawResult() {

		Person returnValue = new Person();

		when(scriptingCommandsMock.evalSha(anyString(), any(ReturnType.class), anyInt()))
				.thenReturn(Flux.just(returnValue));

		executor.execute(ValkeyScript.of("return KEYS[0]")).as(StepVerifier::create).expectNext(returnValue)
				.verifyComplete();
	}
}
