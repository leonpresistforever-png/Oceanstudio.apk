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
}
