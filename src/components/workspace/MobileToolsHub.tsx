import {
  Layers, Shield, Plug, GitMerge, Cpu, Box, Workflow, Sparkles,
  Share2, Users, Puzzle, BookOpen, Webhook, Gamepad2, Mic, Film,
} from 'lucide-react';
import { useAppStore } from '../../store/appStore';
import './MobileToolsHub.css';

interface ToolItem {
  id: string;
  label: string;
  description: string;
  icon: typeof Layers;
  view: 'editor' | 'codebase' | 'preview' | 'plugins' | 'mcp' | 'providers' | 'connections' | 'multiagent' | 'playground' | 'profile' | 'skills' | 'extensions' | 'fallback' | 'prompt-enhancer' | 'workflows' | 'integrations' | 'fusion' | 'studio' | 'virtual-apps';
  accent?: string;
}

const TOOLS: ToolItem[] = [
  { id: 'playground', label: 'Playground', description: 'AI media, bots & automation', icon: Gamepad2, view: 'playground', accent: '#8B5CF6' },
  { id: 'multiagent', label: 'Multi Agent', description: 'Team orchestration', icon: Users, view: 'multiagent', accent: '#0EA5E9' },
  { id: 'providers', label: 'Providers', description: 'AI model connections', icon: Layers, view: 'providers', accent: '#10B981' },
  { id: 'integrations', label: 'Integrations', description: 'Services & APIs', icon: Plug, view: 'integrations', accent: '#F59E0B' },
  { id: 'mcp', label: 'MCP Connectors', description: 'Cloud tool bridges', icon: Webhook, view: 'mcp', accent: '#6366F1' },
  { id: 'skills', label: 'Skills Store', description: 'Agent capabilities', icon: BookOpen, view: 'skills', accent: '#EC4899' },
  { id: 'extensions', label: 'Extensions', description: 'Marketplace add-ons', icon: Box, view: 'extensions', accent: '#14B8A6' },
  { id: 'plugins', label: 'Plugins', description: 'Native dex plugins', icon: Puzzle, view: 'plugins', accent: '#F97316' },
  { id: 'fusion', label: 'Agent Fusion', description: 'Merge agent profiles', icon: GitMerge, view: 'fusion', accent: '#A855F7' },
  { id: 'studio', label: 'Agent Studio', description: 'Build custom agents', icon: Cpu, view: 'studio', accent: '#3B82F6' },
  { id: 'workflows', label: 'Workflows', description: 'Cursor-style flows', icon: Workflow, view: 'workflows', accent: '#64748B' },
  { id: 'connections', label: 'Connections', description: 'Provider canvas', icon: Share2, view: 'connections', accent: '#06B6D4' },
  { id: 'fallback', label: 'Fallback Models', description: 'Resilience routing', icon: Shield, view: 'fallback', accent: '#78716C' },
  { id: 'prompt', label: 'Prompt Enhancer', description: 'Refine instructions', icon: Sparkles, view: 'prompt-enhancer', accent: '#D946EF' },
  { id: 'virtual', label: 'Virtual Apps', description: 'App-in-app space', icon: Box, view: 'virtual-apps', accent: '#84CC16' },
];

const PLAYGROUND_SHORTCUTS = [
  { label: 'Screen Narrator', icon: Mic, view: 'playground' as const },
  { label: 'Recording', icon: Film, view: 'playground' as const },
];

interface Props {
  onSelect?: () => void;
}

export default function MobileToolsHub({ onSelect }: Props) {
  const setCenterView = useAppStore((s) => s.setCenterView);

  function openTool(view: ToolItem['view']) {
    setCenterView(view);
    onSelect?.();
  }

  return (
    <div className="mobile-tools-hub">
      <header className="mobile-tools-header">
        <h2>Tools & Playground</h2>
        <p>All Ocean.studio features — optimized for mobile</p>
      </header>

      <section className="mobile-tools-section">
        <h3>Quick access</h3>
        <div className="mobile-tools-quick">
          {PLAYGROUND_SHORTCUTS.map(({ label, icon: Icon }) => (
            <button
              key={label}
              type="button"
              className="mobile-tools-quick-btn"
              onClick={() => openTool('playground')}
            >
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </div>
      </section>

      <section className="mobile-tools-section">
        <h3>All features</h3>
        <div className="mobile-tools-grid">
          {TOOLS.map(({ id, label, description, icon: Icon, view, accent }) => (
            <button
              key={id}
              type="button"
              className="mobile-tools-card"
              onClick={() => openTool(view)}
              style={{ '--tool-accent': accent } as React.CSSProperties}
            >
              <div className="mobile-tools-card-icon">
                <Icon size={20} strokeWidth={1.75} />
              </div>
              <div className="mobile-tools-card-text">
                <span className="mobile-tools-card-label">{label}</span>
                <span className="mobile-tools-card-desc">{description}</span>
              </div>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}
