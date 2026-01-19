package io.valkey.springframework.data.valkey.connection.valkeyglide;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;

/**
 * Unit tests for OpenTelemetry configuration in Valkey-Glide integration.
 *
 * Important: Validation + "init once" logic lives in ValkeyGlideConnectionFactory#useOpenTelemetry,
 * not in ValkeyGlideClientConfigurationBuilder#useOpenTelemetry (builder only stores config).
 */
class ValkeyGlideOpenTelemetryConfigurationTests {

    private static final String TRACES_ENDPOINT = "http://localhost:4318/v1/traces";
    private static final String METRICS_ENDPOINT = "http://localhost:4318/v1/metrics";

    @BeforeEach
    void resetOtelInitializationState() {
        resetFactoryStaticOpenTelemetryState();
    }

    @Test
    void defaultsShouldReturnExpectedValues() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide defaults =
                ValkeyGlideClientConfiguration.OpenTelemetryForGlide.defaults();

        assertThat(defaults.tracesEndpoint()).isEqualTo(TRACES_ENDPOINT);
        assertThat(defaults.metricsEndpoint()).isEqualTo(METRICS_ENDPOINT);
        assertThat(defaults.samplePercentage()).isEqualTo(1);
        assertThat(defaults.flushIntervalMs()).isEqualTo(5000L);
    }

    @Test
    void shouldThrowExceptionWhenBothEndpointsAreNull() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(null, null, 10, 100L);

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg));
    }

    @Test
    void shouldInitializeWhenTracesOnlyIsProvided() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(TRACES_ENDPOINT, null, 10, 100L);

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatNoException().isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg));
    }

    @Test
    void shouldInitializeWhenMetricsOnlyIsProvided() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(null, METRICS_ENDPOINT, null, null);

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatNoException().isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg));
    }

    @Test
    void shouldNotFailWhenAlreadyInitializedWithSameConfig() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(
                        TRACES_ENDPOINT, METRICS_ENDPOINT, 10, 100L
                );

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatNoException().isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg));
        assertThatNoException().isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg));
    }

    @Test
    void shouldThrowWhenAlreadyInitializedWithDifferentConfig() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide first =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(TRACES_ENDPOINT, null, 10, 100L);

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide second =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(
                        "http://different-endpoint.com:4318/v1/traces", null, 10, 100L
                );

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(first).build()
        );

        assertThatNoException().isThrownBy(() -> invokeUseOpenTelemetry(factory, first));

        assertThatThrownBy(() -> invokeUseOpenTelemetry(factory, second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already initialized with a different configuration");
    }

    @Test
    void shouldTreatBlankEndpointsAsMissingAndFail() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide("   ", "\t", 10, 100L);

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg))
                .withMessageContaining("requires at least one of tracesEndpoint or metricsEndpoint");
    }

    @Test
    void shouldInitializeWhenTracesEndpointIsBlankButMetricsIsProvided() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide("   ", METRICS_ENDPOINT, null, null);

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatNoException().isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg));
    }

    @Test
    void shouldInitializeWhenMetricsEndpointIsBlankButTracesIsProvided() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(TRACES_ENDPOINT, "   ", 10, 100L);

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatNoException().isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg));
    }

    @Test
    void shouldNotInitializeWhenOpenTelemetryConfigIsNull() {
        assertThat(isOtelInitialized()).isFalse();
        assertThat(getInitializedConfig()).isNull();
    }

    @Test
    void shouldThrowWhenSamplePercentageIsOutOfRange() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(TRACES_ENDPOINT, null, 101, 100L);

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg))
                .withMessageContaining("samplePercentage");
    }

    @Test
    void shouldThrowWhenFlushIntervalMsIsNonPositive() {

        ValkeyGlideClientConfiguration.OpenTelemetryForGlide cfg =
                new ValkeyGlideClientConfiguration.OpenTelemetryForGlide(TRACES_ENDPOINT, null, 10, 0L);

        ValkeyGlideConnectionFactory factory = new ValkeyGlideConnectionFactory(
                new ValkeyClusterConfiguration(),
                ValkeyGlideClientConfiguration.builder().useOpenTelemetry(cfg).build()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> invokeUseOpenTelemetry(factory, cfg))
                .withMessageContaining("flushIntervalMs");
    }

    // ----------------- helpers -----------------

    private static boolean isOtelInitialized() {
        try {
            Field initializedField = ValkeyGlideConnectionFactory.class.getDeclaredField("OTEL_INITIALIZED");
            initializedField.setAccessible(true);
            AtomicBoolean initialized = (AtomicBoolean) initializedField.get(null);
            return initialized.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ValkeyGlideClientConfiguration.OpenTelemetryForGlide getInitializedConfig() {
        try {
            Field configField = ValkeyGlideConnectionFactory.class.getDeclaredField("OTEL_INITIALIZED_CONFIG");
            configField.setAccessible(true);
            return (ValkeyGlideClientConfiguration.OpenTelemetryForGlide) configField.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invoke the private useOpenTelemetry method via reflection.
     */
    private static void invokeUseOpenTelemetry(
            ValkeyGlideConnectionFactory factory,
            ValkeyGlideClientConfiguration.OpenTelemetryForGlide openTelemetryForGlide
    ) throws Exception {

        Method m = ValkeyGlideConnectionFactory.class
                .getDeclaredMethod(
                        "useOpenTelemetry",
                        ValkeyGlideClientConfiguration.OpenTelemetryForGlide.class
                );
        m.setAccessible(true);

        try {
            m.invoke(factory, openTelemetryForGlide);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Exception e) {
                throw e;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * Reset the static OpenTelemetry initialization state in ValkeyGlideConnectionFactory.
     */
    private static void resetFactoryStaticOpenTelemetryState() {
        try {
        Class<?> factoryClass = ValkeyGlideConnectionFactory.class;

            Field initializedField = factoryClass.getDeclaredField("OTEL_INITIALIZED");
            initializedField.setAccessible(true);
            AtomicBoolean initialized = (AtomicBoolean) initializedField.get(null);
            initialized.set(false);

            Field configField = factoryClass.getDeclaredField("OTEL_INITIALIZED_CONFIG");
            configField.setAccessible(true);
            configField.set(null, null);

        } catch (Exception e) {
            throw new RuntimeException("Failed to reset OpenTelemetry static state for tests", e);
        }
    }
}
