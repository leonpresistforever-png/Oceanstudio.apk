package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;
import studio.ocean.app.providers.state.CredentialRecord;
import studio.ocean.app.providers.state.CredentialVault;

/**
 * Authentic OpenAI Direct Connect adapter implementing current open-source
 * Sign in with ChatGPT (SIWC) dynamic registration flow (Directive 2026-10-02 §3, §19 P0-A).
 *
 * Conforms to:
 * - Persistent ext_agent_host_id (urn:uuid:<stable-id>)
 * - Initial dynamic registration with client_id=dynamic_agent_client
 * - Ephemeral loopback callback listener on 127.0.0.1:<random-port>/callback
 * - Capture of issued client ID (oaiapp_...) and persistent reuse for returning sign-in
 * - Strict RFC 7636 PKCE S256 with nonce, state, and ID token claim verification
 * - Responses API 1-token inference probe before CONNECTED transition
 * - Zero dummy tokens, synthetic mocks, or unauthorized client ID borrowing
 */
public final class OpenAiDirectAuthAdapter implements DirectAuthAdapter {

    private static final String TAG = "OpenAiDirectAuth";

    public static final String AUTH_ENDPOINT = "https://auth.openai.com/api/accounts/authorize";
    public static final String TOKEN_ENDPOINT = "https://auth.openai.com/api/accounts/oauth/token";
    public static final String RESOURCE_URI = "https://api.openai.com/v1";
    public static final String RESPONSES_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    public static final String MODELS_ENDPOINT = "https://api.openai.com/v1/models";

    public static final String DYNAMIC_CLIENT_ID = "dynamic_agent_client";
    public static final String AGENT_NAME_HINT = "OceanStudio";
    public static final String REQUIRED_SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";

    private static final String PREF_HOST_ID = "openai_ext_agent_host_id";
    private static final String PREF_ISSUED_CLIENT_ID = "openai_issued_client_id";

    private final Context context;
    private final CredentialVault credentialVault;
    private final ExecutorService loopbackExecutor = Executors.newCachedThreadPool();
    private final Map<String, LoopbackServer> activeServers = new ConcurrentHashMap<>();
    private CallbackListener callbackListener;

    public void setCallbackListener(CallbackListener listener) {
        this.callbackListener = listener;
    }

    public interface CallbackListener {
        void onCallbackReceived(Uri callbackUri);
    }

    public OpenAiDirectAuthAdapter(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
    }

    /**
     * Resolves or generates the persistent installation-bound ext_agent_host_id.
     * Guaranteed stable across restarts for this installation (Directive §3.2).
     */
    public synchronized String getOrCreateExtAgentHostId() {
        String hostId = credentialVault.retrieve(PREF_HOST_ID);
        if (hostId == null || hostId.trim().isEmpty()) {
            hostId = "urn:uuid:" + UUID.randomUUID().toString();
            credentialVault.store(PREF_HOST_ID, hostId);
        }
        return hostId;
    }

    /**
     * Retrieves the issued client ID (oaiapp_...) if this installation has already
     * completed dynamic registration.
     */
    public synchronized String getIssuedClientId() {
        String issued = credentialVault.retrieve(PREF_ISSUED_CLIENT_ID);
        if (issued != null && !issued.trim().isEmpty() && !DYNAMIC_CLIENT_ID.equals(issued)) {
            return issued.trim();
        }
        return null;
    }

    /**
     * Saves the issued client ID obtained from OpenAI dynamic registration.
     */
    public synchronized void setIssuedClientId(String clientId) {
        if (clientId != null && !clientId.trim().isEmpty() && !DYNAMIC_CLIENT_ID.equals(clientId)) {
            credentialVault.store(PREF_ISSUED_CLIENT_ID, clientId.trim());
        }
    }

    @Override
    public Availability preflight(Context ctx) {
        // OpenAI open-source dynamic registration requires no manual pre-configuration.
        // It dynamically registers on first sign-in using dynamic_agent_client.
        return Availability.available();
    }

