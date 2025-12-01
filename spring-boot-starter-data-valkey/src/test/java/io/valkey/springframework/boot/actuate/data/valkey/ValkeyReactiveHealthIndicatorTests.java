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

import java.time.Duration;
import java.util.Properties;

import io.lettuce.core.RedisConnectionException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;
import io.valkey.springframework.data.valkey.connection.ClusterInfo;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ReactiveServerCommands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ValkeyReactiveHealthIndicator}.
 *
 * @author Stephane Nicoll
 * @author Mark Paluch
 * @author Nikolay Rybak
 * @author Artsiom Yudovin
 * @author Scott Frederick
 */
class ValkeyReactiveHealthIndicatorTests {

	@Test
	void valkeyIsUp() {
		Properties info = new Properties();
		info.put("redis_version", "8.0.1");
		ReactiveValkeyConnection valkeyConnection = mock(ReactiveValkeyConnection.class);
		given(valkeyConnection.closeLater()).willReturn(Mono.empty());
		ReactiveServerCommands commands = mock(ReactiveServerCommands.class);
		given(commands.info("server")).willReturn(Mono.just(info));
		ValkeyReactiveHealthIndicator healthIndicator = createHealthIndicator(valkeyConnection, commands);
		Mono<Health> health = healthIndicator.health();
		StepVerifier.create(health).consumeNextWith((h) -> {
			assertThat(h.getStatus()).isEqualTo(Status.UP);
			assertThat(h.getDetails()).containsOnlyKeys("version");
			assertThat(h.getDetails()).containsEntry("version", "8.0.1");
		}).expectComplete().verify(Duration.ofSeconds(30));
		then(valkeyConnection).should().closeLater();
	}

	@Test
	void healthWhenClusterStateIsAbsentShouldBeUp() {
		ReactiveValkeyConnectionFactory valkeyConnectionFactory = createClusterConnectionFactory(null);
		ValkeyReactiveHealthIndicator healthIndicator = new ValkeyReactiveHealthIndicator(valkeyConnectionFactory);
		Mono<Health> health = healthIndicator.health();
		StepVerifier.create(health).consumeNextWith((h) -> {
			assertThat(h.getStatus()).isEqualTo(Status.UP);
			assertThat(h.getDetails()).containsEntry("cluster_size", 4L);
			assertThat(h.getDetails()).containsEntry("slots_up", 4L);
			assertThat(h.getDetails()).containsEntry("slots_fail", 0L);
		}).expectComplete().verify(Duration.ofSeconds(30));
		then(valkeyConnectionFactory.getReactiveConnection()).should().closeLater();
	}

	@Test
	void healthWhenClusterStateIsOkShouldBeUp() {
		ReactiveValkeyConnectionFactory valkeyConnectionFactory = createClusterConnectionFactory("ok");
		ValkeyReactiveHealthIndicator healthIndicator = new ValkeyReactiveHealthIndicator(valkeyConnectionFactory);
		Mono<Health> health = healthIndicator.health();
		StepVerifier.create(health).consumeNextWith((h) -> {
			assertThat(h.getStatus()).isEqualTo(Status.UP);
			assertThat(h.getDetails()).containsEntry("cluster_size", 4L);
			assertThat(h.getDetails()).containsEntry("slots_up", 4L);
			assertThat(h.getDetails()).containsEntry("slots_fail", 0L);
		}).expectComplete().verify(Duration.ofSeconds(30));
	}

	@Test
	void healthWhenClusterStateIsFailShouldBeDown() {
		ReactiveValkeyConnectionFactory valkeyConnectionFactory = createClusterConnectionFactory("fail");
		ValkeyReactiveHealthIndicator healthIndicator = new ValkeyReactiveHealthIndicator(valkeyConnectionFactory);
		Mono<Health> health = healthIndicator.health();
		StepVerifier.create(health).consumeNextWith((h) -> {
			assertThat(h.getStatus()).isEqualTo(Status.DOWN);
			assertThat(h.getDetails()).containsEntry("slots_up", 3L);
			assertThat(h.getDetails()).containsEntry("slots_fail", 1L);
		}).expectComplete().verify(Duration.ofSeconds(30));
	}

	@Test
	void valkeyCommandIsDown() {
		ReactiveServerCommands commands = mock(ReactiveServerCommands.class);
		given(commands.info("server")).willReturn(Mono.error(new ValkeyConnectionFailureException("Connection failed")));
		ReactiveValkeyConnection valkeyConnection = mock(ReactiveValkeyConnection.class);
		given(valkeyConnection.closeLater()).willReturn(Mono.empty());
		ValkeyReactiveHealthIndicator healthIndicator = createHealthIndicator(valkeyConnection, commands);
		Mono<Health> health = healthIndicator.health();
		StepVerifier.create(health)
			.consumeNextWith((h) -> assertThat(h.getStatus()).isEqualTo(Status.DOWN))
			.expectComplete()
			.verify(Duration.ofSeconds(30));
		then(valkeyConnection).should().closeLater();
	}

	@Test
	void valkeyConnectionIsDown() {
		ReactiveValkeyConnectionFactory valkeyConnectionFactory = mock(ReactiveValkeyConnectionFactory.class);
		given(valkeyConnectionFactory.getReactiveConnection())
			.willThrow(new RedisConnectionException("Unable to connect to localhost:6379"));
		ValkeyReactiveHealthIndicator healthIndicator = new ValkeyReactiveHealthIndicator(valkeyConnectionFactory);
		Mono<Health> health = healthIndicator.health();
		StepVerifier.create(health)
			.consumeNextWith((h) -> assertThat(h.getStatus()).isEqualTo(Status.DOWN))
			.expectComplete()
			.verify(Duration.ofSeconds(30));
	}

	private ValkeyReactiveHealthIndicator createHealthIndicator(ReactiveValkeyConnection valkeyConnection,
			ReactiveServerCommands serverCommands) {
		ReactiveValkeyConnectionFactory valkeyConnectionFactory = mock(ReactiveValkeyConnectionFactory.class);
		given(valkeyConnectionFactory.getReactiveConnection()).willReturn(valkeyConnection);
		given(valkeyConnection.serverCommands()).willReturn(serverCommands);
		return new ValkeyReactiveHealthIndicator(valkeyConnectionFactory);
	}

	private ReactiveValkeyConnectionFactory createClusterConnectionFactory(String state) {
		Properties clusterProperties = new Properties();
		if (state != null) {
			clusterProperties.setProperty("cluster_state", state);
		}
		clusterProperties.setProperty("cluster_size", "4");
		boolean failure = "fail".equals(state);
		clusterProperties.setProperty("cluster_slots_ok", failure ? "3" : "4");
		clusterProperties.setProperty("cluster_slots_fail", failure ? "1" : "0");
		ReactiveValkeyClusterConnection valkeyConnection = mock(ReactiveValkeyClusterConnection.class);
		given(valkeyConnection.closeLater()).willReturn(Mono.empty());
		given(valkeyConnection.clusterGetClusterInfo()).willReturn(Mono.just(new ClusterInfo(clusterProperties)));
		ReactiveValkeyConnectionFactory valkeyConnectionFactory = mock(ReactiveValkeyConnectionFactory.class);
		given(valkeyConnectionFactory.getReactiveConnection()).willReturn(valkeyConnection);
		return valkeyConnectionFactory;
	}

}
