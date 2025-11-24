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

import static io.valkey.springframework.data.valkey.connection.ClusterTestVariables.*;
import static org.assertj.core.api.Assertions.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import io.valkey.springframework.data.valkey.connection.ClusterInfo;
import io.valkey.springframework.data.valkey.connection.ClusterSlotHashUtil;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterCommands.AddSlots;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode.SlotRange;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideClusterConnection} 
 * cluster functionality using the ValkeyClusterCommands interface directly.
 * 
 * These tests validate the implementation of all ValkeyClusterCommands methods:
 * - Cluster topology operations (clusterGetNodes, clusterGetReplicas, clusterGetMasterReplicaMap)
 * - Slot routing operations (clusterGetSlotForKey, clusterGetNodeForSlot, clusterGetNodeForKey)
 * - Cluster information (clusterGetClusterInfo)
 * - Slot management (clusterAddSlots, clusterDeleteSlots, clusterCountKeysInSlot, clusterGetKeysInSlot)
 * - Node management (clusterMeet, clusterForget, clusterSetSlot, clusterReplicate)
 * 
 * Note: These tests cover DIRECT MODE only, as cluster mode does not support 
 * pipelining or transactions in Valkey-Glide.
 * 
 * <p><strong>Expected Cluster Topology (from Makefile):</strong>
 * <ul>
 *   <li>3 Master nodes: 7379, 7380, 7381</li>
 *   <li>1 Replica node: 7382 (replica of 7379)</li>
 *   <li>Slot distribution:
 *     <ul>
 *       <li>7379: slots 0-5460 (5461 slots)</li>
 *       <li>7380: slots 5461-10922 (5462 slots)</li>
 *       <li>7381: slots 10923-16383 (5461 slots)</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
@EnabledOnValkeyClusterAvailable
public class ValkeyGlideClusterConnectionCommandsIntegrationTests extends AbstractValkeyGlideClusterIntegrationTests {

    // Expected cluster topology constants (from Makefile cluster-init target)
    private static final int EXPECTED_MASTER_COUNT = 3;
    private static final int EXPECTED_REPLICA_COUNT = 1;
    private static final int EXPECTED_TOTAL_NODES = 4;
    
