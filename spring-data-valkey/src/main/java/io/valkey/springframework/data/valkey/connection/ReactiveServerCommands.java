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
import java.util.concurrent.TimeUnit;

import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.FlushOption;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;

/**
 * Valkey Server commands executed using reactive infrastructure.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Dennis Neufeld
 * @since 2.0
 */
public interface ReactiveServerCommands {

	/**
	 * Start an {@literal Append Only File} rewrite process on server.
	 *
	 * @return {@link Mono} indicating command completion.
	 * @see <a href="https://valkey.io/commands/bgrewriteaof">Valkey Documentation: BGREWRITEAOF</a>
	 */
	Mono<String> bgReWriteAof();

	/**
	 * Start background saving of db on server.
	 *
	 * @return {@link Mono} indicating command received by server. Operation success needs to be checked via
	 *         {@link #lastSave()}.
	 * @see <a href="https://valkey.io/commands/bgsave">Valkey Documentation: BGSAVE</a>
	 */
	Mono<String> bgSave();

	/**
	 * Get time unix timestamp of last successful {@link #bgSave()} operation in seconds.
	 *
	 * @return {@link Mono} wrapping unix timestamp.
	 * @see <a href="https://valkey.io/commands/lastsave">Valkey Documentation: LASTSAVE</a>
	 */
	Mono<Long> lastSave();

	/**
	 * Synchronous save current db snapshot on server.
	 *
	 * @return {@link Mono} indicating command completion.
	 * @see <a href="https://valkey.io/commands/save">Valkey Documentation: SAVE</a>
	 */
	Mono<String> save();

	/**
	 * Get the total number of available keys in currently selected database.
	 *
	 * @return {@link Mono} wrapping number of keys.
	 * @see <a href="https://valkey.io/commands/dbsize">Valkey Documentation: DBSIZE</a>
	 */
	Mono<Long> dbSize();

	/**
	 * Delete all keys of the currently selected database.
	 *
	 * @return {@link Mono} indicating command completion.
	 * @see <a href="https://valkey.io/commands/flushdb">Valkey Documentation: FLUSHDB</a>
	 */
	Mono<String> flushDb();

	/**
	 * Delete all keys of the currently selected database using the specified {@link FlushOption}.
	 *
	 * @param option
	 * @return {@link Mono} indicating command completion.
	 * @see <a href="https://valkey.io/commands/flushdb">Valkey Documentation: FLUSHDB</a>
	 * @since 2.7
	 */
	Mono<String> flushDb(FlushOption option);

	/**
	 * Delete all <b>all keys</b> from <b>all databases</b>.
	 *
	 * @return {@link Mono} indicating command completion.
	 * @see <a href="https://valkey.io/commands/flushall">Valkey Documentation: FLUSHALL</a>
	 */
	Mono<String> flushAll();

	/**
	 * Delete all <b>all keys</b> from <b>all databases</b> using the specified {@link FlushOption}.
	 *
	 * @param option
	 * @return {@link Mono} indicating command completion.
	 * @see <a href="https://valkey.io/commands/flushall">Valkey Documentation: FLUSHALL</a>
	 * @since 2.7
	 */
	Mono<String> flushAll(FlushOption option);

	/**
	 * Load {@literal default} server information like
	 * <ul>
	 * <li>memory</li>
	 * <li>cpu utilization</li>
	 * <li>replication</li>
	 * </ul>
	 *
	 * @return {@link Mono} wrapping server information.
	 * @see <a href="https://valkey.io/commands/info">Valkey Documentation: INFO</a>
	 */
	Mono<Properties> info();

	/**
	 * Load server information for given {@code selection}.
	 *
	 * @param section must not be {@literal null} nor {@literal empty}.
	 * @return {@link Mono} wrapping server information of given {@code section}.
	 * @throws IllegalArgumentException when section is {@literal null} or {@literal empty}.
	 * @see <a href="https://valkey.io/commands/info">Valkey Documentation: INFO</a>
	 */
	Mono<Properties> info(String section);

	/**
	 * Load configuration parameters for given {@code pattern} from server.
	 *
	 * @param pattern must not be {@literal null}.
	 * @return {@link Mono} wrapping configuration parameters matching given {@code pattern}.
	 * @throws IllegalArgumentException when {@code pattern} is {@literal null} or {@literal empty}.
	 * @see <a href="https://valkey.io/commands/config-get">Valkey Documentation: CONFIG GET</a>
	 */
	Mono<Properties> getConfig(String pattern);

	/**
	 * Set server configuration for {@code param} to {@code value}.
	 *
	 * @param param must not be {@literal null} nor {@literal empty}.
	 * @param value must not be {@literal null} nor {@literal empty}.
	 * @throws IllegalArgumentException when {@code pattern} / {@code value} is {@literal null} or {@literal empty}.
	 * @see <a href="https://valkey.io/commands/config-set">Valkey Documentation: CONFIG SET</a>
	 */
	Mono<String> setConfig(String param, String value);

	/**
	 * Reset statistic counters on server. <br>
	 * Counters can be retrieved using {@link #info()}.
	 *
	 * @return {@link Mono} indicating command completion.
	 * @see <a href="https://valkey.io/commands/config-resetstat">Valkey Documentation: CONFIG RESETSTAT</a>
	 */
	Mono<String> resetConfigStats();

	/**
	 * Request server timestamp using {@code TIME} command in {@link TimeUnit#MILLISECONDS}.
	 *
	 * @return {@link Mono} wrapping current server time in milliseconds.
	 * @see <a href="https://valkey.io/commands/time">Valkey Documentation: TIME</a>
	 */
	default Mono<Long> time() {
		return time(TimeUnit.MILLISECONDS);
	}

	/**
	 * Request server timestamp using {@code TIME} command.
	 *
	 * @param timeUnit target unit.
	 * @return {@link Mono} wrapping current server time in {@link TimeUnit}.
	 * @since 2.5
	 * @see <a href="https://valkey.io/commands/time">Valkey Documentation: TIME</a>
	 */
	Mono<Long> time(TimeUnit timeUnit);

	/**
	 * Closes a given client connection identified by {@literal host:port}.
	 *
	 * @param host of connection to close. Must not be {@literal null} nor {@literal empty}.
	 * @param port of connection to close
	 * @return {@link Mono} wrapping {@link String} representation of the command result.
	 * @throws IllegalArgumentException if {@code host} is {@literal null} or {@literal empty}.
	 * @see <a href="https://valkey.io/commands/client-kill">Valkey Documentation: CLIENT KILL</a>
	 */
	Mono<String> killClient(String host, int port);

	/**
	 * Assign given name to current connection.
	 *
	 * @param name must not be {@literal null} nor {@literal empty}.
	 * @throws IllegalArgumentException when {@code name} is {@literal null} or {@literal empty}.
	 * @see <a href="https://valkey.io/commands/client-setname">Valkey Documentation: CLIENT SETNAME</a>
	 */
	Mono<String> setClientName(String name);

	/**
	 * Returns the name of the current connection.
	 *
	 * @return {@link Mono} wrapping the connection name.
	 * @see <a href="https://valkey.io/commands/client-getname">Valkey Documentation: CLIENT GETNAME</a>
	 */
	Mono<String> getClientName();

	/**
	 * Request information and statistics about connected clients.
	 *
	 * @return {@link Flux} emitting {@link ValkeyClientInfo} objects.
	 * @see <a href="https://valkey.io/commands/client-list">Valkey Documentation: CLIENT LIST</a>
	 */
	Flux<ValkeyClientInfo> getClientList();
}
