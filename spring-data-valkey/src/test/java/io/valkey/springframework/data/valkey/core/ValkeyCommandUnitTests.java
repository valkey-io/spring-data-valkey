/*
 * Copyright 2014-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link ValkeyCommand}.
 *
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author Oscar Cai
 * @author John Blum
 */
class ValkeyCommandUnitTests {

	@Test // DATAREDIS-73
	void shouldIdentifyAliasCorrectly() {
		assertThat(ValkeyCommand.CONFIG_SET.isRepresentedBy("setconfig")).isTrue();
	}

	@Test // DATAREDIS-73
	void shouldIdentifyAliasCorrectlyWhenNamePassedInMixedCase() {
		assertThat(ValkeyCommand.CONFIG_SET.isRepresentedBy("SetConfig")).isTrue();
	}

	@Test // DATAREDIS-73
	void shouldNotThrowExceptionWhenUsingNullKeyForRepresentationCheck() {
		assertThat(ValkeyCommand.CONFIG_SET.isRepresentedBy(null)).isFalse();
	}

	@Test // DATAREDIS-73
	void shouldIdentifyAliasCorrectlyViaLookup() {
		assertThat(ValkeyCommand.failsafeCommandLookup("setconfig")).isEqualTo(ValkeyCommand.CONFIG_SET);
	}

	@Test // DATAREDIS-73
	void shouldIdentifyAliasCorrectlyWhenNamePassedInMixedCaseViaLookup() {
		assertThat(ValkeyCommand.failsafeCommandLookup("SetConfig")).isEqualTo(ValkeyCommand.CONFIG_SET);
	}

	@Test // DATAREDIS-73
	void shouldReturnUnknownCommandForUnknownCommandString() {
		assertThat(ValkeyCommand.failsafeCommandLookup("strangecommand")).isEqualTo(ValkeyCommand.UNKNOWN);
	}

	@Test // DATAREDIS-73, DATAREDIS-972, DATAREDIS-1013
	void shouldNotThrowExceptionOnValidArgumentCount() {

		ValkeyCommand.AUTH.validateArgumentCount(1);
		ValkeyCommand.ZADD.validateArgumentCount(3);
		ValkeyCommand.ZADD.validateArgumentCount(4);
		ValkeyCommand.ZADD.validateArgumentCount(5);
		ValkeyCommand.ZADD.validateArgumentCount(100);
		ValkeyCommand.SELECT.validateArgumentCount(1);
	}

	@Test // DATAREDIS-822
	void shouldConsiderMinMaxArguments() {

		ValkeyCommand.BITPOS.validateArgumentCount(2);
		ValkeyCommand.BITPOS.validateArgumentCount(3);
		ValkeyCommand.BITPOS.validateArgumentCount(4);
	}

	@Test // DATAREDIS-822
	void shouldReportArgumentMismatchIfMaxArgumentsExceeded() {
		assertThatIllegalArgumentException().isThrownBy(() -> ValkeyCommand.SELECT.validateArgumentCount(0))
				.withMessageContaining("SELECT command requires 1 argument");
	}

	@Test // DATAREDIS-73
	void shouldThrowExceptionOnInvalidArgumentCountWhenExpectedExactMatch() {
		assertThatIllegalArgumentException().isThrownBy(() -> ValkeyCommand.AUTH.validateArgumentCount(2))
				.withMessageContaining("AUTH command requires 1 argument");
	}

	@Test // DATAREDIS-73
	void shouldThrowExceptionOnInvalidArgumentCountForDelWhenExpectedMinimalMatch() {
		assertThatIllegalArgumentException().isThrownBy(() -> ValkeyCommand.DEL.validateArgumentCount(0))
				.withMessageContaining("DEL command requires at least 1 argument");
	}

	@Test // DATAREDIS-972
	void shouldThrowExceptionOnInvalidArgumentCountForZaddWhenExpectedMinimalMatch() {
		assertThatIllegalArgumentException().isThrownBy(() -> ValkeyCommand.ZADD.validateArgumentCount(2))
				.withMessageContaining("ZADD command requires at least 3 arguments");
	}

	@Test // GH-2644
	void isRepresentedByIsCorrectForAllCommandsAndTheirAliases() {

		for (ValkeyCommand command : ValkeyCommand.values()) {

			assertThat(command.isRepresentedBy(command.name())).isTrue();
			assertThat(command.isRepresentedBy(command.name().toLowerCase())).isTrue();

			for (String alias : command.getAliases()) {
				assertThat(command.isRepresentedBy(alias)).isTrue();
				assertThat(command.isRepresentedBy(alias.toUpperCase())).isTrue();
			}
		}
	}

	@Test // GH-2646
	void commandRequiresArgumentsIsCorrect() {

		Arrays.stream(ValkeyCommand.values())
				.forEach(command -> assertThat(command.requiresArguments())
						.describedAs("Valkey command [%s] failed required arguments check", command)
						.isEqualTo((int) ReflectionTestUtils.getField(command, "minArgs") > 0));
	}

	@Test // GH-2646
	void commandRequiresExactNumberOfArgumentsIsCorrect() {

		Arrays.stream(ValkeyCommand.values())
				.forEach(command -> assertThat(command.requiresExactNumberOfArguments())
						.describedAs("Valkey command [%s] failed requires exact arguments check").isEqualTo(
								ReflectionTestUtils.getField(command, "minArgs") == ReflectionTestUtils.getField(command, "maxArgs")));
	}

}
