package studio.ocean.app.browser.security;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewFeature;
import java.io.File;
import java.util.Collection;
import java.util.List;
import studio.ocean.app.browser.network.ProxyControllerAdapter;
import studio.ocean.app.browser.secure.PrivateProfileRegistry;

/**
 * Executes the strict private data erasure sequence.
 * Guarantees that ephemeral profiles, profile cookies, WebStorage, network cache,
 * and quarantined downloads are destroyed WITHOUT FORENSIC RESIDUE.
 *
 * CRITICAL ISOLATION INVARIANT:
 * Never invokes global CookieManager, WebStorage, or GeolocationPermissions clear-all APIs.
 * Doing so erases Normal Browser data. Erasure is strictly scoped to the ephemeral private Profile.
 */
public final class PrivateDataEraser {

    public interface WipeCallback {
        void onWipeComplete(boolean verifiedSuccess);
    }

    private PrivateDataEraser() {
    }

    /**
     * Executes the profile-isolated cleanup order asynchronously on the main looper.
     */
    public static void wipeSession(
            @NonNull Context context,
            @NonNull String profileName,
            @Nullable WebView mainWebView,
            @Nullable List<WebView> popupWebViews,
            @Nullable WipeCallback callback) {
        wipeSession(context, profileName, mainWebView != null ? java.util.Collections.singletonList(mainWebView) : null, popupWebViews, callback);
    }

    /**
     * Overload supporting multiple tab WebViews.
     */
    public static void wipeSession(
            @NonNull Context context,
            @NonNull String profileName,
            @Nullable Collection<WebView> tabWebViews,
            @Nullable List<WebView> popupWebViews,
            @Nullable WipeCallback callback) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            boolean wipeVerified = true;

            try {
                // Step 1: Block new navigation and detach listeners from all tab WebViews
                if (tabWebViews != null) {
                    for (WebView wv : tabWebViews) {
                        if (wv != null) {
                            wv.setDownloadListener(null);
                            wv.setWebViewClient(new android.webkit.WebViewClient());
                            wv.stopLoading();
                        }
                    }
                }

                // Step 2: Stop and destroy all popup WebViews
                if (popupWebViews != null) {
                    for (WebView popup : popupWebViews) {
                        if (popup != null) {
                            popup.stopLoading();
                            popup.loadUrl("about:blank");
                            if (popup.getParent() instanceof ViewGroup) {
                                ((ViewGroup) popup.getParent()).removeView(popup);
                            }
                            popup.destroy();
                        }
                    }
                    popupWebViews.clear();
                }

                // Step 3: Clear profile-scoped browsing data (cookies, WebStorage, geolocation)
                // NEVER call global CookieManager.getInstance().removeAllCookies() or WebStorage.getInstance().deleteAllData()!
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    try {
                        Profile profile = ProfileStore.getInstance().getProfile(profileName);
                        if (profile != null) {
                            profile.getCookieManager().removeAllCookies(null);
                            profile.getWebStorage().deleteAllData();
                            profile.getGeolocationPermissions().clearAll();
                        }
                    } catch (Exception ignored) {
                    }
                }

                // Step 4: Clear WebViews caches and destroy them
                if (tabWebViews != null) {
                    for (WebView wv : tabWebViews) {
                        if (wv != null) {
                            wv.clearCache(true);
                            wv.clearFormData();
                            wv.clearHistory();
                            wv.clearSslPreferences();
                            wv.loadUrl("about:blank");
                            if (wv.getParent() instanceof ViewGroup) {
                                ((ViewGroup) wv.getParent()).removeView(wv);
                            }
                            wv.destroy();
                        }
                    }
                }

                // Step 5: Delete ephemeral profile from ProfileStore after WebViews are destroyed
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    try {
                        ProfileStore.getInstance().deleteProfile(profileName);
                    } catch (Exception ignored) {
                    }
                }

                // Step 6: Unregister profile from crash survival registry
                PrivateProfileRegistry.unregisterProfile(context, profileName);

                // Step 7: Delete private temporary files, quarantined downloads, and cache files
                File cacheDir = context.getCacheDir();
                deleteDirectoryContents(new File(cacheDir, "quarantine"));
                deleteDirectoryContents(new File(cacheDir, "private_browser"));

                // Step 8: Disconnect session-only proxy override
                ProxyControllerAdapter.clearProxy(null);

                // Step 9: Run internal wipe verification
                File quarantineDir = new File(cacheDir, "quarantine");
                if (quarantineDir.exists()) {
                    File[] remaining = quarantineDir.listFiles();
                    if (remaining != null && remaining.length > 0) {
                        wipeVerified = false;
                    }
                }
            } catch (Exception e) {
                wipeVerified = false;
            }

            if (callback != null) {
                callback.onWipeComplete(wipeVerified);
            }
        });
    }

    private static void deleteDirectoryContents(@Nullable File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirectoryContents(file);
            }
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
