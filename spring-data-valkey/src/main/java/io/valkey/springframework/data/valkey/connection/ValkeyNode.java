/*
 * Copyright 2014-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author LeeHyungGeol
 * @since 1.4
 */
public class ValkeyNode implements NamedNode {

	public static final int DEFAULT_PORT = 6379;
	public static final int DEFAULT_SENTINEL_PORT = 26379;

	@Nullable String id;
	@Nullable String name;
	@Nullable String host;
	int port;
	@Nullable NodeType type;
	@Nullable String masterId;

	/**
	 * Creates a new {@link ValkeyNode} with the given {@code host}, {@code port}.
	 *
	 * @param host must not be {@literal null}
	 * @param port
	 */
	public ValkeyNode(String host, int port) {

		Assert.notNull(host, "host must not be null");

		this.host = host;
		this.port = port;
	}

	protected ValkeyNode() {}

	private ValkeyNode(ValkeyNode valkeyNode) {

		this.id = valkeyNode.id;
		this.name = valkeyNode.name;
		this.host = valkeyNode.host;
		this.port = valkeyNode.port;
		this.type = valkeyNode.type;
		this.masterId = valkeyNode.masterId;
	}

	/**
	 * Parse a {@code hostAndPort} string into {@link ValkeyNode}. Supports IPv4, IPv6, and hostname notations including
	 * the port. For example:
	 *
	 * <pre class="code">
	 * ValkeyNode.fromString("127.0.0.1");
	 * ValkeyNode.fromString("127.0.0.1:6379");
	 * ValkeyNode.fromString("[aaaa:bbbb::dddd:eeee]");
	 * ValkeyNode.fromString("[aaaa:bbbb::dddd:eeee]:6379");
	 * ValkeyNode.fromString("my.valkey.server");
	 * ValkeyNode.fromString("my.valkey.server:6379");
	 * </pre>
	 *
	 * @param hostPortString must not be {@literal null} or empty.
	 * @return the parsed {@link ValkeyNode}.
	 * @since 2.7.4
	 */
	public static ValkeyNode fromString(String hostPortString) {
		return fromString(hostPortString, DEFAULT_PORT);
	}

	/**
	 * Parse a {@code hostAndPort} string into {@link ValkeyNode}. Supports IPv4, IPv6, and hostname notations including
	 * the port. For example:
	 *
	 * <pre class="code">
	 * ValkeyNode.fromString("127.0.0.1");
	 * ValkeyNode.fromString("127.0.0.1:6379");
	 * ValkeyNode.fromString("[aaaa:bbbb::dddd:eeee]");
	 * ValkeyNode.fromString("[aaaa:bbbb::dddd:eeee]:6379");
	 * ValkeyNode.fromString("my.valkey.server");
	 * ValkeyNode.fromString("my.valkey.server:6379");
	 * </pre>
	 *
	 * @param hostPortString must not be {@literal null} or empty.
	 * @return the parsed {@link ValkeyNode}.
	 * @since 3.4
	 */
	public static ValkeyNode fromString(String hostPortString, int defaultPort) {

		Assert.notNull(hostPortString, "HostAndPort must not be null");

		String host;
		String portString = null;

		if (hostPortString.startsWith("[")) {
			String[] hostAndPort = getHostAndPortFromBracketedHost(hostPortString);
			host = hostAndPort[0];
			portString = hostAndPort[1];
		} else {
			int colonPos = hostPortString.indexOf(':');
			if (colonPos >= 0 && hostPortString.indexOf(':', colonPos + 1) == -1) {
				// Exactly 1 colon. Split into host:port.
				host = hostPortString.substring(0, colonPos);
				portString = hostPortString.substring(colonPos + 1);
			} else {
				// 0 or 2+ colons. Bare hostname or IPv6 literal.
				int lastColonIndex = hostPortString.lastIndexOf(':');

				// IPv6 literal
				if (lastColonIndex > hostPortString.indexOf(']')) {
					host = hostPortString.substring(0, lastColonIndex);
					portString = hostPortString.substring(lastColonIndex + 1);
				} else {
					// bare hostname
					host = hostPortString;
				}
			}
		}

		int port = defaultPort;

		if (StringUtils.hasText(portString)) {
			try {
				port = Integer.parseInt(portString);
			} catch (RuntimeException ignore) {
				throw new IllegalArgumentException("Unparseable port number: %s".formatted(hostPortString));
			}
		}

		if (!isValidPort(port)) {
			throw new IllegalArgumentException("Port number out of range: %s".formatted(hostPortString));
		}

		return new ValkeyNode(host, port);
	}

