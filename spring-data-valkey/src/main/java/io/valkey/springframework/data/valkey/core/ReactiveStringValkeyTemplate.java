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
package io.valkey.springframework.data.valkey.core;

import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;

/**
 * {@link java.lang.String String-focused} extension of {@link ReactiveValkeyTemplate}. As most operations against Valkey
 * are {@link String} based, this class provides a dedicated arrangement that minimizes configuration of its more
 * generic {@link ReactiveValkeyTemplate template} especially in terms of the used {@link ValkeySerializationContext}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.1
 */
public class ReactiveStringValkeyTemplate extends ReactiveValkeyTemplate<String, String> {

	/**
	 * Creates new {@link ReactiveValkeyTemplate} using given {@link ReactiveValkeyConnectionFactory} applying default
	 * {@link String} serialization.
	 *
	 * @param connectionFactory must not be {@literal null}.
	 * @see ValkeySerializationContext#string()
	 */
	public ReactiveStringValkeyTemplate(ReactiveValkeyConnectionFactory connectionFactory) {
		this(connectionFactory, ValkeySerializationContext.string());
	}

	/**
	 * Creates new {@link ReactiveValkeyTemplate} using given {@link ReactiveValkeyConnectionFactory} and
	 * {@link ValkeySerializationContext}.
	 *
	 * @param connectionFactory must not be {@literal null}.
	 * @param serializationContext must not be {@literal null}.
	 */
	public ReactiveStringValkeyTemplate(ReactiveValkeyConnectionFactory connectionFactory,
			ValkeySerializationContext<String, String> serializationContext) {
		super(connectionFactory, serializationContext);
	}

	/**
	 * Creates new {@link ReactiveValkeyTemplate} using given {@link ReactiveValkeyConnectionFactory} and
	 * {@link ValkeySerializationContext}.
	 *
	 * @param connectionFactory must not be {@literal null}.
	 * @param serializationContext must not be {@literal null}.
	 * @param exposeConnection flag indicating to expose the connection used.
	 */
	public ReactiveStringValkeyTemplate(ReactiveValkeyConnectionFactory connectionFactory,
			ValkeySerializationContext<String, String> serializationContext, boolean exposeConnection) {
		super(connectionFactory, serializationContext, exposeConnection);
	}
}
