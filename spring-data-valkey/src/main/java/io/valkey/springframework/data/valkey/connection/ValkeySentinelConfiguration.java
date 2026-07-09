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
package io.valkey.springframework.data.valkey.connection;

import static org.springframework.util.StringUtils.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.core.env.PropertySource;
import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration.SentinelConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ValkeyConfiguration Configuration} class used to set up a {@link ValkeyConnection} with
 * {@link ValkeyConnectionFactory} for connecting to <a href="https://valkey.io/topics/sentinel">Valkey Sentinel(s)</a>.
 * Useful when setting up a highly available Valkey environment.
 *
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author Vikas Garg
 * @author John Blum
 * @author Samuel Klose
 * @author Mustapha Zorgati
 * @since 1.4
 */
public class ValkeySentinelConfiguration implements ValkeyConfiguration, SentinelConfiguration {

	private static final String VALKEY_SENTINEL_MASTER_CONFIG_PROPERTY = "spring.valkey.sentinel.master";
	private static final String VALKEY_SENTINEL_NODES_CONFIG_PROPERTY = "spring.valkey.sentinel.nodes";
	private static final String VALKEY_SENTINEL_USERNAME_CONFIG_PROPERTY = "spring.valkey.sentinel.username";
	private static final String VALKEY_SENTINEL_PASSWORD_CONFIG_PROPERTY = "spring.valkey.sentinel.password";
	private static final String VALKEY_SENTINEL_DATA_NODE_USERNAME_CONFIG_PROPERTY = "spring.valkey.sentinel.dataNode.username";
	private static final String VALKEY_SENTINEL_DATA_NODE_PASSWORD_CONFIG_PROPERTY = "spring.valkey.sentinel.dataNode.password";
	private static final String VALKEY_SENTINEL_DATA_NODE_DATABASE_CONFIG_PROPERTY = "spring.valkey.sentinel.dataNode.database";

	private int database;

	private @Nullable NamedNode master;

	private ValkeyPassword dataNodePassword = ValkeyPassword.none();
	private ValkeyPassword sentinelPassword = ValkeyPassword.none();

	private final Set<ValkeyNode> sentinels = new LinkedHashSet<>();

	private @Nullable String dataNodeUsername = null;
	private @Nullable String sentinelUsername = null;

	/**
	 * Creates a new, default {@code ValkeySentinelConfiguration}.
	 */
	public ValkeySentinelConfiguration() {}

	/**
	 * Creates a new {@code ValkeySentinelConfiguration} for given {@link String hostPort} combinations.
	 *
	 * <pre class="code">
	 * sentinelHostAndPorts[0] = 127.0.0.1:23679
	 * sentinelHostAndPorts[1] = 127.0.0.1:23680 ...
	 * </pre>
	 *
	 * @param sentinelHostAndPorts must not be {@literal null}.
	 * @since 1.5
	 */
	public ValkeySentinelConfiguration(String master, Set<String> sentinelHostAndPorts) {

		Assert.notNull(master, "Sentinel master must not be null");
		Assert.notNull(sentinelHostAndPorts, "Sentinel nodes must not be null");

		this.master = new SentinelMasterId(master);

		for (String hostAndPort : sentinelHostAndPorts) {
			addSentinel(ValkeyNode.fromString(hostAndPort, ValkeyNode.DEFAULT_SENTINEL_PORT));
		}
	}

