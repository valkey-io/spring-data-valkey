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
package io.valkey.springframework.data.valkey.connection;

import org.springframework.dao.support.PersistenceExceptionTranslator;

/**
 * Thread-safe factory of Valkey connections.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author John Blum
 */
public interface ValkeyConnectionFactory extends PersistenceExceptionTranslator {

	/**
	 * Specifies if pipelined results should be converted to the expected data type.
	 * <p>
	 * If {@literal false}, results of {@link ValkeyConnection#closePipeline()} and {@link ValkeyConnection#exec()} will be
	 * of the type returned by the underlying driver. This method is mostly for backwards compatibility with
	 * {@literal 1.0}. It is generally always a good idea to allow results to be converted and deserialized. In fact, this
	 * is now the default behavior.
	 *
	 * @return {@code true} to convert pipeline and transaction results; {@code false} otherwise.
	 */
	boolean getConvertPipelineAndTxResults();

	/**
	 * Returns a suitable {@link ValkeyConnection connection} for interacting with Valkey.
	 *
	 * @return {@link ValkeyConnection connection} for interacting with Valkey.
	 * @throws IllegalStateException if the connection factory requires initialization and the factory has not yet been
	 *           initialized.
	 */
	ValkeyConnection getConnection();

	/**
	 * Returns a suitable {@link ValkeyClusterConnection connection} for interacting with Valkey Cluster.
	 *
	 * @return a {@link ValkeyClusterConnection connection} for interacting with Valkey Cluster.
	 * @throws IllegalStateException if the connection factory requires initialization and the factory has not yet been
	 *           initialized.
	 * @since 1.7
	 */
	ValkeyClusterConnection getClusterConnection();

	/**
	 * Returns a suitable {@link ValkeySentinelConnection connection} for interacting with Valkey Sentinel.
	 *
	 * @return a {@link ValkeySentinelConnection connection} for interacting with Valkey Sentinel.
	 * @throws IllegalStateException if the connection factory requires initialization and the factory has not yet been
	 *           initialized.
	 * @since 1.4
	 */
	ValkeySentinelConnection getSentinelConnection();

}
