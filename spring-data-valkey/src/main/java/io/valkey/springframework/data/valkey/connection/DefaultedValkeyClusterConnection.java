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
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Dennis Neufeld
 * @since 2.0
 */
@Deprecated
public interface DefaultedValkeyClusterConnection
		extends DefaultedValkeyConnection, ValkeyClusterCommands, ValkeyClusterServerCommands, ValkeyClusterCommandsProvider {

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void bgReWriteAof(ValkeyClusterNode node) {
		serverCommands().bgReWriteAof(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void bgSave(ValkeyClusterNode node) {
		serverCommands().bgSave(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default Long lastSave(ValkeyClusterNode node) {
		return serverCommands().lastSave(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void save(ValkeyClusterNode node) {
		serverCommands().save(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default Long dbSize(ValkeyClusterNode node) {
		return serverCommands().dbSize(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void flushDb(ValkeyClusterNode node) {
		serverCommands().flushDb(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void flushDb(ValkeyClusterNode node, FlushOption option) {
		serverCommands().flushDb(node, option);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void flushAll(ValkeyClusterNode node) {
		serverCommands().flushAll(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void flushAll(ValkeyClusterNode node, FlushOption option) {
		serverCommands().flushAll(node, option);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default Properties info(ValkeyClusterNode node) {
		return serverCommands().info(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default Properties info(ValkeyClusterNode node, String section) {
		return serverCommands().info(node, section);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void shutdown(ValkeyClusterNode node) {
		serverCommands().shutdown(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default Properties getConfig(ValkeyClusterNode node, String pattern) {
		return serverCommands().getConfig(node, pattern);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void setConfig(ValkeyClusterNode node, String param, String value) {
		serverCommands().setConfig(node, param, value);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void resetConfigStats(ValkeyClusterNode node) {
		serverCommands().resetConfigStats(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default void rewriteConfig(ValkeyClusterNode node) {
		serverCommands().rewriteConfig(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default Long time(ValkeyClusterNode node) {
		return serverCommands().time(node);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default Long time(ValkeyClusterNode node, TimeUnit timeUnit) {
		return serverCommands().time(node, timeUnit);
	}

	/** @deprecated in favor of {@link ValkeyConnection#serverCommands()}. */
	@Override
	@Deprecated
	default List<ValkeyClientInfo> getClientList(ValkeyClusterNode node) {
		return serverCommands().getClientList(node);
	}

}
