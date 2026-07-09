/*
 * Copyright 2026-present the original author or authors.
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
package io.valkey.springframework.data.valkey.util;

/**
 * Utility class for building Spring Data Valkey client library identification
 * strings for Valkey CLIENT SETINFO.
 *
 * @author Viktoriya Kutsarova
 * @see <a href="https://valkey.io/docs/latest/commands/client-setinfo/">Valkey Documentation: CLIENT SETINFO</a>
 * @since 4.1
 */
public final class ValkeyClientLibraryInfo {

	/**
	 * Spring Data Valkey framework name constant for CLIENT SETINFO.
	 */
	public static final String FRAMEWORK_NAME = "spring-data-valkey";

	private static final String UNKNOWN_VERSION = "unknown";

	/**
	 * Get the Spring Data Valkey version from the package manifest.
	 *
	 * @return the Spring Data Valkey version, or "unknown" if the version cannot be determined (e.g. when
	 * running from an IDE or tests without a populated Implementation-Version).
	 */
	public static String getVersion() {
		Package pkg = ValkeyClientLibraryInfo.class.getPackage();
		String version = (pkg != null ? pkg.getImplementationVersion() : null);
		return (version != null ? version : UNKNOWN_VERSION);
	}

	private ValkeyClientLibraryInfo() {
	}
}
