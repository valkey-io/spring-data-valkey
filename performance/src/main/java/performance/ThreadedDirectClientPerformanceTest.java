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

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import redis.clients.jedis.Jedis;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded direct client performance test across multiple threads.
 */
public class ThreadedDirectClientPerformanceTest {

	private static final int THREADS = 100;
	private static final int OPERATIONS_PER_THREAD = 100;
	private static final int TOTAL_OPERATIONS = THREADS * OPERATIONS_PER_THREAD;
	private static final String KEY_PREFIX = "threaded:test:";

	public static void main(String[] args) throws Exception {
		String clientType = System.getProperty("client", "valkeyglide");
		
		System.out.println("Running Multi-Threaded Direct Client Performance Test");
		System.out.println("Client: " + clientType);
		System.out.println("Threads: " + THREADS);
		System.out.println("Operations per thread: " + OPERATIONS_PER_THREAD);
		System.out.println("Total operations: " + TOTAL_OPERATIONS);
		System.out.println("----------------------------------------");

		switch (clientType.toLowerCase()) {
			case "valkeyglide" -> testValkeyGlide();
			case "lettuce" -> testLettuce();
			case "jedis" -> testJedis();
			default -> throw new IllegalArgumentException("Unknown client: " + clientType);
		}
	}

	private static void testValkeyGlide() throws Exception {
		GlideClientConfiguration config = GlideClientConfiguration.builder()
			.address(NodeAddress.builder().host("localhost").port(6379).build())
			.build();
		
		try (GlideClient client = GlideClient.createClient(config).get()) {
			ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
			AtomicInteger setSuccess = new AtomicInteger(0);
			AtomicInteger getSuccess = new AtomicInteger(0);
			AtomicInteger deleteSuccess = new AtomicInteger(0);
			
			try {
				// SET operations
				long start = System.nanoTime();
				Runnable setTask = () -> {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							client.set(GlideString.of(KEY_PREFIX + id), GlideString.of("value" + id)).get();
							setSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				};
				
				for (int i = 0; i < THREADS; i++) {
					executorService.submit(setTask);
				}
				executorService.shutdown();
				executorService.awaitTermination(10, TimeUnit.SECONDS);
				long setTime = System.nanoTime() - start;
				printResult("SET", setTime, setSuccess.get());

				// GET operations
				executorService = Executors.newFixedThreadPool(THREADS);
				start = System.nanoTime();
				Runnable getTask = () -> {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							client.get(GlideString.of(KEY_PREFIX + id)).get();
							getSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				};
				
				for (int i = 0; i < THREADS; i++) {
					executorService.submit(getTask);
				}
				executorService.shutdown();
				executorService.awaitTermination(10, TimeUnit.SECONDS);
				long getTime = System.nanoTime() - start;
				printResult("GET", getTime, getSuccess.get());

				// DELETE operations
				executorService = Executors.newFixedThreadPool(THREADS);
				start = System.nanoTime();
				Runnable deleteTask = () -> {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							client.del(new GlideString[]{GlideString.of(KEY_PREFIX + id)}).get();
							deleteSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				};
				
				for (int i = 0; i < THREADS; i++) {
					executorService.submit(deleteTask);
				}
				executorService.shutdown();
				executorService.awaitTermination(10, TimeUnit.SECONDS);
				long deleteTime = System.nanoTime() - start;
				printResult("DELETE", deleteTime, deleteSuccess.get());
			} finally {
				if (!executorService.isShutdown()) {
					executorService.shutdown();
				}
			}
		}
	}

