import { Bot, Code2, FolderOpen, Terminal, LayoutGrid } from 'lucide-react';
import './MobileWorkspaceNav.css';

export type MobilePanel = 'agent' | 'editor' | 'files' | 'terminal' | 'tools';

interface Props {
  active: MobilePanel;
  onChange: (panel: MobilePanel) => void;
}

const TABS: { id: MobilePanel; label: string; icon: typeof Bot }[] = [
  { id: 'agent', label: 'Agent', icon: Bot },
  { id: 'editor', label: 'Editor', icon: Code2 },
  { id: 'terminal', label: 'Terminal', icon: Terminal },
  { id: 'files', label: 'Files', icon: FolderOpen },
  { id: 'tools', label: 'Tools', icon: LayoutGrid },
];

export default function MobileWorkspaceNav({ active, onChange }: Props) {
  return (
    <nav className="mobile-workspace-nav" aria-label="Workspace panels">
      {TABS.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          type="button"
          className={`mobile-nav-tab ${active === id ? 'active' : ''}`}
          onClick={() => onChange(id)}
          aria-current={active === id ? 'page' : undefined}
        >
          <Icon size={20} strokeWidth={active === id ? 2.25 : 1.75} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}
