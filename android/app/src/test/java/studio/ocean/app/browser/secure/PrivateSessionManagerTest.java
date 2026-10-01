package studio.ocean.app.browser.secure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PrivateSessionManagerTest {

    @Test
    public void initializesWithRandomUuidAndProfileName() {
        PrivateSessionManager session1 = new PrivateSessionManager();
        PrivateSessionManager session2 = new PrivateSessionManager();

        assertNotNull(session1.getSessionUuid());
        assertTrue(session1.getProfileName().startsWith("private_"));
        assertNotEquals(session1.getSessionUuid(), session2.getSessionUuid());
        assertNotEquals(session1.getProfileName(), session2.getProfileName());
    }

    @Test
    public void presetSwitchingEnforcesRules() {
        PrivateSessionManager session = new PrivateSessionManager();
        assertEquals(PrivateSessionManager.Preset.BALANCED, session.getPreset());
        assertFalse(session.isHttpsOnly());

        session.setPreset(PrivateSessionManager.Preset.STRICT);
        assertEquals(PrivateSessionManager.Preset.STRICT, session.getPreset());
        assertTrue(session.isHttpsOnly());
    }

    @Test
    public void tracksBlockedCounters() {
        PrivateSessionManager session = new PrivateSessionManager();
        assertEquals(0, session.getTrackerBlockCount());
        assertEquals(0, session.getPopupBlockCount());

        session.incrementTrackerBlocked();
        session.incrementTrackerBlocked();
        session.incrementPopupBlocked();

        assertEquals(2, session.getTrackerBlockCount());
        assertEquals(1, session.getPopupBlockCount());
    }

    @Test
    public void rotatesIdentityWithCleanState() {
        PrivateSessionManager session = new PrivateSessionManager();
        session.incrementTrackerBlocked();
        String oldProfile = session.getProfileName();

        String newProfile = session.rotateIdentity();
        assertNotEquals(oldProfile, newProfile);
        assertTrue(newProfile.startsWith("private_"));
        assertEquals(0, session.getTrackerBlockCount());
    }
}
