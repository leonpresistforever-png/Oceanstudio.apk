export type IntegrationCategory =
  | 'devops'
  | 'communication'
  | 'ide'
  | 'cloud'
  | 'ai'
  | 'database'
  | 'design'
  | 'monitoring'
  | 'productivity';

export type IntegrationAuthType = 'oauth' | 'api_key' | 'mcp' | 'webhook' | 'pat' | 'none';

export type IntegrationStatus = 'available' | 'beta' | 'coming_soon' | 'connected';

export interface IntegrationDefinition {
  id: string;
  name: string;
  description: string;
  icon: string;
  category: IntegrationCategory;
  authType: IntegrationAuthType;
  status: IntegrationStatus;
  brandColor: string;
  tags: string[];
  /** MCP connector id if applicable */
  mcpId?: string;
  /** Provider catalog id if applicable */
  providerId?: string;
  docsUrl?: string;
  envKeys?: string[];
  features: string[];
}

export const INTEGRATION_CATEGORIES: { id: IntegrationCategory; label: string; icon: string }[] = [
  { id: 'devops', label: 'DevOps & Git', icon: '🔧' },
  { id: 'communication', label: 'Communication', icon: '💬' },
  { id: 'ide', label: 'IDE & Coding', icon: '💻' },
  { id: 'cloud', label: 'Cloud & Infra', icon: '☁️' },
  { id: 'ai', label: 'AI Providers', icon: '🤖' },
  { id: 'database', label: 'Database', icon: '🗄️' },
  { id: 'design', label: 'Design', icon: '🎨' },
  { id: 'monitoring', label: 'Monitoring', icon: '📊' },
  { id: 'productivity', label: 'Productivity', icon: '📋' },
];

