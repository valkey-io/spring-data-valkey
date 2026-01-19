/*
 * Copyright 2018-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.support.atomic;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ValueOperations;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;

/**
 * Unit tests for {@link ValkeyAtomicDouble}.
 *
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
class ValkeyAtomicDoubleUnitTests {

	@Mock ValkeyOperations<String, Double> operationsMock;
	@Mock ValueOperations<String, Double> valueOperationsMock;

	@Test // DATAREDIS-872
	@SuppressWarnings("unchecked")
	void shouldUseSetIfAbsentForInitialValue() {

		when(operationsMock.opsForValue()).thenReturn(valueOperationsMock);
		when(operationsMock.getKeySerializer()).thenReturn(mock(ValkeySerializer.class));
		when(operationsMock.getValueSerializer()).thenReturn(mock(ValkeySerializer.class));

		new ValkeyAtomicDouble("id", operationsMock);

		verify(valueOperationsMock).setIfAbsent("id", 0d);
	}
}
