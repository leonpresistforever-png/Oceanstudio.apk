package studio.ocean.app.terminal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Repairs script interpreters while preserving symlinks and complete file contents. */
final class OceanShebangRepair {
    private OceanShebangRepair() {}

    static void ensurePrefixScripts(File prefix) {
        if (prefix == null || !prefix.isDirectory()) return;
        installEnvShim(prefix);
        repairRelocatedNpm(prefix);
        repairDirectory(new File(prefix, "bin"), prefix);
        repairDirectory(new File(prefix, "lib/node_modules/.bin"), prefix);
    }

    private static void repairRelocatedNpm(File prefix) {
        for (String command : new String[]{"npm", "npx"}) {
            File launcher = new File(prefix, "bin/" + command);
            File canonical = new File(prefix, "lib/node_modules/npm/bin/" + command + "-cli.js");
            if (Files.isSymbolicLink(launcher.toPath()) || !launcher.isFile() || !canonical.isFile()
                    || launcher.length() > 65536) continue;
            try {
                String source = new String(read(launcher), StandardCharsets.UTF_8);
                boolean relocated = "npm".equals(command) ? source.contains("require('../lib/cli.js')")
                        : source.contains("require('./npm-cli.js')");
                if (!relocated) continue;
                String node = new File(prefix, "bin/node").getAbsolutePath();
                // Repair only the known copied upstream entry point. Keep the npm
                // tree, valid symlinks and any other launchers intact.
                String shell = "#!/system/bin/sh\nexec '" + node.replace("'", "'\\''") + "' '"
                        + canonical.getAbsolutePath().replace("'", "'\\''") + "' \"$@\"\n";
                writeIfChanged(launcher, shell.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) { }
        }
    }

    private static void installEnvShim(File prefix) {
        File env = new File(prefix, "bin/env");
        File bash = new File(prefix, "bin/bash");
        if (env.exists()) return;
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

    private static void repairDirectory(File directory, File prefix) {
        if (!directory.isDirectory()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            // Atomic replacement of a link would relocate the script. npm's
            // relative imports must resolve beside npm-cli.js, not beside bin/npm.
            if (Files.isSymbolicLink(child.toPath())) {
                // Repair the canonical file within our prefix, keeping the link
                // and relative module imports intact. Never follow external links.
                try {
                    File target = child.getCanonicalFile();
                    if (target.isFile() && target.getPath().startsWith(prefix.getCanonicalPath() + File.separator))
                        repairFile(target);
                } catch (IOException ignored) { }
                continue;
            }
            if (child.isDirectory()) repairDirectory(child, prefix);
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
        return Files.readAllBytes(file.toPath());
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
