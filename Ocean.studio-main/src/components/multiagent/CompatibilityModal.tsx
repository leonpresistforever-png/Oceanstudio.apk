import { motion } from 'framer-motion';
import type { AgentMember, CompatibilityMode } from '../../agents/types';
import { COMPATIBILITY_OPTIONS } from '../../agents/types';

interface CompatibilityModalProps {
  member: AgentMember;
  onUpdate: (patch: Partial<AgentMember>) => void;
  onClose: () => void;
}

export default function CompatibilityModal({ member, onUpdate, onClose }: CompatibilityModalProps) {
  const filters = member.paramFilters;

  function toggleBlocked(param: string) {
    const blocked = filters.blockedParams.includes(param)
      ? filters.blockedParams.filter((p) => p !== param)
      : [...filters.blockedParams, param];
    onUpdate({ paramFilters: { ...filters, blockedParams: blocked } });
  }

  const commonParams = ['thinking', 'reasoning_budget', 'reasoning', 'top_p', 'frequency_penalty', 'presence_penalty'];

  return (
    <div className="mcp-config-overlay" onClick={onClose}>
      <motion.div
        className="mcp-config-modal ma-compat-modal"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
      >
        <h3>API Compatibility</h3>
        <p className="ma-compat-desc">
          Select how requests are formatted for <strong>{member.name}</strong>. Prevents 400 errors from unsupported parameters.
        </p>

        <section className="ma-compat-section">
          <h4>Compatibility Mode</h4>
          <div className="ma-compat-options">
            {COMPATIBILITY_OPTIONS.map((opt) => (
              <label key={opt.value} className={`ma-compat-option ${member.compatibility === opt.value ? 'selected' : ''}`}>
                <input
                  type="radio"
                  name="compatibility"
                  checked={member.compatibility === opt.value}
                  onChange={() => onUpdate({ compatibility: opt.value as CompatibilityMode })}
                />
                <div>
                  <strong>{opt.label}</strong>
                  <span>{opt.description}</span>
                </div>
              </label>
            ))}
          </div>
        </section>

        <section className="ma-compat-section">
          <h4>Param Filters</h4>
          <p className="ma-compat-hint">Strip or re-add request parameters before sending to this provider.</p>

          <label className="mcp-config-field">
            <span>Blocked parameters</span>
            <input
              type="text"
              placeholder="thinking, reasoning_budget"
              value={filters.blockedParams.join(', ')}
              onChange={(e) => onUpdate({
                paramFilters: {
                  ...filters,
                  blockedParams: e.target.value.split(',').map((s) => s.trim()).filter(Boolean),
                },
              })}
            />
          </label>

          <label className="mcp-config-field">
            <span>Allowed parameters</span>
            <input
              type="text"
              placeholder="reasoning, temperature"
              value={filters.allowedParams.join(', ')}
              onChange={(e) => onUpdate({
                paramFilters: {
                  ...filters,
                  allowedParams: e.target.value.split(',').map((s) => s.trim()).filter(Boolean),
                },
              })}
            />
          </label>

          <div className="ma-param-chips">
            {commonParams.map((p) => (
              <button
                key={p}
                type="button"
                className={`ma-param-chip ${filters.blockedParams.includes(p) ? 'blocked' : ''}`}
                onClick={() => toggleBlocked(p)}
              >
                {p}
              </button>
            ))}
          </div>

          <label className="mcp-config-field checkbox">
            <input
              type="checkbox"
              checked={filters.autoLearnFrom400}
              onChange={(e) => onUpdate({ paramFilters: { ...filters, autoLearnFrom400: e.target.checked } })}
            />
            <span>Auto-learn from 400 errors — add unsupported params to block list</span>
          </label>
        </section>

        <div className="ma-compat-actions">
          <button className="plugins-back" onClick={onClose}>Cancel</button>
          <button className="plugin-install-btn" onClick={onClose}>Save</button>
        </div>
      </motion.div>
    </div>
  );
}
