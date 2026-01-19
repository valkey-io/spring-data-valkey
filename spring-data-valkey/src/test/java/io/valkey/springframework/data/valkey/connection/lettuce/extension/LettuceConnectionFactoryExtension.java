/*
 * Copyright 2020-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.lettuce.extension;

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import io.valkey.springframework.data.valkey.ConnectionFactoryTracker;
import io.valkey.springframework.data.valkey.SettingsUtils;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceClientConfiguration;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettucePoolingClientConfiguration;
import io.valkey.springframework.data.valkey.connection.lettuce.LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder;
import io.valkey.springframework.data.valkey.test.extension.LettuceTestClientResources;
import io.valkey.springframework.data.valkey.test.extension.ValkeyCluster;
import io.valkey.springframework.data.valkey.test.extension.ValkeySentinel;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;
import io.valkey.springframework.data.valkey.test.extension.ShutdownQueue;
import org.springframework.data.util.Lazy;

/**
 * JUnit {@link ParameterResolver} providing pre-cached {@link LettuceConnectionFactory} instances. Connection factories
 * can be qualified with {@code @ValkeyStanalone} (default), {@code @ValkeySentinel} or {@code @ValkeyCluster} to obtain a
 * specific factory instance. Instances are managed by this extension and will be shut down on JVM shutdown.
 *
 * @author Mark Paluch
 * @see ValkeyStanalone
 * @see ValkeySentinel
 * @see ValkeyCluster
 */
public class LettuceConnectionFactoryExtension implements ParameterResolver {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
			.create(LettuceConnectionFactoryExtension.class);

	private static final Lazy<LettuceConnectionFactory> STANDALONE = Lazy.of(() -> {

		LettuceClientConfiguration configuration = defaultPoolConfigBuilder().build();

		ManagedLettuceConnectionFactory factory = new ManagedLettuceConnectionFactory(
				SettingsUtils.standaloneConfiguration(), configuration);
		factory.afterPropertiesSet();
		factory.start();

		ShutdownQueue.register(factory);

		return factory;
	});

	private static final Lazy<LettuceConnectionFactory> SENTINEL = Lazy.of(() -> {

		LettuceClientConfiguration configuration = defaultPoolConfigBuilder().build();

		ManagedLettuceConnectionFactory factory = new ManagedLettuceConnectionFactory(SettingsUtils.sentinelConfiguration(),
				configuration);
		factory.afterPropertiesSet();
		factory.start();
		ShutdownQueue.register(factory);

		return factory;
	});

	private static final Lazy<LettuceConnectionFactory> CLUSTER = Lazy.of(() -> {

		LettuceClientConfiguration configuration = defaultPoolConfigBuilder().build();

		ManagedLettuceConnectionFactory factory = new ManagedLettuceConnectionFactory(SettingsUtils.clusterConfiguration(),
				configuration);
		factory.afterPropertiesSet();
		factory.start();
		ShutdownQueue.register(factory);

		return factory;
	});

	private static final Lazy<LettuceConnectionFactory> STANDALONE_UNPOOLED = Lazy.of(() -> {

		LettuceClientConfiguration configuration = defaultClientConfiguration();

		ManagedLettuceConnectionFactory factory = new ManagedLettuceConnectionFactory(
				SettingsUtils.standaloneConfiguration(), configuration);
		factory.afterPropertiesSet();
		factory.start();
		ShutdownQueue.register(factory);

		return factory;
	});

	private static final Lazy<LettuceConnectionFactory> SENTINEL_UNPOOLED = Lazy.of(() -> {

		LettuceClientConfiguration configuration = defaultClientConfiguration();

		ManagedLettuceConnectionFactory factory = new ManagedLettuceConnectionFactory(SettingsUtils.sentinelConfiguration(),
				configuration);
		factory.afterPropertiesSet();
		factory.start();
		ShutdownQueue.register(factory);

		return factory;
	});

	private static final Lazy<LettuceConnectionFactory> CLUSTER_UNPOOLED = Lazy.of(() -> {

		LettuceClientConfiguration configuration = defaultClientConfiguration();

		ManagedLettuceConnectionFactory factory = new ManagedLettuceConnectionFactory(SettingsUtils.clusterConfiguration(),
				configuration);
		factory.afterPropertiesSet();
		factory.start();
		ShutdownQueue.register(factory);

		return factory;
	});

	private static LettucePoolingClientConfigurationBuilder defaultPoolConfigBuilder() {
		return LettucePoolingClientConfiguration.builder()
				.clientResources(LettuceTestClientResources.getSharedClientResources()).shutdownTimeout(Duration.ZERO)
				.shutdownQuietPeriod(Duration.ZERO);
	}

