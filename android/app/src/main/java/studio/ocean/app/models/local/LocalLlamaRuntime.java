package studio.ocean.app.models.local;

import android.content.Context;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Installs and resolves the real upstream llama.cpp Android arm64 runtime.
 *
 * Security properties:
 * - pinned upstream release + SHA-256 verification
 * - extraction confined under app-private storage
 * - no mock/fake runtime state: callers only receive a RuntimePaths object
 *   after the verified llama-server binary exists on disk
 */
public final class LocalLlamaRuntime {
    private static final String VERSION = "b11080";
    private static final String ARCHIVE_NAME = "llama-" + VERSION + "-bin-android-arm64.tar.gz";
    private static final String DOWNLOAD_URL =
            "https://github.com/ggml-org/llama.cpp/releases/download/" + VERSION + "/" + ARCHIVE_NAME;
    // GitHub artifact attestation for llama-b11080-bin-android-arm64.tar.gz.
    private static final String SHA256 =
            "98cbbebc1c0ecc992114b77f06fcf67d62b0bca833c1a81754c4d7778e6056b1";

    public static final class RuntimePaths {
        public final File root;
        public final File server;
        public final File libDir;

        RuntimePaths(File root, File server, File libDir) {
            this.root = root;
            this.server = server;
            this.libDir = libDir;
        }
    }

    private final Context context;
    private final File runtimeRoot;
    private final File archiveFile;

    public LocalLlamaRuntime(Context context) {
        this.context = context.getApplicationContext();
        this.runtimeRoot = new File(this.context.getFilesDir(), "runtime/llama.cpp/" + VERSION);
        this.archiveFile = new File(this.context.getCacheDir(), ARCHIVE_NAME);
    }

