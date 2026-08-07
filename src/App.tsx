import { Routes, Route, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { subscribeToAuth, handleAuthRedirectResult } from './lib/firebase';
import { useAppStore } from './store/appStore';
import AuthPage from './pages/AuthPage';
import LandingPage from './pages/LandingPage';
import WorkspaceSetup from './pages/WorkspaceSetup';
import Workspace from './pages/Workspace';
import ProtectedRoute from './components/ProtectedRoute';

export default function App() {
  const setUser = useAppStore((s) => s.setUser);
  const workspaceConfig = useAppStore((s) => s.workspaceConfig);

  useEffect(() => {
    const unsub = subscribeToAuth((user) => setUser(user));
    void handleAuthRedirectResult();
    return unsub;
  }, [setUser]);

  return (
    <Routes>
      <Route path="/auth" element={<AuthPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            {workspaceConfig ? <Navigate to="/workspace" replace /> : <LandingPage />}
          </ProtectedRoute>
        }
      />
      <Route
        path="/setup"
        element={
          <ProtectedRoute>
            <WorkspaceSetup />
          </ProtectedRoute>
        }
      />
      <Route
        path="/workspace"
        element={
          <ProtectedRoute requireWorkspace>
            <Workspace />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
