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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import io.micrometer.common.lang.Nullable;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import glide.api.GlideClusterClient;

/**
 * Unified interface that abstracts both GlideClient and GlideClusterClient
 * to enable code reuse between standalone and cluster modes.
 * 
 * @author Ilia Kolominsky
 * @since 2.0
 */
class ClusterGlideClientAdapter implements UnifiedGlideClient {

    private final GlideClusterClient glideClusterClient;
    @Nullable private Route nextCommandRoute = null;

    ClusterGlideClientAdapter(GlideClusterClient glideClusterClient) {
        this.glideClusterClient = glideClusterClient;
    }

    /**
     * A zero-copy map view that reconstructs GlideString keys from String keys while preserving value types.
     * This avoids copying values when working around ClusterValue.of() bug.
     * 
     * <p>ClusterValue.ofMultiValueBinary() incorrectly converts Map<GlideString, V> to
     * Map<String, V> by calling getString() on the keys. This view reverses that
     * transformation without copying the values.
     * 
     * @param <V> The value type (e.g., GlideString for HASH commands, Double for Z commands with scores)
     */
    private static class ReconstructedKeyMap<V> extends AbstractMap<GlideString, V> {
        private final Map<String, V> sourceMap;
        
        ReconstructedKeyMap(Map<String, V> sourceMap) {
            this.sourceMap = sourceMap;
        }
        
        @Override
        public Set<Entry<GlideString, V>> entrySet() {
            return new AbstractSet<Entry<GlideString, V>>() {
                @Override
                public Iterator<Entry<GlideString, V>> iterator() {
                    Iterator<? extends Entry<String, V>> sourceIterator = sourceMap.entrySet().iterator();
                    return new Iterator<Entry<GlideString, V>>() {
                        @Override
                        public boolean hasNext() {
                            return sourceIterator.hasNext();
                        }
                        
                        @Override
                        public Entry<GlideString, V> next() {
                            Entry<String, V> sourceEntry = sourceIterator.next();
                            // Reconstruct GlideString key, preserve original value type
                            return new SimpleEntry<>(
                                GlideString.of(sourceEntry.getKey()),
                                sourceEntry.getValue()
                            );
                        }
                    };
                }
                
                @Override
                public int size() {
                    return sourceMap.size();
                }
            };
        }
        
        @Override
        public int size() {
            return sourceMap.size();
        }
    }

    private boolean needToAggregateResult(@Nullable Route route) {
        // TODO: implement more sophisticated logic to determine if aggregation is needed based on command and route type
        if (route == null) {
            // we need to look at the valkey-glide default route for the command
            // for now, assume no aggregation is needed
            return false;
        } else if (route instanceof SimpleMultiNodeRoute) {
            return true;
        }
        return false;
    }

    /**
     * Determines if a command returns a nested Map structure that should bypass ReconstructedKeyMap.
     * 
     * <p>Stream commands (XREAD, XRANGE, etc.) return nested Maps where the outer map keys are stream keys
     * and the values are LinkedHashMap objects containing the actual stream records. These should not go
     * through ReconstructedKeyMap because:
     * <ul>
     *   <li>The outer map is just a container, not the actual data to be returned</li>
     *   <li>The GlideString types are preserved in the nested field-value arrays</li>
     *   <li>The parseByteRecords() method already handles the nested structure correctly</li>
     * </ul>
     *
     * @param args The command arguments, where args[0] is the command name
     * @return true if this command returns nested Maps that should bypass ReconstructedKeyMap
     */
    private boolean isNestedMapCommand(GlideString[] args) {
        if (args == null || args.length == 0) return false;
        
        String cmd = args[0].getString().toUpperCase();
        
        // Stream commands that return nested Map structures
        switch (cmd) {
            case "XREAD":
            case "XREADGROUP":
            case "XRANGE":
            case "XREVRANGE":
            case "XCLAIM":
            case "XAUTOCLAIM":
            case "XPENDING": // detailed form
                return true;
            default:
                return false;
        }
    }

