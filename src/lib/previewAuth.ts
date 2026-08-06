/**
 * Temporary preview credentials — remove before public launch.
 * Use the "Preview Login" button on the auth page or sign in manually.
 */
export const PREVIEW_LOGIN = {
  email: 'preview@ocean.studio',
  password: 'OceanPreview2026!',
} as const;

export function isPreviewCredentials(email: string, password: string): boolean {
  return (
    email.trim().toLowerCase() === PREVIEW_LOGIN.email &&
    password === PREVIEW_LOGIN.password
  );
}

export function createPreviewUser() {
  return {
    uid: 'preview-user',
    email: PREVIEW_LOGIN.email,
    displayName: 'Preview User',
    photoURL: 'https://ui-avatars.com/api/?name=Preview+User&background=0ea5e9&color=fff&size=128',
    emailVerified: true,
  };
}
