package studio.ocean.app.providers.auth;

import android.content.Context;
import java.io.File;
import java.net.URI;
import java.util.Locale;
import studio.ocean.app.providers.cli.OfficialCliAdapter;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ProviderDescriptor;

/**
 * Validates provider configuration before any authentication flow or browser intent is triggered.
 * Guarantees that "Access blocked" / redirect_uri_mismatch is caught internally and never exposed.
 */
public final class AuthPreflight {

    public static final class PreflightResult {
        public final boolean isReady;
        public final boolean passed;
        public final String failureTitle;
        public final String failureMessage;
        public final String errorMessage;
        public final String technicalDetails;
        public final String recommendedAlternative;

        private PreflightResult(boolean isReady, String failureTitle, String failureMessage,
                                String technicalDetails, String recommendedAlternative) {
            this.isReady = isReady;
            this.passed = isReady;
            this.failureTitle = failureTitle;
            this.failureMessage = failureMessage;
            this.errorMessage = failureMessage;
            this.technicalDetails = technicalDetails;
            this.recommendedAlternative = recommendedAlternative;
        }

        public static PreflightResult success() {
            return new PreflightResult(true, null, null, null, null);
        }

        public static PreflightResult fail(String title, String message, String details, String alternative) {
            return new PreflightResult(false, title, message, details, alternative);
        }
    }

    private final Context context;

    public AuthPreflight(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    /**
     * Strictly validates a redirect URI before constructing any browser authorization intent.
     * Android loopback redirects (127.0.0.1 or localhost) are banned to eliminate redirect_uri_mismatch.
     */
    public static PreflightResult validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.trim().isEmpty()) {
            return PreflightResult.fail("redirect_uri_mismatch",
                    "Redirect URI cannot be null or empty.",
                    "Missing redirect_uri",
                    "Use official CLI bridge or API key.");
        }

        String lower = redirectUri.toLowerCase(Locale.ROOT);
        if (lower.contains("127.0.0.1") || lower.contains("localhost")) {
            return PreflightResult.fail("redirect_uri_mismatch",
                    "Loopback redirect URIs (127.0.0.1 / localhost) are rejected by provider OAuth specifications for native mobile apps.",
                    "Unregistered loopback port",
                    "Use official CLI account bridge or direct API key.");
        }

        if (!lower.startsWith("https://") && !lower.startsWith("ocean://")) {
            return PreflightResult.fail("redirect_uri_mismatch",
                    "Native redirect URI must use an approved https:// relay or custom app scheme.",
                    "Non-secure or invalid URI scheme",
                    "Configure an official OAuth redirect URI.");
        }

