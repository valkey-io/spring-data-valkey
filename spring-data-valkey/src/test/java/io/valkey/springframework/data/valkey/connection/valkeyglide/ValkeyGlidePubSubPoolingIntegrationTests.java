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
package io.valkey.springframework.data.valkey.connection.valkeyglide;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.valkey.springframework.data.valkey.SettingsUtils;
import io.valkey.springframework.data.valkey.connection.Message;
import io.valkey.springframework.data.valkey.connection.MessageListener;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.SubscriptionListener;
import io.valkey.springframework.data.valkey.test.condition.ValkeyDetector;
import io.valkey.springframework.data.valkey.test.extension.parametrized.MethodSource;
import io.valkey.springframework.data.valkey.test.extension.parametrized.ParameterizedValkeyTest;
import org.springframework.lang.Nullable;

/**
 * Integration tests for Valkey Glide pub/sub with connection pooling.
 * These tests specifically target issues that can arise when clients are pooled
 * and their state may persist between uses.
 */
@MethodSource("testParams")
class ValkeyGlidePubSubPoolingIntegrationTests {

    private static final Logger logger = LoggerFactory.getLogger(ValkeyGlidePubSubPoolingIntegrationTests.class);

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(2);
    private static final long TERMINATION_TIMEOUT_SECONDS = 10;

    private final boolean useCluster;

    // Shared publisher factory - created in setUp, destroyed in tearDown
    private ValkeyGlideConnectionFactory publisherFactory;

    // Test-specific factories - tests manage their own lifecycle
    private final List<ValkeyGlideConnectionFactory> testFactories = new ArrayList<>();

    public ValkeyGlidePubSubPoolingIntegrationTests(boolean useCluster) {
        this.useCluster = useCluster;
    }

    public static Collection<Object[]> testParams() {
        List<Object[]> params = new ArrayList<>();

        // Always test standalone
        params.add(new Object[] { false });

        // Also test cluster if available
        if (clusterAvailable()) {
            params.add(new Object[] { true });
        }

        return params;
    }

    private static boolean clusterAvailable() {
        return ValkeyDetector.isClusterAvailable();
    }

    @BeforeEach
    void setUp() {
        publisherFactory = createConnectionFactory(2);
    }

    @AfterEach
    void tearDown() {
        // Clean up all test-created factories
        for (ValkeyGlideConnectionFactory factory : testFactories) {
            destroyFactory(factory);
        }
        testFactories.clear();

        destroyFactory(publisherFactory);
    }

    /**
     * Creates a connection factory with the specified pool size and registers it for cleanup.
     */
    private ValkeyGlideConnectionFactory createTestFactory(int poolSize) {
        ValkeyGlideConnectionFactory factory = createConnectionFactory(poolSize);
        testFactories.add(factory);
        return factory;
    }

