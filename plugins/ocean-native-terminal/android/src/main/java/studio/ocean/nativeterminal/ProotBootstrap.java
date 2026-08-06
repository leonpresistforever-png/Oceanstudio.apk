package studio.ocean.nativeterminal;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and extracts Termux-compatible bootstrap for full Linux prefix.
 * Uses targetSdk 28 (configured in variables.gradle) to avoid scoped storage restrictions.
 */
public class ProotBootstrap {
    private static final String TAG = "ProotBootstrap";
    private static final String BOOTSTRAP_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.02.10-r2%2Bapt-android-7-busybox/bootstrap-aarch64.zip";

    private final Context context;
    private final File prefixDir;

    public ProotBootstrap(Context context, File prefixDir) {
        this.context = context;
        this.prefixDir = prefixDir;
    }

    public boolean isBootstrapped() {
        File proot = new File(prefixDir, "usr/bin/proot");
        File bash = new File(prefixDir, "usr/bin/bash");
        File pkg = new File(prefixDir, "usr/bin/pkg");
        return proot.exists() && bash.exists() && pkg.exists();
    }

    public SetupProgress downloadAndExtract(ProgressListener listener) {
        File zipFile = new File(context.getCacheDir(), "termux-bootstrap.zip");
        try {
            listener.onProgress("Downloading Termux bootstrap...");
            if (!downloadFile(BOOTSTRAP_URL, zipFile, listener)) {
                return SetupProgress.fail("Bootstrap download failed — check network");
            }

            listener.onProgress("Extracting Linux prefix (apt, pkg, proot, bash)...");
            extractZip(zipFile, prefixDir);
            zipFile.delete();

            makeExecutable(new File(prefixDir, "usr/bin"));
            createLoginScript();

            if (isBootstrapped()) {
                listener.onProgress("Termux-compatible environment ready");
                return SetupProgress.ok(prefixDir.getAbsolutePath());
            }
            return SetupProgress.fail("Bootstrap incomplete — missing proot/bash/pkg");
        } catch (Exception e) {
            Log.e(TAG, "Bootstrap failed", e);
            return SetupProgress.fail("Bootstrap error: " + e.getMessage());
        }
    }

    private boolean downloadFile(String urlStr, File dest, ProgressListener listener) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setInstanceFollowRedirects(true);

            if (conn.getResponseCode() != 200) {
                Log.w(TAG, "HTTP " + conn.getResponseCode() + " for " + urlStr);
                return false;
            }

            int total = conn.getContentLength();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                byte[] buf = new byte[8192];
                int n, downloaded = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    downloaded += n;
                    if (total > 0 && downloaded % (512 * 1024) < 8192) {
                        listener.onProgress("Downloading... " + (downloaded * 100 / total) + "%");
                    }
                }
            }
            return dest.length() > 0;
        } catch (IOException e) {
            Log.e(TAG, "Download failed", e);
            return false;
        }
    }

    private void extractZip(File zip, File destDir) throws IOException {
        destDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    try (OutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void makeExecutable(File binDir) {
        if (!binDir.exists()) return;
        File[] files = binDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile()) f.setExecutable(true, false);
        }
    }

    private void createLoginScript() throws IOException {
        File profile = new File(prefixDir, "etc/profile");
        profile.getParentFile().mkdirs();
        String content = "# Ocean.studio native terminal profile\n" +
            "export PATH=$PREFIX/usr/bin:$PATH\n" +
            "export HOME=$PREFIX/home\n" +
            "export TMPDIR=$PREFIX/tmp\n" +
            "export OCEAN_TERMINAL=1\n" +
            "echo 'Ocean Native Terminal — pkg install <pkg> | apt install <pkg>'\n";
        try (FileOutputStream fos = new FileOutputStream(profile)) {
            fos.write(content.getBytes());
        }
    }

    public interface ProgressListener {
        void onProgress(String message);
    }

    public static class SetupProgress {
        public final boolean success;
        public final String prefix;
        public final String message;

        SetupProgress(boolean success, String prefix, String message) {
            this.success = success;
            this.prefix = prefix;
            this.message = message;
        }

        static SetupProgress ok(String prefix) {
            return new SetupProgress(true, prefix, "Full Linux environment ready");
        }

        static SetupProgress fail(String msg) {
            return new SetupProgress(false, "", msg);
        }
    }
}
