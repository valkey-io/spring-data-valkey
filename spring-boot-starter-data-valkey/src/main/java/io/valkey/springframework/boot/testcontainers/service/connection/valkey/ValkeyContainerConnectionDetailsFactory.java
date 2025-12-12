/*
 * Copyright 2012-2025 the original author or authors.
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

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails;
import org.testcontainers.containers.Container;

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

	private static final List<String> VALKEY_IMAGE_NAMES = List.of("valkey/valkey");

	private static final int VALKEY_PORT = 6379;

	ValkeyContainerConnectionDetailsFactory() {
		super(VALKEY_IMAGE_NAMES);
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
		public Standalone getStandalone() {
			return Standalone.of(getContainer().getHost(), getContainer().getMappedPort(VALKEY_PORT),
					super.getSslBundle());
		}

	}

}
