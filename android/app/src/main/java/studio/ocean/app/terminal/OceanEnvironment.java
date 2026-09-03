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
        return create(context, shell, "/system/bin/sh".equals(shell));
    }

    public static String[] create(Context context, String shell, boolean isRecovery) {
        if (shell == null || shell.isEmpty()) shell = "/system/bin/sh";
        OceanPaths paths = new OceanPaths(context);
        try { if (paths.temp() != null && !paths.temp().exists()) paths.temp().mkdirs(); } catch (Throwable ignored) {}
        List<String> values = new ArrayList<>();
        values.add("OCEAN_ROOT=" + paths.root()); values.add("OCEAN_HOME=" + paths.home());
        values.add("OCEAN_PREFIX=" + paths.prefix()); values.add("HOME=" + paths.home());
        values.add("PREFIX=" + paths.prefix()); values.add("TMPDIR=" + paths.temp());
        values.add("PATH=" + paths.prefix() + "/bin:/system/bin:/system/xbin");
        values.add("TERM=xterm-256color"); values.add("COLORTERM=truecolor");
        values.add("SHELL=" + shell); values.add("LANG=C.UTF-8");
        if (!isRecovery && !"/system/bin/sh".equals(shell)) {
            values.add("LD_LIBRARY_PATH=" + paths.prefix() + "/lib");
        }
        String androidRoot = System.getenv("ANDROID_ROOT");
        values.add("ANDROID_ROOT=" + (androidRoot != null && !androidRoot.isEmpty() ? androidRoot : "/system"));
        String androidData = System.getenv("ANDROID_DATA");
        values.add("ANDROID_DATA=" + (androidData != null && !androidData.isEmpty() ? androidData : "/data"));
        return values.toArray(new String[0]);
    }

    public static String architecture() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return "arm64-v8a";
        }
        return Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0];
    }
}
