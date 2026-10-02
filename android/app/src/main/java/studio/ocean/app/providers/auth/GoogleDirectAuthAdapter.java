package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import studio.ocean.app.R;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;
import studio.ocean.app.providers.state.CredentialVault;

/**
 * Legitimate OAuth 2.0 with PKCE adapter for Google / Antigravity account authentication (Directive 2026-10-02 §4, §5).
 * Connects directly to Google's official OAuth endpoints (RFC 8252, RFC 7636).
 * Strictly forbids mockups, synthetic codes, or fake tokens.
 */
public final class GoogleDirectAuthAdapter implements DirectAuthAdapter {

    private static final String TAG = "GoogleDirectAuth";
    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    private static final String GOOGLE_MODELS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String DEFAULT_SCOPES = "https://www.googleapis.com/auth/generative-language openid email";

    private final Context context;
    private final CredentialVault credentialVault;

    public GoogleDirectAuthAdapter(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
    }

    private String getEffectiveClientId() {
        String customId = credentialVault.retrieve("oauth_client_id_google");
        if (customId != null && !customId.trim().isEmpty()) {
            return customId.trim();
        }
        try {
            String resId = context.getString(R.string.default_web_client_id);
            if (resId != null && !resId.contains("YOUR_CLIENT_ID") && !resId.trim().isEmpty()) {
                return resId.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public Availability preflight(Context ctx) {
        String clientId = getEffectiveClientId();
        if (clientId == null || clientId.isEmpty()) {
            return Availability.unavailable(
                    "Google Direct OAuth requires a configured OAuth 2.0 Client ID.\n\n"
                    + "Please configure your OAuth Client ID in settings, or connect using the official 'agy' CLI Bridge / API Key."
            );
        }
        return Availability.available();
    }

    @Override
    public AuthStartResult start(AuthRequest request) throws Exception {
        String clientId = request.clientId != null ? request.clientId : getEffectiveClientId();
        if (clientId == null) {
            throw new IllegalStateException("Google OAuth Client ID is not configured.");
        }

        // Validate redirect URI: Loopback URIs (127.0.0.1) are rejected by Google OAuth for native clients
        if (request.redirectUri != null && (request.redirectUri.contains("127.0.0.1") || request.redirectUri.contains("localhost"))) {
            throw new IllegalArgumentException("Google OAuth rejects loopback redirect URIs for native Android clients. Use ocean://auth/callback or an approved custom scheme.");
        }

        String authUrl = GOOGLE_AUTH_ENDPOINT
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(request.redirectUri, "UTF-8")
                + "&scope=" + URLEncoder.encode(DEFAULT_SCOPES, "UTF-8")
                + "&state=" + URLEncoder.encode(request.state, "UTF-8")
                + "&code_challenge=" + URLEncoder.encode(request.codeChallenge, "UTF-8")
                + "&code_challenge_method=S256"
                + "&access_type=offline"
                + "&prompt=consent";

        return AuthStartResult.browser(authUrl);
    }

    @Override
    public AuthResult handleCallback(Uri callback, AuthRequest originalRequest) throws Exception {
        String code = callback.getQueryParameter("code");
        if (code == null || code.trim().isEmpty()) {
            String error = callback.getQueryParameter("error");
            return AuthResult.failure("Google authorization failed: " + (error != null ? error : "No code parameter in callback"));
        }

        String clientId = originalRequest.clientId != null ? originalRequest.clientId : getEffectiveClientId();
        if (clientId == null) {
            return AuthResult.failure("Missing client ID during token exchange.");
        }

        // Token exchange HTTP POST with S256 PKCE code_verifier
        String postBody = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, "UTF-8")
                + "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
                + "&code_verifier=" + URLEncoder.encode(originalRequest.codeVerifier, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(originalRequest.redirectUri, "UTF-8");

        JSONObject tokenJson = postForm(GOOGLE_TOKEN_ENDPOINT, postBody);
        if (tokenJson.has("error")) {
            return AuthResult.failure("Google token exchange rejected: " + tokenJson.optString("error_description", tokenJson.optString("error")));
        }

        String accessToken = tokenJson.getString("access_token");
        String refreshToken = tokenJson.optString("refresh_token", null);
        long expiresIn = tokenJson.optLong("expires_in", 3600);
        long expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L);

        return AuthResult.success(accessToken, refreshToken, expiresAtEpochMs, "google_account", "Google / Gemini Account", "Direct OAuth");
    }

    @Override
    public AuthResult pollDeviceCode(String transactionId) {
        return AuthResult.failure("Google account authorization uses OAuth PKCE browser redirect, not device code.");
    }

    @Override
    public AuthResult refresh(String refreshToken) throws Exception {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return AuthResult.failure("No refresh token provided.");
        }
        String clientId = getEffectiveClientId();
        if (clientId == null) {
            return AuthResult.failure("Missing client ID during token refresh.");
        }

        String postBody = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")
                + "&client_id=" + URLEncoder.encode(clientId, "UTF-8");

        JSONObject tokenJson = postForm(GOOGLE_TOKEN_ENDPOINT, postBody);
        if (tokenJson.has("error")) {
            return AuthResult.failure("Token refresh failed: " + tokenJson.optString("error_description", tokenJson.optString("error")));
        }

        String newAccessToken = tokenJson.getString("access_token");
        String newRefreshToken = tokenJson.optString("refresh_token", refreshToken);
        long expiresIn = tokenJson.optLong("expires_in", 3600);
        long expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L);

        return AuthResult.success(newAccessToken, newRefreshToken, expiresAtEpochMs, "google_account", "Google / Gemini Account", "Direct OAuth");
    }

    @Override
    public List<ModelDescriptor> discoverModels(String accessToken) throws Exception {
        List<ModelDescriptor> models = new ArrayList<>();
        URL url = new URL(GOOGLE_MODELS_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            String body = readStream(conn.getInputStream());
            JSONObject root = new JSONObject(body);
            JSONArray arr = root.optJSONArray("models");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.getJSONObject(i);
                    String name = m.optString("name", ""); // e.g. "models/gemini-1.5-pro"
                    String cleanId = name.startsWith("models/") ? name.substring(7) : name;
                    String displayName = m.optString("displayName", cleanId);
                    String desc = m.optString("description", "Google Gemini model");
                    models.add(new ModelDescriptor(cleanId, displayName, 1048576, false, true, true, "Available"));
                }
            }
        }
        return models;
    }

    @Override
    public QuotaSnapshot fetchQuota(String accessToken) {
        return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED,
                null, "Direct Google Account", "oauth-pkce");
    }

    @Override
    public boolean probe(String accessToken, String model) throws Exception {
        URL url = new URL(GOOGLE_MODELS_ENDPOINT + "?pageSize=1");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        int respCode = conn.getResponseCode();
        return respCode >= 200 && respCode < 300;
    }

    @Override
    public void logout(String accessToken) {
        if (accessToken == null) return;
        new Thread(() -> {
            try {
                URL url = new URL(GOOGLE_REVOKE_ENDPOINT + "?token=" + URLEncoder.encode(accessToken, "UTF-8"));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
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

        byte[] bytes = urlEncodedBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("HTTP " + code + " with empty body");
        String resp = readStream(is);
        return new JSONObject(resp);
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        return baos.toString(StandardCharsets.UTF_8.name());
    }
}
