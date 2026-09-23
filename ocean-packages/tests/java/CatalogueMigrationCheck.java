package studio.ocean.app.terminal;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Host regression checks using the exact shipped catalogue and isolated directories. */
public final class CatalogueMigrationCheck {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
    static Map<String, byte[]> load(Path directory) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        for (String name : new String[]{"catalog.properties", "Packages.gz.bin", "InRelease", "ocean.gpg"})
            files.put(name, Files.readAllBytes(directory.resolve(name)));
        return files;
    }
    static Properties metadata(Map<String, byte[]> files) throws Exception {
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(files.get("catalog.properties")));
        return properties;
    }
    static boolean prepare(Path prefix, Map<String, byte[]> files) throws Exception {
        return OceanPackageCatalog.prepare(prefix.toFile(), name -> new ByteArrayInputStream(files.get(name)));
    }
    static void write(Path path, byte[] value) throws Exception {
        Files.createDirectories(path.getParent()); Files.write(path, value);
    }
    public static void main(String[] args) throws Exception {
        Map<String, byte[]> original = load(Path.of(args[0]));
        String index = metadata(original).getProperty("list_prefix") + "main_binary-aarch64_Packages";
        Path root = Files.createTempDirectory("ocean-catalog-check-");
        Path prefix = root.resolve("empty/usr");
        Path status = prefix.resolve("var/lib/dpkg/status");
        write(status, "Package: user-installed\nStatus: install ok installed\n".getBytes(StandardCharsets.UTF_8));
        check(prepare(prefix, original), "empty cache must be restored");
        check(!prepare(prefix, original), "unchanged catalogue must be idempotent");
        check(Files.readString(status).contains("user-installed"), "installed packages must be preserved");

        Map<String, byte[]> updated = new HashMap<>(original);
        byte[] packages = "Package: ocean-test-fixture\nVersion: 1\nArchitecture: all\n\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(packages); }
        byte[] compressed = bytes.toByteArray();
        // Deliberately non-cryptographic fixture: Java checks snapshot consistency;
        // the production hydrator separately verifies real OpenPGP signatures.
        String signed = "-----BEGIN PGP SIGNED MESSAGE-----\nHash: SHA256\n\nSHA256:\n "
                + hash(packages) + " " + packages.length + " main/binary-aarch64/Packages\n "
                + hash(compressed) + " " + compressed.length + " main/binary-aarch64/Packages.gz\n"
                + "-----BEGIN PGP SIGNATURE-----\nUNIT TEST ONLY\n";
        updated.put("Packages.gz.bin", compressed);
        updated.put("InRelease", signed.getBytes(StandardCharsets.UTF_8));
        Properties props = metadata(original);
        props.setProperty("packages_sha256", hash(packages));
        for (String name : new String[]{"Packages.gz.bin", "InRelease", "ocean.gpg"})
            props.setProperty(name + "_sha256", hash(updated.get(name)));
        bytes.reset(); props.store(bytes, "test fixture"); updated.put("catalog.properties", bytes.toByteArray());
        check(prepare(prefix, updated), "known legacy cache must upgrade");
        check(Arrays.equals(packages, Files.readAllBytes(prefix.resolve("var/lib/apt/lists/" + index))), "new catalogue installed");
        check(!prepare(prefix, updated), "new catalogue preserved on next launch");

        Path unknown = root.resolve("unknown/usr");
        Path custom = unknown.resolve("var/lib/apt/lists/" + index);
        write(custom, "user or newer APT catalogue".getBytes(StandardCharsets.UTF_8));
        check(!prepare(unknown, updated), "unknown cache must survive");
        check(Files.readString(custom).equals("user or newer APT catalogue"), "unknown bytes changed");

        Map<String, byte[]> mixed = new HashMap<>(updated);
        mixed.put("InRelease", original.get("InRelease"));
        props.setProperty("InRelease_sha256", hash(original.get("InRelease")));
        bytes.reset(); props.store(bytes, "mixed fixture"); mixed.put("catalog.properties", bytes.toByteArray());
        Path broken = root.resolve("mixed/usr");
        try { prepare(broken, mixed); throw new AssertionError("mixed signed/index snapshot accepted"); }
        catch (IOException expected) { check(!Files.exists(broken.resolve("var/lib/apt/lists/" + index)), "bad index persisted"); }
        System.out.println("PASS: seed, idempotence, installed database preservation, known-cache upgrade, unknown-cache preservation, mixed-snapshot rejection");
    }
}
