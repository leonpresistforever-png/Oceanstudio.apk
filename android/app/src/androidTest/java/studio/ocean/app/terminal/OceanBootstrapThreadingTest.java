package studio.ocean.app.terminal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Regression coverage for the physical-device ANR: bootstrap I/O is forbidden on main. */
@RunWith(AndroidJUnit4.class)
public final class OceanBootstrapThreadingTest {
    @Test public void installerFailsFastOnAndroidMainThread() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { new OceanBootstrapInstaller(context).install(); }
            catch (Throwable error) { failure.set(error); }
        });
        assertNotNull("main-thread bootstrap must be rejected", failure.get());
        assertTrue(failure.get().getMessage().contains("must not run on Android main thread"));
    }
}
