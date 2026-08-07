import { Settings, LogOut } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAppStore } from '../../store/appStore';
import { getUserAvatarUrl } from '../../store/userProfileStore';
import { logOut, isFirebaseConfigured } from '../../lib/firebase';

export default function SidebarUserFooter() {
  const navigate = useNavigate();
  const user = useAppStore((s) => s.user);
  const setUser = useAppStore((s) => s.setUser);
  const setCenterView = useAppStore((s) => s.setCenterView);

  const displayName = (user as { displayName?: string })?.displayName ?? 'Ocean User';
  const email = user?.email ?? 'Not signed in';
  const photoURL = (user as { photoURL?: string })?.photoURL;
  const avatar = getUserAvatarUrl(photoURL, displayName, email);

  async function handleSignOut() {
    try {
      if (isFirebaseConfigured()) await logOut();
    } catch { /* demo mode */ }
    setUser(null);
    navigate('/auth');
  }

  return (
    <div className="sidebar-user-footer">
      <button
        className="sidebar-user-btn"
        onClick={() => setCenterView('profile')}
        title="Profile & security settings"
      >
        <span className="sidebar-user-avatar-wrap">
          <img src={avatar} alt="" className="sidebar-user-avatar" referrerPolicy="no-referrer" />
        </span>
        <span className="sidebar-user-info">
          <strong>{displayName}</strong>
          <small>{email}</small>
        </span>
      </button>
      <div className="sidebar-user-actions">
        <button className="sidebar-user-icon" onClick={() => setCenterView('profile')} title="Settings">
          <Settings size={14} />
        </button>
        <button className="sidebar-user-icon" onClick={() => void handleSignOut()} title="Sign out">
          <LogOut size={14} />
        </button>
      </div>
    </div>
  );
}