    private ValkeyGlideConnectionFactory createConnectionFactory(int poolSize) {
        ValkeyGlideClientConfiguration clientConfig = ValkeyGlideClientConfiguration.builder()
                .maxPoolSize(poolSize)
                .build();

        ValkeyGlideConnectionFactory factory;
        if (useCluster) {
            ValkeyClusterConfiguration clusterConfig = SettingsUtils.clusterConfiguration();
            factory = new ValkeyGlideConnectionFactory(clusterConfig, clientConfig);
        } else {
            ValkeyStandaloneConfiguration standaloneConfig = SettingsUtils.standaloneConfiguration();
            factory = new ValkeyGlideConnectionFactory(standaloneConfig, clientConfig);
        }

        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private void destroyFactory(@Nullable ValkeyGlideConnectionFactory factory) {
        if (factory != null) {
            try {
                factory.destroy();
            } catch (Exception e) {
                logger.warn("Failed to destroy factory: {}", e.getMessage());
            }
        }
    }

    private void publish(String channel, String message) {
        try (ValkeyConnection publisher = publisherFactory.getConnection()) {
            publisher.publish(channel.getBytes(), message.getBytes());
        }
    }

    interface CompositeListener extends MessageListener, SubscriptionListener {}

    // ==================== Single Client Pool Tests ====================
    // These tests use pool size 1 to guarantee client reuse

    @ParameterizedValkeyTest
    void sameClientShouldNotReceiveMessagesFromPreviousSubscription() throws Exception {
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(1);

        String channel1 = "test:pool:prev:ch1";
        String channel2 = "test:pool:prev:ch2";

        List<String> listener1Messages = Collections.synchronizedList(new ArrayList<>());
        List<String> listener2Messages = Collections.synchronizedList(new ArrayList<>());

        ValkeyConnection conn1 = connectionFactory.getConnection();
        Object nativeClient1 = conn1.getNativeConnection();

        conn1.subscribe((message, pattern) -> listener1Messages.add(new String(message.getBody())),
                channel1.getBytes());

        publish(channel1, "message1");

        await().atMost(AWAIT_TIMEOUT).until(() -> listener1Messages.size() >= 1);
        assertThat(listener1Messages).contains("message1");

        conn1.close();

        ValkeyConnection conn2 = connectionFactory.getConnection();
        assertThat(conn2.getNativeConnection()).isSameAs(nativeClient1);

        conn2.subscribe((message, pattern) -> listener2Messages.add(new String(message.getBody())),
                channel2.getBytes());

        publish(channel1, "message_to_old_channel");
        publish(channel2, "message2");

        await().atMost(AWAIT_TIMEOUT).until(() -> listener2Messages.size() >= 1);

        assertThat(listener2Messages).contains("message2");
        assertThat(listener2Messages).doesNotContain("message_to_old_channel");
        assertThat(listener1Messages).hasSize(1);

        conn2.close();
    }

    @ParameterizedValkeyTest
    void rapidSubscribeUnsubscribeCyclesOnSameClient() throws Exception {
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(1);

        String channelBase = "test:pool:rapid";
        int cycles = 10;
        Object firstClient = null;

        for (int i = 0; i < cycles; i++) {
            String channel = channelBase + ":" + i;
            AtomicInteger received = new AtomicInteger(0);

            ValkeyConnection conn = connectionFactory.getConnection();

            if (firstClient == null) {
                firstClient = conn.getNativeConnection();
            } else {
                assertThat(conn.getNativeConnection()).isSameAs(firstClient);
            }

            conn.subscribe((message, pattern) -> received.incrementAndGet(), channel.getBytes());

            publish(channel, "msg" + i);

            await().atMost(AWAIT_TIMEOUT).until(() -> received.get() >= 1);

            conn.close();
        }
    }

    @ParameterizedValkeyTest
    void subscriptionCallbacksWithCompositeListener() throws Exception {
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(1);

        String channel = "test:pool:callbacks";
        Object firstClient = null;

        for (int i = 0; i < 3; i++) {
            CompletableFuture<Void> subscribed = new CompletableFuture<>();
            CompletableFuture<Void> unsubscribed = new CompletableFuture<>();
            AtomicReference<byte[]> subscribedChannel = new AtomicReference<>();
            AtomicReference<byte[]> unsubscribedChannel = new AtomicReference<>();

            ValkeyConnection conn = connectionFactory.getConnection();

            if (firstClient == null) {
                firstClient = conn.getNativeConnection();
            } else {
                assertThat(conn.getNativeConnection()).isSameAs(firstClient);
            }

            CompositeListener listener = new CompositeListener() {
                @Override
                public void onMessage(Message message, @Nullable byte[] pattern) {}

                @Override
                public void onChannelSubscribed(byte[] ch, long count) {
                    subscribedChannel.set(ch);
                    subscribed.complete(null);
                }

                @Override
                public void onChannelUnsubscribed(byte[] ch, long count) {
                    unsubscribedChannel.set(ch);
                    unsubscribed.complete(null);
                }
            };

            conn.subscribe(listener, channel.getBytes());

            subscribed.get(1, TimeUnit.SECONDS);
            assertThat(subscribedChannel.get()).isEqualTo(channel.getBytes());

            conn.close();

            unsubscribed.get(1, TimeUnit.SECONDS);
            assertThat(unsubscribedChannel.get()).isEqualTo(channel.getBytes());
        }
    }

    @ParameterizedValkeyTest
    void partialUnsubscribeThenReuseClient() throws Exception {
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(1);

        String channel1 = "test:pool:partial:ch1";
        String channel2 = "test:pool:partial:ch2";
        String channel3 = "test:pool:partial:ch3";

        List<String> messages = Collections.synchronizedList(new ArrayList<>());

        ValkeyConnection conn1 = connectionFactory.getConnection();
        Object nativeClient = conn1.getNativeConnection();

        conn1.subscribe((message, pattern) -> {
            messages.add(new String(message.getChannel()) + ":" + new String(message.getBody()));
        }, channel1.getBytes(), channel2.getBytes());

        publish(channel1, "msg1");
        publish(channel2, "msg2");

        await().atMost(AWAIT_TIMEOUT).until(() -> messages.size() >= 2);

        conn1.getSubscription().unsubscribe(channel1.getBytes());
        conn1.close();

        messages.clear();

        ValkeyConnection conn2 = connectionFactory.getConnection();
        assertThat(conn2.getNativeConnection()).isSameAs(nativeClient);

        conn2.subscribe((message, pattern) -> {
            messages.add(new String(message.getChannel()) + ":" + new String(message.getBody()));
        }, channel3.getBytes());

        publish(channel1, "should_not_receive1");
        publish(channel2, "should_not_receive2");
        publish(channel3, "msg3");

        await().atMost(AWAIT_TIMEOUT).until(() -> messages.stream().anyMatch(m -> m.contains("msg3")));

        assertThat(messages.stream().filter(m -> m.contains("msg3")).count()).isEqualTo(1);
        assertThat(messages.stream().filter(m -> m.contains("should_not")).count()).isZero();

        conn2.close();
    }

    @ParameterizedValkeyTest
    void clientStateShouldBeCleanAfterNoOpConnection() throws Exception {
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(1);

        String channel = "test:pool:noop";

        ValkeyConnection conn1 = connectionFactory.getConnection();
        Object nativeClient = conn1.getNativeConnection();
        conn1.close();

        ValkeyConnection conn2 = connectionFactory.getConnection();
        assertThat(conn2.getNativeConnection()).isSameAs(nativeClient);

        AtomicReference<String> received = new AtomicReference<>();
        conn2.subscribe((message, pattern) -> received.set(new String(message.getBody())),
                channel.getBytes());

        publish(channel, "test_msg");

        await().atMost(AWAIT_TIMEOUT).until(() -> received.get() != null);
        assertThat(received.get()).isEqualTo("test_msg");

        conn2.close();
    }

    @ParameterizedValkeyTest
    void mixedChannelAndPatternSubscriptionsOnSameConnection() throws Exception {
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(1);

        String channel = "test:pool:mixed:exact";
        String patternBase = "test:pool:mixed:pattern";
        String pattern = patternBase + ":*";
        String patternMatch = patternBase + ":foo";

        List<String> receivedFromChannel = Collections.synchronizedList(new ArrayList<>());
        List<String> receivedFromPattern = Collections.synchronizedList(new ArrayList<>());

        ValkeyConnection conn = connectionFactory.getConnection();
        Object nativeClient = conn.getNativeConnection();

        conn.subscribe((message, pat) -> {
            if (pat == null) {
                receivedFromChannel.add(new String(message.getBody()));
            } else {
                receivedFromPattern.add(new String(message.getBody()));
            }
        }, channel.getBytes());

        conn.getSubscription().pSubscribe(pattern.getBytes());

        publish(channel, "channel_msg");
        publish(patternMatch, "pattern_msg");

        await().atMost(AWAIT_TIMEOUT).until(() ->
                receivedFromChannel.size() >= 1 && receivedFromPattern.size() >= 1);

        assertThat(receivedFromChannel).contains("channel_msg");
        assertThat(receivedFromPattern).contains("pattern_msg");

        conn.close();

        ValkeyConnection conn2 = connectionFactory.getConnection();
        assertThat(conn2.getNativeConnection()).isSameAs(nativeClient);

        List<String> newMessages = Collections.synchronizedList(new ArrayList<>());
        String newChannel = "test:pool:mixed:new";

        conn2.subscribe((message, pat) -> newMessages.add(new String(message.getBody())),
                newChannel.getBytes());

        publish(channel, "old_channel");
        publish(patternMatch, "old_pattern");
        publish(newChannel, "new_msg");

        await().atMost(AWAIT_TIMEOUT).until(() -> newMessages.contains("new_msg"));

        assertThat(newMessages).containsExactly("new_msg");

        conn2.close();
    }

    // ==================== Concurrent Usage Tests ====================
    // These tests use pool size 3 with semaphore to force reuse

    @ParameterizedValkeyTest
    void concurrentThreadsReceiveOnlyTheirOwnMessages() throws Exception {
        int poolSize = 3;
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(poolSize);
        Semaphore connectionLimiter = new Semaphore(poolSize);

        int threadCount = 6;
        int iterationsPerThread = 10;
        String channelBase = "test:concurrent:isolation";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;

            executor.submit(() -> {
                try {
                    for (int iter = 0; iter < iterationsPerThread; iter++) {
                        String myChannel = channelBase + ":t" + threadId + ":i" + iter;
                        String expectedMsg = "t" + threadId + "_i" + iter;
                        AtomicReference<String> received = new AtomicReference<>();

                        connectionLimiter.acquire();
                        try {
                            ValkeyConnection conn = connectionFactory.getConnection();

                            conn.subscribe((message, pattern) -> {
                                received.set(new String(message.getBody()));
                            }, myChannel.getBytes());

                            publish(myChannel, expectedMsg);

                            await().atMost(AWAIT_TIMEOUT).until(() -> received.get() != null);
                            assertThat(received.get()).isEqualTo(expectedMsg);

                            conn.close();
                        } finally {
                            connectionLimiter.release();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Thread {} failed: {}", threadId, e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(errorCount.get()).isZero();
    }

    @ParameterizedValkeyTest
    void concurrentPatternSubscriptions() throws Exception {
        int poolSize = 3;
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(poolSize);
        Semaphore connectionLimiter = new Semaphore(poolSize);

        int threadCount = 4;
        int iterationsPerThread = 8;
        String patternBase = "test:concurrent:pattern";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;

            executor.submit(() -> {
                try {
                    for (int iter = 0; iter < iterationsPerThread; iter++) {
                        String myPattern = patternBase + ":t" + threadId + ":*";
                        String myChannel = patternBase + ":t" + threadId + ":i" + iter;
                        String expectedMsg = "t" + threadId + "_iter" + iter;
                        AtomicReference<String> received = new AtomicReference<>();

                        connectionLimiter.acquire();
                        try {
                            ValkeyConnection conn = connectionFactory.getConnection();

                            conn.pSubscribe((message, pattern) -> {
                                received.set(new String(message.getBody()));
                            }, myPattern.getBytes());

                            publish(myChannel, expectedMsg);

                            await().atMost(AWAIT_TIMEOUT).until(() -> received.get() != null);
                            assertThat(received.get()).isEqualTo(expectedMsg);

                            conn.close();
                        } finally {
                            connectionLimiter.release();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Thread {} failed: {}", threadId, e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(errorCount.get()).isZero();
    }

    @ParameterizedValkeyTest
    void rapidConcurrentSubscribeUnsubscribeCycles() throws Exception {
        int poolSize = 3;
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(poolSize);
        Semaphore connectionLimiter = new Semaphore(poolSize);

        int threadCount = 5;
        int cyclesPerThread = 20;
        String channelBase = "test:concurrent:rapid";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;

            executor.submit(() -> {
                try {
                    for (int cycle = 0; cycle < cyclesPerThread; cycle++) {
                        String channel = channelBase + ":t" + threadId + ":c" + cycle;
                        String expectedMsg = "t" + threadId + "_c" + cycle;
                        AtomicReference<String> received = new AtomicReference<>();

                        connectionLimiter.acquire();
                        try {
                            ValkeyConnection conn = connectionFactory.getConnection();

                            conn.subscribe((message, pattern) -> {
                                received.set(new String(message.getBody()));
                            }, channel.getBytes());

                            publish(channel, expectedMsg);

                            await().atMost(AWAIT_TIMEOUT).until(() -> received.get() != null);
                            assertThat(received.get()).isEqualTo(expectedMsg);

                            conn.close();
                            successCount.incrementAndGet();
                        } finally {
                            connectionLimiter.release();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Thread {} failed: {}", threadId, e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(2 * TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(errorCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(threadCount * cyclesPerThread);
    }

    @ParameterizedValkeyTest
    void concurrentMixedChannelAndPatternSubscriptions() throws Exception {
        int poolSize = 3;
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(poolSize);
        Semaphore connectionLimiter = new Semaphore(poolSize);

        int threadCount = 4;
        int iterationsPerThread = 5;
        String base = "test:concurrent:mixed";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;

            executor.submit(() -> {
                try {
                    for (int iter = 0; iter < iterationsPerThread; iter++) {
                        String myChannel = base + ":channel:t" + threadId + ":i" + iter;
                        String myPattern = base + ":pattern:t" + threadId + ":*";
                        String myPatternChannel = base + ":pattern:t" + threadId + ":i" + iter;

                        AtomicReference<String> channelMsg = new AtomicReference<>();
                        AtomicReference<String> patternMsg = new AtomicReference<>();

                        connectionLimiter.acquire();
                        try {
                            ValkeyConnection conn = connectionFactory.getConnection();

                            conn.subscribe((message, pattern) -> {
                                String body = new String(message.getBody());
                                if (pattern != null) {
                                    patternMsg.set(body);
                                } else {
                                    channelMsg.set(body);
                                }
                            }, myChannel.getBytes());

                            conn.getSubscription().pSubscribe(myPattern.getBytes());

                            Thread.sleep(50);

                            publish(myChannel, "channel_" + threadId);
                            publish(myPatternChannel, "pattern_" + threadId);

                            await().atMost(AWAIT_TIMEOUT).until(() ->
                                    channelMsg.get() != null && patternMsg.get() != null);

                            assertThat(channelMsg.get()).isEqualTo("channel_" + threadId);
                            assertThat(patternMsg.get()).isEqualTo("pattern_" + threadId);

                            conn.close();
                        } finally {
                            connectionLimiter.release();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Thread {} failed: {}", threadId, e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(errorCount.get()).isZero();
    }

    @ParameterizedValkeyTest
    void highThroughputMessaging() throws Exception {
        int poolSize = 3;
        ValkeyGlideConnectionFactory connectionFactory = createTestFactory(poolSize);
        Semaphore connectionLimiter = new Semaphore(poolSize);

        int threadCount = 4;
        int messagesPerThread = 50;
        String channelBase = "test:concurrent:highthroughput";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;

            executor.submit(() -> {
                try {
                    String myChannel = channelBase + ":t" + threadId;
                    AtomicInteger receivedCount = new AtomicInteger(0);

                    connectionLimiter.acquire();
                    try {
                        ValkeyConnection conn = connectionFactory.getConnection();

                        conn.subscribe((message, pattern) -> {
                            receivedCount.incrementAndGet();
                        }, myChannel.getBytes());

                        try (ValkeyConnection pub = publisherFactory.getConnection()) {
                            for (int m = 0; m < messagesPerThread; m++) {
                                pub.publish(myChannel.getBytes(), ("msg" + m).getBytes());
                            }
                        }

                        await().atMost(Duration.ofSeconds(10))
                                .until(() -> receivedCount.get() >= messagesPerThread);

                        assertThat(receivedCount.get()).isEqualTo(messagesPerThread);

                        conn.close();
                    } finally {
                        connectionLimiter.release();
                    }
                } catch (Exception e) {
                    logger.error("Thread {} failed: {}", threadId, e.getMessage(), e);
                    errorCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(errorCount.get()).isZero();
    }
}
