import { useAppStore } from '../store/appStore';
import SkillsStorePanel from '../components/skills/SkillsStorePanel';
import '../pages/PluginsPage.css';

export default function SkillsPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);

  return (
    <div className="plugins-page">
      <div className="plugins-header">
        <div className="plugins-header-left">
          <button type="button" className="plugins-back" onClick={() => setCenterView('editor')}>← Back</button>
          <h1>Skills Store</h1>
        </div>
      </div>
      <SkillsStorePanel scope="main" title="Workspace Skills" showUpload showAgentCreate />
    </div>
  );
}
