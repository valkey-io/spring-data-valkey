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
import io.valkey.springframework.data.valkey.connection.AbstractValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyGeoCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyHashCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyHyperLogLogCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyKeyCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyListCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeyPipelineException;
import io.valkey.springframework.data.valkey.connection.ValkeyScriptingCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands;
import io.valkey.springframework.data.valkey.connection.ValkeySetCommands;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands;
import io.valkey.springframework.data.valkey.connection.ValkeySubscribedConnectionException;
import io.valkey.springframework.data.valkey.connection.ValkeyZSetCommands;
import io.valkey.springframework.data.valkey.connection.Subscription;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConverters.ResultMapper;
import io.valkey.springframework.data.valkey.connection.MessageListener;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

// Imports from valkey-glide library
import glide.api.models.GlideString;

/**
 * Connection to a Valkey server using Valkey-Glide client. The connection
 * adapts Valkey-Glide's asynchronous API to Spring Data Valkey's synchronous API.
 *
 * @author Ilia Kolominsky
 */
public class ValkeyGlideConnection extends AbstractValkeyConnection {

    protected final UnifiedGlideClient unifiedClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final List<ResultMapper<?, ?>> batchCommandsConverters = new ArrayList<>();
    private final Set<byte[]> watchedKeys = new HashSet<>();
    private @Nullable Subscription subscription;

    // Command interfaces
    private final ValkeyGlideKeyCommands keyCommands;
    private final ValkeyGlideStringCommands stringCommands;
    private final ValkeyGlideListCommands listCommands;
    private final ValkeyGlideSetCommands setCommands;
    private final ValkeyGlideZSetCommands zSetCommands;
    private final ValkeyGlideHashCommands hashCommands;
    private final ValkeyGlideGeoCommands geoCommands;
    private final ValkeyGlideHyperLogLogCommands hyperLogLogCommands;
    private final ValkeyGlideScriptingCommands scriptingCommands;
    private final ValkeyGlideServerCommands serverCommands;
    private final ValkeyGlideStreamCommands streamCommands;

    /**
     * Creates a new {@link ValkeyGlideConnection} with a unified client adapter.
     * Each connection owns and manages its own client instance.
     *
     * @param unifiedClient unified client adapter (standalone or cluster)
     * @param timeout command timeout in milliseconds
     */
    public ValkeyGlideConnection(UnifiedGlideClient unifiedClient) {
        Assert.notNull(unifiedClient, "UnifiedClient must not be null");
        
        this.unifiedClient = unifiedClient;
        
        // Initialize command interfaces
        this.keyCommands = new ValkeyGlideKeyCommands(this);
        this.stringCommands = new ValkeyGlideStringCommands(this);
        this.listCommands = new ValkeyGlideListCommands(this);
        this.setCommands = new ValkeyGlideSetCommands(this);
        this.zSetCommands = new ValkeyGlideZSetCommands(this);
        this.hashCommands = new ValkeyGlideHashCommands(this);
        this.geoCommands = new ValkeyGlideGeoCommands(this);
        this.hyperLogLogCommands = new ValkeyGlideHyperLogLogCommands(this);
        this.scriptingCommands = new ValkeyGlideScriptingCommands(this);
        this.serverCommands = new ValkeyGlideServerCommands(this);
        this.streamCommands = new ValkeyGlideStreamCommands(this);
    }

    /**
     * Verifies that the connection is open.
     *
     * @throws InvalidDataAccessApiUsageException if the connection is closed
     */
    protected void verifyConnectionOpen() {
        if (isClosed()) {
            throw new InvalidDataAccessApiUsageException("Connection is closed");
        }
    }

    @Override
    public ValkeyGeoCommands geoCommands() {
        return this.geoCommands;
    }

    @Override
    public ValkeyHashCommands hashCommands() {
        return this.hashCommands;
    }

    @Override
    public ValkeyHyperLogLogCommands hyperLogLogCommands() {
        return this.hyperLogLogCommands;
    }

