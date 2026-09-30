package studio.ocean.app.browser;

import android.os.Bundle;
import androidx.annotation.Nullable;

/** One browser tab (Lightning-style tab model: URL + optional WebView state). */
final class BrowserTab {
    String url;
    String title;
    boolean desktopSite;
    @Nullable Bundle webState;

    BrowserTab(String url) {
        this.url = url;
        this.title = "";
        this.desktopSite = false;
    }
}
