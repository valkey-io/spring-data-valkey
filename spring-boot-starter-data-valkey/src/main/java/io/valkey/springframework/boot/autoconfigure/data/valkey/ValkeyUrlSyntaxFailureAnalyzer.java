/*
 * Copyright 2012-2022 the original author or authors.
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

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * A {@code FailureAnalyzer} that performs analysis of failures caused by a
 * {@link ValkeyUrlSyntaxException}.
 *
 * @author Scott Frederick
 */
class ValkeyUrlSyntaxFailureAnalyzer extends AbstractFailureAnalyzer<ValkeyUrlSyntaxException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, ValkeyUrlSyntaxException cause) {
		try {
			URI uri = new URI(cause.getUrl());
			if ("valkey-sentinel".equals(uri.getScheme())) {
				return new FailureAnalysis(getUnsupportedSchemeDescription(cause.getUrl(), uri.getScheme()),
						"Use spring.data.valkey.sentinel properties instead of spring.data.valkey.url to configure Valkey sentinel addresses.",
						cause);
			}
			if ("valkey-socket".equals(uri.getScheme())) {
				return new FailureAnalysis(getUnsupportedSchemeDescription(cause.getUrl(), uri.getScheme()),
						"Configure the appropriate Spring Data Valkey connection beans directly instead of setting the property 'spring.data.valkey.url'.",
						cause);
			}
			if (!"valkey".equals(uri.getScheme()) && !"valkeys".equals(uri.getScheme())) {
				return new FailureAnalysis(getUnsupportedSchemeDescription(cause.getUrl(), uri.getScheme()),
						"Use the scheme 'valkey://' for insecure or 'valkeys://' for secure Valkey standalone configuration.",
						cause);
			}
		}
		catch (URISyntaxException ex) {
			// fall through to default description and action
		}
		return new FailureAnalysis(getDefaultDescription(cause.getUrl()),
				"Review the value of the property 'spring.data.valkey.url'.", cause);
	}

	private String getDefaultDescription(String url) {
		return "The URL '" + url + "' is not valid for configuring Spring Data Valkey. ";
	}

	private String getUnsupportedSchemeDescription(String url, String scheme) {
		return getDefaultDescription(url) + "The scheme '" + scheme + "' is not supported.";
	}

}
