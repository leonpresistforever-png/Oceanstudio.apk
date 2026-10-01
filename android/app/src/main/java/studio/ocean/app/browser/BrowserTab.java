package studio.ocean.app.browser;

import android.os.Bundle;
import androidx.annotation.Nullable;
import java.util.UUID;

/**
 * One browser tab (Lightning-style tab model: URL + optional WebView state + security tags).
 * Shared across normal and private browsing contexts; in private contexts tab state is strictly ephemeral.
 */
public final class BrowserTab {
    public final String id;
    public String url;
    public String title;
    public boolean desktopSite;
    public boolean pinned;
    public boolean locked;
    @Nullable public String isolatedProfileName;
    @Nullable public Bundle webState;

    public BrowserTab(String url) {
        this(UUID.randomUUID().toString().substring(0, 8), url, "", false, false, false, null);
    }

    public BrowserTab(String id, String url, String title, boolean desktopSite, boolean pinned, boolean locked, @Nullable String isolatedProfileName) {
        this.id = id != null ? id : UUID.randomUUID().toString().substring(0, 8);
        this.url = url != null ? url : "";
        this.title = title != null ? title : "";
        this.desktopSite = desktopSite;
        this.pinned = pinned;
        this.locked = locked;
        this.isolatedProfileName = isolatedProfileName;
    }

    public BrowserTab copy() {
        BrowserTab cloned = new BrowserTab(UUID.randomUUID().toString().substring(0, 8), url, title, desktopSite, pinned, locked, isolatedProfileName);
        if (webState != null) {
            cloned.webState = new Bundle(webState);
        }
        return cloned;
    }
}
