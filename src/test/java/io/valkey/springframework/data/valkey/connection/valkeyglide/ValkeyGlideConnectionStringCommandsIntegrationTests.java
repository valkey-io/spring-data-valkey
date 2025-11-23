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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.BitFieldSubCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands.BitOperation;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands.SetOption;
import io.valkey.springframework.data.valkey.core.types.Expiration;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * string functionality using the ValkeyStringCommands interface directly.
 * 
 * These tests validate the implementation of all ValkeyStringCommands methods:
 * - Basic string operations (get, set, getSet, etc.)
 * - Multi-key operations (mGet, mSet, mSetNX)
 * - Expiration-related operations (setEx, pSetEx, getEx, getDel)
 * - Increment/decrement operations (incr, incrBy, decr, decrBy)
 * - String manipulation (append, getRange, setRange, strLen)
 * - Bit operations (getBit, setBit, bitCount, bitField, bitOp, bitPos)
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideConnectionStringCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:string:getset", "test:string:getset:old", "new:key",
            "test:string:getdel", "test:string:getex", "test:string:setoptions",
            "test:string:setget", "test:string:setex", "test:string:psetex",
            "test:string:mget:key1", "test:string:mget:key2", "test:string:mget:key3",
            "test:string:msetnx:key1", "test:string:msetnx:key2", "test:string:msetnx:existing",
            "test:string:msetnx:new1", "test:string:msetnx:new2",
            "test:string:incrdecr", "test:string:incrby:int", "test:string:incrby:float",
            "test:string:append", "test:string:range", "test:string:bit", 
            "test:string:bitcount", "test:string:bitpos", 
            "test:string:bitop:key1", "test:string:bitop:key2", "test:string:bitop:dest",
            "test:string:bitfield", "test:string:error:list", "test:string:empty"
        };
    }

    // ==================== Basic String Operations ====================

    @Test
    void testGetSet() {
        String key = "test:string:getset";
        byte[] keyBytes = key.getBytes();
        byte[] value = "test_value".getBytes();
        
        try {
            // Set initial value
            Boolean setResult = connection.stringCommands().set(keyBytes, value);
            assertThat(setResult).isTrue();
            
            // Get the value
            byte[] retrievedValue = connection.stringCommands().get(keyBytes);
            assertThat(retrievedValue).isEqualTo(value);
            
            // Test non-existent key
            byte[] nonExistentValue = connection.stringCommands().get("non:existent:key".getBytes());
            assertThat(nonExistentValue).isNull();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testGetSetWithOldValue() {
        String key = "test:string:getset:old";
        byte[] keyBytes = key.getBytes();
        byte[] initialValue = "initial".getBytes();
        byte[] newValue = "new_value".getBytes();
        
        try {
            // Set initial value
            connection.stringCommands().set(keyBytes, initialValue);
            
            // Get and set new value
            byte[] oldValue = connection.stringCommands().getSet(keyBytes, newValue);
            assertThat(oldValue).isEqualTo(initialValue);
            
            // Verify new value is set
            byte[] currentValue = connection.stringCommands().get(keyBytes);
            assertThat(currentValue).isEqualTo(newValue);
            
            // Test getSet on non-existent key
            byte[] nonExistentOld = connection.stringCommands().getSet("new:key".getBytes(), "value".getBytes());
            assertThat(nonExistentOld).isNull();
        } finally {
            cleanupKey(key);
            cleanupKey("new:key");
        }
    }

    @Test
    void testGetDelAndGetEx() {
        String key1 = "test:string:getdel";
        String key2 = "test:string:getex";
        byte[] value = "test_value".getBytes();
        
        try {
            // Test getDel
            connection.stringCommands().set(key1.getBytes(), value);
            byte[] retrievedValue = connection.stringCommands().getDel(key1.getBytes());
            assertThat(retrievedValue).isEqualTo(value);
            
            // Verify key is deleted
            byte[] deletedValue = connection.stringCommands().get(key1.getBytes());
            assertThat(deletedValue).isNull();
            
            // Test getEx with expiration
            connection.stringCommands().set(key2.getBytes(), value);
            byte[] exValue = connection.stringCommands().getEx(key2.getBytes(), Expiration.seconds(1));
            assertThat(exValue).isEqualTo(value);
            
            // Wait for expiration and verify
            Thread.sleep(1100);
            byte[] expiredValue = connection.stringCommands().get(key2.getBytes());
            assertThat(expiredValue).isNull();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testGetExPersistent() {
        String key = "test:string:getex:persistent";
        byte[] value = "persistent_value".getBytes();
        
        try {
            // Set key with expiration first
            connection.stringCommands().set(key.getBytes(), value, Expiration.seconds(60), SetOption.upsert());
            
            // Verify key has expiration
            Long ttlBefore = connection.keyCommands().ttl(key.getBytes());
            assertThat(ttlBefore).isGreaterThan(0);
            
            // Test getEx with persistent expiration (should remove expiration)
            byte[] retrievedValue = connection.stringCommands().getEx(key.getBytes(), Expiration.persistent());
            assertThat(retrievedValue).isEqualTo(value);
            
            // Verify key no longer has expiration
            Long ttlAfter = connection.keyCommands().ttl(key.getBytes());
            assertThat(ttlAfter).isEqualTo(-1L); // -1 means no expiration
            
            // Verify value is still accessible
            byte[] finalValue = connection.stringCommands().get(key.getBytes());
            assertThat(finalValue).isEqualTo(value);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testGetExKeepTtl() {
        String key = "test:string:getex:keepttl";
        byte[] value = "keepttl_value".getBytes();
        
        try {
            // Set key with expiration first
            connection.stringCommands().set(key.getBytes(), value, Expiration.seconds(60), SetOption.upsert());
            
            // Get initial TTL
            Long ttlBefore = connection.keyCommands().ttl(key.getBytes());
            assertThat(ttlBefore).isGreaterThan(0);
            
            // Test getEx with keepTtl expiration (should preserve existing expiration)
            byte[] retrievedValue = connection.stringCommands().getEx(key.getBytes(), Expiration.keepTtl());
            assertThat(retrievedValue).isEqualTo(value);
            
            // Verify key still has similar expiration
            Long ttlAfter = connection.keyCommands().ttl(key.getBytes());
            assertThat(ttlAfter).isGreaterThan(0);
            assertThat(ttlAfter).isLessThanOrEqualTo(ttlBefore); // Should be same or slightly less due to time passage
            
            // Verify value is still accessible
            byte[] finalValue = connection.stringCommands().get(key.getBytes());
            assertThat(finalValue).isEqualTo(value);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSetWithOptions() {
        String key = "test:string:setoptions";
        byte[] keyBytes = key.getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        
        try {
            // Test setNX (set if not exists)
            Boolean setNXResult1 = connection.stringCommands().setNX(keyBytes, value1);
            assertThat(setNXResult1).isTrue();
            
            // Try setNX again - should fail
            Boolean setNXResult2 = connection.stringCommands().setNX(keyBytes, value2);
            assertThat(setNXResult2).isFalse();
            
            // Verify original value remains
            assertThat(connection.stringCommands().get(keyBytes)).isEqualTo(value1);
            
            // Test set with SetOption.IF_PRESENT
            Boolean setIfPresent = connection.stringCommands().set(keyBytes, value2, 
                Expiration.persistent(), SetOption.ifPresent());
            assertThat(setIfPresent).isTrue();
            assertThat(connection.stringCommands().get(keyBytes)).isEqualTo(value2);
            
            // Test set with SetOption.IF_ABSENT on existing key
            Boolean setIfAbsent = connection.stringCommands().set(keyBytes, value1, 
                Expiration.persistent(), SetOption.ifAbsent());
            assertThat(setIfAbsent).isFalse();
            assertThat(connection.stringCommands().get(keyBytes)).isEqualTo(value2);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSetGet() {
        String key = "test:string:setget";
        byte[] keyBytes = key.getBytes();
        byte[] value1 = "initial".getBytes();
        byte[] value2 = "updated".getBytes();
        
        try {
            // Test setGet on non-existing key
            byte[] oldValue1 = connection.stringCommands().setGet(keyBytes, value1, 
                Expiration.persistent(), SetOption.upsert());
            assertThat(oldValue1).isNull();
            assertThat(connection.stringCommands().get(keyBytes)).isEqualTo(value1);
            
            // Test setGet on existing key
            byte[] oldValue2 = connection.stringCommands().setGet(keyBytes, value2, 
                Expiration.persistent(), SetOption.upsert());
            assertThat(oldValue2).isEqualTo(value1);
            assertThat(connection.stringCommands().get(keyBytes)).isEqualTo(value2);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSetWithExpiration() {
        String key1 = "test:string:setex";
        String key2 = "test:string:psetex";
        byte[] value = "expiring_value".getBytes();
        
        try {
            // Test setEx (seconds)
            Boolean setExResult = connection.stringCommands().setEx(key1.getBytes(), 1, value);
            assertThat(setExResult).isTrue();
            assertThat(connection.stringCommands().get(key1.getBytes())).isEqualTo(value);
            
            // Test pSetEx (milliseconds)
            Boolean pSetExResult = connection.stringCommands().pSetEx(key2.getBytes(), 1000, value);
            assertThat(pSetExResult).isTrue();
            assertThat(connection.stringCommands().get(key2.getBytes())).isEqualTo(value);
            
            // Wait for expiration
            Thread.sleep(1100);
            assertThat(connection.stringCommands().get(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().get(key2.getBytes())).isNull();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    // ==================== Multi-Key Operations ====================

    @Test
    void testMGetMSet() {
        String key1 = "test:string:mget:key1";
        String key2 = "test:string:mget:key2";
        String key3 = "test:string:mget:key3";
        
        try {
            // Set up test data
            Map<byte[], byte[]> keyValues = new HashMap<>();
            keyValues.put(key1.getBytes(), "value1".getBytes());
            keyValues.put(key2.getBytes(), "value2".getBytes());
            keyValues.put(key3.getBytes(), "value3".getBytes());
            
            // Test mSet
            Boolean mSetResult = connection.stringCommands().mSet(keyValues);
            assertThat(mSetResult).isTrue();
            
            // Test mGet
            List<byte[]> values = connection.stringCommands().mGet(
                key1.getBytes(), key2.getBytes(), key3.getBytes(), "non:existent".getBytes());
            
            assertThat(values).hasSize(4);
            assertThat(values.get(0)).isEqualTo("value1".getBytes());
            assertThat(values.get(1)).isEqualTo("value2".getBytes());
            assertThat(values.get(2)).isEqualTo("value3".getBytes());
            assertThat(values.get(3)).isNull(); // non-existent key
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testMSetNX() {
        String key1 = "test:string:msetnx:key1";
        String key2 = "test:string:msetnx:key2";
        String key3 = "test:string:msetnx:existing";
        String key4 = "test:string:msetnx:new1";
        String key5 = "test:string:msetnx:new2";
        
        try {
            // Set one key first
            connection.stringCommands().set(key3.getBytes(), "existing_value".getBytes());
            
            // Try mSetNX with mix of new and existing keys
            Map<byte[], byte[]> keyValues = new HashMap<>();
            keyValues.put(key1.getBytes(), "value1".getBytes());
            keyValues.put(key2.getBytes(), "value2".getBytes());
            keyValues.put(key3.getBytes(), "new_value".getBytes()); // existing key
            
            Boolean mSetNXResult = connection.stringCommands().mSetNX(keyValues);
            assertThat(mSetNXResult).isFalse(); // Should fail due to existing key
            
            // Verify none of the keys were set
            assertThat(connection.stringCommands().get(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().get(key2.getBytes())).isNull();
            assertThat(connection.stringCommands().get(key3.getBytes())).isEqualTo("existing_value".getBytes());
            
            // Test successful mSetNX with all new keys (using different keys)
            Map<byte[], byte[]> newKeyValues = new HashMap<>();
            newKeyValues.put(key4.getBytes(), "newvalue1".getBytes());
            newKeyValues.put(key5.getBytes(), "newvalue2".getBytes());
            Boolean successfulMSetNX = connection.stringCommands().mSetNX(newKeyValues);
            assertThat(successfulMSetNX).isTrue();
            
            assertThat(connection.stringCommands().get(key4.getBytes())).isEqualTo("newvalue1".getBytes());
            assertThat(connection.stringCommands().get(key5.getBytes())).isEqualTo("newvalue2".getBytes());
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(key4);
            cleanupKey(key5);
        }
    }

    // ==================== Increment/Decrement Operations ====================

    @Test
    void testIncrDecr() {
        String key = "test:string:incrdecr";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test incr on non-existent key (should start from 0)
            Long incrResult1 = connection.stringCommands().incr(keyBytes);
            assertThat(incrResult1).isEqualTo(1L);
            
            // Test multiple increments
            Long incrResult2 = connection.stringCommands().incr(keyBytes);
            assertThat(incrResult2).isEqualTo(2L);
            
            // Test decr
            Long decrResult1 = connection.stringCommands().decr(keyBytes);
            assertThat(decrResult1).isEqualTo(1L);
            
            // Test decr to negative
            Long decrResult2 = connection.stringCommands().decr(keyBytes);
            Long decrResult3 = connection.stringCommands().decr(keyBytes);
            assertThat(decrResult2).isEqualTo(0L);
            assertThat(decrResult3).isEqualTo(-1L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testIncrByDecrBy() {
        String intKey = "test:string:incrby:int";
        String floatKey = "test:string:incrby:float";
        
        try {
            // Test incrBy with integers
            Long incrByResult1 = connection.stringCommands().incrBy(intKey.getBytes(), 5L);
            assertThat(incrByResult1).isEqualTo(5L);
            
            Long incrByResult2 = connection.stringCommands().incrBy(intKey.getBytes(), 10L);
            assertThat(incrByResult2).isEqualTo(15L);
            
            // Test decrBy
            Long decrByResult = connection.stringCommands().decrBy(intKey.getBytes(), 7L);
            assertThat(decrByResult).isEqualTo(8L);
            
            // Test incrBy with floats
            Double floatIncrResult1 = connection.stringCommands().incrBy(floatKey.getBytes(), 3.14);
            assertThat(floatIncrResult1).isEqualTo(3.14);
            
            Double floatIncrResult2 = connection.stringCommands().incrBy(floatKey.getBytes(), 2.86);
            assertThat(floatIncrResult2).isEqualTo(6.0);
            
            // Test negative increment (effectively decrement)
            Double floatDecrResult = connection.stringCommands().incrBy(floatKey.getBytes(), -1.5);
            assertThat(floatDecrResult).isEqualTo(4.5);
        } finally {
            cleanupKey(intKey);
            cleanupKey(floatKey);
        }
    }

    // ==================== String Manipulation ====================

    @Test
    void testAppendAndStrLen() {
        String key = "test:string:append";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test append to non-existent key
            Long appendResult1 = connection.stringCommands().append(keyBytes, "Hello".getBytes());
            assertThat(appendResult1).isEqualTo(5L); // Length of "Hello"
            
            // Test string length
            Long strLenResult1 = connection.stringCommands().strLen(keyBytes);
            assertThat(strLenResult1).isEqualTo(5L);
            
            // Test append to existing key
            Long appendResult2 = connection.stringCommands().append(keyBytes, " World".getBytes());
            assertThat(appendResult2).isEqualTo(11L); // Length of "Hello World"
            
            // Verify final value
            byte[] finalValue = connection.stringCommands().get(keyBytes);
            assertThat(finalValue).isEqualTo("Hello World".getBytes());
            
            Long strLenResult2 = connection.stringCommands().strLen(keyBytes);
            assertThat(strLenResult2).isEqualTo(11L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testGetRangeSetRange() {
        String key = "test:string:range";
        byte[] keyBytes = key.getBytes();
        String originalValue = "Hello World";
        
        try {
            // Set initial value
            connection.stringCommands().set(keyBytes, originalValue.getBytes());
            
            // Test getRange
            byte[] range1 = connection.stringCommands().getRange(keyBytes, 0, 4);
            assertThat(range1).isEqualTo("Hello".getBytes());
            
            byte[] range2 = connection.stringCommands().getRange(keyBytes, 6, -1);
            assertThat(range2).isEqualTo("World".getBytes());
            
            byte[] range3 = connection.stringCommands().getRange(keyBytes, -5, -1);
            assertThat(range3).isEqualTo("World".getBytes());
            
            // Test setRange
            connection.stringCommands().setRange(keyBytes, "Valkey".getBytes(), 6);
            byte[] modifiedValue = connection.stringCommands().get(keyBytes);
            assertThat(modifiedValue).isEqualTo("Hello Valkey".getBytes());
            
            // Test setRange beyond string length (should pad with zeros)
            connection.stringCommands().setRange(keyBytes, "!".getBytes(), 20);
            byte[] paddedValue = connection.stringCommands().get(keyBytes);
            assertThat(paddedValue).hasSize(21);
            assertThat(paddedValue[20]).isEqualTo((byte) '!');
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Bit Operations ====================

    @Test
    void testGetBitSetBit() {
        String key = "test:string:bit";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test getBit on non-existent key
            Boolean bit1 = connection.stringCommands().getBit(keyBytes, 0);
            assertThat(bit1).isFalse(); // Non-existent keys return 0
            
            // Test setBit
            Boolean oldBit1 = connection.stringCommands().setBit(keyBytes, 7, true);
            assertThat(oldBit1).isFalse(); // Previous value was 0
            
            // Test getBit after setBit
            Boolean bit2 = connection.stringCommands().getBit(keyBytes, 7);
            assertThat(bit2).isTrue();
            
            // Test setting bit to false
            Boolean oldBit2 = connection.stringCommands().setBit(keyBytes, 7, false);
            assertThat(oldBit2).isTrue(); // Previous value was 1
            
            Boolean bit3 = connection.stringCommands().getBit(keyBytes, 7);
            assertThat(bit3).isFalse();
            
            // Set multiple bits
            connection.stringCommands().setBit(keyBytes, 0, true);
            connection.stringCommands().setBit(keyBytes, 4, true);
            connection.stringCommands().setBit(keyBytes, 8, true);
            
            // Verify individual bits
            assertThat(connection.stringCommands().getBit(keyBytes, 0)).isTrue();
            assertThat(connection.stringCommands().getBit(keyBytes, 4)).isTrue();
            assertThat(connection.stringCommands().getBit(keyBytes, 8)).isTrue();
            assertThat(connection.stringCommands().getBit(keyBytes, 1)).isFalse();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBitCount() {
        String key = "test:string:bitcount";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Set some bits
            connection.stringCommands().setBit(keyBytes, 0, true);
            connection.stringCommands().setBit(keyBytes, 4, true);
            connection.stringCommands().setBit(keyBytes, 8, true);
            connection.stringCommands().setBit(keyBytes, 16, true);
            
            // Test bitCount for entire string
            Long bitCount1 = connection.stringCommands().bitCount(keyBytes);
            assertThat(bitCount1).isEqualTo(4L);
            
            // Test bitCount with range (first byte only)
            Long bitCount2 = connection.stringCommands().bitCount(keyBytes, 0, 0);
            assertThat(bitCount2).isEqualTo(2L); // bits 0 and 4 are in first byte
            
            // Test bitCount with range (second byte)
            Long bitCount3 = connection.stringCommands().bitCount(keyBytes, 1, 1);
            assertThat(bitCount3).isEqualTo(1L); // bit 8 is in second byte
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBitPos() {
        String key = "test:string:bitpos";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Set bits: 11110000 (0xF0)
            connection.stringCommands().set(keyBytes, new byte[]{(byte) 0xF0});
            
            // Find first 1 bit
            Long pos1 = connection.stringCommands().bitPos(keyBytes, true);
            assertThat(pos1).isEqualTo(0L);
            
            // Find first 0 bit
            Long pos0 = connection.stringCommands().bitPos(keyBytes, false);
            assertThat(pos0).isEqualTo(4L);
            
            // Test with range
            Long pos1Range = connection.stringCommands().bitPos(keyBytes, true, Range.closed(0L, 0L));
            assertThat(pos1Range).isEqualTo(0L);
            
            Long pos0Range = connection.stringCommands().bitPos(keyBytes, false, Range.closed(0L, 0L));
            assertThat(pos0Range).isEqualTo(4L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBitOp() {
        String key1 = "test:string:bitop:key1";
        String key2 = "test:string:bitop:key2";
        String destKey = "test:string:bitop:dest";
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), new byte[]{(byte) 0xF0}); // 11110000
            connection.stringCommands().set(key2.getBytes(), new byte[]{(byte) 0x0F}); // 00001111
            
            // Test AND operation
            Long andResult = connection.stringCommands().bitOp(BitOperation.AND, destKey.getBytes(), 
                key1.getBytes(), key2.getBytes());
            assertThat(andResult).isEqualTo(1L); // 1 byte processed
            
            byte[] andValue = connection.stringCommands().get(destKey.getBytes());
            assertThat(andValue[0]).isEqualTo((byte) 0x00); // 11110000 AND 00001111 = 00000000
            
            // Test OR operation  
            Long orResult = connection.stringCommands().bitOp(BitOperation.OR, destKey.getBytes(), 
                key1.getBytes(), key2.getBytes());
            assertThat(orResult).isEqualTo(1L);
            
            byte[] orValue = connection.stringCommands().get(destKey.getBytes());
            assertThat(orValue[0]).isEqualTo((byte) 0xFF); // 11110000 OR 00001111 = 11111111
            
            // Test XOR operation
            Long xorResult = connection.stringCommands().bitOp(BitOperation.XOR, destKey.getBytes(), 
                key1.getBytes(), key2.getBytes());
            assertThat(xorResult).isEqualTo(1L);
            
            byte[] xorValue = connection.stringCommands().get(destKey.getBytes());
            assertThat(xorValue[0]).isEqualTo((byte) 0xFF); // 11110000 XOR 00001111 = 11111111
            
            // Test NOT operation (single key)
            Long notResult = connection.stringCommands().bitOp(BitOperation.NOT, destKey.getBytes(), 
                key1.getBytes());
            assertThat(notResult).isEqualTo(1L);
            
            byte[] notValue = connection.stringCommands().get(destKey.getBytes());
            assertThat(notValue[0]).isEqualTo((byte) 0x0F); // NOT 11110000 = 00001111
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testBitField() {
        String key = "test:string:bitfield";
        byte[] keyBytes = key.getBytes();
        
        try {
            BitFieldSubCommands subCommands = BitFieldSubCommands.create()
                .set(BitFieldSubCommands.BitFieldType.unsigned(8)).valueAt(0).to(255)
                .get(BitFieldSubCommands.BitFieldType.unsigned(8)).valueAt(0);
            
            // Test that bitField executes without throwing exception
            // Note: ValkeyGlide implementation may return Object[] instead of List<Long>
            List<Long> results = connection.stringCommands().bitField(keyBytes, subCommands);
            
            // Basic verification that some result is returned
            assertThat(results).isNotNull();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBitFieldWithOverflowWrap() {
        String key = "test:string:bitfield:wrap";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test OVERFLOW WRAP behavior (default)
            // Increment u2 (max value 3) from 0 to 3, then wrap to 0
            BitFieldSubCommands subCommands = BitFieldSubCommands.create()
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(100).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.WRAP).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(100).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.WRAP).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(100).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.WRAP).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(100).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.WRAP).by(1);
            
            List<Long> results = connection.stringCommands().bitField(keyBytes, subCommands);
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            assertThat(results.get(0)).isEqualTo(1L); // 0 + 1 = 1
            assertThat(results.get(1)).isEqualTo(2L); // 1 + 1 = 2
            assertThat(results.get(2)).isEqualTo(3L); // 2 + 1 = 3 (max value)
            assertThat(results.get(3)).isEqualTo(0L); // 3 + 1 wraps to 0
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBitFieldWithOverflowSat() {
        String key = "test:string:bitfield:sat";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test OVERFLOW SAT behavior (saturate at max/min)
            // Increment u2 (max value 3) from 0 to 3, then saturate at 3
            BitFieldSubCommands subCommands = BitFieldSubCommands.create()
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(102).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.SAT).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(102).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.SAT).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(102).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.SAT).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(102).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.SAT).by(1);
            
            List<Long> results = connection.stringCommands().bitField(keyBytes, subCommands);
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            assertThat(results.get(0)).isEqualTo(1L); // 0 + 1 = 1
            assertThat(results.get(1)).isEqualTo(2L); // 1 + 1 = 2
            assertThat(results.get(2)).isEqualTo(3L); // 2 + 1 = 3 (max value)
            assertThat(results.get(3)).isEqualTo(3L); // 3 + 1 saturates at 3
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBitFieldWithOverflowFail() {
        String key = "test:string:bitfield:fail";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test OVERFLOW FAIL behavior (return null on overflow)
            // Increment u2 (max value 3) from 0 to 3, then fail and return null
            BitFieldSubCommands subCommands = BitFieldSubCommands.create()
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(102).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.FAIL).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(102).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.FAIL).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(102).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.FAIL).by(1)
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(102).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.FAIL).by(1);
            
            List<Long> results = connection.stringCommands().bitField(keyBytes, subCommands);
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            assertThat(results.get(0)).isEqualTo(1L); // 0 + 1 = 1
            assertThat(results.get(1)).isEqualTo(2L); // 1 + 1 = 2
            assertThat(results.get(2)).isEqualTo(3L); // 2 + 1 = 3 (max value)
            assertThat(results.get(3)).isNull(); // 3 + 1 fails and returns null
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBitFieldMixedOverflowModes() {
        String key = "test:string:bitfield:mixed";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test multiple overflow modes in single command
            // Each OVERFLOW command affects subsequent operations until next OVERFLOW
            BitFieldSubCommands subCommands = BitFieldSubCommands.create()
                // Set initial values
                .set(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(100).to(3)
                .set(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(104).to(3)
                .set(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(108).to(3)
                // Test WRAP overflow
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(100).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.WRAP).by(1)
                // Test SAT overflow
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(104).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.SAT).by(1)
                // Test FAIL overflow
                .incr(BitFieldSubCommands.BitFieldType.unsigned(2)).valueAt(108).overflow(BitFieldSubCommands.BitFieldIncrBy.Overflow.FAIL).by(1);
            
            List<Long> results = connection.stringCommands().bitField(keyBytes, subCommands);
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(0L); // SET returns old value (0)
            assertThat(results.get(1)).isEqualTo(0L); // SET returns old value (0)
            assertThat(results.get(2)).isEqualTo(0L); // SET returns old value (0)
            assertThat(results.get(3)).isEqualTo(0L); // WRAP: 3 + 1 = 0
            assertThat(results.get(4)).isEqualTo(3L); // SAT: 3 + 1 = 3 (saturated)
            assertThat(results.get(5)).isNull(); // FAIL: 3 + 1 = null (overflow detected)
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBitFieldWithNonZeroBasedOffset() {
        String key = "test:string:bitfield:nonzero";
        byte[] keyBytes = key.getBytes();
        
        try {
            // Test type-based (non-zero-based) offsets using multipliedByTypeLength()
            // Type-based offsets are prefixed with "#" in Redis and multiplied by the type width
            // For INT_8 (8 bits = 1 byte), offset #1 means byte offset 1
            BitFieldSubCommands subCommands = BitFieldSubCommands.create()
                .set(BitFieldSubCommands.BitFieldType.signed(8))
                    .valueAt(BitFieldSubCommands.Offset.offset(0L).multipliedByTypeLength()).to(100L)
                .set(BitFieldSubCommands.BitFieldType.signed(8))
                    .valueAt(BitFieldSubCommands.Offset.offset(1L).multipliedByTypeLength()).to(200L);
            
            List<Long> setResults = connection.stringCommands().bitField(keyBytes, subCommands);
            
            assertThat(setResults).isNotNull();
            assertThat(setResults).hasSize(2);
            assertThat(setResults).containsExactly(0L, 0L); // Both SET operations return old value (0)
            
            // Now get the values back using type-based offsets
            BitFieldSubCommands getCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.signed(8))
                    .valueAt(BitFieldSubCommands.Offset.offset(0L).multipliedByTypeLength())
                .get(BitFieldSubCommands.BitFieldType.signed(8))
                    .valueAt(BitFieldSubCommands.Offset.offset(1L).multipliedByTypeLength());
            
            List<Long> getResults = connection.stringCommands().bitField(keyBytes, getCommands);
            
            assertThat(getResults).isNotNull();
            assertThat(getResults).hasSize(2);
            assertThat(getResults.get(0)).isEqualTo(100L);
            assertThat(getResults.get(1)).isEqualTo(-56L); // 200 as signed i8 wraps to -56
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Error Handling and Edge Cases ====================

    @Test
    void testStringOperationsOnNonStringTypes() {
        String listKey = "test:string:error:list";
        
        try {
            // Create a list
            connection.listCommands().lPush(listKey.getBytes(), "item".getBytes());
            
            // Try string operations on list key - should fail or return appropriate response
            assertThatThrownBy(() -> connection.stringCommands().incr(listKey.getBytes()))
                .isInstanceOf(DataAccessException.class);
        } finally {
            cleanupKey(listKey);
        }
    }

    @Test
    void testEmptyStringOperations() {
        String key = "test:string:empty";
        byte[] keyBytes = key.getBytes();
        byte[] emptyValue = new byte[0];
        
        try {
            // Set empty string
            Boolean setResult = connection.stringCommands().set(keyBytes, emptyValue);
            assertThat(setResult).isTrue();
            
            // Get empty string
            byte[] retrievedValue = connection.stringCommands().get(keyBytes);
            assertThat(retrievedValue).isEqualTo(emptyValue);
            
            // String length should be 0
            Long strLen = connection.stringCommands().strLen(keyBytes);
            assertThat(strLen).isEqualTo(0L);
            
            // Append to empty string
            Long appendResult = connection.stringCommands().append(keyBytes, "appended".getBytes());
            assertThat(appendResult).isEqualTo(8L); // "appended" is 8 characters
            
            byte[] finalValue = connection.stringCommands().get(keyBytes);
            assertThat(finalValue).isEqualTo("appended".getBytes());
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Pipeline Mode Tests ====================

    @Test
    void testBasicStringOperationsInPipelineMode() {
        String key1 = "test:pipeline:basic1";
        String key2 = "test:pipeline:basic2";
        String key3 = "test:pipeline:basic3";
        String key4 = "test:pipeline:basic4";
        
        try {
            // Open pipeline
            connection.openPipeline();
            
            // Queue basic string operations - assert they return null in pipeline mode
            assertThat(connection.stringCommands().set(key1.getBytes(), "value1".getBytes())).isNull();
            assertThat(connection.stringCommands().setNX(key2.getBytes(), "value2".getBytes())).isNull();
            assertThat(connection.stringCommands().get(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().getSet(key1.getBytes(), "newvalue1".getBytes())).isNull();
            assertThat(connection.stringCommands().getDel(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().setEx(key3.getBytes(), 60, "expiring".getBytes())).isNull();
            assertThat(connection.stringCommands().pSetEx(key4.getBytes(), 60000, "expiring_ms".getBytes())).isNull();
            
            // Execute pipeline
            java.util.List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(7);
            assertThat(results.get(0)).isEqualTo(true); // SET result
            assertThat(results.get(1)).isEqualTo(true); // SETNX result
            assertThat(results.get(2)).isEqualTo("value1".getBytes()); // GET result
            assertThat(results.get(3)).isEqualTo("value1".getBytes()); // GETSET result
            assertThat(results.get(4)).isEqualTo("newvalue1".getBytes()); // GETDEL result
            assertThat(results.get(5)).isEqualTo(true); // SETEX result
            assertThat(results.get(6)).isEqualTo(true); // PSETEX result
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(key4);
        }
    }

    @Test
    void testMultiKeyOperationsInPipelineMode() {
        String key1 = "test:pipeline:mkey1";
        String key2 = "test:pipeline:mkey2";
        String key3 = "test:pipeline:mkey3";
        String key4 = "test:pipeline:mkey4";
        String key5 = "test:pipeline:mkey5";
        
        try {
            // Set up initial data
            connection.stringCommands().set(key1.getBytes(), "initial1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "initial2".getBytes());
            
            connection.openPipeline();
            
            // Queue multi-key operations - assert they return null in pipeline mode
            assertThat(connection.stringCommands().mGet(key1.getBytes(), key2.getBytes(), key3.getBytes())).isNull();
            
            Map<byte[], byte[]> msetData = new HashMap<>();
            msetData.put(key4.getBytes(), "msetvalue1".getBytes());
            msetData.put(key5.getBytes(), "msetvalue2".getBytes());
            assertThat(connection.stringCommands().mSet(msetData)).isNull();
            
            Map<byte[], byte[]> msetnxData = new HashMap<>();
            msetnxData.put("new1".getBytes(), "msetnxvalue1".getBytes());
            msetnxData.put("new2".getBytes(), "msetnxvalue2".getBytes());
            assertThat(connection.stringCommands().mSetNX(msetnxData)).isNull();
            
            assertThat(connection.stringCommands().mGet(key4.getBytes(), key5.getBytes())).isNull();
            
            java.util.List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(4);
            
            // mGet result
            @SuppressWarnings("unchecked")
            java.util.List<byte[]> mgetResult1 = (java.util.List<byte[]>) results.get(0);
            assertThat(mgetResult1).hasSize(3);
            assertThat(mgetResult1.get(0)).isEqualTo("initial1".getBytes());
            assertThat(mgetResult1.get(1)).isEqualTo("initial2".getBytes());
            assertThat(mgetResult1.get(2)).isNull(); // key3 doesn't exist
            
            assertThat(results.get(1)).isEqualTo(true); // MSET result
            assertThat(results.get(2)).isEqualTo(true); // MSETNX result
            
            // Second mGet result
            @SuppressWarnings("unchecked")
            java.util.List<byte[]> mgetResult2 = (java.util.List<byte[]>) results.get(3);
            assertThat(mgetResult2).hasSize(2);
            assertThat(mgetResult2.get(0)).isEqualTo("msetvalue1".getBytes());
            assertThat(mgetResult2.get(1)).isEqualTo("msetvalue2".getBytes());
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(key4);
            cleanupKey(key5);
            cleanupKey("new1");
            cleanupKey("new2");
        }
    }

    @Test
    void testArithmeticOperationsInPipelineMode() {
        String intKey = "test:pipeline:int";
        String floatKey = "test:pipeline:float";
        String decrKey = "test:pipeline:decr";
        
        try {
            connection.openPipeline();
            
            // Queue arithmetic operations - assert they return null in pipeline mode
            assertThat(connection.stringCommands().incr(intKey.getBytes())).isNull();
            assertThat(connection.stringCommands().incrBy(intKey.getBytes(), 5L)).isNull();
            assertThat(connection.stringCommands().incrBy(floatKey.getBytes(), 3.14)).isNull();
            assertThat(connection.stringCommands().incrBy(floatKey.getBytes(), -1.14)).isNull();
            assertThat(connection.stringCommands().decr(decrKey.getBytes())).isNull();
            assertThat(connection.stringCommands().decrBy(decrKey.getBytes(), 3L)).isNull();
            
            java.util.List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(1L); // INCR result
            assertThat(results.get(1)).isEqualTo(6L); // INCRBY result
            assertThat(results.get(2)).isEqualTo(3.14); // INCRBYFLOAT result
            assertThat(results.get(3)).isEqualTo(2.0); // INCRBYFLOAT result
            assertThat(results.get(4)).isEqualTo(-1L); // DECR result
            assertThat(results.get(5)).isEqualTo(-4L); // DECRBY result
            
        } finally {
            cleanupKey(intKey);
            cleanupKey(floatKey);
            cleanupKey(decrKey);
        }
    }

    @Test
    void testStringManipulationInPipelineMode() {
        String key1 = "test:pipeline:manip1";
        String key2 = "test:pipeline:manip2";
        
        try {
            // Set up initial data
            connection.stringCommands().set(key1.getBytes(), "Hello".getBytes());
            connection.stringCommands().set(key2.getBytes(), "Valkey World".getBytes());
            
            connection.openPipeline();
            
            // Queue string manipulation operations - assert they return null in pipeline mode
            assertThat(connection.stringCommands().append(key1.getBytes(), " World".getBytes())).isNull();
            assertThat(connection.stringCommands().strLen(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().getRange(key2.getBytes(), 0, 5)).isNull();
            connection.stringCommands().setRange(key2.getBytes(), "Valkey".getBytes(), 7); // void method
            assertThat(connection.stringCommands().get(key2.getBytes())).isNull();
            
            java.util.List<Object> results = connection.closePipeline();
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(5);
            assertThat(results.get(0)).isEqualTo(11L); // APPEND result (length)
            assertThat(results.get(1)).isEqualTo(11L); // STRLEN result
            assertThat(results.get(2)).isEqualTo("Valkey".getBytes()); // GETRANGE result
            assertThat(results.get(3)).isEqualTo(13L); // SETRANGE result (new length)
            assertThat(results.get(4)).isEqualTo("Valkey Valkey".getBytes()); // GET result after setrange
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testBitOperationsInPipelineMode() {
        String key1 = "test:pipeline:bit1";
        String key2 = "test:pipeline:bit2";
        String destKey = "test:pipeline:bitop";
        
        try {
            // Set up initial data
            connection.stringCommands().set(key1.getBytes(), new byte[]{(byte) 0xF0}); // 11110000
            connection.stringCommands().set(key2.getBytes(), new byte[]{(byte) 0x0F}); // 00001111
            
            connection.openPipeline();
            
            // Queue bit operations - assert they return null in pipeline mode
            assertThat(connection.stringCommands().getBit(key1.getBytes(), 0)).isNull();
            assertThat(connection.stringCommands().setBit(key1.getBytes(), 4, false)).isNull();
            assertThat(connection.stringCommands().bitCount(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().bitCount(key2.getBytes(), 0, 0)).isNull();
            assertThat(connection.stringCommands().bitOp(BitOperation.AND, destKey.getBytes(), key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.stringCommands().bitPos(key1.getBytes(), true)).isNull();
            
            java.util.List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(true); // GETBIT result
            assertThat(results.get(1)).isEqualTo(false); // SETBIT result (old value was 0)
            assertThat(results.get(2)).isEqualTo(4L); // 4 bits still set
            assertThat(results.get(3)).isEqualTo(4L); // BITCOUNT result with range
            assertThat(results.get(4)).isEqualTo(1L); // BITOP result (1 byte processed)
            assertThat(results.get(5)).isEqualTo(0L); // BITPOS result
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testAdvancedStringOperationsInPipelineMode() {
        String key1 = "test:pipeline:advanced1";
        String key2 = "test:pipeline:advanced2";
        
        try {
            connection.openPipeline();
            
            // Queue advanced operations - assert they return null in pipeline mode
            assertThat(connection.stringCommands().set(key1.getBytes(), "test".getBytes(),
                Expiration.seconds(60), SetOption.ifAbsent())).isNull();
            assertThat(connection.stringCommands().setGet(key2.getBytes(), "newvalue".getBytes(),
                Expiration.persistent(), SetOption.upsert())).isNull();
            
            // BitField operations
            BitFieldSubCommands subCommands = BitFieldSubCommands.create()
                .set(BitFieldSubCommands.BitFieldType.unsigned(8)).valueAt(0).to(255)
                .get(BitFieldSubCommands.BitFieldType.unsigned(8)).valueAt(0);
            assertThat(connection.stringCommands().bitField(key1.getBytes(), subCommands)).isNull();
            
            java.util.List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo(true); // SET with options result
            assertThat(results.get(1)).isNull(); // SETGET result (no previous value)
            
            // BitField results
            @SuppressWarnings("unchecked")
            java.util.List<Long> bitFieldResults = (java.util.List<Long>) results.get(2);
            assertThat(bitFieldResults).isNotNull();
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    // ==================== Transaction Mode Tests ====================

    @Test
    void testBasicStringOperationsInTransactionMode() {
        String key1 = "test:tx:basic1";
        String key2 = "test:tx:basic2";
        String key3 = "test:tx:basic3";
        String key4 = "test:tx:basic4";
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue basic string operations - assert they return null in transaction mode
            assertThat(connection.stringCommands().set(key1.getBytes(), "txvalue1".getBytes())).isNull();
            assertThat(connection.stringCommands().setNX(key2.getBytes(), "txvalue2".getBytes())).isNull();
            assertThat(connection.stringCommands().get(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().getSet(key1.getBytes(), "newtxvalue1".getBytes())).isNull();
            assertThat(connection.stringCommands().getDel(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().setEx(key3.getBytes(), 60, "expiring".getBytes())).isNull();
            assertThat(connection.stringCommands().pSetEx(key4.getBytes(), 60000, "expiring_ms".getBytes())).isNull();
            
            // Execute transaction
            java.util.List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(7);
            assertThat(results.get(0)).isEqualTo(true); // SET result
            assertThat(results.get(1)).isEqualTo(true); // SETNX result
            assertThat(results.get(2)).isEqualTo("txvalue1".getBytes()); // GET result
            assertThat(results.get(3)).isEqualTo("txvalue1".getBytes()); // GETSET result
            assertThat(results.get(4)).isEqualTo("newtxvalue1".getBytes()); // GETDEL result
            assertThat(results.get(5)).isEqualTo(true); // SETEX result
            assertThat(results.get(6)).isEqualTo(true); // PSETEX result
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(key4);
        }
    }

    @Test
    void testMultiKeyOperationsInTransactionMode() {
        String key1 = "test:tx:mkey1";
        String key2 = "test:tx:mkey2";
        String key3 = "test:tx:mkey3";
        String key4 = "test:tx:mkey4";
        String key5 = "test:tx:mkey5";
        
        try {
            // Set up initial data
            connection.stringCommands().set(key1.getBytes(), "txinitial1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "txinitial2".getBytes());
            
            connection.multi();
            
            // Queue multi-key operations - assert they return null in transaction mode
            assertThat(connection.stringCommands().mGet(key1.getBytes(), key2.getBytes(), key3.getBytes())).isNull();
            
            Map<byte[], byte[]> msetData = new HashMap<>();
            msetData.put(key4.getBytes(), "txmsetvalue1".getBytes());
            msetData.put(key5.getBytes(), "txmsetvalue2".getBytes());
            assertThat(connection.stringCommands().mSet(msetData)).isNull();
            
            Map<byte[], byte[]> msetnxData = new HashMap<>();
            msetnxData.put("txnew1".getBytes(), "txmsetnxvalue1".getBytes());
            msetnxData.put("txnew2".getBytes(), "txmsetnxvalue2".getBytes());
            assertThat(connection.stringCommands().mSetNX(msetnxData)).isNull();
            
            assertThat(connection.stringCommands().mGet(key4.getBytes(), key5.getBytes())).isNull();
            
            java.util.List<Object> results = connection.exec();
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            
            // mGet result
            @SuppressWarnings("unchecked")
            java.util.List<byte[]> mgetResult1 = (java.util.List<byte[]>) results.get(0);
            assertThat(mgetResult1).hasSize(3);
            assertThat(mgetResult1.get(0)).isEqualTo("txinitial1".getBytes());
            assertThat(mgetResult1.get(1)).isEqualTo("txinitial2".getBytes());
            assertThat(mgetResult1.get(2)).isNull(); // key3 doesn't exist
            
            assertThat(results.get(1)).isEqualTo(true); // MSET result
            assertThat(results.get(2)).isEqualTo(true); // MSETNX result
            
            // Second mGet result
            @SuppressWarnings("unchecked")
            java.util.List<byte[]> mgetResult2 = (java.util.List<byte[]>) results.get(3);
            assertThat(mgetResult2).hasSize(2);
            assertThat(mgetResult2.get(0)).isEqualTo("txmsetvalue1".getBytes());
            assertThat(mgetResult2.get(1)).isEqualTo("txmsetvalue2".getBytes());
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(key4);
            cleanupKey(key5);
            cleanupKey("txnew1");
            cleanupKey("txnew2");
        }
    }

    @Test
    void testArithmeticOperationsInTransactionMode() {
        String intKey = "test:tx:int";
        String floatKey = "test:tx:float";
        String decrKey = "test:tx:decr";
        
        try {
            connection.multi();
            
            // Queue arithmetic operations - assert they return null in transaction mode
            assertThat(connection.stringCommands().incr(intKey.getBytes())).isNull();
            assertThat(connection.stringCommands().incrBy(intKey.getBytes(), 5L)).isNull();
            assertThat(connection.stringCommands().incrBy(floatKey.getBytes(), 3.14)).isNull();
            assertThat(connection.stringCommands().incrBy(floatKey.getBytes(), -1.14)).isNull();
            assertThat(connection.stringCommands().decr(decrKey.getBytes())).isNull();
            assertThat(connection.stringCommands().decrBy(decrKey.getBytes(), 3L)).isNull();
            
            java.util.List<Object> results = connection.exec();
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(1L); // INCR result
            assertThat(results.get(1)).isEqualTo(6L); // INCRBY result
            assertThat(results.get(2)).isEqualTo(3.14); // INCRBYFLOAT result
            assertThat(results.get(3)).isEqualTo(2.0); // INCRBYFLOAT result
            assertThat(results.get(4)).isEqualTo(-1L); // DECR result
            assertThat(results.get(5)).isEqualTo(-4L); // DECRBY result
            
        } finally {
            cleanupKey(intKey);
            cleanupKey(floatKey);
            cleanupKey(decrKey);
        }
    }

    @Test
    void testStringManipulationInTransactionMode() {
        String key1 = "test:tx:manip1";
        String key2 = "test:tx:manip2";
        
        try {
            // Set up initial data
            connection.stringCommands().set(key1.getBytes(), "TxHello".getBytes());
            connection.stringCommands().set(key2.getBytes(), "TxValkey World".getBytes());
            
            connection.multi();
            
            // Queue string manipulation operations - assert they return null in transaction mode
            assertThat(connection.stringCommands().append(key1.getBytes(), " World".getBytes())).isNull();
            assertThat(connection.stringCommands().strLen(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().getRange(key2.getBytes(), 0, 7)).isNull();
            connection.stringCommands().setRange(key2.getBytes(), "Valkey".getBytes(), 9); // void method
            assertThat(connection.stringCommands().get(key2.getBytes())).isNull();
            
            java.util.List<Object> results = connection.exec();
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(5);
            assertThat(results.get(0)).isEqualTo(13L); // APPEND result (length)
            assertThat(results.get(1)).isEqualTo(13L); // STRLEN result
            assertThat(results.get(2)).isEqualTo("TxValkey".getBytes()); // GETRANGE result
            assertThat(results.get(3)).isEqualTo(15L); // SETRANGE result (new length)
            assertThat(results.get(4)).isEqualTo("TxValkey Valkey".getBytes()); // GET result after setrange
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testBitOperationsInTransactionMode() {
        String key1 = "test:tx:bit1";
        String key2 = "test:tx:bit2";
        String destKey = "test:tx:bitop";
        
        try {
            // Set up initial data
            connection.stringCommands().set(key1.getBytes(), new byte[]{(byte) 0xF0}); // 11110000
            connection.stringCommands().set(key2.getBytes(), new byte[]{(byte) 0x0F}); // 00001111
            
            connection.multi();
            
            // Queue bit operations - assert they return null in transaction mode
            assertThat(connection.stringCommands().getBit(key1.getBytes(), 0)).isNull();
            assertThat(connection.stringCommands().setBit(key1.getBytes(), 4, false)).isNull();
            assertThat(connection.stringCommands().bitCount(key1.getBytes())).isNull();
            assertThat(connection.stringCommands().bitCount(key2.getBytes(), 0, 0)).isNull();
            assertThat(connection.stringCommands().bitOp(BitOperation.OR, destKey.getBytes(), key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.stringCommands().bitPos(key1.getBytes(), true)).isNull();
            
            java.util.List<Object> results = connection.exec();
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(6);
            assertThat(results.get(0)).isEqualTo(true); // GETBIT result
            assertThat(results.get(1)).isEqualTo(false); // SETBIT resut (old value was 0)
            assertThat(results.get(2)).isEqualTo(4L); // 4 bits still set
            assertThat(results.get(3)).isEqualTo(4L); // BITCOUNT result with range
            assertThat(results.get(4)).isEqualTo(1L); // BITOP result (1 byte processed)
            assertThat(results.get(5)).isEqualTo(0L); // BITPOS result
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testAdvancedStringOperationsInTransactionMode() {
        String key1 = "test:tx:advanced1";
        String key2 = "test:tx:advanced2";
        
        try {
            connection.multi();
            
            // Queue advanced operations - assert they return null in transaction mode
            assertThat(connection.stringCommands().set(key1.getBytes(), "txtest".getBytes(),
                Expiration.seconds(60), SetOption.ifAbsent())).isNull();
            assertThat(connection.stringCommands().setGet(key2.getBytes(), "txnewvalue".getBytes(),
                Expiration.persistent(), SetOption.upsert())).isNull();
            
            // BitField operations
            BitFieldSubCommands subCommands = BitFieldSubCommands.create()
                .set(BitFieldSubCommands.BitFieldType.unsigned(8)).valueAt(0).to(200)
                .get(BitFieldSubCommands.BitFieldType.unsigned(8)).valueAt(0);
            assertThat(connection.stringCommands().bitField(key1.getBytes(), subCommands)).isNull();
            
            java.util.List<Object> results = connection.exec();
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo(true); // SET with options result
            assertThat(results.get(1)).isNull(); // SETGET result (no previous value)
            
            // BitField results
            @SuppressWarnings("unchecked")
            java.util.List<Long> bitFieldResults = (java.util.List<Long>) results.get(2);
            assertThat(bitFieldResults).isNotNull();
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testTransactionWithWatchedKeys() {
        String watchedKey = "test:tx:watched";
        String valueKey = "test:tx:value";
        
        try {
            // Set initial value
            connection.stringCommands().set(watchedKey.getBytes(), "initial".getBytes());
            
            // Watch the key
            connection.watch(watchedKey.getBytes());
            
            // Start transaction
            connection.multi();
            connection.stringCommands().set(valueKey.getBytes(), "conditional_set".getBytes());
            
            // Execute transaction (should succeed since key wasn't modified)
            java.util.List<Object> results = connection.exec();
            
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo(true);
            
            // Verify the conditional set worked
            assertThat(connection.stringCommands().get(valueKey.getBytes())).isEqualTo("conditional_set".getBytes());
        } finally {
            cleanupKey(watchedKey);
            cleanupKey(valueKey);
        }
    }

    @Test
    void testTransactionAbortWithWatchConflict() {
        String watchedKey = "test:tx:conflict";
        String valueKey = "test:tx:aborted";
        
        try {
            // Set initial value
            connection.stringCommands().set(watchedKey.getBytes(), "initial".getBytes());
            
            // Watch the key
            connection.watch(watchedKey.getBytes());
            
            // Modify the watched key from outside the transaction to simulate conflict
            // In a real scenario, this would happen from another connection
            connection.stringCommands().set(watchedKey.getBytes(), "modified".getBytes());
            
            // Start transaction
            connection.multi();
            connection.stringCommands().set(valueKey.getBytes(), "should_not_be_set".getBytes());
            
            // Execute transaction (should be aborted due to watch conflict)
            // Valkey specification: aborted transactions return empty list
            java.util.List<Object> results = connection.exec();
            
            // Transaction should be aborted - expect empty list (not null or exception)
            assertThat(results).isNotNull().isEmpty();
            
            // Verify the conditional set was NOT executed
            byte[] result = connection.stringCommands().get(valueKey.getBytes());
            assertThat(result).isNull(); // Key should not exist
        } finally {
            cleanupKey(watchedKey);
            cleanupKey(valueKey);
        }
    }

    @Test
    void testMixedStringOperationsInTransaction() {
        String baseKey = "test:tx:mixed";
        
        try {
            connection.multi();
            
            // Mix of different string operations
            connection.stringCommands().set((baseKey + ":1").getBytes(), "100".getBytes());
            connection.stringCommands().incr((baseKey + ":1").getBytes());
            connection.stringCommands().append((baseKey + ":1").getBytes(), ".5".getBytes());
            connection.stringCommands().strLen((baseKey + ":1").getBytes());
            connection.stringCommands().setNX((baseKey + ":2").getBytes(), "new_value".getBytes());
            
            java.util.List<Object> results = connection.exec();
            
            assertThat(results).hasSize(5);
            assertThat(results.get(0)).isEqualTo(true); // SET
            assertThat(results.get(1)).isEqualTo(101L); // INCR
            assertThat(results.get(2)).isEqualTo(5L); // APPEND (length after append)
            assertThat(results.get(3)).isEqualTo(5L); // STRLEN
            assertThat(results.get(4)).isEqualTo(true); // SETNX
            
            // Verify final state
            assertThat(connection.stringCommands().get((baseKey + ":1").getBytes())).isEqualTo("101.5".getBytes());
            assertThat(connection.stringCommands().get((baseKey + ":2").getBytes())).isEqualTo("new_value".getBytes());
        } finally {
            cleanupKey(baseKey + ":1");
            cleanupKey(baseKey + ":2");
        }
    }

}
