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

import org.springframework.dao.DataAccessException;
import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility methods for working with Valkey-Glide CompletableFuture objects.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public abstract class ValkeyGlideFutureUtils {

    /**
     * Get the result of a CompletableFuture, waiting for the specified timeout.
     *
     * @param <T> The type of the result
     * @param future The future to get the result from
     * @param timeout The timeout in milliseconds
     * @param exceptionConverter The exception converter to use for converting glide exceptions
     * @return The result of the future
     * @throws DataAccessException if an error occurs while getting the result
     */
    public static <T> T get(CompletableFuture<T> future, long timeout, ValkeyGlideExceptionConverter exceptionConverter) {
        try {
            return timeout > 0 ? future.get(timeout, TimeUnit.MILLISECONDS) : future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValkeyConnectionFailureException("Interrupted while waiting for Valkey response", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof Exception) {
                DataAccessException converted = exceptionConverter.convert((Exception) cause);
                if (converted != null) {
                    throw converted;
                }
            }
            throw new ValkeyConnectionFailureException("Error executing Valkey command", ex.getCause());
        } catch (TimeoutException ex) {
            throw new ValkeyConnectionFailureException("Valkey command timed out", ex);
        }
    }

    /**
     * Execute a CompletableFuture synchronously, waiting for the specified timeout.
     *
     * @param <T> The type of the result
     * @param supplier A supplier that returns a CompletableFuture
     * @param timeout The timeout in milliseconds
     * @param exceptionConverter The exception converter to use for converting glide exceptions
     * @return The result of the future
     * @throws DataAccessException if an error occurs while executing the future
     */
    public static <T> T execute(FutureSupplier<T> supplier, long timeout, ValkeyGlideExceptionConverter exceptionConverter) {
        try {
            CompletableFuture<T> future = supplier.get();
            return get(future, timeout, exceptionConverter);
        } catch (Exception ex) {
            DataAccessException converted = exceptionConverter.convert(ex);
            if (converted != null) {
                throw converted;
            }
            throw ex;
        }
    }

    /**
     * Functional interface for supplying a CompletableFuture.
     *
     * @param <T> The type of the result
     */
    @FunctionalInterface
    public interface FutureSupplier<T> {
        /**
         * Get a CompletableFuture.
         *
         * @return The future
         * @throws RuntimeException if an error occurs
         */
        CompletableFuture<T> get();
    }
}
