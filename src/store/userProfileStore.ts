import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface UserSecuritySettings {
  confirmDestructiveOps: boolean;
  maskApiKeys: boolean;
  sessionLockMinutes: number;
  requireReauthForProviders: boolean;
  biometricLock: boolean;
  analyticsEnabled: boolean;
}

export const DEFAULT_SECURITY: UserSecuritySettings = {
  confirmDestructiveOps: true,
  maskApiKeys: true,
  sessionLockMinutes: 0,
  requireReauthForProviders: false,
  biometricLock: false,
  analyticsEnabled: true,
};

interface UserProfileState {
  security: UserSecuritySettings;
  lastActiveAt: number;
  setSecurity: (patch: Partial<UserSecuritySettings>) => void;
  touchActivity: () => void;
}

export const useUserProfileStore = create<UserProfileState>()(
  persist(
    (set) => ({
      security: { ...DEFAULT_SECURITY },
      lastActiveAt: Date.now(),
      setSecurity: (patch) =>
        set((s) => ({ security: { ...s.security, ...patch } })),
      touchActivity: () => set({ lastActiveAt: Date.now() }),
    }),
    { name: 'ocean-user-profile' }
  )
);

export function getUserInitials(name?: string | null, email?: string | null): string {
  if (name?.trim()) {
    const parts = name.trim().split(/\s+/);
    return parts.length >= 2
      ? `${parts[0][0]}${parts[1][0]}`.toUpperCase()
      : name.slice(0, 2).toUpperCase();
  }
  if (email) return email.slice(0, 2).toUpperCase();
  return 'OS';
}

export function getUserAvatarUrl(
  photoURL?: string | null,
  name?: string | null,
  email?: string | null
): string {
  if (photoURL) return photoURL;
  const label = encodeURIComponent(name || email || 'Ocean User');
  return `https://ui-avatars.com/api/?name=${label}&background=0ea5e9&color=fff&size=128`;
}
