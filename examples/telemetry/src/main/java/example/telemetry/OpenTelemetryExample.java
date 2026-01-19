/*
 * Copyright 2025 the original author or authors.
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
package example.telemetry;

import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration.OpenTelemetryForGlide;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;

/**
 * Minimal example that demonstrates how to enable and emit OpenTelemetry traces
 * from Valkey-GLIDE using {@link StringValkeyTemplate}.
 *
 * <p>This example exists to validate and showcase the OpenTelemetry integration
 * in Valkey-GLIDE by executing a small number of Valkey commands and exporting
 * traces to an OpenTelemetry Collector.</p>
 *
 * <p>Telemetry is emitted while the application runs and Valkey commands are
 * executed. Traces can be inspected via the configured OpenTelemetry backend,
 * for example by viewing the collector logs:</p>
 *
 * <pre>
 * docker logs -f boot-telemetry-otel-collector-1
 * </pre>
 */
public class OpenTelemetryExample {

    public static void main(String[] args) {

        ValkeyStandaloneConfiguration standaloneConfig =
                new ValkeyStandaloneConfiguration();

        // Change the default tracesEndpoint / metricsEndpoint if needed
        OpenTelemetryForGlide openTelemetry =
                OpenTelemetryForGlide.defaults();

        ValkeyGlideClientConfiguration clientConfig =
                ValkeyGlideClientConfiguration.builder()
                        .useOpenTelemetry(openTelemetry)
                        .build();

        ValkeyGlideConnectionFactory connectionFactory =
                new ValkeyGlideConnectionFactory(standaloneConfig, clientConfig);

        connectionFactory.afterPropertiesSet();

        try {
            StringValkeyTemplate template =
                    new StringValkeyTemplate(connectionFactory);

            // Increase this number to generate more Valkey commands/traces
            int iterations = 10;
            for (int i = 0; i < iterations; i++) {
                String key = "key" + i;
                String value = "value" + i;

                template.opsForValue().set(key, value);
                String readBack = template.opsForValue().get(key);
                System.out.println("Iteration " + i + ": " + key + "=" + readBack);
            }

            System.out.println("Completed " + iterations + " iterations of Valkey commands.");

            // Cleanup
            for (int i = 0; i < iterations; i++) {
                template.delete("key" + i);
            }
        } finally {
            connectionFactory.destroy();
        }
    }
}
