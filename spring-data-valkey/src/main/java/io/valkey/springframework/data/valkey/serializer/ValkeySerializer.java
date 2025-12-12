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
package io.valkey.springframework.data.valkey.serializer;

import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Basic interface serialization and deserialization of Objects to byte arrays (binary data). It is recommended that
 * implementations are designed to handle {@literal null} objects/empty arrays on serialization and deserialization
 * side. Note that Valkey does not accept {@literal null} keys or values but can return null replies (for non-existing
 * keys).
 *
 * @author Mark Pollack
 * @author Costin Leau
 * @author Christoph Strobl
 */
public interface ValkeySerializer<T> {

	/**
	 * Obtain a {@link ValkeySerializer} using java serialization. <strong>Note:</strong> Ensure that your domain objects
	 * are actually {@link java.io.Serializable serializable}.
	 *
	 * @return never {@literal null}.
	 * @since 2.1
	 */
	static ValkeySerializer<Object> java() {
		return java(null);
	}

	/**
	 * Obtain a {@link ValkeySerializer} using java serialization with the given {@link ClassLoader}.
	 * <strong>Note:</strong> Ensure that your domain objects are actually {@link java.io.Serializable serializable}.
	 *
	 * @param classLoader the {@link ClassLoader} to use for deserialization. Can be {@literal null}.
	 * @return new instance of {@link ValkeySerializer}. Never {@literal null}.
	 * @since 2.1
	 */
	static ValkeySerializer<Object> java(@Nullable ClassLoader classLoader) {
		return new JdkSerializationValkeySerializer(classLoader);
	}

	/**
	 * Obtain a {@link ValkeySerializer} that can read and write JSON using
	 * <a href="https://github.com/FasterXML/jackson-core">Jackson</a>.
	 *
	 * @return never {@literal null}.
	 * @since 2.1
	 */
	static ValkeySerializer<Object> json() {
		return new GenericJackson2JsonValkeySerializer();
	}

	/**
	 * Obtain a simple {@link java.lang.String} to {@code byte[]} (and back) serializer using
	 * {@link java.nio.charset.StandardCharsets#UTF_8 UTF-8} as the default {@link java.nio.charset.Charset}.
	 *
	 * @return never {@literal null}.
	 * @since 2.1
	 */
	static ValkeySerializer<String> string() {
		return StringValkeySerializer.UTF_8;
	}

	/**
	 * Obtain a {@link ValkeySerializer} that passes thru {@code byte[]}.
	 *
	 * @return never {@literal null}.
	 * @since 2.2
	 */
	static ValkeySerializer<byte[]> byteArray() {
		return ByteArrayValkeySerializer.INSTANCE;
	}

	/**
	 * Serialize the given object to binary data.
	 *
	 * @param value object to serialize. Can be {@literal null}.
	 * @return the equivalent binary data. Can be {@literal null}.
	 */
	@Nullable
	byte[] serialize(@Nullable T value) throws SerializationException;

	/**
	 * Deserialize an object from the given binary data.
	 *
	 * @param bytes object binary representation. Can be {@literal null}.
	 * @return the equivalent object instance. Can be {@literal null}.
	 */
	@Nullable
	T deserialize(@Nullable byte[] bytes) throws SerializationException;

	/**
	 * Check whether the given value {@code type} can be serialized by this serializer.
	 *
	 * @param type the value type.
	 * @return {@code true} if the value type can be serialized; {@code false} otherwise.
	 */
	default boolean canSerialize(Class<?> type) {
		return ClassUtils.isAssignable(getTargetType(), type);
	}

	/**
	 * Return the serializer target type.
	 *
	 * @return the serializer target type.
	 */
	default Class<?> getTargetType() {
		return Object.class;
	}
}
