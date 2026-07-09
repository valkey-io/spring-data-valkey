/*
 * Copyright 2017-present the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.jedis;

import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.ClusterSlotHashUtil;
import io.valkey.springframework.data.valkey.connection.ValkeyZSetCommands;
import io.valkey.springframework.data.valkey.connection.zset.Aggregate;
import io.valkey.springframework.data.valkey.connection.zset.Tuple;
import io.valkey.springframework.data.valkey.connection.zset.Weights;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanCursor;
import io.valkey.springframework.data.valkey.core.ScanIteration;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import io.valkey.springframework.data.valkey.util.ByteUtils;
import org.springframework.util.Assert;

/**
 * Cluster {@link ValkeyZSetCommands} implementation for Jedis.
 * <p>
 * This class can be used to override only methods that require cluster-specific handling.
 * <p>
 * Pipeline and transaction modes are not supported in cluster mode.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Clement Ong
 * @author Andrey Shlykov
 * @author Jens Deppe
 * @author Shyngys Sapraliyev
 * @author John Blum
 * @author Tihomir Mateev
 * @since 2.0
 */
@NullUnmarked
class JedisClusterZSetCommands extends JedisZSetCommands {

	private final JedisClusterConnection connection;

	JedisClusterZSetCommands(@NonNull JedisClusterConnection connection) {
		super(connection);
		this.connection = connection;
	}

	@Override
	public Set<byte @NonNull []> zDiff(byte @NonNull [] @NonNull... sets) {

		Assert.notNull(sets, "Sets must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sets)) {
			return super.zDiff(sets);
		}

		throw new InvalidDataAccessApiUsageException("ZDIFF can only be executed when all keys map to the same slot");
	}

	@Override
	public Set<Tuple> zDiffWithScores(byte @NonNull [] @NonNull... sets) {

		Assert.notNull(sets, "Sets must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sets)) {
			return super.zDiffWithScores(sets);
		}

		throw new InvalidDataAccessApiUsageException("ZDIFF can only be executed when all keys map to the same slot");
	}

	@Override
	public Long zDiffStore(byte @NonNull [] destKey, byte @NonNull [] @NonNull... sets) {

		Assert.notNull(destKey, "Destination key must not be null");
		Assert.notNull(sets, "Source sets must not be null");

		byte[][] allKeys = ByteUtils.mergeArrays(destKey, sets);

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(allKeys)) {
			return super.zDiffStore(destKey, sets);
		}

