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
    private volatile String loadedModelId = null;
    private volatile Process localRuntimeProcess = null;
    private final java.util.concurrent.atomic.AtomicBoolean runtimeStarting = new java.util.concurrent.atomic.AtomicBoolean();
    private android.os.FileObserver modelsWatcher;

    private LocalModelManager(Context context) {
        this.context = context;
        this.modelsDir = new File(context.getFilesDir(), "models");
        if (!modelsDir.exists()) modelsDir.mkdirs();

        File varDir = new File(context.getFilesDir(), "usr/var/models");
        if (!varDir.exists()) varDir.mkdirs();
        this.statusFile = new File(varDir, "status.json");

        initBuiltinCatalog();
        syncFromDisk();
        // A persisted connection is not proof that a runtime survived process death.
        new studio.ocean.app.providers.state.ProviderConnectionStore(context).delete("local_connection");

        // Watch models directory for externally installed models (Directive 3 §8.1)
        try {
            modelsWatcher = new android.os.FileObserver(modelsDir.getAbsolutePath(), android.os.FileObserver.CLOSE_WRITE | android.os.FileObserver.DELETE | android.os.FileObserver.MOVED_TO) {
                @Override
                public void onEvent(int event, String path) {
                    syncFromDisk();
                }
            };
            modelsWatcher.startWatching();
        } catch (Exception ignored) {}
    }

    private void initBuiltinCatalog() {
        // Attempt to load from models-manifest.json in assets
        boolean loadedFromManifest = false;
        try {
            if (context != null && context.getAssets() != null) {
                try (InputStream is = context.getAssets().open("models-manifest.json")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] b = new byte[4096];
                    int r;
                    while ((r = is.read(b)) != -1) baos.write(b, 0, r);
                    JSONObject root = new JSONObject(baos.toString(StandardCharsets.UTF_8.name()));
                    JSONArray modelsArr = root.optJSONArray("models");
                    if (modelsArr != null) {
                        for (int i = 0; i < modelsArr.length(); i++) {
                            JSONObject mObj = modelsArr.getJSONObject(i);
                            LocalModel model = new LocalModel(
                                    mObj.optString("id"),
                                    mObj.optString("name", mObj.optString("displayName")),
                                    mObj.optString("family"),
                                    mObj.optString("format", "gguf"),
                                    mObj.optString("quantization", "Q4_K_M"),
                                    mObj.optLong("size", mObj.optLong("sizeBytes")),
                                    mObj.optInt("min_ram_mb", mObj.optInt("minRamMb", 1024)),
                                    mObj.optInt("context_length", mObj.optInt("context", 4096)),
                                    mObj.optString("runtime", "llama.cpp"),
                                    mObj.optString("url", mObj.optString("sourceUrl")),
                                    mObj.optString("sha256"),
                                    mObj.optString("license", "Apache-2.0"),
                                    mObj.optString("arch", "arm64")
                            );
                            addCatalogEntry(model);
                        }
                        loadedFromManifest = true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (!loadedFromManifest) {
            // Genuine upstream open-source GGUF releases with verified metadata and licenses
            addCatalogEntry(new LocalModel(
                    "qwen2.5-coder-0.5b",
                    "Qwen 2.5 Coder 0.5B Instruct",
                    "Qwen2.5-Coder",
                    "gguf",
                    "Q4_K_M",
                    491400064L, // ~491 MB
                    1024,
                    8192,
                    "llama.cpp",
                    "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
                    "1d9614638d18024d0fbb36575a15f1302a3adf044df10345688ec4f6e1c4ff32",
                    "Apache-2.0",
                    "arm64"
            ));

            addCatalogEntry(new LocalModel(
                    "smollm2-360m-instruct",
                    "SmolLM2 360M Instruct",
                    "SmolLM2",
                    "gguf",
                    "Q4_K_M",
                    270590560L, // ~271 MB
                    512,
                    4096,
                    "llama.cpp",
                    "https://huggingface.co/unsloth/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
                    "16c7f1667fea34bacad196a57b548effcb37614db4ab5677a20c8c7b823b9e63",
                    "Apache-2.0",
                    "arm64"
            ));

            addCatalogEntry(new LocalModel(
                    "llama-3.2-1b-instruct",
                    "Llama 3.2 1B Instruct",
                    "Llama-3.2",
                    "gguf",
                    "Q4_K_M",
                    807694464L, // ~808 MB
                    2048,
                    8192,
                    "llama.cpp",
                    "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                    "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
                    "Llama-3.2-Community",
                    "arm64"
            ));
        }
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

    public synchronized boolean isModelInstalled(String id) {
        LocalModel model = getModel(id);
        return model != null && (model.state == LocalModel.State.INSTALLED || model.state == LocalModel.State.LOADED || model.state == LocalModel.State.CONNECTED);
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
                        if (existing.state != LocalModel.State.LOADED && existing.state != LocalModel.State.CONNECTED && existing.state != LocalModel.State.CONNECTING) {
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

        // Check free storage headroom (Directive 2026-10-02 §8.3: model size + 15% headroom)
        long requiredBytes = (long) (model.sizeBytes * 1.15);
        long usableBytes = modelsDir.getUsableSpace();
        if (usableBytes > 0 && usableBytes < requiredBytes) {
            String errMsg = "Insufficient storage: " + (usableBytes / (1024 * 1024)) + " MB free, but "
                    + (requiredBytes / (1024 * 1024)) + " MB required (including 15% headroom).";
            model.state = LocalModel.State.ERROR;
            model.errorMessage = errMsg;
            persistStatus();
            if (callback != null) callback.onFailure(errMsg);
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
                // Follow redirects up to 5 hops (e.g. HuggingFace 302/307 to CDN)
                String currentUrl = model.sourceUrl;
                int redirects = 0;
                while (redirects < 5) {
                    URL url = new URL(currentUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "OceanStudio/1.2.6 (Android Bionic)");
                    activeDownloads.put(id, conn);

                    int respCode = conn.getResponseCode();
                    if (respCode == HttpURLConnection.HTTP_MOVED_TEMP || respCode == HttpURLConnection.HTTP_MOVED_PERM
                            || respCode == HttpURLConnection.HTTP_SEE_OTHER || respCode == 307 || respCode == 308) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        activeDownloads.remove(id);
                        if (loc == null || loc.trim().isEmpty()) {
                            throw new IOException("HTTP " + respCode + " redirect without Location header");
                        }
                        currentUrl = loc;
                        redirects++;
                        continue;
                    }

                    if (respCode < 200 || respCode >= 300) {
                        throw new IOException("HTTP error " + respCode + ": " + conn.getResponseMessage());
                    }
                    break;
                }

                if (conn == null) throw new IOException("Failed to establish HTTP connection");

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
                if (partialFile.exists()) partialFile.delete();
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

    public boolean connectModel(String id) {
        if (!runtimeStarting.compareAndSet(false, true)) return false;
        try { return connectModelInternal(id); }
        finally { runtimeStarting.set(false); }
    }

    private boolean connectModelInternal(String id) {
        LocalModel model = getModel(id);
        if (model == null) return false;
        if (id.equals(loadedModelId) && localRuntimeProcess != null && localRuntimeProcess.isAlive()) return true;
        File modelFile = new File(modelsDir, id + ".gguf");
        if (!modelFile.isFile() || modelFile.length() == 0) {
            model.errorMessage = "Model file is missing or empty.";
            return false;
        }

        long availRam = getAvailableDeviceRamMb();
        if (availRam > 0 && availRam < model.minRamMb) {
            model.errorMessage = "Insufficient RAM: " + availRam + " MB available, requires " + model.minRamMb + " MB.";
            return false;
        }

        if (loadedModelId != null && !loadedModelId.equals(id)) disconnectModel(loadedModelId);

        final int port;
        try (java.net.ServerSocket reservation = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            port = reservation.getLocalPort();
        } catch (IOException error) {
            model.errorMessage = "Unable to allocate a local inference port.";
            return false;
        }
        File prefix = new File(context.getFilesDir(), "usr");
        File[] candidates = new File[] {
                new File(context.getApplicationInfo().nativeLibraryDir, "libllama-server.so"),
                new File(prefix, "bin/llama-server-ocean"),
                new File(prefix, "bin/llama-server")
        };
        File server = null;
        for (File candidate : candidates) {
            if (candidate.isFile() && candidate.canExecute()) { server = candidate; break; }
        }
        if (server == null) {
            model.errorMessage = "No executable llama-server runtime is installed. Ocean will not mark this model connected until a real inference runtime exists.";
            model.state = LocalModel.State.INSTALLED;
            persistStatus();
            return false;
        }

        try {
            android.content.Intent keepAlive = new android.content.Intent(context, LocalInferenceService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(keepAlive);
            else context.startService(keepAlive);
            studio.ocean.app.providers.state.CredentialVault vault = new studio.ocean.app.providers.state.CredentialVault(context);
            byte[] randomKey = new byte[32];
            new java.security.SecureRandom().nextBytes(randomKey);
            String runtimeKey = android.util.Base64.encodeToString(randomKey, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
            vault.store("local_inference_key", runtimeKey);
            model.state = LocalModel.State.CONNECTING;
            if (localRuntimeProcess != null) {
                localRuntimeProcess.destroy();
                localRuntimeProcess = null;
            }

            ProcessBuilder pb = new ProcessBuilder(
                    server.getAbsolutePath(),
                    "-m", modelFile.getAbsolutePath(),
                    "--host", "127.0.0.1",
                    "--port", String.valueOf(port),
                    "-c", String.valueOf(Math.min(model.context, 4096)),
                    "--alias", model.id,
                    "--api-key", runtimeKey,
                    "--jinja", "--parallel", "1",
                    "--threads", String.valueOf(Math.min(4, Runtime.getRuntime().availableProcessors()))
            );
            pb.directory(context.getFilesDir());
            pb.environment().put("PREFIX", prefix.getAbsolutePath());
            pb.environment().put("HOME", context.getFilesDir().getAbsolutePath());
            pb.environment().put("PATH", new File(prefix, "bin").getAbsolutePath() + ":/system/bin");
            // The bundled server links inference and C++ statically; do not mix package ABIs.
            pb.environment().remove("LD_PRELOAD");
            pb.environment().remove("LD_LIBRARY_PATH");
            pb.redirectErrorStream(true);
            localRuntimeProcess = pb.start();

            // llama-server writes startup diagnostics continuously. An unread pipe
            // can fill before readiness and block the child indefinitely.
            final Process startedProcess = localRuntimeProcess;
            final File runtimeLog = new File(statusFile.getParentFile(), "llama-server.log");
            Thread drain = new Thread(() -> {
                try (InputStream output = startedProcess.getInputStream(); OutputStream log = new FileOutputStream(runtimeLog)) {
                    byte[] buffer = new byte[4096];
                    int count; long written = 0;
                    while ((count = output.read(buffer)) != -1) {
                        if (written < 1024 * 1024) { log.write(buffer, 0, count); log.flush(); written += count; }
                    }
                } catch (IOException ignored) {}
            }, "ocean-local-runtime-output");
            drain.setDaemon(true);
            drain.start();

            long deadline = System.currentTimeMillis() + 120000L;
            long probeStart = System.currentTimeMillis();
            String generated = null;
            while (System.currentTimeMillis() < deadline) {
                if (!startedProcess.isAlive()) {
                    throw new IOException("llama-server exited before becoming ready.");
                }
                try {
                    HttpURLConnection probe = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/chat/completions").openConnection(java.net.Proxy.NO_PROXY);
                    probe.setRequestMethod("POST");
                    probe.setConnectTimeout(1000);
                    probe.setReadTimeout(15000);
                    probe.setRequestProperty("Content-Type", "application/json");
                    probe.setRequestProperty("Authorization", "Bearer " + runtimeKey);
                    probe.setDoOutput(true);
                    JSONObject body = new JSONObject()
                            .put("model", model.id)
                            .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "Reply with OK")))
                            .put("max_tokens", 4)
                            .put("temperature", 0);
                    try (OutputStream out = probe.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code = probe.getResponseCode();
                    InputStream input = code >= 200 && code < 300 ? probe.getInputStream() : probe.getErrorStream();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    if (input != null) try (InputStream in = input) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) != -1 && bytes.size() < 65536) bytes.write(buf, 0, n);
                    }
                    if (code >= 200 && code < 300) {
                        JSONObject response = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                        JSONArray choices = response.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                            generated = message != null ? message.optString("content", "").trim() : "";
                        }
                        probe.disconnect();
                        if (generated != null && !generated.isEmpty()) break;
                    }
                    probe.disconnect();
                    Thread.sleep(250L);
                } catch (Exception notReady) {
                    try { Thread.sleep(250L); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Local runtime startup interrupted.", ie);
                    }
                }
            }

            if (generated == null || generated.isEmpty()) {
                throw new IOException("llama-server did not produce a valid inference response within 120 seconds.");
            }
            if (!startedProcess.isAlive() || localRuntimeProcess != startedProcess) throw new IOException("Local runtime stopped during verification.");

            model.endpoint = "127.0.0.1:" + port;
            model.healthMs = Math.max(1L, System.currentTimeMillis() - probeStart);
            model.verifiedContext = Math.min(model.context, 4096);
            model.state = LocalModel.State.CONNECTED;
            model.errorMessage = null;
            loadedModelId = id;

            studio.ocean.app.providers.model.ProviderConnection conn =
                    new studio.ocean.app.providers.model.ProviderConnection(
                            "local_connection",
                            studio.ocean.app.providers.ProviderRegistry.ID_LOCAL,
                            model.displayName,
                            studio.ocean.app.providers.model.AuthStrategy.LOCAL,
                            studio.ocean.app.providers.model.ConnectionStatus.CONNECTED,
                            "http://127.0.0.1:" + port + "/v1",
                            model.id,
                            "local_inference_key", null,
                            Collections.singletonList(model.id),
                            null,
                            studio.ocean.app.providers.model.QuotaSnapshot.reported(null, null,
                                    studio.ocean.app.providers.model.QuotaSnapshot.Unit.PROVIDER_DEFINED,
                                    null, "On-device local execution", "local-runtime"),
                            null,
                            System.currentTimeMillis()
                    );
            new studio.ocean.app.providers.state.ProviderConnectionStore(context).save(conn);
            setLocalOverrideEnabled(true);
            persistStatus();
            return true;
        } catch (Exception e) {
            if (localRuntimeProcess != null) {
                localRuntimeProcess.destroy();
                localRuntimeProcess = null;
            }
            model.state = LocalModel.State.INSTALLED;
            model.errorMessage = e.getMessage() != null ? e.getMessage() : "Local runtime failed to start.";
            loadedModelId = null;
            try {
                studio.ocean.app.providers.state.ProviderConnectionStore store =
                        new studio.ocean.app.providers.state.ProviderConnectionStore(context);
                studio.ocean.app.providers.model.ProviderConnection stale =
                        store.findByProviderId(studio.ocean.app.providers.ProviderRegistry.ID_LOCAL);
                if (stale != null) store.delete(stale.id);
            } catch (Exception ignored) {}
            persistStatus();
            context.stopService(new android.content.Intent(context, LocalInferenceService.class));
            return false;
        }
    }

    public synchronized boolean disconnectModel(String id) {
        if (localRuntimeProcess != null) {
            localRuntimeProcess.destroy();
            localRuntimeProcess = null;
        }
        LocalModel model = catalog.get(id);
        if (model != null && (model.state == LocalModel.State.CONNECTED || model.state == LocalModel.State.LOADED)) {
            model.state = LocalModel.State.INSTALLED;
            model.endpoint = null;
            model.healthMs = 0;
            if (id.equals(loadedModelId)) loadedModelId = null;

            try {
                new studio.ocean.app.providers.state.ProviderConnectionStore(context).delete("local_connection");
            } catch (Exception ignored) {}

            persistStatus();
            new studio.ocean.app.providers.state.CredentialVault(context).delete("local_inference_key");
            context.stopService(new android.content.Intent(context, LocalInferenceService.class));
            return true;
        }
        return false;
    }

    public boolean loadModel(String id) {
        return connectModel(id);
    }

    public synchronized boolean unloadModel(String id) {
        return disconnectModel(id);
    }

    public boolean isLocalOverrideEnabled() {
        return context.getSharedPreferences("ocean_model_prefs", Context.MODE_PRIVATE)
                .getBoolean("local_model_override", false);
    }

    public void setLocalOverrideEnabled(boolean enabled) {
        context.getSharedPreferences("ocean_model_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("local_model_override", enabled).apply();
    }

    public synchronized LocalModel getConnectedModel() {
        if (runtimeStarting.get()) return null;
        if (localRuntimeProcess == null || !localRuntimeProcess.isAlive()) {
            if (loadedModelId != null) disconnectModel(loadedModelId);
            return null;
        }
        if (loadedModelId != null) {
            LocalModel m = catalog.get(loadedModelId);
            if (m != null && m.isConnected()) return m;
        }
        for (LocalModel m : catalog.values()) {
            if (m.isConnected()) return m;
        }
        return null;
    }

    public synchronized boolean deleteModel(String id) {
        if (runtimeStarting.get()) return false;
        LocalModel model = catalog.get(id);
        if (model == null) return false;
        if (model.state == LocalModel.State.LOADED || model.state == LocalModel.State.CONNECTED) {
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
