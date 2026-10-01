package studio.ocean.app;

import android.os.Build;
import android.webkit.WebView;

/** Install crash capture before any Activity or agent work can start. */
public final class OceanApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = getProcessName();
            if (!getPackageName().equals(processName)) {
                WebView.setDataDirectorySuffix(processName.replace(':', '_'));
            }
        }
        CrashSurvival.install(this);
        try {
            OceanForgeInstaller.ensureToolOverlay(this);
        } catch (Throwable error) {
            android.util.Log.w("OceanForge", "Tool overlay activation failed", error);
        }
    }
}
