package studio.ocean.app;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.terminal.OceanTerminalRuntimeService;

public final class OceanAgentRunner {

    public interface ConnectionCallback { void onSuccess(); void onFailure(String error); }

    public interface AgentCallback {
        void onThought(String thought);
        void onToolStart(String toolName, String command);
        void onToolOutput(String outputChunk);
        void onToolComplete(int exitCode);
        void onResponse(String response);
        void onError(String error);
    }

    private final Context context;
    private final OceanByokManager byokManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OceanAgentRunner(Context context) {
        this.context = context.getApplicationContext();
        this.byokManager = new OceanByokManager(this.context);
    }

    public void processPrompt(String prompt, AgentCallback callback) {
        new Thread(() -> {
            String trimmed = prompt.trim();
            if (trimmed.isEmpty()) return;

            // Explicit terminal execution request (e.g. starts with '$', 'run ', 'exec ', or standard ocean CLI tool)
            boolean isExplicitCli = trimmed.startsWith("$ ") || trimmed.startsWith("run ") ||
                                    trimmed.startsWith("exec ") || trimmed.startsWith("ocean-") ||
                                    trimmed.startsWith("pkg ") || trimmed.startsWith("apt ") ||
                                    trimmed.startsWith("./");

            if (isExplicitCli) {
                String cmd = trimmed;
                if (cmd.startsWith("$ ")) cmd = cmd.substring(2);
                else if (cmd.startsWith("run ")) cmd = cmd.substring(4);
                else if (cmd.startsWith("exec ")) cmd = cmd.substring(5);

                final String finalCmd = cmd;
                mainHandler.post(() -> callback.onThought("Executing internal terminal command: " + finalCmd));
                executeTerminalCommand(finalCmd, callback);
                return;
            }

            // Normal conversational agent flow: NEVER execute conversational text in shell
            if (!byokManager.isVerified()) {
                mainHandler.post(() -> callback.onResponse("No conversation model is connected. Open BYOK Models & APIs, enter the provider model identifier and API key, then run Save & Test Connection."));
                return;
            }

            // BYOK Model API Call
            String provider = byokManager.getProvider();
            String model = byokManager.getModel();
            mainHandler.post(() -> callback.onThought("Thinking with " + model + " (" + provider + ")..."));

            try {
                String responseText;
                if (OceanByokManager.PROVIDER_GOOGLE.equalsIgnoreCase(provider)) {
                    responseText = callGoogleGemini(byokManager.getApiKey(), model, trimmed);
                } else if (OceanByokManager.PROVIDER_ANTHROPIC.equalsIgnoreCase(provider)) {
                    responseText = callAnthropicClaude(byokManager.getApiKey(), model, trimmed);
                } else {
                    responseText = callOpenAI(byokManager.getApiKey(), byokManager.getBaseUrl(), model, trimmed);
                }

                final String finalResp = responseText;
                mainHandler.post(() -> callback.onResponse(finalResp));

            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.toString();
                mainHandler.post(() -> {
                    callback.onThought("API connection: " + err);
                    callback.onResponse("The provider request failed: " + err + ". Check the API key, model identifier, endpoint, and network connection under BYOK Models & APIs.");
                });
            }
        }).start();
    }

    public void testConnection(ConnectionCallback callback) {
        new Thread(() -> { try { String response=callConfiguredModel("Reply with exactly: OCEAN_CONNECTION_OK");if(response==null||response.trim().isEmpty())throw new IllegalStateException("Provider returned an empty response");mainHandler.post(callback::onSuccess); }
            catch(Exception error){String message=error.getMessage()==null?error.toString():error.getMessage();mainHandler.post(()->callback.onFailure(message));} },"ocean-byok-connection-test").start();
    }

    private String callConfiguredModel(String prompt) throws Exception {
        String provider=byokManager.getProvider(), model=byokManager.getModel();
        if(OceanByokManager.PROVIDER_GOOGLE.equals(provider))return callGoogleGemini(byokManager.getApiKey(),model,prompt);
        if(OceanByokManager.PROVIDER_ANTHROPIC.equals(provider))return callAnthropicClaude(byokManager.getApiKey(),model,prompt);
        return callOpenAI(byokManager.getApiKey(),byokManager.getBaseUrl(),model,prompt);
    }

