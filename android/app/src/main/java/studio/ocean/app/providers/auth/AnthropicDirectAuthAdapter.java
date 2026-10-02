package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import java.util.Collections;
import java.util.List;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;

/**
 * Honest implementation for Anthropic Direct Connect (Directive 2026-10-02 §4, §5).
 * Anthropic does not expose a public OAuth 2.0 flow for third-party Android applications.
 * Directly informs the user to connect via official Claude Code CLI or Anthropic API Key.
 */
public final class AnthropicDirectAuthAdapter implements DirectAuthAdapter {

    private final Context context;

    public AnthropicDirectAuthAdapter(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Availability preflight(Context ctx) {
        return Availability.unavailable(
                "Anthropic does not offer public third-party OAuth for native mobile apps.\n\n"
                + "Please connect via the official Claude Code CLI Bridge ('claude') or enter your Anthropic API Key."
        );
    }

    @Override
    public AuthStartResult start(AuthRequest request) {
        throw new UnsupportedOperationException("Anthropic direct OAuth is not supported by upstream provider.");
    }

    @Override
    public AuthResult handleCallback(Uri callback, AuthRequest originalRequest) {
        return AuthResult.failure("Anthropic direct OAuth is not supported by upstream provider.");
    }

    @Override
    public AuthResult pollDeviceCode(String transactionId) {
        return AuthResult.failure("Anthropic direct device code flow is not supported.");
    }

    @Override
    public AuthResult refresh(String refreshToken) {
        return AuthResult.failure("Anthropic direct OAuth refresh is not supported.");
    }

    @Override
    public List<ModelDescriptor> discoverModels(String accessToken) {
        return Collections.emptyList();
    }

    @Override
    public QuotaSnapshot fetchQuota(String accessToken) {
        return QuotaSnapshot.unknown("Anthropic", "unsupported-direct");
    }

    @Override
    public boolean probe(String accessToken, String model) {
        return false;
    }

    @Override
    public void logout(String accessToken) {}
}
