package studio.ocean.app.providers.gateway;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** The actual upstream management and OpenAI-compatible inference HTTP contracts. */
public final class GatewayClient {
    public static final class HttpFailure extends IOException {
        public final int status;
        HttpFailure(int status, String method, String path, String detail) {
            super("Gateway HTTP " + status + " · " + method + " " + path.split("\\?", 2)[0]
                    + (detail == null || detail.isEmpty() ? "" : ": " + detail));
            this.status = status;
        }
    }
    private final String origin;
    private String cookie;

    public GatewayClient(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid gateway port");
        origin = "http://127.0.0.1:" + port;
    }
    public String baseUrl() { return origin + "/v1"; }
    public void login(String password) throws Exception {
        request("POST", "/api/auth/login", new JSONObject().put("password", password), null, 20);
        if (cookie == null) throw new IOException("Gateway did not issue a management session");
    }
    public JSONObject authorize(String provider, String redirect) throws Exception {
        return request("GET", "/api/oauth/" + safeId(provider) + "/authorize?redirect_uri="
                + URLEncoder.encode(redirect, "UTF-8"), null, null, 20);
    }
    public JSONObject exchange(String provider, String code, String redirect, String verifier, String state) throws Exception {
        return request("POST", "/api/oauth/" + safeId(provider) + "/exchange", new JSONObject()
                .put("code", code).put("redirectUri", redirect).put("codeVerifier", verifier).put("state", state), null, 120);
    }
    public String createInferenceKey(String connectionId) throws Exception {
        JSONObject result = request("POST", "/api/keys", new JSONObject().put("name", "OceanStudio")
                .put("allowedConnections", new JSONArray().put(connectionId))
                .put("scopes", new JSONArray().put("inference").put("self:usage").put("self:account-quota")), null, 20);
        String key = result.optString("key");
        if (key.isEmpty()) throw new IOException("Gateway did not issue an inference key");
        return key;
    }
    public JSONObject models(String key) throws Exception { return request("GET", "/v1/models", null, key, 40); }
    public JSONObject providerModels(String connectionId) throws Exception {
        return request("GET", "/api/providers/" + safeId(connectionId) + "/models?refresh=true&chatOnly=true", null, null, 60);
    }
    public JSONObject quota(String connectionId) throws Exception {
        return request("GET", "/api/usage/" + safeId(connectionId), null, null, 60);
    }
    public JSONObject connections(String provider) throws Exception {
        return request("GET", "/api/providers?provider=" + safeId(provider), null, null, 20);
    }
    public JSONObject completion(String key, String model) throws Exception {
        return request("POST", "/v1/chat/completions", new JSONObject().put("model", model)
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "Reply with OK")))
                .put("max_tokens", 8).put("stream", false), key, 120);
    }
    public void disconnect(String connectionId) throws Exception {
        request("DELETE", "/api/providers/" + safeId(connectionId), null, null, 20);
    }
    public String configureFallback(JSONArray models) throws Exception {
        JSONArray combos = request("GET", "/api/combos", null, null, 20).optJSONArray("combos");
        String id = null;
        if (combos != null) for (int i = 0; i < combos.length(); i++) {
            JSONObject combo = combos.getJSONObject(i);
            if ("Ocean-auto".equals(combo.optString("name"))) id = combo.optString("id");
        }
        JSONObject body = new JSONObject().put("name", "Ocean-auto").put("displayName", "Ocean Auto")
                .put("models", models).put("strategy", "priority");
        request(id == null ? "POST" : "PUT", id == null ? "/api/combos" : "/api/combos/" + safeId(id), body, null, 20);
        return "Ocean-auto";
    }
    public String createFallbackKey(JSONArray accounts) throws Exception {
        JSONObject result = request("POST", "/api/keys", new JSONObject().put("name", "Ocean Auto")
                .put("allowedConnections", accounts).put("scopes", new JSONArray().put("inference").put("self:usage")), null, 20);
        String key = result.optString("key");
        if (key.isEmpty()) throw new IOException("Gateway did not issue its fallback key");
        return key;
    }
    public static String safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid gateway identifier");
        return value;
    }
    private JSONObject request(String method, String path, JSONObject body, String key, int seconds) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(origin + path).openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000); connection.setReadTimeout(seconds * 1000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        // A management session must never bypass an inference key's account restrictions.
        if (cookie != null && !path.startsWith("/v1/")) connection.setRequestProperty("Cookie", cookie);
        if (key != null) connection.setRequestProperty("Authorization", "Bearer " + key);
        try {
            if (body != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = connection.getResponseCode();
            String session = connection.getHeaderField("Set-Cookie");
            if (session != null && session.startsWith("auth_token=")) cookie = session.split(";", 2)[0];
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (input != null) try (InputStream in = input) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 4 * 1024 * 1024) throw new IOException("Gateway response exceeded limit");
                    bytes.write(buffer, 0, count);
                }
            }
            String text = bytes.toString("UTF-8");
            JSONObject response;
            try { response = text.trim().isEmpty() ? new JSONObject() : new JSONObject(text); }
            catch (JSONException invalid) {
                if (status < 200 || status >= 300) throw new HttpFailure(status, method, path, null);
                throw new IOException("Gateway returned invalid JSON for " + path.split("\\?", 2)[0], invalid);
            }
            if (status < 200 || status >= 300) {
                Object error = response.opt("error");
                String detail = error instanceof JSONObject ? ((JSONObject) error).optString("message") : String.valueOf(error);
                throw new HttpFailure(status, method, path, detail == null || detail.equals("null") ? null : detail);
            }
            return response;
        } finally { connection.disconnect(); }
    }
}
