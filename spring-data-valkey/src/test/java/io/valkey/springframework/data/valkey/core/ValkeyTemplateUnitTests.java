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
package io.valkey.springframework.data.valkey.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.Serializable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataAccessException;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.serializer.JdkSerializationValkeySerializer;
import org.springframework.instrument.classloading.ShadowingClassLoader;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unit tests for {@link ValkeyTemplate}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
class ValkeyTemplateUnitTests {

	private ValkeyTemplate<Object, Object> template;
	private @Mock ValkeyConnectionFactory connectionFactoryMock;
	private @Mock ValkeyConnection valkeyConnectionMock;

	@BeforeEach
	void setUp() {

		TransactionSynchronizationManager.clear();

		template = new ValkeyTemplate<>();
		template.setConnectionFactory(connectionFactoryMock);
		when(connectionFactoryMock.getConnection()).thenReturn(valkeyConnectionMock);

		template.afterPropertiesSet();
	}

	@Test // DATAREDIS-277
	void replicaOfIsDelegatedToConnectionCorrectly() {

		template.replicaOf("127.0.0.1", 1001);
		verify(valkeyConnectionMock, times(1)).replicaOf(eq("127.0.0.1"), eq(1001));
	}

	@Test // DATAREDIS-277
	void replicaOfNoOneIsDelegatedToConnectionCorrectly() {

		template.replicaOfNoOne();
		verify(valkeyConnectionMock, times(1)).replicaOfNoOne();
	}

	@Test // DATAREDIS-501
	void templateShouldPassOnAndUseResoureLoaderClassLoaderToDefaultJdkSerializerWhenNotAlreadySet() {

		ShadowingClassLoader scl = new ShadowingClassLoader(ClassLoader.getSystemClassLoader());

		template = new ValkeyTemplate<>();
		template.setConnectionFactory(connectionFactoryMock);
		template.setBeanClassLoader(scl);
		template.afterPropertiesSet();

		when(valkeyConnectionMock.get(any(byte[].class)))
				.thenReturn(new JdkSerializationValkeySerializer().serialize(new SomeArbitrarySerializableObject()));

		Object deserialized = template.opsForValue().get("spring");
		assertThat(deserialized).isNotNull();
		assertThat(deserialized.getClass().getClassLoader()).isEqualTo((ClassLoader) scl);
	}

	@Test // DATAREDIS-531
	void executeWithStickyConnectionShouldNotCloseConnectionWhenDone() {

		CapturingCallback callback = new CapturingCallback();
		template.executeWithStickyConnection(callback);

		assertThat(callback.getConnection()).isSameAs(valkeyConnectionMock);
		verify(valkeyConnectionMock, never()).close();
	}

	@Test // DATAREDIS-988
	void executeSessionShouldReuseConnection() {

		template.execute(new SessionCallback<Object>() {
			@Nullable
			@Override
			public <K, V> Object execute(ValkeyOperations<K, V> operations) throws DataAccessException {

				template.multi();
				template.multi();
				return null;
			}
		});

		verify(connectionFactoryMock).getConnection();
		verify(valkeyConnectionMock).close();
	}

	@Test // DATAREDIS-988
	void executeSessionInTransactionShouldReuseConnection() {

		template.execute(new SessionCallback<Object>() {
			@Override
			public <K, V> Object execute(ValkeyOperations<K, V> operations) throws DataAccessException {

				template.multi();
				template.multi();
				return null;
			}
		});

		verify(connectionFactoryMock).getConnection();
		verify(valkeyConnectionMock).close();
	}

	@Test // DATAREDIS-988, DATAREDIS-891
	void transactionAwareTemplateShouldReleaseConnection() {

		template.setEnableTransactionSupport(true);

		template.execute(new ValkeyCallback<Object>() {
			@Nullable
			@Override
			public Object doInValkey(ValkeyConnection connection) throws DataAccessException {

				template.multi();
				template.multi();
				return null;
			}
		});

		verify(connectionFactoryMock, times(3)).getConnection();
		verify(valkeyConnectionMock, times(3)).close();
	}

	private static class SomeArbitrarySerializableObject implements Serializable {
		private static final long serialVersionUID = -5973659324040506423L;
	}

	static class CapturingCallback implements ValkeyCallback<Cursor<Object>> {

		private ValkeyConnection connection;

		@Override
		public Cursor<Object> doInValkey(ValkeyConnection connection) throws DataAccessException {
			this.connection = connection;
			return null;
		}

		public ValkeyConnection getConnection() {
			return connection;
		}
	}
}
