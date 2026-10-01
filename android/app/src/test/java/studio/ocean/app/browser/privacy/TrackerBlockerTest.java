package studio.ocean.app.browser.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class TrackerBlockerTest {

    private TrackerBlocker blocker;

    @Before
    public void setUp() {
        blocker = new TrackerBlocker();
    }

    @Test
    public void blocksKnownTrackingDomains() {
        assertTrue(blocker.shouldBlock("https://www.google-analytics.com/analytics.js", "https://news.com"));
        assertTrue(blocker.shouldBlock("https://connect.facebook.net/en_US/fbevents.js", "https://store.com"));
        assertTrue(blocker.shouldBlock("https://criteo.com/tag.js", "https://shop.com"));
        assertTrue(blocker.shouldBlock("https://segment.io/v1/p", "https://app.com"));
        assertEquals(4, blocker.getBlockedCount());
    }

    @Test
    public void neverBlocksLocalhostOrLocalDeveloperEndpoints() {
        assertFalse(blocker.shouldBlock("http://localhost:8080/bundle.js", "http://localhost:8080"));
        assertFalse(blocker.shouldBlock("http://127.0.0.1:3000/api/data", "http://127.0.0.1:3000"));
        assertFalse(blocker.shouldBlock("http://devsite.local/styles.css", "http://devsite.local"));
    }

    @Test
    public void allowsWhitelistedSiteOverride() {
        String mainSite = "https://broken-site.com";
        String tracker = "https://www.google-analytics.com/analytics.js";

        assertTrue(blocker.shouldBlock(tracker, mainSite));

        blocker.setSiteAllowed("broken-site.com", true);
        assertTrue(blocker.isSiteAllowed("broken-site.com"));
        assertFalse(blocker.shouldBlock(tracker, mainSite));

        blocker.setSiteAllowed("broken-site.com", false);
        assertTrue(blocker.shouldBlock(tracker, mainSite));
    }

    @Test
    public void allowsDisablingFirewall() {
        blocker.setEnabled(false);
        assertFalse(blocker.shouldBlock("https://www.google-analytics.com/analytics.js", "https://example.com"));
    }
}
