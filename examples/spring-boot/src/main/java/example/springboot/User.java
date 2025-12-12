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
package example.springboot;

import io.valkey.springframework.data.valkey.core.ValkeyHash;
import io.valkey.springframework.data.valkey.core.index.Indexed;
import org.springframework.data.annotation.Id;

@ValkeyHash("users")
public class User {

	@Id
	private String id;

	@Indexed
	private String name;

	@Indexed
	private String email;

	@Indexed
	private int age;

	public User() {
	}

	public User(String id, String name, String email, int age) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.age = age;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getEmail() { return email;	}
	public void setEmail(String email) { this.email = email; }

	public int getAge() { return age; }
	public void setAge(int age) { this.age = age; }

	@Override
	public String toString() {
		return "User{id='" + id + "', name='" + name + "', email='" + email + "', age=" + age + "}";
	}
}
