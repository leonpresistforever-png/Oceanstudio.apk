package studio.ocean.app;

import java.util.Locale;

/**
 * Semantic monochrome icon registry for skills, plugins, and tools.
 * Eliminates the repeated generic star icon by mapping domains to distinct outline symbols.
 */
public final class OceanIconRegistry {

    private OceanIconRegistry() {}

    /**
     * Resolves a semantic icon resource ID based on the capability ID, title, and description.
     */
    public static int getSkillIcon(String id, String title, String description) {
        String text = ((id == null ? "" : id) + " "
                + (title == null ? "" : title) + " "
                + (description == null ? "" : description)).toLowerCase(Locale.ROOT);

        // Terminal / Shell / Command-line
        if (text.contains("terminal") || text.contains("shell") || text.contains("bash")
                || text.contains("sh") || text.contains("cli") || text.contains("pty")) {
            return R.drawable.ic_terminal;
        }

        // Code / Editor / Syntax / Diff / Git
        if (text.contains("code") || text.contains("coding") || text.contains("editor")
                || text.contains("git") || text.contains("diff") || text.contains("review")
                || text.contains("syntax") || text.contains("format")) {
            return R.drawable.ic_editor;
        }

        // Files / Storage / Workspace / Documents / Assets
        if (text.contains("file") || text.contains("storage") || text.contains("workspace")
                || text.contains("data") || text.contains("json") || text.contains("document")
                || text.contains("directory") || text.contains("archive")) {
            return R.drawable.ic_files;
        }

        // Browser / Web / HTTP / Scraping
        if (text.contains("browser") || text.contains("web") || text.contains("http")
                || text.contains("url") || text.contains("html") || text.contains("crawl")
                || text.contains("fetch") || text.contains("download")) {
            return R.drawable.ic_browser_nav;
        }

        // Security / Auth / Permissions / Secrets
        if (text.contains("security") || text.contains("auth") || text.contains("key")
                || text.contains("token") || text.contains("lock") || text.contains("permission")
                || text.contains("secret") || text.contains("credential") || text.contains("cert")) {
            return R.drawable.ic_lock_secure;
        }

        // Network / Ports / Sockets / Services
        if (text.contains("port") || text.contains("network") || text.contains("socket")
                || text.contains("server") || text.contains("proxy") || text.contains("tcp")) {
            return R.drawable.ic_ports;
        }

        // Android Device / OS / Battery / Hardware
        if (text.contains("device") || text.contains("android") || text.contains("battery")
                || text.contains("system") || text.contains("screen") || text.contains("hardware")) {
            return R.drawable.ic_agent;
        }

        // Model / Workflow / AI / Automation
        if (text.contains("model") || text.contains("ai") || text.contains("llm")
                || text.contains("workflow") || text.contains("automation") || text.contains("agent")
                || text.contains("flow") || text.contains("prompt")) {
            return R.drawable.ic_models;
        }

        // Search / Debug / Investigation / Log / Audit
        if (text.contains("search") || text.contains("find") || text.contains("debug")
                || text.contains("log") || text.contains("audit") || text.contains("inspect")
                || text.contains("rca") || text.contains("trace") || text.contains("diagnos")) {
            return R.drawable.ic_search;
        }

        // Connections / External MCP / Integrations
        if (text.contains("connect") || text.contains("integration") || text.contains("mcp")
                || text.contains("bridge") || text.contains("link") || text.contains("remote")) {
            return R.drawable.ic_connections;
        }

        // Semantic default tool icon
        return R.drawable.ic_tools;
    }
}
