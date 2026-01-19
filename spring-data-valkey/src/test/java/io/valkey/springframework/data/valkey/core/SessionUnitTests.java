/*
 * Copyright 2011-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;


import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.StringValkeyConnection;

/**
 * @author Costin Leau
 */
class SessionUnitTests {

	@Test
	void testSession() throws Exception {
		ValkeyConnection conn = mock(ValkeyConnection.class);
		StringValkeyConnection stringConn = mock(StringValkeyConnection.class);
		ValkeyConnectionFactory factory = mock(ValkeyConnectionFactory.class);
		StringValkeyTemplate template = spy(new StringValkeyTemplate(factory));
		when(factory.getConnection()).thenReturn(conn);
		doReturn(stringConn).when(template).preProcessConnection(eq(conn), anyBoolean());

		template.execute(new SessionCallback<Object>() {
			@SuppressWarnings("rawtypes")
			public Object execute(ValkeyOperations operations) {
				checkConnection(template, stringConn);
				template.discard();
				assertThat(operations).isSameAs(template);
				checkConnection(template, stringConn);
				return null;
			}
		});
	}

	private void checkConnection(ValkeyTemplate<?, ?> template, ValkeyConnection expectedConnection) {
		template.execute(connection -> {
			assertThat(connection).isSameAs(expectedConnection);
			return null;
		}, true);
	}
}
