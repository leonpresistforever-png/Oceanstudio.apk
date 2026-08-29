package studio.ocean.app.terminal;

import android.content.Context;
import studio.ocean.app.OceanPaths;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Builds the isolated environment used only by Ocean Terminal child processes. */
public final class OceanEnvironment {
    private final OceanPaths paths;
    public OceanEnvironment(Context context) { paths = new OceanPaths(context.getApplicationContext()); }

    public OceanPaths paths() { return paths; }

    public void ensureFilesystem() throws IOException {
        String[] relative = {"home", "usr/bin", "usr/etc", "usr/include", "usr/lib", "usr/libexec",
            "usr/opt", "usr/share", "usr/tmp", "usr/var/run", "usr/var/tmp",
            "usr/etc/apt/apt.conf.d", "usr/etc/apt/preferences.d", "usr/etc/apt/sources.list.d",
            "usr/var/lib/dpkg/info", "usr/var/lib/dpkg/triggers", "usr/var/lib/dpkg/updates",
            "usr/var/lib/apt/lists", "usr/var/cache/apt/archives", "usr/var/log/apt"};
        for (String path : relative) {
            File directory = new File(paths.root(), path);
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create " + directory);
        }
    }

    public String[] variables(String shell) {
        List<String> values = new ArrayList<>();
        values.add("OCEAN_ROOT=" + paths.root()); values.add("OCEAN_HOME=" + paths.home());
        values.add("OCEAN_PREFIX=" + paths.prefix()); values.add("OCEAN_TMP=" + paths.temp());
        values.add("HOME=" + paths.home()); values.add("PREFIX=" + paths.prefix()); values.add("TMPDIR=" + paths.temp());
        values.add("PATH=" + paths.prefix() + "/bin:/system/bin:/system/xbin");
        values.add("TERM=xterm-256color"); values.add("COLORTERM=truecolor"); values.add("LANG=C.UTF-8");
        values.add("SHELL=" + shell);
        return values.toArray(new String[0]);
    }
}
