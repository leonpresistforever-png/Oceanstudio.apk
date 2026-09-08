package studio.ocean.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Test;

/** Host-side regression coverage for bootstrap link containment and prefix normalization. */
public final class OceanBootstrapLinkPolicyTest {
    @Test public void tarStructuralDirectoriesNormalizeInsideOceanPrefix() throws Exception {
        assertEquals("usr", OceanBootstrapInstaller.normalizeArchivePath("data/", "entry").toString());
        assertEquals("usr", OceanBootstrapInstaller.normalizeArchivePath("data/data/", "entry").toString());
        assertEquals("usr", OceanBootstrapInstaller.normalizeArchivePath(
                "data/data/studio.ocean.app/files/", "entry").toString());
        assertEquals("usr/bin/bash", OceanBootstrapInstaller.normalizeArchivePath(
                "data/data/studio.ocean.app/files/usr/bin/bash", "entry").toString());
    }

    @Test public void foreignApplicationArchiveIsRejected() {
        assertThrows(IOException.class, () -> OceanBootstrapInstaller.normalizeArchivePath(
                "data/data/com.termux/files/usr/bin/bash", "entry"));
        assertThrows(IOException.class, () -> OceanBootstrapInstaller.normalizeArchivePath(
                "data/data/another.app/files/usr/bin/bash", "entry"));
    }

    @Test public void traversalArchiveEntryIsRejected() {
        assertThrows(IOException.class, () -> OceanBootstrapInstaller.normalizeArchivePath(
                "data/data/studio.ocean.app/files/usr/../../escape", "entry"));
    }

    @Test public void canonicalOceanPrefixLinkBecomesRelative() throws Exception {
        assertEquals("bzdiff", OceanBootstrapInstaller.safeSymlinkTarget(
                Paths.get("usr/bin/bzcmp"),
                "/data/data/studio.ocean.app/files/usr/bin/bzdiff"));
    }

    @Test public void safeRelativeParentLinkRemainsContained() throws Exception {
        assertEquals("../libexec/tool", OceanBootstrapInstaller.safeSymlinkTarget(
                Paths.get("usr/bin/tool"), "../libexec/tool"));
    }

    @Test public void relativeEscapeIsRejected() {
        assertThrows(IOException.class, () -> OceanBootstrapInstaller.safeSymlinkTarget(
                Paths.get("usr/bin/tool"), "../../../data/secret"));
    }

    @Test public void externalAbsoluteLinkIsRejected() {
        assertThrows(IOException.class, () -> OceanBootstrapInstaller.safeSymlinkTarget(
                Paths.get("usr/bin/tool"), "/system/bin/sh"));
    }
}
