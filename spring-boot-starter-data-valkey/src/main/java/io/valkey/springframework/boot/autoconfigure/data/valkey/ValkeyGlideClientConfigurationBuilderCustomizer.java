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

package io.valkey.springframework.boot.autoconfigure.data.valkey;

import io.valkey.springframework.data.valkey.connection.valkeyglide.DefaultValkeyGlideClientConfiguration;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link DefaultValkeyGlideClientConfiguration.ValkeyGlideClientConfigurationBuilder
 * ValkeyGlideClientConfigurationBuilder} via a {@link DefaultValkeyGlideClientConfiguration}
 * using the builder pattern.
 *
 * @author Jeremy Parr-Pearson
 * @since 3.5.1
 */
@FunctionalInterface
public interface ValkeyGlideClientConfigurationBuilderCustomizer {

	/**
	 * Customize the {@link DefaultValkeyGlideClientConfiguration.ValkeyGlideClientConfigurationBuilder}.
	 * @param clientConfigurationBuilder the builder to customize
	 */
	void customize(DefaultValkeyGlideClientConfiguration.ValkeyGlideClientConfigurationBuilder clientConfigurationBuilder);

}
