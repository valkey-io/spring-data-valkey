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

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.ProcessBean;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.data.keyvalue.core.KeyValueOperations;
import io.valkey.springframework.data.valkey.core.ValkeyKeyValueAdapter;
import io.valkey.springframework.data.valkey.core.ValkeyKeyValueTemplate;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import org.springframework.data.repository.cdi.CdiRepositoryBean;
import org.springframework.data.repository.cdi.CdiRepositoryExtensionSupport;

/**
 * CDI extension to export Valkey repositories. This extension enables Valkey
 * {@link org.springframework.data.repository.Repository} support. It requires either a {@link ValkeyKeyValueTemplate} or a
 * {@link ValkeyOperations} bean. If no {@link ValkeyKeyValueTemplate} or {@link ValkeyKeyValueAdapter} are provided by the
 * user, the extension creates own managed beans.
 *
 * @author Mark Paluch
 */
public class ValkeyRepositoryExtension extends CdiRepositoryExtensionSupport {

	private final Log log = LogFactory.getLog(ValkeyRepositoryExtension.class);
	private final Map<Set<Annotation>, Bean<ValkeyKeyValueAdapter>> valkeyKeyValueAdapters = new HashMap<>();
	private final Map<Set<Annotation>, Bean<KeyValueOperations>> valkeyKeyValueTemplates = new HashMap<>();
	private final Map<Set<Annotation>, Bean<ValkeyOperations<?, ?>>> valkeyOperations = new HashMap<>();

	public ValkeyRepositoryExtension() {
		log.info("Activating CDI extension for Spring Data Valkey repositories.");
	}

