package studio.ocean.app.terminal;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class OceanDpkgDatabaseRepairTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String ROOT = "/data/data/studio.ocean.app/files/usr";
    private void write(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent()); Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }
    private String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }

    @Test public void brokenRootIsRepairedAndOriginalBackedUpWithoutChangingStatus() throws Exception {
        File prefix = temporary.newFolder(); Path db = prefix.toPath().resolve("var/lib/dpkg");
        Path list = db.resolve("info/resolv-conf.list"); String broken = "/\n" + ROOT + "/etc/resolv.conf\n";
        write(list, broken); write(db.resolve("status"), "installed user packages\n");
        assertEquals(1, OceanDpkgDatabaseRepair.repair(prefix));
        assertEquals("/.\n" + ROOT + "/etc/resolv.conf\n", read(list));
        assertEquals("installed user packages\n", read(db.resolve("status")));
        File[] backups = db.resolve("ocean-list-backups").toFile().listFiles();
        assertNotNull(backups); assertEquals(1, backups.length); assertEquals(broken, read(backups[0].toPath()));
        assertEquals(0, OceanDpkgDatabaseRepair.repair(prefix));
    }

    @Test public void allBrokenListsAreRepairedWhileValidOnesStayByteIdentical() throws Exception {
        File prefix = temporary.newFolder(); Path info = prefix.toPath().resolve("var/lib/dpkg/info");
        write(info.resolve("bash.list"), "/\n" + ROOT + "/bin/bash\n");
        write(info.resolve("resolv-conf.list"), "/\n" + ROOT + "/etc/resolv.conf\n");
        String valid = "/.\n" + ROOT + "/bin/user tool\n"; write(info.resolve("custom.list"), valid);
        assertEquals(2, OceanDpkgDatabaseRepair.repair(prefix));
        assertEquals(valid, read(info.resolve("custom.list")));
    }

    @Test public void symlinkDisplaySuffixIsRemovedOnlyWhenFilesystemConfirmsIt() throws Exception {
        File prefix = temporary.newFolder(); Path bin = prefix.toPath().resolve("bin"); Files.createDirectories(bin);
        Files.createSymbolicLink(bin.resolve("sh"), Path.of("bash"));
        String odd = ROOT + "/bin/literal -> filename";
        Path list = prefix.toPath().resolve("var/lib/dpkg/info/bash.list");
        write(list, "/\n" + ROOT + "/bin/sh -> " + ROOT + "/bin/bash\n" + odd + "\n");
        assertEquals(1, OceanDpkgDatabaseRepair.repair(prefix));
        assertEquals("/.\n" + ROOT + "/bin/sh\n" + odd + "\n", read(list));
    }

    @Test public void aptFrontendAndDpkgLocksBothPreventWrites() throws Exception {
        for (String name : new String[]{"lock", "lock-frontend"}) {
            File prefix = temporary.newFolder(); Path db = prefix.toPath().resolve("var/lib/dpkg");
            Path list = db.resolve("info/resolv-conf.list"); write(list, "/\n");
            try (FileChannel channel = FileChannel.open(db.resolve(name), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock held = channel.lock()) {
                assertEquals(0, OceanDpkgDatabaseRepair.repair(prefix)); assertEquals("/\n", read(list));
                assertFalse(Files.exists(db.resolve("ocean-list-backups")));
            }
        }
    }

    @Test public void aSymlinkedDatabaseRecordIsNotFollowed() throws Exception {
        File prefix = temporary.newFolder(); Path db = prefix.toPath().resolve("var/lib/dpkg");
        Path original = prefix.toPath().resolve("user-file"); write(original, "/\n");
        Files.createDirectories(db.resolve("info"));
        Files.createSymbolicLink(db.resolve("info/external.list"), original);
        assertEquals(0, OceanDpkgDatabaseRepair.repair(prefix)); assertEquals("/\n", read(original));
    }
}
