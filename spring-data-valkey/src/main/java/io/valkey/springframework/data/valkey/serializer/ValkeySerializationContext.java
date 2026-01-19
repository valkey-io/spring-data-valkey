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
 * Serialization context for reactive use.
 * <p>
 * This context provides {@link SerializationPair}s for key, value, hash-key (field), hash-value and {@link String}
 * serialization and deserialization.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author John Blum
 * @since 2.0
 * @see ValkeyElementWriter
 * @see ValkeyElementReader
 */
public interface ValkeySerializationContext<K, V> {

	/**
	 * Creates a new {@link ValkeySerializationContextBuilder}.
	 *
	 * @param <K> expected key type.
	 * @param <V> expected value type.
	 * @return a new {@link ValkeySerializationContextBuilder}.
	 */
	static <K, V> ValkeySerializationContextBuilder<K, V> newSerializationContext() {
		return new DefaultValkeySerializationContext.DefaultValkeySerializationContextBuilder<>();
	}

	/**
	 * Creates a new {@link ValkeySerializationContextBuilder} using a given default {@link ValkeySerializer}.
	 *
	 * @param defaultSerializer must not be {@literal null}.
	 * @param <K> expected key type.
	 * @param <V> expected value type.
	 * @return a new {@link ValkeySerializationContextBuilder}.
	 */
	static <K, V> ValkeySerializationContextBuilder<K, V> newSerializationContext(ValkeySerializer<?> defaultSerializer) {

		Assert.notNull(defaultSerializer, "DefaultSerializer must not be null");

		return newSerializationContext(SerializationPair.fromSerializer(defaultSerializer));
	}

	/**
	 * Creates a new {@link ValkeySerializationContextBuilder} using a given default {@link SerializationPair}.
	 *
	 * @param serializationPair must not be {@literal null}.
	 * @param <K> expected key type.
	 * @param <V> expected value type.
	 * @return a new {@link ValkeySerializationContextBuilder}.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static <K, V> ValkeySerializationContextBuilder<K, V> newSerializationContext(SerializationPair<?> serializationPair) {

		Assert.notNull(serializationPair, "SerializationPair must not be null");

		return new DefaultValkeySerializationContext.DefaultValkeySerializationContextBuilder() //
				.key(serializationPair).value(serializationPair) //
				.hashKey(serializationPair).hashValue(serializationPair);
	}

	/**
	 * Creates a new {@link ValkeySerializationContext} using a {@link ValkeySerializer#byteArray() byte[]} serialization
	 * pair.
	 *
	 * @return new instance of {@link ValkeySerializationContext}.
	 * @deprecated since 2.2. Please use {@link #byteArray()} instead.
	 */
	@Deprecated
	static ValkeySerializationContext<byte[], byte[]> raw() {
		return byteArray();
	}

	/**
	 * Creates a new {@link ValkeySerializationContext} using a {@link ValkeySerializer#byteArray() byte[]} serialization.
	 *
	 * @return new instance of {@link ValkeySerializationContext}.
	 * @since 2.2
	 */
	static ValkeySerializationContext<byte[], byte[]> byteArray() {
		return just(ValkeySerializerToSerializationPairAdapter.byteArray());
	}

	/**
	 * Creates a new {@link ValkeySerializationContext} using a {@link SerializationPair#byteBuffer() ByteBuffer}
	 * serialization.
	 *
	 * @return new instance of {@link ValkeySerializationContext}.
	 * @since 2.2
	 */
	static ValkeySerializationContext<ByteBuffer, ByteBuffer> byteBuffer() {
		return just(ValkeySerializerToSerializationPairAdapter.byteBuffer());
	}

	/**
	 * Creates a new {@link ValkeySerializationContext} using a {@link JdkSerializationValkeySerializer}.
	 *
	 * @return a new {@link ValkeySerializationContext} using JDK Serializaton.
	 * @since 2.1
	 */
	static ValkeySerializationContext<Object, Object> java() {
		return fromSerializer(ValkeySerializer.java());
	}

	/**
	 * Creates a new {@link ValkeySerializationContext} using a {@link JdkSerializationValkeySerializer} with given
	 * {@link ClassLoader} to resolves {@link Class type} of the keys and values stored in Valkey.
	 *
	 * @param classLoader {@link ClassLoader} used to resolve {@link Class types} of keys and value stored in Valkey
	 * during deserialization; can be {@literal null}.
	 * @return a new {@link ValkeySerializationContext} using JDK Serializaton.
	 * @since 2.1
	 */
	static ValkeySerializationContext<Object, Object> java(ClassLoader classLoader) {
		return fromSerializer(ValkeySerializer.java(classLoader));
	}

	/**
	 * Creates a new {@link ValkeySerializationContext} using a {@link StringValkeySerializer}.
	 *
	 * @return a new {@link ValkeySerializationContext} using a {@link StringValkeySerializer}.
	 */
	static ValkeySerializationContext<String, String> string() {
		return fromSerializer(ValkeySerializer.string());
	}