	private static LettuceClientConfiguration defaultClientConfiguration() {
		return LettuceClientConfiguration.builder().clientResources(LettuceTestClientResources.getSharedClientResources())
				.shutdownQuietPeriod(Duration.ZERO).shutdownQuietPeriod(Duration.ZERO).build();
	}

	private static final Map<Class<?>, Lazy<LettuceConnectionFactory>> pooledFactories;
	private static final Map<Class<?>, Lazy<LettuceConnectionFactory>> unpooledFactories;

	static {

		pooledFactories = new HashMap<>();
		pooledFactories.put(ValkeyStanalone.class, STANDALONE);
		pooledFactories.put(ValkeySentinel.class, SENTINEL);
		pooledFactories.put(ValkeyCluster.class, CLUSTER);

		unpooledFactories = new HashMap<>();
		unpooledFactories.put(ValkeyStanalone.class, STANDALONE_UNPOOLED);
		unpooledFactories.put(ValkeySentinel.class, SENTINEL_UNPOOLED);
		unpooledFactories.put(ValkeyCluster.class, CLUSTER_UNPOOLED);
	}

	/**
	 * Obtain a {@link LettuceConnectionFactory} described by {@code qualifier}. Instances are managed by this extension
	 * and will be shut down on JVM shutdown.
	 *
	 * @param qualifier an be any of {@link ValkeyStanalone}, {@link ValkeySentinel}, {@link ValkeyCluster}.
	 * @return the managed {@link LettuceConnectionFactory}.
	 */
	public static LettuceConnectionFactory getConnectionFactory(Class<? extends Annotation> qualifier) {
		return getConnectionFactory(qualifier, true);
	}

	/**
	 * Obtain a {@link LettuceConnectionFactory} described by {@code qualifier}. Instances are managed by this extension
	 * and will be shut down on JVM shutdown.
	 *
	 * @param qualifier an be any of {@link ValkeyStanalone}, {@link ValkeySentinel}, {@link ValkeyCluster}.
	 * @return the managed {@link LettuceConnectionFactory}.
	 */
	public static LettuceConnectionFactory getConnectionFactory(Class<? extends Annotation> qualifier, boolean pooled) {
		return pooled ? pooledFactories.get(qualifier).get() : unpooledFactories.get(qualifier).get();
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return ValkeyConnectionFactory.class.isAssignableFrom(parameterContext.getParameter().getType())
				|| ReactiveValkeyConnectionFactory.class.isAssignableFrom(parameterContext.getParameter().getType());
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {

		ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);

		Class<? extends Annotation> qualifier = getQualifier(parameterContext);

		return store.getOrComputeIfAbsent(qualifier, LettuceConnectionFactoryExtension::getConnectionFactory);
	}

	private static Class<? extends Annotation> getQualifier(ParameterContext parameterContext) {

		if (parameterContext.isAnnotated(ValkeySentinel.class)) {
			return ValkeySentinel.class;
		}

		if (parameterContext.isAnnotated(ValkeyCluster.class)) {
			return ValkeyCluster.class;
		}

		return ValkeyStanalone.class;
	}

	static class ManagedLettuceConnectionFactory extends LettuceConnectionFactory
			implements ConnectionFactoryTracker.Managed, Closeable {

		private volatile boolean mayClose;

		ManagedLettuceConnectionFactory(ValkeyStandaloneConfiguration standaloneConfig,
				LettuceClientConfiguration clientConfig) {
			super(standaloneConfig, clientConfig);
		}

		ManagedLettuceConnectionFactory(ValkeySentinelConfiguration sentinelConfiguration,
				LettuceClientConfiguration clientConfig) {
			super(sentinelConfiguration, clientConfig);
		}

		ManagedLettuceConnectionFactory(ValkeyClusterConfiguration clusterConfiguration,
				LettuceClientConfiguration clientConfig) {
			super(clusterConfiguration, clientConfig);
		}

		@Override
		public void destroy() {

			if (!mayClose) {
				throw new IllegalStateException(
						"Prematurely attempted to close ManagedLettuceConnectionFactory; Shutdown hook didn't run yet which means that the test run isn't finished yet; Please fix the tests so that they don't close this connection factory.");
			}

			super.destroy();
		}

		@Override
		public String toString() {

			StringBuilder builder = new StringBuilder("Lettuce");

			if (isClusterAware()) {
				builder.append(" Cluster");
			}

			if (isValkeySentinelAware()) {
				builder.append(" Sentinel");
			}

			if (this.getClientConfiguration() instanceof LettucePoolingClientConfiguration) {
				builder.append(" [pool]");
			}

			return builder.toString();
		}

		@Override
		public void close() throws IOException {

			mayClose = true;
			destroy();
		}
	}
}
