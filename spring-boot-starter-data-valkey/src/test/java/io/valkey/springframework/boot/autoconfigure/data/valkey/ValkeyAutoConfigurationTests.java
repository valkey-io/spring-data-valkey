/*
 * Copyright 2012-2025 the original author or authors.
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

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;

import io.valkey.springframework.boot.testsupport.classpath.resources.WithPackageResources;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterConfiguration;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import io.valkey.springframework.data.valkey.connection.ValkeyNode;
import io.valkey.springframework.data.valkey.connection.ValkeyPassword;
import io.valkey.springframework.data.valkey.connection.ValkeyStandaloneConfiguration;
import io.valkey.springframework.data.valkey.connection.jedis.JedisConnectionFactory;
import io.valkey.springframework.data.valkey.connection.lettuce.LettuceConnectionFactory;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideClientConfiguration;
import io.valkey.springframework.data.valkey.connection.valkeyglide.ValkeyGlideConnectionFactory;
import io.valkey.springframework.data.valkey.core.ValkeyOperations;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ValkeyAutoConfiguration}.
 *
 * @author Dave Syer
 * @author Christian Dupuis
 * @author Christoph Strobl
 * @author Eddú Meléndez
 * @author Marco Aust
 * @author Mark Paluch
 * @author Stephane Nicoll
 * @author Alen Turkovic
 * @author Scott Frederick
 * @author Weix Sun
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 */
class ValkeyAutoConfigurationTests {

