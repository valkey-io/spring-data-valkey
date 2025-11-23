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
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.BitFieldSubCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyStringCommands;
import io.valkey.springframework.data.valkey.core.types.Expiration;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeyStringCommands} for Valkey-Glide.
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideStringCommands implements ValkeyStringCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideStringCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideStringCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public byte[] get(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("GET",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean setNX(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("SETNX",
                (Long glideResult) -> glideResult != null ? glideResult != 0 : null,
                key,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] getDel(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("GETDEL",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] getEx(byte[] key, Expiration expiration) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(expiration, "Expiration must not be null");
        
        try {
            if (expiration.isPersistent()) {
                return connection.execute("GETEX",
                    (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                    key, "PERSIST");
            } else if (expiration.isKeepTtl()) {
                return connection.execute("GETEX",
                    (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                    key);
            } else if (expiration.isUnixTimestamp()) {
                if (expiration.getTimeUnit() == TimeUnit.SECONDS) {
                    return connection.execute("GETEX",
                        (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                        key, "EXAT", expiration.getExpirationTime());
                } else {
                    return connection.execute("GETEX",
                        (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                        key, "PXAT", expiration.getExpirationTime());
                }
            } else {
                if (expiration.getTimeUnit() == TimeUnit.SECONDS) {
                    return connection.execute("GETEX",
                        (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                        key, "EX", expiration.getExpirationTime());
                } else {
                    return connection.execute("GETEX",
                        (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                        key, "PX", expiration.getExpirationTime());
                }
            }
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] getSet(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("GETSET",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> mGet(byte[]... keys) {
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);
            return connection.execute("MGET",
                (Object[] glideResult) -> ValkeyGlideConverters.toBytesList(glideResult),
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean set(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("SET",
                (String glideResult) -> glideResult != null ? "OK".equals(glideResult) : null,
                key,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean set(byte[] key, byte[] value, Expiration expiration, SetOption option) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(value);
            
            if (expiration != null && !expiration.isPersistent()) {
                if (expiration.isUnixTimestamp()) {
                    if (expiration.getTimeUnit() == TimeUnit.SECONDS) {
                        args.add("EXAT");
                    } else {
                        args.add("PXAT");
                    }
                } else {
                    if (expiration.getTimeUnit() == TimeUnit.SECONDS) {
                        args.add("EX");
                    } else {
                        args.add("PX");
                    }
                }
                args.add(expiration.getExpirationTime());
            }
            
            final boolean hasConditionalOption;
            if (option != null) {
                switch (option) {
                    case SET_IF_ABSENT:
                        args.add("NX");
                        hasConditionalOption = true;
                        break;
                    case SET_IF_PRESENT:
                        args.add("XX");
                        hasConditionalOption = true;
                        break;
                    case UPSERT:
                        // No additional argument needed
                        hasConditionalOption = false;
                        break;
                    default:
                        hasConditionalOption = false;
                        break;
                }
            } else {
                hasConditionalOption = false;
            }
            
            return connection.execute("SET",
                (String glideResult) -> {
                    if (glideResult == null) {
                        return hasConditionalOption ? Boolean.FALSE : null;
                    }
                    return "OK".equals(glideResult) ? Boolean.TRUE : null;
                },
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] setGet(byte[] key, byte[] value, Expiration expiration, SetOption option) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(value);
            args.add("GET");
            
            if (expiration != null && !expiration.isPersistent()) {
                if (expiration.isUnixTimestamp()) {
                    if (expiration.getTimeUnit() == TimeUnit.SECONDS) {
                        args.add("EXAT");
                    } else {
                        args.add("PXAT");
                    }
                } else {
                    if (expiration.getTimeUnit() == TimeUnit.SECONDS) {
                        args.add("EX");
                    } else {
                        args.add("PX");
                    }
                }
                args.add(expiration.getExpirationTime());
            }
            
            if (option != null) {
                switch (option) {
                    case SET_IF_ABSENT:
                        args.add("NX");
                        break;
                    case SET_IF_PRESENT:
                        args.add("XX");
                        break;
                    case UPSERT:
                        // No additional argument needed
                        break;
                }
            }
            
            return connection.execute("SET",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean setEx(byte[] key, long seconds, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("SETEX",
                (String glideResult) -> glideResult != null ? "OK".equals(glideResult) : null,
                key,
                seconds,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean pSetEx(byte[] key, long milliseconds, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("PSETEX",
                (String glideResult) -> glideResult != null ? "OK".equals(glideResult) : null,
                key,
                milliseconds,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean mSet(Map<byte[], byte[]> tuple) {
        Assert.notNull(tuple, "Tuple must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            for (Map.Entry<byte[], byte[]> entry : tuple.entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }
            
            return connection.execute("MSET",
                (String glideResult) -> glideResult != null ? "OK".equals(glideResult) : null,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean mSetNX(Map<byte[], byte[]> tuple) {
        Assert.notNull(tuple, "Tuple must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            for (Map.Entry<byte[], byte[]> entry : tuple.entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }
            
            return connection.execute("MSETNX",
                (Boolean glideResult) -> glideResult,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long incr(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("INCR",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long incrBy(byte[] key, long value) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("INCRBY",
                (Long glideResult) -> glideResult,
                key,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Double incrBy(byte[] key, double value) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("INCRBYFLOAT",
                (Double glideResult) -> glideResult,
                key,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long decr(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("DECR",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long decrBy(byte[] key, long value) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("DECRBY",
                (Long glideResult) -> glideResult,
                key,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long append(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("APPEND",
                (Long glideResult) -> glideResult,
                key,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] getRange(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("GETRANGE",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key,
                start,
                end);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void setRange(byte[] key, byte[] value, long offset) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            connection.execute("SETRANGE",
                (Long glideResult) -> glideResult,
                key,
                offset,
                value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean getBit(byte[] key, long offset) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("GETBIT",
                (Long glideResult) -> glideResult != null ? glideResult != 0 : null,
                key,
                offset);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Boolean setBit(byte[] key, long offset, boolean value) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("SETBIT",
                (Long glideResult) -> glideResult != null ? glideResult != 0 : null,
                key,
                offset,
                value ? 1 : 0);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long bitCount(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("BITCOUNT",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long bitCount(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("BITCOUNT",
                (Long glideResult) -> glideResult,
                key,
                start,
                end);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Long> bitField(byte[] key, BitFieldSubCommands subCommands) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(subCommands, "SubCommands must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            
            for (BitFieldSubCommands.BitFieldSubCommand subCommand : subCommands.getSubCommands()) {
                if (subCommand instanceof BitFieldSubCommands.BitFieldGet) {
                    BitFieldSubCommands.BitFieldGet get = (BitFieldSubCommands.BitFieldGet) subCommand;
                    args.add("GET");
                    args.add(get.getType().asString());
                    args.add(get.getOffset().asString());
                } else if (subCommand instanceof BitFieldSubCommands.BitFieldSet) {
                    BitFieldSubCommands.BitFieldSet set = (BitFieldSubCommands.BitFieldSet) subCommand;
                    args.add("SET");
                    args.add(set.getType().asString());
                    args.add(set.getOffset().asString());
                    args.add(set.getValue());
                } else if (subCommand instanceof BitFieldSubCommands.BitFieldIncrBy) {
                    BitFieldSubCommands.BitFieldIncrBy incrBy = (BitFieldSubCommands.BitFieldIncrBy) subCommand;
                    
                    // Add OVERFLOW command if specified
                    // The OVERFLOW command must be sent before the INCRBY/SET commands it affects
                    if (incrBy.getOverflow() != null) {
                        args.add("OVERFLOW");
                        args.add(incrBy.getOverflow().name());
                    }
                    
                    args.add("INCRBY");
                    args.add(incrBy.getType().asString());
                    args.add(incrBy.getOffset().asString());
                    args.add(incrBy.getValue());
                }
            }
            
            return connection.execute("BITFIELD",
                (Object[] glideResult) -> toBitFieldLongsList(glideResult),
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    /**
     * Dedicated mapper for BITFIELD command results that handles null values.
     * BITFIELD is unique in that it can return NULL elements in the array when
     * OVERFLOW FAIL is used and an overflow/underflow is detected.
     *
     * @param glideResult The raw result from Glide
     * @return List of Long values, with nulls preserved for failed overflow operations
     */
    @Nullable
    private static List<Long> toBitFieldLongsList(@Nullable Object[] glideResult) {
        if (glideResult == null) {
            return null;
        }
        
        List<Long> resultList = new ArrayList<>(glideResult.length);
        for (Object item : glideResult) {
            // Handle null (from OVERFLOW FAIL) - preserve it in the result
            if (item == null) {
                resultList.add(null);
            } else {
                resultList.add(((Number) item).longValue());
            }
        }
        return resultList;
    }

    @Override
    @Nullable
    public Long bitOp(BitOperation op, byte[] destination, byte[]... keys) {
        Assert.notNull(op, "BitOperation must not be null");
        Assert.notNull(destination, "Destination key must not be null");
        Assert.notNull(keys, "Keys must not be null");
        Assert.noNullElements(keys, "Keys must not contain null elements");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(op.name());
            args.add(destination);
            for (byte[] key : keys) {
                args.add(key);
            }
            
            return connection.execute("BITOP",
                (Long glideResult) -> glideResult,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long bitPos(byte[] key, boolean bit, Range<Long> range) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            List<Object> args = new ArrayList<>();
            args.add(key);
            args.add(bit ? 1 : 0);
            
            if (range != null) {
                if (range.getLowerBound().isBounded()) {
                    args.add(range.getLowerBound().getValue().get());
                }
                if (range.getUpperBound().isBounded()) {
                    args.add(range.getUpperBound().getValue().get());
                }
            }
            
            return connection.execute("BITPOS",
                (Long glideResult) -> glideResult,
                args.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long strLen(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("STRLEN",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }
}
