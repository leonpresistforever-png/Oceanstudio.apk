package studio.ocean.app.terminal;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import studio.ocean.app.OceanPaths;

/** Stable launchers outside npm's writable global prefix survive npm self-updates. */
final class OceanRuntimeCompatibility {
    private static final String MARKER = "# OceanStudio managed npm launcher\n";

    private OceanRuntimeCompatibility() {}

    static void ensure(Context context) {
        OceanPaths paths = new OceanPaths(context);
        File modules = new File(paths.prefix(), "lib/node_modules/npm/bin");
        File overlay = new File(paths.home(), ".local/bin");
        if (!overlay.isDirectory() && !overlay.mkdirs()) return;
        installBundledMediaTools(context, overlay, paths.home());
        OceanShebangRepair.ensurePrefixScripts(paths.prefix());
        if (!new File(paths.prefix(), "bin/node").isFile()) return;
        for (String[] entry : new String[][]{{"npm", "npm-cli.js"}, {"npx", "npx-cli.js"}}) {
            if (!new File(modules, entry[1]).isFile()) continue;
            File launcher = new File(overlay, entry[0]);
            try {
                if (launcher.exists() && !isManaged(launcher)) continue;
                String script = "#!" + paths.prefix() + "/bin/bash\n" + MARKER
                        + "exec \"$PREFIX/bin/node\" \"$PREFIX/lib/node_modules/npm/bin/"
                        + entry[1] + "\" \"$@\"\n";
                byte[] bytes = script.getBytes(StandardCharsets.UTF_8);
                if (launcher.isFile() && launcher.length() == bytes.length && isManaged(launcher)) continue;
                File temporary = File.createTempFile("ocean-npm-", ".tmp", overlay);
                try {
                    try (FileOutputStream output = new FileOutputStream(temporary)) {
                        output.write(bytes);
                        output.getFD().sync();
                    }
                    if (!temporary.setExecutable(true, true) || !temporary.renameTo(launcher))
                        throw new IOException("Could not activate " + entry[0] + " launcher");
                } finally { if (temporary.exists()) temporary.delete(); }
            } catch (IOException error) {
                android.util.Log.w("OceanRuntime", "Could not repair npm launcher", error);
            }
        }
    }

    private static void installBundledMediaTools(Context context, File bin, File home) {
        File library = new File(home, ".local/lib");
        if (!library.isDirectory() && !library.mkdirs()) return;
        for (String tool : new String[]{"ffmpeg", "ffprobe"}) {
            installAsset(context, "ocean/native/" + tool, new File(bin, tool));
        }
        try (InputStream input = context.getAssets().open("ocean/native/libraries.sha256")) {
            String manifest = new String(readAll(input), StandardCharsets.US_ASCII);
            for (String line : manifest.split("\n")) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length != 2 || !fields[1].matches("lib/lib[a-zA-Z0-9+_.-]+\\.so(?:\\.[0-9]+)?")) continue;
                installAsset(context, "ocean/native/" + fields[1],
                        new File(library, fields[1].substring(4)));
            }
        } catch (IOException missing) {
            // Source-only and older APKs have no bundled Android FFmpeg build.
        }
    }

    private static void installAsset(Context context, String asset, File destination) {
        try {
            String manifest;
            if (asset.startsWith("ocean/native/lib/")) {
                try (InputStream input = context.getAssets().open("ocean/native/libraries.sha256")) {
                    manifest = new String(readAll(input), StandardCharsets.US_ASCII);
                }
            } else {
                try (InputStream input = context.getAssets().open(asset + ".sha256")) {
                    manifest = new String(readAll(input), StandardCharsets.US_ASCII);
                }
            }
            String name = asset.substring("ocean/native/".length());
            String expected = null;
            for (String row : manifest.split("\n")) {
                String[] fields = row.trim().split("\\s+");
                if (fields.length == 2 && fields[1].equals(name) && fields[0].matches("[0-9a-f]{64}"))
                    expected = fields[0];
            }
            if (expected == null) return;
            File marker = new File(destination.getParentFile(), "." + destination.getName() + ".ocean.sha256");
            if (destination.exists() && !marker.exists()) return; // Preserve a user-managed executable.
            if (destination.exists() && marker.isFile()
                    && expected.equals(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.US_ASCII).trim()))
                return;
            File temporary = File.createTempFile("ocean-media-", ".tmp", destination.getParentFile());
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = context.getAssets().open(asset);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] bytes = new byte[32768];
                    for (int length; (length = input.read(bytes)) != -1;) {
                        digest.update(bytes, 0, length);
                        output.write(bytes, 0, length);
                    }
                    output.getFD().sync();
                }
                StringBuilder actual = new StringBuilder(64);
                for (byte b : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
                if (!expected.equals(actual.toString()) || !temporary.setExecutable(true, true)
                        || !temporary.renameTo(destination))
                    throw new IOException("Android media tool validation failed: " + name);
                Files.write(marker.toPath(), expected.getBytes(StandardCharsets.US_ASCII));
            } finally { if (temporary.exists()) temporary.delete(); }
        } catch (Exception error) {
            android.util.Log.w("OceanRuntime", "Could not stage " + asset, error);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        for (int length; (length = input.read(chunk)) != -1;) bytes.write(chunk, 0, length);
        return bytes.toByteArray();
    }

    private static boolean isManaged(File file) throws IOException {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            byte[] head = new byte[(int) Math.min(256, file.length())];
            int count = input.read(head);
            return count > 0 && new String(head, 0, count, StandardCharsets.UTF_8).contains(MARKER.trim());
        }
    }
}
