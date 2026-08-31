package studio.ocean.app.terminal;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.os.Looper;
import com.github.luben.zstd.ZstdInputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONObject;
import studio.ocean.app.OceanPaths;

/** Verifies and transactionally installs the CI-generated Ocean bootstrap. */
public final class OceanBootstrapInstaller {
    public enum Stage { CHECKING, INSTALLING, VERIFYING_STAGING, ACTIVATING, VERIFYING_ACTIVE }
    public interface ProgressListener { void onProgress(Stage stage, String detail, long completed, long total); }
    private static final ProgressListener NO_PROGRESS = (stage, detail, completed, total) -> {};
    private static final String ASSET_ROOT = "ocean/bootstrap/aarch64/";
    static final String STAGING_PREFIX = ".ocean-bootstrap-staging-";
    private static final String ROLLBACK_PREFIX = ".ocean-prefix-rollback-";
    private static final String TRANSACTION_FILE = ".ocean-bootstrap-transaction.json";
    private static final String BUILD_PREFIX = "/data/data/studio.ocean.app/files/usr";

    private final Context context;
    private final OceanPaths paths;

    public OceanBootstrapInstaller(Context context) {
        this.context = context.getApplicationContext();
        this.paths = new OceanPaths(context);
    }

    public void install() throws IOException { install(NO_PROGRESS); }

    public void install(ProgressListener progress) throws IOException {
        requireWorkerThread("install");
        try {
            installVerified(progress == null ? NO_PROGRESS : progress);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Invalid Ocean bootstrap", error);
        }
    }

