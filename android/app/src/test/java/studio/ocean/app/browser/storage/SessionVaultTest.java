package studio.ocean.app.browser.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import studio.ocean.app.browser.BrowserTab;

public final class SessionVaultTest {

    private File tempDir;
    private SessionVault vault;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ocean-session-vault-test").toFile();
        vault = new SessionVault(tempDir);
    }

    @Test
    public void savesAndListsSessionsWithoutSensitiveData() {
        List<BrowserTab> tabs = new ArrayList<>();
        BrowserTab t1 = new BrowserTab("https://alpha.com");
        t1.title = "Alpha";
        t1.pinned = true;
        tabs.add(t1);

        BrowserTab t2 = new BrowserTab("https://beta.com");
        t2.title = "Beta";
        tabs.add(t2);

        boolean saved = vault.saveSession("My Research", tabs, "tor_orbot");
        assertTrue(saved);

        List<SessionVault.SavedSession> list = vault.listSessions();
        assertEquals(1, list.size());
        SessionVault.SavedSession s = list.get(0);
        assertEquals("My Research", s.name);
        assertEquals("tor_orbot", s.networkProfile);
        assertEquals(2, s.tabs.size());

        SessionVault.SavedTab st1 = s.tabs.get(0);
        assertEquals("https://alpha.com", st1.url);
        assertEquals("Alpha", st1.title);
        assertTrue(st1.pinned);
    }

    @Test
    public void deletesSessionSuccessfully() {
        List<BrowserTab> tabs = new ArrayList<>();
        tabs.add(new BrowserTab("https://temp.com"));
        vault.saveSession("To Delete", tabs, null);

        assertNotNull(vault.getSession("To Delete"));
        boolean deleted = vault.deleteSession("To Delete");
        assertTrue(deleted);
        assertEquals(0, vault.listSessions().size());
    }
}
