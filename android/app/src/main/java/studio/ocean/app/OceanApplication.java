package studio.ocean.app;

/** Install crash capture before any Activity or agent work can start. */
public final class OceanApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashSurvival.install(this);
        try{OceanForgeInstaller.ensureToolOverlay(this);}
        catch(Throwable error){android.util.Log.w("OceanForge","Tool overlay activation failed",error);}
    }
}
