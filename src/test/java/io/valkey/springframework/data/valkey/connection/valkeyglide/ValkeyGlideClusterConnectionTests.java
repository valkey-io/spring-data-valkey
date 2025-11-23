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
package io.valkey.springframework.data.valkey.connection.valkeyglide;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.*;
import static org.assertj.core.data.Offset.offset;
import static io.valkey.springframework.data.valkey.connection.BitFieldSubCommands.*;
import static io.valkey.springframework.data.valkey.connection.BitFieldSubCommands.BitFieldIncrBy.Overflow.*;
import static io.valkey.springframework.data.valkey.connection.BitFieldSubCommands.BitFieldType.*;
import static io.valkey.springframework.data.valkey.connection.ClusterTestVariables.*;
import static io.valkey.springframework.data.valkey.connection.ValkeyGeoCommands.DistanceUnit.*;
import static io.valkey.springframework.data.valkey.connection.ValkeyGeoCommands.GeoRadiusCommandArgs.*;
import static io.valkey.springframework.data.valkey.core.ScanOptions.*;

import glide.api.GlideClusterClient;
import glide.api.models.GlideString;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.SetOptions.Expiry;
import glide.api.models.commands.geospatial.GeospatialData;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Range.Bound;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.test.util.ReflectionTestUtils;

import io.valkey.springframework.data.valkey.connection.*;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode.SlotRange;
import io.valkey.springframework.data.valkey.connection.ValkeyGeoCommands.GeoLocation;
import io.valkey.springframework.data.valkey.connection.ValkeyListCommands.Direction;
import io.valkey.springframework.data.valkey.connection.ValkeyListCommands.Position;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.FlushOption;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands.BitOperation;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands.SetOption;
import io.valkey.springframework.data.valkey.connection.ValkeyZSetCommands.Range;
import io.valkey.springframework.data.valkey.connection.ValueEncoding.ValkeyValueEncoding;
import io.valkey.springframework.data.valkey.connection.zset.DefaultTuple;
import io.valkey.springframework.data.valkey.connection.zset.Tuple;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import io.valkey.springframework.data.valkey.core.script.DigestUtils;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;
import io.valkey.springframework.data.valkey.test.extension.ValkeyCluster;
import io.valkey.springframework.data.valkey.test.util.HexStringUtils;
import redis.clients.jedis.HostAndPort;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Pavel Khokhlov
 * @author Dennis Neufeld
 * @author Tihomir Mateev
 * @author Ilya Kolomin
 */
