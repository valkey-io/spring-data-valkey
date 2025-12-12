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
package io.valkey.springframework.data.valkey.core.script;

import java.util.List;

import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;

/**
 * Executes {@link ValkeyScript}s
 *
 * @author Jennifer Hickey
 * @param <K> The type of keys that may be passed during script execution
 */
public interface ScriptExecutor<K> {

	/**
	 * Executes the given {@link ValkeyScript}
	 *
	 * @param script the script to execute.
	 * @param keys any keys that need to be passed to the script.
	 * @param args any args that need to be passed to the script.
	 * @return The return value of the script or {@literal null} if {@link ValkeyScript#getResultType()} is
	 *         {@literal null}, likely indicating a throw-away status reply (i.e. "OK")
	 */
	<T> T execute(ValkeyScript<T> script, List<K> keys, Object... args);

	/**
	 * Executes the given {@link ValkeyScript}, using the provided {@link ValkeySerializer}s to serialize the script
	 * arguments and result.
	 *
	 * @param script the script to execute.
	 * @param argsSerializer The {@link ValkeySerializer} to use for serializing args.
	 * @param resultSerializer The {@link ValkeySerializer} to use for serializing the script return value.
	 * @param keys any keys that need to be passed to the script.
	 * @param args any args that need to be passed to the script.
	 * @return The return value of the script or {@literal null} if {@link ValkeyScript#getResultType()} is
	 *         {@literal null}, likely indicating a throw-away status reply (i.e. "OK")
	 */
	<T> T execute(ValkeyScript<T> script, ValkeySerializer<?> argsSerializer, ValkeySerializer<T> resultSerializer,
			List<K> keys, Object... args);

}
