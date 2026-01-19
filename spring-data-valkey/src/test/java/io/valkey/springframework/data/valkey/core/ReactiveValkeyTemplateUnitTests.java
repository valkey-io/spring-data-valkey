/*
 * Copyright 2019-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.ReactivePubSubCommands;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ReactiveSubscription;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializationContext;

/**
 * Unit tests for {@link ReactiveValkeyTemplate}.
 *
 * @author Mark Paluch
 */
class ReactiveValkeyTemplateUnitTests {

	private ReactiveValkeyConnectionFactory connectionFactoryMock = mock(ReactiveValkeyConnectionFactory.class);
	private ReactiveValkeyConnection connectionMock = mock(ReactiveValkeyConnection.class);

	@Test // DATAREDIS-999
	void closeShouldUseAsyncRelease() {

		when(connectionFactoryMock.getReactiveConnection()).thenReturn(connectionMock);
		when(connectionMock.closeLater()).thenReturn(Mono.empty());

		ReactiveValkeyTemplate<String, String> template = new ReactiveValkeyTemplate<>(connectionFactoryMock,
				ValkeySerializationContext.string());

		template.execute(connection -> Mono.empty()) //
				.as(StepVerifier::create) //
				.verifyComplete();

		verify(connectionMock).closeLater();
		verifyNoMoreInteractions(connectionMock);
	}

	@Test // DATAREDIS-1053
	void listenToShouldSubscribeToChannel() {

		AtomicBoolean closed = new AtomicBoolean();
		when(connectionFactoryMock.getReactiveConnection()).thenReturn(connectionMock);
		when(connectionMock.closeLater()).thenReturn(Mono.<Void> empty().doOnSubscribe(ignore -> closed.set(true)));

		ReactivePubSubCommands pubSubCommands = mock(ReactivePubSubCommands.class);
		ReactiveSubscription subscription = mock(ReactiveSubscription.class);

		when(connectionMock.pubSubCommands()).thenReturn(pubSubCommands);
		when(pubSubCommands.subscribe(any())).thenReturn(Mono.empty());
		when(pubSubCommands.createSubscription(any())).thenReturn(Mono.just(subscription));
		when(subscription.receive()).thenReturn(Flux.create(sink -> {}));

		ReactiveValkeyTemplate<String, String> template = new ReactiveValkeyTemplate<>(connectionFactoryMock,
				ValkeySerializationContext.string());

		template.listenToChannel("channel") //
				.as(StepVerifier::create) //
				.thenAwait() //
				.thenCancel() //
				.verify();

		assertThat(closed).isTrue();
	}
}
