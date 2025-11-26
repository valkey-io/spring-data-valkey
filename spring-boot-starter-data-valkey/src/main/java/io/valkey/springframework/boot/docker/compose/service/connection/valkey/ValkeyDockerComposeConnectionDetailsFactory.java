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

package io.valkey.springframework.boot.docker.compose.service.connection.valkey;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * {@link DockerComposeConnectionDetailsFactory} to create {@link ValkeyConnectionDetails}
 * for a {@code valkey} service.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Scott Frederick
 * @author Eddú Meléndez
 */
class ValkeyDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<ValkeyConnectionDetails> {

	private static final String[] VALKEY_CONTAINER_NAMES = { "valkey/valkey" };

	private static final int VALKEY_PORT = 6379;

	ValkeyDockerComposeConnectionDetailsFactory() {
		super(VALKEY_CONTAINER_NAMES);
	}

	@Override
	protected ValkeyConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new ValkeyDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link ValkeyConnectionDetails} backed by a {@code valkey} {@link RunningService}.
	 */
	static class ValkeyDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements ValkeyConnectionDetails {

		private final Standalone standalone;

		ValkeyDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.standalone = Standalone.of(service.host(), service.ports().get(VALKEY_PORT), getSslBundle(service));
		}

		@Override
		public Standalone getStandalone() {
			return this.standalone;
		}

	}

}
