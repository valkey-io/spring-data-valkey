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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import io.valkey.springframework.data.valkey.ClusterStateFailureException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link ClusterTopology} holds snapshot like information about {@link ValkeyClusterNode}s.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 1.7
 */
public class ClusterTopology {

	private final Set<ValkeyClusterNode> nodes;

	/**
	 * Creates new instance of {@link ClusterTopology}.
	 *
	 * @param nodes can be {@literal null}.
	 */
	public ClusterTopology(@Nullable Set<ValkeyClusterNode> nodes) {
		this.nodes = nodes != null ? nodes : Collections.emptySet();
	}

	/**
	 * Get all {@link ValkeyClusterNode}s.
	 *
	 * @return never {@literal null}.
	 */
	public Set<ValkeyClusterNode> getNodes() {
		return Collections.unmodifiableSet(nodes);
	}

	/**
	 * Get all nodes (master and replica) in cluster where {@code link-state} is {@literal connected} and {@code flags}
	 * does not contain {@literal fail} or {@literal fail?}.
	 *
	 * @return never {@literal null}.
	 */
	public Set<ValkeyClusterNode> getActiveNodes() {

		Set<ValkeyClusterNode> activeNodes = new LinkedHashSet<>(nodes.size());
		for (ValkeyClusterNode node : nodes) {
			if (node.isConnected() && !node.isMarkedAsFail()) {
				activeNodes.add(node);
			}
		}
		return activeNodes;
	}

	/**
	 * Get all master nodes in cluster where {@code link-state} is {@literal connected} and {@code flags} does not contain
	 * {@literal fail} or {@literal fail?}.
	 *
	 * @return never {@literal null}.
	 */
	public Set<ValkeyClusterNode> getActiveMasterNodes() {

		Set<ValkeyClusterNode> activeMasterNodes = new LinkedHashSet<>(nodes.size());
		for (ValkeyClusterNode node : nodes) {
			if (node.isMaster() && node.isConnected() && !node.isMarkedAsFail()) {
				activeMasterNodes.add(node);
			}
		}
		return activeMasterNodes;
	}

	/**
	 * Get all master nodes in cluster.
	 *
	 * @return never {@literal null}.
	 */
	public Set<ValkeyClusterNode> getMasterNodes() {

		Set<ValkeyClusterNode> masterNodes = new LinkedHashSet<>(nodes.size());
		for (ValkeyClusterNode node : nodes) {
			if (node.isMaster()) {
				masterNodes.add(node);
			}
		}
		return masterNodes;
	}

	/**
	 * Get the {@link ValkeyClusterNode}s (master and replica) serving s specific slot.
	 *
	 * @param slot
	 * @return never {@literal null}.
	 */
	public Set<ValkeyClusterNode> getSlotServingNodes(int slot) {

		Set<ValkeyClusterNode> slotServingNodes = new LinkedHashSet<>(nodes.size());
		for (ValkeyClusterNode node : nodes) {
			if (node.servesSlot(slot)) {
				slotServingNodes.add(node);
			}
		}
		return slotServingNodes;
	}

	/**
	 * Get the {@link ValkeyClusterNode} that is the current master serving the given key.
	 *
	 * @param key must not be {@literal null}.
	 * @return never {@literal null}.
	 * @throws ClusterStateFailureException
	 */
	public ValkeyClusterNode getKeyServingMasterNode(byte[] key) {

		Assert.notNull(key, "Key for node lookup must not be null");

		int slot = ClusterSlotHashUtil.calculateSlot(key);

		for (ValkeyClusterNode node : nodes) {
			if (node.isMaster() && node.servesSlot(slot)) {
				return node;
			}
		}

		throw new ClusterStateFailureException(
				"Could not find master node serving slot %s for key '%s',".formatted(slot, Arrays.toString(key)));
	}

	/**
	 * Get the {@link ValkeyClusterNode} matching given {@literal host} and {@literal port}.
	 *
	 * @param host must not be {@literal null}.
	 * @param port
	 * @return never {@literal null}.
	 * @throws ClusterStateFailureException
	 */
	public ValkeyClusterNode lookup(String host, int port) {

		for (ValkeyClusterNode node : nodes) {
			if (host.equals(node.getHost()) && (node.getPort() != null && port == node.getPort())) {
				return node;
			}
		}

		throw new ClusterStateFailureException(
				"Could not find node at %s:%d; Is your cluster info up to date".formatted(host, port));
	}

	/**
	 * Get the {@link ValkeyClusterNode} matching given {@literal nodeId}.
	 *
	 * @param nodeId must not be {@literal null}.
	 * @return never {@literal null}.
	 * @throws ClusterStateFailureException
	 */
	public ValkeyClusterNode lookup(String nodeId) {

		Assert.notNull(nodeId, "NodeId must not be null");

		for (ValkeyClusterNode node : nodes) {
			if (nodeId.equals(node.getId())) {
				return node;
			}
		}

		throw new ClusterStateFailureException(
				"Could not find node at %s; Is your cluster info up to date".formatted(nodeId));
	}

	/**
	 * Get the {@link ValkeyClusterNode} matching matching either {@link ValkeyClusterNode#getHost() host} and
	 * {@link ValkeyClusterNode#getPort() port} or {@link ValkeyClusterNode#getId() nodeId}
	 *
	 * @param node must not be {@literal null}
	 * @return never {@literal null}.
	 * @throws ClusterStateFailureException
	 */
	public ValkeyClusterNode lookup(ValkeyClusterNode node) {

		Assert.notNull(node, "ValkeyClusterNode must not be null");

		if (nodes.contains(node) && node.hasValidHost() && StringUtils.hasText(node.getId())) {
			return node;
		}

		if (node.hasValidHost() && node.getPort() != null) {
			return lookup(node.getHost(), node.getPort());
		}

		if (StringUtils.hasText(node.getId())) {
			return lookup(node.getId());
		}

		throw new ClusterStateFailureException(
				("Could not find node at %s;" + " Have you provided either host and port or the nodeId").formatted(node));
	}

	/**
	 * @param key must not be {@literal null}.
	 * @return {@literal null}.
	 */
	public Set<ValkeyClusterNode> getKeyServingNodes(byte[] key) {

		Assert.notNull(key, "Key must not be null for Cluster Node lookup.");
		return getSlotServingNodes(ClusterSlotHashUtil.calculateSlot(key));
	}
}
