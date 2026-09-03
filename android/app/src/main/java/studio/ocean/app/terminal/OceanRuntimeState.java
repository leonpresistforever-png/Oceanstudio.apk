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
                && isPrefixMatching(paths.prefix(), marker.getString("prefix"))
                && executable(paths,"bash") && executable(paths,"apt") && executable(paths,"dpkg") && executable(paths,"pkg");
        } catch(Exception invalid){return false;}
    }
    private static boolean isPrefixMatching(File prefix, String markerPrefix) {
        try {
            if (prefix.getCanonicalPath().equals(new File(markerPrefix).getCanonicalPath())) return true;
        } catch (Exception ignored) {}
        String p1 = prefix.getAbsolutePath().replace("/data/user/0/", "/data/data/").replaceAll("/+$", "");
        String p2 = markerPrefix.replace("/data/user/0/", "/data/data/").replaceAll("/+$", "");
        return p1.equals(p2);
    }
    private static boolean executable(OceanPaths paths,String name){
        File file=new File(paths.prefix(),"bin/"+name);
        if(!file.isFile() || file.length() == 0) return false;
        try { android.system.Os.chmod(file.getAbsolutePath(), 0755); } catch (Exception ignored) {}
        return file.canExecute();
    }
}
