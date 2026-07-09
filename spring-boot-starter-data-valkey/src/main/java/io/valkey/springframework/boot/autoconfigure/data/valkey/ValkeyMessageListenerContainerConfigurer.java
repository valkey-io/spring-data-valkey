/*
 * Copyright 2012-present the original author or authors.
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

import java.time.Duration;
import java.util.function.Predicate;

import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.retry.RetryPolicy;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyProperties.Listener;
import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyProperties.Recovery;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.listener.ValkeyMessageListenerContainer;

import org.springframework.util.backoff.BackOff;

/**
 * Configure {@link ValkeyMessageListenerContainer} with sensible defaults tuned using
 * configuration properties.
 * <p>
 * Can be injected into application code and used to define a custom
 * {@code ValkeyMessageListenerContainer} whose configuration is based upon that produced
 * by auto-configuration.
 *
 * @author Stephane Nicoll
 * @since 4.1.0
 */
public class ValkeyMessageListenerContainerConfigurer {

	private final ValkeyProperties properties;

	public ValkeyMessageListenerContainerConfigurer(ValkeyProperties properties) {
		this.properties = properties;
	}

	/**
	 * Configure the specified Valkey message listener container.
	 * @param container the {@link ValkeyMessageListenerContainer} instance to configure
	 * @param connectionFactory the {@link ValkeyConnectionFactory} to use
	 */
	public void configure(ValkeyMessageListenerContainer container, ValkeyConnectionFactory connectionFactory) {
		container.setConnectionFactory(connectionFactory);
		PropertyMapper map = PropertyMapper.get();
		Listener listenerProperties = this.properties.getListener();
		map.from(listenerProperties::isAutoStartup).to(container::setAutoStartup);
		map.from(listenerProperties::getSubscriptionRegistrationTimeout)
			.as(Duration::toMillis)
			.to(container::setMaxSubscriptionRegistrationWaitingTime);
		map.from(getRecoveryBackOff(listenerProperties.getRecovery())).to(container::setRecoveryBackoff);
	}

	static BackOff getRecoveryBackOff(Recovery recovery) {
		PropertyMapper map = PropertyMapper.get();
		RetryPolicy.Builder builder = RetryPolicy.builder().maxRetries(recovery.getMaxRetries());
		map.from(recovery.getDelay()).to(builder::delay);
		map.from(recovery.getMaxDelay()).when(Predicate.not(Duration::isZero)).to(builder::maxDelay);
		map.from(recovery.getMultiplier()).to(builder::multiplier);
		map.from(recovery.getJitter()).when((Predicate.not(Duration::isZero))).to(builder::jitter);
		return builder.build().getBackOff();
	}

}