	/**
	 * Construct a new {@code ValkeySentinelConfiguration} from the given {@link PropertySource}.
	 *
	 * @param propertySource must not be {@literal null}.
	 * @return a new {@code ValkeySentinelConfiguration} configured from the given {@link PropertySource}.
	 * @since 3.3
	 */
	public static ValkeySentinelConfiguration of(PropertySource<?> propertySource) {

		Assert.notNull(propertySource, "PropertySource must not be null");

		ValkeySentinelConfiguration configuration = new ValkeySentinelConfiguration();

		if (propertySource.containsProperty(VALKEY_SENTINEL_MASTER_CONFIG_PROPERTY)) {
			String sentinelMaster = String.valueOf(propertySource.getProperty(VALKEY_SENTINEL_MASTER_CONFIG_PROPERTY));
			configuration.setMaster(sentinelMaster);
		}

		if (propertySource.containsProperty(VALKEY_SENTINEL_NODES_CONFIG_PROPERTY)) {
			String sentinelNodes = String.valueOf(propertySource.getProperty(VALKEY_SENTINEL_NODES_CONFIG_PROPERTY));
			configuration.appendSentinels(commaDelimitedListToSet(sentinelNodes));
		}

		if (propertySource.containsProperty(VALKEY_SENTINEL_PASSWORD_CONFIG_PROPERTY)) {
			String sentinelPassword = String.valueOf(propertySource.getProperty(VALKEY_SENTINEL_PASSWORD_CONFIG_PROPERTY));
			configuration.setSentinelPassword(sentinelPassword);
		}

		if (propertySource.containsProperty(VALKEY_SENTINEL_USERNAME_CONFIG_PROPERTY)) {
			String sentinelUsername = String.valueOf(propertySource.getProperty(VALKEY_SENTINEL_USERNAME_CONFIG_PROPERTY));
			configuration.setSentinelUsername(sentinelUsername);
		}

		if (propertySource.containsProperty(VALKEY_SENTINEL_DATA_NODE_USERNAME_CONFIG_PROPERTY)) {
			String dataNodeUsername = String
					.valueOf(propertySource.getProperty(VALKEY_SENTINEL_DATA_NODE_USERNAME_CONFIG_PROPERTY));
			configuration.setUsername(dataNodeUsername);
		}

		if (propertySource.containsProperty(VALKEY_SENTINEL_DATA_NODE_PASSWORD_CONFIG_PROPERTY)) {
			String dataNodePassword = String
					.valueOf(propertySource.getProperty(VALKEY_SENTINEL_DATA_NODE_PASSWORD_CONFIG_PROPERTY));
			configuration.setPassword(dataNodePassword);
		}

		if (propertySource.containsProperty(VALKEY_SENTINEL_DATA_NODE_DATABASE_CONFIG_PROPERTY)) {
			String databaseSource = String
					.valueOf(propertySource.getProperty(VALKEY_SENTINEL_DATA_NODE_DATABASE_CONFIG_PROPERTY));
			int database;
			try {
				database = Integer.parseInt(databaseSource);
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("Invalid DB index '%s'; integer required".formatted(databaseSource));
			}
			configuration.setDatabase(database);
		}

		return configuration;
	}

	/**
	 * Set {@literal Sentinels} to connect to.
	 *
	 * @param sentinels must not be {@literal null}.
	 */
	public void setSentinels(Iterable<ValkeyNode> sentinels) {

		Assert.notNull(sentinels, "Cannot set sentinels to null");

		this.sentinels.clear();

		for (ValkeyNode sentinel : sentinels) {
			addSentinel(sentinel);
		}
	}

	public Set<ValkeyNode> getSentinels() {
		return Collections.unmodifiableSet(sentinels);
	}

	/**
	 * Add sentinel.
	 *
	 * @param sentinel must not be {@literal null}.
	 */
	public void addSentinel(ValkeyNode sentinel) {

		Assert.notNull(sentinel, "Sentinel must not be null");

		this.sentinels.add(sentinel);
	}

	public void setMaster(NamedNode master) {

		Assert.notNull(master, "Sentinel master node must not be null");

		this.master = master;
	}

	public @Nullable NamedNode getMaster() {
		return master;
	}

	/**
	 * Return the required master node or throw {@link IllegalStateException} if it is not set.
	 *
	 * @return
	 * @since 4.1
	 */
	public NamedNode getRequiredMaster() {

		NamedNode master = getMaster();

		if (master == null) {
			throw new IllegalStateException("Sentinel master node not set");
		}

		return master;
	}

	/**
	 * @see #setMaster(String)
	 * @param master The master node name.
	 * @return this.
	 */
	public ValkeySentinelConfiguration master(String master) {
		this.setMaster(master);
		return this;
	}

	/**
	 * @see #setMaster(NamedNode)
	 * @param master the master node
	 * @return this.
	 */
	public ValkeySentinelConfiguration master(NamedNode master) {
		this.setMaster(master);
		return this;
	}

