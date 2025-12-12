/*
 * Copyright 2022-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.lettuce;

import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import reactor.core.publisher.Mono;

import io.valkey.springframework.data.valkey.connection.ValkeyConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import org.springframework.lang.Nullable;

/**
 * Factory interface to create {@link RedisCredentialsProvider} from a {@link ValkeyConfiguration}. Credentials can be
 * associated with {@link RedisCredentials#hasUsername() username} and/or {@link RedisCredentials#hasPassword()
 * password}.
 * <p>
 * Credentials are based off the given {@link ValkeyConfiguration} objects. Changing the credentials in the actual object
 * affects the constructed {@link RedisCredentials} object. Credentials are requested by the Lettuce client after
 * connecting to the host. Therefore, credential retrieval is subject to complete within the configured connection
 * creation timeout to avoid connection failures.
 *
 * @author Mark Paluch
 * @since 3.0
 */
public interface ValkeyCredentialsProviderFactory {

	/**
	 * Create a {@link RedisCredentialsProvider} for data node authentication given {@link ValkeyConfiguration}.
	 *
	 * @param valkeyConfiguration the {@link ValkeyConfiguration} object.
	 * @return a {@link RedisCredentialsProvider} that emits {@link RedisCredentials} for data node authentication.
	 */
	@Nullable
	default RedisCredentialsProvider createCredentialsProvider(ValkeyConfiguration valkeyConfiguration) {

		if (valkeyConfiguration instanceof ValkeyConfiguration.WithAuthentication
				&& ((ValkeyConfiguration.WithAuthentication) valkeyConfiguration).getPassword().isPresent()) {

			return RedisCredentialsProvider.from(() -> {

				ValkeyConfiguration.WithAuthentication withAuthentication = (ValkeyConfiguration.WithAuthentication) valkeyConfiguration;

				return RedisCredentials.just(withAuthentication.getUsername(), withAuthentication.getPassword().get());
			});
		}

		return () -> Mono.just(AbsentValkeyCredentials.ANONYMOUS);
	}

	/**
	 * Create a {@link RedisCredentialsProvider} for Sentinel node authentication given
	 * {@link ValkeySentinelConfiguration}.
	 *
	 * @param valkeyConfiguration the {@link ValkeySentinelConfiguration} object.
	 * @return a {@link RedisCredentialsProvider} that emits {@link RedisCredentials} for sentinel authentication.
	 */
	default RedisCredentialsProvider createSentinelCredentialsProvider(ValkeySentinelConfiguration valkeyConfiguration) {

		if (valkeyConfiguration.getSentinelPassword().isPresent()) {

			return RedisCredentialsProvider.from(() -> RedisCredentials.just(valkeyConfiguration.getSentinelUsername(),
					valkeyConfiguration.getSentinelPassword().get()));
		}

		return () -> Mono.just(AbsentValkeyCredentials.ANONYMOUS);
	}

	/**
	 * Default anonymous {@link RedisCredentials} without username/password.
	 */
	enum AbsentValkeyCredentials implements RedisCredentials {

		ANONYMOUS;

		@Override
		@Nullable
		public String getUsername() {
			return null;
		}

		@Override
		public boolean hasUsername() {
			return false;
		}

		@Override
		@Nullable
		public char[] getPassword() {
			return null;
		}

		@Override
		public boolean hasPassword() {
			return false;
		}
	}
}
