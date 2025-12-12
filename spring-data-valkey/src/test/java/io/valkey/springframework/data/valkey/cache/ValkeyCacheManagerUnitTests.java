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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cache.Cache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import io.valkey.springframework.data.valkey.cache.ValkeyCacheManager.ValkeyCacheManagerBuilder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link ValkeyCacheManager}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Yanming Zhou
 */
@ExtendWith(MockitoExtension.class)
class ValkeyCacheManagerUnitTests {

	@Mock ValkeyCacheWriter cacheWriter;

	@Test // DATAREDIS-481
	void missingCacheShouldBeCreatedWithDefaultConfiguration() {

		ValkeyCacheConfiguration configuration = ValkeyCacheConfiguration.defaultCacheConfig().disableKeyPrefix();

		ValkeyCacheManager cm = ValkeyCacheManager.builder(cacheWriter).cacheDefaults(configuration).build();
		cm.afterPropertiesSet();

		assertThat(cm.getMissingCache("new-cache").getCacheConfiguration()).isEqualTo(configuration);
	}

	@Test // DATAREDIS-481
	void appliesDefaultConfigurationToInitialCache() {

		ValkeyCacheConfiguration withPrefix = ValkeyCacheConfiguration.defaultCacheConfig().disableKeyPrefix();
		ValkeyCacheConfiguration withoutPrefix = ValkeyCacheConfiguration.defaultCacheConfig().disableKeyPrefix();

		ValkeyCacheManager cm = ValkeyCacheManager.builder(cacheWriter).cacheDefaults(withPrefix) //
				.initialCacheNames(Collections.singleton("first-cache")) //
				.cacheDefaults(withoutPrefix) //
				.initialCacheNames(Collections.singleton("second-cache")) //
				.build();

		cm.afterPropertiesSet();

		assertThat(((ValkeyCache) cm.getCache("first-cache")).getCacheConfiguration()).isEqualTo(withPrefix);
		assertThat(((ValkeyCache) cm.getCache("second-cache")).getCacheConfiguration()).isEqualTo(withoutPrefix);
		assertThat(((ValkeyCache) cm.getCache("other-cache")).getCacheConfiguration()).isEqualTo(withoutPrefix);
	}

	@Test // DATAREDIS-481, DATAREDIS-728
	void predefinedCacheShouldBeCreatedWithSpecificConfig() {

		ValkeyCacheConfiguration configuration = ValkeyCacheConfiguration.defaultCacheConfig().disableKeyPrefix();

		ValkeyCacheManager cm = ValkeyCacheManager.builder(cacheWriter)
				.withInitialCacheConfigurations(Collections.singletonMap("predefined-cache", configuration))
				.withInitialCacheConfigurations(Collections.singletonMap("another-predefined-cache", configuration)).build();
		cm.afterPropertiesSet();

		assertThat(((ValkeyCache) cm.getCache("predefined-cache")).getCacheConfiguration()).isEqualTo(configuration);
		assertThat(((ValkeyCache) cm.getCache("another-predefined-cache")).getCacheConfiguration()).isEqualTo(configuration);
		assertThat(cm.getMissingCache("new-cache").getCacheConfiguration()).isNotEqualTo(configuration);
	}

	@Test // DATAREDIS-481
	void transactionAwareCacheManagerShouldDecoracteCache() {

		Cache cache = ValkeyCacheManager.builder(cacheWriter).transactionAware().build()
				.getCache("decoracted-cache");

		assertThat(cache).isInstanceOfAny(TransactionAwareCacheDecorator.class);
		assertThat(ReflectionTestUtils.getField(cache, "targetCache")).isInstanceOf(ValkeyCache.class);
	}

	@Test // DATAREDIS-767
	void lockedCacheManagerShouldPreventInFlightCacheCreation() {

		ValkeyCacheManager cacheManager = ValkeyCacheManager.builder(cacheWriter).disableCreateOnMissingCache().build();
		cacheManager.afterPropertiesSet();

		assertThat(cacheManager.getCache("not-configured")).isNull();
	}

	@Test // DATAREDIS-767
	void lockedCacheManagerShouldStillReturnPreconfiguredCaches() {

		ValkeyCacheManager cacheManager = ValkeyCacheManager.builder(cacheWriter)
				.initialCacheNames(Collections.singleton("configured")).disableCreateOnMissingCache().build();
		cacheManager.afterPropertiesSet();

		assertThat(cacheManager.getCache("configured")).isNotNull();
	}