		throw new InvalidDataAccessApiUsageException("ZDIFFSTORE can only be executed when all keys map to the same slot");
	}

	@Override
	public Set<byte @NonNull []> zInter(byte @NonNull [] @NonNull... sets) {

		Assert.notNull(sets, "Sets must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sets)) {
			return super.zInter(sets);
		}

		throw new InvalidDataAccessApiUsageException("ZINTER can only be executed when all keys map to the same slot");
	}

	@Override
	public Set<@NonNull Tuple> zInterWithScores(byte @NonNull [] @NonNull... sets) {

		Assert.notNull(sets, "Sets must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sets)) {
			return super.zInterWithScores(sets);
		}

		throw new InvalidDataAccessApiUsageException("ZINTER can only be executed when all keys map to the same slot");
	}

	@Override
	public Set<@NonNull Tuple> zInterWithScores(@NonNull Aggregate aggregate, @NonNull Weights weights,
			byte @NonNull [] @NonNull... sets) {

		Assert.notNull(sets, "Sets must not be null");
		Assert.noNullElements(sets, "Source sets must not contain null elements");
		Assert.isTrue(weights.size() == sets.length,
				() -> "The number of weights %d must match the number of source sets %d".formatted(weights.size(),
						sets.length));

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sets)) {
			return super.zInterWithScores(aggregate, weights, sets);
		}

		throw new InvalidDataAccessApiUsageException("ZINTER can only be executed when all keys map to the same slot");
	}

	@Override
	public Long zInterStore(byte @NonNull [] destKey, byte @NonNull [] @NonNull... sets) {

		Assert.notNull(destKey, "Destination key must not be null");
		Assert.notNull(sets, "Source sets must not be null");
		Assert.noNullElements(sets, "Source sets must not contain null elements");

		byte[][] allKeys = ByteUtils.mergeArrays(destKey, sets);

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(allKeys)) {
			return super.zInterStore(destKey, sets);
		}

		throw new InvalidDataAccessApiUsageException("ZINTERSTORE can only be executed when all keys map to the same slot");
	}

	@Override
	public Long zInterStore(byte @NonNull [] destKey, @NonNull Aggregate aggregate, @NonNull Weights weights,
			byte @NonNull [] @NonNull... sets) {

		Assert.notNull(destKey, "Destination key must not be null");
		Assert.notNull(sets, "Source sets must not be null");
		Assert.noNullElements(sets, "Source sets must not contain null elements");
		Assert.isTrue(weights.size() == sets.length,
				"The number of weights %d must match the number of source sets %d".formatted(weights.size(), sets.length));

		byte[][] allKeys = ByteUtils.mergeArrays(destKey, sets);

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(allKeys)) {
			return super.zInterStore(destKey, aggregate, weights, sets);
		}

		throw new IllegalArgumentException("ZINTERSTORE can only be executed when all keys map to the same slot");
	}

	@Override
	public Set<byte @NonNull []> zUnion(byte @NonNull [] @NonNull... sets) {

		Assert.notNull(sets, "Sets must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sets)) {
			return super.zUnion(sets);
		}

		throw new InvalidDataAccessApiUsageException("ZUNION can only be executed when all keys map to the same slot");
	}

	@Override
	public Set<@NonNull Tuple> zUnionWithScores(byte @NonNull [] @NonNull... sets) {

		Assert.notNull(sets, "Sets must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sets)) {
			return super.zUnionWithScores(sets);
		}

		throw new InvalidDataAccessApiUsageException("ZUNION can only be executed when all keys map to the same slot");
	}

	@Override
	public Set<@NonNull Tuple> zUnionWithScores(@NonNull Aggregate aggregate, @NonNull Weights weights,
			byte @NonNull [] @NonNull... sets) {

		Assert.notNull(sets, "Sets must not be null");
		Assert.noNullElements(sets, "Source sets must not contain null elements");
		Assert.isTrue(weights.size() == sets.length,
				() -> "The number of weights %d must match the number of source sets %d".formatted(weights.size(),
						sets.length));

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(sets)) {
			return super.zUnionWithScores(aggregate, weights, sets);
		}

		throw new InvalidDataAccessApiUsageException("ZUNION can only be executed when all keys map to the same slot");
	}

	@Override
	public Long zUnionStore(byte @NonNull [] destKey, byte @NonNull [] @NonNull... sets) {

		Assert.notNull(destKey, "Destination key must not be null");
		Assert.notNull(sets, "Source sets must not be null");
		Assert.noNullElements(sets, "Source sets must not contain null elements");

		byte[][] allKeys = ByteUtils.mergeArrays(destKey, sets);

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(allKeys)) {
			return super.zUnionStore(destKey, sets);
		}

		throw new InvalidDataAccessApiUsageException("ZUNIONSTORE can only be executed when all keys map to the same slot");
	}

	@Override
	public Long zUnionStore(byte @NonNull [] destKey, @NonNull Aggregate aggregate, @NonNull Weights weights,
			byte @NonNull [] @NonNull... sets) {

		Assert.notNull(destKey, "Destination key must not be null");
		Assert.notNull(sets, "Source sets must not be null");
		Assert.noNullElements(sets, "Source sets must not contain null elements");
		Assert.isTrue(weights.size() == sets.length,
				"The number of weights %d must match the number of source sets %d".formatted(weights.size(), sets.length));

		byte[][] allKeys = ByteUtils.mergeArrays(destKey, sets);

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(allKeys)) {
			return super.zUnionStore(destKey, aggregate, weights, sets);
		}

		throw new InvalidDataAccessApiUsageException("ZUNIONSTORE can only be executed when all keys map to the same slot");
	}

	@Override
	public Cursor<@NonNull Tuple> zScan(byte @NonNull [] key, @NonNull ScanOptions options) {

		Assert.notNull(key, "Key must not be null");

		return new ScanCursor<Tuple>(options) {

			@Override
			protected ScanIteration<Tuple> doScan(CursorId cursorId, ScanOptions options) {

				ScanParams params = JedisConverters.toScanParams(options);

				ScanResult<redis.clients.jedis.resps.Tuple> result = connection.getCluster().zscan(key,
						JedisConverters.toBytes(cursorId), params);
				return new ScanIteration<>(CursorId.of(result.getCursor()),
						JedisConverters.tuplesToTuples().convert(result.getResult()));
			}
		}.open();
	}

}
