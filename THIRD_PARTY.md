# Third-party open source

Ocean.studio for Android uses the following Apache-2.0, MIT, or BSD-compatible libraries beyond the Android SDK.

| Library | License | Use in Ocean.studio |
| --- | --- | --- |
| [AndroidX AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat) | Apache-2.0 | App UI shell |
| [AndroidX Core Splash Screen](https://developer.android.com/jetpack/androidx/releases/core) | Apache-2.0 | Launch splash |
| [AndroidX WebKit](https://developer.android.com/jetpack/androidx/releases/webkit) | Apache-2.0 | In-app Browser (desktop UA metadata, WebView helpers) |
| [Firebase Android SDK](https://firebase.google.com/docs/android/setup) (BOM) | Apache-2.0 | Analytics, Auth, native OAuth |
| [Google Play services Auth](https://developers.google.com/android/guides/setup) | Android SDK / OSS components | Google Sign-In for Firebase |
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | Apache-2.0 | Archive handling in bootstrap |
| [zstd-jni](https://github.com/luben/zstd-jni) | BSD-2-Clause | Zstandard decompression |
| [JUnit](https://junit.org/) | EPL-2.0 (test only) | Unit tests |
| [AndroidX Test / Espresso](https://developer.android.com/training/testing) | Apache-2.0 | Instrumentation tests |
| [org.json](https://github.com/stleary/JSON-java) (test) | Public domain | Unit test JSON |

## Browser design notes

The in-app **Browser** uses the platform **System WebView** (Chromium-based on most devices) with **AndroidX WebKit** helpers. Tab switching follows patterns familiar from MIT-licensed minimal browsers (for example [Lightning Browser](https://github.com/anthonycr/Lightning-Browser)); Ocean.studio does not ship Lightning code or use GeckoView, to keep APK size down.
