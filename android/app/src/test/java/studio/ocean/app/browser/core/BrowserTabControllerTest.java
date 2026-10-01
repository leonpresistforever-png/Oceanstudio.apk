package studio.ocean.app.browser.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import studio.ocean.app.browser.BrowserTab;

public final class BrowserTabControllerTest {

    private BrowserTabController controller;

    @Before
    public void setUp() {
        controller = new BrowserTabController();
    }

    @Test
    public void addsAndSelectsTabs() {
        assertEquals(0, controller.getTabCount());
        controller.addTab("https://example.com");
        assertEquals(1, controller.getTabCount());
        assertEquals(0, controller.getCurrentTabIndex());

        controller.addTab("https://ocean.studio");
        assertEquals(2, controller.getTabCount());
        assertEquals(1, controller.getCurrentTabIndex());
        assertEquals("https://ocean.studio", controller.getCurrentTab().url);

        controller.selectTab(0);
        assertEquals(0, controller.getCurrentTabIndex());
        assertEquals("https://example.com", controller.getCurrentTab().url);
    }

    @Test
    public void closesAndReopensRecentlyClosedTabs() {
        controller.addTab("https://tab1.com");
        controller.addTab("https://tab2.com");
        controller.addTab("https://tab3.com");

        controller.closeTab(1); // Close tab2
        assertEquals(2, controller.getTabCount());
        assertEquals(1, controller.getRecentlyClosedCount());

        BrowserTab restored = controller.reopenLastClosedTab();
        assertNotNull(restored);
        assertEquals("https://tab2.com", restored.url);
        assertEquals(3, controller.getTabCount());
        assertEquals(0, controller.getRecentlyClosedCount());
    }

    @Test
    public void handlesPinnedAndLockedStates() {
        controller.addTab("https://sensitive.com");
        controller.setPinned(0, true);
        controller.setLocked(0, true);

        BrowserTab tab = controller.getTab(0);
        assertNotNull(tab);
        assertTrue(tab.pinned);
        assertTrue(tab.locked);
    }

    @Test
    public void duplicatesTabs() {
        controller.addTab("https://original.com");
        controller.getCurrentTab().title = "Original Title";

        int dupIndex = controller.duplicateTab(0);
        assertEquals(1, dupIndex);
        assertEquals(2, controller.getTabCount());

        BrowserTab duplicated = controller.getTab(dupIndex);
        assertNotNull(duplicated);
        assertEquals("https://original.com", duplicated.url);
        assertEquals("Original Title", duplicated.title);
    }

    @Test
    public void searchesTabsByTitleAndUrl() {
        controller.addTab("https://alpha.com");
        controller.getCurrentTab().title = "Alpha Project";

        controller.addTab("https://beta.com");
        controller.getCurrentTab().title = "Beta Testing";

        List<BrowserTab> matches = controller.searchTabs("alpha");
        assertEquals(1, matches.size());
        assertEquals("Alpha Project", matches.get(0).title);

        List<BrowserTab> urlMatches = controller.searchTabs("beta.com");
        assertEquals(1, urlMatches.size());
    }

    @Test
    public void clearAllTabsWipesAllState() {
        controller.addTab("https://tab1.com");
        controller.addTab("https://tab2.com");
        controller.closeTab(0);

        controller.clearAllTabs();
        assertEquals(0, controller.getTabCount());
        assertEquals(0, controller.getRecentlyClosedCount());
        assertNull(controller.getCurrentTab());
    }
}