	/**
	 * Parses a bracketed host-port string, throwing IllegalArgumentException if parsing fails.
	 *
	 * @param hostPortString the full bracketed host-port specification. Post might not be specified.
	 * @return an array with 2 strings: host and port, in that order.
	 * @throws IllegalArgumentException if parsing the bracketed host-port string fails.
	 */
	private static String[] getHostAndPortFromBracketedHost(String hostPortString) {

		if (hostPortString.charAt(0) != '[') {
			throw new IllegalArgumentException(
					"Bracketed host-port string must start with a bracket: %s".formatted(hostPortString));
		}

		int colonIndex = hostPortString.indexOf(':');
		int closeBracketIndex = hostPortString.lastIndexOf(']');

		if (!(colonIndex > -1 && closeBracketIndex > colonIndex)) {
			throw new IllegalArgumentException("Invalid bracketed host/port: %s".formatted(hostPortString));
		}

		String host = hostPortString.substring(1, closeBracketIndex);
		if (closeBracketIndex + 1 == hostPortString.length()) {
			return new String[] { host, "" };
		} else {
			if (!(hostPortString.charAt(closeBracketIndex + 1) == ':')) {
				throw new IllegalArgumentException("Only a colon may follow a close bracket: %s".formatted(hostPortString));
			}
			for (int i = closeBracketIndex + 2; i < hostPortString.length(); ++i) {
				if (!Character.isDigit(hostPortString.charAt(i))) {
					throw new IllegalArgumentException("Port must be numeric: %s".formatted(hostPortString));
				}
			}
			return new String[] { host, hostPortString.substring(closeBracketIndex + 2) };
		}
	}

	/**
	 * @return can be {@literal null}.
	 */
	@Nullable
	public String getHost() {
		return host;
	}

	/**
	 * @return whether this node has a valid host (not null and not empty).
	 * @since 2.3.8
	 */
	public boolean hasValidHost() {
		return StringUtils.hasText(host);
	}

	/**
	 * @return can be {@literal null}.
	 */
	@Nullable
	public Integer getPort() {
		return port;
	}

	public String asString() {

		if (host != null && host.contains(":")) {
			return "[" + host + "]:" + port;
		}

		return host + ":" + port;
	}

	@Override
	@Nullable
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return can be {@literal null}.
	 * @since 1.7
	 */
	@Nullable
	public String getMasterId() {
		return masterId;
	}

	/**
	 * @return can be {@literal null}.
	 * @since 1.7
	 */
	@Nullable
	public String getId() {
		return id;
	}

	/**
	 * @param id
	 * @since 1.7
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return can be {@literal null}.
	 * @since 1.7
	 */
	@Nullable
	public NodeType getType() {
		return type;
	}

	/**
	 * @return
	 * @since 1.7
	 */
	public boolean isMaster() {
		return ObjectUtils.nullSafeEquals(NodeType.MASTER, getType());
	}

	/**
	 * @return
	 * @since 2.1
	 */
	public boolean isReplica() {
		return ObjectUtils.nullSafeEquals(NodeType.REPLICA, getType());
	}

	/**
	 * Get {@link ValkeyNodeBuilder} for creating new {@link ValkeyNode}.
	 *
	 * @return never {@literal null}.
	 * @since 1.7
	 */
	public static ValkeyNodeBuilder newValkeyNode() {
		return new ValkeyNodeBuilder();
	}

	@Override
	public String toString() {
		return asString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ObjectUtils.nullSafeHashCode(host);
		result = prime * result + ObjectUtils.nullSafeHashCode(port);
		return result;
	}

	@Override
	public boolean equals(@Nullable Object obj) {

		if (this == obj) {
			return true;
		}
		if (obj == null || !(obj instanceof ValkeyNode other)) {
			return false;
		}

		if (!ObjectUtils.nullSafeEquals(this.host, other.host)) {
			return false;
		}

		if (!ObjectUtils.nullSafeEquals(this.port, other.port)) {
			return false;
		}

		if (!ObjectUtils.nullSafeEquals(this.name, other.name)) {
			return false;
		}

		return true;
	}

	/**
	 * @author Christoph Strobl
	 * @since 1.7
	 */
	public enum NodeType {
		MASTER, REPLICA
	}

	/**
	 * Builder for creating new {@link ValkeyNode}.
	 *
	 * @author Christoph Strobl
	 * @since 1.4
	 */
	public static class ValkeyNodeBuilder {

		private ValkeyNode node;

		public ValkeyNodeBuilder() {
			node = new ValkeyNode();
		}

		/**
		 * Define node name.
		 */
		public ValkeyNodeBuilder withName(String name) {
			node.name = name;
			return this;
		}

		/**
		 * Set host and port of server.
		 *
		 * @param host must not be {@literal null}.
		 * @param port
		 * @return
		 */
		public ValkeyNodeBuilder listeningAt(String host, int port) {

			Assert.notNull(host, "Hostname must not be null");
			node.host = host;
			node.port = port;
			return this;
		}

		/**
		 * Set id of server.
		 *
		 * @param id
		 * @return
		 */
		public ValkeyNodeBuilder withId(String id) {

			node.id = id;
			return this;
		}

		/**
		 * Set server role.
		 *
		 * @param type
		 * @return
		 * @since 1.7
		 */
		public ValkeyNodeBuilder promotedAs(NodeType type) {

			node.type = type;
			return this;
		}

		/**
		 * Set the id of the master node.
		 *
		 * @param masterId
		 * @return this.
		 * @since 2.1
		 */
		public ValkeyNodeBuilder replicaOf(String masterId) {

			node.masterId = masterId;
			return this;
		}

		/**
		 * Get the {@link ValkeyNode}.
		 *
		 * @return
		 */
		public ValkeyNode build() {
			return new ValkeyNode(this.node);
		}
	}

	private static boolean isValidPort(int port) {
		return port >= 0 && port <= 65535;
	}

}
