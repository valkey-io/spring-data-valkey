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
package performance;

import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Parameterized load test for ValkeyTemplate using different clients and under various concurrency levels.
 */
public class TemplateLoadTest {

    public static void main(String[] args) throws Exception {
        String client = System.getProperty("client", "valkeyglide");
        int threads = Integer.parseInt(System.getProperty("threads", "10"));
        int operations = Integer.parseInt(System.getProperty("operations", "50"));
        int totalExpected = threads * operations * 2; // SET + GET

        System.out.println("Running Template Load Test");
        System.out.println("Client: " + client);
        System.out.println("Threads: " + threads);
        System.out.println("Operations per thread: " + operations);
        System.out.println("Total operations: " + totalExpected);
        System.out.println("----------------------------------------");

        ValkeyConnectionFactory factory = createConnectionFactory(client);
        if (factory instanceof InitializingBean) {
            ((InitializingBean) factory).afterPropertiesSet();
        }

        try {
            runLoadTest(factory, threads, operations, totalExpected);
        } finally {
            if (factory instanceof DisposableBean) {
                ((DisposableBean) factory).destroy();
            }
        }
    }

    private static ValkeyConnectionFactory createConnectionFactory(String clientType) {
        return switch (clientType.toLowerCase()) {
            case "lettuce" -> new LettuceConnectionFactory();
            case "jedis" -> new JedisConnectionFactory();
            case "valkeyglide" -> new ValkeyGlideConnectionFactory();
            default -> throw new IllegalArgumentException("Unknown client: " + clientType);
        };
    }

    private static void runLoadTest(ValkeyConnectionFactory factory, int threads, 
            int operations, int totalExpected) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        StringValkeyTemplate valkeyTemplate = new StringValkeyTemplate(factory);

        AtomicInteger setOperations = new AtomicInteger(0);
        AtomicInteger getOperations = new AtomicInteger(0);

        try {
            Runnable task = () -> IntStream.range(0, operations).forEach(i -> {
                String key = Thread.currentThread().getName() + ":" + i;
                String value = "value" + i;

                // SET operation
                try {
                    valkeyTemplate.opsForValue().set(key, value);
                    setOperations.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("SET error: " + e.getMessage());
                }

                // GET operation
                try {
                    String result = valkeyTemplate.opsForValue().get(key);
                    getOperations.incrementAndGet();
                    if (result != null && !result.equals(value)) {
                        System.err.println("Data mismatch! Expected: " + value + ", Got: " + result);
                    }
                } catch (Exception e) {
                    System.err.println("GET error: " + e.getMessage());
                }
            });

            IntStream.range(0, threads).forEach(i -> executorService.submit(task));

            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;
            int totalActual = setOperations.get() + getOperations.get();
            int dropped = totalExpected - totalActual;

            System.out.println("Duration: " + String.format("%.2f ms", (double) duration));
            System.out.println("Expected operations: " + totalExpected);
            System.out.println("SET operations: " + setOperations.get());
            System.out.println("GET operations: " + getOperations.get());
            System.out.println("Total operations: " + totalActual);
            System.out.println("Dropped operations: " + dropped);
            System.out.println("Success rate: " + String.format("%.2f%%", (totalActual * 100.0 / totalExpected)));
            System.out.println("Operations per second: " + String.format("%,d", (long) (totalActual * 1000.0 / duration)));
        } finally {
            executorService.shutdown();
        }
    }
}
