import { Plus, Trash2 } from 'lucide-react';
import type { AgentCategory, AgentArchetype, AgentMember } from '../../agents/types';
import { ARCHETYPE_DEFINITIONS } from '../../agents/categories';

interface CategoryPanelProps {
  categories: AgentCategory[];
  activeCategoryId: string | null;
  members: AgentMember[];
  onSelectCategory: (id: string) => void;
  onAddCategory: () => void;
  onRemoveCategory: (id: string) => void;
  onAddSubAgent: (categoryId: string) => void;
  onSelectMember: (id: string) => void;
  selectedMemberId: string | null;
}

export default function CategoryPanel({
  categories,
  activeCategoryId,
  members,
  onSelectCategory,
  onAddCategory,
  onRemoveCategory,
  onAddSubAgent,
  onSelectMember,
  selectedMemberId,
}: CategoryPanelProps) {
  const activeCategory = categories.find((c) => c.id === activeCategoryId) ?? categories[0];
  const categoryMembers = members.filter((m) => m.categoryId === activeCategory?.id);

  return (
    <aside className="ma-category-panel">
      <div className="ma-section-header">
        <span>Categories</span>
        <button className="ma-icon-btn" onClick={onAddCategory} title="Create category"><Plus size={14} /></button>
      </div>

      <div className="ma-category-list">
        {categories.map((cat) => (
          <button
            key={cat.id}
            className={`ma-category-item ${activeCategory?.id === cat.id ? 'active' : ''}`}
            onClick={() => onSelectCategory(cat.id)}
            style={{ borderLeftColor: cat.color }}
          >
            <span className="ma-category-icon">{cat.icon}</span>
            <div>
              <strong>{cat.name}</strong>
              <small>{members.filter((m) => m.categoryId === cat.id).length} agents</small>
            </div>
            {!cat.builtIn && (
              <button
                className="ma-category-delete"
                onClick={(e) => { e.stopPropagation(); onRemoveCategory(cat.id); }}
              >
                <Trash2 size={12} />
              </button>
            )}
          </button>
        ))}
      </div>

      {activeCategory && (
        <div className="ma-category-agents">
          <div className="ma-section-header">
            <span>{activeCategory.name} Agents</span>
            <button className="ma-icon-btn" onClick={() => onAddSubAgent(activeCategory.id)} title="Add sub-agent">
              <Plus size={14} />
            </button>
          </div>

          {categoryMembers.length === 0 ? (
            <p className="ma-category-empty">
              No agents yet. Click + to add {activeCategory.archetypes.map((a) =>
                ARCHETYPE_DEFINITIONS.find((d) => d.archetype === a)?.name ?? a
              ).join(', ')} agents.
            </p>
          ) : (
            categoryMembers.map((m) => {
              const arch = ARCHETYPE_DEFINITIONS.find((d) => d.archetype === m.archetype);
              return (
                <button
                  key={m.id}
                  className={`ma-category-agent ${selectedMemberId === m.id ? 'selected' : ''} ${!m.enabled ? 'disabled' : ''}`}
                  onClick={() => onSelectMember(m.id)}
                >
                  <span className="ma-category-agent-icon">{arch?.icon ?? '🤖'}</span>
                  <div>
                    <strong>{m.name}</strong>
                    <small>{arch?.name ?? m.archetype} · {m.modelLabel}</small>
                  </div>
                  <span className="ma-category-agent-dot" style={{ background: m.brandColor }} />
                </button>
              );
            })
          )}
        </div>
      )}
    </aside>
  );
}
