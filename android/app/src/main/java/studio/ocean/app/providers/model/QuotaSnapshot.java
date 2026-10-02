package studio.ocean.app.providers.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable snapshot of provider quota, usage, and entitlements.
 * Zero-deception policy: never manufactures synthetic percentages or false "Unmetered" claims (PDF 5 §6, §12).
 */
public final class QuotaSnapshot {
    public enum Unit {
        TOKENS, REQUESTS, CREDITS, PERCENT, PROVIDER_DEFINED
    }

    public enum Confidence {
        EXACT, PROVIDER_REPORTED, ESTIMATED, UNKNOWN
    }

    public final Double used;
    public final Double remaining;
    public final Unit unit;
    public final Long resetsAtEpochMs;
    public final Confidence confidence;
    public final String planName;
    public final String planTier;
    public final String source;
    public final long capturedAtEpochMs;

    public QuotaSnapshot(Double used, Double remaining, Unit unit, Long resetsAtEpochMs,
                         Confidence confidence, String planName, String source, long capturedAtEpochMs) {
        this.used = used;
        this.remaining = remaining;
        this.unit = unit != null ? unit : Unit.PROVIDER_DEFINED;
        this.resetsAtEpochMs = resetsAtEpochMs;
        this.confidence = confidence != null ? confidence : Confidence.UNKNOWN;
        this.planName = planName != null ? planName : "";
        this.planTier = this.planName;
        this.source = source != null ? source : "unknown";
        this.capturedAtEpochMs = capturedAtEpochMs;
    }

    public static QuotaSnapshot unknown(String planName, String source) {
        return new QuotaSnapshot(null, null, Unit.PROVIDER_DEFINED, null,
                Confidence.UNKNOWN, planName, source, System.currentTimeMillis());
    }

    public static QuotaSnapshot exact(double used, double remaining, Unit unit, Long resetsAtEpochMs,
                                      String planName, String source) {
        return new QuotaSnapshot(used, remaining, unit, resetsAtEpochMs,
                Confidence.EXACT, planName, source, System.currentTimeMillis());
    }

    public static QuotaSnapshot reported(Double used, Double remaining, Unit unit, Long resetsAtEpochMs,
                                         String planName, String source) {
        return new QuotaSnapshot(used, remaining, unit, resetsAtEpochMs,
                Confidence.PROVIDER_REPORTED, planName, source, System.currentTimeMillis());
    }

    public static QuotaSnapshot unlimited(String planName, String source) {
        return new QuotaSnapshot(0.0, Double.POSITIVE_INFINITY, Unit.REQUESTS, null,
                Confidence.EXACT, planName, source, System.currentTimeMillis());
    }

    public String formatSummary() {
        if (confidence == Confidence.UNKNOWN) {
            return planName != null && !planName.isEmpty()
                    ? planName + " · Usage not reported by provider / Usage unavailable"
                    : "Usage unavailable: not reported by provider";
        }
        if (confidence == Confidence.EXACT && remaining != null) {
            return "Quota: " + remaining + " " + unit.name().toLowerCase() + " remaining (Exact)";
        }
        if (confidence == Confidence.ESTIMATED && remaining != null) {
            return "Quota: ~" + remaining + " " + unit.name().toLowerCase() + " remaining (Estimate)";
        }
        if (confidence == Confidence.PROVIDER_REPORTED) {
            if (remaining != null) {
                return "Quota: " + remaining + " " + unit.name().toLowerCase() + " remaining (Reported by " + source + ")";
            }
            return (planName != null && !planName.isEmpty() ? planName : "Reported") + " · " + source;
        }
        if (remaining != null) {
            return "Quota: " + remaining + " " + unit.name().toLowerCase() + " remaining (" + confidence.name().toLowerCase() + ")";
        }
        return (planName != null && !planName.isEmpty() ? planName : "Standard") + " · " + source;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        if (used != null) o.put("used", used);
        if (remaining != null) o.put("remaining", remaining);
        o.put("unit", unit.name());
        if (resetsAtEpochMs != null) o.put("resetsAt", resetsAtEpochMs);
        o.put("confidence", confidence.name());
        o.put("planName", planName);
        o.put("source", source);
        o.put("capturedAt", capturedAtEpochMs);
        return o;
    }

    public static QuotaSnapshot fromJson(JSONObject o) {
        if (o == null) return unknown("Unknown", "cache");
        Double used = o.has("used") && !o.isNull("used") ? o.optDouble("used") : null;
        Double remaining = o.has("remaining") && !o.isNull("remaining") ? o.optDouble("remaining") : null;
        Unit unit = Unit.valueOf(o.optString("unit", Unit.PROVIDER_DEFINED.name()));
        Long resetsAt = o.has("resetsAt") && !o.isNull("resetsAt") ? o.optLong("resetsAt") : null;
        Confidence confidence = Confidence.valueOf(o.optString("confidence", Confidence.UNKNOWN.name()));
        String planName = o.optString("planName", "");
        String source = o.optString("source", "");
        long capturedAt = o.optLong("capturedAt", System.currentTimeMillis());
        return new QuotaSnapshot(used, remaining, unit, resetsAt, confidence, planName, source, capturedAt);
    }
}
