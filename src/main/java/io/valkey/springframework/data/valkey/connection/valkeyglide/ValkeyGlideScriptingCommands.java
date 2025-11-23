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
import java.util.List;

import io.valkey.springframework.data.valkey.connection.ValkeyScriptingCommands;
import io.valkey.springframework.data.valkey.connection.ReturnType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeyScriptingCommands} for Valkey-Glide.
 *
 * @author Ilya Kolomin
 * @since 2.0
 */
public class ValkeyGlideScriptingCommands implements ValkeyScriptingCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideScriptingCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideScriptingCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public <T> T eval(byte[] script, ReturnType returnType, int numKeys, byte[]... keysAndArgs) {
        Assert.notNull(script, "Script must not be null");
        Assert.notNull(returnType, "ReturnType must not be null");
        Assert.notNull(keysAndArgs, "Keys and args must not be null");
        
        try {
            Object[] args = new Object[2 + keysAndArgs.length];
            args[0] = script;
            args[1] = String.valueOf(numKeys);
            System.arraycopy(keysAndArgs, 0, args, 2, keysAndArgs.length);

            return connection.execute("EVAL",
                (Object glideResult) -> {
                    return convertResult(glideResult, returnType);
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public <T> T evalSha(String scriptSha1, ReturnType returnType, int numKeys, byte[]... keysAndArgs) {
        Assert.notNull(scriptSha1, "Script SHA1 must not be null");
        Assert.notNull(returnType, "ReturnType must not be null");
        Assert.notNull(keysAndArgs, "Keys and args must not be null");
        
        try {
            Object[] args = new Object[2 + keysAndArgs.length];
            args[0] = scriptSha1;
            args[1] = String.valueOf(numKeys);
            System.arraycopy(keysAndArgs, 0, args, 2, keysAndArgs.length);

            return connection.execute("EVALSHA",
                (Object glideResult) -> {
                    return convertResult(glideResult, returnType);
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public <T> T evalSha(byte[] scriptSha1, ReturnType returnType, int numKeys, byte[]... keysAndArgs) {
        Assert.notNull(scriptSha1, "Script SHA1 must not be null");
        return evalSha(new String(scriptSha1), returnType, numKeys, keysAndArgs);
    }

    @Override
    public void scriptFlush() {
        try {
            connection.execute("SCRIPT",
                glideResult -> glideResult, // Return "OK" for pipeline/transaction correlation
                "FLUSH");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Boolean> scriptExists(String... scriptSha1s) {
        Assert.notNull(scriptSha1s, "Script SHA1s must not be null");

        try {
            Object[] args = new Object[1 + scriptSha1s.length];
            args[0] = "EXISTS";
            System.arraycopy(scriptSha1s, 0, args, 1, scriptSha1s.length);

            return connection.execute("SCRIPT",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    List<Boolean> exists = new ArrayList<>(glideResult.length);
                    for (Object value : glideResult) {
                        exists.add((Boolean) value);
                    }
                    return exists;
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Nullable
    public List<Boolean> scriptExists(byte[]... scriptSha1s) {
        Assert.notNull(scriptSha1s, "Script SHA1s must not be null");
        
        String[] sha1s = new String[scriptSha1s.length];
        for (int i = 0; i < scriptSha1s.length; i++) {
            sha1s[i] = new String(scriptSha1s[i]);
        }
        return scriptExists(sha1s);
    }

    @Override
    @Nullable
    public String scriptLoad(byte[] script) {
        Assert.notNull(script, "Script must not be null");

        try {
            return connection.execute("SCRIPT",
                (GlideString glideResult) -> glideResult != null ? glideResult.toString() : null,
                "LOAD", script);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void scriptKill() {
        try {
            connection.execute("SCRIPT",
                (String glideResult) -> glideResult, // Return "OK" for pipeline/transaction correlation
                "KILL");
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> T convertResult(Object result, ReturnType returnType) {
        switch (returnType) {
            case BOOLEAN:
                // Lua false comes back as a null bulk reply
                if (result == null) {
                    return (T) Boolean.FALSE;
                }
                if (result instanceof Number) {
                    return (T) Boolean.valueOf(((Number) result).longValue() == 1L);
                }
                return (T) Boolean.valueOf("1".equals(result.toString()));
            case INTEGER:
                if (result instanceof Number) {
                    return (T) Long.valueOf(((Number) result).longValue());
                }
                return (T) Long.valueOf(result.toString());
            case STATUS:
                // STATUS should return String, following Jedis pattern
                if (result instanceof GlideString) {
                    return (T) new String(((GlideString) result).toString());
                }
                if (result instanceof byte[]) {
                    return (T) new String((byte[]) result, StandardCharsets.UTF_8);
                }
                return (T) result.toString();
            case VALUE:
                // VALUE should return byte[] for simple values, complex objects as-is
                if (result instanceof GlideString) {
                    return (T) ((GlideString) result).getBytes();
                }
                // if (result instanceof String) {
                //     return (T) ((String) result).getBytes(StandardCharsets.UTF_8);
                // }
                // Return complex objects (Maps, Lists, etc.) as-is
                // If caller expects byte[] but gets complex object, they'll get ClassCastException
                return (T) result;
            case MULTI:
                // Assume the result is Object[] for MULTI
                Object[] resultArray = (Object[]) result;
                Object[] convertedArray = new Object[resultArray.length];
                for (int i = 0; i < resultArray.length; i++) {
                    Object item = resultArray[i];
                    if (item instanceof GlideString) {
                        convertedArray[i] = ((GlideString) item).getBytes();
                    } else if (item instanceof String) {
                        convertedArray[i] = ((String) item).getBytes(StandardCharsets.UTF_8);
                    } else {
                        convertedArray[i] = item;
                    }
                }
                return (T) convertedArray;
            default:
                throw new IllegalArgumentException("Unsupported return type: " + returnType);
        }
    }
}
