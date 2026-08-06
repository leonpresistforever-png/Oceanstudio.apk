import { PLAYGROUND_CATEGORIES, getAllPlaygroundTools } from '../../playground/catalog';
import type { PlaygroundToolCategory } from '../../playground/types';
import { usePlaygroundStore } from '../../store/playgroundStore';

interface Props {
  activeCategory: PlaygroundToolCategory;
  activeToolId: string | null;
  searchQuery: string;
  onCategory: (cat: PlaygroundToolCategory) => void;
  onTool: (id: string) => void;
  onSearch: (q: string) => void;
}

export default function PlaygroundToolSidebar({
  activeCategory,
  activeToolId,
  searchQuery,
  onCategory,
  onTool,
  onSearch,
}: Props) {
  const customTools = usePlaygroundStore((s) => s.customMarketplaceTools);
  const allTools = getAllPlaygroundTools(customTools);
  const q = searchQuery.toLowerCase();
  const tools = allTools.filter((t) => {
    if (t.category !== activeCategory) return false;
    if (!q) return true;
    return t.name.toLowerCase().includes(q) || t.description.toLowerCase().includes(q) || t.tags.some((tag) => tag.includes(q));
  });

  return (
    <aside className="pg-tool-sidebar">
      <div className="pg-sidebar-header">
        <h3>Tools</h3>
        <span className="pg-tool-count">{allTools.length}</span>
      </div>
      <input
        className="pg-search"
        placeholder="Search tools..."
        value={searchQuery}
        onChange={(e) => onSearch(e.target.value)}
      />
      <nav className="pg-categories">
        {PLAYGROUND_CATEGORIES.map((cat) => (
          <button
            key={cat.id}
            className={`pg-cat-btn ${activeCategory === cat.id ? 'active' : ''}`}
            onClick={() => onCategory(cat.id)}
          >
            <span>{cat.icon}</span>
            <span>{cat.label}</span>
          </button>
        ))}
      </nav>
      <div className="pg-tool-list">
        {tools.length === 0 && (
          <p className="pg-tool-empty">No tools match your search.</p>
        )}
        {tools.slice(0, 120).map((t) => (
          <button
            key={t.id}
            className={`pg-tool-item ${activeToolId === t.id ? 'active' : ''}`}
            onClick={() => onTool(t.id)}
          >
            <span className="pg-tool-icon">{t.icon}</span>
            <div>
              <strong>{t.name}</strong>
              <small>{t.description.slice(0, 60)}...</small>
            </div>
            <span className={`pg-tool-kind ${t.kind}`}>{t.kind}</span>
          </button>
        ))}
        {tools.length > 120 && <p className="pg-tool-more">+{tools.length - 120} more — refine search</p>}
      </div>
    </aside>
  );
}
