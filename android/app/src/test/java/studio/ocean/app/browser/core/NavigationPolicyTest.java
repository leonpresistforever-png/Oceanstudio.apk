package studio.ocean.app.browser.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NavigationPolicyTest {

    @Test
    public void cleansCommonTrackingParameters() {
        String input = "https://example.com/item?id=12345&utm_source=newsletter&utm_medium=email&fbclid=abcdef123&page=2";
        String expected = "https://example.com/item?id=12345&page=2";
        assertEquals(expected, NavigationPolicy.cleanUrl(input, true));
    }

    @Test
    public void preservesFragmentWhenCleaningParameters() {
        String input = "https://example.com/page?gclid=xyz&utm_campaign=winter#section-2";
        String expected = "https://example.com/page#section-2";
        assertEquals(expected, NavigationPolicy.cleanUrl(input, true));
    }

    @Test
    public void leavesUrlUntouchedWhenNoTrackingParametersPresent() {
        String clean = "https://example.com/search?q=open+source&lang=en";
        assertEquals(clean, NavigationPolicy.cleanUrl(clean, true));
    }

    @Test
    public void upgradesInsecureHttpToHttps() {
        assertEquals("https://news.ycombinator.com", NavigationPolicy.upgradeToHttps("http://news.ycombinator.com"));
        assertEquals("https://example.com/path", NavigationPolicy.upgradeToHttps("https://example.com/path"));
    }

    @Test
    public void detectsLocalhostAndRuntimeLoopbackEndpoints() {
        assertTrue(NavigationPolicy.isLocalAddress("http://localhost:8080"));
        assertTrue(NavigationPolicy.isLocalAddress("http://127.0.0.1:3000/api"));
        assertTrue(NavigationPolicy.isLocalAddress("http://[::1]:5000/"));
        assertTrue(NavigationPolicy.isLocalAddress("http://mydev.local:9000/"));
        assertFalse(NavigationPolicy.isLocalAddress("https://example.com"));
        assertFalse(NavigationPolicy.isLocalAddress("https://1.1.1.1"));
    }

    @Test
    public void identifiesDangerousWebSchemes() {
        assertTrue(NavigationPolicy.isDangerousWebScheme("javascript:alert(1)"));
        assertTrue(NavigationPolicy.isDangerousWebScheme("file:///etc/passwd"));
        assertTrue(NavigationPolicy.isDangerousWebScheme("content://media/external/images/media"));
        assertFalse(NavigationPolicy.isDangerousWebScheme("https://ocean.studio"));
        assertFalse(NavigationPolicy.isDangerousWebScheme("http://localhost:8080"));
    }
}