    public synchronized RuntimePaths ensureInstalled() throws Exception {
        RuntimePaths existing = resolveInstalled();
        File marker = new File(runtimeRoot, ".verified-sha256");
        if (existing != null && marker.isFile()) {
            String saved = readSmallFile(marker).trim();
            if (SHA256.equalsIgnoreCase(saved)) return existing;
        }

        if (!archiveFile.isFile() || !SHA256.equalsIgnoreCase(sha256(archiveFile))) {
            if (archiveFile.exists() && !archiveFile.delete()) {
                throw new IOException("Could not replace an invalid local llama.cpp runtime archive.");
            }
            downloadArchive();
        }

        String digest = sha256(archiveFile);
        if (!SHA256.equalsIgnoreCase(digest)) {
            if (archiveFile.exists()) archiveFile.delete();
            throw new SecurityException("llama.cpp runtime integrity verification failed. Expected "
                    + SHA256 + " but downloaded " + digest);
        }

        File staging = new File(runtimeRoot.getParentFile(), VERSION + ".staging");
        deleteRecursive(staging);
        if (!staging.mkdirs() && !staging.isDirectory()) {
            throw new IOException("Could not create runtime staging directory.");
        }

        try {
            extractTarGz(archiveFile, staging);
            File server = findByName(staging, "llama-server");
            if (server == null || !server.isFile()) {
                throw new IOException("Verified llama.cpp archive did not contain llama-server.");
            }
            if (!server.setExecutable(true, true) && !server.canExecute()) {
                throw new IOException("llama-server could not be marked executable.");
            }

            deleteRecursive(runtimeRoot);
            File parent = runtimeRoot.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!staging.renameTo(runtimeRoot)) {
                copyTree(staging, runtimeRoot);
                deleteRecursive(staging);
            }

            RuntimePaths installed = resolveInstalled();
            if (installed == null) throw new IOException("llama.cpp runtime extraction completed but runtime paths were not found.");
            writeSmallFile(new File(runtimeRoot, ".verified-sha256"), SHA256 + "\n");
            return installed;
        } catch (Exception e) {
            deleteRecursive(staging);
            throw e;
        }
    }

    public RuntimePaths resolveInstalled() {
        if (!runtimeRoot.isDirectory()) return null;
        File server = findByName(runtimeRoot, "llama-server");
        if (server == null || !server.isFile()) return null;
        File lib = findDirectoryByName(runtimeRoot, "lib");
        if (lib == null) lib = runtimeRoot;
        return new RuntimePaths(runtimeRoot, server, lib);
    }

    private void downloadArchive() throws Exception {
        HttpURLConnection conn = null;
        File partial = new File(archiveFile.getAbsolutePath() + ".partial");
        if (partial.exists()) partial.delete();
        try {
            URL url = new URL(DOWNLOAD_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "OceanStudio/1.4.0 Android arm64");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("llama.cpp runtime download failed: HTTP " + code + " " + conn.getResponseMessage());
            }
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.getFD().sync();
            }
            if (!partial.renameTo(archiveFile)) {
                copyFile(partial, archiveFile);
                partial.delete();
            }
        } finally {
            if (conn != null) conn.disconnect();
            if (partial.exists()) partial.delete();
        }
    }

    private static void extractTarGz(File archive, File destination) throws Exception {
        String destinationCanonical = destination.getCanonicalPath() + File.separator;
        try (InputStream fis = new BufferedInputStream(new FileInputStream(archive));
             GzipCompressorInputStream gzip = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.isEmpty()) continue;

                File out = new File(destination, name);
                String canonical = out.getCanonicalPath();
                if (!canonical.equals(destination.getCanonicalPath())
                        && !canonical.startsWith(destinationCanonical)) {
                    throw new SecurityException("Blocked unsafe runtime archive path: " + name);
                }

                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("Could not create " + out);
                    continue;
                }

                if (entry.isSymbolicLink()) {
                    // Resolve archive symlinks after extraction by copying the link target.
                    File linkTarget = new File(out.getParentFile(), entry.getLinkName());
                    if (linkTarget.isFile()) copyFile(linkTarget, out);
                    continue;
                }

                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create " + parent);
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buffer = new byte[64 * 1024];
                    long remaining = entry.getSize();
                    while (remaining > 0) {
                        int read = tar.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read < 0) throw new EOFException("Unexpected end of llama.cpp runtime archive.");
                        fos.write(buffer, 0, read);
                        remaining -= read;
                    }
                    fos.getFD().sync();
                }
                if ((entry.getMode() & 0111) != 0 || out.getName().startsWith("llama-")) {
                    out.setExecutable(true, true);
                }
            }
        }

        // Some release archives encode shared-library symlinks before their target files.
        // Repair those by matching the common versioned lib*.so.* files to missing lib*.so names.
        Deque<File> stack = new ArrayDeque<>();
        stack.push(destination);
        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File f : children) if (f.isDirectory()) stack.push(f);
            for (File f : children) {
                if (!f.isFile()) continue;
                String n = f.getName();
                int so = n.indexOf(".so.");
                if (so > 0) {
                    File plain = new File(f.getParentFile(), n.substring(0, so + 3));
                    if (!plain.exists()) copyFile(f, plain);
                }
            }
        }
    }

    private static File findByName(File root, String fileName) {
        if (root == null || !root.exists()) return null;
        Deque<File> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            File f = stack.pop();
            if (f.isFile() && fileName.equals(f.getName())) return f;
            File[] children = f.listFiles();
            if (children != null) for (File child : children) stack.push(child);
        }
        return null;
    }

    private static File findDirectoryByName(File root, String name) {
        if (root == null || !root.exists()) return null;
        Deque<File> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            File f = stack.pop();
            if (f.isDirectory() && name.equals(f.getName())) return f;
            File[] children = f.listFiles();
            if (children != null) for (File child : children) if (child.isDirectory()) stack.push(child);
        }
        return null;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(java.util.Locale.US, "%02x", b));
        return sb.toString();
    }

    private static void copyTree(File from, File to) throws IOException {
        if (from.isDirectory()) {
            if (!to.exists() && !to.mkdirs()) throw new IOException("Could not create " + to);
            File[] children = from.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(to, child.getName()));
        } else {
            copyFile(from, to);
            if (from.canExecute()) to.setExecutable(true, true);
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = new BufferedInputStream(new FileInputStream(from));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(to))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }

    private static String readSmallFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeSmallFile(File file, String value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }
}