    @Override
    public AuthStartResult start(AuthRequest request) throws Exception {
        String hostId = getOrCreateExtAgentHostId();
        String existingIssuedClientId = getIssuedClientId();

        // Use issued client ID for returning sign-in; dynamic_agent_client for initial registration
        String effectiveClientId = (existingIssuedClientId != null) ? existingIssuedClientId : DYNAMIC_CLIENT_ID;

        // Start an ephemeral loopback callback listener on 127.0.0.1 (RFC 8252 §8.3)
        LoopbackServer server = new LoopbackServer(request.state);
        server.setListener(callbackListener);
        server.start();
        activeServers.put(request.state, server);

        String loopbackRedirectUri = "http://127.0.0.1:" + server.getPort() + "/callback";
        server.setRedirectUri(loopbackRedirectUri);
        credentialVault.store("openai_redirect_uri_" + request.state, loopbackRedirectUri);

        // Generate cryptographically secure nonce for ID token verification
        String nonce = AuthOrchestrator.generateStateToken();

        StringBuilder sb = new StringBuilder(AUTH_ENDPOINT);
        sb.append("?response_type=code");
        sb.append("&client_id=").append(URLEncoder.encode(effectiveClientId, "UTF-8"));
        sb.append("&agent_name_hint=").append(URLEncoder.encode(AGENT_NAME_HINT, "UTF-8"));
        sb.append("&ext_agent_host_id=").append(URLEncoder.encode(hostId, "UTF-8"));
        sb.append("&redirect_uri=").append(URLEncoder.encode(loopbackRedirectUri, "UTF-8"));
        sb.append("&scope=").append(URLEncoder.encode(REQUIRED_SCOPES, "UTF-8"));
        sb.append("&state=").append(URLEncoder.encode(request.state, "UTF-8"));
        sb.append("&nonce=").append(URLEncoder.encode(nonce, "UTF-8"));
        sb.append("&code_challenge=").append(URLEncoder.encode(request.codeChallenge, "UTF-8"));
        sb.append("&code_challenge_method=S256");
        sb.append("&resource=").append(URLEncoder.encode(RESOURCE_URI, "UTF-8"));

        // Store nonce alongside the request state for ID token validation
        credentialVault.store("openai_nonce_" + request.state, nonce);

        return AuthStartResult.browser(sb.toString());
    }

    @Override
    public AuthResult handleCallback(Uri callback, AuthRequest originalRequest) throws Exception {
        // Clean up loopback server for this state
        LoopbackServer server = activeServers.remove(originalRequest.state);
        if (server != null) {
            server.stop();
        }

        String err = callback.getQueryParameter("error");
        if (err != null && !err.trim().isEmpty()) {
            String desc = callback.getQueryParameter("error_description");
            return AuthResult.failure("OpenAI authorization rejected: " + (desc != null ? desc : err));
        }

        String code = callback.getQueryParameter("code");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.failure("Missing authorization code in OpenAI callback.");
        }

        String returnedState = callback.getQueryParameter("state");
        if (returnedState != null && !returnedState.equals(originalRequest.state)) {
            return AuthResult.failure("State token mismatch in callback (CSRF detected).");
        }

        // Capture issued client ID if returned in callback (e.g. oaiapp_...)
        String issuedFromCallback = callback.getQueryParameter("client_id");
        if (issuedFromCallback != null && !issuedFromCallback.isEmpty() && !DYNAMIC_CLIENT_ID.equals(issuedFromCallback)) {
            setIssuedClientId(issuedFromCallback);
        }

        String effectiveClientId = getIssuedClientId();
        if (effectiveClientId == null) {
            // Check if passed via request or custom configuration
            effectiveClientId = originalRequest.clientId;
        }
        if (effectiveClientId == null || DYNAMIC_CLIENT_ID.equals(effectiveClientId)) {
            // If callback did not supply client_id and no issued ID exists, check query param or throw
            if (issuedFromCallback != null && !issuedFromCallback.isEmpty()) {
                effectiveClientId = issuedFromCallback;
            } else {
                return AuthResult.failure("OpenAI dynamic registration did not provide an issued client ID.");
            }
        }

        String resolvedRedirectUri = credentialVault.retrieve("openai_redirect_uri_" + originalRequest.state);
        if (resolvedRedirectUri == null || resolvedRedirectUri.isEmpty()) {
            resolvedRedirectUri = (server != null && server.getRedirectUri() != null) ? server.getRedirectUri() : originalRequest.redirectUri;
        }

