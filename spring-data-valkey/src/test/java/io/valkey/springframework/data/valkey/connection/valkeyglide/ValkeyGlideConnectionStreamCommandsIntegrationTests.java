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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.Limit;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands;
import io.valkey.springframework.data.valkey.connection.stream.*;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoConsumers;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoGroups;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoStream;
import io.valkey.springframework.data.valkey.ValkeySystemException;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * stream functionality using the ValkeyStreamCommands interface directly.
 * 
 * These tests systematically validate all ValkeyStreamCommands methods in three modes:
 * 1. IMMEDIATE MODE - Direct command execution
 * 2. PIPELINE MODE - Batched command execution
 * 3. TRANSACTION MODE - Atomic command execution
 * 
 * Stream Commands Coverage:
 * - Basic operations: xAdd, xDel, xLen, xTrim
 * - Range queries: xRange, xRevRange  
 * - Stream reading: xRead, xReadGroup
 * - Consumer groups: xGroupCreate, xGroupDestroy, xGroupDelConsumer
 * - Acknowledgements: xAck
 * - Message claiming: xClaim, xClaimJustId
 * - Pending messages: xPending (summary and detailed)
 * - Stream information: xInfo, xInfoGroups, xInfoConsumers
 * - Error handling and edge cases
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionStreamCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {
    
    // Helper method to safely get value from byte array map by key content
    private byte[] getValueByKeyContent(Map<byte[], byte[]> map, String keyContent) {
        return map.entrySet().stream()
            .filter(entry -> java.util.Arrays.equals(entry.getKey(), keyContent.getBytes()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:stream:basic", "test:stream:group", "test:stream:consumer",
            "test:stream:read", "test:stream:claim", "test:stream:pending",
            "test:stream:info", "test:stream:range", "test:stream:trim",
            "test:stream:validation", "test:stream:pipeline", "test:stream:transaction",
            "test:stream:edge", "test:stream:multi*", "test:stream:source*", "test:stream:target*"
        };
    }

    // ==================== Basic Stream Operations ====================

    @Test
    void testStreamBasicOperations() {
        String streamKey = "test:stream:basic";
        
        try {
            // Test adding records
            Map<byte[], byte[]> fields1 = Map.of(
                "field1".getBytes(), "value1".getBytes(),
                "field2".getBytes(), "value2".getBytes()
            );
            
            MapRecord<byte[], byte[], byte[]> record1 = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields1);
            
            RecordId id1 = connection.streamCommands().xAdd(record1);
            assertThat(id1).isNotNull();
            assertThat(id1.getValue()).isNotEmpty();
            
            // Test stream length
            Long length = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(length).isEqualTo(1L);
            
            // Add another record with auto-generated ID (avoid potential ID conflicts)
            Map<byte[], byte[]> fields2 = Map.of(
                "field3".getBytes(), "value3".getBytes()
            );
            
            MapRecord<byte[], byte[], byte[]> record2 = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields2);
            
            RecordId id2 = connection.streamCommands().xAdd(record2);
            assertThat(id2).isNotNull();
            assertThat(id2.getValue()).isNotEmpty();
            
            // Test stream length after second record
            length = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(length).isEqualTo(2L);
            
            // Test deleting a record
            Long deletedCount = connection.streamCommands().xDel(streamKey.getBytes(), id1);
            assertThat(deletedCount).isEqualTo(1L);
            
            // Verify length after deletion
            length = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(length).isEqualTo(1L);
            
            // Test deleting non-existent record
            RecordId nonExistentId = RecordId.of("9999-0");
            deletedCount = connection.streamCommands().xDel(streamKey.getBytes(), nonExistentId);
            assertThat(deletedCount).isEqualTo(0L);
            
        } finally {
            cleanupKey(streamKey);
        }
    }

    @Test
    void testStreamWithOptions() {
        String streamKey = "test:stream:options";
        
        try {
            Map<byte[], byte[]> fields = Map.of(
                "field1".getBytes(), "value1".getBytes()
            );
            
            // Test with MAXLEN option
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            
            ValkeyStreamCommands.XAddOptions options = ValkeyStreamCommands.XAddOptions.maxlen(5);
            RecordId id = connection.streamCommands().xAdd(record, options);
            assertThat(id).isNotNull();
            
            // Test with approximation trimming
            ValkeyStreamCommands.XAddOptions approxOptions = ValkeyStreamCommands.XAddOptions.maxlen(10)
                .approximateTrimming(true);
            
            RecordId id2 = connection.streamCommands().xAdd(record, approxOptions);
            assertThat(id2).isNotNull();
            
        } finally {
            cleanupKey(streamKey);
        }
    }

    // ==================== Range Operations ====================

    @Test
    void testStreamRangeOperations() {
        String streamKey = "test:stream:range";
        
        try {
            // Add multiple records
            for (int i = 0; i < 5; i++) {
                Map<byte[], byte[]> fields = Map.of(
                    "index".getBytes(), String.valueOf(i).getBytes(),
                    "data".getBytes(), ("data" + i).getBytes()
                );
                
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                
                connection.streamCommands().xAdd(record);
            }
            
            // Test reading all records
            List<ByteRecord> allRecords = connection.streamCommands()
                .xRange(streamKey.getBytes(), Range.unbounded(), Limit.unlimited());
            
            assertThat(allRecords).hasSize(5);
            
            // Check if any key in the first record's value matches "index" content
            boolean hasIndexKey = allRecords.get(0).getValue().keySet().stream()
                .anyMatch(key -> java.util.Arrays.equals(key, "index".getBytes()));
            assertThat(hasIndexKey).isTrue();
            
            // Test with limit
            List<ByteRecord> limitedRecords = connection.streamCommands()
                .xRange(streamKey.getBytes(), Range.unbounded(), Limit.limit().count(3));
            
            assertThat(limitedRecords).hasSize(3);
            
            // Test reverse range
            List<ByteRecord> reverseRecords = connection.streamCommands()
                .xRevRange(streamKey.getBytes(), Range.unbounded(), Limit.unlimited());
            
            assertThat(reverseRecords).hasSize(5);
            // First record in reverse should be the last one added
            byte[] indexValue = getValueByKeyContent(reverseRecords.get(0).getValue(), "index");
            assertThat(indexValue).isNotNull();
            assertThat(new String(indexValue)).isEqualTo("4");
            
        } finally {
            cleanupKey(streamKey);
        }
    }

    @Test
    void testStreamRangeBoundaryOperations() {
        String streamKey = "test:stream:boundary";
        
        try {
            // Add exactly 3 records to have clear boundaries for testing
            List<RecordId> recordIds = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Map<byte[], byte[]> fields = Map.of(
                    "index".getBytes(), String.valueOf(i).getBytes(),
                    "data".getBytes(), ("record" + i).getBytes()
                );
                
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                
                RecordId recordId = connection.streamCommands().xAdd(record);
                recordIds.add(recordId);
            }
            
            // Verify we have 3 records total
            assertThat(recordIds).hasSize(3);
            
            RecordId firstId = recordIds.get(0);
            RecordId middleId = recordIds.get(1);
            RecordId lastId = recordIds.get(2);
            
            // Test inclusive range - should include both boundary records
            List<ByteRecord> inclusiveRange = connection.streamCommands().xRange(
                streamKey.getBytes(),
                Range.from(Range.Bound.inclusive(firstId.getValue())).to(Range.Bound.inclusive(middleId.getValue())),
                Limit.unlimited()
            );
            assertThat(inclusiveRange).hasSize(2);
            assertThat(inclusiveRange.get(0).getId()).isEqualTo(firstId);
            assertThat(inclusiveRange.get(1).getId()).isEqualTo(middleId);
            
            // Test exclusive lower bound - should exclude first record, include middle
            List<ByteRecord> exclusiveLowerRange = connection.streamCommands().xRange(
                streamKey.getBytes(),
                Range.from(Range.Bound.exclusive(firstId.getValue())).to(Range.Bound.inclusive(middleId.getValue())),
                Limit.unlimited()
            );
            assertThat(exclusiveLowerRange).hasSize(1);
            assertThat(exclusiveLowerRange.get(0).getId()).isEqualTo(middleId);
            
            // Test exclusive upper bound - should include first record, exclude middle  
            List<ByteRecord> exclusiveUpperRange = connection.streamCommands().xRange(
                streamKey.getBytes(),
                Range.from(Range.Bound.inclusive(firstId.getValue())).to(Range.Bound.exclusive(middleId.getValue())),
                Limit.unlimited()
            );
            assertThat(exclusiveUpperRange).hasSize(1);
            assertThat(exclusiveUpperRange.get(0).getId()).isEqualTo(firstId);
            
            // Test both bounds exclusive - should return no records (only first and middle are in range)
            List<ByteRecord> bothExclusiveRange = connection.streamCommands().xRange(
                streamKey.getBytes(),
                Range.from(Range.Bound.exclusive(firstId.getValue())).to(Range.Bound.exclusive(middleId.getValue())),
                Limit.unlimited()
            );
            assertThat(bothExclusiveRange).hasSize(0);
            
            // Test reverse range with boundaries
            // Inclusive reverse range - should include both boundary records in reverse order
            List<ByteRecord> inclusiveRevRange = connection.streamCommands().xRevRange(
                streamKey.getBytes(),
                Range.from(Range.Bound.inclusive(firstId.getValue())).to(Range.Bound.inclusive(lastId.getValue())),
                Limit.unlimited()
            );
            assertThat(inclusiveRevRange).hasSize(3);
            assertThat(inclusiveRevRange.get(0).getId()).isEqualTo(lastId);   // Last record first in reverse
            assertThat(inclusiveRevRange.get(1).getId()).isEqualTo(middleId); // Middle record
            assertThat(inclusiveRevRange.get(2).getId()).isEqualTo(firstId);  // First record last in reverse
            
            // Exclusive reverse range - exclude first record
            List<ByteRecord> exclusiveRevRange = connection.streamCommands().xRevRange(
                streamKey.getBytes(),
                Range.from(Range.Bound.exclusive(firstId.getValue())).to(Range.Bound.inclusive(lastId.getValue())),
                Limit.unlimited()
            );
            assertThat(exclusiveRevRange).hasSize(2);
            assertThat(exclusiveRevRange.get(0).getId()).isEqualTo(lastId);   // Last record first
            assertThat(exclusiveRevRange.get(1).getId()).isEqualTo(middleId); // Middle record second
            
            // Exclusive reverse range - exclude last record  
            List<ByteRecord> exclusiveUpperRevRange = connection.streamCommands().xRevRange(
                streamKey.getBytes(),
                Range.from(Range.Bound.inclusive(firstId.getValue())).to(Range.Bound.exclusive(lastId.getValue())),
                Limit.unlimited()
            );
            assertThat(exclusiveUpperRevRange).hasSize(2);
            assertThat(exclusiveUpperRevRange.get(0).getId()).isEqualTo(middleId); // Middle record first in reverse
            assertThat(exclusiveUpperRevRange.get(1).getId()).isEqualTo(firstId);  // First record second in reverse
            
        } finally {
            cleanupKey(streamKey);
        }
    }

    // ==================== Consumer Group Operations ====================

    @Test
    void testConsumerGroupOperations() {
        String streamKey = "test:stream:group";
        String groupName = "test-group";
        String consumerName = "test-consumer";
        
        try {
            // Add some records first
            Map<byte[], byte[]> fields = Map.of(
                "field1".getBytes(), "value1".getBytes()
            );
            
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            
            connection.streamCommands().xAdd(record);
            
            // Create consumer group using MKSTREAM to avoid "key doesn't exist" error
            String result = connection.streamCommands()
                .xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"), true);
            assertThat(result).isEqualTo("OK");
            
            // Test destroying consumer group
            Boolean destroyed = connection.streamCommands()
                .xGroupDestroy(streamKey.getBytes(), groupName);
            assertThat(destroyed).isTrue();
            
            // Try to destroy non-existent group
            Boolean notDestroyed = connection.streamCommands()
                .xGroupDestroy(streamKey.getBytes(), "non-existent");
            assertThat(notDestroyed).isFalse();
            
            // Create group again with MKSTREAM option
            String streamKey2 = "test:stream:group:mkstream";
            try {
                String result2 = connection.streamCommands()
                    .xGroupCreate(streamKey2.getBytes(), groupName, ReadOffset.latest(), true);
                assertThat(result2).isEqualTo("OK");
                
                // Verify stream was created
                Long length = connection.streamCommands().xLen(streamKey2.getBytes());
                assertThat(length).isEqualTo(0L);
                
            } finally {
                try {
                    connection.streamCommands().xGroupDestroy(streamKey2.getBytes(), groupName);
                } catch (Exception ignored) {}
                cleanupKey(streamKey2);
            }
            
        } finally {
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testConsumerManagement() {
        String streamKey = "test:stream:consumer";
        String groupName = "test-group";
        String consumerName = "test-consumer";
        
        try {
            // Add record and create group
            Map<byte[], byte[]> fields = Map.of("field1".getBytes(), "value1".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            
            connection.streamCommands().xAdd(record);
            connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            
            Consumer consumer = Consumer.from(groupName, consumerName);
            
            // Read a message to create the consumer
            List<ByteRecord> records = connection.streamCommands().xReadGroup(
                consumer,
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            if (!records.isEmpty()) {
            // Delete the consumer - should return true for existing consumer with pending messages
            Boolean deleted = connection.streamCommands()
                .xGroupDelConsumer(streamKey.getBytes(), consumer);
            assertThat(deleted).isTrue();
        }
        
        // Try to delete non-existent consumer - should return false
        Consumer nonExistentConsumer = Consumer.from(groupName, "non-existent");
        Boolean notDeleted = connection.streamCommands()
            .xGroupDelConsumer(streamKey.getBytes(), nonExistentConsumer);
        assertThat(notDeleted).isFalse();
            
        } finally {
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    // ==================== Stream Reading Operations ====================

    @SuppressWarnings("unchecked")
    @Test
    void testStreamReading() {
        String streamKey = "test:stream:read";
        
        try {
            // Add some test records
            for (int i = 0; i < 3; i++) {
                Map<byte[], byte[]> fields = Map.of(
                    "index".getBytes(), String.valueOf(i).getBytes()
                );
                
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                
                connection.streamCommands().xAdd(record);
            }
            
            // Test reading from beginning
            List<ByteRecord> records = connection.streamCommands().xRead(
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.from("0"))
            );
            
            assertThat(records).hasSize(3);
            
            // Test reading with count limit
            List<ByteRecord> limitedRecords = connection.streamCommands().xRead(
                StreamReadOptions.empty().count(2),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.from("0"))
            );
            
            assertThat(limitedRecords).hasSize(2);
            
        } finally {
            cleanupKey(streamKey);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStreamGroupReading() {
        String streamKey = "test:stream:groupread";
        String groupName = "test-group";
        String consumerName = "test-consumer";
        
        try {
            // Add test record
            Map<byte[], byte[]> fields = Map.of("data".getBytes(), "test-value".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            
            RecordId addedId = connection.streamCommands().xAdd(record);
            assertThat(addedId).isNotNull();
            
            // Create consumer group
            connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            
            Consumer consumer = Consumer.from(groupName, consumerName);
            
            // Read from group
            List<ByteRecord> records = connection.streamCommands().xReadGroup(
                consumer,
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            assertThat(records).isNotEmpty();
            
            // Test acknowledgement
            if (!records.isEmpty()) {
                RecordId recordId = records.get(0).getId();
                Long ackCount = connection.streamCommands().xAck(streamKey.getBytes(), groupName, recordId);
                assertThat(ackCount).isEqualTo(1L);
                
                // Try to ack the same message again
                Long ackCount2 = connection.streamCommands().xAck(streamKey.getBytes(), groupName, recordId);
                assertThat(ackCount2).isEqualTo(0L);
            }
            
        } finally {
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    // ==================== Pending Messages Operations ====================

    @SuppressWarnings("unchecked")
    @Test
    void testPendingMessages() {
        String streamKey = "test:stream:pending";
        String groupName = "test-group";
        String consumerName = "test-consumer";
        
        try {
            // Add test record
            Map<byte[], byte[]> fields = Map.of("data".getBytes(), "pending-test".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            
            connection.streamCommands().xAdd(record);
            
            // Create consumer group and read message (making it pending)
            connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            
            Consumer consumer = Consumer.from(groupName, consumerName);
            List<ByteRecord> records = connection.streamCommands().xReadGroup(
                consumer,
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            if (!records.isEmpty()) {
                // Test pending messages summary
                PendingMessagesSummary summary = connection.streamCommands()
                    .xPending(streamKey.getBytes(), groupName);
                assertThat(summary).isNotNull();
                assertThat(summary.getTotalPendingMessages()).isEqualTo(1L);
                assertThat(summary.getGroupName()).isEmpty(); // Default group name is empty in summary
                
                // Test detailed pending messages
                PendingMessages pending = connection.streamCommands()
                    .xPending(streamKey.getBytes(), groupName, consumerName);
                assertThat(pending).isNotNull();
                assertThat(pending.isEmpty()).isFalse();
                assertThat(pending.size()).isEqualTo(1);
                assertThat(pending.getGroupName()).isEqualTo(groupName);
                
                // Verify the pending message details
                PendingMessage pendingMsg = pending.get(0);
                assertThat(pendingMsg).isNotNull();
                assertThat(pendingMsg.getConsumer().getName()).isEqualTo(consumerName);
                assertThat(pendingMsg.getConsumer().getGroup()).isEqualTo(groupName);
                assertThat(pendingMsg.getTotalDeliveryCount()).isEqualTo(1L);
                
                // Test pending with range and count
                PendingMessages pendingWithOptions = connection.streamCommands()
                    .xPending(streamKey.getBytes(), groupName, 
                        ValkeyStreamCommands.XPendingOptions.range(Range.unbounded(), 10L));
                assertThat(pendingWithOptions).isNotNull();
                assertThat(pendingWithOptions.isEmpty()).isFalse();
                assertThat(pendingWithOptions.size()).isEqualTo(1);
            }
            
        } finally {
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    // ==================== Message Claiming Operations ====================

    @SuppressWarnings("unchecked")
    @Test
    void testMessageClaiming() {
        String streamKey = "test:stream:claim";
        String groupName = "test-group";
        String consumer1 = "consumer1";
        String consumer2 = "consumer2";
        
        try {
            // Add test record
            Map<byte[], byte[]> fields = Map.of("data".getBytes(), "claim-test".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            
            connection.streamCommands().xAdd(record);
            
            // Create consumer group and make message pending
            connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            
            List<ByteRecord> records = connection.streamCommands().xReadGroup(
                Consumer.from(groupName, consumer1),
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            if (!records.isEmpty()) {
                RecordId pendingId = records.get(0).getId();
                
                // Test claiming message
                ValkeyStreamCommands.XClaimOptions claimOptions = 
                    ValkeyStreamCommands.XClaimOptions.minIdle(Duration.ofMillis(0)).ids(pendingId);
                
                List<ByteRecord> claimedRecords = connection.streamCommands()
                    .xClaim(streamKey.getBytes(), groupName, consumer2, claimOptions);
                assertThat(claimedRecords).isNotEmpty();
                assertThat(claimedRecords).hasSize(1);
                
                // Verify the claimed record has the expected content
                ByteRecord claimedRecord = claimedRecords.get(0);
                assertThat(claimedRecord.getId()).isEqualTo(pendingId);
                byte[] claimedData = getValueByKeyContent(claimedRecord.getValue(), "data");
                assertThat(claimedData).isNotNull();
                assertThat(new String(claimedData)).isEqualTo("claim-test");
                
                // Test claiming with just IDs
                List<RecordId> claimedIds = connection.streamCommands()
                    .xClaimJustId(streamKey.getBytes(), groupName, consumer2, claimOptions);
                assertThat(claimedIds).isNotEmpty();
                assertThat(claimedIds).hasSize(1);
                assertThat(claimedIds.get(0)).isEqualTo(pendingId);
            }
            
        } finally {
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    // ==================== Stream Information Operations ====================

    @SuppressWarnings("unchecked")
    @Test
    void testStreamInformation() {
        String streamKey = "test:stream:info";
        String groupName = "test-group";
        String consumerName = "test-consumer";
        
        try {
            // Add test record
            Map<byte[], byte[]> fields = Map.of("info".getBytes(), "test".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            
            RecordId addedRecordId = connection.streamCommands().xAdd(record);
            assertThat(addedRecordId).isNotNull();

            // Test stream info - verify actual content
            XInfoStream streamInfo = connection.streamCommands().xInfo(streamKey.getBytes());
            assertThat(streamInfo).isNotNull();
            assertThat(streamInfo.streamLength()).isEqualTo(1L);
            assertThat(streamInfo.getFirstEntry()).isNotNull();
            assertThat(streamInfo.getLastEntry()).isNotNull();

            // Create consumer group and test group info - verify actual content
            String createResult = connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            assertThat(createResult).isEqualTo("OK");

            XInfoGroups groupsInfo = connection.streamCommands().xInfoGroups(streamKey.getBytes());
            assertThat(groupsInfo).isNotNull();
            assertThat(groupsInfo.isEmpty()).isFalse();
            assertThat(groupsInfo.size()).isEqualTo(1);
            
            // Verify group details
            StreamInfo.XInfoGroup groupInfo = groupsInfo.iterator().next();
            assertThat(groupInfo).isNotNull();
            assertThat(groupInfo.groupName()).isEqualTo(groupName);
            assertThat(groupInfo.consumerCount()).isEqualTo(0L); // No consumers yet
            assertThat(groupInfo.pendingCount()).isEqualTo(0L); // No pending messages yet
            
            // Make consumer active and test consumer info - verify actual content
            List<ByteRecord> records = connection.streamCommands().xReadGroup(
                Consumer.from(groupName, consumerName),
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            assertThat(records).isNotEmpty();
            assertThat(records).hasSize(1);
            
            // Verify the record content
            ByteRecord readRecord = records.get(0);
            assertThat(readRecord).isNotNull();
            assertThat(readRecord.getId()).isNotNull();
            byte[] infoValue = getValueByKeyContent(readRecord.getValue(), "info");
            assertThat(infoValue).isNotNull();
            assertThat(new String(infoValue)).isEqualTo("test");
            
            // Test consumer info - verify actual content
            XInfoConsumers consumersInfo = connection.streamCommands()
                .xInfoConsumers(streamKey.getBytes(), groupName);
            assertThat(consumersInfo).isNotNull();
            assertThat(consumersInfo.isEmpty()).isFalse();
            assertThat(consumersInfo.size()).isEqualTo(1);
            
            // Verify consumer details
            StreamInfo.XInfoConsumer consumerInfo = consumersInfo.iterator().next();
            assertThat(consumerInfo).isNotNull();
            assertThat(consumerInfo.consumerName()).isEqualTo(consumerName);
            assertThat(consumerInfo.pendingCount()).isEqualTo(1L); // One pending message
            assertThat(consumerInfo.idleTimeMs()).isGreaterThanOrEqualTo(0L);
            
            // Verify group info is updated after consumer activity
            XInfoGroups updatedGroupsInfo = connection.streamCommands().xInfoGroups(streamKey.getBytes());
            StreamInfo.XInfoGroup updatedGroupInfo = updatedGroupsInfo.iterator().next();
            assertThat(updatedGroupInfo.consumerCount()).isEqualTo(1L); // Now has one consumer
            assertThat(updatedGroupInfo.pendingCount()).isEqualTo(1L); // One pending message
            
        } finally {
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    // ==================== Stream Trimming Operations ====================

    @Test
    void testStreamTrimming() {
        String streamKey = "test:stream:trim";
        
        try {
            // Add multiple records
            for (int i = 0; i < 10; i++) {
                Map<byte[], byte[]> fields = Map.of(
                    "index".getBytes(), String.valueOf(i).getBytes()
                );
                
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                
                connection.streamCommands().xAdd(record);
            }
            
            // Verify initial length
            Long initialLength = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(initialLength).isEqualTo(10L);
            
            // Test exact trimming
            Long trimmed = connection.streamCommands().xTrim(streamKey.getBytes(), 5);
            assertThat(trimmed).isGreaterThan(0L);
            
            Long newLength = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(newLength).isLessThanOrEqualTo(5L);
            
            // Test approximate trimming
            for (int i = 0; i < 5; i++) {
                Map<byte[], byte[]> fields = Map.of(
                    "extra".getBytes(), String.valueOf(i).getBytes()
                );
                
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                
                connection.streamCommands().xAdd(record);
            }
            
            Long approximateTrimmed = connection.streamCommands().xTrim(streamKey.getBytes(), 3, true);
            assertThat(approximateTrimmed).isNotNull();
            
        } finally {
            cleanupKey(streamKey);
        }
    }

    // ==================== Error Handling and Validation ====================

    @SuppressWarnings({ "unchecked", "unchecked" })
    @Test
    void testErrorHandling() {
        String streamKey = "test:stream:validation";
        
        try {
            // Test null parameter validation
            assertThatThrownBy(() -> connection.streamCommands().xLen(null))
                .isInstanceOf(IllegalArgumentException.class);
                
            assertThatThrownBy(() -> connection.streamCommands().xAdd(null))
                .isInstanceOf(IllegalArgumentException.class);
                
            assertThatThrownBy(() -> connection.streamCommands().xDel(null, RecordId.of("1-0")))
                .isInstanceOf(IllegalArgumentException.class);
                
            assertThatThrownBy(() -> connection.streamCommands()
                .xGroupCreate(null, "group", ReadOffset.latest()))
                .isInstanceOf(IllegalArgumentException.class);
                
            assertThatThrownBy(() -> connection.streamCommands()
                .xAck(streamKey.getBytes(), null, RecordId.of("1-0")))
                .isInstanceOf(IllegalArgumentException.class);
            
            // Test operations on non-existent stream
            Long length = connection.streamCommands().xLen("non:existent".getBytes());
            assertThat(length).isEqualTo(0L);
            
            // Test reading from non-existent stream
            @SuppressWarnings("unchecked")
            List<ByteRecord> records = connection.streamCommands().xRead(
                StreamReadOptions.empty(),
                StreamOffset.create("non:existent".getBytes(), ReadOffset.from("0"))
            );
            assertThat(records).isEmpty();
            
            // Test xReadGroup on non-existent consumer group - should throw exception
            assertThatThrownBy(() -> connection.streamCommands().xReadGroup(
                Consumer.from("non-existent-group", "consumer"),
                StreamReadOptions.empty(),
                StreamOffset.create("non:existent".getBytes(), ReadOffset.from("0"))
            ))
            .isInstanceOf(ValkeySystemException.class)
            .hasMessageContaining("NOGROUP");
            
            // Test xRange on non-existent stream  
            List<ByteRecord> rangeRecords = connection.streamCommands()
                .xRange("non:existent".getBytes(), Range.unbounded(), Limit.unlimited());
            assertThat(rangeRecords).isEmpty();
            
            // Test xRevRange on non-existent stream
            List<ByteRecord> revRangeRecords = connection.streamCommands()
                .xRevRange("non:existent".getBytes(), Range.unbounded(), Limit.unlimited());
            assertThat(revRangeRecords).isEmpty();
            
            // Test xClaim on non-existent consumer group - should throw exception
            assertThatThrownBy(() -> connection.streamCommands()
                .xClaim("non:existent".getBytes(), "group", "consumer", 
                    ValkeyStreamCommands.XClaimOptions.minIdle(Duration.ofMillis(0)).ids(RecordId.of("0-0"))))
            .isInstanceOf(ValkeySystemException.class)
            .hasMessageContaining("NOGROUP");
            
            // Test xClaimJustId on non-existent consumer group - should throw exception
            assertThatThrownBy(() -> connection.streamCommands()
                .xClaimJustId("non:existent".getBytes(), "group", "consumer",
                    ValkeyStreamCommands.XClaimOptions.minIdle(Duration.ofMillis(0)).ids(RecordId.of("0-0"))))
            .isInstanceOf(ValkeySystemException.class)
            .hasMessageContaining("NOGROUP");
            
        } finally {
            cleanupKey(streamKey);
        }
    }

    // ==================== Comprehensive Pipeline Mode Tests ====================

    @Test
    void testStreamBasicOperationsPipeline() {
        String streamKey = "test:stream:pipeline:basic";
        
        try {
            connection.openPipeline();
            
            // Test xAdd and xLen in pipeline
            Map<byte[], byte[]> fields1 = Map.of("field1".getBytes(), "value1".getBytes());
            MapRecord<byte[], byte[], byte[]> record1 = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields1);
            
            // Pipeline commands should return null
            RecordId addResult1 = connection.streamCommands().xAdd(record1);
            assertThat(addResult1).isNull();
            
            Long lenResult1 = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(lenResult1).isNull();
            
            Map<byte[], byte[]> fields2 = Map.of("field2".getBytes(), "value2".getBytes());
            MapRecord<byte[], byte[], byte[]> record2 = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields2);
            
            RecordId addResult2 = connection.streamCommands().xAdd(record2);
            assertThat(addResult2).isNull();
            
            Long lenResult2 = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(lenResult2).isNull();
            
            List<Object> results = connection.closePipeline();
            assertThat(results).hasSize(4);
            
            // First xAdd result should be a record ID
            assertThat(results.get(0)).isNotNull();
            // First xLen result should be 1
            assertThat(((Number) results.get(1)).longValue()).isEqualTo(1L);
            // Second xAdd result should be a record ID
            assertThat(results.get(2)).isNotNull();
            // Second xLen result should be 2
            assertThat(((Number) results.get(3)).longValue()).isEqualTo(2L);
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
            cleanupKey(streamKey);
        }
    }

    @Test
    void testStreamRangeOperationsPipeline() {
        String streamKey = "test:stream:pipeline:range";
        
        try {
            // Setup data first
            for (int i = 0; i < 5; i++) {
                Map<byte[], byte[]> fields = Map.of("index".getBytes(), String.valueOf(i).getBytes());
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                connection.streamCommands().xAdd(record);
            }
            
            connection.openPipeline();
            
            // Test range operations in pipeline - pipeline commands should return null
            List<ByteRecord> rangeResult = connection.streamCommands().xRange(streamKey.getBytes(), Range.unbounded(), Limit.unlimited());
            assertThat(rangeResult).isNull();
            
            List<ByteRecord> revRangeResult = connection.streamCommands().xRevRange(streamKey.getBytes(), Range.unbounded(), Limit.limit().count(3));
            assertThat(revRangeResult).isNull();
            
            Long lenResult = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(lenResult).isNull();
            
            List<Object> results = connection.closePipeline();
            assertThat(results).hasSize(3);
            
            // First result should be all 5 records
            @SuppressWarnings("unchecked")
            List<ByteRecord> rangeRecords = (List<ByteRecord>) results.get(0);
            assertThat(rangeRecords).hasSize(5);
            
            // Second result should be 3 records in reverse order
            @SuppressWarnings("unchecked")
            List<ByteRecord> revRangeRecords = (List<ByteRecord>) results.get(1);
            assertThat(revRangeRecords).hasSize(3);
            
            // Third result should be length 5
            assertThat(((Number) results.get(2)).longValue()).isEqualTo(5L);
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
            cleanupKey(streamKey);
        }
    }

    @Test
    void testStreamGroupOperationsPipeline() {
        String streamKey = "test:stream:pipeline:group";
        String groupName = "test-group";
        
        try {
            // Setup data first
            Map<byte[], byte[]> fields = Map.of("data".getBytes(), "test".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            connection.streamCommands().xAdd(record);
            
            connection.openPipeline();
            
            // Test group operations in pipeline - pipeline commands should return null
            String createResult = connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            assertThat(createResult).isNull();
            
            XInfoGroups groupsResult = connection.streamCommands().xInfoGroups(streamKey.getBytes());
            assertThat(groupsResult).isNull();
            
            Boolean destroyResult = connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            assertThat(destroyResult).isNull();
            
            List<Object> results = connection.closePipeline();
            assertThat(results).hasSize(3);
            
            // First result should be "OK"
            assertThat(results.get(0)).isEqualTo("OK");
            
            // Second result should be groups info
            assertThat(results.get(1)).isInstanceOf(XInfoGroups.class);
            XInfoGroups groupsInfo = (XInfoGroups) results.get(1);
            assertThat(groupsInfo.size()).isEqualTo(1);
            
            // Third result should be true (group destroyed)
            assertThat(results.get(2)).isEqualTo(true);
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStreamReadOperationsPipeline() {
        String streamKey = "test:stream:pipeline:read";
        
        try {
            // Setup data first
            for (int i = 0; i < 3; i++) {
                Map<byte[], byte[]> fields = Map.of("index".getBytes(), String.valueOf(i).getBytes());
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                connection.streamCommands().xAdd(record);
            }
            
            connection.openPipeline();
            
            // Test read operations in pipeline
            connection.streamCommands().xRead(
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.from("0"))
            );
            connection.streamCommands().xRead(
                StreamReadOptions.empty().count(2),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.from("0"))
            );
            connection.streamCommands().xLen(streamKey.getBytes());
            
            List<Object> results = connection.closePipeline();
            assertThat(results).hasSize(3);
            
            // First result should be all 3 records
            List<ByteRecord> allRecords = (List<ByteRecord>) results.get(0);
            assertThat(allRecords).hasSize(3);
            
            // Second result should be 2 records
            List<ByteRecord> limitedRecords = (List<ByteRecord>) results.get(1);
            assertThat(limitedRecords).hasSize(2);
            
            // Third result should be length 3
            assertThat(((Number) results.get(2)).longValue()).isEqualTo(3L);
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
            cleanupKey(streamKey);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStreamInfoOperationsPipeline() {
        String streamKey = "test:stream:pipeline:info";
        String groupName = "test-group";
        String consumerName = "test-consumer";
        
        try {
            // Setup data first
            Map<byte[], byte[]> fields = Map.of("info".getBytes(), "test".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            connection.streamCommands().xAdd(record);
            
            // Create group and consumer with pending message
            connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            connection.streamCommands().xReadGroup(
                Consumer.from(groupName, consumerName),
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            connection.openPipeline();
            
            // Test info operations in pipeline
            connection.streamCommands().xInfo(streamKey.getBytes());
            connection.streamCommands().xInfoGroups(streamKey.getBytes());
            connection.streamCommands().xInfoConsumers(streamKey.getBytes(), groupName);
            connection.streamCommands().xPending(streamKey.getBytes(), groupName);
            
            List<Object> results = connection.closePipeline();
            assertThat(results).hasSize(4);
            
            // Verify stream info
            assertThat(results.get(0)).isInstanceOf(XInfoStream.class);
            XInfoStream streamInfo = (XInfoStream) results.get(0);
            assertThat(streamInfo.streamLength()).isEqualTo(1L);
            
            // Verify groups info
            assertThat(results.get(1)).isInstanceOf(XInfoGroups.class);
            XInfoGroups groupsInfo = (XInfoGroups) results.get(1);
            assertThat(groupsInfo.size()).isEqualTo(1);
            
            // Verify consumers info
            assertThat(results.get(2)).isInstanceOf(XInfoConsumers.class);
            XInfoConsumers consumersInfo = (XInfoConsumers) results.get(2);
            assertThat(consumersInfo.size()).isEqualTo(1);
            
            // Verify pending messages
            assertThat(results.get(3)).isInstanceOf(PendingMessagesSummary.class);
            PendingMessagesSummary pendingSummary = (PendingMessagesSummary) results.get(3);
            assertThat(pendingSummary.getTotalPendingMessages()).isEqualTo(1L);
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    // ==================== Comprehensive Transaction Mode Tests ====================

    @Test
    void testStreamBasicOperationsTransaction() {
        String streamKey = "test:stream:transaction:basic";
        
        try {
            connection.multi();
            
            // Test xAdd and xLen in transaction
            Map<byte[], byte[]> fields1 = Map.of("field1".getBytes(), "value1".getBytes());
            MapRecord<byte[], byte[], byte[]> record1 = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields1);
            
            // Transaction commands should return null
            RecordId addResult1 = connection.streamCommands().xAdd(record1);
            assertThat(addResult1).isNull();
            
            Long lenResult1 = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(lenResult1).isNull();
            
            Map<byte[], byte[]> fields2 = Map.of("field2".getBytes(), "value2".getBytes());
            MapRecord<byte[], byte[], byte[]> record2 = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields2);
            
            RecordId addResult2 = connection.streamCommands().xAdd(record2);
            assertThat(addResult2).isNull();
            
            Long lenResult2 = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(lenResult2).isNull();
            
            List<Object> results = connection.exec();
            assertThat(results).hasSize(4);
            
            // First xAdd result should be a record ID
            assertThat(results.get(0)).isNotNull();
            // First xLen result should be 1
            assertThat(((Number) results.get(1)).longValue()).isEqualTo(1L);
            // Second xAdd result should be a record ID
            assertThat(results.get(2)).isNotNull();
            // Second xLen result should be 2
            assertThat(((Number) results.get(3)).longValue()).isEqualTo(2L);
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
            cleanupKey(streamKey);
        }
    }

    @Test
    void testStreamRangeOperationsTransaction() {
        String streamKey = "test:stream:transaction:range";
        
        try {
            // Setup data first
            for (int i = 0; i < 5; i++) {
                Map<byte[], byte[]> fields = Map.of("index".getBytes(), String.valueOf(i).getBytes());
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                connection.streamCommands().xAdd(record);
            }
            
            connection.multi();
            
            // Test range operations in transaction
            connection.streamCommands().xRange(streamKey.getBytes(), Range.unbounded(), Limit.unlimited());
            connection.streamCommands().xRevRange(streamKey.getBytes(), Range.unbounded(), Limit.limit().count(3));
            connection.streamCommands().xLen(streamKey.getBytes());
            
            List<Object> results = connection.exec();
            assertThat(results).hasSize(3);
            
            // First result should be all 5 records
            @SuppressWarnings("unchecked")
            List<ByteRecord> rangeRecords = (List<ByteRecord>) results.get(0);
            assertThat(rangeRecords).hasSize(5);
            
            // Second result should be 3 records in reverse order
            @SuppressWarnings("unchecked")
            List<ByteRecord> revRangeRecords = (List<ByteRecord>) results.get(1);
            assertThat(revRangeRecords).hasSize(3);
            
            // Third result should be length 5
            assertThat(((Number) results.get(2)).longValue()).isEqualTo(5L);
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
            cleanupKey(streamKey);
        }
    }

    @Test
    void testStreamGroupOperationsTransaction() {
        String streamKey = "test:stream:transaction:group";
        String groupName = "test-group";
        
        try {
            // Setup data first
            Map<byte[], byte[]> fields = Map.of("data".getBytes(), "test".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            connection.streamCommands().xAdd(record);
            
            connection.multi();
            
            // Test group operations in transaction
            connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            connection.streamCommands().xInfoGroups(streamKey.getBytes());
            
            List<Object> results = connection.exec();
            assertThat(results).hasSize(2);
            
            // First result should be "OK"
            assertThat(results.get(0)).isEqualTo("OK");
            
            // Second result should be groups info
            assertThat(results.get(1)).isInstanceOf(XInfoGroups.class);
            XInfoGroups groupsInfo = (XInfoGroups) results.get(1);
            assertThat(groupsInfo.size()).isEqualTo(1);
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStreamReadOperationsTransaction() {
        String streamKey = "test:stream:transaction:read";
        
        try {
            // Setup data first
            for (int i = 0; i < 3; i++) {
                Map<byte[], byte[]> fields = Map.of("index".getBytes(), String.valueOf(i).getBytes());
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                connection.streamCommands().xAdd(record);
            }
            
            connection.multi();
            
            // Test read operations in transaction
            connection.streamCommands().xRead(
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.from("0"))
            );
            connection.streamCommands().xRead(
                StreamReadOptions.empty().count(2),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.from("0"))
            );
            connection.streamCommands().xLen(streamKey.getBytes());
            
            List<Object> results = connection.exec();
            assertThat(results).hasSize(3);
            
            // First result should be all 3 records
            List<ByteRecord> allRecords = (List<ByteRecord>) results.get(0);
            assertThat(allRecords).hasSize(3);
            
            // Second result should be 2 records
            List<ByteRecord> limitedRecords = (List<ByteRecord>) results.get(1);
            assertThat(limitedRecords).hasSize(2);
            
            // Third result should be length 3
            assertThat(((Number) results.get(2)).longValue()).isEqualTo(3L);
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
            cleanupKey(streamKey);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStreamInfoOperationsTransaction() {
        String streamKey = "test:stream:transaction:info";
        String groupName = "test-group";
        String consumerName = "test-consumer";
        
        try {
            // Setup data first
            Map<byte[], byte[]> fields = Map.of("info".getBytes(), "test".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            connection.streamCommands().xAdd(record);
            
            // Create group and consumer with pending message
            connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            connection.streamCommands().xReadGroup(
                Consumer.from(groupName, consumerName),
                StreamReadOptions.empty(),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            connection.multi();
            
            // Test info operations in transaction
            connection.streamCommands().xInfo(streamKey.getBytes());
            connection.streamCommands().xInfoGroups(streamKey.getBytes());
            connection.streamCommands().xInfoConsumers(streamKey.getBytes(), groupName);
            connection.streamCommands().xPending(streamKey.getBytes(), groupName);
            
            List<Object> results = connection.exec();
            assertThat(results).hasSize(4);
            
            // Verify stream info
            assertThat(results.get(0)).isInstanceOf(XInfoStream.class);
            XInfoStream streamInfo = (XInfoStream) results.get(0);
            assertThat(streamInfo.streamLength()).isEqualTo(1L);
            
            // Verify groups info
            assertThat(results.get(1)).isInstanceOf(XInfoGroups.class);
            XInfoGroups groupsInfo = (XInfoGroups) results.get(1);
            assertThat(groupsInfo.size()).isEqualTo(1);
            
            // Verify consumers info
            assertThat(results.get(2)).isInstanceOf(XInfoConsumers.class);
            XInfoConsumers consumersInfo = (XInfoConsumers) results.get(2);
            assertThat(consumersInfo.size()).isEqualTo(1);
            
            // Verify pending messages
            assertThat(results.get(3)).isInstanceOf(PendingMessagesSummary.class);
            PendingMessagesSummary pendingSummary = (PendingMessagesSummary) results.get(3);
            assertThat(pendingSummary.getTotalPendingMessages()).isEqualTo(1L);
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    @Test
    void testComplexStreamScenarioTransaction() {
        String streamKey = "test:stream:transaction:complex";
        String groupName = "test-group";
        
        try {
            // Setup initial data
            Map<byte[], byte[]> fields = Map.of("data".getBytes(), "initial".getBytes());
            MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields);
            connection.streamCommands().xAdd(record);
            
            connection.multi();
            
            // Complex scenario: add records, create group, read, acknowledge in one transaction
            Map<byte[], byte[]> fields1 = Map.of("step".getBytes(), "1".getBytes());
            MapRecord<byte[], byte[], byte[]> record1 = StreamRecords.newRecord()
                .in(streamKey.getBytes())
                .ofMap(fields1);
            connection.streamCommands().xAdd(record1);
            
            connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            connection.streamCommands().xLen(streamKey.getBytes());
            connection.streamCommands().xInfo(streamKey.getBytes());
            
            List<Object> results = connection.exec();
            assertThat(results).hasSize(4);
            
            // Verify transaction results
            assertThat(results.get(0)).isNotNull(); // Record ID from xAdd
            assertThat(results.get(1)).isEqualTo("OK"); // Group create result
            assertThat(((Number) results.get(2)).longValue()).isEqualTo(2L); // Stream length
            assertThat(results.get(3)).isInstanceOf(XInfoStream.class); // Stream info
            
            XInfoStream streamInfo = (XInfoStream) results.get(3);
            assertThat(streamInfo.streamLength()).isEqualTo(2L);
            assertThat(streamInfo.groupCount()).isEqualTo(1L);
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    // ==================== Edge Cases and Complex Scenarios ====================

    @SuppressWarnings("unchecked")
    @Test
    void testComplexStreamScenario() {
        String streamKey = "test:stream:edge";
        String groupName = "edge-group";
        String consumer1 = "consumer1";
        String consumer2 = "consumer2";
        
        try {
            // Create a complex scenario with multiple records, groups, and consumers
            
            // Add multiple records with different field structures
            List<RecordId> addedIds = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<byte[], byte[]> fields = new HashMap<>();
                fields.put("id".getBytes(), String.valueOf(i).getBytes());
                fields.put("data".getBytes(), ("value" + i).getBytes());
                
                if (i % 2 == 0) {
                    fields.put("extra".getBytes(), ("extra" + i).getBytes());
                }
                
                MapRecord<byte[], byte[], byte[]> record = StreamRecords.newRecord()
                    .in(streamKey.getBytes())
                    .ofMap(fields);
                
                RecordId addedId = connection.streamCommands().xAdd(record);
                assertThat(addedId).isNotNull();
                assertThat(addedId.getValue()).isNotEmpty();
                addedIds.add(addedId);
            }
            
            // Verify stream length after adding all records
            Long streamLength = connection.streamCommands().xLen(streamKey.getBytes());
            assertThat(streamLength).isEqualTo(5L);
            
            // Create consumer group
            String groupResult = connection.streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0"));
            assertThat(groupResult).isEqualTo("OK");
            
            // Have multiple consumers read messages
            List<ByteRecord> records1 = connection.streamCommands().xReadGroup(
                Consumer.from(groupName, consumer1),
                StreamReadOptions.empty().count(2),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            List<ByteRecord> records2 = connection.streamCommands().xReadGroup(
                Consumer.from(groupName, consumer2),
                StreamReadOptions.empty().count(2),
                StreamOffset.create(streamKey.getBytes(), ReadOffset.lastConsumed())
            );
            
            // Verify each consumer got the expected number of records
            assertThat(records1).hasSize(2);
            assertThat(records2).hasSize(2);
            
            // Verify content of first consumer's first record
            ByteRecord firstRecord = records1.get(0);
            assertThat(firstRecord.getId()).isNotNull();
            byte[] idValue = getValueByKeyContent(firstRecord.getValue(), "id");
            assertThat(idValue).isNotNull();
            String recordIndex = new String(idValue);
            assertThat(recordIndex).matches("[0-4]"); // Should be one of the added record indices
            
            byte[] dataValue = getValueByKeyContent(firstRecord.getValue(), "data");
            assertThat(dataValue).isNotNull();
            assertThat(new String(dataValue)).isEqualTo("value" + recordIndex);
            
            // Verify pending messages before acknowledgment - both consumers should have pending messages
            PendingMessagesSummary summaryBefore = connection.streamCommands().xPending(streamKey.getBytes(), groupName);
            
            assertThat(summaryBefore.getTotalPendingMessages()).isEqualTo(4L); // 2 from each consumer
            
            Map<String, Long> consumerMessageCountBefore = summaryBefore.getPendingMessagesPerConsumer();
            assertThat(consumerMessageCountBefore).containsKey(consumer1);
            assertThat(consumerMessageCountBefore).containsKey(consumer2);
            assertThat(consumerMessageCountBefore.get(consumer1)).isEqualTo(2L);
            assertThat(consumerMessageCountBefore.get(consumer2)).isEqualTo(2L);
            
            // Acknowledge 1 message from consumer1, leaving 1 pending for consumer1 and 2 for consumer2
            Long ackCount = connection.streamCommands().xAck(streamKey.getBytes(), groupName, records1.get(0).getId());
            assertThat(ackCount).isEqualTo(1L);
            
            // Verify pending messages after acknowledgment - consumer1: 1 pending, consumer2: 2 pending, total: 3 pending
            PendingMessagesSummary summary = connection.streamCommands().xPending(streamKey.getBytes(), groupName);
            assertThat(summary.getTotalPendingMessages()).isEqualTo(3L);
            
            // Verify consumer names in pending summary with exact counts
            Map<String, Long> consumerMessageCount = summary.getPendingMessagesPerConsumer();
            assertThat(consumerMessageCount).containsKey(consumer1);
            assertThat(consumerMessageCount).containsKey(consumer2);
            assertThat(consumerMessageCount.get(consumer1)).isEqualTo(1L); // Read 2, acked 1 = 1 pending
            assertThat(consumerMessageCount.get(consumer2)).isEqualTo(2L); // Read 2, acked 0 = 2 pending
            
            // Test stream info with detailed validation
            XInfoStream streamInfo = connection.streamCommands().xInfo(streamKey.getBytes());
            assertThat(streamInfo).isNotNull();
            assertThat(streamInfo.streamLength()).isEqualTo(5L);
            assertThat(streamInfo.groupCount()).isEqualTo(1L);
            assertThat(streamInfo.getFirstEntry()).isNotNull();
            assertThat(streamInfo.getLastEntry()).isNotNull();
            
            // Test groups info with detailed validation
            XInfoGroups groupsInfo = connection.streamCommands().xInfoGroups(streamKey.getBytes());
            assertThat(groupsInfo).isNotNull();
            assertThat(groupsInfo.isEmpty()).isFalse();
            assertThat(groupsInfo.size()).isEqualTo(1);
            
            StreamInfo.XInfoGroup groupInfo = groupsInfo.iterator().next();
            assertThat(groupInfo.groupName()).isEqualTo(groupName);
            assertThat(groupInfo.consumerCount()).isEqualTo(2L); // Two consumers were active
            assertThat(groupInfo.pendingCount()).isEqualTo(3L); // Total pending messages
            
            // Test consumers info with detailed validation
            XInfoConsumers consumersInfo = connection.streamCommands()
                .xInfoConsumers(streamKey.getBytes(), groupName);
            assertThat(consumersInfo).isNotNull();
            assertThat(consumersInfo.size()).isEqualTo(2);
            
            // Verify both consumers exist and have correct names
            List<String> consumerNames = new ArrayList<>();
            for (StreamInfo.XInfoConsumer consumerInfo : consumersInfo) {
                consumerNames.add(consumerInfo.consumerName());
                assertThat(consumerInfo.pendingCount()).isGreaterThanOrEqualTo(0L);
                assertThat(consumerInfo.idleTimeMs()).isGreaterThanOrEqualTo(0L);
            }
            assertThat(consumerNames).containsExactlyInAnyOrder(consumer1, consumer2);
            
        } finally {
            try {
                connection.streamCommands().xGroupDestroy(streamKey.getBytes(), groupName);
            } catch (Exception ignored) {}
            cleanupKey(streamKey);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testMultipleStreamsReading() {
        String stream1 = "test:stream:multi1";
        String stream2 = "test:stream:multi2";
        
        try {
            // Add records to multiple streams
            for (int i = 0; i < 3; i++) {
                Map<byte[], byte[]> fields1 = Map.of(
                    "stream".getBytes(), "1".getBytes(),
                    "index".getBytes(), String.valueOf(i).getBytes()
                );
                
                Map<byte[], byte[]> fields2 = Map.of(
                    "stream".getBytes(), "2".getBytes(),
                    "index".getBytes(), String.valueOf(i).getBytes()
                );
                
                connection.streamCommands().xAdd(StreamRecords.newRecord()
                    .in(stream1.getBytes())
                    .ofMap(fields1));
                    
                connection.streamCommands().xAdd(StreamRecords.newRecord()
                    .in(stream2.getBytes())
                    .ofMap(fields2));
            }
            
            // Read from multiple streams
            List<ByteRecord> records = connection.streamCommands().xRead(
                StreamReadOptions.empty(),
                StreamOffset.create(stream1.getBytes(), ReadOffset.from("0")),
                StreamOffset.create(stream2.getBytes(), ReadOffset.from("0"))
            );
            
            assertThat(records).hasSize(6); // 3 from each stream
            
            // Verify records come from both streams
            boolean hasStream1 = records.stream().anyMatch(r -> {
                byte[] streamValue = getValueByKeyContent(r.getValue(), "stream");
                return streamValue != null && "1".equals(new String(streamValue));
            });
            boolean hasStream2 = records.stream().anyMatch(r -> {
                byte[] streamValue = getValueByKeyContent(r.getValue(), "stream");
                return streamValue != null && "2".equals(new String(streamValue));
            });
                
            assertThat(hasStream1).isTrue();
            assertThat(hasStream2).isTrue();
            
        } finally {
            cleanupKeys(stream1, stream2);
        }
    }

    @Test
    void testEmptyStreamOperations() {
        String emptyStream = "test:stream:empty";
        
        try {
            // Test operations on non-existent stream
            Long length = connection.streamCommands().xLen(emptyStream.getBytes());
            assertThat(length).isEqualTo(0L);
            
            // Test range operations on empty stream
            List<ByteRecord> records = connection.streamCommands()
                .xRange(emptyStream.getBytes(), Range.unbounded(), Limit.unlimited());
            assertThat(records).isEmpty();
            
            // Test trim on empty stream
            Long trimmed = connection.streamCommands().xTrim(emptyStream.getBytes(), 5);
            assertThat(trimmed).isEqualTo(0L);
            
        } finally {
            cleanupKey(emptyStream);
        }
    }
}
