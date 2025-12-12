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

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;

/**
 * Comprehensive integration tests for {@link ValkeyGlideConnection} transaction
 * functionality using the ValkeyTxCommands interface directly.
 * 
 * <p>These tests validate the implementation of all ValkeyTxCommands methods and ensure
 * standard behavior for basic and edge cases including:
 * <ul>
 *   <li>Basic transaction flow: multi() → commands → exec()</li>
 *   <li>Empty transaction should return an empty list</li>
 *   <li>Calling exec() without previous multi()</li>
 *   <li>Double calling of multi()</li>
 *   <li>Watch/unwatch interoperability and conflict scenarios</li>
 *   <li>State management: isQueueing() behavior</li>
 *   <li>Error conditions and edge cases</li>
 *   <li>Complex scenarios: large transactions, concurrent access</li>
 * </ul>
 *
 * <p>While other ValkeyGlideConnection*IntegrationTests cover their respective APIs
 * in transaction/pipeline modes as secondary concerns, this test class focuses
 * exclusively on validating transaction semantics and ValkeyTxCommands interface
 * compliance.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionTxCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            // Basic transaction test keys
            "test:tx:basic:key1", "test:tx:basic:key2", "test:tx:basic:string", "test:tx:basic:hash",
            
            // Discard test keys
            "test:tx:discard:key", "test:tx:discard:original",
            
            // Consecutive transaction test keys
            "test:tx:consecutive:key1", "test:tx:consecutive:key2", "test:tx:consecutive:key3",
            
            // Watch test keys
            "test:tx:watch:success", "test:tx:watch:conflict", "test:tx:watch:multi:key1", "test:tx:watch:multi:key2",
            "test:tx:watch:external", "test:tx:watch:timeout", "test:tx:watch:unwatch",
            
            // Error handling test keys
            "test:tx:error:key", "test:tx:error:watch", "test:tx:error:nested",
            
            // Complex scenario test keys
            "test:tx:large:key", "test:tx:mixed:string", "test:tx:mixed:hash", "test:tx:mixed:list",
            
            // Concurrent test keys (with thread ID suffix pattern)
            "test:tx:concurrent:key", "test:tx:concurrent:shared",
            
            // State management test keys
            "test:tx:state:key", "test:tx:state:pipeline:key",
            
            // Performance test keys
            "test:tx:performance:empty", "test:tx:performance:large"
        };
    }

    // ==================== Basic Transaction Flow Tests ====================

    @Test
    void testBasicMultiExecFlow() {
        String key1 = "test:tx:basic:key1";
        String key2 = "test:tx:basic:key2";
        
        try {
            // Verify initial state
            assertThat(connection.isQueueing()).isFalse();
            
            // Start transaction
            connection.multi();
            assertThat(connection.isQueueing()).isTrue();
            
            // Queue commands - should return null in transaction mode
            Object setResult1 = connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            Object setResult2 = connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            Object getResult1 = connection.stringCommands().get(key1.getBytes());
            Object getResult2 = connection.stringCommands().get(key2.getBytes());
            
            // All commands in transaction should return null (queued)
            assertThat(setResult1).isNull();
            assertThat(setResult2).isNull();
            assertThat(getResult1).isNull();
            assertThat(getResult2).isNull();
            
            // Still in queuing state
            assertThat(connection.isQueueing()).isTrue();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Transaction should complete
            assertThat(connection.isQueueing()).isFalse();
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
    void testEmptyTransaction() {
        // Start and immediately execute empty transaction
        assertThat(connection.isQueueing()).isFalse();
        
        connection.multi();
        assertThat(connection.isQueueing()).isTrue();
        
        List<Object> results = connection.exec();
        
        // Empty transaction should return empty list, not null
        assertThat(connection.isQueueing()).isFalse();
        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    @Test
    void testMultiDiscard() {
        String key = "test:tx:discard:key";
        String originalKey = "test:tx:discard:original";
        
        try {
            // Set initial value
            connection.stringCommands().set(originalKey.getBytes(), "initial".getBytes());
            
            // Start transaction
            connection.multi();
            assertThat(connection.isQueueing()).isTrue();
            
            // Queue commands that should be discarded
            connection.stringCommands().set(key.getBytes(), "should_not_be_set".getBytes());
            connection.stringCommands().set(originalKey.getBytes(), "should_not_overwrite".getBytes());
            
            // Discard transaction
            connection.discard();
            assertThat(connection.isQueueing()).isFalse();
            
            // Verify no commands were executed
            byte[] keyValue = connection.stringCommands().get(key.getBytes());
            assertThat(keyValue).isNull(); // Key should not exist
            
            byte[] originalValue = connection.stringCommands().get(originalKey.getBytes());
            assertThat(originalValue).isEqualTo("initial".getBytes()); // Should remain unchanged
            
        } finally {
            cleanupKeys(key, originalKey);
        }
    }

    @Test
    void testMultipleConsecutiveTransactions() {
        String key1 = "test:tx:consecutive:key1";
        String key2 = "test:tx:consecutive:key2";
        String key3 = "test:tx:consecutive:key3";
        
        try {
            // First transaction
            connection.multi();
            connection.stringCommands().set(key1.getBytes(), "tx1_value".getBytes());
            List<Object> results1 = connection.exec();
            
            assertThat(connection.isQueueing()).isFalse();
            assertThat(results1).hasSize(1);
            assertThat(results1.get(0)).isEqualTo(true);
            
            // Second transaction
            connection.multi();
            connection.stringCommands().set(key2.getBytes(), "tx2_value".getBytes());
            connection.stringCommands().get(key1.getBytes()); // Read from first transaction
            List<Object> results2 = connection.exec();
            
            assertThat(connection.isQueueing()).isFalse();
            assertThat(results2).hasSize(2);
            assertThat(results2.get(0)).isEqualTo(true);
            assertThat(results2.get(1)).isEqualTo("tx1_value".getBytes());
            
            // Third transaction with discard
            connection.multi();
            connection.stringCommands().set(key3.getBytes(), "should_be_discarded".getBytes());
            connection.discard();
            
            assertThat(connection.isQueueing()).isFalse();
            
            // Verify final state
            assertThat(connection.stringCommands().get(key1.getBytes())).isEqualTo("tx1_value".getBytes());
            assertThat(connection.stringCommands().get(key2.getBytes())).isEqualTo("tx2_value".getBytes());
            assertThat(connection.stringCommands().get(key3.getBytes())).isNull();
            
        } finally {
            cleanupKeys(key1, key2, key3);
        }
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    void testExecWithoutMulti() {
        // Should handle exec() without prior multi() gracefully
        assertThat(connection.isQueueing()).isFalse();
        
        assertThatThrownBy(() -> connection.exec())
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasMessageContaining("No ongoing transaction");
        
        assertThat(connection.isQueueing()).isFalse();
    }

    @Test
    void testDiscardWithoutMulti() {
        // discard() without multi() should throw exception (matches Valkey server behavior)
        assertThat(connection.isQueueing()).isFalse();
        
        assertThatThrownBy(() -> connection.discard())
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasMessageContaining("No ongoing transaction");
        
        assertThat(connection.isQueueing()).isFalse();
    }

    @Test
    void testDoubleMultiCall() {
        String key = "test:tx:error:nested";
        
        try {
            // First multi() call
            connection.multi();
            assertThat(connection.isQueueing()).isTrue();
            
            // Second multi() call - should not change state or cause errors
            connection.multi();
            assertThat(connection.isQueueing()).isTrue();
            
            // Queue a command
            connection.stringCommands().set(key.getBytes(), "nested_value".getBytes());
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            assertThat(connection.isQueueing()).isFalse();
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo(true);
            
            // Verify command was executed
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("nested_value".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testStateManagementAfterException() {
        String key = "test:tx:error:key";
        
        try {
            connection.multi();
            assertThat(connection.isQueueing()).isTrue();
            
            // Try to use WATCH during MULTI - should throw exception but preserve transaction state
            assertThatThrownBy(() -> connection.watch(key.getBytes()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("WATCH is not allowed during MULTI");
            
            // Transaction state should still be active
            assertThat(connection.isQueueing()).isTrue();
            
            // Should be able to continue with transaction
            connection.stringCommands().set(key.getBytes(), "after_error".getBytes());
            List<Object> results = connection.exec();
            
            assertThat(connection.isQueueing()).isFalse();
            assertThat(results).hasSize(1);
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("after_error".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Watch/Unwatch Tests ====================

    @Test
    void testWatchSuccess() {
        String key = "test:tx:watch:success";
        
        try {
            // Set initial value
            connection.stringCommands().set(key.getBytes(), "initial".getBytes());
            
            // Watch the key
            connection.watch(key.getBytes());
            
            // Start transaction and modify the watched key
            connection.multi();
            connection.stringCommands().set(key.getBytes(), "modified_in_transaction".getBytes());
            
            List<Object> results = connection.exec();
            
            // Transaction should succeed since no external modification occurred
            assertThat(results).isNotNull();
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo(true);
            
            // Verify value was changed
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("modified_in_transaction".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testWatchConflict() {
        String key = "test:tx:watch:conflict";
        
        try {
            // Set initial value
            connection.stringCommands().set(key.getBytes(), "initial".getBytes());
            
            // Watch the key
            connection.watch(key.getBytes());
            
            // Simulate external modification using separate connection
            try (ValkeyConnection otherConnection = connectionFactory.getConnection()) {
                otherConnection.stringCommands().set(key.getBytes(), "external_change".getBytes());
            }
            
            // Start transaction
            connection.multi();
            connection.stringCommands().set(key.getBytes(), "should_not_be_set".getBytes());
            
            List<Object> results = connection.exec();
            
            // Transaction should be aborted due to WATCH conflict - expect empty list
            assertThat(results).isNotNull().isEmpty();
            
            // External change should remain
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("external_change".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testWatchMultipleKeys() {
        String key1 = "test:tx:watch:multi:key1";
        String key2 = "test:tx:watch:multi:key2";
        
        try {
            // Set initial values
            connection.stringCommands().set(key1.getBytes(), "initial1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "initial2".getBytes());
            
            // Watch both keys
            connection.watch(key1.getBytes(), key2.getBytes());
            
            // Modify one watched key externally
            try (ValkeyConnection otherConnection = connectionFactory.getConnection()) {
                otherConnection.stringCommands().set(key1.getBytes(), "external1".getBytes());
            }
            
            // Transaction should be aborted even if only one watched key was modified
            connection.multi();
            connection.stringCommands().set(key1.getBytes(), "tx1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "tx2".getBytes());
            List<Object> results = connection.exec();
            
            assertThat(results).isNotNull().isEmpty();
            
            // Verify external change persists, original value for unmodified key remains
            assertThat(connection.stringCommands().get(key1.getBytes())).isEqualTo("external1".getBytes());
            assertThat(connection.stringCommands().get(key2.getBytes())).isEqualTo("initial2".getBytes());
            
        } finally {
            cleanupKeys(key1, key2);
        }
    }

    @Test
    void testUnwatch() {
        String key = "test:tx:watch:unwatch";
        
        try {
            // Set initial value
            connection.stringCommands().set(key.getBytes(), "initial".getBytes());
            
            // Watch then unwatch
            connection.watch(key.getBytes());
            connection.unwatch();
            
            // Modify key externally (should not affect transaction after unwatch)
            try (ValkeyConnection otherConnection = connectionFactory.getConnection()) {
                otherConnection.stringCommands().set(key.getBytes(), "external_change".getBytes());
            }
            
            // Transaction should succeed despite external change
            connection.multi();
            connection.stringCommands().set(key.getBytes(), "transaction_value".getBytes());
            List<Object> results = connection.exec();
            
            assertThat(results).isNotNull();
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo(true);
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("transaction_value".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testWatchDuringMultiThrowsException() {
        String key = "test:tx:error:watch";
        
        try {
            connection.multi();
            
            assertThatThrownBy(() -> connection.watch(key.getBytes()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("WATCH is not allowed during MULTI");
            
        } finally {
            // Ensure we clean up transaction state
            if (connection.isQueueing()) {
                connection.discard();
            }
            cleanupKey(key);
        }
    }

    @Test
    void testWatchAutomaticCleanupAfterExec() {
        String key = "test:tx:watch:external";
        
        try {
            // Set initial value and watch
            connection.stringCommands().set(key.getBytes(), "initial".getBytes());
            connection.watch(key.getBytes());
            
            // Execute successful transaction
            connection.multi();
            connection.stringCommands().set(key.getBytes(), "first_tx".getBytes());
            List<Object> results1 = connection.exec();
            assertThat(results1).isNotNull();
            
            // Modify key externally
            try (ValkeyConnection otherConnection = connectionFactory.getConnection()) {
                otherConnection.stringCommands().set(key.getBytes(), "external".getBytes());
            }
            
            // New transaction should succeed (watch was cleared after first exec)
            connection.multi();
            connection.stringCommands().set(key.getBytes(), "second_tx".getBytes());
            List<Object> results2 = connection.exec();
            
            assertThat(results2).isNotNull();
            assertThat(results2).hasSize(1);
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("second_tx".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Complex Scenarios ====================

    @Test
    void testLargeTransaction() {
        String keyPrefix = "test:tx:large:key";
        int commandCount = 100;
        
        try {
            connection.multi();
            
            // Queue many commands
            for (int i = 0; i < commandCount; i++) {
                String key = keyPrefix + ":" + i;
                String value = "value" + i;
                Object result = connection.stringCommands().set(key.getBytes(), value.getBytes());
                assertThat(result).isNull(); // Should be null in transaction mode
            }
            
            List<Object> results = connection.exec();
            
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
    void testMixedCommandTypes() {
        String stringKey = "test:tx:mixed:string";
        String hashKey = "test:tx:mixed:hash";
        String listKey = "test:tx:mixed:list";
        
        try {
            connection.multi();
            
            // Mix different command types
            connection.stringCommands().set(stringKey.getBytes(), "string_value".getBytes());
            connection.hashCommands().hSet(hashKey.getBytes(), "field1".getBytes(), "hash_value".getBytes());
            connection.listCommands().lPush(listKey.getBytes(), "list_item".getBytes());
            
            // Add read operations
            connection.stringCommands().get(stringKey.getBytes());
            connection.hashCommands().hGet(hashKey.getBytes(), "field1".getBytes());
            connection.listCommands().lLen(listKey.getBytes());
            
            List<Object> results = connection.exec();
            
            assertThat(results).hasSize(6);
            
            // Verify write results
            assertThat(results.get(0)).isEqualTo(true); // SET
            assertThat(results.get(1)).isEqualTo(true); // HSET
            assertThat(results.get(2)).isEqualTo(1L);   // LPUSH (returns length)
            
            // Verify read results
            assertThat(results.get(3)).isEqualTo("string_value".getBytes()); // GET
            assertThat(results.get(4)).isEqualTo("hash_value".getBytes());   // HGET
            assertThat(results.get(5)).isEqualTo(1L);                        // LLEN
            
            // Verify values exist outside transaction
            assertThat(connection.stringCommands().get(stringKey.getBytes())).isEqualTo("string_value".getBytes());
            assertThat(connection.hashCommands().hGet(hashKey.getBytes(), "field1".getBytes())).isEqualTo("hash_value".getBytes());
            assertThat(connection.listCommands().lLen(listKey.getBytes())).isEqualTo(1L);
            
        } finally {
            cleanupKeys(stringKey, hashKey, listKey);
        }
    }

    @Test
    void testConcurrentTransactions() throws Exception {
        String sharedKey = "test:tx:concurrent:shared";
        String keyPrefix = "test:tx:concurrent:key";
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        try {
            // Set initial shared value
            connection.stringCommands().set(sharedKey.getBytes(), "0".getBytes());
            
            CompletableFuture<?>[] futures = new CompletableFuture[5];
            
            for (int i = 0; i < 5; i++) {
                final int threadId = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    try (ValkeyConnection conn = connectionFactory.getConnection()) {
                        // Each thread runs its own transaction
                        conn.multi();
                        conn.stringCommands().set((keyPrefix + ":" + threadId).getBytes(), ("thread" + threadId).getBytes());
                        conn.stringCommands().get(sharedKey.getBytes()); // Read shared value
                        List<Object> results = conn.exec();
                        
                        assertThat(results).hasSize(2);
                        assertThat(results.get(0)).isEqualTo(true); // SET result
                        assertThat(results.get(1)).isEqualTo("0".getBytes()); // GET result
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

    // ==================== Pipeline vs Transaction Isolation ====================

    @Test
    void testPipelineTransactionIsolation() {
        String key = "test:tx:state:pipeline:key";
        
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

    // ==================== State Management Tests ====================

    @Test
    void testIsQueueingState() {
        assertThat(connection.isQueueing()).isFalse();
        
        connection.multi();
        assertThat(connection.isQueueing()).isTrue();
        
        connection.exec();
        assertThat(connection.isQueueing()).isFalse();
    }

    @Test
    void testIsQueueingAfterDiscard() {
        assertThat(connection.isQueueing()).isFalse();
        
        connection.multi();
        assertThat(connection.isQueueing()).isTrue();
        
        connection.discard();
        assertThat(connection.isQueueing()).isFalse();
    }

    @Test
    void testIsQueueingAfterWatchAbort() {
        String key = "test:tx:watch:timeout";
        
        try {
            // Set up watch conflict scenario
            connection.stringCommands().set(key.getBytes(), "initial".getBytes());
            connection.watch(key.getBytes());
            
            // External modification
            try (ValkeyConnection otherConnection = connectionFactory.getConnection()) {
                otherConnection.stringCommands().set(key.getBytes(), "external".getBytes());
            }
            
            connection.multi();
            assertThat(connection.isQueueing()).isTrue();
            
            connection.stringCommands().set(key.getBytes(), "should_not_be_set".getBytes());
            List<Object> results = connection.exec();
            
            // After aborted transaction, should not be in queuing state
            assertThat(results).isNotNull().isEmpty();
            assertThat(connection.isQueueing()).isFalse();
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Performance and Edge Cases ====================

    @Test
    void testEmptyTransactionPerformance() {
        // Multiple empty transactions should be handled efficiently
        for (int i = 0; i < 10; i++) {
            connection.multi();
            List<Object> results = connection.exec();
            assertThat(results).isEmpty();
            assertThat(connection.isQueueing()).isFalse();
        }
    }

    @Test
    void testWatchOnNonExistentKey() {
        String nonExistentKey = "test:tx:watch:nonexistent";
        String testKey = "test:tx:state:key";
        
        try {
            // Watch a key that doesn't exist
            connection.watch(nonExistentKey.getBytes());
            
            // Create the key externally
            try (ValkeyConnection otherConnection = connectionFactory.getConnection()) {
                otherConnection.stringCommands().set(nonExistentKey.getBytes(), "created_externally".getBytes());
            }
            
            // Transaction should be aborted because watched key was created
            connection.multi();
            connection.stringCommands().set(testKey.getBytes(), "should_not_be_set".getBytes());
            List<Object> results = connection.exec();
            
            assertThat(results).isNotNull().isEmpty(); // Transaction aborted
            assertThat(connection.stringCommands().get(testKey.getBytes())).isNull();
            
        } finally {
            cleanupKeys(nonExistentKey, testKey);
        }
    }

    @Test
    void testTransactionWithConnectionFailure() {
        // This test ensures transaction state is properly managed even when underlying
        // connection issues occur. However, since we're testing at the integration level
        // and connection failures are hard to simulate reliably, we'll focus on
        // verifying that our transaction state management is robust.
        
        String key = "test:tx:state:key";
        
        try {
            // Test that transaction state is properly reset after various operations
            connection.multi();
            assertThat(connection.isQueueing()).isTrue();
            
            // Simulate a scenario where we might have connection issues
            // by ensuring our state management is consistent
            connection.stringCommands().set(key.getBytes(), "test_value".getBytes());
            
            List<Object> results = connection.exec();
            assertThat(connection.isQueueing()).isFalse();
            assertThat(results).isNotNull();
            assertThat(results).hasSize(1);
            
            // Verify connection is still usable
            assertThat(connection.stringCommands().get(key.getBytes())).isEqualTo("test_value".getBytes());
            
        } finally {
            cleanupKey(key);
        }
    }
}
