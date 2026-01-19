/*
 * Copyright 2016-2025 the original author or authors.
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

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.valkey.springframework.data.valkey.connection.BitFieldSubCommands;
import io.valkey.springframework.data.valkey.connection.BitFieldSubCommands.BitFieldType;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;

/**
 * @author Christoph Strobl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultValueOperationsUnitTests<K, V> {

	@Mock ValkeyConnectionFactory connectionFactoryMock;
	@Mock ValkeyConnection connectionMock;
	private ValkeySerializer<String> serializer;
	private ValkeyTemplate<String, V> template;
	private ValueOperations<String, V> valueOps;

	@BeforeEach
	void setUp() {

		when(connectionFactoryMock.getConnection()).thenReturn(connectionMock);

		serializer = new StringValkeySerializer();

		template = new ValkeyTemplate<String, V>();
		template.setKeySerializer(serializer);
		template.setConnectionFactory(connectionFactoryMock);
		template.afterPropertiesSet();

		this.valueOps = template.opsForValue();
	}

	@Test // DATAREDIS-562
	void bitfieldShouldBeDelegatedCorrectly() {

		BitFieldSubCommands command = BitFieldSubCommands.create().get(BitFieldType.INT_8).valueAt(0L);

		valueOps.bitField("key", command);

		verify(connectionMock, times(1)).bitField(eq(serializer.serialize("key")), eq(command));
	}
}
