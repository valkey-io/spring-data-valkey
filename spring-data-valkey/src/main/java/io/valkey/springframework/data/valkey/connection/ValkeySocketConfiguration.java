/*
 * Copyright 2017-2025 the original author or authors.
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

import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration.DomainSocketConfiguration;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Configuration class used for setting up {@link ValkeyConnection} via {@link ValkeyConnectionFactory} connecting to
 * single <a href="https://valkey.io/">Valkey</a> using a local unix domain socket.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.1
 */
public class ValkeySocketConfiguration implements ValkeyConfiguration, DomainSocketConfiguration {

	private static final String DEFAULT_SOCKET = "/tmp/valkey.sock";

	private String socket = DEFAULT_SOCKET;
	private int database;
	private @Nullable String username = null;
	private ValkeyPassword password = ValkeyPassword.none();

	/**
	 * Create a new default {@link ValkeySocketConfiguration}.
	 */
	public ValkeySocketConfiguration() {}

	/**
	 * Create a new {@link ValkeySocketConfiguration} given {@code socket}.
	 *
	 * @param socket must not be {@literal null} or empty.
	 */
	public ValkeySocketConfiguration(String socket) {

		Assert.hasText(socket, "Socket path must not be null nor empty");

		this.socket = socket;
	}

	@Override
	public String getSocket() {
		return socket;
	}

	@Override
	public void setSocket(String socket) {

		Assert.hasText(socket, "Socket must not be null nor empty");
		this.socket = socket;
	}

	@Override
	public int getDatabase() {
		return database;
	}

	@Override
	public void setDatabase(int index) {

		Assert.isTrue(index >= 0, () -> "Invalid DB index '%s'; non-negative index required".formatted(index));

		this.database = index;
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
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ValkeySocketConfiguration that)) {
			return false;
		}
		if (database != that.database) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(socket, that.socket)) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(username, that.username)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(password, that.password);
	}

	@Override
	public int hashCode() {
		int result = ObjectUtils.nullSafeHashCode(socket);
		result = 31 * result + database;
		result = 31 * result + ObjectUtils.nullSafeHashCode(username);
		result = 31 * result + ObjectUtils.nullSafeHashCode(password);
		return result;
	}
}
