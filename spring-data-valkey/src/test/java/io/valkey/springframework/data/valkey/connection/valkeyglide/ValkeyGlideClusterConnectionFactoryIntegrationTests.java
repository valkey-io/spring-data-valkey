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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.valkey.springframework.data.valkey.connection.ClusterTestVariables;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyListCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.core.ValkeyCallback;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;

/**
 * Integration tests for {@link ValkeyGlideConnectionFactory} in cluster mode.
 * 
 * <p>These tests verify that the connection factory works correctly with a Valkey cluster,
 * ensuring that the cluster adapter properly handles routing and result aggregation.
 * 
 * <p><strong>Important Notes:</strong>
 * <ul>
 *   <li>All test keys use hash tags (e.g., {@code {test}:key}) to ensure multi-key operations
 *       work correctly in cluster mode by routing to the same slot.</li>
 *   <li>Transactions ({@code MULTI/EXEC}) are not supported in cluster mode and are omitted.</li>
 *   <li>Pipeline operations have limited support in cluster mode and are omitted.</li>
 * </ul>
 * 
 * @author Ilia Kolominsky
 * @since 2.0
 */
@EnabledOnValkeyClusterAvailable
@TestInstance(Lifecycle.PER_CLASS)
public class ValkeyGlideClusterConnectionFactoryIntegrationTests {

    private ValkeyGlideConnectionFactory connectionFactory;
    private ValkeyTemplate<String, String> template;

    @BeforeAll
    void setup() {
        // Create a cluster connection factory for tests
        connectionFactory = createClusterConnectionFactory();
        
        // Verify we're in cluster mode
        assertThat(connectionFactory.isClusterAware()).isTrue();

        // Create a template for easier testing
        template = new ValkeyTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(StringValkeySerializer.UTF_8);
        template.setValueSerializer(StringValkeySerializer.UTF_8);
        template.setHashKeySerializer(StringValkeySerializer.UTF_8);
        template.setHashValueSerializer(StringValkeySerializer.UTF_8);
        template.afterPropertiesSet();
    }

    @AfterAll
    void teardown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /**
     * Creates a cluster connection factory for testing.
     */
    private ValkeyGlideConnectionFactory createClusterConnectionFactory() {
        ValkeyClusterConfiguration clusterConfig = new ValkeyClusterConfiguration();
        
        // Use ClusterTestVariables for consistency with other tests
        clusterConfig.clusterNode(ClusterTestVariables.CLUSTER_NODE_1);
        clusterConfig.clusterNode(ClusterTestVariables.CLUSTER_NODE_2);
        clusterConfig.clusterNode(ClusterTestVariables.CLUSTER_NODE_3);
        clusterConfig.clusterNode(ClusterTestVariables.REPLICA_OF_NODE_1);
        return new ValkeyGlideConnectionFactory(clusterConfig, new DefaultValkeyGlideClientConfiguration());
    }

    @Test
    void testGetClusterConnection() {
        ValkeyClusterConnection clusterConnection = connectionFactory.getClusterConnection();
        assertThat(clusterConnection).isNotNull();
        assertThat(clusterConnection).isInstanceOf(ValkeyGlideClusterConnection.class);
        clusterConnection.close();
    }

