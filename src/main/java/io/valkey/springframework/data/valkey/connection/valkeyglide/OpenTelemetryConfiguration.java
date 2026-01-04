package io.valkey.springframework.data.valkey.connection.valkeyglide;

import org.springframework.lang.Nullable;

/**
 * Record representing OpenTelemetry configuration for Valkey-Glide client.
 *
 * @param tracesEndpoint     the OTLP endpoint for traces, or {@code null} if not set.
 * @param metricsEndpoint    the OTLP endpoint for metrics, or {@code null} if not set.
 * @param samplePercentage   the sampling percentage for traces, or {@code null} if not set.
 * @param flushIntervalMs    the flush interval in milliseconds, or {@code null} if not set.
 */
public record OpenTelemetryConfiguration(
        @Nullable String tracesEndpoint,
        @Nullable String metricsEndpoint,
        @Nullable Integer samplePercentage,
        @Nullable Long flushIntervalMs
) {}