	/**
	 * Creates a new {@link ValkeySerializationContext} using the given {@link ValkeySerializer}.
	 *
	 * @param <T> {@link Class Type} of {@link Object} being de/serialized by the {@link ValkeySerializer}.
	 * @param serializer {@link ValkeySerializer} used to de/serialize keys and value stored in Valkey;
	 * must not be {@literal null}.
	 * @return a new {@link ValkeySerializationContext} using the given {@link ValkeySerializer}.
	 */
	static <T> ValkeySerializationContext<T, T> fromSerializer(ValkeySerializer<T> serializer) {
		return just(SerializationPair.fromSerializer(serializer));
	}

	/**
	 * Creates a new {@link ValkeySerializationContext} using the given {@link SerializationPair}.
	 *
	 * @param <T> {@link Class Type} of {@link Object} de/serialized by the {@link SerializationPair}.
	 * @param serializationPair {@link SerializationPair} used to de/serialize keys and values stored in Valkey;
	 * must not be {@literal null}.
	 * @return a new {@link ValkeySerializationContext} using the given {@link SerializationPair}.
	 */
	static <T> ValkeySerializationContext<T, T> just(SerializationPair<T> serializationPair) {
		return ValkeySerializationContext.<T, T> newSerializationContext(serializationPair).build();
	}

	/**
	 * @return {@link SerializationPair} for key-typed serialization and deserialization.
	 */
	SerializationPair<K> getKeySerializationPair();

	/**
	 * @return {@link SerializationPair} for value-typed serialization and deserialization.
	 */
	SerializationPair<V> getValueSerializationPair();

	/**
	 * @return {@link SerializationPair} for hash-key-typed serialization and deserialization.
	 */
	<HK> SerializationPair<HK> getHashKeySerializationPair();

	/**
	 * @return {@link SerializationPair} for hash-value-typed serialization and deserialization.
	 */
	<HV> SerializationPair<HV> getHashValueSerializationPair();

	/**
	 * @return {@link SerializationPair} for {@link String}-typed serialization and deserialization.
	 */
	SerializationPair<String> getStringSerializationPair();

	/**
	 * Typed serialization tuple.
	 *
	 * @author Mark Paluch
	 * @author Christoph Strobl
	 */
	interface SerializationPair<T> {

		/**
		 * Creates a {@link SerializationPair} adapter given {@link ValkeySerializer}.
		 *
		 * @param serializer must not be {@literal null}.
		 * @return a {@link SerializationPair} adapter for {@link ValkeySerializer}.
		 */
		static <T> SerializationPair<T> fromSerializer(ValkeySerializer<T> serializer) {

			Assert.notNull(serializer, "ValkeySerializer must not be null");

			return new ValkeySerializerToSerializationPairAdapter<>(serializer);
		}

		/**
		 * Creates a {@link SerializationPair} adapter given {@link ValkeyElementReader} and {@link ValkeyElementWriter}.
		 *
		 * @param reader must not be {@literal null}.
		 * @param writer must not be {@literal null}.
		 * @return a {@link SerializationPair} encapsulating {@link ValkeyElementReader} and {@link ValkeyElementWriter}.
		 */
		static <T> SerializationPair<T> just(ValkeyElementReader<? extends T> reader,
				ValkeyElementWriter<? extends T> writer) {

			Assert.notNull(reader, "ValkeyElementReader must not be null");
			Assert.notNull(writer, "ValkeyElementWriter must not be null");

			return new DefaultSerializationPair<>(reader, writer);
		}

		/**
		 * Creates a pass through {@link SerializationPair} to pass-thru {@link ByteBuffer} objects.
		 *
		 * @return a pass through {@link SerializationPair}.
		 * @deprecated since 2.2. Please use either {@link #byteArray()} or {@link #byteBuffer()}.
		 */
		@Deprecated
		static <T> SerializationPair<T> raw() {
			return ValkeySerializerToSerializationPairAdapter.raw();
		}

		/**
		 * Creates a pass through {@link SerializationPair} to pass-thru {@code byte} objects.
		 *
		 * @return a pass through {@link SerializationPair}.
		 * @since 2.2
		 */
		static SerializationPair<byte[]> byteArray() {
			return ValkeySerializerToSerializationPairAdapter.byteArray();
		}

		/**
		 * Creates a pass through {@link SerializationPair} to pass-thru {@link ByteBuffer} objects.
		 *
		 * @return a pass through {@link SerializationPair}.
		 * @since 2.2
		 */
		static SerializationPair<ByteBuffer> byteBuffer() {
			return ValkeySerializerToSerializationPairAdapter.byteBuffer();
		}

		/**
		 * @return the {@link ValkeyElementReader}.
		 */
		ValkeyElementReader<T> getReader();

		/**
		 * Deserialize a {@link ByteBuffer} into the according type.
		 *
		 * @param buffer must not be {@literal null}.
		 * @return the deserialized value. Can be {@literal null}.
		 */
		@Nullable
		default T read(ByteBuffer buffer) {
			return getReader().read(buffer);
		}

