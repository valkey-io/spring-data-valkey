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

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * HyperLogLog functionality using the ValkeyHyperLogLogCommands interface directly.
 * 
 * These tests validate the implementation of all ValkeyHyperLogLogCommands methods:
 * - pfAdd: Add elements to HyperLogLog
 * - pfCount: Get cardinality estimate of HyperLogLog(s)
 * - pfMerge: Merge multiple HyperLogLogs into one
 *
 * Tests cover all three invocation modes:
 * - Immediate mode: Direct execution with results
 * - Pipeline mode: Commands return null, results collected in closePipeline()
 * - Transaction mode: Commands return null, results collected in exec()
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionHyperLogLogCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:hll:basic", "test:hll:count:single", "test:hll:count:multiple1", 
            "test:hll:count:multiple2", "test:hll:merge:source1", "test:hll:merge:source2",
            "test:hll:merge:dest", "test:hll:empty", "test:hll:binary", "test:hll:duplicate",
            "test:hll:large", "test:hll:pipeline:basic", "test:hll:pipeline:count1",
            "test:hll:pipeline:count2", "test:hll:pipeline:merge:src1", "test:hll:pipeline:merge:src2",
            "test:hll:pipeline:merge:dst", "test:hll:transaction:basic", "test:hll:transaction:count1",
            "test:hll:transaction:count2", "test:hll:transaction:merge:src1", "test:hll:transaction:merge:src2",
            "test:hll:transaction:merge:dst", "test:hll:error:string", "non:existent:key"
        };
    }

    // ==================== Basic HyperLogLog Operations (Immediate Mode) ====================

    @Test
    void testPfAddBasic() {
        String key = "test:hll:basic";
        byte[] keyBytes = key.getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Test pfAdd with single value
            Long addResult1 = connection.hyperLogLogCommands().pfAdd(keyBytes, value1);
            assertThat(addResult1).isEqualTo(1L); // First element added
            
            // Test pfAdd with multiple values
            Long addResult2 = connection.hyperLogLogCommands().pfAdd(keyBytes, value2, value3);
            assertThat(addResult2).isEqualTo(1L); // HLL was modified
            
            // Test pfAdd with duplicate value
            Long addResult3 = connection.hyperLogLogCommands().pfAdd(keyBytes, value1);
            assertThat(addResult3).isEqualTo(0L); // HLL was not modified (duplicate)
            
            // Test pfAdd with mix of new and duplicate values
            Long addResult4 = connection.hyperLogLogCommands().pfAdd(keyBytes, value1, "value4".getBytes());
            assertThat(addResult4).isEqualTo(1L); // HLL was modified (new value added)
            
            // Verify cardinality is approximately correct (HLL is probabilistic)
            Long count = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(count).isGreaterThanOrEqualTo(3L).isLessThanOrEqualTo(5L); // Should be around 4
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPfCountSingle() {
        String key = "test:hll:count:single";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test pfCount on non-existent key
            Long emptyCount = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(emptyCount).isEqualTo(0L);
            
            // Add some elements
            connection.hyperLogLogCommands().pfAdd(keyBytes, "elem1".getBytes(), "elem2".getBytes(), "elem3".getBytes());
            
            // Test pfCount on existing HLL
            Long count = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(count).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // Should be around 3
            
            // Add more elements to test accuracy
            for (int i = 4; i <= 100; i++) {
                connection.hyperLogLogCommands().pfAdd(keyBytes, ("elem" + i).getBytes());
            }
            
            Long largeCount = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(largeCount).isGreaterThanOrEqualTo(90L).isLessThanOrEqualTo(110L); // Should be around 100
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPfCountMultiple() {
        String key1 = "test:hll:count:multiple1";
        String key2 = "test:hll:count:multiple2";
        byte[] key1Bytes = key1.getBytes();
        byte[] key2Bytes = key2.getBytes();
        
        try {
            // Test pfCount on non-existent keys
            Long emptyCount = connection.hyperLogLogCommands().pfCount(key1Bytes, key2Bytes);
            assertThat(emptyCount).isEqualTo(0L);
            
            // Add different elements to each HLL
            connection.hyperLogLogCommands().pfAdd(key1Bytes, "elem1".getBytes(), "elem2".getBytes(), "elem3".getBytes());
            connection.hyperLogLogCommands().pfAdd(key2Bytes, "elem3".getBytes(), "elem4".getBytes(), "elem5".getBytes());
            
            // Test individual counts
            Long count1 = connection.hyperLogLogCommands().pfCount(key1Bytes);
            Long count2 = connection.hyperLogLogCommands().pfCount(key2Bytes);
            assertThat(count1).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // Should be around 3
            assertThat(count2).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // Should be around 3
            
            // Test union count (elem3 is in both, so total unique should be around 5)
            Long unionCount = connection.hyperLogLogCommands().pfCount(key1Bytes, key2Bytes);
            assertThat(unionCount).isGreaterThanOrEqualTo(4L).isLessThanOrEqualTo(6L); // Should be around 5
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testPfMerge() {
        String source1 = "test:hll:merge:source1";
        String source2 = "test:hll:merge:source2";
        String dest = "test:hll:merge:dest";
        byte[] source1Bytes = source1.getBytes();
        byte[] source2Bytes = source2.getBytes();
        byte[] destBytes = dest.getBytes();
        
        try {
            // Add elements to source HLLs
            connection.hyperLogLogCommands().pfAdd(source1Bytes, "elem1".getBytes(), "elem2".getBytes(), "elem3".getBytes());
            connection.hyperLogLogCommands().pfAdd(source2Bytes, "elem3".getBytes(), "elem4".getBytes(), "elem5".getBytes());
            
            // Get individual counts before merge
            Long count1 = connection.hyperLogLogCommands().pfCount(source1Bytes);
            Long count2 = connection.hyperLogLogCommands().pfCount(source2Bytes);
            
            // Test pfMerge
            connection.hyperLogLogCommands().pfMerge(destBytes, source1Bytes, source2Bytes);
            
            // Verify merge result
            Long mergedCount = connection.hyperLogLogCommands().pfCount(destBytes);
            assertThat(mergedCount).isGreaterThanOrEqualTo(4L).isLessThanOrEqualTo(6L); // Should be around 5 (elem3 overlaps)
            
            // Verify original HLLs are unchanged
            Long count1After = connection.hyperLogLogCommands().pfCount(source1Bytes);
            Long count2After = connection.hyperLogLogCommands().pfCount(source2Bytes);
            assertThat(count1After).isEqualTo(count1);
            assertThat(count2After).isEqualTo(count2);
            
            // Test merging into existing HLL
            connection.hyperLogLogCommands().pfAdd(destBytes, "elem6".getBytes());
            connection.hyperLogLogCommands().pfMerge(destBytes, source1Bytes);
            
            Long finalCount = connection.hyperLogLogCommands().pfCount(destBytes);
            assertThat(finalCount).isGreaterThanOrEqualTo(5L).isLessThanOrEqualTo(7L); // Should be around 6
        } finally {
            cleanupKey(source1);
            cleanupKey(source2);
            cleanupKey(dest);
        }
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    void testHyperLogLogWithEmptyValues() {
        String key = "test:hll:empty";
        byte[] keyBytes = key.getBytes();
        byte[] emptyValue = new byte[0];
        
        try {
            // Add empty value
            Long addResult = connection.hyperLogLogCommands().pfAdd(keyBytes, emptyValue);
            assertThat(addResult).isEqualTo(1L);
            
            // Count should include empty value
            Long count = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(count).isEqualTo(1L);
            
            // Add empty value again (duplicate)
            Long addResult2 = connection.hyperLogLogCommands().pfAdd(keyBytes, emptyValue);
            assertThat(addResult2).isEqualTo(0L); // No change
            
            // Count should still be 1
            Long count2 = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(count2).isEqualTo(1L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHyperLogLogWithBinaryData() {
        String key = "test:hll:binary";
        byte[] keyBytes = key.getBytes();
        byte[] binaryValue1 = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF};
        byte[] binaryValue2 = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x01, 0x7F};
        byte[] binaryValue3 = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF}; // duplicate of value1
        
        try {
            // Add binary values
            Long addResult1 = connection.hyperLogLogCommands().pfAdd(keyBytes, binaryValue1, binaryValue2);
            assertThat(addResult1).isEqualTo(1L);
            
            // Add duplicate binary value
            Long addResult2 = connection.hyperLogLogCommands().pfAdd(keyBytes, binaryValue3);
            assertThat(addResult2).isEqualTo(0L); // Should be duplicate
            
            // Count should be around 2
            Long count = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(count).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHyperLogLogDuplicateHandling() {
        String key = "test:hll:duplicate";
        byte[] keyBytes = key.getBytes();
        byte[] value = "testvalue".getBytes();
        
        try {
            // Add same value multiple times
            Long addResult1 = connection.hyperLogLogCommands().pfAdd(keyBytes, value);
            assertThat(addResult1).isEqualTo(1L); // First time
            
            Long addResult2 = connection.hyperLogLogCommands().pfAdd(keyBytes, value);
            assertThat(addResult2).isEqualTo(0L); // Duplicate
            
            Long addResult3 = connection.hyperLogLogCommands().pfAdd(keyBytes, value);
            assertThat(addResult3).isEqualTo(0L); // Duplicate
            
            // Count should be 1
            Long count = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(count).isEqualTo(1L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHyperLogLogLargeDataSet() {
        String key = "test:hll:large";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Add many unique elements
            for (int i = 0; i < 10000; i++) {
                connection.hyperLogLogCommands().pfAdd(keyBytes, ("element" + i).getBytes());
            }
            
            // Count should be close to 10000 (within acceptable HLL error range)
            Long count = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(count).isGreaterThan(9500L).isLessThan(10500L); // ~5% error tolerance
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHyperLogLogOperationsOnNonHLLTypes() {
        String stringKey = "test:hll:error:string";
        
        try {
            // Create a string key
            connection.stringCommands().set(stringKey.getBytes(), "stringvalue".getBytes());
            
            // Try HLL operations on string key - should fail
            assertThatThrownBy(() -> connection.hyperLogLogCommands().pfAdd(stringKey.getBytes(), "value".getBytes()))
                .isInstanceOf(DataAccessException.class);
                
            assertThatThrownBy(() -> connection.hyperLogLogCommands().pfCount(stringKey.getBytes()))
                .isInstanceOf(DataAccessException.class);
        } finally {
            cleanupKey(stringKey);
        }
    }

    // ==================== Pipeline Mode Tests ====================

    @Test
    void testBasicHyperLogLogCommandsInPipelineMode() {
        String key = "test:hll:pipeline:basic";
        byte[] keyBytes = key.getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue HLL commands - assert they return null in pipeline mode
            assertThat(connection.hyperLogLogCommands().pfAdd(keyBytes, value1, value2)).isNull();
            assertThat(connection.hyperLogLogCommands().pfAdd(keyBytes, value3)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(keyBytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfAdd(keyBytes, value1)).isNull(); // duplicate
            assertThat(connection.hyperLogLogCommands().pfCount(keyBytes)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(5);
            assertThat(results.get(0)).isEqualTo(1L); // pfAdd with new values
            assertThat(results.get(1)).isEqualTo(1L); // pfAdd with new value
            assertThat((Long) results.get(2)).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // pfCount result
            assertThat(results.get(3)).isEqualTo(0L); // pfAdd with duplicate
            assertThat((Long) results.get(4)).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // pfCount result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPfCountMultipleInPipelineMode() {
        String key1 = "test:hll:pipeline:count1";
        String key2 = "test:hll:pipeline:count2";
        byte[] key1Bytes = key1.getBytes();
        byte[] key2Bytes = key2.getBytes();
        
        try {
            // Set up test data
            connection.hyperLogLogCommands().pfAdd(key1Bytes, "elem1".getBytes(), "elem2".getBytes());
            connection.hyperLogLogCommands().pfAdd(key2Bytes, "elem2".getBytes(), "elem3".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue count commands - assert they return null in pipeline mode
            assertThat(connection.hyperLogLogCommands().pfCount(key1Bytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(key2Bytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(key1Bytes, key2Bytes)).isNull(); // union count
            assertThat(connection.hyperLogLogCommands().pfCount("non:existent:key".getBytes())).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(4);
            assertThat((Long) results.get(0)).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L); // key1 count
            assertThat((Long) results.get(1)).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L); // key2 count
            assertThat((Long) results.get(2)).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // union count
            assertThat(results.get(3)).isEqualTo(0L); // non-existent key count
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testPfMergeInPipelineMode() {
        String source1 = "test:hll:pipeline:merge:src1";
        String source2 = "test:hll:pipeline:merge:src2";
        String dest = "test:hll:pipeline:merge:dst";
        byte[] source1Bytes = source1.getBytes();
        byte[] source2Bytes = source2.getBytes();
        byte[] destBytes = dest.getBytes();
        
        try {
            // Set up source HLLs
            connection.hyperLogLogCommands().pfAdd(source1Bytes, "elem1".getBytes(), "elem2".getBytes());
            connection.hyperLogLogCommands().pfAdd(source2Bytes, "elem2".getBytes(), "elem3".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue merge and verification commands
            connection.hyperLogLogCommands().pfMerge(destBytes, source1Bytes, source2Bytes); // void method, no assertion
            assertThat(connection.hyperLogLogCommands().pfCount(destBytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(source1Bytes)).isNull(); // verify unchanged
            assertThat(connection.hyperLogLogCommands().pfCount(source2Bytes)).isNull(); // verify unchanged
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(4);
            assertThat(results.get(0)).isEqualTo("OK"); // pfMerge result
            assertThat((Long) results.get(1)).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // merged count
            assertThat((Long) results.get(2)).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L); // source1 unchanged
            assertThat((Long) results.get(3)).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L); // source2 unchanged
            
        } finally {
            cleanupKey(source1);
            cleanupKey(source2);
            cleanupKey(dest);
        }
    }

    @Test
    void testEmptyHyperLogLogOperationsInPipelineMode() {
        String key = "test:hll:pipeline:empty";
        byte[] keyBytes = key.getBytes();
        byte[] emptyValue = new byte[0];
        
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue operations with empty values - assert they return null in pipeline mode
            assertThat(connection.hyperLogLogCommands().pfAdd(keyBytes, emptyValue)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(keyBytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfAdd(keyBytes, emptyValue)).isNull(); // duplicate
            assertThat(connection.hyperLogLogCommands().pfCount(keyBytes)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(4);
            assertThat(results.get(0)).isEqualTo(1L); // pfAdd with new empty value
            assertThat(results.get(1)).isEqualTo(1L); // pfCount result
            assertThat(results.get(2)).isEqualTo(0L); // pfAdd with duplicate empty value
            assertThat(results.get(3)).isEqualTo(1L); // pfCount result (unchanged)
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Transaction Mode Tests ====================

    @Test
    void testBasicHyperLogLogCommandsInTransactionMode() {
        String key = "test:hll:transaction:basic";
        byte[] keyBytes = key.getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue HLL commands - assert they return null in transaction mode
            assertThat(connection.hyperLogLogCommands().pfAdd(keyBytes, value1, value2)).isNull();
            assertThat(connection.hyperLogLogCommands().pfAdd(keyBytes, value3)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(keyBytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfAdd(keyBytes, value1)).isNull(); // duplicate
            assertThat(connection.hyperLogLogCommands().pfCount(keyBytes)).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(5);
            assertThat(results.get(0)).isEqualTo(1L); // pfAdd with new values
            assertThat(results.get(1)).isEqualTo(1L); // pfAdd with new value
            assertThat((Long) results.get(2)).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // pfCount result
            assertThat(results.get(3)).isEqualTo(0L); // pfAdd with duplicate
            assertThat((Long) results.get(4)).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // pfCount result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPfCountMultipleInTransactionMode() {
        String key1 = "test:hll:transaction:count1";
        String key2 = "test:hll:transaction:count2";
        byte[] key1Bytes = key1.getBytes();
        byte[] key2Bytes = key2.getBytes();
        
        try {
            // Set up test data
            connection.hyperLogLogCommands().pfAdd(key1Bytes, "elem1".getBytes(), "elem2".getBytes());
            connection.hyperLogLogCommands().pfAdd(key2Bytes, "elem2".getBytes(), "elem3".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Queue count commands - assert they return null in transaction mode
            assertThat(connection.hyperLogLogCommands().pfCount(key1Bytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(key2Bytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(key1Bytes, key2Bytes)).isNull(); // union count
            assertThat(connection.hyperLogLogCommands().pfCount("non:existent:key".getBytes())).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            assertThat((Long) results.get(0)).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L); // key1 count
            assertThat((Long) results.get(1)).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L); // key2 count
            assertThat((Long) results.get(2)).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // union count
            assertThat(results.get(3)).isEqualTo(0L); // non-existent key count
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testPfMergeInTransactionMode() {
        String source1 = "test:hll:transaction:merge:src1";
        String source2 = "test:hll:transaction:merge:src2";
        String dest = "test:hll:transaction:merge:dst";
        byte[] source1Bytes = source1.getBytes();
        byte[] source2Bytes = source2.getBytes();
        byte[] destBytes = dest.getBytes();
        
        try {
            // Set up source HLLs
            connection.hyperLogLogCommands().pfAdd(source1Bytes, "elem1".getBytes(), "elem2".getBytes());
            connection.hyperLogLogCommands().pfAdd(source2Bytes, "elem2".getBytes(), "elem3".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Queue merge and verification commands
            connection.hyperLogLogCommands().pfMerge(destBytes, source1Bytes, source2Bytes); // void method, no assertion
            assertThat(connection.hyperLogLogCommands().pfCount(destBytes)).isNull();
            assertThat(connection.hyperLogLogCommands().pfCount(source1Bytes)).isNull(); // verify unchanged
            assertThat(connection.hyperLogLogCommands().pfCount(source2Bytes)).isNull(); // verify unchanged
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            assertThat(results.get(0)).isEqualTo("OK"); // pfMerge result
            assertThat((Long) results.get(1)).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // merged count
            assertThat((Long) results.get(2)).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L); // source1 unchanged
            assertThat((Long) results.get(3)).isGreaterThanOrEqualTo(1L).isLessThanOrEqualTo(3L); // source2 unchanged
            
        } finally {
            cleanupKey(source1);
            cleanupKey(source2);
            cleanupKey(dest);
        }
    }

    @Test
    void testTransactionDiscardWithHyperLogLogCommands() {
        String key = "test:hll:transaction:discard";
        byte[] keyBytes = key.getBytes();
        byte[] value = "value".getBytes();
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue HLL commands
            connection.hyperLogLogCommands().pfAdd(keyBytes, value);
            connection.hyperLogLogCommands().pfCount(keyBytes);
            
            // Discard transaction
            connection.discard();
            
            // Verify key doesn't exist (transaction was discarded)
            Long count = connection.hyperLogLogCommands().pfCount(keyBytes);
            assertThat(count).isEqualTo(0L);
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testWatchWithHyperLogLogCommandsTransaction() throws InterruptedException {
        String watchKey = "test:hll:transaction:watch";
        String otherKey = "test:hll:transaction:other";
        byte[] watchKeyBytes = watchKey.getBytes();
        byte[] otherKeyBytes = otherKey.getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Setup initial data
            connection.hyperLogLogCommands().pfAdd(watchKeyBytes, value1);
            
            // Watch the key
            connection.watch(watchKeyBytes);
            
            // Modify the watched key from "outside" the transaction
            // (simulating another client)
            connection.hyperLogLogCommands().pfAdd(watchKeyBytes, value2);
            
            // Start transaction
            connection.multi();
            
            // Queue HLL commands
            connection.hyperLogLogCommands().pfAdd(otherKeyBytes, value1);
            connection.hyperLogLogCommands().pfCount(otherKeyBytes);
            
            // Execute transaction - should be aborted due to WATCH
            List<Object> results = connection.exec();
            
            // Transaction should be aborted (results should be null)
            assertThat(results).isNotNull().isEmpty();
            
            // Verify that the other key was not set
            Long count = connection.hyperLogLogCommands().pfCount(otherKeyBytes);
            assertThat(count).isEqualTo(0L);
            
        } finally {
            cleanupKey(watchKey);
            cleanupKey(otherKey);
        }
    }

    // ==================== Comprehensive HyperLogLog Workflow Tests ====================

    @Test
    void testHyperLogLogComprehensiveWorkflow() {
        String hll1 = "test:hll:workflow:hll1";
        String hll2 = "test:hll:workflow:hll2";
        String hll3 = "test:hll:workflow:hll3";
        String merged = "test:hll:workflow:merged";
        
        try {
            // Create multiple HLLs with overlapping and unique data
            connection.hyperLogLogCommands().pfAdd(hll1.getBytes(), "common1".getBytes(), "unique1a".getBytes(), "unique1b".getBytes());
            connection.hyperLogLogCommands().pfAdd(hll2.getBytes(), "common1".getBytes(), "common2".getBytes(), "unique2a".getBytes());
            connection.hyperLogLogCommands().pfAdd(hll3.getBytes(), "common2".getBytes(), "unique3a".getBytes(), "unique3b".getBytes());
            
            // Verify individual counts
            Long count1 = connection.hyperLogLogCommands().pfCount(hll1.getBytes());
            Long count2 = connection.hyperLogLogCommands().pfCount(hll2.getBytes());
            Long count3 = connection.hyperLogLogCommands().pfCount(hll3.getBytes());
            
            assertThat(count1).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // Should be around 3
            assertThat(count2).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // Should be around 3
            assertThat(count3).isGreaterThanOrEqualTo(2L).isLessThanOrEqualTo(4L); // Should be around 3
            
            // Test union counts
            Long union12 = connection.hyperLogLogCommands().pfCount(hll1.getBytes(), hll2.getBytes());
            Long union23 = connection.hyperLogLogCommands().pfCount(hll2.getBytes(), hll3.getBytes());
            Long unionAll = connection.hyperLogLogCommands().pfCount(hll1.getBytes(), hll2.getBytes(), hll3.getBytes());
            
            assertThat(union12).isGreaterThanOrEqualTo(4L).isLessThanOrEqualTo(6L); // Should be around 5
            assertThat(union23).isGreaterThanOrEqualTo(3L).isLessThanOrEqualTo(5L); // Should be around 4
            assertThat(unionAll).isGreaterThanOrEqualTo(6L).isLessThanOrEqualTo(8L); // Should be around 7
            
            // Merge all into one HLL
            connection.hyperLogLogCommands().pfMerge(merged.getBytes(), hll1.getBytes(), hll2.getBytes(), hll3.getBytes());
            
            // Verify merged count matches union count
            Long mergedCount = connection.hyperLogLogCommands().pfCount(merged.getBytes());
            assertThat(mergedCount).isEqualTo(unionAll);
            
            // Add more elements to merged HLL
            connection.hyperLogLogCommands().pfAdd(merged.getBytes(), "additional1".getBytes(), "additional2".getBytes());
            
            Long finalCount = connection.hyperLogLogCommands().pfCount(merged.getBytes());
            assertThat(finalCount).isGreaterThan(mergedCount);
            
        } finally {
            cleanupKey(hll1);
            cleanupKey(hll2);
            cleanupKey(hll3);
            cleanupKey(merged);
        }
    }

}
