package studio.ocean.app.providers.gateway;

import org.json.JSONObject;
import studio.ocean.app.providers.model.QuotaSnapshot;

/** Converts only quota fields reported for this model or account-wide windows. */
public final class GatewayQuota {
    private GatewayQuota() { }
    public static QuotaSnapshot parse(JSONObject response, String model) {
        String plan = response.optString("plan", "");
        JSONObject quotas = response.optJSONObject("quotas");
        String bare = model.substring(model.indexOf('/') + 1);
        JSONObject selected = quotas == null ? null : quotas.optJSONObject(bare);
        if (selected == null && quotas != null) {
            for (String name : new String[]{"session", "weekly", "five_hour", "5h", "7d"}) {
                JSONObject value = quotas.optJSONObject(name);
                if (value != null && Double.isFinite(remainingPercent(value))
                        && (selected == null || remainingPercent(value) < remainingPercent(selected))) selected = value;
            }
        }
        if (selected == null || !selected.optBoolean("fractionReported", true)) return unknown(plan);
        Long reset = null;
        try {
            String time = selected.optString("resetAt");
            if (!time.isEmpty() && !time.equals("null")) reset = java.time.Instant.parse(time).toEpochMilli();
        } catch (Exception ignored) { }
        double remaining = remainingPercent(selected);
        if (Double.isFinite(remaining) && remaining >= 0 && remaining <= 100)
            return QuotaSnapshot.reported(100 - remaining, remaining, QuotaSnapshot.Unit.PERCENT,
                    reset, plan, "gateway/provider-quota");
        double used = selected.optDouble("used", Double.NaN), total = selected.optDouble("total", Double.NaN);
        if (Double.isFinite(used) && Double.isFinite(total) && used >= 0 && total > 0)
            return QuotaSnapshot.reported(used, Math.max(0, total - used), QuotaSnapshot.Unit.PROVIDER_DEFINED,
                    reset, plan, "gateway/provider-quota");
        return unknown(plan);
    }
    private static double remainingPercent(JSONObject value) {
        if (value.has("remainingPercentage")) return value.optDouble("remainingPercentage", Double.NaN);
        if (value.has("usedPercentage")) return 100 - value.optDouble("usedPercentage", Double.NaN);
        // Codex's real usage API returns normalized {used,total,remaining} windows.
        double used = value.optDouble("used", Double.NaN), total = value.optDouble("total", Double.NaN);
        if (Double.isFinite(used) && Double.isFinite(total) && used >= 0 && total > 0)
            return (total - used) * 100 / total;
        return Double.NaN;
    }
    private static QuotaSnapshot unknown(String plan) { return QuotaSnapshot.unknown(plan, "gateway/provider-quota"); }
}
