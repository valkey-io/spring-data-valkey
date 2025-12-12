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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Marker interface for configuration classes related to Valkey connection setup. As the setup scenarios are quite
 * diverse instead of struggling with unifying those, {@link ValkeyConfiguration} provides means to identify
 * configurations for the individual purposes.
 *
 * @author Christoph Strobl
 * @author Luis De Bello
 * @author Vikas Garg
 * @since 2.1
 */
public interface ValkeyConfiguration {

	/**
	 * Get the configured database index if the current {@link ValkeyConfiguration} is
	 * {@link #isDatabaseIndexAware(ValkeyConfiguration) database aware} or evaluate and return the value of the given
	 * {@link Supplier}.
	 *
	 * @param other a {@code Supplier} whose result is returned if given {@link ValkeyConfiguration} is not
	 *          {@link #isDatabaseIndexAware(ValkeyConfiguration) database aware}.
	 * @return never {@literal null}.
	 * @throws IllegalArgumentException if {@code other} is {@literal null}.
	 */
	default Integer getDatabaseOrElse(Supplier<Integer> other) {
		return getDatabaseOrElse(this, other);
	}

	/**
	 * Get the configured {@link ValkeyPassword} if the current {@link ValkeyConfiguration} is
	 * {@link #isAuthenticationAware(ValkeyConfiguration) password aware} or evaluate and return the value of the given
	 * {@link Supplier}.
	 *
	 * @param other a {@code Supplier} whose result is returned if given {@link ValkeyConfiguration} is not
	 *          {@link #isAuthenticationAware(ValkeyConfiguration) password aware}.
	 * @return never {@literal null}.
	 * @throws IllegalArgumentException if {@code other} is {@literal null}.
	 */
	default ValkeyPassword getPasswordOrElse(Supplier<ValkeyPassword> other) {
		return getPasswordOrElse(this, other);
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @return {@code true} if given {@link ValkeyConfiguration} is instance of {@link WithPassword}.
	 */
	static boolean isAuthenticationAware(@Nullable ValkeyConfiguration configuration) {
		return configuration instanceof WithAuthentication;
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @return {@code true} if given {@link ValkeyConfiguration} is instance of {@link WithDatabaseIndex}.
	 */
	static boolean isDatabaseIndexAware(@Nullable ValkeyConfiguration configuration) {
		return configuration instanceof WithDatabaseIndex;
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @return {@code true} if given {@link ValkeyConfiguration} is instance of {@link SentinelConfiguration}.
	 */
	static boolean isSentinelConfiguration(@Nullable ValkeyConfiguration configuration) {
		return configuration instanceof SentinelConfiguration;
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @return {@code true} if given {@link ValkeyConfiguration} is instance of {@link WithHostAndPort}.
	 * @since 2.1.6
	 */
	static boolean isHostAndPortAware(@Nullable ValkeyConfiguration configuration) {
		return configuration instanceof WithHostAndPort;
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @return {@code true} if given {@link ValkeyConfiguration} is instance of {@link ClusterConfiguration}.
	 */
	static boolean isClusterConfiguration(@Nullable ValkeyConfiguration configuration) {
		return configuration instanceof ClusterConfiguration;
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @return {@code true} if given {@link ValkeyConfiguration} is instance of {@link StaticMasterReplicaConfiguration}.
	 */
	static boolean isStaticMasterReplicaConfiguration(@Nullable ValkeyConfiguration configuration) {
		return configuration instanceof StaticMasterReplicaConfiguration;
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @return {@code true} if given {@link ValkeyConfiguration} is instance of {@link DomainSocketConfiguration}.
	 */
	static boolean isDomainSocketConfiguration(@Nullable ValkeyConfiguration configuration) {
		return configuration instanceof DomainSocketConfiguration;
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @param other a {@code Supplier} whose result is returned if given {@link ValkeyConfiguration} is not
	 *          {@link #isDatabaseIndexAware(ValkeyConfiguration) database aware}.
	 * @return never {@literal null}.
	 * @throws IllegalArgumentException if {@code other} is {@literal null}.
	 */
	static Integer getDatabaseOrElse(@Nullable ValkeyConfiguration configuration, Supplier<Integer> other) {

		Assert.notNull(other, "Other must not be null");
		return isDatabaseIndexAware(configuration) ? ((WithDatabaseIndex) configuration).getDatabase() : other.get();
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @param other a {@code Supplier} whose result is returned if given {@link ValkeyConfiguration} is not
	 *          {@link #isAuthenticationAware(ValkeyConfiguration) password aware}.
	 * @return can be {@literal null}.
	 * @throws IllegalArgumentException if {@code other} is {@literal null}.
	 */
	@Nullable
	static String getUsernameOrElse(@Nullable ValkeyConfiguration configuration, Supplier<String> other) {

		Assert.notNull(other, "Other must not be null");
		return isAuthenticationAware(configuration) ? ((WithAuthentication) configuration).getUsername() : other.get();
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @param other a {@code Supplier} whose result is returned if given {@link ValkeyConfiguration} is not
	 *          {@link #isAuthenticationAware(ValkeyConfiguration) password aware}.
	 * @return never {@literal null}.
	 * @throws IllegalArgumentException if {@code other} is {@literal null}.
	 */
	static ValkeyPassword getPasswordOrElse(@Nullable ValkeyConfiguration configuration, Supplier<ValkeyPassword> other) {

		Assert.notNull(other, "Other must not be null");
		return isAuthenticationAware(configuration) ? ((WithAuthentication) configuration).getPassword() : other.get();
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @param other a {@code Supplier} whose result is returned if given {@link ValkeyConfiguration} is not
	 *          {@link #isHostAndPortAware(ValkeyConfiguration) port aware}.
	 * @return never {@literal null}.
	 * @throws IllegalArgumentException if {@code other} is {@literal null}.
	 * @since 2.1.6
	 */
	static int getPortOrElse(@Nullable ValkeyConfiguration configuration, IntSupplier other) {

		Assert.notNull(other, "Other must not be null");
		return isHostAndPortAware(configuration) ? ((WithHostAndPort) configuration).getPort() : other.getAsInt();
	}

	/**
	 * @param configuration can be {@literal null}.
	 * @param other a {@code Supplier} whose result is returned if given {@link ValkeyConfiguration} is not
	 *          {@link #isHostAndPortAware(ValkeyConfiguration) host aware}.
	 * @return never {@literal null}.
	 * @throws IllegalArgumentException if {@code other} is {@literal null}.
	 * @since 2.1.6
	 */
	static String getHostOrElse(@Nullable ValkeyConfiguration configuration, Supplier<String> other) {

		Assert.notNull(other, "Other must not be null");
		return isHostAndPortAware(configuration) ? ((WithHostAndPort) configuration).getHostName() : other.get();
	}

	/**
	 * {@link ValkeyConfiguration} part suitable for configurations that may use authentication when connecting.
	 *
	 * @author Christoph Strobl
	 * @author Mark Paluch
	 * @since 2.4
	 */
	interface WithAuthentication {

		/**
		 * Create and set a username with the given {@link String}. Requires Valkey 6 or newer.
		 *
		 * @param username the username.
		 */
		void setUsername(@Nullable String username);

		/**
		 * Get the username to use when connecting.
		 *
		 * @return {@literal null} if none set.
		 */
		@Nullable
		String getUsername();

		/**
		 * Create and set a {@link ValkeyPassword} for given {@link String}.
		 *
		 * @param password can be {@literal null}.
		 */
		default void setPassword(@Nullable String password) {
			setPassword(ValkeyPassword.of(password));
		}

		/**
		 * Create and set a {@link ValkeyPassword} for given {@link String}.
		 *
		 * @param password can be {@literal null}.
		 */
		default void setPassword(@Nullable char[] password) {
			setPassword(ValkeyPassword.of(password));
		}

		/**
		 * Create and set a {@link ValkeyPassword} for given {@link String}.
		 *
		 * @param password must not be {@literal null} use {@link ValkeyPassword#none()} instead.
		 */
		void setPassword(ValkeyPassword password);

		/**
		 * Get the ValkeyPassword to use when connecting.
		 *
		 * @return {@link ValkeyPassword#none()} if none set.
		 */
		ValkeyPassword getPassword();
	}

	/**
	 * {@link ValkeyConfiguration} part suitable for configurations that may use authentication when connecting.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	interface WithPassword extends WithAuthentication {

	}

	/**
	 * {@link ValkeyConfiguration} part suitable for configurations that use a specific database.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	interface WithDatabaseIndex {

		/**
		 * Set the database index to use.
		 *
		 * @param dbIndex
		 */
		void setDatabase(int dbIndex);

		/**
		 * Get the database index to use.
		 *
		 * @return {@code zero} by default.
		 */
		int getDatabase();
	}

	/**
	 * {@link ValkeyConfiguration} part suitable for configurations that use host/port combinations for connecting.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	interface WithHostAndPort {

		/**
		 * Set the Valkey server hostname
		 *
		 * @param hostName must not be {@literal null}.
		 */
		void setHostName(String hostName);

		/**
		 * @return never {@literal null}.
		 */
		String getHostName();

		/**
		 * Set the Valkey server port.
		 *
		 * @param port
		 */
		void setPort(int port);

		/**
		 * Get the Valkey server port.
		 *
		 * @return
		 */
		int getPort();
	}

	/**
	 * {@link ValkeyConfiguration} part suitable for configurations that use native domain sockets for connecting.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	interface WithDomainSocket {

		/**
		 * Set the socket.
		 *
		 * @param socket path to the Valkey socket. Must not be {@literal null}.
		 */
		void setSocket(String socket);

		/**
		 * Get the domain socket.
		 *
		 * @return path to the Valkey socket.
		 */
		String getSocket();
	}

	/**
	 * Configuration interface suitable for Valkey cluster environments.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	interface ClusterConfiguration extends WithPassword {

		/**
		 * Returns an {@link Collections#unmodifiableSet(Set) Set} of {@link ValkeyNode cluster nodes}.
		 *
		 * @return {@link Set} of {@link ValkeyNode cluster nodes}. Never {@literal null}.
		 */
		Set<ValkeyNode> getClusterNodes();

		/**
		 * @return max number of redirects to follow or {@literal null} if not set.
		 */
		@Nullable
		Integer getMaxRedirects();

	}

	/**
	 * Configuration interface suitable for single node valkey connections using local unix domain socket.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	interface DomainSocketConfiguration extends WithDomainSocket, WithDatabaseIndex, WithPassword {

	}

	/**
	 * Configuration interface suitable for Valkey Sentinel environments.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	interface SentinelConfiguration extends WithDatabaseIndex, WithPassword {

		/**
		 * Set the name of the master node.
		 *
		 * @param name must not be {@literal null}.
		 */
		default void setMaster(String name) {

			Assert.notNull(name, "Name of sentinel master must not be null");

			setMaster(new SentinelMasterId(name));
		}

		/**
		 * Set the master node.
		 *
		 * @param master must not be {@literal null}.
		 */
		void setMaster(NamedNode master);

		/**
		 * Get the {@literal Sentinel} master node.
		 *
		 * @return get the master node or {@literal null} if not set.
		 */
		@Nullable
		NamedNode getMaster();

		/**
		 * Returns an {@link Collections#unmodifiableSet(Set)} of {@literal Sentinels}.
		 *
		 * @return {@link Set} of sentinels. Never {@literal null}.
		 */
		Set<ValkeyNode> getSentinels();

		/**
		 * Get the username used when authenticating with a Valkey Server.
		 *
		 * @return can be {@literal null} if not set.
		 * @since 2.4
		 */
		@Nullable
		default String getDataNodeUsername() {
			return getUsername();
		}

		/**
		 * Get the {@link ValkeyPassword} used when authenticating with a Valkey Server.
		 *
		 * @return never {@literal null}.
		 * @since 2.2.2
		 */
		default ValkeyPassword getDataNodePassword() {
			return getPassword();
		}

		/**
		 * Create and set a username with the given {@link String}. Requires Valkey 6 or newer.
		 *
		 * @param sentinelUsername the username for sentinel.
		 * @since 2.7
		 */
		void setSentinelUsername(@Nullable String sentinelUsername);

		/**
		 * Get the username to use when connecting.
		 *
		 * @return {@literal null} if none set.
		 * @since 2.7
		 */
		@Nullable
		String getSentinelUsername();

		/**
		 * Create and set a {@link ValkeyPassword} to be used when authenticating with Valkey Sentinel from the given
		 * {@link String}.
		 *
		 * @param password can be {@literal null}.
		 * @since 2.2.2
		 */
		default void setSentinelPassword(@Nullable String password) {
			setSentinelPassword(ValkeyPassword.of(password));
		}

		/**
		 * Create and set a {@link ValkeyPassword} to be used when authenticating with Valkey Sentinel from the given
		 * {@link Character} sequence.
		 *
		 * @param password can be {@literal null}.
		 * @since 2.2.2
		 */
		default void setSentinelPassword(@Nullable char[] password) {
			setSentinelPassword(ValkeyPassword.of(password));
		}

		/**
		 * Set a {@link ValkeyPassword} to be used when authenticating with Valkey Sentinel.
		 *
		 * @param password must not be {@literal null} use {@link ValkeyPassword#none()} instead.
		 * @since 2.2.2
		 */
		void setSentinelPassword(ValkeyPassword password);

		/**
		 * Returns the {@link ValkeyPassword} to use when connecting to a Valkey Sentinel. <br />
		 * Can be set via {@link #setSentinelPassword(ValkeyPassword)} or {@link ValkeyPassword#none()} if no password has
		 * been set.
		 *
		 * @return the {@link ValkeyPassword} for authenticating with Valkey Sentinel.
		 * @since 2.2.2
		 */
		ValkeyPassword getSentinelPassword();

	}

	/**
	 * Configuration interface suitable for Valkey master/replica environments with fixed hosts.
	 *
	 * @author Christoph Strobl
	 * @author Mark Paluch
	 * @since 2.1
	 */
	interface StaticMasterReplicaConfiguration extends WithDatabaseIndex, WithPassword {

		/**
		 * @return unmodifiable {@link List} of {@link ValkeyStandaloneConfiguration nodes}.
		 */
		List<ValkeyStandaloneConfiguration> getNodes();
	}
}
