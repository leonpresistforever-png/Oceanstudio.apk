import { useAppStore } from '../store/appStore';
import ExtensionsStorePanel from '../components/extensions/ExtensionsStorePanel';
import '../pages/PluginsPage.css';

export default function ExtensionsPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);

  return (
    <div className="plugins-page">
      <div className="plugins-header">
        <div className="plugins-header-left">
          <button type="button" className="plugins-back" onClick={() => setCenterView('editor')}>← Back</button>
          <h1>Extensions</h1>
          <span className="plugins-platform-badge">VS Code · Antigravity · Open VSX compatible</span>
        </div>
      </div>
      <ExtensionsStorePanel title="Extension Marketplace" />
    </div>
  );
}
