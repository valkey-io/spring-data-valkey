/*
 * Copyright 2011-2025 the original author or authors.
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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;
import org.springframework.lang.Nullable;

/**
 * Server-specific commands supported by Valkey.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author Dennis Neufeld
 */
public interface ValkeyServerCommands {

	enum ShutdownOption {
		SAVE, NOSAVE;
	}

	/**
	 * @since 1.7
	 */
	enum MigrateOption {
		COPY, REPLACE
	}

	/**
	 * @since 2.7
	 */
	enum FlushOption {
		SYNC, ASYNC
	}

	/**
	 * Start an {@literal Append Only File} rewrite process on server.
	 *
	 * @since 1.3
	 * @see <a href="https://valkey.io/commands/bgrewriteaof">Valkey Documentation: BGREWRITEAOF</a>
	 */
	void bgReWriteAof();

	/**
	 * Start background saving of db on server.
	 *
	 * @see <a href="https://valkey.io/commands/bgsave">Valkey Documentation: BGSAVE</a>
	 */
	void bgSave();

	/**
	 * Get time of last {@link #bgSave()} operation in seconds.
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/lastsave">Valkey Documentation: LASTSAVE</a>
	 */
	@Nullable
	Long lastSave();

	/**
	 * Synchronous save current db snapshot on server.
	 *
	 * @see <a href="https://valkey.io/commands/save">Valkey Documentation: SAVE</a>
	 */
	void save();

	/**
	 * Get the total number of available keys in currently selected database.
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/dbsize">Valkey Documentation: DBSIZE</a>
	 */
	@Nullable
	Long dbSize();

	/**
	 * Delete all keys of the currently selected database.
	 *
	 * @see <a href="https://valkey.io/commands/flushdb">Valkey Documentation: FLUSHDB</a>
	 */
	void flushDb();

	/**
	 * Delete all keys of the currently selected database using the specified {@link FlushOption}.
	 *
	 * @param option
	 * @see <a href="https://valkey.io/commands/flushdb">Valkey Documentation: FLUSHDB</a>
	 * @since 2.7
	 */
	void flushDb(FlushOption option);

	/**
	 * Delete all <b>all keys</b> from <b>all databases</b>.
	 *
	 * @see <a href="https://valkey.io/commands/flushall">Valkey Documentation: FLUSHALL</a>
	 */
	void flushAll();

	/**
	 * Delete all <b>all keys</b> from <b>all databases</b> using the specified {@link FlushOption}.
	 *
	 * @param option
	 * @see <a href="https://valkey.io/commands/flushall">Valkey Documentation: FLUSHALL</a>
	 * @since 2.7
	 */
	void flushAll(FlushOption option);

	/**
	 * Load {@literal default} server information like
	 * <ul>
	 * <li>memory</li>
	 * <li>cpu utilization</li>
	 * <li>replication</li>
	 * </ul>
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/info">Valkey Documentation: INFO</a>
	 */
	@Nullable
	Properties info();

	/**
	 * Load server information for given {@code selection}.
	 *
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/info">Valkey Documentation: INFO</a>
	 */
	@Nullable
	Properties info(String section);

	/**
	 * Shutdown server.
	 *
	 * @see <a href="https://valkey.io/commands/shutdown">Valkey Documentation: SHUTDOWN</a>
	 */
	void shutdown();

	/**
	 * Shutdown server.
	 *
	 * @see <a href="https://valkey.io/commands/shutdown">Valkey Documentation: SHUTDOWN</a>
	 * @since 1.3
	 */
	void shutdown(ShutdownOption option);

	/**
	 * Load configuration parameters for given {@code pattern} from server.
	 *
	 * @param pattern must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/config-get">Valkey Documentation: CONFIG GET</a>
	 */
	@Nullable
	Properties getConfig(String pattern);

	/**
	 * Set server configuration for {@code param} to {@code value}.
	 *
	 * @param param must not be {@literal null}.
	 * @param value must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/config-set">Valkey Documentation: CONFIG SET</a>
	 */
	void setConfig(String param, String value);

	/**
	 * Reset statistic counters on server. <br>
	 * Counters can be retrieved using {@link #info()}.
	 *
	 * @see <a href="https://valkey.io/commands/config-resetstat">Valkey Documentation: CONFIG RESETSTAT</a>
	 */
	void resetConfigStats();

