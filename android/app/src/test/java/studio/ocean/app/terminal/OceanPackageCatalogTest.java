package studio.ocean.app.terminal;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.Base64;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class OceanPackageCatalogTest {
    // Public archive key shipped in the older APK, pinned byte-for-byte.
    private byte[] previousKey() {
        return Base64.getDecoder().decode("mDMEaplDXhYJKwYBBAHaRw8BAQdAiaW+GifgnKKIx/fo36zIPX8VMxJdBC4YokWHlPd6OXC0LE9jZWFuIFBhY2thZ2UgQXJjaGl2ZSA8YXJjaGl2ZUBvY2Vhbi5zdHVkaW8+iK8EExYKAFcWIQQJ1F3SzcN71Pm8LEWOwVQxylVC4gUCaplDXhsUgAAAAAAEAA5tYW51MiwyLjUrMS4xMSwzLDICGwMFCwkIBwICIgIGFQoJCAsCBBYCAwECHgcCF4AACgkQjsFUMcpVQuLlNwEA8nptLvAyGlQMVnRx592YYLcQmn4DvJbDOsUnVNJfsoUA/ieZwNDvyjW4NG8GO0z0L/O0awmfz5ss19ltfC8Vrx0P");
    }
    private Path installPreviousKey(File prefix, String location) throws Exception {
        Path path = prefix.toPath().resolve(location);
        Files.createDirectories(path.getParent());
        Files.write(path, previousKey());
        return path;
    }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path assets() {
        Path root = Path.of(System.getProperty("user.dir"));
        Path path = root.resolve("src/main/assets/ocean/repository");
        return Files.isDirectory(path) ? path : root.resolve("app/src/main/assets/ocean/repository");
    }
    private OceanPackageCatalog.Assets source() { return name -> Files.newInputStream(assets().resolve(name)); }
    private String indexName() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = source().open("catalog.properties")) { properties.load(in); }
        return properties.getProperty("list_prefix") + "main_binary-aarch64_Packages";
    }
    private void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent()); Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }
    private String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    @Test public void emptyExistingRuntimeGetsRealCatalogueWithoutLosingInstalledPackages() throws Exception {
        File prefix = temporary.newFolder();
        Path status = prefix.toPath().resolve("var/lib/dpkg/status");
        write(status, "Package: user-installed-tool\nStatus: install ok installed\n");
        assertTrue(OceanPackageCatalog.prepare(prefix, source()));
        String packages = read(prefix.toPath().resolve("var/lib/apt/lists/" + indexName()));
        for (String name : new String[]{"pip", "npm", "proot", "proot-distro", "ocean-distro"}) {
            assertTrue(name, packages.contains("Package: " + name + "\n"));
        }
        assertTrue(read(status).contains("user-installed-tool"));
        assertFalse("second launch must leave APT state alone", OceanPackageCatalog.prepare(prefix, source()));
    }
    @Test public void aNewerCompressedAptIndexIsPreserved() throws Exception {
        File prefix = temporary.newFolder();
        Path existing = prefix.toPath().resolve("var/lib/apt/lists/" + indexName() + ".lz4");
        write(existing, "newer apt-owned data");
        assertFalse(OceanPackageCatalog.prepare(prefix, source()));
        assertEquals("newer apt-owned data", read(existing));
    }
    @Test public void userSelectedRepositoryIsPreserved() throws Exception {
        File prefix = temporary.newFolder();
        Path sources = prefix.toPath().resolve("etc/apt/sources.list.d/ocean.list");
        String configured = "deb https://packages.example.test/custom stable main\n";
        write(sources, configured);
        assertFalse(OceanPackageCatalog.prepare(prefix, source()));
        assertEquals(configured, read(sources));
    }
    @Test public void corruptedAssetsNeverBecomeAnInstalledCatalogue() throws Exception {
        File prefix = temporary.newFolder();
        OceanPackageCatalog.Assets corrupt = name -> name.equals("Packages.gz.bin")
                ? new ByteArrayInputStream(new byte[]{0, 1, 2}) : source().open(name);
        try { OceanPackageCatalog.prepare(prefix, corrupt); fail("corrupt catalogue accepted"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("integrity")); }
        assertFalse(new File(prefix, "etc/apt/sources.list.d/ocean.list").exists());
        assertFalse(new File(prefix, "var/lib/apt/lists/" + indexName()).exists());
    }
    @Test public void activeAptLockPreventsConcurrentCatalogueChanges() throws Exception {
        File prefix = temporary.newFolder();
        Path lockPath = prefix.toPath().resolve("var/lib/apt/lists/lock");
        Files.createDirectories(lockPath.getParent());
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            assertFalse(OceanPackageCatalog.prepare(prefix, source()));
            assertFalse(new File(prefix, "var/lib/apt/lists/" + indexName()).exists());
        }
    }
    @Test public void deletingTheCacheCanBeRepairedAgain() throws Exception {
        File prefix = temporary.newFolder();
        assertTrue(OceanPackageCatalog.prepare(prefix, source()));
        Files.delete(prefix.toPath().resolve("var/lib/apt/lists/" + indexName()));
        assertTrue(OceanPackageCatalog.prepare(prefix, source()));
    }
    @Test public void unknownZeroTimestampCatalogueIsPreserved() throws Exception {
        File prefix = temporary.newFolder();
        Path existing = prefix.toPath().resolve("var/lib/apt/lists/" + indexName());
        write(existing, "Package: user-owned-tool\n");
        assertTrue(existing.toFile().setLastModified(0));
        assertFalse(OceanPackageCatalog.prepare(prefix, source()));
        assertEquals("Package: user-owned-tool\n", read(existing));
    }

    @Test public void compressedIndexDoesNotBlockKnownArchiveKeyMigration() throws Exception {
        File prefix = temporary.newFolder();
        Path index = prefix.toPath().resolve("var/lib/apt/lists/" + indexName() + ".lz4");
        write(index, "preserved APT-owned compressed index");
        Path key = installPreviousKey(prefix, "etc/apt/keyrings/ocean.gpg");
        Path trusted = installPreviousKey(prefix, "etc/apt/trusted.gpg.d/ocean.gpg");
        assertTrue(OceanPackageCatalog.prepare(prefix, source()));
        assertEquals("preserved APT-owned compressed index", read(index));
        assertFalse(prefix.toPath().resolve("var/lib/apt/lists/" + indexName()).toFile().exists());
        assertArrayEquals(Files.readAllBytes(assets().resolve("ocean.gpg")), Files.readAllBytes(key));
        assertArrayEquals(Files.readAllBytes(key), Files.readAllBytes(trusted));
        assertArrayEquals(previousKey(), Files.readAllBytes(key.resolveSibling("ocean.gpg.before-archive-key-rotation")));
        assertFalse(OceanPackageCatalog.prepare(prefix, source()));
    }

    @Test public void existingRawIndexAndInstalledStateSurviveKeyMigration() throws Exception {
        File prefix = temporary.newFolder();
        Path index = prefix.toPath().resolve("var/lib/apt/lists/" + indexName());
        Path status = prefix.toPath().resolve("var/lib/dpkg/status");
        write(index, "Package: preserve-current-user-index\n");
        write(status, "Package: ocean-tools\nStatus: install ok installed\n");
        installPreviousKey(prefix, "etc/apt/keyrings/ocean.gpg");
        assertTrue(OceanPackageCatalog.prepare(prefix, source()));
        assertEquals("Package: preserve-current-user-index\n", read(index));
        assertEquals("Package: ocean-tools\nStatus: install ok installed\n", read(status));
    }

    @Test public void migrationRespectsActiveAptLock() throws Exception {
        File prefix = temporary.newFolder();
        Path key = installPreviousKey(prefix, "etc/apt/keyrings/ocean.gpg");
        Path index = prefix.toPath().resolve("var/lib/apt/lists/" + indexName() + ".lz4");
        write(index, "existing APT index");
        Path lockPath = index.getParent().resolve("lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            assertFalse(OceanPackageCatalog.prepare(prefix, source()));
            assertArrayEquals(previousKey(), Files.readAllBytes(key));
        }
    }

    @Test public void unknownKeyIsNeverReplacedByBootstrapRepair() throws Exception {
        File prefix = temporary.newFolder();
        Path key = prefix.toPath().resolve("etc/apt/keyrings/ocean.gpg");
        write(key, "user-managed repository key");
        try { OceanPackageCatalog.prepare(prefix, source()); fail("unknown key replaced"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("unknown")); }
        assertEquals("user-managed repository key", read(key));
        assertFalse(prefix.toPath().resolve("var/lib/apt/lists/" + indexName()).toFile().exists());
    }

    @Test public void keySymlinkIsPreserved() throws Exception {
        File prefix = temporary.newFolder();
        Path actual = temporary.newFile().toPath();
        Files.write(actual, previousKey());
        Path key = prefix.toPath().resolve("etc/apt/keyrings/ocean.gpg");
        Files.createDirectories(key.getParent()); Files.createSymbolicLink(key, actual);
        try { OceanPackageCatalog.prepare(prefix, source()); fail("key symlink replaced"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("symlink")); }
        assertTrue(Files.isSymbolicLink(key)); assertArrayEquals(previousKey(), Files.readAllBytes(actual));
    }

    @Test public void corruptReplacementKeyDoesNotModifyExistingTrust() throws Exception {
        File prefix = temporary.newFolder();
        Path key = installPreviousKey(prefix, "etc/apt/keyrings/ocean.gpg");
        write(prefix.toPath().resolve("var/lib/apt/lists/" + indexName() + ".lz4"), "preserve");
        OceanPackageCatalog.Assets corrupt = name -> name.equals("ocean.gpg")
                ? new ByteArrayInputStream(new byte[]{0, 1, 2}) : source().open(name);
        try { OceanPackageCatalog.prepare(prefix, corrupt); fail("corrupt key accepted"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("integrity")); }
        assertArrayEquals(previousKey(), Files.readAllBytes(key));
    }
}
