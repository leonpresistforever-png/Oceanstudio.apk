package studio.ocean.app.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration and discovered capability state for an MCP server (Directive 2026-10-02 §10, §11).
 * Supports STDIO child processes, Streamable HTTP endpoints, and secret redaction.
 */
public final class McpServerConfig {

    public String id;
    public String name;
    public McpTransportType transport = McpTransportType.STDIO;

    // STDIO specific
    public String command = "";
    public List<String> args = new ArrayList<>();
    public String workingDir = "";
    public Map<String, String> env = new HashMap<>();

    // Streamable HTTP / SSE specific
    public String endpointUrl = "";
    public Map<String, String> headers = new HashMap<>();
    public String bearerTokenRef = null;

    // Truthful runtime inspection state
    public McpStatus status = McpStatus.SAVED;
    public String lastError = null;
    public String protocolVersion = null;
    public String serverName = null;
    public String serverVersion = null;
    public JSONObject capabilities = new JSONObject();
    public JSONArray tools = new JSONArray();
    public long lastHandshakeEpochMs = 0;
    public long latencyMs = 0;
    public int restartCount = 0;

    public McpServerConfig() {
        this.id = UUID.randomUUID().toString();
    }

    public McpServerConfig(String name, McpTransportType transport) {
        this();
        this.name = name;
        this.transport = transport;
    }

    public boolean isConnected() {
        return status == McpStatus.CONNECTED;
    }

    public String getRedactedEndpointOrCommand() {
        if (transport == McpTransportType.STDIO) {
            if (command == null || command.isEmpty()) return "STDIO";
            return command + (args.isEmpty() ? "" : " " + String.join(" ", args));
        } else {
            if (endpointUrl == null || endpointUrl.isEmpty()) return "Remote HTTP";
            try {
                java.net.URI uri = new java.net.URI(endpointUrl);
                // Redact user-info if present in URL
                if (uri.getUserInfo() != null) {
                    return endpointUrl.replace(uri.getUserInfo() + "@", "***@");
                }
            } catch (Exception ignored) {}
            return endpointUrl;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("transport", transport.id);
        obj.put("command", command);

        JSONArray argsArr = new JSONArray();
        for (String a : args) argsArr.put(a);
        obj.put("args", argsArr);

        obj.put("workingDir", workingDir);

        JSONObject envObj = new JSONObject();
        for (Map.Entry<String, String> e : env.entrySet()) envObj.put(e.getKey(), e.getValue());
        obj.put("env", envObj);

        obj.put("endpointUrl", endpointUrl);

        JSONObject headersObj = new JSONObject();
        for (Map.Entry<String, String> e : headers.entrySet()) headersObj.put(e.getKey(), e.getValue());
        obj.put("headers", headersObj);

        if (bearerTokenRef != null) obj.put("bearerTokenRef", bearerTokenRef);

        obj.put("status", status.name());
        obj.put("connected", isConnected());
        if (lastError != null) obj.put("lastError", lastError);
        if (protocolVersion != null) obj.put("protocolVersion", protocolVersion);
        if (serverName != null) obj.put("serverName", serverName);
        if (serverVersion != null) obj.put("serverVersion", serverVersion);

        obj.put("capabilities", capabilities);
        obj.put("tools", tools);
        obj.put("lastHandshakeEpochMs", lastHandshakeEpochMs);
        obj.put("latencyMs", latencyMs);
        obj.put("restartCount", restartCount);

        return obj;
    }

    public static McpServerConfig fromJson(JSONObject obj) {
        McpServerConfig cfg = new McpServerConfig();
        cfg.id = obj.optString("id", UUID.randomUUID().toString());
        cfg.name = obj.optString("name", "MCP Server");
        cfg.transport = McpTransportType.fromString(obj.optString("transport", "stdio"));
        cfg.command = obj.optString("command", "");

        JSONArray argsArr = obj.optJSONArray("args");
        if (argsArr != null) {
            for (int i = 0; i < argsArr.length(); i++) cfg.args.add(argsArr.optString(i));
        }

        cfg.workingDir = obj.optString("workingDir", "");

        JSONObject envObj = obj.optJSONObject("env");
        if (envObj != null) {
            Iterator<String> keys = envObj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                cfg.env.put(k, envObj.optString(k));
            }
        }

        cfg.endpointUrl = obj.optString("endpointUrl", "");

        JSONObject headersObj = obj.optJSONObject("headers");
        if (headersObj != null) {
            Iterator<String> keys = headersObj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                cfg.headers.put(k, headersObj.optString(k));
            }
        }

        cfg.bearerTokenRef = obj.optString("bearerTokenRef", null);
        if (cfg.bearerTokenRef != null && cfg.bearerTokenRef.isEmpty()) cfg.bearerTokenRef = null;

        cfg.status = McpStatus.fromString(obj.optString("status", McpStatus.SAVED.name()));
        cfg.lastError = obj.optString("lastError", null);
        if (cfg.lastError != null && cfg.lastError.isEmpty()) cfg.lastError = null;

        cfg.protocolVersion = obj.optString("protocolVersion", null);
        cfg.serverName = obj.optString("serverName", null);
        cfg.serverVersion = obj.optString("serverVersion", null);

        cfg.capabilities = obj.optJSONObject("capabilities");
        if (cfg.capabilities == null) cfg.capabilities = new JSONObject();

        cfg.tools = obj.optJSONArray("tools");
        if (cfg.tools == null) cfg.tools = new JSONArray();

        cfg.lastHandshakeEpochMs = obj.optLong("lastHandshakeEpochMs", 0);
        cfg.latencyMs = obj.optLong("latencyMs", 0);
        cfg.restartCount = obj.optInt("restartCount", 0);

        return cfg;
    }
}
