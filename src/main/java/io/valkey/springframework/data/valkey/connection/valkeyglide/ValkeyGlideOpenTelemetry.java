package io.valkey.springframework.data.valkey.connection.valkeyglide;

import org.springframework.lang.Nullable;

public final class ValkeyGlideOpenTelemetry {

    // ---- DEFAULT VALUES (documented & reusable) ----
    public static final String DEFAULT_TRACES_ENDPOINT =
            "http://localhost:4318/v1/traces";
    public static final String DEFAULT_METRICS_ENDPOINT =
            "http://localhost:4318/v1/metrics";
    public static final int DEFAULT_SAMPLE_PERCENTAGE = 10;
    public static final long DEFAULT_FLUSH_INTERVAL_MS = 1000L;

    private final @Nullable String tracesEndpoint;
    private final @Nullable String metricsEndpoint;
    private final @Nullable Integer samplePercentage;
    private final @Nullable Long flushIntervalMs;

    private ValkeyGlideOpenTelemetry(Builder b) {
        this.tracesEndpoint = b.tracesEndpoint;
        this.metricsEndpoint = b.metricsEndpoint;
        this.samplePercentage = b.samplePercentage;
        this.flushIntervalMs = b.flushIntervalMs;
    }

    /** Default OpenTelemetry configuration (local collector, sampled, low flush interval). */
    public static ValkeyGlideOpenTelemetry defaults() {
        return builder()
            .tracesEndpoint(DEFAULT_TRACES_ENDPOINT)
            .metricsEndpoint(DEFAULT_METRICS_ENDPOINT)
            .samplePercentage(DEFAULT_SAMPLE_PERCENTAGE)
            .flushIntervalMs(DEFAULT_FLUSH_INTERVAL_MS)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public @Nullable String tracesEndpoint() { return tracesEndpoint; }
    public @Nullable String metricsEndpoint() { return metricsEndpoint; }
    public @Nullable Integer samplePercentage() { return samplePercentage; }
    public @Nullable Long flushIntervalMs() { return flushIntervalMs; }

    public static final class Builder {
        private @Nullable String tracesEndpoint;
        private @Nullable String metricsEndpoint;
        private @Nullable Integer samplePercentage;
        private @Nullable Long flushIntervalMs;

        private Builder() {}

        public Builder tracesEndpoint(@Nullable String v) {
            this.tracesEndpoint = v;
            return this;
        }

        public Builder metricsEndpoint(@Nullable String v) {
            this.metricsEndpoint = v;
            return this;
        }

        public Builder samplePercentage(@Nullable Integer v) {
            this.samplePercentage = v;
            return this;
        }

        public Builder flushIntervalMs(@Nullable Long v) {
            this.flushIntervalMs = v;
            return this;
        }

        public ValkeyGlideOpenTelemetry build() {
            return new ValkeyGlideOpenTelemetry(this);
        }
    }
}
