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
package io.valkey.springframework.data.valkey.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Base class for {@link ValkeyTemplate} implementations defining common properties. Not intended to be used directly.
 *
 * @author Costin Leau
 * @author John Blum
 */
public abstract class ValkeyAccessor implements InitializingBean {

	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());

	private @Nullable ValkeyConnectionFactory connectionFactory;

	@Override
	public void afterPropertiesSet() {
		getRequiredConnectionFactory();
	}

	/**
	 * Returns the factory configured to acquire connections and perform operations on the connected Valkey instance.
	 *
	 * @return the configured {@link ValkeyConnectionFactory}. Can be {@literal null}.
	 * @see ValkeyConnectionFactory
	 */
	@Nullable
	public ValkeyConnectionFactory getConnectionFactory() {
		return this.connectionFactory;
	}

	/**
	 * Returns the required {@link ValkeyConnectionFactory}, throwing an {@link IllegalStateException}
	 * if the {@link ValkeyConnectionFactory} is not set.
	 *
	 * @return the configured {@link ValkeyConnectionFactory}.
	 * @throws IllegalStateException if the {@link ValkeyConnectionFactory} is not set.
	 * @see #getConnectionFactory()
	 * @since 2.0
	 */
	public ValkeyConnectionFactory getRequiredConnectionFactory() {

		ValkeyConnectionFactory connectionFactory = getConnectionFactory();
		Assert.state(connectionFactory != null, "ValkeyConnectionFactory is required");
		return connectionFactory;
	}

	/**
	 * Sets the factory used to acquire connections and perform operations on the connected Valkey instance.
	 *
	 * @param connectionFactory {@link ValkeyConnectionFactory} used to acquire connections.
	 * @see ValkeyConnectionFactory
	 */
	public void setConnectionFactory(@Nullable ValkeyConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}
}
