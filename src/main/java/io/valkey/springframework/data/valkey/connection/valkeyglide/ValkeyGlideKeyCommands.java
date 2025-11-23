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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyKeyCommands;
import io.valkey.springframework.data.valkey.connection.SortParameters;
import io.valkey.springframework.data.valkey.connection.ValueEncoding;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeyKeyCommands} for Valkey-Glide.
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideKeyCommands implements ValkeyKeyCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideKeyCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideKeyCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public Boolean copy(byte[] sourceKey, byte[] targetKey, boolean replace) {
        Assert.notNull(sourceKey, "Source key must not be null");
        Assert.notNull(targetKey, "Target key must not be null");
        
        try {
            if (replace) {
                return connection.execute("COPY",
                    (Boolean glideResult) -> glideResult,
                    sourceKey, targetKey, "REPLACE");
            } else {
                return connection.execute("COPY",
                    (Boolean glideResult) -> glideResult,
                    sourceKey, targetKey);
            }
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean exists(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("EXISTS",
                (Long glideResult) -> glideResult != null ? glideResult > 0 : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long exists(byte[]... keys) {
        Assert.notEmpty(keys, "Keys must not be empty");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);
            return connection.execute("EXISTS",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long del(byte[]... keys) {
        Assert.notEmpty(keys, "Keys must not be empty");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);

            return connection.execute("DEL",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long unlink(byte[]... keys) {
        Assert.notEmpty(keys, "Keys must not be empty");
        Assert.noNullElements(keys, "Keys must not contain null elements");

        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);

            return connection.execute("UNLINK",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public DataType type(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("TYPE",
                (GlideString glideResult) -> glideResult != null ? ValkeyGlideConverters.toDataType(glideResult.getBytes()) : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long touch(byte[]... keys) {
        Assert.notEmpty(keys, "Keys must not be empty");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);

            return connection.execute("TOUCH",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> keys(byte[] pattern) {
        Assert.notNull(pattern, "Pattern must not be null");
        
        try {
            return connection.execute("KEYS",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesSet(glideResult),
                pattern);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Cursor<byte[]> scan(ScanOptions options) {
        return new ValkeyGlideKeyScanCursor(connection, options != null ? options : ScanOptions.NONE);
    }

    /**
     * Simple implementation of a scan cursor for keys.
     */
    private static class ValkeyGlideKeyScanCursor implements Cursor<byte[]> {
        
        private final ValkeyGlideConnection connection;
        private final ScanOptions scanOptions;
        private String cursor = "0";
        private java.util.Iterator<byte[]> currentBatch;
        private boolean finished = false;

        public ValkeyGlideKeyScanCursor(ValkeyGlideConnection connection, ScanOptions scanOptions) {
            this.connection = connection;
            this.scanOptions = scanOptions;
        }

        @Override
        public long getCursorId() {
            try {
                return Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public Cursor.CursorId getId() {
            return Cursor.CursorId.of(cursor);
        }

        @Override
        public boolean hasNext() {
            // First check if we have items in current batch
            if (currentBatch != null && currentBatch.hasNext()) {
                return true;
            }
            
            // If we're finished and no more items in current batch, return false
            if (finished) {
                return false;
            }
            
            // Try to load next batch
            loadNextBatch();
            
            // Check if we have items after loading next batch
            return currentBatch != null && currentBatch.hasNext();
        }

        @Override
        public byte[] next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            return currentBatch.next();
        }

        @Override
        public void close() {
            finished = true;
            currentBatch = null;
        }

        @Override
        public long getPosition() {
            return getCursorId();
        }

        @Override
        public boolean isClosed() {
            return finished;
        }

        private void loadNextBatch() {
            if (connection.isQueueing() || connection.isPipelined()) {
                throw new InvalidDataAccessApiUsageException("'SCAN' cannot be called in pipeline / transaction mode");
            }

            try {
                List<Object> args = new ArrayList<>();
                args.add(cursor);
                
                if (scanOptions.getPattern() != null) {
                    args.add("MATCH");
                    args.add(scanOptions.getPattern());
                }
                
                if (scanOptions.getCount() != null) {
                    args.add("COUNT");
                    args.add(scanOptions.getCount());
                }
                
                Object[] scanResult = connection.execute("SCAN",
                    (Object[] glideResult) -> glideResult,
                    args.toArray());
                
                if (scanResult != null && scanResult.length >= 2) {
                    Object nextCursor = scanResult[0];
                    Object keysResult = scanResult[1];
                    
                    // Handle cursor conversion
                    if (nextCursor instanceof GlideString) {
                        cursor = new String(((GlideString) nextCursor).getBytes(), StandardCharsets.UTF_8);
                    } else if (nextCursor instanceof byte[]) {
                        cursor = new String((byte[]) nextCursor, StandardCharsets.UTF_8);
                    } else {
                        cursor = nextCursor.toString();
                    }
                    
                    if ("0".equals(cursor)) {
                        finished = true;
                    }
                    
                    // Convert keys result
                    List<byte[]> keys = ValkeyGlideConverters.toBytesList(keysResult);
                    currentBatch = keys.iterator();
                }
            } catch (Exception ex) {
                throw new ValkeyGlideExceptionConverter().convert(ex);
            }
        }
    }

    @Override
    @Nullable
    public byte[] randomKey() {
        try {
            return connection.execute("RANDOMKEY",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void rename(byte[] oldKey, byte[] newKey) {
        Assert.notNull(oldKey, "Old key must not be null");
        Assert.notNull(newKey, "New key must not be null");
        
        try {
            connection.execute("RENAME",
                (String glideResult) -> glideResult, // Return the "OK" response for pipeline/transaction modes
                oldKey, newKey);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean renameNX(byte[] oldKey, byte[] newKey) {
        Assert.notNull(oldKey, "Old key must not be null");
        Assert.notNull(newKey, "New key must not be null");
        
        try {
            return connection.execute("RENAMENX",
                (Boolean glideResult) -> glideResult,
                oldKey, newKey);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean expire(byte[] key, long seconds, ExpirationOptions.Condition condition) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            if (condition == ExpirationOptions.Condition.ALWAYS) {
                return connection.execute("EXPIRE",
                    (Boolean glideResult) -> glideResult,
                    key, seconds);
            } else {
                String conditionStr = switch (condition) {
                    case XX -> "XX";
                    case NX -> "NX";
                    case GT -> "GT";
                    case LT -> "LT";
                    default -> throw new IllegalArgumentException("Unsupported expiration condition: " + condition);
                };
                return connection.execute("EXPIRE",
                    (Boolean glideResult) -> glideResult,
                    key, seconds, conditionStr);
            }
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean pExpire(byte[] key, long millis, ExpirationOptions.Condition condition) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            if (condition == ExpirationOptions.Condition.ALWAYS) {
                return connection.execute("PEXPIRE",
                    (Boolean glideResult) -> glideResult,
                    key, millis);
            } else {
                String conditionStr = switch (condition) {
                    case XX -> "XX";
                    case NX -> "NX";
                    case GT -> "GT";
                    case LT -> "LT";
                    default -> throw new IllegalArgumentException("Unsupported expiration condition: " + condition);
                };
                return connection.execute("PEXPIRE",
                    (Boolean glideResult) -> glideResult,
                    key, millis, conditionStr);
            }
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean expireAt(byte[] key, long unixTime, ExpirationOptions.Condition condition) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            if (condition == ExpirationOptions.Condition.ALWAYS) {
                return connection.execute("EXPIREAT",
                    (Boolean glideResult) -> glideResult,
                    key, unixTime);
            } else {
                String conditionStr = switch (condition) {
                    case XX -> "XX";
                    case NX -> "NX";
                    case GT -> "GT";
                    case LT -> "LT";
                    default -> throw new IllegalArgumentException("Unsupported expiration condition: " + condition);
                };
                return connection.execute("EXPIREAT",
                    (Boolean glideResult) -> glideResult,
                    key, unixTime, conditionStr);
            }
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean pExpireAt(byte[] key, long unixTimeInMillis, ExpirationOptions.Condition condition) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            if (condition == ExpirationOptions.Condition.ALWAYS) {
                return connection.execute("PEXPIREAT",
                    (Boolean glideResult) -> glideResult,
                    key, unixTimeInMillis);
            } else {
                String conditionStr = switch (condition) {
                    case XX -> "XX";
                    case NX -> "NX";
                    case GT -> "GT";
                    case LT -> "LT";
                    default -> throw new IllegalArgumentException("Unsupported expiration condition: " + condition);
                };
                return connection.execute("PEXPIREAT",
                    (Boolean glideResult) -> glideResult,
                    key, unixTimeInMillis, conditionStr);
            }
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean persist(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("PERSIST",
                (Boolean glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean move(byte[] key, int dbIndex) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("MOVE",
                (Boolean glideResult) -> glideResult,
                key, dbIndex);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long ttl(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("TTL",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long ttl(byte[] key, TimeUnit timeUnit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(timeUnit, "TimeUnit must not be null");
        
        try {
            return connection.execute("TTL",
                (Long ttlSeconds) -> {
                    if (ttlSeconds == null) {
                        return null;
                    }
                    
                    // Valkey TTL semantics: -2 = key doesn't exist, -1 = key exists but no expiration
                    // These are special values, not actual time values, so don't convert them
                    if (ttlSeconds < 0) {
                        return ttlSeconds;
                    }
                    
                    return timeUnit.convert(ttlSeconds, TimeUnit.SECONDS);
                },
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long pTtl(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("PTTL",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long pTtl(byte[] key, TimeUnit timeUnit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(timeUnit, "TimeUnit must not be null");
        
        try {
            return connection.execute("PTTL",
                (Long pTtlMillis) -> {
                    if (pTtlMillis == null) {
                        return null;
                    }
                    
                    // Valkey PTTL semantics: -2 = key doesn't exist, -1 = key exists but no expiration
                    // These are special values, not actual time values, so don't convert them
                    if (pTtlMillis < 0) {
                        return pTtlMillis;
                    }
                    
                    return timeUnit.convert(pTtlMillis, TimeUnit.MILLISECONDS);
                },
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> sort(byte[] key, SortParameters params) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            
            if (params != null) {
                ValkeyGlideConverters.appendSortParameters(args, params);
            }
            
            return connection.execute("SORT",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long sort(byte[] key, SortParameters params, byte[] storeKey) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(storeKey, "Store key must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            
            if (params != null) {
                ValkeyGlideConverters.appendSortParameters(args, params);
            }
            
            args.add("STORE");
            args.add(storeKey);
            
            return connection.execute("SORT",
                (Long glideResult) -> glideResult,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] dump(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("DUMP",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void restore(byte[] key, long ttlInMillis, byte[] serializedValue, boolean replace) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(serializedValue, "Serialized value must not be null");
        
        try {
            if (replace) {
                connection.execute("RESTORE",
                    (String glideResult) -> glideResult, // Return the "OK" response for pipeline/transaction modes
                    key, ttlInMillis, serializedValue, "REPLACE");
            } else {
                connection.execute("RESTORE",
                    (String glideResult) -> glideResult, // Return the "OK" response for pipeline/transaction modes
                    key, ttlInMillis, serializedValue);
            }
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public ValueEncoding encodingOf(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("OBJECT",
                (GlideString glideResult) -> ValkeyGlideConverters.toValueEncoding(glideResult),
                "ENCODING", key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Duration idletime(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("OBJECT",
                (Long glideResult) -> glideResult != null ? Duration.ofSeconds(glideResult) : null,
                "IDLETIME", key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long refcount(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("OBJECT",
                (Long glideResult) -> glideResult,
                "REFCOUNT", key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }
}
