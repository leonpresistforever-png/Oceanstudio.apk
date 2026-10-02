package studio.ocean.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * OceanStudio Application entry point.
 * Enforces process-isolated WebView data directory initialization at the earliest lifecycle point
 * to prevent multi-process data directory contention crashes between the main process and :secure_browser.
 * (Directive 2026-10-02 §12, P0-1).
 */
public final class OceanApplication extends android.app.Application {

    private static final String TAG = "OceanApplication";
    private static final String SECURE_BROWSER_PROCESS_SUFFIX = ":secure_browser";
    private static final String SECURE_BROWSER_DATA_DIR_SUFFIX = "secure_browser";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        configureWebViewDataDirectoryEarly();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Defensive double-check in case attachBaseContext was bypassed
        configureWebViewDataDirectoryEarly();

        String processName = getProcessNameCompat();
        boolean isSecureBrowserProcess = processName != null && processName.endsWith(SECURE_BROWSER_PROCESS_SUFFIX);

        CrashSurvival.install(this);

        // Tool overlay is only intended for the main terminal/IDE process
        if (!isSecureBrowserProcess) {
            try {
                OceanForgeInstaller.ensureToolOverlay(this);
            } catch (Throwable error) {
                Log.w("OceanForge", "Tool overlay activation failed", error);
            }
        }
    }

    /**
     * Sets the WebView data-directory suffix for isolated child processes before any WebView
     * or third-party library initializes WebKit.
     * The main process preserves the default data directory (no suffix).
     */
    private void configureWebViewDataDirectoryEarly() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        try {
            String processName = getProcessNameCompat();
            if (processName != null && processName.endsWith(SECURE_BROWSER_PROCESS_SUFFIX)) {
                try {
                    Class<?> webViewClass = Class.forName("android.webkit.WebView");
                    java.lang.reflect.Method method = webViewClass.getMethod("setDataDirectorySuffix", String.class);
                    method.invoke(null, SECURE_BROWSER_DATA_DIR_SUFFIX);
                    Log.i(TAG, "Configured WebView dataDirectorySuffix '"
                            + SECURE_BROWSER_DATA_DIR_SUFFIX + "' for process " + processName);
                } catch (Throwable e) {
                    Log.w(TAG, "WebView data directory suffix setting encountered: " + e.getMessage());
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed during early WebView data directory configuration", t);
        }
    }

    /**
     * Resolves the current process name on API 28+ using Application.getProcessName()
     * with fallback to /proc/self/cmdline for maximum compatibility.
     */
    public static String getProcessNameCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                String proc = android.app.Application.getProcessName();
                if (proc != null && !proc.isEmpty()) {
                    return proc;
                }
            } catch (Throwable ignored) {}
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/self/cmdline"), "ISO-8859-1"))) {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = reader.read()) > 0) { // null byte terminates cmdline in procfs
                sb.append((char) ch);
            }
            String cmdline = sb.toString().trim();
            if (!cmdline.isEmpty()) {
                return cmdline;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
