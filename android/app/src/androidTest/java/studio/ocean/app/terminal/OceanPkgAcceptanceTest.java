package studio.ocean.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import studio.ocean.app.OceanPaths;

/** Physical-device acceptance for the signed Ocean repository and real on-device apt/dpkg. */
@RunWith(AndroidJUnit4.class)
public final class OceanPkgAcceptanceTest {
    @Test public void oceanHelloCompletesRealInstallRemoveReinstallCycle() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        OceanPaths paths = new OceanPaths(context);
        assertTrue("verified Ocean runtime required", OceanRuntimeState.isInstalled(context));
        String bash = new File(paths.prefix(), "bin/bash").getAbsolutePath();
        String command = "set -e; pkg update; pkg search ocean-hello; pkg install -y ocean-hello; "
                + "command -v ocean-hello; ocean-hello; dpkg -s ocean-hello; "
                + "pkg remove -y ocean-hello; pkg install -y ocean-hello; "
                + "apt update; apt-cache search ocean; dpkg --version; echo OCEAN_PKG_ACCEPTANCE_OK";
        File log = new File(new File(context.getFilesDir(), "logs"), "pkg-acceptance.log");
        long handle = NativePty.create(bash, new String[]{bash, "--noprofile", "--norc", "-c", command},
                OceanEnvironment.create(context, bash), paths.home().getAbsolutePath(), 24, 100, log.getAbsolutePath());
        assertTrue("PTY creation", handle != 0);
        byte[] buffer = new byte[8192]; StringBuilder output = new StringBuilder();
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            int count = NativePty.read(handle, buffer);
            if (count > 0) output.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
            else break;
        }
        int exit = NativePty.pollExit(handle);
        NativePty.close(handle); NativePty.destroy(handle);
        assertEquals("real pkg output:\n" + output, 0, exit);
        assertTrue("real package executable output", output.toString().contains("Ocean package runtime works."));
        assertTrue("acceptance marker", output.toString().contains("OCEAN_PKG_ACCEPTANCE_OK"));
    }
}
