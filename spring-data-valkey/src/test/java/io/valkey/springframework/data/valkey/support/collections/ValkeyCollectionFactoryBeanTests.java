/*
 * Copyright 2011-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.support.collections;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.valkey.springframework.data.valkey.ObjectFactory;
import io.valkey.springframework.data.valkey.StringObjectFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.extension.ValkeyGlideConnectionFactoryExtension;
import io.valkey.springframework.data.valkey.core.ValkeyCallback;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.support.collections.ValkeyCollectionFactoryBean.CollectionType;
import io.valkey.springframework.data.valkey.test.extension.ValkeyStanalone;

/**
 * Integration tests for {@link ValkeyCollectionFactoryBean}.
 *
 * @author Costin Leau
 * @author Mark Paluch
 * @author Christoph Strobl
 */
public class ValkeyCollectionFactoryBeanTests {

	protected ObjectFactory<String> factory = new StringObjectFactory();
	protected StringValkeyTemplate template;
	protected ValkeyStore col;

	ValkeyCollectionFactoryBeanTests() {

		ValkeyGlideConnectionFactory valkeyGlideConnFactory = ValkeyGlideConnectionFactoryExtension
				.getConnectionFactory(ValkeyStanalone.class);

		this.template = new StringValkeyTemplate(valkeyGlideConnFactory);
	}

	@BeforeEach
	void setUp() {

		this.template.delete("key");
		this.template.delete("nosrt");
	}

	@AfterEach
	void tearDown() throws Exception {

		// clean up the whole db
		template.execute((ValkeyCallback<Object>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}

	private ValkeyStore createCollection(String key) {
		return createCollection(key, null);
	}

	private ValkeyStore createCollection(String key, CollectionType type) {

		ValkeyCollectionFactoryBean fb = new ValkeyCollectionFactoryBean();
		fb.setKey(key);
		fb.setTemplate(template);
		fb.setType(type);
		fb.afterPropertiesSet();

		return fb.getObject();
	}

	@Test
	void testNone() {

		ValkeyStore store = createCollection("nosrt", CollectionType.PROPERTIES);
		assertThat(store).isInstanceOf(ValkeyProperties.class);

		store = createCollection("nosrt", CollectionType.MAP);
		assertThat(store).isInstanceOf(DefaultValkeyMap.class);

		store = createCollection("nosrt", CollectionType.SET);
		assertThat(store).isInstanceOf(DefaultValkeySet.class);

		store = createCollection("nosrt", CollectionType.LIST);
		assertThat(store).isInstanceOf(DefaultValkeyList.class);

		store = createCollection("nosrt");
		assertThat(store).isInstanceOf(DefaultValkeyList.class);
	}

	@Test // GH-2633
	void testExisting() {

		template.delete("key");
		template.opsForHash().put("key", "k", "v");

		assertThat(createCollection("key")).isInstanceOf(DefaultValkeyMap.class);
		assertThat(createCollection("key", CollectionType.MAP)).isInstanceOf(DefaultValkeyMap.class);

		template.delete("key");
		template.opsForSet().add("key", "1", "2");

		assertThat(createCollection("key")).isInstanceOf(DefaultValkeySet.class);
		assertThat(createCollection("key", CollectionType.SET)).isInstanceOf(DefaultValkeySet.class);

		template.delete("key");
		template.opsForList().leftPush("key", "1", "2");

		assertThat(createCollection("key")).isInstanceOf(DefaultValkeyList.class);
		assertThat(createCollection("key", CollectionType.LIST)).isInstanceOf(DefaultValkeyList.class);
	}

	@Test
	void testExistingCol() {

		String key = "set";
		String val = "value";

		template.boundSetOps(key).add(val);
		ValkeyStore col = createCollection(key);
		assertThat(col).isInstanceOf(DefaultValkeySet.class);

		key = "map";
		template.boundHashOps(key).put(val, val);
		col = createCollection(key);
		assertThat(col).isInstanceOf(DefaultValkeyMap.class);

		col = createCollection(key, CollectionType.PROPERTIES);
		assertThat(col).isInstanceOf(ValkeyProperties.class);
	}

	@Test // GH-2633
	void testIncompatibleCollections() {

		template.opsForValue().set("key", "value");
		assertThatIllegalArgumentException().isThrownBy(() -> createCollection("key", CollectionType.LIST))
				.withMessageContaining("Cannot create collection type 'LIST' for a key containing 'STRING'");

		template.delete("key");
		template.opsForList().leftPush("key", "value");
		assertThatIllegalArgumentException().isThrownBy(() -> createCollection("key", CollectionType.SET))
				.withMessageContaining("Cannot create collection type 'SET' for a key containing 'LIST'");
	}

	@Test // GH-2633
	void shouldFailForStreamCreation() {

		template.opsForStream().add("key", Map.of("k", "v"));
		assertThatIllegalArgumentException().isThrownBy(() -> createCollection("key", CollectionType.LIST))
				.withMessageContaining("Cannot create store on keys of type 'STREAM'");
	}

	@Test // Gh-2633
	void shouldFailWhenNotInitialized() {

		ValkeyCollectionFactoryBean fb = new ValkeyCollectionFactoryBean();
		fb.setKey("key");
		fb.setTemplate(template);
		fb.setType(CollectionType.SET);

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> fb.getObject());
	}

	@Test // Gh-2633
	void usesBeanNameIfNoKeyProvided() {

		template.delete("key");
		template.opsForHash().put("key", "k", "v");

		ValkeyCollectionFactoryBean fb = new ValkeyCollectionFactoryBean();
		fb.setBeanName("key");
		fb.setTemplate(template);
		fb.afterPropertiesSet();

		assertThat(fb.getObject()).satisfies(value -> {
			assertThat(value).isInstanceOf(ValkeyMap.class);
			assertThat((ValkeyMap)value).containsEntry("k", "v");
		});
	}
}
