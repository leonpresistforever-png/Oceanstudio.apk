package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import java.util.Collections;
import java.util.List;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;

/**
 * Honest implementation for Kimi / Moonshot Direct Connect (Directive 2026-10-02 §4, §5).
 * Replaces synthetic mock scaffolding with truthful preflight status.
 * Never issues fake tokens or fake user codes.
 */
public final class KimiDirectAuthAdapter implements DirectAuthAdapter {

    private final Context context;

    public KimiDirectAuthAdapter(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Availability preflight(Context ctx) {
        return Availability.unavailable(
                "Kimi direct device-code authorization requires enterprise partner registration.\n\n"
                + "Please connect using the official Kimi CLI Bridge or provide your Moonshot API Key."
        );
    }

    @Override
    public AuthStartResult start(AuthRequest request) {
        throw new UnsupportedOperationException("Kimi direct OAuth is not available without enterprise credentials.");
    }

    @Override
    public AuthResult handleCallback(Uri callback, AuthRequest originalRequest) {
        return AuthResult.failure("Kimi direct OAuth callback not supported.");
    }

    @Override
    public AuthResult pollDeviceCode(String transactionId) {
        return AuthResult.failure("Kimi direct device code flow is not available.");
    }

    @Override
    public AuthResult refresh(String refreshToken) {
        return AuthResult.failure("Kimi direct token refresh is not available.");
    }

    @Override
    public List<ModelDescriptor> discoverModels(String accessToken) {
        return Collections.emptyList();
    }

    @Override
    public QuotaSnapshot fetchQuota(String accessToken) {
        return QuotaSnapshot.unknown("Kimi", "unsupported-direct");
    }

    @Override
    public boolean probe(String accessToken, String model) {
        return false;
    }

    @Override
    public void logout(String accessToken) {}
}
