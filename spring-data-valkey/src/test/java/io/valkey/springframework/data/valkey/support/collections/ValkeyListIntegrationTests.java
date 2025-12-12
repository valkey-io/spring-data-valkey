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
package io.valkey.springframework.data.valkey.support.collections;

import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;

/**
 * Parameterized instance of Valkey tests.
 *
 * @author Costin Leau
 * @author John Blum
 */
public class ValkeyListIntegrationTests extends AbstractValkeyListIntegrationTests<Object> {

	/**
	 * Constructs a new, parameterized {@link ValkeyListIntegrationTests}.
	 *
	 * @param factory {@link ObjectFactory} used to create different types of elements to store in the list.
	 * @param template {@link ValkeyTemplate} used to perform operations on Valkey.
	 */
	public ValkeyListIntegrationTests(ObjectFactory<Object> factory, ValkeyTemplate<Object, Object> template) {
		super(factory, template);
	}

	ValkeyStore copyStore(ValkeyStore store) {
		return ValkeyList.create(store.getKey(), store.getOperations());
	}

	AbstractValkeyCollection<Object> createCollection() {
		return new DefaultValkeyList<Object>(getClass().getName(), this.template);
	}
}
