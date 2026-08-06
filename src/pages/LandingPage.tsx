import { useNavigate } from 'react-router-dom';
import { ArrowRight, LogOut } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { logOut } from '../lib/firebase';
import './LandingPage.css';

const FEATURES = [
  {
    title: 'Hardware-Level Agent',
    description: 'A coding agent with direct access to your filesystem, terminals, and preview environment. Not a simulation — real shell execution on your hardware.',
  },
  {
    title: 'Multi-Terminal Architecture',
    description: 'Native shell for desktop, Google Cloud Shell for web users, and mobile-native terminal for APK builds. Each with full command execution and port preview integration.',
  },
  {
    title: 'Live Port Preview',
    description: 'When your agent or terminal activates a port, see the result instantly in the built-in preview. No browser switching required.',
  },
  {
    title: 'Review-Driven Control',
    description: 'Choose your agent mode: review every action, auto-execute with safety checks, or full bypass for independent operation.',
  },
  {
    title: 'Real Codebase Editor',
    description: 'Monaco-powered editor with full file tree navigation. Edit, create, and refactor code with syntax highlighting across all major languages.',
  },
  {
    title: 'Cursor-Style Agent UI',
    description: 'Transparent agent activity with expandable thought logs, command history, and task progress — the same clarity you expect from modern AI coding tools.',
  },
];

export default function LandingPage() {
  const navigate = useNavigate();
  const user = useAppStore((s) => s.user);

  async function handleLogout() {
    await logOut();
    navigate('/auth');
  }

  return (
    <div className="landing">
      <section className="landing-hero">
        <video
          className="landing-hero-video"
          autoPlay
          muted
          loop
          playsInline
          poster="https://images.pexels.com/photos/577585/pexels-photo-577585.jpeg?auto=compress&cs=tinysrgb&w=1920"
        >
          <source
            src="https://videos.pexels.com/video-files/2278095/2278095-uhd_2560_1440_25fps.mp4"
            type="video/mp4"
          />
        </video>
        <div className="landing-hero-overlay" />

        <nav className="landing-nav">
          <div className="landing-nav-logo">
            <img src="/ocean-icon.svg" alt="" />
            Ocean.studio
          </div>
          <div className="landing-nav-actions">
            {user && (
              <span className="landing-nav-user">{user.email}</span>
            )}
            <button onClick={handleLogout} title="Sign out">
              <LogOut size={18} color="var(--text-secondary)" />
            </button>
          </div>
        </nav>

        <div className="landing-hero-content">
          <h1>Code at the hardware level</h1>
          <p>
            Ocean.studio is a coding agent workspace that runs on your machine with full terminal access,
            live previews, and intelligent automation. Built for developers who need real execution, not mockups.
          </p>
          <button className="landing-cta" onClick={() => navigate('/setup')}>
            Launch Workspace
            <ArrowRight size={18} />
          </button>
        </div>
      </section>

      <section className="landing-features">
        <h2>Built different</h2>
        <div className="landing-features-grid">
          {FEATURES.map((f) => (
            <div key={f.title} className="landing-feature">
              <h3>{f.title}</h3>
              <p>{f.description}</p>
            </div>
          ))}
        </div>
      </section>

      <footer className="landing-footer">
        <div className="landing-footer-grid">
          <div className="landing-footer-brand">
            <div className="landing-nav-logo">
              <img src="/ocean-icon.svg" alt="" />
              Ocean.studio
            </div>
            <p>
              A personal coding agent and workspace designed for hardware-level development.
              Electron, Python, and native shell integration — with future support for web and mobile.
            </p>
          </div>
          <div>
            <h4>Product</h4>
            <ul>
              <li>Agent Workspace</li>
              <li>Terminal Integration</li>
              <li>Port Preview</li>
              <li>Code Editor</li>
            </ul>
          </div>
          <div>
            <h4>Agent Modes</h4>
            <ul>
              <li>Review-driven</li>
              <li>Auto (cautious)</li>
              <li>Bypass (independent)</li>
            </ul>
          </div>
          <div>
            <h4>Platforms</h4>
            <ul>
              <li>Desktop (Electron)</li>
              <li>Web (coming soon)</li>
              <li>Mobile APK (coming soon)</li>
            </ul>
          </div>
        </div>
        <div className="landing-footer-bottom">
          <span>&copy; 2026 Ocean.studio</span>
          <span>Hardware-level coding intelligence</span>
        </div>
      </footer>
    </div>
  );
}
