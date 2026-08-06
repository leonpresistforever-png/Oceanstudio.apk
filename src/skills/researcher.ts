import type { SkillDefinition } from './types';
import { BUILTIN_SKILLS } from './builtinCatalog';
import { CURSOR_WORKFLOW_SKILLS } from './cursorWorkflowSkills';

const ALL_BUILTIN_SKILLS = [...BUILTIN_SKILLS, ...CURSOR_WORKFLOW_SKILLS];
import { buildSearchIndex, filterSearchIndex, mergeUniqueById, type SearchEntry } from '../lib/catalogSearch';

const GITHUB_API = 'https://api.github.com';

let seedCache: SkillDefinition[] | null = null;
let seedLoadPromise: Promise<SkillDefinition[]> | null = null;
let searchIndex: SearchEntry<SkillDefinition>[] | null = null;

async function loadSkillSeed(): Promise<SkillDefinition[]> {
  if (seedCache) return seedCache;
  if (!seedLoadPromise) {
    seedLoadPromise = import('./marketplaceSeed').then((m) => {
      seedCache = m.MARKETPLACE_SKILL_SEED;
      return seedCache;
    });
  }
  return seedLoadPromise;
}

async function getSkillSearchIndex(): Promise<SearchEntry<SkillDefinition>[]> {
  if (searchIndex) return searchIndex;
  const seed = await loadSkillSeed();
  const catalog = [...ALL_BUILTIN_SKILLS, ...seed];
  searchIndex = buildSearchIndex(
    catalog,
    (s) => `${s.name} ${s.description} ${s.category} ${s.tags.join(' ')}`,
  );
  return searchIndex;
}

interface GitHubRepo {
  full_name: string;
  description: string | null;
  html_url: string;
  stargazers_count: number;
}

/** Load marketplace — builtin + lazy seed + optional GitHub OSS fetch */
export async function loadSkillsMarketplace(query = ''): Promise<SkillDefinition[]> {
  const q = query.trim();
  const index = await getSkillSearchIndex();
  const filtered = filterSearchIndex(index, q, q ? 500 : 300);
  const github = q ? await searchGitHubSkills(q) : [];
  return mergeUniqueById([filtered, github]);
}

async function searchGitHubSkills(query: string): Promise<SkillDefinition[]> {
  const q = encodeURIComponent(`${query} SKILL.md OR skill.md agent skill`);
  try {
    const res = await fetch(`${GITHUB_API}/search/repositories?q=${q}&sort=stars&order=desc&per_page=20`);
    if (!res.ok) return [];
    const data = await res.json() as { items: GitHubRepo[] };
    return (data.items ?? []).map((repo) => {
      const slug = repo.full_name.replace('/', '.');
      return {
        id: `oss.github.${slug}`,
        name: repo.full_name.split('/')[1]?.replace(/-/g, ' ') ?? repo.full_name,
        description: repo.description ?? `Open-source skill from ${repo.full_name}`,
        category: 'coding' as const,
        scope: 'both' as const,
        tags: ['github', 'oss'],
        source: 'oss' as const,
        author: repo.full_name.split('/')[0],
        repoUrl: `${repo.html_url}/blob/main/SKILL.md`,
        verified: repo.stargazers_count > 50,
        installs: repo.stargazers_count,
      };
    });
  } catch {
    return [];
  }
}

/** Convert GitHub blob/tree URLs to raw.githubusercontent.com SKILL.md paths */
export function githubSkillRawUrl(repoUrl: string): string | null {
  try {
    const url = new URL(repoUrl);
    if (!url.hostname.includes('github.com')) return null;
    const parts = url.pathname.split('/').filter(Boolean);
    if (parts.length < 2) return null;
    const [owner, repo, ...rest] = parts;
    let ref = 'main';
    let filePath = 'SKILL.md';
    if (rest[0] === 'blob' || rest[0] === 'tree') {
      ref = rest[1] ?? 'main';
      filePath = rest.slice(2).join('/') || 'SKILL.md';
    } else if (rest.length > 0) {
      filePath = rest.join('/');
    }
    if (!filePath.toLowerCase().endsWith('.md')) {
      filePath = filePath.endsWith('/') ? `${filePath}SKILL.md` : `${filePath}/SKILL.md`;
    }
    return `https://raw.githubusercontent.com/${owner}/${repo}/${ref}/${filePath}`;
  } catch {
    return null;
  }
}

/** Fetch skill content from repo URL when installing OSS skill */
export async function fetchSkillContent(skill: SkillDefinition): Promise<string> {
  if (skill.content) return skill.content;
  if (!skill.repoUrl) {
    return `---\nname: ${skill.name}\ndescription: ${skill.description}\n---\n\n# ${skill.name}\n\n${skill.description}\n`;
  }
  try {
    const rawUrl = githubSkillRawUrl(skill.repoUrl)
      ?? skill.repoUrl
        .replace('github.com', 'raw.githubusercontent.com')
        .replace('/blob/', '/')
        .replace('/tree/', '/');
    const res = await fetch(rawUrl);
    if (res.ok) return await res.text();
  } catch { /* fallback */ }
  return `---\nname: ${skill.name}\ndescription: ${skill.description}\n---\n\n# ${skill.name}\n\nInstall from: ${skill.repoUrl}\n\n${skill.description}\n`;
}

/** Fast initial load — top catalog slice from lazy-loaded seed index */
export async function populateMarketplaceOnOpen(): Promise<SkillDefinition[]> {
  const index = await getSkillSearchIndex();
  return filterSearchIndex(index, '', 300);
}

/** Preload seed chunk when app starts (optional warm-up) */
export function preloadSkillMarketplaceSeed(): void {
  void loadSkillSeed();
}
