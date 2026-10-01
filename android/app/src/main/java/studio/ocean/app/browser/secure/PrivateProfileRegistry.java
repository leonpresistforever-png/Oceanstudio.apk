package studio.ocean.app.browser.secure;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewFeature;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks active ephemeral WebKit profile names so that orphan profiles left behind
 * by sudden process death or crashes can be pruned at startup without touching Normal Browser state.
 */
public final class PrivateProfileRegistry {

    private static final String PREF_NAME = "ocean_private_profile_registry";
    private static final String KEY_PROFILES = "active_ephemeral_profiles";

    private PrivateProfileRegistry() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void registerProfile(@NonNull Context context, @NonNull String profileName) {
        SharedPreferences prefs = getPrefs(context);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_PROFILES, new HashSet<>()));
        set.add(profileName);
        prefs.edit().putStringSet(KEY_PROFILES, set).apply();
    }

    public static synchronized void unregisterProfile(@NonNull Context context, @NonNull String profileName) {
        SharedPreferences prefs = getPrefs(context);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_PROFILES, new HashSet<>()));
        set.remove(profileName);
        prefs.edit().putStringSet(KEY_PROFILES, set).apply();
    }

    public static synchronized Set<String> getRegisteredProfiles(@NonNull Context context) {
        return new HashSet<>(getPrefs(context).getStringSet(KEY_PROFILES, new HashSet<>()));
    }

    /**
     * Deletes all orphan ephemeral profiles recorded in the registry upon application launch.
     * Never touches the default profile or Normal Browser storage.
     */
    public static synchronized void cleanupOrphanProfiles(@NonNull Context context) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return;
        }

        SharedPreferences prefs = getPrefs(context);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_PROFILES, new HashSet<>()));
        if (set.isEmpty()) {
            return;
        }

        try {
            ProfileStore store = ProfileStore.getInstance();
            for (String profileName : set) {
                try {
                    store.deleteProfile(profileName);
                } catch (Exception ignored) {
                }
            }
            prefs.edit().remove(KEY_PROFILES).apply();
        } catch (Exception ignored) {
        }
    }
}
