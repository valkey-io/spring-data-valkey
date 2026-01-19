/*
 * Copyright 2022-2025 the original author or authors.
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

/**
 * Provides access to {@link ValkeyCommands} and the segregated command interfaces.
 *
 * @author Mark Paluch
 * @since 3.0
 */
public interface ValkeyCommandsProvider {

	/**
	 * Get {@link ValkeyCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 3.0
	 */
	ValkeyCommands commands();

	/**
	 * Get {@link ValkeyGeoCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyGeoCommands geoCommands();

	/**
	 * Get {@link ValkeyHashCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyHashCommands hashCommands();

	/**
	 * Get {@link ValkeyHyperLogLogCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyHyperLogLogCommands hyperLogLogCommands();

	/**
	 * Get {@link ValkeyKeyCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyKeyCommands keyCommands();

	/**
	 * Get {@link ValkeyListCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyListCommands listCommands();

	/**
	 * Get {@link ValkeySetCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeySetCommands setCommands();

	/**
	 * Get {@link ValkeyScriptingCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyScriptingCommands scriptingCommands();

	/**
	 * Get {@link ValkeyServerCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyServerCommands serverCommands();

	/**
	 * Get {@link ValkeyStreamCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.2
	 */
	ValkeyStreamCommands streamCommands();

	/**
	 * Get {@link ValkeyStringCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyStringCommands stringCommands();

	/**
	 * Get {@link ValkeyZSetCommands}.
	 *
	 * @return never {@literal null}.
	 * @since 2.0
	 */
	ValkeyZSetCommands zSetCommands();
}
