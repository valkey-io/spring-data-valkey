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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterServerCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;

/**
 * Implementation of {@link ValkeyClusterServerCommands} for Valkey-Glide.
 * Uses the elegant execute(Route, ...) pattern for cluster command routing.
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideClusterServerCommands implements ValkeyClusterServerCommands {

    private final ValkeyGlideClusterConnection connection;

    /**
     * Create a new {@link ValkeyGlideClusterServerCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideClusterServerCommands(ValkeyGlideClusterConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    // ========== Cluster-wide operations (execute on all primaries) ==========

    @Override
    public void bgReWriteAof() {
        // valkey-glide default route is bogus - by first key, so we specify ALL_PRIMARIES
        connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "BGREWRITEAOF", (Map<String, ?> rawResult) -> null);
    }

    @Override
    public void bgSave() {
        // valkey-glide default route is - random, so we specify ALL_PRIMARIES
        connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "BGSAVE", (Map<String, ?> rawResult) -> null);
    }

    @Override
    public Long lastSave() {
        // valkey-glide default route is "random", so we specify ALL_PRIMARIES
        // Return the most recent LASTSAVE timestamp across all nodes
        return connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "LASTSAVE",
            (Map<String, Long> rawResult) -> rawResult.isEmpty() ? null : Collections.max(rawResult.values()));
    }

    public void save() {
        // valkey-glide default route is "random", so we specify ALL_PRIMARIES
        connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "SAVE", (Map<String, ?> rawResult) -> null);
    }

    @Override
    public Long dbSize() {
        // valkey-glide default route is "all primaries" and aggregation is summation, as expected
        return connection.execute("DBSIZE", (Long rawResult) -> rawResult);
    }

    @Override
    public void flushDb() {
        // valkey-glide default route is "all nodes" and response policy is "all succeeded", as expected
        connection.execute("FLUSHDB", rawResult -> null);
    }

    @Override
    public void flushDb(FlushOption option) {
        // valkey-glide default route is "all nodes" and response policy is "all succeeded", as expected
        Assert.notNull(option, "FlushOption must not be null");
        connection.execute("FLUSHDB", rawResult -> null, option.name());
    }

    @Override
    public void flushAll() {
        // valkey-glide default route is "all primaries" and response policy is "all succeeded", as expected
        connection.execute("FLUSHALL", rawResult -> null);
    }

    @Override
    public void flushAll(FlushOption option) {
        Assert.notNull(option, "FlushOption must not be null");
        // valkey-glide default route is "all primaries" and response policy is "all succeeded", as expected
        connection.execute("FLUSHALL", rawResult -> null, option.name());
    }

    @Override
    public Properties info() {
        // valkey-glide default route is "all primaries", as expected
        Map<String, GlideString> results = connection.execute("INFO", 
            (Map<String, GlideString> rawResult) -> rawResult);
        
        return aggregateInfoResults(results);
    }

    @Override
    public Properties info(String section) {
        Assert.notNull(section, "Section must not be null");
        // valkey-glide default route is "all primaries", as expected
        Map<String, GlideString> results = connection.execute("INFO", 
            (Map<String, GlideString> rawResult) -> rawResult, section);
        
        return aggregateInfoResults(results);
    }

    @Override
    public void shutdown() {
        connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "SHUTDOWN", rawResult -> null);
    }

    @Override
    public void shutdown(ShutdownOption option) {
        if (option == null) {
            shutdown();
            return;
        }
        connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "SHUTDOWN", rawResult -> null, option.name());
    }

    @Override
    public Properties getConfig(String pattern) {
        Assert.notNull(pattern, "Pattern must not be null");
        Map<String, Object> results = connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "CONFIG", 
            (Map<String, Object> rawResult) -> rawResult, "GET", pattern);
        
        return aggregateConfigResults(results);
    }

    @Override
    public void setConfig(String param, String value) {
        Assert.notNull(param, "Parameter must not be null");
        Assert.notNull(value, "Value must not be null");
        // valkey-glide default route is "all nodes" and response policy is "all succeeded"
        connection.execute("CONFIG", rawResult -> null, "SET", param, value);
    }

    @Override
    public void resetConfigStats() {
        // valkey-glide default route is "all nodes" with aggregation policy "all succeeded",
        // with is better than "all primaries" for this command
        connection.execute("CONFIG", rawResult -> null, "RESETSTAT");
    }

    @Override
    public void rewriteConfig() {
        // valkey-glide default route is "all nodes" with aggregation policy "all succeeded",
        // with is better than "all primaries" for this command
        connection.execute("CONFIG", rawResult -> null, "REWRITE");
    }

    @Override
    public Long time(TimeUnit timeUnit) {
        Assert.notNull(timeUnit, "TimeUnit must not be null");

        // valkey-glide default route is Random, as expected
        return connection.execute("TIME", (Object[] rawResult) -> ValkeyGlideConverters.parseTimeResponse(rawResult, timeUnit));
    }

    @Override
    public List<ValkeyClientInfo> getClientList() {
        // valkey-glide default route is bogus - by first key, so we specify ALL_PRIMARIES
        Map<String, GlideString> results = connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "CLIENT", 
            (Map<String, GlideString> rawResult) -> rawResult, "LIST");
        
        List<ValkeyClientInfo> clientInfos = new ArrayList<>();
        for (GlideString clientList : results.values()) {
            clientInfos.addAll(parseClientListResponse(clientList.toString()));
        }
        return clientInfos;
    }

    @Override
    public void killClient(String host, int port) {
        Assert.hasText(host, "Host must not be empty");
        String hostAndPort = String.format("%s:%d", host, port);
        
        connection.execute(SimpleMultiNodeRoute.ALL_PRIMARIES, "CLIENT", (Map<String, ?> rawResult) -> null, "KILL", hostAndPort);
    }

    // ========== Node-specific operations ==========

    @Override
    public void bgReWriteAof(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "BGREWRITEAOF", glideResult -> glideResult);
    }

    @Override
    public void bgSave(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "BGSAVE", glideResult -> glideResult);
    }

    @Override
    public Long lastSave(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        return connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "LASTSAVE", (Long rawResult) -> rawResult);
    }

    @Override
    public void save(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "SAVE", glideResult -> glideResult);
    }

    @Override
    public Long dbSize(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        return connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "DBSIZE", (Long rawResult) -> rawResult);
    }

    @Override
    public void flushDb(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "FLUSHDB", glideResult -> glideResult);
    }

    @Override
    public void flushDb(ValkeyClusterNode node, FlushOption option) {
        Assert.notNull(node, "Node must not be null");
        Assert.notNull(option, "FlushOption must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "FLUSHDB", glideResult -> glideResult, option.name());
    }

    @Override
    public void flushAll(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "FLUSHALL", glideResult -> glideResult);
    }

    @Override
    public void flushAll(ValkeyClusterNode node, FlushOption option) {
        Assert.notNull(node, "Node must not be null");
        Assert.notNull(option, "FlushOption must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "FLUSHALL", glideResult -> glideResult, option.name());
    }

    @Override
    public Properties info(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        GlideString result = connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "INFO", (GlideString rawResult) -> rawResult);
        return parseInfoResponse(result != null ? result.toString() : null);
    }

    @Override
    public Properties info(ValkeyClusterNode node, String section) {
        Assert.notNull(node, "Node must not be null");
        Assert.notNull(section, "Section must not be null");
        GlideString result = connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "INFO", (GlideString rawResult) -> rawResult, section);
        return parseInfoResponse(result != null ? result.toString() : null);
    }

    @Override
    public void shutdown(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "SHUTDOWN", glideResult -> glideResult);
    }

    @Override
    public Properties getConfig(ValkeyClusterNode node, String pattern) {
        Assert.notNull(node, "Node must not be null");
        Assert.notNull(pattern, "Pattern must not be null");
        Object result = connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "CONFIG", (Object rawResult) -> rawResult, "GET", pattern);
        return parseConfigResponse(result);
    }

    @Override
    public void setConfig(ValkeyClusterNode node, String param, String value) {
        Assert.notNull(node, "Node must not be null");
        Assert.notNull(param, "Parameter must not be null");
        Assert.notNull(value, "Value must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "CONFIG", glideResult -> glideResult, "SET", param, value);
    }

    @Override
    public void resetConfigStats(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "CONFIG", glideResult -> glideResult, "RESETSTAT");
    }

    @Override
    public void rewriteConfig(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "CONFIG", glideResult -> glideResult, "REWRITE");
    }

    @Override
    public Long time(ValkeyClusterNode node, TimeUnit timeUnit) {
        Assert.notNull(node, "Node must not be null");
        Assert.notNull(timeUnit, "TimeUnit must not be null");
        return connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "TIME", (Object[] rawResult) -> ValkeyGlideConverters.parseTimeResponse(rawResult, timeUnit));
    }

    @Override
    public List<ValkeyClientInfo> getClientList(ValkeyClusterNode node) {
        Assert.notNull(node, "Node must not be null");
        GlideString result = connection.execute(new ByAddressRoute(node.getHost(), node.getPort()), "CLIENT", (GlideString rawResult) -> rawResult, "LIST");
        return parseClientListResponse(result.toString());
    }

    // ========== Unsupported operations ==========

    @Override
    public void setClientName(byte[] name) {
        throw new InvalidDataAccessApiUsageException("CLIENT SETNAME is not supported in cluster environment");
    }

    @Override
    public String getClientName() {
        throw new InvalidDataAccessApiUsageException("CLIENT GETNAME is not supported in cluster environment");
    }

    @Override
    public void replicaOf(String host, int port) {
        throw new InvalidDataAccessApiUsageException(
                "REPLICAOF is not supported in cluster environment; Please use CLUSTER REPLICATE");
    }

    @Override
    public void replicaOfNoOne() {
        throw new InvalidDataAccessApiUsageException(
                "REPLICAOF is not supported in cluster environment; Please use CLUSTER REPLICATE");
    }

    @Override
    public void migrate(byte[] key, ValkeyNode target, int dbIndex, @Nullable MigrateOption option) {
        migrate(key, target, dbIndex, option, 0);
    }

    @Override
    public void migrate(byte[] key, ValkeyNode target, int dbIndex, @Nullable MigrateOption option, long timeout) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(target, "Target must not be null");
        
        // Find the source node where the key currently resides
        ValkeyClusterNode sourceNode = connection.clusterCommands().clusterGetNodeForKey(key);
        Assert.notNull(sourceNode, "Could not determine source node for key");
        
        // Build MIGRATE command arguments
        List<Object> args = new ArrayList<>();
        args.add(target.getHost());
        args.add(String.valueOf(target.getPort()));
        args.add(key);
        args.add(String.valueOf(dbIndex));
        args.add(String.valueOf(timeout));
        
        if (option != null) {
            args.add(option.name());
        }
        
        // Execute MIGRATE on the source node
        connection.execute(
            new ByAddressRoute(sourceNode.getHost(), sourceNode.getPort()),
            "MIGRATE",
            glideResult -> glideResult,
            args.toArray()
        );
    }

    // ========== Helper methods ==========

    /**
     * Aggregates INFO results from multiple nodes into a single Properties object.
     * Each property is prefixed with the node address.
     */
    private Properties aggregateInfoResults(Map<String, GlideString> results) {
        Properties aggregated = new Properties();
        
        if (results == null) {
            return aggregated;
        }
        
        for (Map.Entry<String, GlideString> entry : results.entrySet()) {
            String nodeAddress = entry.getKey();
            Properties nodeInfo = parseInfoResponse(entry.getValue().toString());
            for (Map.Entry<Object, Object> prop : nodeInfo.entrySet()) {
                String key = nodeAddress + "." + prop.getKey();
                aggregated.setProperty(key, prop.getValue().toString());
            }
        }
        
        return aggregated;
    }

    /**
     * Parses INFO command response into Properties.
     */
    private Properties parseInfoResponse(String infoResponse) {
        Properties properties = new Properties();
        
        if (infoResponse == null || infoResponse.isEmpty()) {
            return properties;
        }
        
        for (String line : infoResponse.split("\r?\n")) {
            line = line.trim();
            
            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0 && colonIndex < line.length() - 1) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                properties.setProperty(key, value);
            }
        }
        
        return properties;
    }

    /**
     * Aggregates CONFIG GET results from multiple nodes into a single Properties object.
     * Each property is prefixed with the node address.
     */
    private Properties aggregateConfigResults(Map<String, Object> results) {
        Properties aggregated = new Properties();
        
        if (results == null) {
            return aggregated;
        }
        
        for (Map.Entry<String, Object> entry : results.entrySet()) {
            String nodeAddress = entry.getKey();
            Properties nodeConfig = parseConfigResponse(entry.getValue());
            for (Map.Entry<Object, Object> prop : nodeConfig.entrySet()) {
                String key = nodeAddress + "." + prop.getKey();
                aggregated.setProperty(key, prop.getValue().toString());
            }
        }
        
        return aggregated;
    }

    /**
     * Parses CONFIG GET command response into Properties.
     */
    private Properties parseConfigResponse(Object result) {
        Properties properties = new Properties();
        
        if (result == null) {
            return properties;
        }
        
        // CONFIG GET can return Map or List
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                String value = entry.getValue().toString();
                properties.setProperty(key, value);
            }
        } else if (result instanceof List) {
            List<?> list = (List<?>) result;
            // Flat list: [key1, value1, key2, value2, ...]
            for (int i = 0; i < list.size(); i += 2) {
                if (i + 1 < list.size()) {
                    String key = list.get(i).toString();
                    String value = list.get(i + 1).toString();
                    properties.setProperty(key, value);
                }
            }
        }
        
        return properties;
    }

    /**
     * Parses CLIENT LIST command response into a list of ValkeyClientInfo objects.
     */
    private List<ValkeyClientInfo> parseClientListResponse(String clientListResponse) {
        List<ValkeyClientInfo> clientInfos = new ArrayList<>();
        
        if (clientListResponse == null || clientListResponse.isEmpty()) {
            return clientInfos;
        }
        
        for (String line : clientListResponse.split("\r?\n")) {
            line = line.trim();
            Properties properties = new Properties();
            for (String part : line.split("\\s+")) {
                int equalsIndex = part.indexOf('=');
                if (equalsIndex > 0 && equalsIndex < part.length() - 1) {
                    String key = part.substring(0, equalsIndex).trim();
                    String value = part.substring(equalsIndex + 1).trim();
                    properties.setProperty(key, value);
                }
            }
            
            if (!properties.isEmpty()) {
                clientInfos.add(new ValkeyClientInfo(properties));
            }
        }
        
        return clientInfos;
    }
}
