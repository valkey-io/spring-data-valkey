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
package io.valkey.springframework.data.valkey.connection.lettuce;

import io.lettuce.core.api.sync.RedisServerCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import io.valkey.springframework.data.valkey.connection.ClusterCommandExecutor.MultiNodeResult;
import io.valkey.springframework.data.valkey.connection.ClusterCommandExecutor.NodeResult;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterServerCommands;
import io.valkey.springframework.data.valkey.connection.convert.Converters;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceClusterConnection.LettuceClusterCommandCallback;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;
import org.springframework.util.Assert;

/**
 * @author Mark Paluch
 * @author Dennis Neufeld
 * @since 2.0
 */
class LettuceClusterServerCommands extends LettuceServerCommands implements ValkeyClusterServerCommands {

	private final LettuceClusterConnection connection;

	LettuceClusterServerCommands(LettuceClusterConnection connection) {

		super(connection);
		this.connection = connection;
	}

	@Override
	public void bgReWriteAof(ValkeyClusterNode node) {
		executeCommandOnSingleNode(RedisServerCommands::bgrewriteaof, node);
	}

	@Override
	public void bgSave(ValkeyClusterNode node) {
		executeCommandOnSingleNode(RedisServerCommands::bgsave, node);
	}

	@Override
	public Long lastSave(ValkeyClusterNode node) {
		return executeCommandOnSingleNode(client -> client.lastsave().getTime(), node).getValue();
	}

	@Override
	public void save(ValkeyClusterNode node) {
		executeCommandOnSingleNode(RedisServerCommands::save, node);
	}

	@Override
	public Long dbSize(ValkeyClusterNode node) {
		return executeCommandOnSingleNode(RedisServerCommands::dbsize, node).getValue();
	}

	@Override
	public void flushDb(ValkeyClusterNode node) {
		executeCommandOnSingleNode(RedisServerCommands::flushdb, node);
	}

	@Override
	public void flushDb(ValkeyClusterNode node, FlushOption option) {
		executeCommandOnSingleNode(it -> it.flushdb(LettuceConverters.toFlushMode(option)), node);
	}

	@Override
	public void flushAll(ValkeyClusterNode node) {
		executeCommandOnSingleNode(RedisServerCommands::flushall, node);
	}

	@Override
	public void flushAll(ValkeyClusterNode node, FlushOption option) {
		executeCommandOnSingleNode(it -> it.flushall(LettuceConverters.toFlushMode(option)), node);
	}

	@Override
	public Properties info(ValkeyClusterNode node) {
		return LettuceConverters.toProperties(executeCommandOnSingleNode(RedisServerCommands::info, node).getValue());
	}

	@Override
	public Properties info() {

		Properties infos = new Properties();

		List<NodeResult<Properties>> nodeResults = executeCommandOnAllNodes(
				client -> LettuceConverters.toProperties(client.info())).getResults();

		for (NodeResult<Properties> nodePorperties : nodeResults) {
			for (Entry<Object, Object> entry : nodePorperties.getValue().entrySet()) {
				infos.put(nodePorperties.getNode().asString() + "." + entry.getKey(), entry.getValue());
			}
		}

		return infos;
	}

	@Override
	public Properties info(String section) {

		Assert.hasText(section, "Section must not be null or empty");

		Properties infos = new Properties();
		List<NodeResult<Properties>> nodeResults = executeCommandOnAllNodes(
				client -> LettuceConverters.toProperties(client.info(section))).getResults();

		for (NodeResult<Properties> nodePorperties : nodeResults) {
			for (Entry<Object, Object> entry : nodePorperties.getValue().entrySet()) {
				infos.put(nodePorperties.getNode().asString() + "." + entry.getKey(), entry.getValue());
			}
		}

		return infos;
	}

	@Override
	public Properties info(ValkeyClusterNode node, String section) {

		Assert.hasText(section, "Section must not be null or empty");

		return LettuceConverters.toProperties(executeCommandOnSingleNode(client -> client.info(section), node).getValue());
	}

	@Override
	public void shutdown(ValkeyClusterNode node) {

		executeCommandOnSingleNode((LettuceClusterCommandCallback<Void>) client -> {
			client.shutdown(true);
			return null;
		}, node);
	}

