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

import io.valkey.springframework.data.valkey.connection.ValkeyHyperLogLogCommands;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Implementation of {@link ValkeyHyperLogLogCommands} for Valkey-Glide.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideHyperLogLogCommands implements ValkeyHyperLogLogCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideHyperLogLogCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideHyperLogLogCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public Long pfAdd(byte[] key, byte[]... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notEmpty(values, "PFADD requires at least one non 'null' value");
        Assert.noNullElements(values, "Values for PFADD must not contain 'null'");
        
        try {
            Object[] args = new Object[1 + values.length];
            args[0] = key;
            System.arraycopy(values, 0, args, 1, values.length);
            
            return connection.execute("PFADD",
                (Boolean glideResult) -> glideResult ? 1L : 0L,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long pfCount(byte[]... keys) {
        Assert.notEmpty(keys, "PFCOUNT requires at least one non 'null' key");
        Assert.noNullElements(keys, "Keys for PFCOUNT must not contain 'null'");
        
        try {
            Object[] args = new Object[keys.length];
            System.arraycopy(keys, 0, args, 0, keys.length);
            
            return connection.execute("PFCOUNT",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    public void pfMerge(byte[] destinationKey, byte[]... sourceKeys) {
        Assert.notNull(destinationKey, "Destination key must not be null");
        Assert.notNull(sourceKeys, "Source keys must not be null");
        Assert.noNullElements(sourceKeys, "Keys for PFMERGE must not contain 'null'");
        
        try {
            Object[] args = new Object[1 + sourceKeys.length];
            args[0] = destinationKey;
            System.arraycopy(sourceKeys, 0, args, 1, sourceKeys.length);
            
            connection.execute("PFMERGE",
                (String glideResult) -> glideResult, // Return the "OK" response for pipeline/transaction correlation
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }
}
