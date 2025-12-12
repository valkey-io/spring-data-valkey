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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;

/**
 * @author Mark Paluch
 * @author Dennis Neufeld
 * @since 2.0
 */
public interface ValkeyClusterServerCommands extends ValkeyServerCommands {

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#bgReWriteAof()
	 */
	void bgReWriteAof(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#bgSave()
	 */
	void bgSave(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#lastSave()
	 */
	Long lastSave(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#save()
	 */
	void save(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#dbSize()
	 */
	Long dbSize(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#flushDb()
	 */
	void flushDb(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param option
	 * @see ValkeyServerCommands#flushDb(FlushOption)
	 * @since 2.7
	 */
	void flushDb(ValkeyClusterNode node, FlushOption option);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#flushAll()
	 */
	void flushAll(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param option
	 * @see ValkeyServerCommands#flushAll(FlushOption)
	 * @since 2.7
	 */
	void flushAll(ValkeyClusterNode node, FlushOption option);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#info()
	 */
	Properties info(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param section
	 * @return
	 * @see ValkeyServerCommands#info(String)
	 */
	Properties info(ValkeyClusterNode node, String section);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#shutdown()
	 */
	void shutdown(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param pattern
	 * @return
	 * @see ValkeyServerCommands#getConfig(String)
	 */
	Properties getConfig(ValkeyClusterNode node, String pattern);

	/**
	 * @param node must not be {@literal null}.
	 * @param param
	 * @param value
	 * @see ValkeyServerCommands#setConfig(String, String)
	 */
	void setConfig(ValkeyClusterNode node, String param, String value);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#resetConfigStats()
	 */
	void resetConfigStats(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @see ValkeyServerCommands#rewriteConfig()
	 * @since 2.5
	 */
	void rewriteConfig(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#time()
	 */
	default Long time(ValkeyClusterNode node) {
		return time(node, TimeUnit.MILLISECONDS);
	}

	/**
	 * @param node must not be {@literal null}.
	 * @param timeUnit must not be {@literal null}.
	 * @return
	 * @since 2.5
	 * @see ValkeyServerCommands#time(TimeUnit)
	 */
	Long time(ValkeyClusterNode node, TimeUnit timeUnit);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyServerCommands#getClientList()
	 */
	List<ValkeyClientInfo> getClientList(ValkeyClusterNode node);
}
