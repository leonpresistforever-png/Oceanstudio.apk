import type { PlaygroundTool, PlaygroundToolCategory, PlaygroundPlatform } from './types';
import { ELECTRON_3D_SOURCES, ANDROID_CLOUD_3D_SOURCES } from './threeDStudio';
import { inferFeatureTags, getExtraMcpEntries, getExtraPluginEntries } from './marketplaceCatalog';

interface ToolOpts {
  platforms?: PlaygroundPlatform[];
  freeTier?: boolean;
  envRequirements?: string;
  featureTags?: PlaygroundToolCategory[];
  customInstall?: boolean;
  installGuide?: string;
}

function tool(
  id: string,
  name: string,
  description: string,
  category: PlaygroundToolCategory,
  icon: string,
  kind: PlaygroundTool['kind'] = 'builtin',
  provider?: string,
  tags: string[] = [],
  opts: ToolOpts = {}
): PlaygroundTool {
  const featureTags = opts.featureTags ?? inferFeatureTags(id, name, description);
  return {
    id,
    name,
    description,
    category,
    icon,
    kind,
    provider,
    tags,
    enabled: true,
    authType: kind === 'mcp' ? 'mcp' : kind === 'provider' ? 'oauth' : 'none',
    platforms: opts.platforms,
    freeTier: opts.freeTier,
    envRequirements: opts.envRequirements,
    featureTags,
    customInstall: opts.customInstall,
    installGuide: opts.installGuide,
  };
}

function threeDSourceTools(): PlaygroundTool[] {
  const all = [...ELECTRON_3D_SOURCES, ...ANDROID_CLOUD_3D_SOURCES];
  const seen = new Set<string>();
  return all.filter((s) => {
    if (seen.has(s.id)) return false;
    seen.add(s.id);
    return true;
  }).map((s) =>
    tool(
      `3d-${s.id}`,
      s.name,
      s.description,
      'media-3d',
      s.icon,
      s.kind === 'opensource' ? 'opensource' : s.kind === 'mcp' ? 'mcp' : s.kind === 'plugin' ? 'plugin' : 'provider',
      s.id,
      [s.mode, ...s.outputFormats],
      {
        platforms: s.platforms,
        freeTier: s.freeTier,
        envRequirements: s.envRequirements,
        installGuide: s.installGuide,
        featureTags: ['media-3d'],
      }
    )
  );
}

const BUILTIN_AGENTS = [
  tool('pg-fs-read', 'Filesystem Research', 'Agent scans entire workspace and file manager', 'agents', '📂'),
  tool('pg-app-scan', 'App Research', 'Research installed apps and project dependencies', 'agents', '📱'),
  tool('pg-task-assign', 'Assign Agent Task', 'Delegate long-running task to multi-agent team', 'agents', '🤖'),
  tool('pg-batch-jobs', 'Batch Agent Jobs', 'Queue multiple agent tasks in sequence', 'agents', '📋'),
  tool('pg-watch-folder', 'Watch Folder', 'Agent monitors folder for changes', 'agents', '👁️'),
];

const BUILTIN_RESEARCH = [
  tool('pg-web-scrape', 'Deep Web Scrape', 'Long-run website content extraction', 'research', '🌐'),
  tool('pg-web-crawl', 'Site Crawler', 'Crawl entire site sections with Firecrawl', 'research', '🕷️', 'plugin', 'firecrawl'),
  tool('pg-docs-research', 'Docs Research', 'Research documentation across the web', 'research', '📚'),
  tool('pg-code-search', 'Codebase Search', 'Semantic search across all project files', 'research', '🔍'),
  tool('pg-pdf-parse', 'PDF Research', 'Extract and summarize PDF documents', 'research', '📄'),
];

const IMAGE_TOOLS = [
  tool('pg-imagen', 'Google Imagen', 'Image generation via Google AI', 'media-image', '🖼️', 'provider', 'google'),
  tool('pg-dalle', 'DALL·E 3', 'OpenAI image generation', 'media-image', '🎨', 'provider', 'openai'),
  tool('pg-flux', 'Flux Pro', 'High-quality image generation', 'media-image', '✨', 'provider', 'replicate'),
  tool('pg-sdxl', 'Stable Diffusion XL', 'Open-source image gen', 'media-image', '🌀', 'provider', 'stability'),
  tool('pg-midjourney', 'Midjourney', 'Via proxy gateway', 'media-image', '🌸', 'provider', 'omniroute'),
  tool('pg-ideogram', 'Ideogram', 'Text-in-image generation', 'media-image', '🔤', 'provider', 'ideogram'),
];

