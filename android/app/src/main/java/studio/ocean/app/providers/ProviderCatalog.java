package studio.ocean.app.providers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import studio.ocean.app.OceanByokManager;

/** Built-in LLM providers the connect screen can configure with API keys or OAuth. */
public final class ProviderCatalog {
    public enum AuthMode { API_KEY, OAUTH_PKCE }

    public static final class Entry {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String defaultBaseUrl;
        public final String defaultModel;
        public final AuthMode authMode;
        public final String credentialHint;

        Entry(String id, String title, String subtitle, String defaultBaseUrl, String defaultModel,
                AuthMode authMode, String credentialHint) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.defaultBaseUrl = defaultBaseUrl;
            this.defaultModel = defaultModel;
            this.authMode = authMode;
            this.credentialHint = credentialHint;
        }

        public String byokProviderId() {
            if (OceanByokManager.PROVIDER_GOOGLE.equals(id) || OceanByokManager.PROVIDER_ANTHROPIC.equals(id)
                    || OceanByokManager.PROVIDER_OPENAI.equals(id)) return id;
            return OceanByokManager.PROVIDER_CUSTOM;
        }
    }

    private static final List<Entry> ENTRIES = build();

    private ProviderCatalog() {}

    public static List<Entry> all() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static Entry find(String id) {
        for (Entry entry : ENTRIES) if (entry.id.equals(id)) return entry;
        return null;
    }

    private static List<Entry> build() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(OceanByokManager.PROVIDER_GOOGLE, "Google Gemini",
                "Generative Language API · API key or Google OAuth",
                "https://generativelanguage.googleapis.com", "gemini-2.5-flash",
                AuthMode.OAUTH_PKCE, "AIza… API key or Connect with Google"));
        list.add(new Entry(OceanByokManager.PROVIDER_ANTHROPIC, "Anthropic",
                "Claude Messages API", "https://api.anthropic.com/v1", "claude-sonnet-4-20250514",
                AuthMode.API_KEY, "sk-ant-… from console.anthropic.com"));
        list.add(new Entry(OceanByokManager.PROVIDER_OPENAI, "OpenAI",
                "Chat Completions API", "https://api.openai.com/v1", "gpt-4o-mini",
                AuthMode.API_KEY, "sk-… from platform.openai.com"));
        list.add(new Entry("groq", "Groq", "OpenAI-compatible inference",
                "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile",
                AuthMode.API_KEY, "gsk_… from console.groq.com"));
        list.add(new Entry("mistral", "Mistral", "La Plateforme chat API",
                "https://api.mistral.ai/v1", "mistral-small-latest",
                AuthMode.API_KEY, "API key from console.mistral.ai"));
        list.add(new Entry("deepseek", "DeepSeek", "OpenAI-compatible API",
                "https://api.deepseek.com/v1", "deepseek-chat",
                AuthMode.API_KEY, "API key from platform.deepseek.com"));
        list.add(new Entry("cohere", "Cohere", "OpenAI-compatible v2",
                "https://api.cohere.com/v2", "command-r-plus-08-2024",
                AuthMode.API_KEY, "API key from dashboard.cohere.com"));
        list.add(new Entry("together", "Together AI", "Hosted open models",
                "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                AuthMode.API_KEY, "API key from api.together.xyz"));
        list.add(new Entry("fireworks", "Fireworks AI", "Fast open-weight inference",
                "https://api.fireworks.ai/inference/v1", "accounts/fireworks/models/llama-v3p1-8b-instruct",
                AuthMode.API_KEY, "API key from fireworks.ai"));
        list.add(new Entry("openrouter", "OpenRouter", "Routed multi-provider gateway",
                "https://openrouter.ai/api/v1", "openrouter/auto",
                AuthMode.API_KEY, "sk-or-… from openrouter.ai/keys"));
        list.add(new Entry("xai", "xAI", "Grok chat API",
                "https://api.x.ai/v1", "grok-2-latest",
                AuthMode.API_KEY, "API key from console.x.ai"));
        list.add(new Entry("perplexity", "Perplexity", "Sonar models",
                "https://api.perplexity.ai", "sonar",
                AuthMode.API_KEY, "pplx-… from perplexity.ai/settings/api"));
        list.add(new Entry("azure-openai", "Azure OpenAI",
                "Your Azure resource endpoint", "https://YOUR-RESOURCE.openai.azure.com/openai/deployments/YOUR-DEPLOYMENT",
                "gpt-4o", AuthMode.API_KEY, "Azure API key + deployment URL"));
        list.add(new Entry("custom", "Custom endpoint", "Any HTTPS OpenAI-compatible API",
                "https://api.example.com/v1", "model-id",
                AuthMode.API_KEY, "Bearer token or API key"));
        return list;
    }
}
