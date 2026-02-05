// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import rehypeExternalLinks from 'rehype-external-links';

// https://astro.build/config
export default defineConfig({
	// TODO: Once CNAME record is added, update site/base and rename docs/public/CNAME.example
	//site: 'https://spring.valkey.io',
	site: 'https://valkey.io',
	base: '/spring-data-valkey',
	markdown: {
		rehypePlugins: [
			[rehypeExternalLinks, { target: '_blank', rel: ['noopener', 'noreferrer'] }]
		]
	},
	integrations: [
		starlight({
			title: 'Spring Data Valkey',
			logo: {
				light: './src/assets/spring-data-valkey-logo-with-name-light.svg',
				dark: './src/assets/spring-data-valkey-logo-with-name-dark.svg',
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
						{ label: 'Spring Data Valkey', slug: 'overview' },
						{ label: 'Spring Boot', slug: 'commons/spring-boot' },
						{ label: 'Migrating Spring Data', slug: 'commons/migration' },
					]
				},
				{
					label: 'Valkey',
					items: [
						{ label: 'Valkey Overview', slug: 'valkey' },
						{ label: 'Getting Started', slug: 'valkey/getting-started' },
						{ label: 'Drivers', slug: 'valkey/drivers' },
						{ label: 'Connection Modes', slug: 'valkey/connection-modes' },
						{ label: 'ValkeyTemplate', slug: 'valkey/template' },
						{ label: 'Valkey Cache', slug: 'valkey/valkey-cache' },
						{ label: 'Cluster', slug: 'valkey/cluster' },
						{ label: 'Hash Mapping', slug: 'valkey/hash-mappers' },
						{ label: 'Pub/Sub Messaging', slug: 'valkey/pubsub' },
						{ label: 'Valkey Streams', slug: 'valkey/valkey-streams' },
						{ label: 'Scripting', slug: 'valkey/scripting' },
						{ label: 'Valkey Transactions', slug: 'valkey/transactions' },
						{ label: 'Pipelining', slug: 'valkey/pipelining' },
						{ label: 'Support Classes', slug: 'valkey/support-classes' },
					]
				},
				{
					label: 'Valkey Repositories',
					items: [
						{ label: 'Valkey Repositories Overview', slug: 'repositories' },
						{ label: 'Core concepts', slug: 'repositories/core-concepts' },
						{ label: 'Defining Repository Interfaces', slug: 'repositories/definition' },
						{ label: 'Creating Repository Instances', slug: 'repositories/create-instances' },
						{ label: 'Usage', slug: 'valkey/valkey-repositories/usage' },
						{ label: 'Object Mapping Fundamentals', slug: 'repositories/object-mapping' },
						{ label: 'Object-to-Hash Mapping', slug: 'valkey/valkey-repositories/mapping' },
						{ label: 'Keyspaces', slug: 'valkey/valkey-repositories/keyspaces' },
						{ label: 'Secondary Indexes', slug: 'valkey/valkey-repositories/indexes' },
						{ label: 'Time To Live', slug: 'valkey/valkey-repositories/expirations' },
						{ label: 'Valkey-specific Query Methods', slug: 'valkey/valkey-repositories/queries' },
						{ label: 'Query by Example', slug: 'valkey/valkey-repositories/query-by-example' },
						{ label: 'Valkey Repositories Running on a Cluster', slug: 'valkey/valkey-repositories/cluster' },
						{ label: 'Valkey Repositories Anatomy', slug: 'valkey/valkey-repositories/anatomy' },
						{ label: 'Projections', slug: 'repositories/projections' },
						{ label: 'Custom Repository Implementations', slug: 'repositories/custom-implementations' },
						{ label: 'Publishing Events from Aggregate Roots', slug: 'repositories/core-domain-events' },
						{ label: 'Null Handling of Repository Methods', slug: 'repositories/null-handling' },
						{ label: 'CDI Integration', slug: 'valkey/valkey-repositories/cdi-integration' },
						{ label: 'Repository query keywords', slug: 'repositories/query-keywords-reference' },
						{ label: 'Repository query return types', slug: 'repositories/query-return-types-reference' },
					]
				},
				{ label: 'Observability', slug: 'observability' },
				{ label: 'Appendix', slug: 'appendix' },
				{ label: 'Valkey Project ↗', link: 'https://valkey.io/', attrs: { target: '_blank' } },
				{ label: 'Javadoc ↗', link: 'https://spring.valkey.io/spring-data-valkey/api/java/index.html', attrs: { target: '_blank' } },
				{ label: 'Spring Boot Javadoc ↗', link: 'https://spring.valkey.io/spring-boot-starter-data-valkey/api/java/index.html', attrs: { target: '_blank' } },
			],
		}),
	],
});
