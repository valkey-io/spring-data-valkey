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
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.ValkeySystemException;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ReturnType;
import io.valkey.springframework.data.valkey.core.ReactiveValkeyCallback;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader;
import io.valkey.springframework.data.valkey.serializer.ValkeyElementWriter;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext.SerializationPair;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link ReactiveScriptExecutor}. Optimizes performance by attempting to execute script first
 * using {@code EVALSHA}, then falling back to {@code EVAL} if Valkey has not yet cached the script.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author John Blum
 * @param <K> The type of keys that may be passed during script execution
 * @since 2.0
 */
public class DefaultReactiveScriptExecutor<K> implements ReactiveScriptExecutor<K> {

	private final ReactiveValkeyConnectionFactory connectionFactory;
	private final ValkeySerializationContext<K, ?> serializationContext;

	/**
	 * Creates a new {@link DefaultReactiveScriptExecutor} given {@link ReactiveValkeyConnectionFactory} and
	 * {@link ValkeySerializationContext}.
	 *
	 * @param connectionFactory must not be {@literal null}.
	 * @param serializationContext must not be {@literal null}.
	 */
	public DefaultReactiveScriptExecutor(ReactiveValkeyConnectionFactory connectionFactory,
			ValkeySerializationContext<K, ?> serializationContext) {

		Assert.notNull(connectionFactory, "ReactiveValkeyConnectionFactory must not be null");
		Assert.notNull(serializationContext, "ValkeySerializationContext must not be null");

		this.connectionFactory = connectionFactory;
		this.serializationContext = serializationContext;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, List<?> args) {

		Assert.notNull(script, "ValkeyScript must not be null");
		Assert.notNull(keys, "Keys must not be null");
		Assert.notNull(args, "Args must not be null");

		SerializationPair<?> serializationPair = serializationContext.getValueSerializationPair();

		return execute(script, keys, args, serializationPair.getWriter(),
				(ValkeyElementReader<T>) serializationPair.getReader());
	}

	@Override
	public <T> Flux<T> execute(ValkeyScript<T> script, List<K> keys, List<?> args, ValkeyElementWriter<?> argsWriter,
			ValkeyElementReader<T> resultReader) {

		Assert.notNull(script, "ValkeyScript must not be null");
		Assert.notNull(argsWriter, "Argument Writer must not be null");
		Assert.notNull(resultReader, "Result Reader must not be null");
		Assert.notNull(keys, "Keys must not be null");
		Assert.notNull(args, "Args must not be null");

		return execute(connection -> {

			ReturnType returnType = ReturnType.fromJavaType(script.getResultType());
			ByteBuffer[] keysAndArgs = keysAndArgs(argsWriter, keys, args);
			int keySize = keys.size();

			return eval(connection, script, returnType, keySize, keysAndArgs, resultReader);

		});
	}

	protected <T> Flux<T> eval(ReactiveValkeyConnection connection, ValkeyScript<T> script, ReturnType returnType,
			int numKeys, ByteBuffer[] keysAndArgs, ValkeyElementReader<T> resultReader) {

		Flux<T> result = connection.scriptingCommands().evalSha(script.getSha1(), returnType, numKeys, keysAndArgs);

		result = result.onErrorResume(cause -> {

			if (ScriptUtils.exceptionContainsNoScriptError(cause)) {
				return connection.scriptingCommands().eval(scriptBytes(script), returnType, numKeys, keysAndArgs);
			}

			return Flux.error(cause instanceof RuntimeException ? cause
					: new ValkeySystemException(cause.getMessage(), cause));
		});

		return script.returnsRawValue() ? result : deserializeResult(resultReader, result);
	}

	@SuppressWarnings({ "Convert2MethodRef", "rawtypes", "unchecked" })
	protected ByteBuffer[] keysAndArgs(ValkeyElementWriter argsWriter, List<K> keys, List<?> args) {

		return Stream.concat(keys.stream().map(t -> keySerializer().getWriter().write(t)),
				args.stream().map(t -> argsWriter.write(t))).toArray(size -> new ByteBuffer[size]);
	}

	protected ByteBuffer scriptBytes(ValkeyScript<?> script) {
		return serializationContext.getStringSerializationPair().getWriter().write(script.getScriptAsString());
	}

	protected <T> Flux<T> deserializeResult(ValkeyElementReader<T> reader, Flux<T> result) {

		return result.map(it -> {

			T value = ScriptUtils.deserializeResult(reader, it);

			if (value != null) {
				return value;
			}

			throw new InvalidDataAccessApiUsageException("Deserialized script result is null");
		});
	}

	protected SerializationPair<K> keySerializer() {
		return serializationContext.getKeySerializationPair();
	}

	/**
	 * Executes the given action object within a connection that is allocated eagerly and released after {@link Flux}
	 * termination.
	 *
	 * @param <T> return type
	 * @param action callback object to execute
	 * @return object returned by the action
	 */
	private <T> Flux<T> execute(ReactiveValkeyCallback<T> action) {

		Assert.notNull(action, "Callback object must not be null");

		ReactiveValkeyConnectionFactory factory = getConnectionFactory();

		return Flux.usingWhen(Mono.fromSupplier(factory::getReactiveConnection), action::doInValkey,
				ReactiveValkeyConnection::closeLater);
	}

	public ReactiveValkeyConnectionFactory getConnectionFactory() {
		return this.connectionFactory;
	}
}
