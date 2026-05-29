/*
 * Copyright 2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.valkeyglide;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.core.ZSetOperations;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;
import io.valkey.springframework.data.valkey.test.extension.ValkeyCluster;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * Integration tests verifying that ZSet rangeWithScores operations maintain
 * score-based ordering for both standalone and cluster connections.
 *
 * @see <a href="https://github.com/valkey-io/spring-data-valkey/issues/36">Issue #36</a>
 * @see <a href="https://github.com/valkey-io/valkey-glide/issues/4963">Glide #4963</a>
 */
class ValkeyGlideZSetOrderingIntegrationTests {

	@Test
	void rangeWithScoresStandaloneOrder() {
		StringValkeyTemplate template = new StringValkeyTemplate(
				ValkeyGlideConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class));
		try {
			ZSetOperations<String, String> opsForZSet = template.opsForZSet();

			opsForZSet.add("testKey", "value1", 2.0);
			opsForZSet.add("testKey", "value2", 1.0);
			opsForZSet.add("testKey", "value3", 4.0);
			opsForZSet.add("testKey", "value4", 3.0);

			Set<String> rangeResult = opsForZSet.range("testKey", 0, 3);
			String[] rangeArray = rangeResult.toArray(new String[0]);
			assertThat(rangeArray[0]).isEqualTo("value2");
			assertThat(rangeArray[1]).isEqualTo("value1");
			assertThat(rangeArray[2]).isEqualTo("value4");
			assertThat(rangeArray[3]).isEqualTo("value3");

			Set<ZSetOperations.TypedTuple<String>> rangeWithScoresResult = opsForZSet.rangeWithScores("testKey", 0, 3);
			ZSetOperations.TypedTuple<String>[] tuplesArray = rangeWithScoresResult.toArray(new ZSetOperations.TypedTuple[0]);
			assertThat(tuplesArray[0].getValue()).isEqualTo("value2");
			assertThat(tuplesArray[1].getValue()).isEqualTo("value1");
			assertThat(tuplesArray[2].getValue()).isEqualTo("value4");
			assertThat(tuplesArray[3].getValue()).isEqualTo("value3");
		} finally {
			template.delete("testKey");
		}
	}

	@Test
	@EnabledOnValkeyClusterAvailable
	void rangeWithScoresClusterOrder() {
		StringValkeyTemplate template = new StringValkeyTemplate(
				ValkeyGlideConnectionFactoryExtension.getConnectionFactory(ValkeyCluster.class));
		try {
			ZSetOperations<String, String> opsForZSet = template.opsForZSet();

			opsForZSet.add("testKey", "value1", 2.0);
			opsForZSet.add("testKey", "value2", 1.0);
			opsForZSet.add("testKey", "value3", 4.0);
			opsForZSet.add("testKey", "value4", 3.0);

			Set<String> rangeResult = opsForZSet.range("testKey", 0, 3);
			String[] rangeArray = rangeResult.toArray(new String[0]);
			assertThat(rangeArray[0]).isEqualTo("value2");
			assertThat(rangeArray[1]).isEqualTo("value1");
			assertThat(rangeArray[2]).isEqualTo("value4");
			assertThat(rangeArray[3]).isEqualTo("value3");

			Set<ZSetOperations.TypedTuple<String>> rangeWithScoresResult = opsForZSet.rangeWithScores("testKey", 0, 3);
			ZSetOperations.TypedTuple<String>[] tuplesArray = rangeWithScoresResult.toArray(new ZSetOperations.TypedTuple[0]);
			assertThat(tuplesArray[0].getValue()).isEqualTo("value2");
			assertThat(tuplesArray[1].getValue()).isEqualTo("value1");
			assertThat(tuplesArray[2].getValue()).isEqualTo("value4");
			assertThat(tuplesArray[3].getValue()).isEqualTo("value3");
		} finally {
			template.delete("testKey");
		}
	}
}
