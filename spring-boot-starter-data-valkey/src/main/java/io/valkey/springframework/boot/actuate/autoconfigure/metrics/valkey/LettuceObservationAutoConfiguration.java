/*
 * Copyright 2012-present the original author or authors.
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

package io.valkey.springframework.boot.actuate.autoconfigure.metrics.valkey;

import io.lettuce.core.RedisClient;
import io.lettuce.core.tracing.MicrometerTracing;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ClientResourcesBuilderCustomizer;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Lettuce observability.
 *
 * @author Antonin Arquey
 * @author Yanming Zhou
 * @author Dũng Đăng Minh
 * @since 2.6.0
 */
@AutoConfiguration(before = ValkeyAutoConfiguration.class,
		afterName = "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration")
@ConditionalOnClass({ RedisClient.class, MicrometerTracing.class, ObservationRegistry.class })
@ConditionalOnBean(ObservationRegistry.class)
public final class LettuceObservationAutoConfiguration {

	@Bean
	ClientResourcesBuilderCustomizer lettuceObservation(ObservationRegistry observationRegistry) {
		return (client) -> client.tracing(new MicrometerTracing(observationRegistry, "Valkey"));
	}

}
