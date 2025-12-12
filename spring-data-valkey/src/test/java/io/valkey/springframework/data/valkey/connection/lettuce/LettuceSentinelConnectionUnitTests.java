/*
 * Copyright 2014-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.lettuce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.sentinel.api.StatefulRedisSentinelConnection;
import io.lettuce.core.sentinel.api.sync.RedisSentinelCommands;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeyNode.ValkeyNodeBuilder;
import io.valkey.springframework.data.valkey.connection.ValkeyServer;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LettuceSentinelConnectionUnitTests {

	private static final String MASTER_ID = "mymaster";

	private @Mock RedisClient valkeyClientMock;

	private @Mock StatefulRedisSentinelConnection<String, String> connectionMock;
	private @Mock RedisSentinelCommands<String, String> sentinelCommandsMock;

	private @Mock RedisFuture<List<Map<String, String>>> valkeyFutureMock;

	private LettuceSentinelConnection connection;

	@BeforeEach
	void setUp() {

		when(valkeyClientMock.connectSentinel()).thenReturn(connectionMock);
		when(connectionMock.sync()).thenReturn(sentinelCommandsMock);
		this.connection = new LettuceSentinelConnection(valkeyClientMock);
	}

	@Test // DATAREDIS-348
	void shouldConnectAfterCreation() {
		verify(valkeyClientMock, times(1)).connectSentinel();
	}

	@Test // DATAREDIS-348
	void failoverShouldBeSentCorrectly() {

		connection.failover(new ValkeyNodeBuilder().withName(MASTER_ID).build());
		verify(sentinelCommandsMock, times(1)).failover(eq(MASTER_ID));
	}

	@Test // DATAREDIS-348
	void failoverShouldThrowExceptionIfMasterNodeIsNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> connection.failover(null));
	}

	@Test // DATAREDIS-348
	void failoverShouldThrowExceptionIfMasterNodeNameIsEmpty() {
		assertThatIllegalArgumentException().isThrownBy(() -> connection.failover(new ValkeyNodeBuilder().build()));
	}

	@Test // DATAREDIS-348
	void mastersShouldReadMastersCorrectly() {

		when(sentinelCommandsMock.masters()).thenReturn(Collections.<Map<String, String>> emptyList());
		connection.masters();
		verify(sentinelCommandsMock, times(1)).masters();
	}

	@Test // DATAREDIS-348
	void shouldReadReplicasCorrectly() {

		when(sentinelCommandsMock.slaves(MASTER_ID)).thenReturn(Collections.<Map<String, String>> emptyList());
		connection.slaves(MASTER_ID);
		verify(sentinelCommandsMock, times(1)).slaves(eq(MASTER_ID));
	}

	@Test // DATAREDIS-348
	void shouldReadReplicasCorrectlyWhenGivenNamedNode() {

		when(sentinelCommandsMock.slaves(MASTER_ID)).thenReturn(Collections.<Map<String, String>> emptyList());
		connection.replicas(new ValkeyNodeBuilder().withName(MASTER_ID).build());
		verify(sentinelCommandsMock, times(1)).slaves(eq(MASTER_ID));
	}

	@Test // DATAREDIS-348
	void readReplicasShouldThrowExceptionWhenGivenEmptyMasterName() {
		assertThatIllegalArgumentException().isThrownBy(() -> connection.slaves(""));
	}

	@Test // DATAREDIS-348
	void readReplicasShouldThrowExceptionWhenGivenNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> connection.replicas((ValkeyNode) null));
	}

	@Test // DATAREDIS-348
	void readReplicasShouldThrowExceptionWhenNodeWithoutName() {
		assertThatIllegalArgumentException().isThrownBy(() -> connection.replicas(new ValkeyNodeBuilder().build()));
	}

	@Test // DATAREDIS-348
	void shouldRemoveMasterCorrectlyWhenGivenNamedNode() {

		connection.remove(new ValkeyNodeBuilder().withName(MASTER_ID).build());
		verify(sentinelCommandsMock, times(1)).remove(eq(MASTER_ID));
	}

	@Test // DATAREDIS-348
	void removeShouldThrowExceptionWhenGivenEmptyMasterName() {
		assertThatIllegalArgumentException().isThrownBy(() -> connection.remove(""));
	}

	@Test // DATAREDIS-348
	void removeShouldThrowExceptionWhenGivenNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> connection.remove((ValkeyNode) null));
	}

	@Test // DATAREDIS-348
	void removeShouldThrowExceptionWhenNodeWithoutName() {
		assertThatIllegalArgumentException().isThrownBy(() -> connection.remove(new ValkeyNodeBuilder().build()));
	}

	@Test // DATAREDIS-348
	void monitorShouldBeSentCorrectly() {

		ValkeyServer server = new ValkeyServer("127.0.0.1", 6382);
		server.setName("anothermaster");
		server.setQuorum(3L);

		connection.monitor(server);
		verify(sentinelCommandsMock, times(1)).monitor(eq("anothermaster"), eq("127.0.0.1"), eq(6382), eq(3));
	}
}
