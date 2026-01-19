/*
 * Copyright 2015-2025 the original author or authors.
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration.ClusterConfiguration;
import org.springframework.lang.Nullable;
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
	 * Creates a new, default {@link ValkeyClusterConfiguration}.
	 */
	public ValkeyClusterConfiguration() {}

	/**
	 * Creates a new {@link ValkeyClusterConfiguration} for given {@link String hostPort} combinations.
	 *
	 * <pre class="code">
	 * clusterHostAndPorts[0] = 127.0.0.1:23679
	 * clusterHostAndPorts[1] = 127.0.0.1:23680 ...
	 * </pre>
	 *
	 * @param clusterNodes must not be {@literal null}.
	 */
	public ValkeyClusterConfiguration(Collection<String> clusterNodes) {
		initialize(new MapPropertySource("ValkeyClusterConfiguration", asMap(clusterNodes, -1)));
	}

	/**
	 * Creates a new {@link ValkeyClusterConfiguration} looking up configuration values from the given
	 * {@link PropertySource}.
	 *
	 * <pre class="code">
	 * spring.valkey.cluster.nodes=127.0.0.1:23679,127.0.0.1:23680,127.0.0.1:23681
	 * spring.valkey.cluster.max-redirects=3
	 * </pre>
	 *
	 * @param propertySource must not be {@literal null}.
	 * @deprecated since 3.3, use {@link ValkeySentinelConfiguration#of(PropertySource)} instead. This constructor will be
	 *             made private in the next major release.
	 */
	@Deprecated(since = "3.3")
	public ValkeyClusterConfiguration(PropertySource<?> propertySource) {
		initialize(propertySource);
	}

	private void initialize(PropertySource<?> propertySource) {

		Assert.notNull(propertySource, "PropertySource must not be null");

		if (propertySource.containsProperty(VALKEY_CLUSTER_NODES_CONFIG_PROPERTY)) {

			Object valkeyClusterNodes = propertySource.getProperty(VALKEY_CLUSTER_NODES_CONFIG_PROPERTY);
			appendClusterNodes(StringUtils.commaDelimitedListToSet(String.valueOf(valkeyClusterNodes)));
		}
		if (propertySource.containsProperty(VALKEY_CLUSTER_MAX_REDIRECTS_CONFIG_PROPERTY)) {

			Object clusterMaxRedirects = propertySource.getProperty(VALKEY_CLUSTER_MAX_REDIRECTS_CONFIG_PROPERTY);
			this.maxRedirects = NumberUtils.parseNumber(String.valueOf(clusterMaxRedirects), Integer.class);
		}
	}

	/**
	 * Creates a new {@link ValkeyClusterConfiguration} looking up configuration values from the given
	 * {@link PropertySource}.
	 *
	 * <pre class="code">
	 * spring.valkey.cluster.nodes=127.0.0.1:23679,127.0.0.1:23680,127.0.0.1:23681
	 * spring.valkey.cluster.max-redirects=3
	 * </pre>
	 *
	 * @param propertySource must not be {@literal null}.
	 * @return a new {@link ValkeyClusterConfiguration} configured from the given {@link PropertySource}.
	 * @since 3.3
	 */
	public static ValkeyClusterConfiguration of(PropertySource<?> propertySource) {
		return new ValkeyClusterConfiguration(propertySource);
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
	public Integer getMaxRedirects() {
		return maxRedirects != null && maxRedirects > Integer.MIN_VALUE ? maxRedirects : null;
	}

	@Override
	public void setUsername(@Nullable String username) {
		this.username = username;
	}

	@Nullable
	@Override
	public String getUsername() {
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

	/**
	 * @param clusterHostAndPorts must not be {@literal null} or empty.
	 * @param redirects the max number of redirects to follow.
	 * @return cluster config map with properties.
	 */
	private static Map<String, Object> asMap(Collection<String> clusterHostAndPorts, int redirects) {

		Assert.notNull(clusterHostAndPorts, "ClusterHostAndPorts must not be null");
		Assert.noNullElements(clusterHostAndPorts, "ClusterHostAndPorts must not contain null elements");

		Map<String, Object> map = new HashMap<>();

		map.put(VALKEY_CLUSTER_NODES_CONFIG_PROPERTY, StringUtils.collectionToCommaDelimitedString(clusterHostAndPorts));

		if (redirects >= 0) {
			map.put(VALKEY_CLUSTER_MAX_REDIRECTS_CONFIG_PROPERTY, redirects);
		}

		return map;
	}
}
