package studio.ocean.app.providers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import studio.ocean.app.OceanByokManager;
import studio.ocean.app.R;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ProviderDescriptor;

/**
 * Central registry of all AI providers supported by OceanStudio.
 * Defines supported authentication strategies, connection modes, and capabilities.
 */
public final class ProviderRegistry {

    public static final String ID_ANTIGRAVITY = "antigravity";
    public static final String ID_KIMI = "kimi";
    public static final String ID_OPENAI = OceanByokManager.PROVIDER_OPENAI;
    public static final String ID_ANTHROPIC = OceanByokManager.PROVIDER_ANTHROPIC;
    public static final String ID_GOOGLE = OceanByokManager.PROVIDER_GOOGLE;
    public static final String ID_GROQ = "groq";
    public static final String ID_MISTRAL = "mistral";
    public static final String ID_DEEPSEEK = "deepseek";
    public static final String ID_OPENROUTER = "openrouter";
    public static final String ID_COHERE = "cohere";
    public static final String ID_TOGETHER = "together";
    public static final String ID_FIREWORKS = "fireworks";
    public static final String ID_XAI = "xai";
    public static final String ID_PERPLEXITY = "perplexity";
    public static final String ID_AZURE = "azure-openai";
    public static final String ID_LOCAL = "local";
    public static final String ID_CUSTOM = OceanByokManager.PROVIDER_CUSTOM;

    public static final ProviderDescriptor LOCAL_DESCRIPTOR = new ProviderDescriptor(
            ID_LOCAL,
            "Local Model",
            "On-device llama.cpp or local Ollama runtime",
            "http://127.0.0.1:11434/v1",
            "qwen2.5-coder",
            Collections.singletonList(AuthStrategy.LOCAL),
            "Local host port or model path",
            R.drawable.ic_terminal,
            false,
            null
    );

    private static final List<ProviderDescriptor> ALL = build();

    private ProviderRegistry() {}

    public static List<ProviderDescriptor> all() {
        return ALL;
    }

