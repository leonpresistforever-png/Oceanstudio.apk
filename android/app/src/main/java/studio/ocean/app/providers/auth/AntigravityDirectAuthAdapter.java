package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import studio.ocean.app.BuildConfig;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;
import studio.ocean.app.providers.state.CredentialVault;

/**
 * Real Antigravity Direct Connect adapter.
 *
 * Normal users never enter OAuth engineering values. Ocean's own build is
 * registered with Google and supplies its OAuth client registration. The user
 * only presses Connect, signs in in the system browser, grants consent, and
 * returns through the loopback callback handled by AuthOrchestrator.
 *
 * CONNECTED is only allowed after:
 *  1) Google authorization-code exchange succeeds,
 *  2) userinfo succeeds,
 *  3) Cloud Code Assist project discovery/provisioning succeeds, and
 *  4) the authenticated account can fetch its live model catalog.
 */
public final class AntigravityDirectAuthAdapter implements DirectAuthAdapter {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String[] BOOTSTRAP_BASES = new String[] {
            "https://cloudcode-pa.googleapis.com",
            "https://daily-cloudcode-pa.googleapis.com"
    };
    private static final String[] RUNTIME_BASES = new String[] {
            "https://daily-cloudcode-pa.googleapis.com",
            "https://cloudcode-pa.googleapis.com"
    };
    public static final String PROJECT_VAULT_REF = "antigravity_verified_project";

    private static final List<String> SCOPES = Arrays.asList(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/cclog",
            "https://www.googleapis.com/auth/experimentsandconfigs"
    );

    private final Context context;
    private final CredentialVault vault;
    private volatile String lastProjectId;
    private volatile JSONObject lastModelCatalog;

    public AntigravityDirectAuthAdapter(Context context) {
        this.context = context.getApplicationContext();
        this.vault = new CredentialVault(this.context);
        this.lastProjectId = vault.retrieve(PROJECT_VAULT_REF);
    }

    public static List<String> requestedScopes() {
        return new ArrayList<>(SCOPES);
    }

    private static String clientId() {
        return BuildConfig.OCEAN_ANTIGRAVITY_OAUTH_CLIENT_ID == null
                ? "" : BuildConfig.OCEAN_ANTIGRAVITY_OAUTH_CLIENT_ID.trim();
    }

    private static String clientSecret() {
        return BuildConfig.OCEAN_ANTIGRAVITY_OAUTH_CLIENT_SECRET == null
                ? "" : BuildConfig.OCEAN_ANTIGRAVITY_OAUTH_CLIENT_SECRET.trim();
    }

    @Override
    public Availability preflight(Context ctx) {
        if (clientId().isEmpty()) {
            return Availability.unavailable(
                    "Antigravity Direct Connect is not configured in this Ocean build. "
                    + "Ocean's own OAuth registration must be supplied by the build/release pipeline; "
                    + "users should never enter a client ID, API key, redirect URI, or CLI command here.");
        }
        return Availability.available();
    }

    @Override
    public AuthStartResult start(AuthRequest request) throws Exception {
        if (request.redirectUri == null || !request.redirectUri.startsWith("http://127.0.0.1:")) {
            throw new IllegalArgumentException("Antigravity Direct Connect requires Ocean's loopback callback broker.");
        }

        StringBuilder url = new StringBuilder(AUTHORIZE_URL).append("?");
        add(url, "client_id", clientId());
        add(url, "response_type", "code");
        add(url, "redirect_uri", request.redirectUri);
        add(url, "scope", String.join(" ", SCOPES));
        add(url, "state", request.state);
        add(url, "access_type", "offline");
        add(url, "prompt", "consent");
        add(url, "include_granted_scopes", "true");
        if (request.codeChallenge != null && !request.codeChallenge.isEmpty()) {
            add(url, "code_challenge", request.codeChallenge);
            add(url, "code_challenge_method", "S256");
        }
        return AuthStartResult.browser(url.toString());
    }

