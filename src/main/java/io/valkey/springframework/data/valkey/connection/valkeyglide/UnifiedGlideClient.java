/*
 * Copyright 2025 the original author or authors.
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

import java.util.concurrent.ExecutionException;

import glide.api.models.GlideString;

/**
 * Unified interface that abstracts both GlideClient and GlideClusterClient
 * to enable code reuse between standalone and cluster modes.
 * 
 * @author Ilya Kolomin
 * @since 2.0
 */
interface UnifiedGlideClient extends AutoCloseable {
    public enum BatchStatus {
        None,
        Pipeline,
        Transaction,
    }

    BatchStatus getBatchStatus();
    int getBatchCount();
    void startNewBatch(boolean atomic);
    Object[] execBatch() throws InterruptedException, ExecutionException;
    void discardBatch();
    Object customCommand(GlideString[] args) throws InterruptedException, ExecutionException;
    Object getNativeClient();
}
