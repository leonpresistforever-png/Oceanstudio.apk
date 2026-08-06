import { useState, useEffect, useRef } from 'react';
import Editor from '@monaco-editor/react';
import { getOceanAPI } from '../../lib/platform';
import { useAppStore } from '../../store/appStore';

function getLanguageFromPath(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase() || '';
  const map: Record<string, string> = {
    ts: 'typescript', tsx: 'typescript', js: 'javascript', jsx: 'javascript',
    py: 'python', json: 'json', md: 'markdown', css: 'css', html: 'html',
    rs: 'rust', go: 'go', java: 'java', cpp: 'cpp', c: 'c', sql: 'sql',
    yaml: 'yaml', yml: 'yaml', sh: 'shell', bash: 'shell',
  };
  return map[ext] || 'plaintext';
}

export default function CodeEditor() {
  const activeFile = useAppStore((s) => s.activeFile);
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const loadGen = useRef(0);

  useEffect(() => {
    if (!activeFile) {
      setContent('');
      return;
    }
    const filePath = activeFile;
    const gen = ++loadGen.current;
    async function load() {
      setLoading(true);
      try {
        const api = getOceanAPI();
        const text = await api.fs.readFile(filePath);
        if (gen !== loadGen.current) return;
        setContent(text);
      } catch {
        if (gen !== loadGen.current) return;
        setContent('// Unable to load file');
      } finally {
        if (gen === loadGen.current) setLoading(false);
      }
    }
    void load();
  }, [activeFile]);

  async function handleSave(value: string | undefined) {
    if (!activeFile || value === undefined) return;
    setContent(value);
    const api = getOceanAPI();
    await api.fs.writeFile(activeFile, value);
  }

  if (!activeFile) {
    return (
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        height: '100%', color: 'var(--text-tertiary)', fontSize: '0.875rem',
      }}>
        Select a file from the explorer to start editing
      </div>
    );
  }

  const fileName = activeFile.split('/').pop() || activeFile;

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{
        padding: '6px 16px', fontSize: '0.8125rem', color: 'var(--text-secondary)',
        borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-tertiary)',
      }}>
        {fileName}
      </div>
      <div style={{ flex: 1 }}>
        {loading ? (
          <div style={{ padding: 20, color: 'var(--text-tertiary)' }}>Loading...</div>
        ) : (
          <Editor
            height="100%"
            language={getLanguageFromPath(activeFile)}
            value={content}
            onChange={handleSave}
            theme="vs"
            options={{
              fontSize: 13,
              fontFamily: 'JetBrains Mono, monospace',
              minimap: { enabled: true },
              scrollBeyondLastLine: false,
              padding: { top: 12 },
              lineNumbers: 'on',
              renderWhitespace: 'selection',
              automaticLayout: true,
            }}
          />
        )}
      </div>
    </div>
  );
}
