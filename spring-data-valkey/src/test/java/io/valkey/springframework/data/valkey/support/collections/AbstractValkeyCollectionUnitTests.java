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
package io.valkey.springframework.data.valkey.support.collections;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;

/**
 * @author Christoph Strobl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbstractValkeyCollectionUnitTests {

	private AbstractValkeyCollection<String> collection;

	@SuppressWarnings("rawtypes")//
	private ValkeyTemplate valkeyTemplateSpy;
	private @Mock ValkeyConnectionFactory connectionFactoryMock;
	private @Mock(answer = Answers.RETURNS_MOCKS) ValkeyConnection connectionMock;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@BeforeEach
	void setUp() {

		valkeyTemplateSpy = spy(new ValkeyTemplate() {

			public Boolean hasKey(Object key) {
				return false;
			}
		});
		valkeyTemplateSpy.setConnectionFactory(connectionFactoryMock);
		valkeyTemplateSpy.afterPropertiesSet();

		collection = new AbstractValkeyCollection<String>("key", valkeyTemplateSpy) {

			private List<String> delegate = new ArrayList<>();

			@Override
			public boolean add(String value) {
				return this.delegate.add(value);
			};

			@Override
			public DataType getType() {
				return DataType.LIST;
			}

			@Override
			public Iterator<String> iterator() {
				return this.delegate.iterator();
			}

			@Override
			public int size() {
				return this.delegate.size();
			}

			@Override
			public boolean isEmpty() {
				return this.delegate.isEmpty();
			}

		};

		when(connectionFactoryMock.getConnection()).thenReturn(connectionMock);
	}

	@SuppressWarnings("unchecked")
	@Test // DATAREDIS-188
	void testRenameOfEmptyCollectionShouldNotTriggerValkeyOperation() {

		collection.rename("new-key");
		verify(valkeyTemplateSpy, never()).rename(eq("key"), eq("new-key"));
	}

	@SuppressWarnings("unchecked")
	@Test // DATAREDIS-188
	void testRenameCollectionShouldTriggerValkeyOperation() {

		when(valkeyTemplateSpy.hasKey(any())).thenReturn(Boolean.TRUE);

		collection.add("spring-data-valkey");
		collection.rename("new-key");
		verify(valkeyTemplateSpy, times(1)).rename(eq("key"), eq("new-key"));
	}
}
