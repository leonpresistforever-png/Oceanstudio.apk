package studio.ocean.app.terminal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Repairs host shebangs on extracted or installed prefix scripts (e.g. omniroute, npm bins). */
final class OceanShebangRepair {
    private OceanShebangRepair() {}

    static void ensurePrefixScripts(File prefix) {
        if (prefix == null || !prefix.isDirectory()) return;
        installEnvShim(prefix);
        repairDirectory(new File(prefix, "bin"));
        repairDirectory(new File(prefix, "lib/node_modules/.bin"));
    }

    private static void installEnvShim(File prefix) {
        File env = new File(prefix, "bin/env");
        File bash = new File(prefix, "bin/bash");
        if (!bash.isFile()) return;
        String script = "#!" + bash.getAbsolutePath() + "\n"
                + "# OceanStudio minimal /usr/bin/env replacement\n"
                + "if [[ $# -lt 1 ]]; then echo \"env: missing utility\" >&2; exit 127; fi\n"
                + "name=\"$1\"; shift\n"
                + "if [[ \"$name\" == */* ]]; then exec \"$name\" \"$@\"; fi\n"
                + "if command -v \"$name\" >/dev/null 2>&1; then exec \"$name\" \"$@\"; fi\n"
                + "echo \"env: $name: No such file or directory\" >&2; exit 127\n";
        writeIfChanged(env, script.getBytes(StandardCharsets.UTF_8));
    }

    private static void repairDirectory(File directory) {
        if (!directory.isDirectory()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) repairDirectory(child);
            else repairFile(child);
        }
    }

    private static void repairFile(File file) {
        if (!file.isFile() || file.length() > 2_000_000) return;
        try {
            byte[] original = read(file);
            if (original.length < 2 || original[0] != '#' || original[1] != '!') return;
            byte[] rewritten = OceanShebangPolicy.rewriteIfNeeded(original);
            if (rewritten != original) writeIfChanged(file, rewritten);
        } catch (IOException ignored) { }
    }

    private static byte[] read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), 65536)];
            int count = input.read(buffer);
            if (count <= 0) return new byte[0];
            if (count == buffer.length) return buffer;
            byte[] exact = new byte[count];
            System.arraycopy(buffer, 0, exact, 0, count);
            return exact;
        }
    }

    private static void writeIfChanged(File file, byte[] bytes) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
            File temporary = File.createTempFile("ocean-shebang-", ".tmp", parent);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }
            if (!temporary.setExecutable(true, false) || !temporary.renameTo(file)) {
                temporary.delete();
            }
        } catch (IOException ignored) { }
    }
}
