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

import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementWriter;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;

/**
 * Executes {@link ValkeyScript}s using reactive infrastructure.
 * <p>
 * Streams of methods returning {@code Mono<K>} or {@code Flux<M>} are terminated with
 * {@link org.springframework.dao.InvalidDataAccessApiUsageException} when
 * {@link io.valkey.springframework.data.valkey.serializer.ValkeyElementReader#read(ByteBuffer)} returns {@literal null} for a
 * particular element as Reactive Streams prohibit the usage of {@literal null} values.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @param <K> The type of keys that may be passed during script execution
 * @since 2.0
 */
public interface ReactiveScriptExecutor<K> {

	/**
	 * Execute the given {@link ValkeyScript}
	 *
	 * @param script must not be {@literal null}.
	 * @return the return value of the script or {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is
	 *         {@literal null}, likely indicating a throw-away status reply (i.e. "OK")
	 */
	default <T> Flux<T> execute(ValkeyScript<T> script) {
		return execute(script, Collections.emptyList());
	}

	/**
	 * Execute the given {@link ValkeyScript}
	 *
	 * @param script must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return the return value of the script or {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is
	 *         {@literal null}, likely indicating a throw-away status reply (i.e. "OK")
	 */
	default <T> Flux<T> execute(ValkeyScript<T> script, List<K> keys) {
		return execute(script, keys, Collections.emptyList());
	}

	/**
	 * Executes the given {@link ValkeyScript}
	 *
	 * @param script The script to execute. Must not be {@literal null}.
	 * @param keys any keys that need to be passed to the script. Must not be {@literal null}.
	 * @param args any args that need to be passed to the script. Can be {@literal empty}.
	 * @return The return value of the script or {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is
	 *         {@literal null}, likely indicating a throw-away status reply (i.e. "OK")
	 * @since 3.4
	 */
	default <T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, Object... args) {
		return execute(script, keys, Arrays.asList(args));
	}

	/**
	 * Executes the given {@link ValkeyScript}
	 *
	 * @param script The script to execute. Must not be {@literal null}.
	 * @param keys any keys that need to be passed to the script. Must not be {@literal null}.
	 * @param args any args that need to be passed to the script. Can be {@literal empty}.
	 * @return The return value of the script or {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is
	 *         {@literal null}, likely indicating a throw-away status reply (i.e. "OK")
	 */
	<T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, List<?> args);

	/**
	 * Executes the given {@link ValkeyScript}, using the provided {@link ValkeySerializer}s to serialize the script
	 * arguments and result.
	 *
	 * @param script The script to execute. must not be {@literal null}.
	 * @param keys any keys that need to be passed to the script.
	 * @param args any args that need to be passed to the script.
	 * @param argsWriter The {@link ValkeyElementWriter} to use for serializing args. Must not be {@literal null}.
	 * @param resultReader The {@link ValkeyElementReader} to use for serializing the script return value. Must not be
	 *          {@literal null}.
	 * @return The return value of the script or {@link Flux#empty()} if {@link ValkeyScript#getResultType()} is
	 *         {@literal null}, likely indicating a throw-away status reply (i.e. "OK")
	 */
	<T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, List<?> args, ValkeyElementWriter<?> argsWriter,
			ValkeyElementReader<T> resultReader);
}
