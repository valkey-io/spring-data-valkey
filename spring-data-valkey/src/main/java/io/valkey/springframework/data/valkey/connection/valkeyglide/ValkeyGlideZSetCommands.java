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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.ValkeyZSetCommands;
import io.valkey.springframework.data.valkey.connection.zset.Aggregate;
import io.valkey.springframework.data.valkey.connection.zset.DefaultTuple;
import io.valkey.springframework.data.valkey.connection.zset.Tuple;
import io.valkey.springframework.data.valkey.connection.zset.Weights;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeyZSetCommands} for Valkey-Glide.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideZSetCommands implements ValkeyZSetCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideZSetCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideZSetCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public Boolean zAdd(byte[] key, double score, byte[] value, ZAddArgs args) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        Assert.notNull(args, "ZAddArgs must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            // Add arguments based on ZAddArgs
            if (args.contains(ZAddArgs.Flag.NX)) {
                commandArgs.add("NX");
            }
            if (args.contains(ZAddArgs.Flag.XX)) {
                commandArgs.add("XX");
            }
            if (args.contains(ZAddArgs.Flag.GT)) {
                commandArgs.add("GT");
            }
            if (args.contains(ZAddArgs.Flag.LT)) {
                commandArgs.add("LT");
            }
            if (args.contains(ZAddArgs.Flag.CH)) {
                commandArgs.add("CH");
            }
            
            commandArgs.add(score);
            commandArgs.add(value);
            
            return connection.execute("ZADD",
                (Long glideResult) -> glideResult == null ? null : glideResult > 0,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zAdd(byte[] key, Set<Tuple> tuples, ZAddArgs args) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(tuples, "Tuples must not be null");
        Assert.notNull(args, "ZAddArgs must not be null");
        
        if (tuples.isEmpty()) {
            return 0L;
        }
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            // Add arguments based on ZAddArgs
            if (args.contains(ZAddArgs.Flag.NX)) {
                commandArgs.add("NX");
            }
            if (args.contains(ZAddArgs.Flag.XX)) {
                commandArgs.add("XX");
            }
            if (args.contains(ZAddArgs.Flag.GT)) {
                commandArgs.add("GT");
            }
            if (args.contains(ZAddArgs.Flag.LT)) {
                commandArgs.add("LT");
            }
            if (args.contains(ZAddArgs.Flag.CH)) {
                commandArgs.add("CH");
            }
            
            // Add score-value pairs
            for (Tuple tuple : tuples) {
                commandArgs.add(tuple.getScore());
                commandArgs.add(tuple.getValue());
            }
            
            return connection.execute("ZADD",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRem(byte[] key, byte[]... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(values, "Values must not be null");
        Assert.noNullElements(values, "Values must not contain null elements");
        
        try {
            Object[] args = new Object[values.length + 1];
            args[0] = key;
            System.arraycopy(values, 0, args, 1, values.length);
            
            return connection.execute("ZREM",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Double zIncrBy(byte[] key, double increment, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("ZINCRBY",
                (Double glideResult) -> glideResult,
                key, increment, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public byte[] zRandMember(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZRANDMEMBER",
                (GlideString glideResult) -> glideResult != null ? glideResult.getBytes() : null,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<byte[]> zRandMember(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZRANDMEMBER",
                (Object glideResult) -> {
                    if (glideResult == null) {
                        return new ArrayList<>();
                    }
                    if (glideResult instanceof GlideString) {
                        // Single result case
                        List<byte[]> singleResultList = new ArrayList<>(1);
                        singleResultList.add(((GlideString) glideResult).getBytes());
                        return singleResultList;
                    }
                    Object[] glideResultArray = (Object[]) glideResult;
                    List<byte[]> resultList = new ArrayList<>(glideResultArray.length);
                    for (Object item : glideResultArray) {
                        resultList.add(((GlideString) item).getBytes());
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
    public Tuple zRandMemberWithScore(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZRANDMEMBER",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }

                    Object[] firstElement = (Object[]) glideResult[0];
                    byte[] value = ((GlideString) firstElement[0]).getBytes();
                    Double score = (Double) firstElement[1];
                    return new DefaultTuple(value, score);
                },
                key, 1, "WITHSCORES");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Tuple> zRandMemberWithScore(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZRANDMEMBER",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return new ArrayList<>();
                    }
                    
                    List<Tuple> resultList = new ArrayList<>();
                    for (Object item : glideResult) {
                        Object[] pair = (Object[]) item;
                        byte[] value = ((GlideString) pair[0]).getBytes();
                        Double score = (Double) pair[1];
                        resultList.add(new DefaultTuple(value, score));
                    }
                    return resultList;
                },
                key, count, "WITHSCORES");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRank(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("ZRANK",
                (Long glideResult) -> glideResult,
                key, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRevRank(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("ZREVRANK",
                (Long glideResult) -> glideResult,
                key, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zRange(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZRANGE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                key, start, end);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zRangeWithScores(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZRANGE",
                (Map<?, ?>  glideResult) -> {
                    if (glideResult == null || glideResult.isEmpty()) {
                        return null;
                    }

                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Map.Entry<?, ?> entry : glideResult.entrySet()) {
                        byte[] value = ((GlideString) entry.getKey()).getBytes();
                        Double score = ((Number) entry.getValue()).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },

                key, start, end, "WITHSCORES");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zRangeByScore(byte[] key, org.springframework.data.domain.Range<? extends Number> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            // Add range bounds
            commandArgs.add(formatRangeBound(range.getLowerBound(), true));
            commandArgs.add(formatRangeBound(range.getUpperBound(), false));
            
            // Add limit if specified
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZRANGEBYSCORE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zRangeByScore(byte[] key, String min, String max, long offset, long count) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(min, "Min must not be null");
        Assert.notNull(max, "Max must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            commandArgs.add(min);
            commandArgs.add(max);
            commandArgs.add("LIMIT");
            commandArgs.add(offset);
            commandArgs.add(count);
            
            return connection.execute("ZRANGEBYSCORE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zRangeByScoreWithScores(byte[] key, org.springframework.data.domain.Range<? extends Number> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            // Add range bounds
            commandArgs.add(formatRangeBound(range.getLowerBound(), true));
            commandArgs.add(formatRangeBound(range.getUpperBound(), false));
            
            commandArgs.add("WITHSCORES");
            
            // Add limit if specified
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZRANGEBYSCORE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Object item : glideResult) {
                        Object[] valueScorePair = (Object[]) item;
                        byte[] value = ((GlideString) valueScorePair[0]).getBytes();
                        Double score = ((Number) valueScorePair[1]).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zRevRange(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZREVRANGE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                key, start, end);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zRevRangeWithScores(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZREVRANGE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Object item : glideResult) {
                        Object[] valueScorePair = (Object[]) item;
                        byte[] value = ((GlideString) valueScorePair[0]).getBytes();
                        Double score = ((Number) valueScorePair[1]).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },
                key, start, end, "WITHSCORES");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zRevRangeByScore(byte[] key, org.springframework.data.domain.Range<? extends Number> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            // Add range bounds (reversed for ZREVRANGEBYSCORE)
            commandArgs.add(formatRangeBound(range.getUpperBound(), true));
            commandArgs.add(formatRangeBound(range.getLowerBound(), false));
            
            // Add limit if specified
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZREVRANGEBYSCORE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zRevRangeByScoreWithScores(byte[] key, org.springframework.data.domain.Range<? extends Number> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            // Add range bounds (reversed for ZREVRANGEBYSCORE)
            commandArgs.add(formatRangeBound(range.getUpperBound(), true));
            commandArgs.add(formatRangeBound(range.getLowerBound(), false));
            
            commandArgs.add("WITHSCORES");
            
            // Add limit if specified
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZREVRANGEBYSCORE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Object item : glideResult) {
                        // Each item is expected to be an array: [value, score]
                        Object[] valueScorePair = (Object[]) item;
                        byte[] value = ((GlideString) valueScorePair[0]).getBytes();
                        Double score = ((Number) valueScorePair[1]).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zCount(byte[] key, org.springframework.data.domain.Range<? extends Number> range) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        
        try {
            return connection.execute("ZCOUNT",
                (Long glideResult) -> glideResult,
                key, 
                formatRangeBound(range.getLowerBound(), true), 
                formatRangeBound(range.getUpperBound(), false));
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zLexCount(byte[] key, org.springframework.data.domain.Range<byte[]> range) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        
        try {
            return connection.execute("ZLEXCOUNT",
                (Long glideResult) -> glideResult,
                key, 
                formatLexRangeBound(range.getLowerBound(), true), 
                formatLexRangeBound(range.getUpperBound(), false));
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Tuple zPopMin(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZPOPMIN",
                (Map<?, ?>  glideResult) -> {
                    if (glideResult == null || glideResult.isEmpty()) {
                        return null;
                    }
                    Map.Entry<?, ?> entry = glideResult.entrySet().iterator().next();
                    byte[] value = ((GlideString) entry.getKey()).getBytes();
                    Double score = ((Number) entry.getValue()).doubleValue();
                    return new DefaultTuple(value, score);
                },
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zPopMin(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZPOPMIN",
                (Object glideResult) -> glideResult != null ? convertToTupleSetForPop(glideResult, true) : null,
                key, count);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Tuple bZPopMin(byte[] key, long timeout, TimeUnit unit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(unit, "TimeUnit must not be null");
        
        try {
            long timeoutInSeconds = unit.toSeconds(timeout);
            
            return connection.execute("BZPOPMIN",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    byte[] value = ((GlideString) glideResult[1]).getBytes();
                    Double score = ((Number) glideResult[2]).doubleValue();
                    return new DefaultTuple(value, score);
                },
                key, timeoutInSeconds);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Tuple zPopMax(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZPOPMAX",
                (Map<?, ?>  glideResult) -> {
                    if (glideResult == null || glideResult.isEmpty()) {
                        return null;
                    }
                    Map.Entry<?, ?> entry = glideResult.entrySet().iterator().next();
                    byte[] value = ((GlideString) entry.getKey()).getBytes();
                    Double score = ((Number) entry.getValue()).doubleValue();
                    return new DefaultTuple(value, score);
                },
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zPopMax(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZPOPMAX",
                (Object glideResult) -> glideResult != null ? convertToTupleSetForPop(glideResult, false) : null,
                key, count);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Tuple bZPopMax(byte[] key, long timeout, TimeUnit unit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(unit, "TimeUnit must not be null");
        
        try {
            long timeoutInSeconds = unit.toSeconds(timeout);
            
            return connection.execute("BZPOPMAX",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    byte[] value = ((GlideString) glideResult[1]).getBytes();
                    Double score = ((Number) glideResult[2]).doubleValue();
                    return new DefaultTuple(value, score);
                },
                key, timeoutInSeconds);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zCard(byte[] key) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZCARD",
                (Long glideResult) -> glideResult,
                key);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Double zScore(byte[] key, byte[] value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(value, "Value must not be null");
        
        try {
            return connection.execute("ZSCORE",
                (Double glideResult) -> glideResult,
                key, value);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Double> zMScore(byte[] key, byte[]... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(values, "Values must not be null");
        Assert.noNullElements(values, "Values must not contain null elements");
        
        try {
            Object[] args = new Object[values.length + 1];
            args[0] = key;
            System.arraycopy(values, 0, args, 1, values.length);
            
            return connection.execute("ZMSCORE",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    List<Double> resultList = new ArrayList<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultList.add((Double) item);
                    }
                    return resultList;
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRemRange(byte[] key, long start, long end) {
        Assert.notNull(key, "Key must not be null");
        
        try {
            return connection.execute("ZREMRANGEBYRANK",
                (Long glideResult) -> glideResult,
                key, start, end);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRemRangeByLex(byte[] key, org.springframework.data.domain.Range<byte[]> range) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        
        try {
            return connection.execute("ZREMRANGEBYLEX",
                (Long glideResult) -> glideResult,
                key, 
                formatLexRangeBound(range.getLowerBound(), true), 
                formatLexRangeBound(range.getUpperBound(), false));
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRemRangeByScore(byte[] key, org.springframework.data.domain.Range<? extends Number> range) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        
        try {
            return connection.execute("ZREMRANGEBYSCORE",
                (Long glideResult) -> glideResult,
                key, 
                formatRangeBound(range.getLowerBound(), true), 
                formatRangeBound(range.getUpperBound(), false));
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zDiff(byte[]... sets) {
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            return connection.execute("ZDIFF",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zDiffWithScores(byte[]... sets) {
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            commandArgs.add("WITHSCORES");
            
            return connection.execute("ZDIFF",
                (Map<?, ?>  glideResult) -> {
                    if (glideResult == null || glideResult.isEmpty()) {
                        return null;
                    }

                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Map.Entry<?, ?> entry : glideResult.entrySet()) {
                        byte[] value = ((GlideString) entry.getKey()).getBytes();
                        Double score = ((Number) entry.getValue()).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zDiffStore(byte[] destKey, byte[]... sets) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(destKey);
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            return connection.execute("ZDIFFSTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zInter(byte[]... sets) {
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            return connection.execute("ZINTER",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zInterWithScores(byte[]... sets) {
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            commandArgs.add("WITHSCORES");
            
            return connection.execute("ZINTER",
                (Map<?, ?>  glideResult) -> {
                    if (glideResult == null || glideResult.isEmpty()) {
                        return null;
                    }

                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Map.Entry<?, ?> entry : glideResult.entrySet()) {
                        byte[] value = ((GlideString) entry.getKey()).getBytes();
                        Double score = ((Number) entry.getValue()).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zInterWithScores(Aggregate aggregate, Weights weights, byte[]... sets) {
        Assert.notNull(aggregate, "Aggregate must not be null");
        Assert.notNull(weights, "Weights must not be null");
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            // Add WEIGHTS
            commandArgs.add("WEIGHTS");
            for (double weight : weights.toArray()) {
                commandArgs.add(weight);
            }
            
            // Add AGGREGATE
            commandArgs.add("AGGREGATE");
            commandArgs.add(aggregate.name());
            
            commandArgs.add("WITHSCORES");
            
            return connection.execute("ZINTER",
                (Map<?, ?>  glideResult) -> {
                    if (glideResult == null || glideResult.isEmpty()) {
                        return null;
                    }

                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Map.Entry<?, ?> entry : glideResult.entrySet()) {
                        byte[] value = ((GlideString) entry.getKey()).getBytes();
                        Double score = ((Number) entry.getValue()).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zInterStore(byte[] destKey, byte[]... sets) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(destKey);
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            return connection.execute("ZINTERSTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zInterStore(byte[] destKey, Aggregate aggregate, Weights weights, byte[]... sets) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(aggregate, "Aggregate must not be null");
        Assert.notNull(weights, "Weights must not be null");
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(destKey);
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            // Add WEIGHTS
            commandArgs.add("WEIGHTS");
            for (double weight : weights.toArray()) {
                commandArgs.add(weight);
            }
            
            // Add AGGREGATE
            commandArgs.add("AGGREGATE");
            commandArgs.add(aggregate.name());
            
            return connection.execute("ZINTERSTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zUnion(byte[]... sets) {
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            return connection.execute("ZUNION",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zUnionWithScores(byte[]... sets) {
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            commandArgs.add("WITHSCORES");
            
            return connection.execute("ZUNION",
                (Map<?, ?>  glideResult) -> {
                    if (glideResult == null || glideResult.isEmpty()) {
                        return null;
                    }

                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Map.Entry<?, ?> entry : glideResult.entrySet()) {
                        byte[] value = ((GlideString) entry.getKey()).getBytes();
                        Double score = ((Number) entry.getValue()).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<Tuple> zUnionWithScores(Aggregate aggregate, Weights weights, byte[]... sets) {
        Assert.notNull(aggregate, "Aggregate must not be null");
        Assert.notNull(weights, "Weights must not be null");
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            // Add WEIGHTS
            commandArgs.add("WEIGHTS");
            for (double weight : weights.toArray()) {
                commandArgs.add(weight);
            }
            
            // Add AGGREGATE
            commandArgs.add("AGGREGATE");
            commandArgs.add(aggregate.name());
            
            commandArgs.add("WITHSCORES");
            
            return connection.execute("ZUNION",
                (Map<?, ?>  glideResult) -> {
                    if (glideResult == null || glideResult.isEmpty()) {
                        return null;
                    }

                    Set<Tuple> resultSet = new LinkedHashSet<>();
                    for (Map.Entry<?, ?> entry : glideResult.entrySet()) {
                        byte[] value = ((GlideString) entry.getKey()).getBytes();
                        Double score = ((Number) entry.getValue()).doubleValue();
                        resultSet.add(new DefaultTuple(value, score));
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zUnionStore(byte[] destKey, byte[]... sets) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(destKey);
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            return connection.execute("ZUNIONSTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zUnionStore(byte[] destKey, Aggregate aggregate, Weights weights, byte[]... sets) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(aggregate, "Aggregate must not be null");
        Assert.notNull(weights, "Weights must not be null");
        Assert.notNull(sets, "Sets must not be null");
        Assert.noNullElements(sets, "Sets must not contain null elements");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(destKey);
            commandArgs.add(sets.length);
            for (byte[] set : sets) {
                commandArgs.add(set);
            }
            
            // Add WEIGHTS
            commandArgs.add("WEIGHTS");
            for (double weight : weights.toArray()) {
                commandArgs.add(weight);
            }
            
            // Add AGGREGATE
            commandArgs.add("AGGREGATE");
            commandArgs.add(aggregate.name());
            
            return connection.execute("ZUNIONSTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zRangeByLex(byte[] key, org.springframework.data.domain.Range<byte[]> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            commandArgs.add(formatLexRangeBound(range.getLowerBound(), true));
            commandArgs.add(formatLexRangeBound(range.getUpperBound(), false));
            
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZRANGEBYLEX",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Set<byte[]> zRevRangeByLex(byte[] key, org.springframework.data.domain.Range<byte[]> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            commandArgs.add(formatLexRangeBound(range.getUpperBound(), false));
            commandArgs.add(formatLexRangeBound(range.getLowerBound(), true));
            
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZREVRANGEBYLEX",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    Set<byte[]> resultSet = new LinkedHashSet<>(glideResult.length);
                    for (Object item : glideResult) {
                        resultSet.add(((GlideString) item).getBytes());
                    }
                    return resultSet;
                },
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRangeStoreByLex(byte[] dstKey, byte[] srcKey, org.springframework.data.domain.Range<byte[]> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(dstKey, "Destination key must not be null");
        Assert.notNull(srcKey, "Source key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(dstKey);
            commandArgs.add(srcKey);
            commandArgs.add(formatLexRangeBound(range.getLowerBound(), true));
            commandArgs.add(formatLexRangeBound(range.getUpperBound(), false));
            commandArgs.add("BYLEX");
            
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZRANGESTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRangeStoreRevByLex(byte[] dstKey, byte[] srcKey, org.springframework.data.domain.Range<byte[]> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(dstKey, "Destination key must not be null");
        Assert.notNull(srcKey, "Source key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(dstKey);
            commandArgs.add(srcKey);
            commandArgs.add(formatLexRangeBound(range.getUpperBound(), false));
            commandArgs.add(formatLexRangeBound(range.getLowerBound(), true));
            commandArgs.add("BYLEX");
            commandArgs.add("REV");
            
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZRANGESTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRangeStoreByScore(byte[] dstKey, byte[] srcKey, 
            org.springframework.data.domain.Range<? extends Number> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(dstKey, "Destination key must not be null");
        Assert.notNull(srcKey, "Source key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(dstKey);
            commandArgs.add(srcKey);
            commandArgs.add(formatRangeBound(range.getLowerBound(), true));
            commandArgs.add(formatRangeBound(range.getUpperBound(), false));
            commandArgs.add("BYSCORE");
            
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZRANGESTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long zRangeStoreRevByScore(byte[] dstKey, byte[] srcKey, 
            org.springframework.data.domain.Range<? extends Number> range,
            io.valkey.springframework.data.valkey.connection.Limit limit) {
        Assert.notNull(dstKey, "Destination key must not be null");
        Assert.notNull(srcKey, "Source key must not be null");
        Assert.notNull(range, "Range must not be null");
        Assert.notNull(limit, "Limit must not be null");
        
        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(dstKey);
            commandArgs.add(srcKey);
            commandArgs.add(formatRangeBound(range.getUpperBound(), true));
            commandArgs.add(formatRangeBound(range.getLowerBound(), false));
            commandArgs.add("BYSCORE");
            commandArgs.add("REV");
            
            if (limit.isLimited()) {
                commandArgs.add("LIMIT");
                commandArgs.add(limit.getOffset());
                commandArgs.add(limit.getCount());
            }
            
            return connection.execute("ZRANGESTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public Cursor<Tuple> zScan(byte[] key, ScanOptions options) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(options, "ScanOptions must not be null");
       return new ValkeyGlideZSetScanCursor(key, options, connection);
    }

    // ==================== Helper Methods ====================

    /**
     * Unified method to convert pop operation results to tuple sets with configurable sort order.
     * 
     * @param result the result from ZPOPMIN/ZPOPMAX operation
     * @param ascending true for ascending order (lowest first), false for descending order (highest first)
     * @return set of tuples in the specified order
     */
    private Set<Tuple> convertToTupleSetForPop(Object result, boolean ascending) {
        Object converted = ValkeyGlideConverters.defaultFromGlideResult(result);
        List<Tuple> tuples = new ArrayList<>();
        
        // Handle HashMap format returned by Valkey-Glide for pop operations
        Map<?, ?> map = (Map<?, ?>) converted;
        
        // Convert map entries to tuples
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            byte[] value = (byte[]) entry.getKey();
            Double score = ((Number) entry.getValue()).doubleValue();
            tuples.add(new DefaultTuple(value, score));
        }
        
        // Sort tuples based on the specified order
        tuples.sort(ascending ? 
            (t1, t2) -> Double.compare(t1.getScore(), t2.getScore()) :  // ascending: lowest first
            (t1, t2) -> Double.compare(t2.getScore(), t1.getScore()));   // descending: highest first
        
        // Convert to LinkedHashSet to maintain order
        Set<Tuple> resultSet = new LinkedHashSet<>();
        resultSet.addAll(tuples);
        
        return resultSet;
    }

    private String formatRangeBound(org.springframework.data.domain.Range.Bound<? extends Number> bound, boolean isLowerBound) {
        if (bound == null || !bound.getValue().isPresent()) {
            return isLowerBound ? "-inf" : "+inf";
        }
        
        String value = bound.getValue().get().toString();
        return bound.isInclusive() ? value : "(" + value;
    }

    private String formatLexRangeBound(org.springframework.data.domain.Range.Bound<?> bound, boolean isLowerBound) {
        if (bound == null || !bound.getValue().isPresent()) {
            return isLowerBound ? "-" : "+";
        }
        
        Object val = bound.getValue().get();
        String value;
        if (val instanceof byte[]) {
            value = new String((byte[]) val, StandardCharsets.UTF_8);
        } else if (val instanceof String) {
            value = (String) val;
        } else {
            value = val.toString();
        }
        
        return bound.isInclusive() ? "[" + value : "(" + value;
    }

    /**
     * Simple implementation of Cursor for ZSCAN operation.
     */
    private static class ValkeyGlideZSetScanCursor implements Cursor<Tuple> {
        
        private final byte[] key;
        private final ScanOptions options;
        private final ValkeyGlideConnection connection;
        private long cursor = 0;
        private List<Tuple> tuples = new ArrayList<>();
        private int currentIndex = 0;
        private boolean finished = false;
        
        public ValkeyGlideZSetScanCursor(byte[] key, ScanOptions options, ValkeyGlideConnection connection) {
            this.key = key;
            this.options = options;
            this.connection = connection;
            scanNext();
        }
        
        @Override
        public boolean hasNext() {
            if (currentIndex < tuples.size()) {
                return true;
            }
            if (finished) {
                return false;
            }
            scanNext();
            return currentIndex < tuples.size();
        }
        
        @Override
        public Tuple next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            return tuples.get(currentIndex++);
        }
        
        private void scanNext() {
            if (connection.isQueueing() || connection.isPipelined()) {
                throw new InvalidDataAccessApiUsageException("'ZSCAN' cannot be called in pipeline / transaction mode");
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
                
                Object result = connection.execute("ZSCAN", rawResult -> ValkeyGlideConverters.defaultFromGlideResult(rawResult), args.toArray());
                Object converted = ValkeyGlideConverters.defaultFromGlideResult(result);
                
                if (converted == null) {
                    finished = true;
                    return;
                }
                
                List<Object> scanResult = convertToList(converted);
                if (scanResult.size() >= 2) {
                    // First element is the cursor, second is the result array
                    Object cursorObj = ValkeyGlideConverters.defaultFromGlideResult(scanResult.get(0));
                    if (cursorObj instanceof String) {
                        cursor = Long.parseLong((String) cursorObj);
                    } else if (cursorObj instanceof Number) {
                        cursor = ((Number) cursorObj).longValue();
                    } else if (cursorObj instanceof byte[]) {
                        cursor = Long.parseLong(new String((byte[]) cursorObj));
                    } else {
                        cursor = Long.parseLong(cursorObj.toString());
                    }
                    
                    if (cursor == 0) {
                        finished = true;
                    }
                    
                    Object resultArray = scanResult.get(1);
                    if (resultArray != null) {
                        List<Object> tupleList = convertToList(resultArray);
                        tuples.clear();
                        currentIndex = 0;
                        
                        for (int i = 0; i < tupleList.size(); i += 2) {
                            if (i + 1 < tupleList.size()) {
                                byte[] value = (byte[]) ValkeyGlideConverters.defaultFromGlideResult(tupleList.get(i));
                                Double score = parseScore(ValkeyGlideConverters.defaultFromGlideResult(tupleList.get(i + 1)));
                                tuples.add(new DefaultTuple(value, score));
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                finished = true;
                throw new ValkeyGlideExceptionConverter().convert(ex);
            }
        }
        
        @Override
        public void close() {
            finished = true;
        }
        
        @Override
        public boolean isClosed() {
            return finished;
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
        public Cursor.CursorId getId() {
            return Cursor.CursorId.of(cursor);
        }
        
        // Helper methods for convertToList and parseScore
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
            } else {
                throw new IllegalArgumentException("Cannot convert " + obj.getClass() + " to List");
            }
        }
        
        private Double parseScore(Object obj) {
            if (obj instanceof Number) {
                return ((Number) obj).doubleValue();
            } else if (obj instanceof String) {
                return Double.parseDouble((String) obj);
            } else if (obj instanceof byte[]) {
                return Double.parseDouble(new String((byte[]) obj));
            } else {
                throw new IllegalArgumentException("Cannot parse score from " + obj.getClass());
            }
        }
    }
}
