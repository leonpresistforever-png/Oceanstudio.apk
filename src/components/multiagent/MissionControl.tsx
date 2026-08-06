import { motion, AnimatePresence } from 'framer-motion';
import { Activity, CheckCircle, Circle, Clock, AlertCircle, Radio } from 'lucide-react';
import type { WorkflowRunRecord } from '../../agents/types';

interface MissionControlProps {
  runs: WorkflowRunRecord[];
  activeRunId: string | null;
  multiAgentEnabled: boolean;
  comboName?: string;
}

const STATUS_ICON = {
  running: Radio,
  completed: CheckCircle,
  failed: AlertCircle,
  pending: Circle,
} as const;

const STATUS_COLOR = {
  running: '#0ea5e9',
  completed: '#22c55e',
  failed: '#ef4444',
  pending: '#a8a29e',
};

export default function MissionControl({ runs, activeRunId, multiAgentEnabled, comboName }: MissionControlProps) {
  const activeRun = runs.find((r) => r.id === activeRunId);
  const isLive = multiAgentEnabled && !!activeRunId;
  const recentRuns = runs.slice(0, 8);

  return (
    <div className="ma-mission-control">
      <div className="ma-mission-status-bar">
        <div className={`ma-mission-live ${isLive ? 'on' : multiAgentEnabled ? 'standby' : ''}`}>
          <span className="ma-mission-dot" />
          {isLive ? 'MISSION ACTIVE' : multiAgentEnabled ? 'TEAM LIVE' : 'STANDBY'}
        </div>
        {comboName && <span className="ma-mission-combo">{comboName}</span>}
        {activeRun && (
          <span className="ma-mission-phase">
            <Activity size={12} /> {activeRun.currentPhase ?? 'running'}
          </span>
        )}
      </div>

      {activeRun && (
        <motion.div className="ma-mission-active" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
          <h4>Active Mission</h4>
          <p className="ma-mission-prompt">{activeRun.prompt.slice(0, 120)}{activeRun.prompt.length > 120 ? '...' : ''}</p>
          <div className="ma-mission-phases">
            {activeRun.phases.map((phase, i) => {
              const Icon = STATUS_ICON[phase.status];
              return (
                <motion.div
                  key={phase.id}
                  className={`ma-mission-phase-row ${phase.status}`}
                  initial={{ opacity: 0, x: -8 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.05 }}
                >
                  <Icon size={14} style={{ color: STATUS_COLOR[phase.status] }} />
                  <div className="ma-mission-phase-info">
                    <strong>{phase.agentName}</strong>
                    <span>{phase.label}</span>
                  </div>
                  <span className="ma-mission-phase-time">
                    {phase.completedAt ? `${((phase.completedAt - phase.startedAt) / 1000).toFixed(1)}s` : '...'}
                  </span>
                </motion.div>
              );
            })}
          </div>
        </motion.div>
      )}

      <div className="ma-mission-history">
        <h4><Clock size={14} /> Run History</h4>
        {recentRuns.length === 0 ? (
          <p className="ma-mission-empty">No missions yet — enable team and send a message to start.</p>
        ) : (
          <AnimatePresence>
            {recentRuns.map((run) => (
              <motion.div
                key={run.id}
                className={`ma-mission-run-card ${run.status}`}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
              >
                <div className="ma-mission-run-top">
                  <span className={`ma-mission-run-status ${run.status}`}>{run.status}</span>
                  <span className="ma-mission-run-mode">{run.mode}</span>
                  <span className="ma-mission-run-time">{new Date(run.startedAt).toLocaleTimeString()}</span>
                </div>
                <p>{run.comboName} — {run.prompt.slice(0, 60)}...</p>
                <div className="ma-mission-run-agents">
                  {run.agentNames.map((name) => (
                    <span key={name} className="ma-mission-agent-chip">{name}</span>
                  ))}
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
        )}
      </div>
    </div>
  );
}