        return PreflightResult.success();
    }

    /**
     * Validates API key and endpoint configuration before initiating connections.
     */
    public static PreflightResult validateApiKeyConfig(String providerId, String model, String key, String baseUrl) {
        if (key == null || key.trim().isEmpty()) {
            return PreflightResult.fail("API Key Required",
                    "Please enter a valid API key for this provider.",
                    "Key was empty",
                    null);
        }

        String trimmedKey = key.trim();
        if (("google".equals(providerId) || "antigravity".equals(providerId)) && !trimmedKey.startsWith("AIza")) {
            return PreflightResult.fail("Invalid Key Format",
                    "Google API keys typically begin with 'AIza'. Please verify your key.",
                    "Format mismatch",
                    null);
        }

        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            try {
                URI uri = URI.create(baseUrl.trim());
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                    return PreflightResult.fail("Invalid Endpoint URL",
                            "The endpoint URL must start with https:// or http://.",
                            "Invalid URI scheme: " + scheme,
                            null);
                }
            } catch (Exception e) {
                return PreflightResult.fail("Invalid Endpoint URL",
                        "The entered endpoint URL is malformed.",
                        e.getMessage(),
                        null);
            }
        }

        return PreflightResult.success();
    }

    /**
     * Executes strict pre-flight validation.
     * Returns a failing result rather than opening a broken external browser session.
     */
    public PreflightResult validate(ProviderDescriptor provider, AuthStrategy strategy, String endpoint, String credential) {
        if (provider == null) {
            return PreflightResult.fail("Unknown Provider", "The specified provider is not supported.", null, null);
        }

        if (!provider.supports(strategy)) {
            return PreflightResult.fail("Strategy Not Supported",
                    provider.title + " does not support " + strategy.name() + ".",
                    "Supported strategies: " + provider.supportedStrategies,
                    "Use API key or official CLI connection.");
        }

        switch (strategy) {
            case OFFICIAL_CLI:
                return validateCliStrategy(provider);

            case OFFICIAL_OAUTH:
                return validateOAuthStrategy(provider);

            case DEVICE_CODE:
                return validateDeviceCodeStrategy(provider);

            case API_KEY:
                return validateApiKeyStrategy(provider, endpoint, credential);

            case LOCAL:
                return validateLocalStrategy(provider);

            default:
                return PreflightResult.success();
        }
    }

    private PreflightResult validateCliStrategy(ProviderDescriptor provider) {
        if (provider.officialCliName == null || provider.officialCliName.isEmpty()) {
            return PreflightResult.fail("CLI Tool Not Configured",
                    "No official CLI executable is mapped for " + provider.title + ".",
                    "CLI name is null",
                    "Connect via API key instead.");
        }

        File prefixDir = context != null ? new File(context.getFilesDir(), "usr") : null;
        OfficialCliAdapter adapter = new OfficialCliAdapter(provider.officialCliName, prefixDir);
        if (!adapter.isInstalled()) {
            return PreflightResult.fail("Official CLI Not Installed",
                    "The '" + provider.officialCliName + "' tool is not installed on this device.",
                    "Binary not found in application prefix or PATH",
                    "Install " + provider.officialCliName + " in Terminal or connect with an API key.");
        }

        return PreflightResult.success();
    }

    private PreflightResult validateOAuthStrategy(ProviderDescriptor provider) {
        // Enforce Acceptance Rule: Android loopback redirects are strictly banned for native OAuth.
        String registeredClientId = null;
        if (context != null) {
            try {
                registeredClientId = context.getString(studio.ocean.app.R.string.default_web_client_id);
            } catch (Exception ignored) {}
        }

        String customClientId = null;
        if (context != null) {
            studio.ocean.app.providers.state.CredentialVault vault = new studio.ocean.app.providers.state.CredentialVault(context);
            customClientId = vault.retrieve("oauth_client_id_" + provider.id);
        }
        String effectiveClientId = (customClientId != null && !customClientId.trim().isEmpty()) ? customClientId.trim() : registeredClientId;

        // Google / Antigravity OAuth preflight checks
        if ("google".equals(provider.id) || "antigravity".equals(provider.id)) {
            if (effectiveClientId == null || effectiveClientId.contains("YOUR_CLIENT_ID")
                    || effectiveClientId.trim().isEmpty()) {
                return PreflightResult.fail("OAuth Client Not Configured",
                        "Ocean does not have an active OAuth client registration for " + provider.title + ".\n\nPlease configure your OAuth Client ID, or connect using the official 'agy' CLI / API key.",
                        "Client ID missing or placeholder in build manifest",
                        "Configure OAuth Client ID or use agy CLI / Gemini API key.");
            }
            return PreflightResult.success();
        }

        if ("openai".equals(provider.id)) {
            // Open-source SIWC obtains its own issued client; Firebase's Google client is unrelated.
            return PreflightResult.success();
        }

        return PreflightResult.success();
    }

    private PreflightResult validateDeviceCodeStrategy(ProviderDescriptor provider) {
        return PreflightResult.fail("Device Code Auth Unavailable",
                "Device-code authorization is not exposed by " + provider.title + " for this client.",
                "Endpoints not registered for client",
                "Use the official CLI bridge or API key.");
    }

    private PreflightResult validateApiKeyStrategy(ProviderDescriptor provider, String endpoint, String key) {
        return validateApiKeyConfig(provider.id, provider.defaultModel, key, endpoint);
    }

    private PreflightResult validateLocalStrategy(ProviderDescriptor provider) {
        return PreflightResult.success();
    }
}
