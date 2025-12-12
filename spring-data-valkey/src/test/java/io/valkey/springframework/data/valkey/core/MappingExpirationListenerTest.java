/*
 * Copyright 2024-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.convert.ConversionService;
import io.valkey.springframework.data.valkey.connection.Message;
import io.valkey.springframework.data.valkey.core.convert.ValkeyConverter;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;

/**
 * @author Lucian Torje
 * @author Christoph Strobl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MappingExpirationListenerTest {

	@Mock private ValkeyOperations<?, ?> valkeyOperations;
	@Mock private ValkeyConverter valkeyConverter;
	@Mock private ValkeyMessageListenerContainer listenerContainer;
	@Mock private Message message;

	private ValkeyKeyValueAdapter.MappingExpirationListener listener;

	@Test // GH-2954
	void testOnNonKeyExpiration() {

		byte[] key = "testKey".getBytes();
		when(message.getBody()).thenReturn(key);
		listener = new ValkeyKeyValueAdapter.MappingExpirationListener(listenerContainer, valkeyOperations, valkeyConverter,
				ValkeyKeyValueAdapter.ShadowCopy.ON);

		listener.onMessage(message, null);

		verify(valkeyOperations, times(0)).execute(any(ValkeyCallback.class));
	}

	@Test // GH-2954
	void testOnValidKeyExpirationWithShadowCopiesDisabled() {

		List<Object> eventList = new ArrayList<>();

		byte[] key = "abc:testKey".getBytes();
		when(message.getBody()).thenReturn(key);

		listener = new ValkeyKeyValueAdapter.MappingExpirationListener(listenerContainer, valkeyOperations, valkeyConverter,
				ValkeyKeyValueAdapter.ShadowCopy.OFF);
		listener.setApplicationEventPublisher(eventList::add);
		listener.onMessage(message, null);

		verify(valkeyOperations, times(1)).execute(any(ValkeyCallback.class));
		assertThat(eventList).hasSize(1);
		assertThat(eventList.get(0)).isInstanceOf(ValkeyKeyExpiredEvent.class);
		assertThat(((ValkeyKeyExpiredEvent) (eventList.get(0))).getKeyspace()).isEqualTo("abc");
		assertThat(((ValkeyKeyExpiredEvent) (eventList.get(0))).getId()).isEqualTo("testKey".getBytes());
	}

	@Test // GH-2954
	void testOnValidKeyExpirationWithShadowCopiesEnabled() {

		ConversionService conversionService = Mockito.mock(ConversionService.class);
		List<Object> eventList = new ArrayList<>();

		byte[] key = "abc:testKey".getBytes();
		when(message.getBody()).thenReturn(key);
		when(valkeyConverter.getConversionService()).thenReturn(conversionService);
		when(conversionService.convert(any(), eq(byte[].class))).thenReturn("foo".getBytes());

		listener = new ValkeyKeyValueAdapter.MappingExpirationListener(listenerContainer, valkeyOperations, valkeyConverter,
				ValkeyKeyValueAdapter.ShadowCopy.ON);
		listener.setApplicationEventPublisher(eventList::add);
		listener.onMessage(message, null);

		verify(valkeyOperations, times(2)).execute(any(ValkeyCallback.class)); // delete entry in index, delete phantom key
		assertThat(eventList).hasSize(1);
		assertThat(eventList.get(0)).isInstanceOf(ValkeyKeyExpiredEvent.class);
		assertThat(((ValkeyKeyExpiredEvent) (eventList.get(0))).getKeyspace()).isEqualTo("abc");
		assertThat(((ValkeyKeyExpiredEvent) (eventList.get(0))).getId()).isEqualTo("testKey".getBytes());
	}
}
