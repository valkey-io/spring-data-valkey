/*
 * Copyright 2017-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.AbstractTransactionSupportingCacheManager;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link CacheManager} implementation for Valkey backed by {@link ValkeyCache}.
 * <p>
 * This {@link CacheManager} creates {@link Cache caches} on first write, by default. Empty {@link Cache caches}
 * are not visible in Valkey due to how Valkey represents empty data structures.
 * <p>
 * {@link Cache Caches} requiring a different {@link ValkeyCacheConfiguration cache configuration} than the
 * {@link ValkeyCacheConfiguration#defaultCacheConfig() default cache configuration} can be specified via
 * {@link ValkeyCacheManagerBuilder#withInitialCacheConfigurations(Map)} or individually using
 * {@link ValkeyCacheManagerBuilder#withCacheConfiguration(String, ValkeyCacheConfiguration)}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Yanming Zhou
 * @author John Blum
 * @see org.springframework.cache.transaction.AbstractTransactionSupportingCacheManager
 * @see io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory
 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheConfiguration
 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
 * @see io.valkey.springframework.data.valkey.cache.ValkeyCache
 * @since 2.0
 */
public class ValkeyCacheManager extends AbstractTransactionSupportingCacheManager {

	protected static final boolean DEFAULT_ALLOW_RUNTIME_CACHE_CREATION = true;

	private final boolean allowRuntimeCacheCreation;

	private final ValkeyCacheConfiguration defaultCacheConfiguration;

	private final ValkeyCacheWriter cacheWriter;

	private final Map<String, ValkeyCacheConfiguration> initialCacheConfiguration;

	/**
	 * Creates a new {@link ValkeyCacheManager} initialized with the given {@link ValkeyCacheWriter} and default
	 * {@link ValkeyCacheConfiguration}.
	 * <p>
	 * Allows {@link ValkeyCache cache} creation at runtime.
	 *
	 * @param cacheWriter {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations
	 * by executing appropriate Valkey commands; must not be {@literal null}.
	 * @param defaultCacheConfiguration {@link ValkeyCacheConfiguration} applied to new {@link ValkeyCache Valkey caches}
	 * by default when no cache-specific {@link ValkeyCacheConfiguration} is provided; must not be {@literal null}.
	 * @throws IllegalArgumentException if either the given {@link ValkeyCacheWriter} or {@link ValkeyCacheConfiguration}
	 * are {@literal null}.
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheConfiguration
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
	 */
	public ValkeyCacheManager(ValkeyCacheWriter cacheWriter, ValkeyCacheConfiguration defaultCacheConfiguration) {
		this(cacheWriter, defaultCacheConfiguration, DEFAULT_ALLOW_RUNTIME_CACHE_CREATION);
	}

	/**
	 * Creates a new {@link ValkeyCacheManager} initialized with the given {@link ValkeyCacheWriter}
	 * and default {@link ValkeyCacheConfiguration} along with whether to allow cache creation at runtime.
	 *
	 * @param cacheWriter {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations
	 * by executing appropriate Valkey commands; must not be {@literal null}.
	 * @param defaultCacheConfiguration {@link ValkeyCacheConfiguration} applied to new {@link ValkeyCache Valkey caches}
	 * by default when no cache-specific {@link ValkeyCacheConfiguration} is provided; must not be {@literal null}.
	 * @param allowRuntimeCacheCreation boolean specifying whether to allow creation of undeclared caches at runtime;
	 * {@literal true} by default. Maybe just use {@link ValkeyCacheConfiguration#defaultCacheConfig()}.
	 * @throws IllegalArgumentException if either the given {@link ValkeyCacheWriter} or {@link ValkeyCacheConfiguration}
	 * are {@literal null}.
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheConfiguration
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
	 * @since 2.0.4
	 */
	private ValkeyCacheManager(ValkeyCacheWriter cacheWriter, ValkeyCacheConfiguration defaultCacheConfiguration,
			boolean allowRuntimeCacheCreation) {

		Assert.notNull(defaultCacheConfiguration, "DefaultCacheConfiguration must not be null");
		Assert.notNull(cacheWriter, "CacheWriter must not be null");

		this.defaultCacheConfiguration = defaultCacheConfiguration;
		this.cacheWriter = cacheWriter;
		this.initialCacheConfiguration = new LinkedHashMap<>();
		this.allowRuntimeCacheCreation = allowRuntimeCacheCreation;
	}

