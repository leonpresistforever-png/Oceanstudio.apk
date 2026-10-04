import studio.ocean.app.models.local.LocalGenerationSettings;
import studio.ocean.app.models.local.LocalInferenceProtocol;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Uses the actual pinned llama-server tokenizer and actual generated tokens. */
public final class LocalInferenceIntegrationCheck {
    static JSONObject call(String origin, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(origin + path).openConnection(Proxy.NO_PROXY);
        connection.setRequestProperty("Authorization", "Bearer ocean-integration-key");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setReadTimeout(120000);
        try {
            try (OutputStream output = connection.getOutputStream()) { output.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = connection.getResponseCode();
            try (InputStream input = code == 200 ? connection.getInputStream() : connection.getErrorStream()) {
                String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                if (code != 200) throw new AssertionError(path + ": " + code + " " + text);
                return new JSONObject(text);
            }
        } finally { connection.disconnect(); }
    }
    public static void main(String[] args) throws Exception {
        LocalGenerationSettings settings = new LocalGenerationSettings(512, 32, .3f, .8f, 20, 1.1f, 1, true, false, 512);
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", "Tell a short story."))
                .put(new JSONObject().put("role", "user").put("content", "An old question ".repeat(500)))
                .put(new JSONObject().put("role", "assistant").put("content", "An old answer ".repeat(500)))
                .put(new JSONObject().put("role", "user").put("content", "Once upon a time"));
        JSONObject prepared = LocalInferenceProtocol.prepareLlama(new JSONObject().put("model", "stories").put("messages", messages)
                .put("temperature", .3).put("top_p", .8), settings, (path, body) -> call(args[0], path, body));
        if (prepared.getJSONArray("messages").length() != 2) throw new AssertionError("Oversized old exchange was not removed");
        if (prepared.getInt("max_tokens") > 32) throw new AssertionError("Output limit was lost");
        JSONObject reply = call(args[0], "/v1/chat/completions", prepared);
        if (reply.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim().isEmpty())
            throw new AssertionError("Runtime returned no real text");
        JSONObject usage = reply.getJSONObject("usage");
        if (usage.getInt("completion_tokens") > 32 || usage.getInt("total_tokens") > 512)
            throw new AssertionError("Runtime did not honor context/output limits");
        System.out.println("PASS: real llama tokenizer, oversized-history trimming, generated response, context and output limits");
    }
}
