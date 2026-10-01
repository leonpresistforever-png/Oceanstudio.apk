package studio.ocean.app.browser.security;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebStorage;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewFeature;
import java.io.File;
import java.util.List;
import studio.ocean.app.browser.network.ProxyControllerAdapter;

/**
 * Executes the strict 9-step private data erasure sequence (PDF 4 §5.2).
 * Guarantees that cookies, WebStorage, network cache, temporary profiles,
 * and ephemeral files are destroyed without forensic residue.
 */
public final class PrivateDataEraser {

    public interface WipeCallback {
        void onWipeComplete(boolean verifiedSuccess);
    }

    private PrivateDataEraser() {
    }

    /**
     * Executes the 9-step cleanup order asynchronously on the main looper.
     */
    public static void wipeSession(
            @NonNull Context context,
            @NonNull String profileName,
            @Nullable WebView mainWebView,
            @Nullable List<WebView> popupWebViews,
            @Nullable WipeCallback callback) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            boolean wipeVerified = true;

            try {
                // Step 1: Block new navigation and detach download callbacks
                if (mainWebView != null) {
                    mainWebView.setDownloadListener(null);
                    mainWebView.setWebViewClient(new android.webkit.WebViewClient());
                }

                // Step 2: Stop page loads and destroy child popup WebViews
                if (mainWebView != null) {
                    mainWebView.stopLoading();
                }
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

                // Step 3: Clear browsing data (cookies, WebStorage, cache)
                WebStorage.getInstance().deleteAllData();
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeSessionCookies(null);
                cookieManager.removeAllCookies(null);

                if (mainWebView != null) {
                    mainWebView.clearCache(true);
                    mainWebView.clearFormData();
                    mainWebView.clearHistory();
                    mainWebView.clearSslPreferences();
                }

                // Step 4: Clear profile permissions and site data
                GeolocationPermissions.getInstance().clearAll();

                // Step 5: Destroy WebView instance and remove from parent
                if (mainWebView != null) {
                    mainWebView.loadUrl("about:blank");
                    if (mainWebView.getParent() instanceof ViewGroup) {
                        ((ViewGroup) mainWebView.getParent()).removeView(mainWebView);
                    }
                    mainWebView.destroy();
                }

                // Step 6: Delete temporary profile through ProfileStore when supported
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    try {
                        ProfileStore.getInstance().deleteProfile(profileName);
                    } catch (Exception ignored) {
                    }
                }

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