	/**
	 * Pick up existing bean definitions that are required for a Repository to work.
	 *
	 * @param processBean
	 * @param <X>
	 */
	@SuppressWarnings("unchecked")
	<X> void processBean(@Observes ProcessBean<X> processBean) {

		Bean<X> bean = processBean.getBean();

		for (Type type : bean.getTypes()) {
			Type beanType = type;

			if (beanType instanceof ParameterizedType) {
				beanType = ((ParameterizedType) beanType).getRawType();
			}

			if (beanType instanceof Class<?> && ValkeyKeyValueTemplate.class.isAssignableFrom((Class<?>) beanType)) {
				if (log.isDebugEnabled()) {
					log.debug("Discovered %s with qualifiers %s.".formatted(ValkeyKeyValueTemplate.class.getName(),
							bean.getQualifiers()));
				}

				// Store the Key-Value Templates bean using its qualifiers.
				valkeyKeyValueTemplates.put(new HashSet<>(bean.getQualifiers()), (Bean<KeyValueOperations>) bean);
			}

			if (beanType instanceof Class<?> && ValkeyKeyValueAdapter.class.isAssignableFrom((Class<?>) beanType)) {
				if (log.isDebugEnabled()) {
					log.debug("Discovered %s with qualifiers %s.".formatted(ValkeyKeyValueAdapter.class.getName(),
							bean.getQualifiers()));
				}

				// Store the ValkeyKeyValueAdapter bean using its qualifiers.
				valkeyKeyValueAdapters.put(new HashSet<>(bean.getQualifiers()), (Bean<ValkeyKeyValueAdapter>) bean);
			}

			if (beanType instanceof Class<?> && ValkeyOperations.class.isAssignableFrom((Class<?>) beanType)) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Discovered %s with qualifiers %s.".formatted(ValkeyOperations.class.getName(),
							bean.getQualifiers()));
				}

				// Store the ValkeyOperations bean using its qualifiers.
				valkeyOperations.put(new HashSet<>(bean.getQualifiers()), (Bean<ValkeyOperations<?, ?>>) bean);
			}
		}
	}

	void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {

		registerDependenciesIfNecessary(afterBeanDiscovery, beanManager);

		for (Entry<Class<?>, Set<Annotation>> entry : getRepositoryTypes()) {

			Class<?> repositoryType = entry.getKey();
			Set<Annotation> qualifiers = entry.getValue();

			// Create the bean representing the repository.
			CdiRepositoryBean<?> repositoryBean = createRepositoryBean(repositoryType, qualifiers, beanManager);

			if (log.isInfoEnabled()) {
				log.info("Registering bean for %s with qualifiers %s.".formatted(repositoryType.getName(), qualifiers));
			}

			// Register the bean to the container.
			registerBean(repositoryBean);
			afterBeanDiscovery.addBean(repositoryBean);
		}
	}

	/**
	 * Register {@link ValkeyKeyValueAdapter} and {@link ValkeyKeyValueTemplate} if these beans are not provided by the CDI
	 * application.
	 *
	 * @param afterBeanDiscovery
	 * @param beanManager
	 */
	private void registerDependenciesIfNecessary(@Observes AfterBeanDiscovery afterBeanDiscovery,
			BeanManager beanManager) {

		for (Entry<Class<?>, Set<Annotation>> entry : getRepositoryTypes()) {

			Set<Annotation> qualifiers = entry.getValue();

			if (!valkeyKeyValueAdapters.containsKey(qualifiers)) {
				if (log.isInfoEnabled()) {
					log.info("Registering bean for %s with qualifiers %s.".formatted(ValkeyKeyValueAdapter.class.getName(),
							qualifiers));
				}
				ValkeyKeyValueAdapterBean valkeyKeyValueAdapterBean = createValkeyKeyValueAdapterBean(qualifiers, beanManager);
				valkeyKeyValueAdapters.put(qualifiers, valkeyKeyValueAdapterBean);
				afterBeanDiscovery.addBean(valkeyKeyValueAdapterBean);
			}

			if (!valkeyKeyValueTemplates.containsKey(qualifiers)) {
				if (log.isInfoEnabled()) {
					log.info("Registering bean for %s with qualifiers %s.".formatted(ValkeyKeyValueTemplate.class.getName(),
							qualifiers));
				}

				ValkeyKeyValueTemplateBean valkeyKeyValueTemplateBean = createValkeyKeyValueTemplateBean(qualifiers, beanManager);
				valkeyKeyValueTemplates.put(qualifiers, valkeyKeyValueTemplateBean);
				afterBeanDiscovery.addBean(valkeyKeyValueTemplateBean);
			}
		}
	}

	/**
	 * Creates a {@link CdiRepositoryBean} for the repository of the given type, requires a {@link KeyValueOperations}
	 * bean with the same qualifiers.
	 *
	 * @param <T> the type of the repository.
	 * @param repositoryType the class representing the repository.
	 * @param qualifiers the qualifiers to be applied to the bean.
	 * @param beanManager the BeanManager instance.
	 * @return
	 */
	private <T> CdiRepositoryBean<T> createRepositoryBean(Class<T> repositoryType, Set<Annotation> qualifiers,
			BeanManager beanManager) {

		// Determine the KeyValueOperations bean which matches the qualifiers of the repository.
		Bean<KeyValueOperations> valkeyKeyValueTemplate = this.valkeyKeyValueTemplates.get(qualifiers);

		if (valkeyKeyValueTemplate == null) {
			throw new UnsatisfiedResolutionException("Unable to resolve a bean for '%s' with qualifiers %s"
					.formatted(ValkeyKeyValueTemplate.class.getName(), qualifiers));
		}

		// Construct and return the repository bean.
		return new ValkeyRepositoryBean<>(valkeyKeyValueTemplate, qualifiers, repositoryType, beanManager,
				getCustomImplementationDetector());
	}

	/**
	 * Creates a {@link ValkeyKeyValueAdapterBean}, requires a {@link ValkeyOperations} bean with the same qualifiers.
	 *
	 * @param qualifiers the qualifiers to be applied to the bean.
	 * @param beanManager the BeanManager instance.
	 * @return
	 */
	private ValkeyKeyValueAdapterBean createValkeyKeyValueAdapterBean(Set<Annotation> qualifiers, BeanManager beanManager) {

		// Determine the ValkeyOperations bean which matches the qualifiers of the repository.
		Bean<ValkeyOperations<?, ?>> valkeyOperationsBean = this.valkeyOperations.get(qualifiers);

		if (valkeyOperationsBean == null) {
			throw new UnsatisfiedResolutionException("Unable to resolve a bean for '%s' with qualifiers %s."
					.formatted(ValkeyOperations.class.getName(), qualifiers));
		}

		// Construct and return the repository bean.
		return new ValkeyKeyValueAdapterBean(valkeyOperationsBean, qualifiers, beanManager);
	}

	/**
	 * Creates a {@link ValkeyKeyValueTemplateBean}, requires a {@link ValkeyKeyValueAdapter} bean with the same qualifiers.
	 *
	 * @param qualifiers the qualifiers to be applied to the bean.
	 * @param beanManager the BeanManager instance.
	 * @return
	 */
	private ValkeyKeyValueTemplateBean createValkeyKeyValueTemplateBean(Set<Annotation> qualifiers,
			BeanManager beanManager) {

		// Determine the ValkeyKeyValueAdapter bean which matches the qualifiers of the repository.
		Bean<ValkeyKeyValueAdapter> valkeyKeyValueAdapterBean = this.valkeyKeyValueAdapters.get(qualifiers);

		if (valkeyKeyValueAdapterBean == null) {
			throw new UnsatisfiedResolutionException("Unable to resolve a bean for '%s' with qualifiers %s"
					.formatted(ValkeyKeyValueAdapter.class.getName(), qualifiers));
		}

		// Construct and return the repository bean.
		return new ValkeyKeyValueTemplateBean(valkeyKeyValueAdapterBean, qualifiers, beanManager);
	}

}
