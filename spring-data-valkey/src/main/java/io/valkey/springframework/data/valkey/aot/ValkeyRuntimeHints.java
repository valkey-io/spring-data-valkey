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
package io.valkey.springframework.data.valkey.aot;

import java.util.Arrays;
import java.util.function.Consumer;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.data.keyvalue.annotation.KeySpace;
import org.springframework.data.keyvalue.core.AbstractKeyValueAdapter;
import org.springframework.data.keyvalue.core.KeyValueAdapter;
import org.springframework.data.keyvalue.core.KeyValueOperations;
import org.springframework.data.keyvalue.core.KeyValueTemplate;
import org.springframework.data.keyvalue.core.mapping.context.KeyValueMappingContext;
import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.data.keyvalue.repository.config.QueryCreatorType;
import org.springframework.data.keyvalue.repository.query.KeyValuePartTreeQuery;
import org.springframework.data.keyvalue.repository.support.KeyValueRepositoryFactoryBean;
import io.valkey.springframework.data.valkey.cache.ValkeyCacheManager;
import io.valkey.springframework.data.valkey.connection.*;
import io.valkey.springframework.data.valkey.core.*;
import io.valkey.springframework.data.valkey.core.ValkeyConnectionUtils.ValkeyConnectionProxy;
import io.valkey.springframework.data.valkey.core.convert.KeyspaceConfiguration;
import io.valkey.springframework.data.valkey.core.convert.MappingConfiguration;
import io.valkey.springframework.data.valkey.core.convert.MappingValkeyConverter;
import io.valkey.springframework.data.valkey.core.convert.ValkeyConverter;
import io.valkey.springframework.data.valkey.core.convert.ValkeyCustomConversions;
import io.valkey.springframework.data.valkey.core.convert.ReferenceResolver;
import io.valkey.springframework.data.valkey.core.convert.ReferenceResolverImpl;
import io.valkey.springframework.data.valkey.core.index.ConfigurableIndexDefinitionProvider;
import io.valkey.springframework.data.valkey.core.index.IndexConfiguration;
import io.valkey.springframework.data.valkey.core.mapping.ValkeyMappingContext;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;
import io.valkey.springframework.data.valkey.repository.query.ValkeyPartTreeQuery;
import io.valkey.springframework.data.valkey.repository.query.ValkeyQueryCreator;
import io.valkey.springframework.data.valkey.repository.support.ValkeyRepositoryFactoryBean;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * {@link RuntimeHintsRegistrar} for Valkey operations and repository support.
 *
 * @author Christoph Strobl
 * @since 3.0
 */
public class ValkeyRuntimeHints implements RuntimeHintsRegistrar {

	/**
	 * Get a {@link RuntimeHints} instance containing the ones for Valkey.
	 *
	 * @param config callback to provide additional custom hints.
	 * @return new instance of {@link RuntimeHints}.
	 */
	public static RuntimeHints valkeyHints(Consumer<RuntimeHints> config) {

		RuntimeHints hints = new RuntimeHints();
		new ValkeyRuntimeHints().registerHints(hints, null);
		config.accept(hints);
		return hints;
	}

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

		// CACHE
		hints.serialization().registerType(org.springframework.cache.support.NullValue.class,
				hint -> hint.onReachableType(TypeReference.of(ValkeyCacheManager.class)));

