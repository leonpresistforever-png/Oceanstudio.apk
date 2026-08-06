import { useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronRight, Terminal, Plug, Puzzle } from 'lucide-react';
import type { AgentArchetype, AgentMember } from '../../agents/types';
import { ARCHETYPE_DEFINITIONS, createMemberFromArchetype } from '../../agents/categories';
import { suggestForModel } from '../../agents/providerProfiles';
import { PROVIDER_CATALOG } from '../../providers/catalog';

interface SubAgentCreatorProps {
  categoryId: string;
  allowedArchetypes: AgentArchetype[];
  connectedProviders: {
    providerId: string;
    name: string;
    def?: { brandColor?: string; logoLetter?: string; defaultModels?: string[] };
    models: string[];
  }[];
  onCreate: (member: AgentMember) => void;
  onClose: () => void;
}

export default function SubAgentCreator({
  categoryId,
  allowedArchetypes,
  connectedProviders,
  onCreate,
  onClose,
}: SubAgentCreatorProps) {
  const [step, setStep] = useState<'archetype' | 'provider'>('archetype');
  const [selectedArchetype, setSelectedArchetype] = useState<AgentArchetype | null>(null);

  const archetypes = ARCHETYPE_DEFINITIONS.filter(
    (a) => allowedArchetypes.includes(a.archetype) || allowedArchetypes.includes('custom')
  );

  function handleSelectArchetype(archetype: AgentArchetype) {
    setSelectedArchetype(archetype);
    setStep('provider');
  }

  function handleSelectProvider(providerId: string, modelId: string) {
    if (!selectedArchetype) return;
    const def = PROVIDER_CATALOG.find((p) => p.id === providerId);
    const conn = connectedProviders.find((c) => c.providerId === providerId);
    const suggestions = suggestForModel(providerId, modelId);

    const member = createMemberFromArchetype(selectedArchetype, categoryId, {
      providerId,
      providerName: def?.name ?? providerId,
      modelId,
      modelLabel: modelId,
      brandColor: def?.brandColor ?? '#0ea5e9',
      role: suggestions.role ?? undefined,
      taskAssignment: suggestions.taskAssignment,
      tools: (conn as { syncedTools?: { name: string; description: string }[] })?.syncedTools?.map((t) => ({
        id: t.name, name: t.name, description: t.description, source: 'provider' as const,
      })) ?? [],
    });

    if (suggestions.temperature !== undefined) member.params.temperature = suggestions.temperature;
    if (suggestions.thinkingLevel !== undefined) member.params.thinkingLevel = suggestions.thinkingLevel;

    onCreate(member);
    onClose();
  }

  return (
    <div className="mcp-config-overlay" onClick={onClose}>
      <motion.div
        className="mcp-config-modal ma-creator-modal"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
      >
        {step === 'archetype' ? (
          <>
            <h3>Create Sub-Agent</h3>
            <p className="ma-creator-hint">Choose an agent archetype. All agents get terminal, MCP, and plugin access by default.</p>
            <div className="ma-archetype-grid">
              {archetypes.map((a) => (
                <button
                  key={a.archetype}
                  className="ma-archetype-card"
                  style={{ borderColor: a.color }}
                  onClick={() => handleSelectArchetype(a.archetype)}
                >
                  <span className="ma-archetype-icon">{a.icon}</span>
                  <strong>{a.name}</strong>
                  <small>{a.description}</small>
                  <div className="ma-archetype-access">
                    <Terminal size={10} /><Plug size={10} /><Puzzle size={10} />
                  </div>
                </button>
              ))}
            </div>
          </>
        ) : (
          <>
            <button className="ma-creator-back" onClick={() => setStep('archetype')}>← Back</button>
            <h3>Select Provider & Model</h3>
            {connectedProviders.length === 0 ? (
              <p className="ma-creator-hint">Connect providers first to assign a model.</p>
            ) : connectedProviders.map((p) => (
              <div key={p.providerId} className="ma-provider-picker">
                <div className="ma-provider-picker-header">
                  <span style={{ background: p.def?.brandColor, color: '#fff', padding: '4px 8px', borderRadius: 6, fontWeight: 700 }}>
                    {p.def?.logoLetter ?? p.name[0]}
                  </span>
                  <strong>{p.name}</strong>
                </div>
                <div className="ma-model-chips">
                  {p.models.map((modelId) => (
                    <button key={modelId} className="ma-model-chip" onClick={() => handleSelectProvider(p.providerId, modelId)}>
                      {modelId} <ChevronRight size={12} />
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </>
        )}
      </motion.div>
    </div>
  );
}
