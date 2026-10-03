import studio.ocean.app.providers.gateway.GatewayClient;
import studio.ocean.app.providers.gateway.GatewayQuota;
import studio.ocean.app.mcp.OAuthLoopbackReceiver;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Exercises Ocean's HTTP client against the actual published OmniRoute server. */
public class GatewayClientIntegrationCheck {
    static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    static Map<String,String> query(String url) throws Exception {
        Map<String,String> values = new HashMap<>();
        for (String field : new URI(url).getRawQuery().split("&")) {
            String[] pair = field.split("=",2);
            values.put(URLDecoder.decode(pair[0],"UTF-8"), URLDecoder.decode(pair[1],"UTF-8"));
        }
        return values;
    }
    static void requireText(JSONObject reply) {
        JSONArray choices = reply.optJSONArray("choices");
        require(choices != null && choices.length() > 0, "Missing real inference choices");
        require(!choices.getJSONObject(0).getJSONObject("message").optString("content").trim().isEmpty(), "No generated text");
    }
    public static void main(String[] args) throws Exception {
        GatewayClient client = new GatewayClient(Integer.parseInt(args[0]));
        require(GatewayQuota.parse(new JSONObject("{\"plan\":\"plus\",\"quotas\":{\"session\":{\"used\":20,\"total\":100,\"remaining\":80},\"weekly\":{\"used\":90,\"total\":100,\"remaining\":10}}}"), "codex/model").remaining == 10,
                "Codex quota does not reflect the limiting provider-reported window");
        String password = null;
        for (String line : Files.readAllLines(Paths.get(args[1]))) if (line.startsWith("INITIAL_PASSWORD=")) password = line.substring(17);
        client.login(password);
        JSONObject antigravity = client.authorize("antigravity", "http://127.0.0.1:24510/callback");
        Map<String,String> ag = query(antigravity.getString("authUrl"));
        require(new URI(antigravity.getString("authUrl")).getHost().equals("accounts.google.com"), "Wrong Google authorization host");
        require(ag.get("state").equals(antigravity.getString("state")), "Antigravity state mismatch");
        require(ag.get("redirect_uri").equals("http://127.0.0.1:24510/callback"), "Antigravity redirect mismatch");
        JSONObject codex = client.authorize("codex", "http://localhost:1455/auth/callback");
        Map<String,String> cx = query(codex.getString("authUrl"));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(codex.getString("codeVerifier").getBytes(StandardCharsets.US_ASCII)));
        require(challenge.equals(cx.get("code_challenge")) && "S256".equals(cx.get("code_challenge_method")), "Codex PKCE mismatch");
        require(cx.get("state").equals(codex.getString("state")), "Codex state mismatch");
        require(cx.get("redirect_uri").equals("http://localhost:1455/auth/callback"), "Codex callback mismatch");
        String returnUri = "ocean://gateway/return?ticket=" + "a".repeat(64);
        CountDownLatch received = new CountDownLatch(1);
        try (OAuthLoopbackReceiver receiver = new OAuthLoopbackReceiver(0, "/auth/callback", returnUri, "localhost")) {
            receiver.listen("expected", new OAuthLoopbackReceiver.Listener() {
                public void received(String callback) { received.countDown(); }
                public void failed(String message) { throw new AssertionError(message); }
            });
            HttpURLConnection bad = (HttpURLConnection) new URL(receiver.redirectUri()+"?state=wrong&code=x").openConnection(Proxy.NO_PROXY);
            require(bad.getResponseCode()==400, "Wrong state accepted"); bad.disconnect();
            HttpURLConnection good = (HttpURLConnection) new URL(receiver.redirectUri()+"?state=expected&code=x").openConnection(Proxy.NO_PROXY);
            good.setInstanceFollowRedirects(false);
            require(good.getResponseCode()==302 && returnUri.equals(good.getHeaderField("Location")), "Automatic app return missing"); good.disconnect();
            require(received.await(3,TimeUnit.SECONDS), "Callback not delivered");
        }
        String key = client.createInferenceKey(args[2]);
        require(client.providerModels(args[2]).getJSONArray("models").length()>0, "No discovered runtime model");
        requireText(client.completion(key, args[4]));
        JSONArray models = new JSONArray()
                .put(new JSONObject().put("kind","model").put("model",args[4]).put("connectionId",args[3]))
                .put(new JSONObject().put("kind","model").put("model",args[4]).put("connectionId",args[2]));
        String combo = client.configureFallback(models);
        String fallbackKey = client.createFallbackKey(new JSONArray().put(args[3]).put(args[2]));
        requireText(client.completion(fallbackKey, combo));
        System.out.println("PASS: actual OmniRoute management/auth URLs, Codex S256, app callback, quota parsing, encrypted stored credentials, real llama inference, and account fallback");
    }
}
