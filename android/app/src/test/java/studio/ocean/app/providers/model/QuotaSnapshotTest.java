package studio.ocean.app.providers.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class QuotaSnapshotTest {

    @Test
    public void unknownQuotaNeverDisplaysUnmetered() {
        QuotaSnapshot q = QuotaSnapshot.unknown("Pro Plan", "cli");
        assertEquals(QuotaSnapshot.Confidence.UNKNOWN, q.confidence);
        String summary = q.formatSummary();
        assertFalse("Summary must not contain 'Unmetered'", summary.toLowerCase().contains("unmetered"));
        assertTrue("Summary must state usage unavailable", summary.contains("Usage unavailable"));
    }

    @Test
    public void formatsExactAndReportedRemaining() {
        QuotaSnapshot exact = QuotaSnapshot.exact(500, 1500, QuotaSnapshot.Unit.REQUESTS, null, "Team", "api");
        assertEquals(QuotaSnapshot.Confidence.EXACT, exact.confidence);
        assertTrue(exact.formatSummary().contains("1500.0 requests remaining"));

        QuotaSnapshot reported = QuotaSnapshot.reported(100.0, 900.0, QuotaSnapshot.Unit.CREDITS, 1700000000000L, "Enterprise", "portal");
        assertEquals(QuotaSnapshot.Confidence.PROVIDER_REPORTED, reported.confidence);
        assertTrue(reported.formatSummary().contains("900.0 credits remaining"));
    }

    @Test
    public void roundTripsJsonCorrectly() throws Exception {
        QuotaSnapshot original = new QuotaSnapshot(
                250.0, 750.0, QuotaSnapshot.Unit.TOKENS, 1750000000000L,
                QuotaSnapshot.Confidence.PROVIDER_REPORTED, "Claude Pro", "claude-cli", 1700000000L
        );

        JSONObject json = original.toJson();
        QuotaSnapshot restored = QuotaSnapshot.fromJson(json);

        assertNotNull(restored);
        assertEquals(original.used, restored.used);
        assertEquals(original.remaining, restored.remaining);
        assertEquals(original.unit, restored.unit);
        assertEquals(original.resetsAtEpochMs, restored.resetsAtEpochMs);
        assertEquals(original.confidence, restored.confidence);
        assertEquals(original.planName, restored.planName);
        assertEquals(original.source, restored.source);
    }
}