    @Override
    public ValkeyKeyCommands keyCommands() {
        return this.keyCommands;
    }

    @Override
    public ValkeyListCommands listCommands() {
        return this.listCommands;
    }

    @Override
    public ValkeySetCommands setCommands() {
        return this.setCommands;
    }

    @Override
    public ValkeyScriptingCommands scriptingCommands() {
        return this.scriptingCommands;
    }

    @Override
    public ValkeyServerCommands serverCommands() {
        return this.serverCommands;
    }

    @Override
    public ValkeyStreamCommands streamCommands() {
        return this.streamCommands;
    }

    @Override
    public ValkeyStringCommands stringCommands() {
        return this.stringCommands;
    }

    @Override
    public ValkeyZSetCommands zSetCommands() {
        return this.zSetCommands;
    }

    @Override
    public ValkeyCommands commands() {
        return this;
    }

    @Override
    public void close() throws DataAccessException {
        try {
            if (closed.compareAndSet(false, true)) {
                try {
                    unifiedClient.close();
                } catch (Exception ex) {
                    throw new DataAccessException("Error closing Valkey-Glide connection", ex) {};
                }
                
                if (subscription != null) {
                    subscription.close();
                    subscription = null;
                }
            }
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public Object getNativeConnection() {
        verifyConnectionOpen();
        return unifiedClient.getNativeClient();
    }

    @Override
    public boolean isQueueing() {
        return unifiedClient.getBatchStatus() == UnifiedGlideClient.BatchStatus.Transaction;
    }

    @Override
    public boolean isPipelined() {
        return unifiedClient.getBatchStatus() == UnifiedGlideClient.BatchStatus.Pipeline;
    }

    @Override
    public void openPipeline() {
        if (isQueueing()) {
			throw new InvalidDataAccessApiUsageException("Cannot use pipelining while a transaction is active");
		}
        if (!isPipelined()) {
            unifiedClient.startNewBatch(false);
        }
    }

    @Override
    public List<Object> closePipeline() {
        if (!isPipelined()) {
            return new ArrayList<>();
        }

        try {
            if (unifiedClient.getBatchCount() == 0) {
                return new ArrayList<>();
            }

            Object[] results = unifiedClient.execBatch();
            List<Object> resultList = new ArrayList<>(results.length);
            for (int i = 0; i < results.length; i++) {
                Object item = results[i];
                if (item instanceof Exception) {
                    // Convert exceptions in pipeline results
                    resultList.add(new ValkeyGlideExceptionConverter().convert((Exception) item));
                    continue;
                }
                @SuppressWarnings("unchecked")
                ResultMapper<Object, ?> mapper = (ResultMapper<Object, ?>) batchCommandsConverters.get(i);
                resultList.add(mapper.map(item));
            }
            return resultList;
        } catch (Exception ex) {
            throw new ValkeyPipelineException(ex);
        } finally {
            unifiedClient.discardBatch();
            batchCommandsConverters.clear();
        }
    }

    @Override
    public void multi() {
        if (isPipelined()) {
			throw new InvalidDataAccessApiUsageException("Cannot use transaction while a pipeline is open");
		}
        
        if (!isQueueing()) {
            // Create atomic batch (transaction)
            unifiedClient.startNewBatch(true);
        }
    }

    @Override
    public void discard() {
        if (!isQueueing()) { 
            throw new InvalidDataAccessApiUsageException("No ongoing transaction; Did you forget to call multi");
        }
        
        // Clear the current batch and reset transaction state
        unifiedClient.discardBatch();
        batchCommandsConverters.clear();
    }

    @Override
    public List<Object> exec() {
        if (!isQueueing()) {
		    throw new InvalidDataAccessApiUsageException("No ongoing transaction; Did you forget to call multi");
        }
		
        try {
            if (unifiedClient.getBatchCount() == 0) {
                return new ArrayList<>();
            }

            Object[] results = unifiedClient.execBatch();
            
            // Handle transaction abort cases - valkey-glide returns null for WATCH conflicts
            if (results == null) {
                // Return empty list for WATCH conflicts (matches Jedis behavior and Valkey specification)
                return new ArrayList<>();
            }
            
            List<Object> resultList = new ArrayList<>(results.length);
            for (int i = 0; i < results.length; i++) {
                Object item = results[i];
                if (item instanceof Exception) {
                    // Convert exceptions in pipeline results
                    resultList.add(new ValkeyGlideExceptionConverter().convert((Exception) item));
                    continue;
                }
                @SuppressWarnings("unchecked")
                ResultMapper<Object, ?> mapper = (ResultMapper<Object, ?>) batchCommandsConverters.get(i);
                resultList.add(mapper.map(item));
            }
            return resultList;
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        } finally {
            // Clean up transaction state
            unifiedClient.discardBatch();
            batchCommandsConverters.clear();
            // Watches are automatically cleared after EXEC
            watchedKeys.clear();
        }
    }

    @Override
    public void select(int dbIndex) {
        Assert.isTrue(dbIndex >= 0, "DB index must be non-negative");
        try {
            byte[] dbArg = Integer.toString(dbIndex).getBytes(StandardCharsets.US_ASCII);
            execute("SELECT", dbArg);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void unwatch() {
        try {
            if (watchedKeys.isEmpty()) {
                return; // No keys to unwatch
            }
            execute("UNWATCH");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        } finally {
            watchedKeys.clear();
        }
    }

    @Override
    public void watch(byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        if (isQueueing()) {
            throw new InvalidDataAccessApiUsageException("WATCH is not allowed during MULTI");
        }

        try {
            // Execute WATCH immediately to set up key monitoring at connection level
            execute("WATCH", keys);
            
            // Track watched keys for cleanup
            Collections.addAll(watchedKeys, keys);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Long publish(byte[] channel, byte[] message) {
        Assert.notNull(channel, "Channel must not be null");
        Assert.notNull(message, "Message must not be null");

        try {
            Object result = execute("PUBLISH", channel, message);
            return (Long) result;
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void subscribe(MessageListener listener, byte[]... channels) {
        Assert.notNull(listener, "MessageListener must not be null");
        Assert.notNull(channels, "Channels must not be null");
        Assert.noNullElements(channels, "Channels must not contain null elements");

		if (isSubscribed()) {
			throw new ValkeySubscribedConnectionException(
					"Connection already subscribed; use the connection Subscription to cancel or add new channels");
		}

		if (isQueueing() || isPipelined()) {
			throw new InvalidDataAccessApiUsageException("Cannot subscribe in pipeline / transaction mode");
		}

        // TODO: Implement dynamic subscription management when supported by valkey-glide
        throw new UnsupportedOperationException("Dynamic subscriptions not yet implemented");
    }

    @Override
    public void pSubscribe(MessageListener listener, byte[]... patterns) {
        Assert.notNull(listener, "MessageListener must not be null");
        Assert.notNull(patterns, "Patterns must not be null");
        Assert.noNullElements(patterns, "Patterns must not contain null elements");

		if (isSubscribed()) {
			throw new ValkeySubscribedConnectionException(
					"Connection already subscribed; use the connection Subscription to cancel or add new channels");
		}

		if (isQueueing() || isPipelined()) {
			throw new InvalidDataAccessApiUsageException("Cannot subscribe in pipeline / transaction mode");
		}

        // TODO: Implement dynamic subscription management when supported by valkey-glide
        throw new UnsupportedOperationException("Dynamic subscriptions not yet implemented");
    }

    @Override
    public Subscription getSubscription() {
        return subscription;
    }

    @Override
    public boolean isSubscribed() {
        return subscription != null && subscription.isAlive();
    }

    /**
     * Execute a Valkey command using string arguments.
     * 
     * @param command the command to execute
     * @param args the command arguments
     * @return the command result
     */

    /**
     * Executes a Valkey command with arguments and converts the raw driver result
     * into a strongly typed value using the provided {@link ResultMapper}.
     *
     * <p>Behavior depends on whether pipelining/transaction is enabled:
     * <ul>
     *   <li><b>Immediate mode</b> – the command is sent directly to the driver,
     *       and the raw result is synchronously converted via {@code mapper.map(raw)}.</li>
     *   <li><b>Pipeline/Transaction mode</b> – the command and mapper are queued for later execution.
     *       In this case, the method returns {@code null}. When
     *       {@link #closePipeline()}/{@link #exec()} are called, all queued commands are flushed,
     *       raw results are collected, and each queued {@code ResultMapper}
     *       is applied in order.</li>
     * </ul>
     *
     * <p>The caller (API layer) is responsible for providing the appropriate
     * {@link ResultMapper} for the Valkey command being executed. This allows each
     * high-level API method to encapsulate its own decoding logic.
     *
     * @param command The Valkey command name (e.g. "GET", "SMEMBERS").
     * @param mapper  A function that knows how to convert the raw driver result
     *                into a strongly typed value of type {@code R}.
     * @param args    The command arguments, already encoded into driver-acceptable
     *                representations (e.g. {@code byte[]} or primitives).
     * @param <R>     The expected return type after mapping the driver result.
     * @return        The mapped result in immediate mode, or {@code null} if
     *                pipelining/transaction is active (result will be available after
     *                {@link #closePipeline()} or {@link #exec()}).
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <I, R> R execute(String command, ResultMapper<I, R> mapper, Object... args) {
        Assert.notNull(args, "Arguments must not be null");
        Assert.notNull(mapper, "ResultMapper must not be null");

        verifyConnectionOpen();
        try {
            // Convert arguments to appropriate format for Glide
            GlideString[] glideArgs = new GlideString[args.length + 1];
            glideArgs[0] = GlideString.of(command);
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    glideArgs[i + 1] = null;
                } else if (args[i] instanceof byte[]) {
                    glideArgs[i + 1] = GlideString.of((byte[]) args[i]);
                } else if (args[i] instanceof String) {
                    glideArgs[i + 1] = GlideString.of((String) args[i]);
                } else {
                    glideArgs[i + 1] = GlideString.of(args[i].toString());
                }
            }
            // Handle pipeline/transaction mode - add command to batch instead of executing
            if (isQueueing() || isPipelined()) {
                // Store converter for later conversion
                batchCommandsConverters.add(mapper);
                // Add command to the current batch
                unifiedClient.customCommand(glideArgs);
                return null; // Return null for queued commands in transaction
            }
            
            // Immediate execution mode
            I result = (I) unifiedClient.customCommand(glideArgs);
            return mapper.map(result);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Object execute(String command, byte[]... args) {
        Assert.notNull(command, "Command must not be null");
        Assert.notNull(args, "Arguments must not be null");
        Assert.noNullElements(args, "Arguments must not contain null elements");
        try {
            // Delegate to the generic execute method
            return execute(command, rawResult -> {
                return ValkeyGlideConverters.defaultFromGlideResult(rawResult);
            },
            (Object[]) args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    protected boolean isActive(ValkeyNode node) {
        Assert.notNull(node, "ValkeyNode must not be null");
        // TODO: Create new valkey-glide GlideClient instance to test connection to the node
        // connection params should be clonned from the current client except host/port
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    protected ValkeySentinelConnection getSentinelConnection(ValkeyNode sentinel) {
        // TODO: Uncomment when sentinel support is added to valkey-glide
        // and implement sentinel connection using a dedicated GlideClient instance
        // Assert.notNull(sentinel, "Sentinel ValkeyNode must not be null");
        throw new UnsupportedOperationException("Sentinel is not supported by this client.");
    }

    @Override
    public byte[] echo(byte[] message) {
        Assert.notNull(message, "Message must not be null");
        try {
            Object result = execute("ECHO", message);
            return result != null ? (byte[]) result : null;
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public String ping() {
        try {
            Object result = execute("PING");
            return result != null ? new String((byte[]) result, StandardCharsets.UTF_8) : null;
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }
}