	/**
	 * Creates a new {@link ValkeyCacheManager} initialized with the given {@link ValkeyCacheWriter} and a default
	 * {@link ValkeyCacheConfiguration} along with an optional, initial set of {@link String cache names}
	 * used to create {@link ValkeyCache Valkey caches} on startup.
	 * <p>
	 * Allows {@link ValkeyCache cache} creation at runtime.
	 *
	 * @param cacheWriter {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations
	 * by executing appropriate Valkey commands; must not be {@literal null}.
	 * @param defaultCacheConfiguration {@link ValkeyCacheConfiguration} applied to new {@link ValkeyCache Valkey caches}
	 * by default when no cache-specific {@link ValkeyCacheConfiguration} is provided; must not be {@literal null}.
	 * @param initialCacheNames optional set of {@link String cache names} used to create {@link ValkeyCache Valkey caches}
	 * on startup. The default {@link ValkeyCacheConfiguration} will be applied to each cache.
	 * @throws IllegalArgumentException if either the given {@link ValkeyCacheWriter} or {@link ValkeyCacheConfiguration}
	 * are {@literal null}.
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheConfiguration
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
	 */
	public ValkeyCacheManager(ValkeyCacheWriter cacheWriter, ValkeyCacheConfiguration defaultCacheConfiguration,
			String... initialCacheNames) {

		this(cacheWriter, defaultCacheConfiguration, DEFAULT_ALLOW_RUNTIME_CACHE_CREATION, initialCacheNames);
	}

	/**
	 * Creates a new {@link ValkeyCacheManager} initialized with the given {@link ValkeyCacheWriter} and default
	 * {@link ValkeyCacheConfiguration} along with whether to allow cache creation at runtime.
	 * <p>
	 * Additionally, the optional, initial set of {@link String cache names} will be used to
	 * create {@link ValkeyCache Valkey caches} on startup.
	 *
	 * @param cacheWriter {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations
	 * by executing appropriate Valkey commands; must not be {@literal null}.
	 * @param defaultCacheConfiguration {@link ValkeyCacheConfiguration} applied to new {@link ValkeyCache Valkey caches}
	 * by default when no cache-specific {@link ValkeyCacheConfiguration} is provided; must not be {@literal null}.
	 * @param allowRuntimeCacheCreation boolean specifying whether to allow creation of undeclared caches at runtime;
	 * {@literal true} by default. Maybe just use {@link ValkeyCacheConfiguration#defaultCacheConfig()}.
	 * @param initialCacheNames optional set of {@link String cache names} used to create {@link ValkeyCache Valkey caches}
	 * on startup. The default {@link ValkeyCacheConfiguration} will be applied to each cache.
	 * @throws IllegalArgumentException if either the given {@link ValkeyCacheWriter} or {@link ValkeyCacheConfiguration}
	 * are {@literal null}.
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheConfiguration
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
	 * @since 2.0.4
	 */
	public ValkeyCacheManager(ValkeyCacheWriter cacheWriter, ValkeyCacheConfiguration defaultCacheConfiguration,
			boolean allowRuntimeCacheCreation, String... initialCacheNames) {

		this(cacheWriter, defaultCacheConfiguration, allowRuntimeCacheCreation);

		for (String cacheName : initialCacheNames) {
			this.initialCacheConfiguration.put(cacheName, defaultCacheConfiguration);
		}
	}

	/**
	 * Creates new {@link ValkeyCacheManager} using given {@link ValkeyCacheWriter} and default
	 * {@link ValkeyCacheConfiguration}.
	 * <p>
	 * Additionally, an initial {@link ValkeyCache} will be created and configured using the associated
	 * {@link ValkeyCacheConfiguration} for each {@link String named} {@link ValkeyCache} in the given {@link Map}.
	 * <p>
	 * Allows {@link ValkeyCache cache} creation at runtime.
	 *
	 * @param cacheWriter {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations
	 * by executing appropriate Valkey commands; must not be {@literal null}.
	 * @param defaultCacheConfiguration {@link ValkeyCacheConfiguration} applied to new {@link ValkeyCache Valkey caches}
	 * by default when no cache-specific {@link ValkeyCacheConfiguration} is provided; must not be {@literal null}.
	 * @param initialCacheConfigurations {@link Map} of declared, known {@link String cache names} along with associated
	 * {@link ValkeyCacheConfiguration} used to create and configure {@link ValkeyCache Reds caches} on startup;
	 * must not be {@literal null}.
	 * @throws IllegalArgumentException if either the given {@link ValkeyCacheWriter} or {@link ValkeyCacheConfiguration}
	 * are {@literal null}.
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheConfiguration
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
	 */
	public ValkeyCacheManager(ValkeyCacheWriter cacheWriter, ValkeyCacheConfiguration defaultCacheConfiguration,
			Map<String, ValkeyCacheConfiguration> initialCacheConfigurations) {

		this(cacheWriter, defaultCacheConfiguration, DEFAULT_ALLOW_RUNTIME_CACHE_CREATION, initialCacheConfigurations);
	}

