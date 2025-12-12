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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Data's Valkey support.
 *
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Christoph Strobl
 * @author Phillip Webb
 * @author Eddú Meléndez
 * @author Stephane Nicoll
 * @author Marco Aust
 * @author Mark Paluch
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ValkeyOperations.class)
@EnableConfigurationProperties(ValkeyProperties.class)
@Import({ ValkeyGlideConnectionConfiguration.class, LettuceConnectionConfiguration.class, JedisConnectionConfiguration.class })
public class ValkeyAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ValkeyConnectionDetails.class)
	PropertiesValkeyConnectionDetails valkeyConnectionDetails(ValkeyProperties properties,
			ObjectProvider<SslBundles> sslBundles) {
		return new PropertiesValkeyConnectionDetails(properties, sslBundles.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean(name = "valkeyTemplate")
	@ConditionalOnSingleCandidate(ValkeyConnectionFactory.class)
	public ValkeyTemplate<Object, Object> valkeyTemplate(ValkeyConnectionFactory valkeyConnectionFactory) {
		ValkeyTemplate<Object, Object> template = new ValkeyTemplate<>();
		template.setConnectionFactory(valkeyConnectionFactory);
		return template;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnSingleCandidate(ValkeyConnectionFactory.class)
	public StringValkeyTemplate stringValkeyTemplate(ValkeyConnectionFactory valkeyConnectionFactory) {
		return new StringValkeyTemplate(valkeyConnectionFactory);
	}

}
