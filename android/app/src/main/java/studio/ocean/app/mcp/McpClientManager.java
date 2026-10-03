package studio.ocean.app.mcp;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import studio.ocean.app.OceanAgentHubStore;
import studio.ocean.app.providers.state.CredentialVault;

/**
 * Real protocol client and lifecycle manager for Model Context Protocol (MCP) servers (Directive 2026-10-02 §10, §11).
 * Executes JSON-RPC handshakes, version negotiation, capability discovery, and tool routing.
 * Strictly guarantees that random commands or unverified endpoints produce visible START_ERROR / PROTOCOL_ERROR
 * and never false-positive CONNECTED states.
 */
public final class McpClientManager {

    private static final String TAG = "McpClientManager";
    public static final String PROTOCOL_VERSION = "2025-03-26";

    public interface HandshakeCallback {
        void onProgress(McpStatus status);
        void onSuccess(McpServerConfig config);
        void onFailure(McpStatus errorStatus, String error);
    }

    private static volatile McpClientManager instance;

    public static synchronized McpClientManager getInstance(Context context) {
        if (instance == null) {
            instance = new McpClientManager(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final OceanAgentHubStore hubStore;
    private final CredentialVault credentialVault;
    private final McpOAuthResolver oauthResolver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Active STDIO child processes
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, OAuthLoopbackReceiver> oauthReceivers = new ConcurrentHashMap<>();
    private final Map<String, BufferedReader> processReaders = new ConcurrentHashMap<>();
    private final Map<String, BufferedWriter> processWriters = new ConcurrentHashMap<>();
    private final AtomicInteger requestIdCounter = new AtomicInteger(100);

    private McpClientManager(Context context) {
        this.context = context;
        this.hubStore = new OceanAgentHubStore(context);
        this.credentialVault = new CredentialVault(context);
        this.oauthResolver = new McpOAuthResolver(context);
    }

    public McpOAuthResolver getOAuthResolver() {
        return oauthResolver;
    }

    public static class McpAuthRequiredException extends IOException {
        public final String wwwAuthenticate;
        public McpAuthRequiredException(String wwwAuthenticate) {
            super("MCP OAuth 2.1 authorization required");
            this.wwwAuthenticate = wwwAuthenticate;
        }
    }

    /**
     * Connects to an MCP server and runs the complete protocol handshake.
     * Transitions: SAVED -> CONNECTING -> INITIALIZING -> DISCOVERING -> CONNECTED
     * Failures: START_ERROR or PROTOCOL_ERROR.
     */
    public void connect(McpServerConfig config, HandshakeCallback callback) {
        config.status = McpStatus.CONNECTING;
        config.lastError = null;
        updateServerInStore(config);
        postProgress(callback, McpStatus.CONNECTING);

        executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                if (config.transport == McpTransportType.STDIO) {
                    performStdioHandshake(config, callback, startTime);
                } else {
                    performHttpHandshake(config, callback, startTime);
                }
            } catch (McpAuthRequiredException authEx) {
                try {
                    config.status = McpStatus.AUTH_REQUIRED;
                    OAuthLoopbackReceiver oldReceiver = oauthReceivers.remove(config.id);
                    if (oldReceiver != null) oldReceiver.close();
                    OAuthLoopbackReceiver receiver = new OAuthLoopbackReceiver();
                    oauthReceivers.put(config.id, receiver);
                    McpOAuthResolver.OAuthChallengeInfo challenge = oauthResolver.resolveChallenge(config.endpointUrl, authEx.wwwAuthenticate, receiver.redirectUri());
                    McpOAuthResolver.OAuthSession session = oauthResolver.beginAuthorization(challenge, receiver.redirectUri());
                    receiver.listen(session.state, new OAuthLoopbackReceiver.Listener() {
                        @Override public void received(String uri) {
                            oauthReceivers.remove(config.id, receiver);
                            handleOAuthCallback(config, android.net.Uri.parse(uri), callback);
                        }
                        @Override public void failed(String message) {
                            oauthReceivers.remove(config.id, receiver);
                            config.status = McpStatus.AUTH_ERROR;
                            config.lastError = message;
                            updateServerInStore(config);
                            postFailure(callback, McpStatus.AUTH_ERROR, message);
                        }
                    });
                    config.oauthAuthorizationUrl = session.authorizationUrl;
                    config.lastError = "OAuth 2.1 authorization required. Tap Authorize to complete consent in browser.";
                    updateServerInStore(config);
                    postProgress(callback, McpStatus.AUTH_REQUIRED);
                    postFailure(callback, McpStatus.AUTH_REQUIRED, config.lastError);
                } catch (Exception resolveEx) {
                    OAuthLoopbackReceiver receiver = oauthReceivers.remove(config.id);
                    if (receiver != null) receiver.close();
                    config.status = McpStatus.AUTH_ERROR;
                    config.lastError = "OAuth discovery failed: " + resolveEx.getMessage();
                    updateServerInStore(config);
                    postFailure(callback, McpStatus.AUTH_ERROR, config.lastError);
                }
            } catch (Throwable t) {
                McpStatus errStatus = (t instanceof FileNotFoundException || (t.getMessage() != null && t.getMessage().contains("Executable not found")))
                        ? McpStatus.START_ERROR : McpStatus.PROTOCOL_ERROR;
                config.status = errStatus;
                config.lastError = t.getMessage() != null ? t.getMessage() : t.toString();
                disconnect(config.id);
                updateServerInStore(config);
                postFailure(callback, errStatus, config.lastError);
            }
        });
    }

