/*
 * Copyright 2012-2025 the original author or authors.
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

package io.valkey.springframework.boot.actuate.data.valkey;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.util.Assert;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyConnectionUtils;

/**
 * Simple implementation of a {@link HealthIndicator} returning status information for
 * Valkey data stores.
 *
 * @author Christian Dupuis
 * @author Richard Santana
 * @author Scott Frederick
 * @since 2.0.0
 */
public class ValkeyHealthIndicator extends AbstractHealthIndicator {

	private final ValkeyConnectionFactory valkeyConnectionFactory;

	public ValkeyHealthIndicator(ValkeyConnectionFactory connectionFactory) {
		super("Valkey health check failed");
		Assert.notNull(connectionFactory, "'connectionFactory' must not be null");
		this.valkeyConnectionFactory = connectionFactory;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		ValkeyConnection connection = ValkeyConnectionUtils.getConnection(this.valkeyConnectionFactory);
		try {
			doHealthCheck(builder, connection);
		}
		finally {
			ValkeyConnectionUtils.releaseConnection(connection, this.valkeyConnectionFactory);
		}
	}

	private void doHealthCheck(Health.Builder builder, ValkeyConnection connection) {
		if (connection instanceof ValkeyClusterConnection clusterConnection) {
			ValkeyHealth.fromClusterInfo(builder, clusterConnection.clusterGetClusterInfo());
		}
		else {
			ValkeyHealth.up(builder, connection.serverCommands().info());
		}
	}

}
