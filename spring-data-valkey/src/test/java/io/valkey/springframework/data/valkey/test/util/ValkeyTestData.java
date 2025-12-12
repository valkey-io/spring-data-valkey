/*
 * Copyright 2019-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.test.util;

import static org.assertj.core.error.ShouldContain.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.assertj.core.api.AssertProvider;
import org.assertj.core.api.MapAssert;
import org.assertj.core.internal.Failures;

import io.valkey.springframework.data.valkey.core.convert.Bucket;
import io.valkey.springframework.data.valkey.core.convert.ValkeyData;

/**
 * {@link AssertProvider} for {@link ValkeyTestData}.
 *
 * @author Mark Paluch
 */
public class ValkeyTestData implements AssertProvider<ValkeyTestData.ValkeyBucketAssert> {

	private final ValkeyData valkeyData;

	ValkeyTestData(ValkeyData valkeyData) {
		this.valkeyData = valkeyData;
	}

	public static ValkeyTestData from(ValkeyData data) {
		return new ValkeyTestData(data);
	}

	@Override
	public ValkeyBucketAssert assertThat() {
		return new ValkeyBucketAssert(valkeyData);
	}

	public Bucket getBucket() {
		return valkeyData.getBucket();
	}

	public String getId() {
		return valkeyData.getId();
	}

	public ValkeyData getValkeyData() {
		return valkeyData;
	}

	public static class ValkeyBucketAssert extends MapAssert<String, String> {

		private final Failures failures = Failures.instance();

		private final ValkeyData valkeyData;

		ValkeyBucketAssert(ValkeyData valkeyData) {

			super(toStringMap(valkeyData.getBucket().asMap()));
			this.valkeyData = valkeyData;
		}

		/**
		 * Checks for presence of type hint at given path.
		 *
		 * @param path
		 * @param type
		 * @return
		 */
		public ValkeyBucketAssert containingTypeHint(String path, Class<?> type) {

			isNotNull();

			String hint = "Type hint for <%s> at <%s>".formatted(type.getName(), path);

			if (!actual.containsKey(path)) {

				throw failures.failure(info, shouldContain(actual, hint, hint));
			}

			String string = getString(path);

			if (!string.equals(type.getName())) {
				throw failures.failure(info, shouldContain(actual, hint, hint));
			}

			return this;
		}

		/**
		 * Checks for presence of equivalent String value at path.
		 *
		 * @param path
		 * @param value
		 * @return
		 */
		public ValkeyBucketAssert containsEntry(String path, String value) {
			super.containsEntry(path, value);
			return this;
		}

		/**
		 * Checks for presence of equivalent time in msec value at path.
		 *
		 * @param path
		 * @param date
		 * @return
		 */
		public ValkeyBucketAssert containsEntry(String path, Date date) {
			return containsEntry(path, "" + date.getTime());
		}

		/**
		 * Checks given path is not present.
		 *
		 * @param path
		 * @return
		 */
		public ValkeyBucketAssert without(String path) {

			return this;
		}

		private String getString(String path) {
			return actual.get(path);
		}

	}


	private static Map<String, String> toStringMap(Map<String, byte[]> source) {

		Map<String, String> converted = new LinkedHashMap<>();

		source.forEach((k, v) -> converted.put(k, new String(v, StandardCharsets.UTF_8)));

		return converted;
	}

	@Override
	public String toString() {
		return toStringMap(getBucket().asMap()).toString();
	}
}
