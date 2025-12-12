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

package io.valkey.springframework.boot.testsupport.docker.compose;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyConnectionDetails;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JUnit extension for {@link DockerComposeTest}.
 *
 * @author Andy Wilkinson
 * @author Moritz Halbritter
 */
public class DockerComposeTestExtension implements ParameterResolver {

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		Parameter parameter = parameterContext.getParameter();
		return ValkeyConnectionDetails.class.isAssignableFrom(parameter.getType());
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		Method testMethod = extensionContext.getRequiredTestMethod();
		DockerComposeTest annotation = testMethod.getAnnotation(DockerComposeTest.class);

		@SuppressWarnings("resource")
		GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(annotation.image()))
				.withExposedPorts(6379);

		container.start();

		return new SimpleValkeyConnectionDetails(container.getHost(), container.getMappedPort(6379));
	}

	private static class SimpleValkeyConnectionDetails implements ValkeyConnectionDetails {

		private final String host;
		private final int port;

		SimpleValkeyConnectionDetails(String host, int port) {
			this.host = host;
			this.port = port;
		}

		@Override
		public String getUsername() {
			return null;
		}

		@Override
		public String getPassword() {
			return null;
		}

		@Override
		public Standalone getStandalone() {
			return Standalone.of(host, port);
		}

		@Override
		public Sentinel getSentinel() {
			return null;
		}

		@Override
		public Cluster getCluster() {
			return null;
		}
	}

}
