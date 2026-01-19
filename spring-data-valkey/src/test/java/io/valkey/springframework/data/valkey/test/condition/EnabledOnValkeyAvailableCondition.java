/*
 * Copyright 2020-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.test.condition;

import static org.junit.jupiter.api.extension.ConditionEvaluationResult.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;
import io.valkey.springframework.data.valkey.SettingsUtils;

/**
 * {@link ExecutionCondition} for {@link EnabledOnValkeyAvailableCondition @EnabledOnValkeyAvailable}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @see EnabledOnValkeyAvailableCondition
 */
class EnabledOnValkeyAvailableCondition implements ExecutionCondition {

	private static final ConditionEvaluationResult ENABLED_BY_DEFAULT = enabled(
			"@EnabledOnValkeyAvailable is not present");

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {

		Optional<EnabledOnValkeyAvailable> optional = AnnotationUtils.findAnnotation(context.getElement(),
				EnabledOnValkeyAvailable.class);

		if (!optional.isPresent()) {
			return ENABLED_BY_DEFAULT;
		}

		EnabledOnValkeyAvailable annotation = optional.get();

		try (Socket socket = new Socket()) {

			socket.connect(new InetSocketAddress(SettingsUtils.getHost(), annotation.value()), 100);

			return enabled("Connection successful to Valkey at %s:%d".formatted(SettingsUtils.getHost(),
					annotation.value()));
		} catch (IOException ex) {
			return disabled("Cannot connect to Valkey at %s:%d (%s)".formatted(SettingsUtils.getHost(),
					annotation.value(), ex));
		}
	}

}
