package studio.ocean.app.device;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppAccessPolicy {
    private static final String PREFS="ocean_app_access_profiles";
    private final SharedPreferences prefs;
    public AppAccessPolicy(Context context){prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public boolean restrictionEnabled(){return prefs.getBoolean("restrict_enabled",false);}
    public void setRestrictionEnabled(boolean enabled){prefs.edit().putBoolean("restrict_enabled",enabled).apply();}

    public boolean inspectAllowed(String pkg){return !restrictionEnabled()||prefs.getBoolean("inspect_"+pkg,false);}
    public boolean interactAllowed(String pkg){return !restrictionEnabled()||prefs.getBoolean("interact_"+pkg,false);}
    public boolean screenshotAllowed(String pkg){return !restrictionEnabled()||prefs.getBoolean("screenshot_"+pkg,false);}

    public void set(String pkg,boolean inspect,boolean interact,boolean screenshot){
        prefs.edit().putBoolean("inspect_"+pkg,inspect).putBoolean("interact_"+pkg,interact).putBoolean("screenshot_"+pkg,screenshot).apply();
    }
    public boolean selected(String pkg){
        return prefs.getBoolean("inspect_"+pkg,false)||prefs.getBoolean("interact_"+pkg,false)||prefs.getBoolean("screenshot_"+pkg,false);
    }
}