    private void installVerified(ProgressListener progress) throws Exception {
        requireWorkerThread("installVerified");
        if (OceanRuntimeState.isInstalled(context)) return;
        if (!"arm64-v8a".equals(OceanEnvironment.architecture())) {
            throw new IOException("Ocean runtime supports arm64-v8a only");
        }
        JSONObject manifest = manifest();
        if (!"studio.ocean.app".equals(manifest.optString("packageName"))
                || !"aarch64".equals(manifest.optString("architecture"))
                || !"ocean-aarch64.tar.zst".equals(manifest.optString("archive"))) {
            throw new IOException("Bootstrap identity mismatch");
        }
        if (!paths.home().isDirectory() && !paths.home().mkdirs()) {
            throw new IOException("Cannot create Ocean HOME");
        }
        try {
            Os.chmod(paths.home().getAbsolutePath(), 0700);
        } catch (Exception error) {
            throw new IOException("Cannot secure Ocean HOME", error);
        }
        TerminalStartupLog.stage("05H", "Ocean HOME ready path=" + paths.home());

        File archive = new File(context.getCacheDir(), "ocean-aarch64-" + UUID.randomUUID() + ".tar.zst");
        File staging = ownedTransactionDirectory(STAGING_PREFIX);
        File rollback = ownedTransactionDirectory(ROLLBACK_PREFIX);
        boolean oldPrefixMoved = false;
        boolean newPrefixActivated = false;
        try {
            // Recovery is deterministic and non-blocking to callers because install() is worker-only.
            // Partial staging is never activated; unique stale trees are removed before a fresh attempt.
            cleanupStaleTransactions(staging, rollback);
            writeTransaction("PREPARING", staging, rollback);
            progress.onProgress(Stage.CHECKING, "Verifying bundled runtime", 0, 0);
            copyAndVerify(archive, manifest.getLong("archiveSize"), manifest.getString("archiveSha256"));
            if (!staging.mkdir()) throw new IOException("Cannot create bootstrap staging directory " + staging);
            writeTransaction("EXTRACTING", staging, rollback);
            Os.chmod(staging.getAbsolutePath(), 0700);
            TerminalStartupLog.stage("06", "staging directory created path=" + staging);
            TerminalStartupLog.stage("07", "bootstrap extraction begin");
            progress.onProgress(Stage.INSTALLING, "Extracting Ocean runtime", 0, manifest.optLong("entryCount", 0));
            extract(archive, staging, progress, manifest.optLong("entryCount", 0));
            TerminalStartupLog.stage("08", "bootstrap extraction complete");
            File candidate = new File(staging, "usr");
            TerminalStartupLog.stage("09", "staged runtime validation begin");
            progress.onProgress(Stage.VERIFYING_STAGING, "Verifying extracted runtime", 0, 0);
            validate(candidate);
            writeTransaction("STAGED_VALID", staging, rollback);
            TerminalStartupLog.stage("10", "staged runtime valid");

            TerminalStartupLog.stage("11", "activation begin");
            writeTransaction("ACTIVATING", staging, rollback);
            progress.onProgress(Stage.ACTIVATING, "Activating Ocean runtime", 0, 0);
            if (paths.prefix().exists()) {
                if (!paths.prefix().renameTo(rollback)) {
                    throw new IOException("Cannot preserve previous prefix at " + rollback);
                }
                oldPrefixMoved = true;
            }
            if (!candidate.renameTo(paths.prefix())) {
                restorePreviousPrefix(rollback, oldPrefixMoved);
                oldPrefixMoved = false;
                throw new IOException("Cannot activate Ocean prefix from " + candidate);
            }
            newPrefixActivated = true;
            progress.onProgress(Stage.VERIFYING_ACTIVE, "Verifying active runtime", 0, 0);
            validate(paths.prefix());
            TerminalStartupLog.stage("12", "activation complete path=" + paths.prefix());
            writeMarker(manifest);
            deleteTransactionMarker();
            TerminalStartupLog.stage("13", "bootstrap marker written");

            // The marker commits the transaction. Cleanup after this point is deliberately nonfatal.
            cleanupWarning(rollback);
            oldPrefixMoved = false;
            cleanupStaleTransactions(staging, rollback);
        } catch (Exception failure) {
            if (newPrefixActivated) {
                try {
                    deleteTree(paths.prefix());
                    restorePreviousPrefix(rollback, oldPrefixMoved);
                    oldPrefixMoved = false;
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            try { writeTransaction("FAILED", staging, rollback); } catch (Exception metadataFailure) { failure.addSuppressed(metadataFailure); }
            throw failure;
        } finally {
            if (!archive.delete() && archive.exists()) {
                TerminalStartupLog.stage("WARN", "cannot remove bootstrap cache " + archive);
            }
            cleanupWarning(staging);
            if (!oldPrefixMoved) cleanupWarning(rollback);
        }
    }

    private void writeTransaction(String phase, File staging, File rollback) throws IOException {
        JSONObject value = new JSONObject();
        try {
            value.put("phase", phase);
            value.put("staging", staging.getName());
            value.put("rollback", rollback.getName());
            value.put("updatedAt", System.currentTimeMillis());
        } catch (Exception error) { throw new IOException("Cannot encode bootstrap transaction", error); }
        File file = new File(paths.root(), TRANSACTION_FILE);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write((value.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private void deleteTransactionMarker() {
        File file = new File(paths.root(), TRANSACTION_FILE);
        if (file.exists() && !file.delete()) TerminalStartupLog.stage("WARN", "cannot remove completed transaction marker");
    }

    private File ownedTransactionDirectory(String prefix) throws IOException {
        File root = paths.root().getCanonicalFile();
        File result = new File(root, prefix + UUID.randomUUID()).getAbsoluteFile();
        File parent = result.getParentFile();
        if (!root.equals(parent) || !result.getName().startsWith(prefix)) {
            throw new IOException("Unsafe Ocean transaction path " + result);
        }
        return result;
    }

    private void restorePreviousPrefix(File rollback, boolean exists) throws IOException {
        if (exists && !rollback.renameTo(paths.prefix())) {
            throw new IOException("Cannot restore previous Ocean prefix from " + rollback);
        }
    }

    private void writeMarker(JSONObject manifest) throws Exception {
        JSONObject marker = new JSONObject();
        marker.put("bootstrapVersion", manifest.getString("bootstrapVersion"));
        marker.put("abi", "arm64-v8a");
        marker.put("prefix", paths.prefix().getCanonicalPath());
        marker.put("archiveSha256", manifest.getString("archiveSha256"));
        marker.put("verified", true);
        File temporary = new File(paths.root(), paths.runtimeMarker().getName() + ".tmp-" + UUID.randomUUID());
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write((marker.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            Files.move(temporary.toPath(), paths.runtimeMarker().toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temporary.toPath(), paths.runtimeMarker().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private void cleanupStaleTransactions(File currentStaging, File currentRollback) {
        File[] children = paths.root().listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.equals(currentStaging) || child.equals(currentRollback)) continue;
            String name = child.getName();
            if (name.equals(".ocean-bootstrap-staging") || name.equals(".ocean-prefix-previous")
                    || name.startsWith(STAGING_PREFIX) || name.startsWith(ROLLBACK_PREFIX)) {
                cleanupWarning(child);
            }
        }
    }

    private void cleanupWarning(File file) {
        try {
            deleteTree(file);
        } catch (IOException error) {
            TerminalStartupLog.stage("WARN", "stale transaction cleanup failed: " + error.getMessage());
        }
    }

    private JSONObject manifest() throws IOException {
        try (InputStream in = context.getAssets().open(ASSET_ROOT + "ocean-aarch64.manifest.json")) {
            return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IOException("Verified Ocean bootstrap is not bundled in this APK", error);
        }
    }

    private void copyAndVerify(File output, long expectedSize, String expectedHash) throws Exception {
        requireWorkerThread("copyAndVerify");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = 0;
        byte[] buffer = new byte[65536];
        try (InputStream in = context.getAssets().open(ASSET_ROOT + "ocean-aarch64.tar.zst");
             FileOutputStream out = new FileOutputStream(output)) {
            for (int count; (count = in.read(buffer)) != -1;) {
                out.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                size += count;
            }
            out.getFD().sync();
        }
        if (size != expectedSize || !hex(digest.digest()).equalsIgnoreCase(expectedHash)) {
            output.delete();
            throw new IOException("Bootstrap integrity check failed");
        }
    }

    private void extract(File archive, File staging, ProgressListener progress, long totalEntries) throws IOException {
        requireWorkerThread("extract");
        List<DirectoryMode> directoryModes = new ArrayList<>();
        long entries = 0, bytes = 0, lastUpdate = 0;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new ZstdInputStream(new BufferedInputStream(new FileInputStream(archive))))) {
            for (TarArchiveEntry entry; (entry = tar.getNextTarEntry()) != null;) {
                entries++;
                Path logical = normalizeArchivePath(entry.getName(), "entry");
                File target = new File(staging, logical.toString());
                ensureRealParents(staging, logical.getParent());
                if (entry.isDirectory()) {
                    createOrVerifyDirectory(target, entry.getName());
                    directoryModes.add(new DirectoryMode(target, entry.getMode() & 0777));
                    continue;
                }
                requireAbsent(target, entry.getName());
                if (entry.isSymbolicLink()) {
                    String link = safeSymlinkTarget(logical, entry.getLinkName());
                    try {
                        Os.symlink(link, target.getAbsolutePath());
                    } catch (Exception error) {
                        throw new IOException("Cannot create symlink entry=" + entry.getName()
                                + " target=" + link, error);
                    }
                    continue;
                }
                if (entry.isLink()) {
                    Path linkLogical = normalizeLinkPath(entry.getLinkName(), "hardlink");
                    File source = new File(staging, linkLogical.toString());
                    StructStat sourceStat = lstat(source, "hardlink source");
                    if (!OsConstants.S_ISREG(sourceStat.st_mode)) {
                        throw new IOException("Unsafe archive hardlink entry=" + entry.getName()
                                + " target=" + entry.getLinkName() + " normalized=" + linkLogical
                                + " reason=target is not an extracted regular file");
                    }
                    try {
                        Os.link(source.getAbsolutePath(), target.getAbsolutePath());
                    } catch (Exception error) {
                        throw new IOException("Cannot create hardlink entry=" + entry.getName()
                                + " target=" + entry.getLinkName(), error);
                    }
                    continue;
                }
                if (!entry.isFile()) throw new IOException("Unsupported archive entry");
                try (FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    long remaining = entry.getSize();
                    while (remaining > 0) {
                        int count = tar.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (count < 0) throw new IOException("Truncated archive");
                        out.write(buffer, 0, count);
                        remaining -= count;
                        bytes += count;
                    }
                    out.getFD().sync();
                }
                try {
                    Os.chmod(target.getAbsolutePath(), entry.getMode() & 0777);
                } catch (Exception error) {
                    throw new IOException("Cannot set archive mode", error);
                }
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastUpdate >= 200) {
                    progress.onProgress(Stage.INSTALLING, "Extracting Ocean runtime", entries, totalEntries);
                    lastUpdate = now;
                }
            }
        }
        TerminalStartupLog.stage("07P", "extraction entries=" + entries + " bytes=" + bytes);
        progress.onProgress(Stage.INSTALLING, "Extracted Ocean runtime", entries, totalEntries > 0 ? totalEntries : entries);
        // Apply archived directory modes only after all children exist. Applying a read-only
        // mode during extraction could make legitimate later entries impossible to create.
        for (int index = directoryModes.size() - 1; index >= 0; index--) {
            DirectoryMode mode = directoryModes.get(index);
            try {
                Os.chmod(mode.directory.getAbsolutePath(), mode.mode);
            } catch (ErrnoException error) {
                throw filesystemError("chmod archive directory", mode.directory, error);
            }
        }
    }

    private static final class DirectoryMode {
        final File directory;
        final int mode;
        DirectoryMode(File directory, int mode) { this.directory = directory; this.mode = mode; }
    }

    private static Path normalizeArchivePath(String value, String kind) throws IOException {
        if (value == null || value.isEmpty() || value.indexOf('\0') >= 0) {
            throw new IOException("Unsafe archive " + kind + " path=" + value + " reason=empty-or-NUL");
        }
        Path raw = Paths.get(value);
        Path normalized = raw.normalize();
        if (raw.isAbsolute() || normalized.getNameCount() == 0 || normalized.startsWith("..")
                || !"usr".equals(normalized.getName(0).toString())) {
            throw new IOException("Unsafe archive " + kind + " path=" + value
                    + " normalized=" + normalized + " reason=outside usr");
        }
        return normalized;
    }

    private static Path normalizeLinkPath(String value, String kind) throws IOException {
        if (value != null && (value.equals(BUILD_PREFIX) || value.startsWith(BUILD_PREFIX + "/"))) {
            String suffix = value.substring(BUILD_PREFIX.length());
            value = "usr" + suffix;
        }
        return normalizeArchivePath(value, kind);
    }

    // Package-visible so the security policy can be exercised by host tests without
    // weakening the production extraction boundary.
    static String safeSymlinkTarget(Path entry, String original) throws IOException {
        boolean absolute = original != null && original.startsWith("/");
        boolean containsParent = containsParentComponent(original);
        Path resolved;
        if (absolute) {
            resolved = normalizeLinkPath(original, "symlink target");
        } else {
            if (original == null || original.isEmpty() || original.indexOf('\0') >= 0) {
                throw unsafeLink(entry, original, null, absolute, containsParent, "empty-or-NUL");
            }
            resolved = entry.getParent().resolve(original).normalize();
            if (resolved.getNameCount() == 0 || resolved.startsWith("..")
                    || !"usr".equals(resolved.getName(0).toString())) {
                throw unsafeLink(entry, original, resolved, false, containsParent, "escapes usr");
            }
        }
        String relative = entry.getParent().relativize(resolved).toString();
        TerminalStartupLog.stage("07L", "archive symlink entry=" + entry + " target=" + original
                + " normalized=" + resolved + " installedTarget=" + relative
                + " absolute=" + absolute + " containsParent=" + containsParent
                + " canonicalPrefix=" + (original != null && original.startsWith(BUILD_PREFIX)));
        return relative;
    }

    private static boolean containsParentComponent(String value) {
        if (value == null) return false;
        for (Path component : Paths.get(value)) if ("..".equals(component.toString())) return true;
        return false;
    }

    private static IOException unsafeLink(Path entry, String target, Path normalized,
                                          boolean absolute, boolean containsParent, String reason) {
        return new IOException("Unsafe archive symlink entry=" + entry + " target=" + target
                + " normalized=" + normalized + " absolute=" + absolute
                + " containsParent=" + containsParent + " canonicalPrefix="
                + (target != null && target.startsWith(BUILD_PREFIX)) + " reason=" + reason);
    }

    private static String describeEntry(TarArchiveEntry entry, Path normalized) {
        String type = entry.isDirectory() ? "directory" : entry.isSymbolicLink() ? "symlink"
                : entry.isLink() ? "hardlink" : entry.isFile() ? "file" : "special";
        return "archive entry path=" + entry.getName() + " type=" + type
                + " symlinkTarget=" + (entry.isSymbolicLink() ? entry.getLinkName() : "")
                + " hardlinkTarget=" + (entry.isLink() ? entry.getLinkName() : "")
                + " normalized=" + normalized;
    }

    /** Creates parents one component at a time and refuses to traverse a pre-existing symlink. */
    private static void ensureRealParents(File staging, Path parent) throws IOException {
        if (parent == null) return;
        File current = staging;
        for (Path component : parent) {
            current = new File(current, component.toString());
            try {
                StructStat stat = Os.lstat(current.getAbsolutePath());
                if (!OsConstants.S_ISDIR(stat.st_mode)) {
                    throw new IOException("Unsafe archive parent is not a real directory: " + current);
                }
            } catch (ErrnoException error) {
                if (error.errno != OsConstants.ENOENT) throw filesystemError("lstat parent", current, error);
                if (!current.mkdir()) throw new IOException("Cannot create archive parent " + current);
            }
        }
    }

    private static void createOrVerifyDirectory(File target, String entry) throws IOException {
        try {
            StructStat stat = Os.lstat(target.getAbsolutePath());
            if (!OsConstants.S_ISDIR(stat.st_mode)) {
                throw new IOException("Archive directory collides with non-directory entry=" + entry);
            }
        } catch (ErrnoException error) {
            if (error.errno != OsConstants.ENOENT) throw filesystemError("lstat directory", target, error);
            if (!target.mkdir()) throw new IOException("Cannot create archive directory " + entry);
        }
    }

    private static void requireAbsent(File target, String entry) throws IOException {
        try {
            Os.lstat(target.getAbsolutePath());
            throw new IOException("Duplicate archive entry=" + entry);
        } catch (ErrnoException error) {
            if (error.errno != OsConstants.ENOENT) throw filesystemError("lstat entry", target, error);
        }
    }

    private static StructStat lstat(File file, String operation) throws IOException {
        try {
            return Os.lstat(file.getAbsolutePath());
        } catch (ErrnoException error) {
            throw filesystemError(operation, file, error);
        }
    }

    private static void validate(File prefix) throws IOException {
        for (String name : new String[]{"bash", "apt", "dpkg", "pkg"}) {
            File executable = new File(prefix, "bin/" + name);
            if (!executable.isFile() || !executable.canExecute()) throw new IOException("Bootstrap missing " + name);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count; (count = in.read(buffer)) != -1;) out.write(buffer, 0, count);
        return out.toByteArray();
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) out.append(String.format("%02x", value & 255));
        return out.toString();
    }

    /** Deletes only an Ocean-owned transaction tree; lstat prevents traversal through symlinks. */
    static void deleteTree(File file) throws IOException {
        requireWorkerThread("deleteTree");
        if (file == null) return;
        final StructStat stat;
        try {
            stat = Os.lstat(file.getAbsolutePath());
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return;
            throw filesystemError("lstat", file, error);
        }
        if (OsConstants.S_ISDIR(stat.st_mode)) {
            try {
                Os.chmod(file.getAbsolutePath(), stat.st_mode | 0700);
            } catch (ErrnoException error) {
                throw filesystemError("chmod directory", file, error);
            }
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list staging directory " + file);
            for (File child : children) deleteTree(child);
            try {
                Os.remove(file.getAbsolutePath());
            } catch (ErrnoException error) {
                throw filesystemError("remove directory", file, error);
            }
        } else {
            // unlink removes a symlink itself and never follows its target.
            try {
                Os.remove(file.getAbsolutePath());
            } catch (ErrnoException error) {
                throw filesystemError("remove entry", file, error);
            }
        }
    }

    private static IOException filesystemError(String operation, File file, ErrnoException error) {
        return new IOException(operation + " failed path=" + file + " errno=" + error.errno, error);
    }

    static void requireWorkerThread(String operation) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(operation + " must not run on Android main thread");
        }
    }
}
