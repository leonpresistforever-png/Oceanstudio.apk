package studio.ocean.app.browser.privacy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class PermissionFirewallTest {

    private PermissionFirewall firewall;

    @Before
    public void setUp() {
        firewall = new PermissionFirewall();
    }

    @Test
    public void permissionsDeniedByDefault() {
        assertFalse(firewall.isPermissionGranted("https://camera-site.com", "android.webkit.resource.VIDEO_CAPTURE"));
        assertFalse(firewall.isPermissionGranted("https://geo-site.com", "android.webkit.resource.GEOLOCATION"));
    }

    @Test
    public void sessionGrantsAreVolatileAndErasable() {
        String origin = "https://meeting.com";
        String cameraRes = "android.webkit.resource.VIDEO_CAPTURE";

        firewall.grantPermissionForSession(origin, cameraRes);
        assertTrue(firewall.isPermissionGranted(origin, cameraRes));

        firewall.clearSessionPermissions();
        assertFalse(firewall.isPermissionGranted(origin, cameraRes));
    }
}
