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

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.core.serializer.DefaultSerializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Java Serialization {@link ValkeySerializer}.
 * <p>
 * Delegates to the default (Java-based) {@link DefaultSerializer serializer}
 * and {@link DefaultDeserializer deserializer}.
 * <p>
 * This {@link ValkeySerializer serializer} can be constructed with either a custom {@link ClassLoader}
 * or custom {@link Converter converters}.
 *
 * @author Mark Pollack
 * @author Costin Leau
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author John Blum
 */
public class JdkSerializationValkeySerializer implements ValkeySerializer<Object> {

	private final Converter<Object, byte[]> serializer;
	private final Converter<byte[], Object> deserializer;

	/**
	 * Creates a new {@link JdkSerializationValkeySerializer} using the default {@link ClassLoader}.
	 */
	public JdkSerializationValkeySerializer() {
		this(new SerializingConverter(), new DeserializingConverter());
	}

	/**
	 * Creates a new {@link JdkSerializationValkeySerializer} with the given {@link ClassLoader} used to
	 * resolve {@link Class types} during deserialization.
	 *
	 * @param classLoader {@link ClassLoader} used to resolve {@link Class types} for deserialization;
	 * can be {@literal null}.
	 * @since 1.7
	 */
	public JdkSerializationValkeySerializer(@Nullable ClassLoader classLoader) {
		this(new SerializingConverter(), new DeserializingConverter(classLoader));
	}

	/**
	 * Creates a new {@link JdkSerializationValkeySerializer} using {@link Converter converters} to serialize and
	 * deserialize {@link Object objects}.
	 *
	 * @param serializer {@link Converter} used to serialize an {@link Object} to a byte array;
	 * must not be {@literal null}.
	 * @param deserializer {@link Converter} used to deserialize and convert a byte arra into an {@link Object};
	 * must not be {@literal null}
	 * @throws IllegalArgumentException if either the given {@code serializer} or {@code deserializer}
	 * are {@literal null}.
	 * @since 1.7
	 */
	public JdkSerializationValkeySerializer(Converter<Object, byte[]> serializer,
			Converter<byte[], Object> deserializer) {

		Assert.notNull(serializer, "Serializer must not be null");
		Assert.notNull(deserializer, "Deserializer must not be null");

		this.serializer = serializer;
		this.deserializer = deserializer;
	}

	@Nullable
	@Override
	public byte[] serialize(@Nullable Object value) {

		if (value == null) {
			return SerializationUtils.EMPTY_ARRAY;
		}

		try {
			return serializer.convert(value);
		} catch (Exception ex) {
			throw new SerializationException("Cannot serialize", ex);
		}
	}

	@Nullable
	@Override
	public Object deserialize(@Nullable byte[] bytes) {

		if (SerializationUtils.isEmpty(bytes)) {
			return null;
		}

		try {
			return deserializer.convert(bytes);
		} catch (Exception ex) {
			throw new SerializationException("Cannot deserialize", ex);
		}
	}
}
