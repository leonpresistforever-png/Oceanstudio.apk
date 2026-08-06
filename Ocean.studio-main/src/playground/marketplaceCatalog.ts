import type { PlaygroundTool, PlaygroundToolCategory } from './types';

/** Keyword → playground feature mapping for auto-tagging marketplace items */
const FEATURE_KEYWORDS: Record<PlaygroundToolCategory, string[]> = {
  agents: ['agent', 'filesystem', 'github', 'docker', 'kubernetes', 'terraform', 'jest', 'vitest', 'eslint', 'prettier', 'sonarqube', 'codecov', 'bazel', 'gradle', 'maven', 'npm', 'playwright', 'puppeteer', 'selenium', 'cypress', 'linear', 'jira', 'confluence', 'slack', 'discord', 'telegram', 'cron', 'queue', 'webhook'],
  research: ['research', 'scrape', 'crawl', 'browser', 'arxiv', 'pubmed', 'wikipedia', 'news', 'search', 'elasticsearch', 'algolia', 'firecrawl', 'cheerio', 'ocr', 'summarize', 'pdf', 'docs', 'web'],
  'media-image': ['image', 'imagen', 'dalle', 'flux', 'sdxl', 'midjourney', 'ideogram', 'canva', 'figma', 'sharp', 'controlnet', 'comfyui', 'invokeai', 'rembg', 'gfpgan', 'logo', 'mockup', 'icon', 'og-image', 'favicon', 'texture', 'shader', 'palette'],
  'media-video': ['video', 'veo', 'runway', 'pika', 'kling', 'sora', 'ffmpeg', 'flow', 'youtube'],
  'media-3d': ['3d', 'mesh', 'triposr', 'instantmesh', 'shap', 'point-e', 'blender', 'meshlab', 'gltf', 'obj', 'spline', 'tripo', 'meshy', 'luma', 'rodin', 'trellis', 'wonder3d', 'three', 'draco', 'heightmap', 'normal-map', 'tilemap', 'sprite'],
  'audio-tts': ['tts', 'speech', 'voice', 'whisper', 'bark', 'elevenlabs', 'azure', 'openai-tts', 'google-tts', 'vocoder', 'autotune'],
  'audio-music': ['music', 'audio', 'spotify', 'lyria', 'suno', 'udio', 'musicgen', 'audiocraft', 'beat', 'midi', 'chord', 'drum', 'stem', 'sampler', 'metronome', 'tuner', 'harmonizer', 'reverb', 'delay', 'chorus', 'distortion', 'eq', 'compress', 'normalize', 'pitch', 'waveform', 'spectrogram', 'fft'],
  upscale: ['upscale', 'esrgan', 'waifu2x', 'gigapixel', 'topaz', 'enhance'],
  editor: ['edit', 'inpaint', 'background', 'style', 'transfer', 'ip-adapter'],
  mcp: ['mcp', 'connector'],
  plugin: ['plugin', 'extension'],
  schedule: ['schedule', 'cron', 'calendar', 'reminder'],
  'active-bot': ['bot', 'screen', 'accessibility', 'game', 'social', 'upload'],
  recording: ['record', 'capture', 'screen', 'encode'],
  narrator: ['narrator', 'screen', 'voice', 'wake', 'assistant', 'translate', 'read', 'storyteller', 'ocean'],
  skills: ['skill', 'agent', 'prompt', 'instruction', 'workflow', 'playbook', 'guide', 'md'],
};

export function inferFeatureTags(id: string, name: string, description: string): PlaygroundToolCategory[] {
  const hay = `${id} ${name} ${description}`.toLowerCase();
  const tags: PlaygroundToolCategory[] = [];
  for (const [feature, keywords] of Object.entries(FEATURE_KEYWORDS) as [PlaygroundToolCategory, string[]][]) {
    if (feature === 'mcp' || feature === 'plugin') continue;
    if (keywords.some((kw) => hay.includes(kw))) {
      tags.push(feature);
    }
  }
  if (tags.length === 0) {
    tags.push('agents', 'research');
  }
  return [...new Set(tags)];
}

