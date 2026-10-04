package studio.ocean.app.models.local;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LocalInferenceProtocolTest {
    private LocalGenerationSettings settings(int context, int output) {
        return new LocalGenerationSettings(context, output, 0.3f, 0.8f, 17, 1.2f, 1, true, false, context);
    }
    @Test public void nativeOllamaOptionsActuallyCarryLocalControls() throws Exception {
        JSONObject source = new JSONObject().put("model", "ocean-qwen").put("messages", new JSONArray()
                .put(new JSONObject().put("role", "user").put("content", "Hi")));
        JSONObject request = LocalInferenceProtocol.prepareOllama(source, settings(4096, 256));
        assertEquals("ocean-qwen", request.getString("model"));
        JSONObject options = request.getJSONObject("options");
        assertEquals(4096, options.getInt("num_ctx")); assertEquals(256, options.getInt("num_predict"));
        assertEquals(17, options.getInt("top_k")); assertEquals(1, options.getInt("num_thread"));
        assertEquals(1.2, options.getDouble("repeat_penalty"), 0.001);
        assertFalse(request.has("max_tokens")); assertFalse(request.has("tools"));
    }
    @Test public void tokenizerBudgetDropsWholeOldTurnAndKeepsCurrentRequest() throws Exception {
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", "System"))
                .put(new JSONObject().put("role", "user").put("content", "Old question"))
                .put(new JSONObject().put("role", "assistant").put("content", "Old answer"))
                .put(new JSONObject().put("role", "user").put("content", "Current question"));
        JSONObject source = new JSONObject().put("model", "new-model").put("messages", messages);
        JSONObject request = LocalInferenceProtocol.prepareLlama(source, settings(512, 256), (path, body) -> {
            if (path.equals("/apply-template")) return new JSONObject().put("prompt", body.getJSONArray("messages").length() > 2 ? "long" : "short");
            JSONArray tokens = new JSONArray();
            for (int i = 0; i < (body.getString("content").equals("long") ? 480 : 300); i++) tokens.put(i);
            return new JSONObject().put("tokens", tokens);
        });
        assertEquals(2, request.getJSONArray("messages").length());
        assertEquals("Current question", request.getJSONArray("messages").getJSONObject(1).getString("content"));
        assertEquals(180, request.getInt("max_tokens"));
        assertEquals(4, source.getJSONArray("messages").length());
        assertEquals("new-model", request.getString("model"));
    }
    @Test public void nativeToolArgumentsAndNamesRoundTripWithoutLosingExecutionIds() throws Exception {
        LocalGenerationSettings settings = new LocalGenerationSettings(8192, 256, .7f, 1, 40, 1.1f, 1, true, true, 8192);
        JSONObject nativeReply = new JSONObject("{model:'qwen',message:{role:'assistant',content:'',tool_calls:[{function:{name:'run_terminal_command',arguments:{command:'pwd'}}}]},prompt_eval_count:32,eval_count:12}");
        JSONObject reply = LocalInferenceProtocol.ollamaReply(nativeReply);
        JSONObject assistant = reply.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        JSONObject call = assistant.getJSONArray("tool_calls").getJSONObject(0);
        assertEquals("pwd", new JSONObject(call.getJSONObject("function").getString("arguments")).getString("command"));
        JSONObject body = new JSONObject().put("model", "qwen").put("messages", new JSONArray()
                .put(assistant).put(new JSONObject().put("role", "tool").put("tool_call_id", call.getString("id")).put("content", "/workspace")));
        JSONObject request = LocalInferenceProtocol.prepareOllama(body, settings);
        assertEquals("run_terminal_command", request.getJSONArray("messages").getJSONObject(1).getString("tool_name"));
        assertTrue(request.getJSONArray("messages").getJSONObject(0).getJSONArray("tool_calls").getJSONObject(0)
                .getJSONObject("function").get("arguments") instanceof JSONObject);
    }
}
