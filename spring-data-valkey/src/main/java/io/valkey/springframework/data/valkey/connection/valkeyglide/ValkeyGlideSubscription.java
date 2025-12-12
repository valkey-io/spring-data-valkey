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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.dao.DataAccessException;
import io.valkey.springframework.data.valkey.connection.MessageListener;
import io.valkey.springframework.data.valkey.connection.Subscription;
import io.valkey.springframework.data.valkey.connection.Message;
import org.springframework.util.Assert;

/**
 * Implementation of {@link Subscription} using valkey-glide.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideSubscription implements Subscription {

    private final Object client; // Will be GlideClient in actual implementation
    private final MessageListener listener;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final List<byte[]> channels = new ArrayList<>();
    private final List<byte[]> patterns = new ArrayList<>();

    /**
     * Create a new {@link ValkeyGlideSubscription} given a client and message listener.
     *
     * @param client the Valkey-Glide client
     * @param listener the message listener
     */
    public ValkeyGlideSubscription(Object client, MessageListener listener) {
        Assert.notNull(client, "Client must not be null!");
        Assert.notNull(listener, "MessageListener must not be null!");

        this.client = client;
        this.listener = listener;
    }

    @Override
    public void subscribe(byte[]... channels) {
        Assert.notNull(channels, "Channels must not be null!");

        // In a real implementation, this would use the valkey-glide client
        // to subscribe to the specified channels
        for (byte[] channel : channels) {
            if (channel == null) {
                throw new IllegalArgumentException("Channel must not be null!");
            }

            // Add channels to tracking list
            this.channels.add(channel);
        }
    }

    @Override
    public void pSubscribe(byte[]... patterns) {
        Assert.notNull(patterns, "Patterns must not be null!");

        // In a real implementation, this would use the valkey-glide client
        // to pattern-subscribe to the specified patterns
        for (byte[] pattern : patterns) {
            if (pattern == null) {
                throw new IllegalArgumentException("Pattern must not be null!");
            }

            // Add pattern to tracking list
            this.patterns.add(pattern);
        }
    }

    @Override
    public boolean isAlive() {
        return active.get();
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            // In a real implementation, this would unsubscribe from all channels and patterns
            // using the valkey-glide client
            channels.clear();
            patterns.clear();
        }
    }

    @Override
    public void unsubscribe() {
        if (active.get()) {
            // In a real implementation, this would unsubscribe from all channels
            // using the valkey-glide client
            channels.clear();
        }
    }

    @Override
    public void pUnsubscribe() {
        if (active.get()) {
            // In a real implementation, this would unsubscribe from all patterns
            // using the valkey-glide client
            patterns.clear();
        }
    }
    
    @Override
    public void unsubscribe(byte[]... channels) {
        if (active.get() && channels != null) {
            // In a real implementation, this would unsubscribe from specific channels
            // using the valkey-glide client
            for (byte[] channel : channels) {
                this.channels.remove(channel);
            }
        }
    }
    
    @Override
    public void pUnsubscribe(byte[]... patterns) {
        if (active.get() && patterns != null) {
            // In a real implementation, this would unsubscribe from specific patterns
            // using the valkey-glide client
            for (byte[] pattern : patterns) {
                this.patterns.remove(pattern);
            }
        }
    }

    @Override
    public Collection<byte[]> getChannels() {
        return new ArrayList<>(channels);
    }

    @Override
    public Collection<byte[]> getPatterns() {
        return new ArrayList<>(patterns);
    }
    
    @Override
    public MessageListener getListener() {
        return listener;
    }
}
