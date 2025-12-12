/*
 * Copyright 2012-2024 the original author or authors.
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

package io.valkey.springframework.boot.actuate.metrics.cache;

import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.cache.ValkeyCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ValkeyCacheMetrics}.
 *
 * @author Stephane Nicoll
 */
class ValkeyCacheMetricsTests {

	private static final Tags TAGS = Tags.of("app", "test").and("cache", "test");

	@Test
	void cacheMetricsCanBeCreated() {
		ValkeyCache cache = mock(ValkeyCache.class);
		given(cache.getName()).willReturn("test");
		ValkeyCacheMetrics cacheMetrics = new ValkeyCacheMetrics(cache, TAGS);
		assertThat(cacheMetrics).isNotNull();
	}

	// The original tests are not run as they require a full cache implementation (i.e. spring-boot-starter-cache-valkey)

	/*
	@Testcontainers(disabledWithoutDocker = true)
	class ValkeyCacheMetricsTests {

		@Container
		static final GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey:latest")
			.withExposedPorts(6379);

		private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValkeyAutoConfiguration.class, CacheAutoConfiguration.class))
			.withUserConfiguration(CachingConfiguration.class)
			.withPropertyValues("spring.data.valkey.host=" + valkey.getHost(),
					"spring.data.valkey.port=" + valkey.getFirstMappedPort());

		@Test
		void cacheStatisticsAreExposed() {
			this.contextRunner.run(withCacheMetrics((cache, meterRegistry) -> {
				assertThat(meterRegistry.find("cache.size").tags(TAGS).functionCounter()).isNull();
				assertThat(meterRegistry.find("cache.gets").tags(TAGS.and("result", "hit")).functionCounter()).isNotNull();
				assertThat(meterRegistry.find("cache.gets").tags(TAGS.and("result", "miss")).functionCounter()).isNotNull();
				assertThat(meterRegistry.find("cache.gets").tags(TAGS.and("result", "pending")).functionCounter())
					.isNotNull();
				assertThat(meterRegistry.find("cache.evictions").tags(TAGS).functionCounter()).isNull();
				assertThat(meterRegistry.find("cache.puts").tags(TAGS).functionCounter()).isNotNull();
				assertThat(meterRegistry.find("cache.removals").tags(TAGS).functionCounter()).isNotNull();
				assertThat(meterRegistry.find("cache.lock.duration").tags(TAGS).timeGauge()).isNotNull();
			}));
		}

		@Test
		void cacheHitsAreExposed() {
			this.contextRunner.run(withCacheMetrics((cache, meterRegistry) -> {
				String key = UUID.randomUUID().toString();
				cache.put(key, "test");

				cache.get(key);
				cache.get(key);
				assertThat(meterRegistry.get("cache.gets").tags(TAGS.and("result", "hit")).functionCounter().count())
					.isEqualTo(2.0d);
			}));
		}

		@Test
		void cacheMissesAreExposed() {
			this.contextRunner.run(withCacheMetrics((cache, meterRegistry) -> {
				String key = UUID.randomUUID().toString();
				cache.get(key);
				cache.get(key);
				cache.get(key);
				assertThat(meterRegistry.get("cache.gets").tags(TAGS.and("result", "miss")).functionCounter().count())
					.isEqualTo(3.0d);
			}));
		}

		@Test
		void cacheMetricsMatchCacheStatistics() {
			this.contextRunner.run((context) -> {
				ValkeyCache cache = getTestCache(context);
				ValkeyCacheMetrics cacheMetrics = new ValkeyCacheMetrics(cache, TAGS);
				assertThat(cacheMetrics.hitCount()).isEqualTo(cache.getStatistics().getHits());
				assertThat(cacheMetrics.missCount()).isEqualTo(cache.getStatistics().getMisses());
				assertThat(cacheMetrics.putCount()).isEqualTo(cache.getStatistics().getPuts());
				assertThat(cacheMetrics.size()).isNull();
				assertThat(cacheMetrics.evictionCount()).isNull();
			});
		}

		private ContextConsumer<AssertableApplicationContext> withCacheMetrics(
				BiConsumer<ValkeyCache, MeterRegistry> stats) {
			return (context) -> {
				ValkeyCache cache = getTestCache(context);
				SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
				new ValkeyCacheMetrics(cache, Tags.of("app", "test")).bindTo(meterRegistry);
				stats.accept(cache, meterRegistry);
			};
		}

		private ValkeyCache getTestCache(AssertableApplicationContext context) {
			assertThat(context).hasSingleBean(ValkeyCacheManager.class);
			ValkeyCacheManager cacheManager = context.getBean(ValkeyCacheManager.class);
			ValkeyCache cache = (ValkeyCache) cacheManager.getCache("test");
			assertThat(cache).isNotNull();
			return cache;
		}

		@Configuration(proxyBeanMethods = false)
		@EnableCaching
		static class CachingConfiguration {

		}
	}
	*/

}
