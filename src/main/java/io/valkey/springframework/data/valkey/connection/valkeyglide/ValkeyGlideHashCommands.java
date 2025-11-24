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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAccumulator;

import javax.xml.transform.Result;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyHashCommands;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import kotlin.reflect.jvm.internal.ReflectProperties.Val;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeyHashCommands} for Valkey-Glide.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideHashCommands implements ValkeyHashCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideHashCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideHashCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public Boolean hSet(byte[] key, byte[] field, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(field, "Field must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("HSET",
                (Number glideResult) -> glideResult != null ? glideResult.longValue() > 0 : null,
                key,
                field,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean hSetNX(byte[] key, byte[] field, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(field, "Field must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("HSETNX",
                (Boolean glideResult) -> glideResult,
                key,
                field,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] hGet(byte[] key, byte[] field) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(field, "Field must not be null");
        
        try {
            return connection.execute("HGET",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key,
                field);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> hMGet(byte[] key, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            Object[] args = new Object[1 + fields.length];
            args[0] = key;
            System.arraycopy(fields, 0, args, 1, fields.length);
            return connection.execute("HMGET",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesList(glideResult),
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void hMSet(byte[] key, Map<byte[], byte[]> hashes) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(hashes, "Hashes must not be null");
        Assert.notEmpty(hashes, "Hashes must not be empty");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            for (Map.Entry<byte[], byte[]> entry : hashes.entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }
            connection.execute("HMSET",
                // Valkey returns simple OK string for HMSET
                (String glideResult) -> glideResult,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long hIncrBy(byte[] key, byte[] field, long delta) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(field, "Field must not be null");
        
        try {
            return connection.execute("HINCRBY",
                (Long glideResult) -> glideResult,
                key,
                field,
                delta);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Double hIncrBy(byte[] key, byte[] field, double delta) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(field, "Field must not be null");
        
        try {
            return connection.execute("HINCRBYFLOAT",
                (Double glideResult) -> glideResult,
                key,
                field,
                delta);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean hExists(byte[] key, byte[] field) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(field, "Field must not be null");
        
        try {
            return connection.execute("HEXISTS",
                (Boolean glideResult) -> glideResult,
                key, field);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long hDel(byte[] key, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            Object[] args = new Object[1 + fields.length];
            args[0] = key;
            System.arraycopy(fields, 0, args, 1, fields.length);

            return connection.execute("HDEL",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long hLen(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("HLEN",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> hKeys(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("HKEYS",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesSet(glideResult),
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> hVals(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("HVALS",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesList(glideResult),
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Map<byte[], byte[]> hGetAll(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("HGETALL",
                //(Map<GlideString, GlideString> glideResult) -> ValkeyGlideConverters.toBytesMap(glideResult),
                glideResult -> ValkeyGlideConverters.toBytesMap(glideResult),
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] hRandField(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("HRANDFIELD",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Map.Entry<byte[], byte[]> hRandFieldWithValues(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("HRANDFIELD",
                glideResult -> ValkeyGlideConverters.toBytesMapEntry(glideResult),
                key,
                1,
                "WITHVALUES");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> hRandField(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("HRANDFIELD",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesList(glideResult),
                key,
                count);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Map.Entry<byte[], byte[]>> hRandFieldWithValues(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");

        try {
            return connection.execute("HRANDFIELD",
                glideResult -> ValkeyGlideConverters.toMapEntriesList(glideResult),
                key,
                count,
                "WITHVALUES");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Cursor<Map.Entry<byte[], byte[]>> hScan(byte[] key, ScanOptions options) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(options, "ScanOptions must not be null");
        
        return new ValkeyGlideHashScanCursor(key, options, connection);
    }
    
    /**
     * Simple implementation of Cursor for HSCAN operation.
     */
    private static class ValkeyGlideHashScanCursor implements Cursor<Map.Entry<byte[], byte[]>> {
        
        private final byte[] key;
        private final ScanOptions options;
        private final ValkeyGlideConnection connection;
        private long cursor = 0;
        private List<Map.Entry<byte[], byte[]>> entries = new ArrayList<>();
        private int currentIndex = 0;
        private boolean finished = false;
        
        public ValkeyGlideHashScanCursor(byte[] key, ScanOptions options, ValkeyGlideConnection connection) {
            this.key = key;
            this.options = options;
            this.connection = connection;
            scanNext();
        }
        
        @Override
        public boolean hasNext() {
            if (currentIndex < entries.size()) {
                return true;
            }
            if (finished) {
                return false;
            }
            scanNext();
            return currentIndex < entries.size();
        }
        
        @Override
        public Map.Entry<byte[], byte[]> next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            return entries.get(currentIndex++);
        }

        private void scanNext() {
            if (connection.isQueueing() || connection.isPipelined()) {
                throw new InvalidDataAccessApiUsageException("'HSCAN' cannot be called in pipeline / transaction mode");
            }

            try {
                List<Object> args = new ArrayList<>();
                args.add(key);
                args.add(String.valueOf(cursor));
                
                if (options.getCount() != null) {
                    args.add("COUNT");
                    args.add(options.getCount());
                }
                
                if (options.getPattern() != null) {
                    args.add("MATCH");
                    args.add(options.getPattern());
                }
                
                connection.execute("HSCAN", (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    
                    // First element is the new cursor and is byte[], need to convert to String first
                    GlideString cursorStr = (GlideString) glideResult[0];
                    cursor = Long.parseLong(cursorStr.getString());
                    if (cursor == 0) {
                        finished = true;
                    }
                        
                    // Second element is the array of field-value pairs
                    Object[] fieldValuePairs = (Object[]) glideResult[1];
                        
                    // Reset entries for this batch
                    entries.clear();
                    currentIndex = 0;
                    for (int i = 0; i < fieldValuePairs.length; i += 2) {
                        GlideString field = (GlideString) fieldValuePairs[i];
                        GlideString value = (GlideString) fieldValuePairs[i + 1];
                        entries.add(new HashMap.SimpleEntry<>(field.getBytes(), value.getBytes()));
                    }

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
            return finished && currentIndex >= entries.size();
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

    @Override
    @Nullable
    public Long hStrLen(byte[] key, byte[] field) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(field, "Field must not be null");
        
        try {
            return connection.execute("HSTRLEN",
            (Long glideResult) -> glideResult,
            key,
            field);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> hExpire(byte[] key, long seconds, ExpirationOptions.Condition condition, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(seconds);
            
            // Add condition if not ALWAYS
            if (condition != ExpirationOptions.Condition.ALWAYS) {
                args.add(condition.name());
            }
            
            // Add FIELDS keyword and numfields
            args.add("FIELDS");
            args.add(fields.length);
            
            // Add fields
            for (byte[] field : fields) {
                args.add(field);
            }
            
            return connection.execute("HEXPIRE",
                (Object[] glideResult) -> ValkeyGlideConverters.toLongsList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> hpExpire(byte[] key, long millis, ExpirationOptions.Condition condition, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(millis);
            
            // Add condition if not ALWAYS
            if (condition != ExpirationOptions.Condition.ALWAYS) {
                args.add(condition.name());
            }
            
            // Add FIELDS keyword and numfields
            args.add("FIELDS");
            args.add(fields.length);
            
            // Add fields
            for (byte[] field : fields) {
                args.add(field);
            }
            
            return connection.execute("HPEXPIRE",
                (Object[] glideResult) -> ValkeyGlideConverters.toLongsList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> hExpireAt(byte[] key, long unixTime, ExpirationOptions.Condition condition, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(unixTime);
            
            // Add condition if not ALWAYS
            if (condition != ExpirationOptions.Condition.ALWAYS) {
                args.add(condition.name());
            }
            
            // Add FIELDS keyword and numfields
            args.add("FIELDS");
            args.add(fields.length);
            
            // Add fields
            for (byte[] field : fields) {
                args.add(field);
            }
            
            return connection.execute("HEXPIREAT",
                (Object[] glideResult) -> ValkeyGlideConverters.toLongsList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> hpExpireAt(byte[] key, long unixTimeInMillis, ExpirationOptions.Condition condition, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(unixTimeInMillis);
            
            // Add condition if not ALWAYS
            if (condition != ExpirationOptions.Condition.ALWAYS) {
                args.add(condition.name());
            }
            
            // Add FIELDS keyword and numfields
            args.add("FIELDS");
            args.add(fields.length);
            
            // Add fields
            for (byte[] field : fields) {
                args.add(field);
            }
            
            return connection.execute("HPEXPIREAT",
                (Object[] glideResult) -> ValkeyGlideConverters.toLongsList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> hTtl(byte[] key, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add("FIELDS");
            args.add(fields.length);
            for (byte[] field : fields) {
                args.add(field);
            }
            
            return connection.execute("HTTL",
                (Object[] glideResult) -> ValkeyGlideConverters.toLongsList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> hTtl(byte[] key, TimeUnit timeUnit, byte[]... fields) {
        List<Long> ttls = hTtl(key, fields);
        if (ttls == null) {
            return null;
        }
       
        List<Long> converted = new ArrayList<>(ttls.size());
        for (Long ttl : ttls) {
            if (ttl < 0) {
                converted.add(ttl);
            } else {
                converted.add(timeUnit.convert(ttl, TimeUnit.SECONDS));
            }
        }
        return converted;
    }

    @Override
    @Nullable
    public List<Long> hpTtl(byte[] key, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add("FIELDS");
            args.add(fields.length);
            for (byte[] field : fields) {
                args.add(field);
            }
            
            return connection.execute("HPTTL",
                (Object[] glideResult) -> ValkeyGlideConverters.toLongsList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> hPersist(byte[] key, byte[]... fields) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.noNullElements(fields, "Fields must not contain null elements");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add("FIELDS");
            args.add(fields.length);
            for (byte[] field : fields) {
                args.add(field);
            }
            
            return connection.execute("HPERSIST",
                (Object[] glideResult) -> ValkeyGlideConverters.toLongsList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }
}
