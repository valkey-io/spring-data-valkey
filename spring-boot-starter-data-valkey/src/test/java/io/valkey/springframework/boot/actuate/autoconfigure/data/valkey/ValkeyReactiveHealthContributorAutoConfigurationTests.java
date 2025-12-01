/*
 * Copyright 2012-2023 the original author or authors.
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

package io.valkey.springframework.boot.actuate.autoconfigure.data.valkey;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.valkey.springframework.boot.actuate.data.valkey.ValkeyHealthIndicator;
import io.valkey.springframework.boot.actuate.data.valkey.ValkeyReactiveHealthIndicator;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyAutoConfiguration;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyReactiveAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ValkeyReactiveHealthContributorAutoConfiguration}.
 *
 * @author Phillip Webb
 */
class ValkeyReactiveHealthContributorAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValkeyAutoConfiguration.class,
				ValkeyReactiveAutoConfiguration.class, ValkeyReactiveHealthContributorAutoConfiguration.class, 
				HealthContributorAutoConfiguration.class))
		.withPropertyValues("spring.data.valkey.client-type=lettuce"); // Force Lettuce for reactive support

	@Test
	void runShouldCreateIndicator() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(ValkeyReactiveHealthIndicator.class)
			.hasBean("valkeyHealthContributor"));
	}

	@Test
	void runWithRegularIndicatorShouldOnlyCreateReactiveIndicator() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(ValkeyHealthContributorAutoConfiguration.class))
			.run((context) -> assertThat(context).hasSingleBean(ValkeyReactiveHealthIndicator.class)
				.hasBean("valkeyHealthContributor")
				.doesNotHaveBean(ValkeyHealthIndicator.class));
	}

	@Test
	void runWhenDisabledShouldNotCreateIndicator() {
		this.contextRunner.withPropertyValues("management.health.valkey.enabled:false")
			.run((context) -> assertThat(context).doesNotHaveBean(ValkeyReactiveHealthIndicator.class)
				.doesNotHaveBean("valkeyHealthContributor"));
	}

}
