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
package io.valkey.springframework.data.valkey.connection;

import java.util.Optional;

import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * {@link ValueEncoding} is used for the Valkey internal data representation used in order to store the value associated
 * with a key. <br />
 * <dl>
 * <dt>Strings</dt>
 * <dd>{@link ValkeyValueEncoding#RAW} or {@link ValkeyValueEncoding#INT}</dd>
 * <dt>Lists</dt>
 * <dd>{@link ValkeyValueEncoding#ZIPLIST} or {@link ValkeyValueEncoding#LINKEDLIST}</dd>
 * <dt>Sets</dt>
 * <dd>{@link ValkeyValueEncoding#INTSET} or {@link ValkeyValueEncoding#HASHTABLE}</dd>
 * <dt>Hashes</dt>
 * <dd>{@link ValkeyValueEncoding#ZIPLIST} or {@link ValkeyValueEncoding#HASHTABLE}</dd>
 * <dt>Sorted Sets</dt>
 * <dd>{@link ValkeyValueEncoding#ZIPLIST} or {@link ValkeyValueEncoding#SKIPLIST}</dd>
 * <dt>Absent keys</dt>
 * <dd>{@link ValkeyValueEncoding#VACANT}</dd>
 * </dl>
 *
 * @author Christoph Strobl
 * @since 2.1
 */
public interface ValueEncoding {

	@Nullable
	String raw();

	/**
	 * Get the {@link ValueEncoding} for given {@code encoding}.
	 *
	 * @param encoding can be {@literal null}.
	 * @return never {@literal null}.
	 */
	static ValueEncoding of(@Nullable String encoding) {
		return ValkeyValueEncoding.lookup(encoding).orElse(() -> encoding);
	}

	/**
	 * Default {@link ValueEncoding} implementation of encodings used in Valkey.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	enum ValkeyValueEncoding implements ValueEncoding {

		/**
		 * Normal string encoding.
		 */
		RAW("raw"), //
		/**
		 * 64 bit signed interval String representing an integer.
		 */
		INT("int"), //
		/**
		 * Space saving representation for small lists, hashes and sorted sets.
		 */
		ZIPLIST("ziplist"), //
		/**
		 * Encoding for large lists.
		 */
		LINKEDLIST("linkedlist"), //
		/**
		 * Space saving representation for small sets that contain only integers.ø
		 */
		INTSET("intset"), //
		/**
		 * Encoding for large hashes.
		 */
		HASHTABLE("hashtable"), //
		/**
		 * Encoding for sorted sets of any size.
		 */
		SKIPLIST("skiplist"), //
		/**
		 * No encoding present due to non existing key.
		 */
		VACANT(null);

		private final @Nullable String raw;

		ValkeyValueEncoding(@Nullable String raw) {
			this.raw = raw;
		}

		@Override
		public String raw() {
			return raw;
		}

		@Nullable
		static Optional<ValueEncoding> lookup(@Nullable String encoding) {

			for (ValueEncoding valueEncoding : values()) {
				if (ObjectUtils.nullSafeEquals(valueEncoding.raw(), encoding)) {
					return Optional.of(valueEncoding);
				}
			}
			return Optional.empty();
		}
	}
}
