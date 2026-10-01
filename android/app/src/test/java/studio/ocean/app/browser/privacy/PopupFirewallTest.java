package studio.ocean.app.browser.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;

public final class PopupFirewallTest {

    private PopupFirewall firewall;

    @Before
    public void setUp() {
        firewall = new PopupFirewall();
    }

    @Test
    public void blocksNonGesturePopupsByDefault() {
        AtomicBoolean allowed = new AtomicBoolean(true);
        boolean result = firewall.evaluatePopup("https://ad.com/popup", false, null, (allow, remember) -> allowed.set(allow));

        assertFalse(result);
        assertFalse(allowed.get());
        assertEquals(1, firewall.getBlockedCount());
    }

    @Test
    public void triggersPromptOnGesturePopup() {
        AtomicBoolean promptCalled = new AtomicBoolean(false);
        AtomicBoolean decisionAllowed = new AtomicBoolean(false);

        firewall.evaluatePopup(
                "https://login.com/oauth",
                true,
                (url, host, isUserGesture, callback) -> {
                    promptCalled.set(true);
                    callback.onDecision(true, true);
                },
                (allow, remember) -> decisionAllowed.set(allow)
        );

        assertTrue(promptCalled.get());
        assertTrue(decisionAllowed.get());
        assertTrue(firewall.isSessionAllowed("login.com"));
    }

    @Test
    public void rateLimitsRapidRedirectLoops() {
        for (int i = 0; i < 3; i++) {
            firewall.isRateLimited();
        }
        assertTrue(firewall.isRateLimited());
        assertTrue(firewall.getBlockedCount() > 0);
    }
}