    /**
     * Determines if a command has a default multi-node route in Valkey Glide's cluster routing.
     * 
     * <p>This method identifies commands that Glide routes to multiple nodes by default (when no explicit
     * route is provided). The routing rules are defined in Glide's Rust implementation at:
     * <code>valkey-glide/glide-core/redis-rs/redis/src/cluster_routing.rs</code> in the <code>base_routing()</code> function.
     * 
     * <p><b>Background:</b> Glide has a bug where <code>ClusterValue.of()</code> incorrectly treats any Map result
     * as a multi-node response (where keys = node addresses). For commands returning Map results (like HGETALL),
     * this causes the Map to be placed in <code>multiValue</code> instead of <code>singleValue</code>.
     * 
     * <p>To work around this bug, we need to distinguish between:
     * <ul>
     *   <li><b>True multi-node results:</b> Commands that Glide routes to multiple nodes by default,
     *       where <code>multiValue</code> IS a map of {node-address → result}</li>
     *   <li><b>False-positive multiValue:</b> Single-node commands returning Map results (like HGETALL),
     *       where <code>multiValue</code> contains the actual result incorrectly</li>
     * </ul>
     *
     * @param args The command arguments, where args[0] is the command name
     * @return true if this command routes to multiple nodes by default in Glide's implementation
     * 
     * @see <a href="https://github.com/valkey-io/valkey-glide/blob/main/glide-core/redis-rs/redis/src/cluster_routing.rs">
     *      Valkey Glide cluster_routing.rs</a>
     */
    private boolean isDefaultMultiNodeCommand(GlideString[] args) {
        if (args == null || args.length == 0) return false;
        
        String cmd = args[0].getString().toUpperCase();
        
        // Commands from RouteBy::AllNodes in cluster_routing.rs
        // These are routed to ALL cluster nodes (primaries and replicas)
        if (cmd.startsWith("ACL ")) {
            return cmd.matches("ACL (SETUSER|DELUSER|SAVE)");
        }
        if (cmd.startsWith("CLIENT ")) {
            return cmd.matches("CLIENT (SETNAME|SETINFO)");
        }
        if (cmd.startsWith("SLOWLOG ")) {
            return cmd.matches("SLOWLOG (GET|LEN|RESET)");
        }
        if (cmd.startsWith("CONFIG ")) {
            return cmd.matches("CONFIG (SET|RESETSTAT|REWRITE)");
        }
        if (cmd.startsWith("SCRIPT ")) {
            return cmd.matches("SCRIPT (FLUSH|LOAD|KILL)");
        }
        if (cmd.startsWith("LATENCY ")) {
            return cmd.matches("LATENCY (RESET|GRAPH|HISTOGRAM|HISTORY|DOCTOR|LATEST)");
        }
        if (cmd.startsWith("PUBSUB ")) {
            return cmd.matches("PUBSUB (NUMPAT|CHANNELS|NUMSUB|SHARDCHANNELS|SHARDNUMSUB)");
        }
        if (cmd.startsWith("FUNCTION ")) {
            return cmd.matches("FUNCTION (KILL|STATS)");
        }
        
        // Commands from RouteBy::AllPrimaries in cluster_routing.rs
        // These are routed to all PRIMARY nodes in the cluster
        switch (cmd) {
            case "DBSIZE":
            case "DEBUG":
            case "FLUSHALL":
            case "FLUSHDB":
            case "FT._ALIASLIST":
            case "FT._LIST":
            case "INFO":
            case "KEYS":
            case "PING":
            case "SCRIPT EXISTS":
            case "UNWATCH":
            case "WAIT":
            case "RANDOMKEY":
            case "WAITAOF":
                return true;
        }
        
        if (cmd.startsWith("FUNCTION ")) {
            return cmd.matches("FUNCTION (DELETE|FLUSH|LOAD|RESTORE)");
        }
        if (cmd.startsWith("MEMORY ")) {
            return cmd.matches("MEMORY (DOCTOR|MALLOC-STATS|PURGE|STATS)");
        }
        
        return false;
    }

