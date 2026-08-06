import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield, Key, User, LogOut, Mail, Lock } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import {
  useUserProfileStore,
  DEFAULT_SECURITY,
  getUserAvatarUrl,
  getUserInitials,
} from '../store/userProfileStore';
import { logOut, isFirebaseConfigured } from '../lib/firebase';
import './ProfileSettingsPage.css';

export default function ProfileSettingsPage() {
  const navigate = useNavigate();
  const appUser = useAppStore((s) => s.user);
  const setUser = useAppStore((s) => s.setUser);
  const workspaceConfig = useAppStore((s) => s.workspaceConfig);
  const security = useUserProfileStore((s) => s.security);
  const setSecurity = useUserProfileStore((s) => s.setSecurity);
  const [saved, setSaved] = useState(false);

  const displayName = (appUser as { displayName?: string })?.displayName ?? 'Ocean User';
  const email = appUser?.email ?? '—';
  const photoURL = (appUser as { photoURL?: string })?.photoURL;
  const providerId = (appUser as { providerData?: { providerId: string }[] })?.providerData?.[0]?.providerId;
  const avatar = getUserAvatarUrl(photoURL, displayName, email);

  async function handleSignOut() {
    try {
      if (isFirebaseConfigured()) await logOut();
    } catch { /* demo */ }
    setUser(null);
    navigate('/auth');
  }

  function flashSaved() {
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

  return (
    <div className="profile-page">
      <header className="profile-header">
        <h1><User size={20} /> Profile &amp; Security</h1>
        {saved && <span className="profile-saved">Settings saved</span>}
      </header>

      <section className="profile-card profile-identity">
        <img src={avatar} alt="" className="profile-avatar" referrerPolicy="no-referrer" />
        <div>
          <h2>{displayName}</h2>
          <p><Mail size={14} /> {email}</p>
          {providerId && <span className="profile-provider-badge">Signed in via {providerId}</span>}
          {!photoURL && (
            <p className="profile-hint">Sign in with Google to use your Google profile picture.</p>
          )}
        </div>
      </section>

      <section className="profile-card">
        <h3><Shield size={16} /> Security</h3>
        <div className="profile-toggles">
          <label>
            <input
              type="checkbox"
              checked={security.confirmDestructiveOps}
              onChange={(e) => { setSecurity({ confirmDestructiveOps: e.target.checked }); flashSaved(); }}
            />
            <span>Confirm destructive agent commands (rm -rf, sudo, etc.)</span>
          </label>
          <label>
            <input
              type="checkbox"
              checked={security.maskApiKeys}
              onChange={(e) => { setSecurity({ maskApiKeys: e.target.checked }); flashSaved(); }}
            />
            <span>Mask API keys in provider and MCP config fields</span>
          </label>
          <label>
            <input
              type="checkbox"
              checked={security.requireReauthForProviders}
              onChange={(e) => { setSecurity({ requireReauthForProviders: e.target.checked }); flashSaved(); }}
            />
            <span>Require re-authentication before connecting new providers</span>
          </label>
          <label>
            <input
              type="checkbox"
              checked={security.biometricLock}
              onChange={(e) => { setSecurity({ biometricLock: e.target.checked }); flashSaved(); }}
            />
            <span>Biometric lock on APK (when available)</span>
          </label>
          <label>
            <input
              type="checkbox"
              checked={security.analyticsEnabled}
              onChange={(e) => { setSecurity({ analyticsEnabled: e.target.checked }); flashSaved(); }}
            />
            <span>Anonymous usage analytics</span>
          </label>
        </div>

        <label className="profile-field">
          <span><Lock size={14} /> Session auto-lock (minutes, 0 = off)</span>
          <input
            type="number"
            min={0}
            max={480}
            value={security.sessionLockMinutes}
            onChange={(e) => { setSecurity({ sessionLockMinutes: Number(e.target.value) }); flashSaved(); }}
          />
        </label>

        <button className="profile-reset-btn" onClick={() => { setSecurity(DEFAULT_SECURITY); flashSaved(); }}>
          Reset security to defaults
        </button>
      </section>

      <section className="profile-card">
        <h3><Key size={16} /> Workspace</h3>
        <dl className="profile-dl">
          <dt>Agent mode</dt>
          <dd>{workspaceConfig?.agentMode ?? 'review'}</dd>
          <dt>Language</dt>
          <dd>{workspaceConfig?.language ?? 'en'}</dd>
          <dt>UI template</dt>
          <dd>{workspaceConfig?.template ?? 'minimal'}</dd>
        </dl>
      </section>

      <section className="profile-card profile-danger">
        <h3><LogOut size={16} /> Account</h3>
        <p>Sign out of Ocean.studio on this device. Provider tokens remain stored until you disconnect them.</p>
        <button className="profile-signout-btn" onClick={() => void handleSignOut()}>
          <LogOut size={14} /> Sign out
        </button>
      </section>
    </div>
  );
}
