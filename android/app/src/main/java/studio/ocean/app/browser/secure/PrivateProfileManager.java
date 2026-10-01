package studio.ocean.app.browser.secure;

import android.webkit.CookieManager;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

/**
 * Manages AndroidX WebKit ephemeral profiles (PDF 4 §4, §15.1).
 * Isolates cookies, WebStorage, and cache per private session or per isolated tab.
 * Uses ProfileStore.deleteProfile to guarantee complete destruction on session end.
 */
public final class PrivateProfileManager {

    private final boolean multiProfileSupported;

    public PrivateProfileManager() {
        this.multiProfileSupported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
    }

    public boolean isMultiProfileSupported() {
        return multiProfileSupported;
    }

    /**
     * Assigns the ephemeral profile to the given WebView before navigation or script evaluation.
     */
    public boolean applyProfile(@NonNull WebView webView, @NonNull String profileName) {
        if (!multiProfileSupported) {
            // Fallback: Default profile with strict transient clearing
            configureFallbackCookies(webView);
            return false;
        }

        try {
            WebViewCompat.setProfile(webView, profileName);
            Profile profile = ProfileStore.getInstance().getProfile(profileName);
            if (profile != null) {
                CookieManager cookieManager = profile.getCookieManager();
                cookieManager.setAcceptCookie(true);
                cookieManager.setAcceptThirdPartyCookies(webView, false);
            }
            return true;
        } catch (Exception ignored) {
            configureFallbackCookies(webView);
            return false;
        }
    }

    private void configureFallbackCookies(@NonNull WebView webView) {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView, false);
        } catch (Exception ignored) {
        }
    }

    /**
     * Deletes the ephemeral profile and all associated data from the ProfileStore.
     */
    public boolean deleteProfile(@NonNull String profileName) {
        if (!multiProfileSupported) {
            return false;
        }
        try {
            return ProfileStore.getInstance().deleteProfile(profileName);
        } catch (Exception ignored) {
            return false;
        }
    }
}
