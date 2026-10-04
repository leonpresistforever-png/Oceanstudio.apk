package studio.ocean.app.models.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real llama.cpp tokenization and Ollama's native generation protocol. */
public final class LocalInferenceProtocol {
    public interface Endpoint { JSONObject call(String path, JSONObject body) throws Exception; }

    public static JSONObject prepareLlama(JSONObject source, LocalGenerationSettings settings, Endpoint endpoint) throws Exception {
        JSONObject body = new JSONObject(source.toString());
        body.put("top_k", settings.topK).put("repeat_penalty", settings.repeatPenalty);
        JSONArray messages = body.getJSONArray("messages");
        int count;
        for (;;) {
            JSONObject template = new JSONObject().put("messages", messages).put("add_generation_prompt", true);
            if (body.has("tools")) template.put("tools", body.getJSONArray("tools"));
            String prompt = endpoint.call("/apply-template", template).getString("prompt");
            count = endpoint.call("/tokenize", new JSONObject().put("content", prompt)
                    .put("add_special", true).put("parse_special", true)).getJSONArray("tokens").length();
            if (count + Math.min(settings.maxTokens, 128) + 32 <= settings.context) break;
            if (!removeOldTurn(messages) && !compactToolOutput(messages)) break;
        }
        int available = settings.context - count - 32;
        if (available < 16) throw new IOException("This message needs more than " + settings.context
                + " context tokens. Increase Context tokens in the local model drawer.");
        body.put("max_tokens", Math.min(settings.maxTokens, available));
        return body;
    }

    public static JSONObject prepareOllama(JSONObject source, LocalGenerationSettings settings) throws Exception {
        JSONObject body = new JSONObject(source.toString());
        JSONArray messages = body.getJSONArray("messages");
        // Ollama has no public tokenizer endpoint. UTF-8 bytes plus template reserve
        // give a conservative bound; the native num_ctx option is always explicit.
        while (byteBudget(messages, body.optJSONArray("tools")) + Math.min(settings.maxTokens, 128) + 32 > settings.context
                && (removeOldTurn(messages) || compactToolOutput(messages))) { }
        int available = settings.context - byteBudget(messages, body.optJSONArray("tools")) - 32;
        if (available < 16) throw new IOException("This message needs a larger local context. Increase Context tokens in the local model drawer.");
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            JSONArray calls = message.optJSONArray("tool_calls");
            if (calls != null) for (int j = 0; j < calls.length(); j++) {
                JSONObject call = calls.getJSONObject(j), function = call.getJSONObject("function");
                names.put(call.optString("id"), function.getString("name"));
                Object args = function.opt("arguments");
                if (args instanceof String) function.put("arguments", new JSONObject((String) args));
            }
            if ("tool".equals(message.optString("role"))) {
                message.put("tool_name", names.getOrDefault(message.optString("tool_call_id"), ""));
                message.remove("tool_call_id");
            }
        }
        JSONObject nativeBody = new JSONObject().put("model", body.getString("model")).put("messages", messages)
                .put("stream", false).put("keep_alive", -1).put("options", settings.ollamaOptions(Math.min(settings.maxTokens, available)));
        if (settings.tools && body.has("tools")) nativeBody.put("tools", body.getJSONArray("tools"));
        return nativeBody;
    }

    public static JSONObject ollamaReply(JSONObject response) throws Exception {
        JSONObject message = new JSONObject(response.getJSONObject("message").toString());
        JSONArray calls = message.optJSONArray("tool_calls");
        if (calls != null) for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.getJSONObject(i), function = call.getJSONObject("function");
            call.put("id", "ocean_" + java.util.UUID.randomUUID()).put("type", "function");
            if (function.opt("arguments") instanceof JSONObject) function.put("arguments", function.getJSONObject("arguments").toString());
        }
        return new JSONObject().put("model", response.optString("model"))
                .put("choices", new JSONArray().put(new JSONObject().put("index", 0).put("message", message)
                        .put("finish_reason", calls != null && calls.length() > 0 ? "tool_calls" : response.optString("done_reason", "stop"))))
                .put("usage", new JSONObject().put("prompt_tokens", response.optInt("prompt_eval_count"))
                        .put("completion_tokens", response.optInt("eval_count")));
    }

    private static int byteBudget(JSONArray messages, JSONArray tools) {
        int total = 256 + (tools == null ? 0 : tools.toString().getBytes(StandardCharsets.UTF_8).length);
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null) total += 32 + message.optString("content", "").getBytes(StandardCharsets.UTF_8).length
                    + (message.has("tool_calls") ? message.optJSONArray("tool_calls").toString().getBytes(StandardCharsets.UTF_8).length : 0);
        }
        return total;
    }
    private static boolean removeOldTurn(JSONArray messages) throws Exception {
        int first = -1, next = -1;
        for (int i = 0; i < messages.length(); i++) {
            if ("user".equals(messages.getJSONObject(i).optString("role"))) {
                if (first == -1) first = i; else { next = i; break; }
            }
        }
        if (next == -1) return false;
        for (int i = first; i < next; i++) messages.remove(first);
        return true;
    }
    private static boolean compactToolOutput(JSONArray messages) throws Exception {
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            if (!"tool".equals(message.optString("role"))) continue;
            String text = message.optString("content", "");
            if (text.length() < 600) continue;
            String note = "[Shortened for local context; full result is shown in tool output]";
            try {
                JSONObject result = new JSONObject(text);
                boolean changed = false;
                for (String key : new String[]{"output", "stdout", "stderr", "text"}) {
                    String value = result.optString(key, "");
                    if (value.length() >= 600) {
                        result.put(key, value.substring(0, 192) + "\n" + note + "\n" + value.substring(value.length() - 192));
                        changed = true;
                    }
                }
                if (changed) { message.put("content", result.toString()); return true; }
            } catch (org.json.JSONException plainText) { }
            message.put("content", text.substring(0, 192) + "\n" + note + "\n" + text.substring(text.length() - 192));
            return true;
        }
        return false;
    }
}
