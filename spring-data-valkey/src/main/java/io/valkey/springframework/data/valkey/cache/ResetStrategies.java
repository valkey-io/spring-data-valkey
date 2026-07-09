/*
 * Copyright 2026-present the original author or authors.
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
package io.valkey.springframework.data.valkey.cache;

import org.springframework.cache.Cache;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands;

/**
 * Collection of {@link ResetStrategy} implementations.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 4.1
 */
class ResetStrategies {

	/**
	 * Strategy using Valkey {@code FLUSHDB} command to reset the cache. This is the fastest way to reset the cache, but it
	 * will also remove all keys in the database, which may not be desirable if the database is shared with other
	 * applications. Use with caution.
	 */
	enum FlushingResetStrategy implements ResetStrategy, CacheWriterOperation<String> {

		FLUSHING;

		@Override
		public String doWithCacheWriter(ValkeyCacheWriter cacheWriter) {
			return cacheWriter.execute(connection -> {
				connection.serverCommands().flushDb(ValkeyServerCommands.FlushOption.ASYNC);
				return "ok";
			});
		}
	}

	/**
	 * Simple reset strategies.
	 */
	enum DefaultResetStrategies implements ResetStrategy {

		/**
		 * Using {@link Cache#clear()} to clear the cache.
		 */
		CLEAR {
			@Override
			void doWithCache(Cache cache) {
				cache.clear();
			}
		},

		/**
		 * Using {@link Cache#invalidate()} to clear the cache.
		 */
		INVALIDATE {
			@Override
			void doWithCache(Cache cache) {
				cache.invalidate();
			}
		};

		/**
		 * Callback to perform the actual reset.
		 *
		 * @param cache
		 */
		abstract void doWithCache(Cache cache);

	}

}
