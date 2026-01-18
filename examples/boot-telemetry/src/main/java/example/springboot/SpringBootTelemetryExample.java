package example.boottelemetry;

import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot example demonstrating one Valkey command via StringValkeyTemplate.
 * Intended to be used with Valkey-GLIDE + OpenTelemetry enabled via properties.
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

            // System.out.println("Iteration " + i + ": " + key + "=" + readBack);
			}
        System.out.println("Completed " + iterations + " iterations of Valkey commands.");
    }
}
