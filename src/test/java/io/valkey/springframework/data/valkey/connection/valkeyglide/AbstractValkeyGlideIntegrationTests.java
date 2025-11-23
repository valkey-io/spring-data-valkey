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
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * Abstract base class for Valkey Glide integration tests that provides common setup, 
 * teardown, and utility methods to reduce code duplication across test classes.
 * 
 * This class handles:
 * - Connection factory creation and management
 * - Server availability validation
 * - Test lifecycle management (setup/teardown)
 * - Common utility methods for key cleanup with proper error handling
 * - Configuration property access
 * 
 * Subclasses should:
 * - Override {@link #getTestKeyPatterns()} to define their specific test key patterns
 * - Implement their command-specific test methods
 * - Call {@link #cleanupKey(String)} for individual key cleanup in tests
 * 
 * @author Ilya Kolomin
 * @since 2.0
 */
@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(ValkeyGlideConnectionFactoryExtension.class)
public abstract class AbstractValkeyGlideIntegrationTests {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractValkeyGlideIntegrationTests.class);

    protected ValkeyConnectionFactory connectionFactory;
    protected ValkeyConnection connection;

    @BeforeAll
     void setUpAll(@ValkeyStanalone ValkeyConnectionFactory connectionFactory) {
        // Create connection factory
        this.connectionFactory = connectionFactory;
        validateServerExistance(connectionFactory);
    }

    @BeforeEach
    void setUp() {
        connection = connectionFactory.getConnection();
        
        // Clean up any existing test keys
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        if (connection != null && !connection.isClosed()) {
            // Clean up any remaining test keys
            cleanupTestKeys();
            connection.close();
        }
    }

    @AfterAll
    void tearDownAll() {
    }

    // ==================== Helper Methods ====================

    /**
     * Validates that the Valkey server is available by attempting to ping it.
     * 
     * @param factory the connection factory to test
     * @throws AssertionError if server is not reachable
     */
    protected void validateServerExistance(ValkeyConnectionFactory factory) {
        try (ValkeyConnection connection = factory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

    /**
     * Gets the Valkey host from system properties, defaulting to localhost.
     * 
     * @return the Valkey host
     */
    protected String getValkeyHost() {
        return System.getProperty("valkey.host", "localhost");
    }

    /**
     * Gets the Valkey port from system properties, defaulting to 6379.
     * 
     * @return the Valkey port
     */
    protected int getValkeyPort() {
        return Integer.parseInt(System.getProperty("valkey.port", "6379"));
    }

    /**
     * Cleans up all test keys defined by the subclass pattern.
     * This method is called automatically during setup and teardown.
     * 
     * Cleanup exceptions are logged but don't stop the cleanup process to ensure
     * other keys can still be cleaned up. This is important for test isolation.
     */
    protected void cleanupTestKeys() {
        String[] testKeys = getTestKeyPatterns();
        
        for (String key : testKeys) {
            try {
                connection.keyCommands().del(key.getBytes());
            } catch (Exception e) {
                logger.warn("Failed to cleanup test key '{}' during teardown: {}", key, e.getMessage());
                // Continue with other keys - don't let one failure stop the entire cleanup
            }
        }
    }
    
    /**
     * Cleans up a single test key. This method should be called in finally blocks
     * of individual test methods to ensure proper cleanup.
     * 
     * @param key the key to delete
     * @throws RuntimeException if cleanup fails and the failure should be propagated
     */
    protected void cleanupKey(String key) {
        try {
            connection.keyCommands().del(key.getBytes());
        } catch (Exception e) {
            logger.warn("Failed to cleanup test key '{}': {}", key, e.getMessage());
            // For individual key cleanup in tests, we might want to know about failures
            // but typically we don't want to fail the test just because cleanup failed
            // Subclasses can override this behavior if needed
        }
    }

    /**
     * Cleans up multiple test keys. This method should be called in finally blocks
     * of individual test methods to ensure proper cleanup.
     * 
     * @param keys the keys to delete
     */
    protected void cleanupKeys(String... keys) {
        for (String key : keys) {
            cleanupKey(key);
        }
    }

    /**
     * Cleans up a single test key with strict error handling. This method will
     * throw an exception if cleanup fails, which can be useful when cleanup
     * failures indicate a serious problem that should stop the test.
     * 
     * @param key the key to delete
     * @throws RuntimeException if cleanup fails
     */
    protected void cleanupKeyStrict(String key) {
        try {
            connection.keyCommands().del(key.getBytes());
        } catch (Exception e) {
            String message = String.format("Failed to cleanup test key '%s': %s", key, e.getMessage());
            logger.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Returns an array of test key patterns that should be cleaned up during
     * setup and teardown. Subclasses must implement this method to define their
     * specific test key patterns.
     * 
     * @return array of test key patterns used by the subclass
     */
    protected abstract String[] getTestKeyPatterns();

    /**
     * Utility method to merge multiple arrays of test key patterns.
     * Useful for subclasses that need to combine common patterns with specific ones.
     * 
     * @param arrays the arrays to merge
     * @return merged array containing all unique keys
     */
    protected String[] mergeKeyPatterns(String[]... arrays) {
        Set<String> mergedKeys = new HashSet<>();
        for (String[] array : arrays) {
            mergedKeys.addAll(Arrays.asList(array));
        }
        return mergedKeys.toArray(new String[0]);
    }

    /**
     * Common test key patterns that are frequently used across different test classes.
     * Subclasses can use this in combination with their specific patterns.
     * 
     * @return array of common test key patterns
     */
    protected String[] getCommonTestKeyPatterns() {
        return new String[]{
            "non:existent:key",
            "test:common:key1",
            "test:common:key2",
            "test:common:key3"
        };
    }
}
