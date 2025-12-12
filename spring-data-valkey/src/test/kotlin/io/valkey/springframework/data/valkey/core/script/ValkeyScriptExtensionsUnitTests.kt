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
package io.valkey.springframework.data.valkey.core.script

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource

/**
 * Unit tests for `PartialUpdateExtensions`.
 *
 * @author Mark Paluch
 */
class ValkeyScriptExtensionsUnitTests {

	@Test // GH-2234
	fun shouldCreateValkeyScriptFromString() {

		val update = ValkeyScript<String>("foo")

		assertThat(update.resultType).isEqualTo(String::class.java)
		assertThat(update.scriptAsString).isEqualTo("foo")
	}

	@Test // GH-2234
	fun shouldCreateValkeyScriptFromResource() {

		val update = ValkeyScript<String>(ByteArrayResource("foo".toByteArray()))

		assertThat(update.resultType).isEqualTo(String::class.java)
		assertThat(update.scriptAsString).isEqualTo("foo")
	}

}
