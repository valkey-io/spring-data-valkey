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
package io.valkey.springframework.data.valkey.core;

import java.util.Collection;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode.SlotRange;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.FlushOption;

/**
 * Valkey operations for cluster specific operations. A {@link ValkeyClusterNode} can be obtained from
 * {@link ValkeyClusterCommands#clusterGetNodes() a connection} or it can be constructed using either
 * {@link ValkeyClusterNode#getHost() host} and {@link ValkeyClusterNode#getPort()} or the {@link ValkeyClusterNode#getId()
 * node Id}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Dennis Neufeld
 * @since 1.7
 */
@NullUnmarked
public interface ClusterOperations<K, V> {

	/**
	 * Retrieve all keys located at given node matching the given pattern.
	 * <p>
	 * <strong>IMPORTANT:</strong> The {@literal KEYS} command is non-interruptible and scans the entire keyspace which
	 * may cause performance issues.
	 *
	 * @param node must not be {@literal null}.
	 * @param pattern
	 * @return never {@literal null}.
	 * @see ValkeyConnection#keys(byte[])
	 */
	Set<@NonNull K> keys(@NonNull ValkeyClusterNode node, @NonNull K pattern);

	/**
	 * Ping the given node;
	 *
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyConnection#ping()
	 */
	String ping(@NonNull ValkeyClusterNode node);

	/**
	 * Get a random key from the range served by the given node.
	 *
	 * @param node must not be {@literal null}.
	 * @return
	 * @see ValkeyConnection#randomKey()
	 */
	K randomKey(@NonNull ValkeyClusterNode node);

	/**
	 * Add slots to given node;
	 *
	 * @param node must not be {@literal null}.
	 * @param slots must not be {@literal null}.
	 */
	void addSlots(@NonNull ValkeyClusterNode node, int... slots);

	/**
	 * Add slots in {@link SlotRange} to given node.
	 *
	 * @param node must not be {@literal null}.
	 * @param range must not be {@literal null}.
	 */
	void addSlots(@NonNull ValkeyClusterNode node, @NonNull SlotRange range);

	/**
	 * Start an {@literal Append Only File} rewrite process on given node.
	 *
	 * @param node must not be {@literal null}.
	 * @see ValkeyConnection#bgReWriteAof()
	 */
	void bgReWriteAof(@NonNull ValkeyClusterNode node);

	/**
	 * Start background saving of db on given node.
	 *
	 * @param node must not be {@literal null}.
	 * @see ValkeyConnection#bgSave()
	 */
	void bgSave(@NonNull ValkeyClusterNode node);

	/**
	 * Add the node to cluster.
	 *
	 * @param node must not be {@literal null}.
	 */
	void meet(@NonNull ValkeyClusterNode node);

	/**
	 * Remove the node from the cluster.
	 *
	 * @param node must not be {@literal null}.
	 */
	void forget(@NonNull ValkeyClusterNode node);

	/**
	 * Flush db on node.
	 *
	 * @param node must not be {@literal null}.
	 * @see ValkeyConnection#flushDb()
	 */
	void flushDb(@NonNull ValkeyClusterNode node);

	/**
	 * Flush db on node using the specified {@link FlushOption}.
	 *
	 * @param node must not be {@literal null}.
	 * @param option must not be {@literal null}.
	 * @see ValkeyConnection#flushDb(FlushOption)
	 * @since 2.7
	 */
	void flushDb(@NonNull ValkeyClusterNode node, @NonNull FlushOption option);

	/**
	 * @param node must not be {@literal null}.
	 * @return
	 */
	Collection<@NonNull ValkeyClusterNode> getReplicas(@NonNull ValkeyClusterNode node);

	/**
	 * Synchronous save current db snapshot on server.
	 *
	 * @param node must not be {@literal null}.
	 * @see ValkeyConnection#save()
	 */
	void save(@NonNull ValkeyClusterNode node);

	/**
	 * Shutdown given node.
	 *
	 * @param node must not be {@literal null}.
	 * @see ValkeyConnection#shutdown()
	 */
	void shutdown(@NonNull ValkeyClusterNode node);

	/**
	 * Move slot assignment from one source to target node and copy keys associated with the slot.
	 *
	 * @param source must not be {@literal null}.
	 * @param slot
	 * @param target must not be {@literal null}.
	 */
	void reshard(@NonNull ValkeyClusterNode source, int slot, @NonNull ValkeyClusterNode target);
}
