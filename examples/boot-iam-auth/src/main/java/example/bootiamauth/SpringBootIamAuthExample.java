package example.bootiamauth;

import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot example that demonstrates IAM authentication
 * with Valkey-GLIDE for AWS ElastiCache or MemoryDB.
 *
 * <p>This example shows how to use AWS IAM-based authentication instead of
 * password-based authentication when connecting to an ElastiCache or MemoryDB
 * cluster via GLIDE.</p>
 *
 * <h2>Prerequisites</h2>
 * <ul>
 *   <li>An ElastiCache or MemoryDB cluster with IAM authentication enabled</li>
 *   <li>AWS credentials configured (e.g., via environment variables, IAM role, or ~/.aws/credentials)</li>
 *   <li>The IAM user/role must have the {@code elasticache:Connect} or {@code memorydb:Connect} permission</li>
 *   <li>TLS must be enabled (required for IAM authentication)</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Update {@code application.properties} with your cluster details:</p>
 * <pre>
 * spring.data.valkey.host=your-cluster-endpoint.cache.amazonaws.com
 * spring.data.valkey.port=6379
 * spring.data.valkey.username=your-iam-user-id
 * spring.data.valkey.ssl.enabled=true
 * spring.data.valkey.client-type=valkeyglide
 *
 * spring.data.valkey.valkey-glide.iam-authentication.cluster-name=your-cluster-name
 * spring.data.valkey.valkey-glide.iam-authentication.service=ELASTICACHE
 * spring.data.valkey.valkey-glide.iam-authentication.region=us-east-1
 * </pre>
 *
 * @see <a href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#aws-iam-authentication-with-glide">
 *     Valkey-GLIDE IAM Authentication Documentation</a>
 */
@SpringBootApplication
public class SpringBootIamAuthExample implements CommandLineRunner {

    @Autowired
    private StringValkeyTemplate valkeyTemplate;

    public static void main(String[] args) {
        SpringApplication.run(SpringBootIamAuthExample.class, args);
    }

    @Override
    public void run(String... args) {

        System.out.println("=== IAM Authentication with Valkey-GLIDE ===");
        System.out.println("Connected successfully using IAM authentication!");

        // Simple read/write operations
        int iterations = 5;
        for (int i = 0; i < iterations; i++) {
            String key = "iam-test-key-" + i;
            String value = "iam-test-value-" + i;

            valkeyTemplate.opsForValue().set(key, value);
            String readBack = valkeyTemplate.opsForValue().get(key);
            System.out.println("  " + key + " = " + readBack);
        }

        System.out.println("Completed " + iterations + " iterations of Valkey commands with IAM auth.");

        // Cleanup
        for (int i = 0; i < iterations; i++) {
            valkeyTemplate.delete("iam-test-key-" + i);
        }
        System.out.println("Cleanup complete.");
    }
}