	/**
	 * Creates a new {@link ValkeyCacheManager} initialized with the given {@link ValkeyCacheWriter} and a default
	 * {@link ValkeyCacheConfiguration}, and whether to allow {@link ValkeyCache} creation at runtime.
	 * <p>
	 * Additionally, an initial {@link ValkeyCache} will be created and configured using the associated
	 * {@link ValkeyCacheConfiguration} for each {@link String named} {@link ValkeyCache} in the given {@link Map}.
	 *
	 * @param cacheWriter {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations
	 * by executing appropriate Valkey commands; must not be {@literal null}.
	 * @param defaultCacheConfiguration {@link ValkeyCacheConfiguration} applied to new {@link ValkeyCache Valkey caches}
	 * by default when no cache-specific {@link ValkeyCacheConfiguration} is provided; must not be {@literal null}.
	 * @param allowRuntimeCacheCreation boolean specifying whether to allow creation of undeclared caches at runtime;
	 * {@literal true} by default. Maybe just use {@link ValkeyCacheConfiguration#defaultCacheConfig()}.
	 * @param initialCacheConfigurations {@link Map} of declared, known {@link String cache names} along with the
	 * associated {@link ValkeyCacheConfiguration} used to create and configure {@link ValkeyCache Valkey caches}
	 * on startup; must not be {@literal null}.
	 * @throws IllegalArgumentException if either the given {@link ValkeyCacheWriter} or {@link ValkeyCacheConfiguration}
	 * are {@literal null}.
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheConfiguration
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
	 * @since 2.0.4
	 */
	public ValkeyCacheManager(ValkeyCacheWriter cacheWriter, ValkeyCacheConfiguration defaultCacheConfiguration,
			boolean allowRuntimeCacheCreation, Map<String, ValkeyCacheConfiguration> initialCacheConfigurations) {

		this(cacheWriter, defaultCacheConfiguration, allowRuntimeCacheCreation);

		Assert.notNull(initialCacheConfigurations, "InitialCacheConfigurations must not be null");

		this.initialCacheConfiguration.putAll(initialCacheConfigurations);
	}

	/**
	 * @deprecated since 3.2. Use {@link ValkeyCacheManager#ValkeyCacheManager(ValkeyCacheWriter, ValkeyCacheConfiguration, boolean, Map)} instead.
	 */
	@Deprecated(since = "3.2")
	public ValkeyCacheManager(ValkeyCacheWriter cacheWriter, ValkeyCacheConfiguration defaultCacheConfiguration,
			Map<String, ValkeyCacheConfiguration> initialCacheConfigurations, boolean allowRuntimeCacheCreation) {

		this(cacheWriter, defaultCacheConfiguration, allowRuntimeCacheCreation, initialCacheConfigurations);
	}

	/**
	 * Factory method returning a {@literal Builder} used to construct and configure a {@link ValkeyCacheManager}.
	 *
	 * @return new {@link ValkeyCacheManagerBuilder}.
	 * @since 2.3
	 */
	public static ValkeyCacheManagerBuilder builder() {
		return new ValkeyCacheManagerBuilder();
	}

	/**
	 * Factory method returning a {@literal Builder} used to construct and configure a {@link ValkeyCacheManager}
	 * initialized with the given {@link ValkeyCacheWriter}.
	 *
	 * @param cacheWriter {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations
	 * by executing appropriate Valkey commands; must not be {@literal null}.
	 * @return new {@link ValkeyCacheManagerBuilder}.
	 * @throws IllegalArgumentException if the given {@link ValkeyCacheWriter} is {@literal null}.
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
	 */
	public static ValkeyCacheManagerBuilder builder(ValkeyCacheWriter cacheWriter) {

		Assert.notNull(cacheWriter, "CacheWriter must not be null");

		return ValkeyCacheManagerBuilder.fromCacheWriter(cacheWriter);
	}

