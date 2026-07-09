/*
 * Copyright 2014-present the original author or authors.
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

import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.valkey.springframework.data.valkey.connection.NamedNode;
import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelCommands;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyServer;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;

/**
 * {@link ValkeySentinelConnection} implementation on top of {@link Jedis}.
 *
 * @author Christoph Strobl
 * @since 1.4
 */
public class JedisSentinelConnection implements ValkeySentinelConnection {

	private final Jedis jedis;

	/**
	 * Create new {@link JedisSentinelConnection} using given {@link ValkeyNode} as sentinel.
	 *
	 * @param sentinel the {@link ValkeyNode} sentinel to connect to.
	 */
	public JedisSentinelConnection(ValkeyNode sentinel) {

		Assert.notNull(sentinel.getHost(), "Sentinel.getHost() must not be null");
		Assert.notNull(sentinel.getPort(), "Sentinel.getHost() must not be null");

		this.jedis = new Jedis(sentinel.getRequiredHost(), sentinel.getPort());
	}

	/**
	 * Create new {@link JedisSentinelConnection} using given host and port.
	 *
	 * @param host must not be {@literal null} or empty.
	 * @param port
	 */
	public JedisSentinelConnection(String host, int port) {
		this(new Jedis(host, port));
	}

	/**
	 * Create new {@link JedisSentinelConnection} using given {@link Jedis} instance.
	 *
	 * @param jedis
	 */
	public JedisSentinelConnection(Jedis jedis) {

		Assert.notNull(jedis, "Cannot created JedisSentinelConnection using 'null' as client");
		this.jedis = jedis;
		init();
	}

	@Override
	public void failover(NamedNode master) {

		Assert.notNull(master, "Valkey node master must not be 'null' for failover");
		Assert.hasText(master.getName(), "Valkey master name must not be 'null' or empty for failover");
		jedis.sentinelFailover(master.getName());
	}

	@Override
	public List<ValkeyServer> masters() {
		return JedisConverters.toListOfValkeyServer(jedis.sentinelMasters());
	}

	@Override
	public List<ValkeyServer> replicas(NamedNode master) {

		Assert.notNull(master, "Master node cannot be 'null' when loading replicas");
		Assert.notNull(master.getName(), "Master node cannot be 'null' when loading replicas");

		return replicas(master.getName());
	}

	/**
	 * @param masterName
	 * @see ValkeySentinelCommands#replicas(NamedNode)
	 * @return
	 */
	public List<ValkeyServer> replicas(String masterName) {

		Assert.hasText(masterName, "Name of valkey master cannot be 'null' or empty when loading replicas");
		return JedisConverters.toListOfValkeyServer(jedis.sentinelReplicas(masterName));
	}

	@Override
	public void remove(NamedNode master) {

		Assert.notNull(master, "Master node cannot be 'null' when trying to remove");
		remove(master.getName());
	}

	/**
	 * @param masterName
	 * @see ValkeySentinelCommands#remove(NamedNode)
	 */
	@Contract("null -> fail")
	public void remove(@Nullable String masterName) {

		Assert.hasText(masterName, "Name of valkey master cannot be 'null' or empty when trying to remove");
		jedis.sentinelRemove(masterName);
	}

	@Override
	public void monitor(ValkeyServer server) {

		Assert.notNull(server, "Cannot monitor 'null' server");
		Assert.hasText(server.getName(), "Name of server to monitor must not be 'null' or empty");
		Assert.hasText(server.getHost(), "Host must not be 'null' for server to monitor");
		Assert.notNull(server.getPort(), "Port must not be 'null' for server to monitor");
		Assert.notNull(server.getQuorum(), "Quorum must not be 'null' for server to monitor");

		jedis.sentinelMonitor(server.getName(), server.getRequiredHost(), server.getRequiredPort(),
				server.getQuorum().intValue());
	}

	@Override
	public void close() throws IOException {
		jedis.close();
	}

	private void init() {
		if (!jedis.isConnected()) {
			doInit(jedis);
		}
	}

	/**
	 * Do whatever is required to establish the connection to valkey.
	 *
	 * @param jedis
	 */
	protected void doInit(Jedis jedis) {
		jedis.connect();
	}

	@Override
	public boolean isOpen() {
		return jedis.isConnected();
	}

}