@EnabledOnValkeyClusterAvailable
@ExtendWith(ValkeyGlideConnectionFactoryExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValkeyGlideClusterConnectionTests implements ClusterConnectionTests {

	static final List<HostAndPort> CLUSTER_NODES = Arrays.asList(new HostAndPort(CLUSTER_HOST, MASTER_NODE_1_PORT),
			new HostAndPort(CLUSTER_HOST, MASTER_NODE_2_PORT), new HostAndPort(CLUSTER_HOST, MASTER_NODE_3_PORT));

	private static final byte[] KEY_1_BYTES = ValkeyGlideConverters.toBytes(KEY_1);
	private static final byte[] KEY_2_BYTES = ValkeyGlideConverters.toBytes(KEY_2);
	private static final byte[] KEY_3_BYTES = ValkeyGlideConverters.toBytes(KEY_3);

	private static final byte[] SAME_SLOT_KEY_1_BYTES = ValkeyGlideConverters.toBytes(SAME_SLOT_KEY_1);
	private static final byte[] SAME_SLOT_KEY_2_BYTES = ValkeyGlideConverters.toBytes(SAME_SLOT_KEY_2);
	private static final byte[] SAME_SLOT_KEY_3_BYTES = ValkeyGlideConverters.toBytes(SAME_SLOT_KEY_3);

	private static final byte[] VALUE_1_BYTES = ValkeyGlideConverters.toBytes(VALUE_1);
	private static final byte[] VALUE_2_BYTES = ValkeyGlideConverters.toBytes(VALUE_2);
	private static final byte[] VALUE_3_BYTES = ValkeyGlideConverters.toBytes(VALUE_3);

	private static final String STRING_ARIGENTO = "arigento";
	private static final String STRING_CATANIA = "catania";
	private static final String STRING_PALERMO = "palermo";

	private static final GeoLocation<byte[]> ARIGENTO = new GeoLocation<>(STRING_ARIGENTO.getBytes(Charset.forName("UTF-8")),
			POINT_ARIGENTO);
	private static final GeoLocation<byte[]> CATANIA = new GeoLocation<>(STRING_CATANIA.getBytes(Charset.forName("UTF-8")),
			POINT_CATANIA);
	private static final GeoLocation<byte[]> PALERMO = new GeoLocation<>(STRING_PALERMO.getBytes(Charset.forName("UTF-8")),
			POINT_PALERMO);

	private static final Map<String, GeospatialData> GLIDE_GEO_DATA = Map.of(
		STRING_ARIGENTO, new GeospatialData(POINT_ARIGENTO.getX(), POINT_ARIGENTO.getY()),
		STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY()),
		STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY())
    );

	private final GlideClusterClient nativeConnection;
	private final ValkeyGlideClusterConnection clusterConnection;

	/**
	 * Constructor that extracts the native GlideClusterClient from the connection factory.
	 * This enables the dual-layer testing approach where we use the native client for setup/verification.
	 */
	public ValkeyGlideClusterConnectionTests(@ValkeyCluster ValkeyConnectionFactory factory) {
		// Get cluster connection from factory
		ValkeyClusterConnection connection = factory.getClusterConnection();
		
		if (!(connection instanceof ValkeyGlideClusterConnection)) {
			throw new IllegalStateException("Expected ValkeyGlideClusterConnection but got " + connection.getClass());
		}
		
		clusterConnection = (ValkeyGlideClusterConnection) connection;
		nativeConnection = (GlideClusterClient) clusterConnection.getNativeConnection();
	}

	@BeforeEach
	void setUp() throws Exception {
		clusterConnection.flushDb();
	}

	@Test // DATAREDIS-315
	public void appendShouldAddValueCorrectly() {
		try {
			clusterConnection.append(KEY_1_BYTES, VALUE_1_BYTES);
			clusterConnection.append(KEY_1_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1.concat(VALUE_2));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void bRPopLPushShouldWork() {

		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			assertThat(clusterConnection.bRPopLPush(0, KEY_1_BYTES, KEY_2_BYTES)).isEqualTo(VALUE_1_BYTES);
			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(1);
			assertThat(nativeConnection.exists(new String[]{KEY_2}).get()).isEqualTo(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void bRPopLPushShouldWorkOnSameSlotKeys() {
		try {
			nativeConnection.lpush(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			
			assertThat(clusterConnection.bRPopLPush(0, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).isEqualTo(VALUE_1_BYTES);
			assertThat(nativeConnection.exists(new String[]{SAME_SLOT_KEY_1}).get()).isEqualTo(1);
			assertThat(nativeConnection.exists(new String[]{SAME_SLOT_KEY_2}).get()).isEqualTo(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void bitCountShouldWorkCorrectly() {
		try {
			nativeConnection.setbit(KEY_1, 0, 1).get();
			nativeConnection.setbit(KEY_1, 1, 0).get();

			assertThat(clusterConnection.bitCount(KEY_1_BYTES)).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void bitCountWithRangeShouldWorkCorrectly() {
		try {
			nativeConnection.setbit(KEY_1, 0, 1).get();
			nativeConnection.setbit(KEY_1, 1, 0).get();
			nativeConnection.setbit(KEY_1, 2, 1).get();
			nativeConnection.setbit(KEY_1, 3, 0).get();
			nativeConnection.setbit(KEY_1, 4, 1).get();

			assertThat(clusterConnection.bitCount(KEY_1_BYTES, 0, 3)).isEqualTo(3L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void bitOpShouldThrowExceptionWhenKeysDoNotMapToSameSlot() {
		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.bitOp(BitOperation.AND, KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES));
	}

	@Test // DATAREDIS-315
	void bitOpShouldWorkCorrectly() {
		try {
			nativeConnection.set(SAME_SLOT_KEY_1, "foo").get();
			nativeConnection.set(SAME_SLOT_KEY_2, "bar").get();

			clusterConnection.bitOp(BitOperation.AND, SAME_SLOT_KEY_3_BYTES, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.get(SAME_SLOT_KEY_3).get()).isEqualTo("bab");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void blPopShouldPopElementCorrectly() {
		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.lpush(KEY_2, new String[]{VALUE_3}).get();

			assertThat(clusterConnection.bLPop(100, KEY_1_BYTES, KEY_2_BYTES).size()).isEqualTo(2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void blPopShouldPopElementCorrectlyWhenKeyOnSameSlot() {
		try {
			nativeConnection.lpush(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.lpush(SAME_SLOT_KEY_2, new String[]{VALUE_3}).get();

			assertThat(clusterConnection.bLPop(100, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES).size()).isEqualTo(2);
		}
		catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void brPopShouldPopElementCorrectly() {
		try {		
			nativeConnection.lpush(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.lpush(KEY_2, new String[]{VALUE_3}).get();

			assertThat(clusterConnection.bRPop(100, KEY_1_BYTES, KEY_2_BYTES).size()).isEqualTo(2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void brPopShouldPopElementCorrectlyWhenKeyOnSameSlot() {
		try {
			nativeConnection.lpush(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.lpush(SAME_SLOT_KEY_2, new String[]{VALUE_3}).get();

			assertThat(clusterConnection.bRPop(100, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES).size()).isEqualTo(2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void clientListShouldGetInfosForAllClients() {
		assertThat(clusterConnection.getClientList().isEmpty()).isFalse();
	}

	@Test // DATAREDIS-315
	public void clusterGetMasterReplicaMapShouldListMastersAndReplicasCorrectly() {

		Map<ValkeyClusterNode, Collection<ValkeyClusterNode>> masterReplicaMap = clusterConnection
				.clusterGetMasterReplicaMap();

		assertThat(masterReplicaMap).isNotNull();
		assertThat(masterReplicaMap.size()).isEqualTo(3);
		assertThat(masterReplicaMap.get(new ValkeyClusterNode(CLUSTER_HOST, MASTER_NODE_1_PORT)))
				.contains(new ValkeyClusterNode(CLUSTER_HOST, REPLICAOF_NODE_1_PORT));
		assertThat(masterReplicaMap.get(new ValkeyClusterNode(CLUSTER_HOST, MASTER_NODE_2_PORT)).isEmpty()).isTrue();
		assertThat(masterReplicaMap.get(new ValkeyClusterNode(CLUSTER_HOST, MASTER_NODE_3_PORT)).isEmpty()).isTrue();
	}

	@Test // DATAREDIS-315
	public void clusterGetReplicasShouldReturnReplicaCorrectly() {
		Set<ValkeyClusterNode> replicas = clusterConnection
				.clusterGetReplicas(new ValkeyClusterNode(CLUSTER_HOST, MASTER_NODE_1_PORT));

		assertThat(replicas.size()).isEqualTo(1);
		assertThat(replicas).contains(new ValkeyClusterNode(CLUSTER_HOST, REPLICAOF_NODE_1_PORT));
	}

	@Test // DATAREDIS-315
	public void countKeysShouldReturnNumberOfKeysInSlot() {
		try {
			nativeConnection.set(SAME_SLOT_KEY_1, VALUE_1).get();
			nativeConnection.set(SAME_SLOT_KEY_2, VALUE_2).get();

			assertThat(clusterConnection.clusterCountKeysInSlot(ClusterSlotHashUtil.calculateSlot(SAME_SLOT_KEY_1)))
					.isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void dbSizeForSpecificNodeShouldGetNodeDbSize() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			assertThat(clusterConnection.dbSize(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()))).isEqualTo(1L);
			assertThat(clusterConnection.dbSize(new ValkeyClusterNode("127.0.0.1", 7380, SlotRange.empty()))).isEqualTo(1L);
			assertThat(clusterConnection.dbSize(new ValkeyClusterNode("127.0.0.1", 7381, SlotRange.empty()))).isEqualTo(0L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void dbSizeShouldReturnCummulatedDbSize() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			assertThat(clusterConnection.dbSize()).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void decrByShouldDecreaseValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, "5").get();

			assertThat(clusterConnection.decrBy(KEY_1_BYTES, 4)).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void decrShouldDecreaseValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, "5").get();

			assertThat(clusterConnection.decr(KEY_1_BYTES)).isEqualTo(4L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void delShouldRemoveMultipleKeysCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.del(KEY_1_BYTES, KEY_2_BYTES);

			assertThat(nativeConnection.get(KEY_1).get()).isNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void delShouldRemoveMultipleKeysOnSameSlotCorrectly() {
		try {
			nativeConnection.set(SAME_SLOT_KEY_1, VALUE_1).get();
			nativeConnection.set(SAME_SLOT_KEY_2, VALUE_2).get();

			clusterConnection.del(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.get(SAME_SLOT_KEY_1).get()).isNull();
			assertThat(nativeConnection.get(SAME_SLOT_KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void delShouldRemoveSingleKeyCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.del(KEY_1_BYTES);

			assertThat(nativeConnection.get(KEY_1).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void discardShouldThrowException() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(clusterConnection::discard);
	}

	@Test // DATAREDIS-315
	public void dumpAndRestoreShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			byte[] dumpedValue = clusterConnection.dump(KEY_1_BYTES);
			clusterConnection.restore(KEY_2_BYTES, 0, dumpedValue);

			assertThat(nativeConnection.get(KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-696
	public void dumpAndRestoreWithReplaceOptionShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			byte[] dumpedValue = clusterConnection.keyCommands().dump(KEY_1_BYTES);

			nativeConnection.set(KEY_1, VALUE_2).get();

			clusterConnection.keyCommands().restore(KEY_1_BYTES, 0, dumpedValue, true);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void echoShouldReturnInputCorrectly() {
		assertThatExceptionOfType(InvalidDataAccessApiUsageException.class)
				.isThrownBy(() -> clusterConnection.echo(VALUE_1_BYTES));
	}

	@Test // DATAREDIS-315
	public void execShouldThrowException() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(clusterConnection::exec);
	}

	@Test // DATAREDIS-689
	void executeWithArgs() {
		try {
			// valkey-glide client returns boolean for SET command
			assertThat(clusterConnection.execute("SET", KEY_1_BYTES, VALUE_1_BYTES)).isEqualTo(true);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-689
	void executeWithKeyAndArgs() {
		try {
			Object result = clusterConnection.execute("SET", KEY_1_BYTES, Collections.singletonList(VALUE_1_BYTES));
			// valkey-glide client returns boolean for SET command
			assertThat(result).isEqualTo(true);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-689
	void executeWithNoKeyAndArgsThrowsException() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> clusterConnection.execute("KEYS", (byte[]) null, Collections.singletonList("*".getBytes())));
	}

	@Test // DATAREDIS-529
	public void existsShouldCountSameKeyMultipleTimes() {
		try {
			nativeConnection.set(KEY_1, "true").get();

			assertThat(clusterConnection.keyCommands().exists(KEY_1_BYTES, KEY_1_BYTES)).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-529
	public void existsWithMultipleKeysShouldConsiderAbsentKeys() {
		assertThat(clusterConnection.keyCommands().exists("no-exist-1".getBytes(), "no-exist-2".getBytes())).isEqualTo(0L);
	}

	@Test // DATAREDIS-529
	public void existsWithMultipleKeysShouldReturnResultCorrectly() {
		try {
			nativeConnection.set(KEY_1, "true").get();
			nativeConnection.set(KEY_2, "true").get();
			nativeConnection.set(KEY_3, "true").get();

			assertThat(clusterConnection.keyCommands().exists(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES, "nonexistent".getBytes()))
					.isEqualTo(3L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void expireAtShouldBeSetCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.expireAt(KEY_1_BYTES, System.currentTimeMillis() / 1000 + 5000);

			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3114
	@EnabledOnCommand("SPUBLISH") // Valkey 7.0
	public void expireAtWithConditionShouldBeSetCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.expireAt(KEY_1_BYTES, System.currentTimeMillis() / 1000 + 5000,
					ExpirationOptions.Condition.XX)).isFalse();
			assertThat(clusterConnection.expireAt(KEY_1_BYTES, System.currentTimeMillis() / 1000 + 5000,
					ExpirationOptions.Condition.NX)).isTrue();
			assertThat(clusterConnection.expireAt(KEY_1_BYTES, System.currentTimeMillis() / 1000 + 15000,
					ExpirationOptions.Condition.LT)).isFalse();

			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void expireShouldBeSetCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.expire(KEY_1_BYTES, 5);

			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3114
	@EnabledOnCommand("SPUBLISH") // Valkey 7.0
	public void expireWithConditionShouldBeSetCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.expire(KEY_1_BYTES, 15, ExpirationOptions.Condition.XX)).isFalse();
			assertThat(clusterConnection.expire(KEY_1_BYTES, 15, ExpirationOptions.Condition.NX)).isTrue();
			assertThat(clusterConnection.expire(KEY_1_BYTES, 15, ExpirationOptions.Condition.LT)).isFalse();

			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void flushDbOnSingleNodeShouldFlushOnlyGivenNodesDb() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushDb(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()));

			assertThat(nativeConnection.get(KEY_1).get()).isNotNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushDbSyncOnSingleNodeShouldFlushOnlyGivenNodesDb() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushDb(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()), FlushOption.SYNC);

			assertThat(nativeConnection.get(KEY_1).get()).isNotNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushDbAsyncOnSingleNodeShouldFlushOnlyGivenNodesDb() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushDb(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()), FlushOption.ASYNC);

			assertThat(nativeConnection.get(KEY_1).get()).isNotNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void flushDbShouldFlushAllClusterNodes() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushDb();

			assertThat(nativeConnection.get(KEY_1).get()).isNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushDbSyncShouldFlushAllClusterNodes() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushDb(FlushOption.SYNC);

			assertThat(nativeConnection.get(KEY_1).get()).isNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushDbAsyncShouldFlushAllClusterNodes() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushDb(FlushOption.ASYNC);

			assertThat(nativeConnection.get(KEY_1).get()).isNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushAllOnSingleNodeShouldFlushOnlyGivenNodesDb() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushAll(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()));

			assertThat(nativeConnection.get(KEY_1).get()).isNotNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushAllSyncOnSingleNodeShouldFlushOnlyGivenNodesDb() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushAll(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()), FlushOption.SYNC);

			assertThat(nativeConnection.get(KEY_1).get()).isNotNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushAllAsyncOnSingleNodeShouldFlushOnlyGivenNodesDb() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushAll(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()), FlushOption.ASYNC);

			assertThat(nativeConnection.get(KEY_1).get()).isNotNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushAllShouldFlushAllClusterNodes() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushAll();

			assertThat(nativeConnection.get(KEY_1).get()).isNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushAllSyncShouldFlushAllClusterNodes() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushAll(FlushOption.SYNC);

			assertThat(nativeConnection.get(KEY_1).get()).isNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2187
	public void flushAllAsyncShouldFlushAllClusterNodes() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.flushAll(FlushOption.ASYNC);

			assertThat(nativeConnection.get(KEY_1).get()).isNull();
			assertThat(nativeConnection.get(KEY_2).get()).isNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoAddMultipleGeoLocations() {
		assertThat(clusterConnection.geoAdd(KEY_1_BYTES, Arrays.asList(PALERMO, ARIGENTO, CATANIA, PALERMO))).isEqualTo(3L);
	}

	@Test // DATAREDIS-438
	public void geoAddSingleGeoLocation() {
		assertThat(clusterConnection.geoAdd(KEY_1_BYTES, PALERMO)).isEqualTo(1L);
	}

	@Test // DATAREDIS-438
	public void geoDist() {
		try {
			nativeConnection.geoadd(KEY_1, GLIDE_GEO_DATA).get();

			Distance distance = clusterConnection.geoDist(KEY_1_BYTES, PALERMO.getName(), CATANIA.getName());
			assertThat(distance.getValue()).isCloseTo(166274.15156960033D, offset(0.005));
			assertThat(distance.getUnit()).isEqualTo("m");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoDistWithMetric() {
		try {
			nativeConnection.geoadd(KEY_1, GLIDE_GEO_DATA).get();

			Distance distance = clusterConnection.geoDist(KEY_1_BYTES, PALERMO.getName(), CATANIA.getName(), KILOMETERS);
			assertThat(distance.getValue()).isCloseTo(166.27415156960033D, offset(0.005));
			assertThat(distance.getUnit()).isEqualTo("km");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoHash() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			List<String> result = clusterConnection.geoHash(KEY_1_BYTES, PALERMO.getName(), CATANIA.getName());
			assertThat(result).containsExactly("sqc8b49rny0", "sqdtr74hyu0");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoHashNonExisting() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			List<String> result = clusterConnection.geoHash(KEY_1_BYTES, PALERMO.getName(), ARIGENTO.getName(),
					CATANIA.getName());
			assertThat(result).containsExactly("sqc8b49rny0", (String) null, "sqdtr74hyu0");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoPosition() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			List<Point> positions = clusterConnection.geoPos(KEY_1_BYTES, PALERMO.getName(), CATANIA.getName());

			assertThat(positions.get(0).getX()).isCloseTo(POINT_PALERMO.getX(), offset(0.005));
			assertThat(positions.get(0).getY()).isCloseTo(POINT_PALERMO.getY(), offset(0.005));

			assertThat(positions.get(1).getX()).isCloseTo(POINT_CATANIA.getX(), offset(0.005));
			assertThat(positions.get(1).getY()).isCloseTo(POINT_CATANIA.getY(), offset(0.005));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoPositionNonExisting() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			List<Point> positions = clusterConnection.geoPos(KEY_1_BYTES, PALERMO.getName(), ARIGENTO.getName(),
					CATANIA.getName());

			assertThat(positions.get(0).getX()).isCloseTo(POINT_PALERMO.getX(), offset(0.005));
			assertThat(positions.get(0).getY()).isCloseTo(POINT_PALERMO.getY(), offset(0.005));

			assertThat(positions.get(1)).isNull();

			assertThat(positions.get(2).getX()).isCloseTo(POINT_CATANIA.getX(), offset(0.005));
			assertThat(positions.get(2).getY()).isCloseTo(POINT_CATANIA.getY(), offset(0.005));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoRadiusByMemberShouldApplyLimit() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_ARIGENTO, new GeospatialData(POINT_ARIGENTO.getX(), POINT_ARIGENTO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			GeoResults<GeoLocation<byte[]>> result = clusterConnection.geoRadiusByMember(KEY_1_BYTES, PALERMO.getName(),
					new Distance(200, KILOMETERS), newGeoRadiusArgs().limit(2));

			assertThat(result.getContent()).hasSize(2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoRadiusByMemberShouldReturnDistanceCorrectly() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_ARIGENTO, new GeospatialData(POINT_ARIGENTO.getX(), POINT_ARIGENTO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			GeoResults<GeoLocation<byte[]>> result = clusterConnection.geoRadiusByMember(KEY_1_BYTES, PALERMO.getName(),
					new Distance(100, KILOMETERS), newGeoRadiusArgs().includeDistance());

			assertThat(result.getContent()).hasSize(2);
			assertThat(result.getContent().get(0).getDistance().getValue()).isCloseTo(90.978D, offset(0.005));
			assertThat(result.getContent().get(0).getDistance().getUnit()).isEqualTo("km");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoRadiusByMemberShouldReturnMembersCorrectly() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_ARIGENTO, new GeospatialData(POINT_ARIGENTO.getX(), POINT_ARIGENTO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			GeoResults<GeoLocation<byte[]>> result = clusterConnection.geoRadiusByMember(KEY_1_BYTES, PALERMO.getName(),
					new Distance(100, KILOMETERS), newGeoRadiusArgs().sortAscending());

			assertThat(result.getContent().get(0).getContent().getName()).isEqualTo(PALERMO.getName());
			assertThat(result.getContent().get(1).getContent().getName()).isEqualTo(ARIGENTO.getName());
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}	
	}

	@Test // DATAREDIS-438
	public void geoRadiusShouldApplyLimit() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_ARIGENTO, new GeospatialData(POINT_ARIGENTO.getX(), POINT_ARIGENTO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			GeoResults<GeoLocation<byte[]>> result = clusterConnection.geoRadius(KEY_1_BYTES,
					new Circle(new Point(15D, 37D), new Distance(200D, KILOMETERS)), newGeoRadiusArgs().limit(2));

			assertThat(result.getContent()).hasSize(2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoRadiusShouldReturnDistanceCorrectly() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_ARIGENTO, new GeospatialData(POINT_ARIGENTO.getX(), POINT_ARIGENTO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();
			GeoResults<GeoLocation<byte[]>> result = clusterConnection.geoRadius(KEY_1_BYTES,
					new Circle(new Point(15D, 37D), new Distance(200D, KILOMETERS)), newGeoRadiusArgs().includeDistance());

			assertThat(result.getContent()).hasSize(3);
			assertThat(result.getContent().get(0).getDistance().getValue()).isCloseTo(130.423D, offset(0.005));
			assertThat(result.getContent().get(0).getDistance().getUnit()).isEqualTo("km");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoRadiusShouldReturnMembersCorrectly() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_ARIGENTO, new GeospatialData(POINT_ARIGENTO.getX(), POINT_ARIGENTO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();

			GeoResults<GeoLocation<byte[]>> result = clusterConnection.geoRadius(KEY_1_BYTES,
					new Circle(new Point(15D, 37D), new Distance(150D, KILOMETERS)));
			assertThat(result.getContent()).hasSize(2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-438
	public void geoRemoveDeletesMembers() {
		try {
			nativeConnection.geoadd(KEY_1, 
				Map.of(STRING_PALERMO, new GeospatialData(POINT_PALERMO.getX(), POINT_PALERMO.getY()),
					STRING_ARIGENTO, new GeospatialData(POINT_ARIGENTO.getX(), POINT_ARIGENTO.getY()),
					STRING_CATANIA, new GeospatialData(POINT_CATANIA.getX(), POINT_CATANIA.getY())
				)
			).get();
			assertThat(clusterConnection.geoRemove(KEY_1_BYTES, ARIGENTO.getName())).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void getBitShouldWorkCorrectly() {
		try {
			nativeConnection.setbit(KEY_1, 0, 1).get();
			nativeConnection.setbit(KEY_1, 1, 0).get();

			assertThat(clusterConnection.getBit(KEY_1_BYTES, 0)).isTrue();
			assertThat(clusterConnection.getBit(KEY_1_BYTES, 1)).isFalse();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void getClusterNodeForKeyShouldReturnNodeCorrectly() {
		assertThat((ValkeyNode) clusterConnection.clusterGetNodeForKey(KEY_1_BYTES))
				.isEqualTo(new ValkeyNode("127.0.0.1", 7380));
	}

	@Test // DATAREDIS-315, DATAREDIS-661
	public void getConfigShouldLoadConfigurationOfSpecificNode() {

		Properties result = clusterConnection.getConfig(new ValkeyClusterNode(CLUSTER_HOST, REPLICAOF_NODE_1_PORT), "*");

		assertThat(result.getProperty("slaveof")).endsWith("7379");
	}

	@Test // DATAREDIS-315, DATAREDIS-661
	public void getConfigShouldLoadCumulatedConfiguration() {

		Properties result = clusterConnection.getConfig("*max-*-entries*");

		// config get *max-*-entries on valkey 3.0.7 returns 8 entries per node while on 3.2.0-rc3 returns 6.
		// @link https://github.com/spring-projects/spring-data-valkey/pull/187
		assertThat(result.size() % 3).isEqualTo(0);

		for (Object o : result.keySet()) {

			assertThat(o.toString()).startsWith(CLUSTER_HOST);
			assertThat(result.getProperty(o.toString())).doesNotStartWith(CLUSTER_HOST);
		}
	}

	@Test // DATAREDIS-315
	public void getRangeShouldReturnValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			
			assertThat(clusterConnection.getRange(KEY_1_BYTES, 0, 2)).isEqualTo(ValkeyGlideConverters.toBytes("val"));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2050
	@EnabledOnCommand("GETEX")
	public void getExShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.getEx(KEY_1_BYTES, Expiration.seconds(10))).isEqualTo(VALUE_1_BYTES);
			assertThat(clusterConnection.ttl(KEY_1_BYTES)).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2050
	@EnabledOnCommand("GETDEL")
	public void getDelShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.getDel(KEY_1_BYTES)).isEqualTo(VALUE_1_BYTES);
			assertThat(clusterConnection.exists(KEY_1_BYTES)).isFalse();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void getSetShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			byte[] valueBeforeSet = clusterConnection.getSet(KEY_1_BYTES, VALUE_2_BYTES);

			assertThat(valueBeforeSet).isEqualTo(VALUE_1_BYTES);
			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void getShouldReturnValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.get(KEY_1_BYTES)).isEqualTo(VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hDelShouldRemoveFieldsCorrectly() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_1)).get();
			nativeConnection.hset(KEY_1, Map.of(KEY_3, VALUE_2)).get();

			clusterConnection.hDel(KEY_1_BYTES, KEY_2_BYTES);

			assertThat(nativeConnection.hexists(KEY_1, KEY_2).get()).isFalse();
			assertThat(nativeConnection.hexists(KEY_1, KEY_3).get()).isTrue();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hExistsShouldReturnPresenceOfFieldCorrectly() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_1)).get();

			assertThat(clusterConnection.hExists(KEY_1_BYTES, KEY_2_BYTES)).isTrue();
			assertThat(clusterConnection.hExists(KEY_1_BYTES, KEY_3_BYTES)).isFalse();
			assertThat(clusterConnection.hExists(ValkeyGlideConverters.toBytes("foo"), KEY_2_BYTES)).isFalse();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hGetAllShouldRetrieveEntriesCorrectly() {
		try {
			Map<String, String> hashes = new HashMap<>();
			hashes.put(KEY_2, VALUE_1);
			hashes.put(KEY_3, VALUE_2);

			nativeConnection.hset(KEY_1, hashes).get();

			Map<String, String> hGetAllString = clusterConnection.hGetAll(KEY_1_BYTES).entrySet().stream()
        		.collect(Collectors.toMap(
					e -> new String(e.getKey(), StandardCharsets.UTF_8),
					e -> new String(e.getValue(), StandardCharsets.UTF_8)));

			assertThat(hGetAllString.containsKey(KEY_2)).isTrue();
			assertThat(hGetAllString.containsKey(KEY_3)).isTrue();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hGetShouldRetrieveValueCorrectly() {
		try {
			Map<String, String> hashes = new HashMap<>();
			hashes.put(KEY_2, VALUE_1);

			nativeConnection.hset(KEY_1, hashes).get();

			assertThat(clusterConnection.hGet(KEY_1_BYTES, KEY_2_BYTES)).isEqualTo(VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hIncrByFloatShouldIncreaseFieldCorretly() {
		try {
			Map<String, String> hashes = new HashMap<>();
			hashes.put(KEY_2, Long.toString(1L));
			hashes.put(KEY_3, Long.toString(2L));

			nativeConnection.hset(KEY_1, hashes).get();

			clusterConnection.hIncrBy(KEY_1_BYTES, KEY_3_BYTES, 3.5D);

			assertThat(nativeConnection.hget(KEY_1, KEY_3).get()).isEqualTo("5.5");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hIncrByShouldIncreaseFieldCorretly() {
		try {
			Map<String, String> hashes = new HashMap<>();
			hashes.put(KEY_2, Long.toString(1L));
			hashes.put(KEY_3, Long.toString(2L));

			nativeConnection.hset(KEY_1, hashes).get();

			clusterConnection.hIncrBy(KEY_1_BYTES, KEY_3_BYTES, 3);

			assertThat(nativeConnection.hget(KEY_1, KEY_3).get()).isEqualTo("5");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hKeysShouldRetrieveKeysCorrectly() {
		try {
			Map<String, String> hashes = new HashMap<>();
			hashes.put(KEY_2, VALUE_1);
			hashes.put(KEY_3, VALUE_2);

			nativeConnection.hset(KEY_1, hashes).get();

			assertThat(clusterConnection.hKeys(KEY_1_BYTES)).contains(KEY_2_BYTES, KEY_3_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hLenShouldRetrieveSizeCorrectly() {
		try {
			Map<String, String> hashes = new HashMap<>();
			hashes.put(KEY_2, VALUE_1);
			hashes.put(KEY_3, VALUE_2);

			nativeConnection.hset(KEY_1, hashes).get();

			assertThat(clusterConnection.hLen(KEY_1_BYTES)).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hMGetShouldRetrieveValueCorrectly() {
		try {
			Map<String, String> hashes = new HashMap<>();
			hashes.put(KEY_2, VALUE_1);
			hashes.put(KEY_3, VALUE_2);

			nativeConnection.hset(KEY_1, hashes).get();

			assertThat(clusterConnection.hMGet(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES)).contains(VALUE_1_BYTES, VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hMSetShouldAddValuesCorrectly() {
		try {
			Map<byte[], byte[]> hashes = new HashMap<>();
			hashes.put(KEY_2_BYTES, VALUE_1_BYTES);
			hashes.put(KEY_3_BYTES, VALUE_2_BYTES);

			clusterConnection.hMSet(KEY_1_BYTES, hashes);

			assertThat(nativeConnection.hmget(KEY_1, new String[]{KEY_2, KEY_3}).get()).contains(VALUE_1, VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-479
	public void hScanShouldReadEntireValueRange() {
		try {
			int nrOfValues = 321;
			Map<String, String> hashes = new HashMap<>();
			for (int i = 0; i < nrOfValues; i++) {
				hashes.put("key" + i, "value-" + i);
			}
			nativeConnection.hset(KEY_1, hashes).get();

			Cursor<Map.Entry<byte[], byte[]>> cursor = clusterConnection.hScan(KEY_1_BYTES,
					scanOptions().match("key*").build());

			int i = 0;
			while (cursor.hasNext()) {

				cursor.next();
				i++;
			}

			assertThat(i).isEqualTo(nrOfValues);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hSetNXShouldNotSetValueWhenAlreadyExists() {
		try {
			Map<String, String> hashes = new HashMap<>();
			hashes.put(KEY_2, VALUE_1);

			nativeConnection.hset(KEY_1, hashes).get();

			clusterConnection.hSetNX(KEY_1_BYTES, KEY_2_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.hget(KEY_1, KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hSetNXShouldSetValueCorrectly() {
		try {
			clusterConnection.hSetNX(KEY_1_BYTES, KEY_2_BYTES, VALUE_1_BYTES);

			assertThat(nativeConnection.hget(KEY_1, KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void hSetShouldSetValueCorrectly() {
		try {
			clusterConnection.hSet(KEY_1_BYTES, KEY_2_BYTES, VALUE_1_BYTES);

			assertThat(nativeConnection.hget(KEY_1, KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-698
	public void hStrLenReturnsFieldLength() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hStrLen(KEY_1_BYTES, KEY_2_BYTES))
					.isEqualTo(Long.valueOf(VALUE_3.length()));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-698
	public void hStrLenReturnsZeroWhenFieldDoesNotExist() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hStrLen(KEY_1_BYTES, KEY_3_BYTES)).isEqualTo(0L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-698
	public void hStrLenReturnsZeroWhenKeyDoesNotExist() {
		assertThat(clusterConnection.hashCommands().hStrLen(KEY_1_BYTES, KEY_1_BYTES)).isEqualTo(0L);
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hExpireReturnsSuccessAndSetsTTL() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hExpire(KEY_1_BYTES, 5L, KEY_2_BYTES)).contains(1L);
			assertThat(clusterConnection.hashCommands().hTtl(KEY_1_BYTES, KEY_2_BYTES))
					.allSatisfy(val -> assertThat(val).isBetween(0L, 5L));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hExpireReturnsMinusTwoWhenFieldDoesNotExist() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();
			// missing field
			assertThat(clusterConnection.hashCommands().hExpire(KEY_1_BYTES, 5L, KEY_1_BYTES)).contains(-2L);
			// missing key
			assertThat(clusterConnection.hashCommands().hExpire(KEY_2_BYTES, 5L, KEY_2_BYTES)).contains(-2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hExpireReturnsTwoWhenZeroProvided() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hExpire(KEY_1_BYTES, 0L, KEY_2_BYTES)).contains(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hpExpireReturnsSuccessAndSetsTTL() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hpExpire(KEY_1_BYTES, 5000L, KEY_2_BYTES)).contains(1L);
			assertThat(clusterConnection.hashCommands().hTtl(KEY_1_BYTES, TimeUnit.MILLISECONDS, KEY_2_BYTES))
					.allSatisfy(val -> assertThat(val).isBetween(0L, 5000L));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hpExpireReturnsMinusTwoWhenFieldDoesNotExist() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();
			// missing field
			assertThat(clusterConnection.hashCommands().hpExpire(KEY_1_BYTES, 5L, KEY_1_BYTES)).contains(-2L);
			// missing key
			assertThat(clusterConnection.hashCommands().hpExpire(KEY_2_BYTES, 5L, KEY_2_BYTES)).contains(-2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hpExpireReturnsTwoWhenZeroProvided() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hpExpire(KEY_1_BYTES, 0L, KEY_2_BYTES)).contains(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hExpireAtReturnsSuccessAndSetsTTL() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();
			long inFiveSeconds = Instant.now().plusSeconds(5L).getEpochSecond();

			assertThat(clusterConnection.hashCommands().hExpireAt(KEY_1_BYTES, inFiveSeconds, KEY_2_BYTES)).contains(1L);
			assertThat(clusterConnection.hashCommands().hTtl(KEY_1_BYTES, KEY_2_BYTES))
					.allSatisfy(val -> assertThat(val).isBetween(0L, 5L));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hExpireAtReturnsMinusTwoWhenFieldDoesNotExist() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();
			long inFiveSeconds = Instant.now().plusSeconds(5L).getEpochSecond();

			// missing field
			assertThat(clusterConnection.hashCommands().hExpireAt(KEY_1_BYTES, inFiveSeconds, KEY_1_BYTES)).contains(-2L);
			// missing key
			assertThat(clusterConnection.hashCommands().hExpireAt(KEY_2_BYTES, inFiveSeconds, KEY_2_BYTES)).contains(-2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hExpireAdReturnsTwoWhenZeroProvided() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hExpireAt(KEY_1_BYTES, 0L, KEY_2_BYTES)).contains(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hpExpireAtReturnsSuccessAndSetsTTL() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();
			long inFiveSeconds = Instant.now().plusSeconds(5L).toEpochMilli();

			assertThat(clusterConnection.hashCommands().hpExpireAt(KEY_1_BYTES, inFiveSeconds, KEY_2_BYTES)).contains(1L);
			assertThat(clusterConnection.hashCommands().hTtl(KEY_1_BYTES, TimeUnit.MILLISECONDS, KEY_2_BYTES))
					.allSatisfy(val -> assertThat(val).isBetween(0L, 5000L));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hpExpireAtReturnsMinusTwoWhenFieldDoesNotExist() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();
			long inFiveSeconds = Instant.now().plusSeconds(5L).toEpochMilli();

			// missing field
			assertThat(clusterConnection.hashCommands().hpExpireAt(KEY_1_BYTES, inFiveSeconds, KEY_1_BYTES)).contains(-2L);
			// missing key
			assertThat(clusterConnection.hashCommands().hpExpireAt(KEY_2_BYTES, inFiveSeconds, KEY_2_BYTES)).contains(-2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hpExpireAdReturnsTwoWhenZeroProvided() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hpExpireAt(KEY_1_BYTES, 0L, KEY_2_BYTES)).contains(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hPersistReturnsSuccessAndPersistsField() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hExpire(KEY_1_BYTES, 5L, KEY_2_BYTES)).contains(1L);
			assertThat(clusterConnection.hashCommands().hPersist(KEY_1_BYTES, KEY_2_BYTES)).contains(1L);
			assertThat(clusterConnection.hashCommands().hTtl(KEY_1_BYTES, KEY_2_BYTES)).contains(-1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hPersistReturnsMinusOneWhenFieldDoesNotHaveExpiration() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();
			assertThat(clusterConnection.hashCommands().hPersist(KEY_1_BYTES, KEY_2_BYTES)).contains(-1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hPersistReturnsMinusTwoWhenFieldOrKeyMissing() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hPersist(KEY_1_BYTES, KEY_1_BYTES)).contains(-2L);
			assertThat(clusterConnection.hashCommands().hPersist(KEY_3_BYTES, KEY_2_BYTES)).contains(-2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hTtlReturnsMinusOneWhenFieldHasNoExpiration() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_3)).get();

			assertThat(clusterConnection.hashCommands().hTtl(KEY_1_BYTES, KEY_2_BYTES)).contains(-1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3054
	@EnabledOnCommand("HEXPIRE")
	public void hTtlReturnsMinusTwoWhenFieldOrKeyMissing() {
		assertThat(clusterConnection.hashCommands().hTtl(KEY_1_BYTES, KEY_1_BYTES)).contains(-2L);
		assertThat(clusterConnection.hashCommands().hTtl(KEY_3_BYTES, KEY_2_BYTES)).contains(-2L);
	}

	@Test // DATAREDIS-315
	public void hValsShouldRetrieveValuesCorrectly() {
		try {
			nativeConnection.hset(KEY_1, Map.of(KEY_2, VALUE_1, KEY_3, VALUE_2)).get();

			assertThat(clusterConnection.hVals(KEY_1_BYTES)).contains(VALUE_1_BYTES, VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void incrByFloatShouldIncreaseValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, "1").get();

			assertThat(clusterConnection.incrBy(KEY_1_BYTES, 5.5D)).isEqualTo(6.5D);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void incrByShouldIncreaseValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, "1").get();

			assertThat(clusterConnection.incrBy(KEY_1_BYTES, 5)).isEqualTo(6L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void incrShouldIncreaseValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, "1").get();

			assertThat(clusterConnection.incr(KEY_1_BYTES)).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void infoShouldCollectInfoForSpecificNode() {
		Properties properties = clusterConnection.info(new ValkeyClusterNode(CLUSTER_HOST, MASTER_NODE_2_PORT));

		assertThat(properties.getProperty("tcp_port")).isEqualTo(Integer.toString(MASTER_NODE_2_PORT));
	}

	@Test // DATAREDIS-315
	public void infoShouldCollectInfoForSpecificNodeAndSection() {

		Properties properties = clusterConnection.info(new ValkeyClusterNode(CLUSTER_HOST, MASTER_NODE_2_PORT), "server");

		assertThat(properties.getProperty("tcp_port")).isEqualTo(Integer.toString(MASTER_NODE_2_PORT));
		assertThat(properties.getProperty("used_memory")).isNull();
	}

	@Test // DATAREDIS-315, DATAREDIS-685
	public void infoShouldCollectionInfoFromAllClusterNodes() {

		Properties singleNodeInfo = clusterConnection.serverCommands().info(new ValkeyClusterNode("127.0.0.1", 7380));
		assertThat(Double.valueOf(clusterConnection.serverCommands().info().size())).isCloseTo(singleNodeInfo.size() * 3,
				offset(12d));
	}

	@Test // DATAREDIS-315
	public void keysShouldReturnAllKeys() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			assertThat(clusterConnection.keys(ValkeyGlideConverters.toBytes("*"))).contains(KEY_1_BYTES, KEY_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void keysShouldReturnAllKeysForSpecificNode() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			Set<byte[]> keysOnNode = clusterConnection.keys(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()),
				ValkeyGlideConverters.toBytes("*"));

			assertThat(keysOnNode).contains(KEY_2_BYTES).doesNotContain(KEY_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-635
	public void scanShouldReturnAllKeys() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			// TODO: Validate keys and values when SCAN across cluster nodes is supported
			// https://github.com/ikolomi/spring-data-valkey/issues/21
			assertThatExceptionOfType(InvalidDataAccessApiUsageException.class)
					.isThrownBy(() -> clusterConnection.scan(ScanOptions.NONE));

		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Override // DATAREDIS-635
	public void scanShouldReturnAllKeysForSpecificNode() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			Cursor<byte[]> cursor = clusterConnection.scan(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()), NONE);

			List<byte[]> keysOnNode = new ArrayList<>();
			cursor.forEachRemaining(keysOnNode::add);

			assertThat(keysOnNode).contains(KEY_2_BYTES).doesNotContain(KEY_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lIndexShouldGetElementAtIndexCorrectly() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{VALUE_1, VALUE_2, "foo", "bar"}).get();

			assertThat(clusterConnection.lIndex(KEY_1_BYTES, 1)).isEqualTo(VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lInsertShouldAddElementAtPositionCorrectly() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{VALUE_1, VALUE_2, "foo", "bar"}).get();

			clusterConnection.lInsert(KEY_1_BYTES, Position.AFTER, VALUE_2_BYTES, ValkeyGlideConverters.toBytes("booh"));

			assertThat(nativeConnection.lrange(KEY_1, 0, -1).get()[2]).isEqualTo("booh");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2039
	@EnabledOnCommand("LMOVE")
	public void lMoveShouldMoveElementsCorrectly() {
		try {
			nativeConnection.rpush(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.lMove(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES, Direction.RIGHT, Direction.LEFT))
					.isEqualTo(VALUE_3_BYTES);
			assertThat(clusterConnection.lMove(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES, Direction.RIGHT, Direction.LEFT))
					.isEqualTo(VALUE_2_BYTES);

			assertThat(nativeConnection.lrange(SAME_SLOT_KEY_1, 0, -1).get()).containsExactly(VALUE_1);
			assertThat(nativeConnection.lrange(SAME_SLOT_KEY_2, 0, -1).get()).containsExactly(VALUE_2, VALUE_3);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2039
	@EnabledOnCommand("BLMOVE")
	public void blMoveShouldMoveElementsCorrectly() {
		try {
			nativeConnection.rpush(SAME_SLOT_KEY_1, new String[]{VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.lMove(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES, Direction.RIGHT, Direction.LEFT))
					.isEqualTo(VALUE_3_BYTES);
			assertThat(clusterConnection.lMove(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES, Direction.RIGHT, Direction.LEFT))
					.isEqualTo(VALUE_2_BYTES);
			assertThat(
					clusterConnection.bLMove(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES, Direction.RIGHT, Direction.LEFT, 0.01))
					.isNull();

			assertThat(nativeConnection.lrange(SAME_SLOT_KEY_1, 0, -1).get()).isEmpty();
			assertThat(nativeConnection.lrange(SAME_SLOT_KEY_2, 0, -1).get()).containsExactly(VALUE_2, VALUE_3);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lLenShouldCountValuesCorrectly() {
		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.lLen(KEY_1_BYTES)).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lPopShouldReturnElementCorrectly() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.lPop(KEY_1_BYTES)).isEqualTo(VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lPushNXShouldNotAddValuesWhenKeyDoesNotExist() {
		try {
			clusterConnection.lPushX(KEY_1_BYTES, VALUE_1_BYTES);

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(0);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lPushShouldAddValuesCorrectly() {
		try {
			clusterConnection.lPush(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.lrange(KEY_1, 0, -1).get()).contains(VALUE_1, VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lRangeShouldGetValuesCorrectly() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.lRange(KEY_1_BYTES, 0L, -1L)).contains(VALUE_1_BYTES, VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lRemShouldRemoveElementAtPositionCorrectly() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{VALUE_1, VALUE_2, "foo", "bar"}).get();

			clusterConnection.lRem(KEY_1_BYTES, 1L, VALUE_1_BYTES);

			assertThat(nativeConnection.llen(KEY_1).get()).isEqualTo(3L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lSetShouldSetElementAtPositionCorrectly() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{VALUE_1, VALUE_2, "foo", "bar"}).get();

			clusterConnection.lSet(KEY_1_BYTES, 1L, VALUE_1_BYTES);

			assertThat(nativeConnection.lrange(KEY_1, 0, -1).get()[1]).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void lTrimShouldTrimListCorrectly() {
		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_1, VALUE_2, "foo", "bar"}).get();

			clusterConnection.lTrim(KEY_1_BYTES, 2, 3);

			assertThat(nativeConnection.lrange(KEY_1, 0, -1).get()).contains(VALUE_1, VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void mGetShouldReturnCorrectlyWhenKeysDoNotMapToSameSlot() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			assertThat(clusterConnection.mGet(KEY_1_BYTES, KEY_2_BYTES)).containsExactly(VALUE_1_BYTES, VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-756
	public void mGetShouldReturnMultipleSameKeysWhenKeysDoNotMapToSameSlot() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();
			nativeConnection.set(KEY_3, VALUE_3).get();

			List<byte[]> result = clusterConnection.mGet(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES, KEY_1_BYTES);
			assertThat(result).containsExactly(VALUE_1_BYTES, VALUE_2_BYTES, VALUE_3_BYTES, VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void mGetShouldReturnCorrectlyWhenKeysMapToSameSlot() {
		try {
			nativeConnection.set(SAME_SLOT_KEY_1, VALUE_1).get();
			nativeConnection.set(SAME_SLOT_KEY_2, VALUE_2).get();

			assertThat(clusterConnection.mGet(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).containsExactly(VALUE_1_BYTES,
					VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-756
	public void mGetShouldReturnMultipleSameKeysWhenKeysMapToSameSlot() {
		try {
			nativeConnection.set(SAME_SLOT_KEY_1, VALUE_1).get();
			nativeConnection.set(SAME_SLOT_KEY_2, VALUE_2).get();

			List<byte[]> result = clusterConnection.mGet(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES, SAME_SLOT_KEY_1_BYTES);
			assertThat(result).containsExactly(VALUE_1_BYTES, VALUE_2_BYTES, VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void mSetNXShouldReturnFalseIfNotAllKeysSet() {
		try {
			nativeConnection.set(KEY_2, VALUE_3).get();
			Map<byte[], byte[]> map = new LinkedHashMap<>();
			map.put(KEY_1_BYTES, VALUE_1_BYTES);
			map.put(KEY_2_BYTES, VALUE_2_BYTES);

			assertThat(clusterConnection.mSetNX(map)).isFalse();

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
			assertThat(nativeConnection.get(KEY_2).get()).isEqualTo(VALUE_3);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void mSetNXShouldReturnTrueIfAllKeysSet() {
		try {
			Map<byte[], byte[]> map = new LinkedHashMap<>();
			map.put(KEY_1_BYTES, VALUE_1_BYTES);
			map.put(KEY_2_BYTES, VALUE_2_BYTES);

			assertThat(clusterConnection.mSetNX(map)).isTrue();

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
			assertThat(nativeConnection.get(KEY_2).get()).isEqualTo(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void mSetNXShouldWorkForOnSameSlotKeys() {
		try {
			Map<byte[], byte[]> map = new LinkedHashMap<>();
			map.put(SAME_SLOT_KEY_1_BYTES, VALUE_1_BYTES);
			map.put(SAME_SLOT_KEY_2_BYTES, VALUE_2_BYTES);

			assertThat(clusterConnection.mSetNX(map)).isTrue();

			assertThat(nativeConnection.get(SAME_SLOT_KEY_1).get()).isEqualTo(VALUE_1);
			assertThat(nativeConnection.get(SAME_SLOT_KEY_2).get()).isEqualTo(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void mSetShouldWorkWhenKeysDoNotMapToSameSlot() {
		try {
			Map<byte[], byte[]> map = new LinkedHashMap<>();
			map.put(KEY_1_BYTES, VALUE_1_BYTES);
			map.put(KEY_2_BYTES, VALUE_2_BYTES);

			clusterConnection.mSet(map);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
			assertThat(nativeConnection.get(KEY_2).get()).isEqualTo(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void mSetShouldWorkWhenKeysMapToSameSlot() {
		try {
			Map<byte[], byte[]> map = new LinkedHashMap<>();
			map.put(SAME_SLOT_KEY_1_BYTES, VALUE_1_BYTES);
			map.put(SAME_SLOT_KEY_2_BYTES, VALUE_2_BYTES);

			clusterConnection.mSet(map);

			assertThat(nativeConnection.get(SAME_SLOT_KEY_1).get()).isEqualTo(VALUE_1);
			assertThat(nativeConnection.get(SAME_SLOT_KEY_2).get()).isEqualTo(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void moveShouldNotBeSupported() {
		assertThatExceptionOfType(InvalidDataAccessApiUsageException.class)
				.isThrownBy(() -> clusterConnection.move(KEY_1_BYTES, 3));
	}

	@Test // DATAREDIS-315
	public void multiShouldThrowException() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(clusterConnection::multi);
	}

	@Test // DATAREDIS-315
	public void pExpireAtShouldBeSetCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.pExpireAt(KEY_1_BYTES, System.currentTimeMillis() + 5000);

			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3114
	@EnabledOnCommand("SPUBLISH") // Valkey 7.0
	public void pExpireAtWithConditionShouldBeSetCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(
					clusterConnection.pExpireAt(KEY_1_BYTES, System.currentTimeMillis() + 5000, ExpirationOptions.Condition.XX))
					.isFalse();
			assertThat(
					clusterConnection.pExpireAt(KEY_1_BYTES, System.currentTimeMillis() + 5000, ExpirationOptions.Condition.NX))
					.isTrue();
			assertThat(
					clusterConnection.pExpireAt(KEY_1_BYTES, System.currentTimeMillis() + 15000, ExpirationOptions.Condition.LT))
					.isFalse();

			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pExpireShouldBeSetCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.pExpire(KEY_1_BYTES, 5000);

			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-3114
	@EnabledOnCommand("SPUBLISH") // Valkey 7.0
	public void pExpireWithConditionShouldBeSetCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.pExpire(KEY_1_BYTES, 15000, ExpirationOptions.Condition.XX)).isFalse();
			assertThat(clusterConnection.pExpire(KEY_1_BYTES, 15000, ExpirationOptions.Condition.NX)).isTrue();
			assertThat(clusterConnection.pExpire(KEY_1_BYTES, 15000, ExpirationOptions.Condition.LT)).isFalse();

			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pSetExShouldSetValueCorrectly() {
		try {
			clusterConnection.pSetEx(KEY_1_BYTES, 5000, VALUE_1_BYTES);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		}
		catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pTtlShouldReturnValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.expire(KEY_1, 5).get();

			assertThat(clusterConnection.pTtl(KEY_1_BYTES)).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pTtlShouldReturnMinusOneWhenKeyDoesNotHaveExpirationSet() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.pTtl(KEY_1_BYTES)).isEqualTo(-1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pTtlShouldReturnMinusTwoWhenKeyDoesNotExist() {
		assertThat(clusterConnection.pTtl(KEY_1_BYTES)).isEqualTo(-2L);
	}

	@Test // DATAREDIS-526
	public void pTtlWithTimeUnitShouldReturnMinusTwoWhenKeyDoesNotExist() {
		assertThat(clusterConnection.pTtl(KEY_1_BYTES, TimeUnit.HOURS)).isEqualTo(-2L);
	}

	@Test // DATAREDIS-315
	public void persistShouldRemoveTTL() {
		try {
			nativeConnection.set(KEY_1, VALUE_1, SetOptions.builder().expiry(Expiry.Seconds(10L)).build()).get(); 

			assertThat(clusterConnection.persist(KEY_1_BYTES)).isTrue();
			assertThat(nativeConnection.ttl(KEY_1).get()).isEqualTo(-1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pfAddShouldAddValuesCorrectly() {
		try {
			clusterConnection.pfAdd(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES, VALUE_3_BYTES);

			assertThat(nativeConnection.pfcount(new String[]{KEY_1}).get()).isEqualTo(3L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pfCountShouldAllowCountingOnSameSlotKeys() {
		try {
			nativeConnection.pfadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.pfadd(SAME_SLOT_KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.pfCount(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).isEqualTo(3L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pfCountShouldAllowCountingOnSingleKey() {
		try {
			nativeConnection.pfadd(KEY_1, new String[]{VALUE_1, VALUE_2, VALUE_3}).get();
			assertThat(clusterConnection.pfCount(KEY_1_BYTES)).isEqualTo(3L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pfCountShouldThrowErrorCountingOnDifferentSlotKeys() {
		try {
			nativeConnection.pfadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.pfadd(KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			assertThatExceptionOfType(DataAccessException.class)
					.isThrownBy(() -> clusterConnection.pfCount(KEY_1_BYTES, KEY_2_BYTES));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pfMergeShouldThrowErrorOnDifferentSlotKeys() {
		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.pfMerge(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
	}

	@Test // DATAREDIS-315
	public void pfMergeShouldWorkWhenAllKeysMapToSameSlot() {
		try {
			nativeConnection.pfadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.pfadd(SAME_SLOT_KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			clusterConnection.pfMerge(SAME_SLOT_KEY_3_BYTES, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.pfcount(new String[]{SAME_SLOT_KEY_3}).get()).isEqualTo(3L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void pingShouldRetrunPong() {
		assertThat(clusterConnection.ping()).isEqualTo("PONG");
	}

	@Test // DATAREDIS-315
	public void pingShouldRetrunPongForExistingNode() {
		assertThat(clusterConnection.ping(new ValkeyClusterNode("127.0.0.1", 7379, SlotRange.empty()))).isEqualTo("PONG");
	}

	@Test // DATAREDIS-315
	public void pingShouldThrowExceptionWhenNodeNotKnownToCluster() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> clusterConnection.ping(new ValkeyClusterNode("127.0.0.1", 1234, null)));
	}

	@Test // DATAREDIS-315
	public void rPopLPushShouldWorkWhenDoNotMapToSameSlot() {
		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.rPopLPush(KEY_1_BYTES, KEY_2_BYTES)).isEqualTo(VALUE_1_BYTES);
			assertThat(nativeConnection.exists(new String[]{KEY_2}).get()).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void rPopLPushShouldWorkWhenKeysOnSameSlot() {
		try {
			nativeConnection.lpush(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.rPopLPush(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).isEqualTo(VALUE_1_BYTES);
			assertThat(nativeConnection.exists(new String[]{SAME_SLOT_KEY_2}).get()).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void rPopShouldReturnElementCorrectly() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.rPop(KEY_1_BYTES)).isEqualTo(VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void rPushNXShouldNotAddValuesWhenKeyDoesNotExist() {
		try {
			clusterConnection.rPushX(KEY_1_BYTES, VALUE_1_BYTES);

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(0L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void rPushShouldAddValuesCorrectly() {
		try {
			clusterConnection.rPush(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.lrange(KEY_1, 0, -1).get()).contains(VALUE_1, VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void randomKeyShouldReturnCorrectlyWhenKeysAvailable() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			assertThat(clusterConnection.randomKey()).isNotNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void randomKeyShouldReturnNullWhenNoKeysAvailable() {
		assertThat(clusterConnection.randomKey()).isNull();
	}

	@Test // DATAREDIS-315
	public void rename() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.rename(KEY_1_BYTES, KEY_2_BYTES);

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(0L);
			assertThat(nativeConnection.get(KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-1190
	public void renameShouldOverwriteTargetKey() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			clusterConnection.rename(KEY_1_BYTES, KEY_2_BYTES);

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(0L);
			assertThat(nativeConnection.get(KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void renameNXWhenOnSameSlot() {
		try {
			nativeConnection.set(SAME_SLOT_KEY_1, VALUE_1).get();

			assertThat(clusterConnection.renameNX(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).isTrue();

			assertThat(nativeConnection.exists(new String[]{SAME_SLOT_KEY_1}).get()).isEqualTo(0L);
			assertThat(nativeConnection.get(SAME_SLOT_KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void renameNXWhenTargetKeyDoesExist() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_2).get();

			assertThat(clusterConnection.renameNX(KEY_1_BYTES, KEY_2_BYTES)).isFalse();

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
			assertThat(nativeConnection.get(KEY_2).get()).isEqualTo(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void renameNXWhenTargetKeyDoesNotExist() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.renameNX(KEY_1_BYTES, KEY_2_BYTES)).isTrue();

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(0L);
			assertThat(nativeConnection.get(KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void renameSameKeysOnSameSlot() {
		try {
			nativeConnection.set(SAME_SLOT_KEY_1, VALUE_1).get();

			clusterConnection.rename(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.exists(new String[]{SAME_SLOT_KEY_1}).get()).isEqualTo(0L);
			assertThat(nativeConnection.get(SAME_SLOT_KEY_2).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sAddShouldAddValueToSetCorrectly() {
		try {
			clusterConnection.sAdd(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.smembers(KEY_1).get()).contains(VALUE_1, VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sCardShouldCountValuesInSetCorrectly() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.sCard(KEY_1_BYTES)).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sDiffShouldWorkWhenKeysMapToSameSlot() {
		try {
			nativeConnection.sadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(SAME_SLOT_KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.sDiff(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).contains(VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315, DATAREDIS-647
	public void sDiffShouldWorkWhenKeysNotMapToSameSlot() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(KEY_2, new String[]{VALUE_2, VALUE_3}).get();
			nativeConnection.sadd(KEY_3, new String[]{VALUE_1, VALUE_3}).get();

			assertThat(clusterConnection.sDiff(KEY_1_BYTES, KEY_2_BYTES)).contains(VALUE_1_BYTES);
			assertThat(clusterConnection.sDiff(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES)).isEmpty();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sDiffStoreShouldWorkWhenKeysMapToSameSlot() {
		try {
			nativeConnection.sadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(SAME_SLOT_KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			clusterConnection.sDiffStore(SAME_SLOT_KEY_3_BYTES, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.smembers(SAME_SLOT_KEY_3).get()).contains(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sDiffStoreShouldWorkWhenKeysNotMapToSameSlot() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			clusterConnection.sDiffStore(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES);

			assertThat(nativeConnection.smembers(KEY_3).get()).contains(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sInterShouldWorkForKeysMappingToSameSlot() {
		try {
			nativeConnection.sadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(SAME_SLOT_KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.sInter(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).contains(VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sInterShouldWorkForKeysNotMappingToSameSlot() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.sInter(KEY_1_BYTES, KEY_2_BYTES)).contains(VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sInterStoreShouldWorkForKeysMappingToSameSlot() {
		try {
			nativeConnection.sadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(SAME_SLOT_KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			clusterConnection.sInterStore(SAME_SLOT_KEY_3_BYTES, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.smembers(SAME_SLOT_KEY_3).get()).contains(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sInterStoreShouldWorkForKeysNotMappingToSameSlot() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			clusterConnection.sInterStore(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES);

			assertThat(nativeConnection.smembers(KEY_3).get()).contains(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sIsMemberShouldReturnFalseIfValueIsMemberOfSet() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.sIsMember(KEY_1_BYTES, ValkeyGlideConverters.toBytes("foo"))).isFalse();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sIsMemberShouldReturnTrueIfValueIsMemberOfSet() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.sIsMember(KEY_1_BYTES, VALUE_1_BYTES)).isTrue();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2037
	@EnabledOnCommand("SMISMEMBER")
	public void sMIsMemberShouldReturnCorrectValues() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.sMIsMember(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES, VALUE_3_BYTES))
					.containsExactly(true, true, false);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sMembersShouldReturnValuesContainedInSetCorrectly() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.sMembers(KEY_1_BYTES)).contains(VALUE_1_BYTES, VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sMoveShouldWorkWhenKeysDoNotMapToSameSlot() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(KEY_2, new String[]{VALUE_3}).get();

			clusterConnection.sMove(KEY_1_BYTES, KEY_2_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.sismember(KEY_1, VALUE_2).get()).isFalse();
			assertThat(nativeConnection.sismember(KEY_2, VALUE_2).get()).isTrue();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sMoveShouldWorkWhenKeysMapToSameSlot() {
		try {
			nativeConnection.sadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(SAME_SLOT_KEY_2, new String[]{VALUE_3}).get();

			clusterConnection.sMove(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.sismember(SAME_SLOT_KEY_1, VALUE_2).get()).isFalse();
			assertThat(nativeConnection.sismember(SAME_SLOT_KEY_2, VALUE_2).get()).isTrue();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sPopShouldPopValueFromSetCorrectly() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.sPop(KEY_1_BYTES)).isNotNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-668
	void sPopWithCountShouldPopValueFromSetCorrectly() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.setCommands().sPop(KEY_1_BYTES, 2)).hasSize(2);
			assertThat(nativeConnection.scard(KEY_1).get()).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sRandMamberShouldReturnValueCorrectly() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.sRandMember(KEY_1_BYTES)).isNotNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sRandMamberWithCountShouldReturnValueCorrectly() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			assertThat(clusterConnection.sRandMember(KEY_1_BYTES, 3)).isNotNull();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sRemShouldRemoveValueFromSetCorrectly() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();

			clusterConnection.sRem(KEY_1_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.smembers(KEY_1).get()).contains(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sUnionShouldWorkForKeysMappingToSameSlot() {
		try {
			nativeConnection.sadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(SAME_SLOT_KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.sUnion(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).contains(VALUE_1_BYTES,
					VALUE_2_BYTES, VALUE_3_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sUnionShouldWorkForKeysNotMappingToSameSlot() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			assertThat(clusterConnection.sUnion(KEY_1_BYTES, KEY_2_BYTES)).contains(VALUE_1_BYTES, VALUE_2_BYTES,
					VALUE_3_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sUnionStoreShouldWorkForKeysMappingToSameSlot() {
		try {
			nativeConnection.sadd(SAME_SLOT_KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(SAME_SLOT_KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			clusterConnection.sUnionStore(SAME_SLOT_KEY_3_BYTES, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.smembers(SAME_SLOT_KEY_3).get()).contains(VALUE_1, VALUE_2, VALUE_3);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sUnionStoreShouldWorkForKeysNotMappingToSameSlot() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1, VALUE_2}).get();
			nativeConnection.sadd(KEY_2, new String[]{VALUE_2, VALUE_3}).get();

			clusterConnection.sUnionStore(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES);

			assertThat(nativeConnection.smembers(KEY_3).get()).contains(VALUE_1, VALUE_2, VALUE_3);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void selectShouldAllowSelectionOfDBIndexZero() {
		clusterConnection.select(0);
	}

	@Test // DATAREDIS-315
	public void selectShouldThrowExceptionWhenSelectingNonZeroDbIndex() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> clusterConnection.select(1));
	}

	@Test // DATAREDIS-315
	public void setBitShouldWorkCorrectly() {
		try {
			clusterConnection.setBit(KEY_1_BYTES, 0, true);
			clusterConnection.setBit(KEY_1_BYTES, 1, false);

			assertThat(nativeConnection.getbit(KEY_1, 0).get()).isEqualTo(1);
			assertThat(nativeConnection.getbit(KEY_1, 1).get()).isEqualTo(0);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void setExShouldSetValueCorrectly() {
		try {
			clusterConnection.setEx(KEY_1_BYTES, 5, VALUE_1_BYTES);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
			assertThat(nativeConnection.ttl(KEY_1).get()).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void setNxShouldNotSetValueWhenAlreadyExistsInDBCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.setNX(KEY_1_BYTES, VALUE_2_BYTES);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void setNxShouldSetValueCorrectly() {
		try {
			clusterConnection.setNX(KEY_1_BYTES, VALUE_1_BYTES);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void setRangeShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.setRange(KEY_1_BYTES, ValkeyGlideConverters.toBytes("UE"), 3);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo("valUE1");
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void setShouldSetValueCorrectly() {
		try {
			clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES);

			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-316
	public void setWithExpirationAndIfAbsentShouldNotBeAppliedWhenKeyExists() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.set(KEY_1_BYTES, VALUE_2_BYTES, Expiration.seconds(1), SetOption.ifAbsent());

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(1L);
			assertThat(nativeConnection.ttl(KEY_1).get()).isEqualTo(-1L);
			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-316
	public void setWithExpirationAndIfAbsentShouldWorkCorrectly() {
		try {
			clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES, Expiration.seconds(1), SetOption.ifAbsent());

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(1L);
			assertThat(nativeConnection.ttl(KEY_1).get()).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-316
	public void setWithExpirationAndIfPresentShouldNotBeAppliedWhenKeyDoesNotExists() {
		try {
			clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES, Expiration.seconds(1), SetOption.ifPresent());

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(0);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-316
	public void setWithExpirationAndIfPresentShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			clusterConnection.set(KEY_1_BYTES, VALUE_2_BYTES, Expiration.seconds(1), SetOption.ifPresent());

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(1L);
			assertThat(nativeConnection.ttl(KEY_1).get()).isEqualTo(1L);
			assertThat(nativeConnection.get(KEY_1).get()).isEqualTo(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-316
	public void setWithExpirationInMillisecondsShouldWorkCorrectly() {
		try {
			clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES, Expiration.milliseconds(500), SetOption.upsert());

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(1L);
			assertThat(nativeConnection.pttl(KEY_1).get()).isCloseTo(500L, offset(499L));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-316
	public void setWithExpirationInSecondsShouldWorkCorrectly() {
		try {
			clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES, Expiration.seconds(1), SetOption.upsert());

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(1L);
			assertThat(nativeConnection.ttl(KEY_1).get()).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-316
	public void setWithOptionIfAbsentShouldWorkCorrectly() {
		try {
			clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES, Expiration.persistent(), SetOption.ifAbsent());

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(1L);
			assertThat(nativeConnection.ttl(KEY_1).get()).isEqualTo(-1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-316, DATAREDIS-588
	public void setWithOptionIfPresentShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			clusterConnection.set(KEY_1_BYTES, VALUE_2_BYTES, Expiration.persistent(), SetOption.ifPresent());

			assertThat(nativeConnection.exists(new String[]{KEY_1}).get()).isEqualTo(1L);
			assertThat(clusterConnection.get(KEY_1_BYTES)).isEqualTo(VALUE_2_BYTES);
			assertThat(nativeConnection.ttl(KEY_1).get()).isEqualTo(-1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void shouldAllowSettingAndGettingValues() {

		clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES);
		assertThat(clusterConnection.get(KEY_1_BYTES)).isEqualTo(VALUE_1_BYTES);
	}

	@Test // DATAREDIS-315
	public void sortAndStoreShouldAddSortedValuesValuesCorrectly() {
		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_2, VALUE_1}).get();

			assertThat(clusterConnection.sort(KEY_1_BYTES, new DefaultSortParameters().alpha(), KEY_2_BYTES)).isEqualTo(2L);
			assertThat(nativeConnection.exists(new String[]{KEY_2}).get()).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315, GH-2341
	public void sortAndStoreShouldReplaceDestinationList() {
		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_2, VALUE_1}).get();
			nativeConnection.lpush(KEY_2, new String[]{VALUE_3}).get();

			assertThat(clusterConnection.sort(KEY_1_BYTES, new DefaultSortParameters().alpha(), KEY_2_BYTES)).isEqualTo(2L);
			assertThat(nativeConnection.llen(KEY_2).get()).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sortShouldReturnValuesCorrectly() {
		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_2, VALUE_1}).get();

			assertThat(clusterConnection.sort(KEY_1_BYTES, new DefaultSortParameters().alpha())).contains(VALUE_1_BYTES,
					VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void sscanShouldRetrieveAllValuesInSetCorrectly() {
		try {
			for (int i = 0; i < 30; i++) {
				nativeConnection.sadd(KEY_1, new String[]{String.valueOf(i)}).get();
			}

			int count = 0;
			Cursor<byte[]> cursor = clusterConnection.sScan(KEY_1_BYTES, ScanOptions.NONE);
			while (cursor.hasNext()) {
				count++;
				cursor.next();
			}

			assertThat(count).isEqualTo(30);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void strLenShouldWorkCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.strLen(KEY_1_BYTES)).isEqualTo(6L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void ttlShouldReturnMinusOneWhenKeyDoesNotHaveExpirationSet() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();

			assertThat(clusterConnection.ttl(KEY_1_BYTES)).isEqualTo(-1L);
		}
		catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void ttlShouldReturnMinusTwoWhenKeyDoesNotExist() {
		assertThat(clusterConnection.ttl(KEY_1_BYTES)).isEqualTo(-2L);
	}

	@Test // DATAREDIS-315
	public void ttlShouldReturnValueCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.expire(KEY_1, 5).get();

			assertThat(clusterConnection.ttl(KEY_1_BYTES)).isGreaterThan(1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-526
	public void ttlWithTimeUnitShouldReturnMinusTwoWhenKeyDoesNotExist() {
		assertThat(clusterConnection.ttl(KEY_1_BYTES, TimeUnit.HOURS)).isEqualTo(-2L);
	}

	@Test // DATAREDIS-315
	public void typeShouldReadKeyTypeCorrectly() {
		try {
			nativeConnection.sadd(KEY_1, new String[]{VALUE_1}).get();
			nativeConnection.set(KEY_2, VALUE_2).get();
			nativeConnection.hset(KEY_3, Collections.singletonMap(KEY_1, VALUE_1)).get();

			assertThat(clusterConnection.type(KEY_1_BYTES)).isEqualTo(DataType.SET);
			assertThat(clusterConnection.type(KEY_2_BYTES)).isEqualTo(DataType.STRING);
			assertThat(clusterConnection.type(KEY_3_BYTES)).isEqualTo(DataType.HASH);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void unwatchShouldThrowException() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(clusterConnection::unwatch);
	}

	@Test // DATAREDIS-315
	public void watchShouldThrowException() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(clusterConnection::watch);
	}

	@Test // DATAREDIS-674
	void zAddShouldAddMultipleValuesWithScoreCorrectly() {
		try {
			Set<Tuple> tuples = new HashSet<>();
			tuples.add(new DefaultTuple(VALUE_1_BYTES, 10D));
			tuples.add(new DefaultTuple(VALUE_2_BYTES, 20D));

			clusterConnection.zAdd(KEY_1_BYTES, tuples);

			assertThat(nativeConnection.zcard(KEY_1).get()).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zAddShouldAddValueWithScoreCorrectly() {
		try {
			clusterConnection.zAdd(KEY_1_BYTES, 10D, VALUE_1_BYTES);
			clusterConnection.zAdd(KEY_1_BYTES, 20D, VALUE_2_BYTES);

			assertThat(nativeConnection.zcard(KEY_1).get()).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zCardShouldReturnTotalNumberOfValues() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zCard(KEY_1_BYTES)).isEqualTo(3L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zCountShouldCountValuesInRange() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zCount(KEY_1_BYTES, 10, 20)).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zIncrByShouldIncScoreForValueCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();

			clusterConnection.zIncrBy(KEY_1_BYTES, 100D, VALUE_1_BYTES);

			assertThat(nativeConnection.zrank(KEY_1, VALUE_1).get()).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2041
	public void zDiffShouldThrowExceptionWhenKeysDoNotMapToSameSlots() {

		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zDiff(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zDiffStore(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zDiffWithScores(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
	}

	@Test // GH-2041
	@EnabledOnCommand("ZDIFF")
	public void zDiffShouldWorkForSameSlotKeys() {
		try {
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_2, 20D)).get();

			nativeConnection.zadd(SAME_SLOT_KEY_2, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zDiff(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).contains(VALUE_1_BYTES);
			assertThat(clusterConnection.zDiffWithScores(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES))
					.contains(new DefaultTuple(VALUE_1_BYTES, 10D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2041
	@EnabledOnCommand("ZDIFFSTORE")
	public void zDiffStoreShouldWorkForSameSlotKeys() {
		try {
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_2, 20D)).get();

			nativeConnection.zadd(SAME_SLOT_KEY_2, Map.of(VALUE_2, 20D)).get();

			clusterConnection.zDiffStore(SAME_SLOT_KEY_3_BYTES, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.zrange(SAME_SLOT_KEY_3, new RangeByIndex(0, -1)).get()).contains(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2042
	public void zInterShouldThrowExceptionWhenKeysDoNotMapToSameSlots() {

		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zInter(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zInterWithScores(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
	}

	@Test // GH-2042
	@EnabledOnCommand("ZINTER")
	public void zInterShouldWorkForSameSlotKeys() {
		try {
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_2, 20D)).get();

			nativeConnection.zadd(SAME_SLOT_KEY_2, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zInter(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).contains(VALUE_2_BYTES);
			assertThat(clusterConnection.zInterWithScores(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES))
					.contains(new DefaultTuple(VALUE_2_BYTES, 40D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2042
	public void zInterStoreShouldThrowExceptionWhenKeysDoNotMapToSameSlots() {

		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zInterStore(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
	}

	@Test // DATAREDIS-315
	public void zInterStoreShouldWorkForSameSlotKeys() {
		try {
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_2, 20D)).get();

			nativeConnection.zadd(SAME_SLOT_KEY_2, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_2, Map.of(VALUE_3, 30D)).get();

			clusterConnection.zInterStore(SAME_SLOT_KEY_3_BYTES, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.zrange(SAME_SLOT_KEY_3, new RangeByIndex(0, -1)).get()).contains(VALUE_2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2007
	@EnabledOnCommand("ZPOPMIN")
	public void zPopMinShouldWorkCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 30D)).get();

			assertThat(clusterConnection.zPopMin(KEY_1_BYTES)).isEqualTo(new DefaultTuple(VALUE_1_BYTES, 10D));
			assertThat(clusterConnection.zPopMin(KEY_1_BYTES, 2)).containsExactly(new DefaultTuple(VALUE_2_BYTES, 20D),
					new DefaultTuple(VALUE_3_BYTES, 30D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2007
	@EnabledOnCommand("BZPOPMIN")
	public void bzPopMinShouldWorkCorrectly() {
		try {
			assertThat(clusterConnection.zSetCommands().zCard(KEY_1_BYTES)).isZero();

			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 30D)).get();

			assertThat(clusterConnection.bZPopMin(KEY_1_BYTES, 1, TimeUnit.SECONDS))
					.isEqualTo(new DefaultTuple(VALUE_1_BYTES, 10D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2007
	@EnabledOnCommand("ZPOPMAX")
	public void zPopMaxShouldWorkCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 30D)).get();

			assertThat(clusterConnection.zPopMax(KEY_1_BYTES)).isEqualTo(new DefaultTuple(VALUE_3_BYTES, 30D));
			assertThat(clusterConnection.zPopMax(KEY_1_BYTES, 2)).containsExactly(new DefaultTuple(VALUE_2_BYTES, 20D),
					new DefaultTuple(VALUE_1_BYTES, 10D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2007
	@EnabledOnCommand("BZPOPMAX")
	public void bzPopMaxShouldWorkCorrectly() {
		try {
			assertThat(clusterConnection.zSetCommands().zCard(KEY_1_BYTES)).isZero();

			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 30D)).get();

			assertThat(clusterConnection.bZPopMax(KEY_1_BYTES, 1, TimeUnit.SECONDS))
					.isEqualTo(new DefaultTuple(VALUE_3_BYTES, 30D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2049
	@EnabledOnCommand("ZRANDMEMBER")
	public void zRandMemberShouldReturnResultCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zRandMember(KEY_1_BYTES)).isIn(VALUE_1_BYTES, VALUE_2_BYTES);
			assertThat(clusterConnection.zRandMember(KEY_1_BYTES, 2)).hasSize(2).contains(VALUE_1_BYTES, VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2049
	@EnabledOnCommand("ZRANDMEMBER")
	public void zRandMemberWithScoreShouldReturnResultCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zRandMemberWithScore(KEY_1_BYTES)).isNotNull();
			assertThat(clusterConnection.zRandMemberWithScore(KEY_1_BYTES, 2)).hasSize(2);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRangeByLexShouldReturnResultCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of("a", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("b", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("c", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("d", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("e", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("f", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("g", 0D)).get();

			Set<byte[]> values = clusterConnection.zRangeByLex(KEY_1_BYTES, Range.range().lte("c").toRange());

			assertThat(values).contains(ValkeyGlideConverters.toBytes("a"), ValkeyGlideConverters.toBytes("b"),
					ValkeyGlideConverters.toBytes("c"));
			assertThat(values).doesNotContain(ValkeyGlideConverters.toBytes("d"), ValkeyGlideConverters.toBytes("e"),
					ValkeyGlideConverters.toBytes("f"), ValkeyGlideConverters.toBytes("g"));

			values = clusterConnection.zRangeByLex(KEY_1_BYTES, Range.range().lt("c").toRange());
			assertThat(values).contains(ValkeyGlideConverters.toBytes("a"), ValkeyGlideConverters.toBytes("b"));
			assertThat(values).doesNotContain(ValkeyGlideConverters.toBytes("c"));

			values = clusterConnection.zRangeByLex(KEY_1_BYTES, Range.range().gte("aaa").lt("g").toRange());
			assertThat(values).contains(ValkeyGlideConverters.toBytes("b"), ValkeyGlideConverters.toBytes("c"),
					ValkeyGlideConverters.toBytes("d"), ValkeyGlideConverters.toBytes("e"), ValkeyGlideConverters.toBytes("f"));
			assertThat(values).doesNotContain(ValkeyGlideConverters.toBytes("a"), ValkeyGlideConverters.toBytes("g"));

			values = clusterConnection.zRangeByLex(KEY_1_BYTES, Range.range().gte("e").toRange());
			assertThat(values).contains(ValkeyGlideConverters.toBytes("e"), ValkeyGlideConverters.toBytes("f"),
					ValkeyGlideConverters.toBytes("g"));
			assertThat(values).doesNotContain(ValkeyGlideConverters.toBytes("a"), ValkeyGlideConverters.toBytes("b"),
					ValkeyGlideConverters.toBytes("c"), ValkeyGlideConverters.toBytes("d"));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-1998
	public void zRevRangeByLexShouldReturnValuesCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of("a", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("b", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("c", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("d", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("e", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("f", 0D)).get();
			nativeConnection.zadd(KEY_1, Map.of("g", 0D)).get();

			Set<byte[]> values = clusterConnection.zRevRangeByLex(KEY_1_BYTES, Range.range().lte("c").toRange());

			assertThat(values).containsExactly(ValkeyGlideConverters.toBytes("c"), ValkeyGlideConverters.toBytes("b"),
					ValkeyGlideConverters.toBytes("a"));
			assertThat(values).doesNotContain(ValkeyGlideConverters.toBytes("d"), ValkeyGlideConverters.toBytes("e"),
					ValkeyGlideConverters.toBytes("f"), ValkeyGlideConverters.toBytes("g"));

			values = clusterConnection.zRevRangeByLex(KEY_1_BYTES, Range.range().lt("c").toRange());
			assertThat(values).containsExactly(ValkeyGlideConverters.toBytes("b"), ValkeyGlideConverters.toBytes("a"));
			assertThat(values).doesNotContain(ValkeyGlideConverters.toBytes("c"));

			values = clusterConnection.zRevRangeByLex(KEY_1_BYTES, Range.range().gte("aaa").lt("g").toRange());
			assertThat(values).containsExactly(ValkeyGlideConverters.toBytes("f"), ValkeyGlideConverters.toBytes("e"),
					ValkeyGlideConverters.toBytes("d"), ValkeyGlideConverters.toBytes("c"), ValkeyGlideConverters.toBytes("b"));
			assertThat(values).doesNotContain(ValkeyGlideConverters.toBytes("a"), ValkeyGlideConverters.toBytes("g"));

			values = clusterConnection.zRevRangeByLex(KEY_1_BYTES, Range.range().lte("d").toRange(),
					io.valkey.springframework.data.valkey.connection.ValkeyZSetCommands.Limit.limit().count(2).offset(1));

			assertThat(values).hasSize(2).containsExactly(ValkeyGlideConverters.toBytes("c"), ValkeyGlideConverters.toBytes("b"));
			assertThat(values).doesNotContain(ValkeyGlideConverters.toBytes("a"), ValkeyGlideConverters.toBytes("d"),
					ValkeyGlideConverters.toBytes("e"), ValkeyGlideConverters.toBytes("f"), ValkeyGlideConverters.toBytes("g"));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRangeByScoreShouldReturnValuesCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRangeByScore(KEY_1_BYTES, 10, 20)).contains(VALUE_1_BYTES, VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRangeByScoreShouldReturnValuesCorrectlyWhenGivenOffsetAndScore() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRangeByScore(KEY_1_BYTES, 10D, 20D, 0L, 1L)).contains(VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRangeByScoreWithScoresShouldReturnValuesAndScoreCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRangeByScoreWithScores(KEY_1_BYTES, 10, 20))
					.contains((Tuple) new DefaultTuple(VALUE_1_BYTES, 10D), (Tuple) new DefaultTuple(VALUE_2_BYTES, 20D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRangeByScoreWithScoresShouldReturnValuesCorrectlyWhenGivenOffsetAndScore() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRangeByScoreWithScores(KEY_1_BYTES, 10D, 20D, 0L, 1L))
					.contains((Tuple) new DefaultTuple(VALUE_1_BYTES, 10D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRangeShouldReturnValuesCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRange(KEY_1_BYTES, 1, 2)).contains(VALUE_1_BYTES, VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRangeWithScoresShouldReturnValuesAndScoreCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRangeWithScores(KEY_1_BYTES, 1, 2))
					.contains((Tuple) new DefaultTuple(VALUE_1_BYTES, 10D), (Tuple) new DefaultTuple(VALUE_2_BYTES, 20D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRankShouldReturnPositionForValueCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zRank(KEY_1_BYTES, VALUE_2_BYTES)).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRankShouldReturnReversePositionForValueCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zRevRank(KEY_1_BYTES, VALUE_2_BYTES)).isEqualTo(0L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRemRangeByScoreShouldRemoveValues() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 30D)).get();

			clusterConnection.zRemRangeByScore(KEY_1_BYTES, 15D, 25D);

			assertThat(nativeConnection.zcard(KEY_1).get()).isEqualTo(2L);
			assertThat(nativeConnection.zrange(KEY_1, new RangeByIndex(0, -1)).get()).contains(VALUE_1, VALUE_3);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRemRangeShouldRemoveValues() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 30D)).get();

			clusterConnection.zRemRange(KEY_1_BYTES, 1, 2);

			assertThat(nativeConnection.zcard(KEY_1).get()).isEqualTo(1L);
			assertThat(nativeConnection.zrange(KEY_1, new RangeByIndex(0, -1)).get()).contains(VALUE_1);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRemShouldRemoveValueWithScoreCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();

			clusterConnection.zRem(KEY_1_BYTES, VALUE_1_BYTES);

			assertThat(nativeConnection.zcard(KEY_1).get()).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRevRangeByScoreShouldReturnValuesCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRevRangeByScore(KEY_1_BYTES, 10D, 20D)).contains(VALUE_2_BYTES, VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRevRangeByScoreShouldReturnValuesCorrectlyWhenGivenOffsetAndScore() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRevRangeByScore(KEY_1_BYTES, 10D, 20D, 0L, 1L)).contains(VALUE_2_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRevRangeByScoreWithScoresShouldReturnValuesAndScoreCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRevRangeByScoreWithScores(KEY_1_BYTES, 10D, 20D))
					.contains((Tuple) new DefaultTuple(VALUE_2_BYTES, 20D), (Tuple) new DefaultTuple(VALUE_1_BYTES, 10D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRevRangeByScoreWithScoresShouldReturnValuesCorrectlyWhenGivenOffsetAndScore() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRevRangeByScoreWithScores(KEY_1_BYTES, 10D, 20D, 0L, 1L))
					.contains((Tuple) new DefaultTuple(VALUE_2_BYTES, 20D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRevRangeShouldReturnValuesCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRevRange(KEY_1_BYTES, 1, 2)).contains(VALUE_3_BYTES, VALUE_1_BYTES);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zRevRangeWithScoresShouldReturnValuesAndScoreCorrectly() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_3, 5D)).get();

			assertThat(clusterConnection.zRevRangeWithScores(KEY_1_BYTES, 1, 2))
					.contains((Tuple) new DefaultTuple(VALUE_3_BYTES, 5D), (Tuple) new DefaultTuple(VALUE_1_BYTES, 10D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-479
	public void zScanShouldReadEntireValueRange() {
		try {
			int nrOfValues = 321;
			for (int i = 0; i < nrOfValues; i++) {
				nativeConnection.zadd(KEY_1, Map.of("value-" + i, (double) i)).get();
			}

			Cursor<Tuple> tuples = clusterConnection.zScan(KEY_1_BYTES, ScanOptions.NONE);

			int count = 0;
			while (tuples.hasNext()) {

				tuples.next();
				count++;
			}

			assertThat(count).isEqualTo(nrOfValues);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zScoreShouldRetrieveScoreForValue() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zScore(KEY_1_BYTES, VALUE_2_BYTES)).isEqualTo(20D);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2038
	@EnabledOnCommand("ZMSCORE")
	public void zMScoreShouldRetrieveScoreForValues() {
		try {
			nativeConnection.zadd(KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(KEY_1, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zMScore(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES)).containsSequence(10D, 20D);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2042
	public void zUnionShouldThrowExceptionWhenKeysDoNotMapToSameSlots() {
		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zUnion(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zUnionWithScores(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
	}

	@Test // GH-2042
	@EnabledOnCommand("ZUNION")
	public void zUnionShouldWorkForSameSlotKeys() {
		try {
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_3, 30D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_2, Map.of(VALUE_2, 20D)).get();

			assertThat(clusterConnection.zUnion(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).contains(VALUE_1_BYTES,
					VALUE_2_BYTES, VALUE_3_BYTES);
			assertThat(clusterConnection.zUnionWithScores(SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES)).contains(
					new DefaultTuple(VALUE_1_BYTES, 10D), new DefaultTuple(VALUE_2_BYTES, 20D),
					new DefaultTuple(VALUE_3_BYTES, 30D));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-315
	public void zUnionStoreShouldThrowExceptionWhenKeysDoNotMapToSameSlots() {
		assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> clusterConnection.zUnionStore(KEY_3_BYTES, KEY_1_BYTES, KEY_2_BYTES));
	}

	@Test // DATAREDIS-315
	public void zUnionStoreShouldWorkForSameSlotKeys() {
		try {
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_1, 10D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_1, Map.of(VALUE_3, 30D)).get();
			nativeConnection.zadd(SAME_SLOT_KEY_2, Map.of(VALUE_2, 20D)).get();

			clusterConnection.zUnionStore(SAME_SLOT_KEY_3_BYTES, SAME_SLOT_KEY_1_BYTES, SAME_SLOT_KEY_2_BYTES);

			assertThat(nativeConnection.zrange(SAME_SLOT_KEY_3, new RangeByIndex(0, -1)).get()).contains(VALUE_1, VALUE_2,
					VALUE_3);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-694
	void touchReturnsNrOfKeysTouched() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_1).get();

			assertThat(clusterConnection.keyCommands().touch(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES)).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-694
	void touchReturnsZeroIfNoKeysTouched() {
		assertThat(clusterConnection.keyCommands().touch(KEY_1_BYTES)).isEqualTo(0L);
	}

	@Test // DATAREDIS-693
	void unlinkReturnsNrOfKeysTouched() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.set(KEY_2, VALUE_1).get();

			assertThat(clusterConnection.keyCommands().unlink(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES)).isEqualTo(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-693
	void unlinkReturnsZeroIfNoKeysTouched() {
		assertThat(clusterConnection.keyCommands().unlink(KEY_1_BYTES)).isEqualTo(0L);
	}

	@Test // DATAREDIS-697
	void bitPosShouldReturnPositionCorrectly() {
		try {
			nativeConnection.set(GlideString.of(KEY_1.getBytes()), 
					GlideString.of(HexStringUtils.hexToBytes("fff000"))).get();

			assertThat(clusterConnection.stringCommands().bitPos(KEY_1_BYTES, false)).isEqualTo(12L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-697
	void bitPosShouldReturnPositionInRangeCorrectly() {
		try {
			nativeConnection.set(GlideString.of(KEY_1.getBytes()), 
					GlideString.of(HexStringUtils.hexToBytes("fff0f0"))).get();

			assertThat(clusterConnection.stringCommands().bitPos(KEY_1_BYTES, true,
					org.springframework.data.domain.Range.of(Bound.inclusive(2L), Bound.unbounded()))).isEqualTo(16L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-716
	void encodingReturnsCorrectly() {
		try {
			nativeConnection.set(KEY_1, "1000").get();

			assertThat(clusterConnection.keyCommands().encodingOf(KEY_1_BYTES)).isEqualTo(ValkeyValueEncoding.INT);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-716
	void encodingReturnsVacantWhenKeyDoesNotExist() {
		assertThat(clusterConnection.keyCommands().encodingOf(KEY_2_BYTES)).isEqualTo(ValkeyValueEncoding.VACANT);
	}

	@Test // DATAREDIS-716
	void idletimeReturnsCorrectly() {
		try {
			nativeConnection.set(KEY_1, VALUE_1).get();
			nativeConnection.get(KEY_1).get();

			assertThat(clusterConnection.keyCommands().idletime(KEY_1_BYTES)).isLessThan(Duration.ofSeconds(5));
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-716
	void idldetimeReturnsNullWhenKeyDoesNotExist() {
		assertThat(clusterConnection.keyCommands().idletime(KEY_3_BYTES)).isNull();
	}

	@Test // DATAREDIS-716
	void refcountReturnsCorrectly() {
		try {
			nativeConnection.lpush(KEY_1, new String[]{VALUE_1}).get();

			assertThat(clusterConnection.keyCommands().refcount(KEY_1_BYTES)).isEqualTo(1L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // DATAREDIS-716
	void refcountReturnsNullWhenKeyDoesNotExist() {
		assertThat(clusterConnection.keyCommands().refcount(KEY_3_BYTES)).isNull();
	}

	@Test // DATAREDIS-562
	void bitFieldSetShouldWorkCorrectly() {

		assertThat(clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
				create().set(INT_8).valueAt(BitFieldSubCommands.Offset.offset(0L)).to(10L))).containsExactly(0L);
		assertThat(clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
				create().set(INT_8).valueAt(BitFieldSubCommands.Offset.offset(0L)).to(20L))).containsExactly(10L);
	}

	@Test // DATAREDIS-562
	void bitFieldGetShouldWorkCorrectly() {

		assertThat(clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
				create().get(INT_8).valueAt(BitFieldSubCommands.Offset.offset(0L)))).containsExactly(0L);
	}

	@Test // DATAREDIS-562
	void bitFieldIncrByShouldWorkCorrectly() {

		assertThat(clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
				create().incr(INT_8).valueAt(BitFieldSubCommands.Offset.offset(100L)).by(1L))).containsExactly(1L);
	}

	@Test // DATAREDIS-562
	void bitFieldIncrByWithOverflowShouldWorkCorrectly() {

		assertThat(clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
				create().incr(unsigned(2)).valueAt(BitFieldSubCommands.Offset.offset(102L)).overflow(FAIL).by(1L)))
				.containsExactly(1L);
		assertThat(clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
				create().incr(unsigned(2)).valueAt(BitFieldSubCommands.Offset.offset(102L)).overflow(FAIL).by(1L)))
				.containsExactly(2L);
		assertThat(clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
				create().incr(unsigned(2)).valueAt(BitFieldSubCommands.Offset.offset(102L)).overflow(FAIL).by(1L)))
				.containsExactly(3L);

		assertThat(clusterConnection.stringCommands()
				.bitField(ValkeyGlideConverters.toBytes(KEY_1),
						create().incr(unsigned(2)).valueAt(BitFieldSubCommands.Offset.offset(102L)).overflow(FAIL).by(1L))
				.get(0)).isNull();
	}

	@Test // DATAREDIS-562
	void bitfieldShouldAllowMultipleSubcommands() {

		assertThat(clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
				create().incr(signed(5)).valueAt(BitFieldSubCommands.Offset.offset(100L)).by(1L).get(unsigned(4)).valueAt(0L)))
				.containsExactly(1L, 0L);
	}

	@Test // DATAREDIS-562
	void bitfieldShouldWorkUsingNonZeroBasedOffset() {

		assertThat(
				clusterConnection.stringCommands().bitField(ValkeyGlideConverters.toBytes(KEY_1),
						create().set(INT_8).valueAt(BitFieldSubCommands.Offset.offset(0L).multipliedByTypeLength()).to(100L)
								.set(INT_8).valueAt(BitFieldSubCommands.Offset.offset(1L).multipliedByTypeLength()).to(200L)))
				.containsExactly(0L, 0L);
		assertThat(
				clusterConnection.stringCommands()
						.bitField(ValkeyGlideConverters.toBytes(KEY_1),
								create().get(INT_8).valueAt(BitFieldSubCommands.Offset.offset(0L).multipliedByTypeLength()).get(INT_8)
										.valueAt(BitFieldSubCommands.Offset.offset(1L).multipliedByTypeLength())))
				.containsExactly(100L, -56L);
	}

	@Test // DATAREDIS-1005
	void evalShouldRunScript() {

		byte[] keyAndArgs = ValkeyGlideConverters.toBytes("FOO");
		String luaScript = "return redis.call(\"INCR\", KEYS[1])";
		byte[] luaScriptBin = ValkeyGlideConverters.toBytes(luaScript);

		Long result = clusterConnection.scriptingCommands().eval(luaScriptBin, ReturnType.VALUE, 1, keyAndArgs);

		assertThat(result).isEqualTo(1L);
	}

	@Test // DATAREDIS-1005
	void scriptLoadShouldLoadScript() {

		String luaScript = "return redis.call(\"INCR\", KEYS[1])";
		String digest = DigestUtils.sha1DigestAsHex(luaScript);
		byte[] luaScriptBin = ValkeyGlideConverters.toBytes(luaScript);

		String result = clusterConnection.scriptingCommands().scriptLoad(luaScriptBin);

		assertThat(result).isEqualTo(digest);
	}

	@Test // DATAREDIS-1005
	void scriptFlushShouldRemoveScripts() {

		byte[] keyAndArgs = ValkeyGlideConverters.toBytes("FOO");
		String luaScript = "return redis.call(\"GET\", KEYS[1])";
		byte[] luaScriptBin = ValkeyGlideConverters.toBytes(luaScript);

		clusterConnection.scriptingCommands().scriptLoad(luaScriptBin);
		clusterConnection.scriptingCommands().scriptFlush();

		try {
			clusterConnection.scriptingCommands().evalSha(luaScriptBin, ReturnType.VALUE, 1, keyAndArgs);
			fail("expected InvalidDataAccessApiUsageException");
		} catch (InvalidDataAccessApiUsageException ex) {
			assertThat(ex.getMessage()).contains("NOSCRIPT");
		}
	}

	@Test // DATAREDIS-1005
	void evelShaShouldRunScript() {

		byte[] keyAndArgs = ValkeyGlideConverters.toBytes("FOO");
		String luaScript = "return redis.call(\"INCR\", KEYS[1])";
		byte[] digest = ValkeyGlideConverters.toBytes(DigestUtils.sha1DigestAsHex(luaScript));

		clusterConnection.scriptingCommands().scriptLoad(ValkeyGlideConverters.toBytes(luaScript));

		Long result = clusterConnection.scriptingCommands().evalSha(digest, ReturnType.VALUE, 1, keyAndArgs);
		assertThat(result).isEqualTo(1L);
	}

	@Test // GH-1957
	@EnabledOnCommand("LPOS")
	void lPos() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{"a", "b", "c", "1", "2", "3", "c", "c"}).get();
			List<Long> result = clusterConnection.listCommands().lPos(KEY_1_BYTES, "c".getBytes(StandardCharsets.UTF_8), null,
					null);

			assertThat(result).containsOnly(2L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-1957
	@EnabledOnCommand("LPOS")
	void lPosRank() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{"a", "b", "c", "1", "2", "3", "c", "c"}).get();
			List<Long> result = clusterConnection.listCommands().lPos(KEY_1_BYTES, "c".getBytes(StandardCharsets.UTF_8), 2,
					null);

			assertThat(result).containsExactly(6L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-1957
	@EnabledOnCommand("LPOS")
	void lPosNegativeRank() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{"a", "b", "c", "1", "2", "3", "c", "c"}).get();
			List<Long> result = clusterConnection.listCommands().lPos(KEY_1_BYTES, "c".getBytes(StandardCharsets.UTF_8), -1,
					null);

			assertThat(result).containsExactly(7L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-1957
	@EnabledOnCommand("LPOS")
	void lPosCount() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{"a", "b", "c", "1", "2", "3", "c", "c"}).get();
			List<Long> result = clusterConnection.listCommands().lPos(KEY_1_BYTES, "c".getBytes(StandardCharsets.UTF_8), null,
					2);

			assertThat(result).containsExactly(2L, 6L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-1957
	@EnabledOnCommand("LPOS")
	void lPosRankCount() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{"a", "b", "c", "1", "2", "3", "c", "c"}).get();
			List<Long> result = clusterConnection.listCommands().lPos(KEY_1_BYTES, "c".getBytes(StandardCharsets.UTF_8), -1, 2);

			assertThat(result).containsExactly(7L, 6L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-1957
	@EnabledOnCommand("LPOS")
	void lPosCountZero() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{"a", "b", "c", "1", "2", "3", "c", "c"}).get();
			List<Long> result = clusterConnection.listCommands().lPos(KEY_1_BYTES, "c".getBytes(StandardCharsets.UTF_8), null,
					0);

			assertThat(result).containsExactly(2L, 6L, 7L);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-1957
	@EnabledOnCommand("LPOS")
	void lPosNonExisting() {
		try {
			nativeConnection.rpush(KEY_1, new String[]{"a", "b", "c", "1", "2", "3", "c", "c"}).get();
			List<Long> result = clusterConnection.listCommands().lPos(KEY_1_BYTES, "x".getBytes(StandardCharsets.UTF_8), null,
					null);

			assertThat(result).isEmpty();
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
	}

	@Test // GH-2986
	void shouldUseCachedTopology() {
		// Test 1: Force cache invalidation - no cached topology
		ReflectionTestUtils.setField(clusterConnection, "cachedTopology", null);
		ReflectionTestUtils.setField(clusterConnection, "lastCacheTime", 0L);
		
		// Get fresh topology - should fetch from cluster
		ClusterTopology topology1 = clusterConnection.getTopology();
		assertThat(topology1).isNotNull();
		
		// Verify lastCacheTime was set after fetch
		Long lastCacheTime = (Long) ReflectionTestUtils.getField(clusterConnection, "lastCacheTime");
		assertThat(lastCacheTime).isGreaterThan(0L);
		
		// Test 2: Cached topology with valid timestamp (should use cache)
		Long currentTime = System.currentTimeMillis();
		ReflectionTestUtils.setField(clusterConnection, "lastCacheTime", currentTime);
		
		ClusterTopology topology2 = clusterConnection.getTopology();
		assertThat(topology2).isSameAs(topology1); // Same instance = used cache
		
		// Test 3: Cached topology with expired timestamp (should refresh)
		Long cacheTimeout = (Long) ReflectionTestUtils.getField(clusterConnection, "cacheTimeoutMs");
		Long expiredTime = currentTime - cacheTimeout - 100; // Expired by 100ms
		ReflectionTestUtils.setField(clusterConnection, "lastCacheTime", expiredTime);
		
		ClusterTopology topology3 = clusterConnection.getTopology();
		assertThat(topology3).isNotSameAs(topology1); // Different instance = refreshed
	}
}