    @Override
    public AuthResult handleCallback(Uri callback, AuthRequest originalRequest) throws Exception {
        String code = callback.getQueryParameter("code");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.failure("Google authorization callback did not contain an authorization code.");
        }

        StringBuilder form = new StringBuilder();
        form(form, "grant_type", "authorization_code");
        form(form, "client_id", clientId());
        if (!clientSecret().isEmpty()) form(form, "client_secret", clientSecret());
        form(form, "code", code);
        form(form, "redirect_uri", originalRequest.redirectUri);
        if (originalRequest.codeVerifier != null && !originalRequest.codeVerifier.isEmpty()) {
            form(form, "code_verifier", originalRequest.codeVerifier);
        }

        JSONObject token = postForm(TOKEN_URL, form.toString());
        String accessToken = token.optString("access_token", "");
        if (accessToken.isEmpty()) {
            return AuthResult.failure("Google token exchange succeeded without an access token.");
        }
        String refreshToken = token.optString("refresh_token", null);
        long expiresIn = Math.max(0L, token.optLong("expires_in", 0L));
        Long expiresAt = expiresIn > 0
                ? System.currentTimeMillis() + expiresIn * 1000L
                : null;

        JSONObject user = getJson(USERINFO_URL, accessToken);
        String subject = user.optString("id", user.optString("sub", ""));
        String email = user.optString("email", "");
        String name = user.optString("name", "");
        String display = !email.isEmpty() ? email : (!name.isEmpty() ? name : "Google Account");

