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

import io.valkey.springframework.data.valkey.core.BoundKeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;

/**
 * Basic interface for Valkey-based collections. Offers access to the {@link ValkeyOperations} entity used for executing
 * commands against the backing store.
 *
 * @author Costin Leau
 */
public interface ValkeyStore extends BoundKeyOperations<String> {

	/**
	 * Returns the underlying Valkey operations used by the backing implementation.
	 *
	 * @return operations never {@literal null}.
	 */
	ValkeyOperations<String, ?> getOperations();
}