    @Test
    void testHashOperations() {
        // Use hash tags to ensure all keys land on the same slot
        String key1 = "{hash}:basic";
        String key2 = "{hash}:putall";
        String key3 = "{hash}:putifabsent";
        String key4 = "{hash}:increment";
        String key5 = "{hash}:length";
        String key6 = "{hash}:delete";
        String key7 = "{hash}:random";
        String key8 = "{hash}:scan";
        String key9 = "{hash}:expiration";
        
        try {
            template.opsForHash().put(key1, "field1", "value1");
            template.opsForHash().put(key1, "field2", "value2");

            assertThat(template.opsForHash().get(key1, "field1")).isEqualTo("value1");
            assertThat(template.opsForHash().get(key1, "field2")).isEqualTo("value2");

            // PUTALL operation
            Map<String, String> hashMap = Map.of(
                "field1", "value1",
                "field2", "value2",
                "field3", "value3"
            );
            template.opsForHash().putAll(key2, hashMap);
            
            assertThat(template.opsForHash().get(key2, "field1")).isEqualTo("value1");
            assertThat(template.opsForHash().get(key2, "field2")).isEqualTo("value2");
            assertThat(template.opsForHash().get(key2, "field3")).isEqualTo("value3");

            // PUTIFABSENT operation
            Boolean putResult1 = template.opsForHash().putIfAbsent(key3, "field1", "value1");
            assertThat(putResult1).isTrue();
            Boolean putResult2 = template.opsForHash().putIfAbsent(key3, "field1", "different_value");
            assertThat(putResult2).isFalse();
            assertThat(template.opsForHash().get(key3, "field1")).isEqualTo("value1");

            // HASKEY operation
            assertThat(template.opsForHash().hasKey(key1, "field1")).isTrue();
            assertThat(template.opsForHash().hasKey(key1, "nonexistent")).isFalse();

            // MULTIGET operation
            List<Object> hashKeys = List.of("field1", "field2", "nonexistent");
            List<Object> multiGetResult = template.opsForHash().multiGet(key1, hashKeys);
            assertThat(multiGetResult).containsExactly("value1", "value2", null);

            // SIZE operation
            assertThat(template.opsForHash().size(key1)).isEqualTo(2);
            assertThat(template.opsForHash().size(key2)).isEqualTo(3);

            // KEYS operation
            Set<Object> keys = template.opsForHash().keys(key2);
            assertThat(keys).containsExactlyInAnyOrder("field1", "field2", "field3");

            // VALUES operation
            List<Object> values = template.opsForHash().values(key2);
            assertThat(values).containsExactlyInAnyOrder("value1", "value2", "value3");

            // ENTRIES operation (HGETALL)
            Map<Object, Object> entries = template.opsForHash().entries(key2);
            assertThat(entries).hasSize(3);
            assertThat(entries.get("field1")).isEqualTo("value1");
            assertThat(entries.get("field2")).isEqualTo("value2");
            assertThat(entries.get("field3")).isEqualTo("value3");

            // INCREMENT operations
            template.opsForHash().put(key4, "counter", "10");
            
            // Long increment
            Long incrResult1 = template.opsForHash().increment(key4, "counter", 5L);
            assertThat(incrResult1).isEqualTo(15L);
            
            Long incrResult2 = template.opsForHash().increment(key4, "counter", -3L);
            assertThat(incrResult2).isEqualTo(12L);
            
            // Double increment (using different field to avoid conflicts)
            template.opsForHash().put(key4, "floatcounter", "10.5");
            Double floatIncrResult = template.opsForHash().increment(key4, "floatcounter", 2.5);
            assertThat(floatIncrResult).isEqualTo(13.0);

            // LENGTHOFVALUE operation
            template.opsForHash().put(key5, "field1", "Hello World!");
            Long lengthResult = template.opsForHash().lengthOfValue(key5, "field1");
            assertThat(lengthResult).isEqualTo(12L);
            
            // Length of non-existent field
            Long lengthNonExistent = template.opsForHash().lengthOfValue(key5, "nonexistent");
            assertThat(lengthNonExistent).isEqualTo(0L);

            // DELETE hash fields operation
            template.opsForHash().put(key6, "field1", "value1");
            template.opsForHash().put(key6, "field2", "value2");
            template.opsForHash().put(key6, "field3", "value3");
            
            Long deleteResult = template.opsForHash().delete(key6, "field1", "field2");
            assertThat(deleteResult).isEqualTo(2L);
            assertThat(template.opsForHash().hasKey(key6, "field1")).isFalse();
            assertThat(template.opsForHash().hasKey(key6, "field2")).isFalse();
            assertThat(template.opsForHash().hasKey(key6, "field3")).isTrue();

            // RANDOM operations
            template.opsForHash().putAll(key7, Map.of(
                "field1", "value1",
                "field2", "value2",
                "field3", "value3",
                "field4", "value4"
            ));

            // Random key
            Object randomKey = template.opsForHash().randomKey(key7);
            assertThat(randomKey).isIn("field1", "field2", "field3", "field4");
            
            // Random entry
            Map.Entry<Object, Object> randomEntry = template.opsForHash().randomEntry(key7);
            assertThat(randomEntry.getKey()).isIn("field1", "field2", "field3", "field4");
            assertThat(randomEntry.getValue().toString()).startsWith("value");
            
            // Random keys with count
            List<Object> randomKeys = template.opsForHash().randomKeys(key7, 2);
            assertThat(randomKeys).hasSize(2);
            assertThat(randomKeys).allMatch(key -> List.of("field1", "field2", "field3", "field4").contains(key));

            // Random entries with count
            Map<Object, Object> randomEntries = template.opsForHash().randomEntries(key7, 2);
            assertThat(randomEntries).hasSize(2);
            randomEntries.forEach((k, v) -> {
                assertThat(k).isIn("field1", "field2", "field3", "field4");
                assertThat(v.toString()).startsWith("value");
            });

            // SCAN operation (using ValkeyCallback for direct access to connection)
            template.opsForHash().putAll(key8, Map.of(
                "scanfield1", "scanvalue1",
                "scanfield2", "scanvalue2",
                "scanfield3", "scanvalue3"
            ));
            
            // Test hScan using connection directly
            template.execute((ValkeyCallback<Void>) connection -> {
                try (var cursor = connection.hashCommands().hScan(key8.getBytes(), 
                        io.valkey.springframework.data.valkey.core.ScanOptions.scanOptions().count(10).build())) {
                    int count = 0;
                    while (cursor.hasNext()) {
                        Map.Entry<byte[], byte[]> entry = cursor.next();
                        String field = new String(entry.getKey());
                        String value = new String(entry.getValue());
                        assertThat(field).startsWith("scanfield");
                        assertThat(value).startsWith("scanvalue");
                        count++;
                    }
                    assertThat(count).isEqualTo(3);
                }
                return null;
            });

            // Test hash field expiration operations (requires Valkey 7.4+)
            if (isServerVersionAtLeast(7, 4)) {
                template.opsForHash().putAll(key9, Map.of(
                    "expirefield1", "expirevalue1",
                    "expirefield2", "expirevalue2",
                    "persistfield", "persistvalue"
                ));

                // Test hExpire - Set TTL in seconds
                template.execute((ValkeyCallback<List<Long>>) connection -> {
                    List<Long> result = connection.hashCommands().hExpire(key9.getBytes(), 60L, 
                        "expirefield1".getBytes(), "expirefield2".getBytes());
                    assertThat(result).containsExactly(1L, 1L);
                    return result;
                });

                // Test hpExpire - Set TTL in milliseconds  
                template.execute((ValkeyCallback<List<Long>>) connection -> {
                    List<Long> result = connection.hashCommands().hpExpire(key9.getBytes(), 60000L,
                        "expirefield1".getBytes());
                    assertThat(result).containsExactly(1L);
                    return result;
                });

                // Test hExpireAt - Set expiration at specific timestamp
                long futureTimestamp = System.currentTimeMillis() / 1000 + 120;
                template.execute((ValkeyCallback<List<Long>>) connection -> {
                    List<Long> result = connection.hashCommands().hExpireAt(key9.getBytes(), futureTimestamp,
                        "expirefield1".getBytes());
                    assertThat(result).containsExactly(1L);
                    return result;
                });

                // Test hpExpireAt - Set expiration at specific timestamp in milliseconds
                long futureTimestampMillis = System.currentTimeMillis() + 120000;
                template.execute((ValkeyCallback<List<Long>>) connection -> {
                    List<Long> result = connection.hashCommands().hpExpireAt(key9.getBytes(), futureTimestampMillis,
                        "expirefield2".getBytes());
                    assertThat(result).containsExactly(1L);
                    return result;
                });

                // Test hTtl - Get TTL in seconds
                template.execute((ValkeyCallback<List<Long>>) connection -> {
                    List<Long> result = connection.hashCommands().hTtl(key9.getBytes(), 
                        "expirefield1".getBytes(), "persistfield".getBytes());
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0)).isGreaterThan(0L);
                    assertThat(result.get(1)).isEqualTo(-1L);
                    return result;
                });

                template.execute((ValkeyCallback<List<Long>>) connection -> {
                    List<Long> result = connection.hashCommands().hpTtl(key9.getBytes(),
                        "expirefield1".getBytes(), "persistfield".getBytes());
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0)).isGreaterThan(0L);
                    assertThat(result.get(1)).isEqualTo(-1L);
                    return result;
                });

                // Test hPersist - Remove expiration from fields
                template.execute((ValkeyCallback<List<Long>>) connection -> {
                    List<Long> result = connection.hashCommands().hPersist(key9.getBytes(),
                        "expirefield1".getBytes(), "expirefield2".getBytes());
                    assertThat(result).containsExactly(1L, 1L);
                    return result;
                });

                // Verify fields no longer have expiration after hPersist
                template.execute((ValkeyCallback<List<Long>>) connection -> {
                    List<Long> result = connection.hashCommands().hTtl(key9.getBytes(),
                        "expirefield1".getBytes(), "expirefield2".getBytes());
                    assertThat(result).containsExactly(-1L, -1L);
                    return result;
                });
            }
        } finally {
            // Clean up all test keys
            template.delete(key1);
            template.delete(key2);
            template.delete(key3);
            template.delete(key4);
            template.delete(key5);
            template.delete(key6);
            template.delete(key7);
            template.delete(key8);
            template.delete(key9);
        }
    }

    @Test
    void testListOperations() {
        // Use hash tags to ensure all keys land on the same slot
        String key1 = "{list}:basic";
        String key2 = "{list}:push";
        String key3 = "{list}:range";
        String key4 = "{list}:insert";
        String key5 = "{list}:move";
        String key6 = "{list}:set";
        String key7 = "{list}:remove";
        String key8 = "{list}:blocking";
        String srcKey = "{list}:src";
        String dstKey = "{list}:dst";

        try {
            // Basic LPUSH/RPUSH operations
            template.opsForList().leftPush(key1, "value1");
            template.opsForList().leftPush(key1, "value2");
            template.opsForList().rightPush(key1, "value3");

            assertThat(template.opsForList().size(key1)).isEqualTo(3);
            
            // Test LRANGE to verify order
            List<String> range = template.opsForList().range(key1, 0, -1);
            assertThat(range).containsExactly("value2", "value1", "value3");

            // Test LINDEX - get element at index
            assertThat(template.opsForList().index(key1, 0)).isEqualTo("value2");
            assertThat(template.opsForList().index(key1, 1)).isEqualTo("value1");
            assertThat(template.opsForList().index(key1, 2)).isEqualTo("value3");
            
            // Test convenience methods getFirst and getLast
            String firstLastKey = "{list}:firstlast";
            template.opsForList().rightPushAll(firstLastKey, "first", "middle", "last");
            
            String firstElement = template.opsForList().getFirst(firstLastKey);
            assertThat(firstElement).isEqualTo("first");
            
            String lastElement = template.opsForList().getLast(firstLastKey);
            assertThat(lastElement).isEqualTo("last");
            
            // Test with empty list
            String emptyKey = "{list}:empty";
            String firstEmpty = template.opsForList().getFirst(emptyKey);
            assertThat(firstEmpty).isNull();
            
            String lastEmpty = template.opsForList().getLast(emptyKey);
            assertThat(lastEmpty).isNull();
            
            template.delete(firstLastKey);

            // Test LPOP/RPOP
            assertThat(template.opsForList().leftPop(key1)).isEqualTo("value2");
            assertThat(template.opsForList().rightPop(key1)).isEqualTo("value3");
            assertThat(template.opsForList().size(key1)).isEqualTo(1);

            // Test LPUSHX/RPUSHX (push only if key exists)
            template.opsForList().leftPushIfPresent(key2, "shouldnotwork");
            assertThat(template.opsForList().size(key2)).isEqualTo(0);
            
            template.opsForList().leftPush(key2, "initial");
            template.opsForList().leftPushIfPresent(key2, "shouldwork");
            template.opsForList().rightPushIfPresent(key2, "alsowork");
            
            List<String> key2Range = template.opsForList().range(key2, 0, -1);
            assertThat(key2Range).containsExactly("shouldwork", "initial", "alsowork");

            // Test LEFTPUSHALL operations
            String leftPushAllKey = "{list}:leftpushall";
            
            template.opsForList().leftPushAll(leftPushAllKey, "first", "second", "third");
            List<String> leftPushAllResult1 = template.opsForList().range(leftPushAllKey, 0, -1);
            assertThat(leftPushAllResult1).containsExactly("third", "second", "first");
            
            template.delete(leftPushAllKey);
            template.opsForList().leftPushAll(leftPushAllKey, List.of("a", "b", "c"));
            List<String> leftPushAllResult2 = template.opsForList().range(leftPushAllKey, 0, -1);
            assertThat(leftPushAllResult2).containsExactly("c", "b", "a");
            
            template.delete(leftPushAllKey);

            // Test LRANGE and LTRIM
            template.opsForList().rightPushAll(key3, "a", "b", "c", "d", "e");
            List<String> beforeTrim = template.opsForList().range(key3, 0, -1);
            assertThat(beforeTrim).containsExactly("a", "b", "c", "d", "e");
            
            template.opsForList().trim(key3, 1, 3);
            List<String> afterTrim = template.opsForList().range(key3, 0, -1);
            assertThat(afterTrim).containsExactly("b", "c", "d");

            // Test LINSERT - insert before/after pivot
            template.opsForList().rightPushAll(key4, "first", "pivot", "last");
            
            template.execute((ValkeyCallback<Long>) connection -> 
                connection.listCommands().lInsert(key4.getBytes(), 
                    ValkeyListCommands.Position.BEFORE, "pivot".getBytes(), "before_pivot".getBytes()));
            
            template.execute((ValkeyCallback<Long>) connection -> 
                connection.listCommands().lInsert(key4.getBytes(), 
                    ValkeyListCommands.Position.AFTER, "pivot".getBytes(), "after_pivot".getBytes()));
            
            List<String> insertResult = template.opsForList().range(key4, 0, -1);
            assertThat(insertResult).containsExactly("first", "before_pivot", "pivot", "after_pivot", "last");

            // Test LMOVE (move element between lists)
            template.opsForList().rightPushAll(key5, "move1", "move2", "move3");
            String dstKey5 = key5 + "_dst";
            
            template.execute((ValkeyCallback<byte[]>) connection -> 
                connection.listCommands().lMove(key5.getBytes(), dstKey5.getBytes(), 
                    ValkeyListCommands.Direction.LEFT, ValkeyListCommands.Direction.RIGHT));
            
            assertThat(template.opsForList().range(key5, 0, -1)).containsExactly("move2", "move3");
            assertThat(template.opsForList().range(dstKey5, 0, -1)).containsExactly("move1");
            
            // Test high-level move operations
            String moveHighKey1 = "{list}:movehigh1";
            String moveHighKey2 = "{list}:movehigh2";
            template.opsForList().rightPushAll(moveHighKey1, "high1", "high2", "high3");
            
            String movedValue1 = template.opsForList().move(
                io.valkey.springframework.data.valkey.core.ListOperations.MoveFrom.fromTail(moveHighKey1),
                io.valkey.springframework.data.valkey.core.ListOperations.MoveTo.toHead(moveHighKey2)
            );
            assertThat(movedValue1).isEqualTo("high3");
            assertThat(template.opsForList().range(moveHighKey1, 0, -1)).containsExactly("high1", "high2");
            assertThat(template.opsForList().range(moveHighKey2, 0, -1)).containsExactly("high3");
            
            String movedValue2 = template.opsForList().move(
                io.valkey.springframework.data.valkey.core.ListOperations.MoveFrom.fromHead(moveHighKey1),
                io.valkey.springframework.data.valkey.core.ListOperations.MoveTo.toTail(moveHighKey2)
            );
            assertThat(movedValue2).isEqualTo("high1");
            assertThat(template.opsForList().range(moveHighKey1, 0, -1)).containsExactly("high2");
            assertThat(template.opsForList().range(moveHighKey2, 0, -1)).containsExactly("high3", "high1");
            
            template.delete(moveHighKey1);
            template.delete(moveHighKey2);

            // Test LSET - set element at index
            template.opsForList().rightPushAll(key6, "original1", "original2", "original3");
            template.opsForList().set(key6, 1, "modified");
            
            List<String> setResult = template.opsForList().range(key6, 0, -1);
            assertThat(setResult).containsExactly("original1", "modified", "original3");

            // Test LREM - remove occurrences of value
            template.opsForList().rightPushAll(key7, "remove", "keep", "remove", "keep", "remove");
            Long removedCount = template.opsForList().remove(key7, 2, "remove");
            assertThat(removedCount).isEqualTo(2L);
            
            List<String> removeResult = template.opsForList().range(key7, 0, -1);
            assertThat(removeResult).containsExactly("keep", "keep", "remove");

            // Test LPOP/RPOP with count (Valkey 6.2+)
            template.opsForList().rightPushAll(key8, "pop1", "pop2", "pop3", "pop4", "pop5");
            
            List<String> leftPopped = template.opsForList().leftPop(key8, 2);
            if (leftPopped != null) {
                assertThat(leftPopped).containsExactly("pop1", "pop2");
                
                List<String> rightPopped = template.opsForList().rightPop(key8, 2);
                assertThat(rightPopped).containsExactly("pop5", "pop4");
                
                assertThat(template.opsForList().range(key8, 0, -1)).containsExactly("pop3");
            }

            // Test RPOPLPUSH - atomically move from end to beginning
            template.opsForList().rightPushAll(srcKey, "src1", "src2", "src3");
            template.opsForList().rightPushAll(dstKey, "dst1");
            
            String moved = template.opsForList().rightPopAndLeftPush(srcKey, dstKey);
            assertThat(moved).isEqualTo("src3");
            assertThat(template.opsForList().range(srcKey, 0, -1)).containsExactly("src1", "src2");
            assertThat(template.opsForList().range(dstKey, 0, -1)).containsExactly("src3", "dst1");

            // Test LPOS - find position of element (Valkey 6.0.6+)
            if (isServerVersionAtLeast(6, 1)) {
                String posKey = "{list}:position";
                template.opsForList().rightPushAll(posKey, "a", "b", "c", "b", "d");
                
                Long firstPos = template.opsForList().indexOf(posKey, "b");
                assertThat(firstPos).isEqualTo(1L);
                
                Long lastPos = template.opsForList().lastIndexOf(posKey, "b");
                assertThat(lastPos).isEqualTo(3L);
                
                Long notFoundPos = template.opsForList().indexOf(posKey, "z");
                assertThat(notFoundPos).isNull();
                
                Long notFoundLastPos = template.opsForList().lastIndexOf(posKey, "z");
                assertThat(notFoundLastPos).isNull();
                
                List<Long> positions = template.execute((ValkeyCallback<List<Long>>) connection -> 
                    connection.listCommands().lPos(posKey.getBytes(), "b".getBytes(), null, 2));
                assertThat(positions).containsExactly(1L, 3L);
                
                template.delete(posKey);
            }

        } finally {
            // Clean up all test keys
            template.delete(key1);
            template.delete(key2);
            template.delete(key3);
            template.delete(key4);
            template.delete(key5);
            template.delete(key5 + "_dst");
            template.delete(key6);
            template.delete(key7);
            template.delete(key8);
            template.delete(srcKey);
            template.delete(dstKey);
        }
    }

    @Test
    void testSetOperations() {
        // Use hash tags to ensure all keys land on the same slot
        String key1 = "{set}:basic";
        String key2 = "{set}:operations";
        String key3 = "{set}:union";
        String key4 = "{set}:intersection";
        String key5 = "{set}:difference";
        String key6 = "{set}:random";
        String key7 = "{set}:pop";
        String key8 = "{set}:move";
        String key9 = "{set}:scan";
        String key10 = "{set}:scanhigh";
        String destKey = "{set}:dest";

        try {
            // Basic SADD operations
            Long addResult1 = template.opsForSet().add(key1, "value1", "value2", "value3");
            assertThat(addResult1).isEqualTo(3L);

            // Add duplicate - should return 0
            Long addResult2 = template.opsForSet().add(key1, "value2");
            assertThat(addResult2).isEqualTo(0L);

            // SCARD - Set cardinality (size)
            assertThat(template.opsForSet().size(key1)).isEqualTo(3);

            // SISMEMBER - Check membership
            assertThat(template.opsForSet().isMember(key1, "value2")).isTrue();
            assertThat(template.opsForSet().isMember(key1, "nonexistent")).isFalse();

            // SMISMEMBER - Check multiple membership (Valkey 6.2+)
            if (isServerVersionAtLeast(6, 2)) {
                template.execute((ValkeyCallback<List<Boolean>>) connection -> {
                    Map<Object, Boolean> membershipMap = template.opsForSet().isMember(key1, "value1", "nonexistent", "value3");
                    assertThat(membershipMap.get("value1")).isTrue();
                    assertThat(membershipMap.get("nonexistent")).isFalse();
                    assertThat(membershipMap.get("value3")).isTrue();
                    return null;
                });
            }

            // SMEMBERS - Get all members
            Set<String> members = template.opsForSet().members(key1);
            assertThat(members).containsExactlyInAnyOrder("value1", "value2", "value3");

            // SREM - Remove members
            Long remResult1 = template.opsForSet().remove(key1, "value2");
            assertThat(remResult1).isEqualTo(1L);
            assertThat(template.opsForSet().size(key1)).isEqualTo(2);

            Long remResult2 = template.opsForSet().remove(key1, "value1", "nonexistent");
            assertThat(remResult2).isEqualTo(1L);
            assertThat(template.opsForSet().size(key1)).isEqualTo(1);

            // Set up sets for set operations
            template.opsForSet().add(key2, "a", "b", "c");
            template.opsForSet().add(key3, "b", "c", "d");
            template.opsForSet().add(key4, "c", "d", "e");

            // SUNION - Union of sets
            Set<String> unionResult = template.opsForSet().union(key2, key3);
            assertThat(unionResult).containsExactlyInAnyOrder("a", "b", "c", "d");

            // SUNION with multiple keys
            Set<String> unionMultiResult = template.opsForSet().union(key2, List.of(key3, key4));
            assertThat(unionMultiResult).containsExactlyInAnyOrder("a", "b", "c", "d", "e");

            // SUNIONSTORE - Store union result
            Long unionStoreResult = template.opsForSet().unionAndStore(key2, key3, destKey);
            assertThat(unionStoreResult).isEqualTo(4L);
            assertThat(template.opsForSet().members(destKey)).containsExactlyInAnyOrder("a", "b", "c", "d");
            template.delete(destKey);

            // SUNIONSTORE with multiple keys
            Long unionStoreMultiResult = template.opsForSet().unionAndStore(key2, List.of(key3, key4), destKey);
            assertThat(unionStoreMultiResult).isEqualTo(5L);
            assertThat(template.opsForSet().members(destKey)).containsExactlyInAnyOrder("a", "b", "c", "d", "e");
            template.delete(destKey);

            // SINTER - Intersection of sets
            Set<String> intersectResult = template.opsForSet().intersect(key2, key3);
            assertThat(intersectResult).containsExactlyInAnyOrder("b", "c");

            // SINTER with multiple keys
            Set<String> intersectMultiResult = template.opsForSet().intersect(key2, List.of(key3, key4));
            assertThat(intersectMultiResult).containsExactlyInAnyOrder("c");

            // SINTERSTORE - Store intersection result
            Long intersectStoreResult = template.opsForSet().intersectAndStore(key2, key3, destKey);
            assertThat(intersectStoreResult).isEqualTo(2L);
            assertThat(template.opsForSet().members(destKey)).containsExactlyInAnyOrder("b", "c");
            template.delete(destKey);

            // SINTERSTORE with multiple keys
            Long intersectStoreMultiResult = template.opsForSet().intersectAndStore(key2, List.of(key3, key4), destKey);
            assertThat(intersectStoreMultiResult).isEqualTo(1L);
            assertThat(template.opsForSet().members(destKey)).containsExactlyInAnyOrder("c");
            template.delete(destKey);

            // SDIFF - Difference of sets
            Set<String> diffResult = template.opsForSet().difference(key2, key3);
            assertThat(diffResult).containsExactlyInAnyOrder("a");

            // SDIFF with multiple keys
            Set<String> diffMultiResult = template.opsForSet().difference(key2, List.of(key3, key4));
            assertThat(diffMultiResult).containsExactlyInAnyOrder("a");

            // SDIFFSTORE - Store difference result
            Long diffStoreResult = template.opsForSet().differenceAndStore(key2, key3, destKey);
            assertThat(diffStoreResult).isEqualTo(1L);
            assertThat(template.opsForSet().members(destKey)).containsExactlyInAnyOrder("a");
            template.delete(destKey);

            // SDIFFSTORE with multiple keys
            Long diffStoreMultiResult = template.opsForSet().differenceAndStore(key2, List.of(key3, key4), destKey);
            assertThat(diffStoreMultiResult).isEqualTo(1L);
            assertThat(template.opsForSet().members(destKey)).containsExactlyInAnyOrder("a");
            template.delete(destKey);

            // SRANDMEMBER operations
            template.opsForSet().add(key6, "rand1", "rand2", "rand3", "rand4", "rand5");
            
            // SRANDMEMBER - Single random member
            String randomMember = template.opsForSet().randomMember(key6);
            assertThat(randomMember).isIn("rand1", "rand2", "rand3", "rand4", "rand5");

            // SRANDMEMBER with count
            List<String> randomMembers = template.opsForSet().randomMembers(key6, 3);
            assertThat(randomMembers).hasSize(3);
            assertThat(randomMembers).allMatch(member -> 
                List.of("rand1", "rand2", "rand3", "rand4", "rand5").contains(member));
            
            // SRANDMEMBER with negative count
            Set<String> randomDistinct = template.opsForSet().distinctRandomMembers(key6, 3);
            assertThat(randomDistinct).hasSizeLessThanOrEqualTo(3);
            assertThat(randomDistinct).allMatch(member -> 
                List.of("rand1", "rand2", "rand3", "rand4", "rand5").contains(member));

            // SPOP operations
            template.opsForSet().add(key7, "pop1", "pop2", "pop3", "pop4");
            
            // SPOP - Single pop
            String poppedMember = template.opsForSet().pop(key7);
            assertThat(poppedMember).isIn("pop1", "pop2", "pop3", "pop4");
            assertThat(template.opsForSet().size(key7)).isEqualTo(3);

            // SPOP with count
            List<String> poppedMembers = template.opsForSet().pop(key7, 2);
            if (poppedMembers != null) {
                assertThat(poppedMembers).hasSize(2);
                assertThat(template.opsForSet().size(key7)).isEqualTo(1);
            }

            // SMOVE - Move member between sets
            template.opsForSet().add(key8, "move1", "move2", "move3");
            String moveDestKey = key8 + "_dest";
            
            Boolean moveResult = template.opsForSet().move(key8, "move2", moveDestKey);
            assertThat(moveResult).isTrue();
            assertThat(template.opsForSet().isMember(key8, "move2")).isFalse();
            assertThat(template.opsForSet().isMember(moveDestKey, "move2")).isTrue();

            // Try to move non-existent member
            Boolean moveResult2 = template.opsForSet().move(key8, "nonexistent", moveDestKey);
            assertThat(moveResult2).isFalse();
            
            template.delete(moveDestKey);

            // SSCAN operation
            template.opsForSet().add(key9, "scan1", "scan2", "scan3", "scan4", "scan5");
            
            template.execute((ValkeyCallback<Void>) connection -> {
                try (var cursor = connection.setCommands().sScan(key9.getBytes(), 
                        io.valkey.springframework.data.valkey.core.ScanOptions.scanOptions().count(10).build())) {
                    Set<String> scannedMembers = new java.util.HashSet<>();
                    while (cursor.hasNext()) {
                        byte[] member = cursor.next();
                        scannedMembers.add(new String(member));
                    }
                    assertThat(scannedMembers).containsExactlyInAnyOrder("scan1", "scan2", "scan3", "scan4", "scan5");
                }
                return null;
            });

            // Test high-level SetOperations.scan() method
            template.opsForSet().add(key10, "high1", "high2", "high3", "high4", "high5");
            
            try (io.valkey.springframework.data.valkey.core.Cursor<String> cursor = 
                    template.opsForSet().scan(key10, 
                        io.valkey.springframework.data.valkey.core.ScanOptions.scanOptions().count(10).build())) {
                Set<String> scannedHighMembers = new java.util.HashSet<>();
                while (cursor.hasNext()) {
                    String member = cursor.next();
                    scannedHighMembers.add(member);
                }
                assertThat(scannedHighMembers).containsExactlyInAnyOrder("high1", "high2", "high3", "high4", "high5");
            }

        } finally {
            // Clean up all test keys
            template.delete(key1);
            template.delete(key2);
            template.delete(key3);
            template.delete(key4);
            template.delete(key5);
            template.delete(key6);
            template.delete(key7);
            template.delete(key8);
            template.delete(key9);
            template.delete(key10);
            template.delete(destKey);
            template.delete(key8 + "_dest");
        }
    }

    @Test
    void testStringOperations() {
        // Use hash tags to ensure all keys land on the same slot for multi-key operations
        String key1 = "{string}:basic";
        String key2 = "{string}:expire";
        String key3 = "{string}:setnx";
        String key4 = "{string}:getset";
        String multi1Key = "{string}:multi1";
        String multi2Key = "{string}:multi2";
        String multi3Key = "{string}:multi3";
        String msetnx1Key = "{string}:msetnx1";
        String msetnx2Key = "{string}:msetnx2";
        String counterKey = "{string}:counter";
        String floatCounterKey = "{string}:floatcounter";
        String appendKey = "{string}:append";
        String setRangeKey = "{string}:setrange";
        String bitKey = "{string}:bits";
        String bitKey1 = "{string}:bit1";
        String bitKey2 = "{string}:bit2";
        String bitDestKey = "{string}:bitop";
        
        try {
            String value1 = "Hello, valkey-glide!";
            
            template.opsForValue().set(key1, value1);
            String retrieved1 = template.opsForValue().get(key1);
            assertThat(retrieved1).isEqualTo(value1);

            // SET with expiration
            String value2 = "Expiring value";
            template.opsForValue().set(key2, value2, Duration.ofSeconds(60));
            String retrieved2 = template.opsForValue().get(key2);
            assertThat(retrieved2).isEqualTo(value2);

            // SETNX (Set if Not eXists)
            String value3 = "New value";
            Boolean setResult1 = template.opsForValue().setIfAbsent(key3, value3);
            assertThat(setResult1).isTrue();
            Boolean setResult2 = template.opsForValue().setIfAbsent(key3, "Different value");
            assertThat(setResult2).isFalse();
            assertThat(template.opsForValue().get(key3)).isEqualTo(value3);

            // GETSET operation
            template.opsForValue().set(key4, "old value");
            String oldValue = template.opsForValue().getAndSet(key4, "new value");
            assertThat(oldValue).isEqualTo("old value");
            assertThat(template.opsForValue().get(key4)).isEqualTo("new value");

            // MGET/MSET operations
            Map<String, String> multiValues = Map.of(
                multi1Key, "value1",
                multi2Key, "value2",
                multi3Key, "value3"
            );
            template.opsForValue().multiSet(multiValues);
            
            List<String> multiRetrieved = template.opsForValue().multiGet(List.of(
                multi1Key, multi2Key, multi3Key));
            assertThat(multiRetrieved).containsExactly("value1", "value2", "value3");
         
            // MSETNX operation
            Map<String, String> msetnxValues = Map.of(
                msetnx1Key, "msetnx_value1",
                msetnx2Key, "msetnx_value2"
            );
            Boolean msetnxResult = template.opsForValue().multiSetIfAbsent(msetnxValues);
            assertThat(msetnxResult).isTrue();
            assertThat(template.opsForValue().get(msetnx1Key)).isEqualTo("msetnx_value1");

            // INCR/DECR operations
            template.opsForValue().set(counterKey, "10");
            
            Long incResult1 = template.opsForValue().increment(counterKey);
            assertThat(incResult1).isEqualTo(11L);
            
            Long incResult2 = template.opsForValue().increment(counterKey, 5L);
            assertThat(incResult2).isEqualTo(16L);
            
            Long decrResult1 = template.opsForValue().decrement(counterKey);
            assertThat(decrResult1).isEqualTo(15L);
            
            Long decrResult2 = template.opsForValue().decrement(counterKey, 3L);
            assertThat(decrResult2).isEqualTo(12L);
            
            // Floating point increment
            template.opsForValue().set(floatCounterKey, "10");
            Double incFloatResult = template.opsForValue().increment(floatCounterKey, 2.5);
            assertThat(incFloatResult).isEqualTo(12.5);

            // APPEND operation
            template.opsForValue().set(appendKey, "Hello");
            Integer appendResult = template.opsForValue().append(appendKey, " World!");
            assertThat(appendResult).isEqualTo(12);
            assertThat(template.opsForValue().get(appendKey)).isEqualTo("Hello World!");

            // STRING LENGTH operation
            Long strlenResult = template.opsForValue().size(appendKey);
            assertThat(strlenResult).isEqualTo(12L);

            // GETRANGE operation
            String rangeResult = template.opsForValue().get(appendKey, 0, 4);
            assertThat(rangeResult).isEqualTo("Hello");
            
            // SETRANGE operation
            template.opsForValue().set(appendKey, "Hello World!", 6);
            template.opsForValue().set(setRangeKey, "Hello");
            template.opsForValue().set(setRangeKey, " Valkey", 5);
            assertThat(template.opsForValue().get(setRangeKey)).startsWith("Hello");
            
            // BIT operations
            template.opsForValue().set(bitKey, "a");
            
            Boolean getBitResult = template.execute((ValkeyCallback<Boolean>) connection -> 
                connection.stringCommands().getBit(bitKey.getBytes(), 1));
            assertThat(getBitResult).isTrue();
            
            Boolean setBitResult = template.execute((ValkeyCallback<Boolean>) connection -> 
                connection.stringCommands().setBit(bitKey.getBytes(), 0, true));
            assertThat(setBitResult).isFalse();
            
            Long bitCountResult = template.execute((ValkeyCallback<Long>) connection -> 
                connection.stringCommands().bitCount(bitKey.getBytes()));
            assertThat(bitCountResult).isGreaterThan(0L);

            // BITOP operation
            template.opsForValue().set(bitKey1, "a");
            template.opsForValue().set(bitKey2, "b");
            
            Long bitOpResult = template.execute((ValkeyCallback<Long>) connection -> 
                connection.stringCommands().bitOp(ValkeyStringCommands.BitOperation.AND, 
                    bitDestKey.getBytes(), bitKey1.getBytes(), bitKey2.getBytes()));
            assertThat(bitOpResult).isEqualTo(1L);
        } finally {
            // Clean up all test keys
            template.delete(key1);
            template.delete(key2);
            template.delete(key3);
            template.delete(key4);
            template.delete(multi1Key);
            template.delete(multi2Key);
            template.delete(multi3Key);
            template.delete(msetnx1Key);
            template.delete(msetnx2Key);
            template.delete(counterKey);
            template.delete(floatCounterKey);
            template.delete(appendKey);
            template.delete(setRangeKey);
            template.delete(bitKey);
            template.delete(bitKey1);
            template.delete(bitKey2);
            template.delete(bitDestKey);
        }
    }

    /**
     * Note: Transactions (MULTI/EXEC) are NOT supported in cluster mode.
     * This test is intentionally omitted as cluster mode does not support transactions
     * that span multiple keys, and even single-key transactions have limited support.
     */

    /**
     * Checks if the server version is at least the specified major.minor version.
     */
    private boolean isServerVersionAtLeast(int majorVersion, int minorVersion) {
        return template.execute((ValkeyCallback<Boolean>) connection -> {
            Properties serverInfo = connection.serverCommands().info("server");
            String versionString = serverInfo.getProperty("valkey_version",
                    serverInfo.getProperty("redis_version"));
            
            if (versionString == null) {
                versionString = serverInfo.getProperty("server_version");
            }
            
            if (versionString == null) {
                return false;
            }
            
            String[] versionParts = versionString.split("\\.");
            if (versionParts.length < 2) {
                return false;
            }
            
            int serverMajor = Integer.parseInt(versionParts[0]);
            int serverMinor = Integer.parseInt(versionParts[1]);
            
            if (serverMajor > majorVersion) {
                return true;
            } else if (serverMajor == majorVersion) {
                return serverMinor >= minorVersion;
            } else {
                return false;
            }
        });
    }
}
