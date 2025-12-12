/*
 * Copyright 2012-2025 the original author or authors.
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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Import;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.repository.configuration.EnableValkeyRepositories;
import io.valkey.springframework.data.valkey.repository.support.ValkeyRepositoryFactoryBean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Data's Valkey
 * Repositories.
 *
 * @author Eddú Meléndez
 * @author Stephane Nicoll
 * @since 1.4.0
 * @see EnableValkeyRepositories
 */
@AutoConfiguration(after = ValkeyAutoConfiguration.class)
@ConditionalOnClass(EnableValkeyRepositories.class)
@ConditionalOnBean(ValkeyConnectionFactory.class)
@ConditionalOnBooleanProperty(name = "spring.data.valkey.repositories.enabled", matchIfMissing = true)
@ConditionalOnMissingBean(ValkeyRepositoryFactoryBean.class)
@Import(ValkeyRepositoriesRegistrar.class)
public class ValkeyRepositoriesAutoConfiguration {

}
