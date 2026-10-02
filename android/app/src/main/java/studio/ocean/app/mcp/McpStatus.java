package studio.ocean.app.mcp;

/**
 * MCP connection state machine (Directive 2026-10-02 §10, §11).
 * Strictly guarantees truthful connection lifecycle:
 * SAVED -> STARTING -> CONNECTING -> AUTH_REQUIRED / AUTHORIZING -> INITIALIZING -> DISCOVERING -> CONNECTED
 * Only CONNECTED may expose tools to Ocean Agent.
 */
public enum McpStatus {
    SAVED("Saved / Not verified"),
    STARTING("Starting runtime..."),
    CONNECTING("Connecting..."),
    AUTH_REQUIRED("OAuth Required"),
    AUTHORIZING("Authorizing in browser..."),
    INITIALIZING("Initializing handshake..."),
    DISCOVERING("Discovering capabilities..."),
    CONNECTED("Connected"),
    REAUTH_REQUIRED("Re-auth Required"),
    START_ERROR("Start Error"),
    AUTH_ERROR("Auth Error"),
    PROTOCOL_ERROR("Protocol Error"),
    OFFLINE("Offline"),
    DISCONNECTED("Disconnected");

    public final String label;

    McpStatus(String label) {
        this.label = label;
    }

    public static McpStatus fromString(String val) {
        if (val == null) return SAVED;
        for (McpStatus s : values()) {
            if (s.name().equalsIgnoreCase(val) || s.label.equalsIgnoreCase(val)) return s;
        }
        return SAVED;
    }
}
