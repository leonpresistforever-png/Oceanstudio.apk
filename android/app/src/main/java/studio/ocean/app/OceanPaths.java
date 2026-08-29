package studio.ocean.app;

import android.content.Context;
import java.io.File;

/** Single runtime source of truth; never hardcode Android private-data paths. */
public final class OceanPaths {
    private final File root;

    public OceanPaths(Context context) {
        root = context.getFilesDir();
    }

    public File root() { return root; }
    public File home() { return new File(root, "home"); }
    public File prefix() { return new File(root, "usr"); }
    public File temp() { return new File(prefix(), "tmp"); }
    public File runtimeMarker() { return new File(prefix(), ".ocean-runtime.json"); }

    /** Creates the filesystem contract only; it never marks a runtime as installed. */
    public void ensureDirectoryContract() throws java.io.IOException {
        File[] directories = { home(), new File(home(), "projects"), new File(prefix(), "bin"),
            new File(prefix(), "etc"), new File(prefix(), "include"), new File(prefix(), "lib"),
            new File(prefix(), "libexec"), new File(prefix(), "opt"), new File(prefix(), "share"), temp(),
            new File(prefix(), "var/run"), new File(prefix(), "var/tmp"),
            new File(prefix(), "etc/apt/apt.conf.d"), new File(prefix(), "etc/apt/preferences.d"),
            new File(prefix(), "etc/apt/sources.list.d"), new File(prefix(), "var/lib/dpkg/info"),
            new File(prefix(), "var/lib/dpkg/triggers"), new File(prefix(), "var/lib/dpkg/updates"),
            new File(prefix(), "var/lib/apt/lists"), new File(prefix(), "var/cache/apt/archives"),
            new File(prefix(), "var/log/apt") };
        for (File directory : directories) if (!directory.isDirectory() && !directory.mkdirs())
            throw new java.io.IOException("Cannot create " + directory);
    }
}
