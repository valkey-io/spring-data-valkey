/*
 * Copyright 2014-2025 the original author or authors.
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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 1.4
 */
public abstract class AbstractValkeyConnection implements ValkeyConnection {

	private final Log LOGGER = LogFactory.getLog(getClass());

	private @Nullable ValkeySentinelConfiguration sentinelConfiguration;
	private final Map<ValkeyNode, ValkeySentinelConnection> connectionCache = new ConcurrentHashMap<>();

	@Override
	public ValkeySentinelConnection getSentinelConnection() {

		if (!hasValkeySentinelConfigured()) {
			throw new InvalidDataAccessResourceUsageException("No sentinels configured.");
		}

		ValkeyNode node = selectActiveSentinel();
		ValkeySentinelConnection connection = connectionCache.get(node);
		if (connection == null || !connection.isOpen()) {
			connection = getSentinelConnection(node);
			connectionCache.putIfAbsent(node, connection);
		}
		return connection;
	}

	public void setSentinelConfiguration(ValkeySentinelConfiguration sentinelConfiguration) {
		this.sentinelConfiguration = sentinelConfiguration;
	}

	public boolean hasValkeySentinelConfigured() {
		return this.sentinelConfiguration != null;
	}

	private ValkeyNode selectActiveSentinel() {

		Assert.state(hasValkeySentinelConfigured(), "Sentinel configuration missing");

		for (ValkeyNode node : this.sentinelConfiguration.getSentinels()) {
			if (isActive(node)) {
				return node;
			}
		}

		throw new InvalidDataAccessApiUsageException("Could not find any active sentinels");
	}

	/**
	 * Check if node is active by sending ping.
	 *
	 * @param node
	 * @return
	 */
	protected boolean isActive(ValkeyNode node) {
		return false;
	}

	/**
	 * Get {@link ValkeySentinelCommands} connected to given node.
	 *
	 * @param sentinel
	 * @return
	 */
	protected ValkeySentinelConnection getSentinelConnection(ValkeyNode sentinel) {
		throw new UnsupportedOperationException("Sentinel is not supported by this client.");
	}

	@Override
	public void close() throws DataAccessException {

		if (connectionCache.isEmpty()) {
			return;
		}

		for (ValkeyNode node : connectionCache.keySet()) {

			ValkeySentinelConnection connection = connectionCache.remove(node);

			if (!connection.isOpen()) {
				continue;
			}

			try {
				connection.close();
			} catch (IOException ex) {
				LOGGER.info("Failed to close sentinel connection", ex);
			}
		}
	}
}
