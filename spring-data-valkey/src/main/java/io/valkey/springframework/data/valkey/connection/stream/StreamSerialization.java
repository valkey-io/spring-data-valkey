/*
 * Copyright 2018-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.stream;

import java.nio.ByteBuffer;

import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import io.valkey.springframework.data.valkey.util.ByteUtils;
import org.springframework.lang.Nullable;

/**
 * Utility methods for stream serialization.
 *
 * @author Mark Paluch
 * @since 2.2
 */
class StreamSerialization {

	/**
	 * Serialize the {@code value} using the optional {@link ValkeySerializer}. If no conversion is possible, {@code value}
	 * is assumed to be a byte array.
	 *
	 * @param serializer the serializer. Can be {@literal null}.
	 * @param value the value to serialize.
	 * @return the serialized (binary) representation of {@code value}.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static byte[] serialize(@Nullable ValkeySerializer<?> serializer, Object value) {
		return canSerialize(serializer, value) ? ((ValkeySerializer) serializer).serialize(value) : (byte[]) value;
	}

	/**
	 * Deserialize the {@code value using the optional {@link ValkeySerializer}. If no conversion is possible, return
	 * {@code value}. @param serializer @param value @param <T> @return
	 */
	static <T> T deserialize(@Nullable ValkeySerializer<? extends T> serializer, ByteBuffer value) {
		return deserialize(serializer, ByteUtils.getBytes(value));
	}

	/**
	 * Deserialize the {@code value using the optional {@link ValkeySerializer}. If no conversion is possible, return
	 * {@code value}. @param serializer @param value @param <T> @return
	 */
	static <T> T deserialize(@Nullable ValkeySerializer<? extends T> serializer, byte[] value) {
		return serializer != null ? serializer.deserialize(value) : (T) value;
	}

	/**
	 * Returns whether the given {@link ValkeySerializer} is capable of serializing the {@code value} to {@code byte[]}.
	 *
	 * @param serializer the serializer. Can be {@literal null}.
	 * @param value the value to serialize.
	 * @return {@literal true} if the given {@link ValkeySerializer} is capable of serializing the {@code value} to
	 *         {@code byte[]}.
	 */
	private static boolean canSerialize(@Nullable ValkeySerializer<?> serializer, @Nullable Object value) {
		return serializer != null && (value == null || serializer.canSerialize(value.getClass()));
	}
}
