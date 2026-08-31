package studio.ocean.app.terminal;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.UUID;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONObject;
import studio.ocean.app.OceanPaths;

/** Verifies and transactionally installs the CI-generated Ocean bootstrap. */
public final class OceanBootstrapInstaller {
    private static final String ASSET_ROOT = "ocean/bootstrap/aarch64/";
    static final String STAGING_PREFIX = ".ocean-bootstrap-staging-";
    private static final String ROLLBACK_PREFIX = ".ocean-prefix-rollback-";

    private final Context context;
    private final OceanPaths paths;

    public OceanBootstrapInstaller(Context context) {
        this.context = context.getApplicationContext();
        this.paths = new OceanPaths(context);
    }

    public synchronized void install() throws IOException {
        try {
            installVerified();
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Invalid Ocean bootstrap", error);
        }
    }

    private void installVerified() throws Exception {
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
            copyAndVerify(archive, manifest.getLong("archiveSize"), manifest.getString("archiveSha256"));
            if (!staging.mkdir()) throw new IOException("Cannot create bootstrap staging directory " + staging);
            Os.chmod(staging.getAbsolutePath(), 0700);
            TerminalStartupLog.stage("06", "staging directory created path=" + staging);
            TerminalStartupLog.stage("07", "bootstrap extraction begin");
            extract(archive, staging);
            TerminalStartupLog.stage("08", "bootstrap extraction complete");
            File candidate = new File(staging, "usr");
            TerminalStartupLog.stage("09", "staged runtime validation begin");
            validate(candidate);
            TerminalStartupLog.stage("10", "staged runtime valid");

            TerminalStartupLog.stage("11", "activation begin");
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
            validate(paths.prefix());
            TerminalStartupLog.stage("12", "activation complete path=" + paths.prefix());
            writeMarker(manifest);
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
            throw failure;
        } finally {
            if (!archive.delete() && archive.exists()) {
                TerminalStartupLog.stage("WARN", "cannot remove bootstrap cache " + archive);
            }
            cleanupWarning(staging);
            if (!oldPrefixMoved) cleanupWarning(rollback);
        }
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

    private void extract(File archive, File staging) throws IOException {
        String root = staging.getCanonicalPath() + File.separator;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new ZstdInputStream(new BufferedInputStream(new FileInputStream(archive))))) {
            for (TarArchiveEntry entry; (entry = tar.getNextTarEntry()) != null;) {
                String name = entry.getName();
                if (name.startsWith("/") || name.indexOf('\0') >= 0) throw new IOException("Unsafe archive path");
                File target = new File(staging, name);
                if (!target.getCanonicalPath().startsWith(root)) throw new IOException("Archive path traversal");
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create " + name);
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Cannot create archive parent");
                }
                if (entry.isSymbolicLink()) {
                    String link = entry.getLinkName();
                    if (link.startsWith("/") || !new File(parent, link).getCanonicalPath().startsWith(root)) {
                        throw new IOException("Unsafe archive symlink");
                    }
                    try {
                        Os.symlink(link, target.getAbsolutePath());
                    } catch (Exception error) {
                        throw new IOException("Cannot create symlink", error);
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
                    }
                    out.getFD().sync();
                }
                try {
                    Os.chmod(target.getAbsolutePath(), entry.getMode() & 0777);
                } catch (Exception error) {
                    throw new IOException("Cannot set archive mode", error);
                }
            }
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
                Os.rmdir(file.getAbsolutePath());
            } catch (ErrnoException error) {
                throw filesystemError("rmdir", file, error);
            }
        } else {
            // unlink removes a symlink itself and never follows its target.
            try {
                Os.unlink(file.getAbsolutePath());
            } catch (ErrnoException error) {
                throw filesystemError("unlink", file, error);
            }
        }
    }

    private static IOException filesystemError(String operation, File file, ErrnoException error) {
        return new IOException(operation + " failed path=" + file + " errno=" + error.errno, error);
    }
}