		// REFLECTION
		hints.reflection().registerTypes(
				Arrays.asList(TypeReference.of(ValkeyConnection.class), TypeReference.of(StringValkeyConnection.class),
						TypeReference.of(DefaultedValkeyConnection.class), TypeReference.of(DefaultedValkeyClusterConnection.class),
						TypeReference.of(ValkeyKeyCommands.class), TypeReference.of(ValkeyStringCommands.class),
						TypeReference.of(ValkeyListCommands.class), TypeReference.of(ValkeySetCommands.class),
						TypeReference.of(ValkeyZSetCommands.class), TypeReference.of(ValkeyHashCommands.class),
						TypeReference.of(ValkeyTxCommands.class), TypeReference.of(ValkeyPubSubCommands.class),
						TypeReference.of(ValkeyConnectionCommands.class), TypeReference.of(ValkeyServerCommands.class),
						TypeReference.of(ValkeyStreamCommands.class), TypeReference.of(ValkeyScriptingCommands.class),
						TypeReference.of(ValkeyGeoCommands.class), TypeReference.of(ValkeyHyperLogLogCommands.class),
						TypeReference.of(ValkeyClusterCommands.class), TypeReference.of(ReactiveValkeyConnection.class),
						TypeReference.of(ReactiveKeyCommands.class), TypeReference.of(ReactiveStringCommands.class),
						TypeReference.of(ReactiveListCommands.class), TypeReference.of(ReactiveSetCommands.class),
						TypeReference.of(ReactiveZSetCommands.class), TypeReference.of(ReactiveHashCommands.class),
						TypeReference.of(ReactivePubSubCommands.class), TypeReference.of(ReactiveServerCommands.class),
						TypeReference.of(ReactiveStreamCommands.class), TypeReference.of(ReactiveScriptingCommands.class),
						TypeReference.of(ReactiveGeoCommands.class), TypeReference.of(ReactiveHyperLogLogCommands.class),
						TypeReference.of(ReactiveClusterKeyCommands.class), TypeReference.of(ReactiveClusterStringCommands.class),
						TypeReference.of(ReactiveClusterListCommands.class), TypeReference.of(ReactiveClusterSetCommands.class),
						TypeReference.of(ReactiveClusterZSetCommands.class), TypeReference.of(ReactiveClusterHashCommands.class),
						TypeReference.of(ReactiveClusterServerCommands.class),
						TypeReference.of(ReactiveClusterStreamCommands.class),
						TypeReference.of(ReactiveClusterScriptingCommands.class),
						TypeReference.of(ReactiveClusterGeoCommands.class),
						TypeReference.of(ReactiveClusterHyperLogLogCommands.class), TypeReference.of(ReactiveValkeyOperations.class),
						TypeReference.of(ReactiveValkeyConnectionFactory.class), TypeReference.of(ReactiveValkeyTemplate.class),
						TypeReference.of(ValkeyOperations.class), TypeReference.of(ValkeyTemplate.class),
						TypeReference.of(StringValkeyTemplate.class), TypeReference.of(KeyspaceConfiguration.class),
						TypeReference.of(MappingConfiguration.class), TypeReference.of(MappingValkeyConverter.class),
						TypeReference.of(ValkeyConverter.class), TypeReference.of(ValkeyCustomConversions.class),
						TypeReference.of(ReferenceResolver.class), TypeReference.of(ReferenceResolverImpl.class),
						TypeReference.of(IndexConfiguration.class), TypeReference.of(ConfigurableIndexDefinitionProvider.class),
						TypeReference.of(ValkeyMappingContext.class), TypeReference.of(ValkeyRepositoryFactoryBean.class),
						TypeReference.of(ValkeyQueryCreator.class), TypeReference.of(ValkeyPartTreeQuery.class),
						TypeReference.of(MessageListener.class), TypeReference.of(ValkeyMessageListenerContainer.class),

						TypeReference
								.of("io.valkey.springframework.data.valkey.core.BoundOperationsProxyFactory$DefaultBoundKeyOperations"),
						TypeReference.of("io.valkey.springframework.data.valkey.core.DefaultGeoOperations"),
						TypeReference.of("io.valkey.springframework.data.valkey.core.DefaultHashOperations"),
						TypeReference.of("io.valkey.springframework.data.valkey.core.DefaultKeyOperations"),
						TypeReference.of("io.valkey.springframework.data.valkey.core.DefaultListOperations"),
						TypeReference.of("io.valkey.springframework.data.valkey.core.DefaultSetOperations"),
						TypeReference.of("io.valkey.springframework.data.valkey.core.DefaultStreamOperations"),
						TypeReference.of("io.valkey.springframework.data.valkey.core.DefaultValueOperations"),
						TypeReference.of("io.valkey.springframework.data.valkey.core.DefaultZSetOperations"),

						TypeReference.of(ValkeyKeyValueAdapter.class), TypeReference.of(ValkeyKeyValueTemplate.class),

						// Key-Value
						TypeReference.of(KeySpace.class), TypeReference.of(AbstractKeyValueAdapter.class),
						TypeReference.of(KeyValueAdapter.class), TypeReference.of(KeyValueOperations.class),
						TypeReference.of(KeyValueTemplate.class), TypeReference.of(KeyValueMappingContext.class),
						TypeReference.of(KeyValueRepository.class), TypeReference.of(KeyValueRepositoryFactoryBean.class),
						TypeReference.of(QueryCreatorType.class), TypeReference.of(KeyValuePartTreeQuery.class)),

				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS,
						MemberCategory.INVOKE_PUBLIC_METHODS));

		// PROXIES
		hints.proxies().registerJdkProxy(TypeReference.of(ValkeyConnection.class));
		hints.proxies().registerJdkProxy(TypeReference.of(DefaultedValkeyConnection.class));
		hints.proxies().registerJdkProxy(TypeReference.of(ReactiveValkeyConnection.class));
		hints.proxies().registerJdkProxy(TypeReference.of(StringValkeyConnection.class),
				TypeReference.of(DecoratedValkeyConnection.class));

		// keys are bound by a proxy
		boundOperationsProxy(BoundGeoOperations.class, classLoader, hints);
		boundOperationsProxy(BoundHashOperations.class, classLoader, hints);
		boundOperationsProxy(BoundKeyOperations.class, classLoader, hints);
		boundOperationsProxy(BoundListOperations.class, classLoader, hints);
		boundOperationsProxy(BoundSetOperations.class, classLoader, hints);
		boundOperationsProxy(BoundStreamOperations.class, classLoader, hints);
		boundOperationsProxy(BoundValueOperations.class, classLoader, hints);
		boundOperationsProxy(BoundZSetOperations.class, classLoader, hints);

		// Connection Splitting
		registerValkeyConnectionProxy(TypeReference.of(ValkeyCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyGeoCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyHashCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyHyperLogLogCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyKeyCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyListCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeySetCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyScriptingCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyServerCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyStreamCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyStringCommands.class), hints);
		registerValkeyConnectionProxy(TypeReference.of(ValkeyZSetCommands.class), hints);
	}

	static void boundOperationsProxy(Class<?> type, ClassLoader classLoader, RuntimeHints hints) {
		boundOperationsProxy(TypeReference.of(type), classLoader, hints);
	}

	static void boundOperationsProxy(TypeReference typeReference, ClassLoader classLoader, RuntimeHints hints) {

		String boundTargetClass = typeReference.getPackageName() + "." + typeReference.getSimpleName().replace("Bound", "");
		if (ClassUtils.isPresent(boundTargetClass, classLoader)) {
			hints.reflection().registerType(TypeReference.of(boundTargetClass),
					hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS));
		}

		hints.reflection().registerType(typeReference,
				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS));

		hints.proxies().registerJdkProxy(typeReference, //
				TypeReference.of("org.springframework.aop.SpringProxy"), //
				TypeReference.of("org.springframework.aop.framework.Advised"), //
				TypeReference.of("org.springframework.core.DecoratingProxy"));
	}

	static void registerValkeyConnectionProxy(TypeReference typeReference, RuntimeHints hints) {

		hints.proxies().registerJdkProxy(TypeReference.of(ValkeyConnectionProxy.class), //
				typeReference, //
				TypeReference.of("org.springframework.aop.SpringProxy"), //
				TypeReference.of("org.springframework.aop.framework.Advised"), //
				TypeReference.of("org.springframework.core.DecoratingProxy"));
	}
}