	/**
	 * Rewrites the {@code valkey.conf} file.
	 *
	 * @since 2.5
	 * @see <a href="https://valkey.io/commands/config-rewrite">Valkey Documentation: CONFIG REWRITE</a>
	 */
	void rewriteConfig();

	/**
	 * Request server timestamp using {@code TIME} command in {@link TimeUnit#MILLISECONDS}.
	 *
	 * @return current server time in milliseconds or {@literal null} when used in pipeline / transaction.
	 * @since 1.1
	 * @see <a href="https://valkey.io/commands/time">Valkey Documentation: TIME</a>
	 */
	@Nullable
	default Long time() {
		return time(TimeUnit.MILLISECONDS);
	}

	/**
	 * Request server timestamp using {@code TIME} command.
	 *
	 * @param timeUnit target unit.
	 * @return current server time in {@link TimeUnit} or {@literal null} when used in pipeline / transaction.
	 * @since 2.5
	 * @see <a href="https://valkey.io/commands/time">Valkey Documentation: TIME</a>
	 */
	@Nullable
	Long time(TimeUnit timeUnit);

	/**
	 * Closes a given client connection identified by {@literal host:port}.
	 *
	 * @param host of connection to close.
	 * @param port of connection to close
	 * @since 1.3
	 * @see <a href="https://valkey.io/commands/client-kill">Valkey Documentation: CLIENT KILL</a>
	 */
	void killClient(String host, int port);

	/**
	 * Assign given name to current connection.
	 *
	 * @param name
	 * @since 1.3
	 * @see <a href="https://valkey.io/commands/client-setname">Valkey Documentation: CLIENT SETNAME</a>
	 */
	void setClientName(byte[] name);

	/**
	 * Returns the name of the current connection.
	 *
	 * @see <a href="https://valkey.io/commands/client-getname">Valkey Documentation: CLIENT GETNAME</a>
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 1.3
	 */
	@Nullable
	String getClientName();

	/**
	 * Request information and statistics about connected clients.
	 *
	 * @return {@link List} of {@link ValkeyClientInfo} objects or {@literal null} when used in pipeline / transaction.
	 * @since 1.3
	 * @see <a href="https://valkey.io/commands/client-list">Valkey Documentation: CLIENT LIST</a>
	 */
	@Nullable
	List<ValkeyClientInfo> getClientList();

	/**
	 * Change valkey replication setting to new master.
	 *
	 * @param host must not be {@literal null}.
	 * @param port
	 * @since 3.0
	 * @see <a href="https://valkey.io/commands/replicaof">Valkey Documentation: REPLICAOF</a>
	 */
	void replicaOf(String host, int port);

	/**
	 * Change server into master.
	 *
	 * @since 1.3
	 * @see <a href="https://valkey.io/commands/replicaof">Valkey Documentation: REPLICAOF</a>
	 */
	void replicaOfNoOne();

	/**
	 * Atomically transfer a key from a source Valkey instance to a destination Valkey instance. On success the key is
	 * deleted from the original instance and is guaranteed to exist in the target instance.
	 *
	 * @param key must not be {@literal null}.
	 * @param target must not be {@literal null}.
	 * @param dbIndex
	 * @param option can be {@literal null}. Defaulted to {@link MigrateOption#COPY}.
	 * @since 1.7
	 * @see <a href="https://valkey.io/commands/migrate">Valkey Documentation: MIGRATE</a>
	 */
	void migrate(byte[] key, ValkeyNode target, int dbIndex, @Nullable MigrateOption option);

	/**
	 * Atomically transfer a key from a source Valkey instance to a destination Valkey instance. On success the key is
	 * deleted from the original instance and is guaranteed to exist in the target instance.
	 *
	 * @param key must not be {@literal null}.
	 * @param target must not be {@literal null}.
	 * @param dbIndex
	 * @param option can be {@literal null}. Defaulted to {@link MigrateOption#COPY}.
	 * @param timeout
	 * @since 1.7
	 * @see <a href="https://valkey.io/commands/migrate">Valkey Documentation: MIGRATE</a>
	 */
	void migrate(byte[] key, ValkeyNode target, int dbIndex, @Nullable MigrateOption option, long timeout);

}