const VIDEO_TOOLS = [
  tool('pg-veo', 'Veo 3.1', 'Google Veo video generation', 'media-video', '🎬', 'provider', 'google'),
  tool('pg-flow', 'Google Flow', 'OAuth video generation flow', 'media-video', '🌊', 'provider', 'google-flow'),
  tool('pg-runway', 'Runway Gen-3', 'Cinematic video generation', 'media-video', '🎥', 'provider', 'runway'),
  tool('pg-pika', 'Pika Labs', 'Short-form video gen', 'media-video', '⚡', 'provider', 'pika'),
  tool('pg-kling', 'Kling AI', 'Long-form video generation', 'media-video', '🎞️', 'provider', 'kling'),
  tool('pg-sora', 'Sora', 'OpenAI video model', 'media-video', '💫', 'provider', 'openai'),
];

const UPSCALE_TOOLS = [
  tool('pg-upscale-img', 'Image Upscale 4K', 'Upscale images to high resolution', 'upscale', '🔍'),
  tool('pg-upscale-vid', 'Video Upscale 4K', 'Upscale video to high quality', 'upscale', '📺'),
  tool('pg-real-esrgan', 'Real-ESRGAN', 'Open-source image upscaler', 'upscale', '📈', 'plugin'),
  tool('pg-topaz', 'Topaz Video AI', 'Professional video upscale', 'upscale', '💎', 'provider', 'topaz'),
];

const EDITOR_TOOLS = [
  tool('pg-img-edit', 'Image Editor', 'Inpaint, outpaint, style transfer', 'editor', '✏️'),
  tool('pg-bg-remove', 'Background Remove', 'Remove image backgrounds', 'editor', '🪄'),
  tool('pg-inpaint', 'Inpainting', 'Edit regions of images', 'editor', '🖌️'),
  tool('pg-style-transfer', 'Style Transfer', 'Apply artistic styles', 'editor', '🎭'),
];

const THREE_D_TOOLS = [
  tool('pg-text-3d', 'Text to 3D', 'Generate 3D models from text', 'media-3d', '🧊'),
  tool('pg-2d-3d', '2D to 3D', 'Convert images to 3D models', 'media-3d', '📦'),
  tool('pg-meshy', 'Meshy AI', 'Text/image to 3D mesh', 'media-3d', '🔷', 'provider', 'meshy'),
  tool('pg-tripo', 'Tripo 3D', 'High-quality 3D generation', 'media-3d', '🏔️', 'provider', 'tripo'),
  tool('pg-spline', 'Spline Export', 'Export to Spline 3D', 'media-3d', '🎯', 'mcp', 'spline'),
];

const TTS_TOOLS = [
  tool('pg-google-tts', 'Google Cloud TTS', 'Neural text-to-speech', 'audio-tts', '🔊', 'provider', 'google'),
  tool('pg-elevenlabs', 'ElevenLabs', 'Premium voice synthesis', 'audio-tts', '🎙️', 'mcp', 'elevenlabs'),
  tool('pg-openai-tts', 'OpenAI TTS', 'GPT voice synthesis', 'audio-tts', '💬', 'provider', 'openai'),
  tool('pg-azure-tts', 'Azure Speech', 'Microsoft neural voices', 'audio-tts', '☁️', 'provider', 'azure'),
  tool('pg-coqui', 'Coqui TTS', 'Open-source TTS', 'audio-tts', '🐸', 'plugin'),
];

const MUSIC_TOOLS = [
  tool('pg-lyria', 'Google Lyria', 'AI music generation', 'audio-music', '🎵', 'provider', 'google'),
  tool('pg-suno', 'Suno AI', 'Full song generation', 'audio-music', '🎶', 'provider', 'suno'),
  tool('pg-udio', 'Udio', 'Music and vocals', 'audio-music', '🎧', 'provider', 'udio'),
  tool('pg-beat-fetch', 'Beat Research', 'Agent fetches top 100 beats/lyrics', 'audio-music', '🥁'),
  tool('pg-stem-split', 'Stem Splitter', 'Separate drums/bass/vocals', 'audio-music', '🎚️', 'plugin'),
];