    public static ProviderDescriptor find(String id) {
        if (id == null) return null;
        if (ID_LOCAL.equals(id)) return LOCAL_DESCRIPTOR;
        for (ProviderDescriptor p : ALL) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    private static List<ProviderDescriptor> build() {
        List<ProviderDescriptor> list = new ArrayList<>();

        // 1. Google Antigravity
        list.add(new ProviderDescriptor(
                ID_ANTIGRAVITY,
                "Google Antigravity",
                "Official CLI session · subscription login via agy",
                "https://generativelanguage.googleapis.com",
                "gemini-2.5-flash",
                Arrays.asList(AuthStrategy.OFFICIAL_CLI, AuthStrategy.API_KEY),
                "AIza… API key or agy CLI login",
                R.drawable.ic_agent,
                false,
                "agy"
        ));

        // 2. Kimi Code
        list.add(new ProviderDescriptor(
                ID_KIMI,
                "Kimi Code",
                "Moonshot managed service · device-code CLI session",
                "https://api.moonshot.cn/v1",
                "moonshot-v1-auto",
                Arrays.asList(AuthStrategy.OFFICIAL_CLI, AuthStrategy.DEVICE_CODE, AuthStrategy.API_KEY),
                "sk-… from platform.moonshot.cn or kimi login",
                R.drawable.ic_tools,
                false,
                "kimi"
        ));

        // 3. OpenAI / Codex
        list.add(new ProviderDescriptor(
                ID_OPENAI,
                "OpenAI",
                "Codex CLI session or direct API key",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                Arrays.asList(AuthStrategy.OFFICIAL_CLI, AuthStrategy.API_KEY),
                "sk-… from platform.openai.com",
                R.drawable.ic_connections,
                false,
                "codex"
        ));

        // 4. Anthropic / Claude Code
        list.add(new ProviderDescriptor(
                ID_ANTHROPIC,
                "Anthropic",
                "Claude Code CLI session or direct API key",
                "https://api.anthropic.com/v1",
                "claude-sonnet-4-20250514",
                Arrays.asList(AuthStrategy.OFFICIAL_CLI, AuthStrategy.API_KEY),
                "sk-ant-… from console.anthropic.com",
                R.drawable.ic_editor,
                false,
                "claude"
        ));

        // 5. Google Gemini API
        list.add(new ProviderDescriptor(
                ID_GOOGLE,
                "Google Gemini",
                "Generative Language API key",
                "https://generativelanguage.googleapis.com",
                "gemini-2.5-flash",
                Collections.singletonList(AuthStrategy.API_KEY),
                "AIza… API key from aistudio.google.com",
                R.drawable.ic_agent,
                false,
                null
        ));

        // 6. Groq
        list.add(new ProviderDescriptor(
                ID_GROQ,
                "Groq",
                "High-speed LPU inference",
                "https://api.groq.com/openai/v1",
                "llama-3.3-70b-versatile",
                Collections.singletonList(AuthStrategy.API_KEY),
                "gsk_… from console.groq.com",
                R.drawable.ic_ports,
                false,
                null
        ));

        // 7. DeepSeek
        list.add(new ProviderDescriptor(
                ID_DEEPSEEK,
                "DeepSeek",
                "DeepSeek Reasoner & Coder API",
                "https://api.deepseek.com/v1",
                "deepseek-chat",
                Collections.singletonList(AuthStrategy.API_KEY),
                "sk-… from platform.deepseek.com",
                R.drawable.ic_editor,
                false,
                null
        ));

        // 8. Mistral
        list.add(new ProviderDescriptor(
                ID_MISTRAL,
                "Mistral AI",
                "La Plateforme inference API",
                "https://api.mistral.ai/v1",
                "mistral-small-latest",
                Collections.singletonList(AuthStrategy.API_KEY),
                "API key from console.mistral.ai",
                R.drawable.ic_connections,
                false,
                null
        ));

        // 9. OpenRouter
        list.add(new ProviderDescriptor(
                ID_OPENROUTER,
                "OpenRouter",
                "Multi-model routing gateway",
                "https://openrouter.ai/api/v1",
                "openrouter/auto",
                Collections.singletonList(AuthStrategy.API_KEY),
                "sk-or-… from openrouter.ai/keys",
                R.drawable.ic_browser_nav,
                false,
                null
        ));

        // 10. xAI
        list.add(new ProviderDescriptor(
                ID_XAI,
                "xAI",
                "Grok series models",
                "https://api.x.ai/v1",
                "grok-2-latest",
                Collections.singletonList(AuthStrategy.API_KEY),
                "API key from console.x.ai",
                R.drawable.ic_tools,
                false,
                null
        ));

        // 11. Perplexity
        list.add(new ProviderDescriptor(
                ID_PERPLEXITY,
                "Perplexity",
                "Online search-augmented models",
                "https://api.perplexity.ai",
                "sonar",
                Collections.singletonList(AuthStrategy.API_KEY),
                "pplx-… from perplexity.ai/settings/api",
                R.drawable.ic_browser_nav,
                false,
                null
        ));

        // 12. Together AI
        list.add(new ProviderDescriptor(
                ID_TOGETHER,
                "Together AI",
                "Hosted open source weights",
                "https://api.together.xyz/v1",
                "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                Collections.singletonList(AuthStrategy.API_KEY),
                "API key from api.together.xyz",
                R.drawable.ic_connections,
                false,
                null
        ));

        // 13. Fireworks AI
        list.add(new ProviderDescriptor(
                ID_FIREWORKS,
                "Fireworks AI",
                "Fast open-weight inference",
                "https://api.fireworks.ai/inference/v1",
                "accounts/fireworks/models/llama-v3p1-8b-instruct",
                Collections.singletonList(AuthStrategy.API_KEY),
                "API key from fireworks.ai",
                R.drawable.ic_ports,
                false,
                null
        ));

        // 14. Cohere
        list.add(new ProviderDescriptor(
                ID_COHERE,
                "Cohere",
                "Command R+ & Embed v2",
                "https://api.cohere.com/v2",
                "command-r-plus-08-2024",
                Collections.singletonList(AuthStrategy.API_KEY),
                "API key from dashboard.cohere.com",
                R.drawable.ic_files,
                false,
                null
        ));

        // 15. Azure OpenAI
        list.add(new ProviderDescriptor(
                ID_AZURE,
                "Azure OpenAI",
                "Dedicated enterprise deployment",
                "https://YOUR-RESOURCE.openai.azure.com",
                "gpt-4o",
                Collections.singletonList(AuthStrategy.API_KEY),
                "Azure API key + deployment URL",
                R.drawable.ic_lock_secure,
                false,
                null
        ));


        // 17. Custom Endpoint
        list.add(new ProviderDescriptor(
                ID_CUSTOM,
                "Custom Endpoint",
                "Self-hosted OpenAI-compatible gateway",
                "https://api.example.com/v1",
                "custom-model",
                Arrays.asList(AuthStrategy.API_KEY, AuthStrategy.CUSTOM_ENDPOINT),
                "API key or bearer token",
                R.drawable.ic_tools,
                false,
                null
        ));

        return Collections.unmodifiableList(list);
    }
}
