import type { ScrapingProvider } from '../models/types';

export type RuntimePlatform = 'electron' | 'web' | 'mobile';

export interface PlatformCapabilities {
  platform: RuntimePlatform;
  /** Full host shell — all commands, no whitelist */
  unrestrictedTerminal: boolean;
  /** Spawn npx stdio MCP processes */
  localMcpStdio: boolean;
  /** Connect to cloud SSE/HTTP MCP */
  cloudMcpSse: boolean;
  /** Custom OpenAI-compatible model endpoints */
  customModelEndpoints: boolean;
  /** Upload skill files from disk */
  skillFileUpload: boolean;
  /** PostgreSQL direct connection from agent */
  postgresDirect: boolean;
  /** Available scraping backends */
  scrapingProviders: ScrapingProvider[];
  /** OAuth popup for MCP / GitHub */
  oauthPopup: boolean;
  /** Agent can create skills autonomously */
  agentSkillCreation: boolean;
}

const ELECTRON_CAPS: PlatformCapabilities = {
  platform: 'electron',
  unrestrictedTerminal: true,
  localMcpStdio: true,
  cloudMcpSse: true,
  customModelEndpoints: true,
  skillFileUpload: true,
  postgresDirect: true,
  scrapingProviders: ['firecrawl', 'puppeteer', 'playwright', 'fetch', 'custom'],
  oauthPopup: true,
  agentSkillCreation: true,
};

const WEB_CAPS: PlatformCapabilities = {
  platform: 'web',
  unrestrictedTerminal: false, // dev WS terminal only
  localMcpStdio: false,
  cloudMcpSse: true,
  customModelEndpoints: true,
  skillFileUpload: true,
  postgresDirect: false,
  scrapingProviders: ['firecrawl', 'fetch', 'custom'],
  oauthPopup: true,
  agentSkillCreation: true,
};

const MOBILE_CAPS: PlatformCapabilities = {
  platform: 'mobile',
  unrestrictedTerminal: true, // proot native Linux
  localMcpStdio: false,
  cloudMcpSse: true,
  customModelEndpoints: true,
  skillFileUpload: false,
  postgresDirect: false,
  scrapingProviders: ['firecrawl', 'fetch', 'custom'],
  oauthPopup: false,
  agentSkillCreation: true,
};

export function getPlatformCapabilities(info: {
  isElectron?: boolean;
  isAndroid?: boolean;
  hasNativeTerminal?: boolean;
}): PlatformCapabilities {
  if (info.isElectron) return ELECTRON_CAPS;
  if (info.isAndroid || info.hasNativeTerminal) return MOBILE_CAPS;
  return WEB_CAPS;
}
