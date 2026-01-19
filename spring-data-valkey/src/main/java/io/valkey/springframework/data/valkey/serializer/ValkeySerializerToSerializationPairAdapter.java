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

import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext.SerializationPair;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Adapter to delegate serialization/deserialization to {@link ValkeySerializer}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author John Blum
 * @since 2.0
 */
class ValkeySerializerToSerializationPairAdapter<T> implements SerializationPair<T> {

	private static final ValkeySerializerToSerializationPairAdapter<?> BYTE_BUFFER =
			new ValkeySerializerToSerializationPairAdapter<>(null);

	private static final ValkeySerializerToSerializationPairAdapter<byte[]> BYTE_ARRAY =
			new ValkeySerializerToSerializationPairAdapter<>(ValkeySerializer.byteArray());

	private final DefaultSerializationPair<T> pair;

	ValkeySerializerToSerializationPairAdapter(@Nullable ValkeySerializer<T> serializer) {
		pair = new DefaultSerializationPair<>(new DefaultValkeyElementReader<>(serializer),
				new DefaultValkeyElementWriter<>(serializer));
	}

	/**
	 * @return the {@link ValkeySerializerToSerializationPairAdapter} for {@link ByteBuffer}.
	 * @deprecated since 2.2. Please use {@link #byteBuffer()} instead.
	 */
	@SuppressWarnings("unchecked")
	@Deprecated
	static <T> SerializationPair<T> raw() {
		return (SerializationPair<T>) byteBuffer();
	}

	/**
	 * @return the {@link ValkeySerializerToSerializationPairAdapter} for {@code byte[]}.
	 * @since 2.2
	 */
	static SerializationPair<byte[]> byteArray() {
		return BYTE_ARRAY;
	}

	/**
	 * @return the {@link ValkeySerializerToSerializationPairAdapter} for {@link ByteBuffer}.
	 * @since 2.2
	 */
	@SuppressWarnings("unchecked")
	static SerializationPair<ByteBuffer> byteBuffer() {
		return (SerializationPair<ByteBuffer>) BYTE_BUFFER;
	}

	/**
	 * Create a {@link SerializationPair} from given {@link ValkeySerializer}.
	 *
	 * @param <T> {@link Class type} of {@link Object} handled by the {@link ValkeySerializer}.
	 * @param valkeySerializer must not be {@literal null}.
	 * @return the given {@link ValkeySerializer} adapted as a {@link SerializationPair}.
	 */
	public static <T> SerializationPair<T> from(ValkeySerializer<T> valkeySerializer) {

		Assert.notNull(valkeySerializer, "ValkeySerializer must not be null");

		return new ValkeySerializerToSerializationPairAdapter<>(valkeySerializer);
	}

	@Override
	public ValkeyElementReader<T> getReader() {
		return pair.getReader();
	}

	@Override
	public ValkeyElementWriter<T> getWriter() {
		return pair.getWriter();
	}
}
