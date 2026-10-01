package studio.ocean.app.browser.network;

import androidx.annotation.NonNull;

/**
 * Tracks DNS resolver policy and leak protection status (PDF 4 §7).
 * Reports true observable technical status (no fake DNS badges).
 */
public final class DnsPolicy {

    public enum DnsMode {
        DIRECT_SYSTEM("Direct DNS"),
        TUNNEL_DNS("Tunnel DNS"),
        ENCRYPTED_DOH("Encrypted resolver (DoH)");

        public final String label;

        DnsMode(String label) {
            this.label = label;
        }
    }

    private DnsMode mode = DnsMode.DIRECT_SYSTEM;
    private String customDohUrl = "https://cloudflare-dns.com/dns-query";

    public DnsPolicy() {
    }

    public synchronized void setMode(@NonNull DnsMode mode) {
        this.mode = mode;
    }

    @NonNull
    public synchronized DnsMode getMode() {
        return mode;
    }

    public synchronized void setCustomDohUrl(@NonNull String url) {
        this.customDohUrl = url;
    }

    @NonNull
    public synchronized String getCustomDohUrl() {
        return customDohUrl;
    }

    @NonNull
    public synchronized String getDnsStatusSummary() {
        return mode.label;
    }
}
