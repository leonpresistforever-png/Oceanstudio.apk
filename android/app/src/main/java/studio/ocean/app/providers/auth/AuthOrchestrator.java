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
import studio.ocean.app.providers.state.CredentialRecord;
import studio.ocean.app.providers.state.CredentialVault;
import studio.ocean.app.providers.state.ProviderConnectionStore;

/**
 * Direct Connect transaction coordinator.
 *
 * The UI never asks a normal user for OAuth engineering values. Provider-specific
 * adapters own their authorization endpoints; this class owns state/PKCE,
 * callback transport, replay prevention, real post-auth verification, secure
 * token persistence, and the final CONNECTED transition.
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
    private final Map<String, CallbackBroker.LoopbackServer> loopbackServers = new ConcurrentHashMap<>();

    public AuthOrchestrator(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
        this.connectionStore = new ProviderConnectionStore(this.context);
        this.sessionManager = new AuthSessionManager(this.context);
        this.callbackBroker = new CallbackBroker(this.context, this.sessionManager);
        registerDefaultAdapters();
    }

    public AuthSessionManager getSessionManager() { return sessionManager; }
    public CallbackBroker getCallbackBroker() { return callbackBroker; }

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
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    public void startDirectConnect(ProviderDescriptor desc, AuthFlowCallback callback) {
        DirectAuthAdapter adapter = adapters.get(desc.id);
        if (adapter == null) {
            callback.onFailure("Direct Connect is not implemented for " + desc.title + " in this build.");
            return;
        }

        DirectAuthAdapter.Availability availability = adapter.preflight(context);
        if (!availability.isAvailable) {
            callback.onFailure(availability.reasonUnavailable != null
                    ? availability.reasonUnavailable
                    : "Direct Connect is currently unavailable for this provider.");
            return;
        }

        Runnable proceed = () -> executeStart(desc, adapter, callback);
        if (availability.requiresRiskWarning) {
            callback.onRiskWarningRequired(
                    availability.riskWarningTitle,
                    availability.riskWarningMessage,
                    proceed);
        } else {
            proceed.run();
        }
    }

    private void executeStart(ProviderDescriptor desc,
                              DirectAuthAdapter adapter,
                              AuthFlowCallback callback) {
        new Thread(() -> {
            String txId = UUID.randomUUID().toString();
            CallbackBroker.LoopbackServer loopback = null;
            try {
                String state = generateStateToken();
                String verifier = generateCodeVerifier();
                String challenge = generateCodeChallenge(verifier);
                List<String> scopes = requestedScopes(desc.id);

                String redirectUri = "ocean://auth/callback";
                if (requiresLoopback(desc.id)) {
                    loopback = callbackBroker.startLoopbackListener(
                            state,
                            180_000L,
                            new CallbackBroker.CallbackListener() {
                                @Override
                                public void onCodeReceived(AuthTransaction transaction,
                                                           String code,
                                                           String returnedState) {
                                    Uri callbackUri = Uri.parse(
                                            transaction.redirectDescriptor
                                                    + "?code=" + Uri.encode(code)
                                                    + "&state=" + Uri.encode(returnedState != null ? returnedState : transaction.state));
                                    processConsumedCallback(callbackUri, transaction, callback);
                                }

                                @Override
                                public void onError(AuthTransaction transaction, String error) {
                                    if (transaction != null) {
                                        sessionManager.updatePhase(transaction.id, AuthTransaction.PHASE_FAILED);
                                        closeLoopback(transaction.id);
                                    }
                                    callback.onFailure(error);
                                }
                            });
                    redirectUri = loopback.getRedirectUri();
                    loopbackServers.put(txId, loopback);
                }

                sessionManager.createTransaction(
                        txId,
                        desc.id,
                        state,
                        null,
                        verifier,
                        challenge,
                        redirectUri,
                        null,
                        scopes,
                        null);

                DirectAuthAdapter.AuthRequest req = new DirectAuthAdapter.AuthRequest(
                        txId,
                        desc.id,
                        null,
                        state,
                        verifier,
                        challenge,
                        redirectUri,
                        scopes);

                DirectAuthAdapter.AuthStartResult startResult = adapter.start(req);
                sessionManager.updatePhase(txId, AuthTransaction.PHASE_BROWSER_ACTIVE);

                if (startResult.isDeviceCode) {
                    callback.onDeviceCodeReceived(
                            startResult.userCode,
                            startResult.verificationUri,
                            startResult.expiresInSeconds);
                } else if (startResult.authorizationUrl != null && !startResult.authorizationUrl.trim().isEmpty()) {
                    callback.onBrowserLaunchRequired(startResult.authorizationUrl);
                } else {
                    throw new IllegalStateException("Provider did not return an authorization URL.");
                }
            } catch (Exception e) {
                closeLoopback(txId);
                sessionManager.updatePhase(txId, AuthTransaction.PHASE_FAILED);
                callback.onFailure("Authentication initiation failed: " + safeMessage(e));
            }
        }, "ocean-direct-auth-start").start();
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
        if (transactionId != null) tx = sessionManager.getTransaction(transactionId);
        if (tx == null && stateFromUri != null) tx = sessionManager.getTransactionByState(stateFromUri);

        if (tx == null) {
            callback.onFailure("Authentication transaction expired or was not found.");
            return;
        }
        if (tx.replayConsumed) {
            callback.onFailure("Security rejection: this OAuth callback was already consumed.");
            return;
        }
        if (stateFromUri == null || !stateFromUri.equals(tx.state)) {
            callback.onFailure("Authentication state mismatch; callback rejected.");
            return;
        }

        try {
            AuthTransaction consumed = sessionManager.consumeTransaction(tx.id);
            processConsumedCallback(uri, consumed, callback);
        } catch (Exception e) {
            callback.onFailure(safeMessage(e));
        }
    }

    /**
     * Processes a callback whose transaction has already been atomically
     * consumed. This is used by both Android deep links and the loopback broker,
     * avoiding the old double-consume/replay bug.
     */
    private void processConsumedCallback(Uri uri,
                                         AuthTransaction finalTx,
                                         AuthFlowCallback callback) {
        new Thread(() -> {
            try {
                String err = uri.getQueryParameter("error");
                String errDesc = uri.getQueryParameter("error_description");
                if (err != null) {
                    fail(finalTx, callback,
                            "Provider authorization was denied: "
                                    + (errDesc != null ? errDesc : err));
                    return;
                }

                DirectAuthAdapter adapter = adapters.get(finalTx.providerId);
                if (adapter == null) {
                    fail(finalTx, callback,
                            "No authentication adapter is configured for " + finalTx.providerId);
                    return;
                }

                sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_VERIFYING);

                DirectAuthAdapter.AuthRequest originalReq = new DirectAuthAdapter.AuthRequest(
                        finalTx.id,
                        finalTx.providerId,
                        null,
                        finalTx.state,
                        finalTx.codeVerifier,
                        finalTx.codeChallenge,
                        finalTx.redirectDescriptor,
                        finalTx.requestedScopes);

                DirectAuthAdapter.AuthResult result = adapter.handleCallback(uri, originalReq);
                if (!result.isSuccess || result.accessToken == null || result.accessToken.trim().isEmpty()) {
                    fail(finalTx, callback,
                            "Token exchange failed: "
                                    + (result.error != null ? result.error : "provider returned no access token"));
                    return;
                }

                if (!adapter.probe(result.accessToken, null)) {
                    fail(finalTx, callback,
                            "Provider rejected the authenticated verification probe.");
                    return;
                }

                List<ModelDescriptor> discoveredModels = adapter.discoverModels(result.accessToken);
                if (isAntigravity(finalTx.providerId)
                        && (discoveredModels == null || discoveredModels.isEmpty())) {
                    fail(finalTx, callback,
                            "Antigravity authorization succeeded but no live models were discovered; Ocean will not mark this account Connected.");
                    return;
                }

                QuotaSnapshot quota;
                try {
                    quota = adapter.fetchQuota(result.accessToken);
                } catch (Exception ignored) {
                    quota = QuotaSnapshot.unknown("Provider", "quota-unavailable");
                }

                String defaultModel = discoveredModels != null && !discoveredModels.isEmpty()
                        ? discoveredModels.get(0).id
                        : null;

                // credentialRef is always the *current access token*. Refresh
                // tokens live only inside the structured Keystore record.
                String credRef = credentialVault.store(result.accessToken);
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
                        1L);
                credentialVault.storeRecord(record);

                ProviderConnection connection = new ProviderConnection(
                        UUID.randomUUID().toString(),
                        finalTx.providerId,
                        result.displayName != null ? result.displayName : "Connected Account",
                        AuthStrategy.DIRECT_OAUTH,
                        ConnectionStatus.CONNECTED,
                        providerBaseUrl(finalTx.providerId),
                        defaultModel,
                        credRef,
                        null,
                        finalTx.requestedScopes,
                        result.expiresAtEpochMs,
                        quota,
                        discoveredModels,
                        System.currentTimeMillis());

                connectionStore.save(connection);
                sessionManager.updatePhase(finalTx.id, AuthTransaction.PHASE_COMPLETED);
                closeLoopback(finalTx.id);
                callback.onSuccess(connection);
            } catch (Exception e) {
                fail(finalTx, callback,
                        "Authentication callback processing failed: " + safeMessage(e));
            }
        }, "ocean-direct-auth-callback").start();
    }

    private void fail(AuthTransaction tx, AuthFlowCallback callback, String message) {
        if (tx != null) {
            sessionManager.updatePhase(tx.id, AuthTransaction.PHASE_FAILED);
            closeLoopback(tx.id);
        }
        callback.onFailure(message);
    }

    private void closeLoopback(String txId) {
        CallbackBroker.LoopbackServer server = loopbackServers.remove(txId);
        if (server != null) {
            try { server.close(); } catch (Exception ignored) {}
        }
    }

    private static boolean isAntigravity(String providerId) {
        return ProviderRegistry.ID_ANTIGRAVITY.equals(providerId)
                || ProviderRegistry.ID_ANTIGRAVITY_IDE.equals(providerId)
                || ProviderRegistry.ID_ANTIGRAVITY_20.equals(providerId);
    }

    private static boolean requiresLoopback(String providerId) {
        return isAntigravity(providerId);
    }

    private static List<String> requestedScopes(String providerId) {
        if (isAntigravity(providerId)) return AntigravityDirectAuthAdapter.requestedScopes();
        return Collections.singletonList("model:chat");
    }

    private static String providerBaseUrl(String providerId) {
        if (isAntigravity(providerId)) {
            return "https://daily-cloudcode-pa.googleapis.com";
        }
        return "";
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }
}
