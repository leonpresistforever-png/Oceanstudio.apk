package studio.ocean.app;

/** Install crash capture before any Activity or agent work can start. */
public final class OceanApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashSurvival.install(this);
    }
}
