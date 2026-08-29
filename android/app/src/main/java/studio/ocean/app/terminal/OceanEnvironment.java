package studio.ocean.app.terminal;

import android.content.Context;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import studio.ocean.app.OceanPaths;

/** Authoritative environment passed only to Ocean Terminal child processes. */
public final class OceanEnvironment {
    private OceanEnvironment() {}

    public static String[] create(Context context, String shell) {
        OceanPaths paths = new OceanPaths(context);
        List<String> values = new ArrayList<>();
        values.add("OCEAN_ROOT=" + paths.root()); values.add("OCEAN_HOME=" + paths.home());
        values.add("OCEAN_PREFIX=" + paths.prefix()); values.add("HOME=" + paths.home());
        values.add("PREFIX=" + paths.prefix()); values.add("TMPDIR=" + paths.temp());
        values.add("PATH=" + paths.prefix() + "/bin:/system/bin:/system/xbin");
        values.add("TERM=xterm-256color"); values.add("COLORTERM=truecolor");
        values.add("SHELL=" + shell); values.add("LANG=C.UTF-8");
        values.add("ANDROID_ROOT=" + System.getenv("ANDROID_ROOT"));
        values.add("ANDROID_DATA=" + System.getenv("ANDROID_DATA"));
        return values.toArray(new String[0]);
    }

    public static String architecture() { return Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]; }
}
