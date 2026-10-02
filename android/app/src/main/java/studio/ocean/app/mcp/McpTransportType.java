package studio.ocean.app.mcp;

/**
 * Supported MCP transport types (Directive 2026-10-02 §10.1).
 * Official transports: STDIO and Streamable HTTP. Legacy SSE clearly labeled.
 */
public enum McpTransportType {
    STDIO("stdio", "STDIO (Local process)"),
    STREAMABLE_HTTP("http", "Streamable HTTP (Remote)"),
    LEGACY_SSE("sse", "SSE (Legacy)");

    public final String id;
    public final String label;

    McpTransportType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public static McpTransportType fromString(String val) {
        if (val == null) return STDIO;
        if (val.equalsIgnoreCase("http") || val.equalsIgnoreCase("streamable_http")) return STREAMABLE_HTTP;
        if (val.equalsIgnoreCase("sse") || val.equalsIgnoreCase("legacy_sse")) return LEGACY_SSE;
        return STDIO;
    }
}