/** Extra MCP entries beyond base catalog — ensures 100+ per major feature */
const EXTRA_MCP_BY_FEATURE: Partial<Record<PlaygroundToolCategory, [string, string, string, string][]>> = {
  'media-3d': [
    ['meshy-mcp', 'Meshy 3D MCP', 'Text/image to 3D via Meshy API', '🔷'],
    ['tripo-mcp', 'Tripo 3D MCP', 'Tripo cloud 3D generation', '🏔️'],
    ['luma-mcp', 'Luma Genie MCP', 'Luma text-to-3D scenes', '✨'],
    ['replicate-3d-mcp', 'Replicate 3D MCP', 'TripoSR, InstantMesh on Replicate', '🔄'],
    ['fal-3d-mcp', 'Fal 3D MCP', 'Trellis and TripoSR cloud inference', '⚡'],
    ['hf-3d-mcp', 'HF 3D Inference MCP', 'HuggingFace 3D models serverless', '🤗'],
    ['spline-mcp', 'Spline AI MCP', 'Interactive 3D scene export', '🎯'],
    ['rodin-mcp', 'Rodin Hyper3D MCP', 'Detailed mesh generation', '🗿'],
    ['csm-mcp', 'CSM Image-to-3D MCP', 'Common Sense Machines API', '🧠'],
    ['stability-3d-mcp', 'Stability 3D MCP', 'Stable Fast 3D API', '🌀'],
  ],
  'media-image': [
    ['leonardo-mcp', 'Leonardo MCP', 'Leonardo.ai image generation', '🎨'],
    ['ideogram-mcp', 'Ideogram MCP', 'Text-in-image generation', '🔤'],
    ['bfl-mcp', 'Black Forest Labs MCP', 'Flux model API', '🌲'],
    ['getimg-mcp', 'GetImg MCP', 'SDXL and Flux hosting', '🖼️'],
    ['prodia-mcp', 'Prodia MCP', 'Fast SD inference', '⚡'],
  ],
  'media-video': [
    ['minimax-mcp', 'MiniMax Video MCP', 'Hailuo video generation', '🎬'],
    ['haiper-mcp', 'Haiper Video MCP', 'Short video clips', '📹'],
    ['luma-dream-mcp', 'Luma Dream Machine MCP', 'Cinematic video', '🌙'],
  ],
  'audio-tts': [
    ['playht-mcp', 'PlayHT MCP', 'Neural TTS voices', '🔊'],
    ['murf-mcp', 'Murf MCP', 'Studio voice synthesis', '🎙️'],
    ['resemble-mcp', 'Resemble MCP', 'Custom voice cloning', '👤'],
  ],
  'audio-music': [
    ['udio-mcp', 'Udio MCP', 'Full song generation', '🎧'],
    ['soundraw-mcp', 'Soundraw MCP', 'Royalty-free AI music', '🎵'],
    ['aiva-mcp', 'AIVA MCP', 'Classical composition AI', '🎼'],
  ],
  research: [
    ['tavily-mcp', 'Tavily MCP', 'AI-optimized web search', '🔍'],
    ['exa-mcp', 'Exa MCP', 'Neural search API', '🧠'],
    ['brave-search-mcp', 'Brave Search MCP', 'Privacy web search', '🦁'],
    ['jina-mcp', 'Jina Reader MCP', 'URL to markdown', '📄'],
  ],
  agents: [
    ['computer-use-mcp', 'Computer Use MCP', 'Desktop automation agent', '🖥️'],
    ['shell-mcp', 'Shell MCP', 'Terminal command execution', '💻'],
    ['git-mcp', 'Git MCP', 'Repository operations', '📦'],
  ],
  recording: [
    ['obs-mcp', 'OBS WebSocket MCP', 'OBS Studio remote control', '🎥'],
    ['ffmpeg-record-mcp', 'FFmpeg Record MCP', 'CLI screen capture', '📹'],
  ],
};

