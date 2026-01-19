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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.Limit;
import io.valkey.springframework.data.valkey.connection.ValkeyZSetCommands;
import io.valkey.springframework.data.valkey.connection.zset.Aggregate;
import io.valkey.springframework.data.valkey.connection.zset.DefaultTuple;
import io.valkey.springframework.data.valkey.connection.zset.Tuple;
import io.valkey.springframework.data.valkey.connection.zset.Weights;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * sorted set functionality using the ValkeyZSetCommands interface directly.
 * 
 * These tests validate the implementation of all ValkeyZSetCommands methods:
 * - Basic sorted set operations (zAdd, zRem, zCard, zRange, zScore)
 * - Ranking operations (zRank, zRevRank)
 * - Random operations (zRandMember, zPopMin, zPopMax)
 * - Blocking operations (bZPopMin, bZPopMax)
 * - Range operations (zRangeByScore, zRangeByLex, zRevRange)
 * - Counting operations (zCount, zLexCount)
 * - Set algebra operations (zDiff, zInter, zUnion)
 * - Store operations (zDiffStore, zInterStore, zUnionStore)
 * - Range store operations (zRangeStore variants)
 * - Removal operations (zRemRange, zRemRangeByScore, zRemRangeByLex)
 * - Scanning operations (zScan)
 * - Error handling and edge cases
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionZSetCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:zset:add:key", "test:zset:add:single:key", "test:zset:rem:key", "test:zset:mscore:key",
            "test:zset:incrby:key", "test:zset:range:key", "test:zset:rangebyscore:key", "test:zset:rangebylex:key",
            "test:zset:rank:key", "test:zset:count:key", "test:zset:lexcount:key", "test:zset:randmember:key",
            "test:zset:pop:key", "test:zset:diff:key1", "test:zset:diff:key2", "test:zset:diff:dest",
            "test:zset:inter:key1", "test:zset:inter:key2", "test:zset:inter:dest", "test:zset:union:key1",
            "test:zset:union:key2", "test:zset:union:dest", "test:zset:rangestore:src", "test:zset:rangestore:dest",
            "test:zset:rangestore:srclex", "test:zset:remrange:key", "test:zset:scan:key", "test:zset:lex:bounds:key",
            "test:zset:lex:edge:key", "test:zset:lex:complex:key"
        };
    }

    // ==================== Basic ZSet Operations ====================

    @Test
    void testZAddAndZCard() {
        String key = "test:zset:add:key";
        
        try {
            // Test zCard on non-existent key
            Long cardEmpty = connection.zSetCommands().zCard(key.getBytes());
            assertThat(cardEmpty).isEqualTo(0L);
            
            // Test adding single tuple
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            
            Long addResult1 = connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            assertThat(addResult1).isEqualTo(1L);
            
            // Test cardinality after adding one element
            Long card1 = connection.zSetCommands().zCard(key.getBytes());
            assertThat(card1).isEqualTo(1L);
            
            // Test adding multiple tuples
            tuples.clear();
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            
            Long addResult2 = connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            assertThat(addResult2).isEqualTo(2L);
            
            // Test cardinality after adding three elements
            Long card3 = connection.zSetCommands().zCard(key.getBytes());
            assertThat(card3).isEqualTo(3L);
            
            // Test adding duplicate value with different score
            tuples.clear();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.5));
            
            Long addDuplicate = connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            assertThat(addDuplicate).isEqualTo(0L); // Should update existing, not add new
            
            // Cardinality should remain the same
            Long cardSame = connection.zSetCommands().zCard(key.getBytes());
            assertThat(cardSame).isEqualTo(3L);
            
            // But score should be updated
            Double score = connection.zSetCommands().zScore(key.getBytes(), "member1".getBytes());
            assertThat(score).isEqualTo(1.5);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZAddSingleValueWithArgs() {
        String key = "test:zset:add:single:key";
        
        try {
            // Test adding with NX flag (only if not exists)
            Boolean addNX1 = connection.zSetCommands().zAdd(key.getBytes(), 1.0, "member1".getBytes(), 
                ValkeyZSetCommands.ZAddArgs.empty().nx());
            assertThat(addNX1).isTrue();
            
            // Try to add same member with NX flag (should return false)
            Boolean addNX2 = connection.zSetCommands().zAdd(key.getBytes(), 2.0, "member1".getBytes(), 
                ValkeyZSetCommands.ZAddArgs.empty().nx());
            assertThat(addNX2).isFalse();
            
            // Score should remain unchanged
            Double score = connection.zSetCommands().zScore(key.getBytes(), "member1".getBytes());
            assertThat(score).isEqualTo(1.0);
            
            // Test adding with XX flag (only if exists)
            Boolean addXX1 = connection.zSetCommands().zAdd(key.getBytes(), 1.5, "member1".getBytes(), 
                ValkeyZSetCommands.ZAddArgs.empty().xx());
            assertThat(addXX1).isFalse();
            
            // Score should be updated
            Double updatedScore = connection.zSetCommands().zScore(key.getBytes(), "member1".getBytes());
            assertThat(updatedScore).isEqualTo(1.5);
            
            // Try to add new member with XX flag (should return false)
            Boolean addXX2 = connection.zSetCommands().zAdd(key.getBytes(), 2.0, "member2".getBytes(), 
                ValkeyZSetCommands.ZAddArgs.empty().xx());
            assertThat(addXX2).isFalse();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZRemAndZScore() {
        String key = "test:zset:rem:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zScore for existing members
            Double score1 = connection.zSetCommands().zScore(key.getBytes(), "member1".getBytes());
            assertThat(score1).isEqualTo(1.0);
            
            Double score2 = connection.zSetCommands().zScore(key.getBytes(), "member2".getBytes());
            assertThat(score2).isEqualTo(2.0);
            
            // Test zScore for non-existent member
            Double scoreNonExistent = connection.zSetCommands().zScore(key.getBytes(), "nonexistent".getBytes());
            assertThat(scoreNonExistent).isNull();
            
            // Test removing single member
            Long remResult1 = connection.zSetCommands().zRem(key.getBytes(), "member1".getBytes());
            assertThat(remResult1).isEqualTo(1L);
            
            // Verify member was removed
            Double removedScore = connection.zSetCommands().zScore(key.getBytes(), "member1".getBytes());
            assertThat(removedScore).isNull();
            
            // Test removing multiple members
            Long remResult2 = connection.zSetCommands().zRem(key.getBytes(), 
                "member2".getBytes(), "member3".getBytes());
            assertThat(remResult2).isEqualTo(2L);
            
            // Test removing non-existent member
            Long remNonExistent = connection.zSetCommands().zRem(key.getBytes(), "nonexistent".getBytes());
            assertThat(remNonExistent).isEqualTo(0L);
            
            // Verify set is empty
            Long card = connection.zSetCommands().zCard(key.getBytes());
            assertThat(card).isEqualTo(0L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZMScore() {
        String key = "test:zset:mscore:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zMScore for multiple members
            List<Double> scores = connection.zSetCommands().zMScore(key.getBytes(), 
                "member1".getBytes(), "member2".getBytes(), "nonexistent".getBytes(), "member3".getBytes());
            
            assertThat(scores).hasSize(4);
            assertThat(scores.get(0)).isEqualTo(1.0);
            assertThat(scores.get(1)).isEqualTo(2.0);
            assertThat(scores.get(2)).isNull(); // nonexistent member
            assertThat(scores.get(3)).isEqualTo(3.0);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZIncrBy() {
        String key = "test:zset:incrby:key";
        
        try {
            // Add initial member
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test incrementing existing member
            Double newScore1 = connection.zSetCommands().zIncrBy(key.getBytes(), 2.5, "member1".getBytes());
            assertThat(newScore1).isEqualTo(3.5);
            
            // Verify score was updated
            Double score = connection.zSetCommands().zScore(key.getBytes(), "member1".getBytes());
            assertThat(score).isEqualTo(3.5);
            
            // Test incrementing non-existent member (should create with increment as score)
            Double newScore2 = connection.zSetCommands().zIncrBy(key.getBytes(), 5.0, "member2".getBytes());
            assertThat(newScore2).isEqualTo(5.0);
            
            // Test negative increment
            Double newScore3 = connection.zSetCommands().zIncrBy(key.getBytes(), -1.5, "member1".getBytes());
            assertThat(newScore3).isEqualTo(2.0);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Range Operations ====================

    @Test
    void testZRangeAndZRevRange() {
        String key = "test:zset:range:key";
        
        try {
            // Setup test data with different scores
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zRange (ascending order)
            Set<byte[]> range1 = connection.zSetCommands().zRange(key.getBytes(), 0, 2);
            assertThat(range1).hasSize(3);
            List<String> rangeList1 = range1.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(rangeList1).containsExactly("member1", "member2", "member3");
            
            // Test zRange with negative indices
            Set<byte[]> range2 = connection.zSetCommands().zRange(key.getBytes(), -2, -1);
            assertThat(range2).hasSize(2);
            List<String> rangeList2 = range2.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(rangeList2).containsExactly("member4", "member5");
            
            // Test zRangeWithScores
            Set<Tuple> rangeWithScores = connection.zSetCommands().zRangeWithScores(key.getBytes(), 0, 2);
            assertThat(rangeWithScores).hasSize(3);
            
            // Test zRevRange (descending order)
            Set<byte[]> revRange = connection.zSetCommands().zRevRange(key.getBytes(), 0, 2);
            assertThat(revRange).hasSize(3);
            List<String> revRangeList = revRange.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(revRangeList).containsExactly("member5", "member4", "member3");
            
            // Test zRevRangeWithScores
            Set<Tuple> revRangeWithScores = connection.zSetCommands().zRevRangeWithScores(key.getBytes(), 0, 2);
            assertThat(revRangeWithScores).hasSize(3);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZRangeByScore() {
        String key = "test:zset:rangebyscore:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zRangeByScore with Range
            Range<Double> range1 = Range.closed(2.0, 4.0);
            Set<byte[]> rangeResult1 = connection.zSetCommands().zRangeByScore(key.getBytes(), 
                range1.map(Number.class::cast), Limit.unlimited());
            assertThat(rangeResult1).hasSize(3);
            
            // Test zRangeByScore with string bounds
            Set<byte[]> rangeResult2 = connection.zSetCommands().zRangeByScore(key.getBytes(), 
                "2.0", "4.0", 0, 10);
            assertThat(rangeResult2).hasSize(3);
            
            // Test zRangeByScore with limit
            Set<byte[]> rangeResult3 = connection.zSetCommands().zRangeByScore(key.getBytes(), 
                range1.map(Number.class::cast), Limit.limit().count(2));
            assertThat(rangeResult3).hasSize(2);
            
            // Test zRangeByScoreWithScores
            Set<Tuple> rangeWithScores = connection.zSetCommands().zRangeByScoreWithScores(key.getBytes(), 
                range1.map(Number.class::cast), Limit.unlimited());
            assertThat(rangeWithScores).hasSize(3);
            
            // Test zRevRangeByScore
            Set<byte[]> revRangeResult = connection.zSetCommands().zRevRangeByScore(key.getBytes(), 
                range1.map(Number.class::cast), Limit.unlimited());
            assertThat(revRangeResult).hasSize(3);
            
            // Test zRevRangeByScoreWithScores
            Set<Tuple> revRangeWithScores = connection.zSetCommands().zRevRangeByScoreWithScores(key.getBytes(), 
                range1.map(Number.class::cast), Limit.unlimited());
            assertThat(revRangeWithScores).hasSize(3);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZRangeByLex() {
        String key = "test:zset:rangebylex:key";
        
        try {
            // Setup test data with same score for lexicographical ordering
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("a".getBytes(), 0.0));
            tuples.add(new DefaultTuple("b".getBytes(), 0.0));
            tuples.add(new DefaultTuple("c".getBytes(), 0.0));
            tuples.add(new DefaultTuple("d".getBytes(), 0.0));
            tuples.add(new DefaultTuple("e".getBytes(), 0.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zRangeByLex
            Range<byte[]> lexRange = Range.closed("b".getBytes(), "d".getBytes());
            Set<byte[]> lexResult = connection.zSetCommands().zRangeByLex(key.getBytes(), 
                lexRange, Limit.unlimited());
            assertThat(lexResult).hasSize(3);
            
            List<String> lexResultList = lexResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(lexResultList).containsExactly("b", "c", "d");
            
            // Test zRevRangeByLex - should return results in reverse lexicographic order
            Set<byte[]> revLexResult = connection.zSetCommands().zRevRangeByLex(key.getBytes(), 
                lexRange, Limit.unlimited());
            assertThat(revLexResult).hasSize(3);
            
            List<String> revLexResultList = revLexResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(revLexResultList).containsExactly("d", "c", "b");
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Ranking Operations ====================

    @Test
    void testZRankAndZRevRank() {
        String key = "test:zset:rank:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zRank (0-based, ascending)
            Long rank1 = connection.zSetCommands().zRank(key.getBytes(), "member1".getBytes());
            assertThat(rank1).isEqualTo(0L);
            
            Long rank2 = connection.zSetCommands().zRank(key.getBytes(), "member2".getBytes());
            assertThat(rank2).isEqualTo(1L);
            
            Long rank4 = connection.zSetCommands().zRank(key.getBytes(), "member4".getBytes());
            assertThat(rank4).isEqualTo(3L);
            
            // Test zRank for non-existent member
            Long rankNonExistent = connection.zSetCommands().zRank(key.getBytes(), "nonexistent".getBytes());
            assertThat(rankNonExistent).isNull();
            
            // Test zRevRank (0-based, descending)
            Long revRank1 = connection.zSetCommands().zRevRank(key.getBytes(), "member1".getBytes());
            assertThat(revRank1).isEqualTo(3L);
            
            Long revRank4 = connection.zSetCommands().zRevRank(key.getBytes(), "member4".getBytes());
            assertThat(revRank4).isEqualTo(0L);
            
            // Test zRevRank for non-existent member
            Long revRankNonExistent = connection.zSetCommands().zRevRank(key.getBytes(), "nonexistent".getBytes());
            assertThat(revRankNonExistent).isNull();
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Counting Operations ====================

    @Test
    void testZCount() {
        String key = "test:zset:count:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zCount with inclusive range
            Range<Double> range1 = Range.closed(2.0, 4.0);
            Long count1 = connection.zSetCommands().zCount(key.getBytes(), range1.map(Number.class::cast));
            assertThat(count1).isEqualTo(3L);
            
            // Test zCount with exclusive range
            Range<Double> range2 = Range.open(2.0, 4.0);
            Long count2 = connection.zSetCommands().zCount(key.getBytes(), range2.map(Number.class::cast));
            assertThat(count2).isEqualTo(1L); // Only member3 with score 3.0
            
            // Test zCount with unbounded range
            Range<Double> range3 = Range.from(Range.Bound.exclusive(3.0)).to(Range.Bound.unbounded());
            Long count3 = connection.zSetCommands().zCount(key.getBytes(), range3.map(Number.class::cast));
            assertThat(count3).isEqualTo(2L); // member4 and member5
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZLexCount() {
        String key = "test:zset:lexcount:key";
        
        try {
            // Setup test data with same score for lexicographical ordering
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("a".getBytes(), 0.0));
            tuples.add(new DefaultTuple("b".getBytes(), 0.0));
            tuples.add(new DefaultTuple("c".getBytes(), 0.0));
            tuples.add(new DefaultTuple("d".getBytes(), 0.0));
            tuples.add(new DefaultTuple("e".getBytes(), 0.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zLexCount with inclusive range
            Range<byte[]> lexRange1 = Range.closed("b".getBytes(), "d".getBytes());
            Long lexCount1 = connection.zSetCommands().zLexCount(key.getBytes(), lexRange1);
            assertThat(lexCount1).isEqualTo(3L);
            
            // Test zLexCount with exclusive range
            Range<byte[]> lexRange2 = Range.open("b".getBytes(), "d".getBytes());
            Long lexCount2 = connection.zSetCommands().zLexCount(key.getBytes(), lexRange2);
            assertThat(lexCount2).isEqualTo(1L); // Only "c"
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Random and Pop Operations ====================

    @Test
    void testZRandMember() {
        String key = "test:zset:randmember:key";
        
        try {
            // Test zRandMember on empty set
            byte[] emptyRand = connection.zSetCommands().zRandMember(key.getBytes());
            assertThat(emptyRand).isNull();
            
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test single random member
            byte[] randomMember = connection.zSetCommands().zRandMember(key.getBytes());
            assertThat(randomMember).isNotNull();
            String randomMemberStr = new String(randomMember);
            assertThat(randomMemberStr).isIn("member1", "member2", "member3");
            
            // Test multiple random members
            List<byte[]> randomMembers = connection.zSetCommands().zRandMember(key.getBytes(), 2);
            assertThat(randomMembers).hasSize(2);
            
            // Test zRandMemberWithScore
            Tuple randomTuple = connection.zSetCommands().zRandMemberWithScore(key.getBytes());
            assertThat(randomTuple).isNotNull();
            assertThat(randomTuple.getScore()).isIn(1.0, 2.0, 3.0);
            
            // Test zRandMemberWithScore with count
            List<Tuple> randomTuples = connection.zSetCommands().zRandMemberWithScore(key.getBytes(), 2);
            assertThat(randomTuples).hasSize(2);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZPopMinAndZPopMax() {
        String key = "test:zset:pop:key";
        
        try {
            // Test pop on empty set
            Tuple emptyPop = connection.zSetCommands().zPopMin(key.getBytes());
            assertThat(emptyPop).isNull();
            
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zPopMin single
            Tuple minTuple = connection.zSetCommands().zPopMin(key.getBytes());
            assertThat(minTuple).isNotNull();
            assertThat(minTuple.getScore()).isEqualTo(1.0);
            assertThat(new String(minTuple.getValue())).isEqualTo("member1");
            
            // Test zPopMax single
            Tuple maxTuple = connection.zSetCommands().zPopMax(key.getBytes());
            assertThat(maxTuple).isNotNull();
            assertThat(maxTuple.getScore()).isEqualTo(3.0);
            assertThat(new String(maxTuple.getValue())).isEqualTo("member3");
            
            // Add more data for multiple pop tests
            tuples.clear();
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zPopMin multiple
            Set<Tuple> minTuples = connection.zSetCommands().zPopMin(key.getBytes(), 2);
            assertThat(minTuples).hasSize(2);
            
            // Test zPopMax multiple
            Set<Tuple> maxTuples = connection.zSetCommands().zPopMax(key.getBytes(), 2);
            assertThat(maxTuples).hasSize(1); // Only 1 element remaining after previous operations
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZPopMinAndZPopMaxOrdering() {
        String key = "test:zset:pop:ordering:key";
        
        try {
            // Setup test data with specific scores to test ordering
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("low".getBytes(), 1.0));
            tuples.add(new DefaultTuple("medium".getBytes(), 2.0));
            tuples.add(new DefaultTuple("high".getBytes(), 3.0));
            tuples.add(new DefaultTuple("highest".getBytes(), 4.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zPopMin multiple - should return in ascending order (lowest scores first)
            Set<Tuple> minTuples = connection.zSetCommands().zPopMin(key.getBytes(), 2);
            assertThat(minTuples).hasSize(2);
            
            // Convert to list to check ordering
            java.util.List<Tuple> minList = new java.util.ArrayList<>(minTuples);
            assertThat(minList.get(0).getScore()).isEqualTo(1.0);
            assertThat(new String(minList.get(0).getValue())).isEqualTo("low");
            assertThat(minList.get(1).getScore()).isEqualTo(2.0);
            assertThat(new String(minList.get(1).getValue())).isEqualTo("medium");
            
            // Test zPopMax multiple - should return in descending order (highest scores first)
            Set<Tuple> maxTuples = connection.zSetCommands().zPopMax(key.getBytes(), 2);
            assertThat(maxTuples).hasSize(2);
            
            // Convert to list to check ordering
            java.util.List<Tuple> maxList = new java.util.ArrayList<>(maxTuples);
            assertThat(maxList.get(0).getScore()).isEqualTo(4.0);
            assertThat(new String(maxList.get(0).getValue())).isEqualTo("highest");
            assertThat(maxList.get(1).getScore()).isEqualTo(3.0);
            assertThat(new String(maxList.get(1).getValue())).isEqualTo("high");
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testBlockingPopOperations() {
        String key = "test:zset:blocking:pop:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test bZPopMin with short timeout (should return immediately since data exists)
            Tuple minTuple = connection.zSetCommands().bZPopMin(key.getBytes(), 1, TimeUnit.SECONDS);
            assertThat(minTuple).isNotNull();
            assertThat(minTuple.getScore()).isEqualTo(1.0);
            assertThat(new String(minTuple.getValue())).isEqualTo("member1");
            
            // Test bZPopMax with short timeout (should return immediately since data exists)
            Tuple maxTuple = connection.zSetCommands().bZPopMax(key.getBytes(), 1, TimeUnit.SECONDS);
            assertThat(maxTuple).isNotNull();
            assertThat(maxTuple.getScore()).isEqualTo(2.0);
            assertThat(new String(maxTuple.getValue())).isEqualTo("member2");
            
            // Test bZPopMin on empty set with short timeout (should return null after timeout)
            Tuple emptyResult = connection.zSetCommands().bZPopMin(key.getBytes(), 1, TimeUnit.SECONDS);
            assertThat(emptyResult).isNull();
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Set Algebra Operations ====================

    @Test
    void testZDiff() {
        String key1 = "test:zset:diff:key1";
        String key2 = "test:zset:diff:key2";
        
        try {
            // Setup test data
            Set<Tuple> tuples1 = new HashSet<>();
            tuples1.add(new DefaultTuple("a".getBytes(), 1.0));
            tuples1.add(new DefaultTuple("b".getBytes(), 2.0));
            tuples1.add(new DefaultTuple("c".getBytes(), 3.0));
            
            Set<Tuple> tuples2 = new HashSet<>();
            tuples2.add(new DefaultTuple("b".getBytes(), 2.5));
            tuples2.add(new DefaultTuple("d".getBytes(), 4.0));
            
            connection.zSetCommands().zAdd(key1.getBytes(), tuples1, 
                ValkeyZSetCommands.ZAddArgs.empty());
            connection.zSetCommands().zAdd(key2.getBytes(), tuples2, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zDiff
            Set<byte[]> diff = connection.zSetCommands().zDiff(key1.getBytes(), key2.getBytes());
            assertThat(diff).hasSize(2);
            Set<String> diffStrings = diff.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(diffStrings).containsExactlyInAnyOrder("a", "c");
            
            // Test zDiffWithScores
            Set<Tuple> diffWithScores = connection.zSetCommands().zDiffWithScores(key1.getBytes(), key2.getBytes());
            assertThat(diffWithScores).hasSize(2);
            
            // Test zDiffStore
            String destKey = "test:zset:diff:dest";
            Long storeResult = connection.zSetCommands().zDiffStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(storeResult).isEqualTo(2L);
            
            cleanupKey(destKey);
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testZInter() {
        String key1 = "test:zset:inter:key1";
        String key2 = "test:zset:inter:key2";
        
        try {
            // Setup test data
            Set<Tuple> tuples1 = new HashSet<>();
            tuples1.add(new DefaultTuple("a".getBytes(), 1.0));
            tuples1.add(new DefaultTuple("b".getBytes(), 2.0));
            tuples1.add(new DefaultTuple("c".getBytes(), 3.0));
            
            Set<Tuple> tuples2 = new HashSet<>();
            tuples2.add(new DefaultTuple("b".getBytes(), 2.5));
            tuples2.add(new DefaultTuple("c".getBytes(), 3.5));
            tuples2.add(new DefaultTuple("d".getBytes(), 4.0));
            
            connection.zSetCommands().zAdd(key1.getBytes(), tuples1, 
                ValkeyZSetCommands.ZAddArgs.empty());
            connection.zSetCommands().zAdd(key2.getBytes(), tuples2, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zInter
            Set<byte[]> inter = connection.zSetCommands().zInter(key1.getBytes(), key2.getBytes());
            assertThat(inter).hasSize(2);
            Set<String> interStrings = inter.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(interStrings).containsExactlyInAnyOrder("b", "c");
            
            // Test zInterWithScores
            Set<Tuple> interWithScores = connection.zSetCommands().zInterWithScores(key1.getBytes(), key2.getBytes());
            assertThat(interWithScores).hasSize(2);
            
            // Test zInterWithScores with weights and aggregate
            Weights weights = Weights.of(1.0, 2.0);
            Set<Tuple> interWeighted = connection.zSetCommands().zInterWithScores(Aggregate.SUM, weights, 
                key1.getBytes(), key2.getBytes());
            assertThat(interWeighted).hasSize(2);
            
            // Test zInterStore
            String destKey = "test:zset:inter:dest";
            Long storeResult = connection.zSetCommands().zInterStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(storeResult).isEqualTo(2L);
            
            // Test zInterStore with weights and aggregate
            Long storeWeightedResult = connection.zSetCommands().zInterStore(destKey.getBytes(), Aggregate.MAX, weights, 
                key1.getBytes(), key2.getBytes());
            assertThat(storeWeightedResult).isEqualTo(2L);
            
            cleanupKey(destKey);
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    @Test
    void testZUnion() {
        String key1 = "test:zset:union:key1";
        String key2 = "test:zset:union:key2";
        
        try {
            // Setup test data
            Set<Tuple> tuples1 = new HashSet<>();
            tuples1.add(new DefaultTuple("a".getBytes(), 1.0));
            tuples1.add(new DefaultTuple("b".getBytes(), 2.0));
            
            Set<Tuple> tuples2 = new HashSet<>();
            tuples2.add(new DefaultTuple("b".getBytes(), 2.5));
            tuples2.add(new DefaultTuple("c".getBytes(), 3.0));
            
            connection.zSetCommands().zAdd(key1.getBytes(), tuples1, 
                ValkeyZSetCommands.ZAddArgs.empty());
            connection.zSetCommands().zAdd(key2.getBytes(), tuples2, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zUnion
            Set<byte[]> union = connection.zSetCommands().zUnion(key1.getBytes(), key2.getBytes());
            assertThat(union).hasSize(3);
            Set<String> unionStrings = union.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(unionStrings).containsExactlyInAnyOrder("a", "b", "c");
            
            // Test zUnionWithScores
            Set<Tuple> unionWithScores = connection.zSetCommands().zUnionWithScores(key1.getBytes(), key2.getBytes());
            assertThat(unionWithScores).hasSize(3);
            
            // Test zUnionWithScores with weights and aggregate
            Weights weights = Weights.of(1.0, 2.0);
            Set<Tuple> unionWeighted = connection.zSetCommands().zUnionWithScores(Aggregate.MIN, weights, 
                key1.getBytes(), key2.getBytes());
            assertThat(unionWeighted).hasSize(3);
            
            // Test zUnionStore
            String destKey = "test:zset:union:dest";
            Long storeResult = connection.zSetCommands().zUnionStore(destKey.getBytes(), key1.getBytes(), key2.getBytes());
            assertThat(storeResult).isEqualTo(3L);
            
            // Test zUnionStore with weights and aggregate
            Long storeWeightedResult = connection.zSetCommands().zUnionStore(destKey.getBytes(), Aggregate.SUM, weights, 
                key1.getBytes(), key2.getBytes());
            assertThat(storeWeightedResult).isEqualTo(3L);
            
            cleanupKey(destKey);
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
        }
    }

    // ==================== Range Store Operations ====================

    @Test
    void testZRangeStore() {
        String srcKey = "test:zset:rangestore:src";
        String destKey = "test:zset:rangestore:dest";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(srcKey.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zRangeStoreByScore
            Range<Double> scoreRange = Range.closed(2.0, 4.0);
            Long storeByScoreResult = connection.zSetCommands().zRangeStoreByScore(destKey.getBytes(), srcKey.getBytes(), 
                scoreRange.map(Number.class::cast), Limit.unlimited());
            assertThat(storeByScoreResult).isEqualTo(3L);
            
            // Test zRangeStoreRevByScore
            Long storeRevByScoreResult = connection.zSetCommands().zRangeStoreRevByScore(destKey.getBytes(), srcKey.getBytes(), 
                scoreRange.map(Number.class::cast), Limit.unlimited());
            assertThat(storeRevByScoreResult).isEqualTo(3L);
            
            // Test zRangeStoreByLex (need same scores)
            String srcLexKey = "test:zset:rangestore:srclex";
            Set<Tuple> lexTuples = new HashSet<>();
            lexTuples.add(new DefaultTuple("a".getBytes(), 0.0));
            lexTuples.add(new DefaultTuple("b".getBytes(), 0.0));
            lexTuples.add(new DefaultTuple("c".getBytes(), 0.0));
            lexTuples.add(new DefaultTuple("d".getBytes(), 0.0));
            
            connection.zSetCommands().zAdd(srcLexKey.getBytes(), lexTuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            Range<byte[]> lexRange = Range.closed("b".getBytes(), "d".getBytes());
            Long storeByLexResult = connection.zSetCommands().zRangeStoreByLex(destKey.getBytes(), srcLexKey.getBytes(), 
                lexRange, Limit.unlimited());
            assertThat(storeByLexResult).isEqualTo(3L);
            
            // Test zRangeStoreRevByLex
            Long storeRevByLexResult = connection.zSetCommands().zRangeStoreRevByLex(destKey.getBytes(), srcLexKey.getBytes(), 
                lexRange, Limit.unlimited());
            assertThat(storeRevByLexResult).isEqualTo(3L);
            
            cleanupKey(srcLexKey);
        } finally {
            cleanupKey(srcKey);
            cleanupKey(destKey);
        }
    }

    // ==================== Removal Operations ====================

    @Test
    void testZRemRange() {
        String key = "test:zset:remrange:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zRemRange by rank
            Long remRangeResult = connection.zSetCommands().zRemRange(key.getBytes(), 1, 3);
            assertThat(remRangeResult).isEqualTo(3L); // Removed member2, member3, member4
            
            // Verify remaining members
            Long remainingCount = connection.zSetCommands().zCard(key.getBytes());
            assertThat(remainingCount).isEqualTo(2L); // member1 and member5 should remain
            
            // Test zRemRangeByScore
            tuples.clear();
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            Range<Double> scoreRange = Range.closed(2.0, 4.0);
            Long remByScoreResult = connection.zSetCommands().zRemRangeByScore(key.getBytes(), 
                scoreRange.map(Number.class::cast));
            assertThat(remByScoreResult).isEqualTo(3L);
            
            // Test zRemRangeByLex (need same scores)
            tuples.clear();
            tuples.add(new DefaultTuple("a".getBytes(), 0.0));
            tuples.add(new DefaultTuple("b".getBytes(), 0.0));
            tuples.add(new DefaultTuple("c".getBytes(), 0.0));
            tuples.add(new DefaultTuple("d".getBytes(), 0.0));
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            Range<byte[]> lexRange = Range.closed("b".getBytes(), "d".getBytes());
            Long remByLexResult = connection.zSetCommands().zRemRangeByLex(key.getBytes(), lexRange);
            assertThat(remByLexResult).isEqualTo(3L);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Comprehensive Lexicographic Range Tests ====================

    @Test
    void testLexicographicRangeEdgeCases() {
        String key = "test:zset:lex:bounds:key";
        
        try {
            // Setup test data with same score for lexicographical ordering
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("apple".getBytes(), 0.0));
            tuples.add(new DefaultTuple("banana".getBytes(), 0.0));
            tuples.add(new DefaultTuple("cherry".getBytes(), 0.0));
            tuples.add(new DefaultTuple("date".getBytes(), 0.0));
            tuples.add(new DefaultTuple("elderberry".getBytes(), 0.0));
            tuples.add(new DefaultTuple("fig".getBytes(), 0.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test inclusive lower and upper bounds [banana, date]
            Range<byte[]> inclusiveRange = Range.closed("banana".getBytes(), "date".getBytes());
            Set<byte[]> inclusiveResult = connection.zSetCommands().zRangeByLex(key.getBytes(), 
                inclusiveRange, Limit.unlimited());
            assertThat(inclusiveResult).hasSize(3);
            List<String> inclusiveList = inclusiveResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(inclusiveList).containsExactly("banana", "cherry", "date");
            
            // Test exclusive lower bound (banana, elderberry]
            Range<byte[]> exclusiveLowerRange = Range.from(Range.Bound.exclusive("banana".getBytes()))
                .to(Range.Bound.inclusive("elderberry".getBytes()));
            Set<byte[]> exclusiveLowerResult = connection.zSetCommands().zRangeByLex(key.getBytes(), 
                exclusiveLowerRange, Limit.unlimited());
            assertThat(exclusiveLowerResult).hasSize(3);
            List<String> exclusiveLowerList = exclusiveLowerResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(exclusiveLowerList).containsExactly("cherry", "date", "elderberry");
            
            // Test exclusive upper bound [cherry, elderberry)
            Range<byte[]> exclusiveUpperRange = Range.from(Range.Bound.inclusive("cherry".getBytes()))
                .to(Range.Bound.exclusive("elderberry".getBytes()));
            Set<byte[]> exclusiveUpperResult = connection.zSetCommands().zRangeByLex(key.getBytes(), 
                exclusiveUpperRange, Limit.unlimited());
            assertThat(exclusiveUpperResult).hasSize(2);
            List<String> exclusiveUpperList = exclusiveUpperResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(exclusiveUpperList).containsExactly("cherry", "date");
            
            // Test both bounds exclusive (banana, date)
            Range<byte[]> bothExclusiveRange = Range.open("banana".getBytes(), "date".getBytes());
            Set<byte[]> bothExclusiveResult = connection.zSetCommands().zRangeByLex(key.getBytes(), 
                bothExclusiveRange, Limit.unlimited());
            assertThat(bothExclusiveResult).hasSize(1);
            List<String> bothExclusiveList = bothExclusiveResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(bothExclusiveList).containsExactly("cherry");
            
            // Test unbounded lower range (-, date]
            Range<byte[]> unboundedLowerRange = Range.<byte[]>from(Range.Bound.unbounded())
                .to(Range.Bound.inclusive("date".getBytes()));
            Set<byte[]> unboundedLowerResult = connection.zSetCommands().zRangeByLex(key.getBytes(), 
                unboundedLowerRange, Limit.unlimited());
            assertThat(unboundedLowerResult).hasSize(4);
            List<String> unboundedLowerList = unboundedLowerResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(unboundedLowerList).containsExactly("apple", "banana", "cherry", "date");
            
            // Test unbounded upper range [cherry, +)
            Range<byte[]> unboundedUpperRange = Range.<byte[]>from(Range.Bound.inclusive("cherry".getBytes()))
                .to(Range.Bound.unbounded());
            Set<byte[]> unboundedUpperResult = connection.zSetCommands().zRangeByLex(key.getBytes(), 
                unboundedUpperRange, Limit.unlimited());
            assertThat(unboundedUpperResult).hasSize(4);
            List<String> unboundedUpperList = unboundedUpperResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(unboundedUpperList).containsExactly("cherry", "date", "elderberry", "fig");
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testLexicographicCountWithComplexBounds() {
        String key = "test:zset:lex:edge:key";
        
        try {
            // Setup test data with special characters and edge cases
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("!special".getBytes(), 0.0));
            tuples.add(new DefaultTuple("@symbol".getBytes(), 0.0));
            tuples.add(new DefaultTuple("Apple".getBytes(), 0.0));  // Capital A
            tuples.add(new DefaultTuple("apple".getBytes(), 0.0));  // Lower case a
            tuples.add(new DefaultTuple("banana".getBytes(), 0.0));
            tuples.add(new DefaultTuple("z_last".getBytes(), 0.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test count with inclusive bounds including special characters
            Range<byte[]> specialRange = Range.closed("!special".getBytes(), "Apple".getBytes());
            Long specialCount = connection.zSetCommands().zLexCount(key.getBytes(), specialRange);
            assertThat(specialCount).isEqualTo(3L); // !special, @symbol, Apple
            
            // Test count with case-sensitive boundaries
            Range<byte[]> caseRange = Range.closed("Apple".getBytes(), "apple".getBytes());
            Long caseCount = connection.zSetCommands().zLexCount(key.getBytes(), caseRange);
            assertThat(caseCount).isEqualTo(2L); // Apple, apple
            
            // Test count with exclusive bounds
            Range<byte[]> exclusiveRange = Range.open("Apple".getBytes(), "banana".getBytes());
            Long exclusiveCount = connection.zSetCommands().zLexCount(key.getBytes(), exclusiveRange);
            assertThat(exclusiveCount).isEqualTo(1L); // apple
            
            // Test count with unbounded ranges
            Range<byte[]> unboundedRange = Range.from(Range.Bound.inclusive("banana".getBytes()))
                .to(Range.Bound.unbounded());
            Long unboundedCount = connection.zSetCommands().zLexCount(key.getBytes(), unboundedRange);
            assertThat(unboundedCount).isEqualTo(2L); // banana, z_last
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testLexicographicRemovalWithEdgeCases() {
        String key = "test:zset:lex:complex:key";
        
        try {
            // Setup test data with mixed content
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("01_numeric".getBytes(), 0.0));
            tuples.add(new DefaultTuple("alpha".getBytes(), 0.0));
            tuples.add(new DefaultTuple("beta".getBytes(), 0.0));
            tuples.add(new DefaultTuple("gamma".getBytes(), 0.0));
            tuples.add(new DefaultTuple("omega".getBytes(), 0.0));
            tuples.add(new DefaultTuple("zulu".getBytes(), 0.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test removal with inclusive bounds
            Range<byte[]> removalRange = Range.closed("beta".getBytes(), "gamma".getBytes());
            Long removalCount = connection.zSetCommands().zRemRangeByLex(key.getBytes(), removalRange);
            assertThat(removalCount).isEqualTo(2L); // beta, gamma
            
            // Verify remaining elements
            Long remainingCount = connection.zSetCommands().zCard(key.getBytes());
            assertThat(remainingCount).isEqualTo(4L);
            
            // Test removal with exclusive bounds
            Range<byte[]> exclusiveRemovalRange = Range.open("alpha".getBytes(), "omega".getBytes());
            Long exclusiveRemovalCount = connection.zSetCommands().zRemRangeByLex(key.getBytes(), exclusiveRemovalRange);
            assertThat(exclusiveRemovalCount).isEqualTo(0L); // No elements between "alpha" and "omega" exclusively
            
            // Test removal with unbounded lower bound
            Range<byte[]> unboundedRemovalRange = Range.<byte[]>from(Range.Bound.unbounded())
                .to(Range.Bound.inclusive("alpha".getBytes()));
            Long unboundedRemovalCount = connection.zSetCommands().zRemRangeByLex(key.getBytes(), unboundedRemovalRange);
            assertThat(unboundedRemovalCount).isEqualTo(2L); // 01_numeric, alpha
            
            // Final verification
            Long finalCount = connection.zSetCommands().zCard(key.getBytes());
            assertThat(finalCount).isEqualTo(2L); // omega, zulu should remain
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testLexicographicReverseOperationsWithBounds() {
        String key = "test:zset:lex:reverse:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("first".getBytes(), 0.0));
            tuples.add(new DefaultTuple("middle".getBytes(), 0.0));
            tuples.add(new DefaultTuple("second".getBytes(), 0.0));
            tuples.add(new DefaultTuple("third".getBytes(), 0.0));
            tuples.add(new DefaultTuple("zebra".getBytes(), 0.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test zRevRangeByLex with various bound combinations
            Range<byte[]> reverseRange = Range.closed("middle".getBytes(), "third".getBytes());
            Set<byte[]> reverseResult = connection.zSetCommands().zRevRangeByLex(key.getBytes(), 
                reverseRange, Limit.unlimited());
            assertThat(reverseResult).hasSize(3);
            List<String> reverseList = reverseResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(reverseList).containsExactly("third", "second", "middle");
            
            // Test with limit
            Set<byte[]> limitedResult = connection.zSetCommands().zRevRangeByLex(key.getBytes(), 
                reverseRange, Limit.limit().count(2));
            assertThat(limitedResult).hasSize(2);
            List<String> limitedList = limitedResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(limitedList).containsExactly("third", "second");
            
            // Test with offset and limit
            Set<byte[]> offsetResult = connection.zSetCommands().zRevRangeByLex(key.getBytes(), 
                reverseRange, Limit.limit().offset(1).count(1));
            assertThat(offsetResult).hasSize(1);
            List<String> offsetList = offsetResult.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(offsetList).containsExactly("second");
            
            // Test zRangeStoreRevByLex
            String destKey = "test:zset:lex:reverse:dest";
            Long storeResult = connection.zSetCommands().zRangeStoreRevByLex(destKey.getBytes(), key.getBytes(), 
                reverseRange, Limit.unlimited());
            assertThat(storeResult).isEqualTo(3L);
            
            Set<byte[]> storedData = connection.zSetCommands().zRange(destKey.getBytes(), 0, -1);
            List<String> storedList = storedData.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            assertThat(storedList).containsExactly("middle", "second", "third");
            
            cleanupKey(destKey);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Scan Operations ====================

    @Test
    void testZScan() {
        String key = "test:zset:scan:key";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                tuples.add(new DefaultTuple(("member" + i).getBytes(), i * 1.0));
            }
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            
            // Test basic scan
            ScanOptions options = ScanOptions.scanOptions().build();
            Cursor<Tuple> cursor = connection.zSetCommands().zScan(key.getBytes(), options);
            
            java.util.List<Tuple> scannedTuples = new java.util.ArrayList<>();
            while (cursor.hasNext()) {
                scannedTuples.add(cursor.next());
            }
            cursor.close();
            
            // Should find all tuples
            assertThat(scannedTuples).hasSize(10);
            
            // Test scan with pattern
            ScanOptions patternOptions = ScanOptions.scanOptions().match("member[1-3]").build();
            Cursor<Tuple> patternCursor = connection.zSetCommands().zScan(key.getBytes(), patternOptions);
            
            java.util.List<Tuple> patternTuples = new java.util.ArrayList<>();
            while (patternCursor.hasNext()) {
                patternTuples.add(patternCursor.next());
            }
            patternCursor.close();
            
            // Should find fewer tuples due to pattern filter
            assertThat(patternTuples.size()).isLessThanOrEqualTo(3);
            
            // Test scan with count
            ScanOptions countOptions = ScanOptions.scanOptions().count(2).build();
            Cursor<Tuple> countCursor = connection.zSetCommands().zScan(key.getBytes(), countOptions);
            
            java.util.List<Tuple> countTuples = new java.util.ArrayList<>();
            while (countCursor.hasNext()) {
                countTuples.add(countCursor.next());
            }
            countCursor.close();
            
            // Should still find all tuples, but in smaller batches
            assertThat(countTuples).hasSize(10);
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== PIPELINE MODE TESTS ====================
    // All ZSet operations tested in pipeline mode to ensure proper null return handling
    // and correct batch result processing via closePipeline()

    @Test
    void testBasicZSetOperationsInPipelineMode() {
        String key = "test:zset:pipeline:basic";
        
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue basic operations - all should return null in pipeline mode
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            
            assertThat(connection.zSetCommands().zAdd(key.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty())).isNull();
            assertThat(connection.zSetCommands().zCard(key.getBytes())).isNull();
            assertThat(connection.zSetCommands().zScore(key.getBytes(), "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zMScore(key.getBytes(), "member1".getBytes(), "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zRank(key.getBytes(), "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zRevRank(key.getBytes(), "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zIncrBy(key.getBytes(), 1.5, "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zRem(key.getBytes(), "member1".getBytes())).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(8);
            assertThat((Long) results.get(0)).isEqualTo(3L); // zAdd result
            assertThat((Long) results.get(1)).isEqualTo(3L); // zCard result
            assertThat((Double) results.get(2)).isEqualTo(2.0); // zScore result
            
            @SuppressWarnings("unchecked")
            List<Double> mScoreResult = (List<Double>) results.get(3);
            assertThat(mScoreResult).containsExactly(1.0, 2.0); // zMScore result
            
            assertThat((Long) results.get(4)).isEqualTo(1L); // zRank result (0-based)
            assertThat((Long) results.get(5)).isEqualTo(1L); // zRevRank result
            assertThat((Double) results.get(6)).isEqualTo(3.5); // zIncrBy result
            assertThat((Long) results.get(7)).isEqualTo(1L); // zRem result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testRangeOperationsInPipelineMode() {
        String key = "test:zset:pipeline:range";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Start pipeline
            connection.openPipeline();
            
            Range<Double> scoreRange = Range.closed(2.0, 4.0);
            Range<byte[]> lexRange = Range.closed("member2".getBytes(), "member4".getBytes());
            
            // Queue range operations - all should return null in pipeline mode
            assertThat(connection.zSetCommands().zRange(key.getBytes(), 0, 2)).isNull();
            assertThat(connection.zSetCommands().zRangeWithScores(key.getBytes(), 0, 2)).isNull();
            assertThat(connection.zSetCommands().zRevRange(key.getBytes(), 0, 2)).isNull();
            assertThat(connection.zSetCommands().zRevRangeWithScores(key.getBytes(), 0, 2)).isNull();
            assertThat(connection.zSetCommands().zRangeByScore(key.getBytes(), scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zRangeByScoreWithScores(key.getBytes(), scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zRevRangeByScore(key.getBytes(), scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zRevRangeByScoreWithScores(key.getBytes(), scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zCount(key.getBytes(), scoreRange.map(Number.class::cast))).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(9);
            
            @SuppressWarnings("unchecked")
            Set<byte[]> range1 = (Set<byte[]>) results.get(0);
            assertThat(range1).hasSize(3);
            
            @SuppressWarnings("unchecked")
            Set<Tuple> rangeWithScores = (Set<Tuple>) results.get(1);
            assertThat(rangeWithScores).hasSize(3);
            
            assertThat((Long) results.get(8)).isEqualTo(3L); // zCount result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSetAlgebraOperationsInPipelineMode() {
        String key1 = "test:zset:pipeline:set1";
        String key2 = "test:zset:pipeline:set2";
        String destKey = "test:zset:pipeline:dest";
        
        try {
            // Setup test data
            Set<Tuple> tuples1 = new HashSet<>();
            tuples1.add(new DefaultTuple("a".getBytes(), 1.0));
            tuples1.add(new DefaultTuple("b".getBytes(), 2.0));
            tuples1.add(new DefaultTuple("c".getBytes(), 3.0));
            
            Set<Tuple> tuples2 = new HashSet<>();
            tuples2.add(new DefaultTuple("b".getBytes(), 2.5));
            tuples2.add(new DefaultTuple("c".getBytes(), 3.5));
            tuples2.add(new DefaultTuple("d".getBytes(), 4.0));
            
            connection.zSetCommands().zAdd(key1.getBytes(), tuples1, ValkeyZSetCommands.ZAddArgs.empty());
            connection.zSetCommands().zAdd(key2.getBytes(), tuples2, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue set algebra operations - all should return null in pipeline mode
            assertThat(connection.zSetCommands().zDiff(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zDiffWithScores(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zDiffStore(destKey.getBytes(), key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zInter(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zInterWithScores(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zInterStore(destKey.getBytes(), key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zUnion(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zUnionWithScores(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zUnionStore(destKey.getBytes(), key1.getBytes(), key2.getBytes())).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(9);
            
            @SuppressWarnings("unchecked")
            Set<byte[]> diffResult = (Set<byte[]>) results.get(0);
            assertThat(diffResult).hasSize(1); // Only "a" in key1 but not key2
            
            assertThat((Long) results.get(2)).isEqualTo(1L); // zDiffStore result
            assertThat((Long) results.get(5)).isEqualTo(2L); // zInterStore result
            assertThat((Long) results.get(8)).isEqualTo(4L); // zUnionStore result
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testPopAndRandomOperationsInPipelineMode() {
        String key = "test:zset:pipeline:pop";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue pop and random operations - all should return null in pipeline mode
            assertThat(connection.zSetCommands().zRandMember(key.getBytes())).isNull();
            assertThat(connection.zSetCommands().zRandMember(key.getBytes(), 2)).isNull();
            assertThat(connection.zSetCommands().zRandMemberWithScore(key.getBytes())).isNull();
            assertThat(connection.zSetCommands().zRandMemberWithScore(key.getBytes(), 2)).isNull();
            assertThat(connection.zSetCommands().zPopMin(key.getBytes())).isNull();
            assertThat(connection.zSetCommands().zPopMax(key.getBytes())).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(6);
            
            assertThat((byte[]) results.get(0)).isNotNull(); // random member
            
            @SuppressWarnings("unchecked")
            List<byte[]> randomMembers = (List<byte[]>) results.get(1);
            assertThat(randomMembers).hasSize(2);
            
            assertThat((Tuple) results.get(2)).isNotNull(); // random member with score
            
            @SuppressWarnings("unchecked")
            List<Tuple> randomMembersWithScores = (List<Tuple>) results.get(3);
            assertThat(randomMembersWithScores).hasSize(2);
            
            Tuple minTuple = (Tuple) results.get(4);
            assertThat(minTuple).isNotNull();
            assertThat(minTuple.getScore()).isEqualTo(1.0); // Should be the minimum
            
            Tuple maxTuple = (Tuple) results.get(5);
            assertThat(maxTuple).isNotNull();
            assertThat(maxTuple.getScore()).isEqualTo(5.0); // Should be the maximum
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testRangeStoreAndRemovalOperationsInPipelineMode() {
        String srcKey = "test:zset:pipeline:rangestore:src";
        String destKey = "test:zset:pipeline:rangestore:dest";
        String remKey = "test:zset:pipeline:removal:key";
        
        try {
            // Setup test data for range store operations
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(srcKey.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Setup test data for removal operations
            connection.zSetCommands().zAdd(remKey.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Start pipeline
            connection.openPipeline();
            
            Range<Double> scoreRange = Range.closed(2.0, 4.0);
            Range<byte[]> lexRange = Range.closed("member2".getBytes(), "member4".getBytes());
            
            // Queue range store operations - all should return null in pipeline mode
            assertThat(connection.zSetCommands().zRangeStoreByScore(destKey.getBytes(), srcKey.getBytes(), 
                scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zRangeStoreRevByScore(destKey.getBytes(), srcKey.getBytes(), 
                scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            
            // Queue removal operations - all should return null in pipeline mode
            assertThat(connection.zSetCommands().zRemRange(remKey.getBytes(), 1, 3)).isNull();
            assertThat(connection.zSetCommands().zRemRangeByScore(remKey.getBytes(), scoreRange.map(Number.class::cast))).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(4);
            assertThat((Long) results.get(0)).isEqualTo(3L); // zRangeStoreByScore result
            assertThat((Long) results.get(1)).isEqualTo(3L); // zRangeStoreRevByScore result
            assertThat((Long) results.get(2)).isEqualTo(3L); // zRemRange result
            assertThat((Long) results.get(3)).isEqualTo(0L); // zRemRangeByScore result (already removed by previous operation)
            
        } finally {
            cleanupKey(srcKey);
            cleanupKey(destKey);
            cleanupKey(remKey);
        }
    }

    @Test
    void testScanOperationsInPipelineMode() {
        String key = "test:zset:pipeline:scan";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                tuples.add(new DefaultTuple(("member" + i).getBytes(), i * 1.0));
            }
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Start pipeline
            connection.openPipeline();
            
            // Note: zScan with cursor operations are complex in pipeline mode
            // We test that the scan setup doesn't break pipeline mode
            ScanOptions options = ScanOptions.scanOptions().count(2).build();
            
            // We can't easily test zScan in pipeline mode due to cursor nature
            // But we can verify pipeline doesn't break with scan setup
            assertThat(connection.zSetCommands().zCard(key.getBytes())).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(1);
            assertThat((Long) results.get(0)).isEqualTo(5L); // zCard result
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== TRANSACTION MODE TESTS ====================
    // All ZSet operations tested in transaction mode to ensure proper null return handling
    // and correct atomic execution via exec()

    @Test
    void testBasicZSetOperationsInTransactionMode() {
        String key = "test:zset:transaction:basic";
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue basic operations - all should return null in transaction mode
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            
            assertThat(connection.zSetCommands().zAdd(key.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty())).isNull();
            assertThat(connection.zSetCommands().zCard(key.getBytes())).isNull();
            assertThat(connection.zSetCommands().zScore(key.getBytes(), "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zMScore(key.getBytes(), "member1".getBytes(), "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zRank(key.getBytes(), "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zRevRank(key.getBytes(), "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zIncrBy(key.getBytes(), 1.5, "member2".getBytes())).isNull();
            assertThat(connection.zSetCommands().zRem(key.getBytes(), "member1".getBytes())).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(8);
            assertThat((Long) results.get(0)).isEqualTo(3L); // zAdd result
            assertThat((Long) results.get(1)).isEqualTo(3L); // zCard result
            assertThat((Double) results.get(2)).isEqualTo(2.0); // zScore result
            
            @SuppressWarnings("unchecked")
            List<Double> mScoreResult = (List<Double>) results.get(3);
            assertThat(mScoreResult).containsExactly(1.0, 2.0); // zMScore result
            
            assertThat((Long) results.get(4)).isEqualTo(1L); // zRank result (0-based)
            assertThat((Long) results.get(5)).isEqualTo(1L); // zRevRank result
            assertThat((Double) results.get(6)).isEqualTo(3.5); // zIncrBy result
            assertThat((Long) results.get(7)).isEqualTo(1L); // zRem result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testRangeOperationsInTransactionMode() {
        String key = "test:zset:transaction:range";
        
        try {
            // Setup test data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("member1".getBytes(), 1.0));
            tuples.add(new DefaultTuple("member2".getBytes(), 2.0));
            tuples.add(new DefaultTuple("member3".getBytes(), 3.0));
            tuples.add(new DefaultTuple("member4".getBytes(), 4.0));
            tuples.add(new DefaultTuple("member5".getBytes(), 5.0));
            
            connection.zSetCommands().zAdd(key.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Start transaction
            connection.multi();
            
            Range<Double> scoreRange = Range.closed(2.0, 4.0);
            
            // Queue range operations - all should return null in transaction mode
            assertThat(connection.zSetCommands().zRange(key.getBytes(), 0, 2)).isNull();
            assertThat(connection.zSetCommands().zRangeWithScores(key.getBytes(), 0, 2)).isNull();
            assertThat(connection.zSetCommands().zRevRange(key.getBytes(), 0, 2)).isNull();
            assertThat(connection.zSetCommands().zRevRangeWithScores(key.getBytes(), 0, 2)).isNull();
            assertThat(connection.zSetCommands().zRangeByScore(key.getBytes(), scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zRangeByScoreWithScores(key.getBytes(), scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zRevRangeByScore(key.getBytes(), scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zRevRangeByScoreWithScores(key.getBytes(), scoreRange.map(Number.class::cast), Limit.unlimited())).isNull();
            assertThat(connection.zSetCommands().zCount(key.getBytes(), scoreRange.map(Number.class::cast))).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(9);
            
            @SuppressWarnings("unchecked")
            Set<byte[]> range1 = (Set<byte[]>) results.get(0);
            assertThat(range1).hasSize(3);
            
            @SuppressWarnings("unchecked")
            Set<Tuple> rangeWithScores = (Set<Tuple>) results.get(1);
            assertThat(rangeWithScores).hasSize(3);
            
            assertThat((Long) results.get(8)).isEqualTo(3L); // zCount result
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSetAlgebraOperationsInTransactionMode() {
        String key1 = "test:zset:transaction:set1";
        String key2 = "test:zset:transaction:set2";
        String destKey = "test:zset:transaction:dest";
        
        try {
            // Setup test data
            Set<Tuple> tuples1 = new HashSet<>();
            tuples1.add(new DefaultTuple("a".getBytes(), 1.0));
            tuples1.add(new DefaultTuple("b".getBytes(), 2.0));
            tuples1.add(new DefaultTuple("c".getBytes(), 3.0));
            
            Set<Tuple> tuples2 = new HashSet<>();
            tuples2.add(new DefaultTuple("b".getBytes(), 2.5));
            tuples2.add(new DefaultTuple("c".getBytes(), 3.5));
            tuples2.add(new DefaultTuple("d".getBytes(), 4.0));
            
            connection.zSetCommands().zAdd(key1.getBytes(), tuples1, ValkeyZSetCommands.ZAddArgs.empty());
            connection.zSetCommands().zAdd(key2.getBytes(), tuples2, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Start transaction
            connection.multi();
            
            // Queue set algebra operations - all should return null in transaction mode
            assertThat(connection.zSetCommands().zDiff(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zDiffWithScores(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zDiffStore(destKey.getBytes(), key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zInter(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zInterWithScores(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zInterStore(destKey.getBytes(), key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zUnion(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zUnionWithScores(key1.getBytes(), key2.getBytes())).isNull();
            assertThat(connection.zSetCommands().zUnionStore(destKey.getBytes(), key1.getBytes(), key2.getBytes())).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(9);
            
            @SuppressWarnings("unchecked")
            Set<byte[]> diffResult = (Set<byte[]>) results.get(0);
            assertThat(diffResult).hasSize(1); // Only "a" in key1 but not key2
            
            assertThat((Long) results.get(2)).isEqualTo(1L); // zDiffStore result
            assertThat((Long) results.get(5)).isEqualTo(2L); // zInterStore result
            assertThat((Long) results.get(8)).isEqualTo(4L); // zUnionStore result
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(destKey);
        }
    }

    @Test
    void testTransactionDiscardWithZSetCommands() {
        String key = "test:zset:transaction:discard";
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue ZSet commands
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("shouldnotbeset".getBytes(), 1.0));
            connection.zSetCommands().zAdd(key.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            connection.zSetCommands().zScore(key.getBytes(), "shouldnotbeset".getBytes());
            
            // Discard transaction
            connection.discard();
            
            // Verify key does not exist (transaction was discarded)
            Long card = connection.zSetCommands().zCard(key.getBytes());
            assertThat(card).isEqualTo(0L);
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testWatchWithZSetCommandsTransaction() {
        String watchKey = "test:zset:transaction:watch";
        String otherKey = "test:zset:transaction:other";
        
        try {
            // Setup initial data
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("initial".getBytes(), 1.0));
            connection.zSetCommands().zAdd(watchKey.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Watch the key
            connection.watch(watchKey.getBytes());
            
            // Modify the watched key from "outside" the transaction
            tuples.clear();
            tuples.add(new DefaultTuple("modified".getBytes(), 2.0));
            connection.zSetCommands().zAdd(watchKey.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            
            // Start transaction
            connection.multi();
            
            // Queue ZSet commands
            tuples.clear();
            tuples.add(new DefaultTuple("value".getBytes(), 3.0));
            connection.zSetCommands().zAdd(otherKey.getBytes(), tuples, ValkeyZSetCommands.ZAddArgs.empty());
            connection.zSetCommands().zScore(otherKey.getBytes(), "value".getBytes());
            
            // Execute transaction - should be aborted due to WATCH
            List<Object> results = connection.exec();
            
            // Transaction should be aborted (results should be null)
            assertThat(results).isNotNull().isEmpty();
            
            // Verify that the other key was not set
            Long card = connection.zSetCommands().zCard(otherKey.getBytes());
            assertThat(card).isEqualTo(0L);
            
        } finally {
            cleanupKey(watchKey);
            cleanupKey(otherKey);
        }
    }

    // ==================== ADDITIONAL EDGE CASES ====================
    // Additional comprehensive edge case testing for robustness

    @Test
    void testZSetWithBinaryData() {
        String key = "test:zset:binary:data";
        
        try {
            // Test with binary data containing null bytes and various byte values
            byte[] binaryValue = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE, (byte) 0x80};
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple(binaryValue, 1.0));
            
            Long addResult = connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            assertThat(addResult).isEqualTo(1L);
            
            // Verify binary data is preserved exactly
            Double score = connection.zSetCommands().zScore(key.getBytes(), binaryValue);
            assertThat(score).isEqualTo(1.0);
            
            Set<byte[]> range = connection.zSetCommands().zRange(key.getBytes(), 0, -1);
            assertThat(range).hasSize(1);
            assertThat(range.iterator().next()).isEqualTo(binaryValue);
            
            // Test binary data in range operations
            Set<Tuple> rangeWithScores = connection.zSetCommands().zRangeWithScores(key.getBytes(), 0, -1);
            assertThat(rangeWithScores).hasSize(1);
            Tuple tuple = rangeWithScores.iterator().next();
            assertThat(tuple.getValue()).isEqualTo(binaryValue);
            assertThat(tuple.getScore()).isEqualTo(1.0);
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZSetWithExtremeScoreValues() {
        String key = "test:zset:extreme:scores";
        
        try {
            // Test with extreme score values
            Set<Tuple> tuples = new HashSet<>();
            tuples.add(new DefaultTuple("negative_large".getBytes(), -Double.MAX_VALUE / 2));
            tuples.add(new DefaultTuple("negative_small".getBytes(), -0.000001));
            tuples.add(new DefaultTuple("zero".getBytes(), 0.0));
            tuples.add(new DefaultTuple("positive_small".getBytes(), 0.000001));
            tuples.add(new DefaultTuple("positive_large".getBytes(), Double.MAX_VALUE / 2));
            tuples.add(new DefaultTuple("max_precision".getBytes(), 1.7976931348623157E308)); // Near Double.MAX_VALUE
            
            Long addResult = connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            assertThat(addResult).isEqualTo(6L);
            
            // Test ordering is maintained with extreme values
            Set<byte[]> range = connection.zSetCommands().zRange(key.getBytes(), 0, -1);
            List<String> orderedMembers = range.stream()
                .map(String::new)
                .collect(java.util.stream.Collectors.toList());
            
            assertThat(orderedMembers.get(0)).isEqualTo("negative_large");
            assertThat(orderedMembers.get(1)).isEqualTo("negative_small");
            assertThat(orderedMembers.get(2)).isEqualTo("zero");
            assertThat(orderedMembers.get(3)).isEqualTo("positive_small");
            assertThat(orderedMembers.get(4)).isEqualTo("positive_large");
            assertThat(orderedMembers.get(5)).isEqualTo("max_precision");
            
            // Test range operations with extreme values
            Range<Double> extremeRange = Range.closed(-Double.MAX_VALUE, Double.MAX_VALUE);
            Long count = connection.zSetCommands().zCount(key.getBytes(), extremeRange.map(Number.class::cast));
            assertThat(count).isEqualTo(6L);
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testZSetLargeDatasetPerformance() {
        String key = "test:zset:large:dataset:performance";
        
        try {
            // Create a large dataset to test performance characteristics
            Set<Tuple> tuples = new HashSet<>();
            for (int i = 0; i < 10000; i++) {
                tuples.add(new DefaultTuple(("member" + String.format("%05d", i)).getBytes(), i * 1.0));
            }
            
            // Test large add operation
            Long addResult = connection.zSetCommands().zAdd(key.getBytes(), tuples, 
                ValkeyZSetCommands.ZAddArgs.empty());
            assertThat(addResult).isEqualTo(10000L);
            
            // Test cardinality
            Long card = connection.zSetCommands().zCard(key.getBytes());
            assertThat(card).isEqualTo(10000L);
            
            // Test range operations on large dataset
            Set<byte[]> range = connection.zSetCommands().zRange(key.getBytes(), 0, 99);
            assertThat(range).hasSize(100);
            
        } finally {
            cleanupKey(key);
        }
    }

}
