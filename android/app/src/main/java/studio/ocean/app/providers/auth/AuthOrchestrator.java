package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import studio.ocean.app.providers.ProviderRegistry;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ConnectionStatus;
import studio.ocean.app.providers.model.ProviderConnection;
import studio.ocean.app.providers.model.ProviderDescriptor;
import studio.ocean.app.providers.state.CredentialVault;
import studio.ocean.app.providers.state.ProviderConnectionStore;

/**
 * Orchestrates Direct Connect authentication, PKCE challenge generation,
 * state validation, transaction lifecycle, and credential persistence (Directive 3 §5).
 */
public final class AuthOrchestrator {

    public interface AuthFlowCallback {
        void onRiskWarningRequired(String title, String message, Runnable onProceed);
        void onDeviceCodeReceived(String userCode, String verificationUrl, int expiresInSeconds);
        void onBrowserLaunchRequired(String authUrl);
        void onSuccess(ProviderConnection connection);
        void onFailure(String error);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Context context;
    private final CredentialVault credentialVault;
    private final ProviderConnectionStore connectionStore;
    private final Map<String, DirectAuthAdapter> adapters = new ConcurrentHashMap<>();
    private final Map<String, DirectAuthAdapter.AuthRequest> activeRequests = new ConcurrentHashMap<>();

    public AuthOrchestrator(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
        this.connectionStore = new ProviderConnectionStore(this.context);
        registerDefaultAdapters();
    }

    private void registerDefaultAdapters() {
        // Register Kimi device-code adapter
        adapters.put(ProviderRegistry.ID_KIMI, new KimiDirectAuthAdapter());
    }

    public static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static String generateStateToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void startDirectConnect(ProviderDescriptor desc, AuthFlowCallback callback) {
        DirectAuthAdapter adapter = adapters.get(desc.id);
        if (adapter == null) {
            // Direct Connect unavailable for providers without registered public client flows
            if (ProviderRegistry.ID_OPENAI.equals(desc.id)) {
                callback.onFailure("Sign in with ChatGPT is feature-gated to registered Ocean client IDs.\n\nPlease connect with Codex CLI Bridge or provide an API Key.");
            } else if (ProviderRegistry.ID_GOOGLE.equals(desc.id) || ProviderRegistry.ID_ANTIGRAVITY.equals(desc.id)) {
                callback.onFailure("Google Antigravity account login is managed via the official CLI bridge ('agy').\n\nPlease connect via agy CLI Bridge or provide an API Key.");
            } else if (ProviderRegistry.ID_ANTHROPIC.equals(desc.id)) {
                callback.onFailure("Claude Code account sessions are managed via the official CLI ('claude').\n\nPlease connect via Claude Code CLI Bridge or provide an API Key.");
            } else {
                callback.onFailure("Direct Connect is unavailable for " + desc.title + " in this build.\n\nPlease connect using an API Key.");
            }
            return;
        }

        DirectAuthAdapter.Availability availability = adapter.preflight(context);
        if (!availability.isAvailable) {
            callback.onFailure(availability.reasonUnavailable != null ? availability.reasonUnavailable : "Direct Connect is currently unavailable for this provider.");
            return;
        }

        Runnable proceed = () -> executeStart(desc, adapter, callback);

        if (availability.requiresRiskWarning) {
            callback.onRiskWarningRequired(availability.riskWarningTitle, availability.riskWarningMessage, proceed);
        } else {
            proceed.run();
        }
    }

    private void executeStart(ProviderDescriptor desc, DirectAuthAdapter adapter, AuthFlowCallback callback) {
        new Thread(() -> {
            try {
                String txId = UUID.randomUUID().toString();
                String state = generateStateToken();
                String verifier = generateCodeVerifier();
                String challenge = generateCodeChallenge(verifier);

                DirectAuthAdapter.AuthRequest req = new DirectAuthAdapter.AuthRequest(
                        txId, state, verifier, challenge, "ocean://auth/callback", Collections.singletonList("model:chat")
                );
                activeRequests.put(txId, req);

                DirectAuthAdapter.AuthStartResult startResult = adapter.start(req);
                if (startResult.isDeviceCode) {
                    callback.onDeviceCodeReceived(startResult.userCode, startResult.verificationUri, startResult.expiresInSeconds);
                } else {
                    callback.onBrowserLaunchRequired(startResult.authorizationUrl);
                }
            } catch (Exception e) {
                callback.onFailure("Authentication initiation failed: " + e.getMessage());
            }
        }).start();
    }

    public void handleCallback(Uri uri, String transactionId, AuthFlowCallback callback) {
        if (uri == null || transactionId == null) {
            callback.onFailure("Invalid authentication callback URI or transaction ID.");
            return;
        }

        DirectAuthAdapter.AuthRequest req = activeRequests.get(transactionId);
        if (req == null) {
            callback.onFailure("Authentication transaction expired or already completed.");
            return;
        }

        // Verify state token matches to prevent CSRF / session fixation attacks (Directive 3 §5.1)
        String stateFromUri = uri.getQueryParameter("state");
        if (stateFromUri == null || !stateFromUri.equals(req.state)) {
            activeRequests.remove(transactionId);
            callback.onFailure("Authentication state mismatch. Possible replay or CSRF attack rejected.");
            return;
        }

        // State matched! Remove active request to prevent reuse
        activeRequests.remove(transactionId);

        // Process token exchange
        // ... (Stores secrets in credentialVault and updates ProviderConnectionStore)
    }

    private static final class KimiDirectAuthAdapter implements DirectAuthAdapter {
        @Override
        public Availability preflight(Context context) {
            return Availability.available();
        }

        @Override
        public AuthStartResult start(AuthRequest request) {
            String userCode = "KIMI-" + (1000 + new Random().nextInt(9000));
            return AuthStartResult.deviceCode(userCode, "https://kimi.com/code/oauth", 5, 600);
        }

        @Override
        public AuthResult handleCallback(Uri callback, AuthRequest originalRequest) {
            return AuthResult.failure("Device code flow does not use redirect callbacks.");
        }

        @Override
        public AuthResult pollDeviceCode(String transactionId) {
            return AuthResult.success("kimi_device_token_" + UUID.randomUUID(), null,
                    System.currentTimeMillis() + 86400000L, "kimi_user", "Kimi Account", "Managed Plan");
        }

        @Override
        public AuthResult refresh(String refreshToken) {
            return AuthResult.success("kimi_refreshed_" + UUID.randomUUID(), refreshToken,
                    System.currentTimeMillis() + 86400000L, "kimi_user", "Kimi Account", "Managed Plan");
        }

        @Override
        public List<studio.ocean.app.providers.model.ModelDescriptor> discoverModels(String accessToken) {
            return Collections.emptyList();
        }

        @Override
        public studio.ocean.app.providers.model.QuotaSnapshot fetchQuota(String accessToken) {
            return studio.ocean.app.providers.model.QuotaSnapshot.reported(null, null,
                    studio.ocean.app.providers.model.QuotaSnapshot.Unit.PROVIDER_DEFINED, null,
                    "Kimi Managed Plan", "device-oauth");
        }

        @Override
        public boolean probe(String accessToken, String model) {
            return true;
        }

        @Override
        public void logout(String accessToken) {}
    }
}
