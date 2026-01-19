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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

import org.springframework.beans.factory.DisposableBean;
import io.valkey.springframework.data.valkey.core.ValkeyKeyValueAdapter;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import org.springframework.util.Assert;

/**
 * {@link CdiBean} to create {@link ValkeyKeyValueAdapter} instances.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
public class ValkeyKeyValueAdapterBean extends CdiBean<ValkeyKeyValueAdapter> {

	private final Bean<ValkeyOperations<?, ?>> valkeyOperations;

	/**
	 * Creates a new {@link ValkeyKeyValueAdapterBean}.
	 *
	 * @param valkeyOperations must not be {@literal null}.
	 * @param qualifiers must not be {@literal null}.
	 * @param beanManager must not be {@literal null}.
	 */
	public ValkeyKeyValueAdapterBean(Bean<ValkeyOperations<?, ?>> valkeyOperations, Set<Annotation> qualifiers,
			BeanManager beanManager) {

		super(qualifiers, ValkeyKeyValueAdapter.class, beanManager);
		Assert.notNull(valkeyOperations, "ValkeyOperations Bean must not be null");
		this.valkeyOperations = valkeyOperations;
	}

	@Override
	public ValkeyKeyValueAdapter create(CreationalContext<ValkeyKeyValueAdapter> creationalContext) {

		Type beanType = getBeanType();

		return new ValkeyKeyValueAdapter(getDependencyInstance(this.valkeyOperations, beanType));
	}

	private Type getBeanType() {

		for (Type type : this.valkeyOperations.getTypes()) {
			if (type instanceof Class<?> && ValkeyOperations.class.isAssignableFrom((Class<?>) type)) {
				return type;
			}

			if (type instanceof ParameterizedType parameterizedType) {
				if (parameterizedType.getRawType() instanceof Class<?>
						&& ValkeyOperations.class.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
					return type;
				}
			}
		}
		throw new IllegalStateException("Cannot resolve bean type for class " + ValkeyOperations.class.getName());
	}

	@Override
	public void destroy(ValkeyKeyValueAdapter instance, CreationalContext<ValkeyKeyValueAdapter> creationalContext) {

		if (instance instanceof DisposableBean) {
			try {
				instance.destroy();
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		super.destroy(instance, creationalContext);
	}

}