const SCHEDULE_TOOLS = [
  tool('pg-schedule', 'Schedule Task', 'Schedule agent task for later', 'schedule', '⏰'),
  tool('pg-cron', 'Recurring Jobs', 'Daily/hourly agent schedules', 'schedule', '🔄'),
  tool('pg-reminder', 'Agent Reminder', 'Remind and execute at time', 'schedule', '🔔'),
];

const ACTIVE_BOT_BUILTIN = [
  tool('ab-screen', 'Screen Interaction', 'Live screen control with no latency', 'active-bot', '🖱️'),
  tool('ab-files', 'File Arrangement', 'Organize, compile, decompile files', 'active-bot', '🗂️'),
  tool('ab-install', 'App Installer', 'Install and configure applications', 'active-bot', '📲'),
  tool('ab-research', 'Article Analyzer', 'Open sites and analyze content', 'active-bot', '📰'),
  tool('ab-social', 'Social Uploader', 'Upload videos to social media', 'active-bot', '📱'),
  tool('ab-game', 'Game Companion', 'Anti-AFK and gameplay.md learning', 'active-bot', '🕹️'),
  tool('ab-mood', 'Mood Check', 'Greet, joke, and check your mood', 'active-bot', '😊'),
  tool('ab-worker', 'Background Worker', 'Continuous multi-day operation', 'active-bot', '♾️'),
];

const RECORDING_BUILTIN = [
  tool('rec-fullscreen', 'Full Screen 4K', 'Record entire screen at up to 4K 165fps', 'recording', '🖥️'),
  tool('rec-window', 'App Window', 'Record specific application window', 'recording', '🪟'),
  tool('rec-region', 'Partial Area', 'Select region to record', 'recording', '✂️'),
  tool('rec-ultra', 'Ultra Render', 'Crystal-clear hardware bypass encoding', 'recording', '💎'),
  tool('rec-bg', 'Background Record', 'Keep recording while app in background', 'recording', '⏺️'),
];

const NARRATOR_BUILTIN = [
  tool('nar-screen-read', 'Screen Narrator', 'Provider agent reads screen content — storyteller, helper, or technical tones', 'narrator', '👁️', 'provider', 'google.antigravity'),
  tool('nar-voice-cmd', 'Voice Commands', 'Say "ocean" or custom wake phrase to activate background assistant', 'narrator', '🎙️', 'provider', 'openai.codex'),
  tool('nar-live-help', 'Live Helper', 'Continuous background assistant — periodic screen descriptions', 'narrator', '🤝', 'provider', 'anthropic.claude-code'),
  tool('nar-translate', 'Screen Translator', 'Translate on-screen text to any language with TTS', 'narrator', '🌐', 'provider', 'google.gemini'),
];

