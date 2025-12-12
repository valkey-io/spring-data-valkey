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

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeyPipelineException;

/**
 * Comprehensive integration tests for {@link ValkeyGlideConnection} covering
 * the ValkeyConnection interface methods directly.
 * 
 * <p>These tests validate the implementation of ValkeyConnection interface methods and ensure
 * standard behavior for basic and edge cases including:
 * <ul>
 *   <li>Connection lifecycle: close(), isClosed(), getNativeConnection()</li>
 *   <li>Pipeline functionality: openPipeline() → commands → closePipeline()</li>
 *   <li>State management: isPipelined(), isQueueing() interactions</li>
 *   <li>Empty pipeline should return an empty list</li>
 *   <li>getSentinelConnection() - not implemented, should throw exception</li>
 *   <li>Pipeline vs transaction mode isolation</li>
 *   <li>Error conditions and edge cases</li>
 *   <li>Complex scenarios: large pipelines, concurrent access</li>
 * </ul>
 *
 * <p>This test class focuses on validating the core ValkeyConnection interface
 * compliance, with particular emphasis on pipeline functionality which mirrors
 * the comprehensive transaction testing approach used in ValkeyGlideConnectionTxCommandsIntegrationTests.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            // Connection lifecycle test keys
            "test:conn:lifecycle:key", "test:conn:native:key",
            
            // Basic pipeline test keys
            "test:pipe:basic:key1", "test:pipe:basic:key2", "test:pipe:basic:string", "test:pipe:basic:hash",
            
            // Consecutive pipeline test keys
            "test:pipe:consecutive:key1", "test:pipe:consecutive:key2", "test:pipe:consecutive:key3",
            
            // Mixed command type test keys
            "test:pipe:mixed:string", "test:pipe:mixed:hash", "test:pipe:mixed:list", "test:pipe:mixed:set",
            "test:pipe:mixed:zset", "test:pipe:mixed:geo",
            
            // State management test keys
            "test:pipe:state:key", "test:pipe:state:isolation:key",
            
            // Error handling test keys
            "test:pipe:error:key", "test:pipe:error:nested", "test:pipe:error:exception",
            
            // Large pipeline test keys
            "test:pipe:large:key", "test:pipe:performance:key",
            
            // Concurrent test keys (with thread ID suffix pattern)
            "test:pipe:concurrent:shared", "test:pipe:concurrent:key",
            
            // Result conversion test keys
            "test:pipe:conversion:key", "test:pipe:conversion:counter",
            
            // Edge case test keys
            "test:pipe:edge:nonexistent", "test:pipe:edge:empty"
        };
    }

    // ==================== Connection Lifecycle Tests ====================

    @Test
    void testConnectionLifecycle() {
        String key = "test:conn:lifecycle:key";
        
        try {
            // Test initial state
            assertThat(connection.isClosed()).isFalse();
            
            // Test basic operation works
            connection.stringCommands().set(key.getBytes(), "test_value".getBytes());
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("test_value".getBytes());
            
            // Test connection is still open
            assertThat(connection.isClosed()).isFalse();
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testGetNativeConnection() {
        String key = "test:conn:native:key";
        
        try {
            // Verify native connection exists and is not null
            Object nativeConnection = connection.getNativeConnection();
            assertThat(nativeConnection).isNotNull();
            assertThat(nativeConnection.getClass().getName()).contains("GlideClient");
            
            // Verify connection is functional
            connection.stringCommands().set(key.getBytes(), "native_test".getBytes());
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("native_test".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @Disabled("⚠️  WARNING: getSentinelConnection() not implemented, rework the test when it is")
    void testGetSentinelConnection() {
        // getSentinelConnection is not implemented yet
        assertThatThrownBy(() -> connection.getSentinelConnection())
            .isInstanceOf(InvalidDataAccessResourceUsageException.class)
            .hasMessageContaining("No sentinels configured");
    }

    // ==================== Basic Pipeline Tests ====================

    @Test
    void testBasicPipelineFlow() {
        String key1 = "test:pipe:basic:key1";
        String key2 = "test:pipe:basic:key2";
        
        try {
            // Verify initial state
            assertThat(connection.isPipelined()).isFalse();
            
            // Start pipeline
            connection.openPipeline();
            assertThat(connection.isPipelined()).isTrue();
            
            // Queue commands - should return null in pipeline mode
            Object setResult1 = connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            Object setResult2 = connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            Object getResult1 = connection.stringCommands().get(key1.getBytes());
            Object getResult2 = connection.stringCommands().get(key2.getBytes());
            
            // All commands in pipeline should return null (queued)
            assertThat(setResult1).isNull();
            assertThat(setResult2).isNull();
            assertThat(getResult1).isNull();
            assertThat(getResult2).isNull();
            
            // Still in pipeline state
            assertThat(connection.isPipelined()).isTrue();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Pipeline should complete
            assertThat(connection.isPipelined()).isFalse();
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4); // 2 SETs + 2 GETs
            
            // Verify results - SET commands typically return boolean true
            assertThat(results.get(0)).isEqualTo(true); // SET result
            assertThat(results.get(1)).isEqualTo(true); // SET result
            assertThat(results.get(2)).isEqualTo("value1".getBytes()); // GET result
            assertThat(results.get(3)).isEqualTo("value2".getBytes()); // GET result
            
            // Verify values were actually set
            byte[] actualValue1 = connection.stringCommands().get(key1.getBytes());
            byte[] actualValue2 = connection.stringCommands().get(key2.getBytes());
            assertThat(actualValue1).isEqualTo("value1".getBytes());
            assertThat(actualValue2).isEqualTo("value2".getBytes());
            
        } finally {
            cleanupKeys(key1, key2);
        }
    }

    @Test
    void testEmptyPipeline() {
        // Start and immediately execute empty pipeline
        assertThat(connection.isPipelined()).isFalse();
        
        connection.openPipeline();
        assertThat(connection.isPipelined()).isTrue();
        
        List<Object> results = connection.closePipeline();
        
        // Empty pipeline should return empty list, not null
        assertThat(connection.isPipelined()).isFalse();
        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    @Test
    void testMultipleConsecutivePipelines() {
        String key1 = "test:pipe:consecutive:key1";
        String key2 = "test:pipe:consecutive:key2";
        String key3 = "test:pipe:consecutive:key3";
        
        try {
            // First pipeline
            connection.openPipeline();
            connection.stringCommands().set(key1.getBytes(), "pipe1_value".getBytes());
            List<Object> results1 = connection.closePipeline();
            
            assertThat(connection.isPipelined()).isFalse();
            assertThat(results1).hasSize(1);
            assertThat(results1.get(0)).isEqualTo(true);
            
            // Second pipeline
            connection.openPipeline();
            connection.stringCommands().set(key2.getBytes(), "pipe2_value".getBytes());
            connection.stringCommands().get(key1.getBytes()); // Read from first pipeline
            List<Object> results2 = connection.closePipeline();
            
            assertThat(connection.isPipelined()).isFalse();
            assertThat(results2).hasSize(2);
            assertThat(results2.get(0)).isEqualTo(true);
            assertThat(results2.get(1)).isEqualTo("pipe1_value".getBytes());
            
            // Third empty pipeline
            connection.openPipeline();
            List<Object> results3 = connection.closePipeline();
            
            assertThat(connection.isPipelined()).isFalse();
            assertThat(results3).isEmpty();
            
            // Verify final state
            assertThat(connection.stringCommands().get(key1.getBytes())).isEqualTo("pipe1_value".getBytes());
            assertThat(connection.stringCommands().get(key2.getBytes())).isEqualTo("pipe2_value".getBytes());
            assertThat(connection.stringCommands().get(key3.getBytes())).isNull();
            
        } finally {
            cleanupKeys(key1, key2, key3);
        }
    }

    // ==================== Mixed Command Type Pipeline Tests ====================

    @Test
    void testPipelineWithMixedCommandTypes() {
        String stringKey = "test:pipe:mixed:string";
        String hashKey = "test:pipe:mixed:hash";
        String listKey = "test:pipe:mixed:list";
        String setKey = "test:pipe:mixed:set";
        String zsetKey = "test:pipe:mixed:zset";
        
        try {
            connection.openPipeline();
            
            // String commands
            connection.stringCommands().set(stringKey.getBytes(), "string_value".getBytes());
            connection.stringCommands().get(stringKey.getBytes());
            
            // Hash commands
            connection.hashCommands().hSet(hashKey.getBytes(), "field1".getBytes(), "hash_value".getBytes());
            connection.hashCommands().hGet(hashKey.getBytes(), "field1".getBytes());
            
            // List commands
            connection.listCommands().lPush(listKey.getBytes(), "list_item".getBytes());
            connection.listCommands().lLen(listKey.getBytes());
            
            // Set commands
            connection.setCommands().sAdd(setKey.getBytes(), "set_member".getBytes());
            connection.setCommands().sCard(setKey.getBytes());
            
            // ZSet commands
            connection.zSetCommands().zAdd(zsetKey.getBytes(), 1.0, "zset_member".getBytes());
            connection.zSetCommands().zCard(zsetKey.getBytes());
            
            List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(10);
            
            // Verify write results
            assertThat(results.get(0)).isEqualTo(true); // SET
            assertThat(results.get(2)).isEqualTo(true); // HSET
            assertThat(results.get(4)).isEqualTo(1L);   // LPUSH (returns length)
            assertThat(results.get(6)).isEqualTo(1L);   // SADD (returns count added)
            assertThat(results.get(8)).isEqualTo(true); // ZADD
            
            // Verify read results
            assertThat(results.get(1)).isEqualTo("string_value".getBytes()); // GET
            assertThat(results.get(3)).isEqualTo("hash_value".getBytes());   // HGET
            assertThat(results.get(5)).isEqualTo(1L);                        // LLEN
            assertThat(results.get(7)).isEqualTo(1L);                        // SCARD
            assertThat(results.get(9)).isEqualTo(1L);                        // ZCARD
            
            // Verify values exist outside pipeline
            assertThat(connection.stringCommands().get(stringKey.getBytes())).isEqualTo("string_value".getBytes());
            assertThat(connection.hashCommands().hGet(hashKey.getBytes(), "field1".getBytes())).isEqualTo("hash_value".getBytes());
            assertThat(connection.listCommands().lLen(listKey.getBytes())).isEqualTo(1L);
            assertThat(connection.setCommands().sCard(setKey.getBytes())).isEqualTo(1L);
            assertThat(connection.zSetCommands().zCard(zsetKey.getBytes())).isEqualTo(1L);
            
        } finally {
            cleanupKeys(stringKey, hashKey, listKey, setKey, zsetKey);
        }
    }

    @Test
    void testPipelineWithGeoCommands() {
        String geoKey = "test:pipe:mixed:geo";
        
        try {
            connection.openPipeline();
            
            // Add geo location
            connection.geoCommands().geoAdd(geoKey.getBytes(),
                new org.springframework.data.geo.Point(13.361389, 38.115556), "Palermo".getBytes());
            
            // Get geo position
            connection.geoCommands().geoPos(geoKey.getBytes(), "Palermo".getBytes());
            
            List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isEqualTo(1L); // GEOADD result
            
            // Verify geo data exists outside pipeline
            List<org.springframework.data.geo.Point> positions = connection.geoCommands().geoPos(geoKey.getBytes(), "Palermo".getBytes());
            assertThat(positions).hasSize(1);
            assertThat(positions.get(0)).isNotNull();
            
        } finally {
            cleanupKey(geoKey);
        }
    }

    // ==================== State Management Tests ====================

    @Test
    void testIsPipelinedState() {
        assertThat(connection.isPipelined()).isFalse();
        
        connection.openPipeline();
        assertThat(connection.isPipelined()).isTrue();
        
        connection.closePipeline();
        assertThat(connection.isPipelined()).isFalse();
    }

    @Test
    void testPipelineTransactionIsolation() {
        String key = "test:pipe:state:isolation:key";
        
        try {
            // Cannot start transaction while pipeline is active
            connection.openPipeline();
            
            assertThatThrownBy(() -> connection.multi())
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Cannot use transaction while a pipeline is open");
            
            connection.closePipeline();
            
            // Cannot start pipeline while transaction is active  
            connection.multi();
            
            assertThatThrownBy(() -> connection.openPipeline())
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Cannot use pipelining while a transaction is active");
            
            connection.discard();
            
        } finally {
            // Ensure clean state
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
            if (connection.isQueueing()) {
                connection.discard();
            }
            cleanupKey(key);
        }
    }

    @Test
    void testStateManagementAfterException() {
        String key = "test:pipe:state:key";
        
        try {
            connection.openPipeline();
            assertThat(connection.isPipelined()).isTrue();
            
            // Queue some commands including potentially problematic ones
            connection.stringCommands().set(key.getBytes(), "test_value".getBytes());
            
            // Pipeline state should remain active even if there are potential command issues
            assertThat(connection.isPipelined()).isTrue();
            
            // Should be able to close pipeline normally
            List<Object> results = connection.closePipeline();
            assertThat(connection.isPipelined()).isFalse();
            assertThat(results).hasSize(1);
            
            // Verify command was executed
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("test_value".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    void testClosePipelineWithoutOpen() {
        // closePipeline() without openPipeline() should return empty list safely
        assertThat(connection.isPipelined()).isFalse();
        
        List<Object> results = connection.closePipeline();
        
        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
        assertThat(connection.isPipelined()).isFalse();
    }

    @Test
    void testNestedOpenPipelineCalls() {
        String key = "test:pipe:error:nested";
        
        try {
            // First openPipeline() call
            connection.openPipeline();
            assertThat(connection.isPipelined()).isTrue();
            
            // Second openPipeline() call - should be idempotent
            connection.openPipeline();
            assertThat(connection.isPipelined()).isTrue();
            
            // Queue a command
            connection.stringCommands().set(key.getBytes(), "nested_value".getBytes());
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            assertThat(connection.isPipelined()).isFalse();
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo(true);
            
            // Verify command was executed
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("nested_value".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPipelineWithNonExistentKeys() {
        String nonExistentKey1 = "test:pipe:edge:nonexistent1";
        String nonExistentKey2 = "test:pipe:edge:nonexistent2";
        
        try {
            connection.openPipeline();
            
            // Operations on non-existent keys
            connection.stringCommands().get(nonExistentKey1.getBytes());
            connection.stringCommands().get(nonExistentKey2.getBytes());
            connection.listCommands().lLen("non:existent:list".getBytes());
            connection.setCommands().sCard("non:existent:set".getBytes());
            
            List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(4);
            // Results for non-existent keys should be null or 0 depending on command
            assertThat(results.get(0)).isNull(); // GET returns null
            assertThat(results.get(1)).isNull(); // GET returns null
            assertThat(results.get(2)).isEqualTo(0L); // LLEN returns 0
            assertThat(results.get(3)).isEqualTo(0L); // SCARD returns 0
            
        } finally {
            // No cleanup needed for non-existent keys
        }
    }

    // ==================== Large Pipeline Tests ====================

    @Test
    void testLargePipeline() {
        String keyPrefix = "test:pipe:large:key";
        int commandCount = 100;
        
        try {
            connection.openPipeline();
            
            // Queue many commands
            for (int i = 0; i < commandCount; i++) {
                String key = keyPrefix + ":" + i;
                String value = "value" + i;
                Object result = connection.stringCommands().set(key.getBytes(), value.getBytes());
                assertThat(result).isNull(); // Should be null in pipeline mode
            }
            
            List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(commandCount);
            
            // All SET operations should succeed
            for (int i = 0; i < commandCount; i++) {
                assertThat(results.get(i)).isEqualTo(true);
            }
            
            // Verify a few random values were actually set
            assertThat(connection.stringCommands().get((keyPrefix + ":0").getBytes())).isEqualTo("value0".getBytes());
            assertThat(connection.stringCommands().get((keyPrefix + ":50").getBytes())).isEqualTo("value50".getBytes());
            assertThat(connection.stringCommands().get((keyPrefix + ":99").getBytes())).isEqualTo("value99".getBytes());
            
        } finally {
            // Clean up all generated keys
            for (int i = 0; i < commandCount; i++) {
                cleanupKey(keyPrefix + ":" + i);
            }
        }
    }

    @Test
    void testPipelinePerformance() {
        String keyPrefix = "test:pipe:performance:key";
        int commandCount = 200;
        
        try {
            long startTime = System.currentTimeMillis();
            
            connection.openPipeline();
            
            // Mix of SET and GET commands
            for (int i = 0; i < commandCount; i++) {
                String key = keyPrefix + ":" + i;
                connection.stringCommands().set(key.getBytes(), ("value" + i).getBytes());
                connection.stringCommands().get(key.getBytes());
            }
            
            List<Object> results = connection.closePipeline();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            assertThat(results).hasSize(commandCount * 2);
            
            // Pipeline should complete within reasonable time
            assertThat(duration).isLessThan(10000); // Should complete within 10 seconds
            
            // Verify some results
            for (int i = 0; i < commandCount; i += 10) {
                assertThat(results.get(i * 2)).isEqualTo(true); // SET result
                assertThat(results.get(i * 2 + 1)).isEqualTo(("value" + i).getBytes()); // GET result
            }
            
        } finally {
            // Clean up performance test keys
            for (int i = 0; i < commandCount; i++) {
                cleanupKey(keyPrefix + ":" + i);
            }
        }
    }

    // ==================== Concurrent Pipeline Tests ====================

    @Test
    void testConcurrentPipelines() throws Exception {
        String sharedKey = "test:pipe:concurrent:shared";
        String keyPrefix = "test:pipe:concurrent:key";
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        try {
            // Set initial shared value
            connection.stringCommands().set(sharedKey.getBytes(), "shared_value".getBytes());
            
            CompletableFuture<?>[] futures = new CompletableFuture[5];
            
            for (int i = 0; i < 5; i++) {
                final int threadId = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    try (ValkeyConnection conn = connectionFactory.getConnection()) {
                        // Each thread runs its own pipeline
                        conn.openPipeline();
                        conn.stringCommands().set((keyPrefix + ":" + threadId).getBytes(), ("thread" + threadId).getBytes());
                        conn.stringCommands().get(sharedKey.getBytes()); // Read shared value
                        List<Object> results = conn.closePipeline();
                        
                        assertThat(results).hasSize(2);
                        assertThat(results.get(0)).isEqualTo(true); // SET result
                        assertThat(results.get(1)).isEqualTo("shared_value".getBytes()); // GET result
                    }
                }, executor);
            }
            
            CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
            
            // Verify all threads succeeded
            for (int i = 0; i < 5; i++) {
                assertThat(connection.stringCommands().get((keyPrefix + ":" + i).getBytes()))
                    .isEqualTo(("thread" + i).getBytes());
            }
            
        } finally {
            executor.shutdown();
            cleanupKey(sharedKey);
            for (int i = 0; i < 5; i++) {
                cleanupKey(keyPrefix + ":" + i);
            }
        }
    }

    // ==================== Result Conversion Tests ====================

    @Test
    void testPipelineResultConversion() {
        String key = "test:pipe:conversion:key";
        String counterKey = "test:pipe:conversion:counter";
        
        try {
            connection.openPipeline();
            
            // Commands that return different types
            connection.stringCommands().set(key.getBytes(), "42".getBytes()); // Boolean result
            connection.stringCommands().get(key.getBytes()); // byte[] result
            connection.stringCommands().set(counterKey.getBytes(), "10".getBytes()); // Boolean result
            connection.stringCommands().incr(counterKey.getBytes()); // Long result
            connection.stringCommands().strLen(counterKey.getBytes()); // Long result
            
            List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(5);
            
            // Verify result types and values are properly converted
            assertThat(results.get(0)).isEqualTo(true); // SET result
            assertThat(results.get(1)).isEqualTo("42".getBytes()); // GET result  
            assertThat(results.get(2)).isEqualTo(true); // SET result
            assertThat(results.get(3)).isEqualTo(11L); // INCR result
            assertThat(results.get(4)).isEqualTo(2L); // STRLEN result ("11" has length 2)
        } finally {
            cleanupKeys(key, counterKey);
        }
    }

    // ==================== Error Recovery Tests ====================

    @Test
    void testPipelineErrorRecovery() {
        String validKey = "test:pipe:error:valid";
        String errorKey = "test:pipe:error:exception";
        
        try {
            connection.openPipeline();
            
            // Queue valid command
            connection.stringCommands().set(validKey.getBytes(), "valid_value".getBytes());
            
            // Queue potentially problematic command (this behavior may depend on implementation)
            connection.stringCommands().get(errorKey.getBytes()); // Non-existent key returns null
            
            // Queue another valid command
            connection.stringCommands().get(validKey.getBytes());
            
            List<Object> results = connection.closePipeline();
            
            // Pipeline should complete even with null results
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo(true); // Valid SET
            assertThat(results.get(1)).isNull(); // GET non-existent key
            assertThat(results.get(2)).isEqualTo("valid_value".getBytes()); // Valid GET
            
        } finally {
            cleanupKeys(validKey, errorKey);
        }
    }

    // ==================== Edge Case Tests ====================

    @Test
    void testPipelineWithEmptyValues() {
        String key = "test:pipe:edge:empty";
        
        try {
            connection.openPipeline();
            
            // Set field with empty value
            connection.stringCommands().set(key.getBytes(), new byte[0]);
            connection.stringCommands().get(key.getBytes());
            connection.stringCommands().strLen(key.getBytes());
            
            List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo(true); // SET result
            assertThat(results.get(1)).isEqualTo(new byte[0]); // GET result (empty byte array)
            assertThat(results.get(2)).isEqualTo(0L); // STRLEN result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPipelineStateConsistency() {
        // Test that pipeline state is properly maintained across various operations
        assertThat(connection.isPipelined()).isFalse();
        
        // Multiple openPipeline/closePipeline cycles
        for (int i = 0; i < 3; i++) {
            connection.openPipeline();
            assertThat(connection.isPipelined()).isTrue();
            
            List<Object> results = connection.closePipeline();
            assertThat(connection.isPipelined()).isFalse();
            assertThat(results).isEmpty();
        }
        
        // Final state should be clean
        assertThat(connection.isPipelined()).isFalse();
        assertThat(connection.isQueueing()).isFalse();
    }
}
