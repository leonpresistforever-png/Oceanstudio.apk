package studio.ocean.app.browser;

import android.net.Uri;
import androidx.annotation.Nullable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Normalizes user-entered locations for the Ocean Browser URL bar. */
public final class BrowserUrlHelper {
    public static final String DEFAULT_HOME = "https://duckduckgo.com/";

    private BrowserUrlHelper() {}

    public static String normalizeInput(@Nullable String raw) {
        if (raw == null) return DEFAULT_HOME;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return DEFAULT_HOME;
        if (trimmed.contains("\u0000")) return DEFAULT_HOME;
        if (trimmed.startsWith("about:") || trimmed.startsWith("data:")) return trimmed;
        if (looksLikeUrl(trimmed)) {
            if (!trimmed.contains("://")) trimmed = "https://" + trimmed;
            return trimmed;
        }
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
    }

    public static boolean isSecureUrl(@Nullable String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("https://") || lower.startsWith("about:blank");
    }

    public static boolean shouldLeaveWebView(@Nullable Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        String lower = scheme.toLowerCase(Locale.US);
        return !lower.equals("http") && !lower.equals("https") && !lower.equals("about");
    }

    private static boolean looksLikeUrl(String value) {
        if (value.contains(" ")) return false;
        int dot = value.indexOf('.');
        if (dot > 0 && dot < value.length() - 1) return true;
        return value.contains("://") || value.startsWith("localhost") || value.startsWith("127.0.0.1");
    }
}
