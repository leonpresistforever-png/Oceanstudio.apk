package studio.ocean.app.providers.gateway;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import studio.ocean.app.providers.model.QuotaSnapshot;

public class GatewayQuotaTest {
    @Test public void reportsSelectedModelAndProviderPlan() throws Exception {
        QuotaSnapshot quota = GatewayQuota.parse(new JSONObject("{\"plan\":\"Google AI Pro\",\"quotas\":{\"gemini-flash\":{\"remainingPercentage\":72.5,\"resetAt\":\"2026-10-04T12:00:00Z\"},\"claude\":{\"remainingPercentage\":0}}}"), "antigravity/gemini-flash");
        assertEquals(72.5, quota.remaining, 0);
        assertEquals("Google AI Pro", quota.planName);
        assertEquals(QuotaSnapshot.Unit.PERCENT, quota.unit);
        assertEquals(QuotaSnapshot.Confidence.PROVIDER_REPORTED, quota.confidence);
        assertNotNull(quota.resetsAtEpochMs);
        assertEquals(72.5, new JSONObject(quota.toJson().toString()).getDouble("remaining"), 0);
    }
    @Test public void accountWindowsUseMostRestrictiveReportedWindow() throws Exception {
        QuotaSnapshot quota = GatewayQuota.parse(new JSONObject("{\"plan\":\"Plus\",\"quotas\":{\"session\":{\"remainingPercentage\":80},\"weekly\":{\"usedPercentage\":90}}}"), "codex/test-model");
        assertEquals(10, quota.remaining, 0);
    }
    @Test public void unknownOrUnrelatedQuotaIsNotInvented() throws Exception {
        for (String input : new String[]{"{}", "{\"quotas\":{\"other-model\":{\"remainingPercentage\":0}}}", "{\"quotas\":{\"model\":{\"fractionReported\":false,\"remainingPercentage\":0}}}", "{\"quotas\":{\"model\":{\"used\":0,\"total\":0}}}"}) {
            QuotaSnapshot quota = GatewayQuota.parse(new JSONObject(input), "antigravity/model");
            assertNull(quota.remaining);
            assertEquals(QuotaSnapshot.Confidence.UNKNOWN, quota.confidence);
        }
    }
    @Test public void localQuotaSerializesWithoutNonFiniteNumbers() throws Exception {
        QuotaSnapshot quota = QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED, null,
                "On-device", "local-runtime");
        assertNotNull(new JSONObject(quota.toJson().toString()));
    }
}