function generateMcpTools(): PlaygroundTool[] {
  const names = [
    ['filesystem', 'Filesystem MCP', 'Read/write workspace files', '📁'],
    ['github', 'GitHub MCP', 'Repos, issues, PRs', '🐙'],
    ['firebase', 'Firebase MCP', 'Firestore, auth, hosting', '🔥'],
    ['figma', 'Figma MCP', 'Design file access', '🎨'],
    ['browser', 'Browser MCP', 'Automate web browsing', '🌍'],
    ['postgres', 'PostgreSQL MCP', 'SQL database queries', '🐘'],
    ['redis', 'Redis MCP', 'Cache and pub/sub', '🔴'],
    ['slack', 'Slack MCP', 'Team messaging', '💬'],
    ['notion', 'Notion MCP', 'Docs and databases', '📝'],
    ['google-drive', 'Google Drive MCP', 'Cloud file storage', '📂'],
    ['gmail', 'Gmail MCP', 'Email automation', '✉️'],
    ['calendar', 'Google Calendar', 'Schedule events', '📅'],
    ['youtube', 'YouTube MCP', 'Video research', '▶️'],
    ['spotify', 'Spotify MCP', 'Music API', '🎵'],
    ['twitter', 'X/Twitter MCP', 'Social research', '🐦'],
    ['linkedin', 'LinkedIn MCP', 'Professional network', '💼'],
    ['shopify', 'Shopify MCP', 'E-commerce data', '🛒'],
    ['stripe', 'Stripe MCP', 'Payments API', '💳'],
    ['twilio', 'Twilio MCP', 'SMS and voice', '📞'],
    ['aws', 'AWS MCP', 'Cloud services', '☁️'],
    ['gcp', 'GCP MCP', 'Google Cloud', '🌐'],
    ['azure', 'Azure MCP', 'Microsoft Cloud', '🔷'],
    ['docker', 'Docker MCP', 'Container management', '🐳'],
    ['kubernetes', 'K8s MCP', 'Cluster orchestration', '⚓'],
    ['terraform', 'Terraform MCP', 'Infrastructure as code', '🏗️'],
    ['sentry', 'Sentry MCP', 'Error monitoring', '🚨'],
    ['datadog', 'Datadog MCP', 'Observability', '📊'],
    ['linear', 'Linear MCP', 'Issue tracking', '📋'],
    ['jira', 'Jira MCP', 'Project management', '🎯'],
    ['confluence', 'Confluence MCP', 'Wiki docs', '📖'],
    ['airtable', 'Airtable MCP', 'Spreadsheet DB', '📊'],
    ['hubspot', 'HubSpot MCP', 'CRM data', '🤝'],
    ['salesforce', 'Salesforce MCP', 'Enterprise CRM', '☁️'],
    ['zendesk', 'Zendesk MCP', 'Support tickets', '🎫'],
    ['intercom', 'Intercom MCP', 'Customer chat', '💬'],
    ['mailchimp', 'Mailchimp MCP', 'Email marketing', '📧'],
    ['canva', 'Canva MCP', 'Design automation', '🖼️'],
    ['midjourney-mcp', 'Midjourney MCP', 'Image via MCP', '🌸'],
    ['replicate', 'Replicate MCP', 'ML model hosting', '🔄'],
    ['huggingface', 'HuggingFace MCP', 'Model hub', '🤗'],
    ['wandb', 'Weights & Biases', 'ML experiment tracking', '📈'],
    ['mlflow', 'MLflow MCP', 'ML lifecycle', '🔬'],
    ['pinecone', 'Pinecone MCP', 'Vector database', '🌲'],
    ['weaviate', 'Weaviate MCP', 'Vector search', '🔮'],
    ['chroma', 'Chroma MCP', 'Embedding store', '🎨'],
    ['qdrant', 'Qdrant MCP', 'Vector DB', '📍'],
    ['elasticsearch', 'Elasticsearch', 'Full-text search', '🔍'],
    ['algolia', 'Algolia MCP', 'Search API', '⚡'],
    ['mapbox', 'Mapbox MCP', 'Maps and geocoding', '🗺️'],
    ['openweather', 'Weather MCP', 'Weather data', '🌤️'],
    ['newsapi', 'News API MCP', 'News aggregation', '📰'],
    ['arxiv', 'arXiv MCP', 'Research papers', '📄'],
    ['pubmed', 'PubMed MCP', 'Medical research', '🏥'],
    ['wolfram', 'Wolfram Alpha', 'Computational knowledge', '🧮'],
    ['wikipedia', 'Wikipedia MCP', 'Encyclopedia', '📚'],
    ['imdb', 'IMDB MCP', 'Movie database', '🎬'],
    ['goodreads', 'Goodreads MCP', 'Book reviews', '📖'],
    ['yelp', 'Yelp MCP', 'Business reviews', '⭐'],
    ['tripadvisor', 'TripAdvisor MCP', 'Travel reviews', '✈️'],
    ['coinbase', 'Coinbase MCP', 'Crypto data', '₿'],
    ['plaid', 'Plaid MCP', 'Banking data', '🏦'],
    ['quickbooks', 'QuickBooks MCP', 'Accounting', '💰'],
    ['xero', 'Xero MCP', 'Bookkeeping', '📒'],
    ['figma-dev', 'Figma Dev MCP', 'Design tokens', '🎨'],
    ['storybook', 'Storybook MCP', 'Component docs', '📦'],
    ['chromatic', 'Chromatic MCP', 'Visual testing', '👁️'],
    ['percy', 'Percy MCP', 'Visual regression', '📸'],
    ['browserstack', 'BrowserStack', 'Cross-browser test', '🌐'],
    ['lambdatest', 'LambdaTest MCP', 'Cloud testing', '☁️'],
    ['playwright', 'Playwright MCP', 'Browser automation', '🎭'],
    ['puppeteer', 'Puppeteer MCP', 'Headless Chrome', '🤖'],
    ['selenium', 'Selenium MCP', 'Web testing', '🧪'],
    ['cypress', 'Cypress MCP', 'E2E testing', '🌲'],
    ['jest', 'Jest MCP', 'Unit testing', '✅'],
    ['vitest', 'Vitest MCP', 'Fast unit tests', '⚡'],
    ['eslint', 'ESLint MCP', 'Linting', '🔧'],
    ['prettier', 'Prettier MCP', 'Code formatting', '✨'],
    ['sonarqube', 'SonarQube MCP', 'Code quality', '📊'],
    ['codecov', 'Codecov MCP', 'Coverage reports', '📈'],
    ['vercel', 'Vercel MCP', 'Deploy frontend', '▲'],
    ['netlify', 'Netlify MCP', 'Jamstack deploy', '🌐'],
    ['heroku', 'Heroku MCP', 'App hosting', '💜'],
    ['railway', 'Railway MCP', 'Cloud deploy', '🚂'],
    ['render', 'Render MCP', 'Cloud hosting', '🎨'],
    ['fly', 'Fly.io MCP', 'Edge deploy', '🪰'],
    ['cloudflare', 'Cloudflare MCP', 'CDN and workers', '☁️'],
    ['supabase', 'Supabase MCP', 'Backend as a service', '⚡'],
    ['planetscale', 'PlanetScale MCP', 'Serverless MySQL', '🪐'],
    ['neon', 'Neon MCP', 'Serverless Postgres', '💚'],
    ['mongodb', 'MongoDB MCP', 'Document database', '🍃'],
    ['cassandra', 'Cassandra MCP', 'Wide-column DB', '📊'],
    ['dynamodb', 'DynamoDB MCP', 'AWS key-value', '⚡'],
    ['snowflake', 'Snowflake MCP', 'Data warehouse', '❄️'],
    ['bigquery', 'BigQuery MCP', 'Analytics warehouse', '📊'],
    ['databricks', 'Databricks MCP', 'Lakehouse platform', '🧱'],
    ['airflow', 'Airflow MCP', 'Workflow orchestration', '🌬️'],
    ['prefect', 'Prefect MCP', 'Data pipelines', '🔮'],
    ['dbt', 'dbt MCP', 'Data transformation', '🔧'],
    ['fivetran', 'Fivetran MCP', 'Data integration', '🔗'],
    ['segment', 'Segment MCP', 'Customer data', '📊'],
    ['amplitude', 'Amplitude MCP', 'Product analytics', '📈'],
    ['mixpanel', 'Mixpanel MCP', 'Event analytics', '📊'],
    ['hotjar', 'Hotjar MCP', 'Heatmaps', '🔥'],
    ['fullstory', 'FullStory MCP', 'Session replay', '📹'],
    ['logrocket', 'LogRocket MCP', 'Frontend monitoring', '🚀'],
    ['posthog', 'PostHog MCP', 'Product OS', '🦔'],
    ['launchdarkly', 'LaunchDarkly', 'Feature flags', '🚩'],
    ['optimizely', 'Optimizely MCP', 'A/B testing', '🧪'],
    ['split', 'Split.io MCP', 'Feature delivery', '✂️'],
    ['auth0', 'Auth0 MCP', 'Authentication', '🔐'],
    ['clerk', 'Clerk MCP', 'User management', '👤'],
    ['okta', 'Okta MCP', 'Identity platform', '🔑'],
    ['1password', '1Password MCP', 'Secrets management', '🔒'],
    ['vault', 'HashiCorp Vault', 'Secret storage', '🏦'],
    ['doppler', 'Doppler MCP', 'Env secrets', '🔐'],
    ['snyk', 'Snyk MCP', 'Security scanning', '🛡️'],
    ['dependabot', 'Dependabot MCP', 'Dependency updates', '🤖'],
    ['renovate', 'Renovate MCP', 'Auto dependency PRs', '🔄'],
    ['npm', 'npm Registry MCP', 'Package search', '📦'],
    ['pypi', 'PyPI MCP', 'Python packages', '🐍'],
    ['crates', 'Crates.io MCP', 'Rust packages', '🦀'],
    ['nuget', 'NuGet MCP', '.NET packages', '📦'],
    ['maven', 'Maven MCP', 'Java packages', '☕'],
    ['gradle', 'Gradle MCP', 'Build automation', '🏗️'],
    ['bazel', 'Bazel MCP', 'Build system', '🔨'],
    ['nix', 'Nix MCP', 'Reproducible builds', '❄️'],
  ];
  const base = names.map(([id, name, desc, icon]) =>
    tool(`mcp-${id}`, name, desc, 'mcp', icon, 'mcp', id, ['mcp', 'connector'], {
      customInstall: true,
      freeTier: true,
      installGuide: 'Cursor Settings → MCP → Add server, or ocean mcp connect',
    })
  );
  const extra = getExtraMcpEntries().map((e) =>
    tool(e.id, e.name, e.description, 'mcp', e.icon, 'mcp', e.id.replace('mcp-extra-', ''), ['mcp', 'connector', 'extra'], {
      featureTags: e.featureTags,
      customInstall: true,
      freeTier: true,
      installGuide: 'Cursor Settings → MCP → Add server',
    })
  );
  return [...base, ...extra];
}

