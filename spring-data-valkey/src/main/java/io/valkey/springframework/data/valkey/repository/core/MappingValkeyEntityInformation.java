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
package io.valkey.springframework.data.valkey.repository.core;

import org.springframework.data.mapping.MappingException;
import io.valkey.springframework.data.valkey.core.mapping.ValkeyPersistentEntity;
import org.springframework.data.repository.core.support.PersistentEntityInformation;

/**
 * {@link ValkeyEntityInformation} implementation using a {@link ValkeyPersistentEntity} instance to lookup the necessary
 * information. Can be configured with a custom collection to be returned which will trump the one returned by the
 * {@link ValkeyPersistentEntity} if given.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @param <T>
 * @param <ID>
 */
public class MappingValkeyEntityInformation<T, ID> extends PersistentEntityInformation<T, ID>
		implements ValkeyEntityInformation<T, ID> {

	/**
	 * @param entity
	 */
	public MappingValkeyEntityInformation(ValkeyPersistentEntity<T> entity) {

		super(entity);

		if (!entity.hasIdProperty()) {
			throw new MappingException(
					("Entity %s requires to have an explicit id field;" + " Did you forget to provide one using @Id")
							.formatted(entity.getName()));
		}
	}
}
