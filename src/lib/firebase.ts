import { initializeApp, getApps, type FirebaseApp } from 'firebase/app';
import { getAnalytics, isSupported as isAnalyticsSupported, type Analytics } from 'firebase/analytics';
import {
  getAuth,
  GoogleAuthProvider,
  GithubAuthProvider,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  sendPasswordResetEmail,
  signOut,
  onAuthStateChanged,
  type User,
  type Auth,
} from 'firebase/auth';
import { Capacitor } from '@capacitor/core';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || '',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || '',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || '',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || '',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '',
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID || '',
};

export function isFirebaseConfigured(): boolean {
  return Boolean(firebaseConfig.apiKey && firebaseConfig.projectId);
}

let app: FirebaseApp | null = null;
let auth: Auth | null = null;
let analytics: Analytics | null = null;

if (isFirebaseConfigured()) {
  app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
  auth = getAuth(app);

  if (typeof window !== 'undefined') {
    isAnalyticsSupported().then((supported) => {
      if (supported && app) analytics = getAnalytics(app);
    });
  }
}

export { auth, analytics };
export const googleProvider = isFirebaseConfigured() ? new GoogleAuthProvider() : null;
export const githubProvider = isFirebaseConfigured() ? new GithubAuthProvider() : null;

export async function signIn(email: string, password: string) {
  if (!auth) throw new Error('Firebase not configured');
  return signInWithEmailAndPassword(auth, email, password);
}

export async function signUp(email: string, password: string) {
  if (!auth) throw new Error('Firebase not configured');
  return createUserWithEmailAndPassword(auth, email, password);
}

export function isNativeMobileApp(): boolean {
  return typeof window !== 'undefined' &&
    Capacitor.isNativePlatform() &&
    Capacitor.getPlatform() === 'android';
}

export async function handleAuthRedirectResult() {
  if (!auth) return null;
  try {
    return await getRedirectResult(auth);
  } catch {
    return null;
  }
}

export async function signInWithGoogle() {
  if (!auth || !googleProvider) throw new Error('Firebase not configured');
  if (isNativeMobileApp()) return signInWithRedirect(auth, googleProvider);
  return signInWithPopup(auth, googleProvider);
}

export async function signInWithGitHub() {
  if (!auth || !githubProvider) throw new Error('Firebase not configured');
  if (isNativeMobileApp()) return signInWithRedirect(auth, githubProvider);
  return signInWithPopup(auth, githubProvider);
}

export async function resetPassword(email: string) {
  if (!auth) throw new Error('Firebase not configured');
  return sendPasswordResetEmail(auth, email);
}

export async function logOut() {
  if (!auth) throw new Error('Firebase not configured');
  return signOut(auth);
}

export function subscribeToAuth(callback: (user: User | null) => void) {
  if (!auth) {
    callback(null);
    return () => {};
  }
  return onAuthStateChanged(auth, callback);
}

export function formatAuthError(err: unknown): string {
  const code = (err as { code?: string })?.code || '';
  const map: Record<string, string> = {
    'auth/email-already-in-use': 'This email is already registered. Try signing in.',
    'auth/invalid-email': 'Please enter a valid email address.',
    'auth/user-not-found': 'No account found with this email.',
    'auth/wrong-password': 'Incorrect password. Try again or reset it.',
    'auth/invalid-credential': 'Invalid email or password.',
    'auth/weak-password': 'Password must be at least 6 characters.',
    'auth/popup-closed-by-user': 'Sign-in was cancelled.',
    'auth/account-exists-with-different-credential': 'An account already exists with a different sign-in method.',
    'auth/too-many-requests': 'Too many attempts. Please wait and try again.',
  };
  if (code && map[code]) return map[code];
  const message = err instanceof Error ? err.message : 'Authentication failed';
  return message.replace('Firebase: ', '').replace(/\(auth\/.*\)\.?/, '').trim();
}

