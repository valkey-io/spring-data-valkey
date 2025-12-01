/*
 * Copyright 2012-2022 the original author or authors.
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
import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ClientResourcesBuilderCustomizer;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyAutoConfiguration;

/**
 * Auto-configuration for Lettuce metrics with Valkey.
 *
 * @author Antonin Arquey
 * @author Yanming Zhou
 * @since 2.6.0
 */
@AutoConfiguration(before = ValkeyAutoConfiguration.class,
		after = { MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class })
@ConditionalOnClass({ RedisClient.class, MicrometerCommandLatencyRecorder.class })
@ConditionalOnBean(MeterRegistry.class)
public class ValkeyLettuceMetricsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	MicrometerOptions valkeyMicrometerOptions() {
		return MicrometerOptions.create();
	}

	@Bean
	ClientResourcesBuilderCustomizer valkeyLettuceMetrics(MeterRegistry meterRegistry, MicrometerOptions options) {
		return (client) -> client.commandLatencyRecorder(new MicrometerCommandLatencyRecorder(meterRegistry, options));
	}

}
