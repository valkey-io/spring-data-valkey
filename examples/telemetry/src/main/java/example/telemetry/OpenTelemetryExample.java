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

import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration.OpenTelemetryForGlide;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;

import java.util.Arrays;
import java.util.List;

public class OpenTelemetryExample {

	public static void main(String[] args) {

        ValkeyStandaloneConfiguration standaloneConfig = new ValkeyStandaloneConfiguration();

		OpenTelemetryForGlide openTelemetry = OpenTelemetryForGlide.defaults();

		ValkeyGlideClientConfiguration clientConfig =
				ValkeyGlideClientConfiguration.builder()
						.useOpenTelemetry(openTelemetry)
						.build();

		// IMPORTANT: keep the concrete type so we can call afterPropertiesSet()/destroy()
		ValkeyGlideConnectionFactory connectionFactory =
				new ValkeyGlideConnectionFactory(standaloneConfig, clientConfig);

		connectionFactory.afterPropertiesSet();
		System.out.println("Connected to standalone Valkey server");

		try {
			StringValkeyTemplate template = new StringValkeyTemplate(connectionFactory);

			for (int i = 0; i < 10; i++) {
				String key = "key" + i;
				String value = "value" + i;

				template.opsForValue().set(key, value);
				String readBack = template.opsForValue().get(key);

				System.out.println("Iteration " + i + ": " + key + "=" + readBack);
			}

		} finally {
			connectionFactory.destroy();
		}
	}
}
