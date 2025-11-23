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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.DefaultSortParameters;
import io.valkey.springframework.data.valkey.connection.SortParameters;
import io.valkey.springframework.data.valkey.connection.ValueEncoding;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyVersion;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * key functionality using the ValkeyKeyCommands interface directly.
 * 
 * These tests validate the implementation of all ValkeyKeyCommands methods in all 3 invocation modes:
 * - Immediate mode: Direct command execution with results
 * - Pipeline mode: Commands return null, results collected in closePipeline() 
 * - Transaction mode: Commands return null, results collected in exec()
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideConnectionKeyCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:key:exists:key1", "test:key:exists:key2", "test:key:exists:key3",
            "test:key:copy:source", "test:key:copy:target", "test:key:copy:existing",
            "test:key:type:string", "test:key:type:list", "test:key:type:hash", 
            "test:key:touch:key1", "test:key:touch:key2", "test:key:touch:key3",
            "test:key:keys:abc", "test:key:keys:def", "test:key:keys:xyz", "test:other:pattern",
            "test:key:random:key1", "test:key:random:key2",
            "test:key:scan:item1", "test:key:scan:item2", "test:key:scan:other1",
            "test:key:rename:old", "test:key:rename:new",
            "test:key:renamenx:old", "test:key:renamenx:new", "test:key:renamenx:existing",
            "test:key:expire", "test:key:pexpire", "test:key:expireat", "test:key:pexpireat",
            "test:key:persist", "test:key:ttl", "test:key:pttl",
            "test:key:sort:list", "test:key:sort:store", "test:key:dump", "test:key:restore",
            "test:key:move", "test:key:unlink:key1", "test:key:unlink:key2",
            "test:key:encoding", "test:key:idletime", "test:key:refcount",
            "test:key:pipeline:key1", "test:key:pipeline:key2", "test:key:pipeline:key3",
            "test:key:transaction:key1", "test:key:transaction:key2", "test:key:transaction:key3",
            "non:existent:key", "new:key"
        };
    }

    // ==================== Basic Key Operations ====================

    @Test
    void testExistsAndDel() {
        String key1 = "test:key:exists:key1";
        String key2 = "test:key:exists:key2";
        String key3 = "test:key:exists:key3";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test exists on non-existent keys
            Boolean exists1 = connection.keyCommands().exists(key1.getBytes());
            assertThat(exists1).isFalse();
            
            Long existsMultiple1 = connection.keyCommands().exists(key1.getBytes(), key2.getBytes(), key3.getBytes());
            assertThat(existsMultiple1).isEqualTo(0L);
            
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            
            // Test exists on existing keys
            Boolean exists2 = connection.keyCommands().exists(key1.getBytes());
            assertThat(exists2).isTrue();
            
            Long existsMultiple2 = connection.keyCommands().exists(key1.getBytes(), key2.getBytes(), key3.getBytes());
            assertThat(existsMultiple2).isEqualTo(2L); // 2 keys exist
            
            // Test with duplicate keys
            Long existsDuplicate = connection.keyCommands().exists(key1.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(existsDuplicate).isEqualTo(3L); // Counts duplicates
            
            // Test del single key
            Long delResult1 = connection.keyCommands().del(key1.getBytes());
            assertThat(delResult1).isEqualTo(1L);
            
            // Verify key was deleted
            Boolean existsAfterDel = connection.keyCommands().exists(key1.getBytes());
            assertThat(existsAfterDel).isFalse();
            
            // Test del multiple keys
            connection.stringCommands().set(key3.getBytes(), value); // Add key3
            Long delResult2 = connection.keyCommands().del(key2.getBytes(), key3.getBytes());
            assertThat(delResult2).isEqualTo(2L);
            
            // Test del non-existent key
            Long delResult3 = connection.keyCommands().del("non:existent:key".getBytes());
            assertThat(delResult3).isEqualTo(0L);
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testUnlink() {
        String key1 = "test:key:unlink:key1";
        String key2 = "test:key:unlink:key2";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            
            // Test unlink single key
            Long unlinkResult1 = connection.keyCommands().unlink(key1.getBytes());
            assertThat(unlinkResult1).isEqualTo(1L);
            
            // Verify key was unlinked
            Boolean existsAfterUnlink = connection.keyCommands().exists(key1.getBytes());
            assertThat(existsAfterUnlink).isFalse();
            
            // Test unlink multiple keys
            Long unlinkResult2 = connection.keyCommands().unlink(key2.getBytes(), "non:existent:key".getBytes());
            assertThat(unlinkResult2).isEqualTo(1L); // Only one key existed
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testCopy() {
        String sourceKey = "test:key:copy:source";
        String targetKey = "test:key:copy:target";
        String existingTargetKey = "test:key:copy:existing";
        byte[] value = "test_value".getBytes();
        byte[] existingValue = "existing_value".getBytes();
        
        try {
            // Test copy on non-existent source key
            Boolean copyNonExistent = connection.keyCommands().copy(sourceKey.getBytes(), targetKey.getBytes(), false);
            assertThat(copyNonExistent).isFalse();
            
            // Set up test data
            connection.stringCommands().set(sourceKey.getBytes(), value);
            connection.stringCommands().set(existingTargetKey.getBytes(), existingValue);
            
            // Test successful copy
            Boolean copyResult1 = connection.keyCommands().copy(sourceKey.getBytes(), targetKey.getBytes(), false);
            assertThat(copyResult1).isTrue();
            
            // Verify copy was successful
            byte[] copiedValue = connection.stringCommands().get(targetKey.getBytes());
            assertThat(copiedValue).isEqualTo(value);
            
            // Verify source still exists
            byte[] sourceValue = connection.stringCommands().get(sourceKey.getBytes());
            assertThat(sourceValue).isEqualTo(value);
            
            // Test copy without replace (should fail)
            Boolean copyResult2 = connection.keyCommands().copy(sourceKey.getBytes(), existingTargetKey.getBytes(), false);
            assertThat(copyResult2).isFalse();
            
            // Verify existing target was not overwritten
            byte[] existingTargetValue = connection.stringCommands().get(existingTargetKey.getBytes());
            assertThat(existingTargetValue).isEqualTo(existingValue);
            
            // Test copy with replace (should succeed)
            Boolean copyResult3 = connection.keyCommands().copy(sourceKey.getBytes(), existingTargetKey.getBytes(), true);
            assertThat(copyResult3).isTrue();
            
            // Verify existing target was overwritten
            byte[] replacedValue = connection.stringCommands().get(existingTargetKey.getBytes());
            assertThat(replacedValue).isEqualTo(value);
        } finally {
            cleanupKey(sourceKey);
            cleanupKey(targetKey);
            cleanupKey(existingTargetKey);
        }
    }

    @Test
    void testType() {
        String stringKey = "test:key:type:string";
        String listKey = "test:key:type:list";
        String hashKey = "test:key:type:hash";
        
        try {
            // Test type on non-existent key
            DataType nonExistentType = connection.keyCommands().type("non:existent:key".getBytes());
            assertThat(nonExistentType).isEqualTo(DataType.NONE);
            
            // Set up different data types
            connection.stringCommands().set(stringKey.getBytes(), "value".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "item".getBytes());
            connection.hashCommands().hSet(hashKey.getBytes(), "field".getBytes(), "value".getBytes());
            
            // Test different types
            DataType stringType = connection.keyCommands().type(stringKey.getBytes());
            assertThat(stringType).isEqualTo(DataType.STRING);
            
            DataType listType = connection.keyCommands().type(listKey.getBytes());
            assertThat(listType).isEqualTo(DataType.LIST);
            
            DataType hashType = connection.keyCommands().type(hashKey.getBytes());
            assertThat(hashType).isEqualTo(DataType.HASH);
        } finally {
            cleanupKey(stringKey);
            cleanupKey(listKey);
            cleanupKey(hashKey);
        }
    }

    @Test
    void testTouch() {
        String key1 = "test:key:touch:key1";
        String key2 = "test:key:touch:key2";
        String key3 = "test:key:touch:key3";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test touch on non-existent keys
            Long touchResult1 = connection.keyCommands().touch(key1.getBytes(), key2.getBytes());
            assertThat(touchResult1).isEqualTo(0L);
            
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            
            // Test touch on existing keys
            Long touchResult2 = connection.keyCommands().touch(key1.getBytes(), key2.getBytes(), key3.getBytes());
            assertThat(touchResult2).isEqualTo(2L); // Only 2 keys exist
            
            // Test single key touch
            Long touchResult3 = connection.keyCommands().touch(key1.getBytes());
            assertThat(touchResult3).isEqualTo(1L);
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testKeys() {
        String basePattern = "test:key:keys:";
        String key1 = basePattern + "abc";
        String key2 = basePattern + "def";
        String key3 = basePattern + "xyz";
        String otherKey = "test:other:pattern";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            connection.stringCommands().set(key3.getBytes(), value);
            connection.stringCommands().set(otherKey.getBytes(), value);
            
            // Test pattern matching
            Set<byte[]> matchedKeys = connection.keyCommands().keys((basePattern + "*").getBytes());
            assertThat(matchedKeys).hasSize(3);
            
            // Convert to strings for easier comparison
            Set<String> matchedKeyStrings = matchedKeys.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            
            assertThat(matchedKeyStrings).containsExactlyInAnyOrder(key1, key2, key3);
            
            // Test specific pattern
            Set<byte[]> specificMatch = connection.keyCommands().keys((basePattern + "a*").getBytes());
            assertThat(specificMatch).hasSize(1);
            assertThat(new String(specificMatch.iterator().next())).isEqualTo(key1);
            
            // Test non-matching pattern
            Set<byte[]> noMatch = connection.keyCommands().keys("no:match:*".getBytes());
            assertThat(noMatch).isEmpty();
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(otherKey);
        }
    }

    @Test
    void testRandomKey() {
        String key1 = "test:key:random:key1";
        String key2 = "test:key:random:key2";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            
            // Test randomKey when keys exist
            byte[] randomKey = connection.keyCommands().randomKey();
            assertThat(randomKey).isNotNull();
            
            // Should be one of our keys or some other key in the database
            String randomKeyStr = new String(randomKey);
            assertThat(randomKeyStr).isNotEmpty();
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testScan() {
        String basePattern = "test:key:scan:";
        String key1 = basePattern + "item1";
        String key2 = basePattern + "item2";
        String key3 = basePattern + "other1";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            connection.stringCommands().set(key3.getBytes(), value);
            
            // Test basic scan
            ScanOptions options = ScanOptions.scanOptions().match(basePattern + "*").build();
            Cursor<byte[]> cursor = connection.keyCommands().scan(options);
            
            java.util.List<String> scannedKeys = new java.util.ArrayList<>();
            while (cursor.hasNext()) {
                scannedKeys.add(new String(cursor.next()));
            }
            cursor.close();
            
            // Should find our test keys
            assertThat(scannedKeys).containsAll(java.util.Arrays.asList(key1, key2, key3));
            
            // Test scan with count
            ScanOptions countOptions = ScanOptions.scanOptions().count(1).build();
            Cursor<byte[]> countCursor = connection.keyCommands().scan(countOptions);
            
            java.util.List<String> countScannedKeys = new java.util.ArrayList<>();
            while (countCursor.hasNext()) {
                countScannedKeys.add(new String(countCursor.next()));
            }
            countCursor.close();
            
            // Should get results, but in smaller batches
            assertThat(countScannedKeys).isNotEmpty();
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testRename() {
        String oldKey = "test:key:rename:old";
        String newKey = "test:key:rename:new";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(oldKey.getBytes(), value);
            
            // Test rename
            connection.keyCommands().rename(oldKey.getBytes(), newKey.getBytes());
            
            // Verify old key no longer exists
            Boolean oldExists = connection.keyCommands().exists(oldKey.getBytes());
            assertThat(oldExists).isFalse();
            
            // Verify new key exists with correct value
            Boolean newExists = connection.keyCommands().exists(newKey.getBytes());
            assertThat(newExists).isTrue();
            
            byte[] newValue = connection.stringCommands().get(newKey.getBytes());
            assertThat(newValue).isEqualTo(value);
        } finally {
            cleanupKey(oldKey);
            cleanupKey(newKey);
        }
    }

    @Test
    void testRenameNX() {
        String oldKey = "test:key:renamenx:old";
        String newKey = "test:key:renamenx:new";
        String existingKey = "test:key:renamenx:existing";
        byte[] value = "test_value".getBytes();
        byte[] existingValue = "existing_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(oldKey.getBytes(), value);
            connection.stringCommands().set(existingKey.getBytes(), existingValue);
            
            // Test renameNX to non-existing key (should succeed)
            Boolean renameResult1 = connection.keyCommands().renameNX(oldKey.getBytes(), newKey.getBytes());
            assertThat(renameResult1).isTrue();
            
            // Verify rename was successful
            Boolean oldExists = connection.keyCommands().exists(oldKey.getBytes());
            assertThat(oldExists).isFalse();
            
            Boolean newExists = connection.keyCommands().exists(newKey.getBytes());
            assertThat(newExists).isTrue();
            
            // Test renameNX to existing key (should fail)
            Boolean renameResult2 = connection.keyCommands().renameNX(newKey.getBytes(), existingKey.getBytes());
            assertThat(renameResult2).isFalse();
            
            // Verify keys remain unchanged
            Boolean newStillExists = connection.keyCommands().exists(newKey.getBytes());
            assertThat(newStillExists).isTrue();
            
            byte[] existingStillValue = connection.stringCommands().get(existingKey.getBytes());
            assertThat(existingStillValue).isEqualTo(existingValue);
        } finally {
            cleanupKey(oldKey);
            cleanupKey(newKey);
            cleanupKey(existingKey);
        }
    }

    @Test
    void testExpire() {
        String key = "test:key:expire";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test expire
            connection.stringCommands().set(key.getBytes(), value);
            Boolean expireResult = connection.keyCommands().expire(key.getBytes(), 1);
            assertThat(expireResult).isTrue();
            
            // Verify key still exists initially
            Boolean exists1 = connection.keyCommands().exists(key.getBytes());
            assertThat(exists1).isTrue();
            
            // Wait for expiration
            Thread.sleep(2000);
            Boolean exists2 = connection.keyCommands().exists(key.getBytes());
            assertThat(exists2).isFalse();
            
            // Test expire on non-existent key
            Boolean expireNonExistent = connection.keyCommands().expire("non:existent:key".getBytes(), 1);
            assertThat(expireNonExistent).isFalse();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    @EnabledOnValkeyVersion("7.0") // ExpirationOptions conditions (NX, XX, GT, LT) added in Redis 7.0
    void testExpirationConditions() {
        String key = "test:key:expire";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test expire with NX condition (key must not have expiration)
            connection.stringCommands().set(key.getBytes(), value);
            Boolean expireNX1 = connection.keyCommands().expire(key.getBytes(), 10, ExpirationOptions.Condition.NX);
            assertThat(expireNX1).isTrue(); // Should succeed, key had no expiration
            
            Boolean expireNX2 = connection.keyCommands().expire(key.getBytes(), 20, ExpirationOptions.Condition.NX);
            assertThat(expireNX2).isFalse(); // Should fail, key already has expiration
            
            // Test expire with XX condition (key must have expiration)
            Boolean expireXX1 = connection.keyCommands().expire(key.getBytes(), 30, ExpirationOptions.Condition.XX);
            assertThat(expireXX1).isTrue(); // Should succeed, key has expiration
            
            // Test expire with GT condition (new expiration must be greater than current)
            Boolean expireGT1 = connection.keyCommands().expire(key.getBytes(), 20, ExpirationOptions.Condition.GT);
            assertThat(expireGT1).isFalse(); // Should fail, 20s < 30s (current)
            
            Boolean expireGT2 = connection.keyCommands().expire(key.getBytes(), 40, ExpirationOptions.Condition.GT);
            assertThat(expireGT2).isTrue(); // Should succeed, 40s > 30s (current)
            
            // Test expire with LT condition (new expiration must be less than current)
            Boolean expireLT1 = connection.keyCommands().expire(key.getBytes(), 50, ExpirationOptions.Condition.LT);
            assertThat(expireLT1).isFalse(); // Should fail, 50s > 40s (current)
            
            Boolean expireLT2 = connection.keyCommands().expire(key.getBytes(), 35, ExpirationOptions.Condition.LT);
            assertThat(expireLT2).isTrue(); // Should succeed, 35s < 40s (current)
            
            // Remove expiration
            connection.keyCommands().persist(key.getBytes());
            
            Boolean expireXX2 = connection.keyCommands().expire(key.getBytes(), 40, ExpirationOptions.Condition.XX);
            assertThat(expireXX2).isFalse(); // Should fail, key has no expiration
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPExpire() {
        String key = "test:key:pexpire";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test pExpire (milliseconds)
            connection.stringCommands().set(key.getBytes(), value);
            Boolean pExpireResult = connection.keyCommands().pExpire(key.getBytes(), 1000);
            assertThat(pExpireResult).isTrue();
            
            // Verify key still exists initially
            Boolean exists1 = connection.keyCommands().exists(key.getBytes());
            assertThat(exists1).isTrue();
            
            // Wait for expiration
            Thread.sleep(2000);
            Boolean exists2 = connection.keyCommands().exists(key.getBytes());
            assertThat(exists2).isFalse();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testExpireAt() {
        String key = "test:key:expireat";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test expireAt
            connection.stringCommands().set(key.getBytes(), value);
            long futureTimestamp = System.currentTimeMillis() / 1000 + 1;
            
            Boolean expireAtResult = connection.keyCommands().expireAt(key.getBytes(), futureTimestamp);
            assertThat(expireAtResult).isTrue();
            
            // Verify key still exists initially
            Boolean exists1 = connection.keyCommands().exists(key.getBytes());
            assertThat(exists1).isTrue();
            
            // Wait for expiration
            Thread.sleep(2000);
            Boolean exists2 = connection.keyCommands().exists(key.getBytes());
            assertThat(exists2).isFalse();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPExpireAt() {
        String key = "test:key:pexpireat";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test pExpireAt
            connection.stringCommands().set(key.getBytes(), value);
            long futureTimestampMillis = System.currentTimeMillis() + 1000;
            
            Boolean pExpireAtResult = connection.keyCommands().pExpireAt(key.getBytes(), futureTimestampMillis);
            assertThat(pExpireAtResult).isTrue();
            
            // Verify key still exists initially
            Boolean exists1 = connection.keyCommands().exists(key.getBytes());
            assertThat(exists1).isTrue();
            
            // Wait for expiration
            Thread.sleep(2000);
            Boolean exists2 = connection.keyCommands().exists(key.getBytes());
            assertThat(exists2).isFalse();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPersist() {
        String key = "test:key:persist";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data with expiration
            connection.stringCommands().set(key.getBytes(), value);
            connection.keyCommands().expire(key.getBytes(), 10); // 10 seconds
            
            // Test persist
            Boolean persistResult = connection.keyCommands().persist(key.getBytes());
            assertThat(persistResult).isTrue();
            
            // Verify TTL is now -1 (no expiration)
            Long ttl = connection.keyCommands().ttl(key.getBytes());
            assertThat(ttl).isEqualTo(-1L);
            
            // Test persist on key without expiration
            Boolean persistResult2 = connection.keyCommands().persist(key.getBytes());
            assertThat(persistResult2).isFalse();
            
            // Test persist on non-existent key
            Boolean persistNonExistent = connection.keyCommands().persist("non:existent:key".getBytes());
            assertThat(persistNonExistent).isFalse();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testMove() {
        String key = "test:key:move";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key.getBytes(), value);
            
            // Test move to different database (assuming database 1 exists)
            Boolean moveResult = connection.keyCommands().move(key.getBytes(), 1);
            // Note: Move behavior depends on Valkey configuration and available databases
            // In many test setups, only database 0 is available, so move might fail
            // We'll just verify the method executes without throwing an exception
            assertThat(moveResult).isNotNull();
            
            // Test move non-existent key
            Boolean moveNonExistent = connection.keyCommands().move("non:existent:key".getBytes(), 1);
            assertThat(moveNonExistent).isFalse();
        } finally {
            cleanupKey(key);
            // Also cleanup from database 1 if move succeeded
            try {
                connection.select(1);
                connection.keyCommands().del(key.getBytes());
                connection.select(0);
            } catch (Exception e) {
                // Ignore errors when switching databases in test environment
            }
        }
    }

    @Test
    void testTtl() {
        String key = "test:key:ttl";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test TTL on non-existent key
            Long nonExistentTtl = connection.keyCommands().ttl("non:existent:key".getBytes());
            assertThat(nonExistentTtl).isEqualTo(-2L); // Key doesn't exist
            
            // Set up test data without expiration
            connection.stringCommands().set(key.getBytes(), value);
            Long noExpirationTtl = connection.keyCommands().ttl(key.getBytes());
            assertThat(noExpirationTtl).isEqualTo(-1L); // No expiration set
            
            // Set expiration and test TTL
            connection.keyCommands().expire(key.getBytes(), 10);
            Long ttl = connection.keyCommands().ttl(key.getBytes());
            assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(10L);
            
            // Test TTL with time unit conversion
            Long ttlInMillis = connection.keyCommands().ttl(key.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(ttlInMillis).isGreaterThan(0L).isLessThanOrEqualTo(10000L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPTtl() {
        String key = "test:key:pttl";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data with expiration
            connection.stringCommands().set(key.getBytes(), value);
            connection.keyCommands().pExpire(key.getBytes(), 10000); // 10 seconds in milliseconds
            
            // Test pTtl
            Long pTtl = connection.keyCommands().pTtl(key.getBytes());
            assertThat(pTtl).isGreaterThan(0L).isLessThanOrEqualTo(10000L);
            
            // Test pTtl with time unit conversion
            Long pTtlInSeconds = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.SECONDS);
            assertThat(pTtlInSeconds).isGreaterThan(0L).isLessThanOrEqualTo(10L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testTtlTimeUnitSpecialValues() {
        String key = "test:key:ttl:special";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test TTL on non-existent key with TimeUnit conversion
            // Should return -2 (key doesn't exist) without conversion
            Long ttlNonExistentSeconds = connection.keyCommands().ttl("non:existent:key".getBytes(), TimeUnit.SECONDS);
            assertThat(ttlNonExistentSeconds).isEqualTo(-2L);
            
            Long ttlNonExistentMillis = connection.keyCommands().ttl("non:existent:key".getBytes(), TimeUnit.MILLISECONDS);
            assertThat(ttlNonExistentMillis).isEqualTo(-2L);
            
            Long ttlNonExistentMinutes = connection.keyCommands().ttl("non:existent:key".getBytes(), TimeUnit.MINUTES);
            assertThat(ttlNonExistentMinutes).isEqualTo(-2L);
            
            // Test TTL on key without expiration with TimeUnit conversion
            // Should return -1 (no expiration) without conversion
            connection.stringCommands().set(key.getBytes(), value);
            
            Long ttlNoExpirationSeconds = connection.keyCommands().ttl(key.getBytes(), TimeUnit.SECONDS);
            assertThat(ttlNoExpirationSeconds).isEqualTo(-1L);
            
            Long ttlNoExpirationMillis = connection.keyCommands().ttl(key.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(ttlNoExpirationMillis).isEqualTo(-1L);
            
            Long ttlNoExpirationMinutes = connection.keyCommands().ttl(key.getBytes(), TimeUnit.MINUTES);
            assertThat(ttlNoExpirationMinutes).isEqualTo(-1L);
            
            // Test TTL on key with expiration - should convert properly
            connection.keyCommands().expire(key.getBytes(), 60); // 60 seconds
            
            Long ttlWithExpirationSeconds = connection.keyCommands().ttl(key.getBytes(), TimeUnit.SECONDS);
            assertThat(ttlWithExpirationSeconds).isGreaterThan(0L).isLessThanOrEqualTo(60L);
            
            Long ttlWithExpirationMillis = connection.keyCommands().ttl(key.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(ttlWithExpirationMillis).isGreaterThan(0L).isLessThanOrEqualTo(60000L);
            
            // Verify conversion is correct: milliseconds should be roughly 1000x seconds
            if (ttlWithExpirationSeconds != null && ttlWithExpirationSeconds > 0) {
                double ratio = (double) ttlWithExpirationMillis / ttlWithExpirationSeconds;
                assertThat(ratio).isBetween(900.0, 1100.0); // Allow some timing variance
            }
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPTtlTimeUnitSpecialValues() {
        String key = "test:key:pttl:special";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test PTTL on non-existent key with TimeUnit conversion
            // Should return -2 (key doesn't exist) without conversion
            Long pTtlNonExistentSeconds = connection.keyCommands().pTtl("non:existent:key".getBytes(), TimeUnit.SECONDS);
            assertThat(pTtlNonExistentSeconds).isEqualTo(-2L);
            
            Long pTtlNonExistentMillis = connection.keyCommands().pTtl("non:existent:key".getBytes(), TimeUnit.MILLISECONDS);
            assertThat(pTtlNonExistentMillis).isEqualTo(-2L);
            
            Long pTtlNonExistentMinutes = connection.keyCommands().pTtl("non:existent:key".getBytes(), TimeUnit.MINUTES);
            assertThat(pTtlNonExistentMinutes).isEqualTo(-2L);
            
            // Test PTTL on key without expiration with TimeUnit conversion
            // Should return -1 (no expiration) without conversion
            connection.stringCommands().set(key.getBytes(), value);
            
            Long pTtlNoExpirationSeconds = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.SECONDS);
            assertThat(pTtlNoExpirationSeconds).isEqualTo(-1L);
            
            Long pTtlNoExpirationMillis = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(pTtlNoExpirationMillis).isEqualTo(-1L);
            
            Long pTtlNoExpirationMinutes = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.MINUTES);
            assertThat(pTtlNoExpirationMinutes).isEqualTo(-1L);
            
            // Test PTTL on key with expiration - should convert properly
            connection.keyCommands().pExpire(key.getBytes(), 60000); // 60 seconds in milliseconds
            
            Long pTtlWithExpirationMillis = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(pTtlWithExpirationMillis).isGreaterThan(0L).isLessThanOrEqualTo(60000L);
            
            Long pTtlWithExpirationSeconds = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.SECONDS);
            assertThat(pTtlWithExpirationSeconds).isGreaterThan(0L).isLessThanOrEqualTo(60L);
            
            // Verify conversion is correct: milliseconds should be roughly 1000x seconds
            if (pTtlWithExpirationSeconds != null && pTtlWithExpirationSeconds > 0) {
                double ratio = (double) pTtlWithExpirationMillis / pTtlWithExpirationSeconds;
                assertThat(ratio).isBetween(900.0, 1100.0); // Allow some timing variance
            }
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSortComprehensive() {
        String listKey = "test:key:sort:list";
        String storeKey = "test:key:sort:store";
        
        try {
            // Set up test data - add some sortable values
            connection.listCommands().lPush(listKey.getBytes(), "3".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "1".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "2".getBytes());
            
            // Test sort without parameters
            List<byte[]> sortedResult1 = connection.keyCommands().sort(listKey.getBytes(), null);
            assertThat(sortedResult1).hasSize(3);
            // Should be sorted as strings: "1", "2", "3"
            assertThat(new String(sortedResult1.get(0))).isEqualTo("1");
            assertThat(new String(sortedResult1.get(1))).isEqualTo("2");
            assertThat(new String(sortedResult1.get(2))).isEqualTo("3");
            
            // Test sort with LIMIT parameters
            SortParameters params1 = new DefaultSortParameters().limit(0, 2);
            List<byte[]> sortedResult2 = connection.keyCommands().sort(listKey.getBytes(), params1);
            assertThat(sortedResult2).hasSize(2); // Limited by count
            assertThat(new String(sortedResult2.get(0))).isEqualTo("1");
            assertThat(new String(sortedResult2.get(1))).isEqualTo("2");
            
            // Test sort with DESC order
            SortParameters params2 = new DefaultSortParameters().desc();
            List<byte[]> sortedResult3 = connection.keyCommands().sort(listKey.getBytes(), params2);
            assertThat(sortedResult3).hasSize(3);
            assertThat(new String(sortedResult3.get(0))).isEqualTo("3");
            assertThat(new String(sortedResult3.get(1))).isEqualTo("2");
            assertThat(new String(sortedResult3.get(2))).isEqualTo("1");
            
            // Test sort with ASC order (explicit)
            SortParameters params3 = new DefaultSortParameters().asc();
            List<byte[]> sortedResult4 = connection.keyCommands().sort(listKey.getBytes(), params3);
            assertThat(sortedResult4).hasSize(3);
            assertThat(new String(sortedResult4.get(0))).isEqualTo("1");
            assertThat(new String(sortedResult4.get(1))).isEqualTo("2");
            assertThat(new String(sortedResult4.get(2))).isEqualTo("3");
            
            // Test sort with alphabetic ordering
            connection.keyCommands().del(listKey.getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "b".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "a".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "c".getBytes());
            
            SortParameters params4 = new DefaultSortParameters().alpha();
            List<byte[]> sortedResult5 = connection.keyCommands().sort(listKey.getBytes(), params4);
            assertThat(sortedResult5).hasSize(3);
            assertThat(new String(sortedResult5.get(0))).isEqualTo("a");
            assertThat(new String(sortedResult5.get(1))).isEqualTo("b");
            assertThat(new String(sortedResult5.get(2))).isEqualTo("c");
            
            // Test sort with multiple parameters combined
            SortParameters params5 = new DefaultSortParameters().alpha().desc().limit(0, 2);
            List<byte[]> sortedResult6 = connection.keyCommands().sort(listKey.getBytes(), params5);
            assertThat(sortedResult6).hasSize(2);
            assertThat(new String(sortedResult6.get(0))).isEqualTo("c");
            assertThat(new String(sortedResult6.get(1))).isEqualTo("b");
            
            // Test sort with store (use alphabetic sort since list contains "a", "b", "c")
            Long storeResult = connection.keyCommands().sort(listKey.getBytes(), params4, storeKey.getBytes());
            assertThat(storeResult).isEqualTo(3L); // 3 elements stored
            
            // Verify stored results
            List<byte[]> storedList = connection.listCommands().lRange(storeKey.getBytes(), 0, -1);
            assertThat(storedList).hasSize(3);
            
            // Test sort with store and parameters
            Long storeResult2 = connection.keyCommands().sort(listKey.getBytes(), params4, storeKey.getBytes());
            assertThat(storeResult2).isEqualTo(3L);
            
        } finally {
            cleanupKey(listKey);
            cleanupKey(storeKey);
        }
    }

    @Test
    void testDumpRestore() {
        String sourceKey = "test:key:dump";
        String restoreKey = "test:key:restore";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(sourceKey.getBytes(), value);
            
            // Test dump
            byte[] serialized = connection.keyCommands().dump(sourceKey.getBytes());
            assertThat(serialized).isNotNull();
            
            // Test dump on non-existent key
            byte[] nonExistentDump = connection.keyCommands().dump("non:existent:key".getBytes());
            assertThat(nonExistentDump).isNull();
            
            // Test restore
            connection.keyCommands().restore(restoreKey.getBytes(), 0, serialized, false);
            
            // Verify restore was successful
            byte[] restoredValue = connection.stringCommands().get(restoreKey.getBytes());
            assertThat(restoredValue).isEqualTo(value);
            
            // Test restore with replace
            byte[] newValue = "new_value".getBytes();
            connection.stringCommands().set(sourceKey.getBytes(), newValue);
            byte[] newSerialized = connection.keyCommands().dump(sourceKey.getBytes());
            
            connection.keyCommands().restore(restoreKey.getBytes(), 0, newSerialized, true);
            byte[] replacedValue = connection.stringCommands().get(restoreKey.getBytes());
            assertThat(replacedValue).isEqualTo(newValue);
        } finally {
            cleanupKey(sourceKey);
            cleanupKey(restoreKey);
        }
    }

    @Test
    void testObjectIntrospection() {
        String key = "test:key:encoding";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key.getBytes(), value);
            
            // Test encodingOf
            ValueEncoding encoding = connection.keyCommands().encodingOf(key.getBytes());
            assertThat(encoding).isNotNull();
            // The exact encoding depends on Valkey/Valkey version and configuration
            
            // Test encodingOf on non-existent key
            ValueEncoding nonExistentEncoding = connection.keyCommands().encodingOf("non:existent:key".getBytes());
            assertThat(nonExistentEncoding).isEqualTo(ValueEncoding.ValkeyValueEncoding.VACANT);
            
            // Test idletime
            Duration idletime = connection.keyCommands().idletime(key.getBytes());
            assertThat(idletime).isNotNull();
            assertThat(idletime.getSeconds()).isGreaterThanOrEqualTo(0);
            
            // Test idletime on non-existent key
            Duration nonExistentIdletime = connection.keyCommands().idletime("non:existent:key".getBytes());
            assertThat(nonExistentIdletime).isNull();
            
            // Test refcount
            Long refcount = connection.keyCommands().refcount(key.getBytes());
            assertThat(refcount).isNotNull();
            assertThat(refcount).isGreaterThan(0);
            
            // Test refcount on non-existent key
            Long nonExistentRefcount = connection.keyCommands().refcount("non:existent:key".getBytes());
            assertThat(nonExistentRefcount).isNull();
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== PIPELINE MODE TESTS ====================

    @Test
    void testKeyOperationsPipeline() {
        String key1 = "test:key:pipeline:key1";
        String key2 = "test:key:pipeline:key2";
        String key3 = "test:key:pipeline:key3";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            
            // Start pipeline
            connection.openPipeline();
            
            // Pipeline commands should return null
            Boolean existsResult = connection.keyCommands().exists(key1.getBytes());
            assertThat(existsResult).isNull();
            
            Long existsMultipleResult = connection.keyCommands().exists(key1.getBytes(), key2.getBytes(), key3.getBytes());
            assertThat(existsMultipleResult).isNull();
            
            DataType typeResult = connection.keyCommands().type(key1.getBytes());
            assertThat(typeResult).isNull();
            
            Long touchResult = connection.keyCommands().touch(key1.getBytes(), key2.getBytes());
            assertThat(touchResult).isNull();
            
            Boolean copyResult = connection.keyCommands().copy(key1.getBytes(), key3.getBytes(), false);
            assertThat(copyResult).isNull();
            
            Long delResult = connection.keyCommands().del(key2.getBytes());
            assertThat(delResult).isNull();
            
            // Execute pipeline and collect results
            List<Object> results = connection.closePipeline();
            
            // Verify results in order
            assertThat(results).hasSize(6);
            assertThat((Boolean) results.get(0)).isTrue(); // exists(key1)
            assertThat((Long) results.get(1)).isEqualTo(2L); // exists multiple (key1, key2 existed)
            assertThat((DataType) results.get(2)).isEqualTo(DataType.STRING); // type(key1)
            assertThat((Long) results.get(3)).isEqualTo(2L); // touch(key1, key2)
            assertThat((Boolean) results.get(4)).isTrue(); // copy(key1 -> key3)
            assertThat((Long) results.get(5)).isEqualTo(1L); // del(key2)
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testExpirationOperationsPipeline() {
        String key1 = "test:key:pipeline:expire1";
        String key2 = "test:key:pipeline:expire2";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            
            // Start pipeline
            connection.openPipeline();
            
            // Pipeline commands should return null
            Boolean expireResult = connection.keyCommands().expire(key1.getBytes(), 10);
            assertThat(expireResult).isNull();
            
            Boolean pExpireResult = connection.keyCommands().pExpire(key2.getBytes(), 20000);
            assertThat(pExpireResult).isNull();
            
            Long ttlResult = connection.keyCommands().ttl(key1.getBytes());
            assertThat(ttlResult).isNull();
            
            Long pTtlResult = connection.keyCommands().pTtl(key2.getBytes());
            assertThat(pTtlResult).isNull();
            
            Boolean persistResult = connection.keyCommands().persist(key1.getBytes());
            assertThat(persistResult).isNull();
            
            // Execute pipeline and collect results
            List<Object> results = connection.closePipeline();
            
            // Verify results in order
            assertThat(results).hasSize(5);
            assertThat((Boolean) results.get(0)).isTrue(); // expire
            assertThat((Boolean) results.get(1)).isTrue(); // pExpire
            assertThat((Long) results.get(2)).isGreaterThan(0L).isLessThanOrEqualTo(10L); // ttl
            assertThat((Long) results.get(3)).isGreaterThan(0L).isLessThanOrEqualTo(20000L); // pTtl
            assertThat((Boolean) results.get(4)).isTrue(); // persist
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testVoidMethodsPipeline() {
        String oldKey = "test:key:pipeline:rename:old";
        String newKey = "test:key:pipeline:rename:new";
        String restoreKey = "test:key:pipeline:restore";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(oldKey.getBytes(), value);
            byte[] serialized = connection.keyCommands().dump(oldKey.getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Void methods should return null in pipeline mode but still queue the command
            connection.keyCommands().rename(oldKey.getBytes(), newKey.getBytes());
            connection.keyCommands().restore(restoreKey.getBytes(), 0, serialized, false);
            
            // Execute pipeline and collect results
            List<Object> results = connection.closePipeline();
            
            // Verify results - void methods should return "OK" responses
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isEqualTo("OK"); // rename
            assertThat(results.get(1)).isEqualTo("OK"); // restore
            
            // Verify operations actually succeeded
            Boolean newExists = connection.keyCommands().exists(newKey.getBytes());
            assertThat(newExists).isTrue();
            
            Boolean restoreExists = connection.keyCommands().exists(restoreKey.getBytes());
            assertThat(restoreExists).isTrue();
            
        } finally {
            cleanupKey(oldKey);
            cleanupKey(newKey);
            cleanupKey(restoreKey);
        }
    }

    @Test
    void testAdvancedKeyOperationsPipeline() {
        String key1 = "test:key:pipeline:advanced:key1";
        String key2 = "test:key:pipeline:advanced:key2";
        String key3 = "test:key:pipeline:advanced:key3";
        String listKey = "test:key:pipeline:advanced:list";
        String storeKey = "test:key:pipeline:advanced:store";
        String renameKey = "test:key:pipeline:advanced:rename";
        String newRenameKey = "test:key:pipeline:advanced:newrename";
        String basePattern = "test:key:pipeline:advanced:";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            connection.stringCommands().set(key3.getBytes(), value);
            connection.stringCommands().set(renameKey.getBytes(), value);
            connection.listCommands().lPush(listKey.getBytes(), "c".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "a".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "b".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Test all missing operations - pipeline commands should return null
            Long unlinkResult = connection.keyCommands().unlink(key1.getBytes(), key2.getBytes());
            assertThat(unlinkResult).isNull();
            
            Set<byte[]> keysResult = connection.keyCommands().keys((basePattern + "*").getBytes());
            assertThat(keysResult).isNull();
            
            byte[] randomKeyResult = connection.keyCommands().randomKey();
            assertThat(randomKeyResult).isNull();
            
            Boolean renameNXResult = connection.keyCommands().renameNX(renameKey.getBytes(), newRenameKey.getBytes());
            assertThat(renameNXResult).isNull();
            
            Boolean moveResult = connection.keyCommands().move(key3.getBytes(), 1);
            assertThat(moveResult).isNull();
            
            long futureTimestamp = System.currentTimeMillis() / 1000 + 30;
            Boolean expireAtResult = connection.keyCommands().expireAt(newRenameKey.getBytes(), futureTimestamp);
            assertThat(expireAtResult).isNull();
            
            long futureTimestampMillis = System.currentTimeMillis() + 30000;
            Boolean pExpireAtResult = connection.keyCommands().pExpireAt(newRenameKey.getBytes(), futureTimestampMillis);
            assertThat(pExpireAtResult).isNull();
            
            Long ttlTimeUnitResult = connection.keyCommands().ttl(newRenameKey.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(ttlTimeUnitResult).isNull();
            
            SortParameters sortParams = new DefaultSortParameters().alpha();
            List<byte[]> sortResult = connection.keyCommands().sort(listKey.getBytes(), sortParams);
            assertThat(sortResult).isNull();
            
            Long sortStoreResult = connection.keyCommands().sort(listKey.getBytes(), sortParams, storeKey.getBytes());
            assertThat(sortStoreResult).isNull();
            
            byte[] dumpResult = connection.keyCommands().dump(newRenameKey.getBytes());
            assertThat(dumpResult).isNull();
            
            ValueEncoding encodingResult = connection.keyCommands().encodingOf(newRenameKey.getBytes());
            assertThat(encodingResult).isNull();
            
            Duration idletimeResult = connection.keyCommands().idletime(newRenameKey.getBytes());
            assertThat(idletimeResult).isNull();
            
            Long refcountResult = connection.keyCommands().refcount(newRenameKey.getBytes());
            assertThat(refcountResult).isNull();
            
            // Execute pipeline and collect results
            List<Object> results = connection.closePipeline();
            
            // Verify results in order
            assertThat(results).hasSize(14);
            assertThat((Long) results.get(0)).isEqualTo(2L); // unlink(key1, key2)
            assertThat((Set<?>) results.get(1)).isNotNull(); // keys(pattern)
            assertThat(results.get(2)).isNotNull(); // randomKey() - should return some key
            assertThat((Boolean) results.get(3)).isTrue(); // renameNX succeeded
            assertThat(results.get(4)).isNotNull(); // move result (depends on DB config)
            assertThat((Boolean) results.get(5)).isTrue(); // expireAt
            assertThat((Boolean) results.get(6)).isTrue(); // pExpireAt  
            assertThat(results.get(7)).isNotNull(); // ttl with TimeUnit
            assertThat((List<?>) results.get(8)).hasSize(3); // sort result
            assertThat((Long) results.get(9)).isEqualTo(3L); // sort store result
            assertThat(results.get(10)).isNotNull(); // dump result
            assertThat(results.get(11)).isNotNull(); // encodingOf result
            assertThat(results.get(12)).isNotNull(); // idletime result
            assertThat(results.get(13)).isNotNull(); // refcount result
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(listKey);
            cleanupKey(storeKey);
            cleanupKey(renameKey);
            cleanupKey(newRenameKey);
        }
    }

    // ==================== TRANSACTION MODE TESTS ====================

    @Test
    void testKeyOperationsTransaction() {
        String key1 = "test:key:transaction:key1";
        String key2 = "test:key:transaction:key2";
        String key3 = "test:key:transaction:key3";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            
            // Start transaction
            connection.multi();
            
            // Transaction commands should return null
            Boolean existsResult = connection.keyCommands().exists(key1.getBytes());
            assertThat(existsResult).isNull();
            
            Long existsMultipleResult = connection.keyCommands().exists(key1.getBytes(), key2.getBytes(), key3.getBytes());
            assertThat(existsMultipleResult).isNull();
            
            DataType typeResult = connection.keyCommands().type(key1.getBytes());
            assertThat(typeResult).isNull();
            
            Long touchResult = connection.keyCommands().touch(key1.getBytes(), key2.getBytes());
            assertThat(touchResult).isNull();
            
            Boolean copyResult = connection.keyCommands().copy(key1.getBytes(), key3.getBytes(), false);
            assertThat(copyResult).isNull();
            
            Long delResult = connection.keyCommands().del(key2.getBytes());
            assertThat(delResult).isNull();
            
            // Execute transaction and collect results
            List<Object> results = connection.exec();
            
            // Verify results in order
            assertThat(results).hasSize(6);
            assertThat((Boolean) results.get(0)).isTrue(); // exists(key1)
            assertThat((Long) results.get(1)).isEqualTo(2L); // exists multiple (key1, key2 existed)
            assertThat((DataType) results.get(2)).isEqualTo(DataType.STRING); // type(key1)
            assertThat((Long) results.get(3)).isEqualTo(2L); // touch(key1, key2)
            assertThat((Boolean) results.get(4)).isTrue(); // copy(key1 -> key3)
            assertThat((Long) results.get(5)).isEqualTo(1L); // del(key2)
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testExpirationOperationsTransaction() {
        String key1 = "test:key:transaction:expire1";
        String key2 = "test:key:transaction:expire2";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            
            // Start transaction
            connection.multi();
            
            // Transaction commands should return null
            Boolean expireResult = connection.keyCommands().expire(key1.getBytes(), 10);
            assertThat(expireResult).isNull();
            
            Boolean pExpireResult = connection.keyCommands().pExpire(key2.getBytes(), 20000);
            assertThat(pExpireResult).isNull();
            
            Long ttlResult = connection.keyCommands().ttl(key1.getBytes());
            assertThat(ttlResult).isNull();
            
            Long pTtlResult = connection.keyCommands().pTtl(key2.getBytes());
            assertThat(pTtlResult).isNull();
            
            Boolean persistResult = connection.keyCommands().persist(key1.getBytes());
            assertThat(persistResult).isNull();
            
            // Execute transaction and collect results
            List<Object> results = connection.exec();
            
            // Verify results in order
            assertThat(results).hasSize(5);
            assertThat((Boolean) results.get(0)).isTrue(); // expire
            assertThat((Boolean) results.get(1)).isTrue(); // pExpire
            assertThat((Long) results.get(2)).isGreaterThan(0L).isLessThanOrEqualTo(10L); // ttl
            assertThat((Long) results.get(3)).isGreaterThan(0L).isLessThanOrEqualTo(20000L); // pTtl
            assertThat((Boolean) results.get(4)).isTrue(); // persist
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testVoidMethodsTransaction() {
        String oldKey = "test:key:transaction:rename:old";
        String newKey = "test:key:transaction:rename:new";
        String restoreKey = "test:key:transaction:restore";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(oldKey.getBytes(), value);
            byte[] serialized = connection.keyCommands().dump(oldKey.getBytes());
            
            // Start transaction
            connection.multi();
            
            // Void methods should return null in transaction mode but still queue the command
            connection.keyCommands().rename(oldKey.getBytes(), newKey.getBytes());
            connection.keyCommands().restore(restoreKey.getBytes(), 0, serialized, false);
            
            // Execute transaction and collect results
            List<Object> results = connection.exec();
            
            // Verify results - void methods should return "OK" responses
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isEqualTo("OK"); // rename
            assertThat(results.get(1)).isEqualTo("OK"); // restore
            
            // Verify operations actually succeeded
            Boolean newExists = connection.keyCommands().exists(newKey.getBytes());
            assertThat(newExists).isTrue();
            
            Boolean restoreExists = connection.keyCommands().exists(restoreKey.getBytes());
            assertThat(restoreExists).isTrue();
            
        } finally {
            cleanupKey(oldKey);
            cleanupKey(newKey);
            cleanupKey(restoreKey);
        }
    }

    @Test
    void testAdvancedKeyOperationsTransaction() {
        String key1 = "test:key:transaction:advanced:key1";
        String key2 = "test:key:transaction:advanced:key2";
        String key3 = "test:key:transaction:advanced:key3";
        String listKey = "test:key:transaction:advanced:list";
        String storeKey = "test:key:transaction:advanced:store";
        String renameKey = "test:key:transaction:advanced:rename";
        String newRenameKey = "test:key:transaction:advanced:newrename";
        String basePattern = "test:key:transaction:advanced:";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), value);
            connection.stringCommands().set(key2.getBytes(), value);
            connection.stringCommands().set(key3.getBytes(), value);
            connection.stringCommands().set(renameKey.getBytes(), value);
            connection.listCommands().lPush(listKey.getBytes(), "c".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "a".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "b".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Test all missing operations - transaction commands should return null
            Long unlinkResult = connection.keyCommands().unlink(key1.getBytes(), key2.getBytes());
            assertThat(unlinkResult).isNull();
            
            Set<byte[]> keysResult = connection.keyCommands().keys((basePattern + "*").getBytes());
            assertThat(keysResult).isNull();
            
            byte[] randomKeyResult = connection.keyCommands().randomKey();
            assertThat(randomKeyResult).isNull();
            
            Boolean renameNXResult = connection.keyCommands().renameNX(renameKey.getBytes(), newRenameKey.getBytes());
            assertThat(renameNXResult).isNull();
            
            Boolean moveResult = connection.keyCommands().move(key3.getBytes(), 1);
            assertThat(moveResult).isNull();
            
            long futureTimestamp = System.currentTimeMillis() / 1000 + 30;
            Boolean expireAtResult = connection.keyCommands().expireAt(newRenameKey.getBytes(), futureTimestamp);
            assertThat(expireAtResult).isNull();
            
            long futureTimestampMillis = System.currentTimeMillis() + 30000;
            Boolean pExpireAtResult = connection.keyCommands().pExpireAt(newRenameKey.getBytes(), futureTimestampMillis);
            assertThat(pExpireAtResult).isNull();
            
            Long ttlTimeUnitResult = connection.keyCommands().ttl(newRenameKey.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(ttlTimeUnitResult).isNull();
            
            SortParameters sortParams = new DefaultSortParameters().alpha();
            List<byte[]> sortResult = connection.keyCommands().sort(listKey.getBytes(), sortParams);
            assertThat(sortResult).isNull();
            
            Long sortStoreResult = connection.keyCommands().sort(listKey.getBytes(), sortParams, storeKey.getBytes());
            assertThat(sortStoreResult).isNull();
            
            byte[] dumpResult = connection.keyCommands().dump(newRenameKey.getBytes());
            assertThat(dumpResult).isNull();
            
            ValueEncoding encodingResult = connection.keyCommands().encodingOf(newRenameKey.getBytes());
            assertThat(encodingResult).isNull();
            
            Duration idletimeResult = connection.keyCommands().idletime(newRenameKey.getBytes());
            assertThat(idletimeResult).isNull();
            
            Long refcountResult = connection.keyCommands().refcount(newRenameKey.getBytes());
            assertThat(refcountResult).isNull();
            
            // Execute transaction and collect results
            List<Object> results = connection.exec();
            
            // Verify results in order
            assertThat(results).hasSize(14);
            assertThat((Long) results.get(0)).isEqualTo(2L); // unlink(key1, key2)
            assertThat((Set<?>) results.get(1)).isNotNull(); // keys(pattern)
            assertThat(results.get(2)).isNotNull(); // randomKey() - should return some key
            assertThat((Boolean) results.get(3)).isTrue(); // renameNX succeeded
            assertThat(results.get(4)).isNotNull(); // move result (depends on DB config)
            assertThat((Boolean) results.get(5)).isTrue(); // expireAt
            assertThat((Boolean) results.get(6)).isTrue(); // pExpireAt  
            assertThat(results.get(7)).isNotNull(); // ttl with TimeUnit
            assertThat((List<?>) results.get(8)).hasSize(3); // sort result
            assertThat((Long) results.get(9)).isEqualTo(3L); // sort store result
            assertThat(results.get(10)).isNotNull(); // dump result
            assertThat(results.get(11)).isNotNull(); // encodingOf result
            assertThat(results.get(12)).isNotNull(); // idletime result
            assertThat(results.get(13)).isNotNull(); // refcount result
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(listKey);
            cleanupKey(storeKey);
            cleanupKey(renameKey);
            cleanupKey(newRenameKey);
        }
    }

    // ==================== PIPELINE MODE TTL TIMEUNIT CONVERSION TESTS ====================

    @Test
    void testTtlTimeUnitConversionPipeline() {
        String key = "test:key:pipeline:ttl:conversion";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key.getBytes(), value);
            
            // Start pipeline
            connection.openPipeline();
            
            // Set 1 day expiration (86400 seconds)
            Boolean expireResult = connection.keyCommands().expire(key.getBytes(), 86400);
            assertThat(expireResult).isNull(); // Should be null in pipeline mode
            
            // Get TTL in various time units - all should return null in pipeline mode
            Long ttlSeconds = connection.keyCommands().ttl(key.getBytes());
            assertThat(ttlSeconds).isNull();
            
            Long ttlMinutes = connection.keyCommands().ttl(key.getBytes(), TimeUnit.MINUTES);
            assertThat(ttlMinutes).isNull();
            
            Long ttlHours = connection.keyCommands().ttl(key.getBytes(), TimeUnit.HOURS);
            assertThat(ttlHours).isNull();
            
            Long ttlMilliseconds = connection.keyCommands().ttl(key.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(ttlMilliseconds).isNull();
            
            // Execute pipeline and collect results
            List<Object> results = connection.closePipeline();
            
            // Verify results - this is the critical test case that was failing
            assertThat(results).hasSize(5);
            assertThat((Boolean) results.get(0)).isTrue(); // expire succeeded
            
            // TTL in seconds should be around 86400 (1 day)
            Long actualTtlSeconds = (Long) results.get(1);
            assertThat(actualTtlSeconds).isGreaterThanOrEqualTo(86390L).isLessThanOrEqualTo(86400L);
            
            // TTL in minutes should be around 1440 (24 hours * 60 minutes)
            Long actualTtlMinutes = (Long) results.get(2);
            assertThat(actualTtlMinutes).isGreaterThanOrEqualTo(1439L).isLessThanOrEqualTo(1440L);
            
            // TTL in hours should be around 24 - THIS WAS THE FAILING CASE
            Long actualTtlHours = (Long) results.get(3);
            assertThat(actualTtlHours).isGreaterThanOrEqualTo(23L).isLessThanOrEqualTo(24L);
            
            // TTL in milliseconds should be around 86400000 (1 day in ms)
            Long actualTtlMilliseconds = (Long) results.get(4);
            assertThat(actualTtlMilliseconds).isGreaterThanOrEqualTo(86390000L).isLessThanOrEqualTo(86400000L);
            
            // Verify conversion relationships
            if (actualTtlHours != null && actualTtlHours > 0) {
                double hoursToSecondsRatio = (double) actualTtlSeconds / actualTtlHours;
                assertThat(hoursToSecondsRatio).isBetween(3500.0, 3700.0); // ~3600 seconds per hour
                
                double hoursToMillisecondsRatio = (double) actualTtlMilliseconds / actualTtlHours;
                assertThat(hoursToMillisecondsRatio).isBetween(3500000.0, 3700000.0); // ~3600000 ms per hour
            }
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPTtlTimeUnitConversionPipeline() {
        String key = "test:key:pipeline:pttl:conversion";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key.getBytes(), value);
            
            // Start pipeline
            connection.openPipeline();
            
            // Set 1 day expiration in milliseconds (86400000 ms)
            Boolean pExpireResult = connection.keyCommands().pExpire(key.getBytes(), 86400000L);
            assertThat(pExpireResult).isNull(); // Should be null in pipeline mode
            
            // Get PTTL in various time units - all should return null in pipeline mode
            Long pTtlMilliseconds = connection.keyCommands().pTtl(key.getBytes());
            assertThat(pTtlMilliseconds).isNull();
            
            Long pTtlSeconds = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.SECONDS);
            assertThat(pTtlSeconds).isNull();
            
            Long pTtlMinutes = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.MINUTES);
            assertThat(pTtlMinutes).isNull();
            
            Long pTtlHours = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.HOURS);
            assertThat(pTtlHours).isNull();
            
            // Execute pipeline and collect results
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(5);
            assertThat((Boolean) results.get(0)).isTrue(); // pExpire succeeded
            
            // PTTL in milliseconds should be around 86400000 (1 day)
            Long actualPTtlMilliseconds = (Long) results.get(1);
            assertThat(actualPTtlMilliseconds).isGreaterThanOrEqualTo(86390000L).isLessThanOrEqualTo(86400000L);
            
            // PTTL in seconds should be around 86400 (1 day in seconds)
            Long actualPTtlSeconds = (Long) results.get(2);
            assertThat(actualPTtlSeconds).isGreaterThanOrEqualTo(86390L).isLessThanOrEqualTo(86400L);
            
            // PTTL in minutes should be around 1440 (24 hours * 60 minutes)
            Long actualPTtlMinutes = (Long) results.get(3);
            assertThat(actualPTtlMinutes).isGreaterThanOrEqualTo(1439L).isLessThanOrEqualTo(1440L);
            
            // PTTL in hours should be around 24
            Long actualPTtlHours = (Long) results.get(4);
            assertThat(actualPTtlHours).isGreaterThanOrEqualTo(23L).isLessThanOrEqualTo(24L);
            
            // Verify conversion relationships
            if (actualPTtlHours != null && actualPTtlHours > 0) {
                double hoursToMillisecondsRatio = (double) actualPTtlMilliseconds / actualPTtlHours;
                assertThat(hoursToMillisecondsRatio).isBetween(3500000.0, 3800000.0); // ~3600000 ms per hour
            }
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== TRANSACTION MODE TTL TIMEUNIT CONVERSION TESTS ====================

    @Test
    void testTtlTimeUnitConversionTransaction() {
        String key = "test:key:transaction:ttl:conversion";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key.getBytes(), value);
            
            // Start transaction
            connection.multi();
            
            // Set 1 day expiration (86400 seconds)
            Boolean expireResult = connection.keyCommands().expire(key.getBytes(), 86400);
            assertThat(expireResult).isNull(); // Should be null in transaction mode
            
            // Get TTL in various time units - all should return null in transaction mode
            Long ttlSeconds = connection.keyCommands().ttl(key.getBytes());
            assertThat(ttlSeconds).isNull();
            
            Long ttlMinutes = connection.keyCommands().ttl(key.getBytes(), TimeUnit.MINUTES);
            assertThat(ttlMinutes).isNull();
            
            Long ttlHours = connection.keyCommands().ttl(key.getBytes(), TimeUnit.HOURS);
            assertThat(ttlHours).isNull();
            
            Long ttlMilliseconds = connection.keyCommands().ttl(key.getBytes(), TimeUnit.MILLISECONDS);
            assertThat(ttlMilliseconds).isNull();
            
            // Execute transaction and collect results
            List<Object> results = connection.exec();
            
            // Verify results - this is the critical test case that was failing
            assertThat(results).hasSize(5);
            assertThat((Boolean) results.get(0)).isTrue(); // expire succeeded
            
            // TTL in seconds should be around 86400 (1 day)
            Long actualTtlSeconds = (Long) results.get(1);
            assertThat(actualTtlSeconds).isGreaterThanOrEqualTo(86390L).isLessThanOrEqualTo(86400L);
            
            // TTL in minutes should be around 1440 (24 hours * 60 minutes)
            Long actualTtlMinutes = (Long) results.get(2);
            assertThat(actualTtlMinutes).isGreaterThanOrEqualTo(1439L).isLessThanOrEqualTo(1440L);
            
            // TTL in hours should be around 24 - THIS WAS THE FAILING CASE
            Long actualTtlHours = (Long) results.get(3);
            assertThat(actualTtlHours).isGreaterThanOrEqualTo(23L).isLessThanOrEqualTo(24L);
            
            // TTL in milliseconds should be around 86400000 (1 day in ms)
            Long actualTtlMilliseconds = (Long) results.get(4);
            assertThat(actualTtlMilliseconds).isGreaterThanOrEqualTo(86390000L).isLessThanOrEqualTo(86400000L);
            
            // Verify conversion relationships
            if (actualTtlHours != null && actualTtlHours > 0) {
                double hoursToSecondsRatio = (double) actualTtlSeconds / actualTtlHours;
                assertThat(hoursToSecondsRatio).isBetween(3500.0, 3700.0); // ~3600 seconds per hour
                
                double hoursToMillisecondsRatio = (double) actualTtlMilliseconds / actualTtlHours;
                assertThat(hoursToMillisecondsRatio).isBetween(3500000.0, 3700000.0); // ~3600000 ms per hour
            }
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testPTtlTimeUnitConversionTransaction() {
        String key = "test:key:transaction:pttl:conversion";
        byte[] value = "test_value".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(key.getBytes(), value);
            
            // Start transaction
            connection.multi();
            
            // Set 1 day expiration in milliseconds (86400000 ms)
            Boolean pExpireResult = connection.keyCommands().pExpire(key.getBytes(), 86400000L);
            assertThat(pExpireResult).isNull(); // Should be null in transaction mode
            
            // Get PTTL in various time units - all should return null in transaction mode
            Long pTtlMilliseconds = connection.keyCommands().pTtl(key.getBytes());
            assertThat(pTtlMilliseconds).isNull();
            
            Long pTtlSeconds = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.SECONDS);
            assertThat(pTtlSeconds).isNull();
            
            Long pTtlMinutes = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.MINUTES);
            assertThat(pTtlMinutes).isNull();
            
            Long pTtlHours = connection.keyCommands().pTtl(key.getBytes(), TimeUnit.HOURS);
            assertThat(pTtlHours).isNull();
            
            // Execute transaction and collect results
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).hasSize(5);
            assertThat((Boolean) results.get(0)).isTrue(); // pExpire succeeded
            
            // PTTL in milliseconds should be around 86400000 (1 day)
            Long actualPTtlMilliseconds = (Long) results.get(1);
            assertThat(actualPTtlMilliseconds).isGreaterThanOrEqualTo(86390000L).isLessThanOrEqualTo(86400000L);
            
            // PTTL in seconds should be around 86400 (1 day in seconds)
            Long actualPTtlSeconds = (Long) results.get(2);
            assertThat(actualPTtlSeconds).isGreaterThanOrEqualTo(86390L).isLessThanOrEqualTo(86400L);
            
            // PTTL in minutes should be around 1440 (24 hours * 60 minutes)
            Long actualPTtlMinutes = (Long) results.get(3);
            assertThat(actualPTtlMinutes).isGreaterThanOrEqualTo(1439L).isLessThanOrEqualTo(1440L);
            
            // PTTL in hours should be around 24
            Long actualPTtlHours = (Long) results.get(4);
            assertThat(actualPTtlHours).isGreaterThanOrEqualTo(23L).isLessThanOrEqualTo(24L);
            
            // Verify conversion relationships
            if (actualPTtlHours != null && actualPTtlHours > 0) {
                double hoursToMillisecondsRatio = (double) actualPTtlMilliseconds / actualPTtlHours;
                assertThat(hoursToMillisecondsRatio).isBetween(3500000.0, 3700000.0); // ~3600000 ms per hour
            }
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== ERROR HANDLING AND EDGE CASES ====================

    @Test
    void testKeyOperationsErrorHandling() {
        // Test operations on null keys (should throw IllegalArgumentException)
        assertThatThrownBy(() -> connection.keyCommands().exists((byte[]) null))
            .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> connection.keyCommands().del((byte[]) null))
            .isInstanceOf(IllegalArgumentException.class);
        
        // Test rename with non-existent key (should throw an exception)
        assertThatThrownBy(() -> connection.keyCommands().rename("non:existent:key".getBytes(), "new:key".getBytes()))
            .isInstanceOf(Exception.class);
    }

    @Test
    @EnabledOnValkeyVersion("7.0") // ExpirationOptions conditions (NX, XX, GT, LT) added in Redis 7.0
    void testAdvancedExpirationConditions() {
        String key = "test:key:advanced:expiration";
        byte[] value = "test_value".getBytes();
        
        try {
            // ========== expireAt with conditions ==========
            connection.stringCommands().set(key.getBytes(), value);
            long futureTimestamp = System.currentTimeMillis() / 1000 + 30;
            
            // Test expireAt with NX condition (key must not have expiration)
            Boolean expireAtNX1 = connection.keyCommands().expireAt(key.getBytes(), futureTimestamp, ExpirationOptions.Condition.NX);
            assertThat(expireAtNX1).isTrue(); // Should succeed, key had no expiration
            
            Boolean expireAtNX2 = connection.keyCommands().expireAt(key.getBytes(), futureTimestamp + 10, ExpirationOptions.Condition.NX);
            assertThat(expireAtNX2).isFalse(); // Should fail, key already has expiration
            
            // Test expireAt with XX condition (key must have expiration)
            Boolean expireAtXX1 = connection.keyCommands().expireAt(key.getBytes(), futureTimestamp + 20, ExpirationOptions.Condition.XX);
            assertThat(expireAtXX1).isTrue(); // Should succeed, key has expiration
            
            // Remove expiration
            connection.keyCommands().persist(key.getBytes());
            
            Boolean expireAtXX2 = connection.keyCommands().expireAt(key.getBytes(), futureTimestamp + 30, ExpirationOptions.Condition.XX);
            assertThat(expireAtXX2).isFalse(); // Should fail, key has no expiration
            
            // ========== pExpireAt with conditions ==========
            long futureTimestampMillis = System.currentTimeMillis() + 30000;
            
            // Test pExpireAt with NX condition (key must not have expiration)
            Boolean pExpireAtNX1 = connection.keyCommands().pExpireAt(key.getBytes(), futureTimestampMillis, ExpirationOptions.Condition.NX);
            assertThat(pExpireAtNX1).isTrue(); // Should succeed, key had no expiration
            
            Boolean pExpireAtNX2 = connection.keyCommands().pExpireAt(key.getBytes(), futureTimestampMillis + 10000, ExpirationOptions.Condition.NX);
            assertThat(pExpireAtNX2).isFalse(); // Should fail, key already has expiration
            
            // Test pExpireAt with XX condition (key must have expiration)
            Boolean pExpireAtXX1 = connection.keyCommands().pExpireAt(key.getBytes(), futureTimestampMillis + 20000, ExpirationOptions.Condition.XX);
            assertThat(pExpireAtXX1).isTrue(); // Should succeed, key has expiration
            
            // Remove expiration
            connection.keyCommands().persist(key.getBytes());
            
            Boolean pExpireAtXX2 = connection.keyCommands().pExpireAt(key.getBytes(), futureTimestampMillis + 30000, ExpirationOptions.Condition.XX);
            assertThat(pExpireAtXX2).isFalse(); // Should fail, key has no expiration
            
            // ========== Test cross-validation between methods ==========
            // Set expiration with expire, then test with expireAt XX condition
            connection.keyCommands().expire(key.getBytes(), 60); // 60 seconds
            Boolean expireAtXXAfterExpire = connection.keyCommands().expireAt(key.getBytes(), futureTimestamp + 60, ExpirationOptions.Condition.XX);
            assertThat(expireAtXXAfterExpire).isTrue(); // Should succeed, key has expiration from expire()
            
            // Set expiration with pExpire, then test with pExpireAt XX condition
            connection.keyCommands().pExpire(key.getBytes(), 120000); // 120 seconds in milliseconds
            Boolean pExpireAtXXAfterPExpire = connection.keyCommands().pExpireAt(key.getBytes(), futureTimestampMillis + 120000, ExpirationOptions.Condition.XX);
            assertThat(pExpireAtXXAfterPExpire).isTrue(); // Should succeed, key has expiration from pExpire()
            
            // ========== Test conditions on non-existent key ==========
            Boolean expireAtNonExistent = connection.keyCommands().expireAt("non:existent:key".getBytes(), futureTimestamp, ExpirationOptions.Condition.NX);
            assertThat(expireAtNonExistent).isFalse(); // Should fail, key doesn't exist
            
            Boolean pExpireAtNonExistent = connection.keyCommands().pExpireAt("non:existent:key".getBytes(), futureTimestampMillis, ExpirationOptions.Condition.XX);
            assertThat(pExpireAtNonExistent).isFalse(); // Should fail, key doesn't exist
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testExpirationOperationsEdgeCases() {
        String key = "test:key:expiration:edge";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test expiration operations on non-existent key (should return false)
            Boolean expireNonExistent = connection.keyCommands().expire("non:existent:key".getBytes(), 10);
            assertThat(expireNonExistent).isFalse();
            
            // Test with zero expiration (should delete the key immediately)
            connection.stringCommands().set(key.getBytes(), value);
            Boolean expireZero = connection.keyCommands().expire(key.getBytes(), 0);
            assertThat(expireZero).isTrue(); // Should return true since key existed
            Boolean keyExistsAfterZero = connection.keyCommands().exists(key.getBytes());
            assertThat(keyExistsAfterZero).isFalse(); // Key should be deleted
            
            // Test with negative expiration (should delete the key immediately)
            connection.stringCommands().set(key.getBytes(), value);
            Boolean expireNegative = connection.keyCommands().expire(key.getBytes(), -1);
            assertThat(expireNegative).isTrue(); // Should return true since key existed
            Boolean keyExistsAfterNegative = connection.keyCommands().exists(key.getBytes());
            assertThat(keyExistsAfterNegative).isFalse(); // Key should be deleted
        } finally {
            cleanupKey(key);
        }
    }
}
