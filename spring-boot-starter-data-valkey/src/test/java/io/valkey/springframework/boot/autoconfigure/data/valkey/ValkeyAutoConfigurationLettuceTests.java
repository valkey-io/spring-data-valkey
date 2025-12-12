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

package io.valkey.springframework.boot.autoconfigure.data.valkey;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.ReadFrom.Nodes;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions.RefreshTrigger;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import io.lettuce.core.models.role.RedisNodeDescription;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.tracing.Tracing;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;

import io.valkey.springframework.boot.autoconfigure.data.valkey.ValkeyProperties.Pool;
import io.valkey.springframework.boot.testsupport.classpath.ClassPathExclusions;
import io.valkey.springframework.boot.testsupport.classpath.resources.WithPackageResources;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeyPassword;
import io.valkey.springframework.data.valkey.connection.ValkeySentinelConfiguration;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceClientConfiguration;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceClientConfiguration.LettuceClientConfigurationBuilder;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettucePoolingClientConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ValkeyAutoConfiguration} when Valkey GLIDE is not on the classpath.
 *
 * @author Jeremy Parr-Pearson
 */
@ClassPathExclusions("valkey-glide-*.jar")
class ValkeyAutoConfigurationLettuceTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValkeyAutoConfiguration.class, SslAutoConfiguration.class));

	@Test
	void connectionFactoryDefaultsToLettuce() {
		this.contextRunner.run((context) -> assertThat(context.getBean("valkeyConnectionFactory"))
			.isInstanceOf(LettuceConnectionFactory.class));
	}

	@Test
	void connectionFactoryIsNotCreatedWhenValkeyGlideIsSelected() {
		this.contextRunner.withPropertyValues("spring.data.valkey.client-type=valkeyglide")
			.run((context) -> assertThat(context).doesNotHaveBean(ValkeyConnectionFactory.class));
	}

	@Test
	void testOverrideValkeyConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.database:1",
					"spring.data.valkey.lettuce.shutdown-timeout:500")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("foo");
				assertThat(cf.getDatabase()).isOne();
				assertThat(getUserName(cf)).isNull();
				assertThat(cf.getPassword()).isNull();
				assertThat(cf.isUseSsl()).isFalse();
				assertThat(cf.getShutdownTimeout()).isEqualTo(500);
			});
	}

	@Test
	void usesConnectionDetailsIfAvailable() {
		this.contextRunner.withUserConfiguration(ConnectionDetailsConfiguration.class).run((context) -> {
			LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
			assertThat(cf.isUseSsl()).isFalse();
		});
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource
	void shouldConfigureLettuceReadFromProperty(String type, ReadFrom readFrom) {
		this.contextRunner.withPropertyValues("spring.data.valkey.lettuce.read-from:" + type).run((context) -> {
			LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
			LettuceClientConfiguration configuration = factory.getClientConfiguration();
			assertThat(configuration.getReadFrom()).hasValue(readFrom);
		});
	}

	static Stream<Arguments> shouldConfigureLettuceReadFromProperty() {
		return Stream.of(Arguments.of("any", ReadFrom.ANY), Arguments.of("any-replica", ReadFrom.ANY_REPLICA),
				Arguments.of("lowest-latency", ReadFrom.LOWEST_LATENCY), Arguments.of("replica", ReadFrom.REPLICA),
				Arguments.of("replica-preferred", ReadFrom.REPLICA_PREFERRED),
				Arguments.of("upstream", ReadFrom.UPSTREAM),
				Arguments.of("upstream-preferred", ReadFrom.UPSTREAM_PREFERRED));
	}

	@Test
	void shouldConfigureLettuceRegexReadFromProperty() {
		RedisClusterNode node1 = createValkeyNode("valkey-node-1.region-1.example.com");
		RedisClusterNode node2 = createValkeyNode("valkey-node-2.region-1.example.com");
		RedisClusterNode node3 = createValkeyNode("valkey-node-1.region-2.example.com");
		RedisClusterNode node4 = createValkeyNode("valkey-node-2.region-2.example.com");
		this.contextRunner.withPropertyValues("spring.data.valkey.lettuce.read-from:regex:.*region-1.*")
			.run((context) -> {
				LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
				LettuceClientConfiguration configuration = factory.getClientConfiguration();
				assertThat(configuration.getReadFrom()).hasValueSatisfying((readFrom) -> {
					List<RedisNodeDescription> result = readFrom.select(new ValkeyNodes(node1, node2, node3, node4));
					assertThat(result).hasSize(2).containsExactly(node1, node2);
				});
			});
	}

	@Test
	void shouldConfigureLettuceSubnetReadFromProperty() {
		RedisClusterNode nodeInSubnetIpv4 = createValkeyNode("192.0.2.1");
		RedisClusterNode nodeNotInSubnetIpv4 = createValkeyNode("198.51.100.1");
		RedisClusterNode nodeInSubnetIpv6 = createValkeyNode("2001:db8:abcd:0000::1");
		RedisClusterNode nodeNotInSubnetIpv6 = createValkeyNode("2001:db8:abcd:1000::");
		this.contextRunner
			.withPropertyValues("spring.data.valkey.lettuce.read-from:subnet:192.0.2.0/24,2001:db8:abcd:0000::/52")
			.run((context) -> {
				LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
				LettuceClientConfiguration configuration = factory.getClientConfiguration();
				assertThat(configuration.getReadFrom()).hasValueSatisfying((readFrom) -> {
					List<RedisNodeDescription> result = readFrom.select(new ValkeyNodes(nodeInSubnetIpv4,
							nodeNotInSubnetIpv4, nodeInSubnetIpv6, nodeNotInSubnetIpv6));
					assertThat(result).hasSize(2).containsExactly(nodeInSubnetIpv4, nodeInSubnetIpv6);
				});
			});
	}

	@Test
	void testCustomizeClientResources() {
		Tracing tracing = mock(Tracing.class);
		this.contextRunner.withBean(ClientResourcesBuilderCustomizer.class, () -> (builder) -> builder.tracing(tracing))
			.run((context) -> {
				DefaultClientResources clientResources = context.getBean(DefaultClientResources.class);
				assertThat(clientResources.tracing()).isEqualTo(tracing);
			});
	}

	@Test
	void testCustomizeValkeyConfiguration() {
		this.contextRunner.withUserConfiguration(CustomConfiguration.class).run((context) -> {
			LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
			assertThat(cf.isUseSsl()).isTrue();
		});
	}

	@Test
	void testValkeyUrlConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.url:valkey://user:password@example:33")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("example");
				assertThat(cf.getPort()).isEqualTo(33);
				assertThat(getUserName(cf)).isEqualTo("user");
				assertThat(cf.getPassword()).isEqualTo("password");
				assertThat(cf.isUseSsl()).isFalse();
			});
	}

	@Test
	void testOverrideUrlValkeyConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.host:foo", "spring.valkey.data.user:alice",
					"spring.data.valkey.password:xyz", "spring.data.valkey.port:1000",
					"spring.data.valkey.ssl.enabled:false", "spring.data.valkey.url:valkeys://user:password@example:33")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("example");
				assertThat(cf.getPort()).isEqualTo(33);
				assertThat(getUserName(cf)).isEqualTo("user");
				assertThat(cf.getPassword()).isEqualTo("password");
				assertThat(cf.isUseSsl()).isTrue();
			});
	}

	@Test
	void testPasswordInUrlWithColon() {
		this.contextRunner.withPropertyValues("spring.data.valkey.url:valkey://:pass:word@example:33").run((context) -> {
			LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
			assertThat(cf.getHostName()).isEqualTo("example");
			assertThat(cf.getPort()).isEqualTo(33);
			assertThat(getUserName(cf)).isEmpty();
			assertThat(cf.getPassword()).isEqualTo("pass:word");
		});
	}

	@Test
	void testPasswordInUrlStartsWithColon() {
		this.contextRunner.withPropertyValues("spring.data.valkey.url:valkey://user::pass:word@example:33")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("example");
				assertThat(cf.getPort()).isEqualTo(33);
				assertThat(getUserName(cf)).isEqualTo("user");
				assertThat(cf.getPassword()).isEqualTo(":pass:word");
			});
	}

	@Test
	void testValkeyConfigurationUsePoolByDefault() {
		Pool defaultPool = new ValkeyProperties().getLettuce().getPool();
		this.contextRunner.withPropertyValues("spring.data.valkey.host:foo").run((context) -> {
			LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
			assertThat(cf.getHostName()).isEqualTo("foo");
			GenericObjectPoolConfig<?> poolConfig = getPoolingClientConfiguration(cf).getPoolConfig();
			assertThat(poolConfig.getMinIdle()).isEqualTo(defaultPool.getMinIdle());
			assertThat(poolConfig.getMaxIdle()).isEqualTo(defaultPool.getMaxIdle());
			assertThat(poolConfig.getMaxTotal()).isEqualTo(defaultPool.getMaxActive());
			assertThat(poolConfig.getMaxWaitDuration()).isEqualTo(defaultPool.getMaxWait());
		});
	}

	@Test
	void testValkeyConfigurationWithCustomPoolSettings() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.lettuce.pool.min-idle:1",
					"spring.data.valkey.lettuce.pool.max-idle:4", "spring.data.valkey.lettuce.pool.max-active:16",
					"spring.data.valkey.lettuce.pool.max-wait:2000",
					"spring.data.valkey.lettuce.pool.time-between-eviction-runs:30000",
					"spring.data.valkey.lettuce.shutdown-timeout:1000")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("foo");
				GenericObjectPoolConfig<?> poolConfig = getPoolingClientConfiguration(cf).getPoolConfig();
				assertThat(poolConfig.getMinIdle()).isOne();
				assertThat(poolConfig.getMaxIdle()).isEqualTo(4);
				assertThat(poolConfig.getMaxTotal()).isEqualTo(16);
				assertThat(poolConfig.getMaxWaitDuration()).isEqualTo(Duration.ofSeconds(2));
				assertThat(poolConfig.getDurationBetweenEvictionRuns()).isEqualTo(Duration.ofSeconds(30));
				assertThat(cf.getShutdownTimeout()).isEqualTo(1000);
			});
	}

	@Test
	void testValkeyConfigurationDisabledPool() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.lettuce.pool.enabled:false")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("foo");
				assertThat(cf.getClientConfiguration()).isNotInstanceOf(LettucePoolingClientConfiguration.class);
			});
	}

	@Test
	void testValkeyConfigurationWithTimeoutAndConnectTimeout() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.timeout:250",
					"spring.data.valkey.connect-timeout:1000")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("foo");
				assertThat(cf.getTimeout()).isEqualTo(250);
				assertThat(cf.getClientConfiguration()
					.getClientOptions()
					.get()
					.getSocketOptions()
					.getConnectTimeout()
					.toMillis()).isEqualTo(1000);
			});
	}

	@Test
	void testValkeyConfigurationWithDefaultTimeouts() {
		this.contextRunner.withPropertyValues("spring.data.valkey.host:foo").run((context) -> {
			LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
			assertThat(cf.getHostName()).isEqualTo("foo");
			assertThat(cf.getTimeout()).isEqualTo(60000);
			assertThat(cf.getClientConfiguration()
				.getClientOptions()
				.get()
				.getSocketOptions()
				.getConnectTimeout()
				.toMillis()).isEqualTo(10000);
		});
	}

	@Test
	void testValkeyConfigurationWithClientName() {
		this.contextRunner.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.client-name:spring-boot")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("foo");
				assertThat(cf.getClientName()).isEqualTo("spring-boot");
			});
	}

	@Test
	void testValkeyConfigurationWithSentinel() {
		List<String> sentinels = Arrays.asList("127.0.0.1:26379", "127.0.0.1:26380");
		this.contextRunner
			.withPropertyValues("spring.data.valkey.sentinel.master:mymaster",
					"spring.data.valkey.sentinel.nodes:" + StringUtils.collectionToCommaDelimitedString(sentinels))
			.run((context) -> assertThat(context.getBean(LettuceConnectionFactory.class).isValkeySentinelAware())
				.isTrue());
	}

	@Test
	void testValkeyConfigurationWithIpv6Sentinel() {
		List<String> sentinels = Arrays.asList("[0:0:0:0:0:0:0:1]:26379", "[0:0:0:0:0:0:0:1]:26380");
		this.contextRunner
			.withPropertyValues("spring.data.valkey.sentinel.master:mymaster",
					"spring.data.valkey.sentinel.nodes:" + StringUtils.collectionToCommaDelimitedString(sentinels))
			.run((context) -> {
				LettuceConnectionFactory connectionFactory = context.getBean(LettuceConnectionFactory.class);
				assertThat(connectionFactory.isValkeySentinelAware()).isTrue();
				assertThat(connectionFactory.getSentinelConfiguration().getSentinels()).isNotNull()
					.containsExactlyInAnyOrder(new ValkeyNode("[0:0:0:0:0:0:0:1]", 26379),
							new ValkeyNode("[0:0:0:0:0:0:0:1]", 26380));
			});
	}

	@Test
	void testValkeyConfigurationWithSentinelAndDatabase() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.database:1", "spring.data.valkey.sentinel.master:mymaster",
					"spring.data.valkey.sentinel.nodes:127.0.0.1:26379, 127.0.0.1:26380")
			.run((context) -> {
				LettuceConnectionFactory connectionFactory = context.getBean(LettuceConnectionFactory.class);
				assertThat(connectionFactory.getDatabase()).isOne();
				assertThat(connectionFactory.isValkeySentinelAware()).isTrue();
			});
	}

	@Test
	void testValkeyConfigurationWithSentinelAndAuthentication() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.username=user", "spring.data.valkey.password=password",
					"spring.data.valkey.sentinel.master:mymaster",
					"spring.data.valkey.sentinel.nodes:127.0.0.1:26379,  127.0.0.1:26380")
			.run(assertSentinelConfiguration("user", "password", (sentinelConfiguration) -> {
				assertThat(sentinelConfiguration.getSentinelPassword().isPresent()).isFalse();
				Set<ValkeyNode> sentinels = sentinelConfiguration.getSentinels();
				assertThat(sentinels.stream().map(Object::toString).collect(Collectors.toSet()))
					.contains("127.0.0.1:26379", "127.0.0.1:26380");
			}));
	}

	@Test
	void testValkeyConfigurationWithSentinelPasswordAndDataNodePassword() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.password=password", "spring.data.valkey.sentinel.password=secret",
					"spring.data.valkey.sentinel.master:mymaster",
					"spring.data.valkey.sentinel.nodes:127.0.0.1:26379,  127.0.0.1:26380")
			.run(assertSentinelConfiguration(null, "password", (sentinelConfiguration) -> {
				assertThat(sentinelConfiguration.getSentinelUsername()).isNull();
				assertThat(new String(sentinelConfiguration.getSentinelPassword().get())).isEqualTo("secret");
				Set<ValkeyNode> sentinels = sentinelConfiguration.getSentinels();
				assertThat(sentinels.stream().map(Object::toString).collect(Collectors.toSet()))
					.contains("127.0.0.1:26379", "127.0.0.1:26380");
			}));
	}

	@Test
	void testValkeyConfigurationWithSentinelAuthenticationAndDataNodeAuthentication() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.username=username", "spring.data.valkey.password=password",
					"spring.data.valkey.sentinel.username=sentinel", "spring.data.valkey.sentinel.password=secret",
					"spring.data.valkey.sentinel.master:mymaster",
					"spring.data.valkey.sentinel.nodes:127.0.0.1:26379,  127.0.0.1:26380")
			.run(assertSentinelConfiguration("username", "password", (sentinelConfiguration) -> {
				assertThat(sentinelConfiguration.getSentinelUsername()).isEqualTo("sentinel");
				assertThat(new String(sentinelConfiguration.getSentinelPassword().get())).isEqualTo("secret");
				Set<ValkeyNode> sentinels = sentinelConfiguration.getSentinels();
				assertThat(sentinels.stream().map(Object::toString).collect(Collectors.toSet()))
					.contains("127.0.0.1:26379", "127.0.0.1:26380");
			}));
	}

	private ContextConsumer<AssertableApplicationContext> assertSentinelConfiguration(String userName, String password,
			Consumer<ValkeySentinelConfiguration> sentinelConfiguration) {
		return (context) -> {
			LettuceConnectionFactory connectionFactory = context.getBean(LettuceConnectionFactory.class);
			assertThat(getUserName(connectionFactory)).isEqualTo(userName);
			assertThat(connectionFactory.getPassword()).isEqualTo(password);
			assertThat(connectionFactory.getSentinelConfiguration()).satisfies(sentinelConfiguration);
		};
	}

	@Test
	void testValkeySentinelUrlConfiguration() {
		this.contextRunner
			.withPropertyValues(
					"spring.data.valkey.url=valkey-sentinel://username:password@127.0.0.1:26379,127.0.0.1:26380/mymaster")
			.run((context) -> assertThatIllegalStateException()
				.isThrownBy(() -> context.getBean(LettuceConnectionFactory.class))
				.withRootCauseInstanceOf(ValkeyUrlSyntaxException.class)
				.havingRootCause()
				.withMessageContaining(
						"Invalid Valkey URL 'valkey-sentinel://username:password@127.0.0.1:26379,127.0.0.1:26380/mymaster'"));
	}

	@Test
	void testValkeyConfigurationWithCluster() {
		List<String> clusterNodes = Arrays.asList("127.0.0.1:27379", "127.0.0.1:27380", "[::1]:27381");
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes[0]:" + clusterNodes.get(0),
					"spring.data.valkey.cluster.nodes[1]:" + clusterNodes.get(1),
					"spring.data.valkey.cluster.nodes[2]:" + clusterNodes.get(2))
			.run((context) -> {
				ValkeyClusterConfiguration clusterConfiguration = context.getBean(LettuceConnectionFactory.class)
					.getClusterConfiguration();
				assertThat(clusterConfiguration.getClusterNodes()).hasSize(3);
				assertThat(clusterConfiguration.getClusterNodes()).containsExactlyInAnyOrder(
						new ValkeyNode("127.0.0.1", 27379), new ValkeyNode("127.0.0.1", 27380),
						new ValkeyNode("[::1]", 27381));
			});
	}

	@Test
	void testValkeyConfigurationWithClusterAndAuthentication() {
		List<String> clusterNodes = Arrays.asList("127.0.0.1:27379", "127.0.0.1:27380");
		this.contextRunner
			.withPropertyValues("spring.data.valkey.username=user", "spring.data.valkey.password=password",
					"spring.data.valkey.cluster.nodes[0]:" + clusterNodes.get(0),
					"spring.data.valkey.cluster.nodes[1]:" + clusterNodes.get(1))
			.run((context) -> {
				LettuceConnectionFactory connectionFactory = context.getBean(LettuceConnectionFactory.class);
				assertThat(getUserName(connectionFactory)).isEqualTo("user");
				assertThat(connectionFactory.getPassword()).isEqualTo("password");
			}

			);
	}

	@Test
	void testValkeyConfigurationCreateClientOptionsByDefault() {
		this.contextRunner.run(assertClientOptions(ClientOptions.class, (options) -> {
			assertThat(options.getTimeoutOptions().isApplyConnectionTimeout()).isTrue();
			assertThat(options.getTimeoutOptions().isTimeoutCommands()).isTrue();
		}));
	}

	@Test
	void testValkeyConfigurationWithClusterCreateClusterClientOptions() {
		this.contextRunner.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380")
			.run(assertClientOptions(ClusterClientOptions.class, (options) -> {
				assertThat(options.getTimeoutOptions().isApplyConnectionTimeout()).isTrue();
				assertThat(options.getTimeoutOptions().isTimeoutCommands()).isTrue();
			}));
	}

	@Test
	void testValkeyConfigurationWithClusterRefreshPeriod() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.lettuce.cluster.refresh.period=30s")
			.run(assertClientOptions(ClusterClientOptions.class,
					(options) -> assertThat(options.getTopologyRefreshOptions().getRefreshPeriod()).hasSeconds(30)));
	}

	@Test
	void testValkeyConfigurationWithClusterAdaptiveRefresh() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.lettuce.cluster.refresh.adaptive=true")
			.run(assertClientOptions(ClusterClientOptions.class,
					(options) -> assertThat(options.getTopologyRefreshOptions().getAdaptiveRefreshTriggers())
						.isEqualTo(EnumSet.allOf(RefreshTrigger.class))));
	}

	@Test
	void testValkeyConfigurationWithClusterRefreshPeriodHasNoEffectWithNonClusteredConfiguration() {
		this.contextRunner.withPropertyValues("spring.data.valkey.cluster.refresh.period=30s")
			.run(assertClientOptions(ClientOptions.class,
					(options) -> assertThat(options.getClass()).isEqualTo(ClientOptions.class)));
	}

	@Test
	void testValkeyConfigurationWithClusterDynamicRefreshSourcesEnabled() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.lettuce.cluster.refresh.dynamic-refresh-sources=true")
			.run(assertClientOptions(ClusterClientOptions.class,
					(options) -> assertThat(options.getTopologyRefreshOptions().useDynamicRefreshSources()).isTrue()));
	}

	@Test
	void testValkeyConfigurationWithClusterDynamicRefreshSourcesDisabled() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.lettuce.cluster.refresh.dynamic-refresh-sources=false")
			.run(assertClientOptions(ClusterClientOptions.class,
					(options) -> assertThat(options.getTopologyRefreshOptions().useDynamicRefreshSources()).isFalse()));
	}

	@Test
	void testValkeyConfigurationWithClusterDynamicSourcesUnspecifiedUsesDefault() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.lettuce.cluster.refresh.dynamic-sources=")
			.run(assertClientOptions(ClusterClientOptions.class,
					(options) -> assertThat(options.getTopologyRefreshOptions().useDynamicRefreshSources()).isTrue()));
	}

	@Test
	void usesSentinelFromCustomConnectionDetails() {
		this.contextRunner.withUserConfiguration(ConnectionDetailsSentinelConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(ValkeyConnectionDetails.class)
				.doesNotHaveBean(PropertiesValkeyConnectionDetails.class);
			LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
			assertThat(cf.isUseSsl()).isFalse();
			ValkeySentinelConfiguration configuration = cf.getSentinelConfiguration();
			assertThat(configuration).isNotNull();
			assertThat(configuration.getSentinelUsername()).isEqualTo("sentinel-1");
			assertThat(configuration.getSentinelPassword().get()).isEqualTo("secret-1".toCharArray());
			assertThat(configuration.getSentinels()).containsExactly(new ValkeyNode("node-1", 12345));
			assertThat(configuration.getUsername()).isEqualTo("user-1");
			assertThat(configuration.getPassword()).isEqualTo(ValkeyPassword.of("password-1"));
			assertThat(configuration.getDatabase()).isOne();
			assertThat(configuration.getMaster().getName()).isEqualTo("master.valkey.example.com");
		});
	}

	@Test
	void testValkeyConfigurationWithSslEnabled() {
		this.contextRunner.withPropertyValues("spring.data.valkey.ssl.enabled:true").run((context) -> {
			LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
			assertThat(cf.isUseSsl()).isTrue();
		});
	}

	@Test
	@WithPackageResources("test.jks")
	void testValkeyConfigurationWithSslBundle() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.ssl.bundle:test-bundle",
					"spring.ssl.bundle.jks.test-bundle.keystore.location:classpath:test.jks",
					"spring.ssl.bundle.jks.test-bundle.keystore.password:secret",
					"spring.ssl.bundle.jks.test-bundle.key.password:password")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.isUseSsl()).isTrue();
			});
	}

	@Test
	void testValkeyConfigurationWithSslDisabledAndBundle() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.ssl.enabled:false", "spring.data.valkey.ssl.bundle:test-bundle")
			.run((context) -> {
				LettuceConnectionFactory cf = context.getBean(LettuceConnectionFactory.class);
				assertThat(cf.isUseSsl()).isFalse();
			});
	}

	@Test
	void shouldUsePlatformThreadsByDefault() {
		this.contextRunner.run((context) -> {
			LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
			assertThat(factory).isNotNull();
		});
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_21)
	void shouldUseVirtualThreadsIfEnabled() {
		this.contextRunner.withPropertyValues("spring.threads.virtual.enabled=true").run((context) -> {
			LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
			assertThat(factory).isNotNull();
		});
	}

	private <T extends ClientOptions> ContextConsumer<AssertableApplicationContext> assertClientOptions(
			Class<T> expectedType, Consumer<T> options) {
		return (context) -> {
			LettuceClientConfiguration clientConfiguration = context.getBean(LettuceConnectionFactory.class)
				.getClientConfiguration();
			assertThat(clientConfiguration.getClientOptions()).isPresent();
			ClientOptions clientOptions = clientConfiguration.getClientOptions().get();
			assertThat(clientOptions.getClass()).isEqualTo(expectedType);
			options.accept(expectedType.cast(clientOptions));
		};
	}

	private LettucePoolingClientConfiguration getPoolingClientConfiguration(LettuceConnectionFactory factory) {
		return (LettucePoolingClientConfiguration) factory.getClientConfiguration();
	}

	private String getUserName(LettuceConnectionFactory factory) {
		return ReflectionTestUtils.invokeMethod(factory, "getValkeyUsername");
	}

	private RedisClusterNode createValkeyNode(String host) {
		RedisClusterNode node = new RedisClusterNode();
		node.setUri(RedisURI.Builder.redis(host).build());
		return node;
	}

	private static final class ValkeyNodes implements Nodes {

		private final List<RedisNodeDescription> descriptions;

		ValkeyNodes(RedisNodeDescription... descriptions) {
			this.descriptions = List.of(descriptions);
		}

		@Override
		public List<RedisNodeDescription> getNodes() {
			return this.descriptions;
		}

		@Override
		public Iterator<RedisNodeDescription> iterator() {
			return this.descriptions.iterator();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomConfiguration {

		@Bean
		LettuceClientConfigurationBuilderCustomizer customizer() {
			return LettuceClientConfigurationBuilder::useSsl;
		}

		@Bean
		LettuceClientOptionsBuilderCustomizer clientOptionsBuilderCustomizer() {
			return (builder) -> builder.autoReconnect(false);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ConnectionDetailsConfiguration {

		@Bean
		ValkeyConnectionDetails valkeyConnectionDetails() {
			return new ValkeyConnectionDetails() {

				@Override
				public Standalone getStandalone() {
					return new Standalone() {

						@Override
						public String getHost() {
							return "localhost";
						}

						@Override
						public int getPort() {
							return 6379;
						}

					};
				}

			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ConnectionDetailsSentinelConfiguration {

		@Bean
		ValkeyConnectionDetails valkeyConnectionDetails() {
			return new ValkeyConnectionDetails() {

				@Override
				public String getUsername() {
					return "user-1";
				}

				@Override
				public String getPassword() {
					return "password-1";
				}

				@Override
				public Sentinel getSentinel() {
					return new Sentinel() {

						@Override
						public int getDatabase() {
							return 1;
						}

						@Override
						public String getMaster() {
							return "master.valkey.example.com";
						}

						@Override
						public List<Node> getNodes() {
							return List.of(new Node("node-1", 12345));
						}

						@Override
						public String getUsername() {
							return "sentinel-1";
						}

						@Override
						public String getPassword() {
							return "secret-1";
						}

					};
				}

			};
		}

	}

}
