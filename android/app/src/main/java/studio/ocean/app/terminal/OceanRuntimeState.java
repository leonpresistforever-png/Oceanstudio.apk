package studio.ocean.app.terminal;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import studio.ocean.app.OceanPaths;

/** Truthful installation gate: directories alone are never a runtime. */
public final class OceanRuntimeState {
    private OceanRuntimeState() {}
    public static boolean isInstalled(Context context) {
        OceanPaths paths=new OceanPaths(context);
        try {
            JSONObject marker=new JSONObject(new String(Files.readAllBytes(paths.runtimeMarker().toPath()),StandardCharsets.UTF_8));
            return marker.optBoolean("verified",false) && "arm64-v8a".equals(marker.optString("abi"))
                && paths.prefix().getCanonicalPath().equals(new File(marker.getString("prefix")).getCanonicalPath())
                && executable(paths,"bash") && executable(paths,"apt") && executable(paths,"dpkg") && executable(paths,"pkg");
        } catch(Exception invalid){return false;}
    }
    private static boolean executable(OceanPaths paths,String name){File file=new File(paths.prefix(),"bin/"+name);return file.isFile()&&file.canExecute();}
}