    private void performStdioHandshake(McpServerConfig config, HandshakeCallback callback, long startTime) throws Exception {
        if (config.command == null || config.command.trim().isEmpty()) {
            throw new FileNotFoundException("Executable command is empty.");
        }

        // Tokenize command string safely to prevent shell injection
        List<String> cmdTokens = tokenizeCommand(config.command);
        if (cmdTokens.isEmpty()) {
            throw new FileNotFoundException("No executable specified in command.");
        }

        String execName = cmdTokens.get(0);
        File resolvedExec = resolveExecutable(execName);
        if (resolvedExec == null || !resolvedExec.exists() || !resolvedExec.canExecute()) {
            throw new FileNotFoundException("Executable not found or not executable on device: " + execName);
        }

        // Build command argument list
        List<String> fullCmd = new ArrayList<>();
        fullCmd.add(resolvedExec.getAbsolutePath());
        for (int i = 1; i < cmdTokens.size(); i++) fullCmd.add(cmdTokens.get(i));
        fullCmd.addAll(config.args);

        postProgress(callback, McpStatus.INITIALIZING);
        config.status = McpStatus.INITIALIZING;

        // Disconnect previous process if running
        disconnect(config.id);

        ProcessBuilder pb = new ProcessBuilder(fullCmd);
        if (config.workingDir != null && !config.workingDir.trim().isEmpty()) {
            File workDir = new File(config.workingDir.trim());
            if (workDir.exists() && workDir.isDirectory()) pb.directory(workDir);
        }

        // Environment variables
        Map<String, String> env = pb.environment();
        File usrBin = new File(context.getFilesDir(), "usr/bin");
        File forgeBin = new File(context.getFilesDir(), "forge-tools/bin");
        String currentPath = env.get("PATH");
        env.put("PATH", usrBin.getAbsolutePath() + ":" + forgeBin.getAbsolutePath() + (currentPath != null ? ":" + currentPath : ""));
        env.put("HOME", context.getFilesDir().getAbsolutePath());
        env.put("TMPDIR", context.getCacheDir().getAbsolutePath());
        env.putAll(config.env);

        Process process = pb.start();
        activeProcesses.put(config.id, process);

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        processReaders.put(config.id, reader);
        processWriters.put(config.id, writer);

        // Separate thread for stderr capture
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        executor.submit(() -> {
            try {
                String line;
                while ((line = stderrReader.readLine()) != null) {
                    Log.d(TAG, "[" + config.name + " stderr] " + line);
                }
            } catch (IOException ignored) {}
        });

        // Step 1: Send JSON-RPC initialize
        JSONObject initParams = new JSONObject()
                .put("protocolVersion", PROTOCOL_VERSION)
                .put("capabilities", new JSONObject()
                        .put("roots", new JSONObject().put("listChanged", true))
                        .put("sampling", new JSONObject()))
                .put("clientInfo", new JSONObject()
                        .put("name", "OceanStudio")
                        .put("version", "1.2.6"));

        JSONObject initReq = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "initialize")
                .put("params", initParams);

