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
    static LocalModelManager existingInstance() { return instance; }

    private final Context context;
    private final File modelsDir;
    private final File statusFile;
    private final Map<String, LocalModel> catalog = new LinkedHashMap<>();
    private final Map<String, HttpURLConnection> activeDownloads = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private volatile String loadedModelId = null;
    private volatile Process localRuntimeProcess = null;
    private final ManagedLocalRuntime runtime = new ManagedLocalRuntime();
    private final Object connectLock = new Object();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile LocalInferenceService runtimeService;
    private volatile android.content.ServiceConnection runtimeBinding;
    private volatile OllamaClient ollama;
    private volatile String activeBackend = "llama.cpp";
    private volatile String runtimePhase = "Server stopped";
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
        synchronized (connectLock) {
            runtimeStarting.set(true);
            try {
                if (getModel(id) == null) return false;
                preferences().edit().putString("selected_local_model", id).putBoolean("local_runtime_wanted", true).apply();
                boolean connected = "ollama".equals(preferences().getString("local_backend", "llama.cpp"))
                        ? connectOllamaModel(id) : connectModelInternal(id);
                if (connected) preferences().edit().putBoolean("local_runtime_wanted", true).apply();
                updateRuntimeNotification();
                return connected;
            } finally { runtimeStarting.set(false); if (!runtime.isRunning()) releaseRuntimeService(); }
        }
    }

    private android.content.SharedPreferences preferences() {
        return context.getSharedPreferences("ocean_model_prefs", Context.MODE_PRIVATE);
    }
    public String runtimeStatus() { return runtimePhase; }
    public boolean isRuntimeBusy() { return runtimeStarting.get(); }
    public String backend() { return preferences().getString("local_backend", "llama.cpp"); }
    public void useBundledLlama() {
        shutdownRuntime(); preferences().edit().putString("local_backend", "llama.cpp").apply();
        activeBackend = "llama.cpp";
    }
    public boolean startOllamaServer() {
        synchronized (connectLock) {
            runtimeStarting.set(true);
            try {
                preferences().edit().putString("local_backend", "ollama").apply();
                ensureOllamaServer(); return true;
            } catch (Exception error) {
                runtimePhase = "Ollama startup failed: " + error.getMessage(); updateRuntimeNotification(); return false;
            } finally { runtimeStarting.set(false); if (!runtime.isRunning()) releaseRuntimeService(); }
        }
    }
    private void ensureOllamaServer() throws Exception {
        ensureRuntimeService();
        if ("ollama".equals(activeBackend) && runtime.isRunning() && ollama != null) {
            ollama.version(); return;
        }
        runtime.stop(); localRuntimeProcess = null; runtimeExited("Changing local runtime");
        File executable = new File(context.getApplicationInfo().nativeLibraryDir, "libollama.so");
        if (!executable.isFile() || !executable.canExecute()) throw new IOException("Bundled Ollama executable is missing");
        int port;
        try (java.net.ServerSocket available = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            port = available.getLocalPort();
        }
        File home = new File(context.getFilesDir(), "ollama"); home.mkdirs();
        File temporary = new File(home, "tmp"); temporary.mkdirs();
        ProcessBuilder builder = new ProcessBuilder(executable.getAbsolutePath(), "serve");
        builder.directory(home);
        builder.environment().put("HOME", home.getAbsolutePath());
        builder.environment().put("TMPDIR", temporary.getAbsolutePath());
        builder.environment().put("OLLAMA_HOST", "127.0.0.1:" + port);
        builder.environment().put("OLLAMA_MODELS", new File(home, "models").getAbsolutePath());
        builder.environment().put("OLLAMA_NO_CLOUD", "true");
        builder.environment().put("OLLAMA_KEEP_ALIVE", "-1");
        builder.environment().put("OLLAMA_MAX_LOADED_MODELS", "1");
        builder.environment().put("OLLAMA_NUM_PARALLEL", "1");
        builder.environment().put("OLLAMA_CONTEXT_LENGTH", "4096");
        builder.environment().remove("LD_PRELOAD"); builder.environment().remove("LD_LIBRARY_PATH");
        activeBackend = "ollama"; runtimePhase = "Starting Ollama server…"; updateRuntimeNotification();
        localRuntimeProcess = runtime.start(builder, new File(home, "server.log"), this::runtimeExited);
        ollama = new OllamaClient(port);
        long deadline = System.currentTimeMillis() + 90000;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            if (!runtime.isRunning()) throw new IOException(runtime.lastExit());
            try {
                if (!ollama.version().optString("version").isEmpty()) {
                    runtimePhase = "Ollama server ready · 127.0.0.1:" + port; updateRuntimeNotification(); return;
                }
            } catch (Exception error) { last = error; }
            Thread.sleep(250);
        }
        runtime.stop(); throw new IOException("Ollama server did not become ready", last);
    }
    private boolean connectOllamaModel(String id) {
        LocalModel model = getModel(id);
        if (id.equals(loadedModelId) && model.isConnected() && runtime.isRunning() && "ollama".equals(activeBackend)) return true;
        try {
            File file = new File(modelsDir, id + ".gguf");
            if (!file.isFile()) throw new IOException("Downloaded GGUF is missing");
            if (loadedModelId != null && !loadedModelId.equals(id)) disconnectModel(loadedModelId);
            ensureOllamaServer(); // The server must answer /api/version before any model setup.
            model.state = LocalModel.State.CONNECTING;
            runtimePhase = "Ollama ready · registering " + model.displayName; updateRuntimeNotification();
            LocalGenerationSettings settings = new LocalModelSettings(context, model.id).read(model);
            int contextSize = settings.context;
            String alias = ollama.importModel(model.id, file, contextSize);
            runtimePhase = "Loading " + model.displayName + " in Ollama…"; updateRuntimeNotification();
            ollama.load(alias, settings);
            long before = System.currentTimeMillis(); ollama.infer(alias, settings);
            if (!runtime.isRunning()) throw new IOException(runtime.lastExit());
            model.endpoint = ollama.baseUrl().replace("http://", "").replace("/v1", "");
            model.verifiedContext = ollama.loadedContext(alias);
            if (model.verifiedContext != contextSize) throw new IOException("Ollama did not apply the requested context allocation");
            model.healthMs = Math.max(1, System.currentTimeMillis() - before);
            JSONArray capabilities = ollama.show(alias).optJSONArray("capabilities");
            model.supportsTools = false;
            if (capabilities != null) for (int i = 0; i < capabilities.length(); i++)
                if ("tools".equals(capabilities.optString(i))) model.supportsTools = true;
            model.errorMessage = null; model.state = LocalModel.State.CONNECTED; loadedModelId = id;
            studio.ocean.app.providers.model.ProviderConnection connection = new studio.ocean.app.providers.model.ProviderConnection(
                    "local_connection", studio.ocean.app.providers.ProviderRegistry.ID_LOCAL, model.displayName,
                    studio.ocean.app.providers.model.AuthStrategy.LOCAL, studio.ocean.app.providers.model.ConnectionStatus.CONNECTED,
                    ollama.baseUrl(), alias, null, null, Collections.singletonList(alias), null,
                    studio.ocean.app.providers.model.QuotaSnapshot.reported(null, null,
                            studio.ocean.app.providers.model.QuotaSnapshot.Unit.PROVIDER_DEFINED, null,
                            "On-device Ollama execution", "local-runtime"), null, System.currentTimeMillis());
            new studio.ocean.app.providers.state.ProviderConnectionStore(context).save(connection);
            setLocalOverrideEnabled(true); runtimePhase = "Ollama running · " + model.displayName;
            persistStatus(); return true;
        } catch (Exception error) {
            model.state = LocalModel.State.INSTALLED; model.errorMessage = error.getMessage(); loadedModelId = null;
            new studio.ocean.app.providers.state.ProviderConnectionStore(context).delete("local_connection");
            runtimePhase = runtime.isRunning() ? "Ollama ready · model setup failed" : "Ollama stopped";
            persistStatus(); return false;
        }
    }
    public LocalModel ensureConnectedModel() {
        LocalModel connected = getConnectedModel();
        if (connected != null) return connected;
        String selected = preferences().getString("selected_local_model", null);
        if (selected != null && preferences().getBoolean("local_runtime_wanted", false) && connectModel(selected)) return getConnectedModel();
        return null;
    }
    void restoreAfterProcessDeath() {
        if (preferences().getBoolean("local_runtime_wanted", false)) new Thread(this::ensureConnectedModel, "ocean-restore-local").start();
    }
    private synchronized void runtimeExited(String reason) {
        if (runtime.isRunning()) return;
        LocalModel model = loadedModelId == null ? null : catalog.get(loadedModelId);
        if (model != null) { model.state = LocalModel.State.INSTALLED; model.endpoint = null; model.errorMessage = reason; }
        loadedModelId = null;
        runtimePhase = reason == null ? "Server stopped" : reason;
        new studio.ocean.app.providers.state.ProviderConnectionStore(context).delete("local_connection");
        persistStatus(); updateRuntimeNotification();
        if (!runtimeStarting.get()) releaseRuntimeService();
    }
    private void ensureRuntimeService() throws Exception {
        if (runtimeService != null) return;
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Exception> failure = new java.util.concurrent.atomic.AtomicReference<>();
        main.post(() -> {
            try {
            android.content.Intent intent = new android.content.Intent(context, LocalInferenceService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
            android.content.ServiceConnection binding = new android.content.ServiceConnection() {
                @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
                    runtimeService = ((LocalInferenceService.LocalBinder) binder).service(); runtimeBinding = this; ready.countDown();
                }
                @Override public void onServiceDisconnected(android.content.ComponentName name) { runtimeService = null; }
                @Override public void onNullBinding(android.content.ComponentName name) { ready.countDown(); }
            };
            if (!context.bindService(intent, binding, Context.BIND_AUTO_CREATE)) ready.countDown();
            } catch (Exception error) { failure.set(error); ready.countDown(); }
        });
        if (!ready.await(20, java.util.concurrent.TimeUnit.SECONDS) || runtimeService == null)
            throw new IOException("Foreground inference service did not become ready", failure.get());
    }
    private void updateRuntimeNotification() {
        main.post(() -> { LocalInferenceService service = runtimeService; if (service != null) service.updateState(runtimePhase); });
    }
    private void releaseRuntimeService() {
        LocalInferenceService previous = runtimeService;
        main.post(() -> {
            if (runtimeService != previous || runtime.isRunning() || runtimeStarting.get()) return;
            runtimeService = null;
            if (runtimeBinding != null) { context.unbindService(runtimeBinding); runtimeBinding = null; }
            context.stopService(new android.content.Intent(context, LocalInferenceService.class));
        });
    }
    void serviceDestroyed(LocalInferenceService service) {
        if (runtimeService != service) return; // An old service must not stop its successor.
        runtimeService = null; runtimeBinding = null;
        runtime.stop(); localRuntimeProcess = null; runtimeExited("Android stopped the inference service; reconnecting is available");
    }

    private boolean connectModelInternal(String id) {
        LocalModel model = getModel(id);
        if (model == null) return false;
        if (id.equals(loadedModelId) && runtime.isRunning() && "llama.cpp".equals(activeBackend)) return true;
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
        runtime.stop(); localRuntimeProcess = null;

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
            ensureRuntimeService();
            activeBackend = "llama.cpp";
            runtimePhase = "Starting llama.cpp server and loading " + model.displayName;
            studio.ocean.app.providers.state.CredentialVault vault = new studio.ocean.app.providers.state.CredentialVault(context);
            byte[] randomKey = new byte[32];
            new java.security.SecureRandom().nextBytes(randomKey);
            String runtimeKey = android.util.Base64.encodeToString(randomKey, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
            vault.store("local_inference_key", runtimeKey);
            model.state = LocalModel.State.CONNECTING;
            runtime.stop(); localRuntimeProcess = null;

            LocalGenerationSettings settings = new LocalModelSettings(context, model.id).read(model);
            ProcessBuilder pb = new ProcessBuilder(
                    server.getAbsolutePath(),
                    "-m", modelFile.getAbsolutePath(),
                    "--host", "127.0.0.1",
                    "--port", String.valueOf(port),
                    "-c", String.valueOf(settings.context),
                    "--alias", model.id,
                    "--api-key", runtimeKey,
                    "--jinja", "--parallel", "1",
                    "--threads", String.valueOf(settings.threads)
            );
            pb.directory(context.getFilesDir());
            pb.environment().put("PREFIX", prefix.getAbsolutePath());
            pb.environment().put("HOME", context.getFilesDir().getAbsolutePath());
            pb.environment().put("PATH", new File(prefix, "bin").getAbsolutePath() + ":/system/bin");
            // The bundled server links inference and C++ statically; do not mix package ABIs.
            pb.environment().remove("LD_PRELOAD");
            pb.environment().remove("LD_LIBRARY_PATH");
            pb.redirectErrorStream(true);
            final File runtimeLog = new File(statusFile.getParentFile(), "llama-server.log");
            localRuntimeProcess = runtime.start(pb, runtimeLog, this::runtimeExited);
            final Process startedProcess = localRuntimeProcess;

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
            JSONObject properties = localRequest(model.endpoint, runtimeKey, "/props", null);
            JSONObject generation = properties.optJSONObject("default_generation_settings");
            model.verifiedContext = generation == null ? 0 : generation.optInt("n_ctx", 0);
            if (model.verifiedContext != settings.context) throw new IOException("llama.cpp did not apply the requested context allocation");
            JSONObject caps = properties.optJSONObject("chat_template_caps");
            model.supportsTools = caps != null && caps.optBoolean("supports_tool_calls", false);
            runtimePhase = "llama.cpp server running · " + model.displayName;
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
            runtime.stop();
            runtimePhase = "Server stopped";
            model.errorMessage = (e.getMessage() != null ? e.getMessage() : "Local runtime failed to start.")
                    + "\n" + ManagedLocalRuntime.tail(new File(statusFile.getParentFile(), "llama-server.log"));
            loadedModelId = null;
            try {
                studio.ocean.app.providers.state.ProviderConnectionStore store =
                        new studio.ocean.app.providers.state.ProviderConnectionStore(context);
                studio.ocean.app.providers.model.ProviderConnection stale =
                        store.findByProviderId(studio.ocean.app.providers.ProviderRegistry.ID_LOCAL);
                if (stale != null) store.delete(stale.id);
            } catch (Exception ignored) {}
            persistStatus();
            updateRuntimeNotification();
            return false;
        }
    }

    public void shutdownRuntime() {
        synchronized (connectLock) {
            preferences().edit().putBoolean("local_runtime_wanted", false).apply();
            runtime.stop(); localRuntimeProcess = null; runtimeExited("Local server stopped"); releaseRuntimeService();
        }
    }

    public boolean disconnectModel(String id) {
        synchronized (connectLock) {
            LocalModel model = getModel(id);
            if (model == null) return false;
            if ("ollama".equals(activeBackend) && ollama != null && model.isConnected()) {
                try { ollama.unload("ocean-" + id); } catch (Exception ignored) { }
            } else { runtime.stop(); localRuntimeProcess = null; }
            model.state = LocalModel.State.INSTALLED; model.endpoint = null; model.healthMs = 0;
            loadedModelId = null;
            new studio.ocean.app.providers.state.ProviderConnectionStore(context).delete("local_connection");
            new studio.ocean.app.providers.state.CredentialVault(context).delete("local_inference_key");
            preferences().edit().putBoolean("local_runtime_wanted", false).apply();
            runtimePhase = runtime.isRunning() ? "Ollama server ready · no model loaded" : "Server stopped";
            persistStatus(); updateRuntimeNotification();
            if (!runtime.isRunning()) releaseRuntimeService();
            return true;
        }
    }

    public boolean loadModel(String id) {
        return connectModel(id);
    }

    public LocalGenerationSettings generationSettings(LocalModel model) {
        return new LocalModelSettings(context, model.id).read(model);
    }
    public boolean isOllamaRuntime() { return "ollama".equals(activeBackend); }

    /** Changes requiring memory allocation are applied by restarting and probing. */
    public void applyGenerationSettings(String id, LocalGenerationSettings settings) throws Exception {
        synchronized (connectLock) {
            LocalModel model = getConnectedModel();
            if (model == null || !id.equals(model.id)) throw new IOException("The selected local model changed. Reopen its settings.");
            LocalModelSettings store = new LocalModelSettings(context, id);
            LocalGenerationSettings previous = store.read(model);
            store.save(settings);
            if (previous.context != settings.context || previous.threads != settings.threads) {
                disconnectModel(id);
                if (!connectModel(id)) {
                    String reason = model.errorMessage;
                    store.save(previous);
                    connectModel(id);
                    throw new IOException("The new allocation could not start: " + reason + ". Previous settings restored.");
                }
            }
        }
    }

    public JSONObject prepareAgentRequest(String selectedModel, JSONObject source) throws Exception {
        synchronized (connectLock) {
            LocalModel model = getConnectedModel();
            if (model == null) throw new IOException("Local server stopped before this request.");
            String expected = isOllamaRuntime() ? "ocean-" + model.id : model.id;
            if (!(selectedModel.equals(expected) || selectedModel.equals(expected + ":latest")))
                throw new IOException("Local model selection changed before the request. Retry with the selected model.");
            LocalGenerationSettings settings = generationSettings(model);
            JSONObject body = new JSONObject(source.toString());
            if (!settings.tools) { body.remove("tools"); body.remove("tool_choice"); }
            if (isOllamaRuntime()) return LocalInferenceProtocol.prepareOllama(body, settings);
            String key = new studio.ocean.app.providers.state.CredentialVault(context).retrieve("local_inference_key");
            return LocalInferenceProtocol.prepareLlama(body, settings,
                    (path, payload) -> localRequest(model.endpoint, key, path, payload));
        }
    }
    private JSONObject localRequest(String endpoint, String key, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://" + endpoint + path).openConnection(java.net.Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000); connection.setReadTimeout(15000);
        if (key != null && !key.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + key);
        try {
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("Local runtime " + path + " returned HTTP " + code);
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192]; int count;
                while ((count = in.read(bytes)) != -1) {
                    if (out.size() + count > 2 * 1024 * 1024) throw new IOException("Local runtime metadata exceeded its limit");
                    out.write(bytes, 0, count);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { connection.disconnect(); }
    }

    public boolean unloadModel(String id) {
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
        if (!runtime.isRunning()) {
            if (loadedModelId != null) runtimeExited(runtime.lastExit() != null ? runtime.lastExit() : "Local server is no longer running");
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

    public boolean deleteModel(String id) {
        synchronized (connectLock) {
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
    }

    private synchronized void persistStatus() {
        try {
            JSONArray arr = new JSONArray();
            for (LocalModel m : catalog.values()) {
                arr.put(m.toJson());
            }
            JSONObject root = new JSONObject();
            root.put("loadedModel", loadedModelId);
            root.put("runtimeBackend", activeBackend);
            root.put("runtimePhase", runtimePhase);
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
