/*
 * Copyright 2024-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.XPendingOptions;

/**
 * Unit tests for {@link ValkeyStreamCommands}.
 *
 * @author jinkshower
 */
class ValkeyStreamCommandsUnitTests {

	@Test // GH-2982
	void xPendingOptionsUnboundedShouldThrowExceptionWhenCountIsNegative() {
		assertThatIllegalArgumentException().isThrownBy(() -> XPendingOptions.unbounded(-1L));
	}

	@Test // GH-2982
	void xPendingOptionsRangeShouldThrowExceptionWhenRangeIsNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> XPendingOptions.range(null, 10L));
	}

	@Test // GH-2982
	void xPendingOptionsRangeShouldThrowExceptionWhenCountIsNegative() {

		Range<?> range = Range.closed("0", "10");

		assertThatIllegalArgumentException().isThrownBy(() -> XPendingOptions.range(range, -1L));
	}
}
