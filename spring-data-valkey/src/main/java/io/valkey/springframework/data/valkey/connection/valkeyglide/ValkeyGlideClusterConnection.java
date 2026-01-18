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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import io.valkey.springframework.data.valkey.ClusterStateFailureException;

import glide.api.GlideClusterClient;
import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute;
import org.springframework.dao.DataAccessException;
import io.valkey.springframework.data.valkey.connection.ClusterInfo;
import io.valkey.springframework.data.valkey.connection.ClusterTopology;
import io.valkey.springframework.data.valkey.connection.ClusterTopologyProvider;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode.LinkState;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode.SlotRange;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterServerCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyNode.NodeType;
import io.valkey.springframework.data.valkey.connection.ValkeyPipelineException;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConnection;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;

/**
 * {@link ValkeyClusterConnection} implementation on top of {@link GlideClusterClient}.
 * Uses the unified client adapter to reuse existing command implementations while
 * adding cluster-specific functionality. Also implements {@link ClusterTopologyProvider}
 * to provide cluster topology information.
 * 
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideClusterConnection extends ValkeyGlideConnection implements ValkeyClusterConnection, ClusterTopologyProvider {

    private final ClusterGlideClientAdapter clusterAdapter;
    private final long cacheTimeoutMs;
    
    // Cached cluster topology with timestamp
    private volatile ClusterTopology cachedTopology;
    private volatile long lastCacheTime;
    
    // Cache of cluster nodes for backward compatibility
    private final Map<String, ValkeyClusterNode> knownNodes = new ConcurrentHashMap<>();
    private final ValkeyGlideClusterServerCommands clusterServerCommands;
    private final ValkeyGlideClusterListCommands clusterListCommands;
    private final ValkeyGlideClusterKeyCommands clusterKeyCommands;
    private final ValkeyGlideClusterStringCommands clusterStringCommands;
    private final ValkeyGlideClusterSetCommands clusterSetCommands;

    public ValkeyGlideClusterConnection(ClusterGlideClientAdapter clusterAdapter) {
        this(clusterAdapter, null, Duration.ofMillis(100), null);
    }

    public ValkeyGlideClusterConnection(ClusterGlideClientAdapter clusterAdapter, 
            @Nullable ValkeyGlideConnectionFactory factory) {
        this(clusterAdapter, factory, Duration.ofMillis(100), null);
    }

    public ValkeyGlideClusterConnection(ClusterGlideClientAdapter clusterAdapter, 
            @Nullable ValkeyGlideConnectionFactory factory,
            @Nullable DelegatingPubSubListener pubSubListener) {
        this(clusterAdapter, factory, Duration.ofMillis(100), pubSubListener);
    }

    public ValkeyGlideClusterConnection(ClusterGlideClientAdapter clusterAdapter, 
            @Nullable ValkeyGlideConnectionFactory factory, 
            Duration cacheTimeout) {
        this(clusterAdapter, factory, cacheTimeout, null);
    }

    public ValkeyGlideClusterConnection(ClusterGlideClientAdapter clusterAdapter, 
            @Nullable ValkeyGlideConnectionFactory factory,
            Duration cacheTimeout,
            @Nullable DelegatingPubSubListener pubSubListener) {
        super(clusterAdapter, factory, pubSubListener);
        Assert.notNull(cacheTimeout, "CacheTimeout must not be null!");
        
        this.clusterAdapter = clusterAdapter;
        this.cacheTimeoutMs = cacheTimeout.toMillis();
        this.clusterServerCommands = new ValkeyGlideClusterServerCommands(this);
        this.clusterListCommands = new ValkeyGlideClusterListCommands(this);
        this.clusterKeyCommands = new ValkeyGlideClusterKeyCommands(this);
        this.clusterStringCommands = new ValkeyGlideClusterStringCommands(this);
        this.clusterSetCommands = new ValkeyGlideClusterSetCommands(this);
    }

    public ClusterGlideClientAdapter getClusterAdapter() {
        return clusterAdapter;
    }

    /**
     * Cleans up server-side connection state before returning client to pool.
     * This ensures the next connection gets a clean client without stale state.
     */
    @Override
    protected void cleanupConnectionState() {
        // Dont use RESET - we will destroy the configured state
        // Use valkey-glide native pipe to selectively clear the state on the backends,
        // adapter and connection object do not matter - they are being destroyed
        // some state cannot be cleared (like stats) but this is acceptable if pooling to be used

        GlideClusterClient nativeClient = (GlideClusterClient) unifiedClient.getNativeClient();

        @SuppressWarnings("unchecked")
        Callable<Void>[] actions = new Callable[] {
                () -> nativeClient.customCommand(new String[]{"UNWATCH"}, SimpleMultiNodeRoute.ALL_NODES).get(),
            };

        for (Callable<Void> action : actions) {
            try {
                action.call();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public ValkeyClusterServerCommands serverCommands() {
        return this.clusterServerCommands;
    }

    @Override
    public ValkeyClusterCommands clusterCommands() {
        return this;
    }

    @Override
    public ValkeyGlideClusterListCommands listCommands() {
        return this.clusterListCommands;
    }

    @Override
    public ValkeyGlideClusterKeyCommands keyCommands() {
        return this.clusterKeyCommands;
    }

    @Override
    public ValkeyGlideClusterStringCommands stringCommands() {
        return this.clusterStringCommands;
    }

    @Override
    public ValkeyGlideClusterSetCommands setCommands() {
        return this.clusterSetCommands;
    }

	@Override
	public void multi() {
		throw new InvalidDataAccessApiUsageException("MULTI is currently not supported in cluster mode");
	}

	@Override
	public List<Object> exec() {
		throw new InvalidDataAccessApiUsageException("EXEC is currently not supported in cluster mode");
	}

	@Override
	public void discard() {
		throw new InvalidDataAccessApiUsageException("DISCARD is currently not supported in cluster mode");
	}

	@Override
	public void watch(byte[]... keys) {
		throw new InvalidDataAccessApiUsageException("WATCH is currently not supported in cluster mode");
	}

	@Override
	public void unwatch() {
		throw new InvalidDataAccessApiUsageException("UNWATCH is currently not supported in cluster mode");
	}

	@Override
	public boolean isPipelined() {
		return false;
	}

	@Override
	public void openPipeline() {
		throw new InvalidDataAccessApiUsageException("Pipeline is not supported in cluster mode");
	}

	@Override
	public List<Object> closePipeline() throws ValkeyPipelineException {
		throw new InvalidDataAccessApiUsageException("Pipeline is not supported in cluster mode");
	}

	@Override
	public ValkeySentinelConnection getSentinelConnection() {
		throw new InvalidDataAccessApiUsageException("Sentinel is not supported in cluster mode");
	}

    @Override
	public void select(int dbIndex) {

		if (dbIndex != 0) {
			throw new InvalidDataAccessApiUsageException("Cannot SELECT non zero index in cluster mode");
		}
	}

	@Override
	public byte[] echo(byte[] message) {
		throw new InvalidDataAccessApiUsageException("Echo not supported in cluster mode");
	}

    @Override
    public String ping(ValkeyClusterNode node) {
        Assert.notNull(node, "node must not be null");
        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));
        return ping();
    }

    @Override
    public Set<byte[]> keys(ValkeyClusterNode node, byte[] pattern) {
        Assert.notNull(node, "node must not be null");
        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));
        return keys(pattern);
    }

    @Override
    public Cursor<byte[]> scan(ValkeyClusterNode node, ScanOptions options) {
        Assert.notNull(node, "node must not be null");
        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));
        return scan(options);
    }

    @Override
    public byte[] randomKey(ValkeyClusterNode node) {
        Assert.notNull(node, "node must not be null");
        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));
        return randomKey();
    }

    // It seems that the interface is inadequate here - it does not allow to specify the node ID.
    // Referencing Jedis implementation, the node id is taken from the node parameter, which is the destination node.
    @Override
    public void clusterSetSlot(ValkeyClusterNode node, int slot, AddSlots mode) {
        Assert.notNull(node, "node must not be null");
        Assert.notNull(mode, "mode must not be null");

        // Lookup node from topology to get the node with ID
        ValkeyClusterNode nodeToUse = getTopology().lookup(node);
        String nodeId = nodeToUse.getId();

        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));

        // Build command based on mode
        switch (mode) {
        case IMPORTING, MIGRATING, NODE -> 
            execute("CLUSTER", (String glideResult) -> glideResult,
                "SETSLOT", String.valueOf(slot), mode.name(), nodeId);
        case STABLE -> 
            execute("CLUSTER", (String glideResult) -> glideResult,
                "SETSLOT", String.valueOf(slot), "STABLE");
        }
        
        // Invalidate topology cache after slot assignment changes
        invalidateTopologyCache();
    }

    private int nullSafeIntValue(@Nullable Integer value) {
        return value != null ? value : Integer.MAX_VALUE;
    }

    @Override
    public List<byte[]> clusterGetKeysInSlot(int slot, Integer count) {
        ValkeyClusterNode node = clusterGetNodeForSlot(slot);
        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));
        return execute("CLUSTER", 
            rawResult -> ValkeyGlideConverters.toBytesList(rawResult), 
            "GETKEYSINSLOT", String.valueOf(slot), String.valueOf(nullSafeIntValue(count)));
    }

    @Override
    public Long clusterCountKeysInSlot(int slot) {
        ValkeyClusterNode node = clusterGetNodeForSlot(slot);
        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));
        return execute("CLUSTER", 
            (Long rawResult) -> rawResult, 
            "COUNTKEYSINSLOT", String.valueOf(slot));
    }

    @Override
    public void clusterAddSlots(ValkeyClusterNode node, int... slots) {
        Assert.notNull(node, "node must not be null");
        Assert.notNull(slots, "slots must not be null");

        Object[] args = new Object[slots.length + 1];
        args[0] = "ADDSLOTS";
        for (int i = 0; i < slots.length; i++) {
            args[i + 1] = String.valueOf(slots[i]);
        }

        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));
        execute("CLUSTER",
            rawResult -> null, args);
        
        // Invalidate topology cache after slot addition
        invalidateTopologyCache();
    }

    @Override
    public void clusterAddSlots(ValkeyClusterNode node, ValkeyClusterNode.SlotRange range) {
        Assert.notNull(range, "range must not be null");
        clusterAddSlots(node, range.getSlotsArray());
    }

    @Override
    public void clusterDeleteSlots(ValkeyClusterNode node, int... slots) {
        Assert.notNull(node, "node must not be null");
        Assert.notNull(slots, "slots must not be null");

        Object[] args = new Object[slots.length + 1];
        args[0] = "DELSLOTS";
        for (int i = 0; i < slots.length; i++) {
            args[i + 1] = String.valueOf(slots[i]);
        }
        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(node.getHost(), node.getPort()));
        execute("CLUSTER",
            rawResult -> null, args);
        
        // Invalidate topology cache after slot deletion
        invalidateTopologyCache();
    }

    @Override
    public void clusterDeleteSlotsInRange(ValkeyClusterNode node, ValkeyClusterNode.SlotRange range) {
        Assert.notNull(range, "range must not be null");
        clusterDeleteSlots(node, range.getSlotsArray());
    }

    @Override
    public void clusterForget(ValkeyClusterNode node) {
        Assert.notNull(node, "node must not be null");
    
        // Lookup the actual node from topology to get its ID
        ValkeyClusterNode nodeToRemove = getTopology().lookup(node);
        
        // Get all active master nodes EXCEPT the node being forgotten
        Collection<ValkeyClusterNode> activeMasters = 
            getTopology().getActiveMasterNodes();
        
        // Execute CLUSTER FORGET on each master node (except the one being removed)
        for (ValkeyClusterNode masterNode : activeMasters) {
            if (!masterNode.equals(nodeToRemove)) {
                clusterAdapter.setOneShotRouteForNextCommand(
                    new ByAddressRoute(masterNode.getHost(), masterNode.getPort()));
                execute("CLUSTER", rawResult -> null, "FORGET", nodeToRemove.getId());
            }
        }
        
        // Invalidate topology cache after node removal
        invalidateTopologyCache();
    }

    @Override
    public void clusterMeet(ValkeyClusterNode node) {
        Assert.notNull(node, "node must not be null for CLUSTER MEET command");
		Assert.hasText(node.getHost(), "node to meet cluster must have a host");
		Assert.isTrue(node.getPort() > 0, "node to meet cluster must have a port greater 0");

        clusterAdapter.setOneShotRouteForNextCommand(SimpleMultiNodeRoute.ALL_PRIMARIES);
        execute("CLUSTER",
        (Map<String, ?> rawResult) -> null, 
        "MEET", node.getHost(), String.valueOf(node.getPort()));
        
        // Invalidate topology cache after node addition
        invalidateTopologyCache();
    }

    @Override
    public void clusterReplicate(ValkeyClusterNode master, ValkeyClusterNode replica) {
        Assert.notNull(master, "master must not be null");
        Assert.notNull(replica, "replica must not be null");

        ValkeyClusterNode masterNode = getTopology().lookup(master);
        clusterAdapter.setOneShotRouteForNextCommand(new ByAddressRoute(replica.getHost(), replica.getPort()));
        execute("CLUSTER",
            rawResult -> null, 
            "REPLICATE", masterNode.getId());
        
        // Invalidate topology cache after replication change
        invalidateTopologyCache();
    }

    @Override
    public Integer clusterGetSlotForKey(byte[] key) {
        return execute("CLUSTER",
        (Long rawResult) -> ((Long) rawResult).intValue(), 
        "KEYSLOT", key);
    }

    @Override
    public ValkeyClusterNode clusterGetNodeForSlot(int slot) {
        // Use topology to find node for slot
        Set<ValkeyClusterNode> nodes = getTopology().getSlotServingNodes(slot);
        return nodes.stream()
            .filter(node -> node.getType() == ValkeyClusterNode.NodeType.MASTER)
            .findFirst()
            .orElse(nodes.iterator().next()); // Fallback to any node
    }

    @Override
    public ValkeyClusterNode clusterGetNodeForKey(byte[] key) {
        return getTopology().getKeyServingMasterNode(key);
    }

    @Override
    public Collection<ValkeyClusterNode> clusterGetNodes() {
        return getTopology().getNodes();
    }

    @Override
    public Set<ValkeyClusterNode> clusterGetReplicas(ValkeyClusterNode master) {
        Assert.notNull(master, "master must not be null");

        ValkeyClusterNode nodeToMatch = getTopology().lookup(master);
        
        return getTopology().getNodes().stream()
            .filter(node -> node.getType() == NodeType.REPLICA)
            .filter(node -> nodeToMatch.getId().equals(node.getMasterId()))
            .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Map<ValkeyClusterNode, Collection<ValkeyClusterNode>> clusterGetMasterReplicaMap() {
        // Simple approach: Use cached topology (refreshed periodically)
        Map<ValkeyClusterNode, Collection<ValkeyClusterNode>> result = new LinkedHashMap<>();
        
        Collection<ValkeyClusterNode> activeMasters = 
            getTopology().getActiveMasterNodes();
        
        for (ValkeyClusterNode master : activeMasters) {
            Collection<ValkeyClusterNode> replicas = clusterGetReplicas(master);
            result.put(master, replicas);
        }
        
        return result;
    }

    @Override
    public ClusterInfo clusterGetClusterInfo() {
        String infoString = execute("CLUSTER", 
            (GlideString rawResult) -> rawResult.toString(), 
            "INFO");
        
        Properties info = new Properties();
        if (infoString != null) {
            for (String line : infoString.split("\r?\n")) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    info.setProperty(parts[0], parts[1]);
                }
            }
        }
        
        return new ClusterInfo(info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClusterTopology getTopology() {
        // Check cache first
        if (cachedTopology != null && !isCacheExpired()) {
            return cachedTopology;
        }
        
        // Execute CLUSTER SLOTS command using the connection's execute pattern
        Object slotsResponse = execute("CLUSTER", rawResult -> rawResult, "SLOTS");
        
        // Parse the nested array response
        Map<String, ValkeyClusterNode> nodes = parseClusterSlotsResponse(slotsResponse);
        
        // Update known nodes cache
        synchronized (knownNodes) {
            knownNodes.clear();
            knownNodes.putAll(nodes);
        }
        
        // Cache the topology
        cachedTopology = new ClusterTopology(new HashSet<>(nodes.values()));
        lastCacheTime = System.currentTimeMillis();
        
        return cachedTopology;
    }

    /**
     * Checks if the cached topology has expired.
     * 
     * @return true if the cache has expired or no cache exists
     */
    private boolean isCacheExpired() {
        return System.currentTimeMillis() - lastCacheTime > cacheTimeoutMs;
    }

    /**
     * Invalidates the cached cluster topology, forcing a refresh on next access.
     * This should be called after commands that modify the cluster topology
     * (e.g., slot assignments, node additions/removals, replication changes).
     */
    private void invalidateTopologyCache() {
        this.cachedTopology = null;
        this.lastCacheTime = 0;
    }

    /**
     * Parses the CLUSTER SLOTS response into ValkeyClusterNode objects.
     * 
     * @param response The response from CLUSTER SLOTS command
     * @return A map of node ID to ValkeyClusterNode
     */
    private Map<String, ValkeyClusterNode> parseClusterSlotsResponse(Object response) {
        Map<String, ValkeyClusterNode> nodes = new HashMap<>();
        
        if (!(response instanceof Object[])) {
            throw new ClusterStateFailureException("Expected array response from CLUSTER SLOTS, got: " + 
                (response != null ? response.getClass().getSimpleName() : "null"));
        }
        
        Object[] slotsArray = (Object[]) response;
        
        for (Object slotInfo : slotsArray) {
            if (!(slotInfo instanceof Object[])) {
                continue;
            }
            
            Object[] slotData = (Object[]) slotInfo;
            if (slotData.length < 3) {
                continue; // Need at least start_slot, end_slot, and master info
            }
            
            // Parse: [start_slot, end_slot, [master_info], [replica1_info], ...]
            int startSlot = ((Number) slotData[0]).intValue();
            int endSlot = ((Number) slotData[1]).intValue();
            
            // Parse master node (index 2)
            if (slotData[2] instanceof Object[]) {
                Object[] masterInfo = (Object[]) slotData[2];
                ValkeyClusterNode masterNode = parseNodeInfo(masterInfo, NodeType.MASTER, startSlot, endSlot);
                nodes.put(masterNode.getId(), masterNode);
                
                // Parse replica nodes (indices 3+)
                for (int i = 3; i < slotData.length; i++) {
                    if (slotData[i] instanceof Object[]) {
                        Object[] replicaInfo = (Object[]) slotData[i];
                        ValkeyClusterNode replicaNode = parseNodeInfo(replicaInfo, NodeType.REPLICA, null, null);
                        
                        // Create replica node with master reference
                        ValkeyClusterNode replicaWithMaster = ValkeyClusterNode.newValkeyClusterNode()
                            .listeningAt(replicaNode.getHost(), replicaNode.getPort())
                            .withId(replicaNode.getId())
                            .promotedAs(NodeType.REPLICA)
                            .replicaOf(masterNode.getId())
                            .linkState(LinkState.CONNECTED)
                            .build();
                        
                        nodes.put(replicaWithMaster.getId(), replicaWithMaster);
                    }
                }
            }
        }
        
        return nodes;
    }

    /**
     * Parses node information from CLUSTER SLOTS response.
     * 
     * @param nodeInfo Array containing [ip, port, node_id, additional_info...]
     * @param nodeType The type of node (master or replica)
     * @param startSlot Start slot (for master nodes only)
     * @param endSlot End slot (for master nodes only)
     * @return ValkeyClusterNode
     * @throws ClusterStateFailureException if node info is malformed
     */
    private ValkeyClusterNode parseNodeInfo(Object[] nodeInfo, NodeType nodeType, @Nullable Integer startSlot, @Nullable Integer endSlot) {
        if (nodeInfo.length < 3) {
            throw new ClusterStateFailureException(
                "Invalid node info from CLUSTER SLOTS: expected at least [host, port, node_id], got array of length " + nodeInfo.length);
        }
        
        String host = nodeInfo[0] != null ? nodeInfo[0].toString() : "localhost";
        int port = ((Number) nodeInfo[1]).intValue();
        String nodeId = nodeInfo[2] != null ? nodeInfo[2].toString() : generateNodeId(host, port);
        
        // Validate required fields
        if (!StringUtils.hasText(host)) {
            throw new ClusterStateFailureException("Invalid node info from CLUSTER SLOTS: host is empty or null");
        }
        if (port <= 0) {
            throw new ClusterStateFailureException("Invalid node info from CLUSTER SLOTS: invalid port " + port);
        }
        if (!StringUtils.hasText(nodeId)) {
            throw new ClusterStateFailureException("Invalid node info from CLUSTER SLOTS: node ID is empty or null");
        }
        
        ValkeyClusterNode.ValkeyClusterNodeBuilder builder = ValkeyClusterNode.newValkeyClusterNode()
            .listeningAt(host, port)
            .withId(nodeId)
            .promotedAs(nodeType)
            .linkState(LinkState.CONNECTED);
        
        // Add slot range for master nodes
        if (nodeType == NodeType.MASTER && startSlot != null && endSlot != null) {
            builder.serving(new SlotRange(startSlot, endSlot));
        }
        
        return builder.build();
    }

    /**
     * Generates a node ID when not provided in the response.
     * 
     * @param host The node host
     * @param port The node port
     * @return A generated node ID
     */
    private String generateNodeId(String host, int port) {
        return String.format("%s:%d", host, port);
    }

    /**
     * Execute a Valkey command with explicit routing.
     * This is a convenience method that sets the route and executes the command in one call.
     * 
     * @param route The route to use for this command (can be single-node or multi-node)
     * @param command The Valkey command name
     * @param mapper A function to convert the raw driver result
     * @param args The command arguments
     * @param <I> The raw result type from the driver
     * @param <R> The expected return type after mapping
     * @return The mapped result, or null if pipelining/transaction is active
     */
    public <I, R> R execute(Route route, String command, ValkeyGlideConverters.ResultMapper<I, R> mapper, Object... args) {
        clusterAdapter.setOneShotRouteForNextCommand(route);
        return execute(command, mapper, args);
    }

	public Map<ValkeyClusterNode, List<byte[]>> buildNodeKeyMap(byte[]... keys) {
		Map<ValkeyClusterNode, List<byte[]>> nodeKeyMap = new HashMap<>();
		int keysResolved = 0;
		
		for (byte[] key : keys) {
			for (ValkeyClusterNode node : getTopology().getKeyServingNodes(key)) {
				if (node.isMaster()) {
					nodeKeyMap.computeIfAbsent(node, val -> new ArrayList<>()).add(key);
					keysResolved++;
					break;
				}
			}
		}
		
		if (keysResolved != keys.length) {
			throw new IllegalStateException(
				"Cannot determine cluster node for all keys, bad topology?");
		}
		
		return nodeKeyMap;
	}	
}
