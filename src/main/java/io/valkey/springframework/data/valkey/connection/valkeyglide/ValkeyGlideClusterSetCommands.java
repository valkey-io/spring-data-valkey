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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import io.valkey.springframework.data.valkey.connection.ClusterSlotHashUtil;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.util.ByteArraySet;

/**
 * Implementation of {@link ValkeySetCommands} for Valkey Glide in cluster mode.
 * This implementation handles multi-slot routing by fetching set members from different
 * cluster nodes in parallel and performing set operations locally.
 * 
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideClusterSetCommands extends ValkeyGlideSetCommands {

    private final ValkeyGlideClusterConnection connection;

    /**
     * Creates a new {@link ValkeyGlideClusterSetCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideClusterSetCommands(ValkeyGlideClusterConnection connection) {
        super(connection);
        Assert.notNull(connection, "ValkeyGlideClusterConnection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public Set<byte[]> sInter(byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        // Fast path: all keys in same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(keys)) {
            return super.sInter(keys);
        }

        // Parallel path: keys across multiple nodes
        Map<ValkeyClusterNode, List<byte[]>> nodeKeyMap = connection.buildNodeKeyMap(keys);
        
        List<CompletableFuture<Set<byte[]>>> futures = new ArrayList<>();
        
        try {
            // Fetch SMEMBERS for each key in parallel
            for (Map.Entry<ValkeyClusterNode, List<byte[]>> entry : nodeKeyMap.entrySet()) {
                ValkeyClusterNode node = entry.getKey();
                List<byte[]> nodeKeys = entry.getValue();
                
                for (byte[] key : nodeKeys) {
                    // Build SMEMBERS command for this key
                    GlideString[] args = new GlideString[] {
                        GlideString.of("SMEMBERS"),
                        GlideString.of(key)
                    };
                    
                    GlideClusterClient nativeConn = (GlideClusterClient) connection.getNativeConnection();
                    CompletableFuture<Set<byte[]>> future = nativeConn.customCommand(args,
                        new ByAddressRoute(node.getHost(), node.getPort()))
                        .thenApply(result -> {
                            ClusterValue<Object> clusterValue = (ClusterValue<Object>) result;
                            @SuppressWarnings("unchecked")
                            HashSet<GlideString> glideSet = (HashSet<GlideString>) clusterValue.getSingleValue();
                            
                            if (glideSet == null) {
                                return Collections.emptySet();
                            }
                            
                            Set<byte[]> resultSet = new HashSet<>();
                            for (GlideString gs : glideSet) {
                                resultSet.add(gs.getBytes());
                            }
                            return resultSet;
                        });
                    
                    futures.add(future);
                }
            }
            
            // Wait for all nodes to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            
            // Aggregate results - compute intersection
            ByteArraySet result = null;
            
            for (CompletableFuture<Set<byte[]>> future : futures) {
                Set<byte[]> value = future.get();
                ByteArraySet tmp = new ByteArraySet(value);
                
                if (result == null) {
                    result = tmp;
                } else {
                    result.retainAll(tmp);
                    // Early termination if intersection becomes empty
                    if (result.isEmpty()) {
                        break;
                    }
                }
            }
            
            if (result == null || result.isEmpty()) {
                return Collections.emptySet();
            }
            
            return result.asRawSet();
            
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while executing sInter on cluster", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Error executing sInter on cluster", ex);
        } finally {
            // Cancel all futures to ensure proper cleanup
            futures.forEach(f -> f.cancel(true));
        }
    }

    @Override
    @Nullable
    public Set<byte[]> sUnion(byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        // Fast path: all keys in same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(keys)) {
            return super.sUnion(keys);
        }

        // Parallel path: keys across multiple nodes
        Map<ValkeyClusterNode, List<byte[]>> nodeKeyMap = connection.buildNodeKeyMap(keys);
        
        List<CompletableFuture<Set<byte[]>>> futures = new ArrayList<>();
        
        try {
            // Fetch SMEMBERS for each key in parallel
            for (Map.Entry<ValkeyClusterNode, List<byte[]>> entry : nodeKeyMap.entrySet()) {
                ValkeyClusterNode node = entry.getKey();
                List<byte[]> nodeKeys = entry.getValue();
                
                for (byte[] key : nodeKeys) {
                    // Build SMEMBERS command for this key
                    GlideString[] args = new GlideString[] {
                        GlideString.of("SMEMBERS"),
                        GlideString.of(key)
                    };
                    
                    GlideClusterClient nativeConn = (GlideClusterClient) connection.getNativeConnection();
                    CompletableFuture<Set<byte[]>> future = nativeConn.customCommand(args,
                        new ByAddressRoute(node.getHost(), node.getPort()))
                        .thenApply(result -> {
                            ClusterValue<Object> clusterValue = (ClusterValue<Object>) result;
                            @SuppressWarnings("unchecked")
                            HashSet<GlideString> glideSet = (HashSet<GlideString>) clusterValue.getSingleValue();
                            
                            if (glideSet == null) {
                                return Collections.emptySet();
                            }
                            
                            Set<byte[]> resultSet = new HashSet<>();
                            for (GlideString gs : glideSet) {
                                resultSet.add(gs.getBytes());
                            }
                            return resultSet;
                        });
                    
                    futures.add(future);
                }
            }
            
            // Wait for all nodes to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            
            // Aggregate results - compute union
            ByteArraySet result = new ByteArraySet();
            
            for (CompletableFuture<Set<byte[]>> future : futures) {
                Set<byte[]> value = future.get();
                result.addAll(value);
            }
            
            if (result.isEmpty()) {
                return Collections.emptySet();
            }
            
            return result.asRawSet();
            
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while executing sUnion on cluster", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Error executing sUnion on cluster", ex);
        } finally {
            // Cancel all futures to ensure proper cleanup
            futures.forEach(f -> f.cancel(true));
        }
    }

    @Override
    @Nullable
    public Set<byte[]> sDiff(byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        // Fast path: all keys in same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(keys)) {
            return super.sDiff(keys);
        }

        // Parallel path: keys across multiple nodes
        // Note: First key is the base set, others are subtracted from it
        // IMPORTANT: We must preserve the order of keys - first key is base, others are subtracted
        Map<ValkeyClusterNode, List<byte[]>> nodeKeyMap = connection.buildNodeKeyMap(keys);
        
        // Map to store futures by key to preserve order
        Map<byte[], CompletableFuture<Set<byte[]>>> futuresByKey = new HashMap<>();
        
        try {
            // Fetch SMEMBERS for each key in parallel, but track by key to maintain order
            for (Map.Entry<ValkeyClusterNode, List<byte[]>> entry : nodeKeyMap.entrySet()) {
                ValkeyClusterNode node = entry.getKey();
                List<byte[]> nodeKeys = entry.getValue();
                
                for (byte[] key : nodeKeys) {
                    // Build SMEMBERS command for this key
                    GlideString[] args = new GlideString[] {
                        GlideString.of("SMEMBERS"),
                        GlideString.of(key)
                    };
                    
                    GlideClusterClient nativeConn = (GlideClusterClient) connection.getNativeConnection();
                    CompletableFuture<Set<byte[]>> future = nativeConn.customCommand(args,
                        new ByAddressRoute(node.getHost(), node.getPort()))
                        .thenApply(result -> {
                            ClusterValue<Object> clusterValue = (ClusterValue<Object>) result;
                            @SuppressWarnings("unchecked")
                            HashSet<GlideString> glideSet = (HashSet<GlideString>) clusterValue.getSingleValue();
                            
                            if (glideSet == null) {
                                return Collections.emptySet();
                            }
                            
                            Set<byte[]> resultSet = new HashSet<>();
                            for (GlideString gs : glideSet) {
                                resultSet.add(gs.getBytes());
                            }
                            return resultSet;
                        });
                    
                    futuresByKey.put(key, future);
                }
            }
            
            // Wait for all nodes to complete
            CompletableFuture.allOf(futuresByKey.values().toArray(new CompletableFuture[0])).get();
            
            // Aggregate results in the correct order - first key is base, subtract all others
            ByteArraySet result = null;
            
            for (int i = 0; i < keys.length; i++) {
                byte[] key = keys[i];
                Set<byte[]> value = futuresByKey.get(key).get();
                
                if (i == 0) {
                    // First set is the base
                    result = new ByteArraySet(value);
                } else {
                    // Subtract this set from the result
                    result.removeAll(value);
                }
            }
            
            if (result == null || result.isEmpty()) {
                return Collections.emptySet();
            }
            
            return result.asRawSet();
            
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while executing sDiff on cluster", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Error executing sDiff on cluster", ex);
        } finally {
            // Cancel all futures to ensure proper cleanup
            futuresByKey.values().forEach(f -> f.cancel(true));
        }
    }

    @Override
    @Nullable
    public Long sDiffStore(byte[] destKey, byte[]... keys) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        // Build array with destKey and source keys for slot check
        byte[][] allKeys = new byte[keys.length + 1][];
        allKeys[0] = destKey;
        System.arraycopy(keys, 0, allKeys, 1, keys.length);

        // Fast path: all keys in same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(allKeys)) {
            return super.sDiffStore(destKey, keys);
        }

        // Parallel path: compute diff, then store result
        Set<byte[]> diff = sDiff(keys);
        if (diff == null || diff.isEmpty()) {
            return 0L;
        }
        
        return sAdd(destKey, diff.toArray(new byte[diff.size()][]));
    }

    @Override
    @Nullable
    public Long sInterStore(byte[] destKey, byte[]... keys) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        // Build array with destKey and source keys for slot check
        byte[][] allKeys = new byte[keys.length + 1][];
        allKeys[0] = destKey;
        System.arraycopy(keys, 0, allKeys, 1, keys.length);

        // Fast path: all keys in same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(allKeys)) {
            return super.sInterStore(destKey, keys);
        }

        // Parallel path: compute intersection, then store result
        Set<byte[]> result = sInter(keys);
        if (result == null || result.isEmpty()) {
            return 0L;
        }
        
        return sAdd(destKey, result.toArray(new byte[result.size()][]));
    }

    @Override
    @Nullable
    public Long sUnionStore(byte[] destKey, byte[]... keys) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        // Build array with destKey and source keys for slot check
        byte[][] allKeys = new byte[keys.length + 1][];
        allKeys[0] = destKey;
        System.arraycopy(keys, 0, allKeys, 1, keys.length);

        // Fast path: all keys in same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(allKeys)) {
            return super.sUnionStore(destKey, keys);
        }

        // Parallel path: compute union, then store result
        Set<byte[]> result = sUnion(keys);
        if (result == null || result.isEmpty()) {
            return 0L;
        }
        
        return sAdd(destKey, result.toArray(new byte[result.size()][]));
    }

    @Override
    @Nullable
    public Boolean sMove(byte[] srcKey, byte[] destKey, byte[] value) {
        Assert.notNull(srcKey, "Source key must not be null");
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(value, "Value must not be null");

        // Fast path: keys in same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(srcKey, destKey)) {
            return super.sMove(srcKey, destKey, value);
        }

        // Parallel path: manual move across slots
        // Check if source key exists and contains the value
        if (connection.keyCommands().exists(srcKey)) {
            // Remove from source
            if (sRem(srcKey, value) > 0) {
                // Check if value already exists in destination
                if (!sIsMember(destKey, value)) {
                    // Add to destination
                    return sAdd(destKey, value) > 0;
                }
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }
}
