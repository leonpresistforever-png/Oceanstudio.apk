package studio.ocean.app.browser.privacy;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Firewall for popups, window openings, and aggressive redirect loops (PDF 4 §8.2).
 * Strictly requires user gestures for window opening in Private mode and rate-limits rapid redirects.
 */
public final class PopupFirewall {

    public interface PopupPromptCallback {
        void onPromptPopup(String url, String host, boolean isUserGesture, DecisionCallback callback);
    }

    public interface DecisionCallback {
        void onDecision(boolean allow, boolean rememberForSession);
    }

    private static final int RATE_LIMIT_WINDOW_MS = 4000;
    private static final int MAX_REQUESTS_IN_WINDOW = 3;

    private final Set<String> sessionAllowedHosts = new HashSet<>();
    private final LinkedList<Long> recentRequests = new LinkedList<>();
    private final AtomicInteger blockedCount = new AtomicInteger(0);

    public PopupFirewall() {
    }

    public int getBlockedCount() {
        return blockedCount.get();
    }

    public void resetBlockedCount() {
        blockedCount.set(0);
    }

    public synchronized void setSessionAllowed(@NonNull String host) {
        sessionAllowedHosts.add(host.toLowerCase(Locale.ROOT).trim());
    }

    public synchronized boolean isSessionAllowed(@Nullable String host) {
        if (host == null) return false;
        return sessionAllowedHosts.contains(host.toLowerCase(Locale.ROOT).trim());
    }

    /**
     * Checks if rapid redirect/popup rate limit is exceeded.
     */
    public synchronized boolean isRateLimited() {
        long now = System.currentTimeMillis();
        recentRequests.addLast(now);
        while (!recentRequests.isEmpty() && now - recentRequests.peekFirst() > RATE_LIMIT_WINDOW_MS) {
            recentRequests.removeFirst();
        }
        if (recentRequests.size() > MAX_REQUESTS_IN_WINDOW) {
            blockedCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Evaluates whether a popup request should be allowed or prompted.
     * Returns true if allowed immediately, false if blocked or pending prompt.
     */
    public synchronized boolean evaluatePopup(
            @Nullable String targetUrl,
            boolean isUserGesture,
            @Nullable PopupPromptCallback promptCallback,
            @NonNull DecisionCallback onDecision) {

        if (isRateLimited()) {
            onDecision.onDecision(false, false);
            return false;
        }

        String extractedHost = "";
        if (targetUrl != null) {
            String extracted = studio.ocean.app.browser.core.NavigationPolicy.extractHost(targetUrl);
            if (extracted != null) extractedHost = extracted.toLowerCase(Locale.ROOT);
        }
        final String finalHost = extractedHost;

        if (!finalHost.isEmpty() && isSessionAllowed(finalHost)) {
            onDecision.onDecision(true, false);
            return true;
        }

        if (!isUserGesture) {
            // Block non-gesture popups automatically in strict mode
            blockedCount.incrementAndGet();
            onDecision.onDecision(false, false);
            return false;
        }

        if (promptCallback != null) {
            promptCallback.onPromptPopup(targetUrl, finalHost, isUserGesture, (allow, remember) -> {
                if (allow) {
                    if (remember && !finalHost.isEmpty()) {
                        setSessionAllowed(finalHost);
                    }
                } else {
                    blockedCount.incrementAndGet();
                }
                onDecision.onDecision(allow, remember);
            });
            return false;
        }

        // Default to allow if user gesture is confirmed and no UI callback is registered
        return true;
    }
}