        sendJsonRpc(writer, initReq);

        // Await initialize response (timeout 5s)
        JSONObject initResp = readJsonRpcResponse(reader, 5000);
        if (initResp == null) {
            throw new IOException("Server did not respond to initialize within 5 seconds.");
        }
        if (initResp.has("error")) {
            throw new IOException("Protocol initialize error: " + initResp.getJSONObject("error").optString("message", "Unknown protocol error"));
        }

        JSONObject result = initResp.optJSONObject("result");
        if (result == null) {
            throw new IOException("Malformed JSON-RPC response: missing result object.");
        }

        config.protocolVersion = result.optString("protocolVersion", PROTOCOL_VERSION);
        JSONObject serverInfo = result.optJSONObject("serverInfo");
        if (serverInfo != null) {
            config.serverName = serverInfo.optString("name", config.name);
            config.serverVersion = serverInfo.optString("version", "1.0.0");
        }
        JSONObject caps = result.optJSONObject("capabilities");
        config.capabilities = caps != null ? caps : new JSONObject();

        // Step 2: Send initialized notification
        JSONObject notifyInitialized = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized");
        sendJsonRpc(writer, notifyInitialized);

        // Step 3: Discover capabilities (tools/list)
        postProgress(callback, McpStatus.DISCOVERING);
        config.status = McpStatus.DISCOVERING;

        if (config.capabilities.has("tools")) {
            JSONObject toolsReq = new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 2)
                    .put("method", "tools/list");
            sendJsonRpc(writer, toolsReq);

