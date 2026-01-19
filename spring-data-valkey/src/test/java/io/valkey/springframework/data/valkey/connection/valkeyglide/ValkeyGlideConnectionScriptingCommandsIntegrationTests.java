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
import io.valkey.springframework.data.valkey.ValkeySystemException;
import io.valkey.springframework.data.valkey.connection.ReturnType;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyVersion;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideConnection} 
 * Scripting functionality using the RedisScriptingCommands interface directly.
 * 
 * These tests validate the implementation of all RedisScriptingCommands methods:
 * - eval: Execute Lua scripts directly
 * - evalSha: Execute Lua scripts by SHA hash
 * - scriptLoad: Load scripts into cache
 * - scriptExists: Check script existence in cache
 * - scriptFlush: Clear script cache
 * - scriptKill: Kill running scripts
 *
 * Tests cover all three invocation modes:
 * - Immediate mode: Direct execution with results
 * - Pipeline mode: Commands return null, results collected in closePipeline()
 * - Transaction mode: Commands return null, results collected in exec()
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideConnectionScriptingCommandsIntegrationTests extends AbstractValkeyGlideIntegrationTests {

    // Test Lua scripts
    private static final byte[] SIMPLE_RETURN_SCRIPT = "return 'hello'".getBytes();
    private static final byte[] KEY_VALUE_SCRIPT = "return redis.call('get', KEYS[1])".getBytes();
    private static final byte[] ARITHMETIC_SCRIPT = "return tonumber(ARGV[1]) + tonumber(ARGV[2])".getBytes();
    private static final byte[] SET_KEY_SCRIPT = "redis.call('set', KEYS[1], ARGV[1]); return 'OK'".getBytes();
    private static final byte[] BOOLEAN_SCRIPT = "return tonumber(ARGV[1]) > 0".getBytes();
    private static final byte[] MULTI_RETURN_SCRIPT = "return {KEYS[1], ARGV[1], 'static'}".getBytes();
    private static final byte[] COUNT_KEYS_SCRIPT = "return #KEYS".getBytes();

    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:script:key1", "test:script:key2", "test:script:set:key", "test:script:get:key",
            "test:script:pipeline:key1", "test:script:pipeline:key2", "test:script:pipeline:set",
            "test:script:transaction:key1", "test:script:transaction:key2", "test:script:transaction:set",
            "test:script:workflow:counter", "test:script:workflow:data", "test:script:workflow:result"
        };
    }

    // ==================== Basic Scripting Operations (Immediate Mode) ====================

    @Test
    void testEvalBasicScripts() {
        try {
            // Test simple return script
            byte[] result1 = connection.scriptingCommands().eval(SIMPLE_RETURN_SCRIPT, ReturnType.VALUE, 0);
            assertThat(new String(result1)).isEqualTo("hello");
            
            // Test arithmetic script
            Long result2 = connection.scriptingCommands().eval(ARITHMETIC_SCRIPT, ReturnType.INTEGER, 0, "5".getBytes(), "3".getBytes());
            assertThat(result2).isEqualTo(8L);
            
            // Test boolean script
            Boolean result3 = connection.scriptingCommands().eval(BOOLEAN_SCRIPT, ReturnType.BOOLEAN, 0, "1".getBytes());
            assertThat(result3).isTrue();
            
            Boolean result4 = connection.scriptingCommands().eval(BOOLEAN_SCRIPT, ReturnType.BOOLEAN, 0, "0".getBytes());
            assertThat(result4).isFalse();
            
            // Test count keys script
            Long result5 = connection.scriptingCommands().eval(COUNT_KEYS_SCRIPT, ReturnType.INTEGER, 2, "key1".getBytes(), "key2".getBytes());
            assertThat(result5).isEqualTo(2L);
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testEvalWithKeysAndArgs() {
        String testKey = "test:script:key1";
        byte[] testKeyBytes = testKey.getBytes();
        byte[] testValue = "testvalue".getBytes();
        
        try {
            // Set up test data
            connection.stringCommands().set(testKeyBytes, testValue);
            
            // Test script that reads a key
            byte[] result1 = connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, testKeyBytes);
            assertThat(new String(result1)).isEqualTo("testvalue");
            
            // Test script that sets a key
            String setKey = "test:script:set:key";
            byte[] setKeyBytes = setKey.getBytes();
            byte[] setValue = "newvalue".getBytes();
            
            // Note, the docs for ReturnType.STATUS is probably incorrect by stating that STATUS should return byte[]
            String result2 = connection.scriptingCommands().eval(SET_KEY_SCRIPT, ReturnType.STATUS, 1, setKeyBytes, setValue);
            assertThat(new String(result2)).isEqualTo("OK");
            
            // Verify the key was set
            byte[] retrievedValue = connection.stringCommands().get(setKeyBytes);
            assertThat(retrievedValue).isEqualTo(setValue);
            
        } finally {
            cleanupKey(testKey);
            cleanupKey("test:script:set:key");
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testEvalMultiReturnTypes() {
        try {
            // Test MULTI return type
            Object[] result = connection.scriptingCommands().eval(MULTI_RETURN_SCRIPT, ReturnType.MULTI, 1, "mykey".getBytes(), "myarg".getBytes());
            assertThat(result).hasSize(3);
            assertThat(new String((byte[]) result[0])).isEqualTo("mykey");
            assertThat(new String((byte[]) result[1])).isEqualTo("myarg");
            assertThat(new String((byte[]) result[2])).isEqualTo("static");
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    @EnabledOnValkeyVersion("7.0") // Error handling for scriptExists() with no args differs in Redis < 7.0
    void testScriptLoadAndExists() {
        try {
            // Test scriptLoad
            String sha1 = connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT);
            assertThat(sha1).isNotNull();
            assertThat(sha1).hasSize(40); // SHA1 hash is 40 characters
            
            String sha2 = connection.scriptingCommands().scriptLoad(ARITHMETIC_SCRIPT);
            assertThat(sha2).isNotNull();
            assertThat(sha2).hasSize(40);
            assertThat(sha2).isNotEqualTo(sha1); // Different scripts should have different hashes
            
            // Test scriptExists with single script
            List<Boolean> exists1 = connection.scriptingCommands().scriptExists(sha1);
            assertThat(exists1).hasSize(1);
            assertThat(exists1.get(0)).isTrue();
            
            // Test scriptExists with multiple scripts
            List<Boolean> exists2 = connection.scriptingCommands().scriptExists(sha1, sha2);
            assertThat(exists2).hasSize(2);
            assertThat(exists2.get(0)).isTrue();
            assertThat(exists2.get(1)).isTrue();
            
            // Test scriptExists with non-existent script
            String fakeSha = "0123456789abcdef0123456789abcdef01234567";
            List<Boolean> exists3 = connection.scriptingCommands().scriptExists(sha1, fakeSha, sha2);
            assertThat(exists3).hasSize(3);
            assertThat(exists3.get(0)).isTrue();
            assertThat(exists3.get(1)).isFalse();
            assertThat(exists3.get(2)).isTrue();
            
            // Test scriptExists with empty array
            assertThatThrownBy(() -> connection.scriptingCommands().scriptExists())
                .isInstanceOf(ValkeySystemException.class)
                .hasMessageContaining("wrong number of arguments for 'script|exists' command");

            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testEvalShaScripts() {
        try {
            // Load scripts
            String sha1 = connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT);
            String sha2 = connection.scriptingCommands().scriptLoad(ARITHMETIC_SCRIPT);
            
            // Test evalSha with String SHA
            byte[] result1 = connection.scriptingCommands().evalSha(sha1, ReturnType.VALUE, 0);
            assertThat(new String(result1)).isEqualTo("hello");
            
            Long result2 = connection.scriptingCommands().evalSha(sha2, ReturnType.INTEGER, 0, "10".getBytes(), "20".getBytes());
            assertThat(result2).isEqualTo(30L);
            
            // Test evalSha with byte[] SHA
            byte[] sha1Bytes = sha1.getBytes();
            byte[] sha2Bytes = sha2.getBytes();
            
            byte[] result3 = connection.scriptingCommands().evalSha(sha1Bytes, ReturnType.VALUE, 0);
            assertThat(new String(result3)).isEqualTo("hello");
            
            Long result4 = connection.scriptingCommands().evalSha(sha2Bytes, ReturnType.INTEGER, 0, "15".getBytes(), "25".getBytes());
            assertThat(result4).isEqualTo(40L);
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptFlush() {
        try {
            // Load a script
            String sha = connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT);
            
            // Verify script exists
            List<Boolean> existsBefore = connection.scriptingCommands().scriptExists(sha);
            assertThat(existsBefore.get(0)).isTrue();
            
            // Flush script cache
            connection.scriptingCommands().scriptFlush();
            
            // Verify script no longer exists
            List<Boolean> existsAfter = connection.scriptingCommands().scriptExists(sha);
            assertThat(existsAfter.get(0)).isFalse();
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptKill() {
        try {
            // Note: SCRIPT KILL is typically used to kill long-running scripts
            // Since we can't easily create a long-running script in tests,
            // we test that the command executes without error when no script is running
            // This should complete without throwing an exception or return an error message
            assertThatThrownBy(() -> connection.scriptingCommands().scriptKill())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("No scripts in execution right now");
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    void testScriptingWithBinaryData() {
        String testKey = "test:script:binary";
        byte[] binaryData = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        byte[] setBinaryScript = "redis.call('set', KEYS[1], ARGV[1]); return redis.call('get', KEYS[1])".getBytes();
        
        try {
            // Test script with binary data
            byte[] result = connection.scriptingCommands().eval(setBinaryScript, ReturnType.VALUE, 1, testKey.getBytes(), binaryData);
            assertThat(result).isEqualTo(binaryData);
            
        } finally {
            cleanupKey(testKey);
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptingErrorHandling() {
        try {
            // Test script with syntax error
            byte[] invalidScript = "return invalid syntax here".getBytes();
            assertThatThrownBy(() -> connection.scriptingCommands().eval(invalidScript, ReturnType.VALUE, 0))
                .isInstanceOf(DataAccessException.class);
            
            // Test evalSha with non-existent SHA
            String fakeSha = "0123456789abcdef0123456789abcdef01234567";
            assertThatThrownBy(() -> connection.scriptingCommands().evalSha(fakeSha, ReturnType.VALUE, 0))
                .isInstanceOf(DataAccessException.class);
            
            // Test script with wrong number of keys
            byte[] keyScript = "return KEYS[2]".getBytes();
            Object result = connection.scriptingCommands().eval(keyScript, ReturnType.VALUE, 1, "key1".getBytes());
            assertThat(result).isNull(); // KEYS[2] does not exist, should return nil
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testEvalShaWithoutPreLoadingScript() {
        // This test simulates what DefaultScriptExecutor does: try evalSha first without loading
        try {
            // Use a fake SHA1 hash that definitely doesn't exist in Valkey
            // This should trigger a NOSCRIPT error, which is what we need to test
            String fakeSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 40 chars of 'a'
            
            // Try to execute using evalSha WITHOUT loading the script first
            // This should fail with NOSCRIPT error, which is what we need to test
            Exception caughtException = null;
            try {
                connection.scriptingCommands().evalSha(fakeSha, ReturnType.VALUE, 0);
            } catch (Exception ex) {
                caughtException = ex;
            }
            
            // Verify we got an exception
            assertThat(caughtException).as("Expected NOSCRIPT exception when calling evalSha with uncached script").isNotNull();
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptingWithEmptyKeysAndArgs() {
        try {
            // Test script with no keys or args
            byte[] result1 = connection.scriptingCommands().eval(SIMPLE_RETURN_SCRIPT, ReturnType.VALUE, 0);
            assertThat(new String(result1)).isEqualTo("hello");
            
            // Test script that expects args but gets none
            byte[] argScript = "return ARGV[1] or 'default'".getBytes();
            byte[] result2 = connection.scriptingCommands().eval(argScript, ReturnType.VALUE, 0);
            assertThat(new String(result2)).isEqualTo("default");
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    // ==================== Pipeline Mode Tests ====================

    @Test
    void testBasicScriptingCommandsInPipelineMode() {
        try {
            // Load scripts first
            String sha1 = connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT);
            String sha2 = connection.scriptingCommands().scriptLoad(ARITHMETIC_SCRIPT);
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue scripting commands - assert they return null in pipeline mode
            assertThat((Object) connection.scriptingCommands().eval(SIMPLE_RETURN_SCRIPT, ReturnType.VALUE, 0)).isNull();
            assertThat((Object) connection.scriptingCommands().evalSha(sha1, ReturnType.VALUE, 0)).isNull();
            assertThat((Object) connection.scriptingCommands().eval(ARITHMETIC_SCRIPT, ReturnType.INTEGER, 0, "7".getBytes(), "3".getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().evalSha(sha2, ReturnType.INTEGER, 0, "10".getBytes(), "5".getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().scriptExists(sha1, sha2)).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(5);
            assertThat((byte[]) results.get(0)).isEqualTo("hello".getBytes()); // eval result
            assertThat((byte[]) results.get(1)).isEqualTo("hello".getBytes()); // evalSha result
            assertThat((Long) results.get(2)).isEqualTo(10L); // arithmetic eval result
            assertThat((Long) results.get(3)).isEqualTo(15L); // arithmetic evalSha result
            
            @SuppressWarnings("unchecked")
            List<Boolean> existsResult = (List<Boolean>) results.get(4);
            assertThat(existsResult).hasSize(2);
            assertThat(existsResult.get(0)).isTrue();
            assertThat(existsResult.get(1)).isTrue();
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptLoadAndFlushInPipelineMode() {
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue script management commands - assert they return null in pipeline mode
            assertThat(connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT)).isNull();
            assertThat(connection.scriptingCommands().scriptLoad(ARITHMETIC_SCRIPT)).isNull();
            connection.scriptingCommands().scriptFlush(); // void method, no assertion
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isInstanceOf(String.class); // SHA1 hash
            assertThat(results.get(1)).isInstanceOf(String.class); // SHA1 hash
            assertThat((String) results.get(2)).isEqualTo("OK"); // scriptFlush result
            
            String sha1 = (String) results.get(0);
            String sha2 = (String) results.get(1);
            assertThat(sha1).hasSize(40);
            assertThat(sha2).hasSize(40);
            assertThat(sha1).isNotEqualTo(sha2);
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testEvalShaByteArrayInPipelineMode() {
        try {
            // Load scripts first
            String sha1 = connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT);
            String sha2 = connection.scriptingCommands().scriptLoad(ARITHMETIC_SCRIPT);
            
            // Start pipeline
            connection.openPipeline();
            
            // Queue evalSha commands with byte[] SHA - assert they return null in pipeline mode
            assertThat((Object) connection.scriptingCommands().evalSha(sha1.getBytes(), ReturnType.VALUE, 0)).isNull();
            assertThat((Object) connection.scriptingCommands().evalSha(sha2.getBytes(), ReturnType.INTEGER, 0, "9".getBytes(), "3".getBytes())).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(2);
            assertThat((byte[]) results.get(0)).isEqualTo("hello".getBytes()); // evalSha result
            assertThat((Long) results.get(1)).isEqualTo(12L); // arithmetic evalSha result
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptKillInPipelineMode() {
        try {
            // Start pipeline
            connection.openPipeline();
            
            // Queue scriptKill command - should return null in pipeline mode
            connection.scriptingCommands().scriptKill(); // void method, no assertion
            
            // Execute pipeline - expect error since no script is running
            List<Object> result = connection.closePipeline();
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(DataAccessException.class);
            assertThat(((DataAccessException) result.get(0)).getMessage()).contains("No scripts in execution right now");
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptingWithKeysInPipelineMode() {
        String key1 = "test:script:pipeline:key1";
        String key2 = "test:script:pipeline:key2";
        String setKey = "test:script:pipeline:set";
        
        try {
            // Setup test data
            connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Start pipeline
            connection.openPipeline();
            // Queue script commands with keys - assert they return null in pipeline mode
            assertThat((Object) connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, key1.getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, key2.getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().eval(SET_KEY_SCRIPT, ReturnType.STATUS, 1, setKey.getBytes(), "newvalue".getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, setKey.getBytes())).isNull();
            
            // Execute pipeline
            List<Object> results = connection.closePipeline();
            
            // Verify results
            assertThat(results).hasSize(4);
            assertThat((byte[]) results.get(0)).isEqualTo("value1".getBytes());
            assertThat((byte[]) results.get(1)).isEqualTo("value2".getBytes());
            assertThat((String) results.get(2)).isEqualTo("OK");
            assertThat((byte[]) results.get(3)).isEqualTo("newvalue".getBytes());
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(setKey);
            connection.scriptingCommands().scriptFlush();
        }
    }

    // ==================== Transaction Mode Tests ====================

    @Test
    void testBasicScriptingCommandsInTransactionMode() {
        try {
            // Load scripts first
            String sha1 = connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT);
            String sha2 = connection.scriptingCommands().scriptLoad(ARITHMETIC_SCRIPT);
            
            // Start transaction
            connection.multi();
            
            // Queue scripting commands - assert they return null in transaction mode
            assertThat((Object) connection.scriptingCommands().eval(SIMPLE_RETURN_SCRIPT, ReturnType.VALUE, 0)).isNull();
            assertThat((Object) connection.scriptingCommands().evalSha(sha1, ReturnType.VALUE, 0)).isNull();
            assertThat((Object) connection.scriptingCommands().eval(ARITHMETIC_SCRIPT, ReturnType.INTEGER, 0, "8".getBytes(), "4".getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().evalSha(sha2, ReturnType.INTEGER, 0, "12".getBytes(), "6".getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().scriptExists(sha1, sha2)).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(5);
            assertThat((byte[]) results.get(0)).isEqualTo("hello".getBytes()); // eval result
            assertThat((byte[]) results.get(1)).isEqualTo("hello".getBytes()); // evalSha result
            assertThat((Long) results.get(2)).isEqualTo(12L); // arithmetic eval result
            assertThat((Long) results.get(3)).isEqualTo(18L); // arithmetic evalSha result
            
            @SuppressWarnings("unchecked")
            List<Boolean> existsResult = (List<Boolean>) results.get(4);
            assertThat(existsResult).hasSize(2);
            assertThat(existsResult.get(0)).isTrue();
            assertThat(existsResult.get(1)).isTrue();
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptManagementInTransactionMode() {
        try {
            // Start transaction
            connection.multi();
            
            // Queue script management commands - assert they return null in transaction mode
            assertThat((Object) connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT)).isNull();
            assertThat((Object) connection.scriptingCommands().scriptLoad(ARITHMETIC_SCRIPT)).isNull();
            connection.scriptingCommands().scriptFlush(); // void method, no assertion
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isInstanceOf(String.class); // SHA1 hash
            assertThat(results.get(1)).isInstanceOf(String.class); // SHA1 hash
            assertThat((String) results.get(2)).isEqualTo("OK"); // scriptFlush result
            
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testEvalShaByteArrayInTransactionMode() {
        try {
            // Load scripts first
            String sha1 = connection.scriptingCommands().scriptLoad(SIMPLE_RETURN_SCRIPT);
            String sha2 = connection.scriptingCommands().scriptLoad(ARITHMETIC_SCRIPT);
            
            // Start transaction
            connection.multi();
            
            // Queue evalSha commands with byte[] SHA - assert they return null in transaction mode
            assertThat((Object) connection.scriptingCommands().evalSha(sha1.getBytes(), ReturnType.VALUE, 0)).isNull();
            assertThat((Object) connection.scriptingCommands().evalSha(sha2.getBytes(), ReturnType.INTEGER, 0, "11".getBytes(), "4".getBytes())).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(2);
            assertThat((byte[]) results.get(0)).isEqualTo("hello".getBytes()); // evalSha result
            assertThat((Long) results.get(1)).isEqualTo(15L); // arithmetic evalSha result
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptKillInTransactionMode() {
        try {
            // Start transaction
            connection.multi();
            
            // Queue scriptKill command - should return null in transaction mode
            connection.scriptingCommands().scriptKill(); // void method, no assertion
            
            // Execute transaction - expect error since no script is running
            List<Object> result = connection.exec();
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(DataAccessException.class);
            assertThat(((DataAccessException) result.get(0)).getMessage()).contains("No scripts in execution right now");
        } finally {
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testScriptingWithKeysInTransactionMode() {
        String key1 = "test:script:transaction:key1";
        String key2 = "test:script:transaction:key2";
        String setKey = "test:script:transaction:set";
        
        try {
            // Setup test data
            connection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            connection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Queue script commands with keys - assert they return null in transaction mode
            assertThat((Object) connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, key1.getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, key2.getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().eval(SET_KEY_SCRIPT, ReturnType.STATUS, 1, setKey.getBytes(), "transactionvalue".getBytes())).isNull();
            assertThat((Object) connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, setKey.getBytes())).isNull();
            
            // Execute transaction
            List<Object> results = connection.exec();
            
            // Verify results
            assertThat(results).isNotNull();
            assertThat(results).hasSize(4);
            assertThat((byte[]) results.get(0)).isEqualTo("value1".getBytes());
            assertThat((byte[]) results.get(1)).isEqualTo("value2".getBytes());
            assertThat((String) results.get(2)).isEqualTo("OK");
            assertThat((byte[]) results.get(3)).isEqualTo("transactionvalue".getBytes());
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(setKey);
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testTransactionDiscardWithScriptingCommands() {
        String key = "test:script:transaction:discard";
        
        try {
            // Start transaction
            connection.multi();
            
            // Queue script commands
            connection.scriptingCommands().eval(SET_KEY_SCRIPT, ReturnType.STATUS, 1, key.getBytes(), "shouldnotbeset".getBytes());
            connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, key.getBytes());
            
            // Discard transaction
            connection.discard();
            
            // Verify key doesn not exist (transaction was discarded)
            byte[] value = connection.stringCommands().get(key.getBytes());
            assertThat(value).isNull();
            
        } finally {
            cleanupKey(key);
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testWatchWithScriptingCommandsTransaction() throws InterruptedException {
        String watchKey = "test:script:transaction:watch";
        String otherKey = "test:script:transaction:other";
        
        try {
            // Setup initial data
            connection.stringCommands().set(watchKey.getBytes(), "initial".getBytes());
            
            // Watch the key
            connection.watch(watchKey.getBytes());
            
            // Modify the watched key from "outside" the transaction
            connection.stringCommands().set(watchKey.getBytes(), "modified".getBytes());
            
            // Start transaction
            connection.multi();
            
            // Queue script commands
            connection.scriptingCommands().eval(SET_KEY_SCRIPT, ReturnType.STATUS, 1, otherKey.getBytes(), "value".getBytes());
            connection.scriptingCommands().eval(KEY_VALUE_SCRIPT, ReturnType.VALUE, 1, otherKey.getBytes());
            
            // Execute transaction - should be aborted due to WATCH
            List<Object> results = connection.exec();
            
            // Transaction should be aborted (results should be null)
            assertThat(results).isNotNull().isEmpty();
            
            // Verify that the other key was not set
            byte[] value = connection.stringCommands().get(otherKey.getBytes());
            assertThat(value).isNull();
            
        } finally {
            cleanupKey(watchKey);
            cleanupKey(otherKey);
            connection.scriptingCommands().scriptFlush();
        }
    }

    // ==================== Comprehensive Scripting Workflow Tests ====================

    @Test
    void testScriptingComprehensiveWorkflow() {
        String counterKey = "test:script:workflow:counter";
        String dataKey = "test:script:workflow:data";
        String resultKey = "test:script:workflow:result";
        
        // Complex script that increments counter and processes data
        byte[] workflowScript = (
            "local counter = redis.call('incr', KEYS[1]) " +
            "redis.call('hset', KEYS[2], 'count', counter) " +
            "redis.call('hset', KEYS[2], 'data', ARGV[1]) " +
            "redis.call('set', KEYS[3], 'processed:' .. ARGV[1] .. ':' .. counter) " +
            "return {counter, 'processed'}"
        ).getBytes();
        
        try {
            // Load the complex script
            String sha = connection.scriptingCommands().scriptLoad(workflowScript);
            assertThat(sha).isNotNull();
            
            // Execute workflow multiple times using eval
            Object[] result1 = connection.scriptingCommands().eval(workflowScript, ReturnType.MULTI, 3, 
                counterKey.getBytes(), dataKey.getBytes(), resultKey.getBytes(), "data1".getBytes());
            assertThat(result1).hasSize(2);
            assertThat((Long) result1[0]).isEqualTo(1L);
            assertThat(new String((byte[]) result1[1])).isEqualTo("processed");
            
            // Execute workflow using evalSha
            Object[] result2 = connection.scriptingCommands().evalSha(sha, ReturnType.MULTI, 3,
                counterKey.getBytes(), dataKey.getBytes(), resultKey.getBytes(), "data2".getBytes());
            assertThat(result2).hasSize(2);
            assertThat((Long) result2[0]).isEqualTo(2L);
            assertThat(new String((byte[]) result2[1])).isEqualTo("processed");
            
            // Verify final state
            byte[] counterValue = connection.stringCommands().get(counterKey.getBytes());
            assertThat(new String(counterValue)).isEqualTo("2");
            
            byte[] resultValue = connection.stringCommands().get(resultKey.getBytes());
            assertThat(new String(resultValue)).isEqualTo("processed:data2:2");
            
            // Verify hash data
            byte[] hashCount = connection.hashCommands().hGet(dataKey.getBytes(), "count".getBytes());
            byte[] hashData = connection.hashCommands().hGet(dataKey.getBytes(), "data".getBytes());
            assertThat(new String(hashCount)).isEqualTo("2");
            assertThat(new String(hashData)).isEqualTo("data2");
            
            // Test script exists
            List<Boolean> scriptExists = connection.scriptingCommands().scriptExists(sha);
            assertThat(scriptExists.get(0)).isTrue();
            
        } finally {
            cleanupKey(counterKey);
            cleanupKey(dataKey);
            cleanupKey(resultKey);
            connection.scriptingCommands().scriptFlush();
        }
    }

    @Test
    void testComplexScriptingWithPipelineAndTransaction() {
        String key1 = "test:script:complex:key1";
        String key2 = "test:script:complex:key2";
        String key3 = "test:script:complex:key3";
        
        // Script that performs multiple operations
        byte[] complexScript = (
            "redis.call('set', KEYS[1], ARGV[1]) " +
            "redis.call('set', KEYS[2], ARGV[2]) " +
            "local val1 = redis.call('get', KEYS[1]) " +
            "local val2 = redis.call('get', KEYS[2]) " +
            "redis.call('set', KEYS[3], val1 .. ':' .. val2) " +
            "return redis.call('get', KEYS[3])"
        ).getBytes();
        
        try {
            // Test in immediate mode
            byte[] result1 = connection.scriptingCommands().eval(complexScript, ReturnType.VALUE, 3,
                key1.getBytes(), key2.getBytes(), key3.getBytes(), "value1".getBytes(), "value2".getBytes());
            assertThat(new String(result1)).isEqualTo("value1:value2");
            
            // Clear keys
            connection.keyCommands().del(key1.getBytes(), key2.getBytes(), key3.getBytes());
            
            // Test in pipeline mode
            connection.openPipeline();
            assertThat((Object) connection.scriptingCommands().eval(complexScript, ReturnType.VALUE, 3,
                key1.getBytes(), key2.getBytes(), key3.getBytes(), "pipe1".getBytes(), "pipe2".getBytes())).isNull();
            List<Object> pipeResults = connection.closePipeline();
            assertThat(pipeResults).hasSize(1);
            assertThat((byte[]) pipeResults.get(0)).isEqualTo("pipe1:pipe2".getBytes());
            
            // Clear keys
            connection.keyCommands().del(key1.getBytes(), key2.getBytes(), key3.getBytes());
            
            // Test in transaction mode
            connection.multi();
            assertThat((Object) connection.scriptingCommands().eval(complexScript, ReturnType.VALUE, 3,
                key1.getBytes(), key2.getBytes(), key3.getBytes(), "tx1".getBytes(), "tx2".getBytes())).isNull();
            List<Object> txResults = connection.exec();
            assertThat(txResults).isNotNull();
            assertThat(txResults).hasSize(1);
            assertThat((byte[]) txResults.get(0)).isEqualTo("tx1:tx2".getBytes());
            
        } finally {
            cleanupKey(key1);
            cleanupKey(key2);
            cleanupKey(key3);
            connection.scriptingCommands().scriptFlush();
        }
    }

}
