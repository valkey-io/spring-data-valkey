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

/**
 * Exception thrown when a Valkey transaction is aborted due to a WATCH conflict.
 * This exception is used to signal that a watched key was modified between
 * the WATCH command and the EXEC command, causing the transaction to be aborted.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideWatchConflictException extends DataAccessException {

    /**
     * Constructor for ValkeyGlideWatchConflictException.
     *
     * @param msg the detail message
     */
    public ValkeyGlideWatchConflictException(String msg) {
        super(msg);
    }

    /**
     * Constructor for ValkeyGlideWatchConflictException.
     *
     * @param msg the detail message
     * @param cause the root cause from the underlying data access API
     */
    public ValkeyGlideWatchConflictException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
