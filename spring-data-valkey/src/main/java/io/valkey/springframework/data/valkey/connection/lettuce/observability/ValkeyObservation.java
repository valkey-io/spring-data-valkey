/*
 * Copyright 2013-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.lettuce.observability;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * A Valkey-based {@link io.micrometer.observation.Observation}.
 *
 * @author Mark Paluch
 * @since 3.0
 * @deprecated since 3.4 for removal with the next major revision. Use Lettuce's Micrometer integration through
 *             {@link io.lettuce.core.tracing.MicrometerTracing}.
 */
@Deprecated(since = "3.4", forRemoval = true)
public enum ValkeyObservation implements ObservationDocumentation {

	/**
	 * Timer created around a Valkey command execution.
	 */
	VALKEY_COMMAND_OBSERVATION {

		@Override
		public String getName() {
			return "spring.data.valkey";
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowCardinalityCommandKeyNames.values();
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return HighCardinalityCommandKeyNames.values();
		}
	};

	/**
	 * Enums related to low cardinality key names for Valkey commands.
	 */
	enum LowCardinalityCommandKeyNames implements KeyName {

		/**
		 * Database system.
		 */
		DATABASE_SYSTEM {
			@Override
			public String asString() {
				return "db.system";
			}
		},

		/**
		 * Network transport.
		 */
		NET_TRANSPORT {
			@Override
			public String asString() {
				return "net.transport";
			}
		},

		/**
		 * Name of the database host.
		 */
		NET_PEER_NAME {
			@Override
			public String asString() {
				return "net.peer.name";
			}
		},

		/**
		 * Logical remote port number.
		 */
		NET_PEER_PORT {
			@Override
			public String asString() {
				return "net.peer.port";
			}
		},

		/**
		 * Mongo peer address.
		 */
		NET_SOCK_PEER_ADDR {
			@Override
			public String asString() {
				return "net.sock.peer.addr";
			}
		},

		/**
		 * Mongo peer port.
		 */
		NET_SOCK_PEER_PORT {
			@Override
			public String asString() {
				return "net.sock.peer.port";
			}
		},

		/**
		 * Valkey user.
		 */
		DB_USER {
			@Override
			public String asString() {
				return "db.user";
			}
		},

		/**
		 * Valkey database index.
		 */
		DB_INDEX {
			@Override
			public String asString() {
				return "db.valkey.database_index";
			}
		},

		/**
		 * Valkey command value.
		 */
		VALKEY_COMMAND {
			@Override
			public String asString() {
				return "db.operation";
			}
		}

	}

	/**
	 * Enums related to high cardinality key names for Valkey commands.
	 */
	enum HighCardinalityCommandKeyNames implements KeyName {

		/**
		 * Valkey statement.
		 */
		STATEMENT {
			@Override
			public String asString() {
				return "db.statement";
			}
		},

		/**
		 * Valkey error response.
		 */
		ERROR {
			@Override
			public String asString() {
				return "spring.data.valkey.command.error";
			}
		}
	}
}
