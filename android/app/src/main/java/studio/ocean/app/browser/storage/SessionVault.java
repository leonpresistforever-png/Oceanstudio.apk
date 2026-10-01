package studio.ocean.app.browser.storage;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.browser.BrowserTab;

/**
 * Session Vault for preserving tab navigation structure across sessions.
 * HARD RULE (PDF 4 §11):
 * Never stores cookies, LocalStorage, passwords, credentials, headers, or cache data.
 * When restored into Private Browser, tabs open in a brand-new ephemeral profile.
 */
public final class SessionVault {

    private static final String DIR_NAME = "browser_sessions";

    public static final class SavedTab {
        public final String url;
        public final String title;
        public final boolean pinned;
        public final boolean locked;

        public SavedTab(@NonNull String url, @NonNull String title, boolean pinned, boolean locked) {
            this.url = url;
            this.title = title;
            this.pinned = pinned;
            this.locked = locked;
        }

        public BrowserTab toBrowserTab() {
            BrowserTab tab = new BrowserTab(url);
            tab.title = title;
            tab.pinned = pinned;
            tab.locked = locked;
            return tab;
        }
    }

    public static final class SavedSession {
        public final String name;
        public final long createdAt;
        @Nullable public final String networkProfile;
        public final List<SavedTab> tabs;

        public SavedSession(@NonNull String name, long createdAt, @Nullable String networkProfile, @NonNull List<SavedTab> tabs) {
            this.name = name;
            this.createdAt = createdAt;
            this.networkProfile = networkProfile;
            this.tabs = Collections.unmodifiableList(new ArrayList<>(tabs));
        }
    }

    private final File storageDir;

    public SessionVault(@NonNull Context context) {
        this(new File(context.getApplicationContext().getFilesDir(), DIR_NAME));
    }

    public SessionVault(@NonNull File storageDir) {
        this.storageDir = storageDir;
        if (!storageDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            storageDir.mkdirs();
        }
    }

    public synchronized boolean saveSession(@NonNull String name, @NonNull List<BrowserTab> activeTabs, @Nullable String networkProfile) {
        String cleanName = sanitizeName(name);
        if (cleanName.isEmpty() || activeTabs.isEmpty()) {
            return false;
        }

        try {
            JSONObject root = new JSONObject();
            root.put("name", name);
            root.put("createdAt", System.currentTimeMillis());
            if (networkProfile != null) {
                root.put("networkProfile", networkProfile);
            }

            JSONArray tabsArray = new JSONArray();
            for (BrowserTab tab : activeTabs) {
                JSONObject tabObj = new JSONObject();
                // Strictly only URL and title, no sensitive state, no webState Bundle, no cookies
                tabObj.put("url", tab.url);
                tabObj.put("title", tab.title);
                tabObj.put("pinned", tab.pinned);
                tabObj.put("locked", tab.locked);
                tabsArray.put(tabObj);
            }
            root.put("tabs", tabsArray);

            File target = new File(storageDir, cleanName + ".json");
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @NonNull
    public synchronized List<SavedSession> listSessions() {
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<SavedSession> sessions = new ArrayList<>();
        for (File file : files) {
            SavedSession session = readSessionFile(file);
            if (session != null) {
                sessions.add(session);
            }
        }
        Collections.sort(sessions, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        return sessions;
    }

    @Nullable
    public synchronized SavedSession getSession(@NonNull String name) {
        File file = new File(storageDir, sanitizeName(name) + ".json");
        if (!file.exists()) return null;
        return readSessionFile(file);
    }

    public synchronized boolean deleteSession(@NonNull String name) {
        File file = new File(storageDir, sanitizeName(name) + ".json");
        return file.exists() && file.delete();
    }

    @Nullable
    private SavedSession readSessionFile(@NonNull File file) {
        try {
            int length = (int) file.length();
            byte[] bytes = new byte[length];
            try (FileInputStream fis = new FileInputStream(file)) {
                int read = fis.read(bytes);
                if (read != length) return null;
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            String name = root.optString("name", file.getName().replace(".json", ""));
            long createdAt = root.optLong("createdAt", file.lastModified());
            String networkProfile = root.optString("networkProfile", null);

            JSONArray tabsArray = root.optJSONArray("tabs");
            List<SavedTab> tabs = new ArrayList<>();
            if (tabsArray != null) {
                for (int i = 0; i < tabsArray.length(); i++) {
                    JSONObject tabObj = tabsArray.getJSONObject(i);
                    String url = tabObj.getString("url");
                    String title = tabObj.optString("title", "");
                    boolean pinned = tabObj.optBoolean("pinned", false);
                    boolean locked = tabObj.optBoolean("locked", false);
                    tabs.add(new SavedTab(url, title, pinned, locked));
                }
            }
            return new SavedSession(name, createdAt, networkProfile, tabs);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").trim();
    }
}
