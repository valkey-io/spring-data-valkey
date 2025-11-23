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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.valkey.springframework.data.valkey.connection.ClusterSlotHashUtil;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;

/**
 * Implementation of {@link ValkeyStringCommands} for Valkey-Glide.
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideClusterStringCommands extends ValkeyGlideStringCommands {

    private final ValkeyGlideClusterConnection connection;

    /**
     * Creates a new {@link ValkeyGlideStringCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideClusterStringCommands(ValkeyGlideClusterConnection connection) {
        super(connection);
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    /**
     * Executes mSetNX across multiple cluster nodes in parallel.
     * Groups keys by their cluster node and executes MSETNX operations
     * in parallel on each node. Returns true only if all MSETNX operations succeed.
     * 
     * @param tuples the key-value pairs to set
     * @return true if all keys were set, false otherwise
     */
    @Override
    @Nullable
    public Boolean mSetNX(Map<byte[], byte[]> tuples) {
        Assert.notNull(tuples, "Tuples must not be null");
        Assert.notEmpty(tuples, "Tuples must not be empty");

        // Fast path: all keys in same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(tuples.keySet().toArray(new byte[tuples.keySet().size()][]))) {
            return super.mSetNX(tuples);
        }

        // Parallel path: keys across multiple nodes
        Map<ValkeyClusterNode, List<byte[]>> nodeKeyMap = connection.buildNodeKeyMap(
            tuples.keySet().toArray(new byte[tuples.keySet().size()][]));
        
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        try {
            for (Map.Entry<ValkeyClusterNode, List<byte[]>> entry : nodeKeyMap.entrySet()) {
                ValkeyClusterNode node = entry.getKey();
                List<byte[]> nodeKeys = entry.getValue();
                
                // Build MSETNX command arguments for this node
                GlideString[] args = new GlideString[nodeKeys.size() * 2 + 1];
                args[0] = GlideString.of("MSETNX");
                
                int argIndex = 1;
                for (byte[] key : nodeKeys) {
                    args[argIndex++] = GlideString.of(key);
                    args[argIndex++] = GlideString.of(tuples.get(key));
                }
                
                GlideClusterClient nativeConn = (GlideClusterClient) connection.getNativeConnection();
                CompletableFuture<Boolean> future = nativeConn.customCommand(args, 
                    new ByAddressRoute(node.getHost(), node.getPort()))
                    .thenApply(result -> {
                        ClusterValue<Object> clusterValue = (ClusterValue<Object>) result;
                        return  (Boolean) clusterValue.getSingleValue();
                    });
                
                futures.add(future);
            }
            
            // Wait for all nodes to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            
            // Aggregate results - all must be true
            boolean result = true;
            for (CompletableFuture<Boolean> future : futures) {
                if (!future.get()) {
                    result = false;
                    break;
                }
            }
            
            return result;
            
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while executing mSetNX on cluster", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Error executing mSetNX on cluster", ex);
        } finally {
            // Cancel all futures to ensure proper cleanup
            futures.forEach(f -> f.cancel(true));
        }
    }
}
