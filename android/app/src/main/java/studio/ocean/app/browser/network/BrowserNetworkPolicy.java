package studio.ocean.app.browser.network;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enforces network privacy routing, localhost bypass rules, and kill-switch state (PDF 4 §6, §7).
 * HARD RULE: Never send localhost, 127.0.0.1, ::1, or Ocean Runtime Ports to a remote proxy/tunnel.
 */
public final class BrowserNetworkPolicy {

    public enum Mode {
        DIRECT("Direct · No IP change"),
        PROXY("Proxy"),
        TOR_ORBOT("Tor / Orbot exit"),
        VPN_TUNNEL("Tunnel");

        public final String displayLabel;

        Mode(String displayLabel) {
            this.displayLabel = displayLabel;
        }
    }

    public static final List<String> LOCAL_BYPASS_RULES = Collections.unmodifiableList(Arrays.asList(
            "<local>",
            "localhost",
            "127.0.0.1",
            "[::1]",
            "::1",
            "*.local"
    ));

    private Mode mode = Mode.DIRECT;
    @Nullable private String proxyHost;
    private int proxyPort;
    private boolean tunnelConnected = true;
    private boolean killSwitchEnabled = false;

    public BrowserNetworkPolicy() {
    }

    public synchronized Mode getMode() {
        return mode;
    }

    public synchronized void setDirect() {
        this.mode = Mode.DIRECT;
        this.proxyHost = null;
        this.proxyPort = 0;
        this.tunnelConnected = true;
    }

    public synchronized void setProxy(@NonNull String host, int port) {
        this.mode = Mode.PROXY;
        this.proxyHost = host;
        this.proxyPort = port;
        this.tunnelConnected = true;
    }

    public synchronized void setTorOrbot() {
        this.mode = Mode.TOR_ORBOT;
        this.proxyHost = "127.0.0.1";
        this.proxyPort = 9050;
        this.tunnelConnected = true;
    }

    public synchronized void setVpnTunnel() {
        this.mode = Mode.VPN_TUNNEL;
        this.tunnelConnected = true;
    }

    public synchronized void setTunnelConnected(boolean connected) {
        this.tunnelConnected = connected;
    }

    public synchronized boolean isTunnelConnected() {
        return tunnelConnected;
    }

    public synchronized void setKillSwitchEnabled(boolean enabled) {
        this.killSwitchEnabled = enabled;
    }

    public synchronized boolean isKillSwitchEnabled() {
        return killSwitchEnabled;
    }

    @Nullable
    public synchronized String getProxyHost() {
        return proxyHost;
    }

    public synchronized int getProxyPort() {
        return proxyPort;
    }

    /**
     * Verifies if a given destination URL must bypass any remote proxy/tunnel and stay strictly local.
     */
    public static boolean shouldBypassProxy(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return true;
        return studio.ocean.app.browser.core.NavigationPolicy.isLocalAddress(url);
    }

    /**
     * Determines whether navigation should be blocked due to kill switch activation.
     */
    public synchronized boolean isNavigationBlockedByKillSwitch(@Nullable String url) {
        if (shouldBypassProxy(url)) {
            // Localhost requests are never blocked by external tunnel kill switch
            return false;
        }
        if (killSwitchEnabled && (mode == Mode.PROXY || mode == Mode.TOR_ORBOT || mode == Mode.VPN_TUNNEL)) {
            return !tunnelConnected;
        }
        return false;
    }

    @NonNull
    public synchronized String getRouteStatusSummary() {
        switch (mode) {
            case DIRECT:
                return "Direct · No IP change";
            case PROXY:
                return "Proxy · " + (proxyHost != null ? proxyHost + ":" + proxyPort : "Configured");
            case TOR_ORBOT:
                return "Tor / Orbot · SOCKS 9050";
            case VPN_TUNNEL:
                return "Ocean Tunnel · " + (tunnelConnected ? "Connected" : "Disconnected");
            default:
                return "Direct";
        }
    }
}
