/*
 * Copyright 2020-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.test.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code @EnabledOnValkeyDriver} is used to signal that the annotated test class or test method is only <em>enabled</em>
 * when one of the {@link #value() specified Valkey Clients} is used.
 * <p>
 * This annotation must be used in combination with {@link DriverQualifier @DriverQualifier} so the extension can
 * identify the driver from a {@link io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory}.
 * <p>
 * If a test method is disabled via this annotation, that does not prevent the test class from being instantiated.
 * Rather, it prevents the execution of the test method and method-level lifecycle callbacks such as {@code @BeforeEach}
 * methods, {@code @AfterEach} methods, and corresponding extension APIs.
 *
 * @author Thomas Darimont
 * @author Mark Paluch
 * @see io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledOnValkeyDriverCondition.class)
public @interface EnabledOnValkeyDriver {

	/**
	 * One or more Valkey drivers that enable the condition.
	 */
	ValkeyDriver[] value() default {};

	/**
	 * Annotation to identify the field that holds the
	 * {@link io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory} to inspect.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface DriverQualifier {

	}

}
