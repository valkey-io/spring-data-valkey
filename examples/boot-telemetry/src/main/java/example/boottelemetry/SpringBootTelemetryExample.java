package example.boottelemetry;

import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot example that demonstrates OpenTelemetry integration
 * with Valkey-GLIDE via {@link StringValkeyTemplate}.
 *
 * <p>This example exists to verify and showcase that Valkey commands executed
 * through Spring Data Valkey automatically emit OpenTelemetry signals when
 * OpenTelemetry is enabled via application properties.</p>
 *
 * <p>Telemetry is emitted while the application runs and Valkey commands are
 * executed. Traces can be inspected via the configured OpenTelemetry backend,
 * for example by viewing the collector logs:</p>
 *
 * <pre>
 * docker logs -f boot-telemetry-otel-collector-1
 * </pre>
 */
@SpringBootApplication
public class SpringBootTelemetryExample implements CommandLineRunner {

    @Autowired
    private StringValkeyTemplate valkeyTemplate;

    public static void main(String[] args) {
        SpringApplication.run(SpringBootTelemetryExample.class, args);
    }

    @Override
    public void run(String... args) {

        // Increase this number to generate more Valkey commands/traces
        int iterations = 10;
        for (int i = 0; i < iterations; i++) {
            String key = "key" + i;
            String value = "value" + i;

            valkeyTemplate.opsForValue().set(key, value);
            String readBack = valkeyTemplate.opsForValue().get(key);
            System.out.println("Iteration " + i + ": " + key + "=" + readBack);
        }

        System.out.println("Completed " + iterations + " iterations of Valkey commands.");

        // Cleanup
        for (int i = 0; i < iterations; i++) {
            valkeyTemplate.delete("key" + i);
        }
    }
}