	// TODO: Add sentinel tests when ValkeyGlideConnectionFactory supports sentinel

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValkeyAutoConfiguration.class, SslAutoConfiguration.class));

	@Test
	void testDefaultValkeyConfiguration() {
		this.contextRunner.run((context) -> {
			assertThat(context.getBean("valkeyTemplate")).isInstanceOf(ValkeyOperations.class);
			assertThat(context).hasSingleBean(StringValkeyTemplate.class);
			assertThat(context).hasSingleBean(ValkeyConnectionFactory.class);
			assertThat(context.getBean(ValkeyConnectionFactory.class)).isInstanceOf(ValkeyGlideConnectionFactory.class);
		});
	}

	@Test
	void testOverrideValkeyConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.database:1",
					"spring.data.valkey.valkeyglide.shutdown-timeout:500")
			.run((context) -> {
				ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("foo");
				assertThat(cf.getDatabase()).isOne();
				assertThat(getUserName(cf)).isNull();
				assertThat(cf.getPassword()).isNull();
				assertThat(cf.isUseSsl()).isFalse();
			});
	}

	@Test
	void testValkeyUrlConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.url:valkey://user:password@example:33")
			.run((context) -> {
				ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
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
				ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
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
			ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
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
				ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
				assertThat(cf.getHostName()).isEqualTo("example");
				assertThat(cf.getPort()).isEqualTo(33);
				assertThat(getUserName(cf)).isEqualTo("user");
				assertThat(cf.getPassword()).isEqualTo(":pass:word");
			});
	}

	@Test
	void testValkeyConfigurationWithCustomPoolSettings() {
		this.contextRunner.withPropertyValues("spring.data.valkey.valkeyglide.max-pool-size=16").run((context) -> {
			ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
			ValkeyGlideClientConfiguration config = cf.getClientConfiguration();
			assertThat(config.getMaxPoolSize()).isEqualTo(16);
		});
	}

	@Test
	void testValkeyConfigurationWithCustomBean() {
		this.contextRunner.withUserConfiguration(ValkeyStandaloneConfig.class).run((context) -> {
			ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
			assertThat(cf.getHostName()).isEqualTo("foo");
		});
	}

	// TODO: Uncomment when GLIDE supports client names
	// @Test
	// void testValkeyConfigurationWithClientName() {
	// 	this.contextRunner.withPropertyValues("spring.data.valkey.host:foo", "spring.data.valkey.client-name:spring-boot")
	// 		.run((context) -> {
	// 			ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
	// 			assertThat(cf.getHostName()).isEqualTo("foo");
	// 			assertThat(cf.getClientName()).isEqualTo("spring-boot");
	// 		});
	// }

	@Test
	void connectionFactoryWithJedisClientType() {
		this.contextRunner.withPropertyValues("spring.data.valkey.client-type:jedis").run((context) -> {
			assertThat(context).hasSingleBean(ValkeyConnectionFactory.class);
			assertThat(context.getBean(ValkeyConnectionFactory.class)).isInstanceOf(JedisConnectionFactory.class);
		});
	}

	@Test
	void connectionFactoryWithLettuceClientType() {
		this.contextRunner.withPropertyValues("spring.data.valkey.client-type:lettuce").run((context) -> {
			assertThat(context).hasSingleBean(ValkeyConnectionFactory.class);
			assertThat(context.getBean(ValkeyConnectionFactory.class)).isInstanceOf(LettuceConnectionFactory.class);
		});
	}

	@Test
	void connectionFactoryWithValkeyGlideClientType() {
		this.contextRunner.withPropertyValues("spring.data.valkey.client-type:valkeyglide").run((context) -> {
			assertThat(context).hasSingleBean(ValkeyConnectionFactory.class);
			assertThat(context.getBean(ValkeyConnectionFactory.class)).isInstanceOf(ValkeyGlideConnectionFactory.class);
		});
	}

	@Test
	void testValkeyConfigurationWithCluster() {
		List<String> clusterNodes = Arrays.asList("127.0.0.1:27379", "127.0.0.1:27380", "[::1]:27381");
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes[0]:" + clusterNodes.get(0),
					"spring.data.valkey.cluster.nodes[1]:" + clusterNodes.get(1),
					"spring.data.valkey.cluster.nodes[2]:" + clusterNodes.get(2))
			.run((context) -> {
				ValkeyClusterConfiguration clusterConfiguration = context.getBean(ValkeyGlideConnectionFactory.class)
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
				ValkeyGlideConnectionFactory connectionFactory = context.getBean(ValkeyGlideConnectionFactory.class);
				assertThat(getUserName(connectionFactory)).isEqualTo("user");
				assertThat(connectionFactory.getPassword()).isEqualTo("password");
			});
	}

	@Test
	void testValkeyConfigurationCreateClientOptionsByDefault() {
		this.contextRunner.run(assertClientOptions((config) -> {
			assertThat(config).isNotNull();
			assertThat(config.isUseSsl()).isFalse();
		}));
	}

	@Test
	void testValkeyConfigurationWithClusterCreateClusterClientOptions() {
		this.contextRunner.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380")
			.run(assertClientOptions((config) -> {
				assertThat(config).isNotNull();
			}));
	}

	@Test
	void testValkeyConfigurationWithClusterRefreshPeriod() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.valkeyglide.cluster.refresh.period=30s")
			.run((context) -> {
				ValkeyProperties properties = context.getBean(ValkeyProperties.class);
				assertThat(properties.getValkeyGlide().getCluster().getRefresh().getPeriod()).hasSeconds(30);
			});
	}

	@Test
	void testValkeyConfigurationWithClusterAdaptiveRefresh() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.valkeyglide.cluster.refresh.adaptive=true")
			.run((context) -> {
				ValkeyProperties properties = context.getBean(ValkeyProperties.class);
				assertThat(properties.getValkeyGlide().getCluster().getRefresh().isAdaptive()).isTrue();
			});
	}

	@Test
	void testValkeyConfigurationWithClusterRefreshPeriodHasNoEffectWithNonClusteredConfiguration() {
		this.contextRunner.withPropertyValues("spring.data.valkey.cluster.refresh.period=30s")
			.run(assertClientOptions((config) -> {
				assertThat(config).isNotNull();
			}));
	}

	@Test
	void testValkeyConfigurationWithClusterDynamicRefreshSourcesEnabled() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.valkeyglide.cluster.refresh.dynamic-refresh-sources=true")
			.run((context) -> {
				ValkeyProperties properties = context.getBean(ValkeyProperties.class);
				assertThat(properties.getValkeyGlide().getCluster().getRefresh().isDynamicRefreshSources()).isTrue();
			});
	}

	@Test
	void testValkeyConfigurationWithClusterDynamicRefreshSourcesDisabled() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.valkeyglide.cluster.refresh.dynamic-refresh-sources=false")
			.run((context) -> {
				ValkeyProperties properties = context.getBean(ValkeyProperties.class);
				assertThat(properties.getValkeyGlide().getCluster().getRefresh().isDynamicRefreshSources()).isFalse();
			});
	}

	@Test
	void testValkeyConfigurationWithClusterDynamicSourcesUnspecifiedUsesDefault() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.cluster.nodes=127.0.0.1:27379,127.0.0.1:27380",
					"spring.data.valkey.valkeyglide.cluster.refresh.dynamic-sources=")
			.run((context) -> {
				ValkeyProperties properties = context.getBean(ValkeyProperties.class);
				assertThat(properties.getValkeyGlide().getCluster().getRefresh().isDynamicRefreshSources()).isTrue();
			});
	}

	@Test
	void definesPropertiesBasedConnectionDetailsByDefault() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(PropertiesValkeyConnectionDetails.class));
	}

	@Test
	void usesStandaloneFromCustomConnectionDetails() {
		this.contextRunner.withUserConfiguration(ConnectionDetailsStandaloneConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(ValkeyConnectionDetails.class)
				.doesNotHaveBean(PropertiesValkeyConnectionDetails.class);
			ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
			assertThat(cf.isUseSsl()).isFalse();
			ValkeyStandaloneConfiguration configuration = cf.getStandaloneConfiguration();
			assertThat(configuration.getHostName()).isEqualTo("valkey.example.com");
			assertThat(configuration.getPort()).isEqualTo(16379);
			assertThat(configuration.getDatabase()).isOne();
			assertThat(configuration.getUsername()).isEqualTo("user-1");
			assertThat(configuration.getPassword()).isEqualTo(ValkeyPassword.of("password-1"));
		});
	}

	@Test
	void usesClusterFromCustomConnectionDetails() {
		this.contextRunner.withUserConfiguration(ConnectionDetailsClusterConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(ValkeyConnectionDetails.class)
				.doesNotHaveBean(PropertiesValkeyConnectionDetails.class);
			ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
			assertThat(cf.isUseSsl()).isFalse();
			ValkeyClusterConfiguration configuration = cf.getClusterConfiguration();
			assertThat(configuration).isNotNull();
			assertThat(configuration.getUsername()).isEqualTo("user-1");
			assertThat(configuration.getPassword().get()).isEqualTo("password-1".toCharArray());
			assertThat(configuration.getClusterNodes()).containsExactly(new ValkeyNode("node-1", 12345),
					new ValkeyNode("node-2", 23456));
		});
	}

	@Test
	void testValkeyConfigurationWithSslEnabled() {
		this.contextRunner.withPropertyValues("spring.data.valkey.ssl.enabled:true").run((context) -> {
			ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
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
				ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
				assertThat(cf.isUseSsl()).isTrue();
			});
	}

	@Test
	void testValkeyConfigurationWithSslDisabledBundle() {
		this.contextRunner
			.withPropertyValues("spring.data.valkey.ssl.enabled:false", "spring.data.valkey.ssl.bundle:test-bundle")
			.run((context) -> {
				ValkeyGlideConnectionFactory cf = context.getBean(ValkeyGlideConnectionFactory.class);
				assertThat(cf.isUseSsl()).isFalse();
			});
	}

	@Test
	void shouldUsePlatformThreadsByDefault() {
		this.contextRunner.run((context) -> {
			ValkeyGlideConnectionFactory factory = context.getBean(ValkeyGlideConnectionFactory.class);
			assertThat(factory).extracting("executor").isNull();
		});
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_21)
	void shouldUseVirtualThreadsIfEnabled() {
		this.contextRunner.withPropertyValues("spring.threads.virtual.enabled=true").run((context) -> {
			ValkeyGlideConnectionFactory factory = context.getBean(ValkeyGlideConnectionFactory.class);
			assertThat(factory).extracting("executor").isNotNull();
		});
	}

	private ContextConsumer<AssertableApplicationContext> assertClientOptions(
			Consumer<ValkeyGlideClientConfiguration> configConsumer) {
		return (context) -> {
			ValkeyGlideConnectionFactory factory = context.getBean(ValkeyGlideConnectionFactory.class);
			ValkeyGlideClientConfiguration config = factory.getClientConfiguration();
			configConsumer.accept(config);
		};
	}

	private String getUserName(ValkeyGlideConnectionFactory factory) {
		return ReflectionTestUtils.invokeMethod(factory, "getValkeyUsername");
	}

	@Configuration(proxyBeanMethods = false)
	static class ValkeyStandaloneConfig {

		@Bean
		ValkeyStandaloneConfiguration standaloneConfiguration() {
			ValkeyStandaloneConfiguration config = new ValkeyStandaloneConfiguration();
			config.setHostName("foo");
			return config;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ConnectionDetailsStandaloneConfiguration {

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
				public Standalone getStandalone() {
					return new Standalone() {

						@Override
						public int getDatabase() {
							return 1;
						}

						@Override
						public String getHost() {
							return "valkey.example.com";
						}

						@Override
						public int getPort() {
							return 16379;
						}

					};
				}

			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ConnectionDetailsClusterConfiguration {

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
				public Cluster getCluster() {
					return new Cluster() {

						@Override
						public List<Node> getNodes() {
							return List.of(new Node("node-1", 12345), new Node("node-2", 23456));
						}

					};
				}

			};
		}

	}

}
