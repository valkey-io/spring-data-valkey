/*
 * Copyright 2012-2024 the original author or authors.
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

package io.valkey.springframework.boot.test.autoconfigure.data.valkey;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;

/**
 * Example service used with {@link DataValkeyTest @DataValkeyTest} tests.
 *
 * @author Jayaram Pradhan
 */
@Service
public class ExampleService {

	private static final Charset CHARSET = StandardCharsets.UTF_8;

	private final ValkeyOperations<Object, Object> operations;

	public ExampleService(ValkeyOperations<Object, Object> operations) {
		this.operations = operations;
	}

	public boolean hasRecord(PersonHash personHash) {
		return this.operations.execute((ValkeyConnection connection) -> connection.keyCommands()
			.exists(("persons:" + personHash.getId()).getBytes(CHARSET)));
	}

}
