/*
 * Copyright 2011-present the original author or authors.
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

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;

/**
 * Set-specific commands supported by Valkey.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Mingi Lee
 * @see ValkeyCommands
 */
@NullUnmarked
public interface ValkeySetCommands {

	/**
	 * Add given {@code values} to set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param values must not be empty.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sadd">Valkey Documentation: SADD</a>
	 */
	Long sAdd(byte [] key, byte [] @NonNull... values);

	/**
	 * Remove given {@code values} from set at {@code key} and return the number of removed elements.
	 *
	 * @param key must not be {@literal null}.
	 * @param values must not be empty.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/srem">Valkey Documentation: SREM</a>
	 */
	Long sRem(byte [] key, byte []... values);

	/**
	 * Remove and return a random member from set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when key does not exist or used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/spop">Valkey Documentation: SPOP</a>
	 */
	byte[] sPop(byte [] key);

	/**
	 * Remove and return {@code count} random members from set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count number of random members to pop from the set.
	 * @return empty {@link List} if set does not exist. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/spop">Valkey Documentation: SPOP</a>
	 * @since 2.0
	 */
	List<byte []> sPop(byte [] key, long count);

	/**
	 * Move {@code value} from {@code srcKey} to {@code destKey}
	 *
	 * @param srcKey must not be {@literal null}.
	 * @param destKey must not be {@literal null}.
	 * @param value must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/smove">Valkey Documentation: SMOVE</a>
	 */
	Boolean sMove(byte [] srcKey, byte [] destKey, byte [] value);

	/**
	 * Get size of set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/scard">Valkey Documentation: SCARD</a>
	 */
	Long sCard(byte [] key);

	/**
	 * Check if set at {@code key} contains {@code value}.
	 *
	 * @param key must not be {@literal null}.
	 * @param value must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sismember">Valkey Documentation: SISMEMBER</a>
	 */
	Boolean sIsMember(byte [] key, byte [] value);

	/**
	 * Check if set at {@code key} contains one or more {@code values}.
	 *
	 * @param key must not be {@literal null}.
	 * @param values must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @since 2.6
	 * @see <a href="https://valkey.io/commands/smismember">Valkey Documentation: SMISMEMBER</a>
	 */
	List<Boolean> sMIsMember(byte [] key, byte [] @NonNull... values);

	/**
	 * Diff all sets for given {@code keys}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sdiff">Valkey Documentation: SDIFF</a>
	 */
	Set<byte []> sDiff(byte [] @NonNull... keys);

	/**
	 * Diff all sets for given {@code keys} and store result in {@code destKey}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sdiffstore">Valkey Documentation: SDIFFSTORE</a>
	 */
	Long sDiffStore(byte [] destKey, byte [] @NonNull... keys);

	/**
	 * Returns the members intersecting all given sets at {@code keys}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return empty {@link Set} if no intersection found. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sinter">Valkey Documentation: SINTER</a>
	 */
	Set<byte []> sInter(byte [] @NonNull... keys);

	/**
	 * Intersect all given sets at {@code keys} and store result in {@code destKey}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sinterstore">Valkey Documentation: SINTERSTORE</a>
	 */
	Long sInterStore(byte [] destKey, byte [] @NonNull... keys);

	/**
	 * Returns the cardinality of the set which would result from the intersection of all the given sets.
	 *
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sintercard">Valkey Documentation: SINTERCARD</a>
	 * @since 4.0
	 */
	Long sInterCard(byte [] @NonNull... keys);

	/**
	 * Union all sets at given {@code keys}.
	 *
	 * @param keys must not be {@literal null}.
	 * @return empty {@link Set} if keys do not exist. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sunion">Valkey Documentation: SUNION</a>
	 */
	Set<byte []> sUnion(byte [] @NonNull... keys);

	/**
	 * Union all sets at given {@code keys} and store result in {@code destKey}.
	 *
	 * @param destKey must not be {@literal null}.
	 * @param keys must not be {@literal null}.
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/sunionstore">Valkey Documentation: SUNIONSTORE</a>
	 */
	Long sUnionStore(byte [] destKey, byte [] @NonNull... keys);

	/**
	 * Get all elements of set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return empty {@link Set} when key does not exist. {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/smembers">Valkey Documentation: SMEMBERS</a>
	 */
	Set<byte []> sMembers(byte [] key);

	/**
	 * Get random element from set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @see <a href="https://valkey.io/commands/srandmember">Valkey Documentation: SRANDMEMBER</a>
	 */
	byte[] sRandMember(byte [] key);

	/**
	 * Get {@code count} random elements from set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param count
	 * @return {@literal null} when used in pipeline / transaction.
	 * @see <a href="https://valkey.io/commands/srandmember">Valkey Documentation: SRANDMEMBER</a>
	 */
	List<byte []> sRandMember(byte [] key, long count);

	/**
	 * Use a {@link Cursor} to iterate over elements in set at {@code key}.
	 *
	 * @param key must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.4
	 * @see <a href="https://valkey.io/commands/scan">Valkey Documentation: SCAN</a>
	 */
	Cursor<byte []> sScan(byte [] key, ScanOptions options);
}
