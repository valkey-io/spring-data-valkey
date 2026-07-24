/*
 * Copyright 2011-present the original author or authors.
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.dao.InvalidDataAccessApiUsageException;

import glide.api.models.GlideString;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import io.valkey.springframework.data.valkey.connection.ClusterSlotHashUtil;
import io.valkey.springframework.data.valkey.connection.ValkeyKeyCommands;
import io.valkey.springframework.data.valkey.connection.SortParameters;
import io.valkey.springframework.data.valkey.core.Cursor;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Implementation of {@link ValkeyKeyCommands} for Valkey-Glide in cluster mode.
 * This implementation handles cross-slot operations by executing commands separately
 * and aggregating results when keys map to different cluster slots.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideClusterKeyCommands extends ValkeyGlideKeyCommands {

    private final ValkeyGlideClusterConnection connection;

    /**
     * Creates a new {@link ValkeyGlideClusterKeyCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideClusterKeyCommands(ValkeyGlideClusterConnection connection) {
        super(connection);
        Assert.notNull(connection, "ValkeyGlideClusterConnection must not be null!");
        this.connection = connection;
    }

    @Override
    public Cursor<byte[]> scan(ScanOptions options) {
        ScanOptions scanOptions = options != null ? options : ScanOptions.NONE;

        // Node-scoped cluster scan sets a one-shot route before calling this method.
        // Capture the route and pin it for the lifetime of the scan cursor so that
        // all pages go to the same node.
        if (connection.getClusterAdapter().hasOneShotRouteForNextCommand()) {
            return new NodeScopedScanCursor(connection, scanOptions);
        }

        return new ValkeyGlideClusterScanCursor(connection, scanOptions);
    }

    /**
     * Scan cursor that pins a specific node route for all pages.
     * Used when a node-scoped scan is requested via the cluster connection.
     */
    private static class NodeScopedScanCursor implements Cursor<byte[]> {

        private final ValkeyGlideClusterConnection connection;
        private final ScanOptions scanOptions;
        private final Route pinnedRoute;
        private String cursor = "0";
        private Iterator<byte[]> currentBatch = Collections.emptyIterator();
        private boolean finished = false;
        private boolean closed = false;
        private long position = 0;

        NodeScopedScanCursor(ValkeyGlideClusterConnection connection, ScanOptions scanOptions) {
            this.connection = connection;
            this.scanOptions = scanOptions;
            // Capture and consume the one-shot route so it doesn't leak
            this.pinnedRoute = connection.getClusterAdapter().consumeOneShotRoute();
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
        public CursorId getId() {
            return CursorId.of(cursor);
        }

        @Override
        public boolean hasNext() {
            if (closed) return false;
            if (currentBatch.hasNext()) return true;
            if (finished) return false;
            loadNextBatch();
            return currentBatch.hasNext();
        }

        @Override
        public byte[] next() {
            if (!hasNext()) throw new NoSuchElementException();
            position++;
            return currentBatch.next();
        }

        @Override
        public void close() {
            closed = true;
            finished = true;
            currentBatch = Collections.emptyIterator();
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        private void loadNextBatch() {
            try {
                List<Object> args = new ArrayList<>();
                args.add(cursor);

                if (scanOptions.getPattern() != null) {
                    args.add("MATCH");
                    args.add(scanOptions.getPattern());
                } else if (scanOptions.getBytePattern() != null) {
                    args.add("MATCH");
                    args.add(scanOptions.getBytePattern());
                }

                if (scanOptions.getCount() != null) {
                    args.add("COUNT");
                    args.add(scanOptions.getCount());
                }

                if (scanOptions instanceof io.valkey.springframework.data.valkey.core.KeyScanOptions kso && kso.getType() != null) {
                    args.add("TYPE");
                    args.add(kso.getType());
                }

                // Re-set the pinned route before each page
                connection.getClusterAdapter().setOneShotRouteForNextCommand(pinnedRoute);

                Object[] scanResult = connection.execute("SCAN",
                    (Object[] glideResult) -> glideResult,
                    args.toArray());

                if (scanResult != null && scanResult.length >= 2) {
                    Object nextCursor = scanResult[0];
                    if (nextCursor instanceof GlideString gs) {
                        cursor = gs.getString();
                    } else if (nextCursor instanceof byte[] b) {
                        cursor = new String(b, java.nio.charset.StandardCharsets.UTF_8);
                    } else {
                        cursor = nextCursor.toString();
                    }

                    if ("0".equals(cursor)) {
                        finished = true;
                    }

                    List<byte[]> keys = ValkeyGlideConverters.toBytesList(scanResult[1]);
                    currentBatch = keys.iterator();
                } else {
                    finished = true;
                    currentBatch = Collections.emptyIterator();
                }
            } catch (Exception ex) {
                throw new ValkeyGlideExceptionConverter().convert(ex);
            }
        }
    }

    private static class ValkeyGlideClusterScanCursor implements Cursor<byte[]> {

        private final ValkeyGlideClusterConnection connection;
        private final ScanOptions scanOptions;
        private ClusterScanCursor cursorState = ClusterScanCursor.initialCursor();
        private Iterator<byte[]> currentBatch = Collections.emptyIterator();
        private long position = 0;
        private boolean finished = false;
        private boolean closed = false;

        private ValkeyGlideClusterScanCursor(ValkeyGlideClusterConnection connection, ScanOptions scanOptions) {
            this.connection = connection;
            this.scanOptions = scanOptions;
        }

        @Override
        public long getCursorId() {
            return position;
        }

        @Override
        public CursorId getId() {
            return CursorId.of(String.valueOf(position));
        }

        @Override
        public boolean hasNext() {
            if (closed) {
                return false;
            }

            if (currentBatch.hasNext()) {
                return true;
            }

            while (!finished) {
                loadNextBatch();
                if (currentBatch.hasNext()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public byte[] next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return currentBatch.next();
        }

        @Override
        public void close() {
            closed = true;
            finished = true;
            currentBatch = Collections.emptyIterator();
            cursorState.releaseCursorHandle();
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        private void loadNextBatch() {
            if (connection.isQueueing() || connection.isPipelined()) {
                throw new InvalidDataAccessApiUsageException("'SCAN' cannot be called in pipeline / transaction mode");
            }

            try {
                ClusterGlideClientAdapter adapter = connection.getClusterAdapter();
                Object[] scanResult = hasScanOptions(scanOptions) ? adapter.scan(cursorState, toGlideScanOptions(scanOptions)) : adapter.scan(cursorState);

                if (scanResult == null || scanResult.length < 2 || !(scanResult[0] instanceof ClusterScanCursor)) {
                    finished = true;
                    currentBatch = Collections.emptyIterator();
                    return;
                }

                ClusterScanCursor previousCursor = cursorState;
                cursorState = (ClusterScanCursor) scanResult[0];
                previousCursor.releaseCursorHandle();
                finished = cursorState.isFinished();

                List<byte[]> keys = ValkeyGlideConverters.toBytesList(scanResult[1]);
                position += keys.size();
                currentBatch = keys.iterator();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while scanning cluster", ex);
            } catch (Exception ex) {
                throw new ValkeyGlideExceptionConverter().convert(ex);
            }
        }

        private static boolean hasScanOptions(ScanOptions options) {
            return options.getCount() != null || options.getPattern() != null
                    || options.getBytePattern() != null
                    || (options instanceof io.valkey.springframework.data.valkey.core.KeyScanOptions kso && kso.getType() != null);
        }

        private static glide.api.models.commands.scan.ScanOptions toGlideScanOptions(ScanOptions options) {
            var builder = glide.api.models.commands.scan.ScanOptions.builder();

            if (options.getBytePattern() != null) {
                builder.matchPatternBinary(GlideString.of(options.getBytePattern()));
            } else if (options.getPattern() != null) {
                builder.matchPattern(options.getPattern());
            }

            if (options.getCount() != null) {
                builder.count(options.getCount());
            }

            if (options instanceof io.valkey.springframework.data.valkey.core.KeyScanOptions kso && kso.getType() != null) {
                builder.type(glide.api.models.commands.scan.ScanOptions.ObjectType.valueOf(kso.getType().toUpperCase(java.util.Locale.ROOT)));
            }

            return builder.build();
        }
    }

    @Override
    public Boolean move(byte[] key, int dbIndex) {
        throw new InvalidDataAccessApiUsageException("Cluster mode does not allow moving keys");
    }

    @Override
    public void rename(byte[] oldKey, byte[] newKey) {

        Assert.notNull(oldKey, "Old key must not be null");
        Assert.notNull(newKey, "New key must not be null");

        if (ClusterSlotHashUtil.isSameSlotForAllKeys(oldKey, newKey)) {
            super.rename(oldKey, newKey);
            return;
        }

        if (connection.isPipelined() || connection.isQueueing()) {
            throw new InvalidDataAccessApiUsageException(
                    "Cross-slot RENAME cannot be used in pipeline or transaction mode");
        }

        byte[] value = dump(oldKey);

        if (value != null && value.length > 0) {

            restore(newKey, 0, value, true);
            del(oldKey);
        }
    }

    @Override
    public Boolean renameNX(byte[] sourceKey, byte[] targetKey) {

        Assert.notNull(sourceKey, "Source key must not be null");
        Assert.notNull(targetKey, "Target key must not be null");

        if (ClusterSlotHashUtil.isSameSlotForAllKeys(sourceKey, targetKey)) {
            return super.renameNX(sourceKey, targetKey);
        }

        if (connection.isPipelined() || connection.isQueueing()) {
            throw new InvalidDataAccessApiUsageException(
                    "Cross-slot RENAMENX cannot be used in pipeline or transaction mode");
        }

        byte[] value = dump(sourceKey);

        if (value != null && value.length > 0 && !exists(targetKey)) {

            restore(targetKey, 0, value);
            del(sourceKey);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    @Nullable
    public Long sort(byte[] key, SortParameters params, byte[] storeKey) {

        Assert.notNull(key, "Key must not be null");
        Assert.notNull(storeKey, "Store key must not be null");

        // Fast path: same slot
        if (ClusterSlotHashUtil.isSameSlotForAllKeys(key, storeKey)) {
            return super.sort(key, params, storeKey);
        }

        if (connection.isPipelined() || connection.isQueueing()) {
            throw new InvalidDataAccessApiUsageException(
                    "Cross-slot SORT STORE cannot be used in pipeline or transaction mode");
        }

        // Cross-slot path: sort without store, then manually store results
        List<byte[]> sorted = sort(key, params);
        if (sorted == null || sorted.isEmpty()) {
            connection.keyCommands().unlink(storeKey);
            return 0L;
        }

        byte[][] arr = new byte[sorted.size()][];
        connection.keyCommands().unlink(storeKey);
        connection.listCommands().lPush(storeKey, sorted.toArray(arr));
        return (long) sorted.size();
    }
}