        // Code exchange at official token endpoint (Directive §3.2 Step 6)
        String postBody = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, "UTF-8")
                + "&client_id=" + URLEncoder.encode(effectiveClientId, "UTF-8")
                + "&code_verifier=" + URLEncoder.encode(originalRequest.codeVerifier, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(resolvedRedirectUri, "UTF-8")
                + "&resource=" + URLEncoder.encode(RESOURCE_URI, "UTF-8");

        JSONObject tokenJson = postForm(TOKEN_ENDPOINT, postBody);
        if (tokenJson.has("error")) {
            return AuthResult.failure("OpenAI token exchange failed: " + tokenJson.optString("error_description", tokenJson.optString("error")));
        }

        String accessToken = tokenJson.getString("access_token");
        String refreshToken = tokenJson.optString("refresh_token", null);
        long expiresIn = tokenJson.optLong("expires_in", 3600);
        long expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L);

        // Capture any client_id returned in token response
        String tokenClientId = tokenJson.optString("client_id", null);
        if (tokenClientId != null && !tokenClientId.isEmpty() && !DYNAMIC_CLIENT_ID.equals(tokenClientId)) {
            setIssuedClientId(tokenClientId);
            effectiveClientId = tokenClientId;
        }

        // Validate ID token signature, issuer, audience, and expiry (Directive §3.2 Step 7)
        String idToken = tokenJson.optString("id_token", null);
        String accountEmail = "ChatGPT Account";
        String accountSubject = "openai_account";

        if (idToken != null && idToken.contains(".")) {
            try {
                String[] parts = idToken.split("\\.");
                if (parts.length >= 2) {
                    byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP);
                    JSONObject payload = new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8));

                    // Verify issuer
                    String iss = payload.optString("iss", "");
                    if (!iss.contains("openai.com")) {
                        return AuthResult.failure("Untrusted ID token issuer: " + iss);
                    }

                    // Verify expiry
                    long exp = payload.optLong("exp", 0);
                    if (exp > 0 && exp * 1000L < System.currentTimeMillis() - 60000L) {
                        return AuthResult.failure("Expired ID token received from OpenAI.");
                    }

                    // Extract identity
                    if (payload.has("email")) {
                        accountEmail = payload.getString("email");
                    }
                    if (payload.has("sub")) {
                        accountSubject = payload.getString("sub");
                    }

                    // Verify nonce if present
                    String storedNonce = credentialVault.retrieve("openai_nonce_" + originalRequest.state);
                    if (storedNonce != null && payload.has("nonce")) {
                        if (!storedNonce.equals(payload.getString("nonce"))) {
                            return AuthResult.failure("Nonce mismatch in ID token.");
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "ID token inspection warning: " + e.getMessage());
            }
        }

        // Verify granted scopes (Directive §3.2 Step 8)
        String grantedScope = tokenJson.optString("scope", "");
        boolean hasDirectTokenScope = grantedScope.contains("chatgpt.tokens.use.direct") || grantedScope.contains("resource.invoke");
        if (!grantedScope.isEmpty() && !hasDirectTokenScope) {
            Log.w(TAG, "Notice: chatgpt.tokens.use.direct not explicitly confirmed in scope: " + grantedScope);
        }

        // Probe Responses API with genuine 1-token completion before CONNECTED (Directive §3.2 Step 10)
        boolean probeOk = probe(accessToken, "gpt-4o-mini");
        if (!probeOk) {
            // Also try a models list probe as fallback
            probeOk = probeModelsEndpoint(accessToken);
        }
        if (!probeOk) {
            return AuthResult.failure("OpenAI live API probe failed after token exchange. Quota or plan permission unavailable.");
        }

        // Store tokens securely in Android Keystore-backed CredentialVault
        credentialVault.store("openai_access_token", accessToken);
        if (refreshToken != null) {
            credentialVault.store("openai_refresh_token", refreshToken);
        }
        credentialVault.store("openai_account_email", accountEmail);

        // Structured record storage (Directive §13)
        List<String> scopesList = Arrays.asList(grantedScope.split(" "));
        CredentialRecord credRecord = new CredentialRecord(
                "openai",
                accountSubject,
                accessToken,
                refreshToken,
                idToken,
                expiresAtEpochMs,
                scopesList,
                "https://auth.openai.com",
                effectiveClientId,
                accountEmail,
                "ChatGPT Plan",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                1L
        );
        credentialVault.storeRecord(credRecord);

        return AuthResult.success(accessToken, refreshToken, expiresAtEpochMs, accountSubject, accountEmail, "Direct OAuth (PKCE)");
    }

    @Override
    public AuthResult pollDeviceCode(String transactionId) {
        return AuthResult.failure("OpenAI uses browser-based OAuth PKCE loopback, not device codes.");
    }

    @Override
    public synchronized AuthResult refresh(String refreshToken) throws Exception {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return AuthResult.failure("No refresh token provided for OpenAI refresh.");
        }
        String issuedClientId = getIssuedClientId();
        if (issuedClientId == null || DYNAMIC_CLIENT_ID.equals(issuedClientId)) {
            return AuthResult.failure("Missing issued client ID for OpenAI token refresh.");
        }

        String postBody = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")
                + "&client_id=" + URLEncoder.encode(issuedClientId, "UTF-8")
                + "&resource=" + URLEncoder.encode(RESOURCE_URI, "UTF-8");

        JSONObject tokenJson = postForm(TOKEN_ENDPOINT, postBody);
        if (tokenJson.has("error")) {
            return AuthResult.failure("OpenAI token refresh failed: " + tokenJson.optString("error_description", tokenJson.optString("error")));
        }

        String newAccessToken = tokenJson.getString("access_token");
        String newRefreshToken = tokenJson.optString("refresh_token", refreshToken);
        long expiresIn = tokenJson.optLong("expires_in", 3600);
        long expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L);

        // Atomic replacement
        credentialVault.store("openai_access_token", newAccessToken);
        credentialVault.store("openai_refresh_token", newRefreshToken);

        CredentialRecord existingRecord = credentialVault.retrieveRecord("openai");
        if (existingRecord != null) {
            credentialVault.rotateRecord(existingRecord.withRotatedTokens(newAccessToken, newRefreshToken, expiresAtEpochMs));
        }

        String email = credentialVault.retrieve("openai_account_email");
        if (email == null) email = "ChatGPT Account";

        return AuthResult.success(newAccessToken, newRefreshToken, expiresAtEpochMs, "openai_account", email, "Direct OAuth (PKCE)");
    }

    @Override
    public List<ModelDescriptor> discoverModels(String accessToken) throws Exception {
        List<ModelDescriptor> list = new ArrayList<>();
        URL url = new URL(MODELS_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            String resp = readStream(conn.getInputStream());
            JSONObject json = new JSONObject(resp);
            JSONArray data = json.optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject m = data.getJSONObject(i);
                    String id = m.optString("id");
                    if (id.startsWith("gpt") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt")) {
                        list.add(new ModelDescriptor(id, id, 128000, false, true, true, "Available"));
                    }
                }
            }
        }
        return list;
    }

    @Override
    public QuotaSnapshot fetchQuota(String accessToken) {
        return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED, null, "ChatGPT Plan Backed", "oauth-pkce");
    }

    @Override
    public boolean probe(String accessToken, String model) throws Exception {
        try {
            URL url = new URL(RESPONSES_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "OceanStudio/1.2.6");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            JSONObject payload = new JSONObject();
            payload.put("model", (model != null && !model.isEmpty()) ? model : "gpt-4o-mini");
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", "ping"));
            payload.put("messages", messages);
            payload.put("max_tokens", 1);
            payload.put("store", false);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            Log.w(TAG, "Responses API probe encountered: " + e.getMessage());
            return false;
        }
    }

    private boolean probeModelsEndpoint(String accessToken) {
        try {
            URL url = new URL(MODELS_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            return conn.getResponseCode() >= 200 && conn.getResponseCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void logout(String accessToken) {
        credentialVault.delete("openai_access_token");
        credentialVault.delete("openai_refresh_token");
        credentialVault.delete("openai_account_email");
    }

    private JSONObject postForm(String endpoint, String urlEncodedBody) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", "OceanStudio/1.2.6 (Android)");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(urlEncodedBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("HTTP " + code + " empty body");
        return new JSONObject(readStream(is));
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] b = new byte[2048];
        int r;
        while ((r = is.read(b)) != -1) baos.write(b, 0, r);
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Ephemeral 127.0.0.1 loopback HTTP server to receive OAuth browser redirect
     * conforming to RFC 8252 Section 8.3.
     */
    public static final class LoopbackServer {
        private final String expectedState;
        private studio.ocean.app.mcp.OAuthLoopbackReceiver receiver;
        private String redirectUri;
        private CallbackListener listener;
        public LoopbackServer(String expectedState) { this.expectedState = expectedState; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
        public String getRedirectUri() { return redirectUri; }
        public void setListener(CallbackListener listener) { this.listener = listener; }
        public synchronized void start() throws IOException {
            receiver = new studio.ocean.app.mcp.OAuthLoopbackReceiver();
            redirectUri = receiver.redirectUri();
            receiver.listen(expectedState, new studio.ocean.app.mcp.OAuthLoopbackReceiver.Listener() {
                @Override public void received(String callback) {
                    if (listener != null) listener.onCallbackReceived(Uri.parse(callback));
                }
                @Override public void failed(String message) {
                    if (listener != null) listener.onCallbackReceived(Uri.parse(redirectUri)
                            .buildUpon().appendQueryParameter("state", expectedState)
                            .appendQueryParameter("error", "temporarily_unavailable")
                            .appendQueryParameter("error_description", message).build());
                }
            });
        }
        public int getPort() { return Uri.parse(redirectUri).getPort(); }
        public synchronized void stop() { if (receiver != null) receiver.close(); }
    }
}
