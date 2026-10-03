package studio.ocean.app.mcp;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.providers.state.CredentialVault;

/**
 * OAuth 2.1 metadata resolver, DCR (Dynamic Client Registration), PKCE authorization,
 * and token lifecycle manager for OAuth-protected remote MCP servers (Directive 2026-10-02 §7, §8, §19 P0-C).
 *
 * Implements RFC 9728 (OAuth 2.0 Protected Resource Metadata) and RFC 8414 (OAuth 2.0 Authorization Server Metadata).
 * Treats HTTP 401 on initial unauthenticated request as an OAuth challenge (e.g. Cloudflare MCP),
 * discovering endpoints, requesting user authorization, and exchanging bearer credentials.
 */
public final class McpOAuthResolver {

    private static final String TAG = "McpOAuthResolver";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static final class OAuthChallengeInfo {
        public String realm;
        public String resourceMetadataUrl;
        public String scope;
        public String authorizationServer;
        public String authorizationEndpoint;
        public String tokenEndpoint;
        public String registrationEndpoint;
        public String resource;
        public String clientId;
    }

    public static final class OAuthSession {
        public final String state;
        public final String codeVerifier;
        public final String codeChallenge;
        public final String authorizationUrl;
        public final String redirectUri;
        public final OAuthChallengeInfo challengeInfo;

        public OAuthSession(String state, String codeVerifier, String codeChallenge,
                            String authorizationUrl, String redirectUri, OAuthChallengeInfo challengeInfo) {
            this.state = state;
            this.codeVerifier = codeVerifier;
            this.codeChallenge = codeChallenge;
            this.authorizationUrl = authorizationUrl;
            this.redirectUri = redirectUri;
            this.challengeInfo = challengeInfo;
        }
    }

    public static final class TokenResult {
        public final boolean isSuccess;
        public final String accessToken;
        public final String refreshToken;
        public final long expiresAtEpochMs;
        public final String error;

        public TokenResult(boolean isSuccess, String accessToken, String refreshToken, long expiresAtEpochMs, String error) {
            this.isSuccess = isSuccess;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtEpochMs = expiresAtEpochMs;
            this.error = error;
        }

        public static TokenResult success(String accessToken, String refreshToken, long expiresAtEpochMs) {
            return new TokenResult(true, accessToken, refreshToken, expiresAtEpochMs, null);
        }

        public static TokenResult failure(String error) {
            return new TokenResult(false, null, null, 0, error);
        }
    }

    private final Context context;
    private final CredentialVault credentialVault;
    private final Map<String, OAuthSession> pendingSessions = new HashMap<>();

    public McpOAuthResolver(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
    }

    public static String normalizeOrigin(String serverUrl) {
        try {
            URL u = new URL(serverUrl);
            int port = u.getPort();
            if (port == -1 || port == u.getDefaultPort()) {
                return u.getProtocol() + "://" + u.getHost();
            }
            return u.getProtocol() + "://" + u.getHost() + ":" + port;
        } catch (Exception e) {
            return serverUrl;
        }
    }

    /**
     * Resolves Protected Resource Metadata and Authorization Server Metadata from a 401 challenge.
     */
    public OAuthChallengeInfo resolveChallenge(String serverUrl, String wwwAuthenticate) throws Exception {
        return resolveChallenge(serverUrl, wwwAuthenticate, null);
    }

