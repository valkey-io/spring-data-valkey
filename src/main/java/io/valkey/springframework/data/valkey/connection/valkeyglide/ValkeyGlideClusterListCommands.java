/*
 * Copyright 2017-2025 the original author or authors.
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

import io.valkey.springframework.data.valkey.connection.ClusterSlotHashUtil;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Ilia Kolominsky
 */
class ValkeyGlideClusterListCommands extends ValkeyGlideListCommands {

	private final ValkeyGlideClusterConnection connection;

	ValkeyGlideClusterListCommands(ValkeyGlideClusterConnection connection) {

		super(connection);
		this.connection = connection;
	}

	/**
	 * Executes a blocking pop command across multiple cluster nodes in parallel.
	 * Returns the first available result from any node. The timeout is enforced
	 * by the Redis server, not by this method.
	 * 
	 * @param command the command name ("BLPOP" or "BRPOP")
	 * @param timeout the Redis server timeout in seconds
	 * @param keys the keys to pop from
	 * @return the first available [key, value] pair, or empty list if all nodes timeout
	 */
	private List<byte[]> executeBlockingPopOnMultipleNodes(
			String command, int timeout, byte[]... keys) {
		
		Assert.notNull(keys, "Keys must not be null");
		Assert.noNullElements(keys, "Keys must not contain null elements");

		Map<ValkeyClusterNode, List<byte[]>> nodeKeyMap = connection.buildNodeKeyMap(keys);
		List<CompletableFuture<?>> futures = new ArrayList<>();
		
		for (Map.Entry<ValkeyClusterNode, List<byte[]>> entry : nodeKeyMap.entrySet()) {
			ValkeyClusterNode node = entry.getKey();
			List<byte[]> nodeKeys = entry.getValue();

			GlideString[] args = new GlideString[nodeKeys.size() + 2];
			args[0] = GlideString.of(command);
			for (int i = 0; i < nodeKeys.size(); i++) {
				args[i + 1] = GlideString.of(nodeKeys.get(i));
			}
			args[args.length - 1] = GlideString.of(String.valueOf(timeout));

			GlideClusterClient nativeConn = (GlideClusterClient) connection.getNativeConnection();
			futures.add(nativeConn.customCommand(args, 
				new ByAddressRoute(node.getHost(), node.getPort())));
		}

		try {
			CompletableFuture<Object> anyResult = CompletableFuture.anyOf(
				futures.toArray(new CompletableFuture[0]));
			
			@SuppressWarnings("unchecked")
			ClusterValue<Object> glideResult = (ClusterValue<Object>) anyResult.get();
			
			Object result = glideResult.getSingleValue();
			if (result == null) {
				return Collections.emptyList();
			}
			return ValkeyGlideConverters.toBytesList((Object[]) result);
			
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for " + command, ex);
		} catch (ExecutionException ex) {
			throw new RuntimeException("Error executing " + command + " on cluster", ex);
		} finally {
			futures.forEach(f -> f.cancel(true));
		}
	}

	@Override
	public List<byte[]> bLPop(int timeout, byte[]... keys) {
		return executeBlockingPopOnMultipleNodes("BLPOP", timeout, keys);
	}

	@Override
	public List<byte[]> bRPop(int timeout, byte[]... keys) {
		return executeBlockingPopOnMultipleNodes("BRPOP", timeout, keys);
	}

	@Override
	public byte[] rPopLPush(byte[] srcKey, byte[] dstKey) {

		Assert.notNull(srcKey, "Source key must not be null");
		Assert.notNull(dstKey, "Destination key must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(srcKey, dstKey)) {
			return super.rPopLPush(srcKey, dstKey);
		}

		// this is not atomic across nodes, but it is what Jedis does as well
		byte[] val = rPop(srcKey);
		lPush(dstKey, val);
		return val;
	}

	@Override
	public byte[] bRPopLPush(int timeout, byte[] srcKey, byte[] dstKey) {

		Assert.notNull(srcKey, "Source key must not be null");
		Assert.notNull(dstKey, "Destination key must not be null");

		if (ClusterSlotHashUtil.isSameSlotForAllKeys(srcKey, dstKey)) {
			return super.bRPopLPush(timeout, srcKey, dstKey);
		}

		// this is not atomic across nodes, but it is what Jedis does as well
		List<byte[]> val = bRPop(timeout, srcKey);
		if (!CollectionUtils.isEmpty(val)) {
			lPush(dstKey, val.get(1));
			return val.get(1);
		}

		return null;
	}
}
