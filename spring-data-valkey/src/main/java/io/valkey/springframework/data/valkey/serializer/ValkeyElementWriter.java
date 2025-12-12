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
package io.valkey.springframework.data.valkey.serializer;

import java.nio.ByteBuffer;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Strategy interface that specifies a serializer that can serialize an element to its binary representation to be used
 * as Valkey protocol payload.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.0
 */
@FunctionalInterface
public interface ValkeyElementWriter<T> {

	/**
	 * Serialize a {@code element} to its {@link ByteBuffer} representation.
	 *
	 * @param element can be {@literal null}.
	 * @return the {@link ByteBuffer} representing {@code element} in its binary form.
	 */
	ByteBuffer write(@Nullable T element);

	/**
	 * Create new {@link ValkeyElementWriter} using given {@link ValkeySerializer}.
	 *
	 * @param serializer must not be {@literal null}.
	 * @return new instance of {@link ValkeyElementWriter}.
	 */
	static <T> ValkeyElementWriter<T> from(ValkeySerializer<T> serializer) {

		Assert.notNull(serializer, "Serializer must not be null");
		return new DefaultValkeyElementWriter<>(serializer);
	}
}
