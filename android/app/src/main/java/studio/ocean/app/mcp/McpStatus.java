package studio.ocean.app.mcp;

/**
 * MCP connection state machine (Directive 2026-10-02 §10.2).
 * Strictly forbids false-positive connected states without a verified protocol handshake.
 */
public enum McpStatus {
    SAVED("Saved / Not verified"),
    CONNECTING("Connecting..."),
    INITIALIZING("Initializing handshake..."),
    DISCOVERING("Discovering capabilities..."),
    CONNECTED("Connected"),
    START_ERROR("Start Error"),
    PROTOCOL_ERROR("Protocol Error"),
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
