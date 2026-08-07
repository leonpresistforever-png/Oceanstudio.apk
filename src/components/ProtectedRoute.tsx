import { Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useAppStore } from '../store/appStore';
import { isFirebaseConfigured } from '../lib/firebase';

interface Props {
  children: React.ReactNode;
  requireWorkspace?: boolean;
}

export default function ProtectedRoute({ children, requireWorkspace }: Props) {
  const user = useAppStore((s) => s.user);
  const setUser = useAppStore((s) => s.setUser);
  const workspaceConfig = useAppStore((s) => s.workspaceConfig);

  useEffect(() => {
    if (!isFirebaseConfigured() && !user) {
      setUser({ email: 'demo@ocean.studio', uid: 'demo' } as never);
    }
  }, [user, setUser]);

  if (!user && isFirebaseConfigured()) return <Navigate to="/auth" replace />;
  if (requireWorkspace && !workspaceConfig) return <Navigate to="/setup" replace />;

  return <>{children}</>;
}
