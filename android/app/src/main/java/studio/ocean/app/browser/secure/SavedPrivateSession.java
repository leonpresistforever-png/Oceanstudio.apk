package studio.ocean.app.browser.secure;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import studio.ocean.app.browser.BrowserUrlHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Workspace manifest representing a saved private session.
 *
 * CRITICAL EPHEMERAL PRIVACY GUARANTEE (Directive 2026-10-02 §14.3):
 * This class persists ONLY tab URLs, tab order, titles, and pinned states.
 * Under NO circumstances does it persist:
 *   - cookies
 *   - WebStorage (localStorage, sessionStorage)
 *   - IndexedDB
 *   - cache
 *   - service workers
 *   - permissions
 *   - form data
 *   - login / session credentials
 *   - private WebView profile directories
 *
 * When restored, this manifest creates a BRAND NEW ephemeral profile with
 * a fresh random identity and navigates to the saved URLs cleanly.
 */
public final class SavedPrivateSession {

    public static final class TabEntry {
        @NonNull public final String url;
        @NonNull public final String title;
        public final boolean pinned;

        public TabEntry(@NonNull String url, @NonNull String title, boolean pinned) {
            this.url = url;
            this.title = title;
            this.pinned = pinned;
        }

        @NonNull
        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("url", url);
            obj.put("title", title);
            obj.put("pinned", pinned);
            return obj;
        }

        @NonNull
        public static TabEntry fromJson(@NonNull JSONObject obj) {
            String url = obj.optString("url", BrowserUrlHelper.DEFAULT_HOME);
            String title = obj.optString("title", "");
            boolean pinned = obj.optBoolean("pinned", false);
            return new TabEntry(url, title, pinned);
        }
    }

    @NonNull private final String id;
    @NonNull private String name;
    private final long createdAt;
    private long lastOpenedAt;
    @NonNull private final List<TabEntry> tabs;

    public SavedPrivateSession(@NonNull String name, @NonNull List<TabEntry> tabs) {
        this(UUID.randomUUID().toString(), name, System.currentTimeMillis(), System.currentTimeMillis(), tabs);
    }

    public SavedPrivateSession(@NonNull String id, @NonNull String name, long createdAt, long lastOpenedAt, @NonNull List<TabEntry> tabs) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.lastOpenedAt = lastOpenedAt;
        this.tabs = new ArrayList<>(tabs);
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastOpenedAt() {
        return lastOpenedAt;
    }

    public void setLastOpenedAt(long lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }

    @NonNull
    public List<TabEntry> getTabs() {
        return Collections.unmodifiableList(tabs);
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("createdAt", createdAt);
        obj.put("lastOpenedAt", lastOpenedAt);

        JSONArray tabsArr = new JSONArray();
        for (TabEntry t : tabs) {
            tabsArr.put(t.toJson());
        }
        obj.put("tabs", tabsArr);
        return obj;
    }

    @Nullable
    public static SavedPrivateSession fromJson(@NonNull JSONObject obj) {
        try {
            String id = obj.getString("id");
            String name = obj.optString("name", "Untitled Session");
            long createdAt = obj.optLong("createdAt", System.currentTimeMillis());
            long lastOpenedAt = obj.optLong("lastOpenedAt", createdAt);

            List<TabEntry> tabs = new ArrayList<>();
            JSONArray tabsArr = obj.optJSONArray("tabs");
            if (tabsArr != null) {
                for (int i = 0; i < tabsArr.length(); i++) {
                    JSONObject tabObj = tabsArr.optJSONObject(i);
                    if (tabObj != null) {
                        tabs.add(TabEntry.fromJson(tabObj));
                    }
                }
            }
            return new SavedPrivateSession(id, name, createdAt, lastOpenedAt, tabs);
        } catch (Exception e) {
            return null;
        }
    }
}
