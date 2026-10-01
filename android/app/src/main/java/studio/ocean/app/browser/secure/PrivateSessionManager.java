package studio.ocean.app.browser.secure;

import androidx.annotation.NonNull;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the lifecycle of a Private Browsing session (PDF 4 §5, §13).
 * Generates cryptographically random UUIDs, unique profile names, and enforces
 * privacy presets (Balanced, Strict, Custom).
 */
public final class PrivateSessionManager {

    public enum Preset {
        BALANCED("Balanced"),
        STRICT("Strict"),
        CUSTOM("Custom");

        public final String label;
        Preset(String label) {
            this.label = label;
        }
    }

    private final String sessionUuid;
    private String profileName;
    private final long createdAt;
    private Preset preset = Preset.BALANCED;

    private boolean httpsOnly = false;
    private boolean perTabIsolation = false;
    private boolean autoCloseEnabled = true;
    private long autoCloseTimeoutMinutes = 15;

    private final AtomicInteger trackerBlockCount = new AtomicInteger(0);
    private final AtomicInteger popupBlockCount = new AtomicInteger(0);

    public PrivateSessionManager() {
        this.sessionUuid = UUID.randomUUID().toString();
        this.profileName = "private_" + sessionUuid.substring(0, 8);
        this.createdAt = System.currentTimeMillis();
    }

    @NonNull
    public String getSessionUuid() {
        return sessionUuid;
    }

    @NonNull
    public synchronized String getProfileName() {
        return profileName;
    }

    public synchronized void setPreset(@NonNull Preset preset) {
        this.preset = preset;
        if (preset == Preset.STRICT) {
            this.httpsOnly = true;
            this.perTabIsolation = false; // can be enabled per user choice
        } else if (preset == Preset.BALANCED) {
            this.httpsOnly = false;
            this.perTabIsolation = false;
        }
    }

    @NonNull
    public synchronized Preset getPreset() {
        return preset;
    }

    public synchronized void setHttpsOnly(boolean httpsOnly) {
        this.httpsOnly = httpsOnly;
    }

    public synchronized boolean isHttpsOnly() {
        return httpsOnly;
    }

    public synchronized void setPerTabIsolation(boolean perTabIsolation) {
        this.perTabIsolation = perTabIsolation;
    }

    public synchronized boolean isPerTabIsolation() {
        return perTabIsolation;
    }

    public synchronized void setAutoCloseTimeoutMinutes(long minutes) {
        this.autoCloseTimeoutMinutes = minutes;
    }

    public synchronized long getAutoCloseTimeoutMinutes() {
        return autoCloseTimeoutMinutes;
    }

    public synchronized boolean isAutoCloseEnabled() {
        return autoCloseEnabled;
    }

    public synchronized void setAutoCloseEnabled(boolean enabled) {
        this.autoCloseEnabled = enabled;
    }

    public void incrementTrackerBlocked() {
        trackerBlockCount.incrementAndGet();
    }

    public int getTrackerBlockCount() {
        return trackerBlockCount.get();
    }

    public void incrementPopupBlocked() {
        popupBlockCount.incrementAndGet();
    }

    public int getPopupBlockCount() {
        return popupBlockCount.get();
    }

    public long getElapsedMinutes() {
        long elapsed = System.currentTimeMillis() - createdAt;
        return TimeUnit.MILLISECONDS.toMinutes(elapsed);
    }

    @NonNull
    public String getFormattedDuration() {
        long minutes = getElapsedMinutes();
        if (minutes < 1) {
            return "< 1 min";
        }
        return minutes + " min";
    }

    /**
     * Rotates session identity for the "New Identity" feature (PDF 4 §13.2).
     */
    public synchronized String rotateIdentity() {
        trackerBlockCount.set(0);
        popupBlockCount.set(0);
        this.profileName = "private_" + UUID.randomUUID().toString().substring(0, 8);
        return this.profileName;
    }
}
