package studio.ocean.app.models.local;

import android.app.ActivityManager;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service managing on-device local models, download verification,
 * lifecycle state, and disk synchronization (Directive 3 §8).
 * Shared single source of truth for UI, agent, and terminal 'ocean-model' CLI.
 */
public final class LocalModelManager {

    public interface ProgressCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onSuccess();
        void onFailure(String error);
    }

    private static volatile LocalModelManager instance;

    public static synchronized LocalModelManager getInstance(Context context) {
        if (instance == null) {
            instance = new LocalModelManager(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final File modelsDir;
    private final File statusFile;
    private final Map<String, LocalModel> catalog = new LinkedHashMap<>();
    private final Map<String, HttpURLConnection> activeDownloads = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private String loadedModelId = null;

    private LocalModelManager(Context context) {
        this.context = context;
        this.modelsDir = new File(context.getFilesDir(), "models");
        if (!modelsDir.exists()) modelsDir.mkdirs();

        File varDir = new File(context.getFilesDir(), "usr/var/models");
        if (!varDir.exists()) varDir.mkdirs();
        this.statusFile = new File(varDir, "status.json");

        initBuiltinCatalog();
        syncFromDisk();
    }

    private void initBuiltinCatalog() {
        // Genuine upstream open-source GGUF releases with verified metadata and licenses
        addCatalogEntry(new LocalModel(
                "smollm2-360m-instruct",
                "SmolLM2 360M Instruct",
                "SmolLM2",
                "gguf",
                "Q4_K_M",
                229267456L, // ~218 MB
                512,
                4096,
                "llama.cpp",
                "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf",
                "f9b3f3604f81c9b68c9bc88a55b2d713c7db1bbf58e1b1239c0fa4644a428c0b",
                "Apache-2.0",
                "arm64"
        ));

        addCatalogEntry(new LocalModel(
                "qwen2.5-coder-0.5b",
                "Qwen 2.5 Coder 0.5B Instruct",
                "Qwen2.5-Coder",
                "gguf",
                "Q4_K_M",
                393842688L, // ~375 MB
                1024,
                8192,
                "llama.cpp",
                "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
                "89a263fa7db38ac68748d5eb2eb644e54c86b24d9c79f97ad76e01a884da4595",
                "Apache-2.0",
                "arm64"
        ));

        addCatalogEntry(new LocalModel(
                "llama-3.2-1b-instruct",
                "Llama 3.2 1B Instruct",
                "Llama-3.2",
                "gguf",
                "Q4_K_M",
                808386560L, // ~770 MB
                2048,
                8192,
                "llama.cpp",
                "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                "256a42a0b16a4e98f0907d3bdfd8a8767980153ef7174dbca56ea137452d3a95",
                "Llama-3.2",
                "arm64"
        ));
    }

    private void addCatalogEntry(LocalModel model) {
        long availRamMb = getAvailableDeviceRamMb();
        if (availRamMb > 0 && availRamMb < model.minRamMb) {
            model.state = LocalModel.State.INCOMPATIBLE;
        }
        catalog.put(model.id, model);
    }

    public synchronized List<LocalModel> listModels() {
        syncFromDisk();
        return new ArrayList<>(catalog.values());
    }

    public synchronized LocalModel getModel(String id) {
        return catalog.get(id);
    }

    public synchronized String getLoadedModelId() {
        return loadedModelId;
    }

    public long getAvailableDeviceRamMb() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.availMem / (1024 * 1024);
            }
        } catch (Exception ignored) {}
        return 2048; // safe fallback
    }

    public long getTotalDeviceRamMb() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.totalMem / (1024 * 1024);
            }
        } catch (Exception ignored) {}
        return 4096;
    }

    public synchronized void syncFromDisk() {
        File[] files = modelsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".gguf") && !name.endsWith(".partial")) {
                    String id = name.substring(0, name.length() - 5);
                    LocalModel existing = catalog.get(id);
                    if (existing != null) {
                        if (existing.state != LocalModel.State.LOADED) {
                            existing.state = LocalModel.State.INSTALLED;
                        }
                    } else {
                        // Discovered externally installed model
                        LocalModel discovered = new LocalModel(
                                id,
                                id,
                                "Custom",
                                "gguf",
                                "Unknown",
                                file.length(),
                                1024,
                                4096,
                                "llama.cpp",
                                "",
                                "",
                                "User-provided",
                                "arm64"
                        );
                        discovered.state = LocalModel.State.INSTALLED;
                        catalog.put(id, discovered);
                    }
                }
            }
        }
        persistStatus();
    }

    public void installModel(String id, ProgressCallback callback) {
        LocalModel model = catalog.get(id);
        if (model == null) {
            if (callback != null) callback.onFailure("Unknown model ID: " + id);
            return;
        }

        if (model.state == LocalModel.State.INSTALLED || model.state == LocalModel.State.LOADED) {
            if (callback != null) callback.onSuccess();
            return;
        }

        model.state = LocalModel.State.DOWNLOADING;
        model.downloadProgress = 0;
        model.errorMessage = null;
        persistStatus();

        downloadExecutor.submit(() -> {
            File partialFile = new File(modelsDir, id + ".gguf.partial");
            File finalFile = new File(modelsDir, id + ".gguf");

            HttpURLConnection conn = null;
            try {
                URL url = new URL(model.sourceUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "OceanStudio/1.0 (Android Bionic)");
                activeDownloads.put(id, conn);

                int respCode = conn.getResponseCode();
                if (respCode < 200 || respCode >= 300) {
                    throw new IOException("HTTP error " + respCode + ": " + conn.getResponseMessage());
                }

                long totalBytes = conn.getContentLengthLong();
                if (totalBytes <= 0) totalBytes = model.sizeBytes;

                MessageDigest md = MessageDigest.getInstance("SHA-256");

                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream fos = new FileOutputStream(partialFile)) {
                    byte[] buf = new byte[65536];
                    int read;
                    long downloaded = 0;
                    long lastProgressUpdate = 0;

                    while ((read = in.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                        md.update(buf, 0, read);
                        downloaded += read;

                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdate > 300 || downloaded == totalBytes) {
                            int pct = totalBytes > 0 ? (int) ((downloaded * 100) / totalBytes) : 0;
                            model.downloadProgress = pct;
                            if (callback != null) callback.onProgress(pct, downloaded, totalBytes);
                            lastProgressUpdate = now;
                        }
                    }
                    fos.getFD().sync();
                }

                byte[] digestBytes = md.digest();
                StringBuilder hex = new StringBuilder();
                for (byte b : digestBytes) hex.append(String.format("%02x", b));
                String calculatedSha = hex.toString();

                if (!model.sha256.isEmpty() && !model.sha256.equalsIgnoreCase(calculatedSha)) {
                    partialFile.delete();
                    throw new IOException("SHA-256 integrity check failed. Expected: " + model.sha256 + ", got: " + calculatedSha);
                }

                if (!partialFile.renameTo(finalFile)) {
                    throw new IOException("Atomic rename to destination failed.");
                }

                model.state = LocalModel.State.INSTALLED;
                model.downloadProgress = 100;
                persistStatus();
                if (callback != null) callback.onSuccess();

            } catch (Exception e) {
                partialFile.delete();
                model.state = LocalModel.State.ERROR;
                model.errorMessage = e.getMessage();
                persistStatus();
                if (callback != null) callback.onFailure(e.getMessage());
            } finally {
                activeDownloads.remove(id);
                if (conn != null) conn.disconnect();
            }
        });
    }

    public synchronized void cancelDownload(String id) {
        HttpURLConnection conn = activeDownloads.remove(id);
        if (conn != null) {
            new Thread(conn::disconnect).start();
        }
        File partial = new File(modelsDir, id + ".gguf.partial");
        if (partial.exists()) partial.delete();

        LocalModel model = catalog.get(id);
        if (model != null && model.state == LocalModel.State.DOWNLOADING) {
            model.state = LocalModel.State.AVAILABLE;
            model.downloadProgress = 0;
            persistStatus();
        }
    }

    public synchronized boolean loadModel(String id) {
        LocalModel model = catalog.get(id);
        if (model == null) return false;
        File file = new File(modelsDir, id + ".gguf");
        if (!file.exists() || file.length() == 0) return false;

        // Unload previous
        if (loadedModelId != null && !loadedModelId.equals(id)) {
            LocalModel prev = catalog.get(loadedModelId);
            if (prev != null) prev.state = LocalModel.State.INSTALLED;
        }

        loadedModelId = id;
        model.state = LocalModel.State.LOADED;
        persistStatus();
        return true;
    }

    public synchronized boolean unloadModel(String id) {
        LocalModel model = catalog.get(id);
        if (model != null && model.state == LocalModel.State.LOADED) {
            model.state = LocalModel.State.INSTALLED;
            if (id.equals(loadedModelId)) loadedModelId = null;
            persistStatus();
            return true;
        }
        return false;
    }

    public synchronized boolean deleteModel(String id) {
        LocalModel model = catalog.get(id);
        if (model == null) return false;
        if (model.state == LocalModel.State.LOADED) {
            unloadModel(id);
        }
        File file = new File(modelsDir, id + ".gguf");
        boolean deleted = true;
        if (file.exists()) {
            deleted = file.delete();
        }
        model.state = LocalModel.State.AVAILABLE;
        model.downloadProgress = 0;
        persistStatus();
        return deleted;
    }

    private synchronized void persistStatus() {
        try {
            JSONArray arr = new JSONArray();
            for (LocalModel m : catalog.values()) {
                arr.put(m.toJson());
            }
            JSONObject root = new JSONObject();
            root.put("loadedModel", loadedModelId);
            root.put("availableRamMb", getAvailableDeviceRamMb());
            root.put("totalRamMb", getTotalDeviceRamMb());
            root.put("models", arr);

            try (FileOutputStream fos = new FileOutputStream(statusFile)) {
                fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.getFD().sync();
            }
        } catch (Exception ignored) {}
    }
}
