/*
 * Copyright 2019-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import io.valkey.springframework.data.valkey.connection.DataType
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection
import io.valkey.springframework.data.valkey.connection.ReactiveSubscription.Message
import io.valkey.springframework.data.valkey.core.script.ValkeyScript
import io.valkey.springframework.data.valkey.listener.Topic
import io.valkey.springframework.data.valkey.serializer.ValkeyElementReader
import io.valkey.springframework.data.valkey.serializer.ValkeyElementWriter
import java.time.Duration
import java.time.Instant

/**
 * Coroutines variant of [ReactiveValkeyOperations.execute].
 *
 * @author Sebastien Deleuze
 * @since 2.2
 */
fun <K : Any, V : Any, T : Any> ReactiveValkeyOperations<K, V>.executeAsFlow(action: (ReactiveValkeyConnection) -> Flow<T>): Flow<T> {
	return execute { action(it).asPublisher() }.asFlow()
}

/**
 * Coroutines variant of [ReactiveValkeyOperations.execute].
 *
 * @author Mark Paluch
 * @since 2.6
 */
fun <K : Any, V : Any, T : Any> ReactiveValkeyOperations<K, V>.executeInSessionAsFlow(
	action: (ReactiveValkeyOperations<K, V>) -> Flow<T>
): Flow<T> =
	executeInSession { action(it).asPublisher() }.asFlow()

/**
 * Coroutines variant of [ReactiveValkeyOperations.execute].
 *
 * @author Sebastien Deleuze
 * @since 2.2
 */
fun <K : Any, V : Any, T : Any> ReactiveValkeyOperations<K, V>.executeAsFlow(
	script: ValkeyScript<T>,
	keys: List<K> = emptyList(),
	args: List<*> = emptyList<Any>()
): Flow<T> =
	execute(script, keys, args).asFlow()

/**
 * Coroutines variant of [ReactiveValkeyOperations.execute].
 *
 * @author Sebastien Deleuze
 * @since 2.2
 */
fun <K : Any, V : Any, T : Any> ReactiveValkeyOperations<K, V>.executeAsFlow(script: ValkeyScript<T>, keys: List<K> = emptyList(), args: List<*> = emptyList<Any>(), argsWriter: ValkeyElementWriter<*>, resultReader: ValkeyElementReader<T>): Flow<T> =
		execute(script, keys, args, argsWriter, resultReader).asFlow()

/**
 * Coroutines variant of [ReactiveValkeyOperations.convertAndSend].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.sendAndAwait(destination: String, message: V): Long =
		convertAndSend(destination, message).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.listenToChannel].
 *
 * @author Sebastien Deleuze
 * @since 2.2
 */
fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.listenToChannelAsFlow(vararg channels: String): Flow<Message<String, V>> =
		listenToChannel(*channels).asFlow()

/**
 * Coroutines variant of [ReactiveValkeyOperations.listenToPattern].
 *
 * @author Sebastien Deleuze
 * @since 2.2
 */
fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.listenToPatternAsFlow(vararg patterns: String): Flow<Message<String, V>> =
		listenToPattern(*patterns).asFlow()

/**
 * Coroutines variant of [ReactiveValkeyOperations.listenTo].
 *
 * @author Sebastien Deleuze
 * @since 2.2
 */
fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.listenToAsFlow(vararg topics: Topic): Flow<Message<String, V>> =
		listenTo(*topics).asFlow()

/**
 * Coroutines variant of [ReactiveValkeyOperations.hasKey].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.hasKeyAndAwait(key: K): Boolean =
		hasKey(key).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.type].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.typeAndAwait(key: K): DataType =
		type(key).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.keys].
 *
 * @author Sebastien Deleuze
 * @since 2.2
 */
fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.keysAsFlow(pattern: K): Flow<K> =
		keys(pattern).asFlow()

/**
 * Coroutines variant of [ReactiveValkeyOperations.scan].
 *
 * @author Sebastien Deleuze
 * @since 2.2
 */
fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.scanAsFlow(options: ScanOptions = ScanOptions.NONE): Flow<K> =
		scan(options).asFlow()

/**
 * Coroutines variant of [ReactiveValkeyOperations.randomKey].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.randomKeyAndAwait(): K? =
		randomKey().awaitFirstOrNull()

/**
 * Coroutines variant of [ReactiveValkeyOperations.rename].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.renameAndAwait(oldKey: K, newKey: K): Boolean =
		rename(oldKey, newKey).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.renameIfAbsent].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.renameIfAbsentAndAwait(oldKey: K, newKey: K): Boolean =
		renameIfAbsent(oldKey, newKey).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.delete].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.deleteAndAwait(vararg key: K): Long =
		delete(*key).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.unlink].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.unlinkAndAwait(vararg key: K): Long =
		unlink(*key).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.expire].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.expireAndAwait(key: K, timeout: Duration): Boolean =
		expire(key, timeout).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.expireAt].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.expireAtAndAwait(key: K, expireAt: Instant): Boolean = expireAt(key, expireAt).awaitSingle()


/**
 * Coroutines variant of [ReactiveValkeyOperations.persist].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.persistAndAwait(key: K): Boolean =
		persist(key).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.move].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.moveAndAwait(key: K, dbIndex: Int): Boolean = move(key, dbIndex).awaitSingle()

/**
 * Coroutines variant of [ReactiveValkeyOperations.getExpire].
 *
 * @author Mark Paluch
 * @since 2.2
 */
suspend fun <K : Any, V : Any> ReactiveValkeyOperations<K, V>.getExpireAndAwait(key: K): Duration? = getExpire(key).awaitFirstOrNull()
