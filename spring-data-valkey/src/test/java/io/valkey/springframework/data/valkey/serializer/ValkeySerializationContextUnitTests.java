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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ValkeySerializationContext}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Zhou KQ
 */
class ValkeySerializationContextUnitTests {

	@Test // DATAREDIS-602
	void shouldRejectBuildIfKeySerializerIsNotSet() {

		assertThatIllegalArgumentException()
				.isThrownBy(() -> ValkeySerializationContext.<String, String> newSerializationContext() //
				.value(StringValkeySerializer.UTF_8) //
				.hashKey(StringValkeySerializer.UTF_8) //
				.hashValue(StringValkeySerializer.UTF_8) //
						.build());
	}

	@Test // DATAREDIS-602
	void shouldRejectBuildIfValueSerializerIsNotSet() {

		assertThatIllegalArgumentException()
				.isThrownBy(() -> ValkeySerializationContext.<String, String> newSerializationContext() //
				.key(StringValkeySerializer.UTF_8) //
				.hashKey(StringValkeySerializer.UTF_8) //
				.hashValue(StringValkeySerializer.UTF_8) //
						.build());
	}

	@Test // DATAREDIS-602
	void shouldRejectBuildIfHashKeySerializerIsNotSet() {

		assertThatIllegalArgumentException()
				.isThrownBy(() -> ValkeySerializationContext.<String, String> newSerializationContext() //
				.key(StringValkeySerializer.UTF_8) //
				.value(StringValkeySerializer.UTF_8) //
				.hashValue(StringValkeySerializer.UTF_8) //
						.build());
	}

	@Test // DATAREDIS-602
	void shouldRejectBuildIfHashValueSerializerIsNotSet() {

		assertThatIllegalArgumentException()
				.isThrownBy(() -> ValkeySerializationContext.<String, String> newSerializationContext() //
				.key(StringValkeySerializer.UTF_8) //
				.value(StringValkeySerializer.UTF_8) //
				.hashKey(StringValkeySerializer.UTF_8) //
						.build());
	}

	@Test // DATAREDIS-602
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void shouldUseDefaultIfSet() {

		ValkeySerializationContext.<String, String>newSerializationContext(StringValkeySerializer.UTF_8)
				.key(new GenericToStringSerializer(Long.class)) //
				.build();
	}

	@Test // DATAREDIS-602
	void shouldBuildSerializationContext() {

		ValkeySerializationContext<String, Long> serializationContext = createSerializationContext();

		assertThat(serializationContext.getKeySerializationPair()).isNotNull();
		assertThat(serializationContext.getValueSerializationPair()).isNotNull();
		assertThat(serializationContext.getHashKeySerializationPair()).isNotNull();
		assertThat(serializationContext.getHashValueSerializationPair()).isNotNull();
		assertThat(serializationContext.getStringSerializationPair()).isNotNull();
	}

	@Test // DATAREDIS-602
	void shouldEncodeAndDecodeKey() {

		ValkeySerializationContext<String, Long> serializationContext = createSerializationContext();

		String deserialized = serializationContext.getKeySerializationPair()
				.read(serializationContext.getKeySerializationPair().write("foo"));

		assertThat(deserialized).isEqualTo("foo");
	}

	@Test // DATAREDIS-602
	void shouldEncodeAndDecodeValue() {

		ValkeySerializationContext<String, Long> serializationContext = createSerializationContext();

		long deserialized = serializationContext.getValueSerializationPair()
				.read(serializationContext.getValueSerializationPair().write(42L));

		assertThat(deserialized).isEqualTo(42);
	}

	@Test // DATAREDIS-1000
	void shouldEncodeAndDecodeRawByteBufferValue() {

		ValkeySerializationContext<ByteBuffer, ByteBuffer> serializationContext = ValkeySerializationContext.byteBuffer();

		ByteBuffer deserialized = serializationContext.getValueSerializationPair()
				.read(serializationContext.getValueSerializationPair()
						.write(ByteBuffer.wrap("hello".getBytes())));

		assertThat(deserialized).isEqualTo(ByteBuffer.wrap("hello".getBytes()));
	}

	@Test // DATAREDIS-1000
	void shouldEncodeAndDecodeByteArrayValue() {

		ValkeySerializationContext<byte[], byte[]> serializationContext = ValkeySerializationContext.byteArray();

		byte[] deserialized = serializationContext.getValueSerializationPair()
				.read(serializationContext.getValueSerializationPair()
						.write("hello".getBytes()));

		assertThat(deserialized).isEqualTo("hello".getBytes());
	}

	@Test // GH-2651
	void shouldEncodeAndDecodeUtf8StringValue() {

		ValkeySerializationContext.SerializationPair<String> serializationPair =
				buildStringSerializationContext(StringValkeySerializer.UTF_8).getStringSerializationPair();

		assertThat(serializationPair.write("üßØ")).isEqualTo(StandardCharsets.UTF_8.encode("üßØ"));
		assertThat(serializationPair.read(StandardCharsets.UTF_8.encode("üßØ"))).isEqualTo("üßØ");
	}

	@Test // GH-2651
	void shouldEncodeAndDecodeAsciiStringValue() {

		ValkeySerializationContext.SerializationPair<String> serializationPair =
				buildStringSerializationContext(StringValkeySerializer.US_ASCII).getStringSerializationPair();

		assertThat(serializationPair.write("üßØ")).isEqualTo(StandardCharsets.US_ASCII.encode("???"));
		assertThat(serializationPair.read(StandardCharsets.US_ASCII.encode("üßØ"))).isEqualTo("???");
	}

	@Test  // GH-2651
	void shouldEncodeAndDecodeIso88591StringValue() {

		ValkeySerializationContext.SerializationPair<String> serializationPair =
				buildStringSerializationContext(StringValkeySerializer.ISO_8859_1).getStringSerializationPair();

		assertThat(serializationPair.write("üßØ")).isEqualTo(StandardCharsets.ISO_8859_1.encode("üßØ"));
		assertThat(serializationPair.read(StandardCharsets.ISO_8859_1.encode("üßØ"))).isEqualTo("üßØ");
	}

	private ValkeySerializationContext<String, Long> createSerializationContext() {

		return ValkeySerializationContext.<String, Long> newSerializationContext() //
				.key(StringValkeySerializer.UTF_8) //
				.value(ByteBuffer::getLong, value -> ByteBuffer.allocate(8).putLong(value).flip()) //
				.hashKey(StringValkeySerializer.UTF_8) //
				.hashValue(StringValkeySerializer.UTF_8) //
				.build();
	}

	private ValkeySerializationContext<String, String> buildStringSerializationContext(
			ValkeySerializer<String> stringSerializer) {

		return ValkeySerializationContext.<String, String>newSerializationContext(stringSerializer)
				.string(stringSerializer)
				.build();
	}
}
