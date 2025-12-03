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

package io.valkey.springframework.boot.autoconfigure.data.valkey;

import glide.api.models.configuration.ReadFrom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.util.StringUtils;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;

/**
 * Valkey GLIDE connection configuration.
 *
 * @author Jeremy Parr-Pearson
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ValkeyGlideConnectionFactory.class, glide.api.GlideClient.class})
@ConditionalOnProperty(name = "spring.data.valkey.client-type", havingValue = "valkeyglide", matchIfMissing = true)
class ValkeyGlideConnectionConfiguration extends ValkeyConnectionConfiguration {

	ValkeyGlideConnectionConfiguration(ValkeyProperties properties,
			ValkeyConnectionDetails connectionDetails,
			ObjectProvider<ValkeyStandaloneConfiguration> standaloneConfigurationProvider,
			ObjectProvider<ValkeySentinelConfiguration> sentinelConfigurationProvider,
			ObjectProvider<ValkeyClusterConfiguration> clusterConfigurationProvider) {
		super(properties, connectionDetails, standaloneConfigurationProvider, sentinelConfigurationProvider, clusterConfigurationProvider);
	}

	@Bean
	@ConditionalOnMissingBean(ValkeyConnectionFactory.class)
	@ConditionalOnThreading(Threading.PLATFORM)
	ValkeyGlideConnectionFactory valkeyConnectionFactory(
			ObjectProvider<ValkeyGlideClientConfigurationBuilderCustomizer> builderCustomizers) {
		return createValkeyConnectionFactory(builderCustomizers);
	}

	@Bean
	@ConditionalOnMissingBean(ValkeyConnectionFactory.class)
	@ConditionalOnThreading(Threading.VIRTUAL)
	ValkeyGlideConnectionFactory valkeyConnectionFactoryVirtualThreads(
			ObjectProvider<ValkeyGlideClientConfigurationBuilderCustomizer> builderCustomizers) {
		ValkeyGlideConnectionFactory factory = createValkeyConnectionFactory(builderCustomizers);
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("valkey-");
		executor.setVirtualThreads(true);
		factory.setExecutor(executor);
		return factory;
	}

	private ValkeyGlideConnectionFactory createValkeyConnectionFactory(
			ObjectProvider<ValkeyGlideClientConfigurationBuilderCustomizer> builderCustomizers) {
		ValkeyGlideClientConfiguration clientConfiguration = getValkeyGlideClientConfiguration(builderCustomizers);
		ValkeyGlideConnectionFactory factory = switch (this.mode) {
			case STANDALONE -> new ValkeyGlideConnectionFactory(getStandaloneConfig(), clientConfiguration);
			case CLUSTER -> new ValkeyGlideConnectionFactory(getClusterConfiguration(), clientConfiguration);
			case SENTINEL -> new ValkeyGlideConnectionFactory(getSentinelConfig(), clientConfiguration);
			default -> new ValkeyGlideConnectionFactory(getStandaloneConfig(), clientConfiguration);
		};

		// Disable early startup for Spring Boot to avoid connection attempts during bean creation
		factory.setEarlyStartup(false);
		return factory;
	}

	private ValkeyGlideClientConfiguration getValkeyGlideClientConfiguration(
			ObjectProvider<ValkeyGlideClientConfigurationBuilderCustomizer> builderCustomizers) {
		ValkeyGlideClientConfiguration.ValkeyGlideClientConfigurationBuilder builder = ValkeyGlideClientConfiguration
				.builder();

		if (getProperties().getTimeout() != null) {
			builder.commandTimeout(getProperties().getTimeout());
		}
		if (getProperties().getSsl().isEnabled() || getSslBundle() != null) {
			builder.useSsl();
		}
		if (StringUtils.hasText(getProperties().getUrl())) {
			customizeConfigurationFromUrl(builder);
		}

		ValkeyProperties.ValkeyGlide valkeyGlideProperties = getProperties().getValkeyGlide();
		if (valkeyGlideProperties.getConnectionTimeout() != null) {
			builder.connectionTimeout(valkeyGlideProperties.getConnectionTimeout());
		}
		String readFrom = valkeyGlideProperties.getReadFrom();
		if (StringUtils.hasText(readFrom)) {
			builder.readFrom(getReadFrom(readFrom));
		}
		if (valkeyGlideProperties.getInflightRequestsLimit() != null) {
			builder.inflightRequestsLimit(valkeyGlideProperties.getInflightRequestsLimit());
		}
		if (valkeyGlideProperties.getClientAZ() != null) {
			builder.clientAZ(valkeyGlideProperties.getClientAZ());
		}
		if (valkeyGlideProperties.getMaxPoolSize() != null) {
			builder.maxPoolSize(valkeyGlideProperties.getMaxPoolSize());
		}

		builderCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
		return builder.build();
	}

	private void customizeConfigurationFromUrl(ValkeyGlideClientConfiguration.ValkeyGlideClientConfigurationBuilder builder) {
		if (urlUsesSsl()) {
			builder.useSsl();
		}
	}

	private ReadFrom getReadFrom(String readFrom) {
		try {
			return ReadFrom.valueOf(readFrom.toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid readFrom value: " + readFrom, ex);
		}
	}

}
