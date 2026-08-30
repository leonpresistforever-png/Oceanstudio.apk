package studio.ocean.app.terminal;

import android.content.Context;
import java.io.File;
import studio.ocean.app.OceanPaths;

/** Truthful installation gate: directories alone are never a runtime. */
public final class OceanRuntimeState {
    private OceanRuntimeState() {}
    public static boolean isInstalled(Context context) {
        OceanPaths paths=new OceanPaths(context); File shell=new File(paths.prefix(),"bin/bash");
        return paths.runtimeMarker().isFile() && paths.runtimeMarker().length()>0 && shell.isFile() && shell.canExecute();
    }
}
