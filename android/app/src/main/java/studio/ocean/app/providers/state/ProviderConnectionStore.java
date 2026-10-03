package studio.ocean.app.providers.state;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import studio.ocean.app.providers.model.ProviderConnection;

/**
 * Thread-safe persistent JSON repository for provider connections.
 */
public final class ProviderConnectionStore {
    private static final String FILE_NAME = "ocean_provider_connections.json";
    private final File storeFile;
    private final List<ProviderConnection> cache = new ArrayList<>();
    private boolean loaded = false;
    private long loadedLastModified = Long.MIN_VALUE;
    private long loadedLength = Long.MIN_VALUE;

    public ProviderConnectionStore(Context context) {
        this.storeFile = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    public synchronized List<ProviderConnection> listAll() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(cache));
    }

    public synchronized List<ProviderConnection> listConnections() {
        return listAll();
    }

    public synchronized List<ProviderConnection> listByProvider(String providerId) {
        ensureLoaded();
        List<ProviderConnection> matches = new ArrayList<>();
        if (providerId == null) return matches;
        for (ProviderConnection conn : cache) {
            if (providerId.equals(conn.providerId)) matches.add(conn);
        }
        return Collections.unmodifiableList(matches);
    }

    public synchronized ProviderConnection findByProviderId(String providerId) {
        ensureLoaded();
        if (providerId == null) return null;
        for (ProviderConnection conn : cache) {
            if (providerId.equals(conn.providerId)) return conn;
        }
        return null;
    }

    public synchronized ProviderConnection get(String id) {
        ensureLoaded();
        if (id == null) return null;
        for (ProviderConnection conn : cache) {
            if (id.equals(conn.id)) return conn;
        }
        return null;
    }

    public synchronized void save(ProviderConnection connection) {
        if (connection == null) return;
        ensureLoaded();
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).id.equals(connection.id)) {
                cache.set(i, connection);
                persist();
                return;
            }
        }
        cache.add(connection);
        persist();
    }

    public synchronized void delete(String id) {
        if (id == null) return;
        ensureLoaded();
        boolean removed = false;
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).id.equals(id)) {
                cache.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) persist();
    }

    public synchronized void clear() {
        cache.clear();
        persist();
    }

    private void ensureLoaded() {
        long modified = storeFile.exists() ? storeFile.lastModified() : -1L;
        long length = storeFile.exists() ? storeFile.length() : -1L;
        if (loaded && modified == loadedLastModified && length == loadedLength) return;

        cache.clear();
        if (storeFile.exists()) {
            try (FileInputStream fis = new FileInputStream(storeFile)) {
                byte[] bytes = new byte[(int) storeFile.length()];
                int read = fis.read(bytes);
                if (read > 0) {
                    String json = new String(bytes, 0, read, StandardCharsets.UTF_8);
                    JSONArray arr = new JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj != null) {
                            ProviderConnection c = ProviderConnection.fromJson(obj);
                            if (c != null) cache.add(c);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Keep the in-memory view empty rather than trusting stale connections.
            }
        }
        loaded = true;
        loadedLastModified = storeFile.exists() ? storeFile.lastModified() : -1L;
        loadedLength = storeFile.exists() ? storeFile.length() : -1L;
    }

    private void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (ProviderConnection c : cache) {
                arr.put(c.toJson());
            }
            File tmp = new File(storeFile.getParentFile(), storeFile.getName() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(arr.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.getFD().sync();
            }
            boolean replaced = tmp.renameTo(storeFile);
            if (!replaced && storeFile.exists() && storeFile.delete()) {
                replaced = tmp.renameTo(storeFile);
            }
            if (!replaced) throw new IOException("Could not replace provider connection store atomically.");
            loaded = true;
            loadedLastModified = storeFile.lastModified();
            loadedLength = storeFile.length();
        } catch (Exception ignored) {
            // Callers never get a false CONNECTED result from persistence alone;
            // the active runtime/provider must still pass its own verification.
        }
    }
}
