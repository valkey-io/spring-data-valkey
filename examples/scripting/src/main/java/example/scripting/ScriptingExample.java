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
package example.scripting;

import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.script.DefaultValkeyScript;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import java.util.Arrays;
import java.util.Collections;

/** Example demonstrating Lua scripting with Valkey. */
public class ScriptingExample {

    public static void main(String[] args) {

        ValkeyGlideConnectionFactory connectionFactory = new ValkeyGlideConnectionFactory();
        connectionFactory.afterPropertiesSet();

        try {
            ValkeyTemplate<String, String> template = new ValkeyTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setDefaultSerializer(StringValkeySerializer.UTF_8);
            template.afterPropertiesSet();

            // Simple script
            String script = "return redis.call('SET', KEYS[1], ARGV[1])";
            DefaultValkeyScript<String> valkeyScript = new DefaultValkeyScript<>(script, String.class);
            String result = template.execute(valkeyScript, Collections.singletonList("mykey"), "myvalue");
            System.out.println("Script result: " + result);
            System.out.println("Value: " + template.opsForValue().get("mykey"));

            // Atomic increment script with proper Lua syntax
            template.opsForValue().set("counter", "10");
            String incrementScript =
                    "local val = redis.call('GET', KEYS[1])\n"
                            + "local newval = tonumber(val) + tonumber(ARGV[1])\n"
                            + "redis.call('SET', KEYS[1], tostring(newval))\n"
                            + "return newval";
            DefaultValkeyScript<Long> incrementValkeyScript =
                    new DefaultValkeyScript<>(incrementScript, Long.class);
            Long newValue =
                    template.execute(incrementValkeyScript, Collections.singletonList("counter"), "5");
            System.out.println("\nCounter after increment: " + newValue);

            // Reuse script (demonstrates EVALSHA caching)
            System.out.println();
            for (int i = 0; i < 3; i++) {
                Long val =
                        template.execute(incrementValkeyScript, Collections.singletonList("counter"), "1");
                System.out.println("Increment " + (i + 1) + ": " + val);
            }

            // Cleanup
            template.delete(Arrays.asList("mykey", "counter"));
        } finally {
            connectionFactory.destroy();
        }
    }
}
