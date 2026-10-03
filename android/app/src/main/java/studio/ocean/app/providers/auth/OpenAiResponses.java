package studio.ocean.app.providers.auth;

import java.io.*;
import org.json.*;

/** Adapts Ocean's conversation history to plan-authorized Responses inference. */
public final class OpenAiResponses {
    private OpenAiResponses() {}
    public static JSONObject request(JSONObject chat) throws JSONException {
        JSONArray input = new JSONArray(), messages = chat.getJSONArray("messages");
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            String role = message.optString("role");
            if ("tool".equals(role)) {
                input.put(new JSONObject().put("type", "function_call_output").put("call_id", message.getString("tool_call_id"))
                        .put("output", message.optString("content")));
                continue;
            }
            Object content = message.opt("content");
            if (content != null && content != JSONObject.NULL && !"".equals(content))
                input.put(new JSONObject().put("role", "system".equals(role) ? "developer" : role).put("content", content));
            JSONArray calls = message.optJSONArray("tool_calls");
            if (calls != null) for (int j = 0; j < calls.length(); j++) {
                JSONObject call = calls.getJSONObject(j), fn = call.getJSONObject("function");
                input.put(new JSONObject().put("type", "function_call").put("call_id", call.getString("id"))
                        .put("name", fn.getString("name")).put("arguments", fn.getString("arguments")));
            }
        }
        JSONObject request = new JSONObject().put("model", chat.getString("model")).put("input", input)
                .put("store", false).put("stream", true);
        if (chat.has("max_tokens")) request.put("max_output_tokens", chat.get("max_tokens"));
        if (chat.has("reasoning_effort")) request.put("reasoning", new JSONObject().put("effort", chat.get("reasoning_effort")));
        JSONArray tools = chat.optJSONArray("tools");
        if (tools != null) {
            JSONArray defs = new JSONArray();
            for (int i = 0; i < tools.length(); i++) {
                JSONObject fn = new JSONObject(tools.getJSONObject(i).getJSONObject("function").toString());
                defs.put(fn.put("type", "function"));
            }
            request.put("tools", defs).put("tool_choice", "auto");
        }
        return request;
    }
    public static JSONObject reply(String sse) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(sse));
        String line; JSONObject completed = null;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            JSONObject event = new JSONObject(data); String type = event.optString("type");
            if ("response.failed".equals(type) || "response.incomplete".equals(type) || "error".equals(type)) {
                JSONObject response = event.optJSONObject("response");
                JSONObject error = response != null ? response.optJSONObject("error") : event.optJSONObject("error");
                throw new IOException("OpenAI inference stopped: " + (error == null ? type : error.optString("code", type)));
            }
            if ("response.completed".equals(type)) completed = event.getJSONObject("response");
        }
        if (completed == null) throw new IOException("OpenAI stream ended without completed inference");
        JSONArray output = completed.getJSONArray("output"), calls = new JSONArray(); StringBuilder text = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.getJSONObject(i);
            if ("function_call".equals(item.optString("type"))) {
                calls.put(new JSONObject().put("id", item.getString("call_id")).put("type", "function")
                        .put("function", new JSONObject().put("name", item.getString("name")).put("arguments", item.getString("arguments"))));
            } else if ("message".equals(item.optString("type"))) {
                JSONArray content = item.getJSONArray("content");
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.getJSONObject(j);
                    if ("output_text".equals(part.optString("type"))) text.append(part.optString("text"));
                    if ("refusal".equals(part.optString("type"))) text.append(part.optString("refusal"));
                }
            }
        }
        JSONObject message = new JSONObject().put("role", "assistant").put("content", text.toString());
        if (calls.length() > 0) message.put("tool_calls", calls);
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("message", message)));
    }
}
