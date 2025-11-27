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
import glide.api.GlideClient;
import glide.api.models.Batch;

/**
 * 
 * @author Ilia Kolominsky
 * @since 2.0
 */
class StandaloneGlideClientAdapter implements UnifiedGlideClient {

    private final GlideClient glideClient;
    private Batch currentBatch;
    private BatchStatus batchStatus = BatchStatus.None;

    StandaloneGlideClientAdapter(GlideClient glideClient) {
        this.glideClient = glideClient;
    }

    @Override
    public Object customCommand(GlideString[] args) throws InterruptedException, ExecutionException {
        if (currentBatch != null) {
            currentBatch.customCommand(args);
            return null;
        }
        return glideClient.customCommand(args).get();
    }

    @Override
    public Object[] execBatch() throws InterruptedException, ExecutionException {
        if (currentBatch == null) {
            throw new IllegalStateException("No batch in progress");
        }
        return glideClient.exec(currentBatch, false).get();
    }

    @Override
    public Object getNativeClient() {
        return glideClient;
    }

    @Override
    public void close() throws ExecutionException {
        // The native client might be pooled - dont close
    }

    @Override
    public BatchStatus getBatchStatus() {
        return batchStatus;
    }

    @Override
    public int getBatchCount() {
        if (currentBatch == null) {
            throw new IllegalStateException("No batch in progress");
        }
        return currentBatch.getProtobufBatch().getCommandsCount();
    }

    @Override
    public void startNewBatch(boolean atomic) {
        currentBatch = new Batch(atomic).withBinaryOutput();
        batchStatus = atomic ? BatchStatus.Transaction : BatchStatus.Pipeline;
    }

    @Override
    public void discardBatch() {
        currentBatch = null;
        batchStatus = BatchStatus.None;
    }

}
