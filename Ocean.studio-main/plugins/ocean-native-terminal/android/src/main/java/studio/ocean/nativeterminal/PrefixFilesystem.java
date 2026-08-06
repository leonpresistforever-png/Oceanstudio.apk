package studio.ocean.nativeterminal;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Sandboxed filesystem access under the Ocean Linux prefix (home + workspace).
 */
public class PrefixFilesystem {
    private static final long MAX_READ_BYTES = 5 * 1024 * 1024;

    private final File prefixDir;
    private final File homeDir;

    public PrefixFilesystem(OceanLinuxSetup setup) {
        this.prefixDir = setup.getPrefixDir();
        this.homeDir = setup.getHomeDir();
    }

    public String getHomePath() {
        return homeDir.getAbsolutePath();
    }

    public JSArray readDir(String path) throws IOException {
        File dir = resolve(path);
        if (!dir.exists()) {
            dir.mkdirs();
            return new JSArray();
        }
        if (!dir.isDirectory()) {
            throw new IOException("Not a directory: " + path);
        }

        File[] entries = dir.listFiles();
        if (entries == null) return new JSArray();

        Arrays.sort(entries, Comparator
            .comparing((File f) -> !f.isDirectory())
            .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        JSArray result = new JSArray();
        for (File entry : entries) {
            String name = entry.getName();
            if (name.startsWith(".") && !name.equals(".env")) continue;
            JSObject node = new JSObject();
            node.put("name", name);
            node.put("path", entry.getAbsolutePath());
            node.put("type", entry.isDirectory() ? "directory" : "file");
            result.put(node);
        }
        return result;
    }

    public String readFile(String path) throws IOException {
        File file = resolve(path);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found: " + path);
        }
        if (file.length() > MAX_READ_BYTES) {
            throw new IOException("File too large to open");
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        }
        return sb.toString();
    }

    public void writeFile(String path, String content) throws IOException {
        File file = resolve(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStreamWriter writer = new OutputStreamWriter(
            new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private File resolve(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            return homeDir;
        }
        File target = new File(path);
        if (!target.isAbsolute()) {
            target = new File(homeDir, path);
        }
        String canonical = target.getCanonicalPath();
        String prefixCanonical = prefixDir.getCanonicalPath();
        if (!canonical.startsWith(prefixCanonical)) {
            throw new IOException("Access denied outside Ocean prefix");
        }
        return new File(canonical);
    }
}
