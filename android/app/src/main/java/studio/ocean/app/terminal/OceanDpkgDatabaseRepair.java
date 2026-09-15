package studio.ocean.app.terminal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Repairs legacy bootstrap file-list syntax; never removes installed-package state. */
public final class OceanDpkgDatabaseRepair {
    private static final String CANONICAL = "/data/data/studio.ocean.app/files/usr";
    private OceanDpkgDatabaseRepair() {}

    public static int repair(File prefix) throws IOException {
        File database = new File(prefix, "var/lib/dpkg");
        File info = new File(database, "info");
        if (!info.isDirectory()) return 0;
        // APT and dpkg use POSIX locks on these files. Never unlink their locks.
        try (FileChannel frontend = FileChannel.open(new File(database, "lock-frontend").toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileChannel backend = FileChannel.open(new File(database, "lock").toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            try (FileLock first = frontend.tryLock()) {
                if (first == null) return 0;
                try (FileLock second = backend.tryLock()) {
                    if (second == null) return 0;
                    return repairLists(prefix, database, info);
                }
            } catch (OverlappingFileLockException busy) { return 0; }
        }
    }

    private static int repairLists(File prefix, File database, File info) throws IOException {
        File[] files = info.listFiles((dir, name) -> name.endsWith(".list"));
        if (files == null) throw new IOException("Cannot read installed package file lists");
        int repaired = 0;
        for (File file : files) {
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) continue;
            byte[] original = Files.readAllBytes(file.toPath());
            String text = new String(original, StandardCharsets.UTF_8);
            // Only migrate lists showing the known broken bootstrap format.
            if (!Arrays.asList(text.split("\n", -1)).contains("/")) continue;
            StringBuilder fixed = new StringBuilder();
            for (String line : text.split("\n", -1)) {
                if (line.isEmpty()) continue; // Empty entries are invalid in dpkg's format.
                if (line.equals("/")) line = "/.";
                else {
                    int arrow = line.indexOf(" -> ");
                    if (arrow >= 0) {
                        String name = line.substring(0, arrow);
                        Path link = localPath(prefix, name);
                        if (link != null && Files.isSymbolicLink(link)) {
                            Path actual = link.getParent().resolve(Files.readSymbolicLink(link)).normalize();
                            String target = line.substring(arrow + 4);
                            Path recorded = target.startsWith("/") ? localPath(prefix, target)
                                    : link.getParent().resolve(target).normalize();
                            // Restore the filename only when the existing symlink proves it.
                            if (actual.equals(recorded)) line = name;
                        }
                    }
                    while (line.endsWith("/") && line.length() > 1) line = line.substring(0, line.length() - 1);
                }
                fixed.append(line).append('\n');
            }
            File backups = new File(database, "ocean-list-backups");
            Files.createDirectories(backups.toPath());
            File backup = File.createTempFile(file.getName() + ".", ".backup", backups);
            try (FileOutputStream output = new FileOutputStream(backup)) {
                output.write(original); output.getFD().sync();
            }
            atomicWrite(file, fixed.toString().getBytes(StandardCharsets.UTF_8));
            repaired++;
        }
        return repaired;
    }

    private static Path localPath(File prefix, String path) {
        if (!path.startsWith(CANONICAL + "/")) return null;
        Path root = prefix.toPath().toAbsolutePath().normalize();
        Path result = root.resolve(path.substring(CANONICAL.length() + 1)).normalize();
        return result.startsWith(root) ? result : null;
    }

    private static void atomicWrite(File target, byte[] bytes) throws IOException {
        File temporary = File.createTempFile(".ocean-dpkg-", ".tmp", target.getParentFile());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes); output.getFD().sync();
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }
}
