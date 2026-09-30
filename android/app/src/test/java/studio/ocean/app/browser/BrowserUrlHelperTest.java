package studio.ocean.app.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BrowserUrlHelperTest {

    @Test
    public void emptyInputUsesHome() {
        assertEquals(BrowserUrlHelper.DEFAULT_HOME, BrowserUrlHelper.normalizeInput(""));
        assertEquals(BrowserUrlHelper.DEFAULT_HOME, BrowserUrlHelper.normalizeInput("   "));
    }

    @Test
    public void bareDomainGetsHttps() {
        assertEquals("https://example.com", BrowserUrlHelper.normalizeInput("example.com"));
    }

    @Test
    public void searchQueryUsesDuckDuckGo() {
        assertEquals("https://duckduckgo.com/?q=hello+world", BrowserUrlHelper.normalizeInput("hello world"));
    }

    @Test
    public void secureDetection() {
        assertTrue(BrowserUrlHelper.isSecureUrl("https://ocean.studio"));
        assertFalse(BrowserUrlHelper.isSecureUrl("http://localhost:8080"));
    }
}
