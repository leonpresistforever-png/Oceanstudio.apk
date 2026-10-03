package studio.ocean.app;

import java.net.URI;
import java.util.Locale;

/** Immutable provider settings: a request must not mix settings edited during a tool run. */
public final class OceanModelConfig {
    public final String provider, model, apiKey, baseUrl;

    public OceanModelConfig(String provider, String model, String apiKey, String baseUrl) {
        this.provider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        this.model = normalizeModel(this.provider, model);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        if (this.apiKey.isEmpty() && !this.provider.equals("local")) throw new IllegalArgumentException("An API key is required");
        this.baseUrl = this.provider.equals("local") ? normalizeLocalEndpoint(baseUrl) : normalizeEndpoint(baseUrl);
        if (!this.provider.equals("google") && !this.provider.equals("anthropic")
                && !this.provider.equals("openai") && !this.provider.equals("custom") && !this.provider.equals("local"))
            throw new IllegalArgumentException("Choose a supported provider");
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

    static String normalizeEndpoint(String value) {
        String base = value == null ? "" : value.trim().replaceAll("/+$", "");
        try {
            URI uri = new URI(base);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw new IllegalArgumentException();
        } catch (Exception error) {
            throw new IllegalArgumentException("Enter an HTTPS base URL without a query, fragment, or credentials");
        }
        return base;
    }

    static String normalizeLocalEndpoint(String value) {
        String base = value == null ? "" : value.trim().replaceAll("/+$", "");
        try {
            URI uri = new URI(base);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                    || uri.getPort() < 1 || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) throw new IllegalArgumentException();
        } catch (Exception error) {
            throw new IllegalArgumentException("Local inference requires an HTTP endpoint on 127.0.0.1 with an explicit port");
        }
        return base;
    }

    public String endpoint() {
        if (provider.equals("google")) {
            String base = baseUrl.endsWith("/v1beta") || baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1beta";
            return base + "/models/" + model + ":generateContent";
        }
        if (provider.equals("anthropic"))
            return baseUrl.endsWith("/messages") ? baseUrl : baseUrl + (baseUrl.endsWith("/v1") ? "" : "/v1") + "/messages";
        return baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";
    }
}
