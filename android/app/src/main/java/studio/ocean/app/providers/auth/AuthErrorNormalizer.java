package studio.ocean.app.providers.auth;

/**
 * Normalizes technical provider, network, and process exceptions into human-actionable feedback.
 */
public final class AuthErrorNormalizer {

    public static final class NormalizedError {
        public final String title;
        public final String message;
        public final String technicalDetails;
        public final boolean isQuotaExhausted;
        public final boolean isAuthExpired;

        public NormalizedError(String title, String message, String technicalDetails,
                               boolean isQuotaExhausted, boolean isAuthExpired) {
            this.title = title;
            this.message = message;
            this.technicalDetails = technicalDetails;
            this.isQuotaExhausted = isQuotaExhausted;
            this.isAuthExpired = isAuthExpired;
        }
    }

    private AuthErrorNormalizer() {}

    public static NormalizedError normalize(String rawError) {
        if (rawError == null || rawError.isEmpty()) {
            return new NormalizedError("Connection Error", "An unexpected error occurred.", null, false, false);
        }

        String lower = rawError.toLowerCase();

        // 401 / 403 / Invalid Key / Expired Session
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid_api_key")
                || lower.contains("invalid api key") || lower.contains("authentication failed")) {
            return new NormalizedError("Authentication Failed",
                    "The API key or session credential was rejected by the provider. Please verify your credentials.",
                    rawError, false, true);
        }

        // 429 / Quota / Rate Limit
        if (lower.contains("429") || lower.contains("quota") || lower.contains("rate limit")
                || lower.contains("insufficient_quota") || lower.contains("credit")) {
            return new NormalizedError("Usage Limit Reached",
                    "Your provider account has exceeded its current rate limit or quota allowance.",
                    rawError, true, false);
        }

        // Network / DNS / Host Unreachable
        if (lower.contains("unknownhost") || lower.contains("connectexception") || lower.contains("timeout")
                || lower.contains("network is unreachable") || lower.contains("failed to connect")) {
            return new NormalizedError("Network Unavailable",
                    "Unable to reach the provider endpoint. Check your internet connection.",
                    rawError, false, false);
        }

        // Redirect URI Mismatch
        if (lower.contains("redirect_uri_mismatch") || lower.contains("access_blocked")) {
            return new NormalizedError("OAuth Redirect Mismatch",
                    "The provider rejected the local authentication redirect. Direct OAuth requires a registered HTTPS endpoint.",
                    rawError, false, false);
        }

        return new NormalizedError("Connection Error", rawError, rawError, false, false);
    }
}
