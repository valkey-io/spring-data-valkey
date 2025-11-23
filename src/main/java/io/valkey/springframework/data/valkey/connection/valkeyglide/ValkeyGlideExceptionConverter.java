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

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;
import io.valkey.springframework.data.valkey.ValkeySystemException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.lang.Nullable;

/**
 * Converts Valkey-Glide exceptions to Spring DAO exceptions.
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideExceptionConverter {

    /**
     * Convert a Valkey-Glide exception to a Spring DataAccessException.
     *
     * @param ex The exception to convert
     * @return The converted exception, or null if the exception could not be converted
     */
    @Nullable
    public DataAccessException convert(Exception ex) {
        // Currently, we don't have access to the actual Valkey-Glide exception classes
        // So we have to rely on the exception message and class name for conversion
        // In a real implementation, we would check the exception type using instanceof
        
        // This implementation is a placeholder and should be expanded with real exception types
        // once the Valkey-Glide API is available
        
        String message = ex.getMessage();
        if (message == null) {
            message = ex.getClass().getSimpleName();
        }
        
        // Check common Valkey error patterns in the message
        if (message.contains("Connection") && (message.contains("refused") || 
                message.contains("reset") || message.contains("closed") || 
                message.contains("aborted") || message.contains("timeout"))) {
            return new ValkeyConnectionFailureException(message, ex);
        }
        
        if (message.contains("timeout") || message.contains("Timeout")) {
            return new QueryTimeoutException(message, ex);
        }
        
        if (message.contains("WRONGTYPE")) {
            return new InvalidDataAccessApiUsageException(message, ex);
        }
        
        // Handle NOSCRIPT errors - these are API usage errors (script doesn't exist)
        // Valkey-Glide returns "NoScriptError" but Spring's ScriptUtils looks for "NOSCRIPT"
        // We need to normalize the message so the fallback mechanism works
        if (message.contains("NOSCRIPT") || message.contains("NoScriptError") || message.contains("No matching script")) {
            // Convert "NoScriptError" to "NOSCRIPT" for Spring Data Valkey compatibility
            String normalizedMessage = message.replace("NoScriptError", "NOSCRIPT");
            return new InvalidDataAccessApiUsageException(normalizedMessage, ex);
        }
        
        if (message.contains("NOAUTH") || message.contains("Authentication")) {
            return new InvalidDataAccessResourceUsageException(message, ex);
        }
        
        if (message.contains("BUSY") || message.contains("LOADING")) {
            return new ValkeySystemException(message, ex);
        }
        
        // For other exceptions, we need more context
        // This implementation can be expanded based on real error patterns observed
        
        return new ValkeySystemException(message, ex);
    }
}
