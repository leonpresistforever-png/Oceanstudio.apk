import { useModelStore } from '../../store/modelStore';
import './AgentTimeoutBar.css';

const TIMEOUT_PRESETS = [30, 60, 120, 300, 600, 900] as const;

export default function AgentTimeoutBar() {
  const rawTimeout = useModelStore((s) => s.config.agentTimeoutSec);
  const antiTimeout = useModelStore((s) => s.config.antiTimeout);
  const setConfig = useModelStore((s) => s.setConfig);

  const agentTimeoutSec = Number.isFinite(rawTimeout)
    ? Math.min(900, Math.max(30, rawTimeout))
    : 120;

  return (
    <div className="agent-timeout-bar">
      <div className="agent-timeout-header">
        <span className="agent-timeout-label">Session timeout</span>
        {antiTimeout ? (
          <span className="agent-timeout-badge anti">Anti-timeout ON</span>
        ) : (
          <span className="agent-timeout-badge">{agentTimeoutSec >= 60 ? `${Math.round(agentTimeoutSec / 60)}m` : `${agentTimeoutSec}s`}</span>
        )}
      </div>

      <label className="agent-anti-timeout">
        <input
          type="checkbox"
          checked={antiTimeout}
          onChange={(e) => setConfig({ antiTimeout: e.target.checked })}
        />
        <span>Anti-timeout — run until workflow completes (10m+ loops)</span>
      </label>

      {!antiTimeout && (
        <>
          <input
            type="range"
            className="agent-timeout-slider"
            min={30}
            max={900}
            step={30}
            value={agentTimeoutSec}
            onChange={(e) => setConfig({ agentTimeoutSec: Number(e.target.value) })}
          />
          <div className="agent-timeout-presets">
            {TIMEOUT_PRESETS.map((sec) => (
              <button
                key={sec}
                type="button"
                className={agentTimeoutSec === sec ? 'active' : ''}
                onClick={() => setConfig({ agentTimeoutSec: sec })}
              >
                {sec >= 60 ? `${sec / 60}m` : `${sec}s`}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
