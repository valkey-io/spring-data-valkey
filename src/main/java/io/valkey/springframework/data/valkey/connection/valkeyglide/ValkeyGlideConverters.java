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

import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Collection;
import java.util.HashMap;

import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.SortParameters;
import io.valkey.springframework.data.valkey.connection.ValueEncoding;

import glide.api.models.GlideString;

/**
 * Converter utilities for mapping between Spring Data Valkey types and Valkey-Glide types.
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public abstract class ValkeyGlideConverters {

    /**
     * A functional interface used to convert raw driver results
     * (returned as {@link I}) into a strongly typed result {@code R}.
     *
     * <p>This is intended to be supplied by the higher-level API
     * that knows the correct decoding strategy for a specific Valkey command.
     *
     * @param <I> The type of the raw driver result.
     * @param <R> The target type that the raw driver result should be mapped to.
     */
    @FunctionalInterface
    public interface ResultMapper<I, R> {
        /**
         * Maps the raw result object of type {@code I} returned by the driver into
         * a strongly typed value of type {@code R}.
         *
         * @param item The raw driver result, typically a low-level
         *                     representation like Number, byte[], or List<Object>.
         * @return The mapped, higher-level type I.
         */
        R map(I item);
    }

    /**
     * Convert a Spring Data Valkey command argument to the corresponding Valkey-Glide format.
     *
     * @param arg The command argument to convert
     * @return The converted argument
     */
    public static Object toGlideArgument(Object arg) {
        if (arg == null) {
            return null;
        }
        
        if (arg instanceof byte[]) {
            // Convert byte array to ByteBuffer as Glide might expect ByteBuffer
            return ByteBuffer.wrap((byte[]) arg);
        } else if (arg instanceof Map) {
            // Handle Map conversions if needed
            return arg;
        } else if (arg instanceof Collection) {
            // Handle Collection conversions if needed
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<?>) arg) {
                result.add(toGlideArgument(item));
            }
            return result;
        } else if (arg instanceof Number || arg instanceof Boolean || arg instanceof String) {
            // Simple types can be passed as-is
            return arg;
        }
        
        // Default: return as is
        return arg;
    }

    /**
     * Convert a Valkey-Glide result to the Spring Data Valkey format using best-fit.
     *
     * @param result The Glide result to convert
     * @return The converted result
     */
    @Nullable
    public static Object defaultFromGlideResult(@Nullable Object result) {
        if (result == null) {
            return null;
        }
        
        if (result instanceof GlideString) {
            GlideString glideResult = (GlideString) result;
            // Handle special Valkey responses - check if GlideString represents "OK"
            if ("OK".equals(glideResult.toString())) {
                // Valkey SET, SETEX, PSETEX commands return "OK" on success, 
                // but Spring Data Valkey expects Boolean true
                return Boolean.TRUE;
            }
            // Convert GlideString back to byte[]
            return glideResult.getBytes();
        } else if (result instanceof ByteBuffer) {
            // Convert ByteBuffer back to byte[]
            ByteBuffer buffer = (ByteBuffer) result;
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } else if (result instanceof List) {
            // Convert list elements recursively
            List<?> list = (List<?>) result;
            List<Object> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                converted.add(defaultFromGlideResult(item));
            }
            return converted;
        } else if (result instanceof Set) {
            // Convert set elements recursively, but return as Set
            Set<?> set = (Set<?>) result;
            Set<Object> converted = new HashSet<>(set.size());
            for (Object item : set) {
                converted.add(defaultFromGlideResult(item));
            }
            return converted;
        } else if (result instanceof Object[]) {
            // Convert array elements recursively
            Object[] array = (Object[]) result;
            List<Object> converted = new ArrayList<>(array.length);
            for (Object item : array) {
                converted.add(defaultFromGlideResult(item));
            }
            return converted;
        } else if (result instanceof Map) {
            // Convert map entries recursively
            Map<?, ?> map = (Map<?, ?>) result;
            Map<Object, Object> converted = new java.util.HashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(defaultFromGlideResult(entry.getKey()), defaultFromGlideResult(entry.getValue()));
            }
            return converted;
        } else if (result instanceof String) {
            // Handle special Valkey responses
            String strResult = (String) result;
            if ("OK".equals(strResult)) {
                // Valkey SET, SETEX, PSETEX commands return "OK" on success, 
                // but Spring Data Valkey expects Boolean true
                return Boolean.TRUE;
            }
            // Other strings should be converted to byte[] for Spring Data Valkey compatibility
            // This handles pipeline/transaction mode where Glide returns String instead of GlideString
            return strResult.getBytes(StandardCharsets.UTF_8);
        } else if (result instanceof Number || result instanceof Boolean) {
            // Simple types can be passed as-is
            return result;
        } else if (result instanceof byte[]) {
            // byte[] should be passed as-is (already in Spring format)
            return result;
        }
        
        // Default: return as is
        return result;
    }
    
    /**
     * Convert a byte array to a string using the UTF-8 charset.
     *
     * @param bytes The bytes to convert
     * @return The resulting string or null if input is null
     */
    @Nullable
    public static String toString(@Nullable byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Convert a string to a byte array using the UTF-8 charset.
     *
     * @param string The string to convert
     * @return The resulting byte array or null if input is null
     */
    @Nullable
    public static byte[] toBytes(@Nullable String string) {
        return string == null ? null : string.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convert a Valkey command result to a Boolean value.
     * Valkey SET command returns "OK" on success, null on failure with conditions.
     * Valkey conditional commands return 1/0 for true/false.
     *
     * @param result The command result
     * @return Boolean representation of the result
     */
    @Nullable
    public static Boolean stringToBoolean(@Nullable Object result) {
        if (result == null) {
            return null;
        }
        return "OK".equals(result);
    }

    /**
     * Convert a numeric Valkey command result to a Boolean value.
     * Valkey conditional commands return 1/0 for true/false.
     *
     * @param result The numeric command result
     * @return Boolean representation of the result
     */
    @Nullable
    public static Boolean numberToBoolean(@Nullable Object result) {
        return result != null ? ((Number) result).longValue() != 0 : null;
    }

    /**
     * Convert Valkey TYPE command result to DataType enum.
     *
     * @param result The TYPE command result (GlideString is converted to byte[])
     * @return The corresponding DataType
     */
    public static DataType toDataType(@Nullable byte[] result) {
        if (result == null) {
            return DataType.NONE;
        }
        

        // Convert byte[] to String using UTF-8 encoding
        String resultStr = new String(result, StandardCharsets.UTF_8);
        switch (resultStr.toLowerCase()) {
            case "string":
                return DataType.STRING;
            case "list":
                return DataType.LIST;
            case "set":
                return DataType.SET;
            case "zset":
                return DataType.ZSET;
            case "hash":
                return DataType.HASH;
            case "stream":
                return DataType.STREAM;
            case "none":
            default:
                return DataType.NONE;
        }
    }

    /**
     * Convert result to Set<byte[]>.
     *
     * @param result The command result
     * @return Set of byte arrays
     */
    @Nullable
    public static Set<byte[]> toBytesSet(@Nullable Object[] glideResult) {
        if (glideResult == null) {
            return new HashSet<>();
        }

        Set<byte[]> resultSet = new HashSet<>(glideResult.length);
        for (Object item : glideResult) {
            GlideString glideString = (GlideString) item;
            resultSet.add(glideString != null ? glideString.getBytes() : null);
        }
        return resultSet;
        
        // if (result instanceof Collection) {
        //     Set<byte[]> set = new HashSet<>();
        //     for (Object item : (Collection<?>) result) {
        //         if (item instanceof byte[]) {
        //             set.add((byte[]) item);
        //         } else if (item instanceof String) {
        //             set.add(((String) item).getBytes(StandardCharsets.UTF_8));
        //         } else if (item != null) {
        //             set.add(item.toString().getBytes(StandardCharsets.UTF_8));
        //         }
        //     }
        //     return set;
        // }
        
        // Handle array types (Object[] and specific array types)
        // if (result instanceof Object[]) {
        //     Set<byte[]> set = new HashSet<>();
        //     for (Object item : (Object[]) result) {
        //         if (item instanceof byte[]) {
        //             set.add((byte[]) item);
        //         } else if (item instanceof String) {
        //             set.add(((String) item).getBytes(StandardCharsets.UTF_8));
        //         } else if (item != null) {
        //             set.add(item.toString().getBytes(StandardCharsets.UTF_8));
        //         }
        //     }
        //     return set;
        // }
        
        // // Handle byte[][] specifically
        // if (result instanceof byte[][]) {
        //     Set<byte[]> set = new HashSet<>();
        //     for (byte[] item : (byte[][]) result) {
        //         if (item != null) {
        //             set.add(item);
        //         }
        //     }
        //     return set;
        // }
        
        // // Handle String[] specifically
        // if (result instanceof String[]) {
        //     Set<byte[]> set = new HashSet<>();
        //     for (String item : (String[]) result) {
        //         if (item != null) {
        //             set.add(item.getBytes(StandardCharsets.UTF_8));
        //         }
        //     }
        //     return set;
        // }
        
        // return new HashSet<>();
    }

    @Nullable
    public static Set<byte[]> toBytesSet(@Nullable Object result) {
        if (result == null) {
            return new HashSet<>();
        }
        
        if (result instanceof Collection) {
            Set<byte[]> set = new HashSet<>();
            for (Object item : (Collection<?>) result) {
                if (item instanceof byte[]) {
                    set.add((byte[]) item);
                } else if (item instanceof String) {
                    set.add(((String) item).getBytes(StandardCharsets.UTF_8));
                } else if (item != null) {
                    set.add(item.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            return set;
        }
        
        // Handle array types (Object[] and specific array types)
        if (result instanceof Object[]) {
            Set<byte[]> set = new HashSet<>();
            for (Object item : (Object[]) result) {
                if (item instanceof byte[]) {
                    set.add((byte[]) item);
                } else if (item instanceof String) {
                    set.add(((String) item).getBytes(StandardCharsets.UTF_8));
                } else if (item != null) {
                    set.add(item.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            return set;
        }
        
        // Handle byte[][] specifically
        if (result instanceof byte[][]) {
            Set<byte[]> set = new HashSet<>();
            for (byte[] item : (byte[][]) result) {
                if (item != null) {
                    set.add(item);
                }
            }
            return set;
        }
        
        // Handle String[] specifically
        if (result instanceof String[]) {
            Set<byte[]> set = new HashSet<>();
            for (String item : (String[]) result) {
                if (item != null) {
                    set.add(item.getBytes(StandardCharsets.UTF_8));
                }
            }
            return set;
        }
        
        return new HashSet<>();
    }

    @Nullable
    public static List<Map.Entry<byte[], byte[]>> toMapEntriesList(@Nullable Object glideResult) {
        if (glideResult == null) {
            return null;
        }

        Object[] result = (Object[]) glideResult;
        if (result.length == 0) {
            return null;
        }

        List<Map.Entry<byte[], byte[]>> resultList = new ArrayList<>();
        for (Object item : result) {
            Object[] keyValuePair = (Object[]) item;
            GlideString keyObj = (GlideString) keyValuePair[0];
            GlideString valueObj = (GlideString) keyValuePair[1];
            resultList.add(new HashMap.SimpleEntry<>(keyObj.getBytes(), valueObj.getBytes()));
        }
        return resultList;
    }

    /**
     * Convert result to List<byte[]>.
     *
     * @param result The command result
     * @return List of byte arrays
     */
    @Nullable
    public static List<byte[]> toBytesList(@Nullable Object[] glideResult) {
        if (glideResult == null) {
            return new ArrayList<>();
        }

        List<byte[]> convertedList = new ArrayList<>(glideResult.length);
        for (Object item : glideResult) {
            GlideString glideString = (GlideString) item;
            convertedList.add(glideString != null ? glideString.getBytes() : null);
        }
        return convertedList;
    }

    @Nullable
    public static List<Long> toLongsList(@Nullable Object[] glideResult) {
        if (glideResult == null) {
            return null;
        }

        List<Long> resultList = new ArrayList<>(glideResult.length);
        for (Object item : glideResult) {
            resultList.add(((Number) item).longValue());
        }
        return resultList;
    }

    @Nullable
    public static List<Boolean> toBooleansList(@Nullable Object[] glideResult) {
        if (glideResult == null) {
            return null;
        }

        List<Boolean> resultList = new ArrayList<>(glideResult.length);
        for (Object item : glideResult) {
            resultList.add((Boolean) item);
        }
        return resultList;
    }

    @Nullable
    public static List<byte[]> toBytesList(@Nullable Object result) {
        if (result == null) {
            return new ArrayList<>();
        }
        
        if (result instanceof Collection) {
            List<byte[]> list = new ArrayList<>();
            for (Object item : (Collection<?>) result) {
                if (item instanceof byte[]) {
                    list.add((byte[]) item);
                } else if (item instanceof String) {
                    list.add(((String) item).getBytes(StandardCharsets.UTF_8));
                } else if (item != null) {
                    list.add(item.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            return list;
        }
        
        // Handle array types (Object[] and specific array types)
        if (result instanceof Object[]) {
            List<byte[]> list = new ArrayList<>();
            for (Object item : (Object[]) result) {
                if (item instanceof byte[]) {
                    list.add((byte[]) item);
                } else if (item instanceof GlideString) {
                    // Handle GlideString properly - use getBytes() to preserve binary data
                    list.add(((GlideString) item).getBytes());
                } else if (item instanceof String) {
                    list.add(((String) item).getBytes(StandardCharsets.UTF_8));
                } else if (item != null) {
                    list.add(item.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            return list;
        }
        
        // Handle byte[][] specifically
        if (result instanceof byte[][]) {
            List<byte[]> list = new ArrayList<>();
            for (byte[] item : (byte[][]) result) {
                if (item != null) {
                    list.add(item);
                }
            }
            return list;
        }
        
        // Handle String[] specifically
        if (result instanceof String[]) {
            List<byte[]> list = new ArrayList<>();
            for (String item : (String[]) result) {
                if (item != null) {
                    list.add(item.getBytes(StandardCharsets.UTF_8));
                }
            }
            return list;
        }
        
        return new ArrayList<>();
    }

    @Nullable
    public static Map<byte[],  byte[]> toBytesMap(@Nullable Object glideResult) {
        if (glideResult == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<GlideString, GlideString> coverted = (Map<GlideString, GlideString>) glideResult;
        Map<byte[], byte[]> resultMap = new HashMap<>(coverted.size());
        for (Map.Entry<GlideString, GlideString> entry : coverted.entrySet()) {
            resultMap.put(entry.getKey().getBytes(), entry.getValue().getBytes());
        }
        return resultMap;
    }

    @Nullable
    public static Map.Entry<byte[],  byte[]> toBytesMapEntry(@Nullable Object glideResult) {
        if (glideResult == null) {
            return null;
        }

        Object[] result = (Object[]) glideResult;
        if (result.length == 0) {
            return null;
        }

        Object[] keyValuePair = (Object[]) result[0];

        GlideString keyObj = (GlideString) keyValuePair[0];
        GlideString valueObj = (GlideString) keyValuePair[1];
        return new HashMap.SimpleEntry<>(keyObj.getBytes(), valueObj.getBytes());
    }
    /**
     * Append sort parameters to the command arguments.
     *
     * @param args The command arguments list
     * @param params The sort parameters
     */
    public static void appendSortParameters(List<Object> args, SortParameters params) {
        if (params == null) {
            return;
        }
        
        // Add BY pattern if specified
        if (params.getByPattern() != null) {
            args.add("BY");
            args.add(params.getByPattern());
        }
        
        // Add LIMIT if specified
        if (params.getLimit() != null) {
            args.add("LIMIT");
            args.add(params.getLimit().getStart());
            args.add(params.getLimit().getCount());
        }
        
        // Add GET patterns if specified
        if (params.getGetPattern() != null) {
            for (byte[] pattern : params.getGetPattern()) {
                args.add("GET");
                args.add(pattern);
            }
        }
        
        // Add ORDER if specified
        if (params.getOrder() != null) {
            args.add(params.getOrder().name());
        }
        
        // Add ALPHA if specified - check for null safely
        Boolean isAlphabetic = params.isAlphabetic();
        if (isAlphabetic != null && isAlphabetic) {
            args.add("ALPHA");
        }
    }

    /**
     * Convert OBJECT ENCODING result to ValueEncoding.
     *
     * @param result The OBJECT ENCODING command result
     * @return The corresponding ValueEncoding
     */
    @Nullable
    public static ValueEncoding toValueEncoding(@Nullable GlideString glideResult) {
        if (glideResult == null) {
            return ValueEncoding.ValkeyValueEncoding.VACANT;
        }

        switch (glideResult.toString()) {
            case "raw":
                return ValueEncoding.ValkeyValueEncoding.RAW;
            case "int":
                return ValueEncoding.ValkeyValueEncoding.INT;
            case "hashtable":
                return ValueEncoding.ValkeyValueEncoding.HASHTABLE;
            case "zipmap":
            case "linkedlist":
                return ValueEncoding.ValkeyValueEncoding.LINKEDLIST;
            case "ziplist":
            case "listpack":
                return ValueEncoding.ValkeyValueEncoding.ZIPLIST;
            case "intset":
                return ValueEncoding.ValkeyValueEncoding.INTSET;
            case "skiplist":
                return ValueEncoding.ValkeyValueEncoding.SKIPLIST;
            case "embstr":
            case "quicklist":
            case "stream":
                return ValueEncoding.ValkeyValueEncoding.RAW;
            default:
                return ValueEncoding.ValkeyValueEncoding.VACANT;
        }
    }

    /**
     * Parses the TIME command response into a Long value in the specified TimeUnit.
     * 
     * @param result the result from the TIME command (Object[] from Glide)
     * @param timeUnit the desired time unit
     * @return the time in the specified unit
     */
    public static Long parseTimeResponse(Object[] result, TimeUnit timeUnit) {
        
        // TIME returns [GlideString seconds, GlideString microseconds] as Object[]
        Long seconds = Long.parseLong(((GlideString) result[0]).getString());
        Long microseconds = Long.parseLong(((GlideString) result[1]).getString());
        
        // Convert to milliseconds first
        long milliseconds = seconds * 1000 + microseconds / 1000;
        
        return timeUnit.convert(milliseconds, TimeUnit.MILLISECONDS);
    }
}
