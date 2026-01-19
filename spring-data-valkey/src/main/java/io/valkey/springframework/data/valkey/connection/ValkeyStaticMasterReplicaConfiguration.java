/*
 * Copyright 2018-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration.StaticMasterReplicaConfiguration;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Configuration class used for setting up {@link ValkeyConnection} via {@link ValkeyConnectionFactory} using the provided
 * Master / Replica configuration to nodes know to not change address. Eg. when connecting to
 * <a href="https://aws.amazon.com/documentation/elasticache/">AWS ElastiCache with Read Replicas</a>. <br/>
 * Please also note that a Master/Replica connection cannot be used for Pub/Sub operations.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Tamer Soliman
 * @since 2.1
 */
public class ValkeyStaticMasterReplicaConfiguration implements ValkeyConfiguration, StaticMasterReplicaConfiguration {

	private static final int DEFAULT_PORT = 6379;

	private List<ValkeyStandaloneConfiguration> nodes = new ArrayList<>();
	private int database;
	private @Nullable String username = null;
	private ValkeyPassword password = ValkeyPassword.none();

	/**
	 * Create a new {@link StaticMasterReplicaConfiguration} given {@code hostName}.
	 *
	 * @param hostName must not be {@literal null} or empty.
	 */
	public ValkeyStaticMasterReplicaConfiguration(String hostName) {
		this(hostName, DEFAULT_PORT);
	}

	/**
	 * Create a new {@link StaticMasterReplicaConfiguration} given {@code hostName} and {@code port}.
	 *
	 * @param hostName must not be {@literal null} or empty.
	 * @param port a valid TCP port (1-65535).
	 */
	public ValkeyStaticMasterReplicaConfiguration(String hostName, int port) {
		addNode(hostName, port);
	}

	/**
	 * Add a {@link ValkeyStandaloneConfiguration node} to the list of nodes given {@code hostName}.
	 *
	 * @param hostName must not be {@literal null} or empty.
	 * @param port a valid TCP port (1-65535).
	 */
	public void addNode(String hostName, int port) {
		addNode(new ValkeyStandaloneConfiguration(hostName, port));
	}

	/**
	 * Add a {@link ValkeyStandaloneConfiguration node} to the list of nodes.
	 *
	 * @param node must not be {@literal null}.
	 */
	private void addNode(ValkeyStandaloneConfiguration node) {

		Assert.notNull(node, "ValkeyStandaloneConfiguration must not be null");

		node.setPassword(password);
		node.setDatabase(database);
		nodes.add(node);
	}

	/**
	 * Add a {@link ValkeyStandaloneConfiguration node} to the list of nodes given {@code hostName}.
	 *
	 * @param hostName must not be {@literal null} or empty.
	 * @return {@code this} {@link StaticMasterReplicaConfiguration}.
	 */
	public ValkeyStaticMasterReplicaConfiguration node(String hostName) {
		return node(hostName, DEFAULT_PORT);
	}

	/**
	 * Add a {@link ValkeyStandaloneConfiguration node} to the list of nodes given {@code hostName} and {@code port}.
	 *
	 * @param hostName must not be {@literal null} or empty.
	 * @param port a valid TCP port (1-65535).
	 * @return {@code this} {@link StaticMasterReplicaConfiguration}.
	 */
	public ValkeyStaticMasterReplicaConfiguration node(String hostName, int port) {

		addNode(hostName, port);
		return this;
	}

	@Override
	public int getDatabase() {
		return database;
	}

	@Override
	public void setDatabase(int index) {

		Assert.isTrue(index >= 0, "Invalid DB index '%d'; non-negative index required".formatted(index));

		this.database = index;
		this.nodes.forEach(it -> it.setDatabase(database));
	}

	@Override
	public void setUsername(@Nullable String username) {
		this.username = username;
	}

	@Nullable
	@Override
	public String getUsername() {
		return this.username;
	}

	@Override
	public ValkeyPassword getPassword() {
		return password;
	}

	@Override
	public void setPassword(ValkeyPassword password) {

		Assert.notNull(password, "ValkeyPassword must not be null");

		this.password = password;
		this.nodes.forEach(it -> it.setPassword(password));
	}

	@Override
	public List<ValkeyStandaloneConfiguration> getNodes() {
		return Collections.unmodifiableList(nodes);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ValkeyStaticMasterReplicaConfiguration that)) {
			return false;
		}
		if (database != that.database) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(nodes, that.nodes)) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(username, that.username)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(password, that.password);
	}

	@Override
	public int hashCode() {
		int result = ObjectUtils.nullSafeHashCode(nodes);
		result = 31 * result + database;
		result = 31 * result + ObjectUtils.nullSafeHashCode(username);
		result = 31 * result + ObjectUtils.nullSafeHashCode(password);
		return result;
	}
}
