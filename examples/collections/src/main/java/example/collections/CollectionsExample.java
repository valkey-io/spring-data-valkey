/*
 * Copyright 2025 the original author or authors.
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
package example.collections;

import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyTemplate;
import io.valkey.springframework.data.valkey.serializer.StringValkeySerializer;
import io.valkey.springframework.data.valkey.support.atomic.ValkeyAtomicLong;
import io.valkey.springframework.data.valkey.support.collections.DefaultValkeyList;
import io.valkey.springframework.data.valkey.support.collections.DefaultValkeyMap;
import io.valkey.springframework.data.valkey.support.collections.DefaultValkeySet;
import java.util.Arrays;

/** Example demonstrating Valkey-backed Java collections. */
public class CollectionsExample {

    public static void main(String[] args) {

        ValkeyGlideConnectionFactory connectionFactory = new ValkeyGlideConnectionFactory();
        connectionFactory.afterPropertiesSet();

        try {
            ValkeyTemplate<String, String> template = new ValkeyTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setDefaultSerializer(StringValkeySerializer.UTF_8);
            template.afterPropertiesSet();

            // ValkeyList - Java List interface backed by Valkey
            DefaultValkeyList<String> list = new DefaultValkeyList<>("mylist", template);
            list.add("item1");
            list.add("item2");
            System.out.println("List size: " + list.size());
            System.out.println("List contents: " + list.stream().toList());

            // ValkeySet - Java Set interface backed by Valkey
            DefaultValkeySet<String> set = new DefaultValkeySet<>("myset", template);
            set.add("element1");
            set.add("element2");
            System.out.println("\nSet size: " + set.size());
            System.out.println("Set members: " + set.stream().toList());

            // ValkeyMap - Java Map interface backed by Valkey
            DefaultValkeyMap<String, String> map = new DefaultValkeyMap<>("mymap", template);
            map.put("key1", "value1");
            map.put("key2", "value2");
            System.out.println("\nMap size: " + map.size());
            System.out.println("Map entries: " + map.entrySet().stream().toList());

            // Atomic counter
            ValkeyAtomicLong counter = new ValkeyAtomicLong("counter", connectionFactory);
            System.out.println("\nInitial counter: " + counter.get());
            System.out.println("Increment: " + counter.incrementAndGet());
            System.out.println("Add 5: " + counter.addAndGet(5));
            System.out.println("Compare and set: " + counter.compareAndSet(6, 10));

            // Cleanup
            template.delete(Arrays.asList("mylist", "myset", "mymap", "counter"));
        } finally {
            connectionFactory.destroy();
        }
    }
}
