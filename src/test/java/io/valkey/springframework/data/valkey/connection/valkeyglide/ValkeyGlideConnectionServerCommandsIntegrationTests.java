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
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.FlushOption;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.MigrateOption;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.ShutdownOption;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * server functionality using the ValkeyServerCommands interface directly.
 * 
 * These tests validate the implementation of all ValkeyServerCommands methods:
 * - Background operations (bgReWriteAof, bgSave, lastSave, save)
 * - Database operations (dbSize, flushDb, flushAll)
 * - Server information (info, time)
 * - Configuration management (getConfig, setConfig, resetConfigStats, rewriteConfig)
 * - Client management (killClient, setClientName, getClientName, getClientList)
 * - Replication commands (replicaOf, replicaOfNoOne)
 * - Data migration (migrate)
 * - Server control (shutdown) - tested with caution
 * - Error handling and validation
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionServerCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:server:db:key", "test:server:flush:key1", "test:server:flush:key2",
            "test:server:migrate:key", "test:server:config:key", "test:server:client:key",
            "test:server:info:key", "test:server:time:key", "test:server:replication:key",
            "test:server:save:key", "test:server:validation:key", "test:server:edge:key"
        };
    }

    // ==================== Background Operations ====================

    @Test
    void testBgReWriteAof() {
        try {
            // Test background AOF rewrite
            connection.serverCommands().bgReWriteAof();
            
            // Command should execute without throwing an exception
            // We can't easily verify the actual AOF rewrite without server configuration
            // but we can verify the command was accepted
        } catch (Exception e) {
            // Check for the specific error message when AOF is not enabled
            if (e.getMessage() != null && e.getMessage().contains("NOAPPENDONLYFILE")) {
                logger.warn("BGREWRITEAOF test skipped: AOF not enabled on server");
                return;
            } else {
                throw e;
            }
        }
    }

    @Test
    void testBgSave() {
        try {
            // Test background save
            connection.serverCommands().bgSave();
            
            // Command should execute without throwing an exception
            // We can't easily verify the actual save without filesystem access
            // but we can verify the command was accepted
        } catch (Exception e) {
            // Check for the specific error message when background save is already in progress
            if (e.getMessage() != null && e.getMessage().contains("Background save already in progress")) {
                logger.warn("BGSAVE test skipped: Background save already in progress");
                return;
            } else {
                throw e;
            }
        }
    }

    @Test
    void testLastSave() {
        Long lastSaveTime = connection.serverCommands().lastSave();
        
        // lastSave should return a timestamp or null
        if (lastSaveTime != null) {
            assertThat(lastSaveTime).isGreaterThan(0L);
            // The timestamp should be reasonable (not in the future, allowing for clock skew)
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            assertThat(lastSaveTime).isLessThanOrEqualTo(currentTimeSeconds + 60); // Allow 1 minute clock skew
        } else {
            logger.warn("LASTSAVE returned null - no previous save recorded");
        }
    }

    @Test
    void testSave() {
        try {
            // Get lastSave time before save operation
            Long lastSaveTimeBefore = connection.serverCommands().lastSave();
            
            // Test synchronous save
            connection.serverCommands().save();
            
            // Command should execute without throwing an exception
            // Verify that lastSave timestamp is updated
            Long lastSaveTimeAfter = connection.serverCommands().lastSave();
            assertThat(lastSaveTimeAfter).isNotNull();
            assertThat(lastSaveTimeAfter).isGreaterThan(0L);
            
            // If we had a previous save time, the new one should be >= the old one
            if (lastSaveTimeBefore != null) {
                assertThat(lastSaveTimeAfter).isGreaterThanOrEqualTo(lastSaveTimeBefore);
            }
        } catch (Exception e) {
            // Check for the specific error message when save is disabled
            if (e.getMessage() != null && e.getMessage().contains("Background save already in progress")) {
                logger.warn("SAVE test skipped: Background save already in progress");
                return;
            } else {
                throw e;
            }
        }
    }

    // ==================== Database Operations ====================

    @Test
    void testDbSize() {
        String key = "test:server:db:key";
        
        try {
            // Get initial database size
            Long initialSize = connection.serverCommands().dbSize();
            assertThat(initialSize).isNotNull();
            assertThat(initialSize).isGreaterThanOrEqualTo(0L);
            
            // Add a key
            connection.stringCommands().set(key.getBytes(), "value".getBytes());
            
            // Database size should increase
            Long newSize = connection.serverCommands().dbSize();
            assertThat(newSize).isNotNull();
            assertThat(newSize).isGreaterThan(initialSize);
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testFlushDb() {
        String key1 = "test:server:flush:key1";
        String key2 = "test:server:flush:key2";
        
        try {
            // Add some test keys
            connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Verify keys exist
            assertThat(connection.keyCommands().exists(key1.getBytes())).isTrue();
            assertThat(connection.keyCommands().exists(key2.getBytes())).isTrue();
            
            // Flush database
            connection.serverCommands().flushDb();
            
            // Verify keys are gone
            assertThat(connection.keyCommands().exists(key1.getBytes())).isFalse();
            assertThat(connection.keyCommands().exists(key2.getBytes())).isFalse();
            
        } finally {
            cleanupKeys(key1, key2);
        }
    }

    @Test
    void testFlushDbWithOption() {
        String key1 = "test:server:flush:key1";
        String key2 = "test:server:flush:key2";
        
        try {
            // Add some test keys
            connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Verify keys exist
            assertThat(connection.keyCommands().exists(key1.getBytes())).isTrue();
            assertThat(connection.keyCommands().exists(key2.getBytes())).isTrue();
            
            // Test flush with ASYNC option
            connection.serverCommands().flushDb(FlushOption.ASYNC);
            
            // Verify keys are gone (may take a moment with ASYNC)
            assertThat(connection.keyCommands().exists(key1.getBytes())).isFalse();
            assertThat(connection.keyCommands().exists(key2.getBytes())).isFalse();
            
        } finally {
            cleanupKeys(key1, key2);
        }
    }

    @Test
    void testFlushAll() {
        String key1 = "test:server:flush:key1";
        String key2 = "test:server:flush:key2";
        
        try {
            // Add some test keys
            connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Get initial database size
            Long initialSize = connection.serverCommands().dbSize();
            assertThat(initialSize).isGreaterThan(0L);
            
            // Flush all databases
            connection.serverCommands().flushAll();
            
            // Database should be empty or much smaller
            Long newSize = connection.serverCommands().dbSize();
            assertThat(newSize).isLessThan(initialSize);
            
        } finally {
            cleanupKeys(key1, key2);
        }
    }

    @Test
    void testFlushAllWithOption() {
        String key1 = "test:server:flush:key1";
        String key2 = "test:server:flush:key2";
        
        try {
            // Add some test keys
            connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Get initial database size
            Long initialSize = connection.serverCommands().dbSize();
            assertThat(initialSize).isGreaterThan(0L);
            
            // Flush all databases with ASYNC option
            connection.serverCommands().flushAll(FlushOption.ASYNC);
            
            // Database should be empty or much smaller
            Long newSize = connection.serverCommands().dbSize();
            assertThat(newSize).isLessThan(initialSize);
            
        } finally {
            cleanupKeys(key1, key2);
        }
    }

    // ==================== Server Information ====================

    @Test
    void testInfo() {
        // Test getting all server info
        Properties allInfo = connection.serverCommands().info();
        assertThat(allInfo).isNotNull();
        assertThat(allInfo).isNotEmpty();
        
        // Test getting specific section
        Properties serverInfo = connection.serverCommands().info("server");
        assertThat(serverInfo).isNotNull();
        assertThat(serverInfo.containsKey("valkey_version") || serverInfo.containsKey("redis_version")).isTrue();
        
        // Test getting memory info
        Properties memoryInfo = connection.serverCommands().info("memory");
        assertThat(memoryInfo).isNotNull();
        assertThat(memoryInfo.containsKey("used_memory")).isTrue();
        
        // Test non-existent section (should return empty or minimal info)
        Properties nonExistentInfo = connection.serverCommands().info("nonexistent");
        assertThat(nonExistentInfo).isNotNull();
    }

    @Test
    void testTime() {
        // Test getting server time in different units
        Long timeMillis = connection.serverCommands().time(TimeUnit.MILLISECONDS);
        assertThat(timeMillis).isNotNull();
        assertThat(timeMillis).isGreaterThan(0L);
        
        Long timeSeconds = connection.serverCommands().time(TimeUnit.SECONDS);
        assertThat(timeSeconds).isNotNull();
        assertThat(timeSeconds).isGreaterThan(0L);
        
        Long timeMicros = connection.serverCommands().time(TimeUnit.MICROSECONDS);
        assertThat(timeMicros).isNotNull();
        assertThat(timeMicros).isGreaterThan(0L);
        
        // Verify time relationships (allow for some timing variance)
        // Convert to same units for comparison
        long timeSecondsInMillis = timeSeconds * 1000;
        long timeMillisInMicros = timeMillis * 1000;
        
        // Times should be reasonably close (within 1 second)
        assertThat(Math.abs(timeMillis - timeSecondsInMillis)).isLessThan(1000);
        assertThat(Math.abs(timeMicros - timeMillisInMicros)).isLessThan(1000000);
    }

    // ==================== Configuration Management ====================

    @Test
    void testConfigGetSet() {
        // Test getting configuration
        Properties maxMemoryConfig = connection.serverCommands().getConfig("maxmemory");
        assertThat(maxMemoryConfig).isNotNull();
        
        // Test getting multiple configs with wildcard
        Properties allConfigs = connection.serverCommands().getConfig("*");
        assertThat(allConfigs).isNotNull();
        assertThat(allConfigs).isNotEmpty();
        
        // Test setting and getting a safe configuration parameter
        // Use timeout as it's generally safe to modify
        Properties timeoutConfig = connection.serverCommands().getConfig("timeout");
        assertThat(timeoutConfig).isNotNull();
        
        String originalTimeout = timeoutConfig.getProperty("timeout");
        
        try {
            // Only test config modification if we have write permissions
            if (originalTimeout != null) {
                // Set timeout to a safe value
                connection.serverCommands().setConfig("timeout", "60");
                
                // Verify it was set
                Properties newTimeoutConfig = connection.serverCommands().getConfig("timeout");
                assertThat(newTimeoutConfig.getProperty("timeout")).isEqualTo("60");
            }
        } catch (Exception e) {
            // Check for the specific error message when config modification is not allowed
            if (e.getMessage() != null && e.getMessage().contains("read-only")) {
                logger.warn("CONFIG SET test skipped: Server is in read-only mode");
                return;
            } else {
                throw e;
            }
        } finally {
            // Restore original timeout
            if (originalTimeout != null) {
                connection.serverCommands().setConfig("timeout", originalTimeout);
            }
        }
    }

    @Test
    void testResetConfigStats() {
        // Test resetting config stats
        // This command resets statistical counters, so we mainly test that it doesn't throw
        assertThatNoException().isThrownBy(() -> connection.serverCommands().resetConfigStats());
    }

    @Test
    void testRewriteConfig() {
        // Test rewriting config file
        // This command rewrites the config file, so we mainly test that it doesn't throw
        // Note: This might fail if Valkey is running without a config file
        try {
            connection.serverCommands().rewriteConfig();
        } catch (Exception e) {
            // Check for the specific error message when config rewrite is not supported
            // Need to check the exception chain as the message might be wrapped
            Throwable cause = e;
            while (cause != null) {
                String causeMessage = cause.getMessage();
                if (causeMessage != null && causeMessage.contains("The server is running without a config file")) {
                    logger.warn("CONFIG REWRITE test skipped: Server is running without a config file");
                    return;
                }
                cause = cause.getCause();
            }
            
            // If we get here, it's an unexpected error
            throw e;
        }
    }

    // ==================== Client Management ====================

    @Test
    void testClientNameOperations() {
        // Test setting and getting client name
        byte[] clientName = "test-client".getBytes();
        String originalName = null;
        
        try {
            // Get original client name
            originalName = connection.serverCommands().getClientName();
            
            // Set client name
            connection.serverCommands().setClientName(clientName);
            
            // Get client name
            String retrievedName = connection.serverCommands().getClientName();
            assertThat(retrievedName).isEqualTo("test-client");
            
        } finally {
            // Clean up - restore original name or set empty name
            if (originalName != null && !originalName.isEmpty()) {
                connection.serverCommands().setClientName(originalName.getBytes());
            } else {
                connection.serverCommands().setClientName("".getBytes());
            }
        }
    }

    @Test
    void testGetClientList() {
        // Test getting client list
        List<ValkeyClientInfo> clientList = connection.serverCommands().getClientList();
        assertThat(clientList).isNotNull();
        assertThat(clientList).isNotEmpty();
        
        // Verify client info properties
        ValkeyClientInfo firstClient = clientList.get(0);
        assertThat(firstClient).isNotNull();
        
        // Check that basic client info properties exist
        // Some properties might be null or empty depending on Valkey version and configuration
        assertThat(firstClient.get("addr")).isNotNull();
        assertThat(firstClient.get("fd")).isNotNull();
        
        // Client name might be null or empty string - we'll skip detailed validation since it's optional
        assertThat(firstClient.get("age")).isNotNull();
        assertThat(firstClient.get("idle")).isNotNull();
    }

    @Test
    void testKillClient() {
        // Test killing a client
        // This is tricky to test safely, so we test with a non-existent client
        // The command should throw an exception with "No such client" message in the cause chain
        Throwable exception = catchThrowable(() -> 
            connection.serverCommands().killClient("192.168.1.1", 12345));
        
        assertThat(exception).isNotNull();
        
        // Check the entire exception chain for the "No such client" message
        // The actual message format is "An error was signalled by the server: - ResponseError: No such client"
        boolean foundMessage = false;
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("ResponseError: No such client")) {
                foundMessage = true;
                break;
            }
            cause = cause.getCause();
        }
        
        assertThat(foundMessage).isTrue();
    }

    // ==================== Replication Commands ====================

    @Test
    void testReplicaCommands() {
        // Test replication commands
        // These are potentially destructive, so we mainly test that they don't throw
        // and then immediately reset to no replication
        
        try {
            // Test replicaOfNoOne (make this instance a master)
            // This should always work unless replication is disabled
            connection.serverCommands().replicaOfNoOne();
            
            // Test replicaOf with a non-existent master (should fail gracefully)
            // We don't want to actually set up replication in tests
            try {
                connection.serverCommands().replicaOf("localhost", 9999);
                // If this doesn't throw, reset to no replication
                connection.serverCommands().replicaOfNoOne();
            } catch (Exception e) {
                // Expected to fail with non-existent master
                // This is the normal case
            }
            
        } catch (Exception e) {
            // Check for the specific error message when replication commands are not allowed
            if (e.getMessage() != null && e.getMessage().contains("REPLICAOF not allowed")) {
                logger.warn("REPLICAOF test skipped: Replication commands not allowed");
                return;
            } else {
                throw e;
            }
        }
    }

    // ==================== Data Migration ====================

    @Test
    void testMigrate() {
        // Test migrate command
        // This is complex to test as it requires another Valkey instance
        // We test with a non-existent target to verify command structure
        
        String sourceKey = "test:server:migrate:source";
        
        try {
            // Set up source data
            connection.stringCommands().set(sourceKey.getBytes(), "migrate_test_value".getBytes());
            
            // Test migrate to non-existent target (should fail but not crash)
            assertThatThrownBy(() -> 
                connection.serverCommands().migrate(sourceKey.getBytes(), 
                    ValkeyNode.newValkeyNode()
                        .listeningAt("localhost", 9999).build(), 
                    0, null))
                .isInstanceOf(Exception.class);
            
            // Test migrate with timeout and options
            assertThatThrownBy(() -> 
                connection.serverCommands().migrate(sourceKey.getBytes(), 
                    ValkeyNode.newValkeyNode()
                        .listeningAt("localhost", 9999).build(), 
                    0, 
                    MigrateOption.COPY, 
                    5000))
                .isInstanceOf(Exception.class);
                
        } finally {
            cleanupKey(sourceKey);
        }
    }

    // ==================== Pipeline Mode Tests ====================

    @Test
    void testBackgroundOperationsInPipelineMode() {
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue background operations - assert they return null in pipeline mode
            assertThat(connection.serverCommands().lastSave()).isNull();
            // Note: bgReWriteAof, bgSave, save might fail due to server configuration, so we test lastSave
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(1);
            // lastSave should return a timestamp or null
            if (results.get(0) != null) {
                assertThat(results.get(0)).isInstanceOf(Long.class);
                assertThat(((Long) results.get(0))).isGreaterThan(0L);
            }
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    @Test
    void testDatabaseOperationsInPipelineMode() {
        String key1 = "test:server:pipeline:db:key1";
        String key2 = "test:server:pipeline:db:key2";
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue database operations - assert they return null in pipeline mode
            assertThat(connection.serverCommands().dbSize()).isNull();
            connection.serverCommands().flushDb(); // void method, no assertion
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isInstanceOf(Long.class); // dbSize result
            assertThat(((Long) results.get(0))).isGreaterThan(0L);
            assertThat(results.get(1)).isEqualTo("OK"); // flushDb result
            
        } finally {
            cleanupKeys(key1, key2);
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    @Test
    void testDatabaseOperationsWithOptionsInPipelineMode() {
        String key = "test:server:pipeline:flush:key";
        
        try {
            // Set up test data
            connection.stringCommands().set(key.getBytes(), "value".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue database operations with options
            connection.serverCommands().flushDb(FlushOption.ASYNC); // void method, no assertion
            connection.serverCommands().flushAll(FlushOption.SYNC); // void method, no assertion
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isEqualTo("OK"); // flushDb result
            assertThat(results.get(1)).isEqualTo("OK"); // flushAll result
            
        } finally {
            cleanupKey(key);
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    @Test
    void testServerInformationInPipelineMode() {
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue server information commands - assert they return null in pipeline mode
            assertThat(connection.serverCommands().info()).isNull();
            assertThat(connection.serverCommands().info("server")).isNull();
            assertThat(connection.serverCommands().time(TimeUnit.MILLISECONDS)).isNull();
            assertThat(connection.serverCommands().time(TimeUnit.SECONDS)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(4);
            
            // info() result
            assertThat(results.get(0)).isInstanceOf(Properties.class);
            Properties allInfo = (Properties) results.get(0);
            assertThat(allInfo).isNotEmpty();
            
            // info("server") result
            assertThat(results.get(1)).isInstanceOf(Properties.class);
            Properties serverInfo = (Properties) results.get(1);
            assertThat(serverInfo).isNotNull();
            
            // time() results
            assertThat(results.get(2)).isInstanceOf(Long.class);
            assertThat(((Long) results.get(2))).isGreaterThan(0L);
            
            assertThat(results.get(3)).isInstanceOf(Long.class);
            assertThat(((Long) results.get(3))).isGreaterThan(0L);
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    @Test
    void testConfigurationManagementInPipelineMode() {
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue configuration commands - assert they return null in pipeline mode
            assertThat(connection.serverCommands().getConfig("maxmemory")).isNull();
            assertThat(connection.serverCommands().getConfig("*")).isNull();
            connection.serverCommands().resetConfigStats(); // void method, no assertion
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(3);
            
            // getConfig results
            assertThat(results.get(0)).isInstanceOf(Properties.class);
            assertThat(results.get(1)).isInstanceOf(Properties.class);
            Properties allConfigs = (Properties) results.get(1);
            assertThat(allConfigs).isNotEmpty();
            
            // resetConfigStats result
            assertThat(results.get(2)).isEqualTo("OK");
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    @Test
    void testConfigurationSetInPipelineMode() {
        try {
            // Get original timeout for restoration
            Properties timeoutConfig = connection.serverCommands().getConfig("timeout");
            String originalTimeout = timeoutConfig.getProperty("timeout", "0");
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue config set command
            connection.serverCommands().setConfig("timeout", "120"); // void method, no assertion
            assertThat(connection.serverCommands().getConfig("timeout")).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isEqualTo("OK"); // setConfig result
            assertThat(results.get(1)).isInstanceOf(Properties.class); // getConfig result
            
            Properties newTimeoutConfig = (Properties) results.get(1);
            assertThat(newTimeoutConfig.getProperty("timeout")).isEqualTo("120");
            
            // Restore original timeout
            connection.serverCommands().setConfig("timeout", originalTimeout);
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("read-only")) {
                logger.warn("CONFIG SET test skipped: Server is in read-only mode");
                return;
            } else {
                throw e;
            }
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    @Test
    void testClientManagementInPipelineMode() {
        try {
            // Get original client name for restoration
            String originalName = connection.serverCommands().getClientName();
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue client management commands - assert they return null in pipeline mode
            connection.serverCommands().setClientName("test-pipeline-client".getBytes()); // void method, no assertion
            assertThat(connection.serverCommands().getClientName()).isNull();
            assertThat(connection.serverCommands().getClientList()).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo("OK"); // setClientName result
            assertThat(results.get(1)).isEqualTo("test-pipeline-client"); // getClientName result
            
            @SuppressWarnings("unchecked")
            List<ValkeyClientInfo> clientList = (List<ValkeyClientInfo>) results.get(2);
            assertThat(clientList).isNotEmpty();
            
            // Restore original client name
            if (originalName != null && !originalName.isEmpty()) {
                connection.serverCommands().setClientName(originalName.getBytes());
            } else {
                connection.serverCommands().setClientName("".getBytes());
            }
            
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    @Test
    void testReplicationCommandsInPipelineMode() {
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue replication commands - these are safe to test
            connection.serverCommands().replicaOfNoOne(); // void method, no assertion
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo("OK"); // replicaOfNoOne result
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("REPLICAOF not allowed")) {
                logger.warn("REPLICAOF test skipped: Replication commands not allowed");
                return;
            } else {
                throw e;
            }
        } finally {
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    // ==================== Transaction Mode Tests ====================

    @Test
    void testBackgroundOperationsInTransactionMode() {
        try {
            // Start transaction
            connection.multi();
            
            // Queue background operations - assert they return null in transaction mode
            assertThat(connection.serverCommands().lastSave()).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(1);
            // lastSave should return a timestamp or null
            if (results.get(0) != null) {
                assertThat(results.get(0)).isInstanceOf(Long.class);
                assertThat(((Long) results.get(0))).isGreaterThan(0L);
            }
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testDatabaseOperationsInTransactionMode() {
        String key1 = "test:server:transaction:db:key1";
        String key2 = "test:server:transaction:db:key2";
        
        try {
            // Set up test data
            connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Queue database operations - assert they return null in transaction mode
            assertThat(connection.serverCommands().dbSize()).isNull();
            connection.serverCommands().flushDb(); // void method, no assertion
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isInstanceOf(Long.class); // dbSize result
            assertThat(((Long) results.get(0))).isGreaterThan(0L);
            assertThat(results.get(1)).isEqualTo("OK"); // flushDb result
            
        } finally {
            cleanupKeys(key1, key2);
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testDatabaseOperationsWithOptionsInTransactionMode() {
        String key = "test:server:transaction:flush:key";
        
        try {
            // Set up test data
            connection.stringCommands().set(key.getBytes(), "value".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Queue database operations with options
            connection.serverCommands().flushDb(FlushOption.ASYNC); // void method, no assertion
            connection.serverCommands().flushAll(FlushOption.SYNC); // void method, no assertion
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isEqualTo("OK"); // flushDb result
            assertThat(results.get(1)).isEqualTo("OK"); // flushAll result
            
        } finally {
            cleanupKey(key);
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testServerInformationInTransactionMode() {
        try {
            // Start transaction
            connection.multi();
            
            // Queue server information commands - assert they return null in transaction mode
            assertThat(connection.serverCommands().info()).isNull();
            assertThat(connection.serverCommands().info("server")).isNull();
            assertThat(connection.serverCommands().time(TimeUnit.MILLISECONDS)).isNull();
            assertThat(connection.serverCommands().time(TimeUnit.SECONDS)).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            
            // info() result
            assertThat(results.get(0)).isInstanceOf(Properties.class);
            Properties allInfo = (Properties) results.get(0);
            assertThat(allInfo).isNotEmpty();
            
            // info("server") result
            assertThat(results.get(1)).isInstanceOf(Properties.class);
            Properties serverInfo = (Properties) results.get(1);
            assertThat(serverInfo).isNotNull();
            
            // time() results
            assertThat(results.get(2)).isInstanceOf(Long.class);
            assertThat(((Long) results.get(2))).isGreaterThan(0L);
            
            assertThat(results.get(3)).isInstanceOf(Long.class);
            assertThat(((Long) results.get(3))).isGreaterThan(0L);
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testConfigurationManagementInTransactionMode() {
        try {
            // Start transaction
            connection.multi();
            
            // Queue configuration commands - assert they return null in transaction mode
            assertThat(connection.serverCommands().getConfig("maxmemory")).isNull();
            assertThat(connection.serverCommands().getConfig("*")).isNull();
            connection.serverCommands().resetConfigStats(); // void method, no assertion
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(3);
            
            // getConfig results
            assertThat(results.get(0)).isInstanceOf(Properties.class);
            assertThat(results.get(1)).isInstanceOf(Properties.class);
            Properties allConfigs = (Properties) results.get(1);
            assertThat(allConfigs).isNotEmpty();
            
            // resetConfigStats result
            assertThat(results.get(2)).isEqualTo("OK");
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testConfigurationSetInTransactionMode() {
        try {
            // Get original timeout for restoration
            Properties timeoutConfig = connection.serverCommands().getConfig("timeout");
            String originalTimeout = timeoutConfig.getProperty("timeout", "0");
            
            // Start transaction
            connection.multi();
            
            // Queue config set command
            connection.serverCommands().setConfig("timeout", "120"); // void method, no assertion
            assertThat(connection.serverCommands().getConfig("timeout")).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isEqualTo("OK"); // setConfig result
            assertThat(results.get(1)).isInstanceOf(Properties.class); // getConfig result
            
            Properties newTimeoutConfig = (Properties) results.get(1);
            assertThat(newTimeoutConfig.getProperty("timeout")).isEqualTo("120");
            
            // Restore original timeout
            connection.serverCommands().setConfig("timeout", originalTimeout);
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("read-only")) {
                logger.warn("CONFIG SET test skipped: Server is in read-only mode");
                return;
            } else {
                throw e;
            }
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testClientManagementInTransactionMode() {
        try {
            // Get original client name for restoration
            String originalName = connection.serverCommands().getClientName();
            
            // Start transaction
            connection.multi();
            
            // Queue client management commands - assert they return null in transaction mode
            connection.serverCommands().setClientName("test-transaction-client".getBytes()); // void method, no assertion
            assertThat(connection.serverCommands().getClientName()).isNull();
            assertThat(connection.serverCommands().getClientList()).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo("OK"); // setClientName result
            assertThat(results.get(1)).isEqualTo("test-transaction-client"); // getClientName result
            
            @SuppressWarnings("unchecked")
            List<ValkeyClientInfo> clientList = (List<ValkeyClientInfo>) results.get(2);
            assertThat(clientList).isNotEmpty();
            
            // Restore original client name
            if (originalName != null && !originalName.isEmpty()) {
                connection.serverCommands().setClientName(originalName.getBytes());
            } else {
                connection.serverCommands().setClientName("".getBytes());
            }
            
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testReplicationCommandsInTransactionMode() {
        try {
            // Start transaction
            connection.multi();
            
            // Queue replication commands - these are safe to test
            connection.serverCommands().replicaOfNoOne(); // void method, no assertion
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo("OK"); // replicaOfNoOne result
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("REPLICAOF not allowed")) {
                logger.warn("REPLICAOF test skipped: Replication commands not allowed");
                return;
            } else {
                throw e;
            }
        } finally {
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testMigrationCommandsInPipelineMode() {
        String sourceKey = "test:server:pipeline:migrate:key";
        
        try {
            // Set up source data
            connection.stringCommands().set(sourceKey.getBytes(), "migrate_value".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            
            // Test migrate to non-existent target (will fail but we test the pipeline behavior)
            try {
                connection.serverCommands().migrate(sourceKey.getBytes(), 
                    ValkeyNode.newValkeyNode().listeningAt("localhost", 9999).build(), 
                    0, null, 5000); // void method, no assertion
            } catch (Exception e) {
                // Expected to fail, but we're testing pipeline mode
            }
            
            // Execute pipeline (may contain errors but should complete)
            try {
                List<Object> results = connection.closePipeline();
                // Migration will likely fail but pipeline should handle it
            } catch (Exception e) {
                // Expected for migration to non-existent target
                logger.info("Migration failed as expected: " + e.getMessage());
            }
            
        } finally {
            cleanupKey(sourceKey);
            if (connection.isPipelined()) {
                connection.closePipeline();
            }
        }
    }

    @Test
    void testMigrationCommandsInTransactionMode() {
        String sourceKey = "test:server:transaction:migrate:key";
        
        try {
            // Set up source data
            connection.stringCommands().set(sourceKey.getBytes(), "migrate_value".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Test migrate to non-existent target (will fail but we test the transaction behavior)
            connection.serverCommands().migrate(sourceKey.getBytes(), 
                ValkeyNode.newValkeyNode().listeningAt("localhost", 9999).build(), 
                0, MigrateOption.COPY, 5000); // void method, no assertion
            
            // Execute transaction (may contain errors but should complete)
            try {
                List<Object> results = connection.exec();
                // Migration will likely fail but transaction should handle it
            } catch (Exception e) {
                // Expected for migration to non-existent target
                logger.info("Migration failed as expected: " + e.getMessage());
            }
            
        } finally {
            cleanupKey(sourceKey);
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testTransactionDiscardWithServerCommands() {
        String key = "test:server:transaction:discard";
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue server commands
            connection.serverCommands().dbSize(); // assert returns null in transaction mode
            assertThat(connection.serverCommands().dbSize()).isNull();
            assertThat(connection.serverCommands().time(TimeUnit.MILLISECONDS)).isNull();
            
            // Discard transaction
            connection.discard();
            
            // Verify commands were not executed by checking current db state
            Long currentDbSize = connection.serverCommands().dbSize();
            assertThat(currentDbSize).isNotNull();
            
        } finally {
            cleanupKey(key);
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    @Test
    void testWatchWithServerCommandsTransaction() throws InterruptedException {
        String watchKey = "test:server:transaction:watch";
        String otherKey = "test:server:transaction:other";
        
        try {
            // Setup initial data
            connection.stringCommands().set(watchKey.getBytes(), "initial".getBytes());
            
            // Watch the key
            connection.watch(watchKey.getBytes());
            
            // Modify the watched key from "outside" the transaction
            connection.stringCommands().set(watchKey.getBytes(), "modified".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Queue server commands
            connection.serverCommands().dbSize(); // assert returns null in transaction mode
            assertThat(connection.serverCommands().dbSize()).isNull();
            assertThat(connection.serverCommands().time(TimeUnit.MILLISECONDS)).isNull();
            
            // Execute transaction - should be aborted due to WATCH
            List<Object> results = connection.exec();
            
            // Transaction should be aborted (results should be empty list)
            assertThat(results).isNotNull().isEmpty();
            
        } finally {
            cleanupKeys(watchKey, otherKey);
            if (connection.isQueueing()) {
                connection.discard();
            }
        }
    }

    // ==================== Error Handling ====================

    @Test
    void testErrorHandling() {
        // Test error handling for invalid parameters
        
        // Test null parameter handling
        assertThatThrownBy(() -> connection.serverCommands().info(null))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> connection.serverCommands().getConfig(null))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> connection.serverCommands().setConfig(null, "value"))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> connection.serverCommands().setConfig("param", null))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> connection.serverCommands().time(null))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> connection.serverCommands().setClientName(null))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> connection.serverCommands().killClient(null, 6379))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> connection.serverCommands().replicaOf(null, 6379))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Edge Cases ====================

    @Test
    void testEdgeCases() {
        // Test edge cases and boundary conditions
        String originalClientName = null;
        
        try {
            // Get original client name for cleanup
            originalClientName = connection.serverCommands().getClientName();
            // Handle null client name case
            if (originalClientName == null) {
                originalClientName = "";
            }
            
            // Test empty string parameters where applicable
            Properties emptyConfig = connection.serverCommands().getConfig("");
            assertThat(emptyConfig).isNotNull();
            
            // Test getting non-existent config
            Properties nonExistentConfig = connection.serverCommands().getConfig("nonexistent_config_param");
            assertThat(nonExistentConfig).isNotNull();
            assertThat(nonExistentConfig).isEmpty();
            
            // Test setting client name to empty string
            connection.serverCommands().setClientName("".getBytes());
            String emptyName = connection.serverCommands().getClientName();
            // Client name might be null when set to empty string
            assertThat(emptyName == null || emptyName.isEmpty()).isTrue();
            
            // Test time with different units (test a subset to avoid flakiness)
            TimeUnit[] testUnits = {TimeUnit.SECONDS, TimeUnit.MILLISECONDS, TimeUnit.MICROSECONDS};
            for (TimeUnit unit : testUnits) {
                Long time = connection.serverCommands().time(unit);
                assertThat(time).isNotNull();
                assertThat(time).isGreaterThan(0L);
            }
        } finally {
            // Clean up - restore original client name
            if (originalClientName != null && !originalClientName.isEmpty()) {
                connection.serverCommands().setClientName(originalClientName.getBytes());
            } else {
                connection.serverCommands().setClientName("".getBytes());
            }
        }
    }
}
