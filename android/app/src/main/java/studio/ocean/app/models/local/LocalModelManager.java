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
    private volatile Process runtimeProcess;
    private volatile Thread runtimeLogThread;
    private volatile int runtimePort = -1;
    private volatile String runtimeModelId;
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
        return model != null && (model.state == LocalModel.State.INSTALLED || model.state == LocalModel.State.LOADED);
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
                        if (!existing.isConnected() || !isRuntimeAliveFor(existing.id)) {
                            existing.state = LocalModel.State.INSTALLED;
                            if (existing.id.equals(loadedModelId) && !isRuntimeAliveFor(existing.id)) {
                                clearDeadRuntimeState(existing);
                            }
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

    /**
     * Connects a GGUF model only after a genuine llama.cpp server has been
     * installed, started, passed /health, and generated a real chat token.
     * No exception is ignored and CONNECTED is never a speculative UI state.
     *
     * This method performs network/process work and MUST be called off the UI thread.
     */
    public synchronized boolean connectModel(String id) {
        LocalModel model = catalog.get(id);
        if (model == null) return false;
        File modelFile = new File(modelsDir, id + ".gguf");
        if (!modelFile.isFile() || modelFile.length() == 0) {
            failModel(model, "Model file is missing or empty. Reinstall the model.");
            return false;
        }

        long availRam = getAvailableDeviceRamMb();
        if (availRam > 0 && availRam < model.minRamMb) {
            failModel(model, "Insufficient RAM: " + availRam + " MB available, requires " + model.minRamMb + " MB.");
            return false;
        }

        if (isRuntimeAliveFor(id) && verifyInference(runtimePort, model.id)) {
            // Re-register after process/app state restoration, but only after live inference.
            markVerifiedConnected(model, runtimePort, Math.max(1, model.healthMs));
            return true;
        }

        stopRuntimeProcess();
        removeLocalProviderConnection();

        long startedAt = System.currentTimeMillis();
        try {
            LocalLlamaRuntime.RuntimePaths runtime = new LocalLlamaRuntime(context).ensureInstalled();
            int port = chooseLoopbackPort();
            int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
            int contextSize = Math.max(512, Math.min(model.context, availRam > 0 && availRam < 4096 ? 4096 : 8192));

            List<String> command = new ArrayList<>();
            command.add(runtime.server.getAbsolutePath());
            command.add("-m");
            command.add(modelFile.getAbsolutePath());
            command.add("--host");
            command.add("127.0.0.1");
            command.add("--port");
            command.add(String.valueOf(port));
            command.add("-c");
            command.add(String.valueOf(contextSize));
            command.add("-t");
            command.add(String.valueOf(threads));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(runtime.server.getParentFile());
            pb.redirectErrorStream(true);
            String oceanLib = new File(context.getFilesDir(), "usr/lib").getAbsolutePath();
            String existingLd = pb.environment().get("LD_LIBRARY_PATH");
            String ld = runtime.libDir.getAbsolutePath() + ":" + oceanLib;
            if (existingLd != null && !existingLd.trim().isEmpty()) ld += ":" + existingLd;
            pb.environment().put("LD_LIBRARY_PATH", ld);
            pb.environment().put("HOME", context.getFilesDir().getAbsolutePath());

            Process process = pb.start();
            runtimeProcess = process;
            runtimePort = port;
            runtimeModelId = id;
            startRuntimeLogDrain(process, id);

            waitUntilReady(process, port, 90000L);
            if (!verifyInference(port, model.id)) {
                throw new IOException("Local runtime became healthy but failed the real /v1/chat/completions inference probe.");
            }

            long latencyMs = Math.max(1L, System.currentTimeMillis() - startedAt);
            model.verifiedContext = contextSize;
            markVerifiedConnected(model, port, latencyMs);
            return true;
        } catch (Exception e) {
            stopRuntimeProcess();
            removeLocalProviderConnection();
            failModel(model, "Local model connection failed: " + safeMessage(e));
            return false;
        }
    }

    public synchronized boolean disconnectModel(String id) {
        LocalModel model = catalog.get(id);
        boolean wasConnected = model != null && model.isConnected();
        if (id != null && id.equals(runtimeModelId)) stopRuntimeProcess();

        if (model != null && (model.isConnected() || model.state == LocalModel.State.ERROR)) {
            model.state = LocalModel.State.INSTALLED;
            model.endpoint = null;
            model.healthMs = 0;
            model.errorMessage = null;
        }
        if (id != null && id.equals(loadedModelId)) loadedModelId = null;
        removeLocalProviderConnection();
        persistStatus();
        return wasConnected;
    }

    public synchronized boolean loadModel(String id) {
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
        LocalModel candidate = loadedModelId != null ? catalog.get(loadedModelId) : null;
        if (candidate == null) {
            for (LocalModel m : catalog.values()) {
                if (m.isConnected()) {
                    candidate = m;
                    break;
                }
            }
        }
        if (candidate == null) return null;

        if (!isRuntimeAliveFor(candidate.id) || !quickHealthCheck(runtimePort)) {
            clearDeadRuntimeState(candidate);
            removeLocalProviderConnection();
            persistStatus();
            return null;
        }
        return candidate;
    }

    private void markVerifiedConnected(LocalModel model, int port, long latencyMs) {
        model.endpoint = "127.0.0.1:" + port;
        model.healthMs = latencyMs;
        model.state = LocalModel.State.CONNECTED;
        model.errorMessage = null;
        loadedModelId = model.id;
        runtimeModelId = model.id;
        runtimePort = port;

        List<studio.ocean.app.providers.model.ModelDescriptor> models =
                Collections.singletonList(new studio.ocean.app.providers.model.ModelDescriptor(
                        model.id, model.displayName, model.verifiedContext > 0 ? model.verifiedContext : model.context,
                        false, true, false, "Verified local inference"));

        studio.ocean.app.providers.model.ProviderConnection conn =
                new studio.ocean.app.providers.model.ProviderConnection(
                        "local_connection",
                        studio.ocean.app.providers.ProviderRegistry.ID_LOCAL,
                        model.displayName,
                        studio.ocean.app.providers.model.AuthStrategy.LOCAL,
                        studio.ocean.app.providers.model.ConnectionStatus.CONNECTED,
                        "http://127.0.0.1:" + port + "/v1",
                        model.id,
                        null,
                        null,
                        Collections.singletonList("local:inference"),
                        null,
                        studio.ocean.app.providers.model.QuotaSnapshot.unlimited("On-device local execution", "llama.cpp"),
                        models,
                        System.currentTimeMillis());
        new studio.ocean.app.providers.state.ProviderConnectionStore(context).save(conn);
        persistStatus();
    }

    private void failModel(LocalModel model, String message) {
        if (model != null) {
            model.state = LocalModel.State.ERROR;
            model.errorMessage = message;
            model.endpoint = null;
            model.healthMs = 0;
        }
        persistStatus();
    }

    private void clearDeadRuntimeState(LocalModel model) {
        if (model != null) {
            model.state = LocalModel.State.INSTALLED;
            model.endpoint = null;
            model.healthMs = 0;
        }
        if (model != null && model.id.equals(loadedModelId)) loadedModelId = null;
        runtimeModelId = null;
        runtimePort = -1;
        runtimeProcess = null;
    }

    private boolean isRuntimeAliveFor(String modelId) {
        Process p = runtimeProcess;
        return p != null && p.isAlive() && modelId != null && modelId.equals(runtimeModelId) && runtimePort > 0;
    }

    private int chooseLoopbackPort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private void waitUntilReady(Process process, int port, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String last = "waiting for llama-server";
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IOException("llama-server exited before becoming ready. " + readRuntimeLogTail());
            }
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/health").openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1500);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                String body = readResponseBody(conn, code);
                conn.disconnect();
                if (code >= 200 && code < 300) return;
                last = "health HTTP " + code + (body.isEmpty() ? "" : ": " + body);
            } catch (Exception e) {
                last = safeMessage(e);
            }
            Thread.sleep(350L);
        }
        throw new IOException("Timed out waiting for llama-server readiness: " + last + ". " + readRuntimeLogTail());
    }

    private boolean quickHealthCheck(int port) {
        if (port <= 0) return false;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/health").openConnection();
            conn.setConnectTimeout(400);
            conn.setReadTimeout(600);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyInference(int port, String modelId) {
        if (port <= 0) return false;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/chat/completions").openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            JSONObject payload = new JSONObject();
            payload.put("model", modelId);
            payload.put("max_tokens", 1);
            payload.put("temperature", 0);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", "Reply OK"));
            payload.put("messages", messages);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
            int code = conn.getResponseCode();
            String body = readResponseBody(conn, code);
            if (code < 200 || code >= 300) return false;
            JSONObject response = new JSONObject(body);
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return false;
            JSONObject message = choices.optJSONObject(0) != null ? choices.optJSONObject(0).optJSONObject("message") : null;
            return message != null && message.has("content") && !message.optString("content", "").trim().isEmpty();
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readResponseBody(HttpURLConnection conn, int code) throws IOException {
        InputStream raw = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (raw == null) return "";
        try (InputStream in = raw; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            int total = 0;
            while ((n = in.read(buf)) != -1 && total < 65536) {
                int take = Math.min(n, 65536 - total);
                out.write(buf, 0, take);
                total += take;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void startRuntimeLogDrain(Process process, String modelId) {
        File logDir = new File(context.getFilesDir(), "usr/var/models");
        if (!logDir.exists()) logDir.mkdirs();
        File logFile = new File(logDir, "llama-server.log");
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 FileOutputStream log = new FileOutputStream(logFile, false)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String safe = line.length() > 4096 ? line.substring(0, 4096) : line;
                    log.write((safe + "\n").getBytes(StandardCharsets.UTF_8));
                    log.flush();
                }
            } catch (Exception ignored) {
                // Process teardown naturally closes the log stream.
            }
        }, "ocean-llama-log-" + modelId);
        t.setDaemon(true);
        runtimeLogThread = t;
        t.start();
    }

    private String readRuntimeLogTail() {
        File logFile = new File(context.getFilesDir(), "usr/var/models/llama-server.log");
        if (!logFile.isFile()) return "";
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long start = Math.max(0, raf.length() - 8192);
            raf.seek(start);
            byte[] bytes = new byte[(int) (raf.length() - start)];
            raf.readFully(bytes);
            String s = new String(bytes, StandardCharsets.UTF_8).trim();
            return s.length() > 2000 ? s.substring(s.length() - 2000) : s;
        } catch (Exception e) {
            return "";
        }
    }

    private void stopRuntimeProcess() {
        Process p = runtimeProcess;
        runtimeProcess = null;
        runtimePort = -1;
        runtimeModelId = null;
        if (p != null) {
            try {
                p.destroy();
                if (!p.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) p.destroyForcibly();
            } catch (Exception ignored) {
                try { p.destroyForcibly(); } catch (Exception ignored2) {}
            }
        }
        Thread t = runtimeLogThread;
        runtimeLogThread = null;
        if (t != null) t.interrupt();
    }

    private void removeLocalProviderConnection() {
        try {
            new studio.ocean.app.providers.state.ProviderConnectionStore(context).delete("local_connection");
        } catch (Exception ignored) {}
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m.trim();
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
