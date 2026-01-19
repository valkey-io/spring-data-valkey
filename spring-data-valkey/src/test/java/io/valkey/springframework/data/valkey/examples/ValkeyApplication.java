/*
 * Copyright 2023-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.examples;

// tag::file[]
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;

public class ValkeyApplication {

	private static final Log LOG = LogFactory.getLog(ValkeyApplication.class);

	public static void main(String[] args) {

		ValkeyGlideConnectionFactory connectionFactory = new ValkeyGlideConnectionFactory();
		connectionFactory.afterPropertiesSet();

		ValkeyTemplate<String, String> template = new ValkeyTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setDefaultSerializer(StringValkeySerializer.UTF_8);
		template.afterPropertiesSet();

		template.opsForValue().set("foo", "bar");

		LOG.info("Value at foo:" + template.opsForValue().get("foo"));

		connectionFactory.destroy();
	}
}
// end::file[]
