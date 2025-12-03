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

import org.springframework.boot.diagnostics.FailureAnalysis;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ValkeyUrlSyntaxFailureAnalyzer}.
 *
 * @author Scott Frederick
 */
class ValkeyUrlSyntaxFailureAnalyzerTests {

	@Test
	void analyzeInvalidUrlSyntax() {
		ValkeyUrlSyntaxException exception = new ValkeyUrlSyntaxException("valkey://invalid");
		FailureAnalysis analysis = new ValkeyUrlSyntaxFailureAnalyzer().analyze(exception);
		assertThat(analysis.getDescription()).contains("The URL 'valkey://invalid' is not valid");
		assertThat(analysis.getAction()).contains("Review the value of the property 'spring.data.valkey.url'");
	}

	@Test
	void analyzeValkeyHttpUrl() {
		ValkeyUrlSyntaxException exception = new ValkeyUrlSyntaxException("http://127.0.0.1:26379/mymaster");
		FailureAnalysis analysis = new ValkeyUrlSyntaxFailureAnalyzer().analyze(exception);
		assertThat(analysis.getDescription()).contains("The URL 'http://127.0.0.1:26379/mymaster' is not valid")
			.contains("The scheme 'http' is not supported");
		assertThat(analysis.getAction()).contains("Use the scheme 'valkey://' for insecure or 'valkeys://' for secure");
	}

	@Test
	void analyzeValkeySentinelUrl() {
		ValkeyUrlSyntaxException exception = new ValkeyUrlSyntaxException(
				"valkey-sentinel://username:password@127.0.0.1:26379,127.0.0.1:26380/mymaster");
		FailureAnalysis analysis = new ValkeyUrlSyntaxFailureAnalyzer().analyze(exception);
		assertThat(analysis.getDescription()).contains(
				"The URL 'valkey-sentinel://username:password@127.0.0.1:26379,127.0.0.1:26380/mymaster' is not valid")
			.contains("The scheme 'valkey-sentinel' is not supported");
		assertThat(analysis.getAction()).contains("Use spring.data.valkey.sentinel properties");
	}

	@Test
	void analyzeValkeySocketUrl() {
		ValkeyUrlSyntaxException exception = new ValkeyUrlSyntaxException("valkey-socket:///valkey/valkey.sock");
		FailureAnalysis analysis = new ValkeyUrlSyntaxFailureAnalyzer().analyze(exception);
		assertThat(analysis.getDescription()).contains("The URL 'valkey-socket:///valkey/valkey.sock' is not valid")
			.contains("The scheme 'valkey-socket' is not supported");
		assertThat(analysis.getAction()).contains("Configure the appropriate Spring Data Valkey connection beans");
	}

}