    // Slot ranges (from Makefile cluster-slots target)
    private static final int MASTER_1_SLOT_START = 0;
    private static final int MASTER_1_SLOT_END = 5460;
    private static final int MASTER_2_SLOT_START = 5461;
    private static final int MASTER_2_SLOT_END = 10922;
    private static final int MASTER_3_SLOT_START = 10923;
    private static final int MASTER_3_SLOT_END = 16383;

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "{user}:test:key1", "{user}:test:key2", "{user}:test:key3",
            "{product}:test:key1", "{product}:test:key2",
            "test:cluster:key1", "test:cluster:key2", "test:cluster:key3",
            "non:existent:key"
        };
    }

    // ==================== Cluster Topology & Information Commands ====================

    @Test
    void testClusterGetNodes() {
        // Execute
        Iterable<ValkeyClusterNode> nodes = clusterConnection.clusterCommands().clusterGetNodes();
        
        // Verify
        assertThat(nodes).isNotNull();
        
        // Convert to list for easier assertions
        List<ValkeyClusterNode> nodeList = (List<ValkeyClusterNode>) 
            ((nodes instanceof List) ? nodes : 
                java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                    .collect(Collectors.toList()));
        
        // Verify expected node count
        assertThat(nodeList).hasSize(EXPECTED_TOTAL_NODES);
        
        // Count masters and replicas
        int masterCount = 0;
        int replicaCount = 0;
        Map<Integer, ValkeyClusterNode> nodesByPort = new HashMap<>();
        
        for (ValkeyClusterNode node : nodeList) {
            // Verify basic node properties
            assertThat(node.getId()).isNotNull();
            assertThat(node.getHost()).isNotNull();
            assertThat(node.getPort()).isGreaterThan(0);
            
            nodesByPort.put(node.getPort(), node);
            
            // Count masters and replicas
            if (node.isMaster()) {
                masterCount++;
                // Masters should have slot ranges assigned
                assertThat(node.getSlotRange()).isNotNull();
                assertThat(node.getSlotRange().getSlots()).isNotEmpty();
            } else {
                replicaCount++;
                // Replicas should have master ID
                assertThat(node.getMasterId()).isNotNull();
            }
        }
        
        // Verify counts match expected topology
        assertThat(masterCount).isEqualTo(EXPECTED_MASTER_COUNT);
        assertThat(replicaCount).isEqualTo(EXPECTED_REPLICA_COUNT);
        
        // Verify expected ports are present
        assertThat(nodesByPort).containsKeys(MASTER_NODE_1_PORT, MASTER_NODE_2_PORT, MASTER_NODE_3_PORT, REPLICAOF_NODE_1_PORT);
        
        // Verify master nodes
        assertThat(nodesByPort.get(MASTER_NODE_1_PORT).isMaster()).isTrue();
        assertThat(nodesByPort.get(MASTER_NODE_2_PORT).isMaster()).isTrue();
        assertThat(nodesByPort.get(MASTER_NODE_3_PORT).isMaster()).isTrue();
        
        // Verify replica node
        assertThat(nodesByPort.get(REPLICAOF_NODE_1_PORT).isMaster()).isFalse();
    }

    @Test
    void testClusterGetNodesSlotDistribution() {
        // Get all nodes
        Iterable<ValkeyClusterNode> nodes = clusterConnection.clusterCommands().clusterGetNodes();
        
        Map<Integer, ValkeyClusterNode> mastersByPort = new HashMap<>();
        for (ValkeyClusterNode node : nodes) {
            if (node.isMaster()) {
                mastersByPort.put(node.getPort(), node);
            }
        }
        
        // Verify Master 1 (7379) slot range: 0-5460
        ValkeyClusterNode master1 = mastersByPort.get(MASTER_NODE_1_PORT);
        assertThat(master1).isNotNull();
        verifySlotRange(master1, MASTER_1_SLOT_START, MASTER_1_SLOT_END);
        
        // Verify Master 2 (7380) slot range: 5461-10922
        ValkeyClusterNode master2 = mastersByPort.get(MASTER_NODE_2_PORT);
        assertThat(master2).isNotNull();
        verifySlotRange(master2, MASTER_2_SLOT_START, MASTER_2_SLOT_END);
        
        // Verify Master 3 (7381) slot range: 10923-16383
        ValkeyClusterNode master3 = mastersByPort.get(MASTER_NODE_3_PORT);
        assertThat(master3).isNotNull();
        verifySlotRange(master3, MASTER_3_SLOT_START, MASTER_3_SLOT_END);
    }

    @Test
    void testClusterGetReplicas() {
        // Get master node at port 7379 (which should have replica 7382)
        ValkeyClusterNode masterWithReplica = findNodeByPort(MASTER_NODE_1_PORT);
        assertThat(masterWithReplica).isNotNull();
        assertThat(masterWithReplica.isMaster()).isTrue();
        
        // Execute
        Collection<ValkeyClusterNode> replicas = 
            clusterConnection.clusterCommands().clusterGetReplicas(masterWithReplica);
        
        // Verify - should have exactly 1 replica
        assertThat(replicas).isNotNull();
        assertThat(replicas).hasSize(1);
        
        ValkeyClusterNode replica = replicas.iterator().next();
        assertThat(replica.getPort()).isEqualTo(REPLICAOF_NODE_1_PORT);
        assertThat(replica.isMaster()).isFalse();
        assertThat(replica.getMasterId()).isEqualTo(masterWithReplica.getId());
    }

    @Test
    void testClusterGetReplicasForMastersWithoutReplicas() {
        // Masters 7380 and 7381 should have no replicas
        ValkeyClusterNode master2 = findNodeByPort(MASTER_NODE_2_PORT);
        ValkeyClusterNode master3 = findNodeByPort(MASTER_NODE_3_PORT);
        
        assertThat(master2).isNotNull();
        assertThat(master3).isNotNull();
        
        // Execute
        Collection<ValkeyClusterNode> replicas2 = 
            clusterConnection.clusterCommands().clusterGetReplicas(master2);
        Collection<ValkeyClusterNode> replicas3 = 
            clusterConnection.clusterCommands().clusterGetReplicas(master3);
        
        // Verify - should have no replicas
        assertThat(replicas2).isNotNull().isEmpty();
        assertThat(replicas3).isNotNull().isEmpty();
    }

    @Test
    void testClusterGetMasterReplicaMap() {
        // Execute
        Map<ValkeyClusterNode, Collection<ValkeyClusterNode>> masterReplicaMap = 
            clusterConnection.clusterCommands().clusterGetMasterReplicaMap();
        
        // Verify
        assertThat(masterReplicaMap).isNotNull();
        assertThat(masterReplicaMap).hasSize(EXPECTED_MASTER_COUNT);
        
        // Find master with replica (port 7379)
        ValkeyClusterNode masterWithReplica = null;
        Collection<ValkeyClusterNode> itsReplicas = null;
        
        for (Map.Entry<ValkeyClusterNode, Collection<ValkeyClusterNode>> entry : masterReplicaMap.entrySet()) {
            if (entry.getKey().getPort() == MASTER_NODE_1_PORT) {
                masterWithReplica = entry.getKey();
                itsReplicas = entry.getValue();
                break;
            }
        }
        
        // Verify master with replica
        assertThat(masterWithReplica).isNotNull();
        assertThat(itsReplicas).hasSize(1);
        assertThat(itsReplicas.iterator().next().getPort()).isEqualTo(REPLICAOF_NODE_1_PORT);
        
        // Verify other masters have no replicas
        for (Map.Entry<ValkeyClusterNode, Collection<ValkeyClusterNode>> entry : masterReplicaMap.entrySet()) {
            ValkeyClusterNode master = entry.getKey();
            if (master.getPort() == MASTER_NODE_2_PORT || master.getPort() == MASTER_NODE_3_PORT) {
                assertThat(entry.getValue()).isEmpty();
            }
        }
    }

    @Test
    void testClusterGetClusterInfo() {
        // Execute
        ClusterInfo clusterInfo = clusterConnection.clusterCommands().clusterGetClusterInfo();
        
        // Verify
        assertThat(clusterInfo).isNotNull();
        assertThat(clusterInfo.getState()).isNotNull();
        
        // Verify cluster is operational
        assertThat(clusterInfo.getState()).isIn("ok", "OK");
        
        // Verify all slots are assigned
        assertThat(clusterInfo.getSlotsAssigned()).isEqualTo(16384);
        assertThat(clusterInfo.getSlotsOk()).isEqualTo(16384);
        assertThat(clusterInfo.getSlotsFail()).isEqualTo(0);
        
        // Verify node count
        assertThat(clusterInfo.getKnownNodes()).isEqualTo(EXPECTED_TOTAL_NODES);
    }

    // ==================== Slot & Key Routing Commands ====================

    @Test
    void testClusterGetSlotForKey() {
        byte[] key = "{user}:test:key1".getBytes();
        
        // Execute
        Integer slot = clusterConnection.clusterCommands().clusterGetSlotForKey(key);
        
        // Verify
        assertThat(slot).isNotNull();
        assertThat(slot).isBetween(0, 16383); // Valid slot range
        
        // Verify matches expected slot calculation
        int expectedSlot = calculateSlot("{user}:test:key1");
        assertThat(slot).isEqualTo(expectedSlot);
    }

    @Test
    void testClusterGetSlotForKeyWithHashTags() {
        // Test that keys with same hash tag go to same slot
        byte[] key1 = "{user}:123".getBytes();
        byte[] key2 = "{user}:456".getBytes();
        byte[] key3 = "{user}:abc".getBytes();
        
        Integer slot1 = clusterConnection.clusterCommands().clusterGetSlotForKey(key1);
        Integer slot2 = clusterConnection.clusterCommands().clusterGetSlotForKey(key2);
        Integer slot3 = clusterConnection.clusterCommands().clusterGetSlotForKey(key3);
        
        // All should map to same slot (hash tag is "user")
        assertThat(slot1).isEqualTo(slot2);
        assertThat(slot2).isEqualTo(slot3);
        
        // Verify with manual calculation
        int expectedSlot = calculateSlot("{user}:123");
        assertThat(slot1).isEqualTo(expectedSlot);
    }

    @Test
    void testClusterGetNodeForKey() {
        // Create a key that should map to each master
        // We need to find keys that map to different masters' slot ranges
        
        String key1 = findKeyForSlotRange(MASTER_1_SLOT_START, MASTER_1_SLOT_END);
        String key2 = findKeyForSlotRange(MASTER_2_SLOT_START, MASTER_2_SLOT_END);
        String key3 = findKeyForSlotRange(MASTER_3_SLOT_START, MASTER_3_SLOT_END);
        
        // Verify each key maps to correct master
        ValkeyClusterNode node1 = clusterConnection.clusterCommands().clusterGetNodeForKey(key1.getBytes());
        ValkeyClusterNode node2 = clusterConnection.clusterCommands().clusterGetNodeForKey(key2.getBytes());
        ValkeyClusterNode node3 = clusterConnection.clusterCommands().clusterGetNodeForKey(key3.getBytes());
        
        assertThat(node1).isNotNull();
        assertThat(node2).isNotNull();
        assertThat(node3).isNotNull();
        
        // Verify they are different nodes (different masters)
        assertThat(node1.getId()).isNotEqualTo(node2.getId());
        assertThat(node2.getId()).isNotEqualTo(node3.getId());
        assertThat(node1.getId()).isNotEqualTo(node3.getId());
    }

    @Test
    void testClusterCountKeysInSlot() {
        // Set up test data in a specific slot (ensure it's in master 1's range)
        String key1 = "{user}:test:key1";
        String key2 = "{user}:test:key2";
        String key3 = "{user}:test:key3";
        
        try {
            // Add keys to ensure they're in same slot
            clusterConnection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            clusterConnection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            clusterConnection.stringCommands().set(key3.getBytes(), "value3".getBytes());
            
            // Get the slot for these keys
            Integer slot = clusterConnection.clusterCommands().clusterGetSlotForKey(key1.getBytes());
            
            // Execute
            Long count = clusterConnection.clusterCommands().clusterCountKeysInSlot(slot);
            
            // Verify - should have at least our 3 test keys
            assertThat(count).isNotNull();
            assertThat(count).isGreaterThanOrEqualTo(3);
        } finally {
            cleanupKeys(key1, key2, key3);
        }
    }

    
    @Test
    void testClusterGetKeysInSlot() {
        // Set up test data
        String key1 = "{product}:test:key1";
        String key2 = "{product}:test:key2";
        
        try {
            // Add keys
            clusterConnection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            clusterConnection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Get the slot
            Integer slot = clusterConnection.clusterCommands().clusterGetSlotForKey(key1.getBytes());
            
            // Execute - request up to 10 keys
            List<byte[]> keys = clusterConnection.clusterCommands().clusterGetKeysInSlot(slot, 10);
            
            // Verify
            assertThat(keys).isNotNull();
            assertThat(keys).hasSize(2);
            
            // Verify our test keys are in the result
            Set<String> keyStrings = keys.stream()
                .map(String::new)
                .collect(Collectors.toSet());
            
            assertThat(keyStrings).contains(key1, key2);
            
        } finally {
            cleanupKeys(key1, key2);
        }
    }

    // ==================== Cluster Modification Commands (with State Restoration) ====================

    @Test
    void testClusterAddSlotsAndDeleteSlots() {
        // This test modifies cluster state but restores it at the end
        
        // Find a master and temporarily remove some slots, then add them back
        ValkeyClusterNode master2 = findNodeByPort(MASTER_NODE_2_PORT);
        assertThat(master2).isNotNull();
        
        // Choose slots from middle of master2's range to temporarily remove
        int slot1 = MASTER_2_SLOT_START + 1000;
        int slot2 = MASTER_2_SLOT_START + 1001;
        int slot3 = MASTER_2_SLOT_START + 1002;
        
        try {
            // Step 1: Delete slots
            clusterConnection.clusterCommands().clusterDeleteSlots(master2, slot1, slot2, slot3);
            
            // Verify deletion: try to delete again - should FAIL
            assertThatThrownBy(() -> 
                clusterConnection.clusterCommands().clusterDeleteSlots(master2, slot1, slot2, slot3))
                .isInstanceOf(DataAccessException.class);
            
            // Step 2: Add slots back
            clusterConnection.clusterCommands().clusterAddSlots(master2, slot1, slot2, slot3);
            logger.info("Added slots back");
            
            // Verify addition: try to add again - should FAIL
            assertThatThrownBy(() ->
                clusterConnection.clusterCommands().clusterAddSlots(master2, slot1, slot2, slot3))
                .isInstanceOf(DataAccessException.class);
            
            // Final verification
            ValkeyClusterNode nodeForSlot1 = clusterConnection.clusterCommands().clusterGetNodeForSlot(slot1);
            assertThat(nodeForSlot1.getPort()).isEqualTo(MASTER_NODE_2_PORT);

        } catch (Exception e) {
            try { clusterConnection.clusterCommands().clusterAddSlots(master2, slot1, slot2, slot3); } catch (Exception ignore) {}
            throw e;
        }
    }

    @Test
    void testClusterAddSlotsWithSlotRange() {
        // This test modifies cluster state but restores it at the end
        
        ValkeyClusterNode master3 = findNodeByPort(MASTER_NODE_3_PORT);
        assertThat(master3).isNotNull();
        
        // Choose a small range from master3 to temporarily remove and re-add
        int rangeStart = MASTER_3_SLOT_START + 2000;
        int rangeEnd = MASTER_3_SLOT_START + 2004; // 5 slots
        SlotRange testRange = new SlotRange(rangeStart, rangeEnd);
        
        try {
            // Step 1: Delete slot range
            clusterConnection.clusterCommands().clusterDeleteSlotsInRange(master3, testRange);
            
            // Verify deletion: try to delete again - should FAIL
            assertThatThrownBy(() ->
                clusterConnection.clusterCommands().clusterDeleteSlotsInRange(master3, testRange))
                .isInstanceOf(DataAccessException.class);
            
            // Step 2: Add slot range back
            clusterConnection.clusterCommands().clusterAddSlots(master3, testRange);
            
            // Verify addition: try to add again - should FAIL
            assertThatThrownBy(() ->
                clusterConnection.clusterCommands().clusterAddSlots(master3, testRange))
                .isInstanceOf(DataAccessException.class);
            
            // Final verification
            ValkeyClusterNode nodeForSlot = clusterConnection.clusterCommands().clusterGetNodeForSlot(rangeStart);
            assertThat(nodeForSlot.getPort()).isEqualTo(MASTER_NODE_3_PORT);
            
        } catch (Exception e) {
            try { clusterConnection.clusterCommands().clusterAddSlots(master3, testRange); } catch (Exception ignore) {}
            throw e;
        }
    }

    // I suspect there is a bug in the interface that prevents specifying the node ID for IMPORTING/MIGRATING modes.
    // Valkey-Glide uses Jedis approach of taking the id of the node parameter, which is the destination node and is incorrect by the protocol.
    // Hence, the comprehensive migration test below is disabled until this is resolved.
    @Test
    void testClusterSetSlot() {
        // Test CLUSTER SETSLOT command with different modes
        
        ValkeyClusterNode master1 = findNodeByPort(MASTER_NODE_1_PORT);
        ValkeyClusterNode master2 = findNodeByPort(MASTER_NODE_2_PORT);
        assertThat(master1).isNotNull();
        assertThat(master2).isNotNull();
        
        // Choose a slot from master1's range to test migration
        int slotToMigrate = MASTER_1_SLOT_START + 500;
        
        try {
            // Step 1: Mark slot as MIGRATING on source node (master1)
            clusterConnection.clusterCommands().clusterSetSlot(master1, slotToMigrate, AddSlots.MIGRATING);
            
            // Step 2: Mark slot as IMPORTING on destination node (master2)
            clusterConnection.clusterCommands().clusterSetSlot(master2, slotToMigrate, AddSlots.IMPORTING);

            // Step 3: Set slot to STABLE state to restore
            clusterConnection.clusterCommands().clusterSetSlot(master1, slotToMigrate, AddSlots.STABLE);
            clusterConnection.clusterCommands().clusterSetSlot(master2, slotToMigrate, AddSlots.STABLE); 
            
            // Restore both nodes to STABLE
            ValkeyClusterNode nodeForSlot = clusterConnection.clusterCommands().clusterGetNodeForSlot(slotToMigrate);
            assertThat(nodeForSlot.getPort()).isEqualTo(MASTER_NODE_1_PORT);
            
        } finally {
            clusterConnection.clusterCommands().clusterSetSlot(master1, slotToMigrate, AddSlots.STABLE);
            clusterConnection.clusterCommands().clusterSetSlot(master2, slotToMigrate, AddSlots.STABLE);
        }
    }

    @Test
    @org.junit.jupiter.api.Disabled("""
        WARNING: This test is currently disabled, probably due state propagation delays in the cluster.
        
        Despite implementing cache invalidation in ValkeyGlideClusterConnection, the test still fails 
        because the cluster's internal state may take time to propagate, or there may be additional 
        caching layers we haven't identified.
        
        Test will remain disabled until the root cause is fully identified and resolved.
        """)
    void testClusterSetSlotWithActualMigration() {
        // Comprehensive test that performs actual slot migration with key transfer
        // This test validates the complete CLUSTER SETSLOT workflow including key migration
        
        ValkeyClusterNode sourceNode = findNodeByPort(MASTER_NODE_1_PORT);
        ValkeyClusterNode destNode = findNodeByPort(MASTER_NODE_2_PORT);
        assertThat(sourceNode).isNotNull();
        assertThat(destNode).isNotNull();
        
        // Choose a slot from master1's range that we'll migrate to master2
        int slotToMigrate = MASTER_1_SLOT_START + 1000;
        
        // Find a key that maps to this specific slot using ClusterSlotHashUtil
        String testKey = ClusterSlotHashUtil.getKeyForSlot(slotToMigrate);
        byte[] keyBytes = testKey.getBytes();
        byte[] valueBytes = "migration-test-value".getBytes();
        
        try {
            // Setup: Create test key in the slot on source node
            clusterConnection.stringCommands().set(keyBytes, valueBytes);
            
            // Verify key exists and is on source node
            byte[] originalValue = clusterConnection.stringCommands().get(keyBytes);
            assertThat(originalValue).isEqualTo(valueBytes);
            
            // Step 1: Mark slot as IMPORTING on destination node (with source node ID)
            // According to docs: CLUSTER SETSLOT <slot> IMPORTING <source-node-id>
            clusterConnection.clusterCommands().clusterSetSlot(destNode, slotToMigrate, AddSlots.IMPORTING);
            
            // Step 2: Mark slot as MIGRATING on source node (with destination node ID)
            // According to docs: CLUSTER SETSLOT <slot> MIGRATING <destination-node-id>
            clusterConnection.clusterCommands().clusterSetSlot(sourceNode, slotToMigrate, AddSlots.MIGRATING);
            
            // Step 3: Migrate the key from source to destination
            // MIGRATE <host> <port> <key> <db> <timeout> [COPY] [REPLACE]
            clusterConnection.serverCommands().migrate(
                keyBytes,
                destNode,
                0, // database 0
                null, // no option (will do atomic move)
                5000 // 5 second timeout
            );
            
            // Step 4: Complete the migration by assigning slot to destination
            // This should be done on the destination node first, then propagated
            clusterConnection.clusterCommands().clusterSetSlot(destNode, slotToMigrate, AddSlots.NODE);
            
            // Propagate to source node
            clusterConnection.clusterCommands().clusterSetSlot(sourceNode, slotToMigrate, AddSlots.NODE);
            
            // Step 5: Verify migration was successful
            // The slot should now be owned by destination node
            ValkeyClusterNode newOwner = clusterConnection.clusterCommands().clusterGetNodeForSlot(slotToMigrate);
            assertThat(newOwner.getPort()).isEqualTo(MASTER_NODE_2_PORT);
            
            // Verify the key is accessible and has correct value
            byte[] migratedValue = clusterConnection.stringCommands().get(keyBytes);
            assertThat(migratedValue).isEqualTo(valueBytes);
            
        } finally {
            clusterConnection.serverCommands().flushAll();
            
            // Assign slot back to source
            clusterConnection.clusterCommands().clusterSetSlot(sourceNode, slotToMigrate, AddSlots.NODE);
            clusterConnection.clusterCommands().clusterSetSlot(destNode, slotToMigrate, AddSlots.NODE);
        }
    }

    @Test
    void testClusterMeet() {
        // CLUSTER MEET adds a node to the cluster
        // Since we're testing with an existing cluster, we'll verify the command works
        // but won't actually add a new node (as that would require a new Valkey instance)
        
        // Get existing nodes
        Iterable<ValkeyClusterNode> nodes = clusterConnection.clusterCommands().clusterGetNodes();
        ValkeyClusterNode existingNode = nodes.iterator().next();
        
        // Create a node reference (this won't add a new node, just test the command execution)
        ValkeyClusterNode nodeToMeet = ValkeyClusterNode.newValkeyClusterNode()
            .listeningAt(existingNode.getHost(), existingNode.getPort())
            .build();
        
        // Execute CLUSTER MEET (should be idempotent for existing nodes)
        clusterConnection.clusterCommands().clusterMeet(nodeToMeet);
        
        // Verify cluster still has expected number of nodes
        Iterable<ValkeyClusterNode> nodesAfter = clusterConnection.clusterCommands().clusterGetNodes();
        List<ValkeyClusterNode> nodeList = java.util.stream.StreamSupport
            .stream(nodesAfter.spliterator(), false)
            .collect(Collectors.toList());
        
        assertThat(nodeList).hasSize(EXPECTED_TOTAL_NODES);
    }

    @Test
    void testClusterReplicate() {
        // CLUSTER REPLICATE makes a node a replica of a master
        // We'll test by verifying the existing replica relationship
        
        ValkeyClusterNode master1 = findNodeByPort(MASTER_NODE_1_PORT);
        ValkeyClusterNode replica = findNodeByPort(REPLICAOF_NODE_1_PORT);
        
        assertThat(master1).isNotNull();
        assertThat(replica).isNotNull();
        
        // Verify current state: replica should already be replicating master1
        assertThat(replica.isMaster()).isFalse();
        assertThat(replica.getMasterId()).isEqualTo(master1.getId());
        
        // Re-execute CLUSTER REPLICATE (should be idempotent)
        clusterConnection.clusterCommands().clusterReplicate(master1, replica);
        
        // Verify replica relationship is still intact
        Collection<ValkeyClusterNode> replicas = 
            clusterConnection.clusterCommands().clusterGetReplicas(master1);
        assertThat(replicas).hasSize(1);
        assertThat(replicas.iterator().next().getPort()).isEqualTo(REPLICAOF_NODE_1_PORT);
    }

    @Test
    void testClusterForget() {
        // CLUSTER FORGET removes a node from the cluster
        // This is a very destructive operation, so we'll only test it in a controlled way
        // We cannot actually forget a master node as it would break the cluster
        
        // Instead, verify that attempting to forget a non-existent node doesn't break things
        ValkeyClusterNode nonExistentNode = ValkeyClusterNode.newValkeyClusterNode()
            .listeningAt("127.0.0.1", 9999)
            .withId("0000000000000000000000000000000000000000")
            .build();
        
        assertThatThrownBy(() -> 
            clusterConnection.clusterCommands().clusterForget(nonExistentNode))
            .isInstanceOf(DataAccessException.class);
        
        // Verify cluster still has all expected nodes
        Iterable<ValkeyClusterNode> nodes = clusterConnection.clusterCommands().clusterGetNodes();
        List<ValkeyClusterNode> nodeList = java.util.stream.StreamSupport
            .stream(nodes.spliterator(), false)
            .collect(Collectors.toList());
        
        assertThat(nodeList).hasSize(EXPECTED_TOTAL_NODES);
    }

    // ==================== Edge Cases & Error Handling ====================

    @Test
    void testClusterGetSlotForNonExistentKey() {
        byte[] key = "non:existent:key:12345".getBytes();
        
        // Execute - should still return slot even if key doesn't exist
        Integer slot = clusterConnection.clusterCommands().clusterGetSlotForKey(key);
        
        // Verify
        assertThat(slot).isNotNull();
        assertThat(slot).isBetween(0, 16383);
        
        // Verify matches calculation
        int expectedSlot = calculateSlot(key);
        assertThat(slot).isEqualTo(expectedSlot);
    }

    @Test
    void testClusterTopologyConsistency() {
        // Get nodes from different methods and verify consistency
        Iterable<ValkeyClusterNode> allNodes = clusterConnection.clusterCommands().clusterGetNodes();
        Map<ValkeyClusterNode, Collection<ValkeyClusterNode>> masterReplicaMap = 
            clusterConnection.clusterCommands().clusterGetMasterReplicaMap();
        
        // Count masters from both sources
        int masterCountFromNodes = 0;
        for (ValkeyClusterNode node : allNodes) {
            if (node.isMaster()) {
                masterCountFromNodes++;
            }
        }
        
        int masterCountFromMap = masterReplicaMap.size();
        
        // Should match expected counts
        assertThat(masterCountFromNodes).isEqualTo(EXPECTED_MASTER_COUNT);
        assertThat(masterCountFromMap).isEqualTo(EXPECTED_MASTER_COUNT);
    }

    @Test
    void testSlotRangeCoverage() {
        // Verify all slots 0-16383 are covered by master nodes
        Collection<ValkeyClusterNode> masters = getActiveMasterNodes();
        
        assertThat(masters).hasSize(EXPECTED_MASTER_COUNT);
        
        boolean[] slotsCovered = new boolean[16384];
        
        for (ValkeyClusterNode master : masters) {
            SlotRange slotRange = master.getSlotRange();
            assertThat(slotRange).isNotNull();
            
            for (Integer slot : slotRange.getSlots()) {
                slotsCovered[slot] = true;
            }
        }
        
        // Count covered slots
        int coveredCount = 0;
        for (boolean covered : slotsCovered) {
            if (covered) coveredCount++;
        }
        
        // All 16384 slots should be covered
        assertThat(coveredCount).isEqualTo(16384);
    }

    @Test
    void testHashTagSlotCalculationConsistency() {
        // Test various hash tag patterns
        String[][] keyPairs = {
            {"{user}:profile", "{user}:settings"},
            {"{order}:123", "{order}:456"},
            {"{product:id}:abc", "{product:id}:xyz"},
            {"prefix:{tag}:suffix1", "prefix:{tag}:suffix2"}
        };
        
        for (String[] pair : keyPairs) {
            Integer slot1 = clusterConnection.clusterCommands().clusterGetSlotForKey(pair[0].getBytes());
            Integer slot2 = clusterConnection.clusterCommands().clusterGetSlotForKey(pair[1].getBytes());
            
            // Both keys should map to same slot
            assertThat(slot1).isEqualTo(slot2);
            
            // Verify against manual calculation
            int expectedSlot = calculateSlot(pair[0]);
            assertThat(slot1).isEqualTo(expectedSlot);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Verifies that a node's slot range matches the expected start and end slots.
     */
    private void verifySlotRange(ValkeyClusterNode node, int expectedStart, int expectedEnd) {
        SlotRange slotRange = node.getSlotRange();
        assertThat(slotRange).isNotNull();
        
        Set<Integer> slots = slotRange.getSlots();
        assertThat(slots).isNotEmpty();
        
        int actualStart = slots.stream().min(Integer::compare).orElse(-1);
        int actualEnd = slots.stream().max(Integer::compare).orElse(-1);
        
        assertThat(actualStart).isEqualTo(expectedStart);
        assertThat(actualEnd).isEqualTo(expectedEnd);
        
        int expectedSlotCount = expectedEnd - expectedStart + 1;
        assertThat(slots).hasSize(expectedSlotCount);
        
        logger.info("Node at port {} has slots {}-{} ({} slots)", 
            node.getPort(), actualStart, actualEnd, slots.size());
    }

    /**
     * Finds a node by its port number.
     */
    private ValkeyClusterNode findNodeByPort(int port) {
        Iterable<ValkeyClusterNode> nodes = clusterConnection.clusterCommands().clusterGetNodes();
        for (ValkeyClusterNode node : nodes) {
            if (node.getPort() == port) {
                return node;
            }
        }
        return null;
    }

    /**
     * Finds a key whose slot falls within the specified range.
     * Uses a simple iteration approach to find a suitable key.
     */
    private String findKeyForSlotRange(int startSlot, int endSlot) {
        // Try different keys until we find one in the range
        for (int i = 0; i < 10000; i++) {
            String key = "test:key:" + i;
            int slot = calculateSlot(key);
            if (slot >= startSlot && slot <= endSlot) {
                return key;
            }
        }
        
        // Fallback: use hash tag with slot number
        return "{slot" + startSlot + "}:key";
    }
}