	/**
	 * Factory method returning a {@literal Builder} used to construct and configure a {@link ValkeyCacheManager}
	 * initialized with the given {@link ValkeyConnectionFactory}.
	 *
	 * @param connectionFactory {@link ValkeyConnectionFactory} used by the {@link ValkeyCacheManager}
	 * to acquire connections to Valkey when performing {@link ValkeyCache} operations; must not be {@literal null}.
	 * @return new {@link ValkeyCacheManagerBuilder}.
	 * @throws IllegalArgumentException if the given {@link ValkeyConnectionFactory} is {@literal null}.
	 * @see io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory
	 */
	public static ValkeyCacheManagerBuilder builder(ValkeyConnectionFactory connectionFactory) {

		Assert.notNull(connectionFactory, "ConnectionFactory must not be null");

		return ValkeyCacheManagerBuilder.fromConnectionFactory(connectionFactory);
	}

	/**
	 * Factory method used to construct a new {@link ValkeyCacheManager} initialized with the given
	 * {@link ValkeyConnectionFactory} and using {@link ValkeyCacheConfiguration#defaultCacheConfig() defaults} for caching.
	 * <dl>
	 * <dt>locking</dt>
	 * <dd>disabled</dd>
	 * <dt>batch strategy</dt>
	 * <dd>{@link BatchStrategies#keys()}</dd>
	 * <dt>cache configuration</dt>
	 * <dd>{@link ValkeyCacheConfiguration#defaultCacheConfig()}</dd>
	 * <dt>initial caches</dt>
	 * <dd>none</dd>
	 * <dt>transaction aware</dt>
	 * <dd>no</dd>
	 * <dt>in-flight cache creation</dt>
	 * <dd>enabled</dd>
	 * </dl>
	 *
	 * @param connectionFactory {@link ValkeyConnectionFactory} used by the {@link ValkeyCacheManager}
	 * to acquire connections to Valkey when performing {@link ValkeyCache} operations; must not be {@literal null}.
	 * @return new {@link ValkeyCacheManager}.
	 * @throws IllegalArgumentException if the given {@link ValkeyConnectionFactory} is {@literal null}.
	 * @see io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory
	 */
	public static ValkeyCacheManager create(ValkeyConnectionFactory connectionFactory) {

		Assert.notNull(connectionFactory, "ConnectionFactory must not be null");

		ValkeyCacheWriter cacheWriter = ValkeyCacheWriter.nonLockingValkeyCacheWriter(connectionFactory);
		ValkeyCacheConfiguration cacheConfiguration = ValkeyCacheConfiguration.defaultCacheConfig();

		return new ValkeyCacheManager(cacheWriter, cacheConfiguration);
	}

	/**
	 * Determines whether {@link ValkeyCache Valkey caches} are allowed to be created at runtime.
	 *
	 * @return a boolean value indicating whether {@link ValkeyCache Valkey caches} are allowed to be created at runtime.
	 */
	public boolean isAllowRuntimeCacheCreation() {
		return this.allowRuntimeCacheCreation;
	}

	/**
	 * Return an {@link Collections#unmodifiableMap(Map) unmodifiable Map} containing {@link String caches name} mapped to
	 * the {@link ValkeyCache} {@link ValkeyCacheConfiguration configuration}.
	 *
	 * @return unmodifiable {@link Map} containing {@link String cache name}
	 * / {@link ValkeyCacheConfiguration configuration} pairs.
	 */
	public Map<String, ValkeyCacheConfiguration> getCacheConfigurations() {

		Map<String, ValkeyCacheConfiguration> cacheConfigurationMap = new HashMap<>(getCacheNames().size());

		getCacheNames().forEach(cacheName -> {
			ValkeyCache cache = (ValkeyCache) lookupCache(cacheName);
			ValkeyCacheConfiguration cacheConfiguration = cache != null ? cache.getCacheConfiguration() : null;
			cacheConfigurationMap.put(cacheName, cacheConfiguration);
		});

		return Collections.unmodifiableMap(cacheConfigurationMap);
	}

