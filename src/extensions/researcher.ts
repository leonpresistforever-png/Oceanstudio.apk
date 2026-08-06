import type { ExtensionDefinition } from './types';
import { BUILTIN_EXTENSIONS } from './builtinCatalog';
import { buildSearchIndex, filterSearchIndex, mergeUniqueById, type SearchEntry } from '../lib/catalogSearch';

const OPEN_VSX_SEARCH = 'https://open-vsx.org/api/-/search';

let seedCache: ExtensionDefinition[] | null = null;
let seedLoadPromise: Promise<ExtensionDefinition[]> | null = null;
let searchIndex: SearchEntry<ExtensionDefinition>[] | null = null;

async function loadExtensionSeed(): Promise<ExtensionDefinition[]> {
  if (seedCache) return seedCache;
  if (!seedLoadPromise) {
    seedLoadPromise = import('./marketplaceSeed').then((m) => {
      seedCache = m.MARKETPLACE_EXTENSION_SEED;
      return seedCache;
    });
  }
  return seedLoadPromise;
}

async function getExtensionSearchIndex(): Promise<SearchEntry<ExtensionDefinition>[]> {
  if (searchIndex) return searchIndex;
  const seed = await loadExtensionSeed();
  const catalog = [...BUILTIN_EXTENSIONS, ...seed];
  searchIndex = buildSearchIndex(
    catalog,
    (e) => `${e.displayName} ${e.description} ${e.id} ${e.publisher} ${e.tags.join(' ')}`,
  );
  return searchIndex;
}

interface OpenVsxExtension {
  namespace: string;
  name: string;
  displayName?: string;
  description?: string;
  version: string;
  averageRating?: number;
  downloadCount?: number;
  verified?: boolean;
  url?: string;
}

function openVsxToDefinition(ext: OpenVsxExtension): ExtensionDefinition {
  const id = `${ext.namespace}.${ext.name}`;
  const displayName = ext.displayName ?? ext.name.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
  return {
    id,
    name: ext.name,
    displayName,
    description: ext.description ?? `Extension ${id} from Open VSX`,
    version: ext.version,
    publisher: ext.namespace,
    category: inferCategory(ext.name, ext.description ?? ''),
    platforms: ['electron', 'web'],
    installType: 'openvsx',
    source: 'oss',
    openvsxId: id,
    repository: ext.url,
    agentGuide: buildAgentGuide(displayName, ext.description ?? ''),
    commands: [{ id: `${id}.activate`, title: `Use ${displayName}` }],
    activationEvents: ['onStartupFinished'],
    tags: ['openvsx', 'oss', ext.namespace],
    verified: ext.verified ?? false,
    downloads: ext.downloadCount,
    rating: ext.averageRating,
  };
}

function inferCategory(name: string, desc: string): ExtensionDefinition['category'] {
  const hay = `${name} ${desc}`.toLowerCase();
  if (hay.includes('format') || hay.includes('prettier')) return 'formatter';
  if (hay.includes('lint') || hay.includes('eslint')) return 'linter';
  if (hay.includes('debug')) return 'debug';
  if (hay.includes('git') || hay.includes('scm')) return 'scm';
  if (hay.includes('theme') || hay.includes('color')) return 'theme';
  if (hay.includes('copilot') || hay.includes('ai') || hay.includes('cursor') || hay.includes('claude')) return 'ai';
  if (hay.includes('docker') || hay.includes('kubernetes') || hay.includes('terraform')) return 'docker';
  if (hay.includes('test') || hay.includes('jest') || hay.includes('playwright')) return 'testing';
  if (hay.includes('sql') || hay.includes('database') || hay.includes('prisma')) return 'database';
  if (hay.includes('python') || hay.includes('rust') || hay.includes('go ') || hay.includes('java')) return 'language';
  return 'other';
}

function buildAgentGuide(name: string, description: string): string {
  return `## ${name}\n\n${description}\n\n**Agent usage:**\n- Check if extension is enabled before relying on its features\n- Use extension commands via Command Palette equivalents\n- Prefer extension formatters/linters over manual fixes\n- Fall back to terminal CLI if extension unavailable`;
}

/** Fetch live extensions from Open VSX registry */
export async function searchOpenVsx(query: string, size = 30): Promise<ExtensionDefinition[]> {
  try {
    const q = encodeURIComponent(query.trim() || 'popular');
    const res = await fetch(`${OPEN_VSX_SEARCH}?query=${q}&size=${size}`);
    if (!res.ok) return [];
    const data = await res.json() as { extensions?: OpenVsxExtension[] };
    return (data.extensions ?? []).map(openVsxToDefinition);
  } catch {
    return [];
  }
}

export async function loadExtensionsMarketplace(query = ''): Promise<ExtensionDefinition[]> {
  const q = query.trim();
  if (!q) return populateExtensionsOnOpen();

  const index = await getExtensionSearchIndex();
  const filtered = filterSearchIndex(index, q, 500);
  const live = await searchOpenVsx(q, 40);
  return mergeUniqueById([live, filtered]);
}

export async function populateExtensionsOnOpen(): Promise<ExtensionDefinition[]> {
  const [live, index] = await Promise.all([
    searchOpenVsx('prettier eslint python docker', 30),
    getExtensionSearchIndex(),
  ]);
  const seedSlice = filterSearchIndex(index, '', 200);
  return mergeUniqueById([live, seedSlice]);
}

export async function fetchExtensionDetail(id: string): Promise<ExtensionDefinition | null> {
  const [publisher, ...nameParts] = id.split('.');
  const name = nameParts.join('.');
  if (!publisher || !name) return null;
  try {
    const res = await fetch(`https://open-vsx.org/api/${publisher}/${name}`);
    if (!res.ok) return null;
    const data = await res.json() as OpenVsxExtension & { description?: string };
    return openVsxToDefinition({ ...data, namespace: publisher, name });
  } catch {
    return null;
  }
}

/** Preload seed chunk in background */
export function preloadExtensionMarketplaceSeed(): void {
  void loadExtensionSeed();
}
