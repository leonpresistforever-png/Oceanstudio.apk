import type { MarketplaceSearchResult, OceanPluginManifest, PluginPlatform } from '../types';

const GITHUB_API = 'https://api.github.com';

interface GitHubRepo {
  full_name: string;
  description: string | null;
  html_url: string;
  stargazers_count: number;
  topics?: string[];
}

/** Plugin ecosystems — NOT raw MCP servers (those live in MCP Connectors page) */
const KNOWN_PLUGIN_REPOS = [
  'cursor/plugins',
  'anthropics/claude-code',
  'openai/codex',
  'microsoft/vscode',
  'wong2/awesome-mcp-servers',
];

function repoToPlugin(repo: GitHubRepo, platform: PluginPlatform): OceanPluginManifest | null {
  const name = repo.full_name.split('/')[1] ?? repo.full_name;
  const lower = `${repo.description ?? ''} ${name} ${(repo.topics ?? []).join(' ')}`.toLowerCase();
  const isPlugin = lower.includes('plugin') || lower.includes('extension') || lower.includes('tool')
    || lower.includes('agent') || lower.includes('skill') || lower.includes('connector');
  if (!isPlugin && repo.stargazers_count < 30) return null;

  const id = `oss.plugin.${repo.full_name.replace('/', '.')}`;
  return {
    id,
    name: name.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
    version: '1.0.0',
    description: repo.description ?? `Open source plugin from ${repo.full_name}`,
    author: repo.full_name.split('/')[0],
    homepage: repo.html_url,
    repository: repo.html_url,
    platforms: platform === 'mobile' ? ['mobile'] : platform === 'web' ? ['web'] : ['electron', 'web'],
    type: 'tool',
    tags: ['opensource', 'plugin', ...(repo.topics ?? []).slice(0, 5)],
    source: 'opensource',
    verified: repo.stargazers_count > 200,
    downloads: repo.stargazers_count,
    rating: Math.min(5, 3 + repo.stargazers_count / 2000),
    electron: platform !== 'web' ? { entry: 'index', python: 'plugin.py' } : undefined,
    web: platform !== 'electron' ? { entry: repo.html_url, permissions: ['storage'] } : undefined,
    mobile: platform === 'mobile' ? { pkg: name, bootstrap: false } : undefined,
  };
}

async function searchGitHubPlugins(query: string): Promise<GitHubRepo[]> {
  const q = encodeURIComponent(`${query} plugin OR extension OR agent-tool in:name,description,readme`);
  try {
    const res = await fetch(`${GITHUB_API}/search/repositories?q=${q}&sort=stars&order=desc&per_page=20`);
    if (!res.ok) return [];
    const data = await res.json() as { items: GitHubRepo[] };
    return data.items ?? [];
  } catch {
    return [];
  }
}

async function fetchKnownPluginRepos(): Promise<GitHubRepo[]> {
  const results: GitHubRepo[] = [];
  for (const repo of KNOWN_PLUGIN_REPOS) {
    try {
      const res = await fetch(`${GITHUB_API}/repos/${repo}`);
      if (res.ok) results.push(await res.json() as GitHubRepo);
    } catch { /* skip */ }
  }
  return results;
}

export async function searchOpenSourcePlugins(
  query: string,
  platform: PluginPlatform
): Promise<MarketplaceSearchResult[]> {
  const repos = query.trim()
    ? await searchGitHubPlugins(query)
    : await fetchKnownPluginRepos();

  const plugins: MarketplaceSearchResult[] = [];
  const q = query.toLowerCase();

  for (const repo of repos) {
    const manifest = repoToPlugin(repo, platform);
    if (!manifest) continue;

    let score = manifest.downloads ?? 0;
    if (q) {
      const hay = `${manifest.name} ${manifest.description} ${manifest.tags?.join(' ')}`.toLowerCase();
      if (hay.includes(q)) score += 1000;
      for (const word of q.split(/\s+/)) {
        if (word.length > 2 && hay.includes(word)) score += 200;
      }
    }

    plugins.push({ manifest, matchScore: score, sourceUrl: repo.html_url });
  }

  return plugins.sort((a, b) => b.matchScore - a.matchScore);
}

/** Plugin researcher — finds plugins with compatibility layer, NOT MCP servers */
export async function runPluginResearcher(
  platform: PluginPlatform,
  onProgress?: (msg: string) => void
): Promise<OceanPluginManifest[]> {
  onProgress?.('Scanning GitHub for agent plugins and extensions...');
  const pluginResults = await searchOpenSourcePlugins('agent plugin tool', platform);

  onProgress?.('Scanning npm for Ocean-compatible plugin packages...');
  let npmPlugins: OceanPluginManifest[] = [];
  try {
    const res = await fetch('https://registry.npmjs.org/-/v1/search?text=ocean+plugin+OR+cursor+plugin+OR+agent+tool&size=12');
    if (res.ok) {
      const data = await res.json() as { objects: { package: { name: string; description: string; version: string } }[] };
      npmPlugins = data.objects.map((o) => ({
        id: `oss.npm.${o.package.name.replace('@', '').replace('/', '.')}`,
        name: o.package.name.split('/').pop()?.replace(/-/g, ' ') ?? o.package.name,
        version: o.package.version,
        description: o.package.description || `Plugin: ${o.package.name}`,
        platforms: platform === 'mobile' ? ['mobile' as const] : platform === 'web' ? ['web' as const] : ['electron' as const],
        type: 'tool' as const,
        tags: ['opensource', 'npm', 'plugin'],
        source: 'opensource' as const,
        verified: true,
        homepage: `https://www.npmjs.com/package/${o.package.name}`,
      }));
    }
  } catch { /* offline */ }

  onProgress?.(`Found ${pluginResults.length + npmPlugins.length} compatible plugins`);
  const combined = [...pluginResults.map((r) => r.manifest), ...npmPlugins];

  const seen = new Set<string>();
  return combined.filter((p) => {
    if (seen.has(p.id)) return false;
    seen.add(p.id);
    return true;
  });
}