function generatePluginTools(): PlaygroundTool[] {
  const plugins = [
    ['firecrawl', 'Firecrawl', 'Web scrape and crawl', '🔥'],
    ['puppeteer-scraper', 'Puppeteer Scraper', 'Browser scraping', '🎭'],
    ['cheerio', 'Cheerio Parser', 'HTML parsing', '📄'],
    ['sharp', 'Sharp Image', 'Image processing', '🖼️'],
    ['ffmpeg', 'FFmpeg', 'Video/audio processing', '🎬'],
    ['whisper', 'Whisper STT', 'Speech to text', '🎤'],
    ['bark', 'Bark TTS', 'Open TTS', '🔊'],
    ['musicgen', 'MusicGen', 'Meta music generation', '🎵'],
    ['audiocraft', 'AudioCraft', 'Meta audio models', '🎶'],
    ['stable-audio', 'Stable Audio', 'Sound generation', '🎧'],
    ['controlnet', 'ControlNet', 'Image control', '🎛️'],
    ['ip-adapter', 'IP-Adapter', 'Image prompt adapter', '🖌️'],
    ['comfyui', 'ComfyUI', 'Node-based image gen', '🌸'],
    ['automatic1111', 'A1111 WebUI', 'SD web interface', '🌀'],
    ['invokeai', 'InvokeAI', 'Creative AI studio', '✨'],
    ['rembg', 'Remove BG', 'Background removal', '🪄'],
    ['gfpgan', 'GFPGAN', 'Face restoration', '👤'],
    ['codeformer', 'CodeFormer', 'Face enhancement', '🎭'],
    ['realesrgan', 'Real-ESRGAN', 'Image upscale', '📈'],
    ['waifu2x', 'Waifu2x', 'Anime upscale', '🎌'],
    ['topaz-gigapixel', 'Gigapixel AI', 'Photo upscale', '💎'],
    ['meshlab', 'MeshLab', '3D mesh processing', '🧊'],
    ['blender-api', 'Blender API', '3D automation', '🎨'],
    ['three-js', 'Three.js Export', 'Web 3D export', '🌐'],
    ['gltf-transform', 'glTF Transform', '3D format convert', '📦'],
    ['obj2gltf', 'OBJ to glTF', '3D format convert', '🔄'],
    ['draco', 'Draco Compress', '3D mesh compression', '🗜️'],
    ['lyrics-gen', 'Lyrics Generator', 'AI song lyrics', '📝'],
    ['chord-progression', 'Chord AI', 'Chord progressions', '🎹'],
    ['beat-maker', 'Beat Maker', 'Drum pattern gen', '🥁'],
    ['sampler', 'Audio Sampler', 'Sample manipulation', '🎚️'],
    ['midi-export', 'MIDI Export', 'Export to MIDI', '🎼'],
    ['pdf-tools', 'PDF Toolkit', 'PDF merge/split', '📄'],
    ['ocr', 'OCR Scanner', 'Text from images', '👁️'],
    ['translate', 'Deep Translate', '100+ languages', '🌍'],
    ['summarize', 'Doc Summarizer', 'Long doc summary', '📋'],
    ['sentiment', 'Sentiment Analysis', 'Text emotion', '😊'],
    ['ner', 'Entity Extractor', 'Named entities', '🏷️'],
    ['keyword-extract', 'Keyword Extract', 'SEO keywords', '🔑'],
    ['plagiarism', 'Plagiarism Check', 'Content originality', '✅'],
    ['grammar', 'Grammar Check', 'Writing assistant', '✏️'],
    ['code-review', 'Code Review AI', 'Automated review', '🔍'],
    ['test-gen', 'Test Generator', 'Auto unit tests', '🧪'],
    ['doc-gen', 'Doc Generator', 'API documentation', '📖'],
    ['diagram', 'Diagram AI', 'Mermaid/flowcharts', '📊'],
    ['mindmap', 'Mind Map', 'Visual brainstorming', '🧠'],
    ['whiteboard', 'Whiteboard AI', 'Collaborative canvas', '🖊️'],
    ['calendar-sync', 'Calendar Sync', 'Schedule sync', '📅'],
    ['email-draft', 'Email Drafter', 'AI email compose', '✉️'],
    ['slack-bot', 'Slack Bot', 'Team notifications', '💬'],
    ['discord-bot', 'Discord Bot', 'Community bot', '🎮'],
    ['telegram-bot', 'Telegram Bot', 'Messaging bot', '📱'],
    ['webhook-relay', 'Webhook Relay', 'Event forwarding', '🔗'],
    ['cron-runner', 'Cron Runner', 'Scheduled jobs', '⏰'],
    ['queue-worker', 'Queue Worker', 'Background jobs', '📋'],
    ['cache-warmer', 'Cache Warmer', 'Preload caches', '🔥'],
    ['cdn-purge', 'CDN Purge', 'Clear CDN cache', '🌐'],
    ['ssl-check', 'SSL Checker', 'Certificate monitor', '🔒'],
    ['dns-lookup', 'DNS Lookup', 'Domain records', '🌐'],
    ['whois', 'WHOIS Lookup', 'Domain info', '📋'],
    ['speed-test', 'Speed Test', 'Performance audit', '⚡'],
    ['lighthouse', 'Lighthouse', 'Web vitals audit', '💡'],
    ['accessibility', 'A11y Checker', 'WCAG compliance', '♿'],
    ['seo-audit', 'SEO Audit', 'Search optimization', '📈'],
    ['sitemap-gen', 'Sitemap Gen', 'XML sitemap', '🗺️'],
    ['robots-txt', 'Robots.txt', 'Crawler rules', '🤖'],
    ['favicon-gen', 'Favicon Gen', 'Icon generation', '🎯'],
    ['og-image', 'OG Image Gen', 'Social preview cards', '🖼️'],
    ['qr-code', 'QR Generator', 'QR code creation', '📱'],
    ['barcode', 'Barcode Gen', 'Barcode creation', '📊'],
    ['color-palette', 'Color Palette', 'Design colors', '🎨'],
    ['font-pair', 'Font Pairing', 'Typography suggest', '🔤'],
    ['logo-gen', 'Logo Generator', 'AI logo creation', '🏷️'],
    ['mockup', 'Mockup Gen', 'Product mockups', '📱'],
    ['icon-set', 'Icon Set Gen', 'Icon pack creation', '⭐'],
    ['sprite-sheet', 'Sprite Sheet', 'Game sprites', '🎮'],
    ['tilemap', 'Tilemap Gen', 'Game tilemaps', '🗺️'],
    ['particle-fx', 'Particle FX', 'Visual effects', '✨'],
    ['shader-gen', 'Shader Gen', 'GLSL shaders', '🌈'],
    ['texture-gen', 'Texture Gen', 'PBR textures', '🎨'],
    ['normal-map', 'Normal Map', 'Bump map generation', '🗻'],
    ['heightmap', 'Heightmap Gen', 'Terrain generation', '🏔️'],
    ['procedural', 'Procedural Gen', 'Procedural content', '🎲'],
    ['l-system', 'L-System', 'Fractal plants', '🌿'],
    ['voronoi', 'Voronoi Gen', 'Voronoi patterns', '🔷'],
    ['noise-gen', 'Noise Gen', 'Perlin/Simplex noise', '🌊'],
    ['fft-audio', 'FFT Analyzer', 'Audio spectrum', '📊'],
    ['waveform', 'Waveform Viz', 'Audio waveform', '〰️'],
    ['spectrogram', 'Spectrogram', 'Audio heatmap', '🎨'],
    ['pitch-shift', 'Pitch Shift', 'Audio pitch change', '🎵'],
    ['time-stretch', 'Time Stretch', 'Audio tempo', '⏱️'],
    ['noise-reduce', 'Noise Reduce', 'Audio denoise', '🔇'],
    ['normalize', 'Audio Normalize', 'Volume normalize', '📢'],
    ['compress', 'Audio Compress', 'Dynamic range', '🎚️'],
    ['eq', 'Audio EQ', 'Equalizer', '🎛️'],
    ['reverb', 'Reverb FX', 'Spatial audio', '🏛️'],
    ['delay', 'Delay FX', 'Echo effect', '🔁'],
    ['chorus', 'Chorus FX', 'Chorus effect', '🎶'],
    ['distortion', 'Distortion FX', 'Overdrive', '🎸'],
    ['vocoder', 'Vocoder', 'Voice synthesis FX', '🤖'],
    ['autotune', 'Auto-Tune', 'Pitch correction', '🎤'],
    ['harmonizer', 'Harmonizer', 'Vocal harmonies', '🎵'],
    ['metronome', 'Metronome', 'Beat keeping', '⏱️'],
    ['tuner', 'Instrument Tuner', 'Pitch detection', '🎸'],
    ['tab-gen', 'Tab Generator', 'Guitar tabs', '🎸'],
    ['sheet-music', 'Sheet Music', 'Notation export', '🎼'],
  ];
  const base = plugins.map(([id, name, desc, icon]) =>
    tool(`plugin-${id}`, name, desc, 'plugin', icon, 'plugin', id, ['plugin', 'extension'], {
      customInstall: true,
      freeTier: true,
      installGuide: 'ocean plugin install <package> or add to .ocean/plugins/',
    })
  );
  const extra = getExtraPluginEntries().map((e) =>
    tool(e.id, e.name, e.description, 'plugin', e.icon, 'plugin', e.id.replace('plugin-extra-', ''), ['plugin', 'extension', 'extra'], {
      featureTags: e.featureTags,
      customInstall: true,
      freeTier: true,
      installGuide: 'ocean plugin install <package>',
    })
  );
  return [...base, ...extra];
}

