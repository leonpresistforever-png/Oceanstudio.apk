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
    // Exact bundled September 6 snapshot. Never replace an unknown APT/user index.
    private static final String LEGACY_INDEX_SHA256 =
            "b295347a9a330727be05529d08c6e90259091f8a7da7b95a5184722be47acb82";
    private static final String PREVIOUS_6482_INDEX_SHA256 =
            "b5a3ada5f67b4c2a1f4961fc0da20cbae359b0de2e2c38cc68c8561c24df04b5";
    private static final String PREVIOUS_6483_INDEX_SHA256 =
            "e58470cf9e68f4934bee8d5090abe2c67ea770e963f2472516c3868e501d567a";
    private static final String PREVIOUS_6501_INDEX_SHA256 =
            "a465eb5aa9bd5e0e142db24cf7c4437b7295f1e03777a72a685bac9d8d4b6e86";
    private static final String PREVIOUS_ARCHIVE_KEY_SHA256 =
            "badf3406f3ba399c01d47589c48b47b4714dcaa8e146a5646581254042a4678e";
    private static final String[] MANAGED_KEYRINGS = {
            "etc/apt/keyrings/ocean.gpg", "etc/apt/trusted.gpg.d/ocean.gpg"
    };
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
        String bundledHash = required(metadata, "packages_sha256");
        // Index ownership and archive-key migration are independent. A current
        // compressed APT list must not prevent rotation of a known bundled key.
        if (hasIndex(lists, indexName, bundledHash) && !hasPreviousKey(prefix)) return false;
        try (FileChannel channel = FileChannel.open(new File(lists, "lock").toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException busy) { return false; }
            if (lock == null) return false;
            try {
                if (hasIndex(lists, indexName, bundledHash)) {
                    if (!hasPreviousKey(prefix)) return false;
                    return migratePreviousKeys(prefix, verified(assets, metadata, "ocean.gpg"));
                }
                byte[] compressed = verified(assets, metadata, "Packages.gz.bin");
                byte[] release = verified(assets, metadata, "InRelease");
                byte[] key = verified(assets, metadata, "ocean.gpg");
                byte[] packages;
                try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                    packages = read(input);
                }
                checkHash(packages, required(metadata, "packages_sha256"));
                checkSignedIndex(release, compressed, packages);
                // Verify every asset before changing any existing installation.
                File keyring = new File(prefix, "etc/apt/keyrings/ocean.gpg");
                if (Files.isSymbolicLink(keyring.toPath()))
                    throw new IOException("Preserving a user-managed Ocean keyring symlink");
                if (keyring.isFile()) {
                    String currentHash = hash(Files.readAllBytes(keyring.toPath()));
                    if (!currentHash.equals(required(metadata, "ocean.gpg_sha256"))
                            && !currentHash.equals(PREVIOUS_ARCHIVE_KEY_SHA256))
                        throw new IOException("Preserving an unknown Ocean repository keyring");
                }
                migratePreviousKeys(prefix, key);
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

    private static boolean hasPreviousKey(File prefix) throws IOException {
        for (String path : MANAGED_KEYRINGS) {
            File keyring = new File(prefix, path);
            if (!Files.isSymbolicLink(keyring.toPath()) && keyring.isFile()
                    && PREVIOUS_ARCHIVE_KEY_SHA256.equals(hash(Files.readAllBytes(keyring.toPath()))))
                return true;
        }
        return false;
    }

    private static boolean migratePreviousKeys(File prefix, byte[] replacement) throws IOException {
        if (PREVIOUS_ARCHIVE_KEY_SHA256.equals(hash(replacement))) return false;
        boolean changed = false;
        for (String path : MANAGED_KEYRINGS) {
            File keyring = new File(prefix, path);
            if (Files.isSymbolicLink(keyring.toPath()) || !keyring.isFile()) continue;
            byte[] previous = Files.readAllBytes(keyring.toPath());
            if (!PREVIOUS_ARCHIVE_KEY_SHA256.equals(hash(previous))) continue;
            // Keep the exact previous public key for diagnosis/rollback. Never
            // overwrite a user-owned backup or an unknown/customized keyring.
            File backup = new File(keyring.getParentFile(), "ocean.gpg.before-archive-key-rotation");
            if (backup.exists()) {
                if (Files.isSymbolicLink(backup.toPath())
                        || !PREVIOUS_ARCHIVE_KEY_SHA256.equals(hash(Files.readAllBytes(backup.toPath()))))
                    throw new IOException("Preserving an existing archive-key backup");
            } else atomicWrite(backup, previous);
            atomicWrite(keyring, replacement);
            changed = true;
        }
        return changed;
    }

    private static boolean hasIndex(File lists, String name, String bundledHash) throws IOException {
        for (String suffix : new String[]{"", ".lz4", ".gz", ".xz"}) {
            File file = new File(lists, name + suffix);
            if (file.isFile() && file.length() > 0) {
                if (suffix.isEmpty() && !LEGACY_INDEX_SHA256.equals(bundledHash)
                        && file.length() == 509608
                        && LEGACY_INDEX_SHA256.equals(hash(Files.readAllBytes(file.toPath())))) continue;
                if (suffix.isEmpty() && !PREVIOUS_6482_INDEX_SHA256.equals(bundledHash)
                        && file.length() == 3781194
                        && PREVIOUS_6482_INDEX_SHA256.equals(hash(Files.readAllBytes(file.toPath())))) continue;
                if (suffix.isEmpty() && !PREVIOUS_6483_INDEX_SHA256.equals(bundledHash)
                        && file.length() == 3782118
                        && PREVIOUS_6483_INDEX_SHA256.equals(hash(Files.readAllBytes(file.toPath())))) continue;
                if (suffix.isEmpty() && !PREVIOUS_6501_INDEX_SHA256.equals(bundledHash)
                        && file.length() == 3971418
                        && PREVIOUS_6501_INDEX_SHA256.equals(hash(Files.readAllBytes(file.toPath())))) continue;
                return true;
            }
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
        if (!hash(value).equals(expected)) throw new IOException("Package catalogue integrity check failed");
    }
    private static String hash(byte[] value) throws IOException {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
    private static void checkSignedIndex(byte[] release, byte[] compressed, byte[] packages) throws IOException {
        // The build verifies the OpenPGP signature. Bind both installed assets to
        // that signed payload as well, so individually valid hashes cannot mix snapshots.
        String signed = new String(release, StandardCharsets.UTF_8);
        int start = signed.indexOf("\nSHA256:\n");
        int end = signed.indexOf("-----BEGIN PGP SIGNATURE-----");
        if (!signed.startsWith("-----BEGIN PGP SIGNED MESSAGE-----") || start < 0 || end <= start)
            throw new IOException("Bundled signed catalogue is malformed");
        String checksums = signed.substring(start + 9, end);
        for (int i = 0; i < 2; i++) {
            byte[] data = i == 0 ? packages : compressed;
            String name = "main/binary-aarch64/Packages" + (i == 0 ? "" : ".gz");
            boolean found = false;
            for (String line : checksums.split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 3 && parts[2].equals(name)) {
                    if (!parts[1].equals(Integer.toString(data.length)))
                        throw new IOException("Bundled signed catalogue size mismatch");
                    checkHash(data, parts[0]); found = true;
                }
            }
            if (!found) throw new IOException("Bundled signed catalogue is missing " + name);
        }
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