	/**
	 * Gets the default {@link ValkeyCacheConfiguration} applied to new {@link ValkeyCache} instances on creation when
	 * custom, non-specific {@link ValkeyCacheConfiguration} was not provided.
	 *
	 * @return the default {@link ValkeyCacheConfiguration}.
	 */
	protected ValkeyCacheConfiguration getDefaultCacheConfiguration() {
		return this.defaultCacheConfiguration;
	}

	/**
	 * Gets a {@link Map} of {@link String cache names} to {@link ValkeyCacheConfiguration} objects as the initial set
	 * of {@link ValkeyCache Valkey caches} to create on startup.
	 *
	 * @return a {@link Map} of {@link String cache names} to {@link ValkeyCacheConfiguration} objects.
	 */
	protected Map<String, ValkeyCacheConfiguration> getInitialCacheConfiguration() {
		return Collections.unmodifiableMap(this.initialCacheConfiguration);
	}

	/**
	 * Returns a reference to the configured {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations,
	 * such as reading from and writing to the cache.
	 *
	 * @return a reference to the configured {@link ValkeyCacheWriter}.
	 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
	 */
	protected ValkeyCacheWriter getCacheWriter() {
		return this.cacheWriter;
	}

	@Override
	protected ValkeyCache getMissingCache(String name) {
		return isAllowRuntimeCacheCreation() ? createValkeyCache(name, getDefaultCacheConfiguration()) : null;
	}

	/**
	 * Creates a new {@link ValkeyCache} with given {@link String name} and {@link ValkeyCacheConfiguration}.
	 *
	 * @param name {@link String name} for the {@link ValkeyCache}; must not be {@literal null}.
	 * @param cacheConfiguration {@link ValkeyCacheConfiguration} used to configure the {@link ValkeyCache};
	 * resolves to the {@link #getDefaultCacheConfiguration()} if {@literal null}.
	 * @return a new {@link ValkeyCache} instance; never {@literal null}.
	 */
	protected ValkeyCache createValkeyCache(String name, @Nullable ValkeyCacheConfiguration cacheConfiguration) {
		return new ValkeyCache(name, getCacheWriter(), resolveCacheConfiguration(cacheConfiguration));
	}

	@Override
	protected Collection<ValkeyCache> loadCaches() {

		return getInitialCacheConfiguration().entrySet().stream()
				.map(entry -> createValkeyCache(entry.getKey(), entry.getValue())).toList();
	}

	private ValkeyCacheConfiguration resolveCacheConfiguration(@Nullable ValkeyCacheConfiguration cacheConfiguration) {
		return cacheConfiguration != null ? cacheConfiguration : getDefaultCacheConfiguration();
	}

	/**
	 * {@literal Builder} for creating a {@link ValkeyCacheManager}.
	 *
	 * @author Christoph Strobl
	 * @author Mark Paluch
	 * @author Kezhu Wang
	 * @author John Blum
	 * @since 2.0
	 */
	public static class ValkeyCacheManagerBuilder {

		/**
		 * Factory method returning a new {@literal Builder} used to create and configure a {@link ValkeyCacheManager} using
		 * the given {@link ValkeyCacheWriter}.
		 *
		 * @param cacheWriter {@link ValkeyCacheWriter} used to perform {@link ValkeyCache} operations
		 * by executing appropriate Valkey commands; must not be {@literal null}.
		 * @return new {@link ValkeyCacheManagerBuilder}.
		 * @throws IllegalArgumentException if the given {@link ValkeyCacheWriter} is {@literal null}.
		 * @see io.valkey.springframework.data.valkey.cache.ValkeyCacheWriter
		 */
		public static ValkeyCacheManagerBuilder fromCacheWriter(ValkeyCacheWriter cacheWriter) {

			Assert.notNull(cacheWriter, "CacheWriter must not be null");

			return new ValkeyCacheManagerBuilder(cacheWriter);
		}

		/**
		 * Factory method returning a new {@literal Builder} used to create and configure a {@link ValkeyCacheManager} using
		 * the given {@link ValkeyConnectionFactory}.
		 *
		 * @param connectionFactory {@link ValkeyConnectionFactory} used by the {@link ValkeyCacheManager}
		 * to acquire connections to Valkey when performing {@link ValkeyCache} operations; must not be {@literal null}.
		 * @return new {@link ValkeyCacheManagerBuilder}.
		 * @throws IllegalArgumentException if the given {@link ValkeyConnectionFactory} is {@literal null}.
		 * @see io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory
		 */
		public static ValkeyCacheManagerBuilder fromConnectionFactory(ValkeyConnectionFactory connectionFactory) {

			Assert.notNull(connectionFactory, "ConnectionFactory must not be null");

			ValkeyCacheWriter cacheWriter = ValkeyCacheWriter.nonLockingValkeyCacheWriter(connectionFactory);

			return new ValkeyCacheManagerBuilder(cacheWriter);
		}

