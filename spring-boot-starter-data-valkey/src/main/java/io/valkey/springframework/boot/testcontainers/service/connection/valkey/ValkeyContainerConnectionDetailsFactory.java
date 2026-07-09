/*
 * Copyright 2012-present the original author or authors.
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

package io.valkey.springframework.boot.testcontainers.service.connection.valkey;

import java.util.List;

import com.redis.testcontainers.RedisContainer;
import com.redis.testcontainers.RedisStackContainer;
import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

/**
 * {@link ContainerConnectionDetailsFactory} to create {@link ValkeyConnectionDetails}
 * from a {@link ServiceConnection @ServiceConnection}-annotated {@link GenericContainer}
 * using the {@code "valkey"} image.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Eddú Meléndez
 */
class ValkeyContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<Container<?>, ValkeyConnectionDetails> {

	private static final List<String> VALKEY_IMAGE_NAMES = List.of("valkey", "valkey/valkey", "redis",
			"redis/redis-stack", "redis/redis-stack-server");

	private static final int VALKEY_PORT = 6379;

	ValkeyContainerConnectionDetailsFactory() {
		super(VALKEY_IMAGE_NAMES);
	}

	@Override
	protected boolean sourceAccepts(ContainerConnectionSource<Container<?>> source, Class<?> requiredContainerType,
			Class<?> requiredConnectionDetailsType) {
		return super.sourceAccepts(source, requiredContainerType, requiredConnectionDetailsType)
				|| source.accepts(ContainerConnectionDetailsFactory.ANY_CONNECTION_NAME, RedisContainer.class,
						requiredConnectionDetailsType)
				|| source.accepts(ContainerConnectionDetailsFactory.ANY_CONNECTION_NAME, RedisStackContainer.class,
						requiredConnectionDetailsType);
	}

	@Override
	protected ValkeyConnectionDetails getContainerConnectionDetails(ContainerConnectionSource<Container<?>> source) {
		return new ValkeyContainerConnectionDetails(source);
	}

	/**
	 * {@link ValkeyConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class ValkeyContainerConnectionDetails extends ContainerConnectionDetails<Container<?>>
			implements ValkeyConnectionDetails {

		private ValkeyContainerConnectionDetails(ContainerConnectionSource<Container<?>> source) {
			super(source);
		}

		@Override
		public @Nullable SslBundle getSslBundle() {
			return super.getSslBundle();
		}

		@Override
		public Standalone getStandalone() {
			return Standalone.of(getContainer().getHost(), getContainer().getMappedPort(VALKEY_PORT));
		}

	}

}
