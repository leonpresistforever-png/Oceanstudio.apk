import { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, Sparkles, Minimize2, Wand2, Volume2, Mic } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { usePromptEnhancerStore } from '../store/promptEnhancerStore';
import {
  compressPrompt, enhancePromptLocally, estimateTokens,
} from '../lib/promptProcessor';
import { getBrowserVoices } from '../lib/tts';
import type { CompressLevel, EnhanceStyle } from '../prompt/types';
import './PromptEnhancerPage.css';

const STYLES: { id: EnhanceStyle; label: string }[] = [
  { id: 'balanced', label: 'Balanced' },
  { id: 'detailed', label: 'Detailed' },
  { id: 'concise', label: 'Concise' },
  { id: 'technical', label: 'Technical' },
  { id: 'creative', label: 'Creative' },
];

const COMPRESS: { id: CompressLevel; label: string }[] = [
  { id: 'off', label: 'Off' },
  { id: 'light', label: 'Light' },
  { id: 'medium', label: 'Medium' },
  { id: 'aggressive', label: 'Aggressive' },
];

export default function PromptEnhancerPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const config = usePromptEnhancerStore((s) => s.config);
  const setConfig = usePromptEnhancerStore((s) => s.setConfig);

  const [sample, setSample] = useState('fix the login bug and make the ui look better');
  const [preview, setPreview] = useState('');
  const [saved, setSaved] = useState(false);
  const voices = getBrowserVoices();

  function flash() {
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

  function runPreview() {
    let out = sample;
    if (config.autoEnhance) {
      out = enhancePromptLocally(out, config.enhanceStyle);
    }
    if (config.autoCompress) {
      out = compressPrompt(out, config.compressLevel);
    }
    setPreview(out);
  }

  const before = estimateTokens(sample);
  const after = estimateTokens(preview || sample);

  return (
    <div className="pe-page">
      <header className="pe-header">
        <button type="button" className="pe-back" onClick={() => setCenterView('editor')}>
          <ArrowLeft size={14} /> Back
        </button>
        <h1><Sparkles size={20} /> Prompt Enhancer</h1>
        {saved && <span className="pe-saved">Saved</span>}
      </header>

      <div className="pe-hero">
        <Wand2 size={24} />
        <div>
          <h2>Enhance &amp; compress prompts</h2>
          <p>Save quota with automatic compression. Optionally enhance clarity before the agent runs.</p>
        </div>
      </div>

      <div className="pe-grid">
        <section className="pe-card">
          <h3>Automation</h3>
          <label className="pe-toggle">
            <input
              type="checkbox"
              checked={config.autoEnhance}
              onChange={(e) => { setConfig({ autoEnhance: e.target.checked }); flash(); }}
            />
            <span>Auto-enhance prompts before send</span>
          </label>
          <label className="pe-toggle">
            <input
              type="checkbox"
              checked={config.autoCompress}
              onChange={(e) => { setConfig({ autoCompress: e.target.checked }); flash(); }}
            />
            <span>Auto-compress to save tokens</span>
          </label>

          <h4>Enhance style</h4>
          <div className="pe-chips">
            {STYLES.map((s) => (
              <button
                key={s.id}
                type="button"
                className={config.enhanceStyle === s.id ? 'active' : ''}
                onClick={() => { setConfig({ enhanceStyle: s.id }); flash(); }}
              >
                {s.label}
              </button>
            ))}
          </div>

          <h4>Compress level</h4>
          <div className="pe-chips">
            {COMPRESS.map((c) => (
              <button
                key={c.id}
                type="button"
                className={config.compressLevel === c.id ? 'active' : ''}
                onClick={() => { setConfig({ compressLevel: c.id }); flash(); }}
              >
                {c.label}
              </button>
            ))}
          </div>

          <label className="pe-field">
            Max input tokens
            <input
              type="range"
              min={500}
              max={16000}
              step={500}
              value={config.maxInputTokens}
              onChange={(e) => { setConfig({ maxInputTokens: Number(e.target.value) }); flash(); }}
            />
            <span>{config.maxInputTokens}</span>
          </label>

          <label className="pe-field">
            Custom enhance instruction
            <textarea
              rows={3}
              placeholder="Optional system prompt for AI enhance..."
              value={config.customEnhancePrompt}
              onChange={(e) => setConfig({ customEnhancePrompt: e.target.value })}
              onBlur={flash}
            />
          </label>
        </section>

        <section className="pe-card">
          <h3><Volume2 size={16} /> Text-to-Speech</h3>
          <label className="pe-field">
            Voice
            <select
              value={config.ttsVoiceUri}
              onChange={(e) => { setConfig({ ttsVoiceUri: e.target.value }); flash(); }}
            >
              <option value="">System default</option>
              {voices.map((v) => (
                <option key={v.voiceURI} value={v.voiceURI}>{v.name} ({v.lang})</option>
              ))}
            </select>
          </label>
          <label className="pe-field">
            Rate: {config.ttsRate.toFixed(1)}
            <input
              type="range" min={0.5} max={2} step={0.1}
              value={config.ttsRate}
              onChange={(e) => { setConfig({ ttsRate: Number(e.target.value) }); flash(); }}
            />
          </label>
          <label className="pe-toggle">
            <input
              type="checkbox"
              checked={config.ttsStripMarkdown}
              onChange={(e) => { setConfig({ ttsStripMarkdown: e.target.checked }); flash(); }}
            />
            <span>Strip markdown when speaking</span>
          </label>

          <h3><Mic size={16} /> Speech-to-Text</h3>
          <label className="pe-field">
            Language
            <select
              value={config.sttLanguage}
              onChange={(e) => { setConfig({ sttLanguage: e.target.value }); flash(); }}
            >
              <option value="en-US">English (US)</option>
              <option value="en-GB">English (UK)</option>
              <option value="es-ES">Spanish</option>
              <option value="fr-FR">French</option>
              <option value="de-DE">German</option>
              <option value="ja-JP">Japanese</option>
              <option value="zh-CN">Chinese</option>
            </select>
          </label>
        </section>
      </div>

      <section className="pe-preview">
        <h3><Minimize2 size={16} /> Live preview</h3>
        <textarea
          className="pe-sample"
          value={sample}
          onChange={(e) => setSample(e.target.value)}
          rows={3}
        />
        <button type="button" className="pe-btn" onClick={runPreview}>Preview transform</button>
        {preview && (
          <motion.div className="pe-result" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
            <pre>{preview}</pre>
            <p className="pe-tokens">
              ~{before} → ~{after} tokens
              {before > after && <span className="saved"> (saved {before - after})</span>}
            </p>
          </motion.div>
        )}
      </section>
    </div>
  );
}
