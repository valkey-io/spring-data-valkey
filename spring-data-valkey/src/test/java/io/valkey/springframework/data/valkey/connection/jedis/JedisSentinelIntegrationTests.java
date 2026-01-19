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
package io.valkey.springframework.data.valkey.connection.jedis;

import static org.assertj.core.api.Assertions.*;

import redis.clients.jedis.Jedis;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.SettingsUtils;
import io.valkey.springframework.data.valkey.connection.AbstractConnectionIntegrationTests;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyServer;
import io.valkey.springframework.data.valkey.connection.ReturnType;
import io.valkey.springframework.data.valkey.connection.jedis.extension.JedisConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeySentinelAvailable;
import io.valkey.springframework.data.valkey.test.extension.ValkeySentinel;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @author Mark Paluch
 */
@ExtendWith(JedisConnectionFactoryExtension.class)
@EnabledOnValkeySentinelAvailable
public class JedisSentinelIntegrationTests extends AbstractConnectionIntegrationTests {

	private static final ValkeyServer REPLICA_0 = new ValkeyServer("127.0.0.1", 6380);
	private static final ValkeyServer REPLICA_1 = new ValkeyServer("127.0.0.1", 6381);

	public JedisSentinelIntegrationTests(@ValkeySentinel JedisConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	@Test
	public void testEvalArrayScriptError() {
		assertThatExceptionOfType(InvalidDataAccessApiUsageException.class).isThrownBy(() -> {
			// Syntax error
			connection.eval("return {1,2", ReturnType.MULTI, 1, "foo", "bar");
		});
	}

	@Test // DATAREDIS-330
	void shouldReadMastersCorrectly() {

		List<ValkeyServer> servers = (List<ValkeyServer>) connectionFactory.getSentinelConnection().masters();
		assertThat(servers).hasSize(1);
		assertThat(servers.get(0).getName()).isEqualTo(SettingsUtils.getSentinelMaster());
	}

	@Test // DATAREDIS-330
	void shouldReadReplicaOfMastersCorrectly() {

		ValkeySentinelConnection sentinelConnection = connectionFactory.getSentinelConnection();

		List<ValkeyServer> servers = (List<ValkeyServer>) sentinelConnection.masters();
		assertThat(servers).hasSize(1);

		Collection<ValkeyServer> replicas = sentinelConnection.replicas(servers.get(0));
		assertThat(replicas).hasSize(2).contains(REPLICA_0, REPLICA_1);
	}

	@Test // DATAREDIS-552
	void shouldSetClientName() {

		ValkeySentinelConnection sentinelConnection = connectionFactory.getSentinelConnection();
		Jedis jedis = (Jedis) ReflectionTestUtils.getField(sentinelConnection, "jedis");

		assertThat(jedis.clientGetname()).isEqualTo("jedis-client");
	}

	@Test
	@Disabled
	@Override
	public void testRestoreExistingKey() {}

	@Test
	@Disabled
	@Override
	public void testRestoreBadData() {}

	@Test
	@Disabled
	@Override
	public void testEvalShaArrayError() {}

	@Test
	@Disabled
	@Override
	public void testEvalReturnSingleError() {}

	@Test
	@Disabled
	@Override
	public void testEvalShaNotFound() {}

	@Test
	@Disabled
	@Override
	public void testErrorInTx() {}

	@Test
	@Disabled
	@Override
	public void testExecWithoutMulti() {}
}