	private static void testLettuce() throws Exception {
		RedisClient client = RedisClient.create(RedisURI.create("redis://localhost:6379"));
		
		try (StatefulRedisConnection<String, String> connection = client.connect()) {
			RedisCommands<String, String> commands = connection.sync();
			ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
			AtomicInteger setSuccess = new AtomicInteger(0);
			AtomicInteger getSuccess = new AtomicInteger(0);
			AtomicInteger deleteSuccess = new AtomicInteger(0);
			
			try {
				// SET operations
				long start = System.nanoTime();
				Runnable setTask = () -> {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							commands.set(KEY_PREFIX + id, "value" + id);
							setSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				};
				
				for (int i = 0; i < THREADS; i++) {
					executorService.submit(setTask);
				}
				executorService.shutdown();
				executorService.awaitTermination(10, TimeUnit.SECONDS);
				long setTime = System.nanoTime() - start;
				printResult("SET", setTime, setSuccess.get());

				// GET operations
				executorService = Executors.newFixedThreadPool(THREADS);
				start = System.nanoTime();
				Runnable getTask = () -> {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							commands.get(KEY_PREFIX + id);
							getSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				};
				
				for (int i = 0; i < THREADS; i++) {
					executorService.submit(getTask);
				}
				executorService.shutdown();
				executorService.awaitTermination(10, TimeUnit.SECONDS);
				long getTime = System.nanoTime() - start;
				printResult("GET", getTime, getSuccess.get());

				// DELETE operations
				executorService = Executors.newFixedThreadPool(THREADS);
				start = System.nanoTime();
				Runnable deleteTask = () -> {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							commands.del(KEY_PREFIX + id);
							deleteSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				};
				
				for (int i = 0; i < THREADS; i++) {
					executorService.submit(deleteTask);
				}
				executorService.shutdown();
				executorService.awaitTermination(10, TimeUnit.SECONDS);
				long deleteTime = System.nanoTime() - start;
				printResult("DELETE", deleteTime, deleteSuccess.get());
			} finally {
				if (!executorService.isShutdown()) {
					executorService.shutdown();
				}
			}
		} finally {
			client.shutdown();
		}
	}

	private static void testJedis() throws Exception {
		// Jedis connections are not thread-safe, so we need one per thread
		ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
		AtomicInteger setSuccess = new AtomicInteger(0);
		AtomicInteger getSuccess = new AtomicInteger(0);
		AtomicInteger deleteSuccess = new AtomicInteger(0);
		
		try {
			// SET operations
			long start = System.nanoTime();
			Runnable setTask = () -> {
				try (Jedis jedis = new Jedis("localhost", 6379)) {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							jedis.set(KEY_PREFIX + id, "value" + id);
							setSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				}
			};
			
			for (int i = 0; i < THREADS; i++) {
				executorService.submit(setTask);
			}
			executorService.shutdown();
			executorService.awaitTermination(10, TimeUnit.SECONDS);
			long setTime = System.nanoTime() - start;
			printResult("SET", setTime, setSuccess.get());

			// GET operations
			executorService = Executors.newFixedThreadPool(THREADS);
			start = System.nanoTime();
			Runnable getTask = () -> {
				try (Jedis jedis = new Jedis("localhost", 6379)) {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							jedis.get(KEY_PREFIX + id);
							getSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				}
			};
			
			for (int i = 0; i < THREADS; i++) {
				executorService.submit(getTask);
			}
			executorService.shutdown();
			executorService.awaitTermination(10, TimeUnit.SECONDS);
			long getTime = System.nanoTime() - start;
			printResult("GET", getTime, getSuccess.get());

			// DELETE operations
			executorService = Executors.newFixedThreadPool(THREADS);
			start = System.nanoTime();
			Runnable deleteTask = () -> {
				try (Jedis jedis = new Jedis("localhost", 6379)) {
					for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
						try {
							int id = Thread.currentThread().hashCode() * OPERATIONS_PER_THREAD + i;
							jedis.del(KEY_PREFIX + id);
							deleteSuccess.incrementAndGet();
						} catch (Exception e) {
							// Ignore
						}
					}
				}
			};
			
			for (int i = 0; i < THREADS; i++) {
				executorService.submit(deleteTask);
			}
			executorService.shutdown();
			executorService.awaitTermination(10, TimeUnit.SECONDS);
			long deleteTime = System.nanoTime() - start;
			printResult("DELETE", deleteTime, deleteSuccess.get());
		} finally {
			if (!executorService.isShutdown()) {
				executorService.shutdown();
			}
		}
	}

	private static void printResult(String operation, long duration, int successful) {
		System.out.printf("%s:    %,d ops/sec (%.2f ms total), %.1f%% successful%n",
			operation, (long) (TOTAL_OPERATIONS / (duration / 1_000_000_000.0)), duration / 1_000_000.0,
			(successful * 100.0 / TOTAL_OPERATIONS));
	}
}
