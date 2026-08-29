package studio.ocean.app.terminal;

import static org.junit.Assert.*;
import org.junit.Test;

public class LocalProcessDiagnosticsTest {
    @Test public void acceptsOnlyExecutablesInsideOceanPrefix(){
        String prefix="/data/data/studio.ocean.app/files/usr";
        assertTrue(LocalProcessDiagnostics.isWithinPrefix(prefix+"/bin/python",prefix));
        assertFalse(LocalProcessDiagnostics.isWithinPrefix("/system/bin/sh",prefix));
        assertFalse(LocalProcessDiagnostics.isWithinPrefix(prefix+"-backup/bin/bash",prefix));
        assertFalse(LocalProcessDiagnostics.isWithinPrefix("https://example.invalid/exec",prefix));
    }
}