	/**
	 * @see #addSentinel(ValkeyNode)
	 * @param sentinel the node to add as sentinel.
	 * @return this.
	 */
	public ValkeySentinelConfiguration sentinel(ValkeyNode sentinel) {
		this.addSentinel(sentinel);
		return this;
	}

	/**
	 * @see #sentinel(ValkeyNode)
	 * @param host Valkey sentinel node host name or ip.
	 * @param port Valkey sentinel port.
	 * @return this.
	 */
	public ValkeySentinelConfiguration sentinel(String host, Integer port) {
		return sentinel(new ValkeyNode(host, port));
	}

	private void appendSentinels(Set<String> hostAndPorts) {

		for (String hostAndPort : hostAndPorts) {
			addSentinel(ValkeyNode.fromString(hostAndPort, ValkeyNode.DEFAULT_SENTINEL_PORT));
		}
	}

	@Override
	public int getDatabase() {
		return database;
	}

	@Override
	public void setDatabase(int index) {

		Assert.isTrue(index >= 0, "Invalid DB index '%d'; non-negative index required".formatted(index));

		this.database = index;
	}

	@Override
	public void setUsername(@Nullable String username) {
		this.dataNodeUsername = username;
	}

	@Override
	public @Nullable String getUsername() {
		return this.dataNodeUsername;
	}

	@Override
	public ValkeyPassword getPassword() {
		return dataNodePassword;
	}

	@Override
	public void setPassword(ValkeyPassword password) {

		Assert.notNull(password, "ValkeyPassword must not be null");

		this.dataNodePassword = password;
	}

	@Override
	public @Nullable String getSentinelUsername() {
		return this.sentinelUsername;
	}

	@Override
	public void setSentinelUsername(@Nullable String sentinelUsername) {
		this.sentinelUsername = sentinelUsername;
	}

	@Override
	public void setSentinelPassword(ValkeyPassword sentinelPassword) {

		Assert.notNull(sentinelPassword, "SentinelPassword must not be null");
		this.sentinelPassword = sentinelPassword;
	}

	@Override
	public ValkeyPassword getSentinelPassword() {
		return sentinelPassword;
	}

	@Override
	public boolean equals(@Nullable Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof ValkeySentinelConfiguration that)) {
			return false;
		}

		return this.database == that.database && ObjectUtils.nullSafeEquals(this.master, that.master)
				&& ObjectUtils.nullSafeEquals(this.sentinels, that.sentinels)
				&& ObjectUtils.nullSafeEquals(this.dataNodeUsername, that.dataNodeUsername)
				&& ObjectUtils.nullSafeEquals(this.dataNodePassword, that.dataNodePassword)
				&& ObjectUtils.nullSafeEquals(this.sentinelUsername, that.sentinelUsername)
				&& ObjectUtils.nullSafeEquals(this.sentinelPassword, that.sentinelPassword);
	}

	@Override
	public int hashCode() {

		int result = ObjectUtils.nullSafeHashCode(master);

		result = 31 * result + ObjectUtils.nullSafeHashCode(sentinels);
		result = 31 * result + database;
		result = 31 * result + ObjectUtils.nullSafeHashCode(dataNodeUsername);
		result = 31 * result + ObjectUtils.nullSafeHashCode(dataNodePassword);
		result = 31 * result + ObjectUtils.nullSafeHashCode(sentinelUsername);
		result = 31 * result + ObjectUtils.nullSafeHashCode(sentinelPassword);

		return result;
	}

	/**
	 * @param master must not be {@literal null} or empty.
	 * @param sentinelHostAndPorts must not be {@literal null}.
	 * @return configuration map.
	 */
	private static Map<String, Object> asMap(String master, Set<String> sentinelHostAndPorts) {

		Assert.hasText(master, "Master address must not be null or empty");
		Assert.notNull(sentinelHostAndPorts, "SentinelHostAndPorts must not be null");
		Assert.noNullElements(sentinelHostAndPorts, "ClusterHostAndPorts must not contain null elements");

		Map<String, Object> map = new HashMap<>();

		map.put(VALKEY_SENTINEL_MASTER_CONFIG_PROPERTY, master);
		map.put(VALKEY_SENTINEL_NODES_CONFIG_PROPERTY, StringUtils.collectionToCommaDelimitedString(sentinelHostAndPorts));

		return map;
	}
}
