package studio.ocean.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.terminal.OceanEnvironment;

public final class OceanAgentRunner {

    public interface AgentCallback {
        void onThought(String thought);
        void onToolStart(String toolName, String command);
        void onToolOutput(String outputChunk);
        void onToolComplete(int exitCode);
        void onResponse(String response);
        void onError(String error);
    }

    private final Context context;
    private final OceanPaths paths;
    private final OceanByokManager byokManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OceanAgentRunner(Context context) {
        this.context = context.getApplicationContext();
        this.paths = new OceanPaths(this.context);
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
            if (!byokManager.hasApiKey()) {
                mainHandler.post(() -> callback.onThought("Ocean Agent reasoning locally..."));
                String localResponse = generateLocalAssistantResponse(trimmed);
                mainHandler.post(() -> callback.onResponse(localResponse));
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

                // Check if response contains an internal tool command like ```bash ocean-... ```
                Pattern pattern = Pattern.compile("```(?:bash|sh)\\s*\\n([\\s\\S]*?)```");
                Matcher matcher = pattern.matcher(responseText);
                if (matcher.find()) {
                    String toolCmd = matcher.group(1).trim();
                    if (toolCmd.startsWith("ocean-") || toolCmd.startsWith("pkg ") || toolCmd.startsWith("apt ")) {
                        mainHandler.post(() -> callback.onThought("Executing recommended tool internally: " + toolCmd));
                        String toolOutput = executeInternalCommandSilently(toolCmd);
                        if (!toolOutput.isEmpty()) {
                            responseText += "\n\n**Internal Execution Result:**\n```\n" + toolOutput + "\n```";
                        }
                    }
                }

                final String finalResp = responseText;
                mainHandler.post(() -> callback.onResponse(finalResp));

            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.toString();
                mainHandler.post(() -> {
                    callback.onThought("API connection: " + err);
                    callback.onResponse("I couldn't reach the model API (" + err + "). Please check your API key and network connection under Tools ➔ BYOK Models.\n\nIn the meantime, I can assist you with Ocean OS commands and setup locally!");
                });
            }
        }).start();
    }

    private String generateLocalAssistantResponse(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")) {
            return "Hello! I am your Ocean OS AI Assistant.\n\nI can help you build apps, run development toolchains (Python, Node.js, Clang, Rust, Go), install Linux distributions (Gentoo, Ubuntu, Debian, Arch, Alpine), and manage packages with `ocean-pkg`.\n\nTo connect to Google Gemini, Claude, or OpenAI models for unrestricted AI reasoning, open the sidebar and select **Tools ➔ BYOK Models** to enter your API key.\n\nHow can I help you today?";
        }
        if (lower.contains("distro") || lower.contains("gentoo") || lower.contains("ubuntu") || lower.contains("arch") || lower.contains("debian")) {
            return "Ocean OS supports running full Linux distributions using `ocean-distro` in proot user-space.\n\nAvailable distributions include:\n• **Gentoo** (`ocean-distro install gentoo`)\n• **Ubuntu** (`ocean-distro install ubuntu`)\n• **Debian** (`ocean-distro install debian`)\n• **Arch Linux** (`ocean-distro install archlinux`)\n• **Alpine Linux** (`ocean-distro install alpine`)\n\nYou can launch or manage them from the Terminal or directly from the Distro manager in the sidebar.";
        }
        if (lower.contains("package") || lower.contains("pkg") || lower.contains("apt") || lower.contains("install")) {
            return "You can install packages in Ocean OS using `ocean-pkg` or `apt`:\n• `ocean-pkg install <pkg>` or `pkg install <pkg>`\n• `ocean-pkg search <query>`\n• `ocean-pkg update`\n\nAll 460 core packages are pre-indexed and hosted on the official Ocean Package Archive.";
        }
        return "I received your message: \"" + prompt + "\"\n\nI am your local Ocean Assistant. To enable full generative AI reasoning and direct code generation, please configure your **Gemini**, **Anthropic Claude**, or **OpenAI** API key in the sidebar under **Tools ➔ BYOK Models**.\n\nIf you would like to run a terminal command directly, simply prefix it with `$` (e.g. `$ ocean-info` or `$ ls -la`).";
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

    private String executeInternalCommandSilently(String command) {
        try {
            String shell = paths.prefix() + "/bin/bash";
            if (!new File(shell).exists()) shell = paths.prefix() + "/bin/sh";
            if (!new File(shell).exists()) shell = "/system/bin/sh";

            ProcessBuilder pb = new ProcessBuilder(shell, "-c", command);
            pb.directory(paths.home());
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            String[] envArr = OceanEnvironment.create(context, shell);
            for (String e : envArr) {
                int idx = e.indexOf('=');
                if (idx > 0) env.put(e.substring(0, idx), e.substring(idx + 1));
            }

            Process proc = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 20) {
                    sb.append(line).append("\n");
                    count++;
                }
            }
            proc.waitFor(15, TimeUnit.SECONDS);
            if (proc.isAlive()) proc.destroyForcibly();
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public void executeTerminalCommand(String command, AgentCallback callback) {
        mainHandler.post(() -> callback.onToolStart("terminal", command));

        try {
            String shell = paths.prefix() + "/bin/bash";
            if (!new File(shell).exists()) {
                shell = paths.prefix() + "/bin/sh";
            }
            if (!new File(shell).exists()) {
                shell = "/system/bin/sh";
            }

            ProcessBuilder pb = new ProcessBuilder(shell, "-c", command);
            pb.directory(paths.home());
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            String[] envArr = OceanEnvironment.create(context, shell);
            for (String e : envArr) {
                int idx = e.indexOf('=');
                if (idx > 0) {
                    env.put(e.substring(0, idx), e.substring(idx + 1));
                }
            }

            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String l = line;
                    mainHandler.post(() -> callback.onToolOutput(l));
                }
            }

            boolean completed = proc.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                proc.destroyForcibly();
                mainHandler.post(() -> {
                    callback.onToolComplete(124);
                    callback.onResponse("Command execution timed out after 30 seconds.");
                });
                return;
            }

            int exitCode = proc.exitValue();
            mainHandler.post(() -> {
                callback.onToolComplete(exitCode);
                if (exitCode == 0) {
                    callback.onResponse("Command executed successfully.");
                } else {
                    callback.onResponse("Command exited with code " + exitCode + ".");
                }
            });

        } catch (Exception e) {
            final String err = e.getMessage() != null ? e.getMessage() : e.toString();
            mainHandler.post(() -> {
                callback.onError("Execution error: " + err);
                callback.onToolComplete(-1);
            });
        }
    }
}
