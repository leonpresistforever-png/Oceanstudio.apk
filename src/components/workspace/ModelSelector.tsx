import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, Settings2, Sparkles } from 'lucide-react';
import { useModelStore } from '../../store/modelStore';
import {
  GOOGLE_MODELS, ANTHROPIC_MODELS, OPENAI_MODELS, PROVIDER_LABELS, getModelById,
} from '../../models/catalog';
import type { ModelProvider } from '../../models/types';
import './ModelSelector.css';

const PROVIDER_GROUPS: { provider: ModelProvider; models: typeof GOOGLE_MODELS }[] = [
  { provider: 'google', models: GOOGLE_MODELS },
  { provider: 'anthropic', models: ANTHROPIC_MODELS },
  { provider: 'openai', models: OPENAI_MODELS },
];

export default function ModelSelector() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const selectedModelId = useModelStore((s) => s.config.selectedModelId);
  const customModels = useModelStore((s) => s.customModels);
  const setSelectedModel = useModelStore((s) => s.setSelectedModel);
  const setShowConfigPanel = useModelStore((s) => s.setShowConfigPanel);
  const showConfigPanel = useModelStore((s) => s.showConfigPanel);

  const current = getModelById(selectedModelId, customModels);
  const provider = current?.provider ?? (selectedModelId.split('.')[0] as ModelProvider);

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  return (
    <div className="model-selector" ref={ref}>
      <button className="model-selector-trigger" onClick={() => setOpen(!open)}>
        <Sparkles size={14} className={`model-provider-icon model-provider-${provider}`} />
        <span className="model-selector-name">{current?.name ?? selectedModelId}</span>
        <ChevronDown size={14} className={`model-chevron ${open ? 'open' : ''}`} />
      </button>
      <button
        className={`model-config-toggle ${showConfigPanel ? 'active' : ''}`}
        onClick={() => setShowConfigPanel(!showConfigPanel)}
        title="Model configuration"
      >
        <Settings2 size={14} />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            className="model-dropdown"
            initial={{ opacity: 0, y: -8, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.98 }}
            transition={{ duration: 0.15 }}
          >
            {PROVIDER_GROUPS.map(({ provider: p, models }) => (
              <div key={p} className="model-group">
                <div className="model-group-label">{PROVIDER_LABELS[p]}</div>
                {models.map((m) => (
                  <button
                    key={m.id}
                    className={`model-option ${m.id === selectedModelId ? 'selected' : ''}`}
                    onClick={() => { setSelectedModel(m.id); setOpen(false); }}
                  >
                    <span>{m.name}</span>
                    {m.supportsThinking && <span className="model-tag">thinking</span>}
                  </button>
                ))}
              </div>
            ))}
            {customModels.length > 0 && (
              <div className="model-group">
                <div className="model-group-label">Custom Models</div>
                {customModels.map((m) => (
                  <button
                    key={m.id}
                    className={`model-option ${m.id === selectedModelId ? 'selected' : ''}`}
                    onClick={() => { setSelectedModel(m.id); setOpen(false); }}
                  >
                    <span>{m.name}</span>
                    <span className="model-tag custom">custom</span>
                  </button>
                ))}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
