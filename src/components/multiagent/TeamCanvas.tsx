import { motion } from 'framer-motion';
import { Crown, Zap, Users, Sparkles } from 'lucide-react';
import type { AgentCombo } from '../../agents/types';

interface TeamCanvasProps {
  combo: AgentCombo;
  selectedMemberId: string | null;
  onSelectMember: (id: string) => void;
  activeRunId?: string | null;
  activePhaseAgent?: string | null;
}

export default function TeamCanvas({ combo, selectedMemberId, onSelectMember, activeRunId, activePhaseAgent }: TeamCanvasProps) {
  const head = combo.members.find((m) => m.id === combo.headAgentId);
  const workers = combo.members.filter((m) => m.id !== combo.headAgentId);
  const isMissionActive = !!activeRunId;

  function workerPosition(index: number, total: number): { x: number; y: number } {
    if (total === 0) return { x: 0, y: 0 };
    const angle = (index / total) * Math.PI * 2 - Math.PI / 2;
    const radius = Math.min(160, 100 + total * 15);
    return { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius };
  }

  return (
    <div className="ma-team-canvas">
      <div className="ma-team-canvas-header">
        <span className={`ma-canvas-mode ${combo.mode}`}>
          {combo.mode === 'parallel' ? <Zap size={12} /> : combo.mode === 'fusion' ? <Sparkles size={12} /> : <Users size={12} />}
          {combo.mode}
        </span>
        <span className="ma-canvas-stat">{combo.members.filter((m) => m.enabled).length} active</span>
      </div>

      <div className="ma-team-canvas-stage">
        <svg className="ma-team-canvas-lines" viewBox="-220 -220 440 440">
          {combo.mode === 'parallel' && head && workers.map((w, i) => {
            const pos = workerPosition(i, workers.length);
            return (
              <motion.line
                key={w.id}
                x1={0} y1={-40}
                x2={pos.x} y2={pos.y + 20}
                stroke={w.enabled ? w.brandColor : '#d6d3d1'}
                strokeWidth={selectedMemberId === w.id ? 3 : 2}
                strokeDasharray={w.enabled ? undefined : '4 4'}
                initial={{ pathLength: 0, opacity: 0 }}
                animate={{ pathLength: 1, opacity: 1 }}
                transition={{ duration: 0.6, delay: i * 0.08 }}
              />
            );
          })}
          {combo.mode === 'collaborative' && combo.members.map((m, i) => {
            const pos = workerPosition(i, combo.members.length);
            const next = workerPosition((i + 1) % combo.members.length, combo.members.length);
            return (
              <motion.line
                key={`ring-${m.id}`}
                x1={pos.x} y1={pos.y}
                x2={next.x} y2={next.y}
                stroke={m.brandColor}
                strokeWidth={1.5}
                strokeOpacity={0.4}
                initial={{ pathLength: 0 }}
                animate={{ pathLength: 1 }}
                transition={{ duration: 1, delay: i * 0.1 }}
              />
            );
          })}
          {combo.mode === 'fusion' && combo.members.map((m, i) => {
            const pos = workerPosition(i, combo.members.length);
            return (
              <motion.line
                key={`fuse-${m.id}`}
                x1={0} y1={0}
                x2={pos.x} y2={pos.y}
                stroke={m.brandColor}
                strokeWidth={combo.fusionConfig?.memberIds.includes(m.id) ? 2.5 : 1}
                strokeOpacity={combo.fusionConfig?.memberIds.includes(m.id) ? 0.7 : 0.2}
                strokeDasharray={combo.fusionConfig?.memberIds.includes(m.id) ? undefined : '4 4'}
                initial={{ pathLength: 0 }}
                animate={{ pathLength: 1 }}
                transition={{ duration: 0.5, delay: i * 0.06 }}
              />
            );
          })}
          {combo.mode === 'parallel' && workers.filter((w) => w.enabled).map((w, i) => {
            const pos = workerPosition(workers.indexOf(w), workers.length);
            if (!isMissionActive) return null;
            return (
              <motion.circle
                key={`pulse-${w.id}`}
                r={3}
                fill={w.brandColor}
                initial={{ cx: 0, cy: -40 }}
                animate={{ cx: [0, pos.x], cy: [-40, pos.y + 20] }}
                transition={{ duration: 2.5, repeat: Infinity, ease: 'linear', delay: i * 0.5 }}
              />
            );
          })}
        </svg>

        {combo.mode === 'parallel' && head && (
          <motion.div className="ma-canvas-head" layout>
            <Crown size={18} />
            <span>{head.name}</span>
            <small>Head Agent</small>
          </motion.div>
        )}

        {combo.mode === 'collaborative' && (
          <motion.div className="ma-canvas-hub collaborative" layout>
            <Users size={22} />
            <span>All Together</span>
          </motion.div>
        )}

        {combo.mode === 'fusion' && (
          <motion.div className="ma-canvas-hub fusion" layout>
            <Sparkles size={22} />
            <span>{combo.fusionConfig?.fusedName ?? 'Fused Agent'}</span>
            <small>{combo.fusionConfig?.strategy ?? 'weighted-vote'}</small>
          </motion.div>
        )}

        {(combo.mode === 'parallel' ? workers : combo.members).map((member, i) => {
          const pos = workerPosition(i, combo.mode === 'parallel' ? workers.length : combo.members.length);
          const isHead = member.id === combo.headAgentId;
          const inFusion = combo.mode !== 'fusion' || combo.fusionConfig?.memberIds.includes(member.id);
          return (
            <motion.button
              key={member.id}
              className={`ma-canvas-node ${selectedMemberId === member.id ? 'selected' : ''} ${!member.enabled || !inFusion ? 'disabled' : ''}`}
              style={{
                transform: `translate(calc(-50% + ${pos.x}px), calc(-50% + ${combo.mode === 'parallel' ? pos.y + 60 : pos.y}px))`,
                borderColor: member.brandColor,
              }}
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: member.enabled ? 1 : 0.45 }}
              whileHover={{ scale: 1.06 }}
              onClick={() => onSelectMember(member.id)}
            >
              <div className="ma-canvas-node-avatar" style={{ background: member.brandColor }}>
                {member.name[0]}
              </div>
              <span className="ma-canvas-node-name">{member.name}</span>
              <span className="ma-canvas-node-role">{isHead ? 'head' : member.role}</span>
              {member.enabled && isMissionActive && (activePhaseAgent === member.name || !activePhaseAgent) && (
                <span className="ma-canvas-node-pulse" style={{ background: member.brandColor }} />
              )}
            </motion.button>
          );
        })}
      </div>

      <div className="ma-canvas-legend">
        {combo.members.map((m) => (
          <span key={m.id} className="ma-canvas-legend-item" style={{ opacity: m.enabled ? 1 : 0.4 }}>
            <span style={{ background: m.brandColor }} />
            {m.name} · {m.modelLabel.slice(0, 20)}
          </span>
        ))}
      </div>
    </div>
  );
}
