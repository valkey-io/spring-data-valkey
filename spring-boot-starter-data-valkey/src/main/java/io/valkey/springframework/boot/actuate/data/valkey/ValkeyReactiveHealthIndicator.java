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

import java.util.Properties;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.boot.actuate.health.AbstractReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;

import io.valkey.springframework.data.valkey.connection.ClusterInfo;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyClusterConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;

/**
 * A {@link ReactiveHealthIndicator} for Valkey.
 *
 * @author Stephane Nicoll
 * @author Mark Paluch
 * @author Artsiom Yudovin
 * @author Scott Frederick
 * @since 2.0.0
 */
public class ValkeyReactiveHealthIndicator extends AbstractReactiveHealthIndicator {

	private final ReactiveValkeyConnectionFactory connectionFactory;

	public ValkeyReactiveHealthIndicator(ReactiveValkeyConnectionFactory connectionFactory) {
		super("Valkey health check failed");
		this.connectionFactory = connectionFactory;
	}

	@Override
	protected Mono<Health> doHealthCheck(Health.Builder builder) {
		return getConnection().flatMap((connection) -> doHealthCheck(builder, connection));
	}

	private Mono<ReactiveValkeyConnection> getConnection() {
		return Mono.fromSupplier(this.connectionFactory::getReactiveConnection)
			.subscribeOn(Schedulers.boundedElastic());
	}

	private Mono<Health> doHealthCheck(Health.Builder builder, ReactiveValkeyConnection connection) {
		return getHealth(builder, connection).onErrorResume((ex) -> Mono.just(builder.down(ex).build()))
			.flatMap((health) -> connection.closeLater().thenReturn(health));
	}

	private Mono<Health> getHealth(Health.Builder builder, ReactiveValkeyConnection connection) {
		if (connection instanceof ReactiveValkeyClusterConnection clusterConnection) {
			return clusterConnection.clusterGetClusterInfo().map((info) -> fromClusterInfo(builder, info));
		}
		return connection.serverCommands().info("server").map((info) -> up(builder, info));
	}

	private Health up(Health.Builder builder, Properties info) {
		return ValkeyHealth.up(builder, info).build();
	}

	private Health fromClusterInfo(Health.Builder builder, ClusterInfo clusterInfo) {
		return ValkeyHealth.fromClusterInfo(builder, clusterInfo).build();
	}

}
