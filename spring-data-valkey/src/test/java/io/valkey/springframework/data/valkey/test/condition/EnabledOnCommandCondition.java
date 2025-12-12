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

import io.lettuce.core.api.StatefulRedisConnection;

import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;
import io.valkey.springframework.data.valkey.test.extension.LettuceExtension;

/**
 * {@link ExecutionCondition} for {@link EnabledOnCommandCondition @EnabledOnCommand}.
 *
 * @see EnabledOnCommandCondition
 * @author Mark Paluch
 * @author Christoph Strobl
 */
class EnabledOnCommandCondition implements ExecutionCondition {

	private static final ConditionEvaluationResult ENABLED_BY_DEFAULT = enabled("@EnabledOnCommand is not present");
	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ValkeyConditions.class);

	private final LettuceExtension lettuceExtension = new LettuceExtension();

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {

		Optional<EnabledOnCommand> optional = AnnotationUtils.findAnnotation(context.getElement(), EnabledOnCommand.class);

		if (!optional.isPresent()) {
			return ENABLED_BY_DEFAULT;
		}

		String command = optional.get().value();

		ExtensionContext.Store store = context.getRoot().getStore(NAMESPACE);
		ValkeyConditions conditions = store.getOrComputeIfAbsent(ValkeyConditions.class, ignore -> {

			try (StatefulRedisConnection connection = lettuceExtension.resolve(context, StatefulRedisConnection.class)) {
				return ValkeyConditions.of(connection);
			}
		}, ValkeyConditions.class);

		boolean hasCommand = conditions.hasCommand(command);
		return hasCommand ? enabled("Enabled on command " + command)
				: disabled("Disabled, command " + command + " not available on Valkey version " + conditions.getValkeyVersion());
	}

}
