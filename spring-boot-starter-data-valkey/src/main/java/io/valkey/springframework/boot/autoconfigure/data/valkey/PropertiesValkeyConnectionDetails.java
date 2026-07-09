/*
 * Copyright 2012-present the original author or authors.
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

import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Adapts {@link ValkeyProperties} to {@link ValkeyConnectionDetails}.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Scott Frederick
 * @author Yanming Zhou
 * @author Phillip Webb
 */
class PropertiesValkeyConnectionDetails implements ValkeyConnectionDetails {

	private final ValkeyProperties properties;

	private final @Nullable SslBundles sslBundles;

	PropertiesValkeyConnectionDetails(ValkeyProperties properties, @Nullable SslBundles sslBundles) {
		this.properties = properties;
		this.sslBundles = sslBundles;
	}

	@Override
	public @Nullable String getUsername() {
		ValkeyUrl valkeyUrl = getValkeyUrl();
		return (valkeyUrl != null) ? valkeyUrl.credentials().username() : this.properties.getUsername();
	}

	@Override
	public @Nullable String getPassword() {
		ValkeyUrl valkeyUrl = getValkeyUrl();
		return (valkeyUrl != null) ? valkeyUrl.credentials().password() : this.properties.getPassword();
	}

	@Override
	public @Nullable SslBundle getSslBundle() {
		if (!this.properties.getSsl().isEnabled()) {
			return null;
		}
		String bundleName = this.properties.getSsl().getBundle();
		if (StringUtils.hasLength(bundleName)) {
			Assert.notNull(this.sslBundles, "SSL bundle name has been set but no SSL bundles found in context");
			return this.sslBundles.getBundle(bundleName);
		}
		return SslBundle.systemDefault();
	}

	@Override
	public Standalone getStandalone() {
		ValkeyUrl valkeyUrl = getValkeyUrl();
		return (valkeyUrl != null)
				? Standalone.of(valkeyUrl.uri().getHost(), valkeyUrl.uri().getPort(), valkeyUrl.database())
				: Standalone.of(this.properties.getHost(), this.properties.getPort(), this.properties.getDatabase());
	}

	@Override
	public @Nullable Sentinel getSentinel() {
		ValkeyProperties.Sentinel sentinel = this.properties.getSentinel();
		return (sentinel != null) ? new PropertiesSentinel(getStandalone().getDatabase(), sentinel) : null;
	}

	@Override
	public @Nullable Cluster getCluster() {
		ValkeyProperties.Cluster cluster = this.properties.getCluster();
		return (cluster != null) ? new PropertiesCluster(cluster) : null;
	}

	@Override
	public @Nullable MasterReplica getMasterReplica() {
		ValkeyProperties.Masterreplica masterreplica = this.properties.getMasterreplica();
		return (masterreplica != null) ? new PropertiesMasterReplica(masterreplica) : null;
	}

	private @Nullable ValkeyUrl getValkeyUrl() {
		return ValkeyUrl.of(this.properties.getUrl());
	}

	private static List<Node> asNodes(@Nullable List<String> nodes) {
		if (nodes == null) {
			return Collections.emptyList();
		}
		return nodes.stream().map(PropertiesValkeyConnectionDetails::asNode).toList();
	}

	private static Node asNode(String node) {
		int portSeparatorIndex = node.lastIndexOf(':');
		String host = node.substring(0, portSeparatorIndex);
		int port = Integer.parseInt(node.substring(portSeparatorIndex + 1));
		return new Node(host, port);
	}

	/**
	 * {@link Cluster} implementation backed by properties.
	 */
	private static class PropertiesCluster implements Cluster {

		private final List<Node> nodes;

		PropertiesCluster(ValkeyProperties.Cluster properties) {
			this.nodes = asNodes(properties.getNodes());
		}

		@Override
		public List<Node> getNodes() {
			return this.nodes;
		}

	}

	/**
	 * {@link MasterReplica} implementation backed by properties.
	 */
	private static class PropertiesMasterReplica implements MasterReplica {

		private final List<Node> nodes;

		PropertiesMasterReplica(ValkeyProperties.Masterreplica properties) {
			this.nodes = asNodes(properties.getNodes());
		}

		@Override
		public List<Node> getNodes() {
			return this.nodes;
		}

	}

	/**
	 * {@link Sentinel} implementation backed by properties.
	 */
	private static class PropertiesSentinel implements Sentinel {

		private final int database;

		private final ValkeyProperties.Sentinel properties;

		PropertiesSentinel(int database, ValkeyProperties.Sentinel properties) {
			this.database = database;
			this.properties = properties;
		}

		@Override
		public int getDatabase() {
			return this.database;
		}

		@Override
		public String getMaster() {
			String master = this.properties.getMaster();
			Assert.state(master != null, "'master' must not be null");
			return master;
		}

		@Override
		public List<Node> getNodes() {
			return asNodes(this.properties.getNodes());
		}

		@Override
		public @Nullable String getUsername() {
			return this.properties.getUsername();
		}

		@Override
		public @Nullable String getPassword() {
			return this.properties.getPassword();
		}

	}

}
