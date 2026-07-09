/*
 * Copyright 2015-present the original author or authors.
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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.core.env.PropertySource;
import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration.ClusterConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Configuration class used to set up a {@link ValkeyConnection} via {@link ValkeyConnectionFactory} for connecting to
 * <a href="https://valkey.io/topics/cluster-spec">Valkey Cluster</a>. Useful when setting up a highly available Valkey
 * environment.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author John Blum
 * @since 1.7
 */
public class ValkeyClusterConfiguration implements ValkeyConfiguration, ClusterConfiguration {

	private static final String VALKEY_CLUSTER_NODES_CONFIG_PROPERTY = "spring.valkey.cluster.nodes";
	private static final String VALKEY_CLUSTER_MAX_REDIRECTS_CONFIG_PROPERTY = "spring.valkey.cluster.max-redirects";

	private @Nullable Integer maxRedirects;

	private ValkeyPassword password = ValkeyPassword.none();

	private final Set<ValkeyNode> clusterNodes = new LinkedHashSet<>();

	private @Nullable String username = null;

	/**
	 * Creates a new, default {@code ValkeyClusterConfiguration}.
	 */
	public ValkeyClusterConfiguration() {}

	/**
	 * Creates a new {@code ValkeyClusterConfiguration} for given {@link String hostPort} combinations.
	 *
	 * <pre class="code">
	 * clusterHostAndPorts[0] = 127.0.0.1:23679
	 * clusterHostAndPorts[1] = 127.0.0.1:23680 ...
	 * </pre>
	 *
	 * @param clusterNodes must not be {@literal null}.
	 */
	public ValkeyClusterConfiguration(Collection<String> clusterNodes) {
		for (String hostAndPort : clusterNodes) {
			addClusterNode(ValkeyNode.fromString(hostAndPort));
		}
	}

	/**
	 * Creates a new {@code ValkeyClusterConfiguration} looking up configuration values from the given
	 * {@link PropertySource}.
	 *
	 * <pre class="code">
	 * spring.data.valkey.cluster.nodes=127.0.0.1:23679,127.0.0.1:23680,127.0.0.1:23681
	 * spring.data.valkey.cluster.max-redirects=3
	 * </pre>
	 *
	 * @param propertySource must not be {@literal null}.
	 * @return a new {@code ValkeyClusterConfiguration} configured from the given {@link PropertySource}.
	 * @since 3.3
	 */
	public static ValkeyClusterConfiguration of(PropertySource<?> propertySource) {

		Assert.notNull(propertySource, "PropertySource must not be null");

		ValkeyClusterConfiguration configuration = new ValkeyClusterConfiguration();

		if (propertySource.containsProperty(VALKEY_CLUSTER_NODES_CONFIG_PROPERTY)) {

			Object valkeyClusterNodes = propertySource.getProperty(VALKEY_CLUSTER_NODES_CONFIG_PROPERTY);
			configuration.appendClusterNodes(StringUtils.commaDelimitedListToSet(String.valueOf(valkeyClusterNodes)));
		}
		if (propertySource.containsProperty(VALKEY_CLUSTER_MAX_REDIRECTS_CONFIG_PROPERTY)) {

			Object clusterMaxRedirects = propertySource.getProperty(VALKEY_CLUSTER_MAX_REDIRECTS_CONFIG_PROPERTY);
			configuration.setMaxRedirects(NumberUtils.parseNumber(String.valueOf(clusterMaxRedirects), Integer.class));
		}

		return configuration;
	}

	private void appendClusterNodes(Set<String> hostAndPorts) {

		for (String hostAndPort : hostAndPorts) {
			addClusterNode(ValkeyNode.fromString(hostAndPort));
		}
	}

	/**
	 * Set {@literal cluster nodes} to connect to.
	 *
	 * @param nodes must not be {@literal null}.
	 */
	public void setClusterNodes(Iterable<ValkeyNode> nodes) {

		Assert.notNull(nodes, "Cannot set cluster nodes to null");

		this.clusterNodes.clear();

		for (ValkeyNode clusterNode : nodes) {
			addClusterNode(clusterNode);
		}
	}

	@Override
	public Set<ValkeyNode> getClusterNodes() {
		return Collections.unmodifiableSet(clusterNodes);
	}

	/**
	 * Add a cluster node to configuration.
	 *
	 * @param node must not be {@literal null}.
	 */
	public void addClusterNode(ValkeyNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		this.clusterNodes.add(node);
	}

	/**
	 * @param host Valkey cluster node host name or ip address.
	 * @param port Valkey cluster node port.
	 * @return this.
	 */
	public ValkeyClusterConfiguration clusterNode(String host, Integer port) {
		return clusterNode(new ValkeyNode(host, port));
	}

	/**
	 * @return this.
	 */
	public ValkeyClusterConfiguration clusterNode(ValkeyNode node) {

		this.clusterNodes.add(node);

		return this;
	}

	/**
	 * @param maxRedirects the max number of redirects to follow.
	 */
	public void setMaxRedirects(int maxRedirects) {

		Assert.isTrue(maxRedirects >= 0, "MaxRedirects must be greater or equal to 0");

		this.maxRedirects = maxRedirects;
	}

	@Override
	public @Nullable Integer getMaxRedirects() {
		return maxRedirects != null && maxRedirects > Integer.MIN_VALUE ? maxRedirects : null;
	}

	@Override
	public void setUsername(@Nullable String username) {
		this.username = username;
	}

	@Override
	public @Nullable String getUsername() {
		return this.username;
	}

	@Override
	public void setPassword(ValkeyPassword password) {

		Assert.notNull(password, "ValkeyPassword must not be null");

		this.password = password;
	}

	@Override
	public ValkeyPassword getPassword() {
		return password;
	}

	@Override
	public boolean equals(@Nullable Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof ValkeyClusterConfiguration that)) {
			return false;
		}

		return ObjectUtils.nullSafeEquals(this.clusterNodes, that.clusterNodes)
				&& ObjectUtils.nullSafeEquals(this.maxRedirects, that.maxRedirects)
				&& ObjectUtils.nullSafeEquals(this.username, that.username)
				&& ObjectUtils.nullSafeEquals(this.password, that.password);
	}

	@Override
	public int hashCode() {
		int result = ObjectUtils.nullSafeHashCode(clusterNodes);
		result = 31 * result + ObjectUtils.nullSafeHashCode(maxRedirects);
		result = 31 * result + ObjectUtils.nullSafeHashCode(username);
		result = 31 * result + ObjectUtils.nullSafeHashCode(password);
		return result;
	}

}
