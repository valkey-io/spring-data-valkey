// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://spring.valkey.io',
	integrations: [
		starlight({
			title: 'Spring Data Redis',
			logo: {
				light: './src/assets/valkey-logo-with-name-light.svg',
				dark: './src/assets/valkey-logo-with-name-dark.svg',
				replacesTitle: true,
			},
			customCss: ['./src/styles/custom.css', './src/styles/code-wrap.css'],
			favicon: '/favicon-32x32.png',
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/valkey-io/spring-data-valkey' }
			],
			sidebar: [
				{
					label: 'Overview',
					items: [
						{ label: 'Spring Data Redis', slug: 'overview' },
						{ label: 'Upgrading Spring Data', slug: 'commons/upgrade' },
						{ label: 'Migration Guides', slug: 'commons/migration' },
					]
				},
				{
					label: 'Redis',
					items: [
						{ label: 'Redis Overview', slug: 'redis' },
						{ label: 'Getting Started', slug: 'redis/getting-started' },
						{ label: 'Drivers', slug: 'redis/drivers' },
						{ label: 'Connection Modes', slug: 'redis/connection-modes' },
						{ label: 'RedisTemplate', slug: 'redis/template' },
						{ label: 'Redis Cache', slug: 'redis/redis-cache' },
						{ label: 'Cluster', slug: 'redis/cluster' },
						{ label: 'Hash Mapping', slug: 'redis/hash-mappers' },
						{ label: 'Pub/Sub Messaging', slug: 'redis/pubsub' },
						{ label: 'Redis Streams', slug: 'redis/redis-streams' },
						{ label: 'Scripting', slug: 'redis/scripting' },
						{ label: 'Redis Transactions', slug: 'redis/transactions' },
						{ label: 'Pipelining', slug: 'redis/pipelining' },
						{ label: 'Support Classes', slug: 'redis/support-classes' },
					]
				},
				{
					label: 'Repositories',
					items: [
						{ label: 'Redis Repositories Overview', slug: 'repositories' },
						{ label: 'Core concepts', slug: 'repositories/core-concepts' },
						{ label: 'Defining Repository Interfaces', slug: 'repositories/definition' },
						{ label: 'Creating Repository Instances', slug: 'repositories/create-instances' },
						{ label: 'Usage', slug: 'redis/redis-repositories/usage' },
						{ label: 'Object Mapping Fundamentals', slug: 'repositories/object-mapping' },
						{ label: 'Object-to-Hash Mapping', slug: 'redis/redis-repositories/mapping' },
						{ label: 'Keyspaces', slug: 'redis/redis-repositories/keyspaces' },
						{ label: 'Secondary Indexes', slug: 'redis/redis-repositories/indexes' },
						{ label: 'Time To Live', slug: 'redis/redis-repositories/expirations' },
						{ label: 'Redis-specific Query Methods', slug: 'redis/redis-repositories/queries' },
						{ label: 'Query by Example', slug: 'redis/redis-repositories/query-by-example' },
						{ label: 'Redis Repositories Running on a Cluster', slug: 'redis/redis-repositories/cluster' },
						{ label: 'Redis Repositories Anatomy', slug: 'redis/redis-repositories/anatomy' },
						{ label: 'Projections', slug: 'repositories/projections' },
						{ label: 'Custom Repository Implementations', slug: 'repositories/custom-implementations' },
						{ label: 'Publishing Events from Aggregate Roots', slug: 'repositories/core-domain-events' },
						{ label: 'Null Handling of Repository Methods', slug: 'repositories/null-handling' },
						{ label: 'CDI Integration', slug: 'redis/redis-repositories/cdi-integration' },
						{ label: 'Repository query keywords', slug: 'repositories/query-keywords-reference' },
						{ label: 'Repository query return types', slug: 'repositories/query-return-types-reference' },
					]
				},
				{ label: 'Observability', slug: 'observability' },
				{ label: 'Appendix', slug: 'appendix' },
				{
					label: 'External Links',
					items: [
						{ label: 'Javadoc', link: '/api/java/index.html' },
						{ label: 'Wiki', link: 'https://github.com/spring-projects/spring-data-commons/wiki' },
					]
				},
			],
		}),
	],
});