    public OAuthChallengeInfo resolveChallenge(String serverUrl, String wwwAuthenticate, String redirectUri) throws Exception {
        OAuthChallengeInfo info = new OAuthChallengeInfo();
        String origin = normalizeOrigin(serverUrl);
        info.resource = origin;

        // Parse WWW-Authenticate parameters (e.g. Bearer realm="...", resource_metadata="...")
        if (wwwAuthenticate != null) {
            if (wwwAuthenticate.contains("resource_metadata=\"")) {
                int start = wwwAuthenticate.indexOf("resource_metadata=\"") + 19;
                int end = wwwAuthenticate.indexOf("\"", start);
                if (end > start) info.resourceMetadataUrl = wwwAuthenticate.substring(start, end);
            }
            if (wwwAuthenticate.contains("realm=\"")) {
                int start = wwwAuthenticate.indexOf("realm=\"") + 7;
                int end = wwwAuthenticate.indexOf("\"", start);
                if (end > start) info.realm = wwwAuthenticate.substring(start, end);
            }
            if (wwwAuthenticate.contains("scope=\"")) {
                int start = wwwAuthenticate.indexOf("scope=\"") + 7;
                int end = wwwAuthenticate.indexOf("\"", start);
                if (end > start) info.scope = wwwAuthenticate.substring(start, end);
            }
        }

        // RFC 9728 discovery fallback: /.well-known/oauth-protected-resource
        if (info.resourceMetadataUrl == null) {
            info.resourceMetadataUrl = origin + "/.well-known/oauth-protected-resource";
        }

        JSONObject protectedResourceJson = fetchJsonQuietly(info.resourceMetadataUrl);
        if (protectedResourceJson != null) {
            if (protectedResourceJson.has("resource")) {
                info.resource = protectedResourceJson.getString("resource");
            }
            JSONArray authServers = protectedResourceJson.optJSONArray("authorization_servers");
            if (authServers != null && authServers.length() > 0) {
                info.authorizationServer = authServers.getString(0);
            }
        }

        // If no authorization server declared, assume origin hosts the auth endpoints
        if (info.authorizationServer == null) {
            info.authorizationServer = origin;
        }

        // Discover Authorization Server Metadata (RFC 8414)
        String authServerMetaUrl = info.authorizationServer + "/.well-known/oauth-authorization-server";
        JSONObject authServerJson = fetchJsonQuietly(authServerMetaUrl);
        if (authServerJson == null) {
            // OpenID discovery fallback
            authServerJson = fetchJsonQuietly(info.authorizationServer + "/.well-known/openid-configuration");
        }

        if (authServerJson != null) {
            info.authorizationEndpoint = authServerJson.optString("authorization_endpoint", null);
            info.tokenEndpoint = authServerJson.optString("token_endpoint", null);
            info.registrationEndpoint = authServerJson.optString("registration_endpoint", null);
        }

        if (info.authorizationEndpoint == null || info.tokenEndpoint == null)
            throw new IOException("MCP authorization metadata did not declare authorization and token endpoints.");

        // Check if an issued client ID is already stored in CredentialVault for this origin
        String registrationKey = "mcp_client_id:" + info.authorizationServer + ":" + redirectUri;
        String storedClientId = redirectUri == null
                ? credentialVault.retrieve("mcp_last_client_id:" + origin)
                : credentialVault.retrieve(registrationKey);
        if (storedClientId != null && !storedClientId.isEmpty()) {
            info.clientId = storedClientId;
        } else if (info.registrationEndpoint != null && redirectUri != null) {
            // Dynamic Client Registration (DCR)
            info.clientId = registerDynamicClient(info.registrationEndpoint, registrationKey, redirectUri);
        }

        if (info.clientId == null) {
            throw new IOException("MCP server did not issue a client registration for Ocean. Authorization cannot start.");
        }
        credentialVault.store("mcp_last_client_id:" + origin, info.clientId);

        return info;
    }

    /**
     * Executes Dynamic Client Registration (RFC 7591) if supported by the server.
     */
    private String registerDynamicClient(String regEndpoint, String registrationKey, String redirectUri) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("client_name", "OceanStudio");
            JSONArray redirects = new JSONArray();
            redirects.put(redirectUri);
            body.put("redirect_uris", redirects);
            body.put("grant_types", new JSONArray().put("authorization_code").put("refresh_token"));
            body.put("response_types", new JSONArray().put("code"));
            body.put("token_endpoint_auth_method", "none");

            URL url = new URL(regEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                String respStr = readStream(conn.getInputStream());
                JSONObject respJson = new JSONObject(respStr);
                String issuedId = respJson.optString("client_id", null);
                if (issuedId != null && !issuedId.trim().isEmpty()) {
                    credentialVault.store(registrationKey, issuedId);
                    conn.disconnect();
                    return issuedId;
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            throw new IOException("MCP client registration failed.", e);
        }
        throw new IOException("MCP client registration was rejected or returned no client ID.");
    }

