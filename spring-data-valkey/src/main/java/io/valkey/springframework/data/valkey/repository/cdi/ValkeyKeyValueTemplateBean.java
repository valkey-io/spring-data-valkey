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

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.keyvalue.core.KeyValueOperations;
import io.valkey.springframework.data.valkey.core.ValkeyKeyValueAdapter;
import io.valkey.springframework.data.valkey.core.ValkeyKeyValueTemplate;
import io.valkey.springframework.data.valkey.core.mapping.ValkeyMappingContext;
import org.springframework.util.Assert;

/**
 * {@link CdiBean} to create {@link ValkeyKeyValueTemplate} instances.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
public class ValkeyKeyValueTemplateBean extends CdiBean<KeyValueOperations> {

	private final Bean<ValkeyKeyValueAdapter> keyValueAdapter;

	/**
	 * Creates a new {@link ValkeyKeyValueTemplateBean}.
	 *
	 * @param keyValueAdapter must not be {@literal null}.
	 * @param qualifiers must not be {@literal null}.
	 * @param beanManager must not be {@literal null}.
	 */
	public ValkeyKeyValueTemplateBean(Bean<ValkeyKeyValueAdapter> keyValueAdapter, Set<Annotation> qualifiers,
			BeanManager beanManager) {

		super(qualifiers, KeyValueOperations.class, beanManager);
		Assert.notNull(keyValueAdapter, "KeyValueAdapter bean must not be null");
		this.keyValueAdapter = keyValueAdapter;
	}

	@Override
	public KeyValueOperations create(CreationalContext<KeyValueOperations> creationalContext) {

		ValkeyKeyValueAdapter keyValueAdapter = getDependencyInstance(this.keyValueAdapter, ValkeyKeyValueAdapter.class);

		ValkeyMappingContext valkeyMappingContext = new ValkeyMappingContext();
		valkeyMappingContext.afterPropertiesSet();

		return new ValkeyKeyValueTemplate(keyValueAdapter, valkeyMappingContext);
	}

	@Override
	public void destroy(KeyValueOperations instance, CreationalContext<KeyValueOperations> creationalContext) {

		if (instance.getMappingContext() instanceof DisposableBean) {
			try {
				((DisposableBean) instance.getMappingContext()).destroy();
				instance.destroy();
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		super.destroy(instance, creationalContext);
	}

}