		/**
		 * @return the {@link ValkeyElementWriter}.
		 */
		ValkeyElementWriter<T> getWriter();

		/**
		 * Serialize the given {@code element} to its {@link ByteBuffer} representation.
		 *
		 * @param element {@link Object} to write (serialize) as a stream of bytes.
		 * @return the {@link ByteBuffer} representing the given {@code element} in binary form.
		 */
		default ByteBuffer write(T element) {
			return getWriter().write(element);
		}
	}

	/**
	 * Builder for {@link ValkeySerializationContext}.
	 *
	 * @author Mark Paluch
	 * @author Christoph Strobl
	 */
	interface ValkeySerializationContextBuilder<K, V> {

		/**
		 * Set the key {@link SerializationPair}.
		 *
		 * @param pair must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		ValkeySerializationContextBuilder<K, V> key(SerializationPair<K> pair);

		/**
		 * Set the key {@link ValkeyElementReader} and {@link ValkeyElementWriter}.
		 *
		 * @param reader must not be {@literal null}.
		 * @param writer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> key(ValkeyElementReader<K> reader, ValkeyElementWriter<K> writer) {

			key(SerializationPair.just(reader, writer));

			return this;
		}

		/**
		 * Set the key {@link SerializationPair} given a {@link ValkeySerializer}.
		 *
		 * @param serializer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> key(ValkeySerializer<K> serializer) {

			key(SerializationPair.fromSerializer(serializer));

			return this;
		}

		/**
		 * Set the value {@link SerializationPair}.
		 *
		 * @param pair must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		ValkeySerializationContextBuilder<K, V> value(SerializationPair<V> pair);

		/**
		 * Set the value {@link ValkeyElementReader} and {@link ValkeyElementWriter}.
		 *
		 * @param reader must not be {@literal null}.
		 * @param writer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> value(ValkeyElementReader<V> reader, ValkeyElementWriter<V> writer) {

			value(SerializationPair.just(reader, writer));

			return this;
		}

		/**
		 * Set the value {@link SerializationPair} given a {@link ValkeySerializer}.
		 *
		 * @param serializer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> value(ValkeySerializer<V> serializer) {

			value(SerializationPair.fromSerializer(serializer));

			return this;
		}

		/**
		 * Set the hash key {@link SerializationPair}.
		 *
		 * @param pair must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		ValkeySerializationContextBuilder<K, V> hashKey(SerializationPair<?> pair);

		/**
		 * Set the hash key {@link ValkeyElementReader} and {@link ValkeyElementWriter}.
		 *
		 * @param reader must not be {@literal null}.
		 * @param writer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> hashKey(ValkeyElementReader<?> reader,
				ValkeyElementWriter<?> writer) {

			hashKey(SerializationPair.just(reader, writer));

			return this;
		}

		/**
		 * Set the hash key {@link SerializationPair} given a {@link ValkeySerializer}.
		 *
		 * @param serializer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> hashKey(ValkeySerializer<?> serializer) {

			hashKey(SerializationPair.fromSerializer(serializer));

			return this;
		}

		/**
		 * Set the hash value {@link SerializationPair}.
		 *
		 * @param pair must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		ValkeySerializationContextBuilder<K, V> hashValue(SerializationPair<?> pair);

		/**
		 * Set the hash value {@link ValkeyElementReader} and {@link ValkeyElementWriter}.
		 *
		 * @param reader must not be {@literal null}.
		 * @param writer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> hashValue(ValkeyElementReader<?> reader,
				ValkeyElementWriter<?> writer) {

			hashValue(SerializationPair.just(reader, writer));

			return this;
		}

		/**
		 * Set the hash value {@link SerializationPair} given a {@link ValkeySerializer}.
		 *
		 * @param serializer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> hashValue(ValkeySerializer<?> serializer) {

			hashValue(SerializationPair.fromSerializer(serializer));

			return this;
		}

		/**
		 * Set the string {@link SerializationPair}.
		 *
		 * @param pair must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		ValkeySerializationContextBuilder<K, V> string(SerializationPair<String> pair);

		/**
		 * Set the string {@link ValkeyElementReader} and {@link ValkeyElementWriter}.
		 *
		 * @param reader must not be {@literal null}.
		 * @param writer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> string(ValkeyElementReader<String> reader,
				ValkeyElementWriter<String> writer) {

			string(SerializationPair.just(reader, writer));

			return this;
		}

		/**
		 * Set the string {@link SerializationPair} given a {@link ValkeySerializer}.
		 *
		 * @param serializer must not be {@literal null}.
		 * @return {@literal this} builder.
		 */
		default ValkeySerializationContextBuilder<K, V> string(ValkeySerializer<String> serializer) {

			string(SerializationPair.fromSerializer(serializer));

			return this;
		}

		/**
		 * Builds a {@link ValkeySerializationContext}.
		 *
		 * @return the {@link ValkeySerializationContext}.
		 */
		ValkeySerializationContext<K, V> build();

	}
}
