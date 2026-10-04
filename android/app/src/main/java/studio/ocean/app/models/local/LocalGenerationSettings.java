package studio.ocean.app.models.local;

import org.json.JSONObject;

/** Immutable local generation limits. Cloud controls never enter this object. */
public final class LocalGenerationSettings {
    public final int context, maxTokens, topK, threads;
    public final float temperature, topP, repeatPenalty;
    public final boolean keepContext, tools;

    public LocalGenerationSettings(int context, int maxTokens, float temperature, float topP,
            int topK, float repeatPenalty, int threads, boolean keepContext, boolean tools, int modelContext) {
        int limit = Math.max(64, Math.min(32768, modelContext));
        this.context = clamp(context, Math.min(512, limit), limit);
        this.maxTokens = clamp(maxTokens, 16, outputLimit(this.context));
        this.temperature = clamp(temperature, 0, 2);
        this.topP = clamp(topP, 0.01f, 1);
        this.topK = clamp(topK, 0, 200);
        this.repeatPenalty = clamp(repeatPenalty, 0.5f, 2);
        this.threads = clamp(threads, 1, Math.max(1, Runtime.getRuntime().availableProcessors()));
        this.keepContext = keepContext; this.tools = tools;
    }

    public static int outputLimit(int context) {
        return Math.max(16, context - Math.min(128, context / 4));
    }

    public JSONObject ollamaOptions(int output) throws Exception {
        return new JSONObject().put("num_ctx", context).put("num_predict", Math.min(output, maxTokens))
                .put("temperature", temperature).put("top_p", topP).put("top_k", topK)
                .put("repeat_penalty", repeatPenalty).put("num_thread", threads).put("num_gpu", 0);
    }
    public String signature() {
        return context + "|" + maxTokens + "|" + temperature + "|" + topP + "|" + topK + "|"
                + repeatPenalty + "|" + threads + "|" + keepContext + "|" + tools;
    }
    private static int clamp(int n, int lo, int hi) { return Math.max(lo, Math.min(n, hi)); }
    private static float clamp(float n, float lo, float hi) {
        return Float.isNaN(n) || Float.isInfinite(n) ? lo : Math.max(lo, Math.min(n, hi));
    }
}
