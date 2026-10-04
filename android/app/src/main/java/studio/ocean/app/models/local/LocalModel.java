package studio.ocean.app.models.local;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manifest and state contract for an on-device local model (Directive 3 §8.2, Directive 2026-10-02 §11).
 * Supports Connect/Disconnect lifecycle, loopback endpoint inspection, and inference verification.
 */
public final class LocalModel {

    public enum State {
        AVAILABLE("Available"),
        DOWNLOADING("Downloading"),
        INSTALLED("Installed"),
        CONNECTING("Connecting"),
        LOADED("Connected"),
        CONNECTED("Connected"),
        ERROR("Error"),
        INCOMPATIBLE("Needs RAM");

        public final String label;
        State(String label) { this.label = label; }
    }

    public final String id;
    public final String displayName;
    public final String family;
    public final String format;
    public final String quantization;
    public final long sizeBytes;
    public final int minRamMb;
    public final int context;
    public final String backend;
    public final String sourceUrl;
    public final String sha256;
    public final String license;
    public final String architecture;

    public volatile State state = State.AVAILABLE;
    public volatile int downloadProgress = 0;
    public volatile String errorMessage = null;

    // Verified runtime state after connect
    public volatile String endpoint = null;
    public volatile long healthMs = 0;
    public volatile int verifiedContext = 0;
    public volatile boolean supportsTools = false;

    public LocalModel(String id, String displayName, String family, String format,
                      String quantization, long sizeBytes, int minRamMb, int context,
                      String backend, String sourceUrl, String sha256, String license,
                      String architecture) {
        this.id = id;
        this.displayName = displayName;
        this.family = family;
        this.format = format;
        this.quantization = quantization;
        this.sizeBytes = sizeBytes;
        this.minRamMb = minRamMb;
        this.context = context;
        this.backend = backend;
        this.sourceUrl = sourceUrl;
        this.sha256 = sha256;
        this.license = license;
        this.architecture = architecture;
    }

    public boolean isConnected() {
        return state == State.CONNECTED || state == State.LOADED;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("displayName", displayName);
        obj.put("family", family);
        obj.put("format", format);
        obj.put("quantization", quantization);
        obj.put("sizeBytes", sizeBytes);
        obj.put("minRamMb", minRamMb);
        obj.put("context", context);
        obj.put("backend", backend);
        obj.put("sourceUrl", sourceUrl);
        obj.put("sha256", sha256);
        obj.put("license", license);
        obj.put("architecture", architecture);
        obj.put("state", state.name());
        obj.put("downloadProgress", downloadProgress);
        if (endpoint != null) obj.put("endpoint", endpoint);
        obj.put("healthMs", healthMs);
        obj.put("verifiedContext", verifiedContext);
        obj.put("supportsTools", supportsTools);
        if (errorMessage != null) obj.put("errorMessage", errorMessage);
        return obj;
    }

    public static LocalModel fromJson(JSONObject obj) {
        LocalModel model = new LocalModel(
                obj.optString("id"),
                obj.optString("displayName"),
                obj.optString("family"),
                obj.optString("format", "gguf"),
                obj.optString("quantization", "Q4_K_M"),
                obj.optLong("sizeBytes"),
                obj.optInt("minRamMb"),
                obj.optInt("context", 4096),
                obj.optString("backend", "llama.cpp"),
                obj.optString("sourceUrl"),
                obj.optString("sha256"),
                obj.optString("license", "Apache-2.0"),
                obj.optString("architecture", "arm64")
        );
        String s = obj.optString("state", State.AVAILABLE.name());
        try {
            model.state = State.valueOf(s);
        } catch (Exception ignored) {
            model.state = State.AVAILABLE;
        }
        model.downloadProgress = obj.optInt("downloadProgress", 0);
        model.endpoint = obj.optString("endpoint", null);
        model.healthMs = obj.optLong("healthMs", 0);
        model.verifiedContext = obj.optInt("verifiedContext", model.context);
        model.errorMessage = obj.optString("errorMessage", null);
        return model;
    }
}
