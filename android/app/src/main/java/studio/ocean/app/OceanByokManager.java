package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;

public final class OceanByokManager {
    public static final String PREFS_NAME = "ocean_byok_prefs";
    public static final String KEY_PROVIDER = "byok_provider";
    public static final String KEY_MODEL = "byok_model";
    public static final String KEY_API_KEY = "byok_api_key";
    public static final String KEY_BASE_URL = "byok_base_url";

    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_CUSTOM = "custom";

    public static final List<String> GOOGLE_MODELS = Arrays.asList(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.1-pro",
        "gemini-2.5-flash",
        "gemini-2.5-pro"
    );

    public static final List<String> ANTHROPIC_MODELS = Arrays.asList(
        "claude-3-7-sonnet-latest",
        "claude-3-5-haiku-latest",
        "claude-3-opus-latest"
    );

    public static final List<String> OPENAI_MODELS = Arrays.asList(
        "gpt-4o",
        "gpt-4.5-preview",
        "o1",
        "o3-mini"
    );

    private final SharedPreferences prefs;

    public OceanByokManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getProvider() {
        return prefs.getString(KEY_PROVIDER, PROVIDER_GOOGLE);
    }

    public String getModel() {
        return prefs.getString(KEY_MODEL, "gemini-3.7-flash");
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public String getBaseUrl() {
        String provider = getProvider();
        String def = "https://generativelanguage.googleapis.com";
        if (PROVIDER_OPENAI.equals(provider)) def = "https://api.openai.com/v1";
        else if (PROVIDER_ANTHROPIC.equals(provider)) def = "https://api.anthropic.com/v1";
        return prefs.getString(KEY_BASE_URL, def);
    }

    public void saveConfig(String provider, String model, String apiKey, String baseUrl) {
        prefs.edit()
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_MODEL, model)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_BASE_URL, baseUrl)
            .apply();
    }

    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.trim().isEmpty();
    }

    public List<String> getModelsForProvider(String provider) {
        if (PROVIDER_GOOGLE.equals(provider)) return GOOGLE_MODELS;
        if (PROVIDER_ANTHROPIC.equals(provider)) return ANTHROPIC_MODELS;
        if (PROVIDER_OPENAI.equals(provider)) return OPENAI_MODELS;
        return Arrays.asList("custom-model");
    }
}
