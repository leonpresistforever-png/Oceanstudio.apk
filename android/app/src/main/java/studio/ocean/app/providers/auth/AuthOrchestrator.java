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
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.ProviderConnection;
import studio.ocean.app.providers.model.ProviderDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;
import studio.ocean.app.providers.state.CredentialVault;
import studio.ocean.app.providers.state.ProviderConnectionStore;

/**
 * Orchestrates Direct Connect authentication, PKCE challenge generation,
 * state validation, transaction lifecycle, and credential persistence (Directive 2026-10-02 §4, §5).
 * Connects only to genuine upstream endpoints; strictly bans fake tokens and scaffolds.
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
    private final Map<String, DirectAuthAdapter.AuthRequest> activeRequestsByTxId = new ConcurrentHashMap<>();
    private final Map<String, DirectAuthAdapter.AuthRequest> activeRequestsByState = new ConcurrentHashMap<>();

    public AuthOrchestrator(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
        this.connectionStore = new ProviderConnectionStore(this.context);
        registerDefaultAdapters();
    }

    private void registerDefaultAdapters() {
        GoogleDirectAuthAdapter googleAdapter = new GoogleDirectAuthAdapter(context);
        adapters.put(ProviderRegistry.ID_GOOGLE, googleAdapter);
        adapters.put(ProviderRegistry.ID_ANTIGRAVITY, googleAdapter);

        adapters.put(ProviderRegistry.ID_OPENAI, new OpenAiDirectAuthAdapter(context));
        adapters.put(ProviderRegistry.ID_ANTHROPIC, new AnthropicDirectAuthAdapter(context));
        adapters.put(ProviderRegistry.ID_KIMI, new KimiDirectAuthAdapter(context));
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
            callback.onFailure("Direct Connect is unavailable for " + desc.title + " in this build.\n\nPlease connect using an API Key.");
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

                String customClientId = credentialVault.retrieve("oauth_client_id_" + desc.id);
                DirectAuthAdapter.AuthRequest req = new DirectAuthAdapter.AuthRequest(
                        txId, desc.id, customClientId, state, verifier, challenge, "ocean://auth/callback", Collections.singletonList("model:chat")
                );
                activeRequestsByTxId.put(txId, req);
                activeRequestsByState.put(state, req);

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

    public void handleCallback(Uri uri, AuthFlowCallback callback) {
        handleCallback(uri, null, callback);
    }

    public void handleCallback(Uri uri, String transactionId, AuthFlowCallback callback) {
        if (uri == null) {
            callback.onFailure("Invalid authentication callback: URI is null.");
            return;
        }

        String stateFromUri = uri.getQueryParameter("state");
        DirectAuthAdapter.AuthRequest req = null;

        if (transactionId != null) {
            req = activeRequestsByTxId.remove(transactionId);
        }
        if (req == null && stateFromUri != null) {
            req = activeRequestsByState.remove(stateFromUri);
        }

        if (req == null) {
            callback.onFailure("Authentication transaction expired or already completed (replay rejected).");
            return;
        }

        // Clean up both maps to prevent replay
        activeRequestsByTxId.remove(req.transactionId);
        activeRequestsByState.remove(req.state);

        // Verify state parameter matches
        if (stateFromUri == null || !stateFromUri.equals(req.state)) {
            callback.onFailure("Authentication state mismatch. Possible replay or CSRF attack rejected.");
            return;
        }

        final DirectAuthAdapter.AuthRequest finalReq = req;
        new Thread(() -> {
            try {
                String err = uri.getQueryParameter("error");
                String errDesc = uri.getQueryParameter("error_description");
                if (err != null) {
                    callback.onFailure("Provider authorization was denied: " + (errDesc != null ? errDesc : err));
                    return;
                }

                DirectAuthAdapter adapter = adapters.get(finalReq.providerId);
                if (adapter == null) {
                    callback.onFailure("No authentication adapter configured for " + finalReq.providerId);
                    return;
                }

                // 1. Live token exchange
                DirectAuthAdapter.AuthResult result = adapter.handleCallback(uri, finalReq);
                if (!result.isSuccess) {
                    callback.onFailure("Token exchange failed: " + result.error);
                    return;
                }

                // 2. Real minimal authenticated probe before CONNECTED (Directive §4.3 #8, §5)
                boolean probeSuccess = false;
                try {
                    probeSuccess = adapter.probe(result.accessToken, null);
                } catch (Exception probeEx) {
                    callback.onFailure("Authentication probe failed: " + probeEx.getMessage());
                    return;
                }

                if (!probeSuccess) {
                    callback.onFailure("Provider rejected authentication probe: invalid token or insufficient scopes.");
                    return;
                }

                // 3. Discover models & quota
                List<ModelDescriptor> discoveredModels = null;
                try {
                    discoveredModels = adapter.discoverModels(result.accessToken);
                } catch (Exception ignored) {}

                QuotaSnapshot quota = null;
                try {
                    quota = adapter.fetchQuota(result.accessToken);
                } catch (Exception ignored) {}

                String defaultModel = (discoveredModels != null && !discoveredModels.isEmpty())
                        ? discoveredModels.get(0).id : null;

                // 4. Secure storage in Keystore-backed CredentialVault
                String tokenToStore = (result.refreshToken != null && !result.refreshToken.isEmpty())
                        ? result.refreshToken : result.accessToken;
                String credRef = credentialVault.store(tokenToStore);

                // 5. Save verified ProviderConnection
                String connId = UUID.randomUUID().toString();
                String displayName = result.displayName != null ? result.displayName : "Connected Account";
                ProviderConnection connection = new ProviderConnection(
                        connId,
                        finalReq.providerId,
                        displayName,
                        AuthStrategy.DIRECT_OAUTH,
                        ConnectionStatus.CONNECTED,
                        null,
                        defaultModel,
                        credRef,
                        null,
                        finalReq.scopes,
                        result.expiresAtEpochMs,
                        quota,
                        discoveredModels,
                        System.currentTimeMillis()
                );

                connectionStore.save(connection);
                callback.onSuccess(connection);

            } catch (Exception e) {
                callback.onFailure("Authentication callback processing failed: " + e.getMessage());
            }
        }).start();
    }
}
