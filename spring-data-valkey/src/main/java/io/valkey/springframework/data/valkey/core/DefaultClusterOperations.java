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
package io.valkey.springframework.data.valkey.core;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterCommands.AddSlots;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode.SlotRange;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.FlushOption;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.MigrateOption;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default {@link ClusterOperations} implementation.
 *
 * @author Christoph Strobl
 * @author Dennis Neufeld
 * @since 1.7
 * @param <K>
 * @param <V>
 */
class DefaultClusterOperations<K, V> extends AbstractOperations<K, V> implements ClusterOperations<K, V> {

	private final ValkeyTemplate<K, V> template;

	/**
	 * Creates new {@link DefaultClusterOperations} delegating to the given {@link ValkeyTemplate}.
	 *
	 * @param template must not be {@literal null}.
	 */
	DefaultClusterOperations(ValkeyTemplate<K, V> template) {

		super(template);
		this.template = template;
	}

	@Override
	public Set<K> keys(ValkeyClusterNode node, K pattern) {

		Assert.notNull(node, "ClusterNode must not be null");

		return doInCluster(connection -> deserializeKeys(connection.keys(node, rawKey(pattern))));
	}

	@Override
	public K randomKey(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		return doInCluster(connection -> deserializeKey(connection.randomKey(node)));
	}

	@Override
	public String ping(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		return doInCluster(connection -> connection.ping(node));
	}

	@Override
	public void addSlots(ValkeyClusterNode node, int... slots) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.clusterAddSlots(node, slots);
			return null;
		});
	}

	@Override
	public void addSlots(ValkeyClusterNode node, SlotRange range) {

		Assert.notNull(node, "ClusterNode must not be null");
		Assert.notNull(range, "Range must not be null");

		addSlots(node, range.getSlotsArray());
	}

	@Override
	public void bgReWriteAof(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.bgReWriteAof(node);
			return null;
		});
	}

	@Override
	public void bgSave(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.bgSave(node);
			return null;
		});
	}

	@Override
	public void meet(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.clusterMeet(node);
			return null;
		});
	}

	@Override
	public void forget(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.clusterForget(node);
			return null;
		});
	}

	@Override
	public void flushDb(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.flushDb(node);
			return null;
		});
	}

	@Override
	public void flushDb(ValkeyClusterNode node, FlushOption option) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.flushDb(node, option);
			return null;
		});
	}

	@Override
	public Collection<ValkeyClusterNode> getReplicas(final ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		return doInCluster(connection -> connection.clusterGetReplicas(node));
	}

	@Override
	public void save(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.save(node);
			return null;
		});
	}

	@Override
	public void shutdown(ValkeyClusterNode node) {

		Assert.notNull(node, "ClusterNode must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {
			connection.shutdown(node);
			return null;
		});
	}

	@Override
	public void reshard(ValkeyClusterNode source, int slot, ValkeyClusterNode target) {

		Assert.notNull(source, "Source node must not be null");
		Assert.notNull(target, "Target node must not be null");

		doInCluster((ValkeyClusterCallback<Void>) connection -> {

			connection.clusterSetSlot(target, slot, AddSlots.IMPORTING);
			connection.clusterSetSlot(source, slot, AddSlots.MIGRATING);
			List<byte[]> keys = connection.clusterGetKeysInSlot(slot, Integer.MAX_VALUE);

			for (byte[] key : keys) {
				connection.migrate(key, source, 0, MigrateOption.COPY);
			}
			connection.clusterSetSlot(target, slot, AddSlots.NODE);
			return null;
		});
	}

	/**
	 * Executed wrapped command upon {@link ValkeyClusterConnection}.
	 *
	 * @param callback must not be {@literal null}.
	 * @return execution result. Can be {@literal null}.
	 */
	@Nullable
	<T> T doInCluster(ValkeyClusterCallback<T> callback) {

		Assert.notNull(callback, "ClusterCallback must not be null");

		try (ValkeyClusterConnection connection = template.getConnectionFactory().getClusterConnection()) {
			return callback.doInValkey(connection);
		}
	}

}
