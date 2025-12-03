/*
 * Copyright 2012-2025 the original author or authors.
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

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.actuate.autoconfigure.health.CompositeHealthContributorConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.valkey.springframework.boot.actuate.data.valkey.ValkeyHealthIndicator;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyAutoConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link ValkeyHealthIndicator}.
 *
 * @author Christian Dupuis
 * @author Richard Santana
 * @author Stephane Nicoll
 * @author Mark Paluch
 * @since 2.1.0
 */
@AutoConfiguration(after = { ValkeyAutoConfiguration.class, ValkeyReactiveHealthContributorAutoConfiguration.class })
@ConditionalOnClass({ ValkeyConnectionFactory.class, HealthContributor.class })
@ConditionalOnBean(ValkeyConnectionFactory.class)
@ConditionalOnEnabledHealthIndicator("valkey")
public class ValkeyHealthContributorAutoConfiguration
		extends CompositeHealthContributorConfiguration<ValkeyHealthIndicator, ValkeyConnectionFactory> {

	ValkeyHealthContributorAutoConfiguration() {
		super(ValkeyHealthIndicator::new);
	}

	@Bean
	@ConditionalOnMissingBean(name = { "valkeyHealthIndicator", "valkeyHealthContributor" })
	public HealthContributor valkeyHealthContributor(ConfigurableListableBeanFactory beanFactory) {
		return createContributor(beanFactory, ValkeyConnectionFactory.class);
	}

}
