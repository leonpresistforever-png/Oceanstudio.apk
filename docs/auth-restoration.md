# Authentication restoration source audit

The authoritative historical implementation is commit `4d745d7` (the revision immediately before commit `3355fde` removed the web shell during native-only conversion).

- **Framework:** React 19 + React Router, built by Vite 6 and hosted by Capacitor 7 on Android.
- **Component:** `src/pages/AuthPage.tsx` rendered sign-in, sign-up, forgot-password, Firebase actions, Google/GitHub buttons, and mode transitions.
- **Page styling:** `src/pages/AuthPage.css` defined the compact 400px container, 36px logo, three 3px progress segments, Inter typography, 10px radii, 1px borders, field labels, 48px mobile controls, divider lines, and underlined footer action.
- **Global styling:** `src/styles/global.css` defined Inter/system fallbacks and the warm Stone palette: `#FAFAF9`, `#FFFFFF`, `#1C1917`, `#78716C`, `#A8A29E`, and `#E7E5E4`.
- **Logo:** `public/ocean-icon.svg` is the original 64×64 warm rounded square with two curved strokes and a small warm dot. It is ported geometrically to `res/drawable/ic_ocean_mark.xml`.
- **Google icon:** an inline four-color SVG in `src/pages/AuthPage.tsx`, ported to `res/drawable/ic_google.xml`.
- **GitHub icon:** an inline GitHub silhouette SVG in `src/pages/AuthPage.tsx`, ported to `res/drawable/ic_github.xml`.
- **Authentication:** `src/lib/firebase.ts` used Firebase email/password, account creation, password reset, Google, GitHub, persisted Firebase state, and formatted errors. Its historical missing-config navigation bypass was deliberately not restored as production behavior.

The current implementation is a native Android port. It does not restore React, Vite, Capacitor, or a WebView. Firebase email actions use the Identity Toolkit endpoint when configured. The temporary local session requires both `BuildConfig.DEBUG` and the separately supplied `OCEAN_DEV_AUTH_BYPASS=true` build flag, is never stored as a Firebase session, and cannot activate in release because `BuildConfig.DEBUG` is false.
