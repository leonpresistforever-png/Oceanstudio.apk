package studio.ocean.app.models.local;

import android.content.Context;
import android.content.SharedPreferences;

/** Separate preferences for each downloaded model, independent of cloud agent settings. */
public final class LocalModelSettings {
    private final SharedPreferences preferences;
    public LocalModelSettings(Context context, String id) {
        preferences = context.getSharedPreferences("ocean_local_generation_" + id, Context.MODE_PRIVATE);
    }
    public LocalGenerationSettings read(LocalModel model) {
        return new LocalGenerationSettings(preferences.getInt("context", Math.min(4096, model.contextLimit())),
                preferences.getInt("output", 512), preferences.getFloat("temperature", 0.7f),
                preferences.getFloat("top_p", 0.95f), preferences.getInt("top_k", 40),
                preferences.getFloat("repeat_penalty", 1.1f), preferences.getInt("threads", Math.min(4, Runtime.getRuntime().availableProcessors())),
                preferences.getBoolean("keep_context", true), preferences.getBoolean("tools", true) && model.supportsTools, model.contextLimit());
    }
    public void save(LocalGenerationSettings value) {
        preferences.edit().putInt("context", value.context).putInt("output", value.maxTokens)
                .putFloat("temperature", value.temperature).putFloat("top_p", value.topP)
                .putInt("top_k", value.topK).putFloat("repeat_penalty", value.repeatPenalty)
                .putInt("threads", value.threads).putBoolean("keep_context", value.keepContext)
                .putBoolean("tools", value.tools).apply();
    }
}