            JSONObject toolsResp = readJsonRpcResponse(reader, 5000);
            if (toolsResp != null && toolsResp.has("result")) {
                JSONArray toolsList = toolsResp.getJSONObject("result").optJSONArray("tools");
                config.tools = toolsList != null ? toolsList : new JSONArray();
            }
        }

        config.latencyMs = System.currentTimeMillis() - startTime;
        config.lastHandshakeEpochMs = System.currentTimeMillis();
        config.status = McpStatus.CONNECTED;
        config.lastError = null;

        updateServerInStore(config);
        postSuccess(callback, config);
    }

    private void performHttpHandshake(McpServerConfig config, HandshakeCallback callback, long startTime) throws Exception {
        if (config.endpointUrl == null || !config.endpointUrl.startsWith("http")) {
            throw new IllegalArgumentException("Invalid HTTP endpoint URL: " + config.endpointUrl);
        }

        postProgress(callback, McpStatus.INITIALIZING);
        config.status = McpStatus.INITIALIZING;

        JSONObject initParams = new JSONObject()
                .put("protocolVersion", PROTOCOL_VERSION)
                .put("capabilities", new JSONObject()
                        .put("roots", new JSONObject().put("listChanged", true))
                        .put("sampling", new JSONObject()))
                .put("clientInfo", new JSONObject()
                        .put("name", "OceanStudio")
                        .put("version", "1.2.6"));

        JSONObject initReq = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "initialize")
                .put("params", initParams);

        JSONObject initResp = sendHttpPost(config, initReq);
        if (initResp.has("error")) {
            throw new IOException("Remote MCP error: " + initResp.getJSONObject("error").optString("message"));
        }

        JSONObject result = initResp.optJSONObject("result");
        if (result == null) {
            throw new IOException("Remote server returned invalid JSON-RPC result.");
        }

        config.protocolVersion = result.optString("protocolVersion", PROTOCOL_VERSION);
        JSONObject serverInfo = result.optJSONObject("serverInfo");
        if (serverInfo != null) {
            config.serverName = serverInfo.optString("name", config.name);
            config.serverVersion = serverInfo.optString("version", "1.0.0");
        }
        JSONObject caps = result.optJSONObject("capabilities");
        config.capabilities = caps != null ? caps : new JSONObject();

        // Step 2: Send notifications/initialized (Directive §7.1 Step 12)
        JSONObject notifyInitialized = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized");
        sendHttpPost(config, notifyInitialized);

        // Step 3: Discover capabilities (tools/list)
        postProgress(callback, McpStatus.DISCOVERING);
        config.status = McpStatus.DISCOVERING;

        if (config.capabilities.has("tools")) {
            JSONObject toolsReq = new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 2)
                    .put("method", "tools/list");
            JSONObject toolsResp = sendHttpPost(config, toolsReq);
            if (toolsResp.has("error")) throw new IOException("MCP tools discovery failed.");
            if (!toolsResp.has("result")) throw new IOException("MCP tools discovery returned no result.");
            if (toolsResp.has("result")) {
                JSONArray toolsList = toolsResp.getJSONObject("result").optJSONArray("tools");
                config.tools = toolsList != null ? toolsList : new JSONArray();
            }
        }

        config.latencyMs = System.currentTimeMillis() - startTime;
        config.lastHandshakeEpochMs = System.currentTimeMillis();
        config.status = McpStatus.CONNECTED;
        config.lastError = null;

        updateServerInStore(config);
        postSuccess(callback, config);
    }

    private JSONObject sendHttpPost(McpServerConfig config, JSONObject payload) throws Exception {
        URL url = new URL(config.endpointUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        conn.setRequestProperty("User-Agent", "OceanStudio/1.2.6 (MCP Client)");

        if (config.protocolVersion != null && !"initialize".equals(payload.optString("method"))) {
            conn.setRequestProperty("MCP-Protocol-Version", config.protocolVersion);
        }

        // Bearer token resolution: config ref or CredentialVault OAuth storage
        String token = null;
        if (config.bearerTokenRef != null) {
            token = credentialVault.retrieve(config.bearerTokenRef);
        }
        if (token == null || token.isEmpty()) {
            token = oauthResolver.getStoredToken(config.endpointUrl);
        }
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        // Mcp-Session-Id header tracking (Directive §7.1 Step 11, §8.1)
        if (config.sessionId != null && !config.sessionId.isEmpty()) {
            conn.setRequestProperty("Mcp-Session-Id", config.sessionId);
        }

        for (Map.Entry<String, String> header : config.headers.entrySet()) {
            conn.setRequestProperty(header.getKey(), header.getValue());
        }

        byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }

        int code = conn.getResponseCode();

        // Detect HTTP 401 challenge on initial unauthenticated request (Directive §7, §8)
        if (code == 401) {
            String wwwAuth = conn.getHeaderField("WWW-Authenticate");
            conn.disconnect();
            throw new McpAuthRequiredException(wwwAuth);
        }

        // Capture server-assigned Mcp-Session-Id
        String returnedSessionId = conn.getHeaderField("Mcp-Session-Id");
        if (returnedSessionId != null && !returnedSessionId.isEmpty()) {
            config.sessionId = returnedSessionId;
        }

        try {
            if (code == 202 || code == 204) {
                if (payload.has("id")) throw new IOException("MCP request returned no JSON-RPC response.");
                return new JSONObject();
            }
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) throw new IOException("HTTP " + code + " with empty response");
            try (InputStream input = stream) {
                if (code < 200 || code >= 300) throw new IOException("MCP HTTP " + code);
                boolean sse = conn.getContentType() != null && conn.getContentType().startsWith("text/event-stream");
                if (sse) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                    StringBuilder data = new StringBuilder();
                    int total = 0;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        total += line.length();
                        if (total > 2 * 1024 * 1024) throw new IOException("MCP event response too large.");
                        if (line.isEmpty()) {
                            if (data.length() > 0) {
                                JSONObject event = new JSONObject(data.toString());
                                if (event.has("id") && String.valueOf(event.get("id")).equals(String.valueOf(payload.opt("id")))) return event;
                                data.setLength(0);
                            }
                        } else if (line.startsWith("data:")) {
                            if (data.length() > 0) data.append('\n');
                            data.append(line.substring(5).trim());
                        }
                    }
                    throw new IOException("MCP event stream ended without a matching response.");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (bytes.size() + n > 2 * 1024 * 1024) throw new IOException("MCP response too large.");
                    bytes.write(buffer, 0, n);
                }
                String response = bytes.toString(StandardCharsets.UTF_8.name());
                return response.trim().isEmpty() && !payload.has("id") ? new JSONObject() : new JSONObject(response);
            }
        } finally { conn.disconnect(); }
    }

    public void handleOAuthCallback(McpServerConfig config, Uri callbackUri, HandshakeCallback callback) {
        executor.submit(() -> {
            postProgress(callback, McpStatus.AUTHORIZING);
            config.status = McpStatus.AUTHORIZING;
            updateServerInStore(config);

            McpOAuthResolver.TokenResult tokenRes = oauthResolver.exchangeCode(callbackUri);
            if (!tokenRes.isSuccess) {
                config.status = McpStatus.AUTH_ERROR;
                config.lastError = tokenRes.error;
                updateServerInStore(config);
                postFailure(callback, McpStatus.AUTH_ERROR, tokenRes.error);
                return;
            }

            // Retry handshake with freshly acquired token
            connect(config, callback);
        });
    }

    private void sendJsonRpc(BufferedWriter writer, JSONObject obj) throws IOException {
        String str = obj.toString();
        writer.write(str);
        writer.newLine();
        writer.flush();
    }

    private JSONObject readJsonRpcResponse(BufferedReader reader, int timeoutMs) throws IOException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line == null) return null;
                line = line.trim();
                if (line.startsWith("{")) {
                    try {
                        return new JSONObject(line);
                    } catch (Exception ignored) {}
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }
        return null;
    }

    public synchronized void disconnect(String serverId) {
        OAuthLoopbackReceiver receiver = oauthReceivers.remove(serverId);
        if (receiver != null) receiver.close();
        Process p = activeProcesses.remove(serverId);
        if (p != null) {
            try { p.destroy(); } catch (Exception ignored) {}
        }
        processReaders.remove(serverId);
        processWriters.remove(serverId);
        McpServerConfig config = getServer(serverId);
        if (config != null) {
            config.sessionId = null;
            config.status = McpStatus.DISCONNECTED;
            updateServerInStore(config);
        }
    }

    public List<McpServerConfig> listServers() {
        List<McpServerConfig> result = new ArrayList<>();
        JSONArray arr = hubStore.mcps();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null) result.add(McpServerConfig.fromJson(obj));
        }
        return result;
    }

    public McpServerConfig getServer(String id) {
        for (McpServerConfig s : listServers()) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    public void updateServerInStore(McpServerConfig config) {
        try {
            JSONArray arr = hubStore.mcps();
            boolean found = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && config.id.equals(obj.optString("id"))) {
                    arr.put(i, config.toJson());
                    found = true;
                    break;
                }
            }
            if (!found) {
                arr.put(config.toJson());
            }
            hubStore.saveMcps(arr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist MCP server in store", e);
        }
    }

    public void deleteServer(String id) {
        disconnect(id);
        try {
            JSONArray arr = hubStore.mcps();
            JSONArray updated = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && !id.equals(obj.optString("id"))) {
                    updated.put(obj);
                }
            }
            hubStore.saveMcps(updated);
        } catch (Exception ignored) {}
    }

    public boolean isMcpTool(String toolName) {
        for (McpServerConfig s : listServers()) {
            if (s.isConnected()) {
                for (int i = 0; i < s.tools.length(); i++) {
                    JSONObject t = s.tools.optJSONObject(i);
                    if (t != null) {
                        String name = t.optString("name");
                        if (toolName.equals(name)
                                || toolName.equals(s.id + ":" + name)
                                || toolName.equals(s.name + ":" + name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public String callTool(String toolName, JSONObject args) throws Exception {
        for (McpServerConfig s : listServers()) {
            if (s.isConnected()) {
                for (int i = 0; i < s.tools.length(); i++) {
                    JSONObject t = s.tools.optJSONObject(i);
                    if (t != null) {
                        String name = t.optString("name");
                        if (toolName.equals(name)
                                || toolName.equals(s.id + ":" + name)
                                || toolName.equals(s.name + ":" + name)) {
                            return executeToolOnServer(s, name, args);
                        }
                    }
                }
            }
        }
        throw new IOException("No connected MCP server advertises tool: " + toolName);
    }

    private String executeToolOnServer(McpServerConfig server, String toolName, JSONObject args) throws Exception {
        int reqId = requestIdCounter.incrementAndGet();
        JSONObject params = new JSONObject()
                .put("name", toolName)
                .put("arguments", args != null ? args : new JSONObject());
        JSONObject req = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", reqId)
                .put("method", "tools/call")
                .put("params", params);

        JSONObject resp;
        if (server.transport == McpTransportType.STDIO) {
            BufferedWriter writer = processWriters.get(server.id);
            BufferedReader reader = processReaders.get(server.id);
            if (writer == null || reader == null) {
                throw new IOException("STDIO session for " + server.name + " is not connected.");
            }
            sendJsonRpc(writer, req);
            resp = readJsonRpcResponse(reader, 30000);
            if (resp == null) throw new IOException("Tool execution timed out.");
        } else {
            resp = sendHttpPost(server, req);
        }

        if (resp.has("error")) {
            throw new IOException("MCP error: " + resp.getJSONObject("error").optString("message"));
        }

        JSONObject res = resp.optJSONObject("result");
        if (res != null) {
            if (res.optBoolean("isError", false)) {
                JSONArray content = res.optJSONArray("content");
                String errDetail = content != null ? content.toString() : res.toString();
                throw new IOException("MCP tool reported execution failure: " + errDetail);
            }
            return res.toString();
        }
        return "Success";
    }

    private File resolveExecutable(String execName) {
        if (execName.startsWith("/") || execName.startsWith("./")) {
            File f = new File(execName);
            return f.exists() ? f : null;
        }

        File usrBin = new File(context.getFilesDir(), "usr/bin/" + execName);
        if (usrBin.exists()) return usrBin;

        File forgeBin = new File(context.getFilesDir(), "forge-tools/bin/" + execName);
        if (forgeBin.exists()) return forgeBin;

        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                File candidate = new File(dir, execName);
                if (candidate.exists()) return candidate;
            }
        }
        return null;
    }

    private List<String> tokenizeCommand(String cmd) {
        List<String> list = new ArrayList<>();
        if (cmd == null) return list;
        boolean inQuote = false;
        char quoteChar = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if ((c == '"' || c == '\'') && !inQuote) {
                inQuote = true;
                quoteChar = c;
            } else if (inQuote && c == quoteChar) {
                inQuote = false;
            } else if (Character.isWhitespace(c) && !inQuote) {
                if (sb.length() > 0) {
                    list.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) list.add(sb.toString());
        return list;
    }

    private void postProgress(HandshakeCallback cb, McpStatus status) {
        if (cb != null) mainHandler.post(() -> cb.onProgress(status));
    }

    private void postSuccess(HandshakeCallback cb, McpServerConfig config) {
        if (cb != null) mainHandler.post(() -> cb.onSuccess(config));
    }

    private void postFailure(HandshakeCallback cb, McpStatus status, String error) {
        if (cb != null) mainHandler.post(() -> cb.onFailure(status, error));
    }
}
