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

import io.valkey.springframework.data.valkey.connection.DefaultStringValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.StringValkeyConnection;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;

/**
 * String-focused extension of ValkeyTemplate. Since most operations against Valkey are String based, this class provides
 * a dedicated class that minimizes configuration of its more generic {@link ValkeyTemplate template} especially in terms
 * of serializers.
 * <p>
 * Note that this template exposes the {@link ValkeyConnection} used by the {@link ValkeyCallback} as a
 * {@link StringValkeyConnection}.
 *
 * @author Costin Leau
 * @author Mark Paluch
 */
public class StringValkeyTemplate extends ValkeyTemplate<String, String> {

	/**
	 * Constructs a new <code>StringValkeyTemplate</code> instance. {@link #setConnectionFactory(ValkeyConnectionFactory)}
	 * and {@link #afterPropertiesSet()} still need to be called.
	 */
	public StringValkeyTemplate() {
		setKeySerializer(ValkeySerializer.string());
		setValueSerializer(ValkeySerializer.string());
		setHashKeySerializer(ValkeySerializer.string());
		setHashValueSerializer(ValkeySerializer.string());
	}

	/**
	 * Constructs a new <code>StringValkeyTemplate</code> instance ready to be used.
	 *
	 * @param connectionFactory connection factory for creating new connections
	 */
	public StringValkeyTemplate(ValkeyConnectionFactory connectionFactory) {
		this();
		setConnectionFactory(connectionFactory);
		afterPropertiesSet();
	}

	protected ValkeyConnection preProcessConnection(ValkeyConnection connection, boolean existingConnection) {
		return new DefaultStringValkeyConnection(connection);
	}
}
