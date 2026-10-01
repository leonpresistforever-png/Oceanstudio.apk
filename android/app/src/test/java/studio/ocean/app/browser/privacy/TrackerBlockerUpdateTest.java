package studio.ocean.app.browser.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TrackerBlockerUpdateTest {

    @Test
    public void blocksPureTrackerDomains() {
        TrackerBlocker blocker = new TrackerBlocker();
        assertTrue(blocker.shouldBlock("https://graph.facebook.com/api/test", "https://news.com"));
        assertTrue(blocker.shouldBlock("https://www.google-analytics.com/analytics.js", "https://shopping.com"));
        assertTrue(blocker.shouldBlock("https://criteo.net/tags", "https://store.com"));
    }

    @Test
    public void blocksThirdPartyTrackingPaths() {
        TrackerBlocker blocker = new TrackerBlocker();
        assertTrue(blocker.shouldBlock("https://thirdparty.net/tr?event=click", "https://shop.com"));
        assertTrue(blocker.shouldBlock("https://metrics.cloud/telemetry/report", "https://shop.com"));
        assertTrue(blocker.shouldBlock("https://beacon.service.com/collect", "https://shop.com"));
    }

    @Test
    public void preservesLocalhostAndLoopbackServers() {
        TrackerBlocker blocker = new TrackerBlocker();
        assertFalse(blocker.shouldBlock("http://localhost:3000/telemetry", "http://localhost:3000"));
        assertFalse(blocker.shouldBlock("http://127.0.0.1:8080/collect", "http://127.0.0.1:8080"));
        assertFalse(blocker.shouldBlock("http://devserver.local/pageview", "http://devserver.local"));
    }

    @Test
    public void respectsSiteAllowlist() {
        TrackerBlocker blocker = new TrackerBlocker();
        String mainSite = "partner.portal.com";
        blocker.setSiteAllowed(mainSite, true);
        assertTrue(blocker.isSiteAllowed(mainSite));
        assertFalse(blocker.shouldBlock("https://google-analytics.com/analytics.js", "https://" + mainSite));
    }
}
