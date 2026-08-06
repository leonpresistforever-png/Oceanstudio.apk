package studio.ocean.nativeterminal;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Sets up a Termux-compatible Linux prefix in app private storage.
 * Strategy (like Termux):
 * 1. Try full Termux bootstrap download (proot, pkg, apt, bash, git, curl, python)
 * 2. Fall back to bundled busybox for offline/minimal mode
 * 3. targetSdk 28 avoids Android scoped storage restrictions
 */
public class OceanLinuxSetup {
    private static final String TAG = "OceanLinuxSetup";
    private static final String PREFIX_NAME = "ocean-prefix";
    private static final String BUSYBOX_ASSET = "busybox-arm64";

    private final Context context;
    private final File prefixDir;
    private final File homeDir;
    private final File binDir;
    private final ProotBootstrap bootstrap;
    private String lastMessage = "Not set up";
    private boolean fullBootstrap = false;
    private SetupProgressBridge progressBridge;

    public void setProgressBridge(SetupProgressBridge bridge) {
        this.progressBridge = bridge;
    }

    private void emitProgress(String message, int percent) {
        lastMessage = message;
        if (progressBridge != null) {
            progressBridge.onProgress(message, percent);
        }
    }

    public OceanLinuxSetup(Context context) {
        this.context = context;
        this.prefixDir = new File(context.getFilesDir(), PREFIX_NAME);
        this.homeDir = new File(prefixDir, "home");
        this.binDir = new File(prefixDir, "usr/bin");
        this.bootstrap = new ProotBootstrap(context, prefixDir);
    }

    public File getPrefixDir() { return prefixDir; }
    public File getHomeDir() { return homeDir; }
    public File getBinDir() { return binDir; }
    public boolean isFullBootstrap() { return fullBootstrap; }
    public String getLastMessage() { return lastMessage; }

    public boolean isReady() {
        if (bootstrap.isBootstrapped()) return true;
        File busybox = new File(binDir, "busybox");
        return busybox.exists() && busybox.canExecute();
    }

    public String getShellPath() {
        File bash = new File(binDir, "bash");
        if (bash.exists()) return bash.getAbsolutePath();
        File busybox = new File(binDir, "busybox");
        if (busybox.exists()) return busybox.getAbsolutePath();
        return "/system/bin/sh";
    }

    public String getProotPath() {
        File proot = new File(binDir, "proot");
        return proot.exists() ? proot.getAbsolutePath() : null;
    }

    public SetupResult setup() {
        try {
            prefixDir.mkdirs();
            homeDir.mkdirs();
            binDir.mkdirs();
            new File(prefixDir, "usr/lib").mkdirs();
            new File(prefixDir, "tmp").mkdirs();

            // Full Termux bootstrap (proot, pkg, apt, bash, git, curl, python3...)
            if (!bootstrap.isBootstrapped()) {
                ProotBootstrap.SetupProgress bp = bootstrap.downloadAndExtract(msg -> {
                    emitProgress(msg, -1);
                    Log.i(TAG, msg);
                });
                if (bp.success) {
                    fullBootstrap = true;
                    lastMessage = bp.message;
                    return new SetupResult(true, prefixDir.getAbsolutePath(),
                        getShellPath(), "Full Termux environment — pkg/apt/proot ready");
                }
                Log.w(TAG, "Bootstrap failed, falling back to busybox: " + bp.message);
            } else {
                fullBootstrap = true;
                lastMessage = "Termux bootstrap already installed";
                return new SetupResult(true, prefixDir.getAbsolutePath(),
                    getShellPath(), lastMessage);
            }

            // Fallback: busybox minimal environment
            return setupBusyboxFallback();
        } catch (Exception e) {
            Log.e(TAG, "Setup failed", e);
            return new SetupResult(false, "", "", "Setup failed: " + e.getMessage());
        }
    }

    private SetupResult setupBusyboxFallback() throws IOException {
        File busybox = new File(binDir, "busybox");
        if (!busybox.exists()) {
            if (!extractBusybox(busybox)) {
                return new SetupResult(true, prefixDir.getAbsolutePath(),
                    "/system/bin/sh", "Using system shell — run setup with network for full environment");
            }
            busybox.setExecutable(true, false);
        }

        String[] applets = {
            "sh", "bash", "ls", "cat", "mkdir", "rm", "cp", "mv", "wget", "curl",
            "tar", "gzip", "which", "env", "clear", "grep", "sed", "awk", "head", "tail"
        };
        for (String name : applets) createBusyboxApplet(name);

        lastMessage = "Busybox environment ready (install full bootstrap with network)";
        return new SetupResult(true, prefixDir.getAbsolutePath(),
            busybox.getAbsolutePath(), lastMessage);
    }

    private boolean extractBusybox(File target) {
        try {
            InputStream in = context.getAssets().open(BUSYBOX_ASSET);
            return copyStream(in, target);
        } catch (IOException e) {
            Log.w(TAG, "busybox-arm64 not in assets");
            return false;
        }
    }

    private void createBusyboxApplet(String name) throws IOException {
        File applet = new File(binDir, name);
        if (!applet.exists()) {
            copyFile(new File(binDir, "busybox"), applet);
            applet.setExecutable(true, false);
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(src));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dst))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private boolean copyStream(InputStream in, File target) throws IOException {
        try (OutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return target.length() > 0;
    }

    public static class SetupResult {
        public final boolean ready;
        public final String prefix;
        public final String shell;
        public final String message;

        SetupResult(boolean ready, String prefix, String shell, String message) {
            this.ready = ready;
            this.prefix = prefix;
            this.shell = shell;
            this.message = message;
        }
    }
}
