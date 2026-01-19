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
package io.valkey.springframework.data.valkey.connection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Properties;

import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.FlushOption;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;

/**
 * Valkey Server commands executed in cluster environment using reactive infrastructure.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Dennis Neufeld
 * @since 2.0
 */
public interface ReactiveClusterServerCommands extends ReactiveServerCommands {

	/**
	 * Start an {@literal Append Only File} rewrite process on the specific server.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} indicating command completion.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#bgReWriteAof()
	 */
	Mono<String> bgReWriteAof(ValkeyClusterNode node);

	/**
	 * Start background saving of db on server.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} indicating command received by server. Operation success needs to be checked via
	 *         {@link #lastSave(ValkeyClusterNode)}.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#bgSave()
	 */
	Mono<String> bgSave(ValkeyClusterNode node);

	/**
	 * Get time unix timestamp of last successful {@link #bgSave()} operation in seconds.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} wrapping unix timestamp.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#lastSave()
	 */
	Mono<Long> lastSave(ValkeyClusterNode node);

	/**
	 * Synchronous save current db snapshot on server.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} indicating command completion.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#save()
	 */
	Mono<String> save(ValkeyClusterNode node);

	/**
	 * Get the total number of available keys in currently selected database.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} wrapping number of keys.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#dbSize()
	 */
	Mono<Long> dbSize(ValkeyClusterNode node);

	/**
	 * Delete all keys of the currently selected database.
	 *
	 * @param node must not be {@literal null}. {@link Mono} indicating command completion.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#flushDb()
	 */
	Mono<String> flushDb(ValkeyClusterNode node);

	/**
	 * Delete all keys of the currently selected database using the specified {@link FlushOption}.
	 *
	 * @param node must not be {@literal null}. {@link Mono} indicating command completion.
	 * @param option
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#flushDb(FlushOption)
	 * @since 2.7
	 */
	Mono<String> flushDb(ValkeyClusterNode node, FlushOption option);

	/**
	 * Delete all <b>all keys</b> from <b>all databases</b>.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} indicating command completion.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#flushAll()
	 */
	Mono<String> flushAll(ValkeyClusterNode node);

	/**
	 * Delete all <b>all keys</b> from <b>all databases</b> using the specified {@link FlushOption}.
	 *
	 * @param node must not be {@literal null}.
	 * @param option
	 * @return {@link Mono} indicating command completion.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#flushAll(FlushOption)
	 * @since 2.7
	 */
	Mono<String> flushAll(ValkeyClusterNode node, FlushOption option);

	/**
	 * Load {@literal default} server information like
	 * <ul>
	 * <li>memory</li>
	 * <li>cpu utilization</li>
	 * <li>replication</li>
	 * </ul>
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} wrapping server information.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#info()
	 */
	Mono<Properties> info(ValkeyClusterNode node);

	/**
	 * Load server information for given {@code selection}.
	 *
	 * @param node must not be {@literal null}.
	 * @param section must not be {@literal null} nor {@literal empty}.
	 * @return {@link Mono} wrapping server information of given {@code section}.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @throws IllegalArgumentException when section is {@literal null} or {@literal empty}.
	 * @see ValkeyServerCommands#info(String)
	 */
	Mono<Properties> info(ValkeyClusterNode node, String section);

	/**
	 * Load configuration parameters for given {@code pattern} from server.
	 *
	 * @param node must not be {@literal null}.
	 * @param pattern must not be {@literal null}.
	 * @return {@link Mono} wrapping configuration parameters matching given {@code pattern}.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @throws IllegalArgumentException when {@code pattern} is {@literal null} or {@literal empty}.
	 * @see ValkeyServerCommands#getConfig(String)
	 */
	Mono<Properties> getConfig(ValkeyClusterNode node, String pattern);

	/**
	 * Set server configuration for {@code param} to {@code value}.
	 *
	 * @param node must not be {@literal null}.
	 * @param param must not be {@literal null} nor {@literal empty}.
	 * @param value must not be {@literal null} nor {@literal empty}.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @throws IllegalArgumentException when {@code pattern} / {@code value} is {@literal null} or {@literal empty}.
	 * @see ValkeyServerCommands#setConfig(String, String)
	 */
	Mono<String> setConfig(ValkeyClusterNode node, String param, String value);

	/**
	 * Reset statistic counters on server. <br>
	 * Counters can be retrieved using {@link #info()}.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} indicating command completion.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#resetConfigStats()
	 */
	Mono<String> resetConfigStats(ValkeyClusterNode node);

	/**
	 * Request server timestamp using {@code TIME} command.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Mono} wrapping current server time in milliseconds.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#time()
	 */
	Mono<Long> time(ValkeyClusterNode node);

	/**
	 * Request information and statistics about connected clients.
	 *
	 * @param node must not be {@literal null}.
	 * @return {@link Flux} emitting {@link ValkeyClientInfo} objects.
	 * @throws IllegalArgumentException when {@code node} is {@literal null}.
	 * @see ValkeyServerCommands#getClientList()
	 */
	Flux<ValkeyClientInfo> getClientList(ValkeyClusterNode node);
}
