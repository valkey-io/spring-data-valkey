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
package io.valkey.springframework.data.valkey.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyKeyCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands;
import io.valkey.springframework.data.valkey.core.types.Expiration;

/**
 * Unit tests for {@link DefaultValkeyCacheWriter}
 *
 * @author John Blum
 * @author Christoph Strobl
 */
@ExtendWith(MockitoExtension.class)
class DefaultValkeyCacheWriterUnitTests {

	@Mock
	private CacheStatisticsCollector mockCacheStatisticsCollector = mock(CacheStatisticsCollector.class);

	@Mock
	private ValkeyConnection mockConnection;

	@Mock(strictness = Mock.Strictness.LENIENT)
	private ValkeyConnectionFactory mockConnectionFactory;

	@BeforeEach
	void setup() {
		doReturn(this.mockConnection).when(this.mockConnectionFactory).getConnection();
	}

	private ValkeyCacheWriter newValkeyCacheWriter() {
		return spy(new DefaultValkeyCacheWriter(this.mockConnectionFactory, mock(BatchStrategy.class))
				.withStatisticsCollector(this.mockCacheStatisticsCollector));
	}

	@Test // GH-2351
	void getWithNonNullTtl() {

		byte[] key = "TestKey".getBytes();
		byte[] value = "TestValue".getBytes();

		Duration ttl = Duration.ofSeconds(15);
		Expiration expiration = Expiration.from(ttl);

		ValkeyStringCommands mockStringCommands = mock(ValkeyStringCommands.class);

		doReturn(mockStringCommands).when(this.mockConnection).stringCommands();
		doReturn(value).when(mockStringCommands).getEx(any(), any());

		ValkeyCacheWriter cacheWriter = newValkeyCacheWriter();

		assertThat(cacheWriter.get("TestCache", key, ttl)).isEqualTo(value);

		verify(this.mockConnection, times(1)).stringCommands();
		verify(mockStringCommands, times(1)).getEx(eq(key), eq(expiration));
		verify(this.mockConnection, times(1)).close();
		verifyNoMoreInteractions(this.mockConnection, mockStringCommands);
	}

	@Test // GH-2351
	void getWithNullTtl() {

		byte[] key = "TestKey".getBytes();
		byte[] value = "TestValue".getBytes();

		ValkeyStringCommands mockStringCommands = mock(ValkeyStringCommands.class);

		doReturn(mockStringCommands).when(this.mockConnection).stringCommands();
		doReturn(value).when(mockStringCommands).get(any());

		ValkeyCacheWriter cacheWriter = newValkeyCacheWriter();

		assertThat(cacheWriter.get("TestCache", key, null)).isEqualTo(value);

		verify(this.mockConnection, times(1)).stringCommands();
		verify(mockStringCommands, times(1)).get(eq(key));
		verify(this.mockConnection, times(1)).close();
		verifyNoMoreInteractions(this.mockConnection, mockStringCommands);
	}

	@Test // GH-2890
	void mustNotUnlockWhenLockingFails() {

		byte[] key = "TestKey".getBytes();
		byte[] value = "TestValue".getBytes();

		ValkeyStringCommands mockStringCommands = mock(ValkeyStringCommands.class);
		ValkeyKeyCommands mockKeyCommands = mock(ValkeyKeyCommands.class);

		doReturn(mockStringCommands).when(this.mockConnection).stringCommands();
		doReturn(mockKeyCommands).when(this.mockConnection).keyCommands();
		doThrow(new PessimisticLockingFailureException("you-shall-not-pass")).when(mockStringCommands).set(any(byte[].class),
				any(byte[].class), any(), any());

		ValkeyCacheWriter cacheWriter = spy(
				new DefaultValkeyCacheWriter(this.mockConnectionFactory, Duration.ofMillis(10), mock(BatchStrategy.class))
						.withStatisticsCollector(this.mockCacheStatisticsCollector));

		assertThatException()
				.isThrownBy(() -> cacheWriter.get("TestCache", key, () -> value, Duration.ofMillis(10), false));

		verify(mockKeyCommands, never()).del(any());
	}
}
