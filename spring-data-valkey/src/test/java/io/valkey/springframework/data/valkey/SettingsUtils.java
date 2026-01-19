/*
 * Copyright 2011-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeySocketConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;

/**
 * Utility class exposing connection settings to connect Valkey instances during test execution. Settings can be adjusted
 * by overriding these in {@literal io/valkey/springframework/data/valkey/test.properties}.
 *
 * @author Costin Leau
 * @author Mark Paluch
 * @author John Blum
 */
public abstract class SettingsUtils {

	private static final Properties DEFAULTS = new Properties();
	private static final Properties SETTINGS;

	static {
		DEFAULTS.put("host", "127.0.0.1");
		DEFAULTS.put("port", "6379");
		DEFAULTS.put("clusterPort", "7379");
		DEFAULTS.put("sentinelPort", "26379");
		DEFAULTS.put("socket", "work/valkey-6379.sock");

		SETTINGS = new Properties(DEFAULTS);

		try {
			SETTINGS.load(SettingsUtils.class.getResourceAsStream("/io/valkey/springframework/data/valkey/test.properties"));
		} catch (Exception ignore) {
			throw new IllegalArgumentException("Cannot read settings");
		}
	}

	private SettingsUtils() {}

	/**
	 * @return the Valkey hostname.
	 */
	public static String getHost() {
		return SETTINGS.getProperty("host");
	}

	/**
	 * @return the Valkey port.
	 */
	public static int getPort() {
		return Integer.parseInt(SETTINGS.getProperty("port"));
	}

	/**
	 * @return the Valkey Cluster port.
	 */
	public static int getSentinelPort() {
		return Integer.parseInt(SETTINGS.getProperty("sentinelPort"));
	}

	/**
	 * @return the Valkey Sentinel Master Id.
	 */
	public static String getSentinelMaster() {
		return "mymaster";
	}

	/**
	 * @return the Valkey Cluster port.
	 */
	public static int getClusterPort() {
		return Integer.parseInt(SETTINGS.getProperty("clusterPort"));
	}


	/**
	 * @return path to the unix domain socket.
	 */
	public static String getSocket() {
		return SETTINGS.getProperty("socket");
	}

	/**
	 * Construct a new {@link ValkeyStandaloneConfiguration} initialized with test endpoint settings.
	 *
	 * @return a new {@link ValkeyStandaloneConfiguration} initialized with test endpoint settings.
	 */
	public static ValkeyStandaloneConfiguration standaloneConfiguration() {
		return new ValkeyStandaloneConfiguration(getHost(), getPort());
	}

	/**
	 * Construct a new {@link ValkeySentinelConfiguration} initialized with test endpoint settings.
	 *
	 * @return a new {@link ValkeySentinelConfiguration} initialized with test endpoint settings.
	 */
	public static ValkeySentinelConfiguration sentinelConfiguration() {

		List<String> sentinelHostPorts = List.of("%s:%d".formatted(getHost(), getSentinelPort()),
				"%s:%d".formatted(getHost(), getSentinelPort() + 1));

		return new ValkeySentinelConfiguration(getSentinelMaster(), new HashSet<>(sentinelHostPorts));
	}

	/**
	 * Construct a new {@link ValkeyClusterConfiguration} initialized with test endpoint settings.
	 *
	 * @return a new {@link ValkeyClusterConfiguration} initialized with test endpoint settings.
	 */
	public static ValkeyClusterConfiguration clusterConfiguration() {
		return new ValkeyClusterConfiguration(List.of("%s:%d".formatted(getHost(), getClusterPort())));
	}

	/**
	 * Construct a new {@link ValkeySocketConfiguration} initialized with test endpoint settings.
	 *
	 * @return a new {@link ValkeySocketConfiguration} initialized with test endpoint settings.
	 */
	public static ValkeySocketConfiguration socketConfiguration() {
		return new ValkeySocketConfiguration(getSocket());
	}

}
