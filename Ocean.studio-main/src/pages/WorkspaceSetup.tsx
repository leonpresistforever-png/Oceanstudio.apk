import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAppStore, type UITemplate, type Language, type AgentMode } from '../store/appStore';
import { getOceanAPI } from '../lib/platform';
import './WorkspaceSetup.css';

const TEMPLATES: { id: UITemplate; name: string; desc: string }[] = [
  { id: 'minimal', name: 'Minimal', desc: 'Clean white surfaces, subtle shadows' },
  { id: 'classic', name: 'Classic', desc: 'Balanced contrast, familiar IDE feel' },
  { id: 'contrast', name: 'Contrast', desc: 'Sharp black and white definition' },
  { id: 'warm', name: 'Warm', desc: 'Soft cream tones with gentle warmth' },
];

const LANGUAGES: { id: Language; name: string }[] = [
  { id: 'en', name: 'English' },
  { id: 'es', name: 'Espanol' },
  { id: 'fr', name: 'Francais' },
  { id: 'de', name: 'Deutsch' },
  { id: 'ja', name: 'Japanese' },
  { id: 'zh', name: 'Chinese' },
];

const AGENT_MODES: { id: AgentMode; name: string; desc: string }[] = [
  { id: 'review', name: 'Review-driven', desc: 'Every action requires your approval before execution' },
  { id: 'auto', name: 'Auto agent', desc: 'Executes autonomously but pauses on critical operations' },
  { id: 'bypass', name: 'Bypass', desc: 'Full independent execution without review gates' },
];

const STEPS = ['Template', 'Language', 'Agent Mode', 'Review'];

export default function WorkspaceSetup() {
  const navigate = useNavigate();
  const setWorkspaceConfig = useAppStore((s) => s.setWorkspaceConfig);
  const [step, setStep] = useState(0);
  const [template, setTemplate] = useState<UITemplate>('minimal');
  const [language, setLanguage] = useState<Language>('en');
  const [agentMode, setAgentMode] = useState<AgentMode>('review');
  const [launching, setLaunching] = useState(false);

  async function handleLaunch() {
    setLaunching(true);
    const api = getOceanAPI();
    let workspacePath = await api.fs.getDefaultWorkspace();
    const selected = await api.fs.selectWorkspace();
    if (selected) workspacePath = selected;

    setWorkspaceConfig({ template, language, agentMode, workspacePath });
    document.documentElement.setAttribute('data-template', template);
    navigate('/workspace');
  }

  return (
    <div className="setup-page">
      <div className="setup-container">
        <div className="setup-header">
          <h1>Configure your workspace</h1>
          <p>Set up Ocean.studio before you start coding</p>
        </div>

        <div className="setup-progress">
          {STEPS.map((_, i) => (
            <div
              key={i}
              className={`setup-progress-dot ${i === step ? 'active' : ''} ${i < step ? 'done' : ''}`}
            />
          ))}
        </div>

        <AnimatePresence mode="wait">
          <motion.div
            key={step}
            className="setup-step"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.25 }}
          >
            {step === 0 && (
              <>
                <h2>Choose a template</h2>
                <p>Select the visual style for your workspace</p>
                <div className="setup-options">
                  {TEMPLATES.map((t) => (
                    <button
                      key={t.id}
                      className={`setup-option ${template === t.id ? 'selected' : ''}`}
                      onClick={() => setTemplate(t.id)}
                    >
                      <h3>{t.name}</h3>
                      <p>{t.desc}</p>
                    </button>
                  ))}
                </div>
              </>
            )}

            {step === 1 && (
              <>
                <h2>Select language</h2>
                <p>Interface language for your workspace</p>
                <div className="setup-options">
                  {LANGUAGES.map((l) => (
                    <button
                      key={l.id}
                      className={`setup-option ${language === l.id ? 'selected' : ''}`}
                      onClick={() => setLanguage(l.id)}
                    >
                      <h3>{l.name}</h3>
                    </button>
                  ))}
                </div>
              </>
            )}

            {step === 2 && (
              <>
                <h2>Agent mode</h2>
                <p>How should the coding agent behave?</p>
                <div className="setup-options" style={{ gridTemplateColumns: '1fr' }}>
                  {AGENT_MODES.map((m) => (
                    <button
                      key={m.id}
                      className={`setup-option ${agentMode === m.id ? 'selected' : ''}`}
                      onClick={() => setAgentMode(m.id)}
                    >
                      <h3>{m.name}</h3>
                      <p>{m.desc}</p>
                    </button>
                  ))}
                </div>
              </>
            )}

            {step === 3 && (
              <>
                <h2>Review configuration</h2>
                <p>Confirm your workspace settings</p>
                <div className="setup-review">
                  <div className="setup-review-item">
                    <span>Template</span>
                    <span>{TEMPLATES.find((t) => t.id === template)?.name}</span>
                  </div>
                  <div className="setup-review-item">
                    <span>Language</span>
                    <span>{LANGUAGES.find((l) => l.id === language)?.name}</span>
                  </div>
                  <div className="setup-review-item">
                    <span>Agent mode</span>
                    <span>{AGENT_MODES.find((m) => m.id === agentMode)?.name}</span>
                  </div>
                </div>
              </>
            )}

            <div className="setup-nav">
              <button
                className="setup-btn setup-btn-back"
                onClick={() => (step > 0 ? setStep(step - 1) : navigate('/'))}
              >
                {step === 0 ? 'Back to home' : 'Back'}
              </button>
              {step < STEPS.length - 1 ? (
                <button className="setup-btn setup-btn-next" onClick={() => setStep(step + 1)}>
                  Continue
                </button>
              ) : (
                <button className="setup-btn setup-btn-next" onClick={handleLaunch} disabled={launching}>
                  {launching ? 'Launching...' : 'Launch Workspace'}
                </button>
              )}
            </div>
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  );
}
