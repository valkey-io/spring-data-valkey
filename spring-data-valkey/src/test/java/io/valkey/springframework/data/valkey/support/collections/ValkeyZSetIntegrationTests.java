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
 * Parameterized instance of Valkey sorted set tests.
 *
 * @author Costin Leau
 */
public class ValkeyZSetIntegrationTests extends AbstractValkeyZSetTestIntegration<Object> {

	/**
	 * Constructs a new <code>ValkeyZSetTests</code> instance.
	 *
	 * @param factory
	 * @param template
	 */
	public ValkeyZSetIntegrationTests(ObjectFactory<Object> factory, ValkeyTemplate template) {
		super(factory, template);
	}

	ValkeyStore copyStore(ValkeyStore store) {
		return ValkeyZSet.create(store.getKey(), store.getOperations());
	}

	AbstractValkeyCollection<Object> createCollection() {
		String valkeyName = getClass().getName();
		return new DefaultValkeyZSet(valkeyName, template);
	}
}