export const INTEGRATIONS_CATALOG: IntegrationDefinition[] = [
  // DevOps & Git
  { id: 'github', name: 'GitHub', description: 'Repos, PRs, issues, Actions', icon: '🐙', category: 'devops', authType: 'oauth', status: 'connected', brandColor: '#24292f', tags: ['git', 'pr'], features: ['import/export', 'commit/push', 'PR workflow'], docsUrl: 'https://github.com' },
  { id: 'gitlab', name: 'GitLab', description: 'GitLab repos, CI/CD, merge requests', icon: '🦊', category: 'devops', authType: 'pat', status: 'available', brandColor: '#fc6d26', tags: ['git', 'ci'], mcpId: 'mcp.gitlab', envKeys: ['GITLAB_TOKEN'], features: ['MR workflow', 'CI pipelines', 'Container registry'] },
  { id: 'bitbucket', name: 'Bitbucket', description: 'Atlassian Git hosting', icon: '🔵', category: 'devops', authType: 'oauth', status: 'available', brandColor: '#0052cc', tags: ['git'], features: ['Pipelines', 'PRs'] },
  { id: 'azure-devops', name: 'Azure DevOps', description: 'Repos, boards, pipelines', icon: '🔷', category: 'devops', authType: 'pat', status: 'available', brandColor: '#0078d4', tags: ['git', 'ci'], envKeys: ['AZURE_DEVOPS_PAT'], features: ['Boards', 'Pipelines'] },
  { id: 'jenkins', name: 'Jenkins', description: 'CI/CD automation server', icon: '👷', category: 'devops', authType: 'api_key', status: 'beta', brandColor: '#d33833', tags: ['ci'], features: ['Build triggers', 'Job status'] },
  { id: 'circleci', name: 'CircleCI', description: 'Cloud CI/CD platform', icon: '⭕', category: 'devops', authType: 'api_key', status: 'available', brandColor: '#343434', tags: ['ci'], envKeys: ['CIRCLECI_TOKEN'], features: ['Pipelines', 'Orbs'] },

  // Communication
  { id: 'slack', name: 'Slack', description: 'Team messaging, notifications, bot commands', icon: '💬', category: 'communication', authType: 'oauth', status: 'available', brandColor: '#4a154b', tags: ['chat', 'notify'], mcpId: 'mcp.slack', features: ['Agent notifications', 'Channel posts', 'Slash commands'] },
  { id: 'discord', name: 'Discord', description: 'Community and team chat', icon: '🎮', category: 'communication', authType: 'webhook', status: 'available', brandColor: '#5865f2', tags: ['chat'], features: ['Webhooks', 'Bot integration'] },
  { id: 'teams', name: 'Microsoft Teams', description: 'Enterprise collaboration', icon: '👥', category: 'communication', authType: 'oauth', status: 'beta', brandColor: '#6264a7', tags: ['chat'], features: ['Notifications', 'Tabs'] },
  { id: 'linear', name: 'Linear', description: 'Issue tracking for software teams', icon: '📐', category: 'communication', authType: 'api_key', status: 'available', brandColor: '#5e6ad2', tags: ['issues'], mcpId: 'mcp.linear', envKeys: ['LINEAR_API_KEY'], features: ['Issues', 'Cycles', 'Projects'] },
  { id: 'jira', name: 'Jira', description: 'Atlassian project management', icon: '📋', category: 'communication', authType: 'oauth', status: 'available', brandColor: '#0052cc', tags: ['issues'], features: ['Tickets', 'Sprints'] },
  { id: 'notion', name: 'Notion', description: 'Docs, wikis, databases', icon: '📝', category: 'productivity', authType: 'oauth', status: 'available', brandColor: '#000', tags: ['docs'], mcpId: 'mcp.notion', features: ['Pages', 'Databases'] },

  // IDE & Coding
  { id: 'cursor', name: 'Cursor', description: 'Cursor IDE agent bridge, composer, @codebase', icon: '▶', category: 'ide', authType: 'api_key', status: 'connected', brandColor: '#6366f1', tags: ['ide', 'agent'], providerId: 'cursor.cloud', envKeys: ['CURSOR_API_KEY'], features: ['Agent mode', 'Composer', 'MCP bridge', '/cursor/v1 API'] },
  { id: 'windsurf', name: 'Windsurf', description: 'Codeium Windsurf IDE cascade flows', icon: '🏄', category: 'ide', authType: 'api_key', status: 'available', brandColor: '#09b6a2', tags: ['ide'], envKeys: ['CODEIUM_API_KEY'], features: ['Cascade', 'Flows', 'Supercomplete'] },
  { id: 'vscode', name: 'VS Code', description: 'Extensions marketplace, Open VSX', icon: '📘', category: 'ide', authType: 'none', status: 'connected', brandColor: '#007acc', tags: ['ide', 'extensions'], features: ['54+ extensions', 'Open VSX'] },
  { id: 'antigravity-ide', name: 'Antigravity IDE', description: 'Google Antigravity multi-model IDE', icon: '🌊', category: 'ide', authType: 'oauth', status: 'connected', brandColor: '#8b5cf6', tags: ['ide'], providerId: 'google.antigravity', features: ['Multi-model', 'Thinking mode', 'MCP'] },
  { id: 'antigravity-cli', name: 'Antigravity CLI', description: 'Terminal agent with Antigravity models', icon: '⌨️', category: 'ide', authType: 'oauth', status: 'connected', brandColor: '#4285f4', tags: ['cli'], providerId: 'google.antigravity', features: ['Terminal agent', 'Flash/Opus routing'] },
  { id: 'codex', name: 'OpenAI Codex', description: 'Codex agent for code generation', icon: '🤖', category: 'ide', authType: 'api_key', status: 'connected', brandColor: '#10a37f', tags: ['agent'], providerId: 'openai.codex', envKeys: ['OPENAI_API_KEY'], features: ['Code generation', 'Terminal'] },
  { id: 'claude-code', name: 'Claude Code', description: 'Anthropic Claude Code CLI agent', icon: '🧠', category: 'ide', authType: 'api_key', status: 'connected', brandColor: '#d97706', tags: ['agent'], providerId: 'anthropic.claude-code', envKeys: ['ANTHROPIC_API_KEY'], features: ['Review', 'Tests', 'Docs'] },

  // Cloud
  { id: 'gcp', name: 'Google Cloud', description: 'Cloud Shell, GCP APIs, OAuth', icon: '☁️', category: 'cloud', authType: 'oauth', status: 'connected', brandColor: '#4285f4', tags: ['cloud'], features: ['Cloud Shell terminal', 'Sandbox preview'] },
  { id: 'aws', name: 'AWS', description: 'Amazon Web Services', icon: '🟠', category: 'cloud', authType: 'api_key', status: 'available', brandColor: '#ff9900', tags: ['cloud'], envKeys: ['AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY'], features: ['S3', 'Lambda', 'EC2'] },
  { id: 'azure', name: 'Azure', description: 'Microsoft Azure cloud', icon: '🔷', category: 'cloud', authType: 'oauth', status: 'available', brandColor: '#0078d4', tags: ['cloud'], features: ['Functions', 'Storage'] },
  { id: 'vercel', name: 'Vercel', description: 'Frontend deployment platform', icon: '▲', category: 'cloud', authType: 'oauth', status: 'available', brandColor: '#000', tags: ['deploy'], mcpId: 'mcp.vercel', features: ['Deploy', 'Preview URLs'] },
  { id: 'netlify', name: 'Netlify', description: 'Jamstack deployment', icon: '🌐', category: 'cloud', authType: 'oauth', status: 'available', brandColor: '#00c7b7', tags: ['deploy'], features: ['Deploy', 'Forms'] },
  { id: 'firebase', name: 'Firebase', description: 'Google Firebase backend', icon: '🔥', category: 'cloud', authType: 'oauth', status: 'connected', brandColor: '#ffca28', tags: ['backend'], mcpId: 'mcp.firebase', features: ['Auth', 'Firestore', 'Hosting'] },
  { id: 'supabase', name: 'Supabase', description: 'Open-source Firebase alternative', icon: '⚡', category: 'cloud', authType: 'api_key', status: 'available', brandColor: '#3ecf8e', tags: ['backend'], mcpId: 'mcp.supabase', envKeys: ['SUPABASE_URL', 'SUPABASE_KEY'], features: ['Postgres', 'Auth', 'Storage'] },
  { id: 'cloudflare', name: 'Cloudflare', description: 'CDN, Workers, R2', icon: '🌩️', category: 'cloud', authType: 'api_key', status: 'available', brandColor: '#f38020', tags: ['cdn'], mcpId: 'mcp.cloudflare', envKeys: ['CLOUDFLARE_API_TOKEN'], features: ['Workers', 'R2', 'DNS'] },
  { id: 'docker', name: 'Docker', description: 'Container platform', icon: '🐳', category: 'cloud', authType: 'api_key', status: 'available', brandColor: '#2496ed', tags: ['containers'], features: ['Images', 'Compose'] },

  // AI
  { id: 'openai', name: 'OpenAI', description: 'GPT, Codex, o-series models', icon: '🟢', category: 'ai', authType: 'api_key', status: 'connected', brandColor: '#10a37f', tags: ['llm'], providerId: 'openai.codex', envKeys: ['OPENAI_API_KEY'], features: ['GPT-5', 'Codex', 'o3'] },
  { id: 'anthropic', name: 'Anthropic', description: 'Claude models', icon: '🟤', category: 'ai', authType: 'api_key', status: 'connected', brandColor: '#d97706', tags: ['llm'], providerId: 'anthropic.claude-code', envKeys: ['ANTHROPIC_API_KEY'], features: ['Opus', 'Sonnet'] },
  { id: 'google-ai', name: 'Google AI', description: 'Gemini models', icon: '🔵', category: 'ai', authType: 'api_key', status: 'connected', brandColor: '#4285f4', tags: ['llm'], providerId: 'google.gemini', envKeys: ['GOOGLE_API_KEY'], features: ['Gemini Pro', 'Flash'] },
  { id: 'huggingface', name: 'Hugging Face', description: 'Open models, inference API', icon: '🤗', category: 'ai', authType: 'api_key', status: 'connected', brandColor: '#ffd21e', tags: ['llm', 'fallback'], envKeys: ['HF_TOKEN'], features: ['Free tier fallback', '8+ models'] },
  { id: 'nvidia-nim', name: 'NVIDIA NIM', description: 'NVIDIA inference microservices', icon: '🟢', category: 'ai', authType: 'api_key', status: 'connected', brandColor: '#76b900', tags: ['llm', 'fallback'], envKeys: ['NVIDIA_API_KEY'], features: ['12+ NIM models', 'Fallback routing'] },
  { id: 'firecrawl', name: 'Firecrawl', description: 'Web scraping for agents', icon: '🔥', category: 'ai', authType: 'api_key', status: 'connected', brandColor: '#ff6b35', tags: ['scrape'], envKeys: ['FIRECRAWL_API_KEY'], features: ['Scrape', 'Crawl', 'Search'] },

  // Database
  { id: 'postgres', name: 'PostgreSQL', description: 'Relational database', icon: '🐘', category: 'database', authType: 'api_key', status: 'available', brandColor: '#336791', tags: ['sql'], features: ['Agent memory backend'] },
  { id: 'mongodb', name: 'MongoDB', description: 'Document database', icon: '🍃', category: 'database', authType: 'api_key', status: 'available', brandColor: '#47a248', tags: ['nosql'], features: ['Atlas integration'] },
  { id: 'redis', name: 'Redis', description: 'In-memory data store', icon: '🔴', category: 'database', authType: 'api_key', status: 'available', brandColor: '#dc382d', tags: ['cache'], features: ['Session cache'] },

  // Design
  { id: 'figma', name: 'Figma', description: 'Design to code, MCP bridge', icon: '🎨', category: 'design', authType: 'oauth', status: 'available', brandColor: '#f24e1e', tags: ['design'], mcpId: 'mcp.figma', features: ['get_design_context', 'Code Connect'] },

  // Monitoring
  { id: 'sentry', name: 'Sentry', description: 'Error tracking and performance', icon: '🔍', category: 'monitoring', authType: 'api_key', status: 'available', brandColor: '#362d59', tags: ['errors'], envKeys: ['SENTRY_DSN'], features: ['Error tracking'] },
  { id: 'datadog', name: 'Datadog', description: 'Observability platform', icon: '🐕', category: 'monitoring', authType: 'api_key', status: 'beta', brandColor: '#632ca6', tags: ['metrics'], features: ['APM', 'Logs'] },
  { id: 'coralogix', name: 'Coralogix', description: 'Log analytics and observability', icon: '📊', category: 'monitoring', authType: 'api_key', status: 'available', brandColor: '#3dcc8c', tags: ['logs'], mcpId: 'mcp.coralogix', features: ['Logs', 'Metrics', 'Traces'] },

  // Productivity
  { id: 'google-workspace', name: 'Google Workspace', description: 'Drive, Docs, Calendar', icon: '📁', category: 'productivity', authType: 'oauth', status: 'available', brandColor: '#4285f4', tags: ['docs'], features: ['Drive', 'Calendar'] },
  { id: 'stripe', name: 'Stripe', description: 'Payments API', icon: '💳', category: 'productivity', authType: 'api_key', status: 'available', brandColor: '#635bff', tags: ['payments'], envKeys: ['STRIPE_SECRET_KEY'], features: ['Checkout', 'Subscriptions'] },
];

export function getIntegrationsByCategory(category: IntegrationCategory): IntegrationDefinition[] {
  return INTEGRATIONS_CATALOG.filter((i) => i.category === category);
}

export function searchIntegrations(query: string): IntegrationDefinition[] {
  const q = query.toLowerCase();
  if (!q) return INTEGRATIONS_CATALOG;
  return INTEGRATIONS_CATALOG.filter(
    (i) => i.name.toLowerCase().includes(q) || i.tags.some((t) => t.includes(q)) || i.description.toLowerCase().includes(q)
  );
}
