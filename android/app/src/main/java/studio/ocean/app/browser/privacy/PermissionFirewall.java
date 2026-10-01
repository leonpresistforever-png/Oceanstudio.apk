package studio.ocean.app.browser.privacy;

import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Firewall for web capability requests: Camera, Microphone, Geolocation, Notifications (PDF 4 §9).
 * Default policy is strictly BLOCK with explicit prompt for temporary session grants.
 * HARD RULE: Decisions live strictly in-memory and expire completely on session end.
 */
public final class PermissionFirewall {

    public interface PermissionPromptCallback {
        void onPromptPermission(String origin, String[] resources, PermissionResponseCallback callback);
    }

    public interface PermissionResponseCallback {
        void onGranted(boolean allow);
    }

    private final Map<String, Set<String>> sessionGrantedPermissions = new HashMap<>();

    public PermissionFirewall() {
    }

    public synchronized void grantPermissionForSession(@NonNull String origin, @NonNull String resource) {
        String key = origin.toLowerCase(Locale.ROOT).trim();
        Set<String> set = sessionGrantedPermissions.get(key);
        if (set == null) {
            set = new HashSet<>();
            sessionGrantedPermissions.put(key, set);
        }
        set.add(resource);
    }

    public synchronized boolean isPermissionGranted(@NonNull String origin, @NonNull String resource) {
        String key = origin.toLowerCase(Locale.ROOT).trim();
        Set<String> set = sessionGrantedPermissions.get(key);
        return set != null && set.contains(resource);
    }

    /**
     * Handles WebChromeClient.onPermissionRequest.
     */
    public void handlePermissionRequest(
            @NonNull PermissionRequest request,
            @Nullable PermissionPromptCallback promptCallback) {

        String origin = request.getOrigin() != null ? request.getOrigin().toString() : "";
        String[] resources = request.getResources();
        if (resources == null || resources.length == 0) {
            request.deny();
            return;
        }

        // Check if all requested resources are already session-granted
        boolean allGranted = true;
        for (String res : resources) {
            if (!isPermissionGranted(origin, res)) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            request.grant(resources);
            return;
        }

        if (promptCallback != null) {
            promptCallback.onPromptPermission(origin, resources, allow -> {
                if (allow) {
                    for (String res : resources) {
                        grantPermissionForSession(origin, res);
                    }
                    request.grant(resources);
                } else {
                    request.deny();
                }
            });
        } else {
            // Default to deny in private mode
            request.deny();
        }
    }

    /**
     * Handles WebChromeClient.onGeolocationPermissionsShowPrompt.
     */
    public void handleGeolocationPrompt(
            @NonNull String origin,
            @NonNull GeolocationPermissions.Callback callback,
            @Nullable PermissionPromptCallback promptCallback) {

        final String res = "android.webkit.resource.GEOLOCATION";
        if (isPermissionGranted(origin, res)) {
            callback.invoke(origin, true, false); // remember=false to ensure no persistence
            return;
        }

        if (promptCallback != null) {
            promptCallback.onPromptPermission(origin, new String[]{res}, allow -> {
                if (allow) {
                    grantPermissionForSession(origin, res);
                    callback.invoke(origin, true, false);
                } else {
                    callback.invoke(origin, false, false);
                }
            });
        } else {
            callback.invoke(origin, false, false);
        }
    }

    /**
     * Erases all temporary session permissions.
     */
    public synchronized void clearSessionPermissions() {
        sessionGrantedPermissions.clear();
    }
}