const EXTRA_PLUGINS_BY_FEATURE: Partial<Record<PlaygroundToolCategory, [string, string, string, string][]>> = {
  'media-3d': [
    ['nerfstudio', 'NeRF Studio', 'Neural radiance fields', '🌈'],
    ['gaussian-splatting', 'Gaussian Splatting', '3D Gaussian splats', '✨'],
    ['kaolin', 'Kaolin 3D', 'NVIDIA 3D deep learning', '🔺'],
    ['pytorch3d', 'PyTorch3D', 'Facebook 3D ML toolkit', '📐'],
    ['openvdb', 'OpenVDB', 'Volumetric 3D data', '🧊'],
  ],
  'media-image': [
    ['sdxl-lightning', 'SDXL Lightning', 'Fast SDXL inference', '⚡'],
    ['fooocus', 'Fooocus', 'SDXL UI simplified', '🎯'],
    ['kohya', 'Kohya Trainer', 'LoRA fine-tuning', '🏋️'],
  ],
  upscale: [
    ['swinir', 'SwinIR', 'Image restoration upscale', '📈'],
    ['hat', 'HAT Upscale', 'Hybrid attention transformer', '🔍'],
    ['anime4k', 'Anime4K', 'Real-time anime upscale', '🎌'],
  ],
  'audio-music': [
    ['audioldm', 'AudioLDM', 'Text-to-audio generation', '🎵'],
    ['riffusion', 'Riffusion', 'Music from spectrograms', '🎶'],
    ['demucs', 'Demucs', 'Source separation stems', '🎚️'],
  ],
};

export function getExtraMcpEntries(): { id: string; name: string; description: string; icon: string; featureTags: PlaygroundToolCategory[] }[] {
  const out: { id: string; name: string; description: string; icon: string; featureTags: PlaygroundToolCategory[] }[] = [];
  for (const [feature, entries] of Object.entries(EXTRA_MCP_BY_FEATURE) as [PlaygroundToolCategory, [string, string, string, string][]][]) {
    for (const [id, name, desc, icon] of entries) {
      out.push({ id: `mcp-extra-${id}`, name, description: desc, icon, featureTags: [feature, 'mcp'] });
    }
  }
  return out;
}

export function getExtraPluginEntries(): { id: string; name: string; description: string; icon: string; featureTags: PlaygroundToolCategory[] }[] {
  const out: { id: string; name: string; description: string; icon: string; featureTags: PlaygroundToolCategory[] }[] = [];
  for (const [feature, entries] of Object.entries(EXTRA_PLUGINS_BY_FEATURE) as [PlaygroundToolCategory, [string, string, string, string][]][]) {
    for (const [id, name, desc, icon] of entries) {
      out.push({ id: `plugin-extra-${id}`, name, description: desc, icon, featureTags: [feature, 'plugin'] });
    }
  }
  return out;
}

export function countByFeature(tools: PlaygroundTool[]): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const t of tools) {
    const tags = t.featureTags ?? [t.category];
    for (const tag of tags) {
      counts[tag] = (counts[tag] ?? 0) + 1;
    }
  }
  return counts;
}

export function filterMarketplaceTools(
  tools: PlaygroundTool[],
  kind: 'mcp' | 'plugin',
  featureFilter: PlaygroundToolCategory | 'all',
  search: string
): PlaygroundTool[] {
  const q = search.toLowerCase();
  return tools.filter((t) => {
    if (t.kind !== kind && t.category !== kind) return false;
    if (featureFilter !== 'all') {
      const tags = t.featureTags ?? [];
      if (!tags.includes(featureFilter) && t.category !== featureFilter) return false;
    }
    if (!q) return true;
    return (
      t.name.toLowerCase().includes(q) ||
      t.description.toLowerCase().includes(q) ||
      t.tags.some((tag) => tag.includes(q)) ||
      (t.featureTags ?? []).some((tag) => tag.includes(q))
    );
  });
}

export const MARKETPLACE_FEATURE_FILTERS: { id: PlaygroundToolCategory | 'all'; label: string }[] = [
  { id: 'all', label: 'All Features' },
  { id: 'agents', label: 'Agents' },
  { id: 'research', label: 'Research' },
  { id: 'media-image', label: 'Image' },
  { id: 'media-video', label: 'Video' },
  { id: 'media-3d', label: '3D Studio' },
  { id: 'audio-tts', label: 'TTS' },
  { id: 'audio-music', label: 'Music' },
  { id: 'upscale', label: 'Upscale' },
  { id: 'editor', label: 'Editor' },
  { id: 'recording', label: 'Recording' },
  { id: 'active-bot', label: 'Active Bot' },
  { id: 'skills', label: 'Skills' },
  { id: 'schedule', label: 'Schedule' },
];