    @Override
    public Object customCommand(GlideString[] args) throws InterruptedException, ExecutionException {
        try {
            ClusterValue<?> clusterValue = nextCommandRoute == null
                ? glideClusterClient.customCommand(args).get()
                : glideClusterClient.customCommand(args, nextCommandRoute).get();

            // Case 1: Explicit multi-node route - return multiValue for aggregation
            if (needToAggregateResult(nextCommandRoute)) {
                return clusterValue.getMultiValue();
            }
            
            // Case 2: Default multi-node command (route is null but command routes to multiple nodes)
            // These commands need special handling by the framework, so return multiValue
            if (nextCommandRoute == null && isDefaultMultiNodeCommand(args)) {
                // For default multi-node commands, check if we actually got multi-value data
                if (clusterValue.hasMultiData()) {
                    return clusterValue.getMultiValue();
                }
                // If not, fall through to single-value handling
            }
            
            // Case 3: Single-node command, but ClusterValue.of() bug placed result in multiValue
            // This happens for commands that return Map<GlideString, V> where V can be:
            //   - GlideString for HASH commands (HGETALL)
            //   - Double for Z commands with scores (ZRANGE WITHSCORES, ZDIFF WITHSCORES, etc.)
            // ClusterValue.ofMultiValueBinary() converts GlideString keys to Strings:
            //   Original: Map<GlideString, V> { GlideString("field1"): value1, ... }
            //   Becomes:  Map<String, V> { "field1": value1, ... }
            // Use ReconstructedKeyMap to convert keys back while preserving value types
            // https://github.com/valkey-io/valkey-glide/issues/4963
            //
            // EXCEPTION: Stream commands return nested Maps and should NOT go through ReconstructedKeyMap
            if (clusterValue.hasMultiData() && !isDefaultMultiNodeCommand(args) && !isNestedMapCommand(args)) {
                Map<String, ?> multiValue = clusterValue.getMultiValue();
                // Return a zero-copy view that reconstructs GlideString keys while preserving value types
                return new ReconstructedKeyMap<>(multiValue);
            }
            
            // Case 3b: Nested map commands (like streams) that have multiData but should return it as-is
            if (clusterValue.hasMultiData() && isNestedMapCommand(args)) {
                return clusterValue.getMultiValue();
            }
            
            // Case 4: Normal single-value result
            Object singleValue = clusterValue.getSingleValue();
            return singleValue;
        } finally {
            nextCommandRoute = null; // ensure route is reset even on exception
        }
    }

    @Override
    public Object[] execBatch() throws InterruptedException, ExecutionException {
        throw new IllegalStateException("Transactions and pipelines are not supported in cluster mode");
    }

    @Override
    public Object getNativeClient() {
        return glideClusterClient;
    }

    @Override
    public void close() throws ExecutionException {
        // The native client might be pooled - dont close
    }

    @Override
    public BatchStatus getBatchStatus() {
        return BatchStatus.None;
    }

    @Override
    public int getBatchCount() {
        throw new IllegalStateException("No batch in progress");
    }

    @Override
    public void startNewBatch(boolean atomic) {
        throw new IllegalStateException("Transactions and pipelines are not supported in cluster mode");
    }

    @Override
    public void discardBatch() {
        throw new IllegalStateException("Transactions and pipelines are not supported in cluster mode");
    }

    public void setOneShotRouteForNextCommand(Route glideRoute) {
        nextCommandRoute = glideRoute;
    }
    
    public Object customCommand(GlideString[] args, ValkeyClusterNode node) throws InterruptedException, ExecutionException {
        return glideClusterClient.customCommand(args, new ByAddressRoute(node.getHost(), node.getPort())).get().getSingleValue();
    }
}
