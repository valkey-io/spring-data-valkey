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

import java.time.Duration;
import java.util.Set;

import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final StringValkeyTemplate valkeyTemplate;

	@Autowired
	public UserService(StringValkeyTemplate valkeyTemplate) {
		this.valkeyTemplate = valkeyTemplate;
	}

	public void cacheUserSession(String userId, String sessionData) {
		String key = "session:" + userId;
		valkeyTemplate.opsForValue().set(key, sessionData, Duration.ofMinutes(30));
	}

	public String getUserSession(String userId) {
		String key = "session:" + userId;
		return valkeyTemplate.opsForValue().get(key);
	}

	public void addActiveUser(String userId) {
		valkeyTemplate.opsForSet().add("active:users", userId);
	}

	public Set<String> getActiveUsers() {
		return valkeyTemplate.opsForSet().members("active:users");
	}

	public Long incrementLoginCount(String userId) {
		String key = "login:count:" + userId;
		return valkeyTemplate.opsForValue().increment(key);
	}

	public String getLoginCount(String userId) {
		String key = "login:count:" + userId;
		return valkeyTemplate.opsForValue().get(key);
	}
}
