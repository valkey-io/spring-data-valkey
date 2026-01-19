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
 *  limitations under the License.
 */
package io.valkey.springframework.data.valkey.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache.ValueWrapper;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext.SerializationPair;

/**
 * Unit tests for {@link ValkeyCache}.
 *
 * @author John Blum
 * @author Mark Paluch
 */
class ValkeyCacheUnitTests {

	@Test // GH-2650
	void cacheRetrieveValueCallsCacheWriterRetrieveCorrectly() throws Exception {

		ValkeyCacheWriter mockCacheWriter = mock(ValkeyCacheWriter.class);

		doReturn(true).when(mockCacheWriter).supportsAsyncRetrieve();
		doReturn(usingCompletedFuture("TEST".getBytes())).when(mockCacheWriter).retrieve(anyString(), any(byte[].class));

		ValkeyCacheConfiguration cacheConfiguration = ValkeyCacheConfiguration.defaultCacheConfig()
				.serializeValuesWith(SerializationPair.byteArray());

		ValkeyCache cache = new ValkeyCache("TestCache", mockCacheWriter, cacheConfiguration);

		CompletableFuture<ValueWrapper> value = cache.retrieve("TestKey");

		assertThat(value).isNotNull();
		assertThat(new String((byte[]) value.get().get())).isEqualTo("TEST");

		verify(mockCacheWriter, times(1)).retrieve(eq("TestCache"), isA(byte[].class));
		verify(mockCacheWriter).supportsAsyncRetrieve();
		verifyNoMoreInteractions(mockCacheWriter);
	}

	private <T> CompletableFuture<T> usingCompletedFuture(T value) {
		return CompletableFuture.completedFuture(value);
	}
}
