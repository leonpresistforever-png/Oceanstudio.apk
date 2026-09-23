package studio.ocean.app.terminal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/** Repairs an empty APT catalogue without replacing the runtime or installed packages. */
public final class OceanPackageCatalog {
    public interface Assets { InputStream open(String name) throws IOException; }
    private OceanPackageCatalog() {}

    public static boolean prepare(File prefix, Assets assets) throws IOException {
        Properties metadata = new Properties();
        try (InputStream input = assets.open("catalog.properties")) { metadata.load(input); }
        String repository = required(metadata, "repository_url");
        String listPrefix = required(metadata, "list_prefix");
        if (!"1".equals(metadata.getProperty("format")) || !repository.startsWith("https://")
                || repository.contains("\n") || repository.contains("\r") || repository.contains(" ")
                || !listPrefix.matches("[A-Za-z0-9_.%:-]+_dists_stable_")) {
            throw new IOException("Invalid bundled package catalogue configuration");
        }
        File sources = new File(prefix, "etc/apt/sources.list.d/ocean.list");
        if (sources.isFile()) {
            String current = new String(Files.readAllBytes(sources.toPath()), StandardCharsets.UTF_8);
            // A deliberate repository change belongs to the user.
            if (!current.trim().isEmpty() && !current.contains(repository + " ")) return false;
        }
        File lists = new File(prefix, "var/lib/apt/lists");
        Files.createDirectories(new File(lists, "partial").toPath());
        String indexName = listPrefix + "main_binary-aarch64_Packages";
        long minLength = 0;
        try { minLength = Long.parseLong(metadata.getProperty("packages_length", "0")); } catch (Exception ignored) {}
        if (hasIndex(lists, indexName, minLength)) return false;
        try (FileChannel channel = FileChannel.open(new File(lists, "lock").toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException busy) { return false; }
            if (lock == null) return false;
            try {
                if (hasIndex(lists, indexName, minLength)) return false;
                byte[] compressed = verified(assets, metadata, "Packages.gz.bin");
                byte[] release = verified(assets, metadata, "InRelease");
                byte[] key = verified(assets, metadata, "ocean.gpg");
                byte[] packages;
                try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                    packages = read(input);
                }
                checkHash(packages, required(metadata, "packages_sha256"));
                // Verify every asset before changing any existing installation.
                File keyring = new File(prefix, "etc/apt/keyrings/ocean.gpg");
                if (!keyring.isFile()) atomicWrite(keyring, key);
                if (!sources.isFile() || sources.length() == 0) {
                    String source = "deb [signed-by=" + keyring.getAbsolutePath() + "] " + repository + " stable main\n";
                    atomicWrite(sources, source.getBytes(StandardCharsets.UTF_8));
                }
                atomicWrite(new File(lists, listPrefix + "InRelease"), release);
                atomicWrite(new File(lists, indexName), packages);
                // Installation time must not make an old snapshot look fresh.
                if (!new File(lists, indexName).setLastModified(0))
                    throw new IOException("Cannot retain bundled catalogue age");
                return true;
            } finally { lock.release(); }
        }
    }

    private static boolean hasIndex(File lists, String name, long minLength) {
        for (String suffix : new String[]{".lz4", ".gz", ".xz"}) {
            File file = new File(lists, name + suffix);
            if (file.isFile() && file.length() > 0) return true;
        }
        File uncompressed = new File(lists, name);
        if (uncompressed.isFile()) {
            if (minLength > 0 && uncompressed.lastModified() == 0L && uncompressed.length() < minLength) {
                return false;
            }
            return uncompressed.length() > 0;
        }
        return false;
    }
    private static byte[] verified(Assets assets, Properties metadata, String name) throws IOException {
        byte[] bytes;
        try (InputStream input = assets.open(name)) { bytes = read(input); }
        checkHash(bytes, required(metadata, name + "_sha256"));
        return bytes;
    }
    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
        return output.toByteArray();
    }
    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) throw new IOException("Catalogue is missing " + key);
        return value;
    }
    private static void checkHash(byte[] value, String expected) throws IOException {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b & 255));
            if (!hex.toString().equals(expected)) throw new IOException("Package catalogue integrity check failed");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
    private static void atomicWrite(File target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParentFile().toPath());
        File temporary = File.createTempFile(".ocean-catalog-", ".tmp", target.getParentFile());
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(bytes); out.getFD().sync();
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }
}
