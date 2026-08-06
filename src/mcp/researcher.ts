import type { McpConnectorDefinition, McpPlatform } from './types';
import { getCatalogForPlatform } from './catalog';

const GITHUB_API = 'https://api.github.com';

interface GitHubRepo {
  full_name: string;
  description: string | null;
  html_url: string;
  stargazers_count: number;
  topics?: string[];
}

/** Search open-source cloud MCP connectors (SSE/HTTP) */
export async function searchOpenSourceMcp(
  query: string,
  platform: McpPlatform
): Promise<McpConnectorDefinition[]> {
  const catalog = getCatalogForPlatform(platform).filter((c) => c.hosting === 'cloud');
  const q = query.toLowerCase().trim();

  if (!q) return catalog.filter((c) => c.verified);

  const scored = catalog.map((c) => {
    const hay = `${c.name} ${c.description} ${c.category} ${c.id}`.toLowerCase();
    let score = 0;
    if (hay.includes(q)) score += 100;
    for (const word of q.split(/\s+/)) {
      if (word.length > 2 && hay.includes(word)) score += 30;
    }
    return { c, score };
  }).filter((x) => x.score > 0).sort((a, b) => b.score - a.score);

  const githubResults = await searchGitHubMcp(query);
  const combined = [...scored.map((x) => x.c), ...githubResults];

  const seen = new Set<string>();
  return combined.filter((c) => {
    if (seen.has(c.id)) return false;
    seen.add(c.id);
    return true;
  });
}

async function searchGitHubMcp(query: string): Promise<McpConnectorDefinition[]> {
  const q = encodeURIComponent(`${query} mcp server sse OR model-context-protocol`);
  try {
    const res = await fetch(`${GITHUB_API}/search/repositories?q=${q}&sort=stars&order=desc&per_page=15`);
    if (!res.ok) return [];
    const data = await res.json() as { items: GitHubRepo[] };
    return (data.items ?? []).map((repo) => {
      const name = repo.full_name.split('/')[1] ?? repo.full_name;
      return {
        id: `oss.mcp.${repo.full_name.replace('/', '.')}`,
        name: name.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
        description: repo.description ?? `MCP server from ${repo.full_name}`,
        category: 'Open Source',
        transport: 'sse' as const,
        hosting: 'cloud' as const,
        platforms: ['electron', 'web', 'mobile'] as McpPlatform[],
        url: `https://mcp.${name}.dev/sse`,
        verified: repo.stargazers_count > 100,
        docsUrl: repo.html_url,
      };
    });
  } catch {
    return [];
  }
}

/** Scan npm for @modelcontextprotocol packages → local stdio connectors */
export async function searchLocalMcpPackages(query: string): Promise<McpConnectorDefinition[]> {
  try {
    const text = query.trim() || '@modelcontextprotocol/server';
    const res = await fetch(`https://registry.npmjs.org/-/v1/search?text=${encodeURIComponent(text)}&size=20`);
    if (!res.ok) return [];
    const data = await res.json() as { objects: { package: { name: string; description: string; version: string } }[] };
    return data.objects
      .filter((o) => o.package.name.includes('mcp') || o.package.name.includes('modelcontextprotocol'))
      .map((o) => ({
        id: `oss.local.${o.package.name.replace('@', '').replace('/', '.')}`,
        name: o.package.name.replace('@modelcontextprotocol/server-', '').replace(/-/g, ' '),
        description: o.package.description || `Local MCP: ${o.package.name}`,
        category: 'Local',
        transport: 'stdio' as const,
        hosting: 'local' as const,
        platforms: ['electron'] as McpPlatform[],
        command: 'npx',
        args: ['-y', o.package.name],
        verified: true,
        docsUrl: `https://www.npmjs.com/package/${o.package.name}`,
      }));
  } catch {
    return [];
  }
}
