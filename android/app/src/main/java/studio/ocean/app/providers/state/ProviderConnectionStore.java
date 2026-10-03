package studio.ocean.app.providers.state;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    private static final Object FILE_LOCK = new Object();

    public ProviderConnectionStore(Context context) {
        this(context.getApplicationContext().getFilesDir());
    }
    ProviderConnectionStore(File directory) { this.storeFile = new File(directory, FILE_NAME); }

    public List<ProviderConnection> listAll() { synchronized (FILE_LOCK) {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(cache));
    } }

    public List<ProviderConnection> listConnections() {
        return listAll();
    }

    public List<ProviderConnection> listByProvider(String providerId) { synchronized (FILE_LOCK) {
        ensureLoaded();
        List<ProviderConnection> matches = new ArrayList<>();
        if (providerId == null) return matches;
        for (ProviderConnection conn : cache) {
            if (providerId.equals(conn.providerId)) matches.add(conn);
        }
        matches.sort((a, b) -> {
            boolean ac = a.status == studio.ocean.app.providers.model.ConnectionStatus.CONNECTED;
            boolean bc = b.status == studio.ocean.app.providers.model.ConnectionStatus.CONNECTED;
            if (ac != bc) return ac ? -1 : 1;
            return Long.compare(b.lastValidatedAtEpochMs, a.lastValidatedAtEpochMs);
        });
        return Collections.unmodifiableList(matches);
    } }

    public ProviderConnection findByProviderId(String providerId) {
        List<ProviderConnection> matches = listByProvider(providerId);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public ProviderConnection get(String id) { synchronized (FILE_LOCK) {
        ensureLoaded();
        if (id == null) return null;
        for (ProviderConnection conn : cache) {
            if (id.equals(conn.id)) return conn;
        }
        return null;
    } }

    public void save(ProviderConnection connection) { synchronized (FILE_LOCK) {
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
    } }

    public void delete(String id) { synchronized (FILE_LOCK) {
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
    } }

    public void clear() { synchronized (FILE_LOCK) {
        cache.clear();
        persist();
    } }

    private void ensureLoaded() {
        // Different app components own different repository instances. Always read
        // the atomic file under the shared lock, including before each mutation.
        cache.clear();
        if (storeFile.exists()) {
            try (FileInputStream fis = new FileInputStream(storeFile)) {
                byte[] bytes = new byte[(int) storeFile.length()];
                int read = 0, count;
                while (read < bytes.length && (count = fis.read(bytes, read, bytes.length - read)) > 0) read += count;
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
            }
        }
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
            if (!tmp.renameTo(storeFile)) throw new java.io.IOException("Atomic connection-store update failed");
        } catch (Exception error) {
            throw new IllegalStateException("Could not save the verified provider connection", error);
        }
    }
}
