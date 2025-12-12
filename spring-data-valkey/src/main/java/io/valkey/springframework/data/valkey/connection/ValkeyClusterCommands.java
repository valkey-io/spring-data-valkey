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
import java.util.List;
import java.util.Map;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode.SlotRange;

/**
 * Interface for the {@literal cluster} commands supported by Valkey. A {@link ValkeyClusterNode} can be obtained from
 * {@link #clusterGetNodes()} or it can be constructed using either {@link ValkeyClusterNode#getHost() host} and
 * {@link ValkeyClusterNode#getPort()} or the {@link ValkeyClusterNode#getId() node Id}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 1.7
 */
public interface ValkeyClusterCommands {

	/**
	 * Retrieve cluster node information such as {@literal id}, {@literal host}, {@literal port} and {@literal slots}.
	 *
	 * @return never {@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-nodes">Valkey Documentation: CLUSTER NODES</a>
	 */
	Iterable<ValkeyClusterNode> clusterGetNodes();

	/**
	 * Retrieve information about connected replicas for given master node.
	 *
	 * @param master must not be {@literal null}.
	 * @return never {@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-replicas">Valkey Documentation: CLUSTER REPLICAS</a>
	 */
	Collection<ValkeyClusterNode> clusterGetReplicas(ValkeyClusterNode master);

	/**
	 * Retrieve information about masters and their connected replicas.
	 *
	 * @return never {@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-replicas">Valkey Documentation: CLUSTER REPLICAS</a>
	 */
	Map<ValkeyClusterNode, Collection<ValkeyClusterNode>> clusterGetMasterReplicaMap();

	/**
	 * Find the slot for a given {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/cluster-keyslot">Valkey Documentation: CLUSTER KEYSLOT</a>
	 */
	Integer clusterGetSlotForKey(byte[] key);

	/**
	 * Find the {@link ValkeyClusterNode} serving given {@literal slot}.
	 *
	 * @param slot
	 * @return
	 */
	ValkeyClusterNode clusterGetNodeForSlot(int slot);

	/**
	 * Find the {@link ValkeyClusterNode} serving given {@literal key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 */
	ValkeyClusterNode clusterGetNodeForKey(byte[] key);

	/**
	 * Get cluster information.
	 *
	 * @return
	 * @see <a href="https://valkey.io/commands/cluster-info">Valkey Documentation: CLUSTER INFO</a>
	 */
	ClusterInfo clusterGetClusterInfo();

	/**
	 * Assign slots to given {@link ValkeyClusterNode}.
	 *
	 * @param node must not be {@literal null}.
	 * @param slots
	 * @see <a href="https://valkey.io/commands/cluster-addslots">Valkey Documentation: CLUSTER ADDSLOTS</a>
	 */
	void clusterAddSlots(ValkeyClusterNode node, int... slots);

	/**
	 * Assign {@link SlotRange#getSlotsArray()} to given {@link ValkeyClusterNode}.
	 *
	 * @param node must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-addslots">Valkey Documentation: CLUSTER ADDSLOTS</a>
	 */
	void clusterAddSlots(ValkeyClusterNode node, SlotRange range);

	/**
	 * Count the number of keys assigned to one {@literal slot}.
	 *
	 * @param slot
	 * @return
	 * @see <a href="https://valkey.io/commands/cluster-countkeysinslot">Valkey Documentation: CLUSTER COUNTKEYSINSLOT</a>
	 */
	Long clusterCountKeysInSlot(int slot);

	/**
	 * Remove slots from {@link ValkeyClusterNode}.
	 *
	 * @param node must not be {@literal null}.
	 * @param slots
	 * @see <a href="https://valkey.io/commands/cluster-delslots">Valkey Documentation: CLUSTER DELSLOTS</a>
	 */
	void clusterDeleteSlots(ValkeyClusterNode node, int... slots);

	/**
	 * Removes {@link SlotRange#getSlotsArray()} from given {@link ValkeyClusterNode}.
	 *
	 * @param node must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-delslots">Valkey Documentation: CLUSTER DELSLOTS</a>
	 */
	void clusterDeleteSlotsInRange(ValkeyClusterNode node, SlotRange range);

	/**
	 * Remove given {@literal node} from cluster.
	 *
	 * @param node must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-forget">Valkey Documentation: CLUSTER FORGET</a>
	 */
	void clusterForget(ValkeyClusterNode node);

	/**
	 * Add given {@literal node} to cluster.
	 *
	 * @param node must contain {@link ValkeyClusterNode#getHost() host} and {@link ValkeyClusterNode#getPort()} and must
	 *          not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-meet">Valkey Documentation: CLUSTER MEET</a>
	 */
	void clusterMeet(ValkeyClusterNode node);

	/**
	 * @param node must not be {@literal null}.
	 * @param slot
	 * @param mode must not be{@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-setslot">Valkey Documentation: CLUSTER SETSLOT</a>
	 */
	void clusterSetSlot(ValkeyClusterNode node, int slot, AddSlots mode);

	/**
	 * Get {@literal keys} served by slot.
	 *
	 * @param slot
	 * @param count must not be {@literal null}.
	 * @return
	 * @see <a href="https://valkey.io/commands/cluster-getkeysinslot">Valkey Documentation: CLUSTER GETKEYSINSLOT</a>
	 */
	List<byte[]> clusterGetKeysInSlot(int slot, Integer count);

	/**
	 * Assign a {@literal replica} to given {@literal master}.
	 *
	 * @param master must not be {@literal null}.
	 * @param replica must not be {@literal null}.
	 * @see <a href="https://valkey.io/commands/cluster-replicate">Valkey Documentation: CLUSTER REPLICATE</a>
	 */
	void clusterReplicate(ValkeyClusterNode master, ValkeyClusterNode replica);

	enum AddSlots {
		MIGRATING, IMPORTING, STABLE, NODE
	}

}
