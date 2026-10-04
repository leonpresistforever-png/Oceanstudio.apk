package studio.ocean.app.terminal;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class OceanShebangRepairTest {
    @Test public void npmSymlinkAndRelativeImportsStayAtTheirCanonicalLocation() throws Exception {
        Path root = Files.createTempDirectory("ocean-shebang-test");
        Path bin = Files.createDirectories(root.resolve("bin"));
        Path target = root.resolve("lib/node_modules/npm/bin/npm-cli.js");
        Files.createDirectories(target.getParent());
        byte[] original = "#!/usr/bin/env node\nrequire('../lib/cli.js')(process)\n".getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);
        Path link = bin.resolve("npm");
        Files.createSymbolicLink(link, Path.of("../lib/node_modules/npm/bin/npm-cli.js"));
        OceanShebangRepair.ensurePrefixScripts(root.toFile());
        assertTrue(Files.isSymbolicLink(link));
        String repaired = Files.readString(target);
        assertTrue(repaired.startsWith("#!" + OceanShebangPolicy.PREFIX + "/bin/node\n"));
        assertTrue(repaired.endsWith("require('../lib/cli.js')(process)\n"));
        assertEquals(target.toRealPath(), link.toRealPath());
    }

    @Test public void interpreterRepairPreservesScriptsLargerThan64KiB() throws Exception {
        Path root = Files.createTempDirectory("ocean-shebang-large");
        Path script = Files.createDirectories(root.resolve("bin")).resolve("large-script");
        String body = "#!/usr/bin/env node\n" + "// preserve source\n".repeat(8000) + "console.log('end-marker');\n";
        Files.writeString(script, body);
        OceanShebangRepair.ensurePrefixScripts(root.toFile());
        String repaired = Files.readString(script);
        assertTrue(repaired.endsWith("console.log('end-marker');\n"));
        assertEquals(body.substring(body.indexOf('\n')), repaired.substring(repaired.indexOf('\n')));
    }

    @Test public void existingEnvIsPreserved() throws Exception {
        Path root = Files.createTempDirectory("ocean-shebang-env");
        Path bin = Files.createDirectories(root.resolve("bin"));
        Files.writeString(bin.resolve("bash"), "real bash fixture");
        Files.writeString(bin.resolve("env"), "existing env implementation");
        OceanShebangRepair.ensurePrefixScripts(root.toFile());
        assertEquals("existing env implementation", Files.readString(bin.resolve("env")));
    }

    @Test public void previousFlattenedNpmIsRepairedWithoutMovingItsLibraryTree() throws Exception {
        Path root = Files.createTempDirectory("ocean-npm-upgrade");
        Path bin = Files.createDirectories(root.resolve("bin"));
        Path canonical = root.resolve("lib/node_modules/npm/bin/npm-cli.js");
        Files.createDirectories(canonical.getParent());
        String entry = "#!/usr/bin/env node\nrequire('../lib/cli.js')(process)\n";
        Files.writeString(canonical, entry);
        Files.writeString(bin.resolve("npm"), entry);
        OceanShebangRepair.ensurePrefixScripts(root.toFile());
        String repaired = Files.readString(bin.resolve("npm"));
        assertTrue(repaired.contains(canonical.toString()));
        assertFalse(repaired.contains("require('../lib/cli.js')"));
        assertEquals(entry, Files.readString(canonical));
    }

    @Test public void symlinksOutsideThePrefixArePreservedWithoutChangingTheirTargets() throws Exception {
        Path root = Files.createTempDirectory("ocean-shebang-boundary");
        Path outside = Files.createTempFile("external-script", ".js");
        String source = "#!/usr/bin/env node\nconsole.log('external');\n";
        Files.writeString(outside, source);
        Path link = Files.createDirectories(root.resolve("bin")).resolve("external");
        Files.createSymbolicLink(link, outside);
        OceanShebangRepair.ensurePrefixScripts(root.toFile());
        assertTrue(Files.isSymbolicLink(link));
        assertEquals(source, Files.readString(outside));
    }
}
