package studio.ocean.app.device;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Headless Android app messaging (broadcast/service/activity) with App Access policy checks. */
public final class AndroidAppDispatch {
    private AndroidAppDispatch() {}

    public static JSONObject dispatch(Context context, JSONObject json) throws Exception {
        String mode = json.optString("mode", json.optString("delivery", "activity")).toLowerCase(Locale.ROOT);
        if ("start_activity".equals(mode)) mode = "activity";
        if ("start_service".equals(mode)) mode = "service";
        String action = json.optString("action", "");
        String uri = json.optString("uri", json.optString("data", ""));
        String pkg = json.optString("package", json.optString("package_name", ""));
        String type = json.optString("type", json.optString("mime_type", ""));
        String component = json.optString("component", "");

        Intent intent;
        if (!uri.isEmpty()) {
            intent = new Intent(action.isEmpty() ? Intent.ACTION_VIEW : action, Uri.parse(uri));
        } else if (!action.isEmpty()) {
            intent = new Intent(action);
        } else if (!component.isEmpty()) {
            intent = new Intent();
        } else {
            throw new IllegalArgumentException("action, uri/data, or component is required");
        }
        if (!action.isEmpty() && !uri.isEmpty() && intent.getAction() == null) intent.setAction(action);
        if (!pkg.isEmpty()) intent.setPackage(pkg);
        if (!type.isEmpty()) intent.setType(type);
        if (!component.isEmpty()) {
            ComponentName cn = parseComponent(component, pkg);
            intent.setComponent(cn);
            if (pkg.isEmpty()) pkg = cn.getPackageName();
        }

        applyExtras(intent, json.optJSONObject("extras"));
        applyFlags(intent, json);

        String targetPkg = pkg.isEmpty() ? resolveTargetPackage(intent) : pkg;
        AppAccessPolicy policy = new AppAccessPolicy(context);
        if (policy.restrictionEnabled()) {
            if (targetPkg == null || targetPkg.isEmpty()) {
                throw new IllegalStateException("App Access restrictions require an explicit package or component");
            }
            if (!policy.interactAllowed(targetPkg)) {
                throw new IllegalStateException("App Access profile does not allow messaging " + targetPkg);
            }
        }

        JSONObject out = new JSONObject().put("mode", mode).put("action", intent.getAction() == null ? "" : intent.getAction());
        if (!uri.isEmpty()) out.put("uri", uri);
        if (!pkg.isEmpty()) out.put("package", pkg);
        if (intent.getComponent() != null) out.put("component", intent.getComponent().flattenToShortString());

        switch (mode) {
            case "broadcast":
                if (intent.getAction() == null || intent.getAction().isEmpty()) {
                    throw new IllegalArgumentException("broadcast requires action");
                }
                context.sendBroadcast(intent);
                out.put("dispatched", "broadcast");
                break;
            case "service":
                if (intent.getComponent() == null && (intent.getAction() == null || intent.getAction().isEmpty())) {
                    throw new IllegalArgumentException("service requires component or action");
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
                    else context.startService(intent);
                } catch (IllegalStateException e) {
                    context.startService(intent);
                }
                out.put("dispatched", "service");
                break;
            case "activity":
            default:
                if (json.optBoolean("chooser", false)) {
                    Intent chooser = Intent.createChooser(intent, json.optString("title", "Open with"));
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(chooser);
                    out.put("dispatched", "activity_chooser");
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (json.optBoolean("background", false) || json.optBoolean("headless", false)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        }
                    }
                    context.startActivity(intent);
                    out.put("dispatched", "activity");
                    out.put("note", "Activities may still show UI; prefer broadcast or service for headless tasks.");
                }
                break;
        }
        return out.put("success", true).put("exit_code", 0);
    }

    private static ComponentName parseComponent(String component, String defaultPkg) {
        if (component.contains("/")) {
            String[] parts = component.split("/", 2);
            String p = parts[0].isEmpty() ? defaultPkg : parts[0];
            String cls = parts[1];
            if (cls.startsWith(".")) cls = p + cls;
            return new ComponentName(p, cls);
        }
        if (defaultPkg.isEmpty()) throw new IllegalArgumentException("component class requires package");
        return new ComponentName(defaultPkg, component);
    }

    private static String resolveTargetPackage(Intent intent) {
        if (intent.getComponent() != null) return intent.getComponent().getPackageName();
        return intent.getPackage();
    }

    private static void applyFlags(Intent intent, JSONObject json) throws Exception {
        if (json.has("flags")) {
            Object raw = json.get("flags");
            if (raw instanceof JSONArray) {
                JSONArray flags = (JSONArray) raw;
                for (int i = 0; i < flags.length(); i++) {
                    intent.addFlags(parseFlag(flags.get(i)));
                }
            } else if (raw instanceof Number) {
                intent.addFlags(((Number) raw).intValue());
            } else {
                intent.addFlags(parseFlag(raw));
            }
        }
        JSONArray flagNames = json.optJSONArray("flag_names");
        if (flagNames != null) {
            for (int i = 0; i < flagNames.length(); i++) intent.addFlags(parseFlag(flagNames.get(i)));
        }
    }

    private static int parseFlag(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        String name = String.valueOf(value).toUpperCase(Locale.ROOT).replace('-', '_');
        if (name.startsWith("FLAG_")) name = name.substring(5);
        switch (name) {
            case "ACTIVITY_NEW_TASK": return Intent.FLAG_ACTIVITY_NEW_TASK;
            case "ACTIVITY_CLEAR_TOP": return Intent.FLAG_ACTIVITY_CLEAR_TOP;
            case "ACTIVITY_SINGLE_TOP": return Intent.FLAG_ACTIVITY_SINGLE_TOP;
            case "ACTIVITY_EXCLUDE_FROM_RECENTS": return Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
            case "ACTIVITY_NO_USER_ACTION": return Intent.FLAG_ACTIVITY_NO_USER_ACTION;
            case "ACTIVITY_REORDER_TO_FRONT": return Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
            case "INCLUDE_STOPPED_PACKAGES": return Intent.FLAG_INCLUDE_STOPPED_PACKAGES;
            default:
                try {
                    return Intent.class.getField("FLAG_" + name).getInt(null);
                } catch (Exception ignored) {
                    throw new IllegalArgumentException("Unknown intent flag: " + value);
                }
        }
    }

    static void applyExtras(Intent intent, JSONObject extras) throws Exception {
        if (extras == null) return;
        java.util.Iterator<String> keys = extras.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = extras.get(key);
            if (value == JSONObject.NULL || value == null) continue;
            if (value instanceof Boolean) intent.putExtra(key, (Boolean) value);
            else if (value instanceof Integer) intent.putExtra(key, (Integer) value);
            else if (value instanceof Long) intent.putExtra(key, (Long) value);
            else if (value instanceof Double) intent.putExtra(key, (Double) value);
            else if (value instanceof String) intent.putExtra(key, (String) value);
            else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                if (array.length() == 0) continue;
                Object first = array.get(0);
                if (first instanceof Integer) {
                    int[] vals = new int[array.length()];
                    for (int i = 0; i < array.length(); i++) vals[i] = array.getInt(i);
                    intent.putExtra(key, vals);
                } else if (first instanceof Boolean) {
                    boolean[] vals = new boolean[array.length()];
                    for (int i = 0; i < array.length(); i++) vals[i] = array.getBoolean(i);
                    intent.putExtra(key, vals);
                } else {
                    String[] vals = new String[array.length()];
                    for (int i = 0; i < array.length(); i++) vals[i] = String.valueOf(array.get(i));
                    intent.putExtra(key, vals);
                }
            } else if (value instanceof JSONObject) intent.putExtra(key, jsonToBundle((JSONObject) value));
            else intent.putExtra(key, String.valueOf(value));
        }
    }

    private static Bundle jsonToBundle(JSONObject json) throws Exception {
        Bundle bundle = new Bundle();
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof Boolean) bundle.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) bundle.putInt(key, (Integer) value);
            else if (value instanceof Long) bundle.putLong(key, (Long) value);
            else if (value instanceof Double) bundle.putDouble(key, (Double) value);
            else if (value instanceof String) bundle.putString(key, (String) value);
            else if (value instanceof JSONObject) bundle.putBundle(key, jsonToBundle((JSONObject) value));
            else bundle.putString(key, String.valueOf(value));
        }
        return bundle;
    }
}
