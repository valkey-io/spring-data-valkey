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

import org.reactivestreams.Publisher;
import org.springframework.dao.DataAccessException;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection;

/**
 * Generic callback interface for code that operates on a low-level {@link ReactiveValkeyConnection}. Allows to execute
 * any number of operations on a single {@link ReactiveValkeyConnection}, using any type and number of commands.
 * <p>
 * This is particularly useful for delegating to existing data access code that expects a
 * {@link ReactiveValkeyConnection} to work on. For newly written code, it is strongly recommended to use
 * {@link ReactiveValkeyOperations}'s more specific operations.
 *
 * @param <T>
 * @author Mark Paluch
 * @since 2.0
 * @see ReactiveValkeyOperations#execute(ReactiveValkeyCallback)
 */
public interface ReactiveValkeyCallback<T> {

	/**
	 * Gets called by {@link ReactiveValkeyTemplate#execute(ReactiveValkeyCallback)} with an active Valkey connection. Does
	 * not need to care about activating or closing the {@link ReactiveValkeyConnection}.
	 * <p>
	 * Allows for returning a result object created within the callback, i.e. a domain object or a collection of domain
	 * objects.
	 *
	 * @param connection active Valkey connection.
	 * @return a result object publisher
	 * @throws DataAccessException in case of custom exceptions
	 */
	Publisher<T> doInValkey(ReactiveValkeyConnection connection) throws DataAccessException;
}