    /**
     * Prepares PKCE parameters and generates the browser authorization URL.
     */
    public synchronized OAuthSession beginAuthorization(OAuthChallengeInfo info, String redirectUri) throws Exception {
        byte[] verifierBytes = new byte[32];
        SECURE_RANDOM.nextBytes(verifierBytes);
        String codeVerifier = Base64.encodeToString(verifierBytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        String codeChallenge = Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

        byte[] stateBytes = new byte[24];
        SECURE_RANDOM.nextBytes(stateBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : stateBytes) sb.append(String.format("%02x", b));
        String state = sb.toString();

        String scope = info.scope;
        StringBuilder authUrl = new StringBuilder(info.authorizationEndpoint);
        authUrl.append("?response_type=code");
        authUrl.append("&client_id=").append(URLEncoder.encode(info.clientId, "UTF-8"));
        authUrl.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"));
        if (scope != null && !scope.isEmpty()) authUrl.append("&scope=").append(URLEncoder.encode(scope, "UTF-8"));
        authUrl.append("&state=").append(URLEncoder.encode(state, "UTF-8"));
        authUrl.append("&code_challenge=").append(URLEncoder.encode(codeChallenge, "UTF-8"));
        authUrl.append("&code_challenge_method=S256");
        if (info.resource != null) {
            authUrl.append("&resource=").append(URLEncoder.encode(info.resource, "UTF-8"));
        }

        OAuthSession session = new OAuthSession(state, codeVerifier, codeChallenge, authUrl.toString(), redirectUri, info);
        pendingSessions.put(state, session);
        return session;
    }

    /**
     * Exchanges authorization code for tokens, storing encrypted credentials in CredentialVault.
     */
    public synchronized TokenResult exchangeCode(Uri callbackUri) {
        String state = callbackUri.getQueryParameter("state");
        if (state == null) {
            return TokenResult.failure("Missing state in OAuth callback.");
        }

        OAuthSession session = pendingSessions.remove(state);
        if (session == null) {
            return TokenResult.failure("Unrecognized or expired state token.");
        }

        String err = callbackUri.getQueryParameter("error");
        if (err != null) {
            String desc = callbackUri.getQueryParameter("error_description");
            return TokenResult.failure("OAuth consent failed: " + (desc != null ? desc : err));
        }

        String code = callbackUri.getQueryParameter("code");
        if (code == null || code.isEmpty()) {
            return TokenResult.failure("Missing authorization code in callback.");
        }

        try {
            String postBody = "grant_type=authorization_code"
                    + "&code=" + URLEncoder.encode(code, "UTF-8")
                    + "&client_id=" + URLEncoder.encode(session.challengeInfo.clientId, "UTF-8")
                    + "&redirect_uri=" + URLEncoder.encode(session.redirectUri, "UTF-8")
                    + "&code_verifier=" + URLEncoder.encode(session.codeVerifier, "UTF-8");

            if (session.challengeInfo.resource != null) {
                postBody += "&resource=" + URLEncoder.encode(session.challengeInfo.resource, "UTF-8");
            }

            URL url = new URL(session.challengeInfo.tokenEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "OceanStudio/1.2.6 (MCP OAuth Client)");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postBody.getBytes(StandardCharsets.UTF_8));
            }

            int codeResp = conn.getResponseCode();
            InputStream is = (codeResp >= 200 && codeResp < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return TokenResult.failure("Token exchange HTTP " + codeResp + " with empty body");

            JSONObject json = new JSONObject(readStream(is));
            if (json.has("error")) {
                return TokenResult.failure("OAuth token exchange rejected: " + json.optString("error_description", json.optString("error")));
            }

            String accessToken = json.getString("access_token");
            String refreshToken = json.optString("refresh_token", null);
            long expiresIn = json.optLong("expires_in", 3600);
            long expiresAt = System.currentTimeMillis() + (expiresIn * 1000L);

            // Store in CredentialVault
            String origin = normalizeOrigin(session.challengeInfo.resource);
            credentialVault.store("mcp_token:" + origin, accessToken);
            if (refreshToken != null) {
                credentialVault.store("mcp_refresh:" + origin, refreshToken);
            }

            return TokenResult.success(accessToken, refreshToken, expiresAt);

        } catch (Exception e) {
            return TokenResult.failure("Token exchange network exception: " + e.getMessage());
        }
    }

    public synchronized TokenResult refreshToken(String serverUrl) {
        String origin = normalizeOrigin(serverUrl);
        String refreshToken = credentialVault.retrieve("mcp_refresh:" + origin);
        if (refreshToken == null) {
            return TokenResult.failure("No refresh token stored for MCP server: " + origin);
        }

        try {
            OAuthChallengeInfo info = resolveChallenge(serverUrl, null);
            String postBody = "grant_type=refresh_token"
                    + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")
                    + "&client_id=" + URLEncoder.encode(info.clientId, "UTF-8");
            if (info.resource != null) {
                postBody += "&resource=" + URLEncoder.encode(info.resource, "UTF-8");
            }

            URL url = new URL(info.tokenEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postBody.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                String newAccess = json.getString("access_token");
                String newRefresh = json.optString("refresh_token", refreshToken);
                long expiresIn = json.optLong("expires_in", 3600);
                long expiresAt = System.currentTimeMillis() + (expiresIn * 1000L);

                credentialVault.store("mcp_token:" + origin, newAccess);
                credentialVault.store("mcp_refresh:" + origin, newRefresh);
                return TokenResult.success(newAccess, newRefresh, expiresAt);
            } else {
                return TokenResult.failure("Refresh rejected: HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) {
            return TokenResult.failure("Refresh failed: " + e.getMessage());
        }
    }

    public String getStoredToken(String serverUrl) {
        String origin = normalizeOrigin(serverUrl);
        return credentialVault.retrieve("mcp_token:" + origin);
    }

    private JSONObject fetchJsonQuietly(String urlString) {
        try {
            URL u = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                return new JSONObject(readStream(conn.getInputStream()));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] b = new byte[2048];
        int r;
        while ((r = is.read(b)) != -1) baos.write(b, 0, r);
        return baos.toString(StandardCharsets.UTF_8.name());
    }
}
