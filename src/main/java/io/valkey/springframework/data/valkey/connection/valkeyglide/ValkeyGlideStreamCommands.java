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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.Limit;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands;
import io.valkey.springframework.data.valkey.connection.stream.*;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoConsumers;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoGroups;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoStream;
import org.springframework.util.Assert;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeyStreamCommands} for Valkey-Glide.
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideStreamCommands implements ValkeyStreamCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideStreamCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideStreamCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    public Long xAck(byte[] key, String group, RecordId... recordIds) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(group, "Group must not be null!");
        Assert.notNull(recordIds, "Record IDs must not be null!");

        try {
            Object[] params = new Object[recordIds.length + 2];
            params[0] = key;
            params[1] = group;

            for (int i = 0; i < recordIds.length; i++) {
                params[i + 2] = recordIds[i].getValue();
            }

            return connection.execute("XACK",
                (Long glideResult) -> glideResult,
                params);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public RecordId xAdd(MapRecord<byte[], byte[], byte[]> record) {
        return xAdd(record, XAddOptions.none());
    }

    @Override
    public RecordId xAdd(MapRecord<byte[], byte[], byte[]> record, XAddOptions options) {
        Assert.notNull(record, "Record must not be null!");
        Assert.notNull(options, "Options must not be null!");

        byte[] key = record.getStream();
        RecordId recordId = record.getId();
        Map<byte[], byte[]> body = record.getValue();

        Assert.notNull(key, "Key must not be null!");
        Assert.notEmpty(body, "Body must not be empty!");

        try {
            List<Object> params = new ArrayList<>(2 + body.size() * 2 + 4);
            params.add(key);

            if (options.isNoMkStream()) {
                params.add("NOMKSTREAM");
            }

            if (options.hasMaxlen()) {
                params.add("MAXLEN");
                if (options.isApproximateTrimming()) {
                    params.add("~");
                }
                params.add(String.valueOf(options.getMaxlen()));
            }

            if (options.hasMinId()) {
                params.add("MINID");
                if (options.isApproximateTrimming()) {
                    params.add("~");
                }
                params.add(options.getMinId().getValue());
            }

            if (recordId == null || recordId.equals(RecordId.autoGenerate())) {
                params.add("*");
            } else {
                params.add(recordId.getValue());
            }

            for (Map.Entry<byte[], byte[]> entry : body.entrySet()) {
                params.add(entry.getKey());
                params.add(entry.getValue());
            }

            return connection.execute("XADD",
                (GlideString glideResult) -> glideResult != null ? RecordId.of(glideResult.toString()) : null,
                params.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public List<RecordId> xClaimJustId(byte[] key, String group, String newOwner, XClaimOptions options) {
        Assert.notNull(key, "Key must not be null!");
        Assert.hasText(group, "Group must not be null or empty!");
        Assert.hasText(newOwner, "New owner must not be null or empty!");
        Assert.notNull(options, "Options must not be null!");

        try {
            List<Object> args = buildXClaimArgs(key, group, newOwner, options);
            args.add("JUSTID");
            
            return connection.execute("XCLAIM",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return Collections.emptyList();
                    }
                    List<RecordId> recordIds = new ArrayList<>(glideResult.length);
                    for (Object item : glideResult) {
                        recordIds.add(RecordId.of(((GlideString) item).toString()));
                    }
                    return recordIds;
                },
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public List<ByteRecord> xClaim(byte[] key, String group, String newOwner, XClaimOptions options) {
        Assert.notNull(key, "Key must not be null!");
        Assert.hasText(group, "Group must not be null or empty!");
        Assert.hasText(newOwner, "New owner must not be null or empty!");
        Assert.notNull(options, "Options must not be null!");

        try {
            List<Object> args = buildXClaimArgs(key, group, newOwner, options);
            
            return connection.execute("XCLAIM",
                (Map<?, ?> glideResult) -> {
                    return parseByteRecords(glideResult, key, false);
                },
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Long xDel(byte[] key, RecordId... recordIds) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(recordIds, "Record IDs must not be null!");
        
        try {
            Object[] params = new Object[recordIds.length + 1];
            params[0] = key;
            
            for (int i = 0; i < recordIds.length; i++) {
                params[i + 1] = recordIds[i].getValue();
            }
            
            return connection.execute("XDEL",
                (Long glideResult) -> glideResult,
                params);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public String xGroupCreate(byte[] key, String groupName, ReadOffset readOffset) {
        return xGroupCreate(key, groupName, readOffset, false);
    }

    @Override
    public String xGroupCreate(byte[] key, String groupName, ReadOffset readOffset, boolean makeStream) {
        Assert.notNull(key, "Key must not be null!");
        Assert.hasText(groupName, "Group name must not be null or empty!");
        Assert.notNull(readOffset, "ReadOffset must not be null!");

        try {
            List<Object> args = new ArrayList<>(6);
            args.add("CREATE");
            args.add(key);
            args.add(groupName);
            args.add(readOffset.getOffset());
            
            if (makeStream) {
                args.add("MKSTREAM");
            }

            return connection.execute("XGROUP",
                (String glideResult) -> glideResult,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }
    
    @Override
    public Boolean xGroupDelConsumer(byte[] key, Consumer consumer) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(consumer, "Consumer must not be null!");

        try {
            return connection.execute("XGROUP",
                (Long glideResult) -> glideResult != null ? glideResult > 0 : null,
            "DELCONSUMER", key, consumer.getGroup(), consumer.getName());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Boolean xGroupDestroy(byte[] key, String groupName) {
        Assert.notNull(key, "Key must not be null!");
        Assert.hasText(groupName, "Group name must not be null or empty!");

        try {
            return connection.execute("XGROUP",
                (Boolean glideResult) -> glideResult,
                "DESTROY", key, groupName);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public XInfoStream xInfo(byte[] key) {
        Assert.notNull(key, "Key must not be null!");

        try {
            return connection.execute("XINFO",
                (Map<?, ?> glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }

                    // Convert the result to a List<Object>
                    // XInfoStream.fromList() expects a List<Object> with alternating keys and values
                    // Valkey-Glide returns a Map, so we need to convert it
                    // first-entry and last-entry are special cases with nested structures and need to be converted to Map
                    List<Object> list = new ArrayList<>(glideResult.size() * 2);

                    for (Map.Entry<?, ?> entry : glideResult.entrySet()) {
                        list.add(entry.getKey().toString());
                        if (entry.getKey().toString().equals("first-entry") || entry.getKey().toString().equals("last-entry")) {
                            Object[] entryObjects = (Object[]) entry.getValue();
                            Map<byte[], Map<byte[], byte[]>> entryMap = new HashMap<>();
                            for (int i = 0; i < entryObjects.length; i += 2) {
                                byte[] recordIdString = ((GlideString) entryObjects[i]).getBytes();
                                Map<byte[], byte[]> fields = new HashMap<>();
                                Object[] fieldsArr = (Object[]) entryObjects[i + 1];
                                for (int j = 0; j < fieldsArr.length; j += 2) {
                                    byte[] field = ((GlideString) fieldsArr[j]).getBytes();
                                    byte[] value = ((GlideString) fieldsArr[j + 1]).getBytes();
                                    fields.put(field, value);
                                }
                                entryMap.put(recordIdString, fields);
                            }
                            list.add(entryMap);
                        } else {
                            list.add(ValkeyGlideConverters.defaultFromGlideResult(entry.getValue()));
                        }
                    }
                    return XInfoStream.fromList(list);
                },
                "STREAM", key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public XInfoGroups xInfoGroups(byte[] key) {
        Assert.notNull(key, "Key must not be null!");

        try {
            return connection.execute("XINFO",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }

                    // XInfoGroups.fromList expects a list where each group is represented as a separate List
                    // Format: [group1List, group2List, ...] where each groupList is [key1, value1, key2, value2, ...]
                    List<Object> groupsList = new ArrayList<>(glideResult.length);

                    for (Object groupInfo : glideResult) {
                        Map<?, ?> groupMap = (Map<?, ?>) groupInfo;
                        List<Object> singleGroupList = new ArrayList<>(groupMap.size() * 2);
                        for (Map.Entry<?, ?> entry : groupMap.entrySet()) {
                            singleGroupList.add(entry.getKey().toString());
                            if (entry.getValue() == null) {
                                singleGroupList.add(null);
                            } else if (entry.getValue() instanceof Number n) {
                                // convert to long
                                singleGroupList.add(n.longValue());
                            } else {
                                singleGroupList.add(entry.getValue().toString());
                            }
                        }
                        groupsList.add(singleGroupList);
                    }
                    return XInfoGroups.fromList(groupsList);
                },
                "GROUPS", key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public XInfoConsumers xInfoConsumers(byte[] key, String groupName) {
        Assert.notNull(key, "Key must not be null!");
        Assert.hasText(groupName, "Group name must not be null or empty!");

        try {
            return connection.execute("XINFO",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }

                    List<Object> consumersList = new ArrayList<>(glideResult.length);
                    for (Object groupInfo : glideResult) {
                        Map<?, ?> groupMap = (Map<?, ?>) groupInfo;
                        List<Object> singleConsumerList = new ArrayList<>(groupMap.size() * 2);
                        for (Map.Entry<?, ?> entry : groupMap.entrySet()) {
                            singleConsumerList.add(entry.getKey().toString());
                            if (entry.getValue() == null) {
                                singleConsumerList.add(null);
                            } else if (entry.getValue() instanceof Number n) {
                                // convert to long
                                singleConsumerList.add(n.longValue());
                            } else {
                                singleConsumerList.add(entry.getValue().toString());
                            }
                        }
                        consumersList.add(singleConsumerList);
                    }
                    return XInfoConsumers.fromList("", consumersList);
                },
                "CONSUMERS", key, groupName);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Long xLen(byte[] key) {
        Assert.notNull(key, "Key must not be null!");
        
        try {
            return connection.execute("XLEN",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public PendingMessagesSummary xPending(byte[] key, String groupName) {
        Assert.notNull(key, "Key must not be null!");
        Assert.hasText(groupName, "Group name must not be null or empty!");

        try {
            return connection.execute("XPENDING",
                (Object glideResult) -> glideResult != null ? parsePendingMessagesSummary(glideResult) : null,
                key, groupName);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public PendingMessages xPending(byte[] key, String groupName, XPendingOptions options) {
        Assert.notNull(key, "Key must not be null!");
        Assert.hasText(groupName, "Group name must not be null or empty!");
        Assert.notNull(options, "Options must not be null!");

        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(groupName);

            if (options.isLimited()) {
                Range<?> range = options.getRange();
                Object lowerBound = range.getLowerBound().getValue().map(Object.class::cast).orElse("-");
                Object upperBound = range.getUpperBound().getValue().map(Object.class::cast).orElse("+");
                
                args.add(lowerBound);
                args.add(upperBound);
                args.add(options.getCount());

                if (options.hasConsumer()) {
                    args.add(options.getConsumerName());
                }
            }

            return connection.execute("XPENDING",
                (Object glideResult) -> glideResult != null ? parsePendingMessages(glideResult, groupName) : null,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    // Convenience method for getting pending messages for a specific consumer
    public PendingMessages xPending(byte[] key, String groupName, String consumerName) {
        Assert.notNull(key, "Key must not be null!");
        Assert.hasText(groupName, "Group name must not be null or empty!");
        Assert.hasText(consumerName, "Consumer name must not be null or empty!");

        ValkeyStreamCommands.XPendingOptions options = ValkeyStreamCommands.XPendingOptions
                .range(Range.unbounded(), 100L)
                .consumer(consumerName);
        
        return xPending(key, groupName, options);
    }

    @Override
    public List<ByteRecord> xRange(byte[] key, Range<String> range, Limit limit) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(range, "Range must not be null!");
        Assert.notNull(limit, "Limit must not be null!");

        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            
            // Handle lower bound with exclusive/inclusive semantics
            Object lowerBound;
            if (range.getLowerBound().isBounded()) {
                Object value = range.getLowerBound().getValue().orElse("-");
                lowerBound = range.getLowerBound().isInclusive() ? String.valueOf(value) : "(" + String.valueOf(value);
            } else {
                lowerBound = "-";
            }
            
            // Handle upper bound with exclusive/inclusive semantics  
            Object upperBound;
            if (range.getUpperBound().isBounded()) {
                Object value = range.getUpperBound().getValue().orElse("+");
                upperBound = range.getUpperBound().isInclusive() ? String.valueOf(value) : "(" + String.valueOf(value);
            } else {
                upperBound = "+";
            }
            
            args.add(lowerBound);
            args.add(upperBound);

            if (limit.isLimited()) {
                args.add("COUNT");
                args.add(limit.getCount());
            }

            return connection.execute("XRANGE",
                (Map<?, ?> glideResult) -> {
                    return parseByteRecords(glideResult, key, false);
                },
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public List<ByteRecord> xRead(StreamReadOptions readOptions, StreamOffset<byte[]>... streams) {
        Assert.notNull(readOptions, "Read options must not be null!");
        Assert.notNull(streams, "Streams must not be null!");
        Assert.notEmpty(streams, "Streams must not be empty!");

        try {
            List<Object> args = new ArrayList<>();

            if (readOptions.getCount() != null && readOptions.getCount() > 0) {
                args.add("COUNT");
                args.add(readOptions.getCount());
            }

            if (readOptions.getBlock() != null && readOptions.getBlock() >= 0) {
                args.add("BLOCK");
                args.add(readOptions.getBlock());
            }

            args.add("STREAMS");

            for (StreamOffset<byte[]> stream : streams) {
                args.add(stream.getKey());
            }

            for (StreamOffset<byte[]> stream : streams) {
                args.add(stream.getOffset().getOffset());
            }

            // Extract stream keys for workaround to ClusterValue.of() bug
            byte[][] streamKeys = new byte[streams.length][];
            for (int i = 0; i < streams.length; i++) {
                streamKeys[i] = streams[i].getKey();
            }

            return connection.execute("XREAD",
                (Object glideResult) -> {
                    return parseMultiStreamByteRecords(glideResult, streamKeys);
                },
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public List<ByteRecord> xReadGroup(Consumer consumer, StreamReadOptions readOptions, StreamOffset<byte[]>... streams) {
        Assert.notNull(consumer, "Consumer must not be null!");
        Assert.notNull(readOptions, "Read options must not be null!");
        Assert.notNull(streams, "Streams must not be null!");
        Assert.notEmpty(streams, "Streams must not be empty!");

        try {
            List<Object> args = new ArrayList<>();
            args.add("GROUP");
            args.add(consumer.getGroup());
            args.add(consumer.getName());

            if (readOptions.getCount() != null && readOptions.getCount() > 0) {
                args.add("COUNT");
                args.add(readOptions.getCount());
            }

            if (readOptions.getBlock() != null && readOptions.getBlock() >= 0) {
                args.add("BLOCK");
                args.add(readOptions.getBlock());
            }

            if (readOptions.isNoack()) {
                args.add("NOACK");
            }

            args.add("STREAMS");

            for (StreamOffset<byte[]> stream : streams) {
                args.add(stream.getKey());
            }

            for (StreamOffset<byte[]> stream : streams) {
                args.add(stream.getOffset().getOffset());
            }

            // Extract stream keys for workaround to ClusterValue.of() bug
            byte[][] streamKeys = new byte[streams.length][];
            for (int i = 0; i < streams.length; i++) {
                streamKeys[i] = streams[i].getKey();
            }

            return connection.execute("XREADGROUP",
                (Object glideResult) -> {
                    return parseMultiStreamByteRecords(glideResult, streamKeys);
                },
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public List<ByteRecord> xRevRange(byte[] key, Range<String> range, Limit limit) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(range, "Range must not be null!");
        Assert.notNull(limit, "Limit must not be null!");

        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            
            // Handle upper bound with exclusive/inclusive semantics (note: xRevRange has reversed argument order)
            Object upperBound;
            if (range.getUpperBound().isBounded()) {
                Object value = range.getUpperBound().getValue().orElse("+");
                upperBound = range.getUpperBound().isInclusive() ? String.valueOf(value) : "(" + String.valueOf(value);
            } else {
                upperBound = "+";
            }
            
            // Handle lower bound with exclusive/inclusive semantics
            Object lowerBound;
            if (range.getLowerBound().isBounded()) {
                Object value = range.getLowerBound().getValue().orElse("-");
                lowerBound = range.getLowerBound().isInclusive() ? String.valueOf(value) : "(" + String.valueOf(value);
            } else {
                lowerBound = "-";
            }
            
            args.add(upperBound);
            args.add(lowerBound);

            if (limit.isLimited()) {
                args.add("COUNT");
                args.add(limit.getCount());
            }

            return connection.execute("XREVRANGE",
                (Map<?, ?> glideResult) -> {
                    return parseByteRecords(glideResult, key, true);
                },
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Long xTrim(byte[] key, long count) {
        return xTrim(key, count, false);
    }

    @Override
    public Long xTrim(byte[] key, long count, boolean approximateTrimming) {
        Assert.notNull(key, "Key must not be null!");

        try {
            List<Object> args = new ArrayList<>(4);
            args.add(key);
            args.add("MAXLEN");
            
            if (approximateTrimming) {
                args.add("~");
            }
            
            args.add(String.valueOf(count));

            return connection.execute("XTRIM",
                (Long glideResult) -> glideResult,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    // ==================== Helper Methods ====================

    private String convertResultToString(Object result) {
        if (result instanceof byte[]) {
            return new String((byte[]) result, StandardCharsets.UTF_8);
        } else if (result instanceof String) {
            return (String) result;
        } else if (result == null) {
            return null;
        } else {
            return result.toString();
        }
    }

    private List<Object> buildXClaimArgs(byte[] key, String group, String newOwner, XClaimOptions options) {
        List<Object> args = new ArrayList<>();
        args.add(key);
        args.add(group);
        args.add(newOwner);
        args.add(options.getMinIdleTime().toMillis());
        
        if (options.getIds() != null) {
            for (RecordId recordId : options.getIds()) {
                args.add(recordId.getValue());
            }
        }
        
        if (options.getIdleTime() != null) {
            args.add("IDLE");
            args.add(options.getIdleTime().toMillis());
        }
        
        if (options.getUnixTime() != null) {
            args.add("TIME");
            args.add(options.getUnixTime().toEpochMilli());
        }
        
        if (options.getRetryCount() != null) {
            args.add("RETRYCOUNT");
            args.add(options.getRetryCount());
        }
        
        if (options.isForce()) {
            args.add("FORCE");
        }
        
        return args;
    }

    @SuppressWarnings("unchecked")
    private List<Object> convertToList(Object obj) {
        if (obj instanceof List) {
            return (List<Object>) obj;
        } else if (obj instanceof Object[]) {
            Object[] array = (Object[]) obj;
            List<Object> list = new ArrayList<>(array.length);
            for (Object item : array) {
                list.add(item);
            }
            return list;
        } else if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            if (map.isEmpty()) {
                return new ArrayList<>();
            } else {
                List<Object> list = new ArrayList<>(map.size() * 2);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    list.add(entry.getKey());
                    list.add(entry.getValue());
                }
                return list;
            }
        } else if (obj == null) {
            return new ArrayList<>();
        } else {
            throw new IllegalArgumentException("Cannot convert " + obj.getClass() + " to List");
        }
    }

    private List<ByteRecord> parseByteRecords(Map<?, ?> result, byte[] streamKey, boolean reverseOrder) {
        if (result == null) {
            return new ArrayList<>();
        }

        List<ByteRecord> byteRecords = new ArrayList<>(result.size());
        
        // Sort entries by record ID timestamp to maintain correct order
        List<Map.Entry<?, ?>> sortedEntries = new ArrayList<>(result.entrySet());
        sortedEntries.sort((e1, e2) -> {
            // WORKAROUND: ClusterValue.of() bug converts GlideString to String
            String id1;
            if (e1.getKey() instanceof GlideString) {
                id1 = ((GlideString) e1.getKey()).toString();
            } else {
                id1 = e1.getKey().toString();
            }
            
            String id2;
            if (e2.getKey() instanceof GlideString) {
                id2 = ((GlideString) e2.getKey()).toString();
            } else {
                id2 = e2.getKey().toString();
            }
            
            // Extract timestamp part (before the dash)
            long timestamp1 = Long.parseLong(id1.split("-")[0]);
            long timestamp2 = Long.parseLong(id2.split("-")[0]);
            int comparison = Long.compare(timestamp1, timestamp2);
            return reverseOrder ? -comparison : comparison;
        });

        // Parse Valkey-Glide stream record format using converter patterns
        for (Map.Entry<?, ?> entry : sortedEntries) {
            // WORKAROUND: ClusterValue.of() bug converts GlideString to String
            String recordIdString;
            Object keyObj = entry.getKey();
            if (keyObj instanceof GlideString) {
                recordIdString = ((GlideString) keyObj).toString();
            } else if (keyObj instanceof String) {
                recordIdString = (String) keyObj;
            } else {
                recordIdString = keyObj.toString();
            }
            
            Map<byte[], byte[]> fields = new HashMap<>();
            Object[] fieldsArr = (Object[]) entry.getValue();
            
            for (int i = 0; i < fieldsArr.length; i++) {
                Object[] itemArr = (Object[]) fieldsArr[i];
                GlideString field = (GlideString) itemArr[0];
                GlideString value = (GlideString) itemArr[1];
                fields.put(field.getBytes(), value.getBytes());
            }
            ByteRecord byteRecord = StreamRecords.newRecord()
                    .in(streamKey)
                    .withId(RecordId.of(recordIdString))
                    .ofBytes(fields);
            byteRecords.add(byteRecord);
        }
        return byteRecords;
    }

    private List<ByteRecord> parseMultiStreamByteRecords(Object result, byte[][] originalStreamKeys) {
        if (result == null) {
            return new ArrayList<>();
        }

        List<ByteRecord> allRecords = new ArrayList<>();

        // Handle both List and Map formats from Valkey-Glide XREAD/XREADGROUP responses
        if (result instanceof Map) {
            // When Valkey-Glide returns Map format: {streamKey: recordsData}
            Map<?, ?> streamsMap = (Map<?, ?>) result;
            
            // WORKAROUND: Since ClusterValue.of() bug converts GlideString keys to Strings,
            // we cannot rely on the map keys. For single-stream case, use the original key directly.
            if (originalStreamKeys != null && streamsMap.size() == 1 && originalStreamKeys.length == 1) {
                // Single stream case - use the original key directly
                Map.Entry<?, ?> streamEntry = streamsMap.entrySet().iterator().next();
                Map<?, ?> streamRecordsObj = (Map<?, ?>) streamEntry.getValue();
                byte[] streamKey = originalStreamKeys[0];
                List<ByteRecord> streamRecords = parseByteRecords(streamRecordsObj, streamKey, false);
                allRecords.addAll(streamRecords);
            } else {
                // Multiple streams or fallback to old behavior
                for (Map.Entry<?, ?> streamEntry : streamsMap.entrySet()) {
                    Object streamKeyObj = streamEntry.getKey();
                    Map<?, ?> streamRecordsObj = (Map<?, ?>) streamEntry.getValue();
                    
                    byte[] streamKey;
                    if (streamKeyObj instanceof byte[]) {
                        streamKey = (byte[]) streamKeyObj;
                    } else if (streamKeyObj instanceof GlideString) {
                        streamKey = ((GlideString) streamKeyObj).getBytes();
                    } else {
                        // Fallback: Convert String to UTF-8 bytes (may have issues with JDK serialization)
                        streamKey = streamKeyObj.toString().getBytes(StandardCharsets.UTF_8);
                    }
                    
                    List<ByteRecord> streamRecords = parseByteRecords(streamRecordsObj, streamKey, false);
                    allRecords.addAll(streamRecords);
                }
            }
        } 
        return allRecords;
    }


    // This is a LLM-generated code and it's so hellish i cant even think to refactor it. I am ashemed to show it here, but it works.
    private PendingMessagesSummary parsePendingMessagesSummary(Object result) {
        if (result == null) {
            return new PendingMessagesSummary("", 0L, Range.closed("", ""), Collections.emptyMap());
        }

        try {
            List<Object> list = convertToList(result);
            
            if (list.size() >= 4) {
                Object totalObj = ValkeyGlideConverters.defaultFromGlideResult(list.get(0));
                Long totalPendingMessages = 0L;
                if (totalObj instanceof Number) {
                    totalPendingMessages = ((Number) totalObj).longValue();
                } else if (totalObj instanceof List) {
                    // Handle case where result is nested - extract the actual number
                    List<?> nested = (List<?>) totalObj;
                    if (!nested.isEmpty() && nested.get(0) instanceof Number) {
                        totalPendingMessages = ((Number) nested.get(0)).longValue();
                    }
                }
                
                String lowestId = convertResultToString(ValkeyGlideConverters.defaultFromGlideResult(list.get(1)));
                String highestId = convertResultToString(ValkeyGlideConverters.defaultFromGlideResult(list.get(2)));
                Range<String> range = Range.closed(lowestId, highestId);
                
                Map<String, Long> consumerMessageCount = new HashMap<>();
                Object consumersObj = list.get(3);
                
                
                if (consumersObj instanceof Object[]) {
                    // Handle Object[] format: [[consumerName, count], [consumerName, count], ...]
                    Object[] consumersArray = (Object[]) consumersObj;
                    
                    for (Object consumerPair : consumersArray) {
                        if (consumerPair instanceof Object[]) {
                            Object[] pair = (Object[]) consumerPair;
                            if (pair.length >= 2) {
                                Object consumerNameObj = ValkeyGlideConverters.defaultFromGlideResult(pair[0]);
                                Object countObj = ValkeyGlideConverters.defaultFromGlideResult(pair[1]);
                                
                                String consumerName = convertResultToString(consumerNameObj);
                                
                                Long messageCount = 0L;
                                if (countObj instanceof Number) {
                                    messageCount = ((Number) countObj).longValue();
                                } else if (countObj != null) {
                                    try {
                                        // Use convertResultToString to handle byte[] properly
                                        String countStr = convertResultToString(countObj);
                                        messageCount = Long.parseLong(countStr);
                                    } catch (NumberFormatException e) {
                                        messageCount = 0L;
                                    }
                                }
                                
                                consumerMessageCount.put(consumerName, messageCount);
                            }
                        }
                    }
                } else if (consumersObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> consumers = (List<Object>) consumersObj;
                    
                    // Handle flat list format: [consumerName1, count1, consumerName2, count2, ...]
                    for (int i = 0; i < consumers.size(); i += 2) {
                        if (i + 1 < consumers.size()) {
                            Object consumerNameObj = ValkeyGlideConverters.defaultFromGlideResult(consumers.get(i));
                            Object countObj = ValkeyGlideConverters.defaultFromGlideResult(consumers.get(i + 1));
                            
                            String consumerName = convertResultToString(consumerNameObj);
                            
                            Long messageCount = 0L;
                            if (countObj instanceof Number) {
                                messageCount = ((Number) countObj).longValue();
                            } else if (countObj != null) {
                                try {
                                    messageCount = Long.parseLong(countObj.toString());
                                } catch (NumberFormatException e) {
                                    messageCount = 0L;
                                }
                            }
                            
                            consumerMessageCount.put(consumerName, messageCount);
                        }
                    }
                }
                
                return new PendingMessagesSummary("", totalPendingMessages, range, consumerMessageCount);
            }
            
            return new PendingMessagesSummary("", 0L, Range.closed("", ""), Collections.emptyMap());
        } catch (Exception e) {
            throw new ValkeyGlideExceptionConverter().convert(e);
        }
    }

    // This is a LLM-generated code and it's so hellish i cant even think to refactor it. I am ashemed to show it here, but it works.
    private PendingMessages parsePendingMessages(Object result, String groupName) {
        if (result == null) {
            return new PendingMessages(groupName, Collections.emptyList());
        }

        try {
            List<Object> list = convertToList(result);
            List<PendingMessage> pendingMessages = new ArrayList<>();

            for (Object item : list) {
                Object convertedItem = ValkeyGlideConverters.defaultFromGlideResult(item);
                if (convertedItem instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> messageData = (List<Object>) convertedItem;
                    if (messageData.size() >= 4) {
                        String recordId = convertResultToString(ValkeyGlideConverters.defaultFromGlideResult(messageData.get(0)));
                        String consumerName = convertResultToString(ValkeyGlideConverters.defaultFromGlideResult(messageData.get(1)));
                        Long idleTime = ((Number) ValkeyGlideConverters.defaultFromGlideResult(messageData.get(2))).longValue();
                        Long deliveryCount = ((Number) ValkeyGlideConverters.defaultFromGlideResult(messageData.get(3))).longValue();
                        
                        PendingMessage pendingMessage = new PendingMessage(
                            RecordId.of(recordId), 
                            Consumer.from(groupName, consumerName), 
                            Duration.ofMillis(idleTime), 
                            deliveryCount
                        );
                        pendingMessages.add(pendingMessage);
                    }
                }
            }
            return new PendingMessages(groupName, pendingMessages);
        } catch (Exception e) {
            throw new ValkeyGlideExceptionConverter().convert(e);
        }
    }
}
