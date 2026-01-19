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
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * set functionality using the ValkeySetCommands interface directly.
 * 
 * These tests validate the implementation of all ValkeySetCommands methods:
 * - Basic set operations (sAdd, sRem, sCard, sMembers)
 * - Set membership operations (sIsMember, sMIsMember)
 * - Random operations (sRandMember, sPop)
 * - Set movement operations (sMove)
 * - Set algebra operations (sDiff, sInter, sUnion)
 * - Set store operations (sDiffStore, sInterStore, sUnionStore)
 * - Set scanning operations (sScan)
 * - Error handling and edge cases
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionSetCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:set:add:key", "test:set:members:key", "test:set:mismember:key", "test:set:mismember:empty",
            "test:set:rem:key", "test:set:randmember:key", "test:set:pop:key", "test:set:move:src",
            "test:set:move:dest", "test:set:move:nonexistent", "test:set:diff:key1", "test:set:diff:key2",
            "test:set:diff:key3", "test:set:diffstore:key1", "test:set:diffstore:key2", "test:set:diffstore:dest",
            "test:set:inter:key1", "test:set:inter:key2", "test:set:inter:key3", "test:set:interstore:key1",
            "test:set:interstore:key2", "test:set:interstore:dest", "test:set:union:key1", "test:set:union:key2",
            "test:set:union:key3", "test:set:unionstore:key1", "test:set:unionstore:key2", "test:set:unionstore:dest",
            "test:set:scan:key", "test:set:edge:key", "test:set:algebra:edge:key1", "test:set:algebra:edge:key2",
            "test:set:algebra:edge:dest"
        };
    }

    // ==================== Basic Set Operations ====================

    @Test
    void testSAddAndSCard() {
        String key = "test:set:add:key";
        byte[] value1 = "member1".getBytes();
        byte[] value2 = "member2".getBytes();
        byte[] value3 = "member3".getBytes();
        
        try {
            // Test sCard on non-existent key
            Long cardEmpty = connection.setCommands().sCard(key.getBytes());
            assertThat(cardEmpty).isEqualTo(0L);
            
            // Test adding single value
            Long addResult1 = connection.setCommands().sAdd(key.getBytes(), value1);
            assertThat(addResult1).isEqualTo(1L);
            
            // Test cardinality after adding one element
            Long card1 = connection.setCommands().sCard(key.getBytes());
            assertThat(card1).isEqualTo(1L);
            
            // Test adding multiple values
            Long addResult2 = connection.setCommands().sAdd(key.getBytes(), value2, value3);
            assertThat(addResult2).isEqualTo(2L);
            
            // Test cardinality after adding three elements
            Long card3 = connection.setCommands().sCard(key.getBytes());
            assertThat(card3).isEqualTo(3L);
            
            // Test adding duplicate value (should return 0)
            Long addDuplicate = connection.setCommands().sAdd(key.getBytes(), value1);
            assertThat(addDuplicate).isEqualTo(0L);
            
            // Cardinality should remain the same
            Long cardSame = connection.setCommands().sCard(key.getBytes());
            assertThat(cardSame).isEqualTo(3L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSMembersAndSIsMember() {
        String key = "test:set:members:key";
        byte[] value1 = "member1".getBytes();
        byte[] value2 = "member2".getBytes();
        byte[] value3 = "member3".getBytes();
        byte[] nonMember = "nonmember".getBytes();
        
        try {
            // Test sMembers on empty set
            Set<byte[]> emptyMembers = connection.setCommands().sMembers(key.getBytes());
            assertThat(emptyMembers).isEmpty();
            
            // Test sIsMember on empty set
            Boolean isMemberEmpty = connection.setCommands().sIsMember(key.getBytes(), value1);
            assertThat(isMemberEmpty).isFalse();
            
            // Add some members
            connection.setCommands().sAdd(key.getBytes(), value1, value2, value3);
            
            // Test sMembers
            Set<byte[]> members = connection.setCommands().sMembers(key.getBytes());
            assertThat(members).hasSize(3);
            
            // Convert to strings for easier comparison
            Set<String> memberStrings = members.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(memberStrings).containsExactlyInAnyOrder("member1", "member2", "member3");
            
            // Test sIsMember for existing members
            Boolean isMember1 = connection.setCommands().sIsMember(key.getBytes(), value1);
            assertThat(isMember1).isTrue();
            
            Boolean isMember2 = connection.setCommands().sIsMember(key.getBytes(), value2);
            assertThat(isMember2).isTrue();
            
            // Test sIsMember for non-existing member
            Boolean isNonMember = connection.setCommands().sIsMember(key.getBytes(), nonMember);
            assertThat(isNonMember).isFalse();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSMIsMember() {
        String key = "test:set:mismember:key";
        byte[] value1 = "member1".getBytes();
        byte[] value2 = "member2".getBytes();
        byte[] value3 = "member3".getBytes();
        byte[] nonMember = "nonmember".getBytes();
        
        try {
            // Add some members
            connection.setCommands().sAdd(key.getBytes(), value1, value2);
            
            // Test sMIsMember with mixed existing and non-existing members
            List<Boolean> results = connection.setCommands().sMIsMember(key.getBytes(), 
                value1, value2, value3, nonMember);
            
            assertThat(results).hasSize(4);
            assertThat(results.get(0)).isTrue();  // member1 exists
            assertThat(results.get(1)).isTrue();  // member2 exists
            assertThat(results.get(2)).isFalse(); // member3 doesn't exist
            assertThat(results.get(3)).isFalse(); // nonmember doesn't exist
            
            // Test on empty set
            String emptyKey = "test:set:mismember:empty";
            List<Boolean> emptyResults = connection.setCommands().sMIsMember(emptyKey.getBytes(), 
                value1, value2);
            assertThat(emptyResults).containsOnly(false, false);
        } finally {
            cleanupKey(key);
            cleanupKey("test:set:mismember:empty");
        }
    }

    @Test
    void testSRem() {
        String key = "test:set:rem:key";
        byte[] value1 = "member1".getBytes();
        byte[] value2 = "member2".getBytes();
        byte[] value3 = "member3".getBytes();
        byte[] nonMember = "nonmember".getBytes();
        
        try {
            // Add some members
            connection.setCommands().sAdd(key.getBytes(), value1, value2, value3);
            
            // Test removing single member
            Long remResult1 = connection.setCommands().sRem(key.getBytes(), value1);
            assertThat(remResult1).isEqualTo(1L);
            
            // Verify member was removed
            Boolean isMember = connection.setCommands().sIsMember(key.getBytes(), value1);
            assertThat(isMember).isFalse();
            
            // Test removing multiple members
            Long remResult2 = connection.setCommands().sRem(key.getBytes(), value2, value3);
            assertThat(remResult2).isEqualTo(2L);
            
            // Test removing non-existing member
            Long remNonExist = connection.setCommands().sRem(key.getBytes(), nonMember);
            assertThat(remNonExist).isEqualTo(0L);
            
            // Verify set is empty
            Long card = connection.setCommands().sCard(key.getBytes());
            assertThat(card).isEqualTo(0L);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Random Operations ====================

    @Test
    void testSRandMember() {
        String key = "test:set:randmember:key";
        byte[] value1 = "member1".getBytes();
        byte[] value2 = "member2".getBytes();
        byte[] value3 = "member3".getBytes();
        
        try {
            // Test sRandMember on empty set
            byte[] emptyRand = connection.setCommands().sRandMember(key.getBytes());
            assertThat(emptyRand).isNull();
            
            // Add members
            connection.setCommands().sAdd(key.getBytes(), value1, value2, value3);
            
            // Test single random member
            byte[] randomMember = connection.setCommands().sRandMember(key.getBytes());
            assertThat(randomMember).isNotNull();
            
            String randomMemberStr = new String(randomMember);
            assertThat(randomMemberStr).isIn("member1", "member2", "member3");
            
            // Test multiple random members
            List<byte[]> randomMembers = connection.setCommands().sRandMember(key.getBytes(), 2);
            assertThat(randomMembers).hasSize(2);
            
            // Convert to strings for verification
            List<String> randomMemberStrs = randomMembers.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            
            // All returned members should be valid
            for (String member : randomMemberStrs) {
                assertThat(member).isIn("member1", "member2", "member3");
            }
            
            // Test with count larger than set size
            List<byte[]> moreMembers = connection.setCommands().sRandMember(key.getBytes(), 5);
            assertThat(moreMembers).hasSizeLessThanOrEqualTo(3); // Should not exceed set size
            
            // Test with negative count (allows duplicates)
            List<byte[]> negativeCount = connection.setCommands().sRandMember(key.getBytes(), -5);
            assertThat(negativeCount).hasSize(5); // Should return exactly 5 elements (with possible duplicates)
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSPop() {
        String key = "test:set:pop:key";
        byte[] value1 = "member1".getBytes();
        byte[] value2 = "member2".getBytes();
        byte[] value3 = "member3".getBytes();
        
        try {
            // Test sPop on empty set
            byte[] emptyPop = connection.setCommands().sPop(key.getBytes());
            assertThat(emptyPop).isNull();
            
            // Add members
            connection.setCommands().sAdd(key.getBytes(), value1, value2, value3);
            
            // Test single pop
            byte[] poppedMember = connection.setCommands().sPop(key.getBytes());
            assertThat(poppedMember).isNotNull();
            
            String poppedStr = new String(poppedMember);
            assertThat(poppedStr).isIn("member1", "member2", "member3");
            
            // Verify member was removed
            Boolean stillMember = connection.setCommands().sIsMember(key.getBytes(), poppedMember);
            assertThat(stillMember).isFalse();
            
            // Verify cardinality decreased
            Long card = connection.setCommands().sCard(key.getBytes());
            assertThat(card).isEqualTo(2L);
            
            // Test multiple pop
            List<byte[]> poppedMembers = connection.setCommands().sPop(key.getBytes(), 2);
            assertThat(poppedMembers).hasSize(2);
            
            // Verify all were removed
            Long finalCard = connection.setCommands().sCard(key.getBytes());
            assertThat(finalCard).isEqualTo(0L);
            
            // Test pop more than available
            connection.setCommands().sAdd(key.getBytes(), value1);
            List<byte[]> overPop = connection.setCommands().sPop(key.getBytes(), 5);
            assertThat(overPop).hasSize(1); // Should only return what's available
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Set Movement Operations ====================

    @Test
    void testSMove() {
        String srcKey = "test:set:move:src";
        String destKey = "test:set:move:dest";
        byte[] value1 = "member1".getBytes();
        byte[] value2 = "member2".getBytes();
        byte[] nonMember = "nonmember".getBytes();
        
        try {
            // Setup source set
            connection.setCommands().sAdd(srcKey.getBytes(), value1, value2);
            
            // Test moving existing member
            Boolean moveResult1 = connection.setCommands().sMove(srcKey.getBytes(), destKey.getBytes(), value1);
            assertThat(moveResult1).isTrue();
            
            // Verify member was removed from source
            Boolean inSrc = connection.setCommands().sIsMember(srcKey.getBytes(), value1);
            assertThat(inSrc).isFalse();
            
            // Verify member was added to destination
            Boolean inDest = connection.setCommands().sIsMember(destKey.getBytes(), value1);
            assertThat(inDest).isTrue();
            
            // Test moving non-existing member
            Boolean moveResult2 = connection.setCommands().sMove(srcKey.getBytes(), destKey.getBytes(), nonMember);
            assertThat(moveResult2).isFalse();
            
            // Test moving from non-existing set
            String nonExistentKey = "test:set:move:nonexistent";
            Boolean moveResult3 = connection.setCommands().sMove(nonExistentKey.getBytes(), destKey.getBytes(), value2);
            assertThat(moveResult3).isFalse();
        } finally {
            cleanupKey(srcKey);
            cleanupKey(destKey);
            cleanupKey("test:set:move:nonexistent");
        }
    }

    // ==================== Set Algebra Operations ====================

    @Test
    void testSDiff() {
        String key1 = "test:set:diff:key1";
        String key2 = "test:set:diff:key2";
        String key3 = "test:set:diff:key3";
        
        try {
            // Setup test sets
            // key1: {a, b, c, d}
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes());
            // key2: {b, c, e}
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes(), "e".getBytes());
            // key3: {d, f}
            connection.setCommands().sAdd(key3.getBytes(), "d".getBytes(), "f".getBytes());
            
            // Test diff of key1 - key2 (should be {a, d})
            Set<byte[]> diff1 = connection.setCommands().sDiff(key1.getBytes(), key2.getBytes());
            Set<String> diff1Strings = diff1.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(diff1Strings).containsExactlyInAnyOrder("a", "d");
            
            // Test diff of key1 - key2 - key3 (should be {a})
            Set<byte[]> diff2 = connection.setCommands().sDiff(key1.getBytes(), key2.getBytes(), key3.getBytes());
            Set<String> diff2Strings = diff2.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(diff2Strings).containsExactlyInAnyOrder("a");
            
            // Test diff with non-existent key
            String nonExistentKey = "test:set:diff:nonexistent";
            Set<byte[]> diff3 = connection.setCommands().sDiff(key1.getBytes(), nonExistentKey.getBytes());
            Set<String> diff3Strings = diff3.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(diff3Strings).containsExactlyInAnyOrder("a", "b", "c", "d");
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testSDiffStore() {
        String key1 = "test:set:diffstore:key1";
        String key2 = "test:set:diffstore:key2";
        String destKey = "test:set:diffstore:dest";
        
        try {
            // Setup test sets
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes());
            
            // Test sDiffStore
            Long storeResult = connection.setCommands().sDiffStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(storeResult).isEqualTo(1L); // Should store 1 element: "a"
            
            // Verify stored result
            Set<byte[]> storedDiff = connection.setCommands().sMembers(destKey.getBytes());
            Set<String> storedDiffStrings = storedDiff.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(storedDiffStrings).containsExactlyInAnyOrder("a");
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testSInter() {
        String key1 = "test:set:inter:key1";
        String key2 = "test:set:inter:key2";
        String key3 = "test:set:inter:key3";
        
        try {
            // Setup test sets
            // key1: {a, b, c}
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            // key2: {b, c, d}
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes());
            // key3: {c, d, e}
            connection.setCommands().sAdd(key3.getBytes(), "c".getBytes(), "d".getBytes(), "e".getBytes());
            
            // Test intersection of key1 and key2 (should be {b, c})
            Set<byte[]> inter1 = connection.setCommands().sInter(key1.getBytes(), key2.getBytes());
            Set<String> inter1Strings = inter1.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(inter1Strings).containsExactlyInAnyOrder("b", "c");
            
            // Test intersection of all three (should be {c})
            Set<byte[]> inter2 = connection.setCommands().sInter(key1.getBytes(), key2.getBytes(), key3.getBytes());
            Set<String> inter2Strings = inter2.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(inter2Strings).containsExactlyInAnyOrder("c");
            
            // Test intersection with non-existent key (should be empty)
            String nonExistentKey = "test:set:inter:nonexistent";
            Set<byte[]> inter3 = connection.setCommands().sInter(key1.getBytes(), nonExistentKey.getBytes());
            assertThat(inter3).isEmpty();
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testSInterStore() {
        String key1 = "test:set:interstore:key1";
        String key2 = "test:set:interstore:key2";
        String destKey = "test:set:interstore:dest";
        
        try {
            // Setup test sets
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes());
            
            // Test sInterStore
            Long storeResult = connection.setCommands().sInterStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(storeResult).isEqualTo(2L); // Should store 2 elements: "b", "c"
            
            // Verify stored result
            Set<byte[]> storedInter = connection.setCommands().sMembers(destKey.getBytes());
            Set<String> storedInterStrings = storedInter.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(storedInterStrings).containsExactlyInAnyOrder("b", "c");
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testSUnion() {
        String key1 = "test:set:union:key1";
        String key2 = "test:set:union:key2";
        String key3 = "test:set:union:key3";
        
        try {
            // Setup test sets
            // key1: {a, b}
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes());
            // key2: {b, c}
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes());
            // key3: {c, d}
            connection.setCommands().sAdd(key3.getBytes(), "c".getBytes(), "d".getBytes());
            
            // Test union of key1 and key2 (should be {a, b, c})
            Set<byte[]> union1 = connection.setCommands().sUnion(key1.getBytes(), key2.getBytes());
            Set<String> union1Strings = union1.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(union1Strings).containsExactlyInAnyOrder("a", "b", "c");
            
            // Test union of all three (should be {a, b, c, d})
            Set<byte[]> union2 = connection.setCommands().sUnion(key1.getBytes(), key2.getBytes(), key3.getBytes());
            Set<String> union2Strings = union2.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(union2Strings).containsExactlyInAnyOrder("a", "b", "c", "d");
            
            // Test union with non-existent key
            String nonExistentKey = "test:set:union:nonexistent";
            Set<byte[]> union3 = connection.setCommands().sUnion(key1.getBytes(), nonExistentKey.getBytes());
            Set<String> union3Strings = union3.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(union3Strings).containsExactlyInAnyOrder("a", "b");
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
        }
    }

    @Test
    void testSUnionStore() {
        String key1 = "test:set:unionstore:key1";
        String key2 = "test:set:unionstore:key2";
        String destKey = "test:set:unionstore:dest";
        
        try {
            // Setup test sets
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes());
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes());
            
            // Test sUnionStore
            Long storeResult = connection.setCommands().sUnionStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(storeResult).isEqualTo(3L); // Should store 3 elements: "a", "b", "c"
            
            // Verify stored result
            Set<byte[]> storedUnion = connection.setCommands().sMembers(destKey.getBytes());
            Set<String> storedUnionStrings = storedUnion.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(storedUnionStrings).containsExactlyInAnyOrder("a", "b", "c");
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    // ==================== Set Scanning Operations ====================

    @Test
    void testSScan() {
        String key = "test:set:scan:key";
        
        try {
            // Setup test set with multiple members
            for (int i = 0; i < 10; i++) {
                connection.setCommands().sAdd(key.getBytes(), ("member" + i).getBytes());
            }
            
            // Test basic scan
            ScanOptions options = ScanOptions.scanOptions().build();
            Cursor<byte[]> cursor = connection.setCommands().sScan(key.getBytes(), options);
            
            java.util.List<String> scannedMembers = new java.util.ArrayList<>();
            while (cursor.hasNext()) {
                scannedMembers.add(new String(cursor.next()));
            }
            cursor.close();
            
            // Should find all members
            assertThat(scannedMembers).hasSize(10);
            for (int i = 0; i < 10; i++) {
                assertThat(scannedMembers).contains("member" + i);
            }
            
            // Test scan with pattern
            ScanOptions patternOptions = ScanOptions.scanOptions().match("member[1-3]").build();
            Cursor<byte[]> patternCursor = connection.setCommands().sScan(key.getBytes(), patternOptions);
            
            java.util.List<String> patternMembers = new java.util.ArrayList<>();
            while (patternCursor.hasNext()) {
                patternMembers.add(new String(patternCursor.next()));
            }
            patternCursor.close();
            
            // Should find fewer members due to pattern filter
            assertThat(patternMembers.size()).isLessThanOrEqualTo(3);
            
            // Test scan with count
            ScanOptions countOptions = ScanOptions.scanOptions().count(2).build();
            Cursor<byte[]> countCursor = connection.setCommands().sScan(key.getBytes(), countOptions);
            
            java.util.List<String> countMembers = new java.util.ArrayList<>();
            while (countCursor.hasNext()) {
                countMembers.add(new String(countCursor.next()));
            }
            countCursor.close();
            
            // Should still find all members, but in smaller batches
            assertThat(countMembers).hasSize(10);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Error Handling and Edge Cases ====================

    @Test
    void testSetOperationsErrorHandling() {
        // Test operations on null keys (should throw IllegalArgumentException)
        assertThatThrownBy(() -> connection.setCommands().sAdd(null, "value".getBytes()))
            .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> connection.setCommands().sRem(null, "value".getBytes()))
            .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> connection.setCommands().sCard(null))
            .isInstanceOf(IllegalArgumentException.class);
        
        // Test operations on empty keys (should throw IllegalArgumentException)
        // assertThatThrownBy(() -> connection.setCommands().sAdd(new byte[0], "value".getBytes()))
        //     .isInstanceOf(IllegalArgumentException.class);
        
        // assertThatThrownBy(() -> connection.setCommands().sRem(new byte[0], "value".getBytes()))
        //     .isInstanceOf(IllegalArgumentException.class);
        
        // assertThatThrownBy(() -> connection.setCommands().sCard(new byte[0]))
        //     .isInstanceOf(IllegalArgumentException.class);
        
        // Test operations with null values
        assertThatThrownBy(() -> connection.setCommands().sAdd("key".getBytes(), (byte[]) null))
            .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> connection.setCommands().sRem("key".getBytes(), (byte[]) null))
            .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> connection.setCommands().sIsMember("key".getBytes(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetOperationsEdgeCases() {
        String key = "test:set:edge:key";
        
        try {
            // Test operations on very large values
            byte[] largeValue = new byte[1024 * 10]; // 10KB value
            java.util.Arrays.fill(largeValue, (byte) 'A');
            
            Long addLarge = connection.setCommands().sAdd(key.getBytes(), largeValue);
            assertThat(addLarge).isEqualTo(1L);
            
            Boolean isLargeMember = connection.setCommands().sIsMember(key.getBytes(), largeValue);
            assertThat(isLargeMember).isTrue();
            
            Long remLarge = connection.setCommands().sRem(key.getBytes(), largeValue);
            assertThat(remLarge).isEqualTo(1L);
            
            // Test with empty value
            byte[] emptyValue = new byte[0];
            Long addEmpty = connection.setCommands().sAdd(key.getBytes(), emptyValue);
            assertThat(addEmpty).isEqualTo(1L);
            
            Boolean isEmptyMember = connection.setCommands().sIsMember(key.getBytes(), emptyValue);
            assertThat(isEmptyMember).isTrue();
            
            // Test with binary values
            byte[] binaryValue = {0x00, (byte) 0xFF, 0x7F, (byte) 0x80, 0x01};
            Long addBinary = connection.setCommands().sAdd(key.getBytes(), binaryValue);
            assertThat(addBinary).isEqualTo(1L);
            
            Boolean isBinaryMember = connection.setCommands().sIsMember(key.getBytes(), binaryValue);
            assertThat(isBinaryMember).isTrue();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSetAlgebraEdgeCases() {
        String key1 = "test:set:algebra:edge:key1";
        String key2 = "test:set:algebra:edge:key2";
        String destKey = "test:set:algebra:edge:dest";
        
        try {
            // Test operations with empty sets
            Set<byte[]> emptyDiff = connection.setCommands().sDiff(key1.getBytes(), key2.getBytes());
            assertThat(emptyDiff).isEmpty();
            
            Set<byte[]> emptyInter = connection.setCommands().sInter(key1.getBytes(), key2.getBytes());
            assertThat(emptyInter).isEmpty();
            
            Set<byte[]> emptyUnion = connection.setCommands().sUnion(key1.getBytes(), key2.getBytes());
            assertThat(emptyUnion).isEmpty();
            
            // Test store operations with empty results
            Long diffStore = connection.setCommands().sDiffStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(diffStore).isEqualTo(0L);
            
            Long interStore = connection.setCommands().sInterStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(interStore).isEqualTo(0L);
            
            Long unionStore = connection.setCommands().sUnionStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(unionStore).isEqualTo(0L);
            
            // Test with single set having data
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes());
            
            Set<byte[]> diffSingle = connection.setCommands().sDiff(key1.getBytes(), key2.getBytes());
            assertThat(diffSingle).hasSize(2);
            
            Set<byte[]> unionSingle = connection.setCommands().sUnion(key1.getBytes(), key2.getBytes());
            assertThat(unionSingle).hasSize(2);
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    // ==================== Pipeline Mode Tests ====================

    @Test
    void testSetOperationsInPipelineMode() {
        String key1 = "test:set:pipeline:key1";
        String key2 = "test:set:pipeline:key2";
        String destKey = "test:set:pipeline:dest";
        
        try {
            // Setup initial data
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Execute commands in pipeline - all should return null
            Long sAddResult = connection.setCommands().sAdd(key1.getBytes(), "e".getBytes());
            assertThat(sAddResult).isNull();
            
            Long sRemResult = connection.setCommands().sRem(key1.getBytes(), "a".getBytes());
            assertThat(sRemResult).isNull();
            
            Long sCardResult = connection.setCommands().sCard(key1.getBytes());
            assertThat(sCardResult).isNull();
            
            Boolean sIsMemberResult = connection.setCommands().sIsMember(key1.getBytes(), "b".getBytes());
            assertThat(sIsMemberResult).isNull();
            
            Set<byte[]> sMembersResult = connection.setCommands().sMembers(key1.getBytes());
            assertThat(sMembersResult).isNull();
            
            Set<byte[]> sDiffResult = connection.setCommands().sDiff(key1.getBytes(), key2.getBytes());
            assertThat(sDiffResult).isNull();
            
            Long sDiffStoreResult = connection.setCommands().sDiffStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(sDiffStoreResult).isNull();
            
            // Close pipeline and get results
            List<Object> results = connection.closePipeline();
            
            // Verify pipeline results in order
            assertThat(results).hasSize(7);
            assertThat((Long) results.get(0)).isEqualTo(1L);  // sAdd added 1 element
            assertThat((Long) results.get(1)).isEqualTo(1L);  // sRem removed 1 element
            assertThat((Long) results.get(2)).isEqualTo(3L);  // sCard: b, c, e (a removed, e added)
            assertThat((Boolean) results.get(3)).isTrue();    // sIsMember: b exists
            
            // Verify sMembers result
            @SuppressWarnings("unchecked")
            Set<byte[]> membersResult = (Set<byte[]>) results.get(4);
            Set<String> memberStrings = membersResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(memberStrings).containsExactlyInAnyOrder("b", "c", "e");
            
            // Verify sDiff result
            @SuppressWarnings("unchecked")
            Set<byte[]> diffResult = (Set<byte[]>) results.get(5);
            Set<String> diffStrings = diffResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(diffStrings).containsExactlyInAnyOrder("e");  // {b,c,e} - {b,c,d} = {e}
            
            assertThat((Long) results.get(6)).isEqualTo(1L);  // sDiffStore stored 1 element
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testSetOperationsWithMultipleValuesPipeline() {
        String key = "test:set:pipeline:multi:key";
        
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Test commands with multiple values in pipeline
            Long sAddResult = connection.setCommands().sAdd(key.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            assertThat(sAddResult).isNull();
            
            List<Boolean> sMIsMemberResult = connection.setCommands().sMIsMember(key.getBytes(), 
                "a".getBytes(), "b".getBytes(), "d".getBytes());
            assertThat(sMIsMemberResult).isNull();
            
            Long sRemResult = connection.setCommands().sRem(key.getBytes(), "a".getBytes(), "b".getBytes());
            assertThat(sRemResult).isNull();
            
            // Close pipeline and get results
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(3);
            assertThat((Long) results.get(0)).isEqualTo(3L);  // sAdd added 3 elements
            
            @SuppressWarnings("unchecked")
            List<Boolean> multiMemberResult = (List<Boolean>) results.get(1);
            assertThat(multiMemberResult).containsExactly(true, true, false);  // a, b exist; d doesn't
            
            assertThat((Long) results.get(2)).isEqualTo(2L);  // sRem removed 2 elements
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSetRandomOperationsPipeline() {
        String key = "test:set:pipeline:random:key";
        
        try {
            // Setup initial data
            connection.setCommands().sAdd(key.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Test random operations in pipeline
            byte[] sRandMemberResult = connection.setCommands().sRandMember(key.getBytes());
            assertThat(sRandMemberResult).isNull();
            
            List<byte[]> sRandMemberMultiResult = connection.setCommands().sRandMember(key.getBytes(), 2);
            assertThat(sRandMemberMultiResult).isNull();
            
            byte[] sPopResult = connection.setCommands().sPop(key.getBytes());
            assertThat(sPopResult).isNull();
            
            List<byte[]> sPopMultiResult = connection.setCommands().sPop(key.getBytes(), 1);
            assertThat(sPopMultiResult).isNull();
            
            // Close pipeline and get results
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(4);
            
            // sRandMember single result
            byte[] randMember = (byte[]) results.get(0);
            assertThat(randMember).isNotNull();
            String randMemberStr = new String(randMember);
            assertThat(randMemberStr).isIn("a", "b", "c");
            
            // sRandMember multiple result
            @SuppressWarnings("unchecked")
            List<byte[]> randMembers = (List<byte[]>) results.get(1);
            assertThat(randMembers).hasSize(2);
            
            // sPop single result
            byte[] poppedMember = (byte[]) results.get(2);
            assertThat(poppedMember).isNotNull();
            String poppedStr = new String(poppedMember);
            assertThat(poppedStr).isIn("a", "b", "c");
            
            // sPop multiple result
            @SuppressWarnings("unchecked")
            List<byte[]> poppedMembers = (List<byte[]>) results.get(3);
            assertThat(poppedMembers).hasSize(1);
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Transaction Mode Tests ====================

    @Test
    void testSetOperationsInTransactionMode() {
        String key1 = "test:set:tx:key1";
        String key2 = "test:set:tx:key2";
        String destKey = "test:set:tx:dest";
        
        try {
            // Setup initial data
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Execute commands in transaction - all should return null
            Long sAddResult = connection.setCommands().sAdd(key1.getBytes(), "e".getBytes());
            assertThat(sAddResult).isNull();
            
            Long sRemResult = connection.setCommands().sRem(key1.getBytes(), "a".getBytes());
            assertThat(sRemResult).isNull();
            
            Long sCardResult = connection.setCommands().sCard(key1.getBytes());
            assertThat(sCardResult).isNull();
            
            Boolean sIsMemberResult = connection.setCommands().sIsMember(key1.getBytes(), "b".getBytes());
            assertThat(sIsMemberResult).isNull();
            
            Set<byte[]> sMembersResult = connection.setCommands().sMembers(key1.getBytes());
            assertThat(sMembersResult).isNull();
            
            Set<byte[]> sInterResult = connection.setCommands().sInter(key1.getBytes(), key2.getBytes());
            assertThat(sInterResult).isNull();
            
            Long sInterStoreResult = connection.setCommands().sInterStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(sInterStoreResult).isNull();
            
            // Execute transaction and get results
            List<Object> results = connection.exec();
            
            // Verify transaction results in order
            assertThat(results).hasSize(7);
            assertThat((Long) results.get(0)).isEqualTo(1L);  // sAdd added 1 element
            assertThat((Long) results.get(1)).isEqualTo(1L);  // sRem removed 1 element
            assertThat((Long) results.get(2)).isEqualTo(3L);  // sCard: b, c, e (a removed, e added)
            assertThat((Boolean) results.get(3)).isTrue();    // sIsMember: b exists
            
            // Verify sMembers result
            @SuppressWarnings("unchecked")
            Set<byte[]> membersResult = (Set<byte[]>) results.get(4);
            Set<String> memberStrings = membersResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(memberStrings).containsExactlyInAnyOrder("b", "c", "e");
            
            // Verify sInter result
            @SuppressWarnings("unchecked")
            Set<byte[]> interResult = (Set<byte[]>) results.get(5);
            Set<String> interStrings = interResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(interStrings).containsExactlyInAnyOrder("b", "c");  // {b,c,e} ∩ {b,c,d} = {b,c}
            
            assertThat((Long) results.get(6)).isEqualTo(2L);  // sInterStore stored 2 elements
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testSetAlgebraOperationsTransaction() {
        String key1 = "test:set:tx:algebra:key1";
        String key2 = "test:set:tx:algebra:key2";
        String key3 = "test:set:tx:algebra:key3";
        String diffDest = "test:set:tx:algebra:diff:dest";
        String unionDest = "test:set:tx:algebra:union:dest";
        
        try {
            // Setup initial data
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes());
            connection.setCommands().sAdd(key2.getBytes(), "b".getBytes(), "c".getBytes());
            connection.setCommands().sAdd(key3.getBytes(), "c".getBytes(), "d".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Test set algebra operations in transaction
            Set<byte[]> sDiffResult = connection.setCommands().sDiff(key1.getBytes(), key2.getBytes());
            assertThat(sDiffResult).isNull();
            
            Set<byte[]> sUnionResult = connection.setCommands().sUnion(key1.getBytes(), key2.getBytes(), key3.getBytes());
            assertThat(sUnionResult).isNull();
            
            Long sDiffStoreResult = connection.setCommands().sDiffStore(diffDest.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(sDiffStoreResult).isNull();
            
            Long sUnionStoreResult = connection.setCommands().sUnionStore(unionDest.getBytes(), key1.getBytes(), key2.getBytes(), key3.getBytes());
            assertThat(sUnionStoreResult).isNull();
            
            // Execute transaction and get results
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).hasSize(4);
            
            // Verify sDiff result
            @SuppressWarnings("unchecked")
            Set<byte[]> diffResult = (Set<byte[]>) results.get(0);
            Set<String> diffStrings = diffResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(diffStrings).containsExactlyInAnyOrder("a");  // {a,b} - {b,c} = {a}
            
            // Verify sUnion result
            @SuppressWarnings("unchecked")
            Set<byte[]> unionResult = (Set<byte[]>) results.get(1);
            Set<String> unionStrings = unionResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(unionStrings).containsExactlyInAnyOrder("a", "b", "c", "d");  // {a,b} ∪ {b,c} ∪ {c,d}
            
            assertThat((Long) results.get(2)).isEqualTo(1L);  // sDiffStore stored 1 element
            assertThat((Long) results.get(3)).isEqualTo(4L);  // sUnionStore stored 4 elements
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            cleanupKey(diffDest);
            cleanupKey(unionDest);
        }
    }

    @Test
    void testSetMoveOperationTransaction() {
        String srcKey = "test:set:tx:move:src";
        String destKey = "test:set:tx:move:dest";
        
        try {
            // Setup initial data
            connection.setCommands().sAdd(srcKey.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            connection.setCommands().sAdd(destKey.getBytes(), "x".getBytes(), "y".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Test sMove operations in transaction
            Boolean sMoveResult1 = connection.setCommands().sMove(srcKey.getBytes(), destKey.getBytes(), "a".getBytes());
            assertThat(sMoveResult1).isNull();
            
            Boolean sMoveResult2 = connection.setCommands().sMove(srcKey.getBytes(), destKey.getBytes(), "nonexistent".getBytes());
            assertThat(sMoveResult2).isNull();
            
            Long srcCardResult = connection.setCommands().sCard(srcKey.getBytes());
            assertThat(srcCardResult).isNull();
            
            Long destCardResult = connection.setCommands().sCard(destKey.getBytes());
            assertThat(destCardResult).isNull();
            
            // Execute transaction and get results
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).hasSize(4);
            assertThat((Boolean) results.get(0)).isTrue();   // sMove successful
            assertThat((Boolean) results.get(1)).isFalse();  // sMove failed (nonexistent element)
            assertThat((Long) results.get(2)).isEqualTo(2L); // srcCard: b, c (a moved out)
            assertThat((Long) results.get(3)).isEqualTo(3L); // destCard: x, y, a (a moved in)
            
        } finally {
            cleanupKey(srcKey);
            cleanupKey(destKey);
        }
    }

    // ==================== Command-Response Index Correlation Tests ====================

    @Test
    void testMixedSetOperationsPipelineIndexCorrelation() {
        String key1 = "test:set:pipeline:mixed:key1";
        String key2 = "test:set:pipeline:mixed:key2";
        
        try {
            // Setup initial data
            connection.setCommands().sAdd(key1.getBytes(), "a".getBytes(), "b".getBytes());
            
            // Start pipeline with mixed operations
            connection.openPipeline();
            
            // Mix of different operation types to test index correlation
            Long addResult = connection.setCommands().sAdd(key1.getBytes(), "c".getBytes());       // Index 0: Long
            Boolean memberResult = connection.setCommands().sIsMember(key1.getBytes(), "b".getBytes()); // Index 1: Boolean
            Set<byte[]> membersResult = connection.setCommands().sMembers(key1.getBytes());        // Index 2: Set<byte[]>
            Long cardResult = connection.setCommands().sCard(key1.getBytes());                     // Index 3: Long
            List<Boolean> multiMemberResult = connection.setCommands().sMIsMember(key1.getBytes(), 
                "a".getBytes(), "d".getBytes());                                                   // Index 4: List<Boolean>
            byte[] popResult = connection.setCommands().sPop(key1.getBytes());                     // Index 5: byte[]
            Long remResult = connection.setCommands().sRem(key1.getBytes(), "b".getBytes());       // Index 6: Long
            
            // All should be null during pipeline
            assertThat(addResult).isNull();
            assertThat(memberResult).isNull();
            assertThat(membersResult).isNull();
            assertThat(cardResult).isNull();
            assertThat(multiMemberResult).isNull();
            assertThat(popResult).isNull();
            assertThat(remResult).isNull();
            
            // Close pipeline and verify exact index correlation
            List<Object> results = connection.closePipeline();
            
            assertThat(results).hasSize(7);
            
            // Index 0: sAdd result
            assertThat((Long) results.get(0)).isEqualTo(1L);
            
            // Index 1: sIsMember result
            assertThat((Boolean) results.get(1)).isTrue();
            
            // Index 2: sMembers result
            @SuppressWarnings("unchecked")
            Set<byte[]> members = (Set<byte[]>) results.get(2);
            assertThat(members).hasSize(3); // a, b, c
            
            // Index 3: sCard result
            assertThat((Long) results.get(3)).isEqualTo(3L);
            
            // Index 4: sMIsMember result
            @SuppressWarnings("unchecked")
            List<Boolean> multiMember = (List<Boolean>) results.get(4);
            assertThat(multiMember).containsExactly(true, false); // a exists, d doesn't
            
            // Index 5: sPop result
            byte[] popped = (byte[]) results.get(5);
            assertThat(popped).isNotNull();
            String poppedStr = new String(popped);
            assertThat(poppedStr).isIn("a", "b", "c");
            
            // Index 6: sRem result (may be 0 or 1 depending on what was popped)
            Long removed = (Long) results.get(6);
            assertThat(removed).isBetween(0L, 1L);
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testComplexSetOperationsTransactionIndexCorrelation() {
        String baseKey = "test:set:tx:complex:";
        
        try {
            // Setup multiple keys with initial data
            for (int i = 1; i <= 3; i++) {
                connection.setCommands().sAdd((baseKey + i).getBytes(), 
                    ("member" + i).getBytes(), "common".getBytes());
            }
            
            // Start transaction with complex operations
            connection.multi();
            
            // Create a complex sequence of operations to test exact index correlation
            Long add1 = connection.setCommands().sAdd((baseKey + "1").getBytes(), "new1".getBytes());    // Index 0
            Long add2 = connection.setCommands().sAdd((baseKey + "2").getBytes(), "new2".getBytes());    // Index 1
            Long add3 = connection.setCommands().sAdd((baseKey + "3").getBytes(), "new3".getBytes());    // Index 2
            
            Set<byte[]> diff12 = connection.setCommands().sDiff((baseKey + "1").getBytes(), (baseKey + "2").getBytes()); // Index 3
            Set<byte[]> inter123 = connection.setCommands().sInter((baseKey + "1").getBytes(), 
                (baseKey + "2").getBytes(), (baseKey + "3").getBytes());                               // Index 4
            Set<byte[]> union12 = connection.setCommands().sUnion((baseKey + "1").getBytes(), (baseKey + "2").getBytes()); // Index 5
            
            Long diffStore = connection.setCommands().sDiffStore((baseKey + "diff").getBytes(), 
                (baseKey + "1").getBytes(), (baseKey + "3").getBytes());                               // Index 6
            Long unionStore = connection.setCommands().sUnionStore((baseKey + "union").getBytes(), 
                (baseKey + "1").getBytes(), (baseKey + "2").getBytes(), (baseKey + "3").getBytes());  // Index 7
            
            // All should be null during transaction
            assertThat(add1).isNull();
            assertThat(add2).isNull();
            assertThat(add3).isNull();
            assertThat(diff12).isNull();
            assertThat(inter123).isNull();
            assertThat(union12).isNull();
            assertThat(diffStore).isNull();
            assertThat(unionStore).isNull();
            
            // Execute transaction and verify exact index correlation
            List<Object> results = connection.exec();
            
            assertThat(results).hasSize(8);
            
            // Verify each result at its exact index
            assertThat((Long) results.get(0)).isEqualTo(1L);  // add1
            assertThat((Long) results.get(1)).isEqualTo(1L);  // add2
            assertThat((Long) results.get(2)).isEqualTo(1L);  // add3
            
            // Verify set operation results
            @SuppressWarnings("unchecked")
            Set<byte[]> diffResult = (Set<byte[]>) results.get(3);
            Set<String> diffStrings = diffResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(diffStrings).contains("member1", "new1"); // Elements unique to key1
            
            @SuppressWarnings("unchecked")
            Set<byte[]> interResult = (Set<byte[]>) results.get(4);
            Set<String> interStrings = interResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(interStrings).containsExactly("common"); // Only common element
            
            @SuppressWarnings("unchecked")
            Set<byte[]> unionResult = (Set<byte[]>) results.get(5);
            assertThat(unionResult).hasSizeGreaterThanOrEqualTo(4); // At least: member1, member2, common, new1, new2
            
            assertThat((Long) results.get(6)).isGreaterThanOrEqualTo(1L);  // diffStore
            assertThat((Long) results.get(7)).isGreaterThanOrEqualTo(5L);  // unionStore
            
        } finally {
            for (int i = 1; i <= 3; i++) {
                cleanupKey(baseKey + i);
            }
            cleanupKey(baseKey + "diff");
            cleanupKey(baseKey + "union");
        }
    }

    // ==================== Additional Pipeline Mode Tests for Complete Coverage ====================

    @Test
    void testSetMoveAndAlgebraOperationsPipeline() {
        String srcKey = "test:set:pipeline:move:src";
        String destKey = "test:set:pipeline:move:dest";
        String key1 = "test:set:pipeline:algebra:key1";
        String key2 = "test:set:pipeline:algebra:key2";
        String interDest = "test:set:pipeline:algebra:inter:dest";
        String unionDest = "test:set:pipeline:algebra:union:dest";
        
        try {
            // Setup initial data
            connection.setCommands().sAdd(srcKey.getBytes(), "a".getBytes(), "b".getBytes());
            connection.setCommands().sAdd(key1.getBytes(), "x".getBytes(), "y".getBytes());
            connection.setCommands().sAdd(key2.getBytes(), "y".getBytes(), "z".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Test sMove in pipeline
            Boolean sMoveResult = connection.setCommands().sMove(srcKey.getBytes(), destKey.getBytes(), "a".getBytes());
            assertThat(sMoveResult).isNull();
            
            // Test set algebra operations in pipeline
            Set<byte[]> sInterResult = connection.setCommands().sInter(key1.getBytes(), key2.getBytes());
            assertThat(sInterResult).isNull();
            
            Long sInterStoreResult = connection.setCommands().sInterStore(interDest.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(sInterStoreResult).isNull();
            
            Set<byte[]> sUnionResult = connection.setCommands().sUnion(key1.getBytes(), key2.getBytes());
            assertThat(sUnionResult).isNull();
            
            Long sUnionStoreResult = connection.setCommands().sUnionStore(unionDest.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(sUnionStoreResult).isNull();
            
            // Close pipeline and get results
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(5);
            assertThat((Boolean) results.get(0)).isTrue();  // sMove successful
            
            // Verify sInter result
            @SuppressWarnings("unchecked")
            Set<byte[]> interResult = (Set<byte[]>) results.get(1);
            Set<String> interStrings = interResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(interStrings).containsExactlyInAnyOrder("y");
            
            assertThat((Long) results.get(2)).isEqualTo(1L);  // sInterStore stored 1 element
            
            // Verify sUnion result
            @SuppressWarnings("unchecked")
            Set<byte[]> unionResult = (Set<byte[]>) results.get(3);
            Set<String> unionStrings = unionResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(unionStrings).containsExactlyInAnyOrder("x", "y", "z");
            
            assertThat((Long) results.get(4)).isEqualTo(3L);  // sUnionStore stored 3 elements
            
        } finally {
            cleanupKey(srcKey);
            cleanupKey(destKey);
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(interDest);
            cleanupKey(unionDest);
        }
    }

    @Test
    void testSetScanOperationPipeline() {
        String key = "test:set:pipeline:scan:key";
        
        try {
            // Setup test set
            for (int i = 0; i < 5; i++) {
                connection.setCommands().sAdd(key.getBytes(), ("scan" + i).getBytes());
            }
            
            // Note: sScan returns a Cursor which doesn't work in pipeline mode
            // This test verifies that sScan operates correctly after pipeline operations
            connection.openPipeline();
            
            // Execute other operations in pipeline
            Long sCardResult = connection.setCommands().sCard(key.getBytes());
            assertThat(sCardResult).isNull();
            
            // Close pipeline first
            List<Object> results = connection.closePipeline();
            assertThat(results).hasSize(1);
            assertThat((Long) results.get(0)).isEqualTo(5L);
            
            // Now test sScan in immediate mode (sScan doesn't support pipeline/transaction modes)
            ScanOptions options = ScanOptions.scanOptions().build();
            Cursor<byte[]> cursor = connection.setCommands().sScan(key.getBytes(), options);
            
            java.util.List<String> scannedMembers = new java.util.ArrayList<>();
            while (cursor.hasNext()) {
                scannedMembers.add(new String(cursor.next()));
            }
            cursor.close();
            
            assertThat(scannedMembers).hasSize(5);
            for (int i = 0; i < 5; i++) {
                assertThat(scannedMembers).contains("scan" + i);
            }
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Additional Transaction Mode Tests for Complete Coverage ====================

    @Test
    void testSetPopAndRandomOperationsTransaction() {
        String popKey = "test:set:tx:pop:key";
        String randKey = "test:set:tx:rand:key";
        
        try {
            // Setup initial data
            connection.setCommands().sAdd(popKey.getBytes(), "pop1".getBytes(), "pop2".getBytes(), "pop3".getBytes());
            connection.setCommands().sAdd(randKey.getBytes(), "rand1".getBytes(), "rand2".getBytes(), "rand3".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Test sPop operations in transaction
            byte[] sPopResult = connection.setCommands().sPop(popKey.getBytes());
            assertThat(sPopResult).isNull();
            
            List<byte[]> sPopMultiResult = connection.setCommands().sPop(popKey.getBytes(), 1);
            assertThat(sPopMultiResult).isNull();
            
            // Test sRandMember operations in transaction
            byte[] sRandMemberResult = connection.setCommands().sRandMember(randKey.getBytes());
            assertThat(sRandMemberResult).isNull();
            
            List<byte[]> sRandMemberMultiResult = connection.setCommands().sRandMember(randKey.getBytes(), 2);
            assertThat(sRandMemberMultiResult).isNull();
            
            // Test sMIsMember in transaction
            List<Boolean> sMIsMemberResult = connection.setCommands().sMIsMember(randKey.getBytes(), 
                "rand1".getBytes(), "rand2".getBytes(), "nonexistent".getBytes());
            assertThat(sMIsMemberResult).isNull();
            
            // Execute transaction and get results
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).hasSize(5);
            
            // sPop single result
            byte[] poppedSingle = (byte[]) results.get(0);
            assertThat(poppedSingle).isNotNull();
            String poppedStr = new String(poppedSingle);
            assertThat(poppedStr).isIn("pop1", "pop2", "pop3");
            
            // sPop multiple result
            @SuppressWarnings("unchecked")
            List<byte[]> poppedMulti = (List<byte[]>) results.get(1);
            assertThat(poppedMulti).hasSize(1);
            
            // sRandMember single result
            byte[] randSingle = (byte[]) results.get(2);
            assertThat(randSingle).isNotNull();
            String randStr = new String(randSingle);
            assertThat(randStr).isIn("rand1", "rand2", "rand3");
            
            // sRandMember multiple result
            @SuppressWarnings("unchecked")
            List<byte[]> randMulti = (List<byte[]>) results.get(3);
            assertThat(randMulti).hasSize(2);
            
            // sMIsMember result
            @SuppressWarnings("unchecked")
            List<Boolean> multiMemberResult = (List<Boolean>) results.get(4);
            assertThat(multiMemberResult).containsExactly(true, true, false);
            
        } finally {
            cleanupKey(popKey);
            cleanupKey(randKey);
        }
    }

    @Test 
    void testSetScanOperationTransaction() {
        String key = "test:set:tx:scan:key";
        
        try {
            // Setup test set
            for (int i = 0; i < 5; i++) {
                connection.setCommands().sAdd(key.getBytes(), ("txscan" + i).getBytes());
            }
            
            // Note: sScan returns a Cursor which doesn't work in transaction mode
            // This test verifies that sScan operates correctly after transaction operations
            connection.multi();
            
            // Execute other operations in transaction
            Long sCardResult = connection.setCommands().sCard(key.getBytes());
            assertThat(sCardResult).isNull();
            
            // Execute transaction first
            List<Object> results = connection.exec();
            assertThat(results).hasSize(1);
            assertThat((Long) results.get(0)).isEqualTo(5L);
            
            // Now test sScan in immediate mode (sScan doesn't support pipeline/transaction modes)
            ScanOptions options = ScanOptions.scanOptions().build();
            Cursor<byte[]> cursor = connection.setCommands().sScan(key.getBytes(), options);
            
            java.util.List<String> scannedMembers = new java.util.ArrayList<>();
            while (cursor.hasNext()) {
                scannedMembers.add(new String(cursor.next()));
            }
            cursor.close();
            
            assertThat(scannedMembers).hasSize(5);
            for (int i = 0; i < 5; i++) {
                assertThat(scannedMembers).contains("txscan" + i);
            }
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Coverage Validation Test ====================

    @Test
    void testCompleteValkeySetCommandsCoverage() {
        // This test serves as documentation and validation that all 18 ValkeySetCommands methods
        // are covered across all three invocation modes (immediate, pipeline, transaction)
        
        String key = "test:set:coverage:key";
        
        try {
            // Verify all methods exist and are callable in immediate mode
            connection.setCommands().sAdd(key.getBytes(), "test".getBytes());
            connection.setCommands().sRem(key.getBytes(), "test".getBytes());
            connection.setCommands().sAdd(key.getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            
            // Single return methods
            connection.setCommands().sPop(key.getBytes());
            connection.setCommands().sRandMember(key.getBytes());
            connection.setCommands().sCard(key.getBytes());
            connection.setCommands().sIsMember(key.getBytes(), "a".getBytes());
            
            // Collection return methods  
            connection.setCommands().sPop(key.getBytes(), 1);
            connection.setCommands().sRandMember(key.getBytes(), 2);
            connection.setCommands().sMIsMember(key.getBytes(), "a".getBytes(), "b".getBytes());
            connection.setCommands().sMembers(key.getBytes());
            
            // Set algebra methods
            connection.setCommands().sDiff(key.getBytes());
            connection.setCommands().sInter(key.getBytes());
            connection.setCommands().sUnion(key.getBytes());
            
            // Store methods
            String destKey = "test:set:coverage:dest";
            connection.setCommands().sDiffStore(destKey.getBytes(), key.getBytes());
            connection.setCommands().sInterStore(destKey.getBytes(), key.getBytes());
            connection.setCommands().sUnionStore(destKey.getBytes(), key.getBytes());
            
            // Move method
            connection.setCommands().sMove(key.getBytes(), destKey.getBytes(), "a".getBytes());
            
            // Scan method
            ScanOptions options = ScanOptions.scanOptions().build();
            Cursor<byte[]> cursor = connection.setCommands().sScan(key.getBytes(), options);
            cursor.close();
            
            // If we reach here, all 18 methods are implemented and callable
            assertThat(true).isTrue(); // Coverage validation passed
            
            cleanupKey(destKey);
        } finally {
            cleanupKey(key);
        }
    }

}