		private boolean allowRuntimeCacheCreation = true;
		private boolean enableTransactions;

		private CacheStatisticsCollector statisticsCollector = CacheStatisticsCollector.none();

		private final Map<String, ValkeyCacheConfiguration> initialCaches = new LinkedHashMap<>();

		private ValkeyCacheConfiguration defaultCacheConfiguration = ValkeyCacheConfiguration.defaultCacheConfig();

		private @Nullable ValkeyCacheWriter cacheWriter;

		private ValkeyCacheManagerBuilder() {}

		private ValkeyCacheManagerBuilder(ValkeyCacheWriter cacheWriter) {
			this.cacheWriter = cacheWriter;
		}

		/**
		 * Configure whether to allow cache creation at runtime.
		 *
		 * @param allowRuntimeCacheCreation boolean to allow creation of undeclared caches at runtime;
		 * {@literal true} by default.
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 */
		public ValkeyCacheManagerBuilder allowCreateOnMissingCache(boolean allowRuntimeCacheCreation) {
			this.allowRuntimeCacheCreation = allowRuntimeCacheCreation;
			return this;
		}

		/**
		 * Disable {@link ValkeyCache} creation at runtime for non-configured, undeclared caches.
		 * <p>
		 * {@link ValkeyCacheManager#getMissingCache(String)} returns {@literal null} for any non-configured,
		 * undeclared {@link Cache} instead of a new {@link ValkeyCache} instance.
		 * This allows the {@link org.springframework.cache.support.CompositeCacheManager} to participate.
		 *
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 * @see #allowCreateOnMissingCache(boolean)
		 * @see #enableCreateOnMissingCache()
		 * @since 2.0.4
		 */
		public ValkeyCacheManagerBuilder disableCreateOnMissingCache() {
			return allowCreateOnMissingCache(false);
		}

		/**
		 * Enables {@link ValkeyCache} creation at runtime for unconfigured, undeclared caches.
		 *
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 * @see #allowCreateOnMissingCache(boolean)
		 * @see #disableCreateOnMissingCache()
		 * @since 2.0.4
		 */
		public ValkeyCacheManagerBuilder enableCreateOnMissingCache() {
			return allowCreateOnMissingCache(true);
		}

		/**
		 * Returns the default {@link ValkeyCacheConfiguration}.
		 *
		 * @return the default {@link ValkeyCacheConfiguration}.
		 */
		public ValkeyCacheConfiguration cacheDefaults() {
			return this.defaultCacheConfiguration;
		}

		/**
		 * Define a default {@link ValkeyCacheConfiguration} applied to dynamically created {@link ValkeyCache}s.
		 *
		 * @param defaultCacheConfiguration must not be {@literal null}.
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 */
		public ValkeyCacheManagerBuilder cacheDefaults(ValkeyCacheConfiguration defaultCacheConfiguration) {

			Assert.notNull(defaultCacheConfiguration, "DefaultCacheConfiguration must not be null");

			this.defaultCacheConfiguration = defaultCacheConfiguration;

			return this;
		}

		/**
		 * Configure a {@link ValkeyCacheWriter}.
		 *
		 * @param cacheWriter must not be {@literal null}.
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 * @since 2.3
		 */
		public ValkeyCacheManagerBuilder cacheWriter(ValkeyCacheWriter cacheWriter) {

			Assert.notNull(cacheWriter, "CacheWriter must not be null");

			this.cacheWriter = cacheWriter;
			return this;
		}

		/**
		 * Enables cache statistics.
		 *
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 */
		public ValkeyCacheManagerBuilder enableStatistics() {
			this.statisticsCollector = CacheStatisticsCollector.create();
			return this;
		}

