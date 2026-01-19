/*
 * Copyright 2013-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.lettuce;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandInterruptedException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import io.netty.channel.ChannelException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;
import io.valkey.springframework.data.valkey.ValkeySystemException;

/**
 * Converts Lettuce Exceptions to {@link DataAccessException}s
 *
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Mark Paluch
 */
public class LettuceExceptionConverter implements Converter<Exception, DataAccessException> {

	static final LettuceExceptionConverter INSTANCE = new LettuceExceptionConverter();

	public DataAccessException convert(Exception ex) {

		if (ex instanceof ExecutionException || ex instanceof RedisCommandExecutionException) {

			if (ex.getCause() != ex && ex.getCause() instanceof Exception cause) {
				return convert(cause);
			}
			return new ValkeySystemException("Error in execution", ex);
		}

		if (ex instanceof DataAccessException dae) {
			return dae;
		}

		if (ex instanceof RedisCommandInterruptedException) {
			return new ValkeySystemException("Valkey command interrupted", ex);
		}

		if (ex instanceof ChannelException || ex instanceof RedisConnectionException) {
			return new ValkeyConnectionFailureException("Valkey connection failed", ex);
		}

		if (ex instanceof TimeoutException || ex instanceof RedisCommandTimeoutException) {
			return new QueryTimeoutException("Valkey command timed out", ex);
		}

		if (ex instanceof RedisException) {
			return new ValkeySystemException("Valkey exception", ex);
		}

		return null;
	}
}