        return AuthResult.success(accessToken, refreshToken, expiresAt,
                subject.isEmpty() ? display : subject, display, "Antigravity");
    }

    @Override
    public AuthResult pollDeviceCode(String transactionId) {
        return AuthResult.failure("Antigravity Direct Connect uses browser authorization, not a device-code placeholder.");
    }

    @Override
    public AuthResult refresh(String refreshToken) throws Exception {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return AuthResult.failure("No Google refresh token is available; reconnect the account.");
        }
        StringBuilder form = new StringBuilder();
        form(form, "grant_type", "refresh_token");
        form(form, "client_id", clientId());
        if (!clientSecret().isEmpty()) form(form, "client_secret", clientSecret());
        form(form, "refresh_token", refreshToken);

        JSONObject token = postForm(TOKEN_URL, form.toString());
        String access = token.optString("access_token", "");
        if (access.isEmpty()) return AuthResult.failure("Google refresh response contained no access token.");
        long expiresIn = Math.max(0L, token.optLong("expires_in", 0L));
        Long expiresAt = expiresIn > 0
                ? System.currentTimeMillis() + expiresIn * 1000L
                : null;
        return AuthResult.success(access, refreshToken, expiresAt, null, null, "Antigravity");
    }

    @Override
    public boolean probe(String accessToken, String model) throws Exception {
        String project = discoverOrProvisionProject(accessToken);
        if (project == null || project.trim().isEmpty()) return false;
        lastProjectId = project.trim();
        vault.store(PROJECT_VAULT_REF, lastProjectId);

        // A real model catalog call proves this is an Antigravity/Cloud Code
        // entitlement, not merely a generic Google OAuth token.
        JSONObject catalog = fetchModelCatalog(accessToken, lastProjectId);
        JSONObject models = catalog.optJSONObject("models");
        if (models == null || models.length() == 0) {
            throw new IllegalStateException(
                    "Google authorization succeeded but Antigravity returned no usable models for this account.");
        }
        lastModelCatalog = catalog;
        return true;
    }

    @Override
    public List<ModelDescriptor> discoverModels(String accessToken) throws Exception {
        String project = lastProjectId;
        if (project == null || project.isEmpty()) project = discoverOrProvisionProject(accessToken);
        if (project == null || project.isEmpty()) {
            throw new IllegalStateException("Antigravity Cloud Code project could not be resolved.");
        }
        JSONObject catalog = lastModelCatalog != null ? lastModelCatalog : fetchModelCatalog(accessToken, project);
        JSONObject models = catalog.optJSONObject("models");
        List<ModelDescriptor> out = new ArrayList<>();
        if (models == null) return out;

        Iterator<String> keys = models.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject info = models.optJSONObject(key);
            if (info == null) continue;
            if (info.optBoolean("isInternal", false)) continue;
            String id = info.optString("model", key).trim();
            String display = info.optString("displayName", id).trim();
            if (id.isEmpty() || display.isEmpty()) continue;

            JSONObject quotaInfo = info.optJSONObject("quotaInfo");
            String status = "Available";
            if (quotaInfo != null && quotaInfo.optBoolean("isExhausted", false)) {
                status = "Quota exhausted";
            } else if (quotaInfo != null && quotaInfo.has("remainingFraction")) {
                double remaining = quotaInfo.optDouble("remainingFraction", Double.NaN);
                if (!Double.isNaN(remaining)) {
                    status = String.format(Locale.US, "%.0f%% remaining", Math.max(0d, Math.min(1d, remaining)) * 100d);
                }
            }

            out.add(new ModelDescriptor(id, display, 0, out.isEmpty(), true, true, status));
        }
        return out;
    }

    @Override
    public QuotaSnapshot fetchQuota(String accessToken) {
        // Antigravity exposes quota per model. Provider-level aggregation would
        // be misleading, so do not invent a single percentage.
        return QuotaSnapshot.unknown("Antigravity", "Per-model quota reported by fetchAvailableModels");
    }

    @Override
    public void logout(String accessToken) {
        lastProjectId = null;
        lastModelCatalog = null;
        vault.delete(PROJECT_VAULT_REF);
        if (accessToken == null || accessToken.trim().isEmpty()) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = REVOKE_URL + "?token=" + URLEncoder.encode(accessToken, "UTF-8");
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "ocean-antigravity-revoke").start();
    }

    private String discoverOrProvisionProject(String accessToken) throws Exception {
        JSONObject load = null;
        Exception last = null;
        for (String base : BOOTSTRAP_BASES) {
            try {
                load = postJson(base + "/v1internal:loadCodeAssist",
                        new JSONObject()
                                .put("metadata", new JSONObject().put("ideType", "ANTIGRAVITY"))
                                .put("mode", 1),
                        accessToken);
                String project = extractProject(load);
                if (!project.isEmpty()) return project;
                break;
            } catch (Exception e) {
                last = e;
            }
        }
        if (load == null) {
            throw new IllegalStateException("Antigravity loadCodeAssist failed on every bootstrap endpoint.", last);
        }

        String tierId = defaultTier(load);
        JSONObject metadata = new JSONObject()
                .put("ideType", "ANTIGRAVITY")
                .put("platform", "PLATFORM_UNSPECIFIED")
                .put("pluginType", "GEMINI");

        String verifyHint = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            JSONObject body = new JSONObject().put("tierId", tierId).put("metadata", metadata);
            try {
                JSONObject onboard = postJson(
                        "https://daily-cloudcode-pa.googleapis.com/v1internal:onboardUser",
                        body, accessToken);
                if (onboard.optBoolean("done", false)) {
                    JSONObject response = onboard.optJSONObject("response");
                    String project = response != null ? extractProject(response) : "";
                    if (!project.isEmpty()) return project;
                }
            } catch (HttpStatusException e) {
                verifyHint = e.body;
                if (e.status == 401 || e.status == 403) {
                    throw new IllegalStateException(
                            "Antigravity account authorization/provisioning was rejected by Google"
                                    + (verifyHint == null || verifyHint.isEmpty() ? "." : ": " + abbreviate(verifyHint, 500)));
                }
            }
            Thread.sleep(1200L);
        }

        throw new IllegalStateException(
                "Google sign-in succeeded, but this account has no provisioned Cloud Code Assist project. "
                        + "Ocean will not mark it Connected until Google returns a real cloudaicompanionProject.");
    }

    private JSONObject fetchModelCatalog(String accessToken, String projectId) throws Exception {
        Exception last = null;
        for (String base : RUNTIME_BASES) {
            try {
                return postJson(base + "/v1internal:fetchAvailableModels",
                        new JSONObject().put("project", projectId), accessToken);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException("Antigravity model discovery failed on every Cloud Code endpoint.", last);
    }

    private static String extractProject(JSONObject data) {
        if (data == null) return "";
        Object value = data.opt("cloudaicompanionProject");
        if (value instanceof String) return ((String) value).trim();
        if (value instanceof JSONObject) return ((JSONObject) value).optString("id", "").trim();
        String project = data.optString("projectId", data.optString("project", ""));
        return project == null ? "" : project.trim();
    }

    private static String defaultTier(JSONObject load) {
        JSONArray allowed = load.optJSONArray("allowedTiers");
        if (allowed != null) {
            for (int i = 0; i < allowed.length(); i++) {
                JSONObject tier = allowed.optJSONObject(i);
                if (tier != null && tier.optBoolean("isDefault", false)) {
                    String id = tier.optString("id", "").trim();
                    if (!id.isEmpty()) return id;
                }
            }
        }
        JSONObject current = load.optJSONObject("currentTier");
        if (current != null) {
            String id = current.optString("id", "").trim();
            if (!id.isEmpty()) return id;
        }
        return "free-tier";
    }

    private static JSONObject getJson(String url, String bearer) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = open(url, bearer);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            String body = readBody(conn, code);
            if (code < 200 || code >= 300) throw new HttpStatusException(code, body);
            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static JSONObject postJson(String url, JSONObject body, String bearer) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = open(url, bearer);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            int code = conn.getResponseCode();
            String response = readBody(conn, code);
            if (code < 200 || code >= 300) throw new HttpStatusException(code, response);
            return new JSONObject(response);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static JSONObject postForm(String url, String formBody) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            int code = conn.getResponseCode();
            String response = readBody(conn, code);
            if (code < 200 || code >= 300) {
                throw new HttpStatusException(code, response);
            }
            return new JSONObject(response);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String bearer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "antigravity");
        conn.setRequestProperty("X-Goog-Api-Client", "google-cloud-sdk vscode_cloudshelleditor/0.1");
        conn.setRequestProperty("Client-Metadata",
                "{\"ideType\":\"ANTIGRAVITY\",\"platform\":\"PLATFORM_UNSPECIFIED\",\"pluginType\":\"GEMINI\"}");
        return conn;
    }

    private static String readBody(HttpURLConnection conn, int status) throws Exception {
        InputStream raw = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (raw == null) return "";
        try (InputStream in = raw; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 2 * 1024 * 1024) throw new IllegalStateException("Provider response exceeded 2 MiB.");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void add(StringBuilder url, String key, String value) throws Exception {
        if (url.charAt(url.length() - 1) != '?') url.append('&');
        url.append(URLEncoder.encode(key, "UTF-8")).append('=')
                .append(URLEncoder.encode(value, "UTF-8"));
    }

    private static void form(StringBuilder form, String key, String value) throws Exception {
        if (form.length() > 0) form.append('&');
        form.append(URLEncoder.encode(key, "UTF-8")).append('=')
                .append(URLEncoder.encode(value, "UTF-8"));
    }

    private static String abbreviate(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static final class HttpStatusException extends Exception {
        final int status;
        final String body;
        HttpStatusException(int status, String body) {
            super("HTTP " + status + (body == null || body.isEmpty() ? "" : ": " + abbreviate(body, 300)));
            this.status = status;
            this.body = body;
        }
    }
}
