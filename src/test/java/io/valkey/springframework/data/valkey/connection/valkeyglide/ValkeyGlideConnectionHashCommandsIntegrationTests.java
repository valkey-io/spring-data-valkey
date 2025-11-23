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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnCommand;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * hash functionality using the ValkeyHashCommands interface directly.
 * 
 * These tests validate the implementation of all ValkeyHashCommands methods:
 * - Basic hash operations (hSet, hSetNX, hGet, hMGet, hMSet)
 * - Increment/decrement operations (hIncrBy for long and double)
 * - Existence and deletion operations (hExists, hDel)
 * - Structural operations (hLen, hKeys, hVals, hGetAll)
 * - Random field operations (hRandField variants)
 * - Scanning operations (hScan)
 * - Field length operations (hStrLen)
 * - Field expiration operations (hExpire, hpExpire, hExpireAt, hpExpireAt, hPersist, hTtl, hpTtl)
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideConnectionHashCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:hash:basic", "test:hash:setnx", "test:hash:multi", "test:hash:incr",
            "test:hash:exists", "test:hash:structure", "test:hash:random", "test:hash:randomwithvals",
            "test:hash:scan", "test:hash:strlen", "test:hash:expire", "test:hash:expireat",
            "test:hash:persist", "test:hash:ttl", "test:hash:error:string", "test:hash:empty",
            "test:hash:binary", "non:existent:key"
        };
    }

    // ==================== Basic Hash Operations ====================

    @Test
    void testHSetAndHGet() {
        String key = "test:hash:basic";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Test hSet on new field
            Boolean setResult1 = connection.hashCommands().hSet(keyBytes, field1, value1);
            assertThat(setResult1).isTrue();
            
            // Test hGet
            byte[] retrievedValue1 = connection.hashCommands().hGet(keyBytes, field1);
            assertThat(retrievedValue1).isEqualTo(value1);
            
            // Test hSet on existing field (should update)
            Boolean setResult2 = connection.hashCommands().hSet(keyBytes, field1, value2);
            assertThat(setResult2).isFalse(); // Field already existed
            
            // Verify updated value
            byte[] retrievedValue2 = connection.hashCommands().hGet(keyBytes, field1);
            assertThat(retrievedValue2).isEqualTo(value2);
            
            // Test hGet on non-existent field
            byte[] nonExistentValue = connection.hashCommands().hGet(keyBytes, field2);
            assertThat(nonExistentValue).isNull();
            
            // Test hGet on non-existent key
            byte[] nonExistentKeyValue = connection.hashCommands().hGet("non:existent:key".getBytes(), field1);
            assertThat(nonExistentKeyValue).isNull();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHSetNX() {
        String key = "test:hash:setnx";
        byte[] keyBytes = key.getBytes();
        byte[] field = "field".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Test hSetNX on new field
            Boolean setNXResult1 = connection.hashCommands().hSetNX(keyBytes, field, value1);
            assertThat(setNXResult1).isTrue();
            
            // Verify value was set
            byte[] retrievedValue1 = connection.hashCommands().hGet(keyBytes, field);
            assertThat(retrievedValue1).isEqualTo(value1);
            
            // Test hSetNX on existing field - should fail
            Boolean setNXResult2 = connection.hashCommands().hSetNX(keyBytes, field, value2);
            assertThat(setNXResult2).isFalse();
            
            // Verify value was not changed
            byte[] retrievedValue2 = connection.hashCommands().hGet(keyBytes, field);
            assertThat(retrievedValue2).isEqualTo(value1);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHMGetAndHMSet() {
        String key = "test:hash:multi";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] field4 = "nonexistent".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Set up test data using hMSet
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Test hMGet with existing and non-existing fields
            List<byte[]> values = connection.hashCommands().hMGet(keyBytes, field1, field2, field3, field4);
            
            assertThat(values).hasSize(4);
            assertThat(values.get(0)).isEqualTo(value1);
            assertThat(values.get(1)).isEqualTo(value2);
            assertThat(values.get(2)).isEqualTo(value3);
            assertThat(values.get(3)).isNull(); // non-existent field
            
            // Test hMGet on non-existent key
            List<byte[]> nonExistentValues = connection.hashCommands().hMGet("non:existent:key".getBytes(), field1);
            assertThat(nonExistentValues).hasSize(1);
            assertThat(nonExistentValues.get(0)).isNull();
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Increment Operations ====================

    @Test
    void testHIncrBy() {
        String key = "test:hash:incr";
        byte[] keyBytes = key.getBytes();
        byte[] intField = "intField".getBytes();
        byte[] floatField = "floatField".getBytes();
        
        try {
            // Test hIncrBy with long on non-existent field (should start from 0)
            Long incrResult1 = connection.hashCommands().hIncrBy(keyBytes, intField, 5L);
            assertThat(incrResult1).isEqualTo(5L);
            
            // Test subsequent increment
            Long incrResult2 = connection.hashCommands().hIncrBy(keyBytes, intField, 10L);
            assertThat(incrResult2).isEqualTo(15L);
            
            // Test negative increment (decrement)
            Long incrResult3 = connection.hashCommands().hIncrBy(keyBytes, intField, -7L);
            assertThat(incrResult3).isEqualTo(8L);
            
            // Test hIncrBy with double on non-existent field
            Double floatIncrResult1 = connection.hashCommands().hIncrBy(keyBytes, floatField, 3.14);
            assertThat(floatIncrResult1).isEqualTo(3.14);
            
            // Test subsequent float increment
            Double floatIncrResult2 = connection.hashCommands().hIncrBy(keyBytes, floatField, 2.86);
            assertThat(floatIncrResult2).isEqualTo(6.0);
            
            // Test negative float increment
            Double floatIncrResult3 = connection.hashCommands().hIncrBy(keyBytes, floatField, -1.5);
            assertThat(floatIncrResult3).isEqualTo(4.5);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Existence and Deletion Operations ====================

    @Test
    void testHExistsAndHDel() {
        String key = "test:hash:exists";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            connection.hashCommands().hSet(keyBytes, field3, value3);
            
            // Test hExists on existing fields
            Boolean exists1 = connection.hashCommands().hExists(keyBytes, field1);
            assertThat(exists1).isTrue();
            
            Boolean exists2 = connection.hashCommands().hExists(keyBytes, field2);
            assertThat(exists2).isTrue();
            
            // Test hExists on non-existent field
            Boolean existsNon = connection.hashCommands().hExists(keyBytes, "nonexistent".getBytes());
            assertThat(existsNon).isFalse();
            
            // Test hExists on non-existent key
            Boolean existsNonKey = connection.hashCommands().hExists("non:existent:key".getBytes(), field1);
            assertThat(existsNonKey).isFalse();
            
            // Test hDel with single field
            Long delResult1 = connection.hashCommands().hDel(keyBytes, field1);
            assertThat(delResult1).isEqualTo(1L);
            
            // Verify field was deleted
            Boolean existsAfterDel = connection.hashCommands().hExists(keyBytes, field1);
            assertThat(existsAfterDel).isFalse();
            
            // Test hDel with multiple fields
            Long delResult2 = connection.hashCommands().hDel(keyBytes, field2, field3);
            assertThat(delResult2).isEqualTo(2L);
            
            // Test hDel on non-existent field
            Long delResult3 = connection.hashCommands().hDel(keyBytes, "nonexistent".getBytes());
            assertThat(delResult3).isEqualTo(0L);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Structural Operations ====================

    @Test
    void testHLenHKeysHValsHGetAll() {
        String key = "test:hash:structure";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Test on empty/non-existent hash
            Long emptyLen = connection.hashCommands().hLen(keyBytes);
            assertThat(emptyLen).isEqualTo(0L);
            
            Set<byte[]> emptyKeys = connection.hashCommands().hKeys(keyBytes);
            assertThat(emptyKeys).isEmpty();
            
            List<byte[]> emptyVals = connection.hashCommands().hVals(keyBytes);
            assertThat(emptyVals).isEmpty();
            
            Map<byte[], byte[]> emptyAll = connection.hashCommands().hGetAll(keyBytes);
            assertThat(emptyAll).isEmpty();
            
            // Set up test data
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Test hLen
            Long len = connection.hashCommands().hLen(keyBytes);
            assertThat(len).isEqualTo(3L);
            
            // Test hKeys
            Set<byte[]> keys = connection.hashCommands().hKeys(keyBytes);
            assertThat(keys).hasSize(3);
            assertThat(keys).containsExactlyInAnyOrder(field1, field2, field3);
            
            // Test hVals
            List<byte[]> vals = connection.hashCommands().hVals(keyBytes);
            assertThat(vals).hasSize(3);
            assertThat(vals).containsExactlyInAnyOrder(value1, value2, value3);
            
            // Test hGetAll
            Map<byte[], byte[]> all = connection.hashCommands().hGetAll(keyBytes);
            assertThat(all).hasSize(3);

            // Since byte arrays are compared by reference, not content, we need to check the content differently
            boolean foundField1 = false, foundField2 = false, foundField3 = false;
            for (Map.Entry<byte[], byte[]> entry : all.entrySet()) {
                if (java.util.Arrays.equals(entry.getKey(), field1)) {
                    assertThat(entry.getValue()).isEqualTo(value1);
                    foundField1 = true;
                } else if (java.util.Arrays.equals(entry.getKey(), field2)) {
                    assertThat(entry.getValue()).isEqualTo(value2);
                    foundField2 = true;
                } else if (java.util.Arrays.equals(entry.getKey(), field3)) {
                    assertThat(entry.getValue()).isEqualTo(value3);
                    foundField3 = true;
                }
            }
            assertThat(foundField1).as("field1 should be present in hGetAll result").isTrue();
            assertThat(foundField2).as("field2 should be present in hGetAll result").isTrue();
            assertThat(foundField3).as("field3 should be present in hGetAll result").isTrue();
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Random Field Operations ====================

    @Test
    void testHRandField() {
        String key = "test:hash:random";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Test on non-existent key
            byte[] nonExistentField = connection.hashCommands().hRandField("non:existent:key".getBytes());
            assertThat(nonExistentField).isNull();
            
            // Set up test data
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Test hRandField (single field)
            byte[] randomField = connection.hashCommands().hRandField(keyBytes);
            assertThat(randomField).isIn(field1, field2, field3);
            
            // Test hRandField with count
            List<byte[]> randomFields = connection.hashCommands().hRandField(keyBytes, 2);
            assertThat(randomFields).hasSize(2);
            
            // Test hRandField with count larger than hash size
            List<byte[]> allFields = connection.hashCommands().hRandField(keyBytes, 5);
            assertThat(allFields).hasSize(3); // Should return all available fields
            
            // Test hRandField with negative count (allows repetitions)
            List<byte[]> repeatedFields = connection.hashCommands().hRandField(keyBytes, -5);
            assertThat(repeatedFields).hasSize(5); // Should return exactly 5 elements (may repeat)
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHRandFieldWithValues() {
        String key = "test:hash:randomwithvals";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Test on non-existent key
            Map.Entry<byte[], byte[]> nonExistentEntry = connection.hashCommands().hRandFieldWithValues("non:existent:key".getBytes());
            assertThat(nonExistentEntry).isNull();
            
            // Set up test data
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Test hRandFieldWithValues (single entry)
            Map.Entry<byte[], byte[]> randomEntry = connection.hashCommands().hRandFieldWithValues(keyBytes);
            assertThat(randomEntry).isNotNull();
            assertThat(randomEntry.getKey()).isIn(field1, field2, field3);
            // Verify the value matches the key
            if (java.util.Arrays.equals(randomEntry.getKey(), field1)) {
                assertThat(randomEntry.getValue()).isEqualTo(value1);
            } else if (java.util.Arrays.equals(randomEntry.getKey(), field2)) {
                assertThat(randomEntry.getValue()).isEqualTo(value2);
            } else if (java.util.Arrays.equals(randomEntry.getKey(), field3)) {
                assertThat(randomEntry.getValue()).isEqualTo(value3);
            }
            
            // Test hRandFieldWithValues with count
            List<Map.Entry<byte[], byte[]>> randomEntries = connection.hashCommands().hRandFieldWithValues(keyBytes, 2);
            assertThat(randomEntries).hasSize(2);
            
            for (Map.Entry<byte[], byte[]> entry : randomEntries) {
                assertThat(entry.getKey()).isIn(field1, field2, field3);
                // Verify each value matches its key
                if (java.util.Arrays.equals(entry.getKey(), field1)) {
                    assertThat(entry.getValue()).isEqualTo(value1);
                } else if (java.util.Arrays.equals(entry.getKey(), field2)) {
                    assertThat(entry.getValue()).isEqualTo(value2);
                } else if (java.util.Arrays.equals(entry.getKey(), field3)) {
                    assertThat(entry.getValue()).isEqualTo(value3);
                }
            }
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Scanning Operations ====================

    @Test
    void testHScan() {
        String key = "test:hash:scan";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Set up test data with a pattern
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put("field1".getBytes(), "value1".getBytes());
            hashData.put("field2".getBytes(), "value2".getBytes());
            hashData.put("other1".getBytes(), "othervalue1".getBytes());
            hashData.put("other2".getBytes(), "othervalue2".getBytes());
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Test basic scan
            Cursor<Map.Entry<byte[], byte[]>> cursor = connection.hashCommands().hScan(keyBytes, ScanOptions.NONE);
            
            List<Map.Entry<byte[], byte[]>> scannedEntries = new ArrayList<>();
            while (cursor.hasNext()) {
                scannedEntries.add(cursor.next());
            }
            cursor.close();
            
            assertThat(scannedEntries).hasSize(4);
            
            // Test scan with pattern
            ScanOptions patternOptions = ScanOptions.scanOptions().match("field*").build();
            Cursor<Map.Entry<byte[], byte[]>> patternCursor = connection.hashCommands().hScan(keyBytes, patternOptions);
            
            List<Map.Entry<byte[], byte[]>> patternEntries = new ArrayList<>();
            while (patternCursor.hasNext()) {
                patternEntries.add(patternCursor.next());
            }
            patternCursor.close();
            
            // Should find only entries matching "field*" pattern
            assertThat(patternEntries).hasSize(2);
            for (Map.Entry<byte[], byte[]> entry : patternEntries) {
                String fieldName = new String(entry.getKey());
                assertThat(fieldName).startsWith("field");
            }
            
            // Test scan with count
            ScanOptions countOptions = ScanOptions.scanOptions().count(1).build();
            Cursor<Map.Entry<byte[], byte[]>> countCursor = connection.hashCommands().hScan(keyBytes, countOptions);
            
            List<Map.Entry<byte[], byte[]>> countEntries = new ArrayList<>();
            while (countCursor.hasNext()) {
                countEntries.add(countCursor.next());
            }
            countCursor.close();
            
            assertThat(countEntries).hasSize(4); // Should still get all entries, just in smaller batches
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Field Length Operations ====================

    @Test
    void testHStrLen() {
        String key = "test:hash:strlen";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] shortValue = "short".getBytes(); // 5 characters
        byte[] longValue = "this is a longer value".getBytes(); // 22 characters
        
        try {
            // Test on non-existent key/field
            Long nonExistentLen = connection.hashCommands().hStrLen("non:existent:key".getBytes(), field1);
            assertThat(nonExistentLen).isEqualTo(0L);
            
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, shortValue);
            connection.hashCommands().hSet(keyBytes, field2, longValue);
            
            // Test hStrLen
            Long shortLen = connection.hashCommands().hStrLen(keyBytes, field1);
            assertThat(shortLen).isEqualTo(5L);
            
            Long longLen = connection.hashCommands().hStrLen(keyBytes, field2);
            assertThat(longLen).isEqualTo(22L);
            
            // Test on non-existent field in existing key
            Long nonExistentFieldLen = connection.hashCommands().hStrLen(keyBytes, "nonexistent".getBytes());
            assertThat(nonExistentFieldLen).isEqualTo(0L);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Field Expiration Operations ====================

    @Test
    @EnabledOnCommand("HEXPIRE")
    void testHExpire() {
        String key = "test:hash:expire";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            // Test hExpire (seconds) - Basic happy path test to ensure command executes
            List<Long> expireResults = connection.hashCommands().hExpire(keyBytes, 10L, 
                ExpirationOptions.Condition.ALWAYS, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(1);

            // Test hExpire (seconds) - Test non existing key
            expireResults = connection.hashCommands().hExpire("non:existent:key".getBytes(), 10L, 
                ExpirationOptions.Condition.ALWAYS, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(-2);

            // Test hExpire (seconds) - 2 existing and 1 absent fields
            expireResults = connection.hashCommands().hExpire(keyBytes, 10L, 
                ExpirationOptions.Condition.ALWAYS, field1, field2, "nonexistent".getBytes());
            assertThat(expireResults).hasSize(3);
            assertThat(expireResults.get(0)).isEqualTo(1);
            assertThat(expireResults.get(1)).isEqualTo(1);
            assertThat(expireResults.get(2)).isEqualTo(-2);

            // Test hExpire (seconds) - Test NX
            expireResults = connection.hashCommands().hExpire(keyBytes, 10L, 
                ExpirationOptions.Condition.NX, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);

            // Test hExpire (seconds) - Test XX
            expireResults = connection.hashCommands().hExpire(keyBytes, 10L, 
                ExpirationOptions.Condition.XX, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(1);

            // Test hExpire (seconds) - Test GT by setting a shorter expiry first
            expireResults = connection.hashCommands().hExpire(keyBytes, 1L, 
                ExpirationOptions.Condition.GT, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);

            // Test hExpire (seconds) - Test LT by setting a longer expiry
            expireResults = connection.hashCommands().hExpire(keyBytes, 11L, 
                ExpirationOptions.Condition.LT, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnCommand("HPEXPIRE")
    void testHpExpire() {
        String key = "test:hash:expire";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            // Test hExpire (seconds) - Basic happy path test to ensure command executes
            List<Long> expireResults = connection.hashCommands().hpExpire(keyBytes, 10000L, 
                ExpirationOptions.Condition.ALWAYS, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(1);

            // Test hExpire (seconds) - Test non existing key
            expireResults = connection.hashCommands().hpExpire("non:existent:key".getBytes(), 10000L, 
                ExpirationOptions.Condition.ALWAYS, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(-2);

            // Test hExpire (seconds) - 2 existing and 1 absent fields
            expireResults = connection.hashCommands().hpExpire(keyBytes, 10000L, 
                ExpirationOptions.Condition.ALWAYS, field1, field2, "nonexistent".getBytes());
            assertThat(expireResults).hasSize(3);
            assertThat(expireResults.get(0)).isEqualTo(1);
            assertThat(expireResults.get(1)).isEqualTo(1);
            assertThat(expireResults.get(2)).isEqualTo(-2);

            // Test hExpire (seconds) - Test NX
            expireResults = connection.hashCommands().hpExpire(keyBytes, 10000L, 
                ExpirationOptions.Condition.NX, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);

            // Test hExpire (seconds) - Test XX
            expireResults = connection.hashCommands().hpExpire(keyBytes, 10000L, 
                ExpirationOptions.Condition.XX, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(1);

            // Test hExpire (seconds) - Test GT by setting a shorter expiry first
            expireResults = connection.hashCommands().hpExpire(keyBytes, 1000L, 
                ExpirationOptions.Condition.GT, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);

            // Test hExpire (seconds) - Test LT by setting a longer expiry
            expireResults = connection.hashCommands().hpExpire(keyBytes, 11000L, 
                ExpirationOptions.Condition.LT, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnCommand("HEXPIREAT")
    void testHExpireAt() {
        String key = "test:hash:expireat";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            long currentTimestamp = System.currentTimeMillis() / 1000;
            long expirationTimestamp = currentTimestamp + 10; // 10 seconds in the future
            long shorterTimestamp = currentTimestamp + 5; // 5 seconds in the future (shorter than 10)
            long nextExpirationTimestampMillis = currentTimestamp + 20; // 20 seconds in the future

            // Test hExpireAt (seconds) - Basic happy path test to ensure command executes
            List<Long> expireResults = connection.hashCommands().hExpireAt(keyBytes, expirationTimestamp, 
                ExpirationOptions.Condition.ALWAYS, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(1);

            // Test hExpire (seconds) - Test non existing key
            expireResults = connection.hashCommands().hExpireAt("non:existent:key".getBytes(), expirationTimestamp, 
                ExpirationOptions.Condition.ALWAYS, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(-2);

            // Test hExpire (seconds) - 2 existing and 1 absent fields
            expireResults = connection.hashCommands().hExpireAt(keyBytes, expirationTimestamp, 
                ExpirationOptions.Condition.ALWAYS, field1, field2, "nonexistent".getBytes());
            assertThat(expireResults).hasSize(3);
            assertThat(expireResults.get(0)).isEqualTo(1);
            assertThat(expireResults.get(1)).isEqualTo(1);
            assertThat(expireResults.get(2)).isEqualTo(-2);

            // Test hExpire (seconds) - Test NX
            expireResults = connection.hashCommands().hExpireAt(keyBytes, expirationTimestamp, 
                ExpirationOptions.Condition.NX, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);

            // Test hExpire (seconds) - Test XX
            expireResults = connection.hashCommands().hExpireAt(keyBytes, expirationTimestamp, 
                ExpirationOptions.Condition.XX, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(1);

            // Test hExpire (seconds) - Test GT by setting a shorter expiry first
            expireResults = connection.hashCommands().hExpireAt(keyBytes, shorterTimestamp,
                ExpirationOptions.Condition.GT, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);

            // Test hExpire (seconds) - Test LT by setting a longer expiry
            expireResults = connection.hashCommands().hExpireAt(keyBytes, nextExpirationTimestampMillis, 
                ExpirationOptions.Condition.LT, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnCommand("HPEXPIREAT")
    void testHpExpireAt() {
        String key = "test:hash:expireat";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            long currentTimestamp = System.currentTimeMillis();
            long expirationTimestamp = currentTimestamp + 10000; // 10 seconds in the future
            long shorterTimestamp = currentTimestamp + 5000; // 5 seconds in the future (shorter than 10)
            long nextExpirationTimestampMillis = currentTimestamp + 20000; // 20 seconds in the future

            // Test hExpireAt (seconds) - Basic happy path test to ensure command executes
            List<Long> expireResults = connection.hashCommands().hpExpireAt(keyBytes, expirationTimestamp, 
                ExpirationOptions.Condition.ALWAYS, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(1);

            // Test hExpire (seconds) - Test non existing key
            expireResults = connection.hashCommands().hpExpireAt("non:existent:key".getBytes(), expirationTimestamp, 
                ExpirationOptions.Condition.ALWAYS, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(-2);

            // Test hExpire (seconds) - 2 existing and 1 absent fields
            expireResults = connection.hashCommands().hpExpireAt(keyBytes, expirationTimestamp, 
                ExpirationOptions.Condition.ALWAYS, field1, field2, "nonexistent".getBytes());
            assertThat(expireResults).hasSize(3);
            assertThat(expireResults.get(0)).isEqualTo(1);
            assertThat(expireResults.get(1)).isEqualTo(1);
            assertThat(expireResults.get(2)).isEqualTo(-2);

            // Test hExpire (seconds) - Test NX
            expireResults = connection.hashCommands().hpExpireAt(keyBytes, expirationTimestamp, 
                ExpirationOptions.Condition.NX, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);

            // Test hExpire (seconds) - Test XX
            expireResults = connection.hashCommands().hpExpireAt(keyBytes, expirationTimestamp, 
                ExpirationOptions.Condition.XX, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(1);

            // Test hExpire (seconds) - Test GT by setting a shorter expiry first
            expireResults = connection.hashCommands().hpExpireAt(keyBytes, shorterTimestamp,
                ExpirationOptions.Condition.GT, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);

            // Test hExpire (seconds) - Test LT by setting a longer expiry
            expireResults = connection.hashCommands().hpExpireAt(keyBytes, nextExpirationTimestampMillis, 
                ExpirationOptions.Condition.LT, field1);
            assertThat(expireResults).hasSize(1);
            assertThat(expireResults.get(0)).isEqualTo(0);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnCommand("HPERSIST")
    void testHPersist() {
        String key = "test:hash:persist";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            // Test hPersist - Basic test to ensure command executes
            List<Long> persistResults = connection.hashCommands().hPersist(keyBytes, field1, field2);
            assertThat(persistResults).hasSize(2);
            assertThat(persistResults.get(0)).isEqualTo(-1); // field1 had no expiration
            assertThat(persistResults.get(1)).isEqualTo(-1); // field2 had no expiration

            // Set expiration to test persistence removal (combined)
            connection.hashCommands().hExpire(keyBytes, 10L, ExpirationOptions.Condition.ALWAYS, field1);
            persistResults = connection.hashCommands().hPersist(keyBytes, field1, field2, "nonexistent".getBytes());
            assertThat(persistResults).hasSize(3);
            assertThat(persistResults.get(0)).isEqualTo(1); // field1 expiration removed
            assertThat(persistResults.get(1)).isEqualTo(-1); // field2 had no expiration
            assertThat(persistResults.get(2)).isEqualTo(-2); // nonexistent field
            
            // Test on non-existent field
            List<Long> nonExistentResults = connection.hashCommands().hPersist(keyBytes, "nonexistent".getBytes());
            assertThat(nonExistentResults).hasSize(1);
            assertThat(nonExistentResults.get(0)).isEqualTo(-2);

            // Test on non-existent key
            List<Long> nonExistentKeyResults = connection.hashCommands().hPersist("non:existent:key".getBytes(), field1);
            assertThat(nonExistentKeyResults).hasSize(1);
            assertThat(nonExistentKeyResults.get(0)).isEqualTo(-2);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnCommand("HTTL")
    void testHTtl() {
        String key = "test:hash:ttl";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            // Test hTtl - Basic test to ensure command executes
            List<Long> ttlResults = connection.hashCommands().hTtl(keyBytes, field1, field2);
            assertThat(ttlResults).hasSize(2);
            assertThat(ttlResults.get(0)).isEqualTo(-1); // No expiration
            assertThat(ttlResults.get(1)).isEqualTo(-1); // No expiration

            // Set expiration to test TTL retrieval (combined)
            connection.hashCommands().hExpire(keyBytes, 10L, ExpirationOptions.Condition.ALWAYS, field1);
            ttlResults = connection.hashCommands().hTtl(keyBytes, field1, field2, "nonexistent".getBytes());
            assertThat(ttlResults).hasSize(3);
            assertThat(ttlResults.get(0)).isGreaterThan(0); // field1 should have a TTL
            assertThat(ttlResults.get(1)).isEqualTo(-1); // field2 has no expiration
            assertThat(ttlResults.get(2)).isEqualTo(-2); // nonexistent field

            // Test hTtl with TimeUnit
            connection.hashCommands().hExpire(keyBytes, 10L, ExpirationOptions.Condition.ALWAYS, field2);
            List<Long> ttlMillisResults = connection.hashCommands().hTtl(keyBytes, TimeUnit.MILLISECONDS, field1, field2);
            assertThat(ttlMillisResults).hasSize(2);
            assertThat(ttlMillisResults.get(0)).isGreaterThan(5000); // field1 should definitely have >5s left
            assertThat(ttlMillisResults.get(1)).isGreaterThan(5000); // field2 should definitely have >5s left

            // Test non existant key
            ttlResults = connection.hashCommands().hTtl("non:existent:key".getBytes(), field1);
            assertThat(ttlResults).hasSize(1);
            assertThat(ttlResults.get(0)).isEqualTo(-2);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnCommand("HPTTL")
    void testHpTtl() {
        String key = "test:hash:ttl";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Set up test data
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            // Test hpTtl - Basic test to ensure command executes
            List<Long> ttlResults = connection.hashCommands().hpTtl(keyBytes, field1, field2);
            assertThat(ttlResults).hasSize(2);
            assertThat(ttlResults.get(0)).isEqualTo(-1); // No expiration
            assertThat(ttlResults.get(1)).isEqualTo(-1); // No expiration

            // Set expiration to test TTL retrieval (combined)
            connection.hashCommands().hExpire(keyBytes, 10L, ExpirationOptions.Condition.ALWAYS, field1);
            ttlResults = connection.hashCommands().hpTtl(keyBytes, field1, field2, "nonexistent".getBytes());
            assertThat(ttlResults).hasSize(3);
            assertThat(ttlResults.get(0)).isGreaterThan(1000); // field1 should have a TTL
            assertThat(ttlResults.get(1)).isEqualTo(-1); // field2 has no expiration
            assertThat(ttlResults.get(2)).isEqualTo(-2); // nonexistent field

            // Test non existant key
            ttlResults = connection.hashCommands().hpTtl("non:existent:key".getBytes(), field1);
            assertThat(ttlResults).hasSize(1);
            assertThat(ttlResults.get(0)).isEqualTo(-2);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Error Handling and Edge Cases ====================

    @Test
    void testHashOperationsOnNonHashTypes() {
        String stringKey = "test:hash:error:string";
        
        try {
            // Create a string key
            connection.stringCommands().set(stringKey.getBytes(), "stringvalue".getBytes());
            
            // Try hash operations on string key - should fail or return appropriate response
            assertThatThrownBy(() -> connection.hashCommands().hSet(stringKey.getBytes(), "field".getBytes(), "value".getBytes()))
                .isInstanceOf(Exception.class);
        } finally {
            cleanupKey(stringKey);
        }
    }

    @Test
    void testEmptyHashOperations() {
        String key = "test:hash:empty";
        byte[] keyBytes = key.getBytes();
        byte[] field = "field".getBytes();
        byte[] emptyValue = new byte[0];
        
        try {
            // Set field with empty value
            Boolean setResult = connection.hashCommands().hSet(keyBytes, field, emptyValue);
            assertThat(setResult).isTrue();
            
            // Get empty value
            byte[] retrievedValue = connection.hashCommands().hGet(keyBytes, field);
            assertThat(retrievedValue).isEqualTo(emptyValue);
            
            // String length should be 0
            Long strLen = connection.hashCommands().hStrLen(keyBytes, field);
            assertThat(strLen).isEqualTo(0L);
            
            // Hash should have 1 field
            Long hashLen = connection.hashCommands().hLen(keyBytes);
            assertThat(hashLen).isEqualTo(1L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHashOperationsWithBinaryData() {
        String key = "test:hash:binary";
        byte[] keyBytes = key.getBytes();
        byte[] binaryField = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF};
        byte[] binaryValue = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x01, 0x7F};
        
        try {
            // Test with binary field and value
            Boolean setResult = connection.hashCommands().hSet(keyBytes, binaryField, binaryValue);
            assertThat(setResult).isTrue();
            
            // Retrieve binary value
            byte[] retrievedValue = connection.hashCommands().hGet(keyBytes, binaryField);
            assertThat(retrievedValue).isEqualTo(binaryValue);
            
            // Test exists with binary field
            Boolean exists = connection.hashCommands().hExists(keyBytes, binaryField);
            assertThat(exists).isTrue();
            
        // Test hGetAll with binary data
        Map<byte[], byte[]> all = connection.hashCommands().hGetAll(keyBytes);
        assertThat(all).hasSize(1);
        
        // Since byte arrays are compared by reference, not content, we need to check the content differently
        boolean foundBinaryField = false;
        for (Map.Entry<byte[], byte[]> entry : all.entrySet()) {
            if (java.util.Arrays.equals(entry.getKey(), binaryField)) {
                assertThat(entry.getValue()).isEqualTo(binaryValue);
                foundBinaryField = true;
                break;
            }
        }
        assertThat(foundBinaryField).as("binaryField should be present in hGetAll result").isTrue();
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Pipeline Mode Tests ====================

    @Test
    void testHashCommandsInPipelineMode() {
        String key1 = "test:hash:pipeline:basic";
        String key2 = "test:hash:pipeline:multi";
        byte[] key1Bytes = key1.getBytes();
        byte[] key2Bytes = key2.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Start pipeline for main test logic
            connection.openPipeline();
            
            // Queue multiple hash commands - assert they return null in pipeline mode
            assertThat(connection.hashCommands().hSet(key1Bytes, field1, value1)).isNull();
            assertThat(connection.hashCommands().hSet(key1Bytes, field2, value2)).isNull();
            assertThat(connection.hashCommands().hGet(key1Bytes, field1)).isNull();
            assertThat(connection.hashCommands().hExists(key1Bytes, field1)).isNull();
            assertThat(connection.hashCommands().hLen(key1Bytes)).isNull();
            
            // Queue multi-operations - assert they return null in pipeline mode
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(key2Bytes, hashData); // void method, no assertion needed
            assertThat(connection.hashCommands().hMGet(key2Bytes, field1, field2, field3)).isNull();
            assertThat(connection.hashCommands().hGetAll(key2Bytes)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(8);
            assertThat(results.get(0)).isEqualTo(true);  // hSet result
            assertThat(results.get(1)).isEqualTo(true);  // hSet result  
            assertThat(results.get(2)).isEqualTo(value1); // hGet result
            assertThat(results.get(3)).isEqualTo(true);  // hExists result
            assertThat(results.get(4)).isEqualTo(2L);    // hLen result
            
            // hMSet returns simple string reply "OK"
            assertThat(results.get(5)).isEqualTo("OK");
            
            // hMGet results
            @SuppressWarnings("unchecked")
            List<byte[]> mgetResults = (List<byte[]>) results.get(6);
            assertThat(mgetResults).hasSize(3);
            assertThat(mgetResults.get(0)).isEqualTo(value1);
            assertThat(mgetResults.get(1)).isEqualTo(value2);
            assertThat(mgetResults.get(2)).isEqualTo(value3);
            
            // hGetAll results
            @SuppressWarnings("unchecked")
            Map<byte[], byte[]> getAllResults = (Map<byte[], byte[]>) results.get(7);
            assertThat(getAllResults).hasSize(3);
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testHashIncrementCommandsInPipelineMode() {
        String key = "test:hash:pipeline:incr";
        byte[] keyBytes = key.getBytes();
        byte[] intField = "intField".getBytes();
        byte[] floatField = "floatField".getBytes();
        
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue increment commands - assert they return null in pipeline mode
            assertThat(connection.hashCommands().hIncrBy(keyBytes, intField, 5L)).isNull();
            assertThat(connection.hashCommands().hIncrBy(keyBytes, intField, 10L)).isNull();
            assertThat(connection.hashCommands().hIncrBy(keyBytes, floatField, 3.14)).isNull();
            assertThat(connection.hashCommands().hIncrBy(keyBytes, floatField, 2.86)).isNull();
            assertThat(connection.hashCommands().hGet(keyBytes, intField)).isNull();
            assertThat(connection.hashCommands().hGet(keyBytes, floatField)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(5L);    // First increment
            assertThat(results.get(1)).isEqualTo(15L);   // Second increment
            assertThat(results.get(2)).isEqualTo(3.14);  // First float increment
            assertThat(results.get(3)).isEqualTo(6.0);   // Second float increment
            assertThat(results.get(4)).isEqualTo("15".getBytes()); // Get int result
            assertThat(results.get(5)).isEqualTo("6".getBytes());   // Get float result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHashStructuralCommandsInPipelineMode() {
        String key = "test:hash:pipeline:structure";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Setup data first
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue structural commands - assert they return null in pipeline mode
            assertThat(connection.hashCommands().hLen(keyBytes)).isNull();
            assertThat(connection.hashCommands().hKeys(keyBytes)).isNull();
            assertThat(connection.hashCommands().hVals(keyBytes)).isNull();
            assertThat(connection.hashCommands().hGetAll(keyBytes)).isNull();
            assertThat(connection.hashCommands().hDel(keyBytes, field1)).isNull();
            assertThat(connection.hashCommands().hLen(keyBytes)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(3L); // Initial length
            
            @SuppressWarnings("unchecked")
            Set<byte[]> keys = (Set<byte[]>) results.get(1);
            assertThat(keys).hasSize(3);
            
            @SuppressWarnings("unchecked")
            List<byte[]> vals = (List<byte[]>) results.get(2);
            assertThat(vals).hasSize(3);
            
            @SuppressWarnings("unchecked")
            Map<byte[], byte[]> all = (Map<byte[], byte[]>) results.get(3);
            assertThat(all).hasSize(3);
            
            assertThat(results.get(4)).isEqualTo(1L); // Delete result
            assertThat(results.get(5)).isEqualTo(2L); // Final length
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHashRandomFieldCommandsInPipelineMode() {
        String key = "test:hash:pipeline:random";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Setup data first
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue random field commands - assert they return null in pipeline mode
            assertThat(connection.hashCommands().hRandField(keyBytes)).isNull();
            assertThat(connection.hashCommands().hRandField(keyBytes, 2)).isNull();
            assertThat(connection.hashCommands().hRandFieldWithValues(keyBytes)).isNull();
            assertThat(connection.hashCommands().hRandFieldWithValues(keyBytes, 2)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(4);
            
            // Single random field
            assertThat(results.get(0)).isInstanceOf(byte[].class);
            
            // Multiple random fields
            @SuppressWarnings("unchecked")
            List<byte[]> multipleFields = (List<byte[]>) results.get(1);
            assertThat(multipleFields).hasSize(2);
            
            // Single random field with value
            @SuppressWarnings("unchecked")
            Map.Entry<byte[], byte[]> singleEntry = (Map.Entry<byte[], byte[]>) results.get(2);
            assertThat(singleEntry).isNotNull();
            
            // Multiple random fields with values
            @SuppressWarnings("unchecked")
            List<Map.Entry<byte[], byte[]>> multipleEntries = (List<Map.Entry<byte[], byte[]>>) results.get(3);
            assertThat(multipleEntries).hasSize(2);
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnCommand("HEXPIRE")
    void testHashExpirationCommandsInPipelineMode() {
        String key = "test:hash:pipeline:expire";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Setup data first
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue expiration commands - assert they return null in pipeline mode
            assertThat(connection.hashCommands().hExpire(keyBytes, 10L, ExpirationOptions.Condition.ALWAYS, field1)).isNull();
            assertThat(connection.hashCommands().hpExpire(keyBytes, 10000L, ExpirationOptions.Condition.ALWAYS, field2)).isNull();
            assertThat(connection.hashCommands().hTtl(keyBytes, field1, field2)).isNull();
            assertThat(connection.hashCommands().hpTtl(keyBytes, field1, field2)).isNull();
            assertThat(connection.hashCommands().hPersist(keyBytes, field1, field2)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(5);
            
            @SuppressWarnings("unchecked")
            List<Long> expireResults1 = (List<Long>) results.get(0);
            assertThat(expireResults1).hasSize(1);
            assertThat(expireResults1.get(0)).isEqualTo(1L);
            
            @SuppressWarnings("unchecked")
            List<Long> expireResults2 = (List<Long>) results.get(1);
            assertThat(expireResults2).hasSize(1);
            assertThat(expireResults2.get(0)).isEqualTo(1L);
            
            @SuppressWarnings("unchecked")
            List<Long> ttlResults = (List<Long>) results.get(2);
            assertThat(ttlResults).hasSize(2);
            assertThat(ttlResults.get(0)).isGreaterThan(0L);
            assertThat(ttlResults.get(1)).isGreaterThan(0L);
            
            @SuppressWarnings("unchecked")
            List<Long> pttlResults = (List<Long>) results.get(3);
            assertThat(pttlResults).hasSize(2);
            assertThat(pttlResults.get(0)).isGreaterThan(1000L);
            assertThat(pttlResults.get(1)).isGreaterThan(1000L);
            
            @SuppressWarnings("unchecked")
            List<Long> persistResults = (List<Long>) results.get(4);
            assertThat(persistResults).hasSize(2);
            assertThat(persistResults.get(0)).isEqualTo(1L);
            assertThat(persistResults.get(1)).isEqualTo(1L);
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Transaction Mode Tests ====================

    @Test
    void testHashCommandsInTransactionMode() {
        String key1 = "test:hash:transaction:basic";
        String key2 = "test:hash:transaction:multi";
        byte[] key1Bytes = key1.getBytes();
        byte[] key2Bytes = key2.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Start transaction for main test logic
            connection.multi();
            
            // Queue multiple hash commands - assert they return null in transaction mode
            assertThat(connection.hashCommands().hSet(key1Bytes, field1, value1)).isNull();
            assertThat(connection.hashCommands().hSet(key1Bytes, field2, value2)).isNull();
            assertThat(connection.hashCommands().hGet(key1Bytes, field1)).isNull();
            assertThat(connection.hashCommands().hExists(key1Bytes, field1)).isNull();
            assertThat(connection.hashCommands().hLen(key1Bytes)).isNull();
            
            // Queue multi-operations - assert they return null in transaction mode
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(key2Bytes, hashData); // void method, no assertion needed
            assertThat(connection.hashCommands().hMGet(key2Bytes, field1, field2, field3)).isNull();
            assertThat(connection.hashCommands().hGetAll(key2Bytes)).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(8);
            assertThat(results.get(0)).isEqualTo(true);  // hSet result
            assertThat(results.get(1)).isEqualTo(true);  // hSet result  
            assertThat(results.get(2)).isEqualTo(value1); // hGet result
            assertThat(results.get(3)).isEqualTo(true);  // hExists result
            assertThat(results.get(4)).isEqualTo(2L);    // hLen result
            
            // hMSet returns simple string reply "OK"
            assertThat(results.get(5)).isEqualTo("OK");
            
            // hMGet results
            @SuppressWarnings("unchecked")
            List<byte[]> mgetResults = (List<byte[]>) results.get(6);
            assertThat(mgetResults).hasSize(3);
            assertThat(mgetResults.get(0)).isEqualTo(value1);
            assertThat(mgetResults.get(1)).isEqualTo(value2);
            assertThat(mgetResults.get(2)).isEqualTo(value3);
            
            // hGetAll results
            @SuppressWarnings("unchecked")
            Map<byte[], byte[]> getAllResults = (Map<byte[], byte[]>) results.get(7);
            assertThat(getAllResults).hasSize(3);
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testHashIncrementCommandsInTransactionMode() {
        String key = "test:hash:transaction:incr";
        byte[] keyBytes = key.getBytes();
        byte[] intField = "intField".getBytes();
        byte[] floatField = "floatField".getBytes();
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue increment commands - assert they return null in transaction mode
            assertThat(connection.hashCommands().hIncrBy(keyBytes, intField, 5L)).isNull();
            assertThat(connection.hashCommands().hIncrBy(keyBytes, intField, 10L)).isNull();
            assertThat(connection.hashCommands().hIncrBy(keyBytes, floatField, 3.14)).isNull();
            assertThat(connection.hashCommands().hIncrBy(keyBytes, floatField, 2.86)).isNull();
            assertThat(connection.hashCommands().hGet(keyBytes, intField)).isNull();
            assertThat(connection.hashCommands().hGet(keyBytes, floatField)).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(5L);    // First increment
            assertThat(results.get(1)).isEqualTo(15L);   // Second increment
            assertThat(results.get(2)).isEqualTo(3.14);  // First float increment
            assertThat(results.get(3)).isEqualTo(6.0);   // Second float increment
            assertThat(results.get(4)).isEqualTo("15".getBytes()); // Get int result
            assertThat(results.get(5)).isEqualTo("6".getBytes());   // Get float result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHashStructuralCommandsInTransactionMode() {
        String key = "test:hash:transaction:structure";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Setup data first
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Start transaction
            connection.multi();
            
            // Queue structural commands - assert they return null in transaction mode
            assertThat(connection.hashCommands().hLen(keyBytes)).isNull();
            assertThat(connection.hashCommands().hKeys(keyBytes)).isNull();
            assertThat(connection.hashCommands().hVals(keyBytes)).isNull();
            assertThat(connection.hashCommands().hGetAll(keyBytes)).isNull();
            assertThat(connection.hashCommands().hDel(keyBytes, field1)).isNull();
            assertThat(connection.hashCommands().hLen(keyBytes)).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(3L); // Initial length
            
            @SuppressWarnings("unchecked")
            Set<byte[]> keys = (Set<byte[]>) results.get(1);
            assertThat(keys).hasSize(3);
            
            @SuppressWarnings("unchecked")
            List<byte[]> vals = (List<byte[]>) results.get(2);
            assertThat(vals).hasSize(3);
            
            @SuppressWarnings("unchecked")
            Map<byte[], byte[]> all = (Map<byte[], byte[]>) results.get(3);
            assertThat(all).hasSize(3);
            
            assertThat(results.get(4)).isEqualTo(1L); // Delete result
            assertThat(results.get(5)).isEqualTo(2L); // Final length
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testHashRandomFieldCommandsInTransactionMode() {
        String key = "test:hash:transaction:random";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] field3 = "field3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();
        
        try {
            // Setup data first
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put(field1, value1);
            hashData.put(field2, value2);
            hashData.put(field3, value3);
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Start transaction
            connection.multi();
            
            // Queue random field commands - assert they return null in transaction mode
            assertThat(connection.hashCommands().hRandField(keyBytes)).isNull();
            assertThat(connection.hashCommands().hRandField(keyBytes, 2)).isNull();
            assertThat(connection.hashCommands().hRandFieldWithValues(keyBytes)).isNull();
            assertThat(connection.hashCommands().hRandFieldWithValues(keyBytes, 2)).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            
            // Single random field
            assertThat(results.get(0)).isInstanceOf(byte[].class);
            
            // Multiple random fields
            @SuppressWarnings("unchecked")
            List<byte[]> multipleFields = (List<byte[]>) results.get(1);
            assertThat(multipleFields).hasSize(2);
            
            // Single random field with value
            @SuppressWarnings("unchecked")
            Map.Entry<byte[], byte[]> singleEntry = (Map.Entry<byte[], byte[]>) results.get(2);
            assertThat(singleEntry).isNotNull();
            
            // Multiple random fields with values
            @SuppressWarnings("unchecked")
            List<Map.Entry<byte[], byte[]>> multipleEntries = (List<Map.Entry<byte[], byte[]>>) results.get(3);
            assertThat(multipleEntries).hasSize(2);
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnCommand("HEXPIRE")
    void testHashExpirationCommandsInTransactionMode() {
        String key = "test:hash:transaction:expire";
        byte[] keyBytes = key.getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Setup data first
            connection.hashCommands().hSet(keyBytes, field1, value1);
            connection.hashCommands().hSet(keyBytes, field2, value2);
            
            // Start transaction
            connection.multi();
            
            // Queue expiration commands - assert they return null in transaction mode
            assertThat(connection.hashCommands().hExpire(keyBytes, 10L, ExpirationOptions.Condition.ALWAYS, field1)).isNull();
            assertThat(connection.hashCommands().hpExpire(keyBytes, 10000L, ExpirationOptions.Condition.ALWAYS, field2)).isNull();
            assertThat(connection.hashCommands().hTtl(keyBytes, field1, field2)).isNull();
            assertThat(connection.hashCommands().hpTtl(keyBytes, field1, field2)).isNull();
            assertThat(connection.hashCommands().hPersist(keyBytes, field1, field2)).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(5);
            
            @SuppressWarnings("unchecked")
            List<Long> expireResults1 = (List<Long>) results.get(0);
            assertThat(expireResults1).hasSize(1);
            assertThat(expireResults1.get(0)).isEqualTo(1L);
            
            @SuppressWarnings("unchecked")
            List<Long> expireResults2 = (List<Long>) results.get(1);
            assertThat(expireResults2).hasSize(1);
            assertThat(expireResults2.get(0)).isEqualTo(1L);
            
            @SuppressWarnings("unchecked")
            List<Long> ttlResults = (List<Long>) results.get(2);
            assertThat(ttlResults).hasSize(2);
            assertThat(ttlResults.get(0)).isGreaterThan(0L);
            assertThat(ttlResults.get(1)).isGreaterThan(0L);
            
            @SuppressWarnings("unchecked")
            List<Long> pttlResults = (List<Long>) results.get(3);
            assertThat(pttlResults).hasSize(2);
            assertThat(pttlResults.get(0)).isGreaterThan(1000L);
            assertThat(pttlResults.get(1)).isGreaterThan(1000L);
            
            @SuppressWarnings("unchecked")
            List<Long> persistResults = (List<Long>) results.get(4);
            assertThat(persistResults).hasSize(2);
            assertThat(persistResults.get(0)).isEqualTo(1L);
            assertThat(persistResults.get(1)).isEqualTo(1L);
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testTransactionDiscardWithHashCommands() {
        String key = "test:hash:transaction:discard";
        byte[] keyBytes = key.getBytes();
        byte[] field = "field".getBytes();
        byte[] value = "value".getBytes();
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue hash commands
            connection.hashCommands().hSet(keyBytes, field, value);
            connection.hashCommands().hGet(keyBytes, field);
            
            // Discard transaction
            connection.discard();
            
            // Verify key doesn't exist (transaction was discarded)
            Boolean exists = connection.hashCommands().hExists(keyBytes, field);
            assertThat(exists).isFalse();
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testWatchWithHashCommandsTransaction() throws InterruptedException {
        String watchKey = "test:hash:transaction:watch";
        String otherKey = "test:hash:transaction:other";
        byte[] watchKeyBytes = watchKey.getBytes();
        byte[] otherKeyBytes = otherKey.getBytes();
        byte[] field = "field".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Setup initial data
            connection.hashCommands().hSet(watchKeyBytes, field, value1);
            
            // Watch the key
            connection.watch(watchKeyBytes);
            
            // Modify the watched key from "outside" the transaction
            // (simulating another client)
            connection.hashCommands().hSet(watchKeyBytes, field, value2);
            
            // Start transaction
            connection.multi();
            
            // Queue hash commands
            connection.hashCommands().hSet(otherKeyBytes, field, value1);
            connection.hashCommands().hGet(otherKeyBytes, field);
            
            // Execute transaction - should be aborted due to WATCH
            List<Object> results = connection.exec();
            
            // Transaction should be aborted (results should be null)
            assertThat(results).isNotNull().isEmpty();
            
            // Verify that the other key was not set
            Boolean exists = connection.hashCommands().hExists(otherKeyBytes, field);
            assertThat(exists).isFalse();
            
        } finally {
            cleanupKey(watchKey);
            cleanupKey(otherKey);
        }
    }

    // ==================== Error Handling Tests for Pipeline/Transaction ====================

    @Test
    void testPipelineErrorHandling() {
        String validKey = "test:hash:pipeline:error:valid";
        String stringKey = "test:hash:pipeline:error:string";
        byte[] validKeyBytes = validKey.getBytes();
        byte[] stringKeyBytes = stringKey.getBytes();
        byte[] field = "field".getBytes();
        byte[] value = "value".getBytes();
        
        try {
            // Set up a string key to cause type errors
            connection.stringCommands().set(stringKeyBytes, "stringvalue".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue valid command
            connection.hashCommands().hSet(validKeyBytes, field, value);
            
            // Queue command that will cause error (wrong type)
            connection.hashCommands().hSet(stringKeyBytes, field, value);
            
            // Queue another valid command
            connection.hashCommands().hGet(validKeyBytes, field);
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results - valid commands should succeed, error command should return exception
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo(true); // Valid hSet
            assertThat(results.get(1)).isInstanceOf(DataAccessException.class); // Error result
            assertThat(results.get(2)).isEqualTo(value); // Valid hGet
            
        } finally {
            cleanupKey(validKey);
            cleanupKey(stringKey);
        }
    }

    @Test
    void testTransactionErrorHandling() {
        String validKey = "test:hash:transaction:error:valid";
        String stringKey = "test:hash:transaction:error:string";
        byte[] validKeyBytes = validKey.getBytes();
        byte[] stringKeyBytes = stringKey.getBytes();
        byte[] field = "field".getBytes();
        byte[] value = "value".getBytes();
        
        try {
            // Set up a string key to cause type errors
            connection.stringCommands().set(stringKeyBytes, "stringvalue".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Queue valid command
            connection.hashCommands().hSet(validKeyBytes, field, value);
            
            // Queue command that will cause error (wrong type)
            connection.hashCommands().hSet(stringKeyBytes, field, value);
            
            // Queue another valid command
            connection.hashCommands().hGet(validKeyBytes, field);
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results - valid commands should succeed, error command should return exception
            assertThat(results).isNotNull(); // Transaction should not be aborted
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo(true); // Valid hSet
            assertThat(results.get(1)).isInstanceOf(DataAccessException.class); // Error result
            assertThat(results.get(2)).isEqualTo(value); // Valid hGet
            
        } finally {
            cleanupKey(validKey);
            cleanupKey(stringKey);
        }
    }

    @Test
    void testHScanNotAllowedInPipelineTransactionModes() {
        String key = "test:hash:scan:error";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Set up some data
            Map<byte[], byte[]> hashData = new HashMap<>();
            hashData.put("field1".getBytes(), "value1".getBytes());
            connection.hashCommands().hMSet(keyBytes, hashData);
            
            // Test HSCAN not allowed in pipeline mode
            connection.openPipeline();
            assertThatThrownBy(() -> connection.hashCommands().hScan(keyBytes, ScanOptions.NONE))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("pipeline");
            connection.closePipeline();
            
            // Test HSCAN not allowed in transaction mode
            connection.multi();
            assertThatThrownBy(() -> connection.hashCommands().hScan(keyBytes, ScanOptions.NONE))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("transaction");
            connection.discard();
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test 
    void testMixedCommandTypesInPipeline() {
        String hashKey = "test:hash:pipeline:mixed:hash";
        String stringKey = "test:hash:pipeline:mixed:string";
        byte[] hashKeyBytes = hashKey.getBytes();
        byte[] stringKeyBytes = stringKey.getBytes();
        byte[] field = "field".getBytes();
        byte[] hashValue = "hashvalue".getBytes();
        byte[] stringValue = "stringvalue".getBytes();
        
        try {
            // Start pipeline with mixed commands
            connection.openPipeline();
            
            // Mix hash and string commands
            connection.hashCommands().hSet(hashKeyBytes, field, hashValue);
            connection.stringCommands().set(stringKeyBytes, stringValue);
            connection.hashCommands().hGet(hashKeyBytes, field);
            connection.stringCommands().get(stringKeyBytes);
            connection.hashCommands().hExists(hashKeyBytes, field);
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify mixed results
            assertThat(results).hasSize(5);
            assertThat(results.get(0)).isEqualTo(true); // hSet result
            assertThat(results.get(1)).isEqualTo(true); // string set result (converted to boolean)
            assertThat(results.get(2)).isEqualTo(hashValue); // hGet result
            assertThat(results.get(3)).isEqualTo(stringValue); // string get result  
            assertThat(results.get(4)).isEqualTo(true); // hExists result
            
        } finally {
            cleanupKey(hashKey);
            cleanupKey(stringKey);
        }
    }

}
