package studio.ocean.app.browser.core;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Enforces URL safety, scheme validation, HTTPS upgrades, and tracking-parameter stripping.
 * Adheres strictly to Ocean Privacy Directive (PDF 4 §8.2, §9, §13.4).
 * Pure JVM-compatible design enables full unit testing without Android platform mocks.
 */
public final class NavigationPolicy {

    public static final Set<String> TRACKING_PARAMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "utm_source",
            "utm_medium",
            "utm_campaign",
            "utm_term",
            "utm_content",
            "utm_id",
            "utm_source_platform",
            "utm_creative_format",
            "utm_marketing_tactic",
            "fbclid",
            "gclid",
            "gclsrc",
            "dclid",
            "msclkid",
            "yclid",
            "mc_eid",
            "mc_cid",
            "_hsenc",
            "_hsmi",
            "igshid",
            "wbraid",
            "gbraid",
            "twclid",
            "si"
    )));

    private static final Set<String> EXTERNAL_SCHEMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "mailto", "tel", "sms", "intent", "market", "geo", "whatsapp", "tg"
    )));

    private static final Set<String> BLOCKED_WEB_SCHEMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "javascript", "file", "content", "jar"
    )));

    private NavigationPolicy() {
    }

    /**
     * Cleans tracking parameters from a URL if stripTrackingParams is true.
     * Preserves fragment (#) and valid functional query parameters.
     */
    @NonNull
    public static String cleanUrl(@Nullable String rawUrl, boolean stripTrackingParams) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        if (!stripTrackingParams || !trimmed.contains("?")) {
            return trimmed;
        }

        int qIndex = trimmed.indexOf('?');
        String base = trimmed.substring(0, qIndex);
        String rest = trimmed.substring(qIndex + 1);
        String fragment = "";
        int hashIndex = rest.indexOf('#');
        if (hashIndex >= 0) {
            fragment = rest.substring(hashIndex);
            rest = rest.substring(0, hashIndex);
        }

        String[] pairs = rest.split("&");
        StringBuilder cleanedQuery = new StringBuilder();
        for (String pair : pairs) {
            if (pair.isEmpty()) continue;
            int eqIndex = pair.indexOf('=');
            String key = eqIndex >= 0 ? pair.substring(0, eqIndex) : pair;
            if (!TRACKING_PARAMS.contains(key.toLowerCase(Locale.ROOT))) {
                if (cleanedQuery.length() > 0) {
                    cleanedQuery.append("&");
                }
                cleanedQuery.append(pair);
            }
        }

        if (cleanedQuery.length() > 0) {
            return base + "?" + cleanedQuery + fragment;
        } else {
            return base + fragment;
        }
    }

    /**
     * Upgrades an insecure http:// URL to https://.
     */
    @NonNull
    public static String upgradeToHttps(@NonNull String url) {
        if (url.startsWith("http://")) {
            return "https://" + url.substring(7);
        }
        return url;
    }

    public static boolean isHttps(@Nullable String url) {
        return url != null && url.toLowerCase(Locale.ROOT).startsWith("https://");
    }

    public static boolean isInsecureHttp(@Nullable String url) {
        return url != null && url.toLowerCase(Locale.ROOT).startsWith("http://");
    }

    /**
     * Checks whether a URL points to local loopback developer endpoints.
     * CRITICAL LOCALHOST SAFETY: Localhost/runtime services must never route through external tunnels.
     */
    public static boolean isLocalAddress(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String host = extractHost(url);
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("localhost")
                || lower.equals("127.0.0.1")
                || lower.equals("::1")
                || lower.equals("[::1]")
                || lower.endsWith(".local");
    }

    @Nullable
    public static String extractHost(@NonNull String url) {
        try {
            int schemeEnd = url.indexOf("://");
            int start = schemeEnd >= 0 ? schemeEnd + 3 : 0;
            if (start >= url.length()) return null;

            if (url.charAt(start) == '[') {
                int closeBracket = url.indexOf(']', start);
                if (closeBracket > start) {
                    return url.substring(start + 1, closeBracket);
                }
            }

            int slash = url.indexOf('/', start);
            int colon = url.indexOf(':', start);
            int end = url.length();
            if (slash >= 0 && slash < end) end = slash;
            if (colon >= 0 && colon < end) end = colon;
            int quest = url.indexOf('?', start);
            if (quest >= 0 && quest < end) end = quest;
            return url.substring(start, end);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Identifies schemes that must not be loaded directly by arbitrary web content.
     */
    public static boolean isDangerousWebScheme(@Nullable String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT).trim();
        for (String s : BLOCKED_WEB_SCHEMES) {
            if (lower.startsWith(s + ":")) return true;
        }
        return false;
    }

    /**
     * Identifies schemes that should trigger an external Android intent handoff.
     */
    public static boolean isExternalScheme(@Nullable Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        String lower = scheme.toLowerCase(Locale.ROOT);
        return EXTERNAL_SCHEMES.contains(lower);
    }
}
