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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.ValkeySetCommands;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeySetCommands} for Valkey-Glide.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideSetCommands implements ValkeySetCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideSetCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideSetCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public Long sAdd(byte[] key, byte[]... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(values, "Values must not be null");
        Assert.noNullElements(values, "Values must not contain null elements");
        
        try {
            Object[] args = new Object[values.length + 1];
            args[0] = key;
            System.arraycopy(values, 0, args, 1, values.length);
            
            return connection.execute("SADD",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long sRem(byte[] key, byte[]... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(values, "Values must not be null");
        Assert.noNullElements(values, "Values must not contain null elements");
        
        try {
            Object[] args = new Object[values.length + 1];
            args[0] = key;
            System.arraycopy(values, 0, args, 1, values.length);
            
            return connection.execute("SREM",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] sPop(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("SPOP",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> sPop(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");

        try {
            return connection.execute("SPOP",
                (HashSet<GlideString> glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    List<byte[]> resultList = new ArrayList<>();
                    for (GlideString gs : glideResult) {
                        resultList.add(gs.getBytes());
                    }
                    return resultList;
            },
            key, count);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean sMove(byte[] srcKey, byte[] destKey, byte[] value) {
        Assert.notNull(srcKey, "Source key must not be null");
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("SMOVE",
                (Boolean glideResult) -> glideResult,
                srcKey, destKey, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long sCard(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("SCARD",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean sIsMember(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("SISMEMBER",
                (Boolean glideResult) -> glideResult,
                key, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Boolean> sMIsMember(byte[] key, byte[]... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(values, "Values must not be null");
        Assert.noNullElements(values, "Values must not contain null elements");
        
        try {
            Object[] args = new Object[values.length + 1];
            args[0] = key;
            System.arraycopy(values, 0, args, 1, values.length);
            
            return connection.execute("SMISMEMBER",
                (Object[] glideResult) -> ValkeyGlideConverters.toBooleansList(glideResult),
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> sDiff(byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);
            return connection.execute("SDIFF",
                (HashSet<GlideString> glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new HashSet<>();
                    for (GlideString gs : glideResult) {
                        resultSet.add(gs.getBytes());
                    }
                    return resultSet;
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long sDiffStore(byte[] destKey, byte[]... keys) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length + 1];
            args[0] = destKey;
            System.arraycopy(keys, 0, args, 1, keys.length);
            
            return connection.execute("SDIFFSTORE",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> sInter(byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);
            return connection.execute("SINTER",
                (HashSet<GlideString> glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new HashSet<>();
                    for (GlideString gs : glideResult) {
                        resultSet.add(gs.getBytes());
                    }
                    return resultSet;
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long sInterStore(byte[] destKey, byte[]... keys) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length + 1];
            args[0] = destKey;
            System.arraycopy(keys, 0, args, 1, keys.length);
            
            return connection.execute("SINTERSTORE",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> sUnion(byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);

            return connection.execute("SUNION",
                (HashSet<GlideString> glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new HashSet<>();
                    for (GlideString gs : glideResult) {
                        resultSet.add(gs.getBytes());
                    }
                    return resultSet;
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long sUnionStore(byte[] destKey, byte[]... keys) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length + 1];
            args[0] = destKey;
            System.arraycopy(keys, 0, args, 1, keys.length);
            
            return connection.execute("SUNIONSTORE",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> sMembers(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("SMEMBERS",
                (HashSet<GlideString> glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new HashSet<>();
                    for (GlideString gs : glideResult) {
                        resultSet.add(gs.getBytes());
                    }
                    return resultSet;
                },
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] sRandMember(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("SRANDMEMBER",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> sRandMember(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("SRANDMEMBER",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesList(glideResult),
                key, count);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Cursor<byte[]> sScan(byte[] key, ScanOptions options) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(options, "ScanOptions must not be null");
        
        return new ValkeyGlideSetScanCursor(key, options, connection);
    }
    
    /**
     * Simple implementation of Cursor for SSCAN operation.
     */
    private static class ValkeyGlideSetScanCursor implements Cursor<byte[]> {
        
        private final byte[] key;
        private final ScanOptions options;
        private final ValkeyGlideConnection connection;
        private long cursor = 0;
        private List<byte[]> members = new ArrayList<>();
        private int currentIndex = 0;
        private boolean finished = false;
        
        public ValkeyGlideSetScanCursor(byte[] key, ScanOptions options, ValkeyGlideConnection connection) {
            this.key = key;
            this.options = options;
            this.connection = connection;
            scanNext();
        }
        
        @Override
        public boolean hasNext() {
            if (currentIndex < members.size()) {
                return true;
            }
            if (finished) {
                return false;
            }
            scanNext();
            return currentIndex < members.size();
        }
        
        @Override
        public byte[] next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            return members.get(currentIndex++);
        }
        
        private void scanNext() {
            if (connection.isQueueing() || connection.isPipelined()) {
                throw new InvalidDataAccessApiUsageException("'SSCAN' cannot be called in pipeline / transaction mode");
            }

            try {
                List<Object> args = new ArrayList<>();
                args.add(key);
                args.add(String.valueOf(cursor));
                
                if (options.getPattern() != null) {
                    args.add("MATCH");
                    args.add(options.getPattern());
                }
                
                if (options.getCount() != null) {
                    args.add("COUNT");
                    args.add(options.getCount());
                }
                
                connection.execute("SSCAN",
                    (Object[] glideResult) -> {
                        if (glideResult == null) {
                            return null;
                        }
                    
                        // First element is the new cursor and is byte[], need to convert to String first
                        GlideString cursorStr = (GlideString) glideResult[0];
                        cursor = Long.parseLong(cursorStr.getString());
                        if (cursor == 0) {
                            finished = true;
                        }
                        
                        // Reset members for this batch
                        members.clear();
                        currentIndex = 0;
                        
                        members.addAll(ValkeyGlideConverters.toBytesList((Object[]) glideResult[1]));
                        return null; // We don't need to return anything from this mapper
                    },
                    args.toArray());
            } catch (Exception ex) {
                throw new ValkeyGlideExceptionConverter().convert(ex);
            }
        }
        
        @Override
        public void close() {
            // No resources to close for this implementation
        }
        
        @Override
        public boolean isClosed() {
            return finished && currentIndex >= members.size();
        }
        
        @Override
        public long getCursorId() {
            return cursor;
        }
        
        @Override
        public long getPosition() {
            return currentIndex;
        }
        
        @Override
        public CursorId getId() {
            return CursorId.of(cursor);
        }
    }
}
