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

package io.valkey.springframework.boot.autoconfigure.data.valkey;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails.Cluster;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails.Node;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails.Sentinel;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyProperties.Pool;
import org.springframework.boot.ssl.SslBundle;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeyPassword;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import org.springframework.util.ClassUtils;

/**
 * Base Valkey connection configuration.
 *
 * @author Mark Paluch
 * @author Stephane Nicoll
 * @author Alen Turkovic
 * @author Scott Frederick
 * @author Eddú Meléndez
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Yanming Zhou
 */
abstract class ValkeyConnectionConfiguration {

	private static final boolean COMMONS_POOL2_AVAILABLE = ClassUtils.isPresent("org.apache.commons.pool2.ObjectPool",
			ValkeyConnectionConfiguration.class.getClassLoader());

	private final ValkeyProperties properties;

	private final ValkeyStandaloneConfiguration standaloneConfiguration;

	private final ValkeySentinelConfiguration sentinelConfiguration;

	private final ValkeyClusterConfiguration clusterConfiguration;

	private final ValkeyConnectionDetails connectionDetails;

	protected final Mode mode;

	protected ValkeyConnectionConfiguration(ValkeyProperties properties, ValkeyConnectionDetails connectionDetails,
			ObjectProvider<ValkeyStandaloneConfiguration> standaloneConfigurationProvider,
			ObjectProvider<ValkeySentinelConfiguration> sentinelConfigurationProvider,
			ObjectProvider<ValkeyClusterConfiguration> clusterConfigurationProvider) {
		this.properties = properties;
		this.standaloneConfiguration = standaloneConfigurationProvider.getIfAvailable();
		this.sentinelConfiguration = sentinelConfigurationProvider.getIfAvailable();
		this.clusterConfiguration = clusterConfigurationProvider.getIfAvailable();
		this.connectionDetails = connectionDetails;
		this.mode = determineMode();
	}

	protected final ValkeyStandaloneConfiguration getStandaloneConfig() {
		if (this.standaloneConfiguration != null) {
			return this.standaloneConfiguration;
		}
		ValkeyStandaloneConfiguration config = new ValkeyStandaloneConfiguration();
		config.setHostName(this.connectionDetails.getStandalone().getHost());
		config.setPort(this.connectionDetails.getStandalone().getPort());
		config.setUsername(this.connectionDetails.getUsername());
		config.setPassword(ValkeyPassword.of(this.connectionDetails.getPassword()));
		config.setDatabase(this.connectionDetails.getStandalone().getDatabase());
		return config;
	}

	protected final ValkeySentinelConfiguration getSentinelConfig() {
		if (this.sentinelConfiguration != null) {
			return this.sentinelConfiguration;
		}
		if (this.connectionDetails.getSentinel() != null) {
			ValkeySentinelConfiguration config = new ValkeySentinelConfiguration();
			config.master(this.connectionDetails.getSentinel().getMaster());
			config.setSentinels(createSentinels(this.connectionDetails.getSentinel()));
			config.setUsername(this.connectionDetails.getUsername());
			String password = this.connectionDetails.getPassword();
			if (password != null) {
				config.setPassword(ValkeyPassword.of(password));
			}
			config.setSentinelUsername(this.connectionDetails.getSentinel().getUsername());
			String sentinelPassword = this.connectionDetails.getSentinel().getPassword();
			if (sentinelPassword != null) {
				config.setSentinelPassword(ValkeyPassword.of(sentinelPassword));
			}
			config.setDatabase(this.connectionDetails.getSentinel().getDatabase());
			return config;
		}
		return null;
	}

	/**
	 * Create a {@link ValkeyClusterConfiguration} if necessary.
	 * @return {@literal null} if no cluster settings are set.
	 */
	protected final ValkeyClusterConfiguration getClusterConfiguration() {
		if (this.clusterConfiguration != null) {
			return this.clusterConfiguration;
		}
		ValkeyProperties.Cluster clusterProperties = this.properties.getCluster();
		if (this.connectionDetails.getCluster() != null) {
			ValkeyClusterConfiguration config = new ValkeyClusterConfiguration();
			config.setClusterNodes(getNodes(this.connectionDetails.getCluster()));
			if (clusterProperties != null && clusterProperties.getMaxRedirects() != null) {
				config.setMaxRedirects(clusterProperties.getMaxRedirects());
			}
			config.setUsername(this.connectionDetails.getUsername());
			String password = this.connectionDetails.getPassword();
			if (password != null) {
				config.setPassword(ValkeyPassword.of(password));
			}
			return config;
		}
		return null;
	}

	private List<ValkeyNode> getNodes(Cluster cluster) {
		return cluster.getNodes().stream().map(this::asValkeyNode).toList();
	}

	private ValkeyNode asValkeyNode(Node node) {
		return new ValkeyNode(node.host(), node.port());
	}

	protected final ValkeyProperties getProperties() {
		return this.properties;
	}

	protected SslBundle getSslBundle() {
		return switch (this.mode) {
			case STANDALONE -> (this.connectionDetails.getStandalone() != null)
					? this.connectionDetails.getStandalone().getSslBundle() : null;
			case CLUSTER -> (this.connectionDetails.getCluster() != null)
					? this.connectionDetails.getCluster().getSslBundle() : null;
			case SENTINEL -> (this.connectionDetails.getSentinel() != null)
					? this.connectionDetails.getSentinel().getSslBundle() : null;
		};
	}

	protected final boolean isSslEnabled() {
		return getProperties().getSsl().isEnabled();
	}

	protected final boolean urlUsesSsl() {
		return ValkeyUrl.of(this.properties.getUrl()).useSsl();
	}

	protected boolean isPoolEnabled(Pool pool) {
		Boolean enabled = pool.getEnabled();
		return (enabled != null) ? enabled : COMMONS_POOL2_AVAILABLE;
	}

	private List<ValkeyNode> createSentinels(Sentinel sentinel) {
		List<ValkeyNode> nodes = new ArrayList<>();
		for (Node node : sentinel.getNodes()) {
			nodes.add(asValkeyNode(node));
		}
		return nodes;
	}

	protected final ValkeyConnectionDetails getConnectionDetails() {
		return this.connectionDetails;
	}

	private Mode determineMode() {
		if (getSentinelConfig() != null) {
			return Mode.SENTINEL;
		}
		if (getClusterConfiguration() != null) {
			return Mode.CLUSTER;
		}
		return Mode.STANDALONE;
	}

	enum Mode {

		STANDALONE, CLUSTER, SENTINEL

	}

}
