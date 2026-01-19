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
package io.valkey.springframework.data.valkey.core.script;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.NonTransientDataAccessException;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import org.springframework.lang.Nullable;

/**
 * Utilities for Lua script execution and result deserialization.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.0
 */
class ScriptUtils {

	private ScriptUtils() {}

	/**
	 * Deserialize {@code result} using {@link ValkeySerializer} to the serializer type. Collection types and intermediate
	 * collection elements are deserialized recursivly.
	 *
	 * @param resultSerializer must not be {@literal null}.
	 * @param result must not be {@literal null}.
	 * @return the deserialized result.
	 */
	@SuppressWarnings({ "unchecked" })
	static <T> T deserializeResult(ValkeySerializer<T> resultSerializer, Object result) {

		if (result instanceof byte[] resultBytes) {
			return resultSerializer.deserialize(resultBytes);
		}

		if (result instanceof List listResult) {

			List<Object> results = new ArrayList<>(listResult.size());

			for (Object obj : listResult) {
				results.add(deserializeResult(resultSerializer, obj));
			}

			return (T) results;
		}

		return (T) result;
	}

	/**
	 * Deserialize {@code result} using {@link ValkeyElementReader} to the reader type. Collection types and intermediate
	 * collection elements are deserialized recursively.
	 *
	 * @param reader must not be {@literal null}.
	 * @param result must not be {@literal null}.
	 * @return the deserialized result.
	 */
	@Nullable
	@SuppressWarnings({ "unchecked" })
	static <T> T deserializeResult(ValkeyElementReader<T> reader, Object result) {

		if (result instanceof ByteBuffer byteBuffer) {
			return reader.read(byteBuffer);
		}

		if (result instanceof List listResult) {

			List<Object> results = new ArrayList<>(listResult.size());

			for (Object obj : listResult) {
				results.add(deserializeResult(reader, obj));
			}

			return (T) results;
		}
		return (T) result;
	}

	/**
	 * Checks whether given {@link Throwable} contains a {@code NOSCRIPT} error. {@code NOSCRIPT} is reported if a script
	 * was attempted to execute using {@code EVALSHA}.
	 *
	 * @param e the exception.
	 * @return {@literal true} if the exception or one of its causes contains a {@literal NOSCRIPT} error.
	 */
	static boolean exceptionContainsNoScriptError(Throwable e) {

		if (!(e instanceof NonTransientDataAccessException)) {
			return false;
		}

		Throwable current = e;
		while (current != null) {

			String exMessage = current.getMessage();
			if (exMessage != null && exMessage.contains("NOSCRIPT")) {
				return true;
			}

			current = current.getCause();
		}

		return false;
	}
}
