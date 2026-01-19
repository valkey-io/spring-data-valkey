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
package io.valkey.springframework.data.valkey.listener.adapter;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyAvailable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Costin Leau
 * @author Jennifer Hickey
 */
@ExtendWith(SpringExtension.class)
@EnabledOnValkeyAvailable
@ContextConfiguration("/io/valkey/springframework/data/valkey/listener/container.xml")
class ContainerXmlSetupIntegrationTests {

	@Autowired ValkeyMessageListenerContainer container;

	@Test
	void testContainerSetup() {
		assertThat(container.isRunning()).isTrue();
	}
}
