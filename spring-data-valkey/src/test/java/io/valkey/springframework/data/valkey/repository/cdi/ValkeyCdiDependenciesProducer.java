/*
 * Copyright 2016-2025 the original author or authors.
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

package io.valkey.springframework.data.valkey.repository.cdi;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

import org.springframework.beans.factory.DisposableBean;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.ValkeyKeyValueAdapter;
import io.valkey.springframework.data.valkey.core.ValkeyKeyValueTemplate;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.mapping.ValkeyMappingContext;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * @author Mark Paluch
 */
public class ValkeyCdiDependenciesProducer {

	/**
	 * Provides a producer method for {@link ValkeyConnectionFactory}.
	 */
	@Produces
	public ValkeyConnectionFactory valkeyConnectionFactory() {
		return ValkeyGlideConnectionFactoryExtension.getConnectionFactory(ValkeyStanalone.class);
	}

	/**
	 * Provides a producer method for {@link ValkeyOperations}.
	 */
	@Produces
	public ValkeyOperations<byte[], byte[]> valkeyOperationsProducer(ValkeyConnectionFactory valkeyConnectionFactory) {

		ValkeyTemplate<byte[], byte[]> template = new ValkeyTemplate<>();
		template.setConnectionFactory(valkeyConnectionFactory);
		template.afterPropertiesSet();
		return template;
	}

	// shortcut for managed KeyValueAdapter/Template.
	@Produces
	@PersonDB
	public ValkeyOperations<byte[], byte[]> valkeyOperationsProducerQualified(ValkeyOperations<byte[], byte[]> instance) {
		return instance;
	}

	public void closeValkeyOperations(@Disposes ValkeyOperations<byte[], byte[]> valkeyOperations) throws Exception {

		if (valkeyOperations instanceof DisposableBean disposableBean) {
			disposableBean.destroy();
		}
	}

	/**
	 * Provides a producer method for {@link ValkeyKeyValueTemplate}.
	 */
	@Produces
	public ValkeyKeyValueTemplate valkeyKeyValueAdapterDefault(ValkeyOperations<?, ?> valkeyOperations) {

		ValkeyKeyValueAdapter valkeyKeyValueAdapter = new ValkeyKeyValueAdapter(valkeyOperations);
		ValkeyKeyValueTemplate keyValueTemplate = new ValkeyKeyValueTemplate(valkeyKeyValueAdapter, new ValkeyMappingContext());
		return keyValueTemplate;
	}

}
