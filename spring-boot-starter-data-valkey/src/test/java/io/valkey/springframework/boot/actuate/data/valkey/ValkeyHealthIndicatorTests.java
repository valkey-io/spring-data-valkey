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

package io.valkey.springframework.boot.actuate.data.valkey;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;
import io.valkey.springframework.data.valkey.connection.ClusterInfo;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ValkeyHealthIndicator}.
 *
 * @author Christian Dupuis
 * @author Richard Santana
 * @author Stephane Nicoll
 */
class ValkeyHealthIndicatorTests {

	@Test
	void valkeyIsUp() {
		Properties info = new Properties();
		info.put("redis_version", "8.0.1");
		ValkeyConnection valkeyConnection = mock(ValkeyConnection.class);
		ValkeyServerCommands serverCommands = mock(ValkeyServerCommands.class);
		given(valkeyConnection.serverCommands()).willReturn(serverCommands);
		given(serverCommands.info()).willReturn(info);
		ValkeyHealthIndicator healthIndicator = createHealthIndicator(valkeyConnection);
		Health health = healthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("version", "8.0.1");
	}

	@Test
	void valkeyIsDown() {
		ValkeyConnection valkeyConnection = mock(ValkeyConnection.class);
		ValkeyServerCommands serverCommands = mock(ValkeyServerCommands.class);
		given(valkeyConnection.serverCommands()).willReturn(serverCommands);
		given(serverCommands.info()).willThrow(new ValkeyConnectionFailureException("Connection failed"));
		ValkeyHealthIndicator healthIndicator = createHealthIndicator(valkeyConnection);
		Health health = healthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat((String) health.getDetails().get("error")).contains("Connection failed");
	}

	@Test
	void healthWhenClusterStateIsAbsentShouldBeUp() {
		ValkeyConnectionFactory valkeyConnectionFactory = createClusterConnectionFactory(null);
		ValkeyHealthIndicator healthIndicator = new ValkeyHealthIndicator(valkeyConnectionFactory);
		Health health = healthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("cluster_size", 4L);
		assertThat(health.getDetails()).containsEntry("slots_up", 4L);
		assertThat(health.getDetails()).containsEntry("slots_fail", 0L);
		then(valkeyConnectionFactory).should(atLeastOnce()).getConnection();
	}

	@Test
	void healthWhenClusterStateIsOkShouldBeUp() {
		ValkeyConnectionFactory valkeyConnectionFactory = createClusterConnectionFactory("ok");
		ValkeyHealthIndicator healthIndicator = new ValkeyHealthIndicator(valkeyConnectionFactory);
		Health health = healthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("cluster_size", 4L);
		assertThat(health.getDetails()).containsEntry("slots_up", 4L);
		assertThat(health.getDetails()).containsEntry("slots_fail", 0L);
		then(valkeyConnectionFactory).should(atLeastOnce()).getConnection();
	}

	@Test
	void healthWhenClusterStateIsFailShouldBeDown() {
		ValkeyConnectionFactory valkeyConnectionFactory = createClusterConnectionFactory("fail");
		ValkeyHealthIndicator healthIndicator = new ValkeyHealthIndicator(valkeyConnectionFactory);
		Health health = healthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("cluster_size", 4L);
		assertThat(health.getDetails()).containsEntry("slots_up", 3L);
		assertThat(health.getDetails()).containsEntry("slots_fail", 1L);
		then(valkeyConnectionFactory).should(atLeastOnce()).getConnection();
	}

	private ValkeyHealthIndicator createHealthIndicator(ValkeyConnection valkeyConnection) {
		ValkeyConnectionFactory valkeyConnectionFactory = mock(ValkeyConnectionFactory.class);
		given(valkeyConnectionFactory.getConnection()).willReturn(valkeyConnection);
		return new ValkeyHealthIndicator(valkeyConnectionFactory);
	}

	private ValkeyConnectionFactory createClusterConnectionFactory(String state) {
		Properties clusterProperties = new Properties();
		if (state != null) {
			clusterProperties.setProperty("cluster_state", state);
		}
		clusterProperties.setProperty("cluster_size", "4");
		boolean failure = "fail".equals(state);
		clusterProperties.setProperty("cluster_slots_ok", failure ? "3" : "4");
		clusterProperties.setProperty("cluster_slots_fail", failure ? "1" : "0");
		List<ValkeyClusterNode> valkeyMasterNodes = Arrays.asList(new ValkeyClusterNode("127.0.0.1", 7001),
				new ValkeyClusterNode("127.0.0.2", 7001));
		ValkeyClusterConnection valkeyConnection = mock(ValkeyClusterConnection.class);
		given(valkeyConnection.clusterGetNodes()).willReturn(valkeyMasterNodes);
		given(valkeyConnection.clusterGetClusterInfo()).willReturn(new ClusterInfo(clusterProperties));
		ValkeyConnectionFactory valkeyConnectionFactory = mock(ValkeyConnectionFactory.class);
		given(valkeyConnectionFactory.getConnection()).willReturn(valkeyConnection);
		return valkeyConnectionFactory;
	}

}
