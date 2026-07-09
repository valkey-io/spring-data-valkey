/*
 * Copyright 2026-present the original author or authors.
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
package io.valkey.springframework.data.valkey.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import io.valkey.springframework.data.valkey.config.MethodValkeyListenerEndpoint;
import io.valkey.springframework.data.valkey.config.ValkeyListenerConfigUtils;
import io.valkey.springframework.data.valkey.config.ValkeyListenerConfigurer;
import io.valkey.springframework.data.valkey.config.ValkeyListenerEndpointRegistrar;
import io.valkey.springframework.data.valkey.config.ValkeyListenerEndpointRegistry;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Bean post-processor that registers methods annotated with {@link ValkeyListener} to be subscribed to a Valkey message
 * listener container according to the attributes of the annotation.
 * <p>
 * Annotated methods can use flexible arguments as defined by {@link ValkeyListener}.
 * <p>
 * This post-processor is automatically registered by Spring's by the {@link EnableValkeyListeners} annotation.
 * <p>
 * See the {@link EnableValkeyListeners} javadocs for complete usage details.
 *
 * @author Ilyass Bougati
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 4.1
 * @see ValkeyListener
 */
public class ValkeyListenerAnnotationBeanPostProcessor
		implements BeanPostProcessor, BeanFactoryAware, Ordered, SmartInitializingSingleton {

	protected final Log logger = LogFactory.getLog(getClass());

	private final ValkeyListenerEndpointRegistrar registrar = new ValkeyListenerEndpointRegistrar();

	private @Nullable ValkeyListenerEndpointRegistry endpointRegistry;

	private final MessageHandlerMethodFactoryAdapter messageHandlerMethodFactory = new MessageHandlerMethodFactoryAdapter();

	private int order = Ordered.LOWEST_PRECEDENCE;

	private @Nullable BeanFactory beanFactory;

	private @Nullable StringValueResolver embeddedValueResolver;

	private final AtomicInteger counter = new AtomicInteger();

	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * Set the {@link ValkeyListenerEndpointRegistry} that will hold the created endpoint.
	 */
	public void setEndpointRegistry(@Nullable ValkeyListenerEndpointRegistry endpointRegistry) {
		this.endpointRegistry = endpointRegistry;
	}

	/**
	 * Set the {@link MessageHandlerMethodFactory} to use to configure the message listener responsible to serve an
	 * endpoint detected by this processor.
	 * <p>
	 * By default, {@link DefaultMessageHandlerMethodFactory} is used. It can be configured further to support additional
	 * method arguments or to customize conversion and validation support. See {@link DefaultMessageHandlerMethodFactory}
	 * Javadoc for more details.
	 */
	public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory messageHandlerMethodFactory) {
		this.messageHandlerMethodFactory.setMessageHandlerMethodFactory(messageHandlerMethodFactory);
	}

	/**
	 * Making a {@link BeanFactory} available is optional; if not set, {@link #setEndpointRegistry endpoint registry} has
	 * to be explicitly configured.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {

		this.beanFactory = beanFactory;
		if (beanFactory instanceof ConfigurableBeanFactory cbf) {
			this.embeddedValueResolver = new EmbeddedValueResolver(cbf);
			this.registrar.setBeanFactory(cbf);
		}
	}

	@Override
	public void afterSingletonsInstantiated() {

		this.nonAnnotatedClasses.clear();

		if (this.beanFactory instanceof ListableBeanFactory lbf) {

			// Apply ValkeyListenerConfigurer beans from the BeanFactory, if any
			Map<String, ValkeyListenerConfigurer> beans = lbf.getBeansOfType(ValkeyListenerConfigurer.class);
			List<ValkeyListenerConfigurer> configurers = new ArrayList<>(beans.values());
			AnnotationAwareOrderComparator.sort(configurers);
			registrar.apply(configurers);
		}

		if (this.registrar.getEndpointRegistry() == null) {

			// Determine ValkeyListenerEndpointRegistry bean from the BeanFactory
			if (this.endpointRegistry == null) {
				Assert.state(this.beanFactory != null, "BeanFactory must be set to find endpoint registry by bean name");
				this.endpointRegistry = this.beanFactory.getBean(
						ValkeyListenerConfigUtils.VALKEY_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME, ValkeyListenerEndpointRegistry.class);
			}
			this.registrar.setEndpointRegistry(this.endpointRegistry);
		}

		if (!this.messageHandlerMethodFactory.hasMessageHandlerMethodFactory()) {

			// Set the custom handler method factory once resolved by the configurer
			MessageHandlerMethodFactory handlerMethodFactory = this.registrar.getMessageHandlerMethodFactory();
			this.messageHandlerMethodFactory.setMessageHandlerMethodFactory(handlerMethodFactory);
		}

		this.registrar.afterPropertiesSet();
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {

		if (bean instanceof AopInfrastructureBean || bean instanceof ValkeyMessageListenerContainer
				|| bean instanceof ValkeyListenerEndpointRegistry) {
			return bean;
		}

		Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
		if (!this.nonAnnotatedClasses.contains(targetClass)
				&& AnnotationUtils.isCandidateClass(targetClass, ValkeyListener.class)) {
			Map<Method, Set<ValkeyListener>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
					(MethodIntrospector.MetadataLookup<Set<ValkeyListener>>) method -> {
						Set<ValkeyListener> listenerMethods = AnnotatedElementUtils.getMergedRepeatableAnnotations(method,
								ValkeyListener.class, ValkeyListeners.class);
						return (!listenerMethods.isEmpty() ? listenerMethods : null);
					});
			if (annotatedMethods.isEmpty()) {
				this.nonAnnotatedClasses.add(targetClass);
			} else {
				annotatedMethods.forEach(
						(method, listeners) -> listeners.forEach(listener -> processValkeyListener(listener, method, bean)));
			}
		}
		return bean;
	}

	/**
	 * Process the given {@link ValkeyListener} annotation on the given method, registering a corresponding endpoint for
	 * the given bean instance.
	 *
	 * @param valkeyListener the annotation to process
	 * @param method the annotated method
	 * @param bean the instance to invoke the method on
	 */
	protected void processValkeyListener(ValkeyListener valkeyListener, Method method, Object bean) {

		ValkeyMessageListenerContainer container = getValkeyMessageListenerContainer(valkeyListener, method);
		MethodValkeyListenerEndpoint endpoint = createEndpoint(valkeyListener, method, bean);
		this.registrar.registerEndpoint(endpoint, container);
	}

	protected ValkeyMessageListenerContainer getValkeyMessageListenerContainer(ValkeyListener valkeyListener, Method method) {

		Assert.state(this.beanFactory != null, "BeanFactory must be set to obtain message listener container by bean name");

		String containerName = resolve(valkeyListener.container());
		if (StringUtils.hasText(containerName)) {
			return getValkeyMessageListenerContainer(method, containerName);
		}

		ValkeyMessageListenerContainer container = this.beanFactory.getBeanProvider(ValkeyMessageListenerContainer.class)
				.getIfUnique();

		if (container == null) {
			container = getValkeyMessageListenerContainer(method, ValkeyListenerConfigUtils.VALKEY_MESSAGE_LISTENER_BEAN_NAME);
		}

		return container;
	}

	@SuppressWarnings("NullAway")
	private ValkeyMessageListenerContainer getValkeyMessageListenerContainer(Method method, String containerName) {

		try {
			return this.beanFactory.getBean(containerName, ValkeyMessageListenerContainer.class);
		} catch (NoSuchBeanDefinitionException ex) {
			throw new BeanInitializationException("Could not register Valkey listener endpoint on [" + method + "], no "
					+ ValkeyMessageListenerContainer.class.getSimpleName() + " with name '" + containerName
					+ "' was found in the application context", ex);
		}
	}

	public MethodValkeyListenerEndpoint createEndpoint(ValkeyListener valkeyListener, Method method, Object bean) {

		MethodValkeyListenerEndpoint endpoint = new MethodValkeyListenerEndpoint(bean, method);
		endpoint.setMessageHandlerMethodFactory(this.messageHandlerMethodFactory);
		endpoint.setId(getEndpointId(valkeyListener));
		endpoint.setTopic(valkeyListener.topic());
		endpoint.setConsumes(valkeyListener.consumes());

		return endpoint;
	}

	private String getEndpointId(ValkeyListener valkeyListener) {
		if (StringUtils.hasText(valkeyListener.id())) {
			String id = resolve(valkeyListener.id());
			return (id != null ? id : "");
		} else {
			return "io.valkey.springframework.data.valkey.config.ValkeyListenerEndpoint#" + this.counter.getAndIncrement();
		}
	}

	private @Nullable String resolve(String value) {
		return (this.embeddedValueResolver != null ? this.embeddedValueResolver.resolveStringValue(value) : value);
	}

	/**
	 * A {@link MessageHandlerMethodFactory} adapter that offers a configurable underlying instance to use. Useful if the
	 * factory to use is determined once the endpoints have been registered but not created yet.
	 *
	 * @see ValkeyListenerEndpointRegistrar#setMessageHandlerMethodFactory
	 */
	private class MessageHandlerMethodFactoryAdapter implements MessageHandlerMethodFactory {

		private @Nullable MessageHandlerMethodFactory messageHandlerMethodFactory;

		public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory messageHandlerMethodFactory) {
			this.messageHandlerMethodFactory = messageHandlerMethodFactory;
		}

		public boolean hasMessageHandlerMethodFactory() {
			return this.messageHandlerMethodFactory != null;
		}

		@Override
		public InvocableHandlerMethod createInvocableHandlerMethod(Object bean, Method method) {
			return getMessageHandlerMethodFactory().createInvocableHandlerMethod(bean, method);
		}

		private MessageHandlerMethodFactory getMessageHandlerMethodFactory() {
			if (this.messageHandlerMethodFactory == null) {
				this.messageHandlerMethodFactory = createDefaultValkeyHandlerMethodFactory();
			}
			return this.messageHandlerMethodFactory;
		}

		private MessageHandlerMethodFactory createDefaultValkeyHandlerMethodFactory() {
			DefaultMessageHandlerMethodFactory defaultFactory = new DefaultMessageHandlerMethodFactory();
			if (beanFactory != null) {
				defaultFactory.setBeanFactory(beanFactory);
			}
			defaultFactory.afterPropertiesSet();
			return defaultFactory;
		}

	}

}
