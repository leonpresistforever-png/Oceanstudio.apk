package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.UUID;

/** User- and agent-authored skills, MCP entries, HTTP functions, and OpenAI-style tools. */
public final class OceanAgentHubStore {
    private static final String PREFS = "ocean_agent_hub";
    private static final String KEY_SKILLS = "skills_json";
    private static final String KEY_MCPS = "mcps_json";
    private static final String KEY_FUNCTIONS = "functions_json";
    private static final String KEY_TOOLS = "tools_json";

    private final SharedPreferences prefs;

    public OceanAgentHubStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureSeeded();
    }

    private void ensureSeeded() {
        if (prefs.contains(KEY_SKILLS)) return;
        JSONArray skills = new JSONArray();
        String[][] bundled = {
                {"deep-coding", "Deep Coding", "connected", "agent",
                        "Production-grade software engineering: read before edit, minimal diffs, match repo conventions, verify with real commands, and never claim success without tool output."},
                {"ux-design", "Design & UX", "connected", "agent",
                        "Interface critique and implementation: hierarchy, spacing, motion, accessibility, empty states, and greyscale premium surfaces. Propose concrete layout/copy changes."},
                {"debugging", "Debugging & RCA", "connected", "agent",
                        "Hypothesis-driven debugging: reproduce, instrument, compare logs, isolate regressions, fix root cause, and re-run the same path that failed."},
                {"automation", "Automation Architect", "connected", "agent",
                        "Design reliable automations with idempotent steps, clear triggers, rollback paths, and human checkpoints for destructive actions."},
                {"android-device", "Android Device Ops", "connected", "agent",
                        "Use Ocean device tools responsibly: list apps, dispatch headless intents when appropriate, open UI when needed, respect App Access profiles."},
                {"terminal-runtime", "Terminal & Runtime", "connected", "agent",
                        "Operate Ocean's bash runtime: pkg installs, localhost ports, noVNC verification, and non-interactive flags for long commands."},
                {"security-review", "Security Review", "disconnected", "manual",
                        "Threat modeling for mobile and web: secrets handling, IPC boundaries, intent surfaces, and least-privilege recommendations."},
                {"data-pipeline", "Data & Files", "connected", "agent",
                        "Structured file workflows: search, diff, transform JSON/CSV, validate checksums, and keep artifacts inside Ocean home."},
                {"api-integration", "API Integration", "connected", "agent",
                        "Compose HTTP tools with params, auth, and JSON bodies; test with curl from terminal; document failure modes."},
                {"forge-self", "Ocean Forge", "connected", "agent",
                        "Self-modify OceanStudio via Forge: checkpoint, confined workspace edits, tests, build, verify signing before claiming installability."},
                {"research", "Research & Synthesis", "connected", "agent",
                        "Gather evidence, cite tool output, separate facts from guesses, and produce concise decision-ready summaries."},
                {"release-ops", "Release & CI", "disconnected", "manual",
                        "Release hygiene: version bumps, workflow triggers, artifact checks, and rollback notes for APK/package repos."}
        };
        for (String[] row : bundled) {
            skills.put(skill(row[0], row[1], row[2], row[3], row[4]));
        }
        prefs.edit()
                .putString(KEY_SKILLS, skills.toString())
                .putString(KEY_MCPS, new JSONArray().toString())
                .putString(KEY_FUNCTIONS, new JSONArray().toString())
                .putString(KEY_TOOLS, defaultTools().toString())
                .apply();
    }

    private static JSONObject skill(String id, String title, String status, String source, String body) {
        try {
            return new JSONObject()
                    .put("id", id)
                    .put("title", title)
                    .put("status", status)
                    .put("source", source)
                    .put("description", body)
                    .put("created_at", System.currentTimeMillis());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private JSONArray defaultTools() {
        JSONArray tools = new JSONArray();
        try {
            tools.put(openAiTool("web_fetch", "Fetch a public HTTP URL and return response metadata.",
                    new JSONObject().put("type", "object").put("properties", new JSONObject()
                            .put("url", new JSONObject().put("type", "string"))).put("required", new JSONArray().put("url"))));
            tools.put(openAiTool("http_request", "Run a configured HTTP request (method, headers, JSON body).",
                    new JSONObject().put("type", "object").put("properties", new JSONObject()
                            .put("method", new JSONObject().put("type", "string"))
                            .put("url", new JSONObject().put("type", "string"))
                            .put("body", new JSONObject().put("type", "string")))));
        } catch (Exception ignored) {}
        return tools;
    }

    private static JSONObject openAiTool(String name, String description, JSONObject parameters) throws Exception {
        return new JSONObject().put("name", name).put("description", description).put("parameters", parameters);
    }

    public JSONArray skills() { return read(KEY_SKILLS); }
    public JSONArray mcps() { return read(KEY_MCPS); }
    public JSONArray functions() { return read(KEY_FUNCTIONS); }
    public JSONArray tools() { return read(KEY_TOOLS); }

    public void saveSkills(JSONArray arr) { prefs.edit().putString(KEY_SKILLS, arr.toString()).apply(); }
    public void saveMcps(JSONArray arr) { prefs.edit().putString(KEY_MCPS, arr.toString()).apply(); }
    public void saveFunctions(JSONArray arr) { prefs.edit().putString(KEY_FUNCTIONS, arr.toString()).apply(); }
    public void saveTools(JSONArray arr) { prefs.edit().putString(KEY_TOOLS, arr.toString()).apply(); }

    public JSONObject addSkill(String title, String description, String source) throws Exception {
        JSONArray arr = skills();
        JSONObject item = skill(safeId(title), title, "connected", source, description);
        arr.put(item);
        saveSkills(arr);
        return item;
    }

    public JSONObject addMcp(String name, String transport, String command) throws Exception {
        JSONArray arr = mcps();
        JSONObject item = new JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("name", name)
                .put("transport", transport)
                .put("command", command)
                .put("status", "saved")
                .put("connected", false)
                .put("created_at", System.currentTimeMillis());
        arr.put(item);
        saveMcps(arr);
        return item;
    }

    public JSONObject addFunction(String name, JSONObject httpConfig) throws Exception {
        JSONArray arr = functions();
        JSONObject item = new JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("name", name)
                .put("http", httpConfig)
                .put("created_at", System.currentTimeMillis());
        arr.put(item);
        saveFunctions(arr);
        return item;
    }

    public String activeSkillPromptBlock() {
        StringBuilder out = new StringBuilder();
        JSONArray arr = skills();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            if (!"connected".equals(s.optString("status"))) continue;
            out.append("• ").append(s.optString("title")).append(": ")
                    .append(s.optString("description")).append("\n");
        }
        return out.toString().trim();
    }

    public String augmentedInstructions(String base) {
        String skills = activeSkillPromptBlock();
        if (skills.isEmpty()) return base == null ? "" : base;
        String prefix = base == null ? "" : base.trim();
        return (prefix.isEmpty() ? "" : prefix + "\n\n") + "Active Ocean skills (follow when relevant):\n" + skills;
    }

    private JSONArray read(String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static String safeId(String title) {
        String id = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (id.isEmpty()) id = "skill";
        return id + "-" + Integer.toHexString(title.hashCode() & 0xffff);
    }
}
