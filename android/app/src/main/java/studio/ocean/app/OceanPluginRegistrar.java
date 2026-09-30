package studio.ocean.app;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Registers user-installed plugins under files/home/.ocean/plugins (manifest + optional handler). */
public final class OceanPluginRegistrar {
    private OceanPluginRegistrar() {}

    public static File pluginsDir(Context context) {
        File dir = new File(context.getFilesDir(), "home/.ocean/plugins");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void registerManifest(Context context, String id, String name, String command, String description) throws Exception {
        String safeId = safeId(id);
        if (safeId.isEmpty()) throw new IllegalArgumentException("Plugin id is required");
        String cmd = command == null ? "" : command.trim();
        if (!cmd.matches("[A-Za-z0-9._+:-]{1,128}")) throw new IllegalArgumentException("Command must match an installed Ocean binary name");
        Properties p = new Properties();
        p.setProperty("id", safeId);
        p.setProperty("name", bounded(name, 128));
        p.setProperty("command", cmd);
        p.setProperty("description", bounded(description == null ? "User-registered Ocean plugin." : description, 512));
        writeManifest(context, safeId, p);
    }

    public static void scaffoldAndRegister(Context context, String id, String name, String language, String description) throws Exception {
        String safeId = safeId(id);
        if (safeId.isEmpty()) throw new IllegalArgumentException("Plugin id is required");
        File binRoot = new File(context.getFilesDir(), "forge-tools/bin");
        if (!binRoot.exists()) binRoot.mkdirs();
        File handler = new File(binRoot, "ocean-plugin-" + safeId);
        if (handler.exists()) throw new IllegalStateException("Handler already exists: " + handler.getName());
        String lang = language == null ? "bash" : language.trim().toLowerCase(Locale.ROOT);
        String prefix = new File(context.getFilesDir(), "usr/bin").getAbsolutePath();
        String script;
        if ("python".equals(lang)) {
            script = "#!" + prefix + "/python3\n"
                    + "import json, sys\n"
                    + "data = sys.stdin.read()\n"
                    + "print(json.dumps({\"plugin\": \"" + safeId + "\", \"input\": data}, ensure_ascii=False))\n";
        } else {
            script = "#!" + prefix + "/bash\n"
                    + "set -euo pipefail\n"
                    + "input=\"$(cat)\"\n"
                    + "printf 'Plugin %s received %s bytes\\n' '" + safeId + "' \"${#input}\"\n"
                    + "printf '%s\\n' \"$input\"\n";
        }
        try (FileOutputStream out = new FileOutputStream(handler)) {
            out.write(script.getBytes(StandardCharsets.UTF_8));
        }
        handler.setExecutable(true, false);
        registerManifest(context, safeId, name, handler.getName(), description);
    }

    public static void importManifestJson(Context context, JSONObject manifest) throws Exception {
        if (manifest.has("plugins")) {
            JSONArray arr = manifest.getJSONArray("plugins");
            for (int i = 0; i < arr.length(); i++) {
                importManifestJson(context, arr.getJSONObject(i));
            }
            return;
        }
        String id = manifest.optString("id", manifest.optString("plugin_id", ""));
        registerManifest(context, id,
                manifest.optString("name", id),
                manifest.optString("command", ""),
                manifest.optString("description", "Imported plugin."));
    }

    public static void importFromZip(Context context, InputStream raw) throws Exception {
        boolean registered = false;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.endsWith(".plugin")) {
                    StringBuilder sb = new StringBuilder();
                    int n;
                    while ((n = zip.read(buffer)) > 0) sb.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                    Properties p = new Properties();
                    p.load(new java.io.StringReader(sb.toString()));
                    registerManifest(context,
                            p.getProperty("id", name.replace(".plugin", "")),
                            p.getProperty("name", "Plugin"),
                            p.getProperty("command", ""),
                            p.getProperty("description", "Imported from archive."));
                    registered = true;
                } else if (name.endsWith(".json")) {
                    StringBuilder sb = new StringBuilder();
                    int n;
                    while ((n = zip.read(buffer)) > 0) sb.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                    importManifestJson(context, new JSONObject(sb.toString()));
                    registered = true;
                }
            }
        }
        if (!registered) throw new IllegalArgumentException("Archive must contain a .plugin or plugin manifest .json");
    }

    public static void importFromGithubRepo(Context context, String repoUrl) throws Exception {
        String[] parts = parseGithubRepo(repoUrl);
        if (parts == null) throw new IllegalArgumentException("Use a github.com/owner/repo URL");
        String owner = parts[0];
        String repo = parts[1];
        String[] candidates = {
                "https://raw.githubusercontent.com/" + owner + "/" + repo + "/HEAD/.ocean/plugins/manifest.json",
                "https://raw.githubusercontent.com/" + owner + "/" + repo + "/main/.ocean/plugins/manifest.json",
                "https://raw.githubusercontent.com/" + owner + "/" + repo + "/master/.ocean/plugins/manifest.json"
        };
        for (String url : candidates) {
            try {
                String body = httpGet(url, 15000);
                if (body != null && !body.isEmpty()) {
                    importManifestJson(context, new JSONObject(body));
                    return;
                }
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("No .ocean/plugins/manifest.json found on default branches");
    }

    public static void importFromCatalogUrl(Context context, String catalogUrl) throws Exception {
        if (catalogUrl == null || catalogUrl.trim().isEmpty()) throw new IllegalArgumentException("URL or path required");
        String trimmed = catalogUrl.trim();
        if (trimmed.startsWith("file://")) {
            File file = new File(trimmed.substring(7));
            if (!file.isFile()) throw new IllegalArgumentException("Local file not found");
            try (FileInputStream in = new FileInputStream(file)) {
                String body = readStream(in);
                if (trimmed.endsWith(".zip")) importFromZip(context, new FileInputStream(file));
                else importManifestJson(context, new JSONObject(body));
            }
            return;
        }
        if (trimmed.startsWith("/")) {
            File file = new File(trimmed);
            if (!file.isFile()) throw new IllegalArgumentException("Local file not found");
            if (trimmed.endsWith(".zip")) {
                try (FileInputStream in = new FileInputStream(file)) {
                    importFromZip(context, in);
                }
            } else {
                try (FileInputStream in = new FileInputStream(file)) {
                    importManifestJson(context, new JSONObject(readStream(in)));
                }
            }
            return;
        }
        String body = httpGet(trimmed, 20000);
        if (trimmed.endsWith(".zip")) {
            importFromZip(context, new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.ISO_8859_1)));
        } else {
            importManifestJson(context, new JSONObject(body));
        }
    }

    private static void writeManifest(Context context, String id, Properties p) throws Exception {
        File out = new File(pluginsDir(context), id + ".plugin");
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(p.getProperty("id")).append('\n');
        sb.append("name=").append(p.getProperty("name")).append('\n');
        sb.append("command=").append(p.getProperty("command")).append('\n');
        sb.append("description=").append(p.getProperty("description")).append('\n');
        try (OutputStream fos = new FileOutputStream(out)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String httpGet(String url, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
        conn.setRequestProperty("User-Agent", "OceanStudio-PluginImport/1.0");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalArgumentException("HTTP " + code);
        try (InputStream in = conn.getInputStream()) {
            return readStream(in);
        }
    }

    private static String readStream(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String[] parseGithubRepo(String url) {
        if (url == null) return null;
        String u = url.trim().replace("https://github.com/", "").replace("http://github.com/", "");
        if (u.contains("github.com/")) {
            int i = u.indexOf("github.com/");
            u = u.substring(i + "github.com/".length());
        }
        u = u.replaceAll("\\?.*$", "").replaceAll("/$", "");
        String[] parts = u.split("/");
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null;
        return new String[]{parts[0], parts[1]};
    }

    private static String safeId(String value) {
        if (value == null) return "";
        String id = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        return id.length() > 80 ? id.substring(0, 80) : id;
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        value = value.replace('\0', ' ').replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() > max ? value.substring(0, max) : value;
    }
}