	@Override
	public Properties getConfig(String pattern) {

		Assert.hasText(pattern, "Pattern must not be null or empty");

		List<NodeResult<Map<String, String>>> mapResult = executeCommandOnAllNodes(client -> client.configGet(pattern))
				.getResults();

		Properties properties = new Properties();

		for (NodeResult<Map<String, String>> entry : mapResult) {

			String prefix = entry.getNode().asString();
			entry.getValue().forEach((key, value) -> properties.setProperty(prefix + "." + key, value));
		}

		return properties;
	}

	@Override
	public Properties getConfig(ValkeyClusterNode node, String pattern) {

		Assert.hasText(pattern, "Pattern must not be null or empty");

		return executeCommandOnSingleNode(client -> Converters.toProperties(client.configGet(pattern)), node).getValue();
	}

	@Override
	public void setConfig(String param, String value) {

		Assert.hasText(param, "Parameter must not be null or empty");
		Assert.notNull(value, "Value must not be null");

		executeCommandOnAllNodes(client -> client.configSet(param, value));
	}

	@Override
	public void setConfig(ValkeyClusterNode node, String param, String value) {

		Assert.hasText(param, "Parameter must not be null or empty");
		Assert.hasText(value, "Value must not be null or empty");

		executeCommandOnSingleNode(client -> client.configSet(param, value), node);
	}

	@Override
	public void resetConfigStats() {
		executeCommandOnAllNodes(RedisServerCommands::configResetstat);
	}

	@Override
	public void resetConfigStats(ValkeyClusterNode node) {
		executeCommandOnSingleNode(RedisServerCommands::configResetstat, node);
	}

	@Override
	public void rewriteConfig() {
		executeCommandOnAllNodes(RedisServerCommands::configRewrite);
	}

	@Override
	public void rewriteConfig(ValkeyClusterNode node) {
		executeCommandOnSingleNode(RedisServerCommands::configRewrite, node);
	}

	@Override
	public Long time(ValkeyClusterNode node, TimeUnit timeUnit) {
		return convertListOfStringToTime(executeCommandOnSingleNode(RedisServerCommands::time, node).getValue(), timeUnit);
	}

	@Override
	public List<ValkeyClientInfo> getClientList() {

		List<String> map = executeCommandOnAllNodes(RedisServerCommands::clientList).resultsAsList();

		ArrayList<ValkeyClientInfo> result = new ArrayList<>();
		for (String infos : map) {
			result.addAll(LettuceConverters.toListOfValkeyClientInformation(infos));
		}
		return result;
	}

	@Override
	public List<ValkeyClientInfo> getClientList(ValkeyClusterNode node) {

		return LettuceConverters
				.toListOfValkeyClientInformation(executeCommandOnSingleNode(RedisServerCommands::clientList, node).getValue());
	}

	@Override
	public void replicaOf(String host, int port) {
		throw new InvalidDataAccessApiUsageException(
				"REPLICAOF is not supported in cluster environment; Please use CLUSTER REPLICATE.");
	}

	@Override
	public void replicaOfNoOne() {
		throw new InvalidDataAccessApiUsageException(
				"REPLICAOF is not supported in cluster environment; Please use CLUSTER REPLICATE.");
	}

	private <T> NodeResult<T> executeCommandOnSingleNode(LettuceClusterCommandCallback<T> command,
			ValkeyClusterNode node) {
		return connection.getClusterCommandExecutor().executeCommandOnSingleNode(command, node);
	}

	private <T> MultiNodeResult<T> executeCommandOnAllNodes(final LettuceClusterCommandCallback<T> cmd) {
		return connection.getClusterCommandExecutor().executeCommandOnAllNodes(cmd);
	}

	private static Long convertListOfStringToTime(List<byte[]> serverTimeInformation, TimeUnit timeUnit) {

		Assert.notEmpty(serverTimeInformation, "Received invalid result from server; Expected 2 items in collection.");
		Assert.isTrue(serverTimeInformation.size() == 2,
				"Received invalid number of arguments from valkey server; Expected 2 received " + serverTimeInformation.size());

		return Converters.toTimeMillis(LettuceConverters.toString(serverTimeInformation.get(0)),
				LettuceConverters.toString(serverTimeInformation.get(1)), timeUnit);
	}
}
