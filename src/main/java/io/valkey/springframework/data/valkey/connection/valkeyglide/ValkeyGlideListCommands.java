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

import io.valkey.springframework.data.valkey.connection.ValkeyListCommands;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeyListCommands} for Valkey-Glide.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideListCommands implements ValkeyListCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideListCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideListCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public Long rPush(byte[] key, byte[]... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(values, "Values must not be null");
        Assert.noNullElements(values, "Values must not contain null elements");
        
        try {
            Object[] args = new Object[values.length + 1];
            args[0] = key;
            System.arraycopy(values, 0, args, 1, values.length);
            
            return connection.execute("RPUSH",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> lPos(byte[] key, byte[] element, @Nullable Integer rank, @Nullable Integer count) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(element, "Element must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(element);
            
            if (rank != null) {
                args.add("RANK");
                args.add(rank);
            }
            
            if (count != null) {
                args.add("COUNT");
                args.add(count);
            }
            
            return connection.execute("LPOS",
                (Object glideResult) -> {
                    if (glideResult == null) {
                        return new ArrayList<>();
                    }

                    // glideResult can be a single Long or an array of Longs
                    if (glideResult instanceof Long) {
                        List<Long> singleResult = new ArrayList<>();
                        singleResult.add((Long) glideResult);
                        return singleResult;
                    }
                    return ValkeyGlideConverters.toLongsList((Object[]) glideResult);
                },
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long lPush(byte[] key, byte[]... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(values, "Values must not be null");
        Assert.noNullElements(values, "Values must not contain null elements");
        
        try {
            Object[] args = new Object[values.length + 1];
            args[0] = key;
            System.arraycopy(values, 0, args, 1, values.length);
            
            return connection.execute("LPUSH",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long rPushX(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("RPUSHX",
                (Long glideResult) -> glideResult,
                key, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long lPushX(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("LPUSHX",
                (Long glideResult) -> glideResult,
                key, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long lLen(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("LLEN",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> lRange(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("LRANGE",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesList(glideResult),
                key, start, end);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void lTrim(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            connection.execute("LTRIM",
                (String glideResult) -> glideResult, // Return the "OK" response for pipeline/transaction modes
                key, start, end);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] lIndex(byte[] key, long index) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("LINDEX",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key, index);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long lInsert(byte[] key, Position where, byte[] pivot, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(where, "Position must not be null");
        Assert.notNull(pivot, "Pivot must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            String position = (where == Position.BEFORE) ? "BEFORE" : "AFTER";
            return connection.execute("LINSERT",
                (Long glideResult) -> glideResult,
                key, position, pivot, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] lMove(byte[] sourceKey, byte[] destinationKey, Direction from, Direction to) {
        Assert.notNull(sourceKey, "Source key must not be null");
        Assert.notNull(destinationKey, "Destination key must not be null");
        Assert.notNull(from, "From direction must not be null");
        Assert.notNull(to, "To direction must not be null");
        
        try {
            String fromStr = (from == Direction.LEFT) ? "LEFT" : "RIGHT";
            String toStr = (to == Direction.LEFT) ? "LEFT" : "RIGHT";
            return connection.execute("LMOVE",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                sourceKey, destinationKey, fromStr, toStr);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] bLMove(byte[] sourceKey, byte[] destinationKey, Direction from, Direction to, double timeout) {
        Assert.notNull(sourceKey, "Source key must not be null");
        Assert.notNull(destinationKey, "Destination key must not be null");
        Assert.notNull(from, "From direction must not be null");
        Assert.notNull(to, "To direction must not be null");
        
        try {
            String fromStr = (from == Direction.LEFT) ? "LEFT" : "RIGHT";
            String toStr = (to == Direction.LEFT) ? "LEFT" : "RIGHT";
            return connection.execute("BLMOVE",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                sourceKey, destinationKey, fromStr, toStr, timeout);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void lSet(byte[] key, long index, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            connection.execute("LSET",
                (String glideResult) -> glideResult, // Return the "OK" response for pipeline/transaction modes
                key, index, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long lRem(byte[] key, long count, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("LREM",
                (Long glideResult) -> glideResult,
                key, count, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] lPop(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("LPOP",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> lPop(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("LPOP",
                (Object glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }

                    // glideResult can be a single Long or an array of Longs
                    if (glideResult instanceof GlideString) {
                        List<byte[]> singleResult = new ArrayList<>();
                        singleResult.add(((GlideString) glideResult).getBytes());
                        return singleResult;
                    }
                    return ValkeyGlideConverters.toBytesList((Object[]) glideResult);
                },
                key, count);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] rPop(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("RPOP",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> rPop(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("RPOP",
                (Object glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }

                    // glideResult can be a single Long or an array of Longs
                    if (glideResult instanceof GlideString) {
                        List<byte[]> singleResult = new ArrayList<>();
                        singleResult.add(((GlideString) glideResult).getBytes());
                        return singleResult;
                    }
                    return ValkeyGlideConverters.toBytesList((Object[]) glideResult);
                },
                key, count);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> bLPop(int timeout, byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length + 1];
            System.arraycopy(keys, 0, args, 0, keys.length);
            args[keys.length] = timeout;
            
            return connection.execute("BLPOP",
                (Object glideResult) -> {
                    if (glideResult == null) {
                        return new ArrayList<>();
                    }
                    return ValkeyGlideConverters.toBytesList((Object[]) glideResult);
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> bRPop(int timeout, byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length + 1];
            System.arraycopy(keys, 0, args, 0, keys.length);
            args[keys.length] = timeout;
            
            return connection.execute("BRPOP",
                (Object glideResult) -> {
                    if (glideResult == null) {
                        return new ArrayList<>();
                    }
                    return ValkeyGlideConverters.toBytesList((Object[]) glideResult);
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] rPopLPush(byte[] srcKey, byte[] dstKey) {
        Assert.notNull(srcKey, "Source key must not be null");
        Assert.notNull(dstKey, "Destination key must not be null");
        
        try {
            return connection.execute("RPOPLPUSH",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                srcKey, dstKey);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] bRPopLPush(int timeout, byte[] srcKey, byte[] dstKey) {
        Assert.notNull(srcKey, "Source key must not be null");
        Assert.notNull(dstKey, "Destination key must not be null");
        
        try {
            return connection.execute("BRPOPLPUSH",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                srcKey, dstKey, timeout);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }
}
