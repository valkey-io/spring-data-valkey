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

import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext.SerializationPair;

/**
 * Default implementation of {@link SerializationPair}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.0
 */
class DefaultSerializationPair<T> implements SerializationPair<T> {

	private final ValkeyElementReader<T> reader;
	private final ValkeyElementWriter<T> writer;

	@SuppressWarnings("unchecked")
	DefaultSerializationPair(ValkeyElementReader<? extends T> reader, ValkeyElementWriter<? extends T> writer) {

		this.reader = (ValkeyElementReader) reader;
		this.writer = (ValkeyElementWriter) writer;
	}

	@Override
	public ValkeyElementReader<T> getReader() {
		return this.reader;
	}

	@Override
	public ValkeyElementWriter<T> getWriter() {
		return this.writer;
	}
}
