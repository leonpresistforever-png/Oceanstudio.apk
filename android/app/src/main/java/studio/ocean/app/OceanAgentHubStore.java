package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** User- and agent-authored skills (on disk), MCP entries, HTTP functions, and OpenAI-style tools. */
public final class OceanAgentHubStore {
    private static final String PREFS = "ocean_agent_hub";
    private static final String KEY_MCPS = "mcps_json";
    private static final String KEY_FUNCTIONS = "functions_json";
    private static final String KEY_TOOLS = "tools_json";
    private static final String KEY_SKILLS_DISK = "skills_disk_version";
    private static final int SKILLS_DISK_VERSION = 2;
    private static final int PROMPT_CHAR_BUDGET_PER_SKILL = 2800;
    private static final int PROMPT_CHAR_BUDGET_TOTAL = 12000;

    private static final Pattern FRONT_MATTER = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    private static final Pattern FM_LINE = Pattern.compile("^([a-zA-Z0-9_-]+):\\s*(.*)$");

    private final Context context;
    private final SharedPreferences prefs;

    public OceanAgentHubStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureSeeded();
    }

    private File skillsRoot() {
        return new File(context.getFilesDir(), "home/.ocean/skills");
    }

    private File skillDir(String id) {
        return new File(skillsRoot(), id);
    }

    private File skillFile(String id) {
        return new File(skillDir(id), "SKILL.md");
    }

    private void ensureSeeded() {
        int version = prefs.getInt(KEY_SKILLS_DISK, 0);
        if (version < SKILLS_DISK_VERSION) {
            migrateLegacyPrefsToDisk();
            seedBundledSkills();
            prefs.edit().putInt(KEY_SKILLS_DISK, SKILLS_DISK_VERSION).apply();
        } else {
            seedBundledSkills();
        }
        if (!prefs.contains(KEY_TOOLS)) {
            prefs.edit().putString(KEY_TOOLS, defaultTools().toString()).apply();
        }
        if (!prefs.contains(KEY_FUNCTIONS)) {
            prefs.edit().putString(KEY_FUNCTIONS, new JSONArray().toString()).apply();
        }
        if (!prefs.contains(KEY_MCPS)) {
            prefs.edit().putString(KEY_MCPS, new JSONArray().toString()).apply();
        }
    }

    private void migrateLegacyPrefsToDisk() {
        if (!prefs.contains("skills_json")) return;
        try {
            JSONArray legacy = new JSONArray(prefs.getString("skills_json", "[]"));
            for (int i = 0; i < legacy.length(); i++) {
                JSONObject s = legacy.optJSONObject(i);
                if (s == null) continue;
                String id = s.optString("id", safeId(s.optString("title", "skill")));
                if (skillFile(id).exists()) continue;
                String title = s.optString("title", id);
                String description = s.optString("description", "");
                String status = s.optString("status", "disconnected");
                String source = s.optString("source", "manual");
                writeSkillMarkdown(id, title, description, status, source, "# " + title + "\n\n" + description + "\n");
            }
            prefs.edit().remove("skills_json").apply();
        } catch (Exception ignored) {}
    }

    private void seedBundledSkills() {
        skillsRoot().mkdirs();
        for (String[] pack : OceanBundledSkills.PACKS) {
            String id = pack[0];
            String markdown = pack[1];
            File file = skillFile(id);
            if (file.exists() && file.length() >= 400) continue;
            writeRawSkillFile(id, markdown);
        }
    }

    private void writeRawSkillFile(String id, String markdown) {
        try {
            File dir = skillDir(id);
            dir.mkdirs();
            File out = new File(dir, "SKILL.md");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(markdown.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    public void writeSkillMarkdown(String id, String name, String description, String status, String source, String bodyMarkdown) {
        String fm = "---\n"
                + "id: " + id + "\n"
                + "name: " + name + "\n"
                + "description: " + description + "\n"
                + "status: " + status + "\n"
                + "source: " + source + "\n"
                + "---\n\n";
        writeRawSkillFile(id, fm + bodyMarkdown);
    }

    public String readSkillMarkdown(String id) {
        File file = skillFile(id);
        if (!file.exists()) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public Map<String, String> parseFrontMatter(String markdown) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = FRONT_MATTER.matcher(markdown);
        if (!m.find()) return map;
        String block = m.group(1);
        for (String line : block.split("\n")) {
            Matcher lm = FM_LINE.matcher(line.trim());
            if (lm.matches()) map.put(lm.group(1), lm.group(2).trim());
        }
        return map;
    }

    public String skillBodyWithoutFrontMatter(String markdown) {
        return FRONT_MATTER.matcher(markdown).replaceFirst("").trim();
    }

    public JSONArray skills() {
        JSONArray out = new JSONArray();
        File root = skillsRoot();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return out;
        List<File> sorted = new ArrayList<>();
        for (File d : dirs) sorted.add(d);
        sorted.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (File dir : sorted) {
            String id = dir.getName();
            String md = readSkillMarkdown(id);
            if (md.isEmpty()) continue;
            Map<String, String> fm = parseFrontMatter(md);
            try {
                JSONObject item = new JSONObject()
                        .put("id", fm.getOrDefault("id", id))
                        .put("title", fm.getOrDefault("name", id))
                        .put("description", fm.getOrDefault("description", ""))
                        .put("status", fm.getOrDefault("status", "disconnected"))
                        .put("source", fm.getOrDefault("source", "manual"));
                out.put(item);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public JSONObject findSkillBySlash(String slashCommand) {
        if (slashCommand == null || !slashCommand.startsWith("/")) return null;
        String needle = slashCommand.substring(1).toLowerCase(Locale.ROOT);
        JSONArray arr = skills();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            String id = s.optString("id", "").toLowerCase(Locale.ROOT);
            String title = s.optString("title", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            if (needle.equals(id) || needle.equals(title) || needle.equals(id.replace("-", ""))) return s;
        }
        return null;
    }

    public void saveSkills(JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            String id = s.optString("id");
            String md = readSkillMarkdown(id);
            if (md.isEmpty()) continue;
            Map<String, String> fm = parseFrontMatter(md);
            fm.put("status", s.optString("status", fm.getOrDefault("status", "disconnected")));
            fm.put("name", s.optString("title", fm.getOrDefault("name", id)));
            fm.put("description", s.optString("description", fm.getOrDefault("description", "")));
            fm.put("source", s.optString("source", fm.getOrDefault("source", "manual")));
            String body = skillBodyWithoutFrontMatter(md);
            writeSkillMarkdown(id, fm.getOrDefault("name", id), fm.getOrDefault("description", ""),
                    fm.get("status"), fm.getOrDefault("source", "manual"), body);
        }
    }

    public JSONArray mcps() { return read(KEY_MCPS); }
    public JSONArray functions() { return read(KEY_FUNCTIONS); }
    public JSONArray tools() { return read(KEY_TOOLS); }

    public void saveMcps(JSONArray arr) { prefs.edit().putString(KEY_MCPS, arr.toString()).apply(); }
    public void saveFunctions(JSONArray arr) { prefs.edit().putString(KEY_FUNCTIONS, arr.toString()).apply(); }
    public void saveTools(JSONArray arr) { prefs.edit().putString(KEY_TOOLS, arr.toString()).apply(); }

    public JSONObject addSkill(String title, String bodyMarkdown, String source) throws Exception {
        String id = safeId(title);
        while (skillFile(id).exists()) id = id + "-" + Integer.toHexString((int) (Math.random() * 0xffff));
        String description = firstLine(bodyMarkdown);
        writeSkillMarkdown(id, title, description, "connected", source, bodyMarkdown);
        JSONObject item = new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("description", description)
                .put("status", "connected")
                .put("source", source);
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

    public void setSkillConnected(String id, boolean connected) {
        try {
            JSONArray arr = skills();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.getJSONObject(i);
                if (id.equals(s.optString("id"))) {
                    s.put("status", connected ? "connected" : "disconnected");
                }
            }
            saveSkills(arr);
        } catch (Exception ignored) {}
    }

    public String activeSkillPromptBlock() {
        StringBuilder out = new StringBuilder();
        int budget = PROMPT_CHAR_BUDGET_TOTAL;
        JSONArray arr = skills();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            if (!"connected".equals(s.optString("status"))) continue;
            String id = s.optString("id");
            String md = readSkillMarkdown(id);
            String body = skillBodyWithoutFrontMatter(md);
            if (body.isEmpty()) body = s.optString("description");
            if (body.length() > PROMPT_CHAR_BUDGET_PER_SKILL) {
                body = body.substring(0, PROMPT_CHAR_BUDGET_PER_SKILL) + "\n…(truncated)";
            }
            String block = "### " + s.optString("title") + "\n" + body + "\n\n";
            if (block.length() > budget) {
                block = block.substring(0, Math.max(0, budget - 20)) + "\n…(truncated)\n";
            }
            out.append(block);
            budget -= block.length();
            if (budget <= 0) break;
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
        return id;
    }

    private static String firstLine(String text) {
        if (text == null) return "";
        String[] lines = text.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) return t.length() > 160 ? t.substring(0, 160) : t;
        }
        return text.length() > 160 ? text.substring(0, 160) : text;
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
}
