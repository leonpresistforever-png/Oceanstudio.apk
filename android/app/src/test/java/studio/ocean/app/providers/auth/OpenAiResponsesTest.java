package studio.ocean.app.providers.auth;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class OpenAiResponsesTest {
    @Test public void mapsToolHistoryAndDisablesStorage() throws Exception {
        JSONObject chat = new JSONObject().put("model", "account-model").put("messages", new JSONArray()
            .put(new JSONObject().put("role", "system").put("content", "instructions"))
            .put(new JSONObject().put("role", "assistant").put("tool_calls", new JSONArray().put(new JSONObject()
                .put("id", "call1").put("function", new JSONObject().put("name", "run").put("arguments", "{}")))))
            .put(new JSONObject().put("role", "tool").put("tool_call_id", "call1").put("content", "result")));
        JSONObject result = OpenAiResponses.request(chat);
        assertFalse(result.getBoolean("store")); assertTrue(result.getBoolean("stream"));
        assertEquals("developer", result.getJSONArray("input").getJSONObject(0).getString("role"));
        assertEquals("function_call", result.getJSONArray("input").getJSONObject(1).getString("type"));
        assertEquals("call1", result.getJSONArray("input").getJSONObject(2).getString("call_id"));
    }
    @Test public void requiresCompletionAndPropagatesLateQuotaError() throws Exception {
        for (String stream : new String[]{"data: {\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}\n",
            "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n"}) {
            try { OpenAiResponses.reply(stream); fail("incomplete inference accepted"); }
            catch (java.io.IOException expected) {}
        }
        JSONObject reply = OpenAiResponses.reply("data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"OK\"}]}]}}\n");
        assertEquals("OK", reply.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
    }
}
