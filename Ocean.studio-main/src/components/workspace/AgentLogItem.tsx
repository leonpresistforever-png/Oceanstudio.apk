import { ChevronRight, ThumbsUp, ThumbsDown, Copy, MessageCircleQuestion, Volume2, Square } from 'lucide-react';
import { useAppStore, type AgentLogEntry } from '../../store/appStore';
import './AgentPanel.css';

interface Props {
  entry: AgentLogEntry;
  speakingId?: string | null;
  onAsk?: (text: string, entryId: string) => void;
  onSpeak?: (text: string, entryId: string) => void;
  onStopSpeak?: () => void;
}

function getResponseText(entry: AgentLogEntry): string {
  return (entry.detail ?? entry.metadata?.responseText as string ?? entry.summary) || '';
}

export default function AgentLogItem({ entry, speakingId, onAsk, onSpeak, onStopSpeak }: Props) {
  const updateAgentLog = useAppStore((s) => s.updateAgentLog);

  function toggleExpand() {
    if (entry.detail || entry.type === 'command') {
      updateAgentLog(entry.id, { expanded: !entry.expanded });
    }
  }

  function setFeedback(feedback: 'up' | 'down') {
    updateAgentLog(entry.id, {
      feedback: entry.feedback === feedback ? undefined : feedback,
    });
  }

  async function handleCopy() {
    const text = getResponseText(entry);
    try {
      await navigator.clipboard.writeText(text);
    } catch { /* ignore */ }
  }

  function handleAsk() {
    const text = getResponseText(entry);
    onAsk?.(text, entry.id);
    updateAgentLog(entry.id, { taggedForQuestion: true });
  }

  function handleSpeak() {
    const text = getResponseText(entry);
    if (speakingId === entry.id) {
      onStopSpeak?.();
    } else {
      onSpeak?.(text, entry.id);
    }
  }

  const showActions = entry.type === 'result' && entry.status === 'completed';

  if (entry.type === 'user') {
    return (
      <div className={`agent-log-user ${entry.taggedForQuestion ? 'tagged' : ''}`}>
        {entry.summary}
      </div>
    );
  }

  if (entry.type === 'command') {
    return (
      <div>
        <div className="agent-log-command" onClick={toggleExpand}>
          <span className="dollar">$</span>
          <span className="agent-log-summary">{entry.summary}</span>
        </div>
        {entry.expanded && entry.detail && (
          <div className="agent-log-detail">{entry.detail}</div>
        )}
      </div>
    );
  }

  const isThought = entry.type === 'thought';
  const hasDetail = Boolean(entry.detail);
  const durationText = entry.duration
    ? `${Math.round(entry.duration / 1000)}s`
    : isThought && entry.status === 'running'
    ? '...'
    : '';

  return (
    <div className={`agent-log-entry ${entry.taggedForQuestion ? 'tagged' : ''}`}>
      <div
        className={`agent-log-header ${entry.status === 'running' ? 'running' : ''}`}
        onClick={hasDetail ? toggleExpand : undefined}
        style={{ cursor: hasDetail ? 'pointer' : 'default' }}
      >
        {hasDetail && (
          <ChevronRight
            size={14}
            className={`chevron ${entry.expanded ? 'expanded' : ''}`}
          />
        )}
        <span
          className={`agent-log-summary ${entry.status === 'running' ? 'shimmer' : ''}`}
        >
          {entry.summary}
        </span>
        {durationText && (
          <span className="agent-log-duration">{durationText}</span>
        )}
      </div>
      {entry.expanded && entry.detail && (
        <div className="agent-log-detail">{entry.detail}</div>
      )}
      {showActions && (
        <div className="agent-log-actions">
          <button
            type="button"
            className={entry.feedback === 'up' ? 'active' : ''}
            onClick={() => setFeedback('up')}
            title="Good response"
          >
            <ThumbsUp size={14} />
          </button>
          <button
            type="button"
            className={entry.feedback === 'down' ? 'active' : ''}
            onClick={() => setFeedback('down')}
            title="Poor response"
          >
            <ThumbsDown size={14} />
          </button>
          <button type="button" onClick={handleCopy} title="Copy">
            <Copy size={14} />
          </button>
          <button
            type="button"
            className={entry.taggedForQuestion ? 'active' : ''}
            onClick={handleAsk}
            title="Ask follow-up"
          >
            <MessageCircleQuestion size={14} />
          </button>
          <button
            type="button"
            className={speakingId === entry.id ? 'active speaking' : ''}
            onClick={handleSpeak}
            title={speakingId === entry.id ? 'Stop' : 'Speak'}
          >
            {speakingId === entry.id ? <Square size={14} /> : <Volume2 size={14} />}
          </button>
        </div>
      )}
    </div>
  );
}
