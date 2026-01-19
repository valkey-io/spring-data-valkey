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

import glide.api.models.PubSubMessage;
import glide.api.models.GlideString;

import io.valkey.springframework.data.valkey.connection.DefaultMessage;
import io.valkey.springframework.data.valkey.connection.MessageListener;

/**
 * A delegating pub/sub listener that is configured at client creation time,
 * with the actual listener set later when subscribe() is called.
 */
class DelegatingPubSubListener {
    
    private volatile MessageListener messageListener;
    
    /**
     * Called by Glide when a pub/sub message arrives.
     */
    void onMessage(PubSubMessage msg, Object context) {
        MessageListener listener = this.messageListener;
        if (listener != null && msg != null) {
            byte[] channel = msg.getChannel().getBytes();
            byte[] body = msg.getMessage().getBytes();
            byte[] pattern = msg.getPattern()
                .map(GlideString::getBytes)
                .orElse(null);
            
            listener.onMessage(new DefaultMessage(channel, body), pattern);
            
        }

    }
    
    /**
     * Set the actual listener when subscribe() is called.
     */
    void setListener(MessageListener listener) {
        this.messageListener = listener;
    }
    
    /**
     * Clear the listener when subscription closes.
     */
    void clearListener() {
        this.messageListener = null;
    }
    
    
    boolean hasListener() {
        return messageListener != null;
    }
}
