package studio.ocean.app.terminal;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;

/** Migrate only byte-identical released Ocean frontends; preserve user changes. */
public final class OceanPackageFrontend {
    private static final String LEGACY_SHA256 =
            "6e9b34f5b87d78e0cf393b4cea91f2ad90be752f5e26e9e069d89fd0a2daa33f";
    private static final String AUTO_SYNC_SHA256 =
            "62a376a77d995f93a69a0eb7e0a815b330851262fea3831a9dad70f09e96b1a2";
    private OceanPackageFrontend() {}

    public static boolean prepare(File prefix, InputStream bundled) throws IOException {
        byte[] replacement;
        try (InputStream input = bundled; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) != -1;) bytes.write(buffer, 0, n);
            replacement = bytes.toByteArray();
        }
        Path target = new File(prefix, "bin/pkg").toPath();
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || new File(prefix, "var/run/ocean-pkg.lock.d").exists()) return false;
        Path dpkg = new File(prefix, "var/lib/dpkg").toPath();
        Files.createDirectories(dpkg);
        try (FileChannel frontend = FileChannel.open(dpkg.resolve("lock-frontend"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileChannel database = FileChannel.open(dpkg.resolve("lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock first = tryLock(frontend);
            if (first == null) return false;
            try {
                FileLock second = tryLock(database);
                if (second == null) return false;
                try { return replaceKnown(target, dpkg, replacement); }
                finally { second.release(); }
            } finally { first.release(); }
        }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try { return channel.tryLock(); }
        catch (OverlappingFileLockException busy) { return null; }
    }

    private static boolean replaceKnown(Path target, Path dpkg, byte[] replacement) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return false;
        byte[] original = Files.readAllBytes(target);
        String digest = sha256(original);
        if (java.util.Arrays.equals(original, replacement)
                || !(LEGACY_SHA256.equals(digest) || AUTO_SYNC_SHA256.equals(digest))) return false;
        Path backup = dpkg.resolve(LEGACY_SHA256.equals(digest)
                ? "ocean-pkg-1.1.0.backup" : "ocean-pkg-auto-sync.backup");
        if (!Files.exists(backup)) Files.copy(target, backup);
        File temporary = File.createTempFile(".ocean-pkg-", ".tmp", target.getParent().toFile());
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(replacement); out.getFD().sync();
            }
            if (!temporary.setExecutable(true, false)) throw new IOException("Cannot make pkg executable");
            Files.move(temporary.toPath(), target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
        return true;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hex.append(String.format("%02x", b & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
}
