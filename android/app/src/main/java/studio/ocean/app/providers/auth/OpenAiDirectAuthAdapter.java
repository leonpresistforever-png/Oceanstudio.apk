package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;
import studio.ocean.app.providers.state.CredentialVault;

/**
 * Legitimate OAuth 2.0 PKCE adapter for OpenAI account authorization (Directive 2026-10-02 §4, §5).
 * Follows RFC 7636 and RFC 8252. Strictly forbids mockups or synthetic credentials.
 */
public final class OpenAiDirectAuthAdapter implements DirectAuthAdapter {

    private static final String AUTH_ENDPOINT = "https://auth.openai.com/oauth/authorize";
    private static final String TOKEN_ENDPOINT = "https://auth.openai.com/oauth/token";
    private static final String MODELS_ENDPOINT = "https://api.openai.com/v1/models";

    private final Context context;
    private final CredentialVault credentialVault;

    public OpenAiDirectAuthAdapter(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
    }

    private String getCustomClientId() {
        return credentialVault.retrieve("oauth_client_id_openai");
    }

    @Override
    public Availability preflight(Context ctx) {
        String clientId = getCustomClientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            return Availability.unavailable(
                    "OpenAI Direct OAuth requires a configured OAuth 2.0 Client ID (RFC 8252).\n\n"
                    + "OpenAI does not expose an open public native client ID. Please configure your OpenAI OAuth Client ID, or connect using the Codex CLI Bridge / API Key."
            );
        }
        return Availability.available();
    }

    @Override
    public AuthStartResult start(AuthRequest request) throws Exception {
        String clientId = request.clientId != null ? request.clientId : getCustomClientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI OAuth Client ID is not configured.");
        }

        if (request.redirectUri != null && (request.redirectUri.contains("127.0.0.1") || request.redirectUri.contains("localhost"))) {
            throw new IllegalArgumentException("OpenAI rejects loopback redirect URIs for native mobile clients. Use ocean://auth/callback.");
        }

        String authUrl = AUTH_ENDPOINT
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(request.redirectUri, "UTF-8")
                + "&scope=" + URLEncoder.encode("openid model.request", "UTF-8")
                + "&state=" + URLEncoder.encode(request.state, "UTF-8")
                + "&code_challenge=" + URLEncoder.encode(request.codeChallenge, "UTF-8")
                + "&code_challenge_method=S256";

        return AuthStartResult.browser(authUrl);
    }

    @Override
    public AuthResult handleCallback(Uri callback, AuthRequest originalRequest) throws Exception {
        String code = callback.getQueryParameter("code");
        if (code == null || code.trim().isEmpty()) {
            String err = callback.getQueryParameter("error");
            return AuthResult.failure("OpenAI authorization failed: " + (err != null ? err : "Missing code in callback"));
        }

        String clientId = originalRequest.clientId != null ? originalRequest.clientId : getCustomClientId();
        if (clientId == null) {
            return AuthResult.failure("Missing client ID during token exchange.");
        }

        String postBody = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, "UTF-8")
                + "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
                + "&code_verifier=" + URLEncoder.encode(originalRequest.codeVerifier, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(originalRequest.redirectUri, "UTF-8");

        JSONObject tokenJson = postForm(TOKEN_ENDPOINT, postBody);
        if (tokenJson.has("error")) {
            return AuthResult.failure("OpenAI token exchange rejected: " + tokenJson.optString("error_description", tokenJson.optString("error")));
        }

        String accessToken = tokenJson.getString("access_token");
        String refreshToken = tokenJson.optString("refresh_token", null);
        long expiresIn = tokenJson.optLong("expires_in", 3600);
        long expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L);

        return AuthResult.success(accessToken, refreshToken, expiresAtEpochMs, "openai_account", "OpenAI Account", "Direct OAuth");
    }

    @Override
    public AuthResult pollDeviceCode(String transactionId) {
        return AuthResult.failure("OpenAI does not use device code authorization.");
    }

    @Override
    public AuthResult refresh(String refreshToken) throws Exception {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return AuthResult.failure("No refresh token provided.");
        }
        String clientId = getCustomClientId();
        if (clientId == null) {
            return AuthResult.failure("Missing client ID during token refresh.");
        }

        String postBody = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")
                + "&client_id=" + URLEncoder.encode(clientId, "UTF-8");

        JSONObject tokenJson = postForm(TOKEN_ENDPOINT, postBody);
        if (tokenJson.has("error")) {
            return AuthResult.failure("OpenAI token refresh failed: " + tokenJson.optString("error_description", tokenJson.optString("error")));
        }

        String accessToken = tokenJson.getString("access_token");
        String newRefreshToken = tokenJson.optString("refresh_token", refreshToken);
        long expiresIn = tokenJson.optLong("expires_in", 3600);
        long expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L);

        return AuthResult.success(accessToken, newRefreshToken, expiresAtEpochMs, "openai_account", "OpenAI Account", "Direct OAuth");
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
                    if (id.startsWith("gpt") || id.startsWith("o1") || id.startsWith("o3")) {
                        list.add(new ModelDescriptor(id, id, 128000, false, true, true, "Available"));
                    }
                }
            }
        }
        return list;
    }

    @Override
    public QuotaSnapshot fetchQuota(String accessToken) {
        return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED, null, "OpenAI Direct Account", "oauth-pkce");
    }

    @Override
    public boolean probe(String accessToken, String model) throws Exception {
        URL url = new URL(MODELS_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        return conn.getResponseCode() >= 200 && conn.getResponseCode() < 300;
    }

    @Override
    public void logout(String accessToken) {}

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
}
