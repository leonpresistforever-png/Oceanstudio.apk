package studio.ocean.app.browser.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class BrowserNetworkPolicyTest {

    private BrowserNetworkPolicy policy;

    @Before
    public void setUp() {
        policy = new BrowserNetworkPolicy();
    }

    @Test
    public void defaultsToDirectRoute() {
        assertEquals(BrowserNetworkPolicy.Mode.DIRECT, policy.getMode());
        assertTrue(policy.getRouteStatusSummary().contains("Direct"));
    }

    @Test
    public void preservesLocalhostFromProxyOrTunnelBypass() {
        policy.setProxy("198.51.100.25", 8080);
        assertEquals(BrowserNetworkPolicy.Mode.PROXY, policy.getMode());

        assertTrue(BrowserNetworkPolicy.shouldBypassProxy("http://localhost:8080"));
        assertTrue(BrowserNetworkPolicy.shouldBypassProxy("http://127.0.0.1:3000"));
        assertTrue(BrowserNetworkPolicy.shouldBypassProxy("http://[::1]:5173"));
        assertTrue(BrowserNetworkPolicy.shouldBypassProxy("http://runtime.local:9000"));
        assertFalse(BrowserNetworkPolicy.shouldBypassProxy("https://google.com"));
    }

    @Test
    public void killSwitchBlocksNavigationOnlyWhenTunnelFails() {
        policy.setVpnTunnel();
        policy.setKillSwitchEnabled(true);
        policy.setTunnelConnected(true);

        assertFalse(policy.isNavigationBlockedByKillSwitch("https://target.com"));

        // Simulate tunnel disconnect
        policy.setTunnelConnected(false);
        assertTrue(policy.isNavigationBlockedByKillSwitch("https://target.com"));

        // Localhost must NEVER be blocked even if external tunnel dies
        assertFalse(policy.isNavigationBlockedByKillSwitch("http://localhost:8080"));
        assertFalse(policy.isNavigationBlockedByKillSwitch("http://127.0.0.1:3000"));
    }
}
