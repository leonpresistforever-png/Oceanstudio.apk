import { useState, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  X, Plus, Trash2, Upload, Database, Globe, Webhook, Wrench, ChevronDown,
} from 'lucide-react';
import { useModelStore } from '../../store/modelStore';
import { getPlatformCapabilities } from '../../lib/platformCapabilities';
import type { ModelDefinition, ScrapingProvider, ThinkingLevel, StrictnessLevel } from '../../models/types';
import './ModelConfigPanel.css';

interface Props {
  platformInfo: { isElectron?: boolean; isAndroid?: boolean; hasNativeTerminal?: boolean };
}

export default function ModelConfigPanel({ platformInfo }: Props) {
  const config = useModelStore((s) => s.config);
  const customModels = useModelStore((s) => s.customModels);
  const setConfig = useModelStore((s) => s.setConfig);
  const addCustomModel = useModelStore((s) => s.addCustomModel);
  const removeCustomModel = useModelStore((s) => s.removeCustomModel);
  const addSkill = useModelStore((s) => s.addSkill);
  const removeSkill = useModelStore((s) => s.removeSkill);
  const addWebhook = useModelStore((s) => s.addWebhook);
  const removeWebhook = useModelStore((s) => s.removeWebhook);
  const addCustomFunction = useModelStore((s) => s.addCustomFunction);
  const removeCustomFunction = useModelStore((s) => s.removeCustomFunction);
  const setShowConfigPanel = useModelStore((s) => s.setShowConfigPanel);

  const caps = getPlatformCapabilities(platformInfo);
  const fileRef = useRef<HTMLInputElement>(null);

  const [expanded, setExpanded] = useState<Record<string, boolean>>({
    params: true, instructions: false, custom: false, skills: false, tools: false, scraping: false, db: false,
  });
  const [customName, setCustomName] = useState('');
  const [customEndpoint, setCustomEndpoint] = useState('');
  const [webhookName, setWebhookName] = useState('');
  const [webhookUrl, setWebhookUrl] = useState('');
  const [fnName, setFnName] = useState('');
  const [fnDesc, setFnDesc] = useState('');
  const [fnWebhook, setFnWebhook] = useState('');

  function toggle(section: string) {
    setExpanded((e) => ({ ...e, [section]: !e[section] }));
  }

  function handleAddCustomModel() {
    if (!customName.trim() || !customEndpoint.trim()) return;
    const id = `custom.${crypto.randomUUID().slice(0, 8)}`;
    const model: ModelDefinition = {
      id, name: customName.trim(), provider: 'custom', isCustom: true,
      description: `Custom endpoint: ${customEndpoint}`,
    };
    addCustomModel(model);
    setConfig({ customEndpoint: customEndpoint.trim(), customApiKey: config.customApiKey });
    setCustomName('');
    setCustomEndpoint('');
  }

  async function handleSkillUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const content = await file.text();
    addSkill({
      id: crypto.randomUUID(),
      name: file.name,
      content,
      source: 'upload',
      createdAt: Date.now(),
    });
    e.target.value = '';
  }

  function handleAddWebhook() {
    if (!webhookName.trim() || !webhookUrl.trim()) return;
    addWebhook({
      id: crypto.randomUUID(),
      name: webhookName.trim(),
      url: webhookUrl.trim(),
      events: ['agent.message', 'agent.complete'],
    });
    setWebhookName('');
    setWebhookUrl('');
  }

  function handleAddFunction() {
    if (!fnName.trim()) return;
    addCustomFunction({
      id: crypto.randomUUID(),
      name: fnName.trim(),
      description: fnDesc.trim() || fnName,
      webhookUrl: fnWebhook.trim() || undefined,
    });
    setFnName('');
    setFnDesc('');
    setFnWebhook('');
  }

  return (
    <motion.div
      className="model-config-panel"
      initial={{ height: 0, opacity: 0 }}
      animate={{ height: 'auto', opacity: 1 }}
      exit={{ height: 0, opacity: 0 }}
      transition={{ duration: 0.2 }}
    >
      <div className="model-config-header">
        <span>Model Configuration</span>
        <button onClick={() => setShowConfigPanel(false)}><X size={14} /></button>
      </div>

      <div className="model-config-body">
        {/* Parameters */}
        <ConfigSection title="Parameters" id="params" expanded={expanded.params} onToggle={toggle}>
          <Slider label="Temperature" value={config.temperature} min={0} max={2} step={0.05}
            onChange={(v) => setConfig({ temperature: v })} />
          <Slider label="Top P" value={config.topP} min={0} max={1} step={0.05}
            onChange={(v) => setConfig({ topP: v })} />
          <Slider label="Max Tokens" value={config.maxTokens} min={256} max={128000} step={256}
            onChange={(v) => setConfig({ maxTokens: v })} format={(v) => v.toLocaleString()} />
          <SelectField label="Thinking Level" value={config.thinkingLevel}
            options={[
              { value: 'off', label: 'Off' },
              { value: 'low', label: 'Low' },
              { value: 'medium', label: 'Medium' },
              { value: 'high', label: 'High' },
              { value: 'max', label: 'Maximum' },
            ]}
            onChange={(v) => setConfig({ thinkingLevel: v as ThinkingLevel })} />
          <SelectField label="Strict Rules" value={config.strictness}
            options={[
              { value: 'relaxed', label: 'Relaxed' },
              { value: 'normal', label: 'Normal' },
              { value: 'strict', label: 'Strict' },
              { value: 'maximum', label: 'Maximum' },
            ]}
            onChange={(v) => setConfig({ strictness: v as StrictnessLevel })} />
          <label className="model-check">
            <input type="checkbox" checked={config.toolsEnabled}
              onChange={(e) => setConfig({ toolsEnabled: e.target.checked })} />
            Enable tools &amp; MCP
          </label>
        </ConfigSection>

        {/* Instructions */}
        <ConfigSection title="Instructions" id="instructions" expanded={expanded.instructions} onToggle={toggle}>
          <label className="model-check">
            <input type="checkbox" checked={config.bypassDefaultInstructions}
              onChange={(e) => setConfig({ bypassDefaultInstructions: e.target.checked })} />
            Bypass default system instructions
          </label>
          <textarea
            className="model-textarea"
            placeholder="Custom instructions for the agent..."
            value={config.customInstructions}
            onChange={(e) => setConfig({ customInstructions: e.target.value })}
            rows={3}
          />
        </ConfigSection>

        {/* Custom model endpoint */}
        {caps.customModelEndpoints && (
          <ConfigSection title="Custom Model / Endpoint" id="custom" expanded={expanded.custom} onToggle={toggle}>
            <input className="model-input" placeholder="Model name" value={customName}
              onChange={(e) => setCustomName(e.target.value)} />
            <input className="model-input" placeholder="OpenAI-compatible endpoint URL"
              value={customEndpoint} onChange={(e) => setCustomEndpoint(e.target.value)} />
            <input className="model-input" type="password" placeholder="API key (optional)"
              value={config.customApiKey ?? ''}
              onChange={(e) => setConfig({ customApiKey: e.target.value })} />
            <button className="model-btn" onClick={handleAddCustomModel}>
              <Plus size={12} /> Add Custom Model
            </button>
            {customModels.map((m) => (
              <div key={m.id} className="model-list-item">
                <span>{m.name}</span>
                <button onClick={() => removeCustomModel(m.id)}><Trash2 size={12} /></button>
              </div>
            ))}
          </ConfigSection>
        )}

        {/* Skills */}
        <ConfigSection title="Skills" id="skills" expanded={expanded.skills} onToggle={toggle}>
          {caps.skillFileUpload && (
            <>
              <input ref={fileRef} type="file" accept=".md,.txt,.json" hidden
                onChange={handleSkillUpload} />
              <button className="model-btn" onClick={() => fileRef.current?.click()}>
                <Upload size={12} /> Upload Skill File
              </button>
            </>
          )}
          {caps.agentSkillCreation && (
            <button className="model-btn secondary" onClick={() => {
              addSkill({
                id: crypto.randomUUID(),
                name: `agent-skill-${Date.now()}`,
                content: '# Agent-created skill\n\nThe agent can write skills here during sessions.',
                source: 'agent',
                createdAt: Date.now(),
              });
            }}>
              <Plus size={12} /> Let Agent Create Skill
            </button>
          )}
          {config.skills.map((s) => (
            <div key={s.id} className="model-list-item">
              <span>{s.name} <em className="model-source">({s.source})</em></span>
              <button onClick={() => removeSkill(s.id)}><Trash2 size={12} /></button>
            </div>
          ))}
        </ConfigSection>

        {/* Webhooks & Functions */}
        <ConfigSection title="Webhooks & Functions" id="tools" expanded={expanded.tools} onToggle={toggle}>
          <input className="model-input" placeholder="Webhook name" value={webhookName}
            onChange={(e) => setWebhookName(e.target.value)} />
          <input className="model-input" placeholder="Webhook URL" value={webhookUrl}
            onChange={(e) => setWebhookUrl(e.target.value)} />
          <button className="model-btn" onClick={handleAddWebhook}>
            <Webhook size={12} /> Add Webhook
          </button>
          {config.webhooks.map((w) => (
            <div key={w.id} className="model-list-item">
              <span>{w.name}</span>
              <button onClick={() => removeWebhook(w.id)}><Trash2 size={12} /></button>
            </div>
          ))}
          <hr className="model-divider" />
          <input className="model-input" placeholder="Function name" value={fnName}
            onChange={(e) => setFnName(e.target.value)} />
          <input className="model-input" placeholder="Description" value={fnDesc}
            onChange={(e) => setFnDesc(e.target.value)} />
          <input className="model-input" placeholder="Webhook URL (optional)" value={fnWebhook}
            onChange={(e) => setFnWebhook(e.target.value)} />
          <button className="model-btn" onClick={handleAddFunction}>
            <Wrench size={12} /> Add Custom Function
          </button>
          {config.customFunctions.map((f) => (
            <div key={f.id} className="model-list-item">
              <span>{f.name}</span>
              <button onClick={() => removeCustomFunction(f.id)}><Trash2 size={12} /></button>
            </div>
          ))}
        </ConfigSection>

        {/* Scraping */}
        <ConfigSection title="Web Scraping" id="scraping" expanded={expanded.scraping} onToggle={toggle}>
          <SelectField label="Provider" value={config.scrapingProvider}
            options={caps.scrapingProviders.map((p) => ({
              value: p,
              label: p === 'firecrawl' ? 'Firecrawl (cloud)' :
                p === 'puppeteer' ? 'Puppeteer (desktop)' :
                p === 'playwright' ? 'Playwright (desktop)' :
                p === 'fetch' ? 'HTTP Fetch' : 'Custom',
            }))}
            onChange={(v) => setConfig({ scrapingProvider: v as ScrapingProvider })} />
          {config.scrapingProvider === 'custom' && (
            <>
              <input className="model-input" placeholder="Custom scraping endpoint"
                value={config.customScrapingEndpoint ?? ''}
                onChange={(e) => setConfig({ customScrapingEndpoint: e.target.value })} />
              <input className="model-input" type="password" placeholder="API key"
                value={config.customScrapingApiKey ?? ''}
                onChange={(e) => setConfig({ customScrapingApiKey: e.target.value })} />
            </>
          )}
          <p className="model-hint">
            <Globe size={12} /> {caps.platform}: {caps.scrapingProviders.join(', ')} available
          </p>
        </ConfigSection>

        {/* PostgreSQL */}
        {caps.postgresDirect && (
          <ConfigSection title="PostgreSQL" id="db" expanded={expanded.db} onToggle={toggle}>
            <input className="model-input" type="password"
              placeholder="postgresql://user:pass@host:5432/db"
              value={config.postgresConnectionString ?? ''}
              onChange={(e) => setConfig({ postgresConnectionString: e.target.value })} />
            <p className="model-hint"><Database size={12} /> Agent memory &amp; RAG via PostgreSQL</p>
          </ConfigSection>
        )}
      </div>
    </motion.div>
  );
}

function ConfigSection({ title, id, expanded, onToggle, children }: {
  title: string; id: string; expanded: boolean;
  onToggle: (id: string) => void; children: React.ReactNode;
}) {
  return (
    <div className="model-config-section">
      <button className="model-section-header" onClick={() => onToggle(id)}>
        <span>{title}</span>
        <ChevronDown size={14} className={expanded ? 'open' : ''} />
      </button>
      <AnimatePresence>
        {expanded && (
          <motion.div className="model-section-body"
            initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}>
            {children}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function Slider({ label, value, min, max, step, onChange, format }: {
  label: string; value: number; min: number; max: number; step: number;
  onChange: (v: number) => void; format?: (v: number) => string;
}) {
  return (
    <div className="model-slider">
      <div className="model-slider-header">
        <span>{label}</span>
        <span className="model-slider-value">{format ? format(value) : value.toFixed(2)}</span>
      </div>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))} />
    </div>
  );
}

function SelectField({ label, value, options, onChange }: {
  label: string; value: string;
  options: { value: string; label: string }[];
  onChange: (v: string) => void;
}) {
  return (
    <label className="model-select-field">
      <span>{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </label>
  );
}
