/*
 * Copyright 2015-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.keyvalue.core.KeyValueAdapter;
import org.springframework.data.keyvalue.core.KeyValueCallback;
import org.springframework.data.keyvalue.core.KeyValueTemplate;
import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import io.valkey.springframework.data.valkey.core.convert.ValkeyConverter;
import io.valkey.springframework.data.valkey.core.convert.ValkeyData;
import io.valkey.springframework.data.valkey.core.mapping.ValkeyMappingContext;
import io.valkey.springframework.data.valkey.core.mapping.ValkeyPersistentEntity;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Valkey specific implementation of {@link KeyValueTemplate}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 1.7
 */
public class ValkeyKeyValueTemplate extends KeyValueTemplate {

	private final ValkeyKeyValueAdapter adapter;

	/**
	 * Create new {@link ValkeyKeyValueTemplate}.
	 *
	 * @param adapter must not be {@literal null}.
	 * @param mappingContext must not be {@literal null}.
	 */
	public ValkeyKeyValueTemplate(ValkeyKeyValueAdapter adapter, ValkeyMappingContext mappingContext) {
		super(adapter, mappingContext);
		this.adapter = adapter;
	}

	/**
	 * Obtain the underlying valkey specific {@link org.springframework.data.convert.EntityConverter}.
	 *
	 * @return never {@literal null}.
	 * @since 2.1
	 */
	public ValkeyConverter getConverter() {
		return adapter.getConverter();
	}

	@Override
	public ValkeyMappingContext getMappingContext() {
		return (ValkeyMappingContext) super.getMappingContext();
	}

	/**
	 * Retrieve entities by resolving their {@literal id}s and converting them into required type. <br />
	 * The callback provides either a single {@literal id} or an {@link Iterable} of {@literal id}s, used for retrieving
	 * the actual domain types and shortcuts manual retrieval and conversion of {@literal id}s via {@link ValkeyTemplate}.
	 *
	 * <pre>
	 * <code>
	 * List&#60;ValkeySession&#62; sessions = template.find(new ValkeyCallback&#60;Set&#60;byte[]&#62;&#62;() {
	 *   public Set&#60;byte[]&#60; doInValkey(ValkeyConnection connection) throws DataAccessException {
	 *     return connection
	 *       .sMembers("spring:session:sessions:securityContext.authentication.principal.username:user"
	 *         .getBytes());
	 *   }
	 * }, ValkeySession.class);
	 * </code>
	 * </pre>
	 *
	 * @param callback provides the to retrieve entity ids. Must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return empty list if not elements found.
	 */
	public <T> List<T> find(ValkeyCallback<?> callback, Class<T> type) {

		Assert.notNull(callback, "Callback must not be null");

		return execute(new ValkeyKeyValueCallback<List<T>>() {

			@Override
			public List<T> doInValkey(ValkeyKeyValueAdapter adapter) {

				Object callbackResult = adapter.execute(callback);

				if (callbackResult == null) {
					return Collections.emptyList();
				}

				Iterable<?> ids = ClassUtils.isAssignable(Iterable.class, callbackResult.getClass())
						? (Iterable<?>) callbackResult : Collections.singleton(callbackResult);

				List<T> result = new ArrayList<>();
				for (Object id : ids) {

					String idToUse = adapter.getConverter().getConversionService().canConvert(id.getClass(), String.class)
							? adapter.getConverter().getConversionService().convert(id, String.class) : id.toString();

					findById(idToUse, type).ifPresent(result::add);
				}

				return result;
			}
		});
	}

	@Override
	public <T> T insert(Object id, T objectToInsert) {

		if (objectToInsert instanceof PartialUpdate) {
			doPartialUpdate((PartialUpdate<?>) objectToInsert);
			return objectToInsert;
		}

		if (!(objectToInsert instanceof ValkeyData)) {

			ValkeyConverter converter = adapter.getConverter();

			ValkeyPersistentEntity<?> entity = converter.getMappingContext()
					.getRequiredPersistentEntity(objectToInsert.getClass());

			KeyValuePersistentProperty idProperty = entity.getRequiredIdProperty();
			PersistentPropertyAccessor<T> propertyAccessor = entity.getPropertyAccessor(objectToInsert);

			if (propertyAccessor.getProperty(idProperty) == null) {

				propertyAccessor.setProperty(idProperty, id);
				return super.insert(id, propertyAccessor.getBean());
			}
		}

		return super.insert(id, objectToInsert);
	}

	@Override
	public <T> T update(T objectToUpdate) {

		if (objectToUpdate instanceof PartialUpdate<?> partialUpdate) {
			doPartialUpdate(partialUpdate);

			return objectToUpdate;
		}

		return super.update(objectToUpdate);
	}

	@Override
	public <T> T update(Object id, T objectToUpdate) {
		return super.update(id, objectToUpdate);
	}

	protected void doPartialUpdate(final PartialUpdate<?> update) {

		execute(new ValkeyKeyValueCallback<Void>() {

			@Override
			public Void doInValkey(ValkeyKeyValueAdapter adapter) {

				adapter.update(update);
				return null;
			}
		});
	}

	/**
	 * Valkey specific {@link KeyValueCallback}.
	 *
	 * @author Christoph Strobl
	 * @param <T>
	 * @since 1.7
	 */
	public static abstract class ValkeyKeyValueCallback<T> implements KeyValueCallback<T> {

		@Override
		public T doInKeyValue(KeyValueAdapter adapter) {
			return doInValkey((ValkeyKeyValueAdapter) adapter);
		}

		public abstract T doInValkey(ValkeyKeyValueAdapter adapter);
	}

}
