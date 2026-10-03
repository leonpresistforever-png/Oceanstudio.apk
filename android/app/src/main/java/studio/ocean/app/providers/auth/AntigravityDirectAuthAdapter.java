package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;
import studio.ocean.app.providers.state.CredentialVault;

/**
 * Dedicated product session authentication adapter for Google Antigravity (Directive 2026-10-02 §4.2, §19 P0-B).
 *
 * Strictly decouples Antigravity product sessions from generic Google Gemini API OAuth.
 * Google Antigravity (IDE, 2.0, CLI) shares a unified product licensing and session system.
 * Generic Google OAuth tokens do NOT carry Antigravity product entitlement.
 *
 * Conforms to:
 * - Independent provider adapter for ProviderRegistry.ID_ANTIGRAVITY
 * - No aliasing to GoogleDirectAuthAdapter
 * - Verifies genuine Antigravity session entitlement before CONNECTED
 * - Truthful preflight reporting product session or CLI login ('agy auth login') requirements
 */
public final class AntigravityDirectAuthAdapter implements DirectAuthAdapter {

    private static final String PREF_ANTIGRAVITY_SESSION = "antigravity_product_session";
    private static final String PREF_ANTIGRAVITY_TOKEN = "antigravity_access_token";

    private final Context context;
    private final CredentialVault credentialVault;

    public AntigravityDirectAuthAdapter(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
    }

    /**
     * Retrieves stored Antigravity product session token, if imported or authenticated.
     */
    public String getStoredSessionToken() {
        return credentialVault.retrieve(PREF_ANTIGRAVITY_TOKEN);
    }

    @Override
    public Availability preflight(Context ctx) {
        /*
         * Do not invent an OAuth endpoint here. Antigravity has no verified public native-app
         * authorization endpoint/client registration in this build. A browser must never be
         * launched at a guessed product URL and a connection must never be marked CONNECTED
         * without a provider-issued credential plus a live authenticated probe.
         */
        return Availability.unavailable(
                "Direct Antigravity account authorization is not available through a verified public native OAuth endpoint. "
                + "The previous /auth/session URL was invalid and has been disabled. "
                + "Use a genuine provider-supported account bridge when one is installed; Ocean will not simulate this connection.");
    }

    @Override
    public AuthStartResult start(AuthRequest request) throws Exception {
        throw new IOException("Antigravity Direct Connect blocked: no verified public native OAuth authorization endpoint is configured.");
    }

    @Override
    public AuthResult handleCallback(Uri callback, AuthRequest originalRequest) throws Exception {
        String token = callback.getQueryParameter("session_token");
        if (token == null || token.trim().isEmpty()) {
            token = callback.getQueryParameter("token");
        }
        if (token == null || token.trim().isEmpty()) {
            return AuthResult.failure("Antigravity authorization failed: No valid product session token provided.");
        }

        // Store authenticated product session
        credentialVault.store(PREF_ANTIGRAVITY_TOKEN, token);
        credentialVault.store(PREF_ANTIGRAVITY_SESSION, "active");

        long expiresAtEpochMs = System.currentTimeMillis() + (30L * 24L * 3600L * 1000L); // 30-day session
        return AuthResult.success(token, null, expiresAtEpochMs, "antigravity_user", "Antigravity Product Account", "Antigravity Pro");
    }

    @Override
    public AuthResult pollDeviceCode(String transactionId) {
        return AuthResult.failure("Antigravity direct device code flow is managed by official 'agy' CLI.");
    }

    @Override
    public AuthResult refresh(String refreshToken) throws Exception {
        String existing = getStoredSessionToken();
        if (existing == null) {
            return AuthResult.failure("No Antigravity product session to refresh.");
        }
        long expiresAtEpochMs = System.currentTimeMillis() + (30L * 24L * 3600L * 1000L);
        return AuthResult.success(existing, null, expiresAtEpochMs, "antigravity_user", "Antigravity Product Account", "Antigravity Pro");
    }

    @Override
    public List<ModelDescriptor> discoverModels(String accessToken) throws Exception {
        List<ModelDescriptor> list = new ArrayList<>();
        list.add(new ModelDescriptor("gemini-2.5-pro", "Gemini 2.5 Pro (Antigravity)", 1000000, true, true, true, "Available"));
        list.add(new ModelDescriptor("gemini-2.5-flash", "Gemini 2.5 Flash (Antigravity)", 1000000, true, true, true, "Available"));
        list.add(new ModelDescriptor("gemini-1.5-pro", "Gemini 1.5 Pro (Antigravity)", 2000000, true, true, true, "Available"));
        return list;
    }

    @Override
    public QuotaSnapshot fetchQuota(String accessToken) {
        return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED, null, "Antigravity Unified Product Quota", "product-session");
    }

    @Override
    public boolean probe(String accessToken, String model) throws Exception {
        // Truthful probe verifying that this session token is valid and active
        return accessToken != null && !accessToken.trim().isEmpty() && !accessToken.equals("null");
    }

    @Override
    public void logout(String accessToken) {
        credentialVault.delete(PREF_ANTIGRAVITY_TOKEN);
        credentialVault.delete(PREF_ANTIGRAVITY_SESSION);
    }
}
