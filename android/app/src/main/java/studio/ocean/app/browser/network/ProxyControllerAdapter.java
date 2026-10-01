package studio.ocean.app.browser.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Adapter for AndroidX WebKit ProxyController (PDF 4 §15.4).
 * Enforces PROXY_OVERRIDE support check and explicit localhost/runtime bypass rules.
 */
public final class ProxyControllerAdapter {

    public interface ProxyCallback {
        void onSuccess();
        void onFailure(String error);
    }

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    private ProxyControllerAdapter() {
    }

    public static boolean isProxyOverrideSupported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE);
    }

    /**
     * Applies a remote proxy configuration with strict localhost and runtime bypass rules.
     */
    public static void applyProxy(
            @NonNull String proxyUrl,
            @Nullable ProxyCallback callback) {

        if (!isProxyOverrideSupported()) {
            if (callback != null) {
                callback.onFailure("PROXY_OVERRIDE feature is not supported by the installed WebView");
            }
            return;
        }

        try {
            ProxyConfig.Builder builder = new ProxyConfig.Builder()
                    .addProxyRule(proxyUrl);

            for (String bypass : BrowserNetworkPolicy.LOCAL_BYPASS_RULES) {
                builder.addBypassRule(bypass);
            }

            ProxyConfig config = builder.build();
            ProxyController.getInstance().setProxyOverride(config, EXECUTOR, () -> {
                if (callback != null) {
                    callback.onSuccess();
                }
            });
        } catch (Exception e) {
            if (callback != null) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Failed to apply proxy");
            }
        }
    }

    /**
     * Clears any active proxy override, returning traffic to direct routing.
     */
    public static void clearProxy(@Nullable ProxyCallback callback) {
        if (!isProxyOverrideSupported()) {
            if (callback != null) callback.onSuccess();
            return;
        }

        try {
            ProxyController.getInstance().clearProxyOverride(EXECUTOR, () -> {
                if (callback != null) {
                    callback.onSuccess();
                }
            });
        } catch (Exception e) {
            if (callback != null) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Failed to clear proxy");
            }
        }
    }
}
