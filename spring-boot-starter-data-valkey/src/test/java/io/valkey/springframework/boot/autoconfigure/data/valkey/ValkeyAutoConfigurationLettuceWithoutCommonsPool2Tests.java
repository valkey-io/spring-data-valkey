/*
 * Copyright 2012-2023 the original author or authors.
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

package io.valkey.springframework.boot.autoconfigure.data.valkey;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.valkey.springframework.boot.testsupport.classpath.ClassPathExclusions;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettucePoolingClientConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ValkeyAutoConfiguration} when commons-pool2 is not on the classpath.
 *
 * @author Stephane Nicoll
 */
@ClassPathExclusions("commons-pool2-*.jar")
class ValkeyAutoConfigurationLettuceWithoutCommonsPool2Tests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValkeyAutoConfiguration.class))
		.withPropertyValues("spring.data.valkey.client-type:lettuce");

	@Test
	void poolWithoutCommonsPool2IsDisabledByDefault() {
		this.contextRunner.withPropertyValues("spring.data.valkey.host:foo").run((context) -> {
			LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
			assertThat(cf.getHostName()).isEqualTo("foo");
			assertThat(cf.getClientConfiguration()).isNotInstanceOf(LettucePoolingClientConfiguration.class);
		});
	}

}
