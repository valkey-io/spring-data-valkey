/*
 * Copyright 2014-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.valkey.springframework.data.valkey.ValkeySystemException;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ReturnType;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;

/**
 * @author Christoph Strobl
 */
@ExtendWith(MockitoExtension.class)
class DefaultScriptExecutorUnitTests {

	private final DefaultValkeyScript<String> SCRIPT = new DefaultValkeyScript<>("return KEYS[0]", String.class);

	private StringValkeyTemplate template;
	private @Mock ValkeyConnection valkeyConnectionMock;
	private @Mock ValkeyConnectionFactory connectionFactoryMock;

	private DefaultScriptExecutor<String> executor;

	@BeforeEach
	void setUp() {

		when(connectionFactoryMock.getConnection()).thenReturn(valkeyConnectionMock);
		template = spy(new StringValkeyTemplate(connectionFactoryMock));
		template.afterPropertiesSet();

		executor = new DefaultScriptExecutor<>(template);
	}

	@Test // DATAREDIS-347
	void excuteCheckForPresenceOfScriptViaEvalSha1() {

		when(valkeyConnectionMock.evalSha(anyString(), any(ReturnType.class), anyInt())).thenReturn("FOO".getBytes());

		executor.execute(SCRIPT, null);

		verify(valkeyConnectionMock, times(1)).evalSha(anyString(), any(ReturnType.class), anyInt());
	}

	@Test // DATAREDIS-347
	void excuteShouldNotCallEvalWhenSha1Exists() {

		when(valkeyConnectionMock.evalSha(anyString(), any(ReturnType.class), anyInt())).thenReturn("FOO".getBytes());

		executor.execute(SCRIPT, null);

		verify(valkeyConnectionMock, never()).eval(any(byte[].class), any(ReturnType.class), anyInt());
	}

	@Test // DATAREDIS-347
	void excuteShouldUseEvalInCaseNoSha1PresentForGivenScript() {

		when(valkeyConnectionMock.evalSha(anyString(), any(ReturnType.class), anyInt())).thenThrow(
				new ValkeySystemException("NOSCRIPT No matching script; Please use EVAL.", new Exception()));

		executor.execute(SCRIPT, null);

		verify(valkeyConnectionMock, times(1)).eval(any(byte[].class), any(ReturnType.class), anyInt());
	}

	@Test // DATAREDIS-347
	void excuteShouldThrowExceptionInCaseEvalShaFailsWithOtherThanValkeySystemException() {

		when(valkeyConnectionMock.evalSha(anyString(), any(ReturnType.class), anyInt())).thenThrow(
				new UnsupportedOperationException("NOSCRIPT No matching script; Please use EVAL.", new Exception()));

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> executor.execute(SCRIPT, null));
	}

	@Test // DATAREDIS-347
	void excuteShouldThrowExceptionInCaseEvalShaFailsWithAlthoughTheScriptExists() {

		when(valkeyConnectionMock.evalSha(anyString(), any(ReturnType.class), anyInt())).thenThrow(
				new ValkeySystemException("Found Script but could not execute it.", new Exception()));

		assertThatExceptionOfType(ValkeySystemException.class).isThrownBy(() -> executor.execute(SCRIPT, null));
	}
}
