package studio.ocean.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public final class OceanShebangPolicyTest {
    @Test public void rewritesUsrBinEnvToOceanPrefix() {
        String line = "#!/usr/bin/env bash";
        String rewritten = OceanShebangPolicy.rewriteShebangLine(line);
        assertEquals("#!" + OceanShebangPolicy.PREFIX + "/bin/bash", rewritten);
    }

    @Test public void rewritesUsrBinEnvPython3() {
        String line = "#!/usr/bin/env python3";
        assertEquals("#!" + OceanShebangPolicy.PREFIX + "/bin/python3",
                OceanShebangPolicy.rewriteShebangLine(line));
    }

    @Test public void leavesOceanBashShebangUntouched() {
        String line = "#!/data/data/studio.ocean.app/files/usr/bin/bash";
        assertNull(OceanShebangPolicy.rewriteShebangLine(line));
    }

    @Test public void rewritesOmnirouteStyleNodeEnv() {
        String line = "#!/usr/bin/env node";
        assertEquals("#!" + OceanShebangPolicy.PREFIX + "/bin/node",
                OceanShebangPolicy.rewriteShebangLine(line));
    }
}
