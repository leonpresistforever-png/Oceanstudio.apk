package studio.ocean.app;

import java.net.URI;
import java.util.Locale;

/** Immutable provider settings: a request must not mix settings edited during a tool run. */
public final class OceanModelConfig {
    /** Wire-protocol provider consumed by OceanAgentConversation (google/openai/anthropic/local/custom). */
    public final String provider;
    /** Original routed provider identity, retained for provider-specific transports such as Antigravity. */
    public final String sourceProvider;
    public final String model, apiKey, baseUrl;

    public OceanModelConfig(String provider, String model, String apiKey, String baseUrl) {
        String rawProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        this.sourceProvider = rawProvider;
        this.provider = isAntigravityProvider(rawProvider) ? "google" : rawProvider;
        this.model = normalizeModel(this.provider, model);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        if (this.apiKey.isEmpty() && !this.provider.equals("local"))
            throw new IllegalArgumentException("An API key or verified access token is required");
        this.baseUrl = normalizeEndpoint(rawProvider, baseUrl);
        if (!rawProvider.equals("google") && !rawProvider.equals("anthropic")
                && !rawProvider.equals("openai") && !rawProvider.equals("custom")
                && !rawProvider.equals("local") && !isAntigravityProvider(rawProvider))
            throw new IllegalArgumentException("Choose a supported provider");
    }

    public boolean isAntigravity() {
        return isAntigravityProvider(sourceProvider);
    }

    private static boolean isAntigravityProvider(String provider) {
        return "antigravity".equals(provider)
                || "antigravity_ide".equals(provider)
                || "antigravity_20".equals(provider);
    }

    static String normalizeModel(String provider, String value) {
        String model = value == null ? "" : value.trim();
        if ("google".equals(provider) && model.startsWith("models/")) model = model.substring(7);
        String lower = model.toLowerCase(Locale.ROOT);
        if (model.isEmpty() || lower.equals("google") || lower.equals("gemini")
                || lower.equals("anthropic") || lower.equals("claude")
                || lower.equals("openai") || lower.equals("custom"))
            throw new IllegalArgumentException("Enter a model ID, not a provider name. For Gemini, use an ID such as gemini-2.5-flash from your provider's model list.");
        if (model.length() > 200 || model.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("Model IDs cannot contain spaces");
        if ("google".equals(provider) && !model.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Enter the Gemini model ID only, without an endpoint URL");
        return model;
    }

    static String normalizeEndpoint(String provider, String value) {
        String base = value == null ? "" : value.trim().replaceAll("/+$", "");
        try {
            URI uri = new URI(base);
            boolean local = "local".equals(provider);
            boolean schemeOk = "https".equalsIgnoreCase(uri.getScheme())
                    || (local && "http".equalsIgnoreCase(uri.getScheme()));
            boolean hostOk = uri.getHost() != null
                    && (!local || "127.0.0.1".equals(uri.getHost()));
            if (!schemeOk || !hostOk || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw new IllegalArgumentException();
        } catch (Exception error) {
            if ("local".equals(provider))
                throw new IllegalArgumentException("Local models must use an Ocean-owned http://127.0.0.1 endpoint");
            throw new IllegalArgumentException("Enter an HTTPS base URL without a query, fragment, or credentials");
        }
        return base;
    }

    public String endpoint() {
        if (isAntigravity()) {
            // Antigravity requests are wrapped and sent through ProviderExecutionEngine's
            // verified Cloud Code transport. Returning the base here prevents accidental
            // construction of the public Gemini API URL.
            return baseUrl;
        }
        if (provider.equals("google")) {
            String base = baseUrl.endsWith("/v1beta") || baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1beta";
            return base + "/models/" + model + ":generateContent";
        }
        if (provider.equals("anthropic"))
            return baseUrl.endsWith("/messages") ? baseUrl : baseUrl + (baseUrl.endsWith("/v1") ? "" : "/v1") + "/messages";
        return baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";
    }
}