    private String callGoogleGemini(String apiKey, String model, String prompt) throws Exception {
        String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject contentObj = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject partObj = new JSONObject();
        partObj.put("text", "You are the built-in AI pair programmer and system assistant for Ocean OS, an advanced mobile operating system with terminal, compiler toolchains, distros, and development environment. Be helpful, concise, and accurate.\n\nUser request: " + prompt);
        parts.put(partObj);
        contentObj.put("parts", parts);
        contents.put(contentObj);
        body.put("contents", contents);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append("\n");
        }

        if (code >= 200 && code < 300) {
            JSONObject resJson = new JSONObject(sb.toString());
            JSONArray cands = resJson.optJSONArray("candidates");
            if (cands != null && cands.length() > 0) {
                JSONObject cand = cands.getJSONObject(0);
                JSONObject content = cand.optJSONObject("content");
                if (content != null) {
                    JSONArray resParts = content.optJSONArray("parts");
                    if (resParts != null && resParts.length() > 0) {
                        return resParts.getJSONObject(0).optString("text", "No response text.");
                    }
                }
            }
            return "Received empty response from Gemini.";
        } else {
            throw new RuntimeException("HTTP " + code + ": " + sb.toString());
        }
    }

    private String callOpenAI(String apiKey, String baseUrl, String model, String prompt) throws Exception {
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", "You are the built-in AI pair programmer and system assistant for Ocean OS, an advanced mobile operating system with terminal, compiler toolchains, distros, and development environment. Be helpful, concise, and accurate.");
        messages.put(sysMsg);
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.put(userMsg);
        body.put("messages", messages);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append("\n");
        }

        if (code >= 200 && code < 300) {
            JSONObject resJson = new JSONObject(sb.toString());
            JSONArray choices = resJson.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg != null) {
                    return msg.optString("content", "No content.");
                }
            }
            return "Received empty response from OpenAI.";
        } else {
            throw new RuntimeException("HTTP " + code + ": " + sb.toString());
        }
    }

    private String callAnthropicClaude(String apiKey, String model, String prompt) throws Exception {
        URL url = new URL("https://api.anthropic.com/v1/messages");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", 1024);
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.put(userMsg);
        body.put("messages", messages);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append("\n");
        }

        if (code >= 200 && code < 300) {
            JSONObject resJson = new JSONObject(sb.toString());
            JSONArray content = resJson.optJSONArray("content");
            if (content != null && content.length() > 0) {
                return content.getJSONObject(0).optString("text", "No text.");
            }
            return "Received empty response from Claude.";
        } else {
            throw new RuntimeException("HTTP " + code + ": " + sb.toString());
        }
    }

    public void executeTerminalCommand(String command, AgentCallback callback) {
        mainHandler.post(() -> callback.onToolStart("terminal", command));
        Intent intent=new Intent(context,OceanTerminalRuntimeService.class);
        context.startService(intent);
        ServiceConnection connection=new ServiceConnection(){
            private boolean released;
            private void release(){if(!released){released=true;try{context.unbindService(this);}catch(Exception ignored){}}}
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                OceanTerminalRuntimeService service=((OceanTerminalRuntimeService.LocalBinder)binder).service();
                service.requestCommand(command,new OceanTerminalRuntimeService.CommandCallback(){
                    @Override public void onOutput(byte[] bytes,int length){callback.onToolOutput(new String(bytes,0,length,StandardCharsets.UTF_8));}
                    @Override public void onExit(int code){callback.onToolComplete(code);callback.onResponse(code==0?"Command completed successfully.":"Command exited with code "+code+".");release();}
                    @Override public void onFailure(Throwable error){callback.onError("Terminal service error: "+error.getMessage());callback.onToolComplete(-1);release();}
                });
            }
            @Override public void onServiceDisconnected(ComponentName name){release();}
        };
        if(!context.bindService(intent,connection,Context.BIND_AUTO_CREATE)){callback.onError("Ocean Terminal service is unavailable.");callback.onToolComplete(-1);}
    }
}
