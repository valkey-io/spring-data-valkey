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

import glide.api.models.GlideString;

import io.valkey.springframework.data.valkey.connection.MessageListener;
import io.valkey.springframework.data.valkey.connection.SubscriptionListener;
import io.valkey.springframework.data.valkey.connection.util.AbstractSubscription;
import org.springframework.util.Assert;

/**
 * Implementation of {@link Subscription} using valkey-glide.
 *
 * @author Ilia Kolominsky
 */
class ValkeyGlideSubscription extends AbstractSubscription {

    private final UnifiedGlideClient client;
    private final DelegatingPubSubListener pubSubListener;
    private final SubscriptionListener subscriptionListener;

    ValkeyGlideSubscription(MessageListener listener, UnifiedGlideClient client,
            DelegatingPubSubListener pubSubListener) {
        super(listener);
        
        Assert.notNull(client, "UnifiedGlideClient must not be null");
        Assert.notNull(pubSubListener, "DelegatingPubSubListener must not be null");
        
        this.client = client;
        this.pubSubListener = pubSubListener;
        this.subscriptionListener = listener instanceof SubscriptionListener
            ? (SubscriptionListener) listener
            : SubscriptionListener.NO_OP_SUBSCRIPTION_LISTENER;
    }

    @Override
    protected void doSubscribe(byte[]... channels) {
        sendPubsubCommand("SUBSCRIBE_BLOCKING", channels);

        for (byte[] channel : channels) {
            subscriptionListener.onChannelSubscribed(channel, getChannels().size());
        }
    }

    @Override
    protected void doPsubscribe(byte[]... patterns) {
        sendPubsubCommand("PSUBSCRIBE_BLOCKING", patterns);

        for (byte[] pattern : patterns) {
            subscriptionListener.onPatternSubscribed(pattern, getPatterns().size());
        }
    }

    @Override
    protected void doUnsubscribe(boolean all, byte[]... channels) {
        byte[][] toNotify;
        
        if (all) {
            toNotify = getChannels().toArray(new byte[0][]);
            sendPubsubCommand("UNSUBSCRIBE_BLOCKING");
        } else {
            toNotify = channels;
            sendPubsubCommand("UNSUBSCRIBE_BLOCKING", channels);
        }

        for (byte[] channel : toNotify) {
            subscriptionListener.onChannelUnsubscribed(channel, getChannels().size());
        }
    }

    @Override
    protected void doPUnsubscribe(boolean all, byte[]... patterns) {
        byte[][] toNotify;
        
        if (all) {
            toNotify = getPatterns().toArray(new byte[0][]);
            sendPubsubCommand("PUNSUBSCRIBE_BLOCKING");
        } else {
            toNotify = patterns;
            sendPubsubCommand("PUNSUBSCRIBE_BLOCKING", patterns);
        }

        for (byte[] pattern : toNotify) {
            subscriptionListener.onPatternUnsubscribed(pattern, getPatterns().size());
        }
    }

    @Override
    protected void doClose() {
        // Clear listener first to prevent stale messages
        pubSubListener.clearListener();
        
        // Capture channels/patterns BEFORE unsubscribing (they get cleared by parent)
        byte[][] channelsToNotify = getChannels().toArray(new byte[0][]);
        byte[][] patternsToNotify = getPatterns().toArray(new byte[0][]);
        
        // Unsubscribe from SPECIFIC channels we subscribed to, not ALL
        if (channelsToNotify.length > 0) {
            sendPubsubCommand("UNSUBSCRIBE_BLOCKING");
        }
        
        if (patternsToNotify.length > 0) {
            sendPubsubCommand("PUNSUBSCRIBE_BLOCKING");
        }
        
        // Notify subscription callbacks
        for (byte[] channel : channelsToNotify) {
            subscriptionListener.onChannelUnsubscribed(channel, 0);
        }
        for (byte[] pattern : patternsToNotify) {
            subscriptionListener.onPatternUnsubscribed(pattern, 0);
        }
    }

    /**
     * Send a pub/sub command directly to the client using GlideString.
     */
    private void sendPubsubCommand(String command, byte[]... channels) {
        GlideString[] cmd = new GlideString[channels.length + 2];

        int i = 0;
        cmd[i++] = GlideString.of(command);
        for (byte[] channel : channels) {
            cmd[i++] = GlideString.of(channel);
        }

        // Always append timeout = 0
        cmd[i] = GlideString.of("0");

        try {
            client.customCommand(cmd);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ValkeyGlideExceptionConverter().convert(e);
        }
    }
}
