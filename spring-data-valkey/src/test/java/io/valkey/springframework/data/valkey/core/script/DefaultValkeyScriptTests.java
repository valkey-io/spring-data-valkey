/*
 * Copyright 2013-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.core.script;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.scripting.support.StaticScriptSource;

/**
 * Test of {@link DefaultValkeyScript}
 *
 * @author Jennifer Hickey
 * @author Christoph Strobl
 * @author Mark Paluch
 */
class DefaultValkeyScriptTests {

	@Test
	void testGetSha1() {

		StaticScriptSource script = new StaticScriptSource("return KEYS[1]");
		DefaultValkeyScript<String> valkeyScript = new DefaultValkeyScript<>();
		valkeyScript.setScriptSource(script);
		valkeyScript.setResultType(String.class);
		String sha1 = valkeyScript.getSha1();
		// Ensure multiple calls return same sha
		assertThat(valkeyScript.getSha1()).isEqualTo(sha1);
		script.setScript("return KEYS[2]");
		// Sha should now be different as script text has changed
		assertThat(sha1).isNotEqualTo(valkeyScript.getSha1());
	}

	@Test
	void testGetScriptAsString() {

		DefaultValkeyScript<String> valkeyScript = new DefaultValkeyScript<>();
		valkeyScript.setScriptText("return ARGS[1]");
		valkeyScript.setResultType(String.class);
		assertThat(valkeyScript.getScriptAsString()).isEqualTo("return ARGS[1]");
	}

	@Test // DATAREDIS-1030
	void testGetScriptAsStringFromResource() {

		ValkeyScript<String> valkeyScript = ValkeyScript
				.of(new ClassPathResource("io/valkey/springframework/data/valkey/core/script/cas.lua"));
		assertThat(valkeyScript.getScriptAsString()).startsWith("local current = redis.call('GET', KEYS[1])");
	}

	@Test
	void testGetScriptAsStringError() {

		DefaultValkeyScript<Long> valkeyScript = new DefaultValkeyScript<>();
		valkeyScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("nonexistent")));
		valkeyScript.setResultType(Long.class);

		assertThatExceptionOfType(ScriptingException.class).isThrownBy(valkeyScript::getScriptAsString);
	}

	@Test
	void initializeWithNoScript() throws Exception {

		DefaultValkeyScript<Long> valkeyScript = new DefaultValkeyScript<>();
		assertThatIllegalStateException().isThrownBy(valkeyScript::afterPropertiesSet);
	}
}