export const PLAYGROUND_TOOLS: PlaygroundTool[] = [
  ...BUILTIN_AGENTS,
  ...BUILTIN_RESEARCH,
  ...IMAGE_TOOLS,
  ...VIDEO_TOOLS,
  ...UPSCALE_TOOLS,
  ...EDITOR_TOOLS,
  ...THREE_D_TOOLS,
  ...threeDSourceTools(),
  ...TTS_TOOLS,
  ...MUSIC_TOOLS,
  ...SCHEDULE_TOOLS,
  ...ACTIVE_BOT_BUILTIN,
  ...RECORDING_BUILTIN,
  ...NARRATOR_BUILTIN,
  ...generateMcpTools(),
  ...generatePluginTools(),
];

export const PLAYGROUND_CATEGORIES: { id: PlaygroundToolCategory; label: string; icon: string }[] = [
  { id: 'narrator', label: 'Screen Narrator', icon: '👁️' },
  { id: 'active-bot', label: 'Active Bot', icon: '⚡' },
  { id: 'recording', label: 'Recording', icon: '🎥' },
  { id: 'agents', label: 'Agent Tasks', icon: '🤖' },
  { id: 'research', label: 'Research', icon: '🔬' },
  { id: 'media-image', label: 'Image Gen', icon: '🖼️' },
  { id: 'media-video', label: 'Video Gen', icon: '🎬' },
  { id: 'upscale', label: 'Upscale', icon: '📈' },
  { id: 'editor', label: 'Image Editor', icon: '✏️' },
  { id: 'media-3d', label: '3D Studio', icon: '🧊' },
  { id: 'audio-tts', label: 'Text to Speech', icon: '🔊' },
  { id: 'audio-music', label: 'Music Studio', icon: '🎵' },
  { id: 'schedule', label: 'Schedule', icon: '⏰' },
  { id: 'mcp', label: 'MCP Store', icon: '🔌' },
  { id: 'plugin', label: 'Plugin Store', icon: '🧩' },
  { id: 'skills', label: 'Skills Store', icon: '📚' },
];

export function getToolsByCategory(cat: PlaygroundToolCategory): PlaygroundTool[] {
  return PLAYGROUND_TOOLS.filter((t) => t.category === cat);
}

export function getPlaygroundTool(id: string, customTools: PlaygroundTool[] = []): PlaygroundTool | undefined {
  return [...customTools, ...PLAYGROUND_TOOLS].find((t) => t.id === id);
}

export function getAllPlaygroundTools(customTools: PlaygroundTool[] = []): PlaygroundTool[] {
  return [...customTools, ...PLAYGROUND_TOOLS];
}

export function getMcpCount(): number {
  return PLAYGROUND_TOOLS.filter((t) => t.category === 'mcp' || t.kind === 'mcp').length;
}

export function getPluginCount(): number {
  return PLAYGROUND_TOOLS.filter((t) => t.category === 'plugin' || t.kind === 'plugin').length;
}