		/**
		 * Append a {@link Set} of cache names to be pre initialized with current {@link ValkeyCacheConfiguration}.
		 * <strong>NOTE:</strong> This calls depends on {@link #cacheDefaults(ValkeyCacheConfiguration)} using whatever
		 * default {@link ValkeyCacheConfiguration} is present at the time of invoking this method.
		 *
		 * @param cacheNames must not be {@literal null}.
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 */
		public ValkeyCacheManagerBuilder initialCacheNames(Set<String> cacheNames) {

			Assert.notNull(cacheNames, "CacheNames must not be null");
			Assert.noNullElements(cacheNames, "CacheNames must not be null");

			cacheNames.forEach(it -> withCacheConfiguration(it, defaultCacheConfiguration));

			return this;
		}

		/**
		 * Enable {@link ValkeyCache}s to synchronize cache put/evict operations with ongoing Spring-managed transactions.
		 *
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 */
		public ValkeyCacheManagerBuilder transactionAware() {
			this.enableTransactions = true;
			return this;
		}

		/**
		 * Registers the given {@link String cache name} and {@link ValkeyCacheConfiguration} used to create
		 * and configure a {@link ValkeyCache} on startup.
		 *
		 * @param cacheName {@link String name} of the cache to register for creation on startup.
		 * @param cacheConfiguration {@link ValkeyCacheConfiguration} used to configure the new cache on startup.
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 * @since 2.2
		 */
		public ValkeyCacheManagerBuilder withCacheConfiguration(String cacheName,
				ValkeyCacheConfiguration cacheConfiguration) {

			Assert.notNull(cacheName, "CacheName must not be null");
			Assert.notNull(cacheConfiguration, "CacheConfiguration must not be null");

			this.initialCaches.put(cacheName, cacheConfiguration);

			return this;
		}

		/**
		 * Append a {@link Map} of cache name/{@link ValkeyCacheConfiguration} pairs to be pre initialized.
		 *
		 * @param cacheConfigurations must not be {@literal null}.
		 * @return this {@link ValkeyCacheManagerBuilder}.
		 */
		public ValkeyCacheManagerBuilder withInitialCacheConfigurations(
				Map<String, ValkeyCacheConfiguration> cacheConfigurations) {

			Assert.notNull(cacheConfigurations, "CacheConfigurations must not be null!");
			cacheConfigurations.forEach((cacheName, configuration) -> Assert.notNull(configuration,
					String.format("ValkeyCacheConfiguration for cache %s must not be null!", cacheName)));

			this.initialCaches.putAll(cacheConfigurations);

			return this;
		}

		/**
		 * Get the {@link ValkeyCacheConfiguration} for a given cache by its name.
		 *
		 * @param cacheName must not be {@literal null}.
		 * @return {@link Optional#empty()} if no {@link ValkeyCacheConfiguration} set for the given cache name.
		 * @since 2.2
		 */
		public Optional<ValkeyCacheConfiguration> getCacheConfigurationFor(String cacheName) {
			return Optional.ofNullable(this.initialCaches.get(cacheName));
		}

		/**
		 * Get the {@link Set} of cache names for which the builder holds {@link ValkeyCacheConfiguration configuration}.
		 *
		 * @return an unmodifiable {@link Set} holding the name of caches
		 * for which a {@link ValkeyCacheConfiguration configuration} has been set.
		 * @since 2.2
		 */
		public Set<String> getConfiguredCaches() {
			return Collections.unmodifiableSet(this.initialCaches.keySet());
		}

		/**
		 * Create new instance of {@link ValkeyCacheManager} with configuration options applied.
		 *
		 * @return new instance of {@link ValkeyCacheManager}.
		 */
		public ValkeyCacheManager build() {

			Assert.state(cacheWriter != null, "CacheWriter must not be null;"
					+ " You can provide one via 'ValkeyCacheManagerBuilder#cacheWriter(ValkeyCacheWriter)'");

			ValkeyCacheWriter resolvedCacheWriter = !CacheStatisticsCollector.none().equals(this.statisticsCollector)
					? this.cacheWriter.withStatisticsCollector(this.statisticsCollector)
					: this.cacheWriter;

			ValkeyCacheManager cacheManager = newValkeyCacheManager(resolvedCacheWriter);

			cacheManager.setTransactionAware(this.enableTransactions);

			return cacheManager;
		}

		private ValkeyCacheManager newValkeyCacheManager(ValkeyCacheWriter cacheWriter) {
			return new ValkeyCacheManager(cacheWriter, cacheDefaults(), this.allowRuntimeCacheCreation, this.initialCaches);
		}
	}
}
