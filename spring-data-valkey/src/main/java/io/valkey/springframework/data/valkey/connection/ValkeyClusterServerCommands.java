/*
 * Copyright 2017-present the original author or authors.
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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;

/**
 * @author Mark Paluch
 * @author Dennis Neufeld
 * @since 2.0
 */
@NullUnmarked
public interface ValkeyClusterServerCommands extends ValkeyServerCommands {

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#bgReWriteAof()
	 */
	void bgReWriteAof(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#bgSave()
	 */
	void bgSave(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#lastSave()
	 */
	Long lastSave(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#save()
	 */
	void save(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#dbSize()
	 */
	Long dbSize(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#flushDb()
	 */
	void flushDb(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param option
	 * @see ValkeyServerCommands#flushDb(FlushOption)
	 * @since 2.7
	 */
	void flushDb(@NonNull ValkeyClusterNode node, @NonNull FlushOption option);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#flushAll()
	 */
	void flushAll(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param option
	 * @see ValkeyServerCommands#flushAll(FlushOption)
	 * @since 2.7
	 */
	void flushAll(@NonNull ValkeyClusterNode node, @NonNull FlushOption option);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#info()
	 */
	Properties info(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param section
	 * @return
	 * @see ValkeyServerCommands#info(String)
	 */
	Properties info(@NonNull ValkeyClusterNode node, @NonNull String section);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#shutdown()
	 */
	void shutdown(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param pattern
	 * @return
	 * @see ValkeyServerCommands#getConfig(String)
	 */
	Properties getConfig(@NonNull ValkeyClusterNode node, @NonNull String pattern);

	/**
	 * @param node must not be {@literal null}.
	 * @param param
	 * @param value
	 * @see ValkeyServerCommands#setConfig(String, String)
	 */
	void setConfig(@NonNull ValkeyClusterNode node, @NonNull String param, @NonNull String value);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#resetConfigStats()
	 */
	void resetConfigStats(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#rewriteConfig()
	 * @since 2.5
	 */
	void rewriteConfig(@NonNull ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#time()
	 */
	default Long time(@NonNull ValkeyClusterNode node) {
		return time(node, TimeUnit.MILLISECONDS);
	}

	/**
	 * @param node must not be {@literal null}.
	 * @param timeUnit must not be {@literal null}.
	 * @return
	 * @since 2.5
	 * @see ValkeyServerCommands#time(TimeUnit)
	 */
	Long time(@NonNull ValkeyClusterNode node, @NonNull TimeUnit timeUnit);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#getClientList()
	 */
	List<@NonNull ValkeyClientInfo> getClientList(@NonNull ValkeyClusterNode node);
}
