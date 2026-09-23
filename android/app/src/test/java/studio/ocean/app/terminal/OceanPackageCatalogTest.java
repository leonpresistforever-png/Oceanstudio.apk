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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class OceanPackageCatalogTest {
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
    @Test public void olderBundledCatalogueIsUpgraded() throws Exception {
        File prefix = temporary.newFolder();
        Path existing = prefix.toPath().resolve("var/lib/apt/lists/" + indexName());
        write(existing, "Package: old-tool\n");
        assertTrue(new File(prefix, "var/lib/apt/lists/" + indexName()).setLastModified(0));
        assertTrue(OceanPackageCatalog.prepare(prefix, source()));
        String packages = read(existing);
        assertTrue(packages.contains("Package: pip\n"));
        assertTrue(packages.length() > 1000000);
    }
}
