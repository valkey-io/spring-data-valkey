/*
 * Copyright 2011-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.valkeyglide;

import java.util.List;

import org.springframework.dao.InvalidDataAccessApiUsageException;

import io.valkey.springframework.data.valkey.connection.ClusterSlotHashUtil;
import io.valkey.springframework.data.valkey.connection.ValkeyKeyCommands;
import io.valkey.springframework.data.valkey.connection.SortParameters;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Implementation of {@link ValkeyKeyCommands} for Valkey-Glide in cluster mode.
 * This implementation handles cross-slot operations by executing commands separately
 * and aggregating results when keys map to different cluster slots.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideClusterKeyCommands extends ValkeyGlideKeyCommands {

    private final ValkeyGlideClusterConnection connection;

    /**
     * Creates a new {@link ValkeyGlideClusterKeyCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideClusterKeyCommands(ValkeyGlideClusterConnection connection) {
        super(connection);
        Assert.notNull(connection, "ValkeyGlideClusterConnection must not be null!");
        this.connection = connection;
    }

    @Override
    public Cursor<byte[]> scan(ScanOptions options) {
        // TODO: https://github.com/ikolomi/spring-data-valkey/issues/21
		throw new InvalidDataAccessApiUsageException("Scan is not supported across multiple nodes within a cluster");
    }

    @Override
	public Boolean move(byte[] key, int dbIndex) {
		throw new InvalidDataAccessApiUsageException("Cluster mode does not allow moving keys");
	}

	@Override
	public void rename(byte[] oldKey, byte[] newKey) {

		Assert.notNull(oldKey, "Old key must not be null");
		Assert.notNull(newKey, "New key must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(oldKey, newKey)) {
			super.rename(oldKey, newKey);
			return;
		}

		byte[] value = dump(oldKey);

		if (value != null && value.length > 0) {

			restore(newKey, 0, value, true);
			del(oldKey);
		}
	}

	@Override
	public Boolean renameNX(byte[] sourceKey, byte[] targetKey) {

		Assert.notNull(sourceKey, "Source key must not be null");
		Assert.notNull(targetKey, "Target key must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sourceKey, targetKey)) {
			return super.renameNX(sourceKey, targetKey);
		}

		byte[] value = dump(sourceKey);

		if (value != null && value.length > 0 && !exists(targetKey)) {

			restore(targetKey, 0, value);
			del(sourceKey);
			return Boolean.TRUE;
		}
		return Boolean.FALSE;
	}

	@Override
	@Nullable
	public Long sort(byte[] key, SortParameters params, byte[] storeKey) {

		Assert.notNull(key, "Key must not be null");
		Assert.notNull(storeKey, "Store key must not be null");

		// Fast path: same slot
		if (ClusterSlotHashUtil.isSameSlotForAllKeys(key, storeKey)) {
			return super.sort(key, params, storeKey);
		}

		// Cross-slot path: sort without store, then manually store results
		List<byte[]> sorted = sort(key, params);
		if (sorted == null || sorted.isEmpty()) {
			return 0L;
		}

		byte[][] arr = new byte[sorted.size()][];
		connection.keyCommands().unlink(storeKey);
		connection.listCommands().lPush(storeKey, sorted.toArray(arr));
		return (long) sorted.size();
	}
}
