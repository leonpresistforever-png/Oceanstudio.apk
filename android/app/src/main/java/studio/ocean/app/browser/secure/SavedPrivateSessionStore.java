package studio.ocean.app.browser.secure;

import android.content.Context;
import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages storage and retrieval of SavedPrivateSession manifests.
 * Stored strictly as a JSON manifest of tab URLs and titles.
 * NEVER stores any WebView cache, cookies, or profile data.
 */
public final class SavedPrivateSessionStore {

    private static final String SESSIONS_FILE = "saved_private_sessions.json";
    private final File storageFile;
    private final List<SavedPrivateSession> sessions = new ArrayList<>();

    public SavedPrivateSessionStore(@NonNull Context context) {
        this.storageFile = new File(context.getFilesDir(), SESSIONS_FILE);
        load();
    }

    public synchronized List<SavedPrivateSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions));
    }

    public synchronized void saveSession(@NonNull SavedPrivateSession session) {
        // Remove existing session with same ID if present
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getId().equals(session.getId())) {
                sessions.remove(i);
                break;
            }
        }
        sessions.add(0, session);
        persist();
    }

    public synchronized void deleteSession(@NonNull String sessionId) {
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getId().equals(sessionId)) {
                sessions.remove(i);
                persist();
                break;
            }
        }
    }

    private synchronized void load() {
        sessions.clear();
        if (!storageFile.exists()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(storageFile)) {
            byte[] bytes = new byte[(int) storageFile.length()];
            int read = fis.read(bytes);
            if (read <= 0) return;
            String jsonStr = new String(bytes, 0, read, StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    SavedPrivateSession session = SavedPrivateSession.fromJson(obj);
                    if (session != null) {
                        sessions.add(session);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (SavedPrivateSession s : sessions) {
                arr.put(s.toJson());
            }
            File tempFile = new File(storageFile.getParentFile(), storageFile.getName() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(arr.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            if (!tempFile.renameTo(storageFile)) {
                // If rename fails, try copy
                storageFile.delete();
                tempFile.renameTo(storageFile);
            }
        } catch (Exception ignored) {
        }
    }
}
