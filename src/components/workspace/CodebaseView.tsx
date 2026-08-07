import { useAppStore } from '../../store/appStore';
import FileTree from './FileTree';

export default function CodebaseView() {
  const workspacePath = useAppStore((s) => s.workspacePath);

  return (
    <div style={{ height: '100%', overflow: 'auto', padding: '16px' }}>
      <h3 style={{ fontSize: '0.875rem', fontWeight: 600, marginBottom: '12px', color: 'var(--text-secondary)' }}>
        Codebase — {workspacePath}
      </h3>
      {workspacePath ? (
        <FileTree rootPath={workspacePath} />
      ) : (
        <p style={{ color: 'var(--text-tertiary)', fontSize: '0.875rem' }}>No workspace selected</p>
      )}
    </div>
  );
}
