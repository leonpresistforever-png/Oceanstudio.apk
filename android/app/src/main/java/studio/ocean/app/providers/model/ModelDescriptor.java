package studio.ocean.app.providers.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Metadata and capabilities for an AI model provided by an active connection.
 */
public final class ModelDescriptor {
    public final String id;
    public final String name;
    public final String modelId;
    public final String displayName;
    public final int contextLength;
    public final boolean isDefault;
    public final boolean supportsTools;
    public final boolean supportsVision;
    public final String cooldownStatus; // e.g. "Available", "Cooldown 12m", "Plan limit"

    public ModelDescriptor(String id, String name, int contextLength, boolean isDefault,
                           boolean supportsTools, boolean supportsVision, String cooldownStatus) {
        this.id = id;
        this.name = name != null ? name : id;
        this.modelId = this.id;
        this.displayName = this.name;
        this.contextLength = contextLength;
        this.isDefault = isDefault;
        this.supportsTools = supportsTools;
        this.supportsVision = supportsVision;
        this.cooldownStatus = cooldownStatus != null ? cooldownStatus : "Available";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("contextLength", contextLength);
        o.put("isDefault", isDefault);
        o.put("supportsTools", supportsTools);
        o.put("supportsVision", supportsVision);
        o.put("cooldownStatus", cooldownStatus);
        return o;
    }

    public static ModelDescriptor fromJson(JSONObject o) {
        if (o == null) return null;
        return new ModelDescriptor(
                o.optString("id"),
                o.optString("name"),
                o.optInt("contextLength", 128000),
                o.optBoolean("isDefault", false),
                o.optBoolean("supportsTools", true),
                o.optBoolean("supportsVision", false),
                o.optString("cooldownStatus", "Available")
        );
    }
}
