package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import studio.ocean.app.providers.state.CredentialRecord;
import studio.ocean.app.providers.state.CredentialVault;
import studio.ocean.app.providers.state.ProviderConnectionStore;

/**
 * Orchestrates Direct Connect authentication, PKCE challenge generation,
 * state validation, transaction lifecycle, and credential persistence (Directive 2026-10-02 §11, §12, §13).
 * Connects only to genuine upstream endpoints; strictly bans fake tokens and scaffolds.
 * Fully survives Android process death and protects against replay attacks using AuthSessionManager.
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
    private final AuthSessionManager sessionManager;
    private final CallbackBroker callbackBroker;
    private final Map<String, DirectAuthAdapter> adapters = new ConcurrentHashMap<>();

    public AuthOrchestrator(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
        this.connectionStore = new ProviderConnectionStore(this.context);
        this.sessionManager = new AuthSessionManager(this.context);
        this.callbackBroker = new CallbackBroker(this.context, this.sessionManager);
        registerDefaultAdapters();
    }

    public AuthSessionManager getSessionManager() {
        return sessionManager;
    }

    public CallbackBroker getCallbackBroker() {
        return callbackBroker;
    }

    private void registerDefaultAdapters() {
        adapters.put(ProviderRegistry.ID_GOOGLE, new GoogleDirectAuthAdapter(context));
        adapters.put(ProviderRegistry.ID_ANTIGRAVITY, new AntigravityDirectAuthAdapter(context));
        adapters.put(ProviderRegistry.ID_ANTIGRAVITY_IDE, new AntigravityDirectAuthAdapter(context));
        adapters.put(ProviderRegistry.ID_ANTIGRAVITY_20, new AntigravityDirectAuthAdapter(context));
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

                // Persist transaction across process death in AuthSessionManager (Directive §11, §12)
                AuthTransaction tx = sessionManager.createTransaction(
                        txId,
                        desc.id,
                        state,
                        null,
                        verifier,
                        challenge,
                        "ocean://auth/callback",
                        null,
                        Collections.singletonList("model:chat"),
                        null
                );

                DirectAuthAdapter.AuthRequest req = new DirectAuthAdapter.AuthRequest(
                        txId, desc.id, null, state, verifier, challenge, "ocean://auth/callback", Collections.singletonList("model:chat")
                );

                DirectAuthAdapter.AuthStartResult startResult = adapter.start(req);
                sessionManager.updatePhase(txId, AuthTransaction.PHASE_BROWSER_ACTIVE);

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
        AuthTransaction tx = null;

        if (transactionId != null) {
            tx = sessionManager.getTransaction(transactionId);
        }
        if (tx == null && stateFromUri != null) {
            tx = sessionManager.getTransactionByState(stateFromUri);
        }

        if (tx == null) {
            callback.onFailure("Authentication transaction expired or not found.");
            return;
        }

        if (tx.replayConsumed) {
            callback.onFailure("Security violation: OAuth transaction was already consumed (replay attack rejected).");
            return;
        }

        // Verify state parameter matches
        if (stateFromUri == null || !stateFromUri.equals(tx.state)) {
            callback.onFailure("Authentication state mismatch. Possible replay or CSRF attack rejected.");
            return;
        }

        // Mark consumed immediately to prevent replay
        AuthTransaction consumedTx;
        try {
            consumedTx = sessionManager.consumeTransaction(tx.id);
        } catch (Exception e) {
            callback.onFailure(e.getMessage());
            return;
        }

        final AuthTransaction finalTx = consumedTx;
        new Thread(() -> {
            try {
                String err = uri.getQueryParameter("error");
                String errDesc = uri.getQueryParameter("error_description");
                if (err != null) {
                    sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_FAILED);
                    callback.onFailure("Provider authorization was denied: " + (errDesc != null ? errDesc : err));
                    return;
                }

                DirectAuthAdapter adapter = adapters.get(finalTx.providerId);
                if (adapter == null) {
                    sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_FAILED);
                    callback.onFailure("No authentication adapter configured for " + finalTx.providerId);
                    return;
                }

                sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_VERIFYING);

                // 1. Live token exchange
                DirectAuthAdapter.AuthRequest originalReq = new DirectAuthAdapter.AuthRequest(
                        finalTx.id, finalTx.providerId, null, finalTx.state, finalTx.codeVerifier,
                        finalTx.codeChallenge, finalTx.redirectDescriptor, finalTx.requestedScopes
                );
                DirectAuthAdapter.AuthResult result = adapter.handleCallback(uri, originalReq);
                if (!result.isSuccess) {
                    sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_FAILED);
                    callback.onFailure("Token exchange failed: " + result.error);
                    return;
                }

                // 2. Real minimal authenticated probe before CONNECTED (Directive §4.3 #8, §5)
                boolean probeSuccess = false;
                try {
                    probeSuccess = adapter.probe(result.accessToken, null);
                } catch (Exception probeEx) {
                    sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_FAILED);
                    callback.onFailure("Authentication probe failed: " + probeEx.getMessage());
                    return;
                }

                if (!probeSuccess) {
                    sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_FAILED);
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

                // 4. Secure storage in Keystore-backed CredentialVault (both structured record and ref key)
                String tokenToStore = (result.refreshToken != null && !result.refreshToken.isEmpty())
                        ? result.refreshToken : result.accessToken;
                String credRef = credentialVault.store(tokenToStore);

                CredentialRecord record = new CredentialRecord(
                        finalTx.providerId,
                        result.accountId,
                        result.accessToken,
                        result.refreshToken,
                        null,
                        result.expiresAtEpochMs != null ? result.expiresAtEpochMs : 0L,
                        finalTx.requestedScopes,
                        null,
                        null,
                        result.displayName,
                        result.planTier,
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        1L
                );
                credentialVault.storeRecord(record);

                // 5. Save verified ProviderConnection
                String connId = UUID.randomUUID().toString();
                String displayName = result.displayName != null ? result.displayName : "Connected Account";
                ProviderConnection connection = new ProviderConnection(
                        connId,
                        finalTx.providerId,
                        displayName,
                        AuthStrategy.DIRECT_OAUTH,
                        ConnectionStatus.CONNECTED,
                        null,
                        defaultModel,
                        credRef,
                        null,
                        finalTx.requestedScopes,
                        result.expiresAtEpochMs,
                        quota,
                        discoveredModels,
                        System.currentTimeMillis()
                );

                connectionStore.save(connection);
                sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_COMPLETED);
                callback.onSuccess(connection);

            } catch (Exception e) {
                sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_FAILED);
                callback.onFailure("Authentication callback processing failed: " + e.getMessage());
            }
        }).start();
    }
}