	@Test // DATAREDIS-935
	void cacheManagerBuilderReturnsConfiguredCaches() {

		ValkeyCacheManagerBuilder cmb = ValkeyCacheManager.builder(cacheWriter)
				.initialCacheNames(Collections.singleton("configured")).disableCreateOnMissingCache();

		assertThat(cmb.getConfiguredCaches()).containsExactly("configured");
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> cmb.getConfiguredCaches().add("another"));
	}

	@Test // DATAREDIS-935
	void cacheManagerBuilderDoesNotAllowSneakingInConfiguration() {

		ValkeyCacheManagerBuilder cmb = ValkeyCacheManager.builder(cacheWriter)
				.initialCacheNames(Collections.singleton("configured")).disableCreateOnMissingCache();

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> cmb.getConfiguredCaches().add("another"));
	}

	@Test // DATAREDIS-935
	void cacheManagerBuilderReturnsConfigurationForKnownCache() {

		ValkeyCacheManagerBuilder cmb = ValkeyCacheManager.builder(cacheWriter)
				.initialCacheNames(Collections.singleton("configured")).disableCreateOnMissingCache();

		assertThat(cmb.getCacheConfigurationFor("configured")).isPresent();
	}

	@Test // DATAREDIS-935
	void cacheManagerBuilderReturnsEmptyOptionalForUnknownCache() {

		ValkeyCacheManagerBuilder cmb = ValkeyCacheManager.builder(cacheWriter)
				.initialCacheNames(Collections.singleton("configured")).disableCreateOnMissingCache();

		assertThat(cmb.getCacheConfigurationFor("unknown")).isNotPresent();
	}

	@Test // DATAREDIS-1118
	void shouldConfigureValkeyCacheWriter() {

		ValkeyCacheWriter writerMock = mock(ValkeyCacheWriter.class);

		ValkeyCacheManager cm = ValkeyCacheManager.builder(cacheWriter).cacheWriter(writerMock).build();

		assertThat(cm).extracting("cacheWriter").isEqualTo(writerMock);
	}

	@Test // DATAREDIS-1118
	void cacheWriterMustNotBeNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> ValkeyCacheManager.builder().cacheWriter(null));
	}

	@Test // DATAREDIS-1118
	void builderShouldRequireCacheWriter() {
		assertThatIllegalStateException().isThrownBy(() -> ValkeyCacheManager.builder().build());
	}

	@Test // DATAREDIS-1082
	void builderSetsStatisticsCollectorWhenEnabled() {

		when(cacheWriter.withStatisticsCollector(any())).thenReturn(cacheWriter);
		ValkeyCacheManager.builder(cacheWriter).enableStatistics().build();

		verify(cacheWriter).withStatisticsCollector(any(DefaultCacheStatisticsCollector.class));
	}

	@Test // DATAREDIS-1082
	void builderWontSetStatisticsCollectorByDefault() {

		ValkeyCacheManager.builder(cacheWriter).build();

		verify(cacheWriter, never()).withStatisticsCollector(any());
	}

	@Test // PR-2583
	void customizeValkeyCacheConfigurationBasedOnDefaultsIsImmutable() {

		ValkeyCacheConfiguration defaultCacheConfiguration = ValkeyCacheConfiguration.defaultCacheConfig()
				.entryTtl(Duration.ofMinutes(30));

		ValkeyCacheManagerBuilder cacheManagerBuilder = ValkeyCacheManager.builder().cacheDefaults(defaultCacheConfiguration);

		ValkeyCacheConfiguration customCacheConfiguration = cacheManagerBuilder.cacheDefaults()
				.entryTtl(Duration.ofSeconds(10))
				.disableKeyPrefix();

		assertThat(customCacheConfiguration).isNotSameAs(defaultCacheConfiguration);
		assertThat(cacheManagerBuilder.cacheDefaults(customCacheConfiguration)).isSameAs(cacheManagerBuilder);
		assertThat(cacheManagerBuilder.cacheDefaults().usePrefix()).isFalse();
		assertThat(cacheManagerBuilder.cacheDefaults().getTtlFunction().getTimeToLive(null, null))
			.isEqualTo(Duration.ofSeconds(10));
		assertThat(defaultCacheConfiguration.usePrefix()).isTrue();
		assertThat(defaultCacheConfiguration.getTtlFunction().getTimeToLive(null, null))
			.isEqualTo(Duration.ofMinutes(30));
	}
}
