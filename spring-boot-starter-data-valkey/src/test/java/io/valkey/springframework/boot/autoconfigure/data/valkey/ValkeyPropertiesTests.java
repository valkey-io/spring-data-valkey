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

package io.valkey.springframework.boot.autoconfigure.data.valkey;

import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.junit.jupiter.api.Test;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyProperties.Lettuce;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyProperties.ValkeyGlide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ValkeyProperties}.
 *
 * @author Stephane Nicoll
 */
class ValkeyPropertiesTests {

	@Test
	void lettuceDefaultsAreConsistent() {
		Lettuce lettuce = new ValkeyProperties().getLettuce();
		ClusterTopologyRefreshOptions defaultClusterTopologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
			.build();
		assertThat(lettuce.getCluster().getRefresh().isDynamicRefreshSources())
			.isEqualTo(defaultClusterTopologyRefreshOptions.useDynamicRefreshSources());
	}

	@Test
	void sslIsNotEnabledWhenBundleIsEmpty() {
		ValkeyProperties properties = new ValkeyProperties();
		properties.getSsl().setBundle("");
		assertThat(properties.getSsl().isEnabled()).isFalse();
	}


	@Test
	void valkeyGlideDefaultsAreConsistent() {
		ValkeyGlide valkeyGlide = new ValkeyProperties().getValkeyGlide();
		assertThat(valkeyGlide.getConnectionTimeout()).isNull();
		assertThat(valkeyGlide.getReadFrom()).isNull();
		assertThat(valkeyGlide.getInflightRequestsLimit()).isNull();
		assertThat(valkeyGlide.getClientAZ()).isNull();
		assertThat(valkeyGlide.getCluster()).isNotNull();
		assertThat(valkeyGlide.getMaxPoolSize()).isEqualTo(8);
		assertThat(valkeyGlide.getOpenTelemetry()).isNotNull();
		assertThat(valkeyGlide.getOpenTelemetry().isEnabled()).isEqualTo(false);
		assertThat(valkeyGlide.getIamAuthentication()).isNull();
	}
}
