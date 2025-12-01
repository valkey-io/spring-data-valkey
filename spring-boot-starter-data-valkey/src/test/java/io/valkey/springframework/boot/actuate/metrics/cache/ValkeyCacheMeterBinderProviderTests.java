/*
 * Copyright 2012-2020 the original author or authors.
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

import java.util.Collections;

import io.micrometer.core.instrument.binder.MeterBinder;
import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.cache.ValkeyCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ValkeyCacheMeterBinderProvider}.
 *
 * @author Stephane Nicoll
 */
class ValkeyCacheMeterBinderProviderTests {

	@Test
	void valkeyCacheProvider() {
		ValkeyCache cache = mock(ValkeyCache.class);
		given(cache.getName()).willReturn("test");
		MeterBinder meterBinder = new ValkeyCacheMeterBinderProvider().getMeterBinder(cache, Collections.emptyList());
		assertThat(meterBinder).isInstanceOf(ValkeyCacheMetrics.class);
	}

}
