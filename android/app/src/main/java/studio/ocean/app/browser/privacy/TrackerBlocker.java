package studio.ocean.app.browser.privacy;

import android.net.Uri;
import android.webkit.WebResourceResponse;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance tracker and ad request blocking engine (PDF 4 §8.1).
 * Intercepts third-party analytics, tracking pixels, telemetry endpoints, and known ad networks.
 * Returns lightweight empty 204 responses to prevent page layout breakages.
 */
public final class TrackerBlocker {

    private static final List<String> TRACKER_DOMAINS = Collections.unmodifiableList(Arrays.asList(
            "google-analytics.com",
            "googletagmanager.com",
            "googletagservices.com",
            "doubleclick.net",
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "analytics.facebook.com",
            "pixel.facebook.com",
            "connect.facebook.net",
            "graph.facebook.com/tr",
            "ads-twitter.com",
            "analytics.twitter.com",
            "criteo.com",
            "criteo.net",
            "outbrain.com",
            "taboola.com",
            "scorecardresearch.com",
            "quantserve.com",
            "segment.io",
            "segment.com",
            "api.mixpanel.com",
            "hotjar.com",
            "clarity.ms",
            "appsflyer.com",
            "adjust.com",
            "branch.io",
            "adroll.com",
            "rubiconproject.com",
            "pubmatic.com",
            "openx.net",
            "casalemedia.com",
            "moatads.com",
            "adnxs.com",
            "advertising.com",
            "yieldmanager.com",
            "smartadserver.com",
            "app-measurement.com",
            "newrelic.com",
            "nr-data.net"
    ));

    private final Set<String> perSiteAllowlist = new HashSet<>();
    private final AtomicInteger blockedCounter = new AtomicInteger(0);
    private boolean enabled = true;

    public TrackerBlocker() {
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getBlockedCount() {
        return blockedCounter.get();
    }

    public void resetBlockedCount() {
        blockedCounter.set(0);
    }

    public synchronized void setSiteAllowed(@NonNull String host, boolean allowed) {
        String lower = host.toLowerCase(Locale.ROOT).trim();
        if (allowed) {
            perSiteAllowlist.add(lower);
        } else {
            perSiteAllowlist.remove(lower);
        }
    }

    public synchronized boolean isSiteAllowed(@Nullable String host) {
        if (host == null) return false;
        return perSiteAllowlist.contains(host.toLowerCase(Locale.ROOT).trim());
    }

    /**
     * Determines whether the requested resource URL is a known tracker and should be blocked.
     */
    public boolean shouldBlock(@Nullable String requestUrl, @Nullable String mainFrameUrl) {
        if (!enabled || requestUrl == null || requestUrl.trim().isEmpty()) {
            return false;
        }

        try {
            String requestHost = studio.ocean.app.browser.core.NavigationPolicy.extractHost(requestUrl);
            if (requestHost == null) return false;
            String lowerReqHost = requestHost.toLowerCase(Locale.ROOT);

            // Never block localhost, 127.0.0.1, or local developer servers
            if (lowerReqHost.equals("localhost") || lowerReqHost.equals("127.0.0.1") || lowerReqHost.endsWith(".local")) {
                return false;
            }

            // Check if user explicitly allowed the main frame site
            if (mainFrameUrl != null) {
                String mainHost = studio.ocean.app.browser.core.NavigationPolicy.extractHost(mainFrameUrl);
                if (mainHost != null && isSiteAllowed(mainHost)) {
                    return false;
                }
            }

            // Check against known tracker domain patterns
            for (String domain : TRACKER_DOMAINS) {
                if (lowerReqHost.equals(domain) || lowerReqHost.endsWith("." + domain)) {
                    blockedCounter.incrementAndGet();
                    return true;
                }
            }

            // Check specific tracking path indicators
            if (requestUrl.contains("/telemetry") || requestUrl.contains("/collect") || requestUrl.contains("/pageview")) {
                if (isKnownThirdPartyTracker(requestUrl, mainFrameUrl)) {
                    blockedCounter.incrementAndGet();
                    return true;
                }
            }

            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isKnownThirdPartyTracker(String requestUrl, String mainFrameUrl) {
        if (mainFrameUrl == null) return false;
        try {
            String rHost = studio.ocean.app.browser.core.NavigationPolicy.extractHost(requestUrl);
            String mHost = studio.ocean.app.browser.core.NavigationPolicy.extractHost(mainFrameUrl);
            if (rHost == null || mHost == null) return false;
            return !rHost.equalsIgnoreCase(mHost);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Returns an empty HTTP 204 No Content response to satisfy the WebView without breaking layout.
     */
    @NonNull
    public static WebResourceResponse createBlockedResponse() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("X-Ocean-Firewall", "Blocked-Tracker");
        return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content", headers, new ByteArrayInputStream(new byte[0]));
    }
}
